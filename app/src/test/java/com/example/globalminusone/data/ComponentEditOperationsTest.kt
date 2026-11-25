package com.example.globalminusone.data

import android.content.SharedPreferences
import com.example.globalminusone.MemoryPreferences
import org.junit.Assert.*
import org.junit.Test

class ComponentEditOperationsTest {
    @Test fun menuReadsNextYThenRowsEvenWithoutAnchorOrInGridAndInvalidAnchor() {
        val page = page(layout = PageLayoutType.GRID)
        for (anchor in listOf(null, "missing")) {
            val calls = mutableListOf<String>()
            val item = card("new").copy(w = 999, x = 99)
            val result = ComponentInsertionOperations.placeMenu(page, null, item, anchor, 24,
                nextY = { calls += "y"; 17 }, rows = {
                    calls += "rows"
                    assertEquals(item.copy(w = 24, x = 0, y = 17), it)
                    6
                })
            assertEquals(listOf("y", "rows"), calls)
            if (anchor == null) assertEquals(item.copy(w = 24, x = 0, y = 17), result!!.components.last())
            else assertNull(result)
        }
        for (id in listOf("missing", "anchor")) {
            assertNull(ComponentInsertionOperations.placeMenu(page, id, card("new"), null, 32,
                nextY = { error("Invalid container must not measure") }, rows = { error("Invalid container must not measure") }))
        }
    }

    @Test fun freeInsertionShiftsSiblingsWhileGridKeepsGeometryAndNestedAnchorsStayLocal() {
        val anchor = card("anchor").copy(x = 9, y = 8)
        val tail = card("tail").copy(y = 12)
        val page = page(children = listOf(anchor, tail))
        val item = card("new").copy(x = 1, y = 99)
        val free = ComponentInsertionOperations.insert(page, null, item, "anchor", 32, 7)!!
        assertEquals(listOf(item.copy(x = 9, y = 8), anchor.copy(y = 15), tail.copy(y = 19)), free)
        val grid = ComponentInsertionOperations.insert(page.copy(layoutType = PageLayoutType.GRID), null, item, "anchor", 32, 7)!!
        assertEquals(listOf(item, anchor, tail), grid)
        val container = folder("outer", listOf(folder("inner", listOf(anchor))))
        val nested = page.copy(components = listOf(container, tail))
        val inner = nested.components.findComponent("inner")!!
        assertNull(ComponentInsertionOperations.insert(nested, inner, item, "tail", 32, 7))
        val inserted = ComponentInsertionOperations.insert(nested, inner, item, "anchor", 32, 7)!!
        assertEquals(listOf(item, anchor), inserted.findComponent("inner")!!.containerComponents)
        assertEquals(tail, inserted.last())
    }

    @Test fun pickerTargetsRequireCorrectTypeAndContainerSubtreeWithoutRootFallback() {
        val image = card("image", ComponentType.SINGLE_IMAGE)
        val inner = folder("inner", listOf(image))
        val outer = folder("outer", listOf(inner))
        val page = page(children = listOf(outer, folder("other", emptyList()), card("plain")))
        assertEquals(image, PickedCardOperations.resolveTarget(page, "image", null, ComponentType.SINGLE_IMAGE)!!.existing)
        assertEquals(image, PickedCardOperations.resolveTarget(page, "image", "outer", ComponentType.SINGLE_IMAGE)!!.existing)
        assertNull(PickedCardOperations.resolveTarget(page, "image", "other", ComponentType.SINGLE_IMAGE))
        assertNull(PickedCardOperations.resolveTarget(page, "image", "missing", ComponentType.SINGLE_IMAGE))
        assertNull(PickedCardOperations.resolveTarget(page, null, "plain", ComponentType.SINGLE_IMAGE))
        assertNull(PickedCardOperations.resolveTarget(page, "image", null, ComponentType.FILE_SHORTCUT))
        assertNull(PickedCardOperations.resolveTarget(page, "missing", null, ComponentType.SINGLE_IMAGE))
    }

    @Test fun newPickerCardsMeasureOnlyRequiredScopesAndPrepareOnlyNewFiles() {
        for (layout in PageLayoutType.entries) for (container in listOf<ComponentItem?>(null, folder("folder", emptyList()))) {
            val calls = mutableListOf<String>()
            val page = page(layout = layout)
            val target = PickedCardOperations.Target(null, container)
            val file = PickedCardOperations.build(page, target, ComponentType.FILE_SHORTCUT, "", "content://files/a", emptyList(), 24,
                nextY = { calls += "y:$it"; 35 }, prepareNewFile = { calls += "prepare"; it.copy(w = 13, h = 9) })
            val measured = container != null || layout == PageLayoutType.FREE
            assertEquals(if (measured) listOf("y:${container?.id}", "prepare") else listOf("prepare"), calls)
            assertEquals(if (measured) 35 else 0, file.y)
            assertEquals(13, file.w)
            assertEquals(9, file.h)
            assertEquals(ComponentType.FILE_SHORTCUT.label, file.title)
            assertEquals("content://files/a", file.intentUri)
            assertTrue(file.id.isNotBlank())
            val image = PickedCardOperations.build(page, target, ComponentType.SINGLE_IMAGE, "picture", "content://images/a", emptyList(), 24,
                nextY = { 5 }, prepareNewFile = { error("Images must not prepare files") })
            assertEquals("picture", image.title)
            assertEquals("content://images/a", image.imageUri)
        }
    }

    @Test fun existingPickerCardsSkipConstructionMeasurementsAndCubeRetainsTitleGeometryAndCopiesUris() {
        val cube = card("cube", ComponentType.PHOTO_CUBE).copy(title = "custom", x = 7, y = 18, w = 9, h = 11)
        val uris = mutableListOf("content://images/1", "content://images/2")
        val result = PickedCardOperations.build(page(), PickedCardOperations.Target(cube, null), ComponentType.PHOTO_CUBE,
            "replacement", uris[0], uris, 32, nextY = { error("Existing must not measure") }, prepareNewFile = { error("Existing must not prepare") })
        uris.clear()
        assertEquals(cube.copy(photoCubeUris = listOf("content://images/1", "content://images/2")), result)
        for (type in listOf(ComponentType.SINGLE_IMAGE, ComponentType.FILE_SHORTCUT)) {
            val old = cube.copy(type = type)
            val updated = PickedCardOperations.build(page(), PickedCardOperations.Target(old, null), type, "new name", "content://picked/a", emptyList(), 32,
                nextY = { error("Existing must not measure") }, prepareNewFile = { error("Existing file must not prepare") })
            val expected = old.copy(title = "new name")
            assertEquals(if (type == ComponentType.FILE_SHORTCUT) expected.copy(intentUri = "content://picked/a") else expected.copy(imageUri = "content://picked/a"), updated)
        }
    }

    @Test fun contentUriAndCubeDistinctLimitRulesRemainExact() {
        for (uri in listOf("file:///a", "https://example.com/a", "CONTENT://images/a", "content:/a", "content:///a", "content://", "not a uri")) {
            assertFalse(uri, PickedCardOperations.validContentUri(uri))
        }
        assertTrue(PickedCardOperations.validContentUri("content://images/a"))
        val repository = repository()
        val uris = (0..99).map { "content://images/$it" }
        assertTrue(repository.savePhotoCube("p", null, listOf(uris[0], uris[0]) + uris + "file:///invalid-after-limit"))
        assertEquals(uris, repository.state.value.pages[0].components.last().photoCubeUris)
        val before = repository.state.value
        val generation = repository.imageAccessGeneration.value
        assertFalse(repository.savePhotoCube("p", null, emptyList()))
        assertFalse(repository.savePhotoCube("p", null, listOf("content://images/a", "file:///bad")))
        assertFalse(repository.saveImageCard("p", null, "bad", "CONTENT://images/a"))
        assertEquals(before, repository.state.value)
        assertEquals(generation, repository.imageAccessGeneration.value)
    }

    @Test fun existingNestedPickerUpdateIgnoresAnchorAndKeepsTreeAndSelectsOwningPage() {
        val initial = initialState()
        val repository = MinusOneRepository(preferences(initial.copy(
            pages = initial.pages + page(children = emptyList()).copy(id = "second"), currentPageIndex = 1)))
        assertTrue(repository.saveImageCard("p", "image", "renamed", "content://images/new", beforeId = "missing"))
        val updated = repository.state.value.pages[0].components.findComponent("image")!!
        assertEquals("renamed", updated.title)
        assertEquals("content://images/new", updated.imageUri)
        assertEquals("image", repository.state.value.selectedComponentId)
        assertEquals(0, repository.state.value.currentPageIndex)
        assertEquals("image", repository.state.value.pages[0].components.findComponent("inner")!!.containerComponents.single().id)
        assertTrue(repository.savePhotoCube("p", "cube", listOf("content://images/cube"), beforeId = "missing"))
        assertEquals("custom cube", repository.state.value.pages[0].components.findComponent("cube")!!.title)
    }

    @Test fun updateGeometryBoundsOnlyTransformTheTargetAndKeepSubtreesAndOtherFields() {
        val nested = folder("nested", listOf(card("child")))
        val source = listOf(folder("outer", listOf(nested)), card("sibling"))
        val extreme = nested.copy(x = 999, y = -9, w = 999, h = 0, gridSpanX = 999, gridSpanY = 99,
            gridCol = 999, gridRow = 999, title = "changed")
        val result = ComponentUpdateOperations.transform(source, "nested", 24) { extreme }
        val bounded = extreme.copy(x = 0, y = 0, w = 24, h = 1, gridSpanX = GRID_SPAN_FULL_ROW,
            gridSpanY = 12, gridCol = 63, gridRow = 200)
        assertEquals(listOf(source[0].copy(containerComponents = listOf(bounded)), source[1]), result)
        val lower = ComponentUpdateOperations.transform(source, "nested", 24) {
            it.copy(x = -99, w = 0, gridSpanX = -99, gridSpanY = -99, gridCol = -99, gridRow = -99)
        }.findComponent("nested")!!
        assertEquals(nested.copy(x = 0, w = 1, gridSpanX = 0, gridSpanY = 0, gridCol = -1, gridRow = -1), lower)
        assertEquals(12, ComponentUpdateOperations.transform(source, "nested", 24) { it.copy(gridSpanX = 98) }.findComponent("nested")!!.gridSpanX)
    }

    @Test fun repositoryUpdateCatchesTransformExceptionsChangedIdsAndInvalidTreesAndRespectsLock() {
        val repository = repository()
        val before = repository.state.value
        assertFalse(repository.updateComponent(0, "image") { throw IllegalStateException("caller failure") })
        assertFalse(repository.updateComponent(0, "image") { it.copy(id = "changed") })
        assertFalse(repository.updateComponent(0, "inner") { it.copy(containerComponents = listOf(card("anchor"))) })
        assertEquals(before, repository.state.value)
        assertTrue(repository.setLayoutLocked(true))
        val locked = repository.state.value
        assertFalse(repository.updateComponent(0, "image") { error("Locked transform must not run") })
        assertFalse(repository.placeMenuComponent("p", null, card("new")))
        assertFalse(repository.saveImageCard("p", "image", "locked", "content://images/a"))
        assertEquals(locked, repository.state.value)
    }

    @Test fun failedWritesPublishNeitherStateNorImageNotificationsForAnyEditedPath() {
        val prefs = RejectingPreferences(initialState())
        val repository = MinusOneRepository(prefs)
        val before = repository.state.value
        val generation = repository.imageAccessGeneration.value
        prefs.reject = true
        assertFalse(repository.placeMenuComponent("p", "inner", card("new")))
        assertFalse(repository.saveImageCard("p", "image", "rejected", "content://images/new"))
        assertFalse(repository.savePhotoCube("p", "cube", listOf("content://images/1", "content://images/2")))
        assertFalse(repository.updateComponent(0, "image") { it.copy(title = "rejected") })
        assertEquals(before, repository.state.value)
        assertEquals(generation, repository.imageAccessGeneration.value)
        assertNotNull(repository.persistenceError.value)
    }

    @Test fun expiredPickerAndMenuDestinationsAndNonSiblingAnchorsNeverFallBackToRoot() {
        val repository = repository()
        val before = repository.state.value
        assertFalse(repository.placeMenuComponent("missing", null, card("new")))
        assertFalse(repository.placeMenuComponent("p", "missing", card("new")))
        assertFalse(repository.placeMenuComponent("p", "inner", card("new"), "anchor"))
        assertFalse(repository.saveImageCard("missing", null, "new", "content://images/a"))
        assertFalse(repository.saveImageCard("p", null, "new", "content://images/a", "missing"))
        assertFalse(repository.saveImageCard("p", "image", "new", "content://images/a", "other"))
        assertFalse(repository.saveImageCard("p", "anchor", "new", "content://images/a"))
        assertFalse(repository.saveImageCard("p", null, "new", "content://images/a", "inner", "anchor"))
        assertEquals(before, repository.state.value)
    }

    @Test fun editSaveAndDiscardReadBackMenuPickerAndNormalizedUpdateTogether() {
        val prefs = preferences(initialState())
        val repository = MinusOneRepository(prefs)
        val original = repository.state.value.pages
        fun mutate() {
            assertTrue(repository.placeMenuComponent("p", "inner", card("new"), "image"))
            assertTrue(repository.saveImageCard("p", "image", "saved", "content://images/new"))
            assertTrue(repository.updateComponent(0, "new") { it.copy(gridCol = 999, gridRow = 999) })
        }
        assertTrue(repository.setEditMode(true))
        mutate()
        assertTrue(repository.finishPanelEdit(false))
        assertEquals(original, repository.state.value.pages)
        assertEquals(original, MinusOneRepository(prefs).state.value.pages)
        assertTrue(repository.setEditMode(true))
        mutate()
        assertTrue(repository.finishPanelEdit(true))
        assertEquals(listOf("new", "image"), repository.state.value.pages[0].components.findComponent("inner")!!.containerComponents.map { it.id })
        assertEquals(63, repository.state.value.pages[0].components.findComponent("new")!!.gridCol)
        assertEquals(200, repository.state.value.pages[0].components.findComponent("new")!!.gridRow)
        assertEquals(repository.state.value.pages, MinusOneRepository(prefs).state.value.pages)
    }

    private fun card(id: String, type: ComponentType = ComponentType.APP_ICON) = ComponentItem(id = id, type = type,
        title = "title-$id", textContent = "content-$id", w = 4, h = 4, gridSpanX = 1, gridSpanY = 1)
    private fun folder(id: String, children: List<ComponentItem>) = ComponentItem(id = id, type = ComponentType.FOLDER,
        containerLayout = ContainerLayoutType.GRID, containerComponents = children, w = 16, h = 8)
    private fun page(children: List<ComponentItem> = listOf(card("anchor")), layout: PageLayoutType = PageLayoutType.FREE) =
        PageConfig(id = "p", name = "page", components = children, layoutType = layout, gridColumnCount = 4)
    private fun initialState() = GlobalMinusOneState(pages = listOf(page(listOf(card("anchor"),
        folder("outer", listOf(folder("inner", listOf(card("image", ComponentType.SINGLE_IMAGE))))),
        folder("other", emptyList()), card("cube", ComponentType.PHOTO_CUBE).copy(title = "custom cube")))))
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
