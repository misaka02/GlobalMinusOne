package com.example.globalminusone.ui.panel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ContainerLayoutType
import com.example.globalminusone.data.containerIconSizeDp
import com.example.globalminusone.data.containerRowGapDp
import com.example.globalminusone.ui.AppIcons as Icons

internal data class FolderToolbarDensity(
    val compactHeader: Boolean,
    val compactMenu: Boolean,
    val ultraCompactHeader: Boolean
)

/** Adapts folder popup chrome to container inner width so narrow single-tool popups stay unobstructed. */
internal fun folderToolbarDensity(contentWidthDp: Float): FolderToolbarDensity = FolderToolbarDensity(
    compactHeader = contentWidthDp < 216f,
    compactMenu = contentWidthDp < 168f,
    ultraCompactHeader = contentWidthDp < 124f
)

/** Single-window folder toolbar: quiet header in browse mode, focused controls in edit mode, inline menu with no extra Popups. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FolderWindowToolbar(
    item: ComponentItem,
    editing: Boolean,
    locked: Boolean,
    menuExpanded: Boolean,
    onMenu: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onOpenSection: (String) -> Unit,
    onAdd: (() -> Unit)?,
    onCompactGrid: (() -> Unit)? = null,
    onInlineGridChange: ((columns: Int?, iconSizeDp: Int?, gapDp: Int?, rowGapDp: Int?) -> Unit)? = null,
    selectedChild: ComponentItem? = null,
    onOpenSelectedProperties: (() -> Unit)? = null,
    onMoveSelectedChild: (() -> Unit)? = null,
    onDeleteSelectedChild: (() -> Unit)? = null,
    onClose: () -> Unit
) {
    val palette = LocalPanelPalette.current
    val menuPalette = LocalPanelMenuPalette.current
    val totalCount = item.containerComponents.size + item.containerActions.size
    val groupBg = if (palette.isDark) Color(0xFF282E38) else Color(0xFFF3F5F8)
    val groupBorder = if (palette.isDark) Color(0xFF39414F) else Color(0xFFE3E7EE)
    var inlineTuningOpen by remember(item.id) { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val densitySpec = folderToolbarDensity(maxWidth.value)
    val iconBtnSize = if (densitySpec.ultraCompactHeader) 26.dp else 32.dp
    val iconGlyphSize = if (densitySpec.ultraCompactHeader) 15.dp else 17.dp
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 顶部标题栏：标题 + 项目数量 + 菜单与关闭
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = if (densitySpec.ultraCompactHeader) 30.dp else 38.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (densitySpec.ultraCompactHeader) 4.dp else 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .combinedClickable(
                        onClick = { if (!locked) onMenu(!menuExpanded) },
                        onLongClick = { if (!locked && !editing) onEdit() }
                    )
                    .semantics { contentDescription = if (editing) "${item.title}，内部编辑中" else item.title },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (densitySpec.ultraCompactHeader) 4.dp else 6.dp)
            ) {
                Text(
                    text = item.title.ifBlank { "文件夹" },
                    color = palette.textPrimary,
                    fontSize = if (densitySpec.ultraCompactHeader) 13.sp else 15.sp,
                    lineHeight = if (densitySpec.ultraCompactHeader) 18.sp else 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (!editing && !densitySpec.ultraCompactHeader && totalCount > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(groupBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "$totalCount",
                            color = palette.textSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (!locked && onAdd != null && editing) {
                Surface(
                    onClick = { onMenu(false); onAdd() },
                    shape = CircleShape,
                    color = palette.accent.copy(alpha = 0.14f),
                    modifier = Modifier.size(iconBtnSize)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Add, "添加项目", Modifier.size(iconGlyphSize), tint = palette.accent)
                    }
                }
            }
            if (!locked) {
                Surface(
                    onClick = { onMenu(!menuExpanded) },
                    shape = CircleShape,
                    color = if (menuExpanded) palette.accent.copy(alpha = 0.14f) else groupBg,
                    modifier = Modifier.size(iconBtnSize)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (menuExpanded) Icons.KeyboardArrowUp else Icons.MoreVert,
                            "文件夹菜单",
                            Modifier.size(iconGlyphSize),
                            tint = if (menuExpanded) palette.accent else palette.textSecondary
                        )
                    }
                }
            }
            Surface(
                onClick = onClose,
                shape = CircleShape,
                color = groupBg,
                modifier = Modifier.size(iconBtnSize)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Close, "关闭文件夹", Modifier.size(iconGlyphSize), tint = palette.textSecondary)
                }
            }
        }

        // 清爽两段式功能条：标准宽度常驻展示；窄小工具窗在浏览态收纳进 ⋮ 菜单，编辑态保持显式退出/属性入口
        if (!locked && (!densitySpec.compactHeader || editing)) {
            val editLabel = when {
                editing && densitySpec.ultraCompactHeader -> "完成编辑"
                editing -> "完成内部编辑"
                else -> "编辑内部布局"
            }
            val propLabel = if (densitySpec.ultraCompactHeader) "属性设置" else "文件夹属性"
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = groupBg,
                border = BorderStroke(0.6.dp, groupBorder)
            ) {
                if (densitySpec.compactMenu) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(3.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        FolderSegmentAction(
                            label = editLabel,
                            icon = if (editing) Icons.Check else Icons.Edit,
                            active = editing,
                            accent = palette.accent,
                            inactiveTextColor = palette.textPrimary,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onMenu(false); onEdit() }
                        )
                        FolderSegmentAction(
                            label = propLabel,
                            icon = Icons.Settings,
                            active = false,
                            accent = palette.accent,
                            inactiveTextColor = palette.textPrimary,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onMenu(false); onOpenSection("") }
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FolderSegmentAction(
                            label = editLabel,
                            icon = if (editing) Icons.Check else Icons.Edit,
                            active = editing,
                            accent = palette.accent,
                            inactiveTextColor = palette.textPrimary,
                            modifier = Modifier.weight(1f),
                            onClick = { onMenu(false); onEdit() }
                        )
                        Box(
                            Modifier
                                .width(0.8.dp)
                                .height(16.dp)
                                .background(groupBorder)
                        )
                        FolderSegmentAction(
                            label = propLabel,
                            icon = Icons.Settings,
                            active = false,
                            accent = palette.accent,
                            inactiveTextColor = palette.textPrimary,
                            modifier = Modifier.weight(1f),
                            onClick = { onMenu(false); onOpenSection("") }
                        )
                    }
                }
            }
        }

        if (editing && !locked && selectedChild != null) {
            val dangerColor = menuPalette?.danger ?: Color(0xFFD93025)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = palette.accent.copy(alpha = 0.08f),
                border = BorderStroke(0.6.dp, palette.accent.copy(alpha = 0.28f))
            ) {
                val selectedBarScroll = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(selectedBarScroll, enabled = shouldEnableChildHorizontalScroll(true, selectedBarScroll.maxValue))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "已选：${selectedChild.title.ifBlank { selectedChild.type.label }}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 110.dp)
                    )
                    if (onOpenSelectedProperties != null) {
                        FolderQuickTag("属性", palette.cardBackground, palette.accent) { onMenu(false); onOpenSelectedProperties() }
                    }
                    if (onMoveSelectedChild != null) {
                        FolderQuickTag("移动", palette.cardBackground, palette.textPrimary) { onMenu(false); onMoveSelectedChild() }
                    }
                    if (onDeleteSelectedChild != null) {
                        FolderQuickTag("删除", dangerColor.copy(alpha = 0.14f), dangerColor) { onMenu(false); onDeleteSelectedChild() }
                    }
                }
            }
        }

        // 仅在“内部编辑”模式下展开收纳良好的快捷参数条
        if (editing && !locked) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = groupBg,
                border = BorderStroke(0.6.dp, groupBorder)
            ) {
                val quickBarScroll = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(quickBarScroll, enabled = shouldEnableChildHorizontalScroll(true, quickBarScroll.maxValue))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onAdd != null) {
                        FolderQuickTag("＋ 添加项目", palette.accent.copy(alpha = 0.14f), palette.accent) { onMenu(false); onAdd() }
                    }
                    if (item.containerLayout == ContainerLayoutType.GRID && onInlineGridChange != null) {
                        FolderQuickTag(
                            label = if (inlineTuningOpen) "收起调参 ▴" else "快速调参 ▾",
                            bgColor = if (inlineTuningOpen) palette.accent else palette.accent.copy(alpha = 0.12f),
                            textColor = if (inlineTuningOpen) Color.White else palette.accent
                        ) { onMenu(false); inlineTuningOpen = !inlineTuningOpen }
                    }
                    if (item.containerLayout == ContainerLayoutType.GRID && onCompactGrid != null) {
                        FolderQuickTag("紧凑对齐", palette.cardBackground.copy(alpha = 0.72f), palette.accent) { onMenu(false); onCompactGrid() }
                    }
                    FolderQuickTag(
                        label = if (item.containerLayout == ContainerLayoutType.GRID) "网格 · ${item.containerColumns}列" else "自由摆放",
                        bgColor = palette.cardBackground.copy(alpha = 0.72f),
                        textColor = palette.textPrimary
                    ) { onMenu(false); onOpenSection("内部排列") }
                    FolderQuickTag("留白", palette.cardBackground.copy(alpha = 0.72f), palette.textSecondary) { onMenu(false); onOpenSection("图标与文字留白") }
                    FolderQuickTag("窗口尺寸", palette.cardBackground.copy(alpha = 0.72f), palette.textSecondary) { onMenu(false); onOpenSection("文件夹窗口") }
                }
            }

            if (inlineTuningOpen && item.containerLayout == ContainerLayoutType.GRID && onInlineGridChange != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = groupBg,
                    border = BorderStroke(0.6.dp, groupBorder)
                ) {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        GridDimensionControls(
                            columnCount = item.containerColumns,
                            iconSizeDp = containerIconSizeDp(item) ?: item.containerIconSizeDp,
                            gapDp = item.containerItemGapDp,
                            onColumns = { onInlineGridChange(it, null, null, null) },
                            onIconSize = { onInlineGridChange(null, it, null, null) },
                            onGap = { onInlineGridChange(null, null, it, null) },
                            maxColumns = 8,
                            allowAutoColumns = false,
                            rowGapDp = containerRowGapDp(item),
                            onRowGap = { onInlineGridChange(null, null, null, it) }
                        )
                    }
                }
            }
        }

        if (menuExpanded && !locked) {
            val panelBg = menuPalette?.background ?: palette.cardBackground
            val borderColor = menuPalette?.divider ?: groupBorder
            val compactMenu = densitySpec.compactMenu
            fun runAction(action: () -> Unit) { onMenu(false); action() }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = panelBg,
                border = BorderStroke(0.8.dp, borderColor),
                shadowElevation = 3.dp
            ) {
                Column(Modifier.padding(vertical = if (compactMenu) 4.dp else 6.dp)) {
                    FolderMenuEntry(
                        title = when {
                            editing && compactMenu -> "完成编辑"
                            editing -> "完成内部编辑"
                            compactMenu -> "编辑布局"
                            else -> "编辑内部布局"
                        },
                        icon = if (editing) Icons.Check else Icons.Edit,
                        subtitle = if (compactMenu) "" else if (editing) "退出文件夹内部编辑" else "调整子项、拖拽与网格参数",
                        active = editing,
                        compact = compactMenu
                    ) {
                        runAction(onEdit)
                    }
                    FolderMenuEntry(
                        title = if (compactMenu) "属性设置" else "文件夹属性",
                        icon = Icons.Settings,
                        subtitle = if (compactMenu) "" else "外观、标题、布局与窗口全部设置",
                        compact = compactMenu
                    ) {
                        runAction { onOpenSection("") }
                    }
                    HorizontalDivider(color = borderColor.copy(alpha = 0.6f))
                    if (!compactMenu) {
                        Text(
                            text = "内容与布局",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textSecondary
                        )
                    }
                    if (onAdd != null) {
                        FolderMenuEntry(
                            title = "添加项目",
                            icon = Icons.Add,
                            subtitle = if (compactMenu) "" else "向此文件夹添加卡片",
                            compact = compactMenu
                        ) {
                            runAction { onAdd.invoke() }
                        }
                    }
                    FolderMenuEntry(
                        title = if (compactMenu) "排列间距" else "排列与间距",
                        icon = Icons.GridView,
                        subtitle = if (compactMenu) "" else if (item.containerLayout == ContainerLayoutType.GRID) "当前：网格 · ${item.containerColumns}列" else "当前：自由摆放",
                        compact = compactMenu
                    ) {
                        runAction { onOpenSection("内部排列") }
                    }
                    FolderMenuEntry(
                        title = if (compactMenu) "留白设置" else "图标与文字留白",
                        icon = Icons.FormatPaint,
                        subtitle = if (compactMenu) "" else "上下内边距 · 图文间隔",
                        compact = compactMenu
                    ) {
                        runAction { onOpenSection("图标与文字留白") }
                    }
                    HorizontalDivider(color = borderColor.copy(alpha = 0.6f))
                    if (!compactMenu) {
                        Text(
                            text = "窗口与关闭",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textSecondary
                        )
                    }
                    FolderMenuEntry(
                        title = "窗口大小",
                        icon = Icons.Tune,
                        subtitle = if (compactMenu) "" else
                            "宽${if (item.containerPopupWidthDp > 0) "${item.containerPopupWidthDp}dp" else "自动"} · " +
                                "高${if (item.containerPopupHeightDp > 0) "${item.containerPopupHeightDp}dp" else "自动"}",
                        compact = compactMenu
                    ) {
                        runAction { onOpenSection("文件夹窗口") }
                    }
                    FolderMenuEntry(
                        title = if (compactMenu) "关闭" else "关闭文件夹",
                        icon = Icons.Close,
                        compact = compactMenu
                    ) { runAction(onClose) }
                }
            }
        }
    }
    }
}

@Composable
private fun FolderSegmentAction(
    label: String,
    icon: ImageVector,
    active: Boolean,
    accent: Color,
    inactiveTextColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(30.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (active) accent else Color.Transparent
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, Modifier.size(13.dp), tint = if (active) Color.White else accent)
            Text(
                text = label,
                color = if (active) Color.White else inactiveTextColor,
                fontSize = 11.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FolderQuickTag(
    label: String,
    bgColor: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = bgColor
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun FolderMenuEntry(
    title: String,
    icon: ImageVector,
    subtitle: String = "",
    active: Boolean = false,
    enabled: Boolean = true,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val palette = LocalPanelPalette.current
    val menuPalette = LocalPanelMenuPalette.current
    val titleColor = if (!enabled) (menuPalette?.disabled ?: palette.textSecondary.copy(alpha = 0.45f))
    else if (active) (menuPalette?.accent ?: palette.accent)
    else (menuPalette?.text ?: palette.textPrimary)
    val subColor = if (enabled) (menuPalette?.secondary ?: palette.textSecondary)
    else (menuPalette?.disabled ?: palette.textSecondary.copy(alpha = 0.45f))
    val iconTint = if (!enabled) subColor else if (active) (menuPalette?.accent ?: palette.accent) else subColor
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = if (compact) 8.dp else 12.dp,
                vertical = if (compact) 6.dp else 9.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, Modifier.size(if (compact) 14.dp else 17.dp), tint = iconTint)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    title,
                    color = titleColor,
                    fontSize = if (compact) 11.sp else 13.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotEmpty()) Text(
                    subtitle,
                    color = subColor,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
