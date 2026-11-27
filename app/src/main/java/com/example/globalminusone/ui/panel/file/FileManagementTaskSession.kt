package com.example.globalminusone.ui.panel.file

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.*

/** One foreground operation. Tokens own publication; cancellation never rolls back completed file IO. */
internal class FileManagementTaskSession(private val scope: CoroutineScope, private val onChanged: () -> Unit) {
    val busy = mutableStateOf(false)
    val progress = mutableStateOf<String?>(null)
    val notice = mutableStateOf<String?>(null)
    val reload = mutableIntStateOf(0)
    val analyzer = FileManagementAnalyzerState()
    @Volatile private var rootKey: String? = null
    private var visible = false
    private var generation = 0L
    private var active: Task? = null
    private val mutationJobs = mutableListOf<Job>()

    fun bindRoot(key: String?, expanded: Boolean) {
        val previous = synchronized(this) {
            if (key == rootKey && expanded == visible) return
            val job = detach(showNotice = false)
            generation++
            rootKey = key
            visible = expanded
            analyzer.bindRoot(key)
            job
        }
        previous?.cancel()
    }

    /** Called under the monitor; actual cancellation and external callbacks always run outside it. */
    private fun detach(showNotice: Boolean): Job? {
        val previous = active ?: return null
        active = null
        busy.value = false
        progress.value = null
        if (showNotice) notice.value = "已取消。已完成的文件操作不会回滚；未完成的统计/查找结果已废弃"
        return previous.job
    }

    fun cancel(showNotice: Boolean = true) {
        val previous = synchronized(this) { detach(showNotice) }
        previous?.cancel()
    }

    fun run(mutates: Boolean = false, expectedRootKey: String? = rootKey, block: suspend Task.() -> Unit) {
        val task = synchronized(this) {
            if (!visible || active != null) return
            if (expectedRootKey != rootKey) {
                notice.value = "目录已变化，请重新选择操作范围"
                return
            }
            mutationJobs.removeAll { it.isCompleted }
            if (mutates && mutationJobs.isNotEmpty()) {
                notice.value = "正在等待上次文件操作退出，请稍后重试；已完成的操作不会回滚"
                return
            }
            val token = Task(rootKey, generation, mutates)
            active = token
            busy.value = true
            progress.value = null
            notice.value = null
            token.job = scope.launch(start = CoroutineStart.LAZY) {
                token.started = true
                try {
                    token.checkCurrent()
                    token.block()
                    token.finishedNormally = true
                } catch (e: CancellationException) {
                    token.publish { notice.value = "已取消。已完成的文件操作不会回滚；未完成的统计/查找结果已废弃" }
                    throw e
                } catch (e: Exception) {
                    token.publish { notice.value = e.message ?: "操作失败，请检查授权" }
                }
            }
            if (mutates) mutationJobs += token.job
            token
        }
        // Completion also runs if the owning scope was cancelled before the lazy body ever started.
        task.job.invokeOnCompletion { complete(task) }
        task.job.start()
    }

    private fun complete(task: Task) {
        val completion = synchronized(this) {
            if (active === task) {
                active = null
                busy.value = false
                progress.value = null
            }
            var invalidatedRead: Job? = null
            val notifyChanged = task.mutates && task.started
            if (notifyChanged) {
                // Normal mutations already published their incremental results in the command body.
                // An interrupted writer can finish after a newer read captured an earlier filesystem snapshot.
                if (!task.finishedNormally && task.ioStarted && rootKey == task.root) {
                    analyzer.invalidate()
                    if (active?.mutates == false) invalidatedRead = detach(showNotice = false)
                }
                reload.intValue++
            }
            notifyChanged to invalidatedRead
        }
        completion.second?.cancel()
        if (completion.first) onChanged()
    }

    inner class Task internal constructor(internal val root: String?, private val epoch: Long, internal val mutates: Boolean) {
        internal lateinit var job: Job
        @Volatile internal var started = false
        @Volatile internal var finishedNormally = false
        @Volatile internal var ioStarted = false
        fun isCurrent(): Boolean = synchronized(this@FileManagementTaskSession) {
            active === this && generation == epoch && rootKey == root && visible
        }
        suspend fun checkCurrent() {
            currentCoroutineContext().ensureActive()
            if (!isCurrent()) throw CancellationException("File management owner changed")
        }
        fun publish(block: () -> Unit) = synchronized(this@FileManagementTaskSession) {
            if (active === this && generation == epoch && rootKey == root && visible) block()
        }
        fun currentTaskProgress(message: String) { publish { progress.value = message } }
        suspend fun <T> currentTaskIO(block: suspend () -> T): T {
            checkCurrent()
            ioStarted = true
            val result = block()
            checkCurrent()
            return result
        }
    }
}
