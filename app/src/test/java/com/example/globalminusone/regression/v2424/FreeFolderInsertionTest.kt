package com.example.globalminusone.regression.v2424

import com.example.globalminusone.MemoryPreferences
import com.example.globalminusone.data.*
import org.junit.Assert.*
import org.junit.Test
import com.example.globalminusone.data.BoundWidgetInfo
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.ContainerLayoutType
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.operations.findComponent

class FreeFolderInsertionTest {
    @Test fun addingAboveInsideFreeFolderUsesCoordinatesForMenuFileAndWidgetPaths() {
        repeat(3) { path ->
            val preferences = MemoryPreferences()
            val repo = MinusOneRepository(preferences)
            val page = repo.state.value.pages.first()
            val anchor = ComponentItem(type = ComponentType.NOTE_CARD, x = 3, y = 20, w = 8, h = 8, autoHeight = false)
            val later = anchor.copy(id = "later", y = 50)
            val folder = ComponentItem(type = ComponentType.FOLDER, containerLayout = ContainerLayoutType.FREE,
                containerComponents = listOf(anchor, later))
            repo.addComponentToPage(0, folder)
            val added = when (path) {
                0 -> repo.placeMenuComponent(page.id, folder.id, ComponentItem(type = ComponentType.APP_ICON), anchor.id)
                1 -> repo.saveFileShortcut(page.id, null, "合成文件", "content://synthetic/file", folder.id, anchor.id)
                else -> repo.addBoundWidget(BoundWidgetInfo(791, "synthetic.provider", "Widget", "合成小部件"), page.id, folder.id, anchor.id)
            }
            assertTrue(added)
            val children = repo.state.value.pages.first().components.findComponent(folder.id)!!.containerComponents
            assertEquals(20, children.first().y)
            assertTrue(children.first().id != anchor.id)
            assertTrue(children.first { it.id == anchor.id }.y > 20)
            assertTrue(children.first { it.id == later.id }.y > 50)
            val restored = MinusOneRepository(preferences).state.value.pages.first().components.findComponent(folder.id)!!
            assertEquals(ContainerLayoutType.FREE, restored.containerLayout)
            assertEquals(children, restored.containerComponents)
        }
    }
}
