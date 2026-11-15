package com.example.globalminusone.system

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

internal data class AppShellResult(val exitCode: Int?, val output: String, val timedOut: Boolean)

/** Intentionally app UID only; does not call su or use the shared privileged session. */
internal object AppShellExecutor {
    suspend fun execute(context: Context, command: String): AppShellResult = withContext(Dispatchers.IO) {
        require(command.isNotBlank() && command.length <= 4096) { "命令需为1～4096字符" }
        val output = File.createTempFile("shell-result-", ".txt", context.cacheDir)
        var process: Process? = null
        try {
            process = ProcessBuilder("/system/bin/sh", "-c", command).redirectErrorStream(true).redirectOutput(output).start()
            val started = android.os.SystemClock.elapsedRealtime()
            while (process.isAlive && android.os.SystemClock.elapsedRealtime() - started < 5000 && output.length() < 65536) delay(50)
            val stopped = process.isAlive
            if (stopped) process.destroyForcibly()
            val bytes = output.inputStream().use { input ->
                val buffer = ByteArray(16384); val read = input.read(buffer)
                if (read <= 0) byteArrayOf() else buffer.copyOf(read)
            }
            val suffix = if (output.length() > bytes.size) "\n（输出已截断）" else ""
            AppShellResult(if (stopped) null else process.exitValue(), bytes.toString(Charsets.UTF_8) + suffix, stopped)
        } finally {
            process?.takeIf { it.isAlive }?.destroyForcibly()
            output.delete()
        }
    }
}
