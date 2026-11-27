package com.example.globalminusone

import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.ContainerLayoutType
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.policy.PagePresetOption
import com.example.globalminusone.data.policy.buildPagePresetConfig
import com.example.globalminusone.data.policy.containerCompactTargetMetrics
import com.example.globalminusone.system.AppAndIntentLauncher
import com.example.globalminusone.ui.panel.gesture.shouldEnableChildHorizontalScroll
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R13AuditFixesTest {

    @Test
    fun childHorizontalScroll_disabledWhenContentFits_soPagerFlingIsNeverHijacked() {
        assertFalse(shouldEnableChildHorizontalScroll(allowed = true, maxValue = 0))
        assertFalse(shouldEnableChildHorizontalScroll(allowed = true, maxValue = -4))
        assertFalse(shouldEnableChildHorizontalScroll(allowed = false, maxValue = 120))
        assertFalse(shouldEnableChildHorizontalScroll(allowed = true, maxValue = 120, exclusiveBlocked = true))
        assertTrue(shouldEnableChildHorizontalScroll(allowed = true, maxValue = 1))
        assertTrue(shouldEnableChildHorizontalScroll(allowed = true, maxValue = 240, exclusiveBlocked = false))
    }

    @Test
    fun buildPagePresetConfig_andAddPagePreset_consistentlyIncludeOperationBar() {
        val prefs = MemoryPreferences()
        val repository = MinusOneRepository(prefs)

        PagePresetOption.entries.forEach { preset ->
            val direct = buildPagePresetConfig(preset)
            assertTrue(
                "Preset ${preset.name} should include an OPERATION_BAR",
                direct.components.any { it.type == ComponentType.OPERATION_BAR }
            )
            val idx = repository.addPagePreset(preset)
            assertTrue("addPagePreset(${preset.name}) should succeed", idx >= 0)
            val added = repository.state.value.pages[idx]
            assertEquals(direct.layoutType, added.layoutType)
            assertTrue(
                "Added page for ${preset.name} should include OPERATION_BAR",
                added.components.any { it.type == ComponentType.OPERATION_BAR }
            )
        }
    }

    @Test
    fun containerCompactTargetMetrics_adaptsToPopupAndHostWidthsWithoutHardcoded300Dp() {
        val folder = ComponentItem(
            id = "folder_compact",
            type = ComponentType.FOLDER,
            title = "工具",
            containerLayout = ContainerLayoutType.GRID,
            containerColumns = 4,
            containerComponents = listOf(
                ComponentItem(id = "a1", type = ComponentType.APP_ICON, title = "A", packageName = "pkg.a"),
                ComponentItem(id = "a2", type = ComponentType.APP_ICON, title = "B", packageName = "pkg.b")
            )
        )
        val browseMetrics = containerCompactTargetMetrics(
            item = folder,
            availableWidthDp = 306f,
            editingInside = false,
            gridColumns = 32,
            configuredHeightUnitDp = 0
        )
        assertEquals(2, browseMetrics.columns)
        assertTrue(browseMetrics.widthDp in 150f..220f)
        assertTrue(browseMetrics.stepYDp > 0f)
        assertEquals(browseMetrics.widthDp / 32f, browseMetrics.stepYDp, 0.001f)

        val editMetrics = containerCompactTargetMetrics(
            item = folder,
            availableWidthDp = 306f,
            editingInside = true,
            gridColumns = 32,
            configuredHeightUnitDp = 12
        )
        assertEquals(4, editMetrics.columns)
        assertEquals(12f, editMetrics.stepYDp, 0.001f)
        assertTrue(editMetrics.widthDp > browseMetrics.widthDp)

        val box = ComponentItem(
            id = "box_half",
            type = ComponentType.CONTAINER_BOX,
            title = "半宽容器",
            w = 16,
            containerColumns = 2,
            containerLayout = ContainerLayoutType.GRID
        )
        val boxMetrics = containerCompactTargetMetrics(
            item = box,
            availableWidthDp = 400f,
            editingInside = true,
            gridColumns = 32,
            configuredHeightUnitDp = 0
        )
        // marginLeft/Right default 2+2, paddingLeft/Right default 8+8, wrapper extra 16 -> inset = 36dp
        assertEquals(2, boxMetrics.columns)
        assertEquals(164f, boxMetrics.widthDp, 0.01f)
        assertEquals(164f / 32f, boxMetrics.stepYDp, 0.001f)
    }

    @Test
    fun appCacheAndDrawerInvalidation_trackPackageChangesIndependently() {
        val initialCacheGen = AppAndIntentLauncher.cacheGeneration.value
        val initialListGen = AppAndIntentLauncher.appListGeneration.value

        AppAndIntentLauncher.invalidateInstalledAppList()
        assertEquals(initialListGen + 1, AppAndIntentLauncher.appListGeneration.value)
        assertEquals(initialCacheGen, AppAndIntentLauncher.cacheGeneration.value)

        AppAndIntentLauncher.onPackageChanged("   ")
        assertEquals(initialListGen + 2, AppAndIntentLauncher.appListGeneration.value)
        assertEquals(initialCacheGen, AppAndIntentLauncher.cacheGeneration.value)

        AppAndIntentLauncher.onPackageChanged("com.example.changed")
        assertEquals(initialListGen + 3, AppAndIntentLauncher.appListGeneration.value)
        assertEquals(initialCacheGen + 1, AppAndIntentLauncher.cacheGeneration.value)
    }
}
