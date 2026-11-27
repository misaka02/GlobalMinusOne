package com.example.globalminusone.service.overlay

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

    /** Velocity-aware horizontal edge-snap: fast horizontal flick snaps to the flicked side. */
    fun snapSuspensionBallXPercentWithVelocity(
        posXPercent: Float,
        velocityXFractionPerSec: Float,
        snapToEdge: Boolean
    ): Float {
        val clamped = posXPercent.coerceIn(0.02f, 0.98f)
        if (!snapToEdge) return clamped
        return when {
            velocityXFractionPerSec >= 0.9f -> 0.98f
            velocityXFractionPerSec <= -0.9f -> 0.02f
            else -> snapSuspensionBallXPercent(clamped, true)
        }
    }
}

/** Pure rules controlling SurfaceFlinger blur and focus during panel transitions. */
internal object PanelWindowBlurPolicy {
    fun effectiveBlurRadius(
        configuredRadius: Int,
        authorizationPending: Boolean,
        panelVisible: Boolean,
        panelClosing: Boolean,
        openProgress: Float
    ): Int {
        if (configuredRadius <= 0 || authorizationPending || !panelVisible || panelClosing) return 0
        return if (openProgress >= 0.999f) configuredRadius else 0
    }
}

internal fun screenshotOrientationMatches(bitmapWidth: Int, bitmapHeight: Int, displayWidth: Int, displayHeight: Int): Boolean {
    if (minOf(bitmapWidth, bitmapHeight, displayWidth, displayHeight) <= 0) return false
    return (bitmapWidth > bitmapHeight) == (displayWidth > displayHeight)
}

internal data class PanelEdgeRect(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** The actual edge trigger is the only exclusion owner; the full panel never reserves a second band. */
internal fun triggerEdgeExclusions(width: Int, height: Int, density: Float, visible: Boolean,
    verticalEdge: Boolean): List<PanelEdgeRect> {
    if (!visible || !verticalEdge || width <= 0 || height <= 0 || !density.isFinite() || density <= 0) return emptyList()
    val extent = minOf((200 * density).toInt().coerceAtLeast(0), height)
    if (extent == 0) return emptyList()
    val start = (height - extent) / 2
    return listOf(PanelEdgeRect(0, start, width, start + extent))
}

internal data class OverlayUpdates(val triggers: Boolean, val ball: Boolean, val eye: Boolean,
    val blur: Boolean, val visibility: Boolean)

/** Card text, selection and file preferences must not rebuild unrelated WindowManager views. */
internal fun overlayUpdates(old: GlobalMinusOneState?, next: GlobalMinusOneState): OverlayUpdates {
    val trigger = old == null || old.triggerConfig != next.triggerConfig ||
        old.advancedConfig.highestOverlayLevel != next.advancedConfig.highestOverlayLevel
    return OverlayUpdates(trigger,
        trigger || old?.suspensionBallConfig != next.suspensionBallConfig,
        old == null || old.advancedConfig.eyeProtectionEnabled != next.advancedConfig.eyeProtectionEnabled ||
            old.advancedConfig.eyeProtectionColorHex != next.advancedConfig.eyeProtectionColorHex,
        old == null || old.themeConfig.backgroundBlurRadius != next.themeConfig.backgroundBlurRadius,
        old == null || old.advancedConfig.blacklistPackages != next.advancedConfig.blacklistPackages ||
            old.advancedConfig.autoCloseInLandscape != next.advancedConfig.autoCloseInLandscape)
}
