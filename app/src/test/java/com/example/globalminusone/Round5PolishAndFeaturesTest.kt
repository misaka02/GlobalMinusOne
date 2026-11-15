package com.example.globalminusone

import com.example.globalminusone.data.*
import com.example.globalminusone.ui.panel.PanelGestureAxis
import com.example.globalminusone.ui.panel.panelScrollAxisForComponent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Round5PolishAndFeaturesTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun componentOrientation_resolvesExplicitAndAutoDimensions() {
        assertTrue(supportsVerticalOrientation(ComponentType.BRIGHTNESS_BAR))
        assertTrue(supportsVerticalOrientation(ComponentType.VOLUME_BAR))
        assertTrue(supportsVerticalOrientation(ComponentType.STAT_BATTERY_BAR))
        assertTrue(supportsVerticalOrientation(ComponentType.STAT_RAM))
        assertTrue(supportsVerticalOrientation(ComponentType.STAT_ROM))
        assertFalse(supportsVerticalOrientation(ComponentType.APP_ICON))

        val explicitVert = ComponentItem(
            id = "b1",
            type = ComponentType.BRIGHTNESS_BAR,
            orientation = ComponentOrientation.VERTICAL,
            w = 32,
            h = 5
        )
        assertTrue(resolveComponentVertical(explicitVert, widthDp = 240f, heightDp = 44f))

        val explicitHoriz = ComponentItem(
            id = "b2",
            type = ComponentType.VOLUME_BAR,
            orientation = ComponentOrientation.HORIZONTAL,
            w = 6,
            h = 18
        )
        assertFalse(resolveComponentVertical(explicitHoriz, widthDp = 64f, heightDp = 180f))

        val autoSlider = ComponentItem(
            id = "b3",
            type = ComponentType.BRIGHTNESS_BAR,
            orientation = ComponentOrientation.AUTO,
            w = 32,
            h = 5
        )
        assertFalse(resolveComponentVertical(autoSlider, widthDp = 240f, heightDp = 50f))
        assertTrue(resolveComponentVertical(autoSlider, widthDp = 60f, heightDp = 160f))
    }

    @Test
    fun verticalSlider_defaultsToTallGridSpanAndHeight() {
        val metrics = derivePageGridMetrics(availableWidthDp = 268f, columnCount = 4)
        val vertBrightness = ComponentItem(
            id = "vb",
            type = ComponentType.BRIGHTNESS_BAR,
            orientation = ComponentOrientation.VERTICAL
        )
        val vertStat = ComponentItem(
            id = "vs",
            type = ComponentType.STAT_RAM,
            orientation = ComponentOrientation.VERTICAL
        )
        val horizBrightness = ComponentItem(
            id = "hb",
            type = ComponentType.BRIGHTNESS_BAR,
            orientation = ComponentOrientation.HORIZONTAL
        )

        assertEquals(1 to 3, resolvePageGridSpan(vertBrightness, metrics))
        assertEquals(1 to 2, resolvePageGridSpan(vertStat, metrics))
        assertEquals(4 to 1, resolvePageGridSpan(horizBrightness, metrics))

        val vertHeight = componentDisplayHeightDp(
            item = vertBrightness,
            outerWidthDp = 64f,
            gridColumns = 32,
            configuredHeightUnitDp = 0
        )
        assertTrue(vertHeight >= verticalComponentMinHeightDp(ComponentType.BRIGHTNESS_BAR))
    }

    @Test
    fun scrollGestureAxis_switchesCleanlyBetweenHorizontalAndVerticalSliders() {
        val horizVol = ComponentItem(type = ComponentType.VOLUME_BAR, orientation = ComponentOrientation.HORIZONTAL)
        val vertVol = ComponentItem(type = ComponentType.VOLUME_BAR, orientation = ComponentOrientation.VERTICAL)
        val folder = ComponentItem(type = ComponentType.FOLDER)

        assertEquals(PanelGestureAxis.Horizontal, panelScrollAxisForComponent(horizVol, 200f, 48f))
        assertEquals(PanelGestureAxis.Vertical, panelScrollAxisForComponent(vertVol, 60f, 160f))
        assertEquals(PanelGestureAxis.Both, panelScrollAxisForComponent(folder, 60f, 60f))
    }

    @Test
    fun folderPreviewMode_usesSingleFullPreviewInsteadOf2x2ForOneItem() {
        assertEquals(FolderPreviewMode.EMPTY, resolveFolderPreviewMode(0))
        assertEquals(FolderPreviewMode.SINGLE, resolveFolderPreviewMode(1))
        assertEquals(FolderPreviewMode.GRID_2X2, resolveFolderPreviewMode(2))
        assertEquals(FolderPreviewMode.GRID_2X2, resolveFolderPreviewMode(4))
    }

    @Test
    fun orientationSerialization_preservesStateJsonV5Compatibility() {
        val legacyJson = """{"id":"legacy1","type":"BRIGHTNESS_BAR","title":"亮度条"}"""
        val decoded = json.decodeFromString<ComponentItem>(legacyJson)
        assertEquals(ComponentOrientation.AUTO, decoded.orientation)

        val vert = ComponentItem(
            id = "vert1",
            type = ComponentType.VOLUME_BAR,
            orientation = ComponentOrientation.VERTICAL
        )
        val roundTripped = json.decodeFromString<ComponentItem>(json.encodeToString(vert))
        assertEquals(ComponentOrientation.VERTICAL, roundTripped.orientation)
    }

    @Test
    fun pagePresets_createDistinctComponents_survivePersistence_andRespectLocksAndLimits() {
        val prefs = MemoryPreferences()
        val repository = MinusOneRepository(prefs)
        val initialCount = repository.state.value.pages.size

        val controlIdx = repository.addPagePreset(PagePresetOption.CONTROL_CENTER, "我的控制页")
        assertEquals(initialCount, controlIdx)
        val controlPage = repository.state.value.pages[controlIdx]
        assertEquals("我的控制页", controlPage.name)
        assertEquals(PageLayoutType.GRID, controlPage.layoutType)

        val brightness = controlPage.components.first { it.type == ComponentType.BRIGHTNESS_BAR }
        val volume = controlPage.components.first { it.type == ComponentType.VOLUME_BAR }
        assertEquals(ComponentOrientation.VERTICAL, brightness.orientation)
        assertEquals(ComponentOrientation.VERTICAL, volume.orientation)
        assertEquals(1, brightness.gridSpanX)
        assertEquals(3, brightness.gridSpanY)

        // Adding the same preset twice generates distinct IDs
        val controlIdx2 = repository.addPagePreset(PagePresetOption.CONTROL_CENTER)
        val ids1 = controlPage.components.map { it.id }.toSet()
        val ids2 = repository.state.value.pages[controlIdx2].components.map { it.id }.toSet()
        assertTrue(ids1.intersect(ids2).isEmpty())

        // App folders preset contains both a 1-item folder (SINGLE preview) and multi-item folder (GRID_2X2)
        val foldersIdx = repository.addPagePreset(PagePresetOption.APP_FOLDERS)
        val folderItems = repository.state.value.pages[foldersIdx].components.filter { it.type == ComponentType.FOLDER }
        assertTrue(folderItems.any { resolveFolderPreviewMode(it.containerComponents.size + it.containerActions.size) == FolderPreviewMode.SINGLE })
        assertTrue(folderItems.any { resolveFolderPreviewMode(it.containerComponents.size + it.containerActions.size) == FolderPreviewMode.GRID_2X2 })

        // Other built-in presets create valid pages and persist across repository reload
        val prodIdx = repository.addPagePreset(PagePresetOption.PRODUCTIVITY)
        assertEquals(PageLayoutType.LINEAR, repository.state.value.pages[prodIdx].layoutType)
        val monIdx = repository.addPagePreset(PagePresetOption.SYSTEM_MONITOR)
        assertTrue(repository.state.value.pages[monIdx].components.any {
            it.type == ComponentType.STAT_RAM && it.orientation == ComponentOrientation.VERTICAL
        })

        val reloaded = MinusOneRepository(prefs)
        assertEquals(repository.state.value.pages.size, reloaded.state.value.pages.size)
        assertEquals(
            ComponentOrientation.VERTICAL,
            reloaded.state.value.pages[controlIdx].components.first { it.type == ComponentType.BRIGHTNESS_BAR }.orientation
        )

        // Layout lock blocks preset creation and leaves state intact
        val countBeforeLock = repository.state.value.pages.size
        repository.setLayoutLocked(true)
        assertEquals(-1, repository.addPagePreset(PagePresetOption.CONTROL_CENTER))
        assertEquals(countBeforeLock, repository.state.value.pages.size)
        repository.setLayoutLocked(false)

        // Blank preset delegating to addPage also works cleanly
        val blankIdx = repository.addPagePreset(PagePresetOption.BLANK_GRID, "空白自定义页")
        assertEquals(countBeforeLock, blankIdx)
        assertEquals("空白自定义页", repository.state.value.pages[blankIdx].name)
        assertEquals(PageLayoutType.GRID, repository.state.value.pages[blankIdx].layoutType)
    }

    @Test
    fun folderPopup_usesCompactWidthForVerticalSlidersWhileKeepingWideCardsAt360Dp() {
        val explicitVertBrightness = ComponentItem(
            id = "vb1",
            type = ComponentType.BRIGHTNESS_BAR,
            title = "竖排亮度条",
            orientation = ComponentOrientation.VERTICAL,
            w = 6,
            h = 16,
            gridSpanX = 1,
            gridSpanY = 3
        )
        val autoVertBrightness = ComponentItem(
            id = "vb2",
            type = ComponentType.BRIGHTNESS_BAR,
            title = "自动竖排亮度条",
            orientation = ComponentOrientation.AUTO,
            w = 4,
            h = 16,
            gridSpanX = 1,
            gridSpanY = 3
        )
        val vertVolume = ComponentItem(
            id = "vv1",
            type = ComponentType.VOLUME_BAR,
            title = "竖排音量条",
            orientation = ComponentOrientation.VERTICAL,
            w = 32,
            h = 16,
            gridSpanX = 1,
            gridSpanY = 3
        )
        val wifiTile = ComponentItem(
            id = "sw1",
            type = ComponentType.SWITCH_WIFI,
            title = "Wi-Fi",
            gridSpanX = 1,
            gridSpanY = 1
        )
        val textNote = ComponentItem(
            id = "txt1",
            type = ComponentType.TEXT_LABEL,
            title = "便签",
            gridSpanX = 1,
            gridSpanY = 1
        )

        assertTrue(isContainerPopupCompactItem(explicitVertBrightness))
        assertTrue(isContainerPopupCompactItem(autoVertBrightness))
        assertTrue(isContainerPopupCompactItem(vertVolume))
        assertTrue(isContainerPopupCompactItem(wifiTile))
        assertFalse(isContainerPopupCompactItem(textNote))

        val singleVertFolder = ComponentItem(
            id = "f_single_vb",
            type = ComponentType.FOLDER,
            containerLayout = ContainerLayoutType.GRID,
            containerColumns = 3,
            containerComponents = listOf(explicitVertBrightness)
        )
        assertEquals(180f, containerPopupWidthDp(singleVertFolder, 360f), 0.01f)

        val autoVertFolder = singleVertFolder.copy(id = "f_auto_vb", containerComponents = listOf(autoVertBrightness))
        assertEquals(180f, containerPopupWidthDp(autoVertFolder, 360f), 0.01f)

        val mixedCompactFolder = singleVertFolder.copy(
            id = "f_mixed_compact",
            containerComponents = listOf(explicitVertBrightness, vertVolume, wifiTile)
        )
        assertTrue(containerPopupWidthDp(mixedCompactFolder, 360f) < 300f)

        val wideTextFolder = singleVertFolder.copy(id = "f_text", containerComponents = listOf(textNote))
        assertEquals(360f, containerPopupWidthDp(wideTextFolder, 360f), 0.01f)

        val singleColumnVertFolder = singleVertFolder.copy(id = "f_single_col_vb", containerColumns = 1)
        assertEquals(104f, containerPopupWidthDp(singleColumnVertFolder, 360f), 0.01f)

        val explicitNarrowFolder = singleVertFolder.copy(id = "f_manual_narrow", containerPopupWidthDp = 96)
        assertEquals(96f, containerPopupWidthDp(explicitNarrowFolder, 360f), 0.01f)
        assertEquals(
            92f,
            containerPopupWidthDp(singleVertFolder.copy(id = "f_manual_floor", containerPopupWidthDp = 40), 360f),
            0.01f
        )

        val narrowDensity = com.example.globalminusone.ui.panel.folderToolbarDensity(80f)
        assertTrue(narrowDensity.compactHeader)
        assertTrue(narrowDensity.compactMenu)
        assertTrue(narrowDensity.ultraCompactHeader)
        val mediumDensity = com.example.globalminusone.ui.panel.folderToolbarDensity(156f)
        assertTrue(mediumDensity.compactHeader)
        assertTrue(mediumDensity.compactMenu)
        assertFalse(mediumDensity.ultraCompactHeader)
        val standardDensity = com.example.globalminusone.ui.panel.folderToolbarDensity(336f)
        assertFalse(standardDensity.compactHeader)
        assertFalse(standardDensity.compactMenu)
        assertFalse(standardDensity.ultraCompactHeader)
    }

    @Test
    fun appContextMenuCardTypes_includeWebsiteAndFileShortcuts() {
        assertTrue(com.example.globalminusone.ui.panel.isAppContextMenuCardType(ComponentType.APP_ICON))
        assertTrue(com.example.globalminusone.ui.panel.isAppContextMenuCardType(ComponentType.CUSTOM_SHORTCUT))
        assertTrue(com.example.globalminusone.ui.panel.isAppContextMenuCardType(ComponentType.STATIC_SHORTCUT))
        assertTrue(com.example.globalminusone.ui.panel.isAppContextMenuCardType(ComponentType.SHELL_COMMAND))
        assertTrue(com.example.globalminusone.ui.panel.isAppContextMenuCardType(ComponentType.WEBSITE_LINK))
        assertTrue(com.example.globalminusone.ui.panel.isAppContextMenuCardType(ComponentType.FILE_SHORTCUT))
        assertFalse(com.example.globalminusone.ui.panel.isAppContextMenuCardType(ComponentType.BRIGHTNESS_BAR))
        assertFalse(com.example.globalminusone.ui.panel.isAppContextMenuCardType(ComponentType.FOLDER))
        assertFalse(com.example.globalminusone.ui.panel.isAppContextMenuCardType(ComponentType.SWITCH_WIFI))
    }
}
