package com.example.globalminusone.regression

import com.example.globalminusone.system.file.BrowserEntry
import com.example.globalminusone.system.file.BrowserLocation
import com.example.globalminusone.ui.panel.file.FileWorkspaceSelection
import com.example.globalminusone.ui.panel.file.FileWorkspaceTransferPlan
import com.example.globalminusone.ui.panel.file.TransferClipboard
import com.example.globalminusone.ui.panel.file.fileWorkspacePath
import com.example.globalminusone.ui.panel.file.fileWorkspaceClipboardTitle
import com.example.globalminusone.ui.panel.file.FileBrowserReturnRefreshState
import org.junit.Assert.*
import org.junit.Test

class FileWorkspaceLogicTest {
    @Test fun closingPreviewRequestsOneFreshListingWithoutLifecycleResume() {
        val refresh = FileBrowserReturnRefreshState(true)
        assertFalse(refresh.update(true)) // ordinary recomposition retains the cache
        assertFalse(refresh.update(false)) // preview opens; observation is suspended
        assertFalse(refresh.update(false)) // switching preview documents stays suspended
        assertTrue(refresh.update(true)) // external additions/deletions must now be listed
        assertFalse(refresh.update(true))
    }

    @Test fun closingPreviewWhileHiddenDefersAndCoalescesRefreshUntilPaneReturns() {
        val refresh = FileBrowserReturnRefreshState(true)
        assertFalse(refresh.update(false)) // preview opens
        assertFalse(refresh.update(false)) // panel hides
        assertFalse(refresh.update(false)) // preview closes while panel remains hidden
        assertTrue(refresh.update(true))
        assertFalse(refresh.update(true))
        assertFalse(refresh.update(false))
        assertTrue(refresh.update(true)) // a later independent gap still refreshes
    }

    @Test fun partialTransferTitleCountsOnlyRetryRecordsAndPreservesOriginalPlan() {
        val first = file("a.txt"); val second = file("b.txt"); val third = file("c.txt")
        val plan = FileWorkspaceTransferPlan.create(0, TransferClipboard(listOf(first, second, third), source, false), target)
        val remaining = plan.clipboard.entries.filterNot { it.location.key in setOf(first.location.key, second.location.key) }
        assertEquals(3, plan.clipboard.entries.size)
        assertEquals("A → B · 待处理 1 项 · 复制", fileWorkspaceClipboardTitle(plan.sourcePane, remaining.size, plan.clipboard.move))
        assertEquals("B → A · 待处理 3 项 · 移动", fileWorkspaceClipboardTitle(1, plan.clipboard.entries.size, true))
    }

    private val source = BrowserLocation("源", path = "/shared/source")
    private val target = BrowserLocation("目标", path = "/shared/target")
    private fun file(name: String, blocked: String? = null, root: Boolean = false) = BrowserEntry(
        BrowserLocation(name, path = "/shared/source/$name", rootAccess = root), name, false,
        "text/plain", 12, null, blockedReason = blocked, parentLocation = source)

    @Test fun firstLongPressedRecordIsIncludedInBatchWithoutASecondTap() {
        val first = file("first.txt")
        val selected = FileWorkspaceSelection().select(first)
        assertEquals(setOf(first.location.key), selected.keys)
        assertEquals(listOf(first), selected.transfer(source, false)!!.entries)
    }

    @Test fun switchingSidesDoesNotRequireRebuildingSelectionFromVisibleRows() {
        val first = file("a.txt"); val second = file("b.txt")
        val selected = FileWorkspaceSelection().select(first).select(second)
        val plan = FileWorkspaceTransferPlan.create(1, selected.transfer(source, true)!!, target)
        assertEquals(1, plan.sourcePane)
        assertEquals(0, plan.targetPane)
        assertTrue(plan.clipboard.move)
        assertEquals(listOf(first, second), plan.clipboard.entries)
    }

    @Test fun blockedRowsNeverBecomeOperableSelectionAndToggleRemovesTheSameRecord() {
        val usable = file("a.txt"); val blocked = file("private.txt", "不可访问")
        val selected = FileWorkspaceSelection().select(usable).select(blocked)
        assertEquals(setOf(usable.location.key), selected.keys)
        assertTrue(selected.toggle(usable).records.isEmpty())
        assertNull(selected.toggle(usable).transfer(source, false))
    }

    @Test fun partialTransferReceiptRetainsFailedAndUnprocessedRecords() {
        val first = file("a.txt"); val second = file("b.txt"); val third = file("c.txt")
        val selected = FileWorkspaceSelection().select(first).select(second).select(third)
        val retry = selected.without(setOf(first.location.key))
        assertEquals(listOf(second, third), retry.transfer(source, true)!!.entries)
    }

    @Test fun confirmationTakesAnEntrySnapshotAndKeepsTheOriginalTarget() {
        val original = file("original.txt")
        val mutableEntries = mutableListOf(original)
        val plan = FileWorkspaceTransferPlan.create(0, TransferClipboard(mutableEntries, source, false), target)
        mutableEntries.clear()
        assertEquals(listOf(original), plan.clipboard.entries)
        assertEquals(target, plan.destination)
    }

    @Test fun missingOrSameDirectoryTargetDoesNotCreateAnExecutablePlan() {
        val clip = TransferClipboard(listOf(file("a.txt")), source, false)
        assertThrows(IllegalArgumentException::class.java) { FileWorkspaceTransferPlan.create(0, clip, null) }
        assertThrows(IllegalArgumentException::class.java) { FileWorkspaceTransferPlan.create(0, clip, source) }
        assertThrows(IllegalArgumentException::class.java) { FileWorkspaceTransferPlan.create(0, clip.copy(entries = emptyList()), target) }
    }

    @Test fun rootModeAndProtectedRowsCannotBeSmuggledIntoCrossPanePlan() {
        val rootFile = file("root.txt", root = true)
        assertThrows(IllegalArgumentException::class.java) { FileWorkspaceTransferPlan.create(0, TransferClipboard(listOf(rootFile), source, false), target) }
        assertThrows(IllegalArgumentException::class.java) { FileWorkspaceTransferPlan.create(0, TransferClipboard(listOf(file("blocked.txt", "拒绝")), source, false), target) }
    }

    @Test fun nestedSearchResultsCanBeCopiedBackToTheSearchRoot() {
        val subfolder = BrowserLocation("子目录", path = "/shared/source/sub")
        val nested = file("found.txt").copy(parentLocation = subfolder)
        val plan = FileWorkspaceTransferPlan.create(0, TransferClipboard(listOf(nested), source, false), source)
        assertEquals(source, plan.destination)
        assertEquals(subfolder, plan.clipboard.entries.single().parentLocation)
    }

    @Test fun safTargetDisplaysTheDirectoryPathRatherThanOnlyItsLastName() {
        val saf = BrowserLocation("工作", rootUri = "content://example/tree/primary%3ADocuments", documentId = "primary:Documents/工作")
        assertEquals("/Documents/工作", fileWorkspacePath(saf))
        assertEquals("/shared/target", fileWorkspacePath(target))
    }
}
