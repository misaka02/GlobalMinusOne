package com.example.globalminusone.ui.panel

import androidx.compose.runtime.mutableStateOf
import com.example.globalminusone.system.BrowserEntry
import com.example.globalminusone.system.BrowserLocation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import java.util.concurrent.atomic.AtomicLong

/** Cache/grant checks and asynchronous publication for one pane. Observation remains lifecycle-owned. */
internal class FileBrowserDirectoryState {
    val entries = mutableStateOf<List<BrowserEntry>>(emptyList())
    val visibleEntries = mutableStateOf<List<BrowserEntry>>(emptyList())
    val locationKey = mutableStateOf<String?>(null)
    val loading = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)
    private val requests = AtomicLong()

    fun invalidate(): Long = requests.incrementAndGet()

    suspend fun load(
        current: BrowserLocation?,
        cached: (BrowserLocation) -> List<BrowserEntry>?,
        fresh: (BrowserLocation) -> Boolean,
        list: suspend (BrowserLocation) -> List<BrowserEntry>,
        invalidateCache: (BrowserLocation) -> Unit,
        filterCached: (List<BrowserEntry>) -> List<BrowserEntry>
    ) {
        val request = invalidate()
        error.value = null
        if (current == null) {
            entries.value = emptyList()
            visibleEntries.value = emptyList()
            locationKey.value = null
            loading.value = false
            return
        }
        try {
            // cached() validates access first. Never expose cached rows before this succeeds.
            val snapshot = cached(current)
            val retained = snapshot ?: entries.value.takeIf { locationKey.value == current.key }.orEmpty()
            entries.value = retained
            visibleEntries.value = filterCached(retained)
            locationKey.value = current.key
            loading.value = snapshot == null
            if (snapshot != null && fresh(current)) return
            val loaded = list(current)
            val filtered = withContext(Dispatchers.Default) { filterCached(loaded) }
            if (requests.get() == request) {
                entries.value = loaded
                visibleEntries.value = filtered
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            invalidateCache(current)
            if (requests.get() == request) {
                entries.value = emptyList()
                visibleEntries.value = emptyList()
                error.value = e.message ?: "目录加载失败，请重试"
            }
        } finally {
            if (currentCoroutineContext().isActive && requests.get() == request) loading.value = false
        }
    }
}
