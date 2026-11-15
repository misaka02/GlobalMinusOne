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
internal fun OperationSettingsScreen(
    repository: MinusOneRepository,
    onBack: () -> Unit,
    onNavigateToSBSettings: () -> Unit
) {
    val state by repository.state.collectAsState()
    val triggerConfig = state.triggerConfig

    var showTriggerWayDialog by remember { mutableStateOf(false) }
    var showTriggerPosDialog by remember { mutableStateOf(false) }
    var showShowSideDialog by remember { mutableStateOf(false) }
    var gestureActionTarget by remember { mutableStateOf<String?>(null) }

    var expandedGroup by rememberSaveable { mutableStateOf("触发") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SubPageTopBar(title = "触发设置", onBack = onBack)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 36.dp)
        ) {
            // 分组：触发
            item { SettingsHint("选择一个分类展开设置，其他分类会自动收起。") }

            settingsGroup(
                title = "触发", summary = "${triggerConfig.triggerWay.label} · 滑动距离与触发方式",
                expanded = expandedGroup == "触发",
                onToggle = { expandedGroup = if (expandedGroup == "触发") "" else "触发" }
            ) {

                item {
                    PreferenceItem(
                        title = "触发方式",
                        summary = triggerConfig.triggerWay.label,
                        onClick = { showTriggerWayDialog = true }
                    )
                }

                if (triggerConfig.triggerWay == TriggerWay.SLIDE) item {
                    Text(
                        text = "从可见手势条内开始滑动，按“位置”分类中的“手势条动作”执行。当前下滑：${triggerConfig.swipeDownAction.label}。" +
                            if (triggerConfig.swipeDownAction == com.example.globalminusone.data.GestureAction.NONE)
                                "需要向下呼出面板时，请将触发方式改为“侧边下滑”。" else "",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }

                if (triggerConfig.triggerWay == TriggerWay.SECOND_SLIDE) item {
                    Column {
                    PreferenceSliderItem(
                        title = "二次滑出有效时间窗",
                        value = triggerConfig.secondSlideTimeoutMs.toFloat(),
                        valueRange = 400f..3000f,
                        valueText = "${triggerConfig.secondSlideTimeoutMs} ms",
                        onValueChange = { ms ->
                            repository.updateTriggerConfig { it.copy(secondSlideTimeoutMs = ms.toInt().coerceIn(400, 3000)) }
                        }
                    )
                    Text(
                        text = "首次触碰边缘条将亮起激活提示，在此时间窗内再次滑动即可触发对应动作。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                    }
}

                if (triggerConfig.triggerWay == TriggerWay.SIDE_PULLDOWN) item {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                        Text(
                            text = "侧边下滑手势条位置（当前：${triggerConfig.triggerPosition.label}）",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "点击下方按钮可直接切换手势条在屏幕左侧、右侧或双侧；也可在下方“区域”调节距侧边的左右水平距离。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.5.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(TriggerPosition.LEFT, TriggerPosition.RIGHT, TriggerPosition.BOTH).forEach { pos ->
                                val selected = triggerConfig.triggerPosition == pos
                                if (selected) {
                                    Button(
                                        onClick = { repository.updateTriggerConfig { it.withPosition(pos) } },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(pos.label, fontSize = 13.sp)
                                    }
                                } else {
                                    FilledTonalButton(
                                        onClick = { repository.updateTriggerConfig { it.withPosition(pos) } },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(pos.label, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Column {
                    PreferenceSliderItem(
                        title = "最小滑动距离",
                        value = triggerConfig.minimumSwipeDistanceDp.toFloat(),
                        valueRange = 16f..160f,
                        valueText = "${triggerConfig.minimumSwipeDistanceDp} dp",
                        onValueChange = { distance ->
                            repository.updateTriggerConfig { it.copy(minimumSwipeDistanceDp = distance.toInt()) }
                        }
                    )
                    Text(
                        text = if (triggerConfig.triggerWay == TriggerWay.SIDE_PULLDOWN) "在侧边触发条内主要向下移动达到此距离后，松手呼出负一屏。" else "边缘条与悬浮球滑动达到此距离才触发动作；数值越大越不易误触。系统点击与移动的判断距离保持不变。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                    }
}

            }

            settingsGroup(
                title = "反馈与避让", summary = "震动反馈、系统震动与输入法避让",
                expanded = expandedGroup == "反馈与避让",
                onToggle = { expandedGroup = if (expandedGroup == "反馈与避让") "" else "反馈与避让" }
            ) {
                item {
                    PreferenceSwitchItem(
                        title = "震动",
                        checked = triggerConfig.vibrate,
                        onCheckedChange = { checked ->
                            repository.updateTriggerConfig { it.copy(vibrate = checked) }
                        }
                    )
                }

                item {
                    PreferenceSwitchItem(
                        title = "使用系统震动",
                        summary = "部分机型开启后会造成手机重启",
                        checked = triggerConfig.useSystemVibrate,
                        onCheckedChange = { checked ->
                            repository.updateTriggerConfig { it.copy(useSystemVibrate = checked) }
                        }
                    )
                }

                item {
                    PreferenceSwitchItem(
                        title = "避让输入法",
                        checked = triggerConfig.avoidInputMethod,
                        onCheckedChange = { checked ->
                            repository.updateTriggerConfig { it.copy(avoidInputMethod = checked) }
                        }
                    )
                }
            }

            settingsGroup(
                title = "位置", summary = "触发条：${triggerConfig.triggerPosition.label} · 面板：${triggerConfig.defaultShowSide.label}",
                expanded = expandedGroup == "位置",
                onToggle = { expandedGroup = if (expandedGroup == "位置") "" else "位置" }
            ) {

                item {
                    PreferenceItem(
                        title = "触发位置",
                        summary = triggerConfig.triggerPosition.label,
                        onClick = { showTriggerPosDialog = true }
                    )
                }

                item {
                    PreferenceItem(
                        title = "负一屏默认显示位置",
                        summary = triggerConfig.defaultShowSide.label,
                        onClick = { showShowSideDialog = true }
                    )
                }

            }

            settingsGroup(
                title = "区域", summary = "手势条可见性、尺寸、透明度与偏移",
                expanded = expandedGroup == "区域",
                onToggle = { expandedGroup = if (expandedGroup == "区域") "" else "区域" }
            ) {

                item {
                    PreferenceSwitchItem(
                        title = "触发区域可见",
                        checked = triggerConfig.touchVisible,
                        onCheckedChange = { checked ->
                            repository.updateTriggerConfig { it.copy(touchVisible = checked) }
                        }
                    )
                }

                item {
                    PreferenceSliderItem(
                        title = "手势条透明度",
                        value = triggerConfig.touchAlpha,
                        valueRange = 0.05f..1.0f,
                        valueText = "${(triggerConfig.touchAlpha * 100).toInt()}%",
                        onValueChange = { alpha ->
                            repository.updateTriggerConfig { it.copy(touchAlpha = alpha) }
                        }
                    )
                }

                item {
                    PreferenceSliderItem(
                        title = "宽(高)度",
                        value = triggerConfig.touchWidthPercent.toFloat(),
                        valueRange = 1f..20f,
                        valueText = "${triggerConfig.touchWidthPercent}%",
                        onValueChange = { w ->
                            repository.updateTriggerConfig { it.copy(touchWidthPercent = w.toInt()) }
                        }
                    )
                }

                item {
                    PreferenceSliderItem(
                        title = "高(宽)度 (长度)",
                        value = triggerConfig.touchHeightPercent.toFloat(),
                        valueRange = 5f..100f,
                        valueText = "${triggerConfig.touchHeightPercent}%",
                        onValueChange = { h ->
                            repository.updateTriggerConfig { it.copy(touchHeightPercent = h.toInt()) }
                        }
                    )
                }

                item {
                    val isHorizontalBar = triggerConfig.triggerWay != TriggerWay.SIDE_PULLDOWN &&
                        (triggerConfig.triggerPosition == TriggerPosition.TOP || triggerConfig.triggerPosition == TriggerPosition.BOTTOM)
                    PreferenceSliderItem(
                        title = if (isHorizontalBar) "左右水平偏移 (位置)" else "上下垂直位置 (高度位置)",
                        value = triggerConfig.touchOffsetPercent.toFloat(),
                        valueRange = 0f..100f,
                        valueText = "${triggerConfig.touchOffsetPercent}%",
                        onValueChange = { offset ->
                            repository.updateTriggerConfig { it.copy(touchOffsetPercent = offset.toInt()) }
                        }
                    )
                }

                if (triggerConfig.triggerWay == TriggerWay.SIDE_PULLDOWN) item {
                    Column {
                    val customDp = triggerConfig.sidePullHorizontalOffsetDp
                    val sliderVal = if (customDp >= 0) customDp.coerceIn(0, 160).toFloat() else 32f
                    PreferenceSliderItem(
                        title = "左右水平位置 (距侧边距离)",
                        value = sliderVal,
                        valueRange = 0f..160f,
                        valueText = if (customDp < 0) "自动避让" else "${customDp} dp",
                        onValueChange = { dpVal ->
                            repository.updateTriggerConfig { it.copy(sidePullHorizontalOffsetDp = dpVal.toInt().coerceIn(0, 160)) }
                        }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (customDp < 0) "当前自动内移避开系统侧滑返回区；拖动滑块可自由调节左右距离（0 dp 为紧贴屏幕边缘）。"
                            else "0 dp 紧贴屏幕最边缘，数值越大越向屏幕内侧偏移。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.5.sp,
                            modifier = Modifier.weight(1f).padding(top = 4.dp)
                        )
                        if (customDp >= 0) {
                            TextButton(
                                onClick = { repository.updateTriggerConfig { it.copy(sidePullHorizontalOffsetDp = -1) } }
                            ) {
                                Text("恢复自动", fontSize = 12.5.sp)
                            }
                        }
                    }
                    }
}

            }

            settingsGroup(
                title = "手势条动作", summary = "单击、双击、长按与四向滑动",
                expanded = expandedGroup == "手势条动作",
                onToggle = { expandedGroup = if (expandedGroup == "手势条动作") "" else "手势条动作" }
            ) {
                if (triggerConfig.triggerWay != TriggerWay.SIDE_PULLDOWN) {


                item {
                    PreferenceItem(
                        title = "单击",
                        summary = triggerConfig.singleTapAction.label,
                        onClick = { gestureActionTarget = "single" }
                    )
                }

                item {
                    PreferenceItem(
                        title = "双击",
                        summary = triggerConfig.doubleTapAction.label,
                        onClick = { gestureActionTarget = "double" }
                    )
                }

                item {
                    PreferenceItem(
                        title = "长按",
                        summary = triggerConfig.longPressAction.label,
                        onClick = { gestureActionTarget = "long" }
                    )
                }

                item {
                    PreferenceItem(
                        title = "上滑",
                        summary = triggerConfig.swipeUpAction.label,
                        onClick = { gestureActionTarget = "up" }
                    )
                }

                item {
                    PreferenceItem(
                        title = "下滑",
                        summary = triggerConfig.swipeDownAction.label,
                        onClick = { gestureActionTarget = "down" }
                    )
                }

                item {
                    PreferenceItem(
                        title = "左滑",
                        summary = triggerConfig.swipeLeftAction.label,
                        onClick = { gestureActionTarget = "left" }
                    )
                }

                item {
                    PreferenceItem(
                        title = "右滑",
                        summary = triggerConfig.swipeRightAction.label,
                        onClick = { gestureActionTarget = "right" }
                    )
                }

                } else {
                    item { SettingsHint("侧边下滑固定用于呼出面板；切换其它触发方式后可设置手势条动作。") }
                }

            }

            settingsGroup(
                title = "悬浮球", summary = "进入悬浮球的外观、鼠标与动作设置",
                expanded = expandedGroup == "悬浮球",
                onToggle = { expandedGroup = if (expandedGroup == "悬浮球") "" else "悬浮球" }
            ) {

                item {
                    PreferenceItem(
                        title = "悬浮球设置",
                        summary = "外观、呼吸、渐隐、鼠标模式与动作手势",
                        onClick = onNavigateToSBSettings
                    )
                }
            }


        }
    }

    // 触发方式单选弹窗
    if (showTriggerWayDialog) {
        SingleChoiceDialog(
            title = "触发方式",
            options = TriggerWay.values().map { it.label },
            selectedIndex = TriggerWay.values().indexOf(triggerConfig.triggerWay),
            onSelect = { idx ->
                val way = TriggerWay.values()[idx]
                repository.updateTriggerConfig { current ->
                    val normalized = if (way == TriggerWay.SIDE_PULLDOWN &&
                        current.triggerPosition !in setOf(TriggerPosition.LEFT, TriggerPosition.RIGHT, TriggerPosition.BOTH)
                    ) {
                        current.withPosition(TriggerPosition.LEFT)
                    } else {
                        current
                    }
                    normalized.copy(triggerWay = way)
                }
                showTriggerWayDialog = false
            },
            onDismiss = { showTriggerWayDialog = false }
        )
    }

    // 触发位置单选弹窗
    if (showTriggerPosDialog) {
        val positions = if (triggerConfig.triggerWay == TriggerWay.SIDE_PULLDOWN)
            listOf(TriggerPosition.LEFT, TriggerPosition.RIGHT, TriggerPosition.BOTH) else TriggerPosition.values().toList()
        SingleChoiceDialog(
            title = "触发位置",
            options = positions.map { it.label },
            selectedIndex = positions.indexOf(triggerConfig.triggerPosition),
            onSelect = { idx ->
                repository.updateTriggerConfig { it.withPosition(positions[idx]) }
                showTriggerPosDialog = false
            },
            onDismiss = { showTriggerPosDialog = false }
        )
    }

    // 呼出方向单选弹窗
    if (showShowSideDialog) {
        SingleChoiceDialog(
            title = "负一屏默认显示位置",
            options = ShowSideMode.values().map { it.label },
            selectedIndex = ShowSideMode.values().indexOf(triggerConfig.defaultShowSide),
            onSelect = { idx ->
                repository.updateTriggerConfig { it.copy(defaultShowSide = ShowSideMode.values()[idx]) }
                showShowSideDialog = false
            },
            onDismiss = { showShowSideDialog = false }
        )
    }

    // 12 手势动作单选弹窗
    if (gestureActionTarget != null) {
        val target = gestureActionTarget!!
        val currentAction = when (target) {
            "single" -> triggerConfig.singleTapAction
            "double" -> triggerConfig.doubleTapAction
            "long" -> triggerConfig.longPressAction
            "up" -> triggerConfig.swipeUpAction
            "down" -> triggerConfig.swipeDownAction
            "left" -> triggerConfig.swipeLeftAction
            "right" -> triggerConfig.swipeRightAction
            else -> GestureAction.NONE
        }

        GestureActionChoiceDialog(
            title = "动作设置",
            currentAction = currentAction,
            onSelect = { action ->
                repository.updateTriggerConfig { cfg ->
                    when (target) {
                        "single" -> cfg.copy(singleTapAction = action)
                        "double" -> cfg.copy(doubleTapAction = action)
                        "long" -> cfg.copy(longPressAction = action)
                        "up" -> cfg.copy(swipeUpAction = action)
                        "down" -> cfg.copy(swipeDownAction = action)
                        "left" -> cfg.copy(swipeLeftAction = action)
                        "right" -> cfg.copy(swipeRightAction = action)
                        else -> cfg
                    }
                }
                gestureActionTarget = null
            },
            onDismiss = { gestureActionTarget = null }
        )
    }
}
