package com.example.globalminusone.ui.settings

import com.example.globalminusone.data.ScreenOcrEngine
import com.example.globalminusone.screenassistant.ScreenAssistantOverlayUiPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

class SettingsNavigationAndOverlayPolicyTest {
    @Test
    fun singleTopRepeatedRouteUsesStrictlyIncreasingNonce() {
        val nonces = AtomicLong(0L)
        assertNull(SettingsNavigationPolicy.buildRequest("  ", nonces::incrementAndGet))

        val first = SettingsNavigationPolicy.buildRequest("SCREEN_ASSISTANT", nonces::incrementAndGet)
        assertNotNull(first)
        assertTrue(SettingsNavigationPolicy.shouldApply(first, 0L))
        assertFalse(SettingsNavigationPolicy.shouldApply(first, first!!.nonce))

        val second = SettingsNavigationPolicy.buildRequest("SCREEN_ASSISTANT", nonces::incrementAndGet)
        assertNotNull(second)
        assertTrue(second!!.nonce > first.nonce)
        assertTrue(SettingsNavigationPolicy.shouldApply(second, first.nonce))
    }

    @Test
    fun settingsHintAppearsOnPendingEmptyNoteOrAccessibilityFallback() {
        assertFalse(
            ScreenAssistantOverlayUiPolicy.shouldShowSettingsHint(
                panelCollapsed = true,
                ocrDone = true,
                blockCount = 0,
                ocrNote = "error",
                engineUsed = "无障碍节点树",
                configuredEngine = ScreenOcrEngine.AUTO,
                isSubImageMode = false
            )
        )
        assertFalse(
            ScreenAssistantOverlayUiPolicy.shouldShowSettingsHint(
                panelCollapsed = false,
                ocrDone = true,
                blockCount = 0,
                ocrNote = "error",
                engineUsed = "无障碍节点树",
                configuredEngine = ScreenOcrEngine.AUTO,
                isSubImageMode = true
            )
        )
        assertTrue(
            ScreenAssistantOverlayUiPolicy.shouldShowSettingsHint(
                panelCollapsed = false,
                ocrDone = false,
                blockCount = 0,
                ocrNote = "",
                engineUsed = "",
                configuredEngine = ScreenOcrEngine.AUTO,
                isSubImageMode = false
            )
        )
        assertTrue(
            ScreenAssistantOverlayUiPolicy.shouldShowSettingsHint(
                panelCollapsed = false,
                ocrDone = true,
                blockCount = 5,
                ocrNote = "",
                engineUsed = "无障碍节点树",
                configuredEngine = ScreenOcrEngine.AUTO,
                isSubImageMode = false
            )
        )
        assertFalse(
            ScreenAssistantOverlayUiPolicy.shouldShowSettingsHint(
                panelCollapsed = false,
                ocrDone = true,
                blockCount = 5,
                ocrNote = "",
                engineUsed = "无障碍界面树",
                configuredEngine = ScreenOcrEngine.ACCESSIBILITY_TREE,
                isSubImageMode = false
            )
        )
        assertFalse(
            ScreenAssistantOverlayUiPolicy.shouldShowSettingsHint(
                panelCollapsed = false,
                ocrDone = true,
                blockCount = 4,
                ocrNote = "",
                engineUsed = "离线中文 OCR 扩展",
                configuredEngine = ScreenOcrEngine.AUTO,
                isSubImageMode = false
            )
        )
    }
}
