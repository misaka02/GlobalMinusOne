package com.example.globalminusone

import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.ContainerLayoutType
import com.example.globalminusone.data.PageConfig
import com.example.globalminusone.data.arrangeContainerChildren
import com.example.globalminusone.data.isContainerType
import com.example.globalminusone.data.validateComponentTrees
import com.example.globalminusone.ui.panel.resizedCardSize
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderAndResizeTest {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @Test
    fun folderAndContainerBoxAreContainersButAppIsNot() {
        assertTrue(ComponentType.FOLDER.isContainerType())
        assertTrue(ComponentType.CONTAINER_BOX.isContainerType())
        assertFalse(ComponentType.APP_ICON.isContainerType())
    }

    @Test
    fun legacyComponentWithoutContainerLayoutDefaultsToFree() {
        val old = json.decodeFromString<ComponentItem>("""{"id":"old-container","type":"CONTAINER_BOX"}""")

        assertEquals(ContainerLayoutType.FREE, old.containerLayout)
    }

    @Test
    fun nestedFolderContentsOfDifferentTypesSurviveRoundTripAndTreeValidation() {
        val nested = ComponentItem(
            id = "nested-box",
            type = ComponentType.CONTAINER_BOX,
            containerLayout = ContainerLayoutType.GRID,
            containerComponents = listOf(
                ComponentItem(id = "shortcut", type = ComponentType.CUSTOM_SHORTCUT, title = "Tool", intentUri = "intent://tool"),
                ComponentItem(id = "file", type = ComponentType.FILE_BROWSER, title = "Files")
            )
        )
        val folder = ComponentItem(
            id = "folder",
            type = ComponentType.FOLDER,
            title = "Mixed items",
            containerLayout = ContainerLayoutType.GRID,
            containerComponents = listOf(
                ComponentItem(id = "note", type = ComponentType.NOTE_CARD, textContent = "Keep this text"),
                ComponentItem(id = "app", type = ComponentType.APP_ICON, packageName = "example.app"),
                nested
            )
        )

        val decoded = json.decodeFromString<ComponentItem>(json.encodeToString(folder))
        validateComponentTrees(listOf(PageConfig(name = "Round trip", components = listOf(decoded))))

        assertEquals(folder, decoded)
        assertEquals(
            setOf(ComponentType.NOTE_CARD, ComponentType.APP_ICON, ComponentType.CONTAINER_BOX,
                ComponentType.CUSTOM_SHORTCUT, ComponentType.FILE_BROWSER),
            decoded.containerComponents.flatMap { listOf(it) + it.containerComponents }.map { it.type }.toSet()
        )
    }

    @Test
    fun flowArrangementUsesWidthsWrapsFullWidthAndPreservesOriginalItems() {
        val input = listOf(
            ComponentItem(id = "a", type = ComponentType.NOTE_CARD, title = "A", textContent = "alpha", w = 3, h = 2),
            ComponentItem(id = "b", type = ComponentType.APP_ICON, title = "B", packageName = "example.b", w = 5, h = 2, autoHeight = false),
            ComponentItem(
                id = "wide", type = ComponentType.FOLDER, title = "Wide", w = 8, h = 2,
                containerComponents = listOf(ComponentItem(id = "child", type = ComponentType.NOTE_CARD, textContent = "nested"))
            ),
            ComponentItem(id = "last", type = ComponentType.FILE_BROWSER, title = "Last", w = 1, h = 2)
        )
        val before = input.toList()

        val arranged = arrangeContainerChildren(input, columns = 8, widthDp = 80f, stepYDp = 10f)

        assertEquals(4, arranged.size)
        assertEquals(listOf("a" to (0 to 0), "b" to (3 to 0), "wide" to (0 to 3), "last" to (0 to 6)),
            arranged.map { it.id to (it.x to it.y) })
        assertEquals(listOf(3, 5, 8, 1), arranged.map { it.w })
        assertEquals(before, input)
        input.zip(arranged).forEach { (original, placed) ->
            assertEquals(original.id, placed.id)
            assertEquals(original.type, placed.type)
            assertEquals(original.title, placed.title)
            assertEquals(original.textContent, placed.textContent)
            assertEquals(original.packageName, placed.packageName)
            assertEquals(original.containerComponents, placed.containerComponents)
        }
    }

    @Test
    fun resizeRoundsGridDeltasAndClampsWidthAndHeight() {
        assertEquals(7 to 3, resizedCardSize(w = 4, h = 5, dx = 30f, dy = -20f, sx = 10f, sy = 10f, columns = 8))
        assertEquals(8 to 120, resizedCardSize(w = 7, h = 119, dx = 1000f, dy = 20f, sx = 10f, sy = 10f, columns = 8))
        assertEquals(1 to 1, resizedCardSize(w = 1, h = 1, dx = -1000f, dy = -1000f, sx = 10f, sy = 10f, columns = 8))
    }

    @Test
    fun configuredFolderColumnsAffectDefaultIconsWithoutChangingSavedWidths() {
        val original = (0..3).map { ComponentItem(id = "app-$it", type = ComponentType.APP_ICON) }
        val placed = arrangeContainerChildren(original, 32, 320f, 10f, iconColumns = 3)
        assertEquals(listOf(0, 10, 20, 0), placed.map { it.x })
        assertTrue(placed.last().y > placed.first().y)
        assertTrue(placed.all { it.w == 10 })
        assertTrue(original.all { it.w == 8 })
    }
}
