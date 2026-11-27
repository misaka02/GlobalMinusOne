package com.example.globalminusone.ui.panel.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.PageLayoutType
import com.example.globalminusone.ui.AppIcons as Icons
import com.example.globalminusone.ui.panel.PanelOverlayRadii
import com.example.globalminusone.ui.panel.PanelOverlayTokens

@Composable
internal fun EditWorkbenchPrimaryRow(
    currentPage: Int,
    totalPages: Int,
    pageName: String,
    subPanelTitle: String?,
    toolsExpanded: Boolean,
    panelGeometryActive: Boolean,
    triggerZoneActive: Boolean,
    accent: Color,
    tokens: PanelOverlayTokens,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onAdd: () -> Unit,
    onToggleTools: () -> Unit,
    onFinishActiveTool: () -> Unit,
    onExitSubPanel: (() -> Unit)?,
    onDone: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            WorkbenchPageNavGroup(
                currentPage, totalPages, pageName, subPanelTitle,
                accent, tokens, onPrevPage, onNextPage, onExitSubPanel
            )
            WorkbenchActionChip("＋卡片", active = false, accent = accent, tokens = tokens, onClick = onAdd)
            WorkbenchActionChip(
                label = if (toolsExpanded) "工具 ▴" else "工具 ▾",
                active = toolsExpanded || panelGeometryActive || triggerZoneActive,
                accent = accent,
                tokens = tokens,
                onClick = onToggleTools
            )
            if (triggerZoneActive || panelGeometryActive) {
                WorkbenchActionChip(
                    label = if (triggerZoneActive) "完成触发" else "完成边框",
                    active = true,
                    accent = accent,
                    tokens = tokens,
                    onClick = onFinishActiveTool
                )
            }
        }
        Surface(
            onClick = onDone,
            shape = RoundedCornerShape(PanelOverlayRadii.Chip),
            color = accent,
            modifier = Modifier.heightIn(min = 36.dp)
        ) {
            Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                Text("完成", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun WorkbenchPageNavGroup(
    currentPage: Int,
    totalPages: Int,
    pageName: String,
    subPanelTitle: String?,
    accent: Color,
    tokens: PanelOverlayTokens,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onExitSubPanel: (() -> Unit)?
) {
    val titleHint = pageName.takeIf { it.isNotBlank() }?.let { "·$it" }.orEmpty()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (subPanelTitle != null && onExitSubPanel != null) {
            BannerIconChip(
                Icons.Rounded.ArrowBack, "返回上一级", accent, accent.copy(alpha = 0.14f),
                onExitSubPanel, Modifier.size(32.dp, 36.dp)
            )
        }
        if (totalPages > 1) {
            BannerIconChip(
                Icons.Rounded.KeyboardArrowLeft, "上一页", tokens.textPrimary, tokens.chipBg,
                onPrevPage, Modifier.size(28.dp, 36.dp)
            )
        }
        Surface(
            shape = RoundedCornerShape(PanelOverlayRadii.Chip),
            color = tokens.chipBg,
            modifier = Modifier.heightIn(min = 36.dp)
        ) {
            Box(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                Text(
                    "${currentPage + 1}/$totalPages$titleHint",
                    color = tokens.textPrimary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (totalPages > 1) {
            BannerIconChip(
                Icons.Rounded.KeyboardArrowRight, "下一页", tokens.textPrimary, tokens.chipBg,
                onNextPage, Modifier.size(28.dp, 36.dp)
            )
        }
    }
}

@Composable
internal fun EditWorkbenchToolsStrip(
    layoutType: PageLayoutType,
    dockTop: Boolean,
    panelGeometryActive: Boolean,
    triggerZoneActive: Boolean,
    accent: Color,
    tokens: PanelOverlayTokens,
    onSelectLayoutType: ((PageLayoutType) -> Unit)?,
    onToggleLayoutType: () -> Unit,
    onPanelGeometry: () -> Unit,
    onPanelNumbers: () -> Unit,
    onGlobalProperties: () -> Unit,
    onTriggerZone: () -> Unit,
    onCompactGrid: (() -> Unit)?,
    onNewPage: (() -> Unit)?,
    onSubPanelSettings: (() -> Unit)?,
    onToggleDock: () -> Unit,
    onCollapse: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        WorkbenchLayoutSegment(layoutType, accent, tokens, onSelectLayoutType, onToggleLayoutType)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WorkbenchActionChip("拖动边框", panelGeometryActive, accent, tokens, onPanelGeometry)
            WorkbenchActionChip("精确尺寸", false, accent, tokens, onPanelNumbers)
            WorkbenchActionChip("面板外观", false, accent, tokens, onGlobalProperties)
            WorkbenchActionChip("边缘触发", triggerZoneActive, accent, tokens, onTriggerZone)
            if (onCompactGrid != null) WorkbenchActionChip("紧凑对齐", false, accent, tokens, onCompactGrid)
            if (onNewPage != null) WorkbenchActionChip("新建页", false, accent, tokens, onNewPage)
            if (onSubPanelSettings != null) WorkbenchActionChip("子面板", false, accent, tokens, onSubPanelSettings)
            WorkbenchActionChip(if (dockTop) "置底" else "置顶", false, accent, tokens, onToggleDock)
            WorkbenchActionChip("极简", false, accent, tokens, onCollapse)
        }
    }
}

@Composable
private fun WorkbenchLayoutSegment(
    layoutType: PageLayoutType,
    accent: Color,
    tokens: PanelOverlayTokens,
    onSelectLayoutType: ((PageLayoutType) -> Unit)?,
    onToggleLayoutType: () -> Unit
) {
    if (onSelectLayoutType == null) {
        WorkbenchActionChip("切换排版（当前：${layoutType.label}）", false, accent, tokens, onToggleLayoutType)
        return
    }
    Surface(shape = RoundedCornerShape(PanelOverlayRadii.Chip), color = tokens.chipBg, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val modes = listOf(PageLayoutType.LINEAR to "线性", PageLayoutType.GRID to "网格", PageLayoutType.FREE to "自由")
            modes.forEach { (mode, label) ->
                WorkbenchLayoutModePill(
                    label = label,
                    selected = layoutType == mode,
                    accent = accent,
                    unselectedText = tokens.textSecondary,
                    onClick = { onSelectLayoutType(mode) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun WorkbenchLayoutModePill(
    label: String,
    selected: Boolean,
    accent: Color,
    unselectedText: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(28.dp),
        shape = RoundedCornerShape(7.dp),
        color = if (selected) accent else Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (selected) Color.White else unselectedText,
                fontSize = 11.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun WorkbenchActionChip(
    label: String,
    active: Boolean,
    accent: Color,
    tokens: PanelOverlayTokens,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(PanelOverlayRadii.Chip),
        color = if (active) accent.copy(alpha = 0.16f) else tokens.chipBg,
        border = if (active) BorderStroke(0.6.dp, accent.copy(alpha = 0.45f)) else null,
        modifier = modifier.heightIn(min = 34.dp)
    ) {
        Box(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (active) accent else tokens.textPrimary,
                fontSize = 11.5.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun EditBannerSelectedCardBar(
    selectedCardTitle: String?,
    accent: Color,
    surfaceColor: Color,
    groupChipColor: Color,
    tokens: PanelOverlayTokens,
    onCardProperties: () -> Unit,
    onClearSelection: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(PanelOverlayRadii.Control),
        color = surfaceColor,
        border = BorderStroke(0.8.dp, accent.copy(alpha = 0.45f)),
        shadowElevation = 3.dp,
        modifier = modifier
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val cardLabel = selectedCardTitle?.ifBlank { "未命名卡片" } ?: "当前卡片"
            Text(
                "已选：$cardLabel",
                Modifier.weight(1f),
                color = tokens.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Surface(onClick = onCardProperties, shape = RoundedCornerShape(8.dp), color = accent.copy(alpha = 0.14f)) {
                Text(
                    "卡片属性",
                    Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    color = accent,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            if (onClearSelection != null) {
                Surface(onClick = onClearSelection, shape = RoundedCornerShape(8.dp), color = groupChipColor) {
                    Text(
                        "取消",
                        Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        color = tokens.textSecondary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
internal fun BannerIconChip(
    icon: ImageVector,
    description: String,
    tint: Color,
    bgColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(onClick = onClick, shape = RoundedCornerShape(PanelOverlayRadii.Chip), color = bgColor, modifier = modifier) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(16.dp))
        }
    }
}
