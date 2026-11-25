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

/**
 * Policy flag controlling whether the permanent two-button ("编辑内部布局 | 文件夹属性") row
 * appears beneath the folder title. Both actions live in the ⋮ menu so browsing keeps full vertical space.
 */
internal fun shouldShowFolderHeaderDualActionRow(
    editing: Boolean = false,
    locked: Boolean = false
): Boolean = false

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
    onOpenSelectedGeometry: (() -> Unit)? = null,
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

            if (!locked && editing) {
                Surface(
                    onClick = { onMenu(false); onEdit() },
                    shape = RoundedCornerShape(999.dp),
                    color = palette.accent,
                    modifier = Modifier.height(iconBtnSize)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = if (densitySpec.ultraCompactHeader) 7.dp else 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(Icons.Check, "完成编辑", Modifier.size(13.dp), tint = Color.White)
                        if (!densitySpec.ultraCompactHeader) {
                            Text(
                                text = "完成",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
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
                Box {
                    Surface(
                        onClick = { onMenu(!menuExpanded) },
                        shape = CircleShape,
                        color = if (menuExpanded) palette.accent.copy(alpha = 0.14f) else groupBg,
                        modifier = Modifier.size(iconBtnSize)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.MoreVert,
                                "文件夹菜单",
                                Modifier.size(iconGlyphSize),
                                tint = if (menuExpanded) palette.accent else palette.textSecondary
                            )
                        }
                    }
                    fun runAction(action: () -> Unit) {
                        onMenu(false)
                        action()
                    }
                    PanelDropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { onMenu(false) }
                    ) {
                        com.example.globalminusone.ui.Android12WindowKeyGuard()
                        val mPalette = checkNotNull(LocalPanelMenuPalette.current)
                        PanelMenuItem(
                            text = {
                                Text(
                                    if (editing) "完成内部编辑" else "编辑内部布局",
                                    fontSize = 13.sp,
                                    fontWeight = if (editing) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (editing) mPalette.accent else mPalette.text
                                )
                            },
                            onClick = { runAction(onEdit) }
                        )
                        PanelMenuItem(
                            text = { Text("文件夹属性", fontSize = 13.sp) },
                            onClick = { runAction { onOpenSection("") } }
                        )
                        HorizontalDivider()
                        PanelMenuSectionTitle("内容与布局")
                        if (onAdd != null) {
                            PanelMenuItem(
                                text = { Text("添加项目", fontSize = 13.sp) },
                                onClick = { runAction { onAdd.invoke() } }
                            )
                        }
                        PanelMenuItem(
                            text = {
                                Text(
                                    if (item.containerLayout == ContainerLayoutType.GRID) {
                                        "排列与间距（网格 · ${item.containerColumns}列）"
                                    } else {
                                        "排列与间距（自由摆放）"
                                    },
                                    fontSize = 13.sp
                                )
                            },
                            onClick = { runAction { onOpenSection("内部排列") } }
                        )
                        PanelMenuItem(
                            text = { Text("图标与文字留白", fontSize = 13.sp) },
                            onClick = { runAction { onOpenSection("图标与文字留白") } }
                        )
                        HorizontalDivider()
                        PanelMenuSectionTitle("窗口与关闭")
                        PanelMenuItem(
                            text = { Text("窗口大小", fontSize = 13.sp) },
                            onClick = { runAction { onOpenSection("文件夹窗口") } }
                        )
                        PanelMenuItem(
                            text = { Text("关闭文件夹", fontSize = 13.sp) },
                            onClick = { runAction(onClose) }
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

        if (shouldShowFolderHeaderDualActionRow(editing = editing, locked = locked)) {
            // 保留兼容钩子，默认关闭：编辑布局与文件夹属性统一通过 ⋮ 三点菜单进入
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
                    if (onOpenSelectedGeometry != null) {
                        FolderQuickTag("尺寸", palette.cardBackground, palette.accent) { onMenu(false); onOpenSelectedGeometry() }
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
