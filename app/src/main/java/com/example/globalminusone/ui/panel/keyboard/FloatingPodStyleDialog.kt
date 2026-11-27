package com.example.globalminusone.ui.panel.keyboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.policy.FloatingPodStyleSpec
import com.example.globalminusone.data.policy.floatingPodSizeBoundsFor
import com.example.globalminusone.data.policy.formatRgbHexColor
import com.example.globalminusone.data.policy.parseFloatingPodStyle
import com.example.globalminusone.data.policy.parseRgbHexColor
import kotlin.math.roundToInt
import com.example.globalminusone.ui.panel.LocalPanelPalette
import com.example.globalminusone.ui.panel.editor.NumberStepper
import com.example.globalminusone.ui.panel.PanelAlertDialog
import com.example.globalminusone.ui.panel.cards.resolveCardPalette

private val POD_BG_PRESETS = listOf<Pair<String, Long?>>(
    "跟随主题" to null,
    "纯白亮色" to 0xFFFFFFFFL,
    "柔和暖白" to 0xFFF7F8FAL,
    "暗黑深灰" to 0xFF1E222BL,
    "纯黑OLED" to 0xFF000000L,
    "雾蓝深灰" to 0xFF1E3A5FL,
    "墨绿深灰" to 0xFF1B3B36L,
    "淡紫深灰" to 0xFF2D253AL
)

private val POD_TEXT_PRESETS = listOf<Pair<String, Long?>>(
    "自动适配" to null,
    "深炭黑字" to 0xFF1F2329L,
    "纯白亮字" to 0xFFFFFFFFL,
    "柔和灰白" to 0xFFD0D5DDL,
    "科技蓝字" to 0xFF2196F3L,
    "琥珀金字" to 0xFFF59E0BL
)

@Composable
internal fun FloatingPodStyleDialog(
    item: ComponentItem,
    onDismiss: () -> Unit,
    onSave: (FloatingPodStyleSpec) -> Boolean
) {
    val palette = LocalPanelPalette.current
    val baseCardColors = remember(item, palette.themeConfig) { resolveCardPalette(item, palette.themeConfig) }
    val isSlider = item.type == ComponentType.BRIGHTNESS_BAR || item.type == ComponentType.VOLUME_BAR
    val defaultCornerDp = if (isSlider) 20 else 16
    val bounds = remember(item.type) { floatingPodSizeBoundsFor(item.type) }
    val initial = remember(item.id, item.intentUri) { parseFloatingPodStyle(item.intentUri) }

    var draft by remember(item.id, item.intentUri) { mutableStateOf(initial) }
    var bgHexInput by remember(item.id, item.intentUri) {
        mutableStateOf(initial.bgColorHex?.let(::formatRgbHexColor).orEmpty())
    }
    var textHexInput by remember(item.id, item.intentUri) {
        mutableStateOf(initial.textColorHex?.let(::formatRgbHexColor).orEmpty())
    }

    val modalTrack = if (palette.isDark) Color(0xFF2A313D) else Color(0xFFF1F3F6)
    val modalTextPrimary = if (palette.isDark) Color(0xFFF3F6FA) else Color(0xFF181D26)
    val modalTextSecondary = if (palette.isDark) Color(0xFFA7B1C2) else Color(0xFF5A6577)

    val previewVisuals = remember(draft, palette, baseCardColors, defaultCornerDp) {
        resolvePodVisuals(draft, palette, baseCardColors, defaultCornerDp)
    }
    val effectiveWidth = if (draft.widthDp > 0) draft.widthDp else bounds.defaultWidthDp
    val effectiveHeight = if (draft.heightDp > 0) draft.heightDp else bounds.defaultHeightDp
    val effectiveAlpha = if (draft.bgAlphaPercent in 10..100) draft.bgAlphaPercent else 96
    val effectiveCorner = previewVisuals.cornerRadiusDp

    PanelAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "弹窗尺寸与样式 · ${item.title.ifBlank { item.type.label }}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(effectiveCorner.dp),
                    color = previewVisuals.surfaceColor,
                    border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "${item.title.ifBlank { item.type.label }} · 弹窗实时预览",
                            color = previewVisuals.cardColors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${effectiveWidth}×${effectiveHeight}dp · 不透明 $effectiveAlpha% · 圆角 ${effectiveCorner}dp",
                            color = previewVisuals.cardColors.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                PodSizeEditorBlock(
                    title = "弹窗宽度 (dp)",
                    currentCustom = draft.widthDp,
                    effectiveValue = effectiveWidth,
                    minVal = bounds.minWidthDp,
                    maxVal = bounds.maxWidthDp,
                    defaultVal = bounds.defaultWidthDp,
                    presets = bounds.widthPresets,
                    accent = palette.accent,
                    modalTrack = modalTrack,
                    modalTextPrimary = modalTextPrimary,
                    onChange = { draft = draft.copy(widthDp = it) }
                )

                PodSizeEditorBlock(
                    title = "弹窗高度 (dp)",
                    currentCustom = draft.heightDp,
                    effectiveValue = effectiveHeight,
                    minVal = bounds.minHeightDp,
                    maxVal = bounds.maxHeightDp,
                    defaultVal = bounds.defaultHeightDp,
                    presets = bounds.heightPresets,
                    accent = palette.accent,
                    modalTrack = modalTrack,
                    modalTextPrimary = modalTextPrimary,
                    onChange = { draft = draft.copy(heightDp = it) }
                )

                PodColorPresetAndHexBlock(
                    title = "弹窗背景颜色",
                    presets = POD_BG_PRESETS,
                    selectedColor = draft.bgColorHex,
                    hexInput = bgHexInput,
                    placeholder = "#1E222B",
                    accent = palette.accent,
                    modalTrack = modalTrack,
                    modalTextPrimary = modalTextPrimary,
                    modalTextSecondary = modalTextSecondary,
                    onSelectPreset = { hex ->
                        draft = draft.copy(bgColorHex = hex)
                        bgHexInput = hex?.let(::formatRgbHexColor).orEmpty()
                    },
                    onHexTextChange = { text ->
                        bgHexInput = text.take(7)
                        if (text.isBlank()) {
                            draft = draft.copy(bgColorHex = null)
                        } else {
                            parseRgbHexColor(text)?.let { draft = draft.copy(bgColorHex = it) }
                        }
                    }
                )

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val alphaLabel = if (draft.bgAlphaPercent in 10..100) "${draft.bgAlphaPercent}%" else "默认 (96%)"
                        Text("弹窗不透明度: $alphaLabel", color = modalTextPrimary, fontSize = 12.sp)
                        if (draft.bgAlphaPercent in 10..100) {
                            Text(
                                text = "恢复默认",
                                color = palette.accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable { draft = draft.copy(bgAlphaPercent = -1) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("默认" to -1, "75%" to 75, "88%" to 88, "100%" to 100).forEach { (label, valPct) ->
                            val selected = draft.bgAlphaPercent == valPct
                            PodQuickChip(
                                label = label,
                                selected = selected,
                                accent = palette.accent,
                                track = modalTrack,
                                textPrimary = modalTextPrimary,
                                modifier = Modifier.weight(1f),
                                onClick = { draft = draft.copy(bgAlphaPercent = valPct) }
                            )
                        }
                    }
                    Slider(
                        value = effectiveAlpha.toFloat(),
                        onValueChange = { draft = draft.copy(bgAlphaPercent = it.roundToInt().coerceIn(10, 100)) },
                        valueRange = 10f..100f,
                        colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent)
                    )
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val cornerLabel = if (draft.cornerRadiusDp >= 0) "${draft.cornerRadiusDp}dp" else "默认 (${defaultCornerDp}dp)"
                        Text("弹窗圆角: $cornerLabel", color = modalTextPrimary, fontSize = 12.sp)
                        if (draft.cornerRadiusDp >= 0) {
                            Text(
                                text = "恢复默认",
                                color = palette.accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable { draft = draft.copy(cornerRadiusDp = -1) }
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    NumberStepper(
                        label = "圆角半径 (dp)",
                        value = effectiveCorner,
                        onValueChange = { draft = draft.copy(cornerRadiusDp = it.coerceIn(0, FloatingPodStyleSpec.MAX_POD_CORNER_DP)) },
                        minimum = 0,
                        maximum = FloatingPodStyleSpec.MAX_POD_CORNER_DP
                    )
                    Slider(
                        value = effectiveCorner.toFloat(),
                        onValueChange = {
                            draft = draft.copy(cornerRadiusDp = it.roundToInt().coerceIn(0, FloatingPodStyleSpec.MAX_POD_CORNER_DP))
                        },
                        valueRange = 0f..FloatingPodStyleSpec.MAX_POD_CORNER_DP.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent)
                    )
                }

                PodColorPresetAndHexBlock(
                    title = "弹窗文字与图标色",
                    presets = POD_TEXT_PRESETS,
                    columns = 3,
                    selectedColor = draft.textColorHex,
                    hexInput = textHexInput,
                    placeholder = "#FFFFFF",
                    accent = palette.accent,
                    modalTrack = modalTrack,
                    modalTextPrimary = modalTextPrimary,
                    modalTextSecondary = modalTextSecondary,
                    onSelectPreset = { hex ->
                        draft = draft.copy(textColorHex = hex)
                        textHexInput = hex?.let(::formatRgbHexColor).orEmpty()
                    },
                    onHexTextChange = { text ->
                        textHexInput = text.take(7)
                        if (text.isBlank()) {
                            draft = draft.copy(textColorHex = null)
                        } else {
                            parseRgbHexColor(text)?.let { draft = draft.copy(textColorHex = it) }
                        }
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }) {
                Text("保存", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = {
                        draft = FloatingPodStyleSpec()
                        bgHexInput = ""
                        textHexInput = ""
                    }
                ) { Text("恢复默认") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

@Composable
private fun PodSizeEditorBlock(
    title: String,
    currentCustom: Int,
    effectiveValue: Int,
    minVal: Int,
    maxVal: Int,
    defaultVal: Int,
    presets: List<Int>,
    accent: Color,
    modalTrack: Color,
    modalTextPrimary: Color,
    onChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val labelText = if (currentCustom > 0) "${currentCustom}dp" else "默认 (${defaultVal}dp)"
            Text("$title: $labelText", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            if (currentCustom > 0) {
                Text(
                    text = "恢复默认",
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { onChange(0) }
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            PodQuickChip(
                label = "默认",
                selected = currentCustom <= 0,
                accent = accent,
                track = modalTrack,
                textPrimary = modalTextPrimary,
                modifier = Modifier.weight(1f),
                onClick = { onChange(0) }
            )
            presets.forEach { preset ->
                PodQuickChip(
                    label = "${preset}",
                    selected = currentCustom == preset,
                    accent = accent,
                    track = modalTrack,
                    textPrimary = modalTextPrimary,
                    modifier = Modifier.weight(1f),
                    onClick = { onChange(preset) }
                )
            }
        }
        NumberStepper(
            label = title,
            value = effectiveValue,
            onValueChange = { onChange(it.coerceIn(minVal, maxVal)) },
            minimum = minVal,
            maximum = maxVal
        )
        Slider(
            value = effectiveValue.toFloat(),
            onValueChange = { onChange(it.roundToInt().coerceIn(minVal, maxVal)) },
            valueRange = minVal.toFloat()..maxVal.toFloat(),
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
        )
    }
}

@Composable
private fun PodColorPresetAndHexBlock(
    title: String,
    presets: List<Pair<String, Long?>>,
    selectedColor: Long?,
    hexInput: String,
    placeholder: String,
    accent: Color,
    modalTrack: Color,
    modalTextPrimary: Color,
    modalTextSecondary: Color,
    onSelectPreset: (Long?) -> Unit,
    onHexTextChange: (String) -> Unit,
    columns: Int = 4
) {
    val hexInvalid = hexInput.isNotBlank() && parseRgbHexColor(hexInput) == null
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        presets.chunked(columns.coerceAtLeast(1)).forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                rowItems.forEach { (label, hex) ->
                    val selected = selectedColor == hex
                    FilledTonalButton(
                        onClick = { onSelectPreset(hex) },
                        modifier = Modifier.weight(1f).height(34.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (selected) accent else modalTrack,
                            contentColor = if (selected) Color.White else modalTextPrimary
                        )
                    ) {
                        if (hex != null) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(Color(hex))
                                    .border(0.6.dp, Color.Gray.copy(alpha = 0.45f), CircleShape)
                            )
                            Spacer(Modifier.width(3.dp))
                        }
                        Text(label, fontSize = 10.sp, maxLines = 1)
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(selectedColor?.let { Color(it) } ?: modalTrack)
                    .border(1.dp, modalTextSecondary.copy(alpha = 0.4f), CircleShape)
            )
            OutlinedTextField(
                value = hexInput,
                onValueChange = onHexTextChange,
                singleLine = true,
                isError = hexInvalid,
                label = { Text("自定义十六进制色值", fontSize = 11.sp) },
                placeholder = { Text(placeholder, fontSize = 12.sp, fontFamily = FontFamily.Monospace) },
                supportingText = if (hexInvalid) {
                    { Text("格式如 #RRGGBB", fontSize = 10.sp) }
                } else null,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PodQuickChip(
    label: String,
    selected: Boolean,
    accent: Color,
    track: Color,
    textPrimary: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(32.dp),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (selected) accent else track,
            contentColor = if (selected) Color.White else textPrimary
        )
    ) {
        Text(label, fontSize = 11.sp, maxLines = 1)
    }
}
