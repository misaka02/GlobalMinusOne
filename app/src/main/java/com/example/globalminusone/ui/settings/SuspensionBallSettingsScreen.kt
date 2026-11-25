package com.example.globalminusone.ui.settings

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.globalminusone.ui.AppIcons as Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.globalminusone.data.ConfigProfile
import com.example.globalminusone.data.GestureAction
import com.example.globalminusone.data.IconClickBehavior
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.PageTransition
import com.example.globalminusone.data.ScenarioModeItem
import com.example.globalminusone.data.ShowSideMode
import com.example.globalminusone.data.TriggerPosition
import com.example.globalminusone.data.TriggerWay
import com.example.globalminusone.service.GlobalAccessibilityService
import com.example.globalminusone.service.OverlayService

@Composable
internal fun SuspensionBallSettingsScreen(
    repository: MinusOneRepository,
    initialExpandedGroup: String = "",
    jumpNonce: Long = 0L,
    onBack: () -> Unit
) {
    val state by repository.state.collectAsState()
    val ballConfig = state.suspensionBallConfig

    var gestureActionTarget by remember { mutableStateOf<String?>(null) }
    var showMouseSpeedDialog by remember { mutableStateOf(false) }

    var expandedGroup by rememberSaveable { mutableStateOf(initialExpandedGroup) }
    val listState = rememberLazyListState()
    LaunchedEffect(initialExpandedGroup, jumpNonce) {
        if (initialExpandedGroup.isNotBlank()) {
            expandedGroup = initialExpandedGroup
            val idx = when (initialExpandedGroup) {
                "外观" -> 1
                "呼吸" -> 2
                "渐隐" -> 3
                "鼠标" -> 4
                "动作" -> 5
                else -> null
            }
            if (idx != null) listState.animateScrollToItem(idx)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SubPageTopBar(title = "悬浮球设置", onBack = onBack)

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 36.dp)
        ) {
            // 分组：外观
            item { SettingsHint("选择一个分类展开设置，其他分类会自动收起。") }

            settingsGroup(
                title = "外观", summary = "大小、颜色、透明度与吸附",
                expanded = expandedGroup == "外观",
                onToggle = { expandedGroup = if (expandedGroup == "外观") "" else "外观" }
            ) {

                item {
                    PreferenceSwitchItem(
                        title = "图标",
                        checked = ballConfig.showIcon,
                        onCheckedChange = { checked ->
                            repository.updateSuspensionBallConfig { it.copy(showIcon = checked) }
                        }
                    )
                }

                item {
                    PreferenceSwitchItem(
                        title = "自动贴边吸附",
                        summary = "拖动松手后自动平滑吸附至屏幕左右侧边缘；关闭则允许自由停留在任意位置",
                        checked = ballConfig.snapToEdge,
                        onCheckedChange = { checked ->
                            repository.updateSuspensionBallConfig {
                                it.copy(
                                    snapToEdge = checked,
                                    posXPercent = com.example.globalminusone.service.OverlayVisibilityPolicy.snapSuspensionBallXPercent(it.posXPercent, checked)
                                )
                            }
                        }
                    )
                }

                item {
                    PreferenceSliderItem(
                        title = "半径",
                        value = ballConfig.radiusDp.toFloat(),
                        valueRange = 14f..60f,
                        valueText = "${ballConfig.radiusDp} dp",
                        onValueChange = { r ->
                            repository.updateSuspensionBallConfig { it.copy(radiusDp = r.toInt()) }
                        }
                    )
                }

                item {
                    PreferenceSliderItem(
                        title = "透明度",
                        value = ballConfig.alpha,
                        valueRange = 0.10f..1.0f,
                        valueText = "${(ballConfig.alpha * 100).toInt()}%",
                        onValueChange = { a ->
                            repository.updateSuspensionBallConfig { it.copy(alpha = a) }
                        }
                    )
                }

            }

            settingsGroup(
                title = "呼吸", summary = "呼吸效果与变化速度",
                expanded = expandedGroup == "呼吸",
                onToggle = { expandedGroup = if (expandedGroup == "呼吸") "" else "呼吸" }
            ) {

                item {
                    PreferenceSwitchItem(
                        title = "呼吸模式",
                        checked = ballConfig.breatheMode,
                        onCheckedChange = { checked ->
                            repository.updateSuspensionBallConfig { it.copy(breatheMode = checked) }
                        }
                    )
                }

                item {
                    PreferenceSliderItem(
                        title = "呼吸频率",
                        value = ballConfig.breatheFrequencySeconds.coerceIn(1, 5).toFloat(),
                        valueRange = 1f..5f,
                        valueText = "${ballConfig.breatheFrequencySeconds.coerceIn(1, 5)}s",
                        onValueChange = { freq ->
                            repository.updateSuspensionBallConfig { it.copy(breatheFrequencySeconds = freq.toInt().coerceIn(1, 5)) }
                        }
                    )
                }

            }

            settingsGroup(
                title = "渐隐", summary = "空闲时的透明度与等待时间",
                expanded = expandedGroup == "渐隐",
                onToggle = { expandedGroup = if (expandedGroup == "渐隐") "" else "渐隐" }
            ) {

                item {
                    val isFadeOut = ballConfig.autoFadeSeconds > 0
                    PreferenceSwitchItem(
                        title = "自动渐隐",
                        checked = isFadeOut,
                        onCheckedChange = { checked ->
                            repository.updateSuspensionBallConfig {
                                it.copy(autoFadeSeconds = if (checked) 10 else 0)
                            }
                        }
                    )
                }

                item {
                    PreferenceSliderItem(
                        title = "等待时间",
                        value = ballConfig.autoFadeSeconds.coerceAtLeast(1).toFloat(),
                        valueRange = 1f..30f,
                        valueText = "${ballConfig.autoFadeSeconds.coerceAtLeast(1)}s",
                        onValueChange = { sec ->
                            repository.updateSuspensionBallConfig { it.copy(autoFadeSeconds = sec.toInt()) }
                        }
                    )
                }

            }

            settingsGroup(
                title = "鼠标", summary = "边缘停留进入 · 相对指针与点击",
                expanded = expandedGroup == "鼠标",
                onToggle = { expandedGroup = if (expandedGroup == "鼠标") "" else "鼠标" }
            ) {

                item {
                    PreferenceSwitchItem(
                        title = "鼠标模式",
                        summary = "长按并拖动悬浮球到屏幕边缘，停留约0.6秒后进入。需要辅助服务已连接；未开启时只提示，不自动跳转授权。",
                        checked = ballConfig.mouseMode,
                        onCheckedChange = { checked ->
                            repository.updateSuspensionBallConfig { it.copy(mouseMode = checked) }
                        }
                    )
                }

                item {
                    PreferenceItem(
                        title = "移动速度",
                        summary = ballConfig.mouseSpeed,
                        onClick = { showMouseSpeedDialog = true }
                    )
                }

                item {
                    PreferenceSliderItem(
                        title = "触控行程（屏幕短边比例）",
                        value = ballConfig.mouseRange.coerceIn(20, 80).toFloat(),
                        valueRange = 20f..80f,
                        valueText = "${ballConfig.mouseRange.coerceIn(20, 80)}% · 数值越大，移动越精细",
                        onValueChange = { range ->
                            repository.updateSuspensionBallConfig { it.copy(mouseRange = range.toInt().coerceIn(20, 80)) }
                        }
                    )
                }

                item {
                    SettingsHint("进入后先松手，再在球上滑动来移动指针；短点球可点击指针位置。双指触碰球，或长按球后点“退出鼠标模式”可恢复普通悬浮球。指针本身不接收触摸，球只在自身范围内接收新触摸。")
                }

            }

            settingsGroup(
                title = "动作", summary = "点击、长按与滑动动作",
                expanded = expandedGroup == "动作",
                onToggle = { expandedGroup = if (expandedGroup == "动作") "" else "动作" }
            ) {

                item {
                    PreferenceItem(
                        title = "单击",
                        summary = ballConfig.singleTapAction.label,
                        onClick = { gestureActionTarget = "sb_single" }
                    )
                }

                item {
                    PreferenceItem(
                        title = "双击",
                        summary = ballConfig.doubleTapAction.label,
                        onClick = { gestureActionTarget = "sb_double" }
                    )
                }

                item {
                    PreferenceItem(
                        title = "左滑",
                        summary = ballConfig.swipeLeftAction.label,
                        onClick = { gestureActionTarget = "sb_left" }
                    )
                }

                item {
                    PreferenceItem(
                        title = "右滑",
                        summary = ballConfig.swipeRightAction.label,
                        onClick = { gestureActionTarget = "sb_right" }
                    )
                }

                item {
                    PreferenceItem(
                        title = "上滑",
                        summary = ballConfig.swipeUpAction.label,
                        onClick = { gestureActionTarget = "sb_up" }
                    )
                }

                item {
                    PreferenceItem(
                        title = "下滑",
                        summary = ballConfig.swipeDownAction.label,
                        onClick = { gestureActionTarget = "sb_down" }
                    )
                }

                item {
                    PreferenceItem(
                        title = "长按",
                        summary = ballConfig.longPressAction.label,
                        onClick = { gestureActionTarget = "sb_long" }
                    )
                }
            }


        }
    }

    if (showMouseSpeedDialog) {
        val speeds = listOf("快", "一般", "慢")
        SingleChoiceDialog(
            title = "移动速度",
            options = speeds,
            selectedIndex = speeds.indexOf(ballConfig.mouseSpeed).coerceAtLeast(0),
            onSelect = { idx ->
                repository.updateSuspensionBallConfig { it.copy(mouseSpeed = speeds[idx]) }
                showMouseSpeedDialog = false
            },
            onDismiss = { showMouseSpeedDialog = false }
        )
    }

    if (gestureActionTarget != null) {
        val target = gestureActionTarget!!
        val currentAction = when (target) {
            "sb_single" -> ballConfig.singleTapAction
            "sb_double" -> ballConfig.doubleTapAction
            "sb_left" -> ballConfig.swipeLeftAction
            "sb_right" -> ballConfig.swipeRightAction
            "sb_up" -> ballConfig.swipeUpAction
            "sb_down" -> ballConfig.swipeDownAction
            "sb_long" -> ballConfig.longPressAction
            else -> GestureAction.NONE
        }

        GestureActionChoiceDialog(
            title = "悬浮球动作",
            currentAction = currentAction,
            onSelect = { action ->
                repository.updateSuspensionBallConfig { cfg ->
                    when (target) {
                        "sb_single" -> cfg.copy(singleTapAction = action)
                        "sb_double" -> cfg.copy(doubleTapAction = action)
                        "sb_left" -> cfg.copy(swipeLeftAction = action)
                        "sb_right" -> cfg.copy(swipeRightAction = action)
                        "sb_up" -> cfg.copy(swipeUpAction = action)
                        "sb_down" -> cfg.copy(swipeDownAction = action)
                        "sb_long" -> cfg.copy(longPressAction = action)
                        else -> cfg
                    }
                }
                gestureActionTarget = null
            },
            onDismiss = { gestureActionTarget = null }
        )
    }
}
