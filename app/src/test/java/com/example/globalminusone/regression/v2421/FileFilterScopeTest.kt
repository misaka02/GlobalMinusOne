package com.example.globalminusone.regression.v2421

import com.example.globalminusone.system.*
import com.example.globalminusone.ui.panel.file.fileBrowserEmptyMessage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import com.example.globalminusone.system.file.BrowserEntry
import com.example.globalminusone.system.file.BrowserLocation
import com.example.globalminusone.system.file.FileCategory
import com.example.globalminusone.system.file.location
import com.example.globalminusone.system.file.matchesQuickFilter
import com.example.globalminusone.system.file.searchFileTree

class FileFilterScopeTest {
    private val root = BrowserLocation("存储", path = "/storage/emulated/0")
    private val download = BrowserLocation("Download", path = "${root.path}/Download")
    private val deep = BrowserLocation("Deep", path = "${download.path}/Deep")
    private fun folder(location: BrowserLocation) = BrowserEntry(location, location.title, true,
        "vnd.android.document/directory", null, null)

    @Test fun correctDirectoryScopeMatchesDirectoryNamesAndRecursiveSearchCanFindThem() = runBlocking {
        val list: suspend (BrowserLocation) -> List<BrowserEntry> = { here -> when (here.key) {
            root.key -> listOf(folder(download))
            download.key -> listOf(folder(deep))
            else -> emptyList()
        } }
        assertFalse(matchesQuickFilter("Download", "Deep"))
        assertTrue(matchesQuickFilter("Deep", "Deep"))
        val local = searchFileTree(root, "Deep", FileCategory.ALL, permissionsNow = { "read" },
            checkRead = {}, list = list, onProgress = {}, includeSubdirectories = false)
        assertTrue(local.entries.isEmpty())
        val recursive = searchFileTree(root, "Deep", FileCategory.ALL, permissionsNow = { "read" },
            checkRead = {}, list = list, onProgress = {}, includeSubdirectories = true)
        assertEquals(deep, recursive.entries.single().location)
        assertEquals(download, recursive.entries.single().parentLocation)
    }

    @Test fun filteredEmptyDirectoryDoesNotFalselyClaimAllItsFilesAreHidden() {
        val rows = listOf(folder(download))
        val filtered = fileBrowserEmptyMessage(rows, false, "", "Deep", FileCategory.ALL, false)
        assertTrue(filtered.contains("当前目录"))
        assertTrue(filtered.contains("包含子目录"))
        assertFalse(filtered.contains("只有隐藏"))
        assertTrue(fileBrowserEmptyMessage(rows, false, "", "Deep", FileCategory.ALL, true).contains("当前查找结果"))
        assertEquals("此目录为空", fileBrowserEmptyMessage(emptyList(), false, "", "", FileCategory.ALL, false))
    }
}
