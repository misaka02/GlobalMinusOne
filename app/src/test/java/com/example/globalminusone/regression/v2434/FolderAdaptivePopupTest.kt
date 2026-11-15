package com.example.globalminusone.regression.v2434

import com.example.globalminusone.data.*
import org.junit.Assert.*
import org.junit.Test

class FolderAdaptivePopupTest {
    private fun folder(icon: Int = 48, gap: Int = 4) = ComponentItem(type = ComponentType.FOLDER,
        containerColumns = 4, containerIconSizeDp = icon, containerItemGapDp = gap,
        containerComponents = List(8) { ComponentItem(id = "child-$it", type = ComponentType.APP_ICON) })

    @Test fun automaticWidthChangesWithActual32Then48Then64DpIcons() {
        assertEquals(listOf(180f, 244f, 308f), listOf(32, 48, 64).map {
            containerPopupWidthDp(folder(icon = it), 800f)
        })
    }

    @Test fun automaticWidthUsesEveryDpOfTheRequestedGaps() {
        assertEquals(listOf(244f, 256f, 268f), listOf(4, 8, 12).map {
            containerPopupWidthDp(folder(gap = it), 800f)
        })
    }

    @Test fun zeroIconSizeUsesTheRealDefaultForEachChildType() {
        val apps = folder(icon = 0, gap = 0)
        assertEquals(184f, containerPopupWidthDp(apps, 800f), .001f)
        val shortcuts = apps.copy(containerComponents = apps.containerComponents.map { it.copy(type = ComponentType.CUSTOM_SHORTCUT) })
        assertEquals(180f, containerPopupWidthDp(shortcuts, 800f), .001f)
        val nestedFolders = apps.copy(containerComponents = apps.containerComponents.map { it.copy(type = ComponentType.FOLDER) })
        assertEquals(288f, containerPopupWidthDp(nestedFolders, 800f), .001f)
    }

    @Test fun legacyCellWidthAndExplicitWindowDimensionsStillTakeEffect() {
        val oldCell = folder().copy(containerItemSizeDp = 96)
        assertEquals(420f, containerPopupWidthDp(oldCell, 800f), .001f)
        assertEquals(548f, containerPopupWidthDp(oldCell.copy(containerIconSizeDp = 124), 800f), .001f)
        val manualWindow = oldCell.copy(containerPopupWidthDp = 320, containerPopupHeightDp = 240)
        assertEquals(320f, containerPopupWidthDp(manualWindow, 800f), .001f)
        assertEquals(240f, containerPopupHeightDp(manualWindow, 800f), .001f)
        assertEquals(200f, containerPopupWidthDp(manualWindow, 200f), .001f)
        assertEquals(180f, containerPopupHeightDp(manualWindow, 180f), .001f)
        assertEquals(800f, containerPopupHeightDp(manualWindow.copy(containerPopupHeightDp = 0), 800f), .001f)
    }

    @Test fun shortContentDoesNotReplaceTheConfiguredFiveColumns() {
        val configured = folder().copy(containerColumns = 5, containerComponents = folder().containerComponents.take(2))
        assertEquals(2, containerPopupColumns(configured))
        assertEquals(5, configured.containerColumns)
        assertEquals(5, configured.copy(containerLayout = ContainerLayoutType.FREE).containerColumns)
        assertEquals(5, containerPopupColumns(configured.copy(containerComponents = folder().containerComponents)))
    }

    @Test fun manualNarrowWindowsKeepExactDpScrollableContentAndFreeGeometry() {
        val manual = folder(icon = 128, gap = 12).copy(containerPopupWidthDp = 260)
        assertEquals(260f, containerPopupWidthDp(manual, 800f), .001f)
        assertEquals(564f, containerGridContentWidthDp(manual, 236f), .001f)
        val freeChild = ComponentItem(type = ComponentType.CONTAINER_BOX, x = 7, y = 31, w = 13, h = 17,
            autoHeight = false, containerComponents = manual.containerComponents)
        val free = manual.copy(containerLayout = ContainerLayoutType.FREE, containerComponents = listOf(freeChild))
        assertEquals(260f, containerPopupWidthDp(free, 800f), .001f)
        assertEquals(listOf(freeChild), free.containerComponents)
        assertEquals(manual.containerComponents, free.containerComponents.single().containerComponents)
    }
}
