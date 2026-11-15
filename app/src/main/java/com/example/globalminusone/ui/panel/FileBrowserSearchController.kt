package com.example.globalminusone.ui.panel

import androidx.compose.runtime.mutableStateOf
import com.example.globalminusone.system.FileSearchCursor
import com.example.globalminusone.system.FileSearchResult
import com.example.globalminusone.system.mergeFileSearchResults
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Owns only recursive-search work and publication, independent of focus, navigation and permissions. */
internal class FileBrowserSearchController(private val scope: CoroutineScope) {
    val results = mutableStateOf<FileSearchResult?>(null)
    val finding = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)
    val progress = mutableStateOf("")
    private var generation = 0L
    private var job: Job? = null

    fun isCurrent(request: Long) = request == generation

    /** Query identity changed: old callbacks must not publish, even if cancellation is delayed. */
    fun invalidate(): Long {
        generation++
        job?.cancel()
        finding.value = false
        results.value = null
        error.value = null
        return generation
    }

    fun clear() {
        invalidate()
        progress.value = ""
    }

    fun cancelByUser() {
        generation++
        job?.cancel()
        finding.value = false
        error.value = "已取消，未完成结果未发布"
    }

    fun start(
        cursor: FileSearchCursor?,
        preserveResults: Boolean,
        search: suspend ((String) -> Unit) -> FileSearchResult,
        onFirstPage: suspend () -> Unit
    ) {
        job?.cancel()
        val request = ++generation
        finding.value = true
        error.value = null
        progress.value = ""
        if (cursor == null && !preserveResults) results.value = null
        job = scope.launch {
            try {
                val result = search { if (isCurrent(request)) progress.value = it }
                if (isCurrent(request)) {
                    results.value = mergeFileSearchResults(if (cursor != null) results.value else null, result)
                    if (cursor == null) onFirstPage()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isCurrent(request)) error.value = e.message ?: "查找失败"
            } finally {
                if (isCurrent(request)) finding.value = false
            }
        }
    }

    /** Directory receipts refresh the same query after its scan; they are not query edits. */
    suspend fun refreshWhenSettled(eligible: () -> Boolean, restart: () -> Unit) {
        val request = generation
        val running = job?.takeIf { it.isActive }
        if (results.value != null || running != null) {
            running?.join()
            if (isCurrent(request) && eligible()) restart()
        }
    }
}
