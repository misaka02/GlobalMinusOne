package com.example.globalminusone.regression.v2438

import com.example.globalminusone.data.*
import com.example.globalminusone.ui.panel.previousPropertySection
import org.junit.Assert.*
import org.junit.Test

class FolderSettingsAndGeometryTest {
    @Test fun directFolderSettingsDoNotInventParentScreensOnBack() {
        for (entry in listOf("内部排列", "图标与文字留白", "文件夹窗口", "")) {
            assertNull(previousPropertySection(entry, entry))
        }
    }

    @Test fun ordinaryPropertyNavigationStillVisitsItsRealParent() {
        assertEquals("容器与行为", previousPropertySection("内部排列", ""))
        assertEquals("", previousPropertySection("容器与行为", ""))
        assertNull(previousPropertySection("", ""))
    }

    @Test fun fileShortcutUsesOneLabeledIconSlotInsteadOfAClippedFullWidthRow() {
        val file = ComponentItem(id = "file", type = ComponentType.FILE_SHORTCUT, title = "note1.txt", intentUri = "content://test/file")
        val app = ComponentItem(id = "app", type = ComponentType.APP_ICON)
        val bounds = containerGridChildBounds(listOf(file, app), 32, 320f, 10f, iconColumns = 4, itemGapDp = 4)
        assertEquals(77f, bounds[0].width, 0f)
        assertEquals(0f, bounds[1].y, 0f)
        assertTrue(bounds[0].height >= 34f + 14f + 2f)
        assertEquals(ComponentType.FILE_SHORTCUT.defaultW, file.w)
        assertEquals("note1.txt", bounds[0].item.title)
        assertEquals("content://test/file", bounds[0].item.intentUri)
    }

    @Test fun newLinearStatisticsHaveRoomForHeaderAndRealContent() {
        val original = ComponentItem(type = ComponentType.STAT_BATTERY_BAR, x = 3, y = 12, showTitle = true)
        val prepared = prepareStatisticsCard(original, 32, 296f, 0, 1.3f, true)
        assertEquals(32, prepared.w)
        assertTrue(prepared.h * (296f / 32) >= 58f * 1.3f + 32f + 16f)
        assertEquals(original.id, prepared.id)
        assertEquals(3, prepared.x)
        assertEquals(12, prepared.y)
        assertEquals(6, original.h)
    }

    @Test fun creationGeometryAdaptsToNarrowHostsAndExplicitHeightUnits() {
        val item = ComponentItem(type = ComponentType.STAT_RAM)
        val wide = prepareStatisticsCard(item, 32, 320f, 0, 1f, false)
        val narrow = prepareStatisticsCard(item, 32, 160f, 0, 1f, false)
        assertTrue(narrow.h > wide.h)
        assertTrue(narrow.w >= wide.w)
        val explicit = prepareStatisticsCard(item, 32, 320f, 20, 1f, false)
        assertTrue(explicit.h * 20 >= 104)
    }

    @Test fun nonStatisticsAndSerializationDefaultsAreNotMigrated() {
        val note = ComponentItem(type = ComponentType.NOTE_CARD, w = 7, h = 11)
        assertSame(note, prepareStatisticsCard(note, 32, 180f, 0, 1.3f, true))
        assertEquals(10, ComponentType.STAT_BATTERY_BAR.defaultW)
        assertEquals(6, ComponentType.STAT_BATTERY_BAR.defaultH)
    }

    @Test fun newFileCardsAlsoFitTheirLabelOutsideIconGrids() {
        val original = ComponentItem(type = ComponentType.FILE_SHORTCUT, intentUri = "content://test/note")
        val prepared = prepareFileShortcutCard(original, 32, 296f, 0, 1.3f)
        assertTrue(prepared.h * (296f / 32) >= 34f + 14f * 1.3f + 10f + 16f)
        assertEquals(original.w, prepared.w)
        assertEquals(original.intentUri, prepared.intentUri)
        assertEquals(7, original.h)
    }
}
