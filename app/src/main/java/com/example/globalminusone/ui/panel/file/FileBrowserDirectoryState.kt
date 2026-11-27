package com.example.globalminusone.ui.panel.file

import androidx.compose.runtime.mutableStateOf
import com.example.globalminusone.system.file.BrowserEntry
import com.example.globalminusone.system.file.BrowserLocation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.ensureActive
import java.util.concurrent.atomic.AtomicLong

/** Cache/grant checks and asynchronous publication for one pane. Observation remains lifecycle-owned. */
internal class FileBrowserDirectoryState {
    val entries = mutableStateOf<List<BrowserEntry>>(emptyList())
    val visibleEntries = mutableStateOf<List<BrowserEntry>>(emptyList())
    val locationKey = mutableStateOf<String?>(null)
    val loading = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)
    private val requests = AtomicLong()

    @Synchronized fun invalidate(): Long = requests.incrementAndGet()

    /** Only short state assignments run under this monitor; filtering/IO stay outside it. */
    @Synchronized
    private fun publish(request: Long, update: () -> Unit): Boolean {
        if (requests.get() != request) return false
        update()
        return true
    }

    suspend fun load(
        current: BrowserLocation?,
        cached: (BrowserLocation) -> List<BrowserEntry>?,
        fresh: (BrowserLocation) -> Boolean,
        list: suspend (BrowserLocation) -> List<BrowserEntry>,
        invalidateCache: (BrowserLocation) -> Unit,
        filterCached: (List<BrowserEntry>) -> List<BrowserEntry>
    ) {
        val caller = currentCoroutineContext()
        val request = synchronized(this) { caller.ensureActive(); requests.incrementAndGet() }
        publish(request) { error.value = null }
        if (current == null) {
            publish(request) {
                entries.value = emptyList()
                visibleEntries.value = emptyList()
                locationKey.value = null
                loading.value = false
            }
            return
        }
        try {
            // cached() validates access first. Never expose cached rows before this succeeds.
            val snapshot = cached(current)
            caller.ensureActive()
            val retained = synchronized(this) {
                if (requests.get() != request) return
                snapshot ?: entries.value.takeIf { locationKey.value == current.key }.orEmpty()
            }
            val cachedVisible = filterCached(retained)
            caller.ensureActive()
            if (!publish(request) {
                entries.value = retained
                visibleEntries.value = cachedVisible
                locationKey.value = current.key
                loading.value = snapshot == null
            }) return
            if (snapshot != null && fresh(current)) return
            val loaded = list(current)
            val filtered = withContext(Dispatchers.Default) { filterCached(loaded) }
            caller.ensureActive()
            publish(request) {
                entries.value = loaded
                visibleEntries.value = filtered
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            invalidateCache(current)
            publish(request) {
                entries.value = emptyList()
                visibleEntries.value = emptyList()
                error.value = e.message ?: "目录加载失败，请重试"
            }
        } finally {
            if (caller.isActive) publish(request) { loading.value = false }
        }
    }
}
