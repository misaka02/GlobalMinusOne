package com.example.globalminusone.system.privilege

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.Base64
import java.util.UUID
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

/** Uses the one cached main libsu shell; ordinary requests never call getShell/cmd or create su. */
internal class LibsuRootTransport private constructor(private val shell: Shell) {
    private val closed = java.util.concurrent.atomic.AtomicBoolean()
    // isAlive() may synchronously release dead-process streams; status/cache checks perform no I/O.
    val alive get() = !closed.get() && bootstrap.isCurrent(shell)
    fun close() { if (closed.compareAndSet(false, true)) retirement.retire(shell) }

    suspend fun execute(command: String, maxBytes: Int, timeoutMs: Long): RootProcessOutcome = drainRootOperation {
        withContext(Dispatchers.IO) {
            check(alive) { "Root连接已失效；请手动重新授权" }
            val result = AtomicReference<RootProcessOutcome?>()
            val future = FutureTask {
                shell.execTask { stdin, stdout, stderr ->
                    val outputPipe = stdout.buffered(8192)
                    val errorPipe = stderr.buffered(1024)
                    val token = "GMO_" + UUID.randomUUID().toString().replace("-", "")
                    val scratch = "/data/local/.gmo-io-" + UUID.randomUUID().toString().replace("-", "")
                    val script = rootTaskScript(command, scratch, token, maxBytes, timeoutMs)
                    val taskFailure = AtomicReference<Throwable?>()
                    val errorReader = Thread {
                        try {
                            var bytes = 0
                            while (true) {
                                val line = boundedRootLine(errorPipe, 16_384)
                                if (line == "$token:STDERRDONE") break
                                bytes += line.length; check(bytes <= 16_384) { "Root协议stderr超限" }
                            }
                        } catch (e: Throwable) { taskFailure.set(e); close() }
                    }.apply { name = "GmoRootTaskErrors"; isDaemon = true; start() }
                    try {
                        stdin.write(script.toByteArray(Charsets.UTF_8)); stdin.flush()
                        val begin = boundedRootLine(outputPipe, 256)
                        check(begin == "$token:BEGIN") { if (begin == "$token:DENIED") "su未进入uid0" else "Root响应起始标记无效" }
                        val output = Base64.getDecoder().decode(boundedRootLine(outputPipe, ((maxBytes.toLong() + 2) / 3 * 4 + 8).toInt()))
                        check(boundedRootLine(outputPipe, 256) == "$token:ERR") { "Root响应边界无效" }
                        val errors = Base64.getDecoder().decode(boundedRootLine(outputPipe, 22_000))
                        val end = boundedRootLine(outputPipe, 256)
                        check(end.startsWith("$token:END:")) { "Root响应结束标记无效" }
                        val exit = end.substringAfterLast(':').toIntOrNull() ?: error("Root退出码无效")
                        errorReader.join(1000)
                        check(!errorReader.isAlive && taskFailure.get() == null) { "Root协议stderr未结束" }
                        check(output.size <= maxBytes && errors.size <= 16_384) { "Root输出超过限额；未返回部分内容" }
                        result.set(RootProcessOutcome(exit, output, errors.toString(Charsets.UTF_8), exit == 124 || exit == 137))
                    } finally { if (errorReader.isAlive) { close(); errorReader.interrupt() } }
                }
                result.get() ?: error("Root shell未执行任务")
            }
            Thread(future, "GmoRootTask").apply { isDaemon = true; start() }
            val deadline = System.nanoTime() + timeoutMs * 1_000_000
            try {
                while (true) {
                    currentCoroutineContext().ensureActive()
                    if (System.nanoTime() >= deadline) {
                        close(); future.cancel(true)
                        return@withContext RootProcessOutcome(null, stderr = "Root命令超时；会话已结束，恢复备份需重新显式授权", timedOut = true)
                    }
                    try { return@withContext future.get(40, TimeUnit.MILLISECONDS) }
                    catch (_: TimeoutException) { }
                }
                @Suppress("UNREACHABLE_CODE") error("unreachable")
            } catch (e: Exception) { close(); future.cancel(true); throw e }
        }
    }

    companion object {
        private val retirement = RootShellRetirement<Shell> { runCatching { it.close() }; Unit }
        // MainShell keeps BuilderImpl by reference; replacing its builder after creation would throw.
        private val bootstrap = RootShellBootstrap(object : RootMainShellApi<Shell> {
            private val builder by lazy { Shell.Builder.create() }
            private var installed = false
            override fun configure(command: String, timeoutSeconds: Long) {
                builder.setCommands(command).setTimeout(timeoutSeconds)
                if (!installed) { Shell.setDefaultBuilder(builder); installed = true }
            }
            override fun main(): Shell = Shell.getShell()
            override fun cached(): Shell? = Shell.getCachedShell()
            override fun aliveRoot(shell: Shell): Boolean = shell.isRoot
            override fun close(shell: Shell) { retirement.retire(shell) }
        })
        fun open(su: String): LibsuRootTransport {
            retirement.awaitPending(2_000)
            return LibsuRootTransport(bootstrap.explicitOpen(su))
        }
    }
}

internal fun boundedRootLine(input: InputStream, limit: Int): String {
    var buf = ByteArray(minOf(4096, limit.coerceAtLeast(1)))
    var len = 0
    while (true) {
        val c = input.read()
        check(c >= 0) { "Root管道提前关闭" }
        if (c == 10) return String(buf, 0, len, Charsets.US_ASCII).trimEnd('\r')
        check(len < limit) { "Root协议行超限" }
        if (len == buf.size) {
            buf = buf.copyOf(minOf(limit, (buf.size * 2).coerceAtLeast(len + 1)))
        }
        buf[len++] = c.toByte()
    }
}

internal fun rootTaskScript(command: String, scratch: String, token: String, maxBytes: Int, timeoutMs: Long): String {
    require(scratch.matches(Regex("^/data/local/\\.gmo-io-[a-f0-9]{32}$")))
    require(token.matches(Regex("^GMO_[a-f0-9]{32}$")) && '\u0000' !in command)
    val delimiter = token + "_SCRIPT"
    require(!command.lineSequence().any { it == delimiter })
    val seconds = ((timeoutMs - 1000) / 1000).coerceAtLeast(1)
    // A here-document avoids Linux MAX_ARG_STRLEN for a 1MiB UTF-8 save; payload never enters argv/logs.
    return "( set +e; set +u; [ \"\$(id -u)\" = 0 ] || { printf '$token:DENIED\\n'; exit 126; }; " +
        "d=${rootShellQuote(scratch)}; (umask 077; mkdir -- \"\$d\") || exit 73; " +
        "trap 'rm -f -- \"\$d/out\" \"\$d/err\" \"\$d/request.sh\" \"\$d/code\" \"\$d/errorpipe\"; rmdir -- \"\$d\"' EXIT; " +
        "cat >\"\$d/request.sh\" <<'$delimiter'\n$command\n$delimiter\n" +
        "mkfifo \"\$d/errorpipe\" || exit 73; head -c 16385 \"\$d/errorpipe\" >\"\$d/err\" & er=\$!; " +
        "(/system/bin/timeout -s KILL $seconds /system/bin/sh \"\$d/request.sh\"; c=\$?; printf '%s' \"\$c\" >\"\$d/code\") 2>\"\$d/errorpipe\" | head -c ${maxBytes.toLong() + 1} >\"\$d/out\"; " +
        "wait \"\$er\"; c=\$(cat \"\$d/code\") || c=125; " +
        "printf '$token:BEGIN\\n'; base64 \"\$d/out\" | tr -d '\\n'; printf '\\n$token:ERR\\n'; " +
        "head -c 16385 \"\$d/err\" | base64 | tr -d '\\n'; printf '\\n$token:END:%s\\n' \"\$c\"; " +
        "); printf '$token:STDERRDONE\\n' >&2\n"
}
