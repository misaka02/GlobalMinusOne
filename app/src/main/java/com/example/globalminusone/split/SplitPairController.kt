package com.example.globalminusone.split

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal fun interface SplitTaskExecutor { suspend fun execute(request: SplitSystemRequest): SplitSystemResult }
internal data class SplitPairResult(val confirmed: Boolean, val message: String)

/** Vendor edge-panel control flow only. No task focus, ordinary launch, stage move or task removal. */
internal class SplitPairController(private val executor: SplitTaskExecutor) {
    companion object { private val transactions = Mutex() }
    private suspend fun run(request: SplitSystemRequest): SplitSystemResult {
        currentCoroutineContext().ensureActive()
        return executor.execute(request).also { check(it.succeeded) { it.stderr.ifBlank { "厂商分屏接口未受理请求" } } }
    }
    private suspend fun snapshot() = decodeSplitSnapshot(run(SplitSystemRequest.Snapshot).stdout)
    suspend fun pair(origin: SplitOrigin, component: String, targetPackage: String): SplitPairResult = transactions.withLock {
        try {
            require(origin.userId == 0 && validSplitPackage(origin.packageName) && validSplitPackage(targetPackage) &&
                component.substringBefore('/') == targetPackage && origin.packageName != targetPackage) { "分屏仅支持当前用户下不同的两个应用" }
            check(run(SplitSystemRequest.VendorProbe).stdout == VENDOR_SPLIT_CAPABLE) { "系统未提供兼容的厂商分屏接口" }
            val before = snapshot()
            check(before.origin(origin.packageName, 0)?.id == origin.taskId) { "原应用已不在前台；分屏未执行" }
            // The privileged executor repeats all foreground/home/system checks immediately before invocation.
            val accepted = run(SplitSystemRequest.VendorEdge(origin.taskId, origin.packageName, component))
            check(accepted.stdout == VENDOR_SPLIT_ACCEPTED) { "厂商分屏请求未受理" }
            // A system callback may still finish after UI cancellation. Observe only; never undo user tasks.
            withContext(NonCancellable) {
                val deadline = System.nanoTime() + 6_000_000_000L
                repeat(20) {
                    if (System.nanoTime() >= deadline) return@withContext unconfirmed()
                    delay(250)
                    val actual = runCatching { snapshot() }.getOrNull() ?: return@repeat
                    val target = actual.leafTasks().singleOrNull {
                        it.packageName == targetPackage && it.userId == 0 && it.displayId == 0 && it.visible
                    }
                    if (target != null && confirmsSplitPair(actual, origin, target.id, targetPackage))
                        return@withContext SplitPairResult(true, "已核对原应用与所选应用位于同一分屏的两侧")
                }
                unconfirmed()
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { SplitPairResult(false, error.message ?: "厂商分屏请求失败") }
    }
    private fun unconfirmed() = SplitPairResult(false, "厂商已受理请求，但未核对到指定两应用的分屏；未清理或重排应用任务")
}
