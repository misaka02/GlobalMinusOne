package com.example.globalminusone.system

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

enum class RootStatus { NotRequested, Unavailable, Denied, TimedOut, Granted, Error }
enum class PrivilegeAction { WIFI_ON, WIFI_OFF, BLUETOOTH_ON, BLUETOOTH_OFF, WIFI_STATE, BLUETOOTH_STATE }
data class RootCommandResult(val status: RootStatus, val exitCode: Int?, val stdout: String, val stderr: String, val reason: String) {
    val succeeded get() = status == RootStatus.Granted && exitCode == 0
}
internal data class RootProcessRequest(val executable: String, val command: String, val input: ByteArray?, val maxBytes: Int, val timeoutMs: Long, val entryMarker: String? = null)
internal data class RootProcessOutcome(val exitCode: Int?, val bytes: ByteArray = byteArrayOf(), val stderr: String = "", val timedOut: Boolean = false)
internal fun interface RootExecutor { suspend fun execute(request: RootProcessRequest): RootProcessOutcome }
internal sealed interface RootReadRequest {
    data class ListDirectory(val path: String) : RootReadRequest
    data class ReadFile(val path: String, val maxBytes: Int) : RootReadRequest
}
internal fun rootReadCommand(request: RootReadRequest): String = when(request) {
    is RootReadRequest.ReadFile -> {
        require(request.maxBytes in 1..(32 * 1024 * 1024)) { "root读取限额无效" }
        "head -c ${request.maxBytes + 1} -- ${rootShellPath(request.path)}"
    }
    is RootReadRequest.ListDirectory -> {
        val dir = rootShellPath(request.path)
        "d=$dir; [ -d \"\$d\" ] && [ -r \"\$d\" ] || exit 73; " +
            "for p in \"\$d\"/* \"\$d\"/.[!.]* \"\$d\"/..?*; do " +
            "[ -e \"\$p\" ] || [ -L \"\$p\" ] || continue; " +
            "if [ -L \"\$p\" ]; then k=l; elif [ -d \"\$p\" ]; then k=d; elif [ -f \"\$p\" ]; then k=f; else k=o; fi; " +
            "m=\$(stat -c '%s %Y' -- \"\$p\") || exit 74; printf '%s\\000%s\\000%s\\000' \"\$k\" \"\$m\" \"\$p\"; done"
    }
}

/** Paths are data, never shell syntax. Control bytes are refused rather than interpreted. */
internal fun rootShellPath(path: String): String {
    return rootShellQuote(normalizeRootPath(path))
}
internal fun normalizeRootPath(path: String): String {
    require(path.startsWith('/') && path.none { it.code < 32 || it.code == 127 }) { "root路径必须是绝对路径，且不能含控制字符" }
    val parts = path.split('/')
    require(parts.none { it == "." || it == ".." }) { "root路径不能含.或..跳转段" }
    return "/" + parts.filter { it.isNotEmpty() }.joinToString("/")
}
internal fun rootShellQuote(value: String) = "'" + value.replace("'", "'\"'\"'") + "'"

/** Only an explicit user action calls requestAuthorization. No grant is stored in configuration. */
object UserRootSession {
    private val mutableStatus = MutableStateFlow(RootStatus.NotRequested)
    val status: StateFlow<RootStatus> = mutableStatus.asStateFlow()
    private val gate = Mutex()
    private var executable: String? = null
    private val generation = java.util.concurrent.atomic.AtomicLong()
    private val processLock = Any()
    @Volatile private var broker: LibsuRootTransport? = null
    private val revocationListeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()
    private val callbackExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { task ->
        Thread(task, "GmoRootRevocation").apply { isDaemon = true }
    }
    fun registerRevocationListener(listener: () -> Unit): java.io.Closeable {
        revocationListeners += listener
        return java.io.Closeable { revocationListeners -= listener }
    }
    fun authorizedEpoch(): Long? = synchronized(processLock) {
        if (status.value == RootStatus.Granted && executorForTests == null && broker?.alive != true) {
            mutableStatus.value = RootStatus.Error; executable = null; broker?.close(); broker = null
            generation.incrementAndGet(); RootWritePolicy.clear(); notifyRevoked()
        }
        generation.get().takeIf { status.value == RootStatus.Granted }
    }
    fun isAuthorizationCurrent(epoch: Long): Boolean = authorizedEpoch() == epoch
    fun revokeAuthorization() {
        synchronized(processLock) {
            generation.incrementAndGet(); mutableStatus.value = RootStatus.NotRequested; executable = null
            broker?.close(); broker = null
            RootWritePolicy.clear()
        }
        notifyRevoked()
    }
    private fun notifyRevoked() {
        val listeners = revocationListeners.toList()
        callbackExecutor.execute { listeners.forEach { runCatching { it() } } }
    }
    internal fun currentEpoch(): Long = generation.get()
    private var executorForTests: RootExecutor? = null
    private var probeForTests: (() -> String?)? = null
    internal fun resetForTests(executor: RootExecutor? = null, probe: (() -> String?)? = null) {
        revokeAuthorization(); executorForTests = executor; probeForTests = probe
    }
    private data class Raw(val exit: Int?, val bytes: ByteArray, val diagnostic: String, val timedOut: Boolean = false)

    suspend fun requestAuthorization(): RootCommandResult = gate.withLock {
        if (authorizedEpoch() != null) return@withLock RootCommandResult(RootStatus.Granted, 0, "0", "", "复用当前有效Root会话")
        val epoch = generation.get()
        val su = if (probeForTests != null) probeForTests?.invoke() else withContext(Dispatchers.IO) {
            listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su").firstOrNull { File(it).canExecute() }
                ?: runCatching { ProcessBuilder("sh", "-c", "command -v su").start().let { process ->
                    try { if (process.waitFor(1, TimeUnit.SECONDS) && process.exitValue() == 0)
                        process.inputStream.bufferedReader().readLine()?.takeIf { it.startsWith('/') && File(it).canExecute() } else null
                    } finally { process.destroy() }
                } }.getOrNull()
        }
        if (epoch != generation.get()) return@withLock RootCommandResult(status.value, null, "", "", "Root请求结束或失败；未重新授权")
        if (su == null) {
            synchronized(processLock) {
                if (epoch != generation.get()) return@withLock RootCommandResult(status.value, null, "", "", "Root请求结束或失败；未重新授权")
                mutableStatus.value = RootStatus.Unavailable; executable = null
            }
            return@withLock RootCommandResult(RootStatus.Unavailable, null, "", "", "未找到su；设备未提供root能力")
        }
        synchronized(processLock) { if (epoch == generation.get()) executable = su }
        val raw = perform(su, "id -u", null, 4096, 30_000, epoch = epoch)
        if (epoch != generation.get()) return@withLock RootCommandResult(status.value, null, "", raw.diagnostic, raw.diagnostic.ifBlank { "Root请求已取消或会话已结束" })
        val next = when {
            raw.timedOut -> RootStatus.TimedOut
            raw.exit == 0 && raw.bytes.toString(Charsets.UTF_8).trim() == "0" -> RootStatus.Granted
            raw.exit != null -> RootStatus.Denied
            else -> RootStatus.Error
        }
        synchronized(processLock) {
            if (epoch != generation.get()) return@withLock RootCommandResult(status.value, null, "", "", "Root请求结束或失败；未重新授权")
            mutableStatus.value = next
            if (next != RootStatus.Granted) { executable = null; broker?.close(); broker = null; RootWritePolicy.clear(); generation.incrementAndGet(); notifyRevoked() }
        }
        RootCommandResult(next, raw.exit, raw.bytes.toString(Charsets.UTF_8), raw.diagnostic,
            when(next) { RootStatus.Granted -> "已确认su返回uid0（仅当前会话）"; RootStatus.TimedOut -> "su授权超时，已终止请求"; RootStatus.Denied -> "su未授予root权限"; else -> raw.diagnostic.ifBlank { "无法启动su" } })
    }

    suspend fun runPrivilegeAction(action: PrivilegeAction): RootCommandResult {
        val command = when(action) {
            PrivilegeAction.WIFI_ON -> "svc wifi enable"
            PrivilegeAction.WIFI_OFF -> "svc wifi disable"
            PrivilegeAction.BLUETOOTH_ON -> "svc bluetooth enable"
            PrivilegeAction.BLUETOOTH_OFF -> "svc bluetooth disable"
            PrivilegeAction.WIFI_STATE -> "cmd wifi status"
            PrivilegeAction.BLUETOOTH_STATE -> "dumpsys bluetooth_manager"
        }
        val lease = authorizedEpoch() ?: return RootCommandResult(status.value, null, "", "", "尚无有效Root会话；请显式授权")
        return runAuthorized(command, expectedEpoch = lease, maxBytes = if (action == PrivilegeAction.BLUETOOTH_STATE) 128 * 1024 else 16_384, timeoutMs = 8_000).let { raw ->
            RootCommandResult(if (raw.exit == 0 && status.value == RootStatus.Granted) RootStatus.Granted else if (raw.timedOut) RootStatus.TimedOut else status.value.takeIf { it != RootStatus.Granted } ?: RootStatus.Error,
                raw.exit, raw.bytes.toString(Charsets.UTF_8), raw.diagnostic,
                if (raw.exit == 0 && status.value == RootStatus.Granted) "命令已完成，仍须核对系统实际状态" else raw.diagnostic.ifBlank { "特权命令失败；退出码${raw.exit}" })
        }
    }

    /** Fixed split operations share the current explicit Root lease and existing serialized transport. */
    internal suspend fun runSplitRequest(request: com.example.globalminusone.split.SplitSystemRequest, apkPath: String? = null): com.example.globalminusone.split.SplitSystemResult {
        val lease = authorizedEpoch() ?: return com.example.globalminusone.split.SplitSystemResult(-1, "", "当前没有有效Root会话；分屏未执行")
        val command = when (request) {
            com.example.globalminusone.split.SplitSystemRequest.VendorProbe,
            is com.example.globalminusone.split.SplitSystemRequest.VendorEdge -> {
                val sourceApk = apkPath ?: return com.example.globalminusone.split.SplitSystemResult(-1, "", "缺少已安装应用路径；厂商分屏未执行")
                com.example.globalminusone.split.SplitVendorRootCommand.command(request, sourceApk)
            }
            else -> com.example.globalminusone.split.SplitSystemProtocol.command(request)
        }
        val raw = runAuthorized(command, null, if (request == com.example.globalminusone.split.SplitSystemRequest.Snapshot) 2 * 1024 * 1024 else 64 * 1024, 8_000, lease)
        if (!isAuthorizationCurrent(lease)) return com.example.globalminusone.split.SplitSystemResult(-1, "", "Root会话已失效，旧分屏结果已丢弃")
        return com.example.globalminusone.split.SplitSystemProtocol.sanitize(request,
            com.example.globalminusone.split.SplitSystemResult(raw.exit, raw.bytes.toString(Charsets.UTF_8), raw.diagnostic))
    }

    internal suspend fun runFileRequest(request: RootReadRequest): ByteArray {
        val lease = authorizedEpoch() ?: error("Root会话未授权或已失效")
        val command = rootReadCommand(request)
        val limit = if (request is RootReadRequest.ReadFile) request.maxBytes else 4 * 1024 * 1024
        val raw = runAuthorized(command, null, limit, 8_000, lease)
        check(status.value == RootStatus.Granted) { "root会话已结束或授权失效" }
        check(raw.exit == 0 && !raw.timedOut) { raw.diagnostic.ifBlank { raw.bytes.toString(Charsets.UTF_8).take(600).ifBlank { "root命令失败（${raw.exit}）" } } }
        return raw.bytes
    }

    internal suspend fun runMutationRequest(request: RootMutationRequest): ByteArray {
        val lease = authorizedEpoch() ?: error("Root会话未授权或已失效")
        if (request is RootMutationRequest.SaveText) RootWritePolicy.consumeIssued(request.approval, request.bytes)
        if (request is RootMutationRequest.Create) RootWritePolicy.requireDirectory(request.parent)
        if (request is RootMutationRequest.Create && request.text != null) decodeEditableText(request.text)
        val raw = runAuthorized(rootMutationCommand(request), null, 16_384, 8_000, lease)
        check(status.value == RootStatus.Granted && raw.exit == 0 && !raw.timedOut) {
            raw.diagnostic.ifBlank { "Root事务失败；请检查原目标与备份，未宣称保存完成（${raw.exit}）" }
        }
        return raw.bytes
    }

    private suspend fun runAuthorized(command: String, input: ByteArray? = null, maxBytes: Int, timeoutMs: Long, expectedEpoch: Long): Raw = gate.withLock {
        drainRootOperation {
            val epoch = generation.get()
            if (epoch != expectedEpoch || status.value != RootStatus.Granted || executable == null) return@drainRootOperation Raw(null, byteArrayOf(), "尚未手动授予本会话root权限")
            // A privileged daemon may outlive its su client. A server-side watchdog bounds the actual job too.
            if (executorForTests == null && !File("/system/bin/timeout").canExecute()) return@drainRootOperation Raw(null, byteArrayOf(), "设备缺少timeout，无法安全限制root命令；此操作未执行")
            val marker = "GMO_ROOT_" + java.util.UUID.randomUUID().toString().replace("-", "")
            val guarded = "[ \"\$(id -u)\" = 0 ] || exit 126; printf '$marker:0\\n' >&2; " + command
            val raw = perform(executable ?: return@drainRootOperation Raw(null, byteArrayOf(), "root会话已结束"), guarded, input, maxBytes, timeoutMs + 1000, marker, epoch)
            if (epoch != generation.get()) return@drainRootOperation Raw(-1, byteArrayOf(), "root会话已结束，旧响应已废弃")
            val entered = raw.diagnostic.contains("$marker:0\n")
            if (!entered) {
                synchronized(processLock) {
                    if (epoch != generation.get()) return@drainRootOperation Raw(-1, byteArrayOf(), "root会话已结束，旧响应已废弃")
                    mutableStatus.value = if (raw.timedOut) RootStatus.TimedOut else if (raw.exit != null && raw.exit != 0) RootStatus.Denied else RootStatus.Error
                    executable = null; broker?.close(); broker = null; RootWritePolicy.clear(); generation.incrementAndGet(); notifyRevoked()
                }
                Raw(raw.exit?.takeIf { it != 0 } ?: -1, byteArrayOf(), "未进入root会话：${raw.diagnostic.take(500)}", raw.timedOut)
            } else {
                if (raw.timedOut) synchronized(processLock) {
                    if (epoch == generation.get()) { mutableStatus.value = RootStatus.TimedOut; executable = null; broker?.close(); broker = null; RootWritePolicy.clear(); generation.incrementAndGet(); notifyRevoked() }
                }
                raw.copy(diagnostic = raw.diagnostic.replace("$marker:0\n", ""))
            }
        }
    }

    private suspend fun perform(su: String, command: String, input: ByteArray?, maxBytes: Int, timeoutMs: Long, marker: String? = null, epoch: Long): Raw {
        currentCoroutineContext().ensureActive()
        if (epoch != generation.get()) return Raw(null, byteArrayOf(), "root会话已结束")
        return try {
            val fake = executorForTests
            if (fake == null) execute(su, command, input, maxBytes, timeoutMs, epoch) else {
                val result = fake.execute(RootProcessRequest(su, command, input, maxBytes, timeoutMs, marker))
                if (result.bytes.size > maxBytes) Raw(null, byteArrayOf(), result.stderr + "root输出超过单次资源上限")
                else Raw(result.exitCode, result.bytes, result.stderr, result.timedOut)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Explicit authorization cancelled before its grant is established still ends that attempt.
            // Normal authorized commands are drained above; cancellation is not a grant revocation.
            if (epoch == generation.get() && status.value != RootStatus.Granted) revokeAuthorization()
            throw e
        }
        catch (e: Exception) {
            if (epoch != generation.get()) return Raw(null, byteArrayOf(), "Root会话已结束，旧管道错误已丢弃")
            val timeout = generateSequence<Throwable>(e) { it.cause }.any { it is java.util.concurrent.TimeoutException }
            val next = if (timeout) RootStatus.TimedOut else if (e is com.topjohnwu.superuser.NoShellException) RootStatus.Denied else RootStatus.Error
            synchronized(processLock) {
                if (epoch != generation.get()) return Raw(null, byteArrayOf(), "Root会话已结束，旧管道错误已丢弃")
                mutableStatus.value = next; executable = null; broker?.close(); broker = null
                RootWritePolicy.clear(); generation.incrementAndGet(); notifyRevoked()
            }
            Raw(null, byteArrayOf(), if (timeout) "Root授权/命令超时，已结束会话" else e.message ?: "Root进程失败", timeout)
        }
    }

    /** The sole su process survives ordinary overlay recreation; only explicit end/failure closes it. */
    private suspend fun execute(su: String, command: String, input: ByteArray?, maxBytes: Int, timeoutMs: Long, epoch: Long): Raw = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        if (epoch != generation.get()) return@withContext Raw(null, byteArrayOf(), "root会话已结束")
        check(input == null) { "Root协议不接受任意stdin；写入由类型化事务编码" }
        val connection = broker ?: LibsuRootTransport.open(su).also { started ->
            synchronized(processLock) {
                if (epoch != generation.get()) { started.close(); return@withContext Raw(null, byteArrayOf(), "root会话已结束") }
                broker = started
            }
        }
        val result = connection.execute(command, maxBytes, timeoutMs)
        Raw(result.exitCode, result.bytes, result.stderr, result.timedOut)
    }
}
