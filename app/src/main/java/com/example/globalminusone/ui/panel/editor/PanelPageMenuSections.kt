package com.example.globalminusone.ui.panel.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.PageLayoutType
import com.example.globalminusone.data.policy.PanelOpenPageMode
import com.example.globalminusone.ui.AppIcons as Icons
import com.example.globalminusone.ui.panel.PanelOverlayRadii
import com.example.globalminusone.ui.panel.PanelOverlayTokens

@Composable
internal fun NineKeySheetHeader(
    currentPageIndex: Int,
    totalPages: Int,
    showPageManage: Boolean,
    accent: Color,
    tokens: PanelOverlayTokens,
    onTogglePageManage: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("页面与控制中心", color = tokens.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("第 ${currentPageIndex + 1} / $totalPages 页", color = tokens.textSecondary, fontSize = 11.5.sp)
        }
        Surface(
            onClick = onTogglePageManage,
            shape = RoundedCornerShape(PanelOverlayRadii.Chip),
            color = if (showPageManage) accent.copy(alpha = 0.14f) else tokens.chipBg,
            border = BorderStroke(0.6.dp, if (showPageManage) accent.copy(alpha = 0.45f) else tokens.border)
        ) {
            Text(
                text = if (showPageManage) "收起管理" else "管理页面",
                color = if (showPageManage) accent else tokens.textSecondary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        Surface(
            onClick = onDismiss,
            shape = CircleShape,
            color = tokens.chipBg,
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, "关闭", tint = tokens.textSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
internal fun NineKeyPageCard(
    state: GlobalMinusOneState,
    currentPageIndex: Int,
    layoutLocked: Boolean,
    showPageManage: Boolean,
    accent: Color,
    tokens: PanelOverlayTokens,
    onNavigatePage: ((Int) -> Unit)?,
    onSelectLayoutType: ((PageLayoutType) -> Unit)?,
    onToggleLayoutType: () -> Unit,
    onSwapPageLeft: () -> Unit,
    onNewPage: () -> Unit,
    onSwapPageRight: () -> Unit,
    onDeleteCurrentPage: () -> Unit,
    onToggleLockOpenPage: (() -> Unit)? = null
) {
    val curLayout = state.pages.getOrNull(currentPageIndex)?.layoutType ?: PageLayoutType.FREE
    val currentPageId = state.pages.getOrNull(currentPageIndex)?.id.orEmpty()
    val lockedHere = state.advancedConfig.panelOpenPageMode == PanelOpenPageMode.LOCKED_PAGE &&
        state.advancedConfig.lockedOpenPageId == currentPageId
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PanelOverlayRadii.Card),
        color = tokens.elevatedSurface,
        border = BorderStroke(0.8.dp, tokens.border)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NineKeyPageSwitchRow(state, currentPageIndex, accent, tokens, onNavigatePage)
            NineKeyLayoutSegmentedBar(curLayout, layoutLocked, accent, tokens, onSelectLayoutType, onToggleLayoutType)
            if (showPageManage) {
                HorizontalDivider(color = tokens.divider, thickness = 0.6.dp)
                NineKeyPageManageRow(
                    currentPageIndex, state.pages.size, layoutLocked, accent, tokens,
                    onSwapPageLeft, onNewPage, onSwapPageRight, onDeleteCurrentPage
                )
                if (onToggleLockOpenPage != null) {
                    LockOpenPageChip(lockedHere = lockedHere, accent = accent, tokens = tokens, onClick = onToggleLockOpenPage)
                }
            }
        }
    }
}

@Composable
private fun NineKeyPageSwitchRow(
    state: GlobalMinusOneState,
    currentPageIndex: Int,
    accent: Color,
    tokens: PanelOverlayTokens,
    onNavigatePage: ((Int) -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PageNavArrowChip(Icons.Rounded.ArrowBack, "上一页", state.pages.size > 1, tokens) {
            onNavigatePage?.invoke(currentPageIndex - 1)
        }
        if (state.pages.size > 1 && onNavigatePage != null) {
            PageNumberChipsStrip(state, currentPageIndex, accent, tokens, onNavigatePage, Modifier.weight(1f))
        } else {
            Text(
                text = "第 ${currentPageIndex + 1} / ${state.pages.size} 页",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = tokens.textPrimary
            )
        }
        PageNavArrowChip(Icons.Rounded.ArrowForward, "下一页", state.pages.size > 1, tokens) {
            onNavigatePage?.invoke(currentPageIndex + 1)
        }
    }
}

@Composable
private fun PageNumberChipsStrip(
    state: GlobalMinusOneState,
    currentPageIndex: Int,
    accent: Color,
    tokens: PanelOverlayTokens,
    onNavigatePage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val adv = state.advancedConfig
        state.pages.forEachIndexed { idx, p ->
            val active = idx == currentPageIndex
            val suffix = p.name.takeIf { it.isNotBlank() }?.let { "·$it" }.orEmpty()
            val lockTag = if (adv.panelOpenPageMode == PanelOpenPageMode.LOCKED_PAGE && adv.lockedOpenPageId == p.id) "·默认" else ""
            PageNumberChip(
                text = "${idx + 1}$suffix$lockTag",
                active = active,
                accent = accent,
                tokens = tokens,
                onClick = { if (!active) onNavigatePage(idx) }
            )
        }
    }
}

@Composable
private fun PageNumberChip(
    text: String,
    active: Boolean,
    accent: Color,
    tokens: PanelOverlayTokens,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (active) accent else tokens.cardBg,
        border = if (active) null else BorderStroke(0.6.dp, tokens.border)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) Color.White else tokens.textSecondary,
            maxLines = 1
        )
    }
}

@Composable
private fun PageNavArrowChip(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    tokens: PanelOverlayTokens,
    onClick: () -> Unit
) {
    val tint = if (enabled) tokens.textSecondary else tokens.textDisabled
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        color = tokens.cardBg,
        border = BorderStroke(0.6.dp, tokens.border),
        modifier = Modifier.size(36.dp)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, description, tint = tint, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun NineKeyLayoutSegmentedBar(
    curLayout: PageLayoutType,
    layoutLocked: Boolean,
    accent: Color,
    tokens: PanelOverlayTokens,
    onSelectLayoutType: ((PageLayoutType) -> Unit)?,
    onToggleLayoutType: () -> Unit
) {
    if (onSelectLayoutType == null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("当前排版：${curLayout.label}", fontSize = 12.sp, color = tokens.textSecondary)
            Surface(
                onClick = onToggleLayoutType,
                enabled = !layoutLocked,
                shape = RoundedCornerShape(8.dp),
                color = accent.copy(alpha = 0.14f)
            ) {
                Text(
                    "切换",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (layoutLocked) tokens.textDisabled else accent
                )
            }
        }
        return
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = tokens.cardBg,
        border = BorderStroke(0.6.dp, tokens.border)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val modes = listOf(PageLayoutType.LINEAR to "线性", PageLayoutType.GRID to "网格", PageLayoutType.FREE to "自由")
            modes.forEach { (mode, name) ->
                LayoutSegmentedPill(name, curLayout == mode, layoutLocked, accent, tokens, { onSelectLayoutType(mode) },
                    Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LayoutSegmentedPill(
    name: String,
    selected: Boolean,
    layoutLocked: Boolean,
    accent: Color,
    tokens: PanelOverlayTokens,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (!layoutLocked && selected) accent else Color.Transparent
    val fg = when {
        layoutLocked -> tokens.textDisabled
        selected -> Color.White
        else -> tokens.textPrimary
    }
    Surface(onClick = onClick, enabled = !layoutLocked, modifier = modifier.height(32.dp),
        shape = RoundedCornerShape(8.dp), color = bg) {
        Box(contentAlignment = Alignment.Center) {
            Text(name, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = fg, maxLines = 1)
        }
    }
}

@Composable
private fun NineKeyPageManageRow(
    currentPageIndex: Int,
    totalPages: Int,
    layoutLocked: Boolean,
    accent: Color,
    tokens: PanelOverlayTokens,
    onSwapPageLeft: () -> Unit,
    onNewPage: () -> Unit,
    onSwapPageRight: () -> Unit,
    onDeleteCurrentPage: () -> Unit
) {
    val canDelete = !layoutLocked && totalPages > 1
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val narrowRow = maxWidth < 220.dp
        val orderRow: @Composable (Modifier) -> Unit = { mod ->
            Row(mod, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PageOrderChip("左移页", !layoutLocked && currentPageIndex > 0, tokens.cardBg, tokens.border,
                    tokens.textPrimary, tokens.textDisabled, onSwapPageLeft, Modifier.weight(1f))
                PageOrderChip("新建页", !layoutLocked, tokens.cardBg, tokens.border,
                    accent, tokens.textDisabled, onNewPage, Modifier.weight(1f))
                PageOrderChip("右移页", !layoutLocked && currentPageIndex < totalPages - 1,
                    tokens.cardBg, tokens.border, tokens.textPrimary, tokens.textDisabled, onSwapPageRight, Modifier.weight(1f))
            }
        }
        if (narrowRow) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                orderRow(Modifier.fillMaxWidth())
                PageManageDeleteChip(canDelete, tokens, onDeleteCurrentPage, Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                orderRow(Modifier.weight(1f))
                PageManageDeleteChip(canDelete, tokens, onDeleteCurrentPage)
            }
        }
    }
}

@Composable
private fun PageManageDeleteChip(
    canDelete: Boolean,
    tokens: PanelOverlayTokens,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = canDelete,
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(PanelOverlayRadii.Chip),
        color = if (canDelete) tokens.danger.copy(alpha = 0.12f) else tokens.cardBg,
        border = BorderStroke(0.6.dp, if (canDelete) tokens.danger.copy(alpha = 0.35f) else tokens.border)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
            Text(
                "删除页",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (canDelete) tokens.danger else tokens.textDisabled,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun NineKeyActionGridCard(
    editMode: Boolean,
    layoutLocked: Boolean,
    tokens: PanelOverlayTokens,
    hasPanelSize: Boolean,
    onToggleEditMode: () -> Unit,
    onOpenAddComponent: () -> Unit,
    onOpenThemeAppearance: () -> Unit,
    onOpenPanelSize: () -> Unit,
    onToggleLayoutLock: () -> Unit,
    onOpenFileBrowser: () -> Unit,
    onTempDisable: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PanelOverlayRadii.Card),
        color = tokens.elevatedSurface,
        border = BorderStroke(0.8.dp, tokens.border)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                NineKeyButton("编辑布局", Icons.Rounded.Edit, editMode && !layoutLocked, !layoutLocked,
                    modifier = Modifier.weight(1f), onClick = onToggleEditMode)
                NineKeyButton("添加卡片", Icons.Rounded.Add, active = false, enabled = !layoutLocked,
                    modifier = Modifier.weight(1f), onClick = onOpenAddComponent)
                NineKeyButton("主题外观", Icons.Rounded.Palette, modifier = Modifier.weight(1f),
                    onClick = onOpenThemeAppearance)
                if (hasPanelSize) {
                    NineKeyButton("面板尺寸", Icons.Rounded.Tune, modifier = Modifier.weight(1f), onClick = onOpenPanelSize)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                NineKeyButton(if (layoutLocked) "解锁布局" else "锁定布局", Icons.Rounded.Lock,
                    active = layoutLocked, modifier = Modifier.weight(1f), onClick = onToggleLayoutLock)
                NineKeyButton("文件浏览", Icons.Rounded.Folder, modifier = Modifier.weight(1f),
                    onClick = onOpenFileBrowser)
                NineKeyButton("临时关闭", Icons.Rounded.VisibilityOff, modifier = Modifier.weight(1f),
                    onClick = onTempDisable)
                NineKeyButton("进入设置", Icons.Rounded.Settings, modifier = Modifier.weight(1f),
                    onClick = onOpenSettings)
            }
        }
    }
}

@Composable
private fun PageOrderChip(
    label: String,
    enabled: Boolean,
    bg: Color,
    border: Color,
    textColor: Color,
    disabledColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(PanelOverlayRadii.Chip),
        color = bg,
        border = BorderStroke(0.6.dp, border)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) textColor else disabledColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LockOpenPageChip(
    lockedHere: Boolean,
    accent: Color,
    tokens: PanelOverlayTokens,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(34.dp),
        shape = RoundedCornerShape(PanelOverlayRadii.Chip),
        color = if (lockedHere) accent.copy(alpha = 0.14f) else tokens.cardBg,
        border = BorderStroke(0.6.dp, if (lockedHere) accent.copy(alpha = 0.45f) else tokens.border)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
            Text(
                text = if (lockedHere) "已设为默认唤出页 · 点此恢复打开上次面板" else "设为默认唤出页（每次唤出固定打开此页）",
                fontSize = 11.sp,
                fontWeight = if (lockedHere) FontWeight.SemiBold else FontWeight.Medium,
                color = if (lockedHere) accent else tokens.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
