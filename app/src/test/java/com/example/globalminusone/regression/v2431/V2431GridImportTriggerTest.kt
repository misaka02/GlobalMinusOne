package com.example.globalminusone.regression.v2431

import com.example.globalminusone.MemoryPreferences
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.ContainerLayoutType
import com.example.globalminusone.data.policy.GRID_SPAN_FULL_ROW
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.PageConfig
import com.example.globalminusone.data.PageLayoutType
import com.example.globalminusone.data.TriggerConfig
import com.example.globalminusone.data.TriggerPosition
import com.example.globalminusone.data.policy.arrangeContainerChildren
import com.example.globalminusone.data.policy.arrangePageGridComponents
import com.example.globalminusone.data.policy.containerGridSpanWidth
import com.example.globalminusone.data.policy.derivePageGridMetrics
import com.example.globalminusone.data.operations.flattenComponents
import com.example.globalminusone.data.panels.PanelsBackupImporter
import com.example.globalminusone.data.panels.PanelsPageImportMode
import com.example.globalminusone.data.policy.resizedPageGridSpan
import com.example.globalminusone.data.operations.validateComponentTrees
import com.example.globalminusone.ui.panel.editor.computeTriggerZonePreviewGeometry
import com.example.globalminusone.ui.panel.editor.draggedTriggerHeightPercent
import com.example.globalminusone.ui.panel.editor.draggedTriggerOffsetPercent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V2431GridImportTriggerTest {

    private fun genuineArchive() = PanelsBackupImporter.parse(
        requireNotNull(javaClass.getResourceAsStream("/panels/synthetic-panels-sample.bkp"))
    )

    @Test
    fun gridContainerArrangesMixedIconsSwitchesAndActionsIntoConfiguredColumns() {
        val children = listOf(
            ComponentItem(id = "app-1", type = ComponentType.APP_ICON, title = "App1"),
            ComponentItem(id = "wifi", type = ComponentType.SWITCH_WIFI, title = "WiFi"),
            ComponentItem(id = "shot", type = ComponentType.ACTION_SCREENSHOT, title = "截屏"),
            ComponentItem(id = "torch", type = ComponentType.SWITCH_TORCH, title = "手电筒")
        )

        val arranged3 = arrangeContainerChildren(
            children = children,
            columns = 32,
            widthDp = 320f,
            stepYDp = 10f,
            iconColumns = 3
        )
        assertEquals(listOf(0, 10, 20, 0), arranged3.map { it.x })
        assertTrue(arranged3.all { it.w == 10 })
        assertEquals(0, arranged3[0].y)
        assertEquals(0, arranged3[1].y)
        assertEquals(0, arranged3[2].y)
        assertTrue("Fourth tile must wrap to second row in 3-column grid", arranged3[3].y > 0)
        // Original saved widths remain untouched
        assertTrue(children.all { it.w == 8 })

        val arranged5 = arrangeContainerChildren(
            children = children + ComponentItem(id = "bt", type = ComponentType.SWITCH_BLUETOOTH, title = "蓝牙") +
                ComponentItem(id = "lock", type = ComponentType.ACTION_LOCK, title = "锁屏"),
            columns = 32,
            widthDp = 320f,
            stepYDp = 10f,
            iconColumns = 5
        )
        assertEquals(listOf(0, 6, 12, 18, 24, 0), arranged5.map { it.x })
        assertTrue(arranged5.last().y > arranged5.first().y)
    }

    @Test
    fun containerGridSpanWidthComputesSingleMultiAndFullRowSpans() {
        assertEquals(8, containerGridSpanWidth(spanCols = 1, totalSlots = 4, gridColumns = 32))
        assertEquals(16, containerGridSpanWidth(spanCols = 2, totalSlots = 4, gridColumns = 32))
        assertEquals(24, containerGridSpanWidth(spanCols = 3, totalSlots = 4, gridColumns = 32))
        assertEquals(32, containerGridSpanWidth(spanCols = 4, totalSlots = 4, gridColumns = 32))
        assertEquals(10, containerGridSpanWidth(spanCols = 1, totalSlots = 3, gridColumns = 32))
        assertEquals(32, containerGridSpanWidth(spanCols = 3, totalSlots = 3, gridColumns = 32))
    }

    @Test
    fun panelsPageImportsIntoGridContainerAndGridFolderPreservingAllItemsAndColumns() {
        assertEquals(
            listOf("独立页面", "网格容器", "网格文件夹"),
            PanelsPageImportMode.entries.map { it.label }
        )
        val archive = genuineArchive()

        val gridContainer = archive.convertPageAsContainer(panelId = 2, asFolder = false, gridColumns = 32)
        assertEquals(ComponentType.CONTAINER_BOX, gridContainer.type)
        assertEquals(ContainerLayoutType.GRID, gridContainer.containerLayout)
        assertTrue(gridContainer.showTitle)
        assertTrue(gridContainer.autoHeight)
        assertEquals(32, gridContainer.w)
        // Panel 2 has 103 total items across top-level and nested folders
        assertEquals(103, gridContainer.containerComponents.flattenComponents().size)
        assertEquals(
            listOf("DemoApp01", "DemoApp02", "DemoApp03", "DemoApp04"),
            gridContainer.containerComponents.take(4).map { it.title }
        )

        val gridFolder = archive.convertPageAsContainer(panelId = 2, asFolder = true, gridColumns = 32)
        assertEquals(ComponentType.FOLDER, gridFolder.type)
        assertEquals(ContainerLayoutType.GRID, gridFolder.containerLayout)
        assertFalse(gridFolder.showTitle)
        assertEquals(8, gridFolder.w)
        assertEquals(103, gridFolder.containerComponents.flattenComponents().size)

        // App-drawer page also converts cleanly into a valid container tree
        val drawerContainer = archive.convertPageAsContainer(panelId = 6, asFolder = false, gridColumns = 32)
        assertEquals(1, drawerContainer.containerComponents.size)
        assertEquals(ComponentType.APP_DRAWER_CARD, drawerContainer.containerComponents.single().type)

        validateComponentTrees(
            listOf(PageConfig(name = "Imported Containers", components = listOf(gridContainer, gridFolder, drawerContainer)))
        )
    }

    @Test
    fun appendImportedContainersPersistsAtomicallyWithoutOverlapsAndRejectsInvalidStates() {
        val prefs = MemoryPreferences()
        val repo = MinusOneRepository(prefs)
        val archive = genuineArchive()
        val linearPageId = repo.state.value.pages.first { it.layoutType == PageLayoutType.LINEAR }.id
        val freePageIdx = repo.addPage(PageLayoutType.FREE, "测试自由页")
        val freePageId = repo.state.value.pages[freePageIdx].id

        // 0) Import a Grid Container onto a LINEAR page (expands to full width)
        val linearBox = archive.convertPageAsContainer(panelId = 13, asFolder = false, gridColumns = 32)
        assertTrue(repo.appendImportedContainers(listOf(linearBox), targetPageId = linearPageId))
        val placedLinear = repo.state.value.pages.first { it.id == linearPageId }.components.first { it.id == linearBox.id }
        assertEquals(32, placedLinear.w)
        assertEquals(ContainerLayoutType.GRID, placedLinear.containerLayout)

        // 1) Import two Panels pages as Grid Folders side-by-side onto a FREE page
        val f1 = archive.convertPageAsContainer(panelId = 13, asFolder = true, gridColumns = 32)
        val f2 = archive.convertPageAsContainer(panelId = 2, asFolder = true, gridColumns = 32)
        assertTrue(repo.appendImportedContainers(listOf(f1, f2), targetPageId = freePageId))

        val afterFolders = repo.state.value.pages.first { it.id == freePageId }.components
        val placedF1 = afterFolders.first { it.id == f1.id }
        val placedF2 = afterFolders.first { it.id == f2.id }
        assertEquals(0, placedF1.x)
        assertEquals(8, placedF2.x)
        assertEquals(placedF1.y, placedF2.y)

        // 2) Import a Panels page as a full-width Grid Container below existing cards on FREE page
        val box = archive.convertPageAsContainer(panelId = 13, asFolder = false, gridColumns = 32)
        assertTrue(repo.appendImportedContainers(listOf(box), targetPageId = freePageId))
        val placedBox = repo.state.value.pages.first { it.id == freePageId }.components.first { it.id == box.id }
        assertTrue(placedBox.y > placedF1.y)
        assertEquals(ContainerLayoutType.GRID, placedBox.containerLayout)

        // 3) Reload from preferences proves synchronous commit
        val reloaded = MinusOneRepository(prefs)
        assertTrue(reloaded.state.value.pages.first { it.id == freePageId }.components.any { it.id == box.id })

        // 4) Rejects import while in editMode or layoutLocked
        repo.setEditMode(true)
        assertFalse(
            repo.appendImportedContainers(
                listOf(archive.convertPageAsContainer(panelId = 13, asFolder = false, gridColumns = 32)),
                targetPageId = freePageId
            )
        )
        repo.finishPanelEdit(save = false)

        repo.setLayoutLocked(true)
        assertFalse(
            repo.appendImportedContainers(
                listOf(archive.convertPageAsContainer(panelId = 13, asFolder = false, gridColumns = 32)),
                targetPageId = freePageId
            )
        )
    }

    @Test
    fun triggerZoneVerticalDragMathAndTransactionalEditSessionRoundTrip() {
        val base = TriggerConfig(
            triggerPosition = TriggerPosition.LEFT,
            touchWidthPercent = 3,
            touchHeightPercent = 20,
            touchOffsetPercent = 50
        )
        val geom = computeTriggerZonePreviewGeometry(
            position = TriggerPosition.LEFT,
            screenWidthPx = 1080f,
            screenHeightPx = 2400f,
            density = 3f,
            config = base
        )
        // height = 20% of 2400 = 480px; movableSpan = 1920px; y = 50% of 1920 = 960px
        assertEquals(480f, geom.heightPx, 0.01f)
        assertEquals(1920f, geom.movableSpanPx, 0.01f)
        assertEquals(960f, geom.yPx, 0.01f)

        // Dragging down by +480px (+25% of 1920px) moves 50% -> 75%
        assertEquals(75, draggedTriggerOffsetPercent(50, deltaPx = 480f, movableSpanPx = geom.movableSpanPx))
        // Dragging past top or bottom clamps cleanly to 0..100
        assertEquals(0, draggedTriggerOffsetPercent(50, deltaPx = -5000f, movableSpanPx = geom.movableSpanPx))
        assertEquals(100, draggedTriggerOffsetPercent(50, deltaPx = 5000f, movableSpanPx = geom.movableSpanPx))
        assertEquals(30, draggedTriggerHeightPercent(20, deltaHeightPx = 240f, totalAxisPx = 2400f))

        // Transactional edit session: Discard reverts dragged trigger offset; Save commits it
        val prefs = MemoryPreferences()
        val repo = MinusOneRepository(prefs)
        val initialOffset = repo.state.value.triggerConfig.touchOffsetPercent

        repo.setEditMode(true)
        repo.updateTriggerConfig { it.withTouchGeometry(offsetPercent = 25, heightPercent = 25) }
        assertTrue(repo.hasPanelEditChanges())
        assertEquals(25, repo.state.value.triggerConfig.touchOffsetPercent)
        assertEquals(0.25f, repo.state.value.triggerConfig.verticalPositionPercent, 0.001f)

        // Discard changes
        repo.finishPanelEdit(save = false)
        assertEquals(initialOffset, repo.state.value.triggerConfig.touchOffsetPercent)

        // Drag again and Save
        repo.setEditMode(true)
        repo.updateTriggerConfig { it.withTouchGeometry(offsetPercent = 32, heightPercent = 18) }
        repo.finishPanelEdit(save = true)
        assertEquals(32, repo.state.value.triggerConfig.touchOffsetPercent)
        assertEquals(18, repo.state.value.triggerConfig.touchHeightPercent)
        assertEquals(32, MinusOneRepository(prefs).state.value.triggerConfig.touchOffsetPercent)
    }

    @Test
    fun pageGridLayoutDerivesColumnsAutomaticallyFromWidthIconSizeAndGap() {
        // Standard 70% width negative-one panel inner width (~268dp)
        assertEquals(5, derivePageGridMetrics(availableWidthDp = 268f, itemSizeDp = 48, itemGapDp = 4).columns)
        assertEquals(4, derivePageGridMetrics(availableWidthDp = 268f, itemSizeDp = 58, itemGapDp = 8).columns)
        assertEquals(3, derivePageGridMetrics(availableWidthDp = 268f, itemSizeDp = 76, itemGapDp = 8).columns)
        assertEquals(2, derivePageGridMetrics(availableWidthDp = 268f, itemSizeDp = 104, itemGapDp = 8).columns)

        // Widening the panel automatically expands column count without any manual column option
        assertEquals(5, derivePageGridMetrics(availableWidthDp = 350f, itemSizeDp = 58, itemGapDp = 8).columns)
        // Increasing spacing reduces column count naturally when tight
        assertEquals(3, derivePageGridMetrics(availableWidthDp = 268f, itemSizeDp = 58, itemGapDp = 18).columns)
    }

    @Test
    fun pageGridMultiCellOccupancyPacksIconsAlongsideWidgetsWithoutOverlap() {
        val items = listOf(
            ComponentItem(id = "widget", type = ComponentType.APP_WIDGET, title = "时钟小部件", gridSpanX = 2, gridSpanY = 2),
            ComponentItem(id = "icon1", type = ComponentType.APP_ICON, title = "微信"),
            ComponentItem(id = "icon2", type = ComponentType.SWITCH_WIFI, title = "WiFi"),
            ComponentItem(id = "icon3", type = ComponentType.ACTION_SCREENSHOT, title = "截屏"),
            ComponentItem(id = "icon4", type = ComponentType.FOLDER, title = "工具夹"),
            ComponentItem(id = "slider", type = ComponentType.BRIGHTNESS_BAR, title = "亮度", gridSpanX = GRID_SPAN_FULL_ROW, gridSpanY = 1)
        )

        val result = arrangePageGridComponents(
            components = items,
            availableWidthDp = 268f,
            itemSizeDp = 58,
            itemGapDp = 8
        )
        assertEquals(4, result.metrics.columns)

        val byId = result.placements.associateBy { it.id }
        val widget = byId.getValue("widget")
        assertEquals(0, widget.col); assertEquals(0, widget.row)
        assertEquals(2, widget.spanX); assertEquals(2, widget.spanY)

        // Four 1x1 items pack neatly into cols 2..3 of rows 0 and 1 right beside the 2x2 widget!
        assertEquals(2 to 0, byId.getValue("icon1").let { it.col to it.row })
        assertEquals(3 to 0, byId.getValue("icon2").let { it.col to it.row })
        assertEquals(2 to 1, byId.getValue("icon3").let { it.col to it.row })
        assertEquals(3 to 1, byId.getValue("icon4").let { it.col to it.row })

        // Full-row slider lands cleanly on row 2 spanning all 4 columns
        val slider = byId.getValue("slider")
        assertEquals(0, slider.col); assertEquals(2, slider.row)
        assertEquals(4, slider.spanX); assertEquals(1, slider.spanY)

        // Pairwise non-overlap invariant across all placed rectangles
        for (i in result.placements.indices) {
            for (j in i + 1 until result.placements.size) {
                val a = result.placements[i]
                val b = result.placements[j]
                val disjoint = (a.col + a.spanX <= b.col) || (b.col + b.spanX <= a.col) ||
                    (a.row + a.spanY <= b.row) || (b.row + b.spanY <= a.row)
                assertTrue("Placements ${a.id} and ${b.id} must not overlap", disjoint)
            }
        }

        // Resize drag math clamps cleanly to 1..maxColumns and 1..maxRows
        assertEquals(3 to 2, resizedPageGridSpan(2, 2, dxPx = 140f, dyPx = 20f, pitchXPx = 130f, pitchYPx = 130f, maxColumns = 4))
        assertEquals(4 to 1, resizedPageGridSpan(2, 2, dxPx = 999f, dyPx = -999f, pitchXPx = 130f, pitchYPx = 130f, maxColumns = 4))
    }

    @Test
    fun folderAndContainerGridRespectIconSizeGapAndChildMultiCellSpan() {
        // 4-column container on narrow 260dp card with large 88dp icon size + 8dp gap never wraps prematurely
        val fourIcons = List(4) { idx ->
            ComponentItem(id = "ic-$idx", type = ComponentType.APP_ICON, title = "App$idx")
        }
        val arrangedIcons = arrangeContainerChildren(
            children = fourIcons,
            columns = 32,
            widthDp = 260f,
            stepYDp = 8f,
            iconColumns = 4,
            itemGapDp = 8,
            itemSizeDp = 88
        )
        assertEquals("All 4 icons must stay on the first row when columns=4", listOf(0, 0, 0, 0), arrangedIcons.map { it.y })
        assertTrue("88dp itemSize scales row height up", arrangedIcons.all { it.h >= 11 })

        // Child multi-cell span (2 cols x 2 rows) inside a 4-column container
        val mixed = listOf(
            ComponentItem(id = "stat", type = ComponentType.STAT_RAM, gridSpanX = 2, gridSpanY = 2),
            ComponentItem(id = "wifi", type = ComponentType.SWITCH_WIFI),
            ComponentItem(id = "bt", type = ComponentType.SWITCH_BLUETOOTH),
            ComponentItem(id = "next", type = ComponentType.APP_ICON)
        )
        val arrangedMixed = arrangeContainerChildren(
            children = mixed,
            columns = 32,
            widthDp = 320f,
            stepYDp = 10f,
            iconColumns = 4,
            itemGapDp = 0
        )
        assertEquals(16, arrangedMixed[0].w)
        assertTrue(arrangedMixed[0].h >= 8)
        assertEquals(0, arrangedMixed[0].y)
        assertEquals(0, arrangedMixed[1].y)
        assertEquals(0, arrangedMixed[2].y)
        // Fifth slot wraps to second row
        assertTrue(arrangedMixed[3].y > 0)
    }

    @Test
    fun repositoryPageLayoutThreeWaySwitchAndGridStylePersistenceRoundTrip() {
        val prefs = MemoryPreferences()
        val repo = MinusOneRepository(prefs)

        // Page 0 starts LINEAR -> toggle cycles LINEAR -> GRID -> FREE -> LINEAR
        assertEquals(PageLayoutType.LINEAR, repo.state.value.pages[0].layoutType)
        repo.togglePageLayoutType(0)
        assertEquals(PageLayoutType.GRID, repo.state.value.pages[0].layoutType)
        repo.togglePageLayoutType(0)
        assertEquals(PageLayoutType.FREE, repo.state.value.pages[0].layoutType)
        repo.togglePageLayoutType(0)
        assertEquals(PageLayoutType.LINEAR, repo.state.value.pages[0].layoutType)

        // Add dedicated GRID page, customize gridItemSizeDp & gridItemGapDp, and set multi-cell span on a widget
        val gridIdx = repo.addPage(PageLayoutType.GRID, "自定义网格页")
        assertTrue(gridIdx >= 0)
        repo.updatePageGridStyle(gridIdx, itemSizeDp = 76, itemGapDp = 12)
        val widget = ComponentItem(id = "w1", type = ComponentType.NOTE_CARD, title = "大便签", gridSpanX = 2, gridSpanY = 3)
        repo.addComponentToPage(gridIdx, widget)

        val reloaded = MinusOneRepository(prefs)
        val savedGridPage = reloaded.state.value.pages[gridIdx]
        assertEquals(PageLayoutType.GRID, savedGridPage.layoutType)
        assertEquals(76, savedGridPage.gridItemSizeDp)
        assertEquals(12, savedGridPage.gridItemGapDp)
        val savedWidget = savedGridPage.components.first { it.id == "w1" }
        assertEquals(2, savedWidget.gridSpanX)
        assertEquals(3, savedWidget.gridSpanY)
    }
}
