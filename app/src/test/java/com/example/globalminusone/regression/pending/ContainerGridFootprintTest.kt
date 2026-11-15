package com.example.globalminusone.regression.pending

import com.example.globalminusone.data.*
import org.junit.Assert.*
import org.junit.Test

class ContainerGridFootprintTest {
    @Test fun sixColumnFoldersUseTheirActualRowHeight() {
        val original = (0..6).map { ComponentItem(id = "folder-$it", type = ComponentType.FOLDER, autoHeight = false) }
        val placed = arrangeContainerChildren(original, 32, 320f, 10f, iconColumns = 6)
        val first = placed.first()
        assertEquals(5, first.w)
        assertEquals(placed.first().h, placed[6].h)
        assertTrue(placed[6].y >= first.y + first.h + 1)
        // Display arrangement must not silently change persistent card dimensions.
        assertEquals(ComponentType.FOLDER.defaultW, original.first().w)
        assertEquals(0, original.last().y)
    }
    @Test fun gridMeasuresUniformIconCellsWhileFreeKeepsAnExplicitManualHeight() {
        val folder = ComponentItem(id = "folder", type = ComponentType.FOLDER, h = 24, autoHeight = false)
        val narrow = arrangeContainerChildren(listOf(folder), 32, 320f, 10f, iconColumns = 6).single()
        val wide = arrangeContainerChildren(listOf(folder), 32, 320f, 10f, iconColumns = 2).single()
        assertTrue(narrow.h < 24)
        assertTrue(wide.h < 24)
        assertEquals(24, folder.h)
        assertEquals(240f, componentDisplayHeightDp(folder, 80f, 32, configuredHeightUnitDp = 10), .01f)
    }
    @Test fun automaticFolderReservesItsTitleAndIconWhileManualHeightRemainsExact() {
        val folder = ComponentItem(id = "folder", type = ComponentType.FOLDER, showTitle = true, autoHeight = true)
        assertTrue(folder.autoHeight)
        assertTrue(componentDisplayHeightDp(folder, 50f, 32) >= 76f)
        assertEquals(50f, componentDisplayHeightDp(folder.copy(autoHeight = false), 50f, 32), .01f)
    }
}
