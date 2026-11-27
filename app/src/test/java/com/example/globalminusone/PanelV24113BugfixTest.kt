package com.example.globalminusone

import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.PageConfig
import com.example.globalminusone.data.PageLayoutType
import com.example.globalminusone.data.operations.SubPanelOperations
import com.example.globalminusone.system.media.BrightnessMode
import com.example.globalminusone.system.media.ForegroundBrightnessOverrideController
import com.example.globalminusone.system.media.WINDOW_BRIGHTNESS_OVERRIDE_NONE
import com.example.globalminusone.system.media.brightnessPercentToWindowFloat
import com.example.globalminusone.ui.panel.PanelOverlayPortalState
import com.example.globalminusone.ui.panel.gesture.StationaryHoldPolicy
import com.example.globalminusone.ui.panel.gesture.resolveFreeCanvasBaseHeightPx
import com.example.globalminusone.ui.panel.gesture.stableContentExtent
import com.example.globalminusone.ui.settings.SettingsNavigationPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelV24113BugfixTest {

    @Test
    fun portalClearIncrementsClearGenerationAndEmptiesEntries() {
        val portal = PanelOverlayPortalState()
        val keyA = Any()
        portal.upsert(keyA) {}
        assertTrue(portal.contains(keyA))
        val initialGen = portal.clearGeneration

        portal.clear()
        assertFalse(portal.contains(keyA))
        assertFalse(portal.hasEntries)
        assertEquals(initialGen + 1, portal.clearGeneration)
    }

    @Test
    fun brightnessPercentToWindowFloatStaysWithinSafePositiveBounds() {
        assertEquals(0.01f, brightnessPercentToWindowFloat(0), 0.0001f)
        assertEquals(0.45f, brightnessPercentToWindowFloat(45), 0.0001f)
        assertEquals(1.0f, brightnessPercentToWindowFloat(100), 0.0001f)
        assertEquals(1.0f, brightnessPercentToWindowFloat(130), 0.0001f)
    }

    @Test
    fun foregroundBrightnessOverrideSurvivesOwnSystemWriteAndClearsOnExternalChanges() {
        val controller = ForegroundBrightnessOverrideController()
        assertEquals(WINDOW_BRIGHTNESS_OVERRIDE_NONE, controller.current(), 0.0001f)

        // Panel slider sets override over foreground video app.
        val applied = controller.onSliderApplied(percent = 65, writtenSystemRaw = 166, foregroundPackage = "tv.danmaku.bili")
        assertEquals(0.65f, applied, 0.0001f)

        // Our own Settings observer firing back with the same raw value preserves the window override.
        assertEquals(0.65f, controller.onSystemBrightnessObserved(166, BrightnessMode.MANUAL), 0.0001f)

        // Staying on the same video app preserves the override.
        assertEquals(0.65f, controller.onForegroundPackageChanged("tv.danmaku.bili"), 0.0001f)

        // Switching to another app releases the override cleanly.
        assertEquals(WINDOW_BRIGHTNESS_OVERRIDE_NONE, controller.onForegroundPackageChanged("com.android.launcher"), 0.0001f)

        // External brightness slider change or enabling auto brightness clears override.
        controller.onSliderApplied(percent = 30, writtenSystemRaw = 77, foregroundPackage = "tv.danmaku.bili")
        assertEquals(WINDOW_BRIGHTNESS_OVERRIDE_NONE, controller.onSystemBrightnessObserved(120, BrightnessMode.MANUAL), 0.0001f)

        controller.onSliderApplied(percent = 80, writtenSystemRaw = 204, foregroundPackage = "tv.danmaku.bili")
        assertEquals(WINDOW_BRIGHTNESS_OVERRIDE_NONE, controller.onSystemBrightnessObserved(204, BrightnessMode.AUTOMATIC), 0.0001f)

        // Screen off explicit clear resets override.
        controller.onSliderApplied(percent = 50, writtenSystemRaw = 128, foregroundPackage = "tv.danmaku.bili")
        assertEquals(WINDOW_BRIGHTNESS_OVERRIDE_NONE, controller.clear(), 0.0001f)
    }

    @Test
    fun tapPolicyAllowsQuickTouchSlopWhileHoldPolicyStaysStrict() {
        val holdPolicy = StationaryHoldPolicy(maxDriftPx = 3f, maxPathPx = 12f)
        val tapPolicy = StationaryHoldPolicy(maxDriftPx = 8f, maxPathPx = 16f)

        // Small 5px finger wobble before release must cancel hold but still register a quick tap.
        holdPolicy.observe(dx = 5f, dy = 0f, pointers = 1, pressed = true, consumedMovement = false)
        assertTrue(holdPolicy.cancelled)
        assertTrue(tapPolicy.observe(dx = 5f, dy = 0f, pointers = 1, pressed = true, consumedMovement = false))
        assertTrue(tapPolicy.observeRelease(dx = 0f, dy = 0f, pointers = 1, consumed = false))

        // Large 12px swipe cancels both hold and tap.
        val swipeTap = StationaryHoldPolicy(maxDriftPx = 8f, maxPathPx = 16f)
        assertFalse(swipeTap.observe(dx = 12f, dy = 0f, pointers = 1, pressed = true, consumedMovement = false))
        assertFalse(swipeTap.observeRelease(dx = 0f, dy = 0f, pointers = 1, consumed = false))
    }

    @Test
    fun freeCanvasBaseHeightHasZeroPhantomOverflowOutsideEditMode() {
        val baseNormal = resolveFreeCanvasBaseHeightPx(
            maxContentPx = 280f,
            stepY = 20f,
            isEditMode = false,
            isInteracting = false,
            editBottomBufferPx = 44f
        )
        assertEquals(280f, baseNormal, 0.001f)
        // Inside an 800px viewport, content <= 800px produces exact 800px extent (maxValue = 0).
        assertEquals(800f, stableContentExtent(baseNormal, scroll = 0, viewport = 800f), 0.001f)

        // Edit mode adds drag buffer cleanly without affecting normal mode.
        val baseEdit = resolveFreeCanvasBaseHeightPx(
            maxContentPx = 280f,
            stepY = 20f,
            isEditMode = true,
            isInteracting = false,
            editBottomBufferPx = 44f
        )
        assertEquals(280f + 80f + 44f, baseEdit, 0.001f)
    }

    @Test
    fun configImportPageNavigationRequestBuildsCleanly() {
        val request = SettingsNavigationPolicy.buildRequest("CONFIG_IMPORT_PAGE") { 7L }
        assertEquals("CONFIG_IMPORT_PAGE", request?.screen)
        assertEquals(7L, request?.nonce)
    }

    @Test
    fun subPanelOperationsCreateUpdateDeleteAndGuardLockCleanly() {
        var seq = 0
        val newId = { "id-${++seq}" }
        val base = GlobalMinusOneState(pages = listOf(PageConfig(id = "root-p", name = "主")))

        val createdPair = SubPanelOperations.create(base, "抽屉", inheritGeometry = false, PageLayoutType.GRID, newId)
        assertNotNull(createdPair)
        val (withSub, sub) = createdPair!!
        assertEquals(listOf(sub.id), withSub.subPanels.map { it.id })
        assertTrue(withSub.pages.any { it.subPanelId == sub.id })

        val updated = SubPanelOperations.update(withSub, sub.id) { it.copy(name = "工具集") }
        assertEquals("工具集", updated?.subPanels?.first()?.name)

        // Add entry link to root page (inside nested container too), then verify delete prunes all links + pages.
        val linked = updated!!.copy(
            pages = updated.pages.map { p ->
                if (p.subPanelId.isEmpty()) p.copy(
                    components = listOf(
                        ComponentItem(
                            id = "folder-1",
                            type = ComponentType.FOLDER,
                            containerComponents = listOf(ComponentItem(id = "e-nested", type = ComponentType.SUB_PANEL_ENTRY, subPanelId = sub.id))
                        ),
                        ComponentItem(id = "e-top", type = ComponentType.SUB_PANEL_ENTRY, subPanelId = sub.id)
                    )
                ) else p
            }
        )
        val deleted = SubPanelOperations.delete(linked, sub.id)!!
        assertTrue(deleted.subPanels.isEmpty())
        assertTrue(deleted.pages.none { it.subPanelId == sub.id })
        assertTrue(deleted.pages.flatMap { it.components }.none { it.subPanelId == sub.id })
        assertTrue(deleted.pages.first().components.first().containerComponents.isEmpty())

        // Locked layout rejects all SubPanelOperations mutations.
        val locked = withSub.copy(advancedConfig = withSub.advancedConfig.copy(layoutLocked = true))
        assertNull(SubPanelOperations.create(locked, "X", true, PageLayoutType.GRID, newId))
        assertNull(SubPanelOperations.update(locked, sub.id) { it.copy(name = "Y") })
        assertNull(SubPanelOperations.delete(locked, sub.id))
    }
}
