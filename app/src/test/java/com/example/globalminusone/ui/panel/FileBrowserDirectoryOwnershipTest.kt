package com.example.globalminusone.ui.panel

import com.example.globalminusone.system.BrowserEntry
import com.example.globalminusone.system.BrowserLocation
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class FileBrowserDirectoryOwnershipTest {
    private fun location(name: String) = BrowserLocation(name, path = "/synthetic/$name")
    private fun rows(name: String) = listOf(BrowserEntry(location(name), name, false, "text/plain", 1L, 1L))

    @Test fun lateCacheOrFilterOnIoCannotReplaceANewerDirectory() = runTest {
        for (blockCache in listOf(true, false)) {
            val directory = FileBrowserDirectoryState()
            val started = CountDownLatch(1); val release = CountDownLatch(1)
            fun block() { started.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
            val old = launch(Dispatchers.IO) {
                directory.load(location("old"),
                    cached = { if (blockCache) block(); rows("old") }, fresh = { true },
                    list = { error("Fresh cache must not rescan") }, invalidateCache = {},
                    filterCached = { if (!blockCache) block(); it })
            }
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS))
                directory.invalidate()
                directory.load(location("new"), cached = { rows("new") }, fresh = { true },
                    list = { error("Fresh cache must not rescan") }, invalidateCache = {}, filterCached = { it })
                release.countDown(); old.join()
                assertEquals(rows("new"), directory.entries.value)
                assertEquals(rows("new"), directory.visibleEntries.value)
                assertEquals(location("new").key, directory.locationKey.value)
                assertFalse(directory.loading.value)
            } finally { release.countDown(); old.cancelAndJoin() }
        }
    }

    @Test fun oldCancelledListCannotClearANewerLoadingStateOrPublishRows() = runTest {
        val directory = FileBrowserDirectoryState()
        val oldResult = CompletableDeferred<List<BrowserEntry>>()
        val newResult = CompletableDeferred<List<BrowserEntry>>()
        val old = backgroundScope.launch {
            directory.load(location("old"), cached = { null }, fresh = { false },
                list = { withContext(NonCancellable) { oldResult.await() } }, invalidateCache = {}, filterCached = { it })
        }
        runCurrent()
        old.cancel()
        val next = backgroundScope.launch {
            directory.load(location("new"), cached = { null }, fresh = { false },
                list = { newResult.await() }, invalidateCache = {}, filterCached = { it })
        }
        runCurrent()
        assertTrue(directory.loading.value)
        oldResult.complete(rows("old")); old.join()
        assertTrue(directory.loading.value)
        assertEquals(location("new").key, directory.locationKey.value)
        assertTrue(directory.entries.value.isEmpty())
        newResult.complete(rows("new")); next.join()
        assertEquals(rows("new"), directory.entries.value)
        assertFalse(directory.loading.value)
    }
}
