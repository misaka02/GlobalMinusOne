package com.example.globalminusone.ui.panel.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.CardReorderMotionStyle
import com.example.globalminusone.data.FOLLOW_GLOBAL_ANIMATION_MS
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.ModuleAnimationConfig
import com.example.globalminusone.data.PageTransition
import com.example.globalminusone.data.SubPanelTransitionStyle
import com.example.globalminusone.data.ThemeConfig
import com.example.globalminusone.data.formatModuleAnimationSummary
import com.example.globalminusone.ui.panel.PanelPalette
import kotlin.math.roundToInt

/** Unified per-module animation editor inside ThemeAppearanceModal; syncs bidirectionally with App Settings. */
@Composable
internal fun PanelAnimationSettingsSection(
    theme: ThemeConfig,
    repository: MinusOneRepository,
    palette: PanelPalette,
    modalTrack: Color,
    modalTextPrimary: Color
) {
    val modules = theme.moduleAnimations
    val updateModules: ((ModuleAnimationConfig) -> ModuleAnimationConfig) -> Unit = { transform ->
        repository.updateThemeConfig { cfg -> cfg.copy(moduleAnimations = transform(cfg.moduleAnimations)) }
    }

    Text(text = "翻页转场效果", color = palette.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    PanelChipGrid(
        options = PageTransition.values().toList(),
        selected = theme.pageTransition,
        columns = 3,
        palette = palette,
        trackColor = modalTrack,
        textColor = modalTextPrimary,
        labelOf = { it.label },
        onSelect = { t -> repository.updateThemeConfig { cfg -> cfg.copy(pageTransition = t) } }
    )

    Text(text = "全局默认动画时长（0 为关闭）", color = palette.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("关闭 0" to 0, "极速 50" to 50, "轻快 120" to 120, "流畅 260" to 260).forEach { (label, ms) ->
            val selected = theme.animationDurationMs == ms
            Surface(
                onClick = { repository.updateThemeConfig { it.copy(animationDurationMs = ms) } },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                color = if (selected) palette.accent else modalTrack
            ) {
                Box(Modifier.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) Color.White else modalTextPrimary,
                        maxLines = 1
                    )
                }
            }
        }
    }
    var draftGlobalMs by remember { mutableStateOf<Int?>(null) }
    NumberStepper(
        "默认时长 (ms)",
        draftGlobalMs ?: theme.animationDurationMs,
        { v -> repository.updateThemeConfig { it.copy(animationDurationMs = v) } },
        minimum = 0,
        maximum = 800
    )
    BufferedDurationSlider(
        valueMs = theme.animationDurationMs,
        accentColor = palette.accent,
        onDraftChange = { draftGlobalMs = it },
        onCommit = { ms -> repository.updateThemeConfig { it.copy(animationDurationMs = ms) } }
    )

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(text = "各模块细分动画（与设置同步）", color = palette.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        if (modules.hasCustomOverrides()) {
            Surface(
                onClick = { updateModules { ModuleAnimationConfig() } },
                shape = RoundedCornerShape(7.dp),
                color = palette.accent.copy(alpha = 0.14f)
            ) {
                Text("全部跟随", Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = palette.accent, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    PanelModuleDurationCard("面板呼出与收起", modules.panelOpenCloseDurationMs, theme.animationDurationMs, palette, modalTrack, modalTextPrimary, onChange = { ms ->
        updateModules { it.copy(panelOpenCloseDurationMs = ms) }
    })
    PanelModuleDurationCard("页面切换翻页", modules.pageSwitchDurationMs, theme.animationDurationMs, palette, modalTrack, modalTextPrimary, onChange = { ms ->
        updateModules { it.copy(pageSwitchDurationMs = ms) }
    })
    SubPanelAnimationSyncBlock(theme, repository, palette, modalTrack, modalTextPrimary)
    CardReorderAnimationSyncBlock(theme, repository, palette, modalTrack, modalTextPrimary)
    PanelModuleDurationCard("文件夹、容器与浮窗", modules.containerPopupDurationMs, theme.animationDurationMs, palette, modalTrack, modalTextPrimary, onChange = { ms ->
        updateModules { it.copy(containerPopupDurationMs = ms) }
    })
    PanelModuleDurationCard("弹窗、菜单与设置页", modules.modalMenuDurationMs, theme.animationDurationMs, palette, modalTrack, modalTextPrimary, onChange = { ms ->
        updateModules { it.copy(modalMenuDurationMs = ms) }
    })
}

@Composable
internal fun SubPanelAnimationSyncBlock(
    theme: ThemeConfig,
    repository: MinusOneRepository,
    palette: PanelPalette,
    trackColor: Color = if (palette.isDark) Color(0xFF2B313D) else Color(0xFFF1F4F8),
    textColor: Color = palette.textPrimary
) {
    val modules = theme.moduleAnimations
    PanelModuleDurationCard("子面板切换动画", modules.subPanelDurationMs, theme.animationDurationMs, palette, trackColor, textColor,
        onChange = { ms -> repository.updateThemeConfig { it.copy(moduleAnimations = it.moduleAnimations.copy(subPanelDurationMs = ms)) } }
    ) {
        PanelChipGrid(
            options = SubPanelTransitionStyle.values().toList(),
            selected = modules.subPanelTransitionStyle,
            columns = 4,
            palette = palette,
            trackColor = trackColor,
            textColor = textColor,
            labelOf = { it.label },
            onSelect = { style ->
                repository.updateThemeConfig { it.copy(moduleAnimations = it.moduleAnimations.copy(subPanelTransitionStyle = style)) }
            }
        )
    }
}

@Composable
internal fun CardReorderAnimationSyncBlock(
    theme: ThemeConfig,
    repository: MinusOneRepository,
    palette: PanelPalette,
    trackColor: Color = if (palette.isDark) Color(0xFF2B313D) else Color(0xFFF1F4F8),
    textColor: Color = palette.textPrimary
) {
    val modules = theme.moduleAnimations
    PanelModuleDurationCard("卡片移动与换位手感", modules.cardReorderDurationMs, theme.animationDurationMs, palette, trackColor, textColor,
        onChange = { ms -> repository.updateThemeConfig { it.copy(moduleAnimations = it.moduleAnimations.copy(cardReorderDurationMs = ms)) } }
    ) {
        PanelChipGrid(
            options = CardReorderMotionStyle.values().toList(),
            selected = modules.cardReorderStyle,
            columns = 4,
            palette = palette,
            trackColor = trackColor,
            textColor = textColor,
            labelOf = { it.label },
            onSelect = { style ->
                repository.updateThemeConfig { it.copy(moduleAnimations = it.moduleAnimations.copy(cardReorderStyle = style)) }
            }
        )
    }
}

@Composable
internal fun ContainerPopupAnimationSyncRow(
    theme: ThemeConfig,
    overrideMs: Int,
    globalMs: Int,
    palette: PanelPalette,
    onChange: (Int) -> Unit
) {
    val followGlobal = overrideMs < 0
    val effectiveMs = if (followGlobal) globalMs.coerceIn(0, 800) else overrideMs.coerceIn(0, 800)
    var draftMs by remember { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("文件夹/浮窗动画（同步设置）", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                Text(formatModuleAnimationSummary(draftMs ?: overrideMs, globalMs), fontSize = 10.sp, color = palette.accent)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("跟随全局", fontSize = 10.sp, color = palette.textSecondary)
                Switch(
                    checked = followGlobal,
                    onCheckedChange = { follow -> onChange(if (follow) FOLLOW_GLOBAL_ANIMATION_MS else globalMs.coerceIn(0, 800)) }
                )
            }
        }
        if (!followGlobal) {
            BufferedDurationSlider(
                valueMs = effectiveMs,
                accentColor = palette.accent,
                onDraftChange = { draftMs = it },
                onCommit = onChange
            )
        }
    }
}

@Composable
private fun PanelModuleDurationCard(
    title: String,
    overrideMs: Int,
    globalMs: Int,
    palette: PanelPalette,
    trackColor: Color,
    textColor: Color,
    onChange: (Int) -> Unit,
    extraContent: (@Composable () -> Unit)? = null
) {
    val followGlobal = overrideMs < 0
    val effectiveMs = if (followGlobal) globalMs.coerceIn(0, 800) else overrideMs.coerceIn(0, 800)
    var draftMs by remember { mutableStateOf<Int?>(null) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = trackColor.copy(alpha = 0.55f),
        border = BorderStroke(0.6.dp, palette.cardBorder.copy(alpha = 0.55f))
    ) {
        Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                    Text(formatModuleAnimationSummary(draftMs ?: overrideMs, globalMs), fontSize = 10.sp, color = palette.accent)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("跟随全局", fontSize = 10.sp, color = palette.textSecondary)
                    Switch(
                        checked = followGlobal,
                        onCheckedChange = { follow -> onChange(if (follow) FOLLOW_GLOBAL_ANIMATION_MS else globalMs.coerceIn(0, 800)) }
                    )
                }
            }
            extraContent?.invoke()
            if (!followGlobal) {
                BufferedDurationSlider(
                    valueMs = effectiveMs,
                    accentColor = palette.accent,
                    onDraftChange = { draftMs = it },
                    onCommit = onChange
                )
            }
        }
    }
}

@Composable
private fun BufferedDurationSlider(
    valueMs: Int,
    accentColor: Color,
    onDraftChange: (Int?) -> Unit,
    onCommit: (Int) -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    var draftMs by remember { mutableStateOf<Int?>(null) }
    val currentOnDraft by rememberUpdatedState(onDraftChange)
    LaunchedEffect(valueMs) { if (draftMs == null) currentOnDraft(null) }
    LaunchedEffect(interaction) {
        interaction.interactions.collect {
            if (it is DragInteraction.Cancel) { draftMs = null; currentOnDraft(null) }
        }
    }
    Slider(
        value = (draftMs ?: valueMs).coerceIn(0, 800).toFloat(),
        onValueChange = { raw -> raw.roundToInt().coerceIn(0, 800).let { draftMs = it; currentOnDraft(it) } },
        onValueChangeFinished = { draftMs?.let { draftMs = null; currentOnDraft(null); onCommit(it) } },
        interactionSource = interaction,
        valueRange = 0f..800f,
        colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
    )
}

@Composable
private fun <T> PanelChipGrid(
    options: List<T>,
    selected: T,
    columns: Int,
    palette: PanelPalette,
    trackColor: Color,
    textColor: Color,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        options.chunked(columns).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                rowItems.forEach { item ->
                    val isSelected = item == selected
                    Surface(
                        onClick = { onSelect(item) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) palette.accent else trackColor
                    ) {
                        Box(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = labelOf(item),
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
