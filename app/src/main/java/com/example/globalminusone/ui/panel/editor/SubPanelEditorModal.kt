package com.example.globalminusone.ui.panel.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.PageConfig
import com.example.globalminusone.data.PageLayoutType
import com.example.globalminusone.data.operations.SubPanelConfig
import kotlin.math.roundToInt
import com.example.globalminusone.ui.panel.LocalPanelPalette
import com.example.globalminusone.ui.panel.PanelAlertDialog

/** Configures an isolated sub-panel: geometry/appearance inheritance or overrides, plus its pages. */
@Composable
internal fun SubPanelEditorModal(
    subPanel: SubPanelConfig,
    ownedPages: List<Pair<Int, PageConfig>>,
    currentTruePageIndex: Int,
    repository: MinusOneRepository,
    onSelectPage: (Int) -> Unit,
    onDeleteSubPanel: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalPanelPalette.current
    val surface = if (palette.isDark) Color(0xFF1E222B) else Color(0xFFFFFFFF)
    var name by remember(subPanel.id) { mutableStateOf(subPanel.name) }
    var confirmDelete by remember(subPanel.id) { mutableStateOf(false) }
    var showSaveTemplateDialog by remember(subPanel.id) { mutableStateOf(false) }
    var templateNameDraft by remember(subPanel.id) { mutableStateOf(subPanel.name) }
    var showLibraryManager by remember(subPanel.id) { mutableStateOf(false) }
    val state by repository.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val handleDismiss: () -> Unit = {
        val cleaned = name.trim()
        if (cleaned.isEmpty()) {
            repository.updateSubPanel(subPanel.id) { cur -> cur.copy(name = "子面板") }
        }
        onDismiss()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.36f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = handleDismiss
            )
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .heightIn(max = 520.dp)
                // Non-consuming hit-test barrier so inner controls work without falling through to scrim.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Final)
                        }
                    }
                },
            shape = RoundedCornerShape(18.dp),
            color = surface,
            border = BorderStroke(1.dp, palette.cardBorder),
            shadowElevation = 10.dp
        ) {
            Column(
                Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("子面板配置", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                    TextButton(onClick = handleDismiss) { Text("关闭") }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { nextName ->
                        name = nextName
                        val trimmed = nextName.trim()
                        if (trimmed.isNotEmpty()) {
                            repository.updateSubPanel(subPanel.id) { cur -> cur.copy(name = trimmed) }
                        }
                    },
                    label = { Text("子面板名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("继承主面板尺寸与位置", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                        Text("关闭后可独立调节本子面板宽高和对齐位置", fontSize = 11.sp, color = palette.textSecondary)
                    }
                    Switch(
                        checked = subPanel.inheritGeometry,
                        onCheckedChange = { inherit ->
                            repository.updateSubPanel(subPanel.id) { it.copy(inheritGeometry = inherit) }
                        }
                    )
                }

                if (!subPanel.inheritGeometry) {
                    SubPanelPercentSlider("面板宽度 (%)", subPanel.panelWidthPercent, 10..100, palette.accent, palette.textPrimary) { v ->
                        repository.updateSubPanel(subPanel.id) { it.copy(panelWidthPercent = v) }
                    }
                    SubPanelPercentSlider("面板高度 (%)", subPanel.panelHeightPercent, 15..100, palette.accent, palette.textPrimary) { v ->
                        repository.updateSubPanel(subPanel.id) { it.copy(panelHeightPercent = v) }
                    }
                    SubPanelPercentSlider("垂直位置 (%)", subPanel.panelVerticalPositionPercent, 0..100, palette.accent, palette.textPrimary) { v ->
                        repository.updateSubPanel(subPanel.id) { it.copy(panelVerticalPositionPercent = v) }
                    }
                    SubPanelPercentSlider("侧边偏移 (%)", subPanel.panelHorizontalOffsetPercent, 0..80, palette.accent, palette.textPrimary) { v ->
                        repository.updateSubPanel(subPanel.id) { it.copy(panelHorizontalOffsetPercent = v) }
                    }
                }

                HorizontalDivider()

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("继承主面板外观主题", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                        Text("关闭后可单独设定明暗模式、背景透明度与卡片圆角", fontSize = 11.sp, color = palette.textSecondary)
                    }
                    Switch(
                        checked = subPanel.inheritAppearance,
                        onCheckedChange = { inherit ->
                            repository.updateSubPanel(subPanel.id) { it.copy(inheritAppearance = inherit) }
                        }
                    )
                }

                if (!subPanel.inheritAppearance) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("深色模式", fontSize = 12.sp, color = palette.textPrimary)
                        Switch(
                            checked = subPanel.isDarkMode,
                            onCheckedChange = { dark ->
                                repository.updateSubPanel(subPanel.id) { it.copy(isDarkMode = dark) }
                            }
                        )
                    }
                    SubPanelPercentSlider("背景透明度 (%)", subPanel.panelBgAlphaPercent, 0..100, palette.accent, palette.textPrimary) { v ->
                        repository.updateSubPanel(subPanel.id) { it.copy(panelBgAlphaPercent = v) }
                    }
                    SubPanelPercentSlider("卡片圆角 (dp)", subPanel.cardCornerRadiusDp, 0..32, palette.accent, palette.textPrimary) { v ->
                        repository.updateSubPanel(subPanel.id) { it.copy(cardCornerRadiusDp = v) }
                    }
                }

                SubPanelAnimationSyncBlock(palette.themeConfig, repository, palette)

                HorizontalDivider()

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("子面板分页（${ownedPages.size}）", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                    TextButton(onClick = {
                        val addedIdx = repository.addPage(PageLayoutType.GRID, "${subPanel.name} ${ownedPages.size + 1}", subPanel.id)
                        if (addedIdx >= 0) onSelectPage(addedIdx)
                    }) { Text("＋新增页") }
                }
                ownedPages.forEach { (trueIdx, page) ->
                    val active = trueIdx == currentTruePageIndex
                    Surface(
                        onClick = { onSelectPage(trueIdx) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (active) palette.accent.copy(alpha = 0.14f) else palette.cardBackground.copy(alpha = 0.7f),
                        border = BorderStroke(0.8.dp, if (active) palette.accent else palette.cardBorder)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${page.name} (${page.layoutType.label} · ${page.components.size}项)",
                                fontSize = 12.sp,
                                color = if (active) palette.accent else palette.textPrimary,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                            )
                            if (ownedPages.size > 1) {
                                Text(
                                    "删页",
                                    fontSize = 11.sp,
                                    color = Color(0xFFE53935),
                                    modifier = Modifier.clickable { repository.deletePage(trueIdx) }.padding(horizontal = 6.dp)
                                )
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { templateNameDraft = name.trim().ifBlank { subPanel.name }; showSaveTemplateDialog = true },
                        modifier = Modifier.weight(1f)
                    ) { Text("存入子面板仓库", fontSize = 12.sp) }
                    OutlinedButton(
                        onClick = { showLibraryManager = true },
                        modifier = Modifier.weight(1f)
                    ) { Text("管理仓库 (${state.subPanelLibrary.size})", fontSize = 12.sp) }
                }

                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE53935))
                ) { Text("删除此子面板及其所有分页") }
            }
        }
    }

    if (showLibraryManager) {
        SubPanelLibraryModal(repository = repository, pickerMode = false, onDismiss = { showLibraryManager = false })
    }

    if (showSaveTemplateDialog) {
        PanelAlertDialog(
            onDismissRequest = { showSaveTemplateDialog = false },
            title = { Text("存入子面板仓库") },
            text = {
                OutlinedTextField(
                    value = templateNameDraft,
                    onValueChange = { templateNameDraft = it },
                    label = { Text("模板名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val saved = repository.saveSubPanelToLibrary(subPanel.id, templateNameDraft)
                    showSaveTemplateDialog = false
                    android.widget.Toast.makeText(
                        context,
                        if (saved != null) "已保存模板「${saved.name}」至子面板仓库" else "仓库已满或保存失败",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showSaveTemplateDialog = false }) { Text("取消") } }
        )
    }

    if (confirmDelete) {
        PanelAlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除子面板？") },
            text = { Text("将删除「${subPanel.name}」的全部页面以及指向它的子面板入口卡片，主面板不受影响。") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDeleteSubPanel() }) {
                    Text("删除", color = Color(0xFFE53935))
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun SubPanelPercentSlider(
    label: String,
    value: Int,
    range: IntRange,
    accent: Color,
    textColor: Color,
    onChange: (Int) -> Unit
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 12.sp, color = textColor)
            Text("$value", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)
        }
        Slider(
            value = value.coerceIn(range.first, range.last).toFloat(),
            onValueChange = { onChange(it.roundToInt().coerceIn(range.first, range.last)) },
            valueRange = range.first.toFloat()..range.last.toFloat()
        )
    }
}
