package com.example.globalminusone.data

import android.content.SharedPreferences
import com.example.globalminusone.MemoryPreferences
import org.junit.Assert.*
import org.junit.Test

class PageOperationsTest {
    @Test fun newPagesGenerateOperationBarIdBeforePageIdAndKeepOriginalDefaultsAndNames() {
        for (type in PageLayoutType.entries) {
            val ids = mutableListOf<String>()
            val page = PageOperations.createPage(type, "  ", 4) { "id-${ids.size}".also { ids.add(it) } }
            assertEquals(listOf("id-0", "id-1"), ids)
            assertEquals("id-1", page.id)
            assertEquals("${type.label} 4", page.name)
            val bar = page.components.single()
            assertEquals("id-0", bar.id)
            assertEquals(ComponentType.OPERATION_BAR, bar.type)
            assertEquals("操作栏", bar.title)
            assertFalse(bar.showTitle)
            assertEquals(4, bar.h)
            assertEquals(if (type == PageLayoutType.FREE) 30 else 32, bar.w)
            if (type == PageLayoutType.FREE) { assertEquals(1, bar.x); assertEquals(44, bar.y) }
            if (type == PageLayoutType.GRID) { assertEquals(GRID_SPAN_FULL_ROW, bar.gridSpanX); assertEquals(1, bar.gridSpanY) }
        }
        assertEquals("  named  ", PageOperations.createPage(PageLayoutType.FREE, "  named  ", 1) { "id" }.name)
    }

    @Test fun deletingPagesRetainsOneAndAdjustsActiveIndexForEveryRelativePosition() {
        val pages = pages()
        for ((active, removed, expected) in listOf(Triple(2, 2, 1), Triple(1, 1, 1), Triple(2, 0, 1), Triple(0, 2, 0))) {
            val result = PageOperations.delete(pages, active, removed)!!
            assertEquals(expected, result.activeIndex)
            assertEquals(pages.filterIndexed { index, _ -> index != removed }, result.pages)
        }
        assertNull(PageOperations.delete(pages.take(1), 0, 0))
        assertNull(PageOperations.delete(pages, 0, -1))
        assertNull(PageOperations.delete(pages, 0, 3))
        val repository = repository()
        repository.selectComponent("selected")
        assertTrue(repository.deletePage(1))
        assertNull(repository.state.value.selectedComponentId)
    }

    @Test fun swapsFollowMovedPageRatherThanOldActivePageAndPreserveSelection() {
        val repository = repository()
        repository.setCurrentPageIndex(0)
        repository.selectComponent("selected")
        assertTrue(repository.swapPageLeft(2))
        assertEquals(listOf("p0", "p2", "p1"), repository.state.value.pages.map { it.id })
        assertEquals(1, repository.state.value.currentPageIndex)
        assertEquals("selected", repository.state.value.selectedComponentId)
        repository.setCurrentPageIndex(2)
        assertNull(repository.state.value.selectedComponentId)
        repository.selectComponent("selected")
        assertTrue(repository.swapPageRight(0))
        assertEquals(listOf("p2", "p0", "p1"), repository.state.value.pages.map { it.id })
        assertEquals(1, repository.state.value.currentPageIndex)
        assertEquals("selected", repository.state.value.selectedComponentId)
        assertFalse(repository.swapPageLeft(0))
        assertFalse(repository.swapPageRight(2))
    }

    @Test fun transformsRunOnceOnlyForValidUnlockedIndexAndRejectDuplicateComponentTrees() {
        val repository = repository()
        var calls = 0
        assertFalse(repository.updatePageConfig(-1) { calls++; it })
        assertTrue(repository.updatePageConfig(0) { calls++; it })
        assertEquals(1, calls)
        val before = repository.state.value
        assertFalse(repository.updatePageConfig(1) { calls++; it.copy(components = before.pages[0].components) })
        assertEquals(2, calls)
        assertEquals(before, repository.state.value)
        val thrown = IllegalStateException("transform failure must propagate")
        try {
            repository.updatePageConfig(0) { throw thrown }
            fail("Transform exceptions must not become false validation results")
        } catch (expected: IllegalStateException) {
            assertSame(thrown, expected)
        }
        assertEquals(before, repository.state.value)
        assertTrue(repository.setLayoutLocked(true))
        assertFalse(repository.updatePageConfig(0) { calls++; it })
        assertEquals(2, calls)
        assertEquals(-1, repository.addPage(PageLayoutType.GRID))
        assertEquals(-1, repository.addPagePreset(PagePresetOption.CONTROL_CENTER))
    }

    @Test fun gridStylePreservesInheritedAndExplicitZeroWhileClampingOtherBoundaries() {
        val page = pages()[0].copy(gridRowGapDp = -1)
        val bounded = PageOperations.gridStyle(page, 999, -999, 99, 999, 0)
        assertEquals(160, bounded.gridItemSizeDp)
        assertEquals(-24, bounded.gridItemGapDp)
        assertEquals(12, bounded.gridColumnCount)
        assertEquals(192, bounded.gridIconSizeDp)
        assertEquals(0, bounded.gridRowGapDp)
        assertEquals(-1, PageOperations.gridStyle(page).gridRowGapDp)
        assertEquals(ROW_GAP_INHERIT, PageOperations.gridStyle(page, rowGapDp = ROW_GAP_INHERIT).gridRowGapDp)
        val lower = PageOperations.gridStyle(page, -1, 999, -1, -1, -9999)
        assertEquals(44, lower.gridItemSizeDp)
        assertEquals(48, lower.gridItemGapDp)
        assertEquals(0, lower.gridColumnCount)
        assertEquals(0, lower.gridIconSizeDp)
        assertEquals(-24, lower.gridRowGapDp)
        assertEquals(48, PageOperations.gridStyle(page, rowGapDp = 999).gridRowGapDp)
    }

    @Test fun freeSourceSortIsStableByYThenXAndOnlyTargetWidthsChange() {
        val late = component("late", 2, 2, 40).copy(
            type = ComponentType.CONTAINER_BOX,
            containerComponents = listOf(component("nested", 7, 8, 9)),
        )
        val equalA = component("equal-a", 1, 1, 7)
        val equalB = component("equal-b", 1, 1, 9)
        val first = component("first", 0, 1, 4)
        val source = pages()[0].copy(layoutType = PageLayoutType.FREE, components = listOf(late, equalA, equalB, first))
        val linear = PageOperations.convertLayout(source, PageLayoutType.LINEAR, 20)
        assertEquals(listOf(first, equalA, equalB, late).map { it.copy(w = 20) }, linear.components)
        val grid = PageOperations.convertLayout(source, PageLayoutType.GRID, 20)
        assertEquals(listOf(first, equalA, equalB, late.copy(w = 20)), grid.components)
    }

    @Test fun conversionsToFreeStackWithOneUnitGapAndLinearToGridUsesNaturalWidth() {
        val a = component("a", 4, 20, 3).copy(h = 3)
        val b = component("b", 8, 5, 99).copy(h = 0)
        val grid = pages()[0].copy(layoutType = PageLayoutType.GRID, components = listOf(a, b))
        val free = PageOperations.convertLayout(grid, PageLayoutType.FREE, 20)
        assertEquals(a.copy(x = 0, y = 0), free.components[0])
        assertEquals(b.copy(x = 0, y = 4, w = b.type.defaultW.coerceIn(1, 20), h = b.type.defaultH.coerceAtLeast(2)), free.components[1])
        val linear = grid.copy(layoutType = PageLayoutType.LINEAR)
        val linearFree = PageOperations.convertLayout(linear, PageLayoutType.FREE, 20)
        assertEquals(a.type.defaultW.coerceIn(1, 20), linearFree.components[0].w)
        val natural = PageOperations.convertLayout(linear, PageLayoutType.GRID, 20)
        assertEquals(listOf(a, b).map { it.copy(w = it.type.defaultW.coerceIn(1, 20)) }, natural.components)
    }

    @Test fun fitAndPresetPathsRetainTheirNamesCountsAndExistingFitPolicy() {
        val repository = repository()
        val plain = repository.addPage(PageLayoutType.GRID, "  plain  ")
        assertEquals("  plain  ", repository.state.value.pages[plain].name)
        val preset = repository.addPagePreset(PagePresetOption.CONTROL_CENTER, "  preset  ")
        assertEquals("preset", repository.state.value.pages[preset].name)
        val fit = fitPageGridColumns(320f, 4)
        repository.fitPageGridColumnCount(plain, 4, 320f)
        val fitted = repository.state.value.pages[plain]
        assertEquals(fit.itemSizeDp, fitted.gridItemSizeDp)
        assertEquals(fit.itemGapDp, fitted.gridItemGapDp)
        assertEquals(fit.columnCount, fitted.gridColumnCount)
        assertEquals(fit.iconSizeDp, fitted.gridIconSizeDp)
        repository.fitPageGridColumnCount(plain, 0, 320f)
        assertEquals(0, repository.state.value.pages[plain].gridColumnCount)
    }

    @Test fun rejectedWritesKeepOriginalStateAndReturnFailureForOtherwiseValidOperations() {
        val prefs = RejectingPreferences(initialState())
        val repository = MinusOneRepository(prefs)
        val before = repository.state.value
        prefs.reject = true
        assertEquals(-1, repository.addPage(PageLayoutType.GRID))
        assertFalse(repository.deletePage(1))
        assertFalse(repository.swapPageRight(0))
        assertFalse(repository.updatePageConfig(0) { it.copy(name = "rejected") })
        assertEquals(before, repository.state.value)
        assertNotNull(repository.persistenceError.value)
    }

    @Test fun editSaveAndDiscardReloadCorrectPagesAndSameLayoutDoesNotWriteOrReorder() {
        val prefs = MemoryPreferences(mapOf("state_json_v5" to ConfigurationCodec.encode(initialState())))
        val repository = MinusOneRepository(prefs)
        val original = repository.state.value
        val writes = prefs.writeCount
        repository.setPageLayoutType(0, original.pages[0].layoutType)
        assertEquals(writes, prefs.writeCount)
        assertTrue(repository.setEditMode(true))
        repository.addPage(PageLayoutType.GRID, "discard")
        assertTrue(repository.finishPanelEdit(false))
        assertEquals(original.pages, MinusOneRepository(prefs).state.value.pages)
        assertTrue(repository.setEditMode(true))
        val index = repository.addPage(PageLayoutType.LINEAR, "saved")
        repository.togglePageLayoutType(index)
        assertEquals(PageLayoutType.GRID, repository.state.value.pages[index].layoutType)
        repository.togglePageLayoutType(index)
        assertEquals(PageLayoutType.FREE, repository.state.value.pages[index].layoutType)
        repository.togglePageLayoutType(index)
        assertEquals(PageLayoutType.LINEAR, repository.state.value.pages[index].layoutType)
        assertTrue(repository.finishPanelEdit(true))
        assertEquals(repository.state.value.pages, MinusOneRepository(prefs).state.value.pages)
    }

    private fun component(id: String, x: Int, y: Int, w: Int) = ComponentItem(
        id = id, type = ComponentType.APP_ICON, title = "title-$id", x = x, y = y, w = w, h = 3,
        textContent = "content-$id", gridSpanX = 2, gridSpanY = 3, gridCol = 4, gridRow = 5,
    )
    private fun pages() = (0..2).map { index -> PageConfig(id = "p$index", name = "page-$index",
        components = if (index == 0) listOf(component("selected", 1, 2, 8)) else emptyList()) }
    private fun initialState() = GlobalMinusOneState(pages = pages())
    private fun repository() = MinusOneRepository(MemoryPreferences(mapOf("state_json_v5" to ConfigurationCodec.encode(initialState()))))
    private class RejectingPreferences(state: GlobalMinusOneState,
        private val backing: MemoryPreferences = MemoryPreferences(mapOf("state_json_v5" to ConfigurationCodec.encode(state)))) : SharedPreferences by backing {
        var reject = false
        override fun edit(): SharedPreferences.Editor {
            if (reject) throw IllegalStateException("write rejected")
            return backing.edit()
        }
    }
}
