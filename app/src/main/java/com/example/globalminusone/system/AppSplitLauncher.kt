package com.example.globalminusone.system

import android.content.ComponentName
import android.content.Context
import android.os.Process
import android.widget.Toast
import com.example.globalminusone.browser.PrivilegedSplitBackend
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.split.*
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicLong

object AppSplitLauncher {
    internal const val TARGET = "split_target"
    internal const val LABEL = "split_label"
    internal const val ORIGIN_PACKAGE = "split_origin_package"
    internal const val ORIGIN_TASK = "split_origin_task"
    internal const val ORIGIN_USER = "split_origin_user"
    internal const val ORIGIN_CAPTURE = "split_origin_capture"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val generations = AtomicLong()
    private data class Capture(val id: Long, val packageName: String, val userId: Int, val origin: SplitOrigin? = null)
    private var capture: Capture? = null
    private var captureJob: Job? = null
    private var openJob: Job? = null

    /** Called once before a closed panel opens; freeze the original app before any panel workspace. */
    fun capturePanelOrigin(context: Context) {
        val app = context.applicationContext
        val pkg = MinusOneRepository.getInstance(app).currentForegroundPackage.value
        val id = generations.incrementAndGet()
        captureJob?.cancel(); openJob?.cancel()
        capture = Capture(id, pkg.takeIf { validSplitPackage(it) && it != app.packageName && it != "com.android.systemui" }.orEmpty(), Process.myUid() / 100000)
        val pending = checkNotNull(capture)
        captureJob = scope.launch {
            val executor = authorizedExecutor(app) ?: return@launch
            val result = runCatching { executor.execute(SplitSystemRequest.Snapshot) }.getOrNull() ?: return@launch
            val snapshot = runCatching { decodeSplitSnapshot(result.stdout) }.getOrNull() ?: return@launch
            val focused = snapshot.leafTasks().singleOrNull { it.id == snapshot.focusedId } ?: return@launch
            if (focused.packageName == app.packageName || focused.packageName == "com.android.systemui" ||
                (pending.packageName.isNotEmpty() && pending.packageName != focused.packageName)) return@launch
            val task = snapshot.origin(focused.packageName, pending.userId) ?: return@launch
            if (capture?.id == id) capture = pending.copy(packageName = task.packageName,
                origin = SplitOrigin(task.packageName, task.id, pending.userId, id))
        }
    }
    internal fun authorizedExecutor(context: Context): SplitTaskExecutor? {
        UserRootSession.authorizedEpoch()?.let { epoch -> return SplitTaskExecutor { request ->
            if (!UserRootSession.isAuthorizationCurrent(epoch)) SplitSystemResult(-1, "", "Root会话已结束，分屏未执行")
            else UserRootSession.runSplitRequest(request, context.applicationInfo.sourceDir)
        } }
        if (PrivilegedSplitBackend.available(context)) return SplitTaskExecutor { PrivilegedSplitBackend.execute(context, it) }
        return null
    }
    /** Called from TYPE_APPLICATION_OVERLAY only. Never launches an Activity/picker before pairing. */
    fun open(context: Context, component: ComponentName? = null, label: String = ""): Boolean {
        val app = context.applicationContext
        if (component == null) { toast(app, "请选择覆盖层应用菜单中的第二应用；分屏选择器保持停用"); return false }
        val target = runCatching { app.packageManager.getActivityInfo(component, 0) }.getOrNull()
        if (target == null || !target.exported || !target.enabled || !target.applicationInfo.enabled || component.packageName == app.packageName) {
            toast(app, "目标应用入口不可用，未执行分屏"); return false
        }
        val request = capture
        if (request == null) { toast(app, "未记录面板前的应用，请退出面板后重新打开"); return false }
        if (authorizedExecutor(app) == null) { toast(app, "当前应用配对分屏需要有效Root或Shizuku会话；未请求新授权"); return false }
        openJob?.cancel()
        openJob = scope.launch {
            captureJob?.join()
            val origin = capture?.takeIf { it.id == request.id }?.origin
            if (origin == null) { toast(app, "未能确定面板前的原任务，请退出面板后重新打开"); return@launch }
            val executor = authorizedExecutor(app) ?: return@launch
            val result = SplitPairController(executor).pair(origin, component.flattenToString(), component.packageName)
            if (capture?.id == request.id) toast(app, result.message)
        }
        return true
    }
    private fun toast(context: Context, text: String) { Toast.makeText(context, text, Toast.LENGTH_LONG).show() }
}
