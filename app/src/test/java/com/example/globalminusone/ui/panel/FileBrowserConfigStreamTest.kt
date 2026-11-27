package com.example.globalminusone.ui.panel

import com.example.globalminusone.data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import com.example.globalminusone.data.FileBrowserBookmark
import com.example.globalminusone.data.FileBrowserConfig
import com.example.globalminusone.data.FileBrowserRoot
import com.example.globalminusone.data.FileBrowserSortOrder
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.ui.panel.file.FileBrowserConfigStream

@OptIn(ExperimentalCoroutinesApi::class)
class FileBrowserConfigStreamTest {
    @Test fun unrelatedStateUpdatesDoNotEmitFileConfiguration() = runTest {
        val source = MutableStateFlow(GlobalMinusOneState())
        val stream = FileBrowserConfigStream(source)
        val seen = mutableListOf<FileBrowserConfig>()
        val job = backgroundScope.launch { stream.values.collect { seen.add(it) } }
        runCurrent()
        assertEquals(listOf(source.value.fileBrowserConfig), seen)
        source.value = source.value.copy(noteText = "new note", clipboardHistory = emptyList())
        runCurrent()
        source.value = source.value.copy(pages = source.value.pages.reversed(), editMode = true)
        runCurrent()
        assertEquals(1, seen.size)
        job.cancelAndJoin()
        assertEquals(0, source.subscriptionCount.value)
    }

    @Test fun everyFileConfigurationFieldRemainsObservable() = runTest {
        val source = MutableStateFlow(GlobalMinusOneState())
        val stream = FileBrowserConfigStream(source)
        val seen = mutableListOf<FileBrowserConfig>()
        backgroundScope.launch { stream.values.collect { seen.add(it) } }
        runCurrent()
        val changes: List<(FileBrowserConfig) -> FileBrowserConfig> = listOf(
            { it.copy(roots = listOf(FileBrowserRoot("content://synthetic/tree/test", "test"))) },
            { it.copy(sortOrder = FileBrowserSortOrder.SIZE) },
            { it.copy(sortDescending = !it.sortDescending) },
            { it.copy(showHidden = !it.showHidden) },
            { it.copy(windowWidthFraction = .8f) },
            { it.copy(windowHeightFraction = .5f) },
            { it.copy(windowXFraction = .1f) },
            { it.copy(windowYFraction = .2f) },
            { it.copy(favorites = listOf(FileBrowserBookmark("favorite", path = "/synthetic/favorite"))) },
            { it.copy(recent = listOf(FileBrowserBookmark("recent", path = "/synthetic/recent"))) }
        )
        changes.forEachIndexed { index, change ->
            source.value = source.value.copy(fileBrowserConfig = change(source.value.fileBrowserConfig))
            runCurrent()
            assertEquals(index + 2, seen.size)
            assertEquals(source.value.fileBrowserConfig, seen.last())
            assertEquals(source.value.fileBrowserConfig, stream.current)
        }
    }

    @Test fun independentOwnersAndColdResubscriptionsStartWithTheirLatestValue() = runTest {
        val first = MutableStateFlow(GlobalMinusOneState())
        val second = MutableStateFlow(GlobalMinusOneState(fileBrowserConfig = FileBrowserConfig(showHidden = true)))
        val old = FileBrowserConfigStream(first); val current = FileBrowserConfigStream(second)
        assertEquals(0, first.subscriptionCount.value)
        assertTrue(current.current.showHidden)
        val seen = mutableListOf<FileBrowserConfig>()
        val oldJob = backgroundScope.launch { old.values.collect { seen.add(it) } }
        runCurrent(); oldJob.cancelAndJoin()
        second.value = second.value.copy(fileBrowserConfig = second.value.fileBrowserConfig.copy(sortDescending = true))
        val next = backgroundScope.launch { current.values.collect { seen.add(it) } }
        runCurrent()
        assertEquals(second.value.fileBrowserConfig, seen.last())
        assertEquals(0, first.subscriptionCount.value)
        next.cancelAndJoin()
        assertEquals(0, second.subscriptionCount.value)
    }
}
