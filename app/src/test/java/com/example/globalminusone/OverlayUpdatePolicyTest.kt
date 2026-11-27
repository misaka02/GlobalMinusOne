package com.example.globalminusone

import com.example.globalminusone.data.*
import com.example.globalminusone.service.overlay.overlayUpdates
import org.junit.Assert.*
import org.junit.Test
import com.example.globalminusone.data.GlobalMinusOneState

class OverlayUpdatePolicyTest {
    @Test fun editingCardsAndWindowPositionDoesNotRebuildSystemWindows() {
        val original = GlobalMinusOneState()
        val edited = original.copy(editMode = true, selectedComponentId = "card",
            themeConfig = original.themeConfig.copy(panelHeightPercent = 60),
            fileBrowserConfig = original.fileBrowserConfig.copy(showHidden = true))
        val changes = overlayUpdates(original, edited)
        assertFalse(changes.triggers || changes.ball || changes.eye || changes.blur || changes.visibility)
    }
    @Test fun actualSystemWindowPreferencesStillUpdateTheRightViews() {
        val original = GlobalMinusOneState()
        assertTrue(overlayUpdates(original, original.copy(triggerConfig = original.triggerConfig.copy(serviceEnabled = !original.triggerConfig.serviceEnabled))).triggers)
        assertTrue(overlayUpdates(original, original.copy(suspensionBallConfig = original.suspensionBallConfig.copy(radiusDp = 28))).ball)
        val changed = overlayUpdates(original, original.copy(advancedConfig = original.advancedConfig.copy(
            blacklistPackages = setOf("example.app"), eyeProtectionEnabled = true)))
        assertTrue(changed.visibility)
        assertTrue(changed.eye)
        assertFalse(changed.triggers)
        val landscapeChanged = overlayUpdates(original, original.copy(advancedConfig = original.advancedConfig.copy(
            autoCloseInLandscape = !original.advancedConfig.autoCloseInLandscape)))
        assertTrue(landscapeChanged.visibility)
    }
}
