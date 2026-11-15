package com.example.globalminusone.split

import android.content.ComponentName
import android.content.Intent
import android.os.Process
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/** Runs only in the existing Root/Shizuku authorized process; never in the application UID. */
object VendorSplitExecutor {
    private data class Endpoint(val factory: Method, val edge: Method)
    private fun endpoint(): Endpoint? {
        for (name in listOf("com.oplus.splitscreen.OplusSplitScreenManager", "com.color.splitscreen.ColorSplitScreenManager")) {
            val found = runCatching {
                val cls = Class.forName(name)
                val factory = cls.getMethod("getInstance")
                val edge = cls.getMethod("splitScreenForEdgePanel", Intent::class.java, Int::class.javaPrimitiveType)
                check(java.lang.reflect.Modifier.isStatic(factory.modifiers) && cls.isAssignableFrom(factory.returnType))
                check(edge.returnType == Int::class.javaPrimitiveType)
                Endpoint(factory, edge)
            }.getOrNull()
            if (found != null) return found
        }
        return null
    }
    @JvmStatic @Synchronized fun execute(request: SplitSystemRequest): SplitSystemResult {
        if (Process.myUid() != 0 && Process.myUid() != 2000) return rejected("厂商分屏仅允许已授权Root/Shizuku进程", 4)
        return try {
            SplitSystemProtocol.decode(SplitSystemProtocol.op(request), SplitSystemProtocol.taskId(request),
                SplitSystemProtocol.component(request), SplitSystemProtocol.userId(request))
            val endpoint = endpoint() ?: return rejected("未发现兼容的Oplus/Color分屏签名", 3)
            if (request == SplitSystemRequest.VendorProbe) return SplitSystemResult(0, VENDOR_SPLIT_CAPABLE, "")
            require(request is SplitSystemRequest.VendorEdge && request.userId == 0)
            val component = requireNotNull(ComponentName.unflattenFromString(request.targetComponent))
            val homes = readHomePackages() ?: return rejected("无法核对桌面应用，未执行分屏")
            // Initialize the manager before the final foreground read; no Activity is launched here.
            val manager = endpoint.factory.invoke(null) ?: return rejected("厂商分屏实例不可用")
            val actual = SplitSystemExecutor.execute(SplitSystemRequest.Snapshot)
            if (!actual.succeeded || !vendorSplitSourceAllowed(decodeSplitSnapshot(actual.stdout),
                    request.sourceTaskId, request.sourcePackage, component.packageName, homes))
                return rejected("当前前台任务与源快照不一致，未执行分屏", 2)
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val returned = endpoint.edge.invoke(manager, intent, 0) as? Int ?: return rejected("厂商返回类型不兼容")
            if (vendorSplitRequestAccepted(returned)) SplitSystemResult(0, VENDOR_SPLIT_ACCEPTED, "")
            else rejected("厂商未受理分屏请求（$returned）")
        } catch (error: InvocationTargetException) {
            if (error.targetException is SecurityException) rejected("厂商分屏权限拒绝", 4) else rejected("厂商分屏调用失败")
        } catch (_: SecurityException) { rejected("厂商分屏权限拒绝", 4) }
        catch (_: Exception) { rejected("厂商分屏签名或运行环境不兼容", 3) }
    }
    private fun rejected(message: String, code: Int = 5) = SplitSystemResult(code, "", message)
    private fun readHomePackages(): Set<String>? {
        var child: java.lang.Process? = null
        return try {
            child = ProcessBuilder("/system/bin/timeout", "-k", "1s", "2s", "/system/bin/cmd", "package",
                "query-activities", "--brief", "--user", "0", "-a", Intent.ACTION_MAIN, "-c", Intent.CATEGORY_HOME)
                .redirectErrorStream(true).start()
            val bytes = child.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val size = input.read(buffer)
                    if (size < 0) break
                    check(output.size() + size <= 64 * 1024)
                    output.write(buffer, 0, size)
                }
                output.toByteArray()
            }
            if (bytes.size > 64 * 1024 || !child.waitFor(1, java.util.concurrent.TimeUnit.SECONDS) || child.exitValue() != 0) null
            else bytes.toString(Charsets.UTF_8).lineSequence().mapNotNull { ComponentName.unflattenFromString(it.trim())?.packageName }
                .filter(::validSplitPackage).toSet().takeIf { it.isNotEmpty() }
        } catch (_: Exception) { null }
        finally { child?.destroy() }
    }
}

/** Fixed app_process entry for the existing serialized Root shell; no new service or grant. */
@androidx.annotation.Keep
object VendorSplitEntry {
    @JvmStatic fun main(args: Array<String>) {
        val result = try {
            val request = when {
                args.contentEquals(arrayOf("probe")) -> SplitSystemRequest.VendorProbe
                args.size == 4 && args[0] == "edge" -> SplitSystemProtocol.decode(7, args[1].toInt(), args[2] + "|" + args[3], 0)
                else -> error("参数无效")
            }
            VendorSplitExecutor.execute(request)
        } catch (_: Exception) { SplitSystemResult(-1, "", "厂商分屏入口参数无效") }
        if (result.succeeded) println(result.stdout) else System.err.println(result.stderr)
        kotlin.system.exitProcess(if (result.succeeded) 0 else result.exitCode?.takeIf { it in 1..125 } ?: 1)
    }
}
