package com.example.globalminusone.ui.panel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.ui.AppIcons as Icons
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun SystemSwitchCard(
    item: ComponentItem,
    isEditMode: Boolean,
    controls: SwitchCardControls,
    expandedState: () -> StateFlow<Boolean>
) {
    when (item.type) {
        ComponentType.SWITCH_TORCH -> {
            val isTorchOn by controls.isTorchOn.collectAsState()
            SwitchTileComponent(
                title = if (isTorchOn) "手电筒·开" else "手电筒",
                icon = if (isTorchOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                active = isTorchOn,
                subtitle = if (controls.canControlTorch()) "点按切换" else "需相机权限（点按申请）",
                onClick = { if (!isEditMode) {
                    if (controls.canControlTorch()) controls.toggleTorch() else controls.requestTorchPermission()
                } }
            )
        }
        ComponentType.SWITCH_RINGER -> {
            val ringerLabel by controls.ringerModeLabel.collectAsState()
            SwitchTileComponent(
                title = "声音模式",
                subtitle = ringerLabel,
                icon = Icons.Rounded.VolumeUp,
                onClick = { if (!isEditMode) controls.cycleRingerMode() }
            )
        }
        ComponentType.SWITCH_MUTE -> {
            val label by controls.ringerModeLabel.collectAsState()
            SwitchTileComponent(active = label == "静音", subtitle = label,
                title = "静音",
                icon = Icons.Rounded.VolumeMute,
                onClick = { if (!isEditMode) controls.toggleMute() }
            )
        }
        ComponentType.SWITCH_VIBRATE -> {
            val label by controls.ringerModeLabel.collectAsState()
            SwitchTileComponent(active = label == "震动", subtitle = label,
                title = "震动",
                icon = Icons.Rounded.Vibration,
                onClick = { if (!isEditMode) controls.toggleVibrate() }
            )
        }
        ComponentType.SWITCH_AUTO_BRIGHTNESS -> {
            var actual by remember { mutableStateOf(controls.isAutoBrightnessEnabled()) }
            val expanded by expandedState().collectAsState()
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            LaunchedEffect(expanded, controls) {
                if (expanded) actual = controls.isAutoBrightnessEnabled()
            }
            DisposableEffect(lifecycle, expanded, controls) {
                val observer = LifecycleEventObserver { _, event ->
                    if (expanded && event == Lifecycle.Event.ON_RESUME) actual = controls.isAutoBrightnessEnabled()
                }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer) }
            }
            SwitchTileComponent(active = actual, subtitle = if (controls.canWriteSettings()) "点按切换" else "需修改设置权限（点按申请）",
                title = "自动亮度",
                icon = Icons.Rounded.BrightnessMedium,
                onClick = {
                    if (!isEditMode) {
                        controls.toggleAutoBrightness(); actual = controls.isAutoBrightnessEnabled()
                    }
                }
            )
        }
        ComponentType.SWITCH_AUTO_ROTATE -> {
            var actual by remember { mutableStateOf(controls.isAutoRotateEnabled()) }
            val expanded by expandedState().collectAsState()
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            LaunchedEffect(expanded, controls) {
                if (expanded) actual = controls.isAutoRotateEnabled()
            }
            DisposableEffect(lifecycle, expanded, controls) {
                val observer = LifecycleEventObserver { _, event ->
                    if (expanded && event == Lifecycle.Event.ON_RESUME) actual = controls.isAutoRotateEnabled()
                }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer) }
            }
            SwitchTileComponent(active = actual, subtitle = if (controls.canWriteSettings()) "点按切换" else "需修改设置权限（点按申请）",
                title = "自动旋转",
                icon = Icons.Rounded.ScreenRotation,
                onClick = {
                    if (!isEditMode) {
                        controls.toggleAutoRotate(); actual = controls.isAutoRotateEnabled()
                    }
                }
            )
        }
        ComponentType.SWITCH_KEEP_AWAKE -> {
            val keepAwake by controls.keepAwake.collectAsState()
            SwitchTileComponent(title = "面板常亮", subtitle = "仅面板显示期间", active = keepAwake,
                icon = Icons.Rounded.Nightlight, onClick = { if (!isEditMode) controls.toggleKeepAwake() })
        }
        else -> Unit
    }
}

@Composable
internal fun SystemActionCard(
    item: ComponentItem,
    isEditMode: Boolean,
    commands: SystemActionCardCommands,
    onRequestClose: () -> Unit
) {
    when (item.type) {
        ComponentType.SWITCH_LOCATION, ComponentType.SWITCH_HOTSPOT,
        ComponentType.SWITCH_NFC, ComponentType.SWITCH_AIRPLANE -> {
            val icon = when (item.type) {
                ComponentType.SWITCH_LOCATION -> Icons.Rounded.LocationOn
                ComponentType.SWITCH_HOTSPOT -> Icons.Rounded.WifiTethering
                ComponentType.SWITCH_NFC -> Icons.Rounded.Nfc
                else -> Icons.Rounded.Flight
            }
            SwitchTileComponent(
                title = item.title.ifBlank { item.type.label },
                subtitle = "打开系统设置",
                icon = icon,
                onClick = { dispatchSystemCardAction(item.type, isEditMode, commands, onRequestClose) }
            )
        }
        ComponentType.ACTION_LOCK -> {
            SwitchTileComponent(
                title = "锁屏",
                icon = Icons.Rounded.Lock,
                onClick = { dispatchSystemCardAction(item.type, isEditMode, commands, onRequestClose) }
            )
        }
        ComponentType.ACTION_HOME -> {
            SwitchTileComponent(
                title = "桌面",
                icon = Icons.Rounded.Home,
                onClick = { dispatchSystemCardAction(item.type, isEditMode, commands, onRequestClose) }
            )
        }
        ComponentType.ACTION_BACK -> {
            SwitchTileComponent(
                title = "返回",
                icon = Icons.Rounded.ArrowBack,
                onClick = { dispatchSystemCardAction(item.type, isEditMode, commands, onRequestClose) }
            )
        }
        ComponentType.ACTION_RECENTS -> {
            SwitchTileComponent(
                title = "最近任务",
                icon = Icons.Rounded.History,
                onClick = { dispatchSystemCardAction(item.type, isEditMode, commands, onRequestClose) }
            )
        }
        ComponentType.ACTION_NOTIFICATIONS -> {
            SwitchTileComponent(
                title = "通知栏",
                icon = Icons.Rounded.Notifications,
                onClick = { dispatchSystemCardAction(item.type, isEditMode, commands, onRequestClose) }
            )
        }
        ComponentType.ACTION_SPLIT_SCREEN -> {
            SwitchTileComponent(
                title = "分屏暂不可用",
                icon = Icons.Rounded.ViewAgenda,
                subtitle = "功能待修复，可编辑或移除此卡片",
                enabled = false,
                onClick = {}
            )
        }
        ComponentType.ACTION_SCREENSHOT -> {
            SwitchTileComponent(
                title = "截屏",
                icon = Icons.Rounded.Screenshot,
                onClick = { dispatchSystemCardAction(item.type, isEditMode, commands, onRequestClose) }
            )
        }
        ComponentType.ACTION_VOICE_ASSIST -> {
            SwitchTileComponent(
                title = "语音助手",
                icon = Icons.Rounded.Mic,
                onClick = { dispatchSystemCardAction(item.type, isEditMode, commands, onRequestClose) }
            )
        }
        ComponentType.ACTION_WECHAT_SCAN -> {
            SwitchTileComponent(
                title = "微信扫一扫",
                icon = Icons.Rounded.CenterFocusStrong,
                onClick = { dispatchSystemCardAction(item.type, isEditMode, commands, onRequestClose) }
            )
        }
        ComponentType.ACTION_REGION_SCREENSHOT -> {
            SwitchTileComponent(
                title = item.title.ifBlank { "区域截屏" },
                subtitle = "框选识别文字、翻译或保存",
                icon = Icons.Rounded.CropFree,
                onClick = { dispatchSystemCardAction(item.type, isEditMode, commands, onRequestClose) }
            )
        }
        ComponentType.ACTION_SCREEN_ASSISTANT -> {
            SwitchTileComponent(
                title = item.title.ifBlank { "全局识屏" },
                subtitle = "选字复制 · 原位翻译 · 提取小图",
                icon = Icons.Rounded.Translate,
                onClick = { dispatchSystemCardAction(item.type, isEditMode, commands, onRequestClose) }
            )
        }
        ComponentType.ACTION_BREENO_TOUCH -> {
            SwitchTileComponent(
                title = item.title.ifBlank { "小布识屏" },
                subtitle = "优先唤起 ColorOS 小布识屏",
                icon = Icons.Rounded.AutoAwesome,
                onClick = { dispatchSystemCardAction(item.type, isEditMode, commands, onRequestClose) }
            )
        }
        else -> Unit
    }
}

/** Order is part of each existing card action; permission and failure policy stays in its command owner. */
internal fun dispatchSystemCardAction(
    type: ComponentType,
    isEditMode: Boolean,
    commands: SystemActionCardCommands,
    onRequestClose: () -> Unit
) {
    if (isEditMode) return
    when (type) {
        ComponentType.SWITCH_LOCATION -> { onRequestClose(); commands.openLocationSettings() }
        ComponentType.SWITCH_HOTSPOT -> { onRequestClose(); commands.openHotspotSettings() }
        ComponentType.SWITCH_NFC -> { onRequestClose(); commands.openNfcSettings() }
        ComponentType.SWITCH_AIRPLANE -> { onRequestClose(); commands.openAirplaneModeSettings() }
        ComponentType.ACTION_LOCK -> { commands.lockScreen(); onRequestClose() }
        ComponentType.ACTION_HOME -> { commands.goHome(); onRequestClose() }
        ComponentType.ACTION_BACK -> { onRequestClose() }
        ComponentType.ACTION_RECENTS -> { commands.openRecentApps(); onRequestClose() }
        ComponentType.ACTION_NOTIFICATIONS -> { commands.expandNotifications(); onRequestClose() }
        ComponentType.ACTION_SCREENSHOT -> {
            if (!commands.hasAccessibilityService()) {
                commands.takeScreenshot()
            } else {
                onRequestClose()
                commands.postScreenshot(300L) { commands.takeScreenshot() }
            }
        }
        ComponentType.ACTION_VOICE_ASSIST -> { onRequestClose(); commands.launchVoiceAssistant() }
        ComponentType.ACTION_WECHAT_SCAN -> { commands.launchWechatScan(); onRequestClose() }
        ComponentType.ACTION_REGION_SCREENSHOT -> { commands.takeRegionScreenshot() }
        ComponentType.ACTION_SCREEN_ASSISTANT -> { commands.launchScreenAssistant() }
        ComponentType.ACTION_BREENO_TOUCH -> { commands.launchBreenoTouch() }
        else -> Unit
    }
}

@Composable
internal fun SystemEyeCareCard(active: Boolean, isEditMode: Boolean, onToggle: () -> Unit) {
    val eyeCare = active
    SwitchTileComponent(
        title = "全局护眼",
        icon = Icons.Rounded.Visibility,
        active = eyeCare,
        onClick = {
            if (!isEditMode) {
                onToggle()
            }
        }
    )
}
