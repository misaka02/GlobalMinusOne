package com.example.globalminusone.service

import com.example.globalminusone.data.GlobalMinusOneState

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
