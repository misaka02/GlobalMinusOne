package com.example.globalminusone.ui.panel.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.PageConfig
import com.example.globalminusone.data.PageLayoutType
import com.example.globalminusone.data.policy.PagePresetOption
import com.example.globalminusone.ui.AppIcons as Icons
import com.example.globalminusone.ui.panel.LocalPanelPalette
import com.example.globalminusone.ui.panel.PanelAlertDialog
import com.example.globalminusone.ui.panel.PanelPalette
import com.example.globalminusone.ui.panel.panelOverlayTokens

@Composable
internal fun PanelFooterPagePill(
    currentPageProvider: () -> Int,
    pages: List<PageConfig>,
    layoutLocked: Boolean,
    palette: PanelPalette,
    chromeAlpha: Float,
    onOpenMenu: () -> Unit
) {
    val realPage by remember(currentPageProvider, pages.size) {
        derivedStateOf { currentPageProvider().coerceIn(0, (pages.size - 1).coerceAtLeast(0)) }
    }
    val activeLayoutShort = when (pages.getOrNull(realPage)?.layoutType) {
        PageLayoutType.FREE -> "自由"
        PageLayoutType.GRID -> "网格"
        else -> "线性"
    }
    val tokens = panelOverlayTokens(palette)
    val latestMenu by rememberUpdatedState(onOpenMenu)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .padding(bottom = 2.dp)
            .graphicsLayer { alpha = chromeAlpha }
            .clickable(onClickLabel = "打开页面与菜单") { latestMenu() }
            .pointerInput(Unit) {
                var distance = 0f
                detectVerticalDragGestures(
                    onDragStart = { distance = 0f },
                    onDragCancel = { distance = 0f },
                    onDragEnd = { if (distance <= -22.dp.toPx()) latestMenu(); distance = 0f }
                ) { change, dragAmount ->
                    change.consume()
                    distance += dragAmount
                }
            }
            .semantics { contentDescription = "页面与菜单，当前第${realPage + 1}页，共${pages.size}页" },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = tokens.elevatedSurface.copy(alpha = 0.94f),
            shape = RoundedCornerShape(999.dp),
            border = BorderStroke(0.75.dp, tokens.border)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pages.size in 2..6) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                        pages.indices.forEach { dotIndex ->
                            val activeDot = dotIndex == realPage
                            Box(
                                modifier = Modifier
                                    .size(width = if (activeDot) 10.dp else 4.dp, height = 4.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (activeDot) palette.accent
                                        else tokens.textSecondary.copy(alpha = 0.42f)
                                    )
                            )
                        }
                    }
                }
                Text(
                    text = (if (layoutLocked) "已锁定 · " else "") + "${realPage + 1}/${pages.size} · $activeLayoutShort",
                    color = tokens.textPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = null,
                    tint = tokens.textSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// 32 等分点阵「自由布局」引擎 (FreeLayoutPage)
// -----------------------------------------------------------------------------------------

// -----------------------------------------------------------------------------------------
// 底部 9 键操作栏浮层 (BottomNineKeyModal)
// -----------------------------------------------------------------------------------------

@Composable
fun NumberStepper(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minimum: Int = 0, maximum: Int = Int.MAX_VALUE
) {
    val palette = LocalPanelPalette.current
    var inputOpen by remember { mutableStateOf(false) }
    if (inputOpen) ExactNumberInput(label, value, minimum, maximum, onValueChange) { inputOpen = false }
    val slotBg = if (palette.isDark) Color(0xFF282E38) else Color(0xFFF3F5F8)
    val slotBorder = BorderStroke(0.8.dp, palette.cardBorder.copy(alpha = 0.55f))
    val valuePillBg = palette.accent.copy(alpha = 0.12f)

    Surface(
        modifier = modifier.clip(RoundedCornerShape(10.dp)),
        color = slotBg,
        shape = RoundedCornerShape(10.dp),
        border = slotBorder
    ) {
        BoxWithConstraints(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            val inlineRow = maxWidth >= 210.dp
            val stackNumber = maxWidth < 144.dp
            val number: @Composable (Modifier) -> Unit = { numberModifier ->
                Box(
                    numberModifier
                        .height(48.dp)
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(valuePillBg)
                        .clickable { inputOpen = true }
                        .semantics { contentDescription = "输入$label" },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$value",
                        modifier = Modifier.padding(horizontal = 10.dp),
                        color = palette.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            val step: @Composable (Boolean, Modifier) -> Unit = { increase, stepModifier ->
                val canStep = if (increase) value < maximum else value > minimum
                IconButton(
                    enabled = canStep,
                    onClick = {
                        val next = value.toLong() + if (increase) 1L else -1L
                        onValueChange(next.coerceIn(minimum.toLong(), maximum.toLong()).toInt())
                    },
                    modifier = stepModifier.height(48.dp)
                ) {
                    Icon(
                        if (increase) Icons.Rounded.Add else Icons.Rounded.Remove,
                        "$label ${if (increase) "增加" else "减少"}",
                        tint = palette.textPrimary.copy(alpha = if (canStep) 1f else .35f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (inlineRow) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label,
                        color = palette.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f).padding(start = 2.dp, end = 6.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    step(false, Modifier.width(42.dp))
                    number(Modifier.widthIn(min = 52.dp))
                    step(true, Modifier.width(42.dp))
                }
            } else {
                Column {
                    Text(
                        text = label,
                        color = palette.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                    // Every hit target keeps its own 48dp height region; narrow paired fields stack cleanly.
                    if (stackNumber) {
                        number(Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth()) {
                            step(false, Modifier.weight(1f)); step(true, Modifier.weight(1f))
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            step(false, Modifier.width(48.dp))
                            number(Modifier.weight(1f))
                            step(true, Modifier.width(48.dp))
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// 主题外观微调弹窗 (ThemeAppearanceModal)
// -----------------------------------------------------------------------------------------

// -----------------------------------------------------------------------------------------
// 删除该页确认弹窗 (DeletePageConfirmModal)
// -----------------------------------------------------------------------------------------

@Composable
fun DeletePageConfirmModal(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    PanelAlertDialog(onDismissRequest = onDismiss,
        title = { Text("删除页面？") },
        text = { Text("确定删除吗？这将无法恢复！") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("确定删除", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
fun NewPageTypeModal(
    onSelectType: (PageLayoutType) -> Unit = {},
    onSelectPreset: ((PagePresetOption, String) -> Unit)? = null,
    onImportFromBackup: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var pageNameInput by remember { mutableStateOf("") }
    val palette = LocalPanelPalette.current
    val presets = remember { PagePresetOption.entries.filter { it.isPreset } }
    val blanks = remember { PagePresetOption.entries.filter { !it.isPreset } }
    PanelAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建一页") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = palette.cardBackground.copy(alpha = 0.72f),
                    border = BorderStroke(0.8.dp, palette.cardBorder.copy(alpha = 0.7f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = pageNameInput,
                        onValueChange = { pageNameInput = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = palette.textPrimary, fontSize = 12.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(palette.accent),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
                        decorationBox = { inner ->
                            Box {
                                if (pageNameInput.isEmpty()) {
                                    Text(
                                        "可选：输入新页面名称（留空自动命名）",
                                        color = palette.textSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                                inner()
                            }
                        }
                    )
                }

                Text(
                    "实用布局预设",
                    color = palette.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                presets.forEach { preset ->
                    Surface(
                        onClick = {
                            if (onSelectPreset != null) onSelectPreset(preset, pageNameInput)
                            else onSelectType(preset.layoutType)
                        },
                        shape = RoundedCornerShape(11.dp),
                        color = palette.cardBackground,
                        border = BorderStroke(0.8.dp, palette.cardBorder.copy(alpha = 0.75f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    preset.title,
                                    color = palette.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    preset.subtitle,
                                    color = palette.textSecondary,
                                    fontSize = 10.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = palette.accent.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    preset.badge,
                                    color = palette.accent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                Text(
                    "空白页面",
                    color = palette.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                blanks.forEach { blank ->
                    OutlinedButton(
                        onClick = {
                            if (onSelectPreset != null) onSelectPreset(blank, pageNameInput)
                            else onSelectType(blank.layoutType)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("${blank.title}（${blank.subtitle.substringBefore('·').trim()}）", fontSize = 12.sp)
                    }
                }
                if (onImportFromBackup != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "从备份导入",
                        color = palette.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        onClick = onImportFromBackup,
                        shape = RoundedCornerShape(11.dp),
                        color = palette.cardBackground,
                        border = BorderStroke(0.8.dp, palette.accent.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                "导入页面配置（.zip / .json / .bkp）",
                                color = palette.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "跳转备份选择器并默认按“追加单页”导入，若面板尺寸不适配会自动提示",
                                color = palette.textSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
