package com.example.globalminusone.data

import android.content.SharedPreferences
import com.example.globalminusone.MemoryPreferences
import org.junit.Assert.*
import org.junit.Test
import com.example.globalminusone.data.operations.ComponentGridOperations
import com.example.globalminusone.data.storage.ConfigurationCodec
import com.example.globalminusone.data.policy.containerIconSizeDp
import com.example.globalminusone.data.policy.containerRowGapDp
import com.example.globalminusone.data.policy.resolveContainerGridDropPlacements
import com.example.globalminusone.data.policy.resolvePageGridDropPlacements

class ComponentGridOperationsTest {
    @Test fun pagePlacementAndNudgeKeepManualGapsAndAllOtherCardProperties() {
        val original = page()
        val placed = ComponentGridOperations.placeComponentInPageGrid(original, "a", 2, 4, 320f, 1f, 32, 0)
        assertEquals(card("a").copy(gridCol = 2, gridRow = 4), placed.components.first())
        val nudged = ComponentGridOperations.nudgeComponentInPageGrid(placed, "a", -1, 2, 320f, 1f, 32, 0)
        assertEquals(card("a").copy(gridCol = 1, gridRow = 6), nudged.components.first())
        assertEquals(original.copy(components = nudged.components), nudged)
        assertEquals(original.components.map { it.id }, nudged.components.map { it.id })
    }

    @Test fun pageMetricsRetainInheritedAndExplicitRowGapsAndCustomSizing() {
        for (gap in listOf(ROW_GAP_INHERIT, 0, 19)) for (fill in listOf(false, true)) {
            val source = page().copy(gridItemSizeDp = 83, gridItemGapDp = 11, gridRowGapDp = gap,
                gridColumnCount = 3, gridIconSizeDp = 47, gridFillGaps = fill)
            val expected = resolvePageGridDropPlacements(source.components, 301f,
                itemSizeDp = 83, itemGapDp = 11, gridColumns = 24, configuredHeightUnitDp = 9,
                fontScale = 1.4f, columnCount = 3, iconSizeDp = 47, fillGaps = fill, rowGapDp = gap,
                movedId = "a", targetCol = 2, targetRow = 5)
            assertEquals(expected, ComponentGridOperations.placeComponentInPageGrid(source, "a", 2, 5,
                301f, 1.4f, 24, 9).components)
        }
    }

    @Test fun pageCompactionUsesVisualRowThenColumnAndOnlyClearsPins() {
        val nested = card("late").copy(type = ComponentType.FOLDER, gridCol = 2, gridRow = 7,
            containerComponents = listOf(card("nested")), containerActions = listOf(action("legacy")))
        val first = card("first").copy(gridCol = 0, gridRow = 0)
        val second = card("second").copy(gridCol = 2, gridRow = 0)
        val source = page().copy(components = listOf(nested, second, first))
        val result = ComponentGridOperations.compactPageGridPlacements(source, 320f, 1f, 32, 0)
        assertEquals(listOf(first, second, nested).map { it.copy(gridCol = -1, gridRow = -1) }, result.components)
        assertEquals(source.copy(components = result.components), result)
    }

    @Test fun nestedContainerPlacementChangesOnlyTheTargetChildrenAndKeepsLegacyActions() {
        val repository = repository()
        val before = repository.state.value.pages[0]
        val target = before.components.single().containerComponents.single()
        assertTrue(repository.placeChildInContainerGrid(0, "inner", "a", 2, 4, 320f, 8f, 4, 1.2f))
        val root = repository.state.value.pages[0].components.single()
        val updated = root.containerComponents.single()
        assertEquals(2, updated.containerComponents.first().gridCol)
        assertEquals(4, updated.containerComponents.first().gridRow)
        assertEquals(target.containerActions, updated.containerActions)
        assertEquals(target.copy(containerComponents = updated.containerComponents), updated)
        assertEquals(before.components.single().copy(containerComponents = listOf(updated)), root)
    }

    @Test fun containerMetricsForwardPaddingLabelGapAndInheritedOrExplicitRowGap() {
        for (gap in listOf(ROW_GAP_INHERIT, 0, 17)) {
            val source = container().copy(containerItemSizeDp = 89, containerItemGapDp = 13,
                containerRowGapDp = gap, containerIconSizeDp = 51,
                containerItemPaddingVerticalDp = 7, containerIconLabelGapDp = 9)
            val expected = resolveContainerGridDropPlacements(source.containerComponents, 24, 307f, 9f,
                configuredHeightUnitDp = 11, fontScale = 1.3f, iconColumns = 3,
                itemGapDp = 13, itemSizeDp = 89, iconSizeDp = 51,
                rowGapDp = if (gap == ROW_GAP_INHERIT) 13 else gap,
                itemPaddingVerticalDp = 7, iconLabelGapDp = 9, movedId = "a", targetCol = 2, targetRow = 4)
            val result = ComponentGridOperations.placeChildInContainerGrid(source, "a", 2, 4,
                307f, 9f, 3, 1.3f, 24, 11)
            assertEquals(expected, result.containerComponents)
            assertEquals(source.copy(containerComponents = expected), result)
        }
    }

    @Test fun containerCompactionUsesVisualOrderAndPreservesNestedTreesAndActions() {
        val late = card("late").copy(type = ComponentType.FOLDER, gridCol = 3, gridRow = 8,
            containerComponents = listOf(card("grandchild")))
        val first = card("first").copy(gridCol = 0, gridRow = 0)
        val second = card("second").copy(gridCol = 2, gridRow = 0)
        val source = container().copy(containerComponents = listOf(late, second, first))
        val result = ComponentGridOperations.compactContainerGridPlacements(source, 320f, 8f, 4, 1f, 32, 0)
        assertEquals(listOf(first, second, late).map { it.copy(gridCol = -1, gridRow = -1) }, result.containerComponents)
        assertEquals(source.copy(containerComponents = result.containerComponents), result)
    }

    @Test fun legacyActionSwapDoesNotTouchRealChildrenOrActionGeometry() {
        val source = container().copy(containerActions = listOf(action("x").copy(xDp = 29), action("y")))
        val result = ComponentGridOperations.placeActionInContainerGrid(source, "x", 1, 0, 4)
        assertEquals(listOf(source.containerActions[0].copy(gridCol = 1, gridRow = 0),
            source.containerActions[1].copy(gridCol = 0, gridRow = 0)), result.containerActions)
        assertEquals(source.copy(containerActions = result.containerActions), result)
    }

    @Test fun nonGridOperationsReturnOriginalObjectsAndRepositoryStillAcceptsValidNoOps() {
        val page = page().copy(layoutType = PageLayoutType.FREE)
        assertSame(page, ComponentGridOperations.placeComponentInPageGrid(page, "a", 2, 3, 320f, 1f, 32, 0))
        assertSame(page, ComponentGridOperations.nudgeComponentInPageGrid(page, "a", 2, 3, 320f, 1f, 32, 0))
        assertSame(page, ComponentGridOperations.compactPageGridPlacements(page, 320f, 1f, 32, 0))
        val container = container().copy(containerLayout = ContainerLayoutType.FREE)
        assertSame(container, ComponentGridOperations.placeChildInContainerGrid(container, "a", 2, 3, 320f, 8f, 4, 1f, 32, 0))
        assertSame(container, ComponentGridOperations.placeActionInContainerGrid(container, "legacy", 2, 3, 4))
        assertSame(container, ComponentGridOperations.compactContainerGridPlacements(container, 320f, 8f, 4, 1f, 32, 0))
        val repository = MinusOneRepository(preferences(GlobalMinusOneState(pages = listOf(page.copy(components = listOf(container))))))
        val before = repository.state.value
        assertTrue(repository.placeComponentInPageGrid(0, "missing", 2, 3))
        assertTrue(repository.placeActionInContainerGrid(0, "inner", "missing", 2, 3, 4))
        assertEquals(before, repository.state.value)
    }

    @Test fun locksInvalidTargetsAndRejectedWritesKeepAllSixTransactionBoundaries() {
        val prefs = RejectingPreferences(initialState())
        val repository = MinusOneRepository(prefs)
        assertFalse(repository.placeComponentInPageGrid(-1, "a", 2, 3))
        assertFalse(repository.placeChildInContainerGrid(0, "missing", "a", 2, 3, 320f, 8f, 4))
        assertFalse(repository.placeActionInContainerGrid(0, "missing", "legacy", 2, 3, 4))
        assertTrue(repository.setLayoutLocked(true))
        val locked = repository.state.value
        operations(repository).forEach { assertFalse(it()) }
        assertEquals(locked, repository.state.value)
        assertTrue(repository.setLayoutLocked(false))
        val before = repository.state.value
        prefs.reject = true
        operations(repository).forEach { assertFalse(it()); assertEquals(before, repository.state.value) }
        assertNotNull(repository.persistenceError.value)
    }

    @Test fun editingDiscardsOrSavesPageAndNestedContainerPinsWithPreferenceReadback() {
        val prefs = preferences(initialState())
        val repository = MinusOneRepository(prefs)
        val original = repository.state.value.pages
        assertTrue(repository.setEditMode(true))
        assertTrue(repository.placeComponentInPageGrid(0, "outer", 1, 6, 320f))
        assertTrue(repository.placeChildInContainerGrid(0, "inner", "a", 2, 4, 320f, 8f, 4))
        assertTrue(repository.finishPanelEdit(false))
        assertEquals(original, repository.state.value.pages)
        assertEquals(original, MinusOneRepository(prefs).state.value.pages)
        assertTrue(repository.setEditMode(true))
        assertTrue(repository.placeComponentInPageGrid(0, "outer", 1, 6, 320f))
        assertTrue(repository.placeChildInContainerGrid(0, "inner", "a", 2, 4, 320f, 8f, 4))
        assertTrue(repository.placeActionInContainerGrid(0, "inner", "legacy", 3, 5, 4))
        assertTrue(repository.finishPanelEdit(true))
        assertNotEquals(original, repository.state.value.pages)
        assertEquals(repository.state.value.pages, MinusOneRepository(prefs).state.value.pages)
    }

    private fun operations(repository: MinusOneRepository): List<() -> Boolean> = listOf(
        { repository.placeComponentInPageGrid(0, "outer", 1, 6, 320f) },
        { repository.nudgeComponentInPageGrid(0, "outer", 0, 1, 320f) },
        { repository.compactPageGridPlacements(0, 320f) },
        { repository.placeChildInContainerGrid(0, "inner", "a", 2, 4, 320f, 8f, 4) },
        { repository.placeActionInContainerGrid(0, "inner", "legacy", 3, 5, 4) },
        { repository.compactContainerGridPlacements(0, "inner", 320f, 8f, 4) },
    )
    private fun card(id: String) = ComponentItem(id = id, type = ComponentType.APP_ICON,
        title = "title-$id", textContent = "content-$id", gridSpanX = 1, gridSpanY = 1,
        x = 2, y = 3, w = 4, h = 4)
    private fun action(id: String) = QuickActionItem(id = id, title = id, intentUri = "intent:$id")
    private fun page() = PageConfig(id = "page", name = "grid", layoutType = PageLayoutType.GRID,
        gridColumnCount = 4, components = listOf(card("a"), card("b")))
    private fun container() = ComponentItem(id = "inner", type = ComponentType.FOLDER,
        containerLayout = ContainerLayoutType.GRID, containerComponents = listOf(card("a"), card("b")),
        containerActions = listOf(action("legacy")), gridSpanX = 1, gridSpanY = 1)
    private fun initialState() = GlobalMinusOneState(pages = listOf(page().copy(components = listOf(
        ComponentItem(id = "outer", type = ComponentType.FOLDER, gridSpanX = 1, gridSpanY = 1,
            containerComponents = listOf(container()))))))
    private fun preferences(state: GlobalMinusOneState) = MemoryPreferences(mapOf("state_json_v5" to ConfigurationCodec.encode(state)))
    private fun repository() = MinusOneRepository(preferences(initialState()))
    private class RejectingPreferences(state: GlobalMinusOneState,
        private val backing: MemoryPreferences = MemoryPreferences(mapOf("state_json_v5" to ConfigurationCodec.encode(state)))) : SharedPreferences by backing {
        var reject = false
        override fun edit(): SharedPreferences.Editor {
            if (reject) throw IllegalStateException("write rejected")
            return backing.edit()
        }
    }
}
