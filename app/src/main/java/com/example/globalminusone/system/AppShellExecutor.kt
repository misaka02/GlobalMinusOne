package com.example.globalminusone.system

import android.content.Context
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import com.example.globalminusone.data.ShellExecutionMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

internal data class AppShellResult(
    val exitCode: Int?,
    val output: String,
    val timedOut: Boolean,
    val elapsedMs: Long = 0L,
    val modeUsed: ShellExecutionMode = ShellExecutionMode.NORMAL
)

/**
 * Executes user-configured Shell command cards under the chosen privilege level:
 * - [ShellExecutionMode.NORMAL]: app UID `/system/bin/sh -c`
 * - [ShellExecutionMode.SHIZUKU]: bound ADB shell `ShizukuShellService` (requires explicit Shizuku grant)
 * - [ShellExecutionMode.ROOT]: serialized `UserRootSession` (requires explicit Root grant)
 */
internal object AppShellExecutor {
    private val shizukuGate = Mutex()

    suspend fun execute(
        context: Context,
        command: String,
        mode: ShellExecutionMode = ShellExecutionMode.NORMAL,
        timeoutSec: Int = 10
    ): AppShellResult {
        require(command.isNotBlank() && command.length <= 4096) { "命令需为1～4096字符" }
        val boundedSec = timeoutSec.coerceIn(2, 60)
        val startedAt = SystemClock.elapsedRealtime()
        return when (mode) {
            ShellExecutionMode.NORMAL -> executeLocal(context, command, boundedSec, startedAt)
            ShellExecutionMode.SHIZUKU -> executeViaShizuku(context.applicationContext, command, boundedSec, startedAt)
            ShellExecutionMode.ROOT -> executeViaRoot(command, boundedSec, startedAt)
        }
    }

    private suspend fun executeLocal(
        context: Context,
        command: String,
        timeoutSec: Int,
        startedAt: Long
    ): AppShellResult = withContext(Dispatchers.IO) {
        val output = File.createTempFile("shell-result-", ".txt", context.cacheDir)
        var process: Process? = null
        try {
            process = ProcessBuilder("/system/bin/sh", "-c", command)
                .redirectErrorStream(true)
                .redirectOutput(output)
                .start()
            val timeoutMs = timeoutSec * 1000L
            while (process.isAlive && SystemClock.elapsedRealtime() - startedAt < timeoutMs && output.length() < 65536) {
                delay(50)
            }
            val stopped = process.isAlive
            if (stopped) process.destroyForcibly()
            val bytes = output.inputStream().use { input ->
                val buffer = ByteArray(16384)
                val read = input.read(buffer)
                if (read <= 0) byteArrayOf() else buffer.copyOf(read)
            }
            val suffix = if (output.length() > bytes.size) "\n（输出已截断）" else ""
            AppShellResult(
                exitCode = if (stopped) null else process.exitValue(),
                output = bytes.toString(Charsets.UTF_8) + suffix,
                timedOut = stopped,
                elapsedMs = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L),
                modeUsed = ShellExecutionMode.NORMAL
            )
        } finally {
            process?.takeIf { it.isAlive }?.destroyForcibly()
            output.delete()
        }
    }

    private suspend fun executeViaRoot(
        command: String,
        timeoutSec: Int,
        startedAt: Long
    ): AppShellResult {
        val result = UserRootSession.runShellCardCommand(command, timeoutSec * 1000L)
        val combinedOutput = buildString {
            if (result.stdout.isNotBlank()) append(result.stdout.trimEnd())
            if (result.stderr.isNotBlank()) {
                if (isNotEmpty()) append('\n')
                append(result.stderr.trimEnd())
            }
            if (isEmpty() && result.reason.isNotBlank()) {
                append(result.reason)
            }
        }
        return AppShellResult(
            exitCode = result.exitCode,
            output = combinedOutput,
            timedOut = result.status == RootStatus.TimedOut,
            elapsedMs = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L),
            modeUsed = ShellExecutionMode.ROOT
        )
    }

    private suspend fun executeViaShizuku(
        appContext: Context,
        command: String,
        timeoutSec: Int,
        startedAt: Long
    ): AppShellResult = shizukuGate.withLock {
        if (!ShizukuPermissions.isServiceAvailable()) {
            return@withLock AppShellResult(
                exitCode = null,
                output = "Shizuku 服务未运行：请先启动 Shizuku 并授权本应用后重试。",
                timedOut = false,
                elapsedMs = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L),
                modeUsed = ShellExecutionMode.SHIZUKU
            )
        }
        val granted = ShizukuPermissions.requestAuthorization(25_000L)
        if (!granted) {
            return@withLock AppShellResult(
                exitCode = null,
                output = "尚未授予 Shizuku 权限：请在弹窗中允许后重试。",
                timedOut = false,
                elapsedMs = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L),
                modeUsed = ShellExecutionMode.SHIZUKU
            )
        }

        val client = ShizukuUserServiceClient(
            serviceClassName = ShizukuShellService::class.java.name,
            tag = "shell-card",
            processSuffix = "shellcard",
            version = 1,
            connectTimeoutMs = 10_000L
        )
        client.withOneShotBinder(appContext) { remote ->
            if (remote == null || !remote.isBinderAlive) {
                return@withOneShotBinder AppShellResult(
                    exitCode = null,
                    output = "连接 Shizuku Shell 子服务失败，请确认 Shizuku 正在运行。",
                    timedOut = false,
                    elapsedMs = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L),
                    modeUsed = ShellExecutionMode.SHIZUKU
                )
            }
            withContext(Dispatchers.IO) {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                try {
                    data.writeInterfaceToken("com.example.globalminusone.ShellCard.v1")
                    data.writeString(command)
                    data.writeInt(timeoutSec)
                    val ok = remote.transact(IBinder.FIRST_CALL_TRANSACTION, data, reply, 0)
                    check(ok) { "Shizuku shell card transact failed" }
                    reply.readException()
                    val timedOut = reply.readInt() == 1
                    val exit = reply.readInt()
                    val text = reply.readString().orEmpty()
                    AppShellResult(
                        exitCode = if (timedOut) null else exit,
                        output = text,
                        timedOut = timedOut,
                        elapsedMs = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L),
                        modeUsed = ShellExecutionMode.SHIZUKU
                    )
                } finally {
                    data.recycle()
                    reply.recycle()
                }
            }
        }
    }
}
