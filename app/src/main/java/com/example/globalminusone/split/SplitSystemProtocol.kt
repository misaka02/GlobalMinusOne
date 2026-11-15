package com.example.globalminusone.split

import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

sealed interface SplitSystemRequest {
    data object Help : SplitSystemRequest
    data object Snapshot : SplitSystemRequest
    data class Focus(val taskId: Int) : SplitSystemRequest
    data class StartTarget(val component: String, val userId: Int) : SplitSystemRequest
    data class MoveToSide(val taskId: Int) : SplitSystemRequest
    data class RemoveFromSide(val taskId: Int) : SplitSystemRequest
    data object VendorProbe : SplitSystemRequest
    data class VendorEdge(val sourceTaskId: Int, val sourcePackage: String, val targetComponent: String, val userId: Int = 0) : SplitSystemRequest
}
data class SplitSystemResult(val exitCode: Int?, val stdout: String, val stderr: String) {
    val succeeded get() = exitCode == 0
}

/** Fixed typed IPC only. No shell syntax, URI, extras, display redirection or arbitrary commands. */
object SplitSystemProtocol {
    @JvmStatic fun decode(op: Int, taskId: Int, component: String?, userId: Int): SplitSystemRequest {
        require(taskId >= 0 && userId in 0..999) { "分屏参数无效" }
        return when (op) {
            0, 1 -> { require(taskId == 0 && component == null && userId == 0); if (op == 0) SplitSystemRequest.Help else SplitSystemRequest.Snapshot }
            2, 4, 5 -> {
                require(taskId > 0 && component == null && userId == 0)
                when (op) { 2 -> SplitSystemRequest.Focus(taskId); 4 -> SplitSystemRequest.MoveToSide(taskId); else -> SplitSystemRequest.RemoveFromSide(taskId) }
            }
            3 -> {
                require(taskId == 0 && component != null && component.length <= 512 &&
                    component.matches(Regex("""[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.$]+"""))) { "分屏目标组件无效" }
                SplitSystemRequest.StartTarget(component, userId)
            }
            6 -> { require(taskId == 0 && component == null && userId == 0); SplitSystemRequest.VendorProbe }
            7 -> {
                val fields = component?.split('|').orEmpty()
                require(taskId > 0 && userId == 0 && fields.size == 2 && validSplitPackage(fields[0]) &&
                    fields[1].length <= 512 && fields[1].matches(Regex("""[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.$]+"""))) { "厂商分屏参数无效" }
                SplitSystemRequest.VendorEdge(taskId, fields[0], fields[1], 0)
            }
            else -> throw IllegalArgumentException("未知分屏操作")
        }
    }
    @JvmStatic fun op(request: SplitSystemRequest): Int = when (request) {
        SplitSystemRequest.Help -> 0; SplitSystemRequest.Snapshot -> 1; is SplitSystemRequest.Focus -> 2
        is SplitSystemRequest.StartTarget -> 3; is SplitSystemRequest.MoveToSide -> 4; is SplitSystemRequest.RemoveFromSide -> 5
        SplitSystemRequest.VendorProbe -> 6; is SplitSystemRequest.VendorEdge -> 7
    }
    @JvmStatic fun taskId(request: SplitSystemRequest): Int = when (request) {
        is SplitSystemRequest.VendorEdge -> request.sourceTaskId
        is SplitSystemRequest.Focus -> request.taskId; is SplitSystemRequest.MoveToSide -> request.taskId
        is SplitSystemRequest.RemoveFromSide -> request.taskId; else -> 0
    }
    @JvmStatic fun component(request: SplitSystemRequest): String? = when (request) {
        is SplitSystemRequest.StartTarget -> request.component
        is SplitSystemRequest.VendorEdge -> request.sourcePackage + "|" + request.targetComponent
        else -> null
    }
    @JvmStatic fun userId(request: SplitSystemRequest): Int = when (request) {
        is SplitSystemRequest.StartTarget -> request.userId; is SplitSystemRequest.VendorEdge -> request.userId; else -> 0
    }
    @JvmStatic fun command(request: SplitSystemRequest): String {
        decode(op(request), taskId(request), component(request), userId(request))
        val shell = "dumpsys -t 3 activity service com.android.systemui/.SystemUIService WMShell"
        return when (request) {
            SplitSystemRequest.Help -> "$shell help; am help"
            SplitSystemRequest.Snapshot -> "dumpsys -t 3 activity activities"
            is SplitSystemRequest.Focus -> "am task focus ${request.taskId}"
            is SplitSystemRequest.StartTarget -> "am start --user ${request.userId} -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n '${request.component}' -f 0x10000000"
            is SplitSystemRequest.MoveToSide -> "$shell splitscreen moveToSideStage ${request.taskId} 1"
            is SplitSystemRequest.RemoveFromSide -> "$shell splitscreen removeFromSideStage ${request.taskId}"
            SplitSystemRequest.VendorProbe, is SplitSystemRequest.VendorEdge -> error("厂商分屏必须使用受限特权入口")
        }
    }
    @JvmStatic fun sanitize(request: SplitSystemRequest, result: SplitSystemResult): SplitSystemResult {
        if (request == SplitSystemRequest.VendorProbe || request is SplitSystemRequest.VendorEdge) {
            if (!result.succeeded) return SplitSystemResult(result.exitCode, "", when (result.exitCode) {
                2 -> "当前前台任务与源快照不一致，未执行分屏"
                3 -> "未发现兼容的厂商分屏接口"
                4 -> "厂商分屏权限拒绝"
                5 -> "厂商未受理分屏请求"
                else -> "厂商分屏连接中断或超时，结果未核验"
            })
            val expected = if (request == SplitSystemRequest.VendorProbe) VENDOR_SPLIT_CAPABLE else VENDOR_SPLIT_ACCEPTED
            return if (result.stdout.trim() == expected) SplitSystemResult(0, expected, "")
            else SplitSystemResult(-1, "", "厂商分屏返回值无法核验")
        }
        if (!result.succeeded) return SplitSystemResult(result.exitCode, "", "系统分屏命令失败（${result.exitCode ?: "连接中断"}）")
        if (request == SplitSystemRequest.Snapshot) {
            val snapshot = parseSplitTaskDump(result.stdout)
            return if (snapshot.tasks.isEmpty()) SplitSystemResult(-1, "", "系统未提供可识别的任务结构")
            else SplitSystemResult(0, encodeSplitSnapshot(snapshot), "")
        }
        if (result.stdout.contains("Error:", true) || result.stdout.contains("Exception", true) ||
            result.stdout.contains("Permission Denial", true) || result.stdout.contains("Invalid command:", true) || result.stderr.contains("Error:", true))
            return SplitSystemResult(-1, "", "系统未接受分屏操作，请检查本机接口与应用限制")
        return SplitSystemResult(0, if (request == SplitSystemRequest.Help) result.stdout.take(64 * 1024) else "REQUEST_ACCEPTED", "")
    }
}

/** Called only inside an already authorized privileged factory; child process is bounded and reaped. */
object SplitSystemExecutor {
    @JvmStatic fun execute(request: SplitSystemRequest): SplitSystemResult {
        if (request == SplitSystemRequest.VendorProbe || request is SplitSystemRequest.VendorEdge)
            return VendorSplitExecutor.execute(request)
        var process: Process? = null
        return try {
            process = ProcessBuilder("/system/bin/timeout", "-k", "1s", "5s", "/system/bin/sh", "-c", SplitSystemProtocol.command(request))
                .redirectErrorStream(true).start()
            val bytes = ByteArrayOutputStream()
            process.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer); if (read < 0) break
                    check(bytes.size() + read <= 2 * 1024 * 1024) { "任务快照超过限额" }
                    bytes.write(buffer, 0, read)
                }
            }
            check(process.waitFor(1, TimeUnit.SECONDS)) { "分屏命令未退出" }
            SplitSystemProtocol.sanitize(request, SplitSystemResult(process.exitValue(), bytes.toString("UTF-8"), ""))
        } catch (_: Exception) { SplitSystemResult(-1, "", "分屏服务命令超时、输出超限或不可用") }
        finally { process?.destroy() }
    }
}
