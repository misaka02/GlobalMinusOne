package com.example.globalminusone.regression.v2437

import com.example.globalminusone.system.*
import com.example.globalminusone.ui.panel.fileBrowserControlsHeightDp
import com.example.globalminusone.ui.panel.fileListCanScroll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class R8FileBrowserRegressionTest {
    private val root = BrowserLocation("shared", path = "/storage/emulated/0")
    private fun entry(name: String, parent: BrowserLocation, directory: Boolean = false) = BrowserEntry(
        BrowserLocation(name, path = "${parent.path}/$name"), name, directory,
        if (directory) "vnd.android.document/directory" else "audio/mpeg", null, null)

    @Test fun sharedRootSearchReachesDownloadAuditThroughUnreadableSiblingDirectories() = runBlocking<Unit> {
        val denied = entry("Android", root, true)
        val download = entry("Download", root, true)
        val audit = entry("GMO-Audit", download.location, true)
        val visited = mutableListOf<String>()
        val result = searchFileTree(root, "song1", FileCategory.ALL, null, { "grant" }, {}, {
            visited += it.key
            when (it.key) {
                root.key -> listOf(denied, download)
                denied.location.key -> throw IllegalStateException("unreadable sibling")
                download.location.key -> listOf(audit)
                else -> listOf(entry("song1.mp3", audit.location), entry("song2.mp3", audit.location))
            }
        }, {}, true)
        assertEquals(listOf("song1.mp3"), result.entries.map { it.name })
        assertTrue(audit.location.key in visited)
        assertEquals(1, result.skippedDirectories)
        assertNull(result.continuation)
    }

    @Test fun zeroMatchBudgetPageRetainsDirectoryQueueAndResumesWithoutSkipping() = runBlocking<Unit> {
        val download = entry("Download", root, true)
        val audit = entry("GMO-Audit", download.location, true)
        val list: suspend (BrowserLocation) -> List<BrowserEntry> = {
            when (it.key) { root.key -> listOf(download); download.location.key -> listOf(audit); else -> listOf(entry("song1.mp3", audit.location)) }
        }
        var cursor: FileSearchCursor? = null
        val found = mutableListOf<String>()
        var pages = 0
        do {
            val result = searchFileTree(root, "song1", FileCategory.ALL, cursor, { "same" }, {}, list, {}, true,
                budget = FileSearchBudget(entries = 1, directories = 1), nowMillis = { 0L })
            pages++; found += result.entries.map { it.name }; cursor = result.continuation
            if (cursor != null) { assertTrue(result.limited); assertNotNull(result.limitReason) }
        } while (cursor != null && pages < 10)
        assertNull(cursor)
        assertEquals(listOf("song1.mp3"), found)
        assertEquals(3, pages)
    }

    @Test fun timeBudgetReturnsContinuationAndPermissionChangesStillInvalidateIt() = runBlocking<Unit> {
        val download = entry("Download", root, true)
        var now = 0L
        val result = searchFileTree(root, "song1", FileCategory.ALL, null, { "read" }, {}, { listOf(download) }, {}, true,
            budget = FileSearchBudget(millis = 1), nowMillis = { now++ })
        assertNotNull(result.continuation)
        assertEquals("达到本批扫描时间上限", result.limitReason)
        assertThrows(IllegalStateException::class.java) { runBlocking {
            searchFileTree(root, "song1", FileCategory.ALL, result.continuation, { "revoked" }, {}, { emptyList() }, {}, true)
        } }
    }

    @Test fun cancellationDoesNotTurnIntoAnEmptyCompletedSearch() {
        assertThrows(CancellationException::class.java) { runBlocking {
            searchFileTree(root, "song1", FileCategory.ALL, null, { "read" }, {}, { throw CancellationException("cancelled") }, {}, true)
        } }
    }

    @Test fun continuationAddsMatchesInsteadOfReplacingTheFirstBatch() {
        val first = entry("song1.mp3", root)
        val second = entry("song1-copy.mp3", root)
        val combined = mergeFileSearchResults(FileSearchResult(listOf(first), null, 0),
            FileSearchResult(listOf(first, second), null, 2))
        assertEquals(listOf(first, second), combined.entries)
        assertEquals(2, combined.skippedDirectories)
        assertFalse(combined.limited)
    }

    @Test fun directionClaimRespectsBothBoundariesAndZeroMotion() {
        assertFalse(fileListCanScroll(2f, false, true))
        assertTrue(fileListCanScroll(-2f, false, true))
        assertTrue(fileListCanScroll(2f, true, false))
        assertFalse(fileListCanScroll(-2f, true, false))
        assertFalse(fileListCanScroll(0f, true, true))
    }

    @Test fun viewportBudgetIncludesMeasuredRowsAndBottomPadding() {
        val height = 340f
        val actualRowHeight = 52f
        val remaining = height - fileBrowserControlsHeightDp(height, true, actualRowHeight) - 56f
        assertTrue(remaining >= actualRowHeight * 3f + 16f)
    }
}
