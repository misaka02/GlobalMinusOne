package com.example.globalminusone.ui.panel

import com.example.globalminusone.data.ComponentType
import org.junit.Assert.*
import org.junit.Test

class SystemCardActionDispatchTest {
    private class Commands : SystemActionCardCommands {
        val events = mutableListOf<String>()
        var available = false
        var succeeded = false
        var delayed: (() -> Unit)? = null
        var onCommand: (String) -> Unit = {}
        private fun command(name: String): Boolean { events += name; onCommand(name); return succeeded }
        override fun openLocationSettings() { command("location") }
        override fun openHotspotSettings() { command("hotspot") }
        override fun openNfcSettings() { command("nfc") }
        override fun openAirplaneModeSettings() { command("airplane") }
        override fun lockScreen(): Boolean = command("lock")
        override fun goHome() { command("home") }
        override fun openRecentApps(): Boolean = command("recents")
        override fun expandNotifications(): Boolean = command("notifications")
        override fun takeScreenshot(): Boolean = command("screenshot")
        override fun launchVoiceAssistant() { command("voice") }
        override fun takeRegionScreenshot(): Boolean = command("region")
        override fun launchScreenAssistant(): Boolean = command("assistant")
        override fun launchBreenoTouch(): Boolean = command("breeno")
        override fun hasAccessibilityService(): Boolean { events += "accessibility"; return available }
        override fun postScreenshot(delayMillis: Long, action: () -> Unit) { events += "post:$delayMillis"; delayed = action }
        override fun launchWechatScan() { command("wechat") }
        fun dispatch(type: ComponentType, editing: Boolean = false) =
            dispatchSystemCardAction(type, editing, this) { events += "close" }
    }

    @Test fun editModeSuppressesEveryDispatchIncludingAccessibilityReadsAndDelayedWork() {
        val commands = Commands()
        for (type in ComponentType.entries) commands.dispatch(type, editing = true)
        assertTrue(commands.events.isEmpty())
        assertNull(commands.delayed)
    }

    @Test fun commandFirstCardsCloseAfterDispatchEvenWhenCommandReturnsFalse() {
        val commands = Commands()
        for ((type, expected) in listOf(
            ComponentType.ACTION_LOCK to "lock", ComponentType.ACTION_HOME to "home",
            ComponentType.ACTION_RECENTS to "recents", ComponentType.ACTION_NOTIFICATIONS to "notifications",
            ComponentType.ACTION_WECHAT_SCAN to "wechat"
        )) {
            commands.events.clear(); commands.dispatch(type)
            assertEquals(listOf(expected, "close"), commands.events)
        }
    }

    @Test fun settingsAndVoiceCloseBeforeDispatchAndBackOnlyCloses() {
        val commands = Commands()
        for ((type, expected) in listOf(
            ComponentType.SWITCH_LOCATION to "location", ComponentType.SWITCH_HOTSPOT to "hotspot",
            ComponentType.SWITCH_NFC to "nfc", ComponentType.SWITCH_AIRPLANE to "airplane",
            ComponentType.ACTION_VOICE_ASSIST to "voice"
        )) {
            commands.events.clear(); commands.dispatch(type)
            assertEquals(listOf("close", expected), commands.events)
        }
        commands.events.clear(); commands.dispatch(ComponentType.ACTION_BACK)
        assertEquals(listOf("close"), commands.events)
    }

    @Test fun screenshotWithoutAccessibilityDispatchesImmediatelyWithoutClosing() {
        val commands = Commands()
        commands.dispatch(ComponentType.ACTION_SCREENSHOT)
        assertEquals(listOf("accessibility", "screenshot"), commands.events)
        assertNull(commands.delayed)
    }

    @Test fun screenshotWithAccessibilityClosesThenPostsExactly300msAndCapturesOnlyOnCallback() {
        val commands = Commands(); commands.available = true
        commands.dispatch(ComponentType.ACTION_SCREENSHOT)
        assertEquals(listOf("accessibility", "close", "post:300"), commands.events)
        checkNotNull(commands.delayed).invoke()
        assertEquals(listOf("accessibility", "close", "post:300", "screenshot"), commands.events)
    }

    @Test fun captureToolsOwnTheirWindowPreparationWithoutAnExtraClose() {
        val commands = Commands()
        for ((type, expected) in listOf(
            ComponentType.ACTION_REGION_SCREENSHOT to "region",
            ComponentType.ACTION_SCREEN_ASSISTANT to "assistant",
            ComponentType.ACTION_BREENO_TOUCH to "breeno"
        )) {
            commands.events.clear(); commands.dispatch(type)
            assertEquals(listOf(expected), commands.events)
        }
    }

    @Test fun unavailableSplitAndUnrelatedCardsHaveNoSystemSideEffects() {
        val commands = Commands()
        for (type in listOf(ComponentType.ACTION_SPLIT_SCREEN, ComponentType.NOTE_CARD, ComponentType.FOLDER)) commands.dispatch(type)
        assertTrue(commands.events.isEmpty())
    }

    @Test fun failuresPreserveOriginalOrderAndPropagateWithoutFallbackOrExtraClose() {
        val commands = Commands(); val failure = IllegalStateException("command failed")
        commands.onCommand = { throw failure }
        try { commands.dispatch(ComponentType.ACTION_LOCK); fail("failure must propagate") }
        catch (caught: IllegalStateException) { assertSame(failure, caught) }
        assertEquals(listOf("lock"), commands.events)
        commands.events.clear()
        try { commands.dispatch(ComponentType.ACTION_VOICE_ASSIST); fail("failure must propagate") }
        catch (caught: IllegalStateException) { assertSame(failure, caught) }
        assertEquals(listOf("close", "voice"), commands.events)
        commands.events.clear(); commands.available = true
        commands.dispatch(ComponentType.ACTION_SCREENSHOT)
        try { checkNotNull(commands.delayed).invoke(); fail("delayed failure must propagate") }
        catch (caught: IllegalStateException) { assertSame(failure, caught) }
        assertEquals(listOf("accessibility", "close", "post:300", "screenshot"), commands.events)
    }
}
