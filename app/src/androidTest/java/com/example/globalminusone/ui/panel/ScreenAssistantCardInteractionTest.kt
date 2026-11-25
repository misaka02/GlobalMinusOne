package com.example.globalminusone.ui.panel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ScreenAssistantCardInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun touchDispatchesTheScreenAssistantCardOnceOutsideEditMode() {
        val events = mutableListOf<String>()
        val editing = mutableStateOf(false)
        val item = ComponentItem(type = ComponentType.ACTION_SCREEN_ASSISTANT, title = "全局识屏")
        val commands = object : SystemActionCardCommands {
            override fun launchScreenAssistant(): Boolean { events += "识屏"; return true }
            override fun openLocationSettings() = error("unexpected location action")
            override fun openHotspotSettings() = error("unexpected hotspot action")
            override fun openNfcSettings() = error("unexpected NFC action")
            override fun openAirplaneModeSettings() = error("unexpected airplane action")
            override fun lockScreen() = error("unexpected lock action")
            override fun goHome() = error("unexpected home action")
            override fun openRecentApps() = error("unexpected recent apps action")
            override fun expandNotifications() = error("unexpected notifications action")
            override fun takeScreenshot() = error("unexpected screenshot action")
            override fun launchVoiceAssistant() = error("unexpected voice action")
            override fun takeRegionScreenshot() = error("unexpected region action")
            override fun launchBreenoTouch() = error("unexpected Breeno action")
            override fun hasAccessibilityService() = error("unexpected accessibility query")
            override fun postScreenshot(delayMillis: Long, action: () -> Unit) = error("unexpected delayed screenshot")
            override fun launchWechatScan() = error("unexpected WeChat action")
        }
        compose.setContent {
            CompositionLocalProvider(LocalIsEditMode provides editing.value) {
                Box(Modifier.size(width = 160.dp, height = 84.dp)) {
                    SystemActionCard(item, editing.value, commands) { events += "关闭" }
                }
            }
        }
        compose.onNodeWithContentDescription("全局识屏").performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(listOf("识屏"), events)
            editing.value = true
        }
        compose.onNodeWithContentDescription("全局识屏").performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf("识屏"), events) }
    }
}
