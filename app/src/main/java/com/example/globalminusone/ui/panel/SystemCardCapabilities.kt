package com.example.globalminusone.ui.panel

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.globalminusone.data.QuickActionItem
import com.example.globalminusone.service.GlobalAccessibilityService
import com.example.globalminusone.system.AppAndIntentLauncher
import com.example.globalminusone.system.BrightnessState
import com.example.globalminusone.system.RadioControlResult
import com.example.globalminusone.system.RadioController
import com.example.globalminusone.system.RadioKind
import com.example.globalminusone.system.RadioState
import com.example.globalminusone.system.RootCommandResult
import com.example.globalminusone.system.RootStatus
import com.example.globalminusone.system.SystemController
import com.example.globalminusone.system.UserRootSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow

internal interface SwitchCardControls {
    val isTorchOn: StateFlow<Boolean>
    val ringerModeLabel: StateFlow<String>
    val keepAwake: StateFlow<Boolean>
    fun canControlTorch(): Boolean
    fun toggleTorch(): Boolean
    fun requestTorchPermission()
    fun cycleRingerMode(): String
    fun toggleMute()
    fun toggleVibrate()
    fun isAutoBrightnessEnabled(): Boolean
    fun isAutoRotateEnabled(): Boolean
    fun canWriteSettings(): Boolean
    fun toggleAutoBrightness(): Boolean
    fun toggleAutoRotate(): Boolean
    fun toggleKeepAwake()
}

@Composable
internal fun rememberSwitchCardControls(system: SystemController): SwitchCardControls =
    remember(system) {
        object : SwitchCardControls {
            override val isTorchOn: StateFlow<Boolean> get() = system.isTorchOn
            override val ringerModeLabel: StateFlow<String> get() = system.ringerModeLabel
            override val keepAwake: StateFlow<Boolean> get() = system.keepAwake
            override fun canControlTorch(): Boolean = system.canControlTorch()
            override fun toggleTorch(): Boolean = system.toggleTorch()
            override fun requestTorchPermission() = system.requestTorchPermission()
            override fun cycleRingerMode(): String = system.cycleRingerMode()
            override fun toggleMute() = system.toggleMute()
            override fun toggleVibrate() = system.toggleVibrate()
            override fun isAutoBrightnessEnabled(): Boolean = system.isAutoBrightnessEnabled()
            override fun isAutoRotateEnabled(): Boolean = system.isAutoRotateEnabled()
            override fun canWriteSettings(): Boolean = system.canWriteSettings()
            override fun toggleAutoBrightness(): Boolean = system.toggleAutoBrightness()
            override fun toggleAutoRotate(): Boolean = system.toggleAutoRotate()
            override fun toggleKeepAwake() = system.toggleKeepAwake()
        }
    }

internal interface BrightnessCardControl {
    val brightness: StateFlow<BrightnessState>
    fun refreshBrightness()
    fun requestWriteSettingsPermission()
    fun useManualBrightness(): Boolean
    fun setBrightnessPercent(percent: Int): Boolean
}

@Composable
internal fun rememberBrightnessCardControl(system: SystemController): BrightnessCardControl =
    remember(system) {
        object : BrightnessCardControl {
            override val brightness: StateFlow<BrightnessState> get() = system.brightness
            override fun refreshBrightness() = system.refreshBrightness()
            override fun requestWriteSettingsPermission() = system.requestWriteSettingsPermission()
            override fun useManualBrightness(): Boolean = system.useManualBrightness()
            override fun setBrightnessPercent(percent: Int): Boolean = system.setBrightnessPercent(percent)
        }
    }

internal interface VolumeCardControl {
    fun isVolumeFixed(): Boolean
    fun getMediaVolumePercent(): Int
    fun setMediaVolumePercent(percent: Int): Boolean
    fun setMediaVolumePercentAsync(percent: Int) { setMediaVolumePercent(percent) }
    fun commitMediaVolumePercentAsync(percent: Int, onFallbackNeeded: () -> Unit) {
        if (!setMediaVolumePercent(percent) || isVolumeFixed()) onFallbackNeeded()
    }
    fun openSystemVolumePanel()
}

@Composable
internal fun rememberVolumeCardControl(system: SystemController): VolumeCardControl =
    remember(system) {
        object : VolumeCardControl {
            override fun isVolumeFixed(): Boolean = system.isMediaVolumeFixed()
            override fun getMediaVolumePercent(): Int = system.getMediaVolumePercent()
            override fun setMediaVolumePercent(percent: Int): Boolean = system.setMediaVolumePercent(percent)
            override fun setMediaVolumePercentAsync(percent: Int) = system.setMediaVolumePercentAsync(percent)
            override fun commitMediaVolumePercentAsync(percent: Int, onFallbackNeeded: () -> Unit) =
                system.commitMediaVolumePercentAsync(percent, onFallbackNeeded)
            override fun openSystemVolumePanel() = system.openSystemVolumePanel()
        }
    }

internal interface RadioCardControl {
    val wifi: StateFlow<RadioState>
    val bluetooth: StateFlow<RadioState>
    val backend: StateFlow<RadioController.Backend>
    fun selectBackend(value: RadioController.Backend)
    fun hasShizukuAuthorization(): Boolean
    fun startObserving()
    fun stopObserving()
    fun openOfficialControl(kind: RadioKind): Boolean
    suspend fun refresh(kind: RadioKind): RadioState
    suspend fun setEnabled(kind: RadioKind, enabled: Boolean): RadioControlResult
}

@Composable
internal fun rememberRadioCardControl(system: SystemController): RadioCardControl {
    val control = system.radios
    return remember(control) {
        object : RadioCardControl {
            override val wifi: StateFlow<RadioState> get() = control.wifi
            override val bluetooth: StateFlow<RadioState> get() = control.bluetooth
            override val backend: StateFlow<RadioController.Backend> get() = control.backend
            override fun selectBackend(value: RadioController.Backend) = control.selectBackend(value)
            override fun hasShizukuAuthorization(): Boolean = control.hasShizukuAuthorization()
            override fun startObserving() = control.startObserving()
            override fun stopObserving() = control.stopObserving()
            override fun openOfficialControl(kind: RadioKind): Boolean = control.openOfficialControl(kind)
            override suspend fun refresh(kind: RadioKind): RadioState = control.refresh(kind)
            override suspend fun setEnabled(kind: RadioKind, enabled: Boolean): RadioControlResult = control.setEnabled(kind, enabled)
        }
    }
}

internal interface RadioAuthorizationActions {
    val externalAuthorizationPending: StateFlow<Boolean>
    val rootStatus: StateFlow<RootStatus>
    fun requestShizukuAuthorization(onComplete: (Boolean) -> Unit): Job
    fun requestRootAuthorization(onComplete: (RootCommandResult) -> Unit): Job
    fun cancelRootAuthorization()
}

@Composable
internal fun rememberRadioAuthorizationActions(system: SystemController): RadioAuthorizationActions =
    remember(system) {
        object : RadioAuthorizationActions {
            override val externalAuthorizationPending: StateFlow<Boolean> get() = system.externalAuthorizationPending
            override val rootStatus: StateFlow<RootStatus> get() = UserRootSession.status
            override fun requestShizukuAuthorization(onComplete: (Boolean) -> Unit): Job = system.requestShizukuAuthorization(onComplete)
            override fun requestRootAuthorization(onComplete: (RootCommandResult) -> Unit): Job = system.requestRootAuthorization(onComplete)
            override fun cancelRootAuthorization() = system.cancelRootAuthorization()
        }
    }

internal interface SystemActionCardCommands {
    fun openLocationSettings()
    fun openHotspotSettings()
    fun openNfcSettings()
    fun openAirplaneModeSettings()
    fun lockScreen(): Boolean
    fun goHome()
    fun openRecentApps(): Boolean
    fun expandNotifications(): Boolean
    fun takeScreenshot(): Boolean
    fun launchVoiceAssistant()
    fun takeRegionScreenshot(): Boolean
    fun launchScreenAssistant(): Boolean
    fun launchBreenoTouch(): Boolean
    fun hasAccessibilityService(): Boolean
    fun postScreenshot(delayMillis: Long, action: () -> Unit)
    fun launchWechatScan()
}

@Composable
internal fun rememberSystemActionCardCommands(system: SystemController, context: Context): SystemActionCardCommands =
    remember(system, context) {
        object : SystemActionCardCommands {
            override fun openLocationSettings() = system.openLocationSettings()
            override fun openHotspotSettings() = system.openHotspotSettings()
            override fun openNfcSettings() = system.openNfcSettings()
            override fun openAirplaneModeSettings() = system.openAirplaneModeSettings()
            override fun lockScreen(): Boolean = system.lockScreen()
            override fun goHome() = system.goHome()
            override fun openRecentApps(): Boolean = system.openRecentApps()
            override fun expandNotifications(): Boolean = system.expandNotifications()
            override fun takeScreenshot(): Boolean = system.takeScreenshot()
            override fun launchVoiceAssistant() = system.launchVoiceAssistant()
            override fun takeRegionScreenshot(): Boolean = system.takeRegionScreenshot()
            override fun launchScreenAssistant(): Boolean = system.launchScreenAssistant()
            override fun launchBreenoTouch(): Boolean = system.launchBreenoTouch()
            override fun hasAccessibilityService(): Boolean = GlobalAccessibilityService.instance != null
            override fun postScreenshot(delayMillis: Long, action: () -> Unit) {
                Handler(Looper.getMainLooper()).postDelayed({ action() }, delayMillis)
            }
            override fun launchWechatScan() {
                AppAndIntentLauncher.launchQuickAction(
                    context,
                    QuickActionItem(
                        title = "微信扫一扫",
                        intentUri = "weixin://dl/scan",
                        packageName = "com.tencent.mm"
                    )
                )
            }
        }
    }

