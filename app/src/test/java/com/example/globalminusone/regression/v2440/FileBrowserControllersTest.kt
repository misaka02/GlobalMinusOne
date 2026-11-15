package com.example.globalminusone.regression.v2440

import com.example.globalminusone.system.BrowserEntry
import com.example.globalminusone.system.BrowserLocation
import com.example.globalminusone.system.FileSearchResult
import com.example.globalminusone.ui.panel.FileBrowserDirectoryState
import com.example.globalminusone.ui.panel.FileBrowserNavigationState
import com.example.globalminusone.ui.panel.FileInspectorState
import com.example.globalminusone.ui.panel.FileBrowserSearchController
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FileBrowserControllersTest {
    private val root = BrowserLocation("shared", path = "/storage/emulated/0")
    private fun entry(name: String) = BrowserEntry(root.copy(path = "${root.path}/$name"), name,
        false, "text/plain", null, null)
    private fun result(name: String) = FileSearchResult(listOf(entry(name)), null, 1)

    @Test fun clearingQueryRejectsLateProgressResultsAndCompletion() = runTest {
        val controller = FileBrowserSearchController(this)
        val release = CompletableDeferred<Unit>()
        controller.start(null, false, search = { report ->
            withContext(NonCancellable) { release.await(); report("obsolete"); result("old") }
        }, onFirstPage = { fail("Cleared search scrolled the list") })
        runCurrent()
        controller.clear()
        release.complete(Unit)
        runCurrent()
        assertNull(controller.results.value)
        assertEquals("", controller.progress.value)
        assertFalse(controller.finding.value)
        assertNull(controller.error.value)
    }

    @Test fun replacingQueryCannotPublishCancelledWorkOverNewResult() = runTest {
        val controller = FileBrowserSearchController(this)
        val release = CompletableDeferred<Unit>()
        controller.start(null, false, { withContext(NonCancellable) { release.await(); result("old") } }, {})
        runCurrent()
        controller.start(null, false, { result("new") }, {})
        runCurrent()
        release.complete(Unit)
        runCurrent()
        assertEquals("new", controller.results.value?.entries?.single()?.name)
        assertFalse(controller.finding.value)
    }

    @Test fun receiptRefreshKeepsPublishedPageUntilReplacementArrives() = runTest {
        val controller = FileBrowserSearchController(this)
        val published = result("published")
        controller.start(null, false, { published }, {})
        runCurrent()
        val publishedSnapshot = controller.results.value
        assertEquals(published, publishedSnapshot)
        val replacement = CompletableDeferred<FileSearchResult>()
        controller.refreshWhenSettled({ true }) {
            controller.start(null, true, { replacement.await() }, {})
        }
        runCurrent()
        assertSame(publishedSnapshot, controller.results.value)
        assertTrue(controller.finding.value)
        replacement.complete(result("refreshed"))
        runCurrent()
        assertEquals("refreshed", controller.results.value?.entries?.single()?.name)
    }

    @Test fun refreshWaitingForScanCannotRestartAfterNavigationInvalidatesQuery() = runTest {
        val controller = FileBrowserSearchController(this)
        val release = CompletableDeferred<FileSearchResult>()
        controller.start(null, false, { release.await() }, {})
        runCurrent()
        var restarted = false
        launch { controller.refreshWhenSettled({ true }) { restarted = true } }
        runCurrent()
        controller.invalidate()
        runCurrent()
        assertFalse(restarted)
        assertNull(controller.results.value)
    }

    @Test fun userCancelKeepsLastPublishedResultAndExposesCancellation() = runTest {
        val controller = FileBrowserSearchController(this)
        val published = result("published")
        controller.start(null, false, { published }, {})
        runCurrent()
        val publishedSnapshot = controller.results.value
        assertEquals(published, publishedSnapshot)
        controller.start(null, true, { CompletableDeferred<FileSearchResult>().await() }, {})
        runCurrent()
        controller.cancelByUser()
        runCurrent()
        assertSame(publishedSnapshot, controller.results.value)
        assertEquals("已取消，未完成结果未发布", controller.error.value)
        assertFalse(controller.finding.value)
    }

    @Test fun directoryNavigationRejectsAnOldLoadEvenWithoutCooperativeCancellation() = runTest {
        val directory = FileBrowserDirectoryState()
        val release = CompletableDeferred<List<BrowserEntry>>()
        val old = launch { directory.load(root, { null }, { false }, { release.await() }, {}, { it }) }
        runCurrent()
        val child = root.copy(title = "child", path = "${root.path}/child")
        val currentRows = listOf(entry("current"))
        directory.load(child, { currentRows }, { true }, { error("Fresh cache must not scan") }, {}, { it })
        release.complete(listOf(entry("old")))
        old.join()
        assertSame(currentRows, directory.entries.value)
        assertEquals(child.key, directory.locationKey.value)
        assertFalse(directory.loading.value)
    }

    @Test fun revokedGrantClearsCachedRowsBeforePublishingAnError() = runTest {
        val directory = FileBrowserDirectoryState()
        directory.entries.value = listOf(entry("cached"))
        directory.visibleEntries.value = directory.entries.value
        var invalidated = false
        directory.load(root, { throw SecurityException("grant revoked") }, { true },
            { fail("Listing after grant failure"); emptyList() }, { invalidated = true }, { it })
        assertTrue(invalidated)
        assertTrue(directory.entries.value.isEmpty())
        assertTrue(directory.visibleEntries.value.isEmpty())
        assertEquals("grant revoked", directory.error.value)
        assertFalse(directory.loading.value)
    }

    @Test fun hiddenPaneClearsRowsAndInvalidatesOutstandingLoad() = runTest {
        val directory = FileBrowserDirectoryState()
        val release = CompletableDeferred<List<BrowserEntry>>()
        val old = launch { directory.load(root, { null }, { false }, { release.await() }, {}, { it }) }
        runCurrent()
        directory.load(null, { error("No cache when hidden") }, { false }, { emptyList() }, {}, { it })
        release.complete(listOf(entry("old")))
        old.join()
        assertTrue(directory.entries.value.isEmpty())
        assertNull(directory.locationKey.value)
        assertFalse(directory.loading.value)
    }

    @Test fun breadcrumbBackTraversesNestedDirectoriesThenCommonRoots() {
        val navigation = FileBrowserNavigationState()
        val child = root.copy(title = "child", path = "${root.path}/child")
        assertTrue(navigation.navigate(listOf(root, child)))
        assertEquals("目录", navigation.section.value)
        assertEquals(listOf(root), navigation.parentStack())
        navigation.navigate(navigation.parentStack())
        navigation.section.value = "收藏"
        assertFalse(navigation.navigate(listOf(root)))
        assertEquals("目录", navigation.section.value)
        navigation.navigate(navigation.parentStack())
        assertEquals("常用", navigation.section.value)
        assertTrue(navigation.stack.value.isEmpty())
    }

    @Test fun inspectorSwitchingFilesDiscardsPreviousDraftAndConfirmation() {
        val inspector = FileInspectorState()
        inspector.open(entry("first"))
        inspector.hashCompare.value = "previous hash"
        inspector.toggleRename("first")
        inspector.renameInput.value = "unfinished"
        inspector.toggleDelete()
        inspector.open(entry("second"))
        assertEquals("second", inspector.entry.value?.name)
        assertEquals("second", inspector.renameInput.value)
        assertEquals("", inspector.hashCompare.value)
        assertFalse(inspector.renameMode.value)
        assertFalse(inspector.deleteConfirm.value)
        assertNull(inspector.properties.value)
        assertNull(inspector.checksums.value)
    }

    @Test fun inspectorRenameAndDeleteRemainMutuallyExclusive() {
        val inspector = FileInspectorState()
        inspector.open(entry("name"))
        inspector.toggleRename("name")
        assertTrue(inspector.renameMode.value)
        inspector.toggleDelete()
        assertFalse(inspector.renameMode.value)
        assertTrue(inspector.deleteConfirm.value)
        inspector.toggleRename("name")
        assertFalse(inspector.deleteConfirm.value)
        assertTrue(inspector.renameMode.value)
        inspector.toggleRename("name")
        assertFalse(inspector.renameMode.value)
    }

}
