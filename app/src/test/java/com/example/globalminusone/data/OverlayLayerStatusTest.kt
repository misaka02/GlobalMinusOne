package com.example.globalminusone.data

import android.view.WindowManager
import com.example.globalminusone.service.overlay.buildOverlayLayerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayLayerStatusTest {

    @Test
    fun summaryTextReflectsOrdinaryAccessibilityAndFallbackStates() {
        val ordinary = buildOverlayLayerStatus(
            requestedAccessibilityLayer = false,
            accessibilityConnected = true,
            panelWindowType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            activeTriggerTypes = listOf(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY),
            lastError = null
        )
        assertEquals(OverlayWindowLayer.APPLICATION, ordinary.panelLayer)
        assertTrue(ordinary.summaryText().contains("普通悬浮层"))

        val disconnected = buildOverlayLayerStatus(
            requestedAccessibilityLayer = true,
            accessibilityConnected = false,
            panelWindowType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            activeTriggerTypes = listOf(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY),
            lastError = null
        )
        assertTrue(disconnected.summaryText().contains("无障碍服务未开启"))

        val activeAccessibility = buildOverlayLayerStatus(
            requestedAccessibilityLayer = true,
            accessibilityConnected = true,
            panelWindowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            activeTriggerTypes = listOf(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY),
            lastError = null
        )
        assertEquals(OverlayWindowLayer.ACCESSIBILITY, activeAccessibility.triggerLayer)
        assertTrue(activeAccessibility.summaryText().contains("已生效"))

        val fallbackError = buildOverlayLayerStatus(
            requestedAccessibilityLayer = true,
            accessibilityConnected = true,
            panelWindowType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            activeTriggerTypes = listOf(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY),
            lastError = "BadTokenException: denied"
        )
        assertTrue(fallbackError.summaryText().contains("BadTokenException: denied"))
    }
}
