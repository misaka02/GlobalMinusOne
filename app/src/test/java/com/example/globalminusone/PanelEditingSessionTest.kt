package com.example.globalminusone.regression

import com.example.globalminusone.MemoryPreferences
import com.example.globalminusone.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.globalminusone.data.BoundWidgetInfo
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.FileBrowserBookmark
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.PageLayoutType

class PanelEditingSessionTest {
    @Test
    fun enteringSelectingAndSwitchingPagesDoesNotMarkEditSessionDirty() {
        val repository = MinusOneRepository(MemoryPreferences())
        repository.setCurrentPageIndex(0)
        repository.setEditMode(true)

        assertFalse(repository.hasPanelEditChanges())
        repository.selectComponent("p1_container_app")
        assertFalse(repository.hasPanelEditChanges())
        repository.setCurrentPageIndex(1)
        assertFalse(repository.hasPanelEditChanges())
    }

    @Test
    fun draftIsNotVisibleToFreshRepositoryUntilSaved() {
        val prefs = MemoryPreferences()
        val repository = MinusOneRepository(prefs)
        val pageId = repository.state.value.pages[0].id
        repository.setEditMode(true)
        repository.updatePageConfig(0) { page ->
            page.copy(components = page.components + ComponentItem(id = "draft-card", type = ComponentType.TEXT_LABEL))
        }
        assertTrue(repository.hasPanelEditChanges())

        val reloadedBeforeSave = MinusOneRepository(prefs).state.value
        assertFalse(reloadedBeforeSave.pages.first { it.id == pageId }.components.any { it.id == "draft-card" })

        repository.finishPanelEdit(save = true)
        val reloadedAfterSave = MinusOneRepository(prefs).state.value
        assertTrue(reloadedAfterSave.pages.first { it.id == pageId }.components.any { it.id == "draft-card" })
        assertFalse(reloadedAfterSave.editMode)
        assertNull(reloadedAfterSave.selectedComponentId)
    }

    @Test
    fun discardRestoresPageAndGeometryButKeepsFileFavoritesAndTodos() {
        val repository = MinusOneRepository(MemoryPreferences())
        val originalPages = repository.state.value.pages
        val originalWidth = repository.state.value.themeConfig.panelWidthPercent
        val originalColumns = repository.state.value.advancedConfig.gridColumns
        repository.setEditMode(true)
        repository.updatePageConfig(0) { page -> page.copy(name = "草稿页", components = emptyList()) }
        repository.updateThemeConfig { it.copy(panelWidthPercent = 42) }
        repository.updateAdvancedConfig { it.copy(gridColumns = 20) }
        repository.updateFileBrowserConfig { config ->
            config.copy(favorites = listOf(FileBrowserBookmark(title = "合成收藏", path = "/synthetic")))
        }
        repository.addTodo("编辑期间新增记录")

        assertTrue(repository.hasPanelEditChanges())
        repository.finishPanelEdit(save = false)

        assertEquals(originalPages, repository.state.value.pages)
        assertEquals(originalWidth, repository.state.value.themeConfig.panelWidthPercent)
        assertEquals(originalColumns, repository.state.value.advancedConfig.gridColumns)
        assertEquals("合成收藏", repository.state.value.fileBrowserConfig.favorites.single().title)
        assertTrue(repository.state.value.todos.any { it.content == "编辑期间新增记录" })
        assertFalse(repository.state.value.editMode)
    }

    @Test
    fun returningPanelValuesToBaselineClearsDirtyState() {
        val repository = MinusOneRepository(MemoryPreferences())
        val original = repository.state.value.pages[0].components.first()
        repository.setEditMode(true)
        repository.updatePageConfig(0) { page ->
            page.copy(components = page.components.map { if (it.id == original.id) it.copy(title = "临时标题") else it })
        }
        assertTrue(repository.hasPanelEditChanges())
        repository.updatePageConfig(0) { page ->
            page.copy(components = page.components.map { if (it.id == original.id) original else it })
        }
        assertFalse(repository.hasPanelEditChanges())
    }

    @Test
    fun menuInsertionUsesLinearAnchorOrderAndFreeLayoutYPlacement() {
        val repository = MinusOneRepository(MemoryPreferences())
        val linePageId = "synthetic-linear"
        val first = ComponentItem(id = "line-first", type = ComponentType.TEXT_LABEL)
        val anchor = ComponentItem(id = "line-anchor", type = ComponentType.TEXT_LABEL)
        repository.updatePageConfig(0) { it.copy(id = linePageId, layoutType = PageLayoutType.LINEAR, components = listOf(first, anchor)) }
        val lineAdded = ComponentItem(id = "line-added", type = ComponentType.NOTE_CARD)
        assertTrue(repository.placeMenuComponent(linePageId, null, lineAdded, beforeId = anchor.id))
        assertEquals(listOf(first.id, lineAdded.id, anchor.id), repository.state.value.pages[0].components.map { it.id })

        val freePageId = "synthetic-free"
        val freeAnchor = ComponentItem(id = "free-anchor", type = ComponentType.TEXT_LABEL, x = 3, y = 5)
        val lower = ComponentItem(id = "free-lower", type = ComponentType.TEXT_LABEL, x = 0, y = 12)
        repository.updatePageConfig(0) { it.copy(id = freePageId, layoutType = PageLayoutType.FREE, components = listOf(freeAnchor, lower)) }
        val freeAdded = ComponentItem(id = "free-added", type = ComponentType.TEXT_LABEL, w = 4)
        assertTrue(repository.placeMenuComponent(freePageId, null, freeAdded, beforeId = freeAnchor.id))
        val placed = repository.state.value.pages[0].components
        assertEquals(listOf(freeAdded.id, freeAnchor.id, lower.id), placed.map { it.id })
        assertEquals(5, placed[0].y)
        assertTrue("Anchored cards must move down to make room", placed[1].y > freeAnchor.y)
        assertTrue("Cards below the anchor must also make room", placed[2].y > lower.y)
    }

    @Test
    fun asynchronousFileImageAndWidgetResultsReuseTheSameStableAnchor() {
        val repository = MinusOneRepository(MemoryPreferences())
        val pageId = "picker-page"
        val anchor = ComponentItem(id = "picker-anchor", type = ComponentType.TEXT_LABEL)
        repository.updatePageConfig(0) { it.copy(id = pageId, layoutType = PageLayoutType.LINEAR, components = listOf(anchor)) }

        assertTrue(repository.saveFileShortcut(pageId, null, "合成文件", "content://synthetic.provider/file", beforeId = anchor.id))
        assertTrue(repository.saveImageCard(pageId, null, "合成图片", "content://synthetic.provider/image", beforeId = anchor.id))
        assertTrue(repository.addBoundWidget(
            BoundWidgetInfo(appWidgetId = 901, providerPackage = "synthetic.provider", providerClass = "SyntheticWidget", label = "合成小部件"),
            pageId = pageId,
            beforeId = anchor.id
        ))

        val components = repository.state.value.pages.single { it.id == pageId }.components
        val anchorIndex = components.indexOfFirst { it.id == anchor.id }
        val insertedBeforeAnchor = components.take(anchorIndex)
        assertEquals(3, insertedBeforeAnchor.size)
        assertTrue(insertedBeforeAnchor.any { it.type == ComponentType.FILE_SHORTCUT && it.title == "合成文件" })
        assertTrue(insertedBeforeAnchor.any { it.type == ComponentType.SINGLE_IMAGE && it.title == "合成图片" })
        assertTrue(insertedBeforeAnchor.any { it.type == ComponentType.APP_WIDGET && it.widgetId == 901 })
    }

    @Test
    fun invalidatedAsyncAnchorOrContainerDoesNotFallBackToTopLevel() {
        val repository = MinusOneRepository(MemoryPreferences())
        val pageId = "invalid-target-page"
        val container = ComponentItem(
            id = "target-container", type = ComponentType.CONTAINER_BOX,
            containerComponents = listOf(ComponentItem(id = "target-anchor", type = ComponentType.TEXT_LABEL))
        )
        repository.updatePageConfig(0) { it.copy(id = pageId, components = listOf(container)) }
        val before = repository.state.value.pages.single { it.id == pageId }

        assertFalse(repository.saveFileShortcut(pageId, null, "文件", "content://synthetic.provider/file", containerId = container.id, beforeId = "deleted-anchor"))
        assertFalse(repository.saveImageCard(pageId, null, "图片", "content://synthetic.provider/image", containerId = "deleted-container", beforeId = "target-anchor"))
        assertFalse(repository.addBoundWidget(
            BoundWidgetInfo(appWidgetId = 902, providerPackage = "synthetic.provider", providerClass = "SyntheticWidget", label = "小部件"),
            pageId = pageId, containerId = container.id, beforeId = "deleted-anchor"
        ))
        assertEquals(before, repository.state.value.pages.single { it.id == pageId })
    }
}
