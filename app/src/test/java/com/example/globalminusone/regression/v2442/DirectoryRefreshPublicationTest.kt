package com.example.globalminusone.regression.v2442

import com.example.globalminusone.system.BrowserEntry
import com.example.globalminusone.system.BrowserLocation
import com.example.globalminusone.ui.panel.FileBrowserDirectoryState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DirectoryRefreshPublicationTest {
    private val directory = BrowserLocation("synthetic", path = "/synthetic")
    private fun entry(name: String) = BrowserEntry(directory.copy(path = "/synthetic/$name"), name, false, "text/plain", 2, null)

    @Test fun sameDirectoryKeepsPublishedRowsUntilFreshFilteredRowsAreReady() = runTest {
        val state = FileBrowserDirectoryState()
        val first = listOf(entry("old"))
        state.load(directory, { first }, { true }, { error("fresh cache must not list") }, {}, { it })
        val pending = CompletableDeferred<List<BrowserEntry>>()
        val job = launch { state.load(directory, { null }, { false }, { pending.await() }, {}, { it.filter { row -> row.name != "hidden" } }) }
        runCurrent()
        assertTrue(state.loading.value)
        assertEquals(first, state.visibleEntries.value)
        val visible = entry("new")
        pending.complete(listOf(visible, entry("hidden")))
        job.join()
        assertFalse(state.loading.value)
        assertEquals(listOf(visible), state.visibleEntries.value)
        assertEquals(directory.key, state.locationKey.value)
    }
}
