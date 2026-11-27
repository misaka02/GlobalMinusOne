package com.example.globalminusone

import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.PageConfig
import com.example.globalminusone.data.PageLayoutType
import com.example.globalminusone.data.operations.PageOperations
import com.example.globalminusone.data.operations.SubPanelConfig
import com.example.globalminusone.data.operations.SubPanelNavStack
import com.example.globalminusone.data.ThemeConfig
import com.example.globalminusone.data.operations.applyGeometryToSubPanel
import com.example.globalminusone.data.backup.BackupExportMode
import com.example.globalminusone.data.backup.BackupImportSelection
import com.example.globalminusone.data.backup.NativeBackupCodec
import com.example.globalminusone.data.backup.PageImportMode
import com.example.globalminusone.data.backup.analyzeBackupCompatibility
import com.example.globalminusone.data.backup.mergeSelectedBackupState
import com.example.globalminusone.data.backup.scopeStateForExport
import com.example.globalminusone.data.operations.resolveSubPanelTheme
import com.example.globalminusone.data.policy.resolveVisibleGridRows
import com.example.globalminusone.service.overlay.resolveEdgeTriggerAlpha
import com.example.globalminusone.system.media.VolumeWriteCoalescer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelV24112FeaturesTest {

    @Test
    fun volumeWriteCoalescerDrainsWithoutDeadlockAndConvergesToLatest() {
        val coalescer = VolumeWriteCoalescer()
        assertTrue(coalescer.enqueue(20))
        assertFalse(coalescer.enqueue(55))
        assertEquals(55, coalescer.pollNext())

        // Interleaved update arrives right before the drain pass finishes.
        assertFalse(coalescer.enqueue(82))
        assertTrue(coalescer.finishPassAndCheckRestart())
        assertEquals(82, coalescer.pollNext())

        // Once idle again, next enqueue starts a fresh drain instead of staying locked.
        assertFalse(coalescer.finishPassAndCheckRestart())
        assertTrue(coalescer.enqueue(37))
        assertEquals(37, coalescer.pollNext())
        assertFalse(coalescer.finishPassAndCheckRestart())
    }

    @Test
    fun edgeTriggerAlphaIsStrictlyZeroWhenTouchVisibleIsFalse() {
        assertEquals(
            0f,
            resolveEdgeTriggerAlpha(
                touchVisible = false,
                touchAlpha = 0.85f,
                isTouched = true,
                isSecondSlideHighlighted = true
            ),
            0.0001f
        )
        assertEquals(
            1.0f,
            resolveEdgeTriggerAlpha(
                touchVisible = true,
                touchAlpha = 0.4f,
                isTouched = true,
                isSecondSlideHighlighted = true
            ),
            0.0001f
        )
        assertEquals(
            0.35f,
            resolveEdgeTriggerAlpha(
                touchVisible = true,
                touchAlpha = 0.35f,
                isTouched = false,
                isSecondSlideHighlighted = false
            ),
            0.0001f
        )
    }

    @Test
    fun enteringEditModeDoesNotAutoSelectCardAndGridRowsEliminateBlankTrailingRows() {
        val page = PageConfig(
            id = "p-main",
            name = "主页",
            layoutType = PageLayoutType.GRID,
            gridRowCount = 0,
            gridAutoRows = true,
            components = listOf(
                ComponentItem(id = "c1", type = ComponentType.APP_ICON, title = "A"),
                ComponentItem(id = "c2", type = ComponentType.VOLUME_BAR, title = "B")
            )
        )
        val repo = MinusOneRepository(MemoryPreferences())
        assertTrue(repo.setEditMode(true))
        assertNull(repo.state.value.selectedComponentId)

        // Adaptive rows: exact occupied rows with zero empty trailing rows when idle.
        assertEquals(2, resolveVisibleGridRows(occupiedRows = 2, configuredRowCount = 0, autoRows = true, isDragging = false))
        assertEquals(3, resolveVisibleGridRows(occupiedRows = 2, configuredRowCount = 0, autoRows = true, isDragging = true))

        // Manual row count enforces minimum rows while auto-expanding on overflow.
        assertEquals(4, resolveVisibleGridRows(occupiedRows = 2, configuredRowCount = 4, autoRows = false, isDragging = false))
        assertEquals(6, resolveVisibleGridRows(occupiedRows = 6, configuredRowCount = 4, autoRows = false, isDragging = false))

        // Repository updates manual row count and auto-rows flag cleanly.
        assertTrue(repo.updatePageGridStyle(0, rowCount = 4, autoRows = false))
        assertEquals(4, repo.state.value.pages[0].gridRowCount)
        assertFalse(repo.state.value.pages[0].gridAutoRows)
        assertEquals("p-main", page.id)
    }

    @Test
    fun pageOperationsStayIsolatedWithinOwnerPanelScope() {
        val root1 = PageConfig(id = "r1", name = "Root1", subPanelId = "")
        val sub1 = PageConfig(id = "s1", name = "Sub1", subPanelId = "sub-a")
        val root2 = PageConfig(id = "r2", name = "Root2", subPanelId = "")

        val swapped = PageOperations.swapRight(listOf(root1, sub1, root2), pageIndex = 0)
        assertNotNull(swapped)
        assertEquals(listOf("r2", "s1", "r1"), swapped!!.pages.map { it.id })
        assertEquals(2, swapped.activeIndex)

        // Cannot delete sole page inside sub-a, nor swap sub-a across root boundary.
        assertNull(PageOperations.swapLeft(listOf(root1, sub1, root2), pageIndex = 1))
        assertNull(PageOperations.delete(listOf(root1, sub1, root2), activeIndex = 1, pageIndex = 1))

        val deletedRoot = PageOperations.delete(listOf(root1, sub1, root2), activeIndex = 0, pageIndex = 0)
        assertNotNull(deletedRoot)
        // Active index lands on remaining root page ("r2"), not "s1" from another sub-panel.
        assertEquals("r2", deletedRoot!!.pages[deletedRoot.activeIndex].id)
    }

    @Test
    fun subPanelCrudNavigationAndBackupRoundTripWorkCleanly() {
        val repo = MinusOneRepository(MemoryPreferences())
        val subA = repo.createSubPanel("工具抽屉")
        val subB = repo.createSubPanel("快捷嵌套")
        assertNotNull(subA)
        assertNotNull(subB)

        val rootPageId = repo.state.value.pages.first { it.subPanelId.isEmpty() }.id
        val entryToA = ComponentItem(type = ComponentType.SUB_PANEL_ENTRY, title = "工具抽屉", subPanelId = subA!!.id)
        assertTrue(repo.placeMenuComponent(rootPageId, null, entryToA, null))

        val subAPage = repo.state.value.pages.first { it.subPanelId == subA.id }
        val nestedToB = ComponentItem(type = ComponentType.SUB_PANEL_ENTRY, title = "快捷嵌套", subPanelId = subB!!.id)
        assertTrue(repo.placeMenuComponent(subAPage.id, null, nestedToB, null))

        // Stack navigation avoids cycles by unwinding to existing ancestor and enforces depth cap.
        val validIds = repo.state.value.subPanels.mapTo(HashSet()) { it.id }
        var stack = SubPanelNavStack()
        stack = stack.push(subA.id, validIds)
        stack = stack.push(subB.id, validIds)
        assertEquals(listOf(subA.id, subB.id), stack.stack)
        stack = stack.push(subA.id, validIds)
        assertEquals(listOf(subA.id), stack.stack)
        assertTrue(stack.pop().currentId == null)

        // Geometry inheritance vs override.
        val mainTheme = ThemeConfig(panelWidthPercent = 72, panelHeightPercent = 96, isDarkMode = false, followSystemDark = true)
        assertEquals(72, resolveSubPanelTheme(mainTheme, subA).panelWidthPercent)
        val detached = applyGeometryToSubPanel(
            subA.copy(inheritGeometry = false, inheritAppearance = false, isDarkMode = true, panelBgAlphaPercent = 80),
            mainTheme.copy(panelWidthPercent = 52)
        )
        val resolvedDetached = resolveSubPanelTheme(mainTheme, detached)
        assertEquals(52, resolvedDetached.panelWidthPercent)
        assertTrue(resolvedDetached.isDarkMode)
        assertFalse(resolvedDetached.followSystemDark)
        assertTrue(repo.updateSubPanel(subA.id) { detached })

        // State validation accepts sub-panels, SUB_PANEL_ENTRY cards, and row configs.
        NativeBackupCodec.validateState(repo.state.value)

        // Deleting subA clears both its pages and SUB_PANEL_ENTRY links on remaining pages.
        assertTrue(repo.deleteSubPanel(subA.id))
        assertTrue(repo.state.value.subPanels.none { it.id == subA.id })
        assertTrue(repo.state.value.pages.none { it.subPanelId == subA.id })
        assertTrue(repo.state.value.pages.flatMap { it.components }.none { it.subPanelId == subA.id })
    }

    @Test
    fun selectiveBackupExportCompatibilityAndMergePreserveIntegrity() {
        val sp = SubPanelConfig(id = "sp-1", name = "子页", inheritGeometry = false, panelWidthPercent = 88)
        val pSub = PageConfig(
            id = "p-sub",
            name = "子面板页",
            subPanelId = sp.id,
            components = listOf(ComponentItem(id = "sub-card", type = ComponentType.VOLUME_BAR))
        )
        val pMain1 = PageConfig(
            id = "p-main-1",
            name = "主一",
            layoutType = PageLayoutType.GRID,
            gridColumnCount = 6,
            components = listOf(
                ComponentItem(id = "entry-1", type = ComponentType.SUB_PANEL_ENTRY, subPanelId = sp.id)
            )
        )
        val pMain2 = PageConfig(id = "p-main-2", name = "主二")
        val incoming = GlobalMinusOneState(
            themeConfig = ThemeConfig(panelWidthPercent = 82, panelHeightPercent = 95, isDarkMode = true),
            pages = listOf(pMain1, pMain2, pSub),
            subPanels = listOf(sp),
            currentPageIndex = 0
        )
        val current = GlobalMinusOneState(
            themeConfig = ThemeConfig(panelWidthPercent = 48, panelHeightPercent = 70, isDarkMode = false),
            pages = listOf(PageConfig(id = "cur-1", name = "当前页")),
            currentPageIndex = 0
        )

        val issues = analyzeBackupCompatibility(incoming, current)
        assertTrue(issues.any { it.title.contains("宽度") })
        assertTrue(issues.any { it.title.contains("高度") })
        assertTrue(issues.any { it.title.contains("多列网格") })

        // Selected-page export pulls transitively referenced sub-panel and its owned pages.
        val scopedPages = scopeStateForExport(incoming, BackupExportMode.SELECTED_PAGES, setOf(pMain1.id))
        assertEquals(listOf(pMain1.id, pSub.id), scopedPages.pages.map { it.id })
        assertEquals(listOf(sp.id), scopedPages.subPanels.map { it.id })

        // Selective append remaps IDs, preserves host geometry when requested, and keeps valid state.
        val mergedAppend = mergeSelectedBackupState(
            current = current,
            incoming = incoming,
            selection = BackupImportSelection(
                importThemeAndPanel = true,
                importTriggersAndBall = false,
                importBrowserData = false,
                importProfiles = false,
                selectedPageIds = setOf(pMain1.id),
                pageImportMode = PageImportMode.APPEND,
                applySourcePanelGeometry = false
            )
        )
        NativeBackupCodec.validateState(mergedAppend)
        assertEquals(48, mergedAppend.themeConfig.panelWidthPercent)
        assertTrue(mergedAppend.themeConfig.isDarkMode)
        assertEquals(3, mergedAppend.pages.size)
        assertEquals(1, mergedAppend.subPanels.size)
        assertNotEquals(sp.id, mergedAppend.subPanels.first().id)

        // Replacing current page with a sub-panel page avoids leaving an orphan empty sub-panel.
        val mergedReplaceSub = mergeSelectedBackupState(
            current = current,
            incoming = incoming,
            selection = BackupImportSelection(
                importThemeAndPanel = false,
                importTriggersAndBall = false,
                importBrowserData = false,
                importProfiles = false,
                selectedPageIds = setOf(pSub.id),
                pageImportMode = PageImportMode.REPLACE_CURRENT,
                applySourcePanelGeometry = false
            )
        )
        NativeBackupCodec.validateState(mergedReplaceSub)
        assertEquals(1, mergedReplaceSub.pages.size)
        assertTrue(mergedReplaceSub.subPanels.isEmpty())
    }
}
