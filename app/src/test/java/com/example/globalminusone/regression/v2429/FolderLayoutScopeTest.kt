package com.example.globalminusone.regression.v2429

import com.example.globalminusone.data.*
import com.example.globalminusone.ui.panel.FolderDragOrder
import com.example.globalminusone.ui.panel.FolderDragSlot
import org.junit.Assert.*
import org.junit.Test

class FolderLayoutScopeTest {
    @Test fun bareGridRowsMatchIconBodyIncludingFontSubtitleAndExplicitSize() {
        val apps = List(25) { ComponentItem(type = ComponentType.APP_ICON, h = 100, showTitle = true,
            paddingTopDp = 40, marginBottomDp = 40) }
        val rows = arrangeContainerChildren(apps, 24, 240f, 10f, iconColumns = 5, itemGapDp = 0)
        assertTrue(rows.all { it.h == 6 }) // 36 icon + 14 label + 8 padding + 2 spacer = 60dp.
        assertEquals(listOf(0, 6, 12, 18, 24), rows.map { it.y }.distinct())
        val parent = ComponentItem(type = ComponentType.CONTAINER_BOX, containerLayout = ContainerLayoutType.GRID,
            containerColumns = 5, containerComponents = apps, autoHeight = true, showTitle = false)
        // Actual GRID rendering uses 36+14+2=52dp with no hidden vertical padding;
        // the legacy logical-grid adapter above intentionally keeps its old row contract.
        assertEquals(280f, componentDisplayHeightDp(parent, 264f, 24, configuredHeightUnitDp = 10), .01f)
        val shortcut = ComponentItem(type = ComponentType.CUSTOM_SHORTCUT, subtitle = "second line", h = 100)
        val scaled = arrangeContainerChildren(listOf(shortcut), 24, 240f, 10f,
            fontScale = 2f, iconColumns = 5, itemGapDp = 0, itemSizeDp = 80).single()
        assertEquals(10, scaled.h) // 36 icon + 28 label + 24 subtitle + 10 = 98dp, snapped up.
        val defaultSize = arrangeContainerChildren(listOf(shortcut), 24, 240f, 10f,
            fontScale = 2f, iconColumns = 5, itemGapDp = 0).single()
        assertEquals(10, defaultSize.h) // 34 + 28 + 24 + 10 = 96dp.
        val noSubtitle = arrangeContainerChildren(listOf(shortcut.copy(subtitle = "")), 24, 240f, 10f,
            fontScale = 2f, iconColumns = 5, itemGapDp = 0).single()
        assertEquals(8, noSubtitle.h)
    }
    @Test fun oldCustomIconWidthsDoNotTurnAnIconFolderIntoAMixedPopup() {
        val folder = ComponentItem(type = ComponentType.FOLDER, containerColumns = 5,
            containerComponents = List(25) { ComponentItem(type = ComponentType.APP_ICON, w = it + 1) })
        val defaults = folder.copy(containerComponents = folder.containerComponents.map { it.copy(w = it.type.defaultW) })
        assertEquals(containerPopupWidthDp(defaults, 600f), containerPopupWidthDp(folder, 600f), 0f)
        assertEquals(224f, containerPopupWidthDp(folder, 600f), 0f)
        assertEquals(224f, containerPopupWidthDp(folder, 247f), 0f)
    }
    @Test fun twentyFiveIconsUseFiveEqualColumnsAndRowsDespiteOldGeometry() {
        val saved = List(25) { n -> ComponentItem(id = "$n", type = if (n % 3 == 0) ComponentType.FOLDER else ComponentType.APP_ICON,
            x = 17, y = 99, w = n % 12 + 1, h = n + 1, autoHeight = false, showTitle = false) }
        val shown = arrangeContainerChildren(saved, 32, 320f, 10f, iconColumns = 5, itemGapDp = 0)
        assertEquals(1, shown.map { it.w to it.h }.distinct().size)
        assertEquals(listOf(0, 6, 12, 18, 24), shown.take(5).map { it.x })
        assertEquals(5, shown.map { it.y }.distinct().size)
        assertTrue(saved.all { it.x == 17 && it.y == 99 })
        assertEquals(25, saved.last().h)
    }

    @Test fun gridRetainsAllComplexChildTypesAndTheirContent() {
        val children = ComponentType.entries.map { type -> ComponentItem(id = type.name, type = type,
            w = 32, h = 12, textContent = "keep:${type.name}", showTitle = false) }
        val shown = arrangeContainerChildren(children, 32, 320f, 10f, iconColumns = 5)
        assertEquals(children.map { it.id }, shown.map { it.id })
        children.zip(shown).forEach { (saved, display) ->
            assertEquals(saved.type, display.type)
            assertEquals(saved.textContent, display.textContent)
            assertEquals(saved.containerComponents, display.containerComponents)
            if (!isContainerGridIcon(saved.type)) assertEquals(saved.w, display.w)
        }
        shown.zipWithNext().forEach { (a, b) ->
            if (a.y == b.y) assertTrue(a.x + a.w <= b.x) else assertTrue(a.y + a.h <= b.y)
        }
    }

    @Test fun folderScopeSuppressesGlobalEditAndAllowsLocalEditWithoutEnablingPanel() {
        assertFalse(containerEditingMode(panelEditing = true, folderEditing = false, locked = false))
        assertTrue(containerEditingMode(panelEditing = false, folderEditing = true, locked = false))
        assertTrue(containerEditingMode(panelEditing = true, folderEditing = null, locked = false))
        assertFalse(containerEditingMode(panelEditing = false, folderEditing = null, locked = false))
        assertFalse(containerEditingMode(panelEditing = true, folderEditing = true, locked = true))
    }

    @Test fun gridReorderUsesDisplayedCellsAndPreservesIdentity() {
        val cards = List(6) { ComponentItem(id = "$it", type = ComponentType.APP_ICON, showTitle = false) }
        val shown = arrangeContainerChildren(cards, 32, 320f, 10f, iconColumns = 5, itemGapDp = 0)
        val order = FolderDragOrder("0", shown.map { FolderDragSlot(it.id, it.x.toFloat(), it.y.toFloat(), it.w.toFloat(), it.h.toFloat()) })
        val target = shown.last()
        val preview = order.preview(target.x.toFloat(), target.y.toFloat())
        assertEquals(cards.map { it.id }.toSet(), preview.toSet())
        assertEquals("0", preview.last())
    }

    @Test fun nestedDrawerFootprintIsFiniteWhenAnOldAutomaticHeightIsHuge() {
        val drawer = ComponentItem(type = ComponentType.APP_DRAWER_CARD, h = Int.MAX_VALUE, autoHeight = true)
        assertTrue(componentDisplayHeightDp(drawer, 320f, 32).isFinite())
        assertTrue(componentDisplayHeightDp(drawer, 320f, 32) <= CONTAINER_VIEWPORT_MAX_DP)
    }

    @Test fun freeMeasurementPreservesManualGeometryAndDoesNotPersistAutomaticHeight() {
        val manual = ComponentItem(type = ComponentType.APP_ICON, x = 7, y = 30, w = 9, h = 17, autoHeight = false)
        val automatic = ComponentItem(type = ComponentType.TEXT_LABEL, x = 2, y = 51, w = 4, h = 1,
            autoHeight = true, textContent = "Many lines\nto measure\ninside a folder")
        val saved = listOf(manual, automatic)
        val shown = measureFreeContainerChildren(saved, 32, 320f, 10f, configuredHeightUnitDp = 10)
        assertEquals(manual, shown.first())
        assertEquals(automatic.x, shown.last().x)
        assertEquals(automatic.y, shown.last().y)
        assertEquals(automatic.w, shown.last().w)
        assertTrue(shown.last().h > automatic.h)
        assertEquals(1, saved.last().h)
    }
}
