package com.example.globalminusone.data

import android.content.SharedPreferences
import com.example.globalminusone.MemoryPreferences
import org.junit.Assert.*
import org.junit.Test

class ComponentTreeOperationsTest {
    @Test fun initialPageGeometryPreservesLayoutSpecificRulesAndLazyNextY() {
        var calls = 0
        val next = { calls++; 27 }
        val item = card("new").copy(x = 0, y = 0, w = 8, h = 0)
        val free = page("p", listOf(card("old")), PageLayoutType.FREE)
        assertEquals(item.copy(x = 12, y = 27, h = 1), ComponentPlacementOperations.inPage(free, item, 32, next))
        assertEquals(1, calls)
        assertEquals(item.copy(h = 1), ComponentPlacementOperations.inPage(free.copy(components = emptyList()), item, 32, next))
        assertEquals(item.copy(x = 7, y = 9, h = 1), ComponentPlacementOperations.inPage(free, item.copy(x = 7, y = 9), 32, next))
        assertEquals(item.copy(h = 1), ComponentPlacementOperations.inPage(free.copy(layoutType = PageLayoutType.GRID), item, 32, next))
        assertEquals(item.copy(w = 32), ComponentPlacementOperations.inPage(free.copy(layoutType = PageLayoutType.LINEAR), item, 32, next))
        assertEquals(1, calls)
    }

    @Test fun initialContainerGeometryClampsAndReadsNextYOnlyForZeroY() {
        var calls = 0
        val next = { calls++; 31 }
        val item = card("new").copy(x = 99, y = 0, w = 999, h = 0)
        assertEquals(item.copy(x = 0, y = 31, w = 24, h = 1), ComponentPlacementOperations.inContainer(item, 24, next))
        assertEquals(item.copy(x = 0, y = 0, w = 24, h = 1), ComponentPlacementOperations.inContainer(item.copy(y = -4), 24, next))
        assertEquals(1, calls)
    }

    @Test fun samePageMoveRemovesBeforeNextYAndAppendsWithoutChangingWidgetIdentity() {
        val widget = card("widget").copy(type = ComponentType.APP_WIDGET, widgetId = 72, x = 99, w = 99)
        val pages = listOf(page("p", listOf(widget, card("stay"))))
        var calls = 0
        val result = ComponentTransferOperations.transfer(pages, 0, 0, "widget", null, false, 24,
            nextY = { target, container ->
                calls++
                assertNull(container)
                assertEquals(listOf("stay"), target.components.map { it.id })
                43
            }, newId = { error("Moving must not allocate IDs") })!!
        assertEquals(1, calls)
        assertEquals("widget", result.placedId)
        assertEquals(listOf(card("stay"), widget.copy(x = 0, y = 43, w = 24)), result.pages[0].components)
    }

    @Test fun crossPageMoveIntoDeepContainerKeepsArbitraryChildrenAndSourceSiblings() {
        val source = folder("source", listOf(card("drawer").copy(type = ComponentType.APP_DRAWER_CARD), card("image").copy(type = ComponentType.SINGLE_IMAGE)))
        val destination = folder("outer", listOf(folder("inner", listOf(card("existing")))))
        val pages = listOf(page("s", listOf(source, card("stay"))), page("t", listOf(destination)))
        val result = ComponentTransferOperations.transfer(pages, 0, 1, "source", "inner", false, 32,
            nextY = { target, id -> assertEquals("t", target.id); assertEquals("inner", id); 28 })!!
        assertEquals(listOf(card("stay")), result.pages[0].components)
        val inner = result.pages[1].components.findComponent("inner")!!
        assertEquals(listOf(card("existing"), source.copy(y = 28)), inner.containerComponents)
        assertEquals(source.containerComponents, inner.containerComponents.last().containerComponents)
    }

    @Test fun deepCopyAllocatesParentActionsThenChildrenAndRetainsAllNonIdData() {
        val leaf = card("leaf")
        val child = folder("child", listOf(leaf)).copy(containerActions = listOf(action("child-action")))
        val source = folder("root", listOf(child)).copy(containerActions = listOf(action("a"), action("b")))
        val pages = listOf(page("s", listOf(source)), page("t", emptyList()))
        val ids = mutableListOf<String>()
        val result = ComponentTransferOperations.transfer(pages, 0, 1, "root", null, true, 32,
            nextY = { _, _ -> 17 }, newId = { "id-${ids.size}".also { ids.add(it) } })!!
        assertEquals((0..5).map { "id-$it" }, ids)
        val expected = source.copy(id = "id-0", y = 17,
            containerActions = listOf(action("a").copy(id = "id-1"), action("b").copy(id = "id-2")),
            containerComponents = listOf(child.copy(id = "id-3", containerActions = listOf(action("child-action").copy(id = "id-4")),
                containerComponents = listOf(leaf.copy(id = "id-5")))))
        assertEquals(pages[0], result.pages[0])
        assertEquals(listOf(expected), result.pages[1].components)
    }

    @Test fun rejectedTransferTargetsAndWidgetCopiesConsumeNoIdsOrNextY() {
        val source = folder("root", listOf(folder("child", listOf(card("leaf")))))
        val pages = listOf(page("s", listOf(source, card("plain"))), page("t", emptyList()))
        for ((id, target, copy) in listOf(Triple("missing", null, true), Triple("root", "root", false),
            Triple("root", "child", true), Triple("root", "missing", true), Triple("root", "plain", true))) {
            assertNull(ComponentTransferOperations.transfer(pages, 0, 0, id, target, copy, 32,
                nextY = { _, _ -> error("Guard must precede placement") }, newId = { error("Guard must precede IDs") }))
        }
        val widgetTree = source.copy(containerComponents = listOf(card("widget").copy(widgetId = 3)))
        assertNull(ComponentTransferOperations.transfer(listOf(pages[0].copy(components = listOf(widgetTree))),
            0, 0, "root", null, true, 32, nextY = { _, _ -> error("No placement") }, newId = { error("No IDs") }))
    }

    @Test fun transferDoesNotSwallowIdOrPlacementExceptions() {
        val pages = listOf(page("p", listOf(card("source"))))
        val failure = IllegalStateException("expected")
        for (copy in listOf(false, true)) {
            try {
                ComponentTransferOperations.transfer(pages, 0, 0, "source", null, copy, 32,
                    nextY = { _, _ -> throw failure }, newId = { throw failure })
                fail("Exceptions must propagate outside the final repository validation")
            } catch (expected: IllegalStateException) { assertSame(failure, expected) }
        }
    }

    @Test fun pageRemovalPinsAutomaticSiblingsOnlyWhenGridHasRemainingPin() {
        val removed = card("remove")
        val automatic = card("auto")
        val pinned = card("pin").copy(gridCol = 3, gridRow = 5)
        val page = page("p", listOf(removed, automatic, pinned), PageLayoutType.GRID)
        var reads = 0
        val remaining = remove(page.components, "remove", page, true) { reads++; 1f }
        assertEquals(listOf(automatic.copy(gridCol = 1, gridRow = 0), pinned), remaining)
        assertEquals(1, reads)
        assertEquals(listOf(automatic, pinned), remove(page.components, "remove", page, false) { error("Non GRID must not measure") })
        assertEquals(listOf(automatic), remove(listOf(removed, automatic), "remove", page, true) { error("No pin must not measure") })
        assertEquals(page.components, remove(page.components, "missing", page, true) { error("No match must not measure") })
    }

    @Test fun nestedRemovalUsesOriginalPageWidthAndMeasuresEachNeededBranchLazily() {
        val inner = folder("inner", listOf(card("remove"), card("auto"), card("pin").copy(gridCol = 3, gridRow = 5)))
            .copy(containerColumns = 4, containerItemGapDp = 7, containerRowGapDp = 11,
                containerItemPaddingVerticalDp = 8, containerIconLabelGapDp = 9)
        val outer = folder("outer", listOf(card("drawer").copy(type = ComponentType.APP_DRAWER_CARD), inner)).copy(w = 4)
        val page = page("p", listOf(outer))
        var reads = 0
        val expectedAuto = containerGridChildBounds(inner.containerComponents, 32, 320f, 10f,
            fontScale = 1.3f, iconColumns = 4, itemGapDp = 7, rowGapDp = 11,
            itemPaddingVerticalDp = 8, iconLabelGapDp = 9).first { it.item.id == "auto" }
        val result = remove(page.components, "remove", page, false) { reads++; 1.3f }
        assertEquals(1, reads)
        val updatedInner = result.findComponent("inner")!!
        assertEquals(listOf(card("auto").copy(gridCol = expectedAuto.col, gridRow = expectedAuto.row), inner.containerComponents.last()), updatedInner.containerComponents)
        assertEquals(listOf(outer.copy(containerComponents = listOf(outer.containerComponents.first(), updatedInner))), result)
        // Direct siblings take precedence over recursion, even for a malformed duplicate-ID input.
        assertEquals(listOf(inner), remove(listOf(card("remove"), inner), "remove", page, false) { error("No recursion after direct match") })
    }

    @Test fun repositoryAddTransferAndRemovalRespectLocksTargetsWritesAndSelection() {
        val prefs = RejectingPreferences(state())
        val repository = MinusOneRepository(prefs)
        val initial = repository.state.value
        assertFalse(repository.addComponentToContainer(0, "missing", card("new")))
        assertFalse(repository.transferComponent("s", "source", "t", "missing"))
        assertEquals(initial, repository.state.value)
        repository.selectComponent("dangling")
        assertTrue(repository.removeComponent(0, "missing"))
        assertNull(repository.state.value.selectedComponentId)
        assertTrue(repository.setLayoutLocked(true))
        val locked = repository.state.value
        repository.addComponentToPage(0, card("new"))
        assertFalse(repository.addComponentToContainer(1, "target", card("new")))
        assertFalse(repository.transferComponent("s", "source", "t"))
        assertTrue(repository.removeComponent(0, "source"))
        assertTrue(repository.removeComponent(-1, "source"))
        assertEquals(locked, repository.state.value)
        assertTrue(repository.setLayoutLocked(false))
        val before = repository.state.value
        prefs.reject = true
        repository.addComponentToPage(0, card("new"))
        assertEquals(before, repository.state.value)
        assertFalse(repository.addComponentToContainer(1, "target", card("new")))
        assertFalse(repository.transferComponent("s", "source", "t"))
        assertFalse(repository.removeComponent(0, "source"))
        assertEquals(before, repository.state.value)
    }

    @Test fun deletingSubtreeClearsDescendantSelectionAndPreservesOtherBranches() {
        val repository = MinusOneRepository(preferences(state()))
        repository.selectComponent("leaf")
        assertTrue(repository.removeComponent(0, "source"))
        assertNull(repository.state.value.selectedComponentId)
        assertEquals(listOf(card("stay")), repository.state.value.pages[0].components)
        assertEquals(state().pages[1], repository.state.value.pages[1])
    }

    @Test fun editDiscardAndSaveReadBackAddsMovesAndRemovalsAtomically() {
        val prefs = preferences(state())
        val repository = MinusOneRepository(prefs)
        val original = repository.state.value.pages
        fun mutate() {
            repository.addComponentToPage(0, card("new"))
            assertTrue(repository.addComponentToContainer(1, "target", card("nested")))
            assertTrue(repository.transferComponent("s", "source", "t", "target"))
            assertTrue(repository.removeComponent(0, "stay"))
        }
        assertTrue(repository.setEditMode(true))
        mutate()
        assertTrue(repository.finishPanelEdit(false))
        assertEquals(original, repository.state.value.pages)
        assertEquals(original, MinusOneRepository(prefs).state.value.pages)
        assertTrue(repository.setEditMode(true))
        mutate()
        assertTrue(repository.finishPanelEdit(true))
        assertEquals(listOf("new"), repository.state.value.pages[0].components.map { it.id })
        assertEquals(listOf("nested", "source"), repository.state.value.pages[1].components.findComponent("target")!!.containerComponents.map { it.id })
        assertEquals(repository.state.value.pages, MinusOneRepository(prefs).state.value.pages)
    }

    private fun remove(items: List<ComponentItem>, id: String, page: PageConfig, grid: Boolean, scale: () -> Float) =
        ComponentRemovalOperations.remove(items = items, targetId = id, isGridScope = grid, pageConfig = page,
            gridColumns = 32, heightUnitDp = 0, fontScale = scale, pageGridWidthDp = 320f)
    private fun card(id: String) = ComponentItem(id = id, type = ComponentType.APP_ICON,
        title = "title-$id", textContent = "content-$id", w = 4, h = 4, gridSpanX = 1, gridSpanY = 1)
    private fun action(id: String) = QuickActionItem(id = id, title = id, intentUri = "intent:$id")
    private fun folder(id: String, children: List<ComponentItem>) = ComponentItem(id = id, type = ComponentType.FOLDER,
        containerLayout = ContainerLayoutType.GRID, containerComponents = children, w = 16, h = 8,
        gridSpanX = 1, gridSpanY = 1)
    private fun page(id: String, children: List<ComponentItem>, layout: PageLayoutType = PageLayoutType.FREE) =
        PageConfig(id = id, name = id, components = children, layoutType = layout, gridColumnCount = 4)
    private fun state() = GlobalMinusOneState(pages = listOf(page("s", listOf(folder("source", listOf(card("leaf"))), card("stay"))),
        page("t", listOf(folder("target", emptyList())))))
    private fun preferences(state: GlobalMinusOneState) = MemoryPreferences(mapOf("state_json_v5" to ConfigurationCodec.encode(state)))
    private class RejectingPreferences(state: GlobalMinusOneState,
        private val backing: MemoryPreferences = MemoryPreferences(mapOf("state_json_v5" to ConfigurationCodec.encode(state)))) : SharedPreferences by backing {
        var reject = false
        override fun edit(): SharedPreferences.Editor {
            if (reject) throw IllegalStateException("write rejected")
            return backing.edit()
        }
    }
}
