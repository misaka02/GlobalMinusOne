package com.example.globalminusone.service

import com.example.globalminusone.data.GlobalMinusOneState

/** Shared visibility rules for both edge triggers and newly created floating balls. */
internal object OverlayVisibilityPolicy {
    fun isBlacklisted(state: GlobalMinusOneState, foregroundPackage: String): Boolean =
        foregroundPackage.isNotBlank() && foregroundPackage in state.advancedConfig.blacklistPackages

    fun shouldShowTrigger(
        state: GlobalMinusOneState,
        foregroundPackage: String,
        isLandscape: Boolean,
        panelVisible: Boolean,
        now: Long = System.currentTimeMillis()
    ): Boolean = state.triggerConfig.run {
        serviceEnabled && !isBlacklisted(state, foregroundPackage) &&
            !(isLandscape && hideInLandscape) && !panelVisible && !isTempDisabled(now)
    }

    /**
     * BUG-002 fix: When `advancedConfig.autoCloseInLandscape` is enabled and the device
     * enters or is in landscape orientation while the panel is open or opening, the panel
     * must automatically close.
     */
    fun shouldAutoClosePanelInLandscape(
        state: GlobalMinusOneState,
        isLandscape: Boolean,
        panelOpenOrOpening: Boolean
    ): Boolean = isLandscape && panelOpenOrOpening && state.advancedConfig.autoCloseInLandscape

    /**
     * BUG-003 fix: Optional horizontal edge-snap for the suspension ball after a drag gesture.
     */
    fun snapSuspensionBallXPercent(posXPercent: Float, snapToEdge: Boolean): Float {
        val clamped = posXPercent.coerceIn(0.02f, 0.98f)
        if (!snapToEdge) return clamped
        return if (clamped < 0.5f) 0.02f else 0.98f
    }
}
