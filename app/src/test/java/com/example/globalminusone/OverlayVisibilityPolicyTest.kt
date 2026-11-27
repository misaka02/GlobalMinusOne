package com.example.globalminusone

import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.TriggerWay
import com.example.globalminusone.service.overlay.OverlayVisibilityPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayVisibilityPolicyTest {
    private val state = GlobalMinusOneState()

    @Test
    fun settingsBlacklistControlsEveryTriggerKind() {
        TriggerWay.entries.forEach { way ->
            val configured = state.copy(
                advancedConfig = state.advancedConfig.copy(blacklistPackages = setOf("blocked.app")),
                triggerConfig = state.triggerConfig.copy(triggerWay = way, blacklistPackages = emptyList())
            )
            assertTrue(OverlayVisibilityPolicy.isBlacklisted(configured, "blocked.app"))
            assertFalse(OverlayVisibilityPolicy.shouldShowTrigger(configured, "blocked.app", false, false))
            assertTrue(OverlayVisibilityPolicy.shouldShowTrigger(configured, "other.app", false, false))
        }
    }

    @Test
    fun removingUiBlacklistEntryReleasesLegacyEntry() {
        val configured = state.copy(
            advancedConfig = state.advancedConfig.copy(blacklistPackages = emptySet()),
            triggerConfig = state.triggerConfig.copy(blacklistPackages = listOf("blocked.app"))
        )
        assertFalse(OverlayVisibilityPolicy.isBlacklisted(configured, "blocked.app"))
        assertTrue(OverlayVisibilityPolicy.shouldShowTrigger(configured, "blocked.app", false, false))
    }

    @Test
    fun floatingBallUsesSameLandscapeTemporaryDisableAndPanelRules() {
        val ball = state.copy(triggerConfig = state.triggerConfig.copy(
            triggerWay = TriggerWay.SUSPENSION_BALL,
            tempDisabledUntilMs = 2_000L
        ))
        assertFalse(OverlayVisibilityPolicy.shouldShowTrigger(ball, "app", false, false, now = 1_999L))
        assertTrue(OverlayVisibilityPolicy.shouldShowTrigger(ball, "app", false, false, now = 2_000L))
        assertFalse(OverlayVisibilityPolicy.shouldShowTrigger(ball, "app", true, false, now = 2_000L))
        assertFalse(OverlayVisibilityPolicy.shouldShowTrigger(ball, "app", false, true, now = 2_000L))
        val disabled = ball.copy(triggerConfig = ball.triggerConfig.copy(serviceEnabled = false))
        assertFalse(OverlayVisibilityPolicy.shouldShowTrigger(disabled, "app", false, false, now = 2_000L))
    }

    @Test
    fun autoClosePanelInLandscapeRespectsConfigAndOrientation() {
        val enabledState = state.copy(advancedConfig = state.advancedConfig.copy(autoCloseInLandscape = true))
        val disabledState = state.copy(advancedConfig = state.advancedConfig.copy(autoCloseInLandscape = false))

        assertTrue(OverlayVisibilityPolicy.shouldAutoClosePanelInLandscape(enabledState, isLandscape = true, panelOpenOrOpening = true))
        assertFalse(OverlayVisibilityPolicy.shouldAutoClosePanelInLandscape(enabledState, isLandscape = false, panelOpenOrOpening = true))
        assertFalse(OverlayVisibilityPolicy.shouldAutoClosePanelInLandscape(enabledState, isLandscape = true, panelOpenOrOpening = false))
        assertFalse(OverlayVisibilityPolicy.shouldAutoClosePanelInLandscape(disabledState, isLandscape = true, panelOpenOrOpening = true))
    }

    @Test
    fun suspensionBallEdgeSnapSnapsToNearestEdgeWhenEnabled() {
        assertFalse(OverlayVisibilityPolicy.snapSuspensionBallXPercent(0.28f, snapToEdge = false) == 0.02f)
        assertTrue(OverlayVisibilityPolicy.snapSuspensionBallXPercent(0.28f, snapToEdge = true) == 0.02f)
        assertTrue(OverlayVisibilityPolicy.snapSuspensionBallXPercent(0.50f, snapToEdge = true) == 0.98f)
        assertTrue(OverlayVisibilityPolicy.snapSuspensionBallXPercent(0.75f, snapToEdge = true) == 0.98f)
    }
}
