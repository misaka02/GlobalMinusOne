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
internal fun ThemeSettingsScreen(
    repository: MinusOneRepository,
    initialExpandedGroup: String = "",
    jumpNonce: Long = 0L,
    onBack: () -> Unit
) {
    val state by repository.state.collectAsState()
    val themeConfig = state.themeConfig
    var showAnimationDialog by remember { mutableStateOf(false) }

    var expandedGroup by rememberSaveable { mutableStateOf(initialExpandedGroup) }
    val listState = rememberLazyListState()
    LaunchedEffect(initialExpandedGroup, jumpNonce) {
        if (initialExpandedGroup.isNotBlank()) {
            expandedGroup = initialExpandedGroup
            val idx = when (initialExpandedGroup) {
                "设置页面配色" -> 1
                "亮色 / 暗色模式与一键预设" -> 2
                "背景遮罩" -> 3
                "面板底框" -> 4
                "面板尺寸与位置" -> 5
                "卡片外观与透明度 (长按单个卡片可独立微调)" -> 6
                "文字与标题颜色及不透明度" -> 7
                "动画与其它" -> 8
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
        SubPageTopBar(title = "主题外观", onBack = onBack)

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 36.dp)
        ) {
            item { SettingsHint("选择一个分类展开设置，其他分类会自动收起。") }

            settingsGroup(
                title = "设置页面配色", summary = if (state.advancedConfig.settingsFollowSystemTheme) "当前跟随系统 · 仅影响设置界面" else "当前手动配色 · 仅影响设置界面",
                expanded = expandedGroup == "设置页面配色",
                onToggle = { expandedGroup = if (expandedGroup == "设置页面配色") "" else "设置页面配色" }
            ) {
                item {
                    PreferenceSwitchItem(
                        title = "设置页面跟随系统深色",
                        summary = if (state.advancedConfig.settingsFollowSystemTheme) "当前：自动跟随系统深浅色切换" else "当前：使用下方手动深色/亮色开关",
                        checked = state.advancedConfig.settingsFollowSystemTheme,
                        onCheckedChange = { follow ->
                            repository.updateAdvancedConfig { it.copy(settingsFollowSystemTheme = follow) }
                        }
                    )
                }

                item {
                    PreferenceSwitchItem(
                        title = "设置页面暗色模式",
                        summary = if (state.advancedConfig.settingsDarkMode) "当前：暗色 · 关闭切换亮色" else "当前：亮色 · 开启切换暗色",
                        checked = state.advancedConfig.settingsDarkMode,
                        onCheckedChange = { dark ->
                            repository.updateAdvancedConfig { it.copy(settingsDarkMode = dark, settingsFollowSystemTheme = false) }
                        }
                    )
                }

            }

            settingsGroup(
                title = "面板配色与预设", summary = "${if (themeConfig.followSystemDark) "跟随系统" else if (themeConfig.isDarkMode) "暗色卡片" else "亮色卡片"} · 常用视觉预设",
                expanded = expandedGroup == "亮色 / 暗色模式与一键预设",
                onToggle = { expandedGroup = if (expandedGroup == "亮色 / 暗色模式与一键预设") "" else "亮色 / 暗色模式与一键预设" }
            ) {

                item {
                    PreferenceSwitchItem(
                        title = "面板跟随系统深色模式",
                        summary = if (themeConfig.followSystemDark) "当前：自动跟随系统深浅色切换卡片与文字配色" else "当前：使用下方手动预设配色",
                        checked = themeConfig.followSystemDark,
                        onCheckedChange = { follow ->
                            repository.updateThemeConfig { it.copy(followSystemDark = follow) }
                        }
                    )
                }

                item {
                    PreferenceSwitchItem(
                        title = "暗色卡片模式",
                        summary = if (themeConfig.isDarkMode) "当前：暗色模式 (深色卡片底 + 浅白高对比度文字)" else "当前：亮色模式 (纯白卡片底 + 深炭黑高对比度文字)",
                        checked = themeConfig.isDarkMode,
                        onCheckedChange = { dark ->
                            repository.applyLightDarkPreset(
                                darkMode = dark,
                                cardAlpha = themeConfig.cardBgAlphaPercent,
                                panelAlpha = themeConfig.panelBgAlphaPercent
                            )
                        }
                    )
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "一键视觉预设", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { repository.applyLightDarkPreset(darkMode = false, cardAlpha = 100, panelAlpha = 0) },
                                modifier = Modifier.widthIn(min = 132.dp).heightIn(min = 48.dp)
                            ) {
                                Text(text = "亮色卡片", fontSize = 14.sp)
                            }
                            FilledTonalButton(
                                onClick = { repository.applyLightDarkPreset(darkMode = true, cardAlpha = 100, panelAlpha = 0) },
                                modifier = Modifier.widthIn(min = 132.dp).heightIn(min = 48.dp)
                            ) {
                                Text(text = "暗色卡片", fontSize = 14.sp)
                            }
                        }
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { repository.applyLightDarkPreset(darkMode = false, cardAlpha = 75, panelAlpha = 0) },
                                modifier = Modifier.widthIn(min = 132.dp).heightIn(min = 48.dp)
                            ) {
                                Text(text = "半透亮色", fontSize = 14.sp)
                            }
                            FilledTonalButton(
                                onClick = { repository.applyLightDarkPreset(darkMode = true, cardAlpha = 72, panelAlpha = 0) },
                                modifier = Modifier.widthIn(min = 132.dp).heightIn(min = 48.dp)
                            ) {
                                Text(text = "半透暗色", fontSize = 14.sp)
                            }
                            FilledTonalButton(
                                onClick = { repository.applyLightDarkPreset(darkMode = true, cardAlpha = 0, panelAlpha = 0) },
                                modifier = Modifier.widthIn(min = 132.dp).heightIn(min = 48.dp)
                            ) {
                                Text(text = "全透明无底", fontSize = 14.sp)
                            }
                        }
                    }
                }

            }

            settingsGroup(
                title = "背景遮罩", summary = "遮罩亮暗、透明度与模糊",
                expanded = expandedGroup == "背景遮罩",
                onToggle = { expandedGroup = if (expandedGroup == "背景遮罩") "" else "背景遮罩" }
            ) {
                item {
                    val isDarkMask = (themeConfig.maskColorHex and 0x00FFFFFFL) < 0x808080L
                    PreferenceSwitchItem(
                        title = "暗色背景遮罩",
                        summary = if (isDarkMask) "当前：暗色遮罩 (关闭则切换为亮色雾化遮罩)" else "当前：亮色雾化遮罩",
                        checked = isDarkMask,
                        onCheckedChange = { darkMask ->
                            repository.updateThemeConfig {
                                it.copy(maskColorHex = if (darkMask) 0xFF000000L else 0xFFF2F4F8L)
                            }
                        }
                    )
                }

                item {
                    PreferenceSliderItem(
                        title = "背景遮罩不透明度",
                        value = themeConfig.maskAlphaPercent.toFloat(),
                        valueRange = 0f..95f,
                        valueText = "${themeConfig.maskAlphaPercent}%",
                        onValueChange = { alpha ->
                            repository.updateThemeConfig { it.copy(maskAlphaPercent = alpha.toInt()) }
                        }
                    )
                }

                item {
                    PreferenceSliderItem(
                        title = "背景高斯模糊半径 (Android 12+)",
                        value = themeConfig.backgroundBlurRadius.toFloat(),
                        valueRange = 0f..80f,
                        valueText = "${themeConfig.backgroundBlurRadius}px",
                        onValueChange = { blur ->
                            repository.updateThemeConfig { it.copy(backgroundBlurRadius = blur.toInt()) }
                        }
                    )
                }
            }

            settingsGroup(
                title = "面板底框", summary = "底框配色、显示模式与透明度",
                expanded = expandedGroup == "面板底框",
                onToggle = { expandedGroup = if (expandedGroup == "面板底框") "" else "面板底框" }
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "面板抽屉底框模式", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { repository.updateThemeConfig { it.copy(panelBgAlphaPercent = 0) } },
                                modifier = Modifier.widthIn(min = 132.dp).heightIn(min = 48.dp)
                            ) {
                                Text(text = "透明无底框", fontSize = 14.sp)
                            }
                            FilledTonalButton(
                                onClick = {
                                    repository.updateThemeConfig {
                                        it.copy(
                                            panelBgColorHex = 0xFFF2F3F5L,
                                            panelBgAlphaPercent = if (it.panelBgAlphaPercent == 0) 85 else it.panelBgAlphaPercent
                                        )
                                    }
                                },
                                modifier = Modifier.widthIn(min = 132.dp).heightIn(min = 48.dp)
                            ) {
                                Text(text = "亮色底框", fontSize = 14.sp)
                            }
                            FilledTonalButton(
                                onClick = {
                                    repository.updateThemeConfig {
                                        it.copy(
                                            panelBgColorHex = 0xFF14171DL,
                                            panelBgAlphaPercent = if (it.panelBgAlphaPercent == 0) 85 else it.panelBgAlphaPercent
                                        )
                                    }
                                },
                                modifier = Modifier.widthIn(min = 132.dp).heightIn(min = 48.dp)
                            ) {
                                Text(text = "暗色底框", fontSize = 14.sp)
                            }
                        }
                    }
                }

                item {
                    PreferenceSliderItem(
                        title = "面板底框不透明度",
                        value = themeConfig.panelBgAlphaPercent.toFloat(),
                        valueRange = 0f..100f,
                        valueText = "${themeConfig.panelBgAlphaPercent}%",
                        onValueChange = { alpha ->
                            repository.updateThemeConfig {
                                val defaultHex = if ((it.panelBgColorHex and 0x00FFFFFFL) == 0L) {
                                    if (it.isDarkMode) 0xFF14171DL else 0xFFF2F3F5L
                                } else it.panelBgColorHex
                                it.copy(panelBgColorHex = defaultHex, panelBgAlphaPercent = alpha.toInt())
                            }
                        }
                    )
                }
            }

            settingsGroup(
                title = "面板尺寸与位置", summary = "面板宽高、位置与屏幕边距",
                expanded = expandedGroup == "面板尺寸与位置",
                onToggle = { expandedGroup = if (expandedGroup == "面板尺寸与位置") "" else "面板尺寸与位置" }
            ) {
                item {
                    PreferenceSliderItem(
                        title = "负一屏宽度",
                        value = themeConfig.panelWidthPercent.toFloat(),
                        valueRange = 30f..100f,
                        valueText = "${themeConfig.panelWidthPercent}%",
                        onValueChange = { w ->
                            repository.updateThemeConfig { it.copy(panelWidthPercent = w.toInt()) }
                        }
                    )
                }

                item {
                    PreferenceSliderItem(title = "负一屏高度", value = themeConfig.panelHeightPercent.toFloat(),
                        valueRange = 30f..100f, valueText = "${themeConfig.panelHeightPercent}%",
                        onValueChange = { v -> repository.updateThemeConfig { it.copy(panelHeightPercent = v.toInt()) } })
                }
                item {
                    PreferenceSliderItem(title = "面板离边缘位置", value = themeConfig.panelHorizontalOffsetPercent.toFloat(),
                        valueRange = 0f..100f, valueText = "${themeConfig.panelHorizontalOffsetPercent}% · 0%贴边",
                        onValueChange = { v -> repository.updateThemeConfig { it.copy(panelHorizontalOffsetPercent = v.toInt()) } })
                }
                item {
                    PreferenceSliderItem(title = "面板垂直位置", value = themeConfig.panelVerticalPositionPercent.toFloat(),
                        valueRange = 0f..100f, valueText = "${themeConfig.panelVerticalPositionPercent}% · 0%顶部 / 100%底部",
                        onValueChange = { v -> repository.updateThemeConfig { it.copy(panelVerticalPositionPercent = v.toInt()) } })
                }

                item {
                    PreferenceSliderItem(
                        title = "屏幕边缘间隔",
                        value = themeConfig.edgeMarginPercent.toFloat(),
                        valueRange = 0f..15f,
                        valueText = "${themeConfig.edgeMarginPercent}%",
                        onValueChange = { m ->
                            repository.updateThemeConfig { it.copy(edgeMarginPercent = m.toInt()) }
                        }
                    )
                }
            }

            settingsGroup(
                title = "卡片外观", summary = "默认底色、圆角与透明度；长按卡片可独立微调",
                expanded = expandedGroup == "卡片外观与透明度 (长按单个卡片可独立微调)",
                onToggle = { expandedGroup = if (expandedGroup == "卡片外观与透明度 (长按单个卡片可独立微调)") "" else "卡片外观与透明度 (长按单个卡片可独立微调)" }
            ) {

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "全局卡片默认底色", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                        val cardColorChoices = listOf(
                            "纯白亮色" to 0xFFFFFFFFL,
                            "柔和暖白" to 0xFFF2F4F7L,
                            "暗黑深灰" to 0xFF1E222BL,
                            "纯黑OLED" to 0xFF000000L
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            cardColorChoices.forEach { (label, hex) ->
                                FilledTonalButton(
                                    onClick = {
                                        val dark = (hex and 0x00FFFFFFL) < 0x808080L
                                        repository.updateThemeConfig {
                                            it.copy(
                                                isDarkMode = dark,
                                                followSystemDark = false,
                                                defaultCardBgColorHex = hex,
                                                defaultTextColorHex = if (dark) 0xFFF5F7FAL else 0xFF1F2329L,
                                                titleColorHex = if (dark) 0xFFFFFFFFL else 0xFF1F2329L
                                            )
                                        }
                                    },
                                    modifier = Modifier.widthIn(min = 132.dp).heightIn(min = 48.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Text(text = label, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }

                item {
                    PreferenceSliderItem(
                        title = "全局卡片背景不透明度",
                        value = themeConfig.cardBgAlphaPercent.toFloat(),
                        valueRange = 0f..100f,
                        valueText = "${themeConfig.cardBgAlphaPercent}%",
                        onValueChange = { alpha ->
                            repository.updateThemeConfig { it.copy(cardBgAlphaPercent = alpha.toInt()) }
                        }
                    )
                }

                item {
                    PreferenceSliderItem(
                        title = "卡片圆角",
                        value = themeConfig.cardCornerRadiusDp.toFloat(),
                        valueRange = 0f..50f,
                        valueText = "${themeConfig.cardCornerRadiusDp}px",
                        onValueChange = { r ->
                            repository.updateThemeConfig { it.copy(cardCornerRadiusDp = r.toInt()) }
                        }
                    )
                }

                item {
                    PreferenceSliderItem(
                        title = "卡片间距",
                        value = themeConfig.cardSpacingDp.coerceIn(
                            com.example.globalminusone.data.MIN_CARD_SPACING_DP,
                            com.example.globalminusone.data.MAX_CARD_SPACING_DP
                        ).toFloat(),
                        valueRange = com.example.globalminusone.data.MIN_CARD_SPACING_DP.toFloat()..com.example.globalminusone.data.MAX_CARD_SPACING_DP.toFloat(),
                        valueText = "${themeConfig.cardSpacingDp}dp",
                        onValueChange = { s ->
                            repository.updateThemeConfig { it.copy(cardSpacingDp = s.toInt()) }
                        }
                    )
                }

            }

            settingsGroup(
                title = "文字与标题", summary = "正文与标题的配色和透明度",
                expanded = expandedGroup == "文字与标题颜色及不透明度",
                onToggle = { expandedGroup = if (expandedGroup == "文字与标题颜色及不透明度") "" else "文字与标题颜色及不透明度" }
            ) {

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "全局文字与标题颜色", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                        val textColorChoices = listOf(
                            "深炭黑字" to 0xFF1F2329L,
                            "纯白亮字" to 0xFFFFFFFFL,
                            "柔和灰白" to 0xFFD0D5DDL,
                            "科技蓝字" to 0xFF2196F3L
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            textColorChoices.forEach { (label, hex) ->
                                FilledTonalButton(
                                    onClick = {
                                        repository.updateThemeConfig {
                                            it.copy(defaultTextColorHex = hex, titleColorHex = hex)
                                        }
                                    },
                                    modifier = Modifier.widthIn(min = 132.dp).heightIn(min = 48.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Text(text = label, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }

                item {
                    PreferenceSliderItem(
                        title = "全局文字不透明度",
                        value = themeConfig.textAlphaPercent.toFloat(),
                        valueRange = 10f..100f,
                        valueText = "${themeConfig.textAlphaPercent}%",
                        onValueChange = { alpha ->
                            repository.updateThemeConfig { it.copy(textAlphaPercent = alpha.toInt()) }
                        }
                    )
                }

                item {
                    PreferenceSliderItem(
                        title = "卡片标题不透明度",
                        value = themeConfig.titleAlphaPercent.toFloat(),
                        valueRange = 10f..100f,
                        valueText = "${themeConfig.titleAlphaPercent}%",
                        onValueChange = { alpha ->
                            repository.updateThemeConfig { it.copy(titleAlphaPercent = alpha.toInt()) }
                        }
                    )
                }

            }

            settingsGroup(
                title = "动画与其它", summary = "呼出动画、时长与异形屏适配",
                expanded = expandedGroup == "动画与其它",
                onToggle = { expandedGroup = if (expandedGroup == "动画与其它") "" else "动画与其它" }
            ) {

                item {
                    PreferenceItem(
                        title = "呼出动画",
                        summary = themeConfig.pageTransition.label,
                        onClick = { showAnimationDialog = true }
                    )
                }

                item {
                    PreferenceSliderItem(
                        title = "动画时间",
                        value = themeConfig.animationDurationMs.coerceIn(0, 800).toFloat(),
                        valueRange = 0f..800f,
                        valueText = if (themeConfig.animationDurationMs <= 0) "0ms (无动画)" else "${themeConfig.animationDurationMs}ms",
                        onValueChange = { ms ->
                            repository.updateThemeConfig { it.copy(animationDurationMs = ms.toInt()) }
                        }
                    )
                }

                item {
                    PreferenceSwitchItem(
                        title = "覆盖异形屏",
                        summary = "非异形屏请勿开启, 否则会出现显示异常",
                        checked = themeConfig.coverStatusBar,
                        onCheckedChange = { checked ->
                            repository.updateThemeConfig { it.copy(coverStatusBar = checked) }
                        }
                    )
                }
            }


        }
    }

    if (showAnimationDialog) {
        val transitions = PageTransition.values()
        SingleChoiceDialog(
            title = "呼出动画",
            options = transitions.map { it.label },
            selectedIndex = transitions.indexOf(themeConfig.pageTransition),
            onSelect = { idx ->
                repository.updateThemeConfig { it.copy(pageTransition = transitions[idx]) }
                showAnimationDialog = false
            },
            onDismiss = { showAnimationDialog = false }
        )
    }
}
