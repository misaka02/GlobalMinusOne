package com.example.globalminusone.regression.v2439

import com.example.globalminusone.system.*
import com.example.globalminusone.ui.panel.file.fileBrowserControlsHeightDp
import com.example.globalminusone.ui.panel.file.fileBrowserNarrowChrome
import com.example.globalminusone.ui.panel.file.fileBrowserToolbarColumns
import org.junit.Assert.*
import org.junit.Test
import com.example.globalminusone.system.file.BrowserEntry
import com.example.globalminusone.system.file.BrowserLocation
import com.example.globalminusone.system.file.DirectoryObservationSource
import com.example.globalminusone.system.file.FileCategory
import com.example.globalminusone.system.file.FileSearchResult
import com.example.globalminusone.system.file.activeFileSearchResult
import com.example.globalminusone.system.file.browserEntryCountLabel
import com.example.globalminusone.system.file.directoryObservationSource
import com.example.globalminusone.system.file.entry
import com.example.globalminusone.system.file.filterBrowserEntries
import com.example.globalminusone.system.file.mergeFileSearchResults

class FileBrowserNavigationAndObservationTest {
    private val root = BrowserLocation("shared", path = "/storage/emulated/0")
    private fun entry(name: String, directory: Boolean = false) = BrowserEntry(
        root.copy(title = name, path = "${root.path}/$name"), name, directory,
        if (directory) "vnd.android.document/directory" else "text/plain", null, null)

    @Test fun pureDirectoryFilterNeverClaimsThoseDirectoriesAreFileMatches() {
        val folders = listOf(entry("Download", true), entry("Pictures", true), entry(".hidden", true))
        val visible = filterBrowserEntries(folders, "", false, false, "*.txt", FileCategory.ALL)
        assertEquals(listOf("Download", "Pictures"), visible.map { it.name })
        assertEquals("0 个匹配文件 · 2 个目录保留", browserEntryCountLabel(visible, "", "*.txt", FileCategory.ALL))
    }

    @Test fun mixedDirectoryFilterCountsOnlyMatchingFilesAndKeepsNavigation() {
        val directory = entry("images", true)
        val text = entry("note.txt")
        val filtered = filterBrowserEntries(listOf(directory, text, entry("image.jpg")), "", false, false,
            "*.txt", FileCategory.ALL)
        assertEquals(listOf(directory, text), filtered)
        assertEquals("1 个匹配文件 · 1 个目录保留", browserEntryCountLabel(filtered, "", "*.txt", FileCategory.ALL))
        val typeFiltered = filterBrowserEntries(listOf(directory, text), "", false, false, "", FileCategory.IMAGE)
        assertEquals(listOf(directory), typeFiltered)
        assertEquals("0 个匹配文件 · 1 个目录保留", browserEntryCountLabel(typeFiltered, "", "", FileCategory.IMAGE))
    }

    @Test fun explicitNameQueryStillFiltersDirectoryNamesAndHasMatchSemantics() {
        val filtered = filterBrowserEntries(listOf(entry("notes", true), entry("pictures", true)),
            "note", false, false, "", FileCategory.ALL)
        assertEquals(listOf("notes"), filtered.map { it.name })
        assertEquals("1 项匹配", browserEntryCountLabel(filtered, "note", "", FileCategory.ALL))
    }

    @Test fun clearingOrLeavingSearchCannotDisplayAnOldRecursiveEmptyPage() {
        val oldEmptyPage = FileSearchResult(emptyList(), null, 0)
        assertNull(activeFileSearchResult(oldEmptyPage, true, ""))
        assertNull(activeFileSearchResult(oldEmptyPage, true, "   "))
        assertNull(activeFileSearchResult(oldEmptyPage, false, "note"))
        val browsing = filterBrowserEntries(listOf(entry("notes", true), entry("note.txt")),
            "", false, false, "", FileCategory.ALL)
        assertEquals(2, browsing.size)
    }

    @Test fun refreshWithAnUnchangedNonemptyQueryKeepsThePublishedResultIdentity() {
        val published = FileSearchResult(listOf(entry("note.txt")), null, 0)
        assertSame(published, activeFileSearchResult(published, true, "note"))
        val nextPage = FileSearchResult(listOf(entry("note2.txt")), null, 0)
        val merged = mergeFileSearchResults(published, nextPage)
        assertEquals(listOf("note.txt", "note2.txt"), activeFileSearchResult(merged, true, "note")?.entries?.map { it.name })
    }

    @Test fun onlyLocalDirectoryOrTreeLocationsSelectObservationBackends() {
        assertEquals(DirectoryObservationSource.LOCAL, directoryObservationSource(root))
        val tree = BrowserLocation("tree", rootUri = "content://provider/tree/root", documentId = "root:child")
        assertEquals(DirectoryObservationSource.PROVIDER, directoryObservationSource(tree))
        assertEquals(DirectoryObservationSource.NONE, directoryObservationSource(root.copy(rootAccess = true)))
        assertEquals(DirectoryObservationSource.NONE, directoryObservationSource(tree.copy(documentId = null)))
        assertEquals(DirectoryObservationSource.NONE, directoryObservationSource(BrowserLocation("single",
            singleDocumentUri = "content://provider/document/file")))
    }

    @Test fun wrappedSelectionToolbarReservesItsActualHeightBeforeAllocatingTools() {
        val window = 420f
        val actualToolbar = 128f
        val editor = 44f
        val row = 48f
        val tools = fileBrowserControlsHeightDp(window, true, row, editor, actualToolbar)
        assertTrue(window - editor - tools - actualToolbar >= 3f * row + 16f)
        assertTrue(tools < fileBrowserControlsHeightDp(window, true, row, editor, 56f))
    }

    @Test fun narrowWindowsAndLargeTextUseCompactChromeWithoutLosingToolbarActions() {
        assertFalse(fileBrowserNarrowChrome(400f, 1f))
        assertTrue(fileBrowserNarrowChrome(183f, 1f))
        assertTrue(fileBrowserNarrowChrome(400f, 1.5f))
        assertEquals(4, fileBrowserToolbarColumns(183f, 1f, 4))
        assertEquals(2, fileBrowserToolbarColumns(183f, 1.3f, 4))
        assertEquals(1, fileBrowserToolbarColumns(100f, 2f, 1))
        val actions = listOf("位置", "新建", "收起查找", "更多")
        assertEquals(actions, actions.chunked(fileBrowserToolbarColumns(183f, 1.3f, actions.size)).flatten())
    }
}
