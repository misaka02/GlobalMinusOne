package com.example.globalminusone.regression.v2436

import com.example.globalminusone.data.FileBrowserSortOrder
import com.example.globalminusone.system.*
import com.example.globalminusone.ui.panel.file.FileWorkspacePathPolicy
import com.example.globalminusone.ui.panel.file.fileBrowserControlsHeightDp
import com.example.globalminusone.ui.panel.file.resolveFileWorkspacePath
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import com.example.globalminusone.system.file.BrowserEntry
import com.example.globalminusone.system.file.BrowserLocation
import com.example.globalminusone.system.file.FileCategory
import com.example.globalminusone.system.file.entry
import com.example.globalminusone.system.file.filterBrowserEntries
import com.example.globalminusone.system.file.location
import com.example.globalminusone.system.file.searchFileTree
import com.example.globalminusone.system.file.sortBrowserEntries

class R7FileBrowserRegressionTest {
    private val root = BrowserLocation("shared", path = "/storage/emulated/0")
    private fun entry(name: String, dir: Boolean = false, parent: BrowserLocation = root) = BrowserEntry(
        BrowserLocation(name, path = "${parent.path}/$name"), name, dir,
        if (dir) "vnd.android.document/directory" else "audio/mpeg", 12L, 50L)

    @Test fun filenameSearchDoesNotRetainUnmatchedNavigationDirectories() {
        val entries = listOf(entry("Alarms", true), entry("song1.mp3"), entry("song2.mp3"))
        assertEquals(listOf("song1.mp3"), filterBrowserEntries(entries, "song1", false, false, "", FileCategory.ALL).map { it.name })
        assertEquals(listOf("song1.mp3"), filterBrowserEntries(entries, "song1\\.mp3$", true, false, "", FileCategory.ALL).map { it.name })
        assertEquals(emptyList<BrowserEntry>(), filterBrowserEntries(entries, "[", true, false, "", FileCategory.ALL))
        assertEquals(listOf("Alarms", "song1.mp3", "song2.mp3"), filterBrowserEntries(entries, "", false, false, "*.mp3", FileCategory.AUDIO).map { it.name })
    }

    @Test fun recursiveTraversalCanVisitUnmatchedDirectoriesWithoutReturningThem() = runBlocking<Unit> {
        val folder = entry("Alarms", true)
        val result = searchFileTree(root, "song1", FileCategory.ALL, null, { "same-read-grant" }, {},
            { if (it == root) listOf(folder, entry("song2.mp3")) else listOf(entry("song1.mp3", parent = folder.location)) }, {}, true)
        assertEquals(listOf("song1.mp3"), result.entries.map { it.name })
        assertEquals(folder.location, result.entries.single().parentLocation)
    }

    @Test fun descendingNameOrderIsAppliedInsideBothDirectoryAndFileGroups() {
        val entries = listOf(entry("Alarms", true), entry("Zebra", true), entry("song1.mp3"), entry("song9.mp3"))
        assertEquals(listOf("Zebra", "Alarms", "song9.mp3", "song1.mp3"), sortBrowserEntries(entries, FileBrowserSortOrder.NAME, true).map { it.name })
        assertEquals(listOf("Alarms", "Zebra", "song1.mp3", "song9.mp3"), sortBrowserEntries(entries, FileBrowserSortOrder.NAME, false).map { it.name })
    }

    @Test fun rootPagePathInputUsesAnAvailableRootAndStillValidatesFinalDirectory() = runBlocking<Unit> {
        val child = entry("Music", true)
        val calls = mutableListOf<String>()
        val plan = FileWorkspacePathPolicy.plan("/storage/emulated/0/Music", emptyList(), listOf(root))
        val resolved = resolveFileWorkspacePath(plan) { calls += it.key; if (it == root) listOf(child) else emptyList() }
        assertEquals(listOf(root, child.location), resolved)
        assertEquals(listOf(root.key, child.location.key), calls)
        assertEquals(emptyList<String>(), FileWorkspacePathPolicy.plan(root.path!!, emptyList(), listOf(root)).segments)
        assertThrows(IllegalArgumentException::class.java) { FileWorkspacePathPolicy.plan("/data", emptyList(), listOf(root)) }
        assertThrows(IllegalArgumentException::class.java) { FileWorkspacePathPolicy.plan("Music", emptyList(), listOf(root)) }
        assertThrows(IllegalArgumentException::class.java) { FileWorkspacePathPolicy.plan("/storage/emulated/0/../data", emptyList(), listOf(root)) }
    }

    @Test fun controlsCannotUseTheThreeRowBudgetAtNormalWindowSizes() {
        for (height in listOf(240f, 260f, 340f, 420f)) {
            assertTrue(height - fileBrowserControlsHeightDp(height, false) >= 132f)
            assertTrue(height - fileBrowserControlsHeightDp(height, true) - 56f >= 132f)
        }
        assertEquals(0f, fileBrowserControlsHeightDp(0f, false), 0f)
        assertTrue(fileBrowserControlsHeightDp(80f, false) < 80f)
    }
}
