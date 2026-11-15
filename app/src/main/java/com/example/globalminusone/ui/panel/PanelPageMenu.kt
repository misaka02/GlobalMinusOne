package com.example.globalminusone.ui.panel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.example.globalminusone.ui.AppIcons as Icons
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.PageLayoutType

@Composable
fun BottomNineKeyModal(
    state: GlobalMinusOneState,
    currentPageIndex: Int,
    onDismiss: () -> Unit,
    onToggleEditMode: () -> Unit,
    onOpenAddComponent: () -> Unit,
    onTempDisable: () -> Unit,
    onDeleteCurrentPage: () -> Unit,
    onOpenThemeAppearance: () -> Unit,
    onOpenSettings: () -> Unit,
    onSwapPageLeft: () -> Unit,
    onNewPage: () -> Unit,
    onSwapPageRight: () -> Unit,
    onOpenFileBrowser: () -> Unit,
    onToggleLayoutLock: () -> Unit,
    onToggleLayoutType: () -> Unit = {},
    onSelectLayoutType: ((PageLayoutType) -> Unit)? = null,
    onNavigatePage: ((Int) -> Unit)? = null,
    onOpenPanelSize: (() -> Unit)? = null
) {
    val layoutLocked = state.advancedConfig.layoutLocked
    val palette = LocalPanelPalette.current
    val sheetBg = if (palette.isDark) Color(0xFF1A1E25) else Color(0xFFFFFFFF)
    val groupBg = if (palette.isDark) Color(0xFF232832) else Color(0xFFF4F6FA)
    val primaryText = if (palette.isDark) Color(0xFFF2F4F8) else Color(0xFF181B20)
    val secondaryText = if (palette.isDark) Color(0xFF9AA4B4) else Color(0xFF5A6270)
    val disabledText = if (palette.isDark) Color(0xFF525A68) else Color(0xFFBDC4CF)
    val handleColor = if (palette.isDark) Color(0xFF3E4654) else Color(0xFFD6DCE5)
    val tileBg = if (palette.isDark) Color(0xFF2B313D) else Color(0xFFFFFFFF)
    val borderColor = if (palette.isDark) Color(0xFF343B48) else Color(0xFFE6EAF2)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.48f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {},
            color = sheetBg,
            shadowElevation = 10.dp,
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 顶部拉条
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(handleColor)
                )

                val curPage = state.pages.getOrNull(currentPageIndex)
                val curLayout = curPage?.layoutType ?: PageLayoutType.FREE

                // 分区 1：页面导航、页码切换与排版模式卡片
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = groupBg,
                    border = BorderStroke(0.8.dp, borderColor)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = { if (state.pages.size > 1) onNavigatePage?.invoke(currentPageIndex - 1) },
                                enabled = state.pages.size > 1,
                                shape = RoundedCornerShape(10.dp),
                                color = tileBg,
                                border = BorderStroke(0.6.dp, borderColor),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowBack,
                                        contentDescription = "上一页",
                                        tint = if (state.pages.size > 1) secondaryText else disabledText,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }

                            if (state.pages.size > 1 && onNavigatePage != null) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    state.pages.forEachIndexed { idx, p ->
                                        val active = idx == currentPageIndex
                                        Surface(
                                            onClick = { if (!active) onNavigatePage(idx) },
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (active) palette.accent else tileBg,
                                            border = if (active) null else BorderStroke(0.6.dp, borderColor)
                                        ) {
                                            Text(
                                                text = "${idx + 1}${p.name.takeIf { it.isNotBlank() }?.let { "·$it" }.orEmpty()}",
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                fontSize = 11.sp,
                                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                                color = if (active) Color.White else secondaryText,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "第 ${currentPageIndex + 1} / ${state.pages.size} 页",
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = primaryText
                                )
                            }

                            Surface(
                                onClick = { if (state.pages.size > 1) onNavigatePage?.invoke(currentPageIndex + 1) },
                                enabled = state.pages.size > 1,
                                shape = RoundedCornerShape(10.dp),
                                color = tileBg,
                                border = BorderStroke(0.6.dp, borderColor),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowForward,
                                        contentDescription = "下一页",
                                        tint = if (state.pages.size > 1) secondaryText else disabledText,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }

                        // 当前页三态排版切换分段条
                        if (onSelectLayoutType != null) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = tileBg,
                                border = BorderStroke(0.6.dp, borderColor)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(3.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf(
                                        PageLayoutType.LINEAR to "线性",
                                        PageLayoutType.GRID to "网格",
                                        PageLayoutType.FREE to "自由"
                                    ).forEach { (mode, shortName) ->
                                        val selected = curLayout == mode
                                        Surface(
                                            onClick = { onSelectLayoutType(mode) },
                                            enabled = !layoutLocked,
                                            modifier = Modifier.weight(1f).height(32.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            color = when {
                                                layoutLocked -> Color.Transparent
                                                selected -> palette.accent
                                                else -> Color.Transparent
                                            }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = shortName,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                    color = when {
                                                        layoutLocked -> disabledText
                                                        selected -> Color.White
                                                        else -> primaryText
                                                    },
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("当前排版：${curLayout.label}", fontSize = 12.sp, color = secondaryText)
                                Surface(
                                    onClick = onToggleLayoutType,
                                    enabled = !layoutLocked,
                                    shape = RoundedCornerShape(8.dp),
                                    color = palette.accent.copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = "切换 ⇄",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (layoutLocked) disabledText else palette.accent
                                    )
                                }
                            }
                        }

                        // 页面顺序与新建删除动作条
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PageOrderChip("交换左页", !layoutLocked && currentPageIndex > 0, tileBg, borderColor, primaryText, disabledText, Modifier.weight(1f), onSwapPageLeft)
                            PageOrderChip("新建一页", !layoutLocked, tileBg, borderColor, palette.accent, disabledText, Modifier.weight(1f), onNewPage)
                            PageOrderChip("交换右页", !layoutLocked && currentPageIndex < state.pages.lastIndex, tileBg, borderColor, primaryText, disabledText, Modifier.weight(1f), onSwapPageRight)
                            PageOrderChip("删除该页…", !layoutLocked && state.pages.size > 1, tileBg, borderColor, Color(0xFFEF4444), disabledText, Modifier.weight(1f), onDeleteCurrentPage)
                        }
                    }
                }

                // 分区 2：布局编辑与面板外观（4 列对齐磁贴）
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "面板编辑与外观",
                        color = secondaryText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = groupBg,
                        border = BorderStroke(0.8.dp, borderColor)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            NineKeyButton(
                                title = "编辑模式",
                                icon = Icons.Rounded.Edit,
                                active = state.editMode && !layoutLocked,
                                enabled = !layoutLocked,
                                modifier = Modifier.weight(1f),
                                onClick = onToggleEditMode
                            )
                            NineKeyButton(
                                title = "添加",
                                icon = Icons.Rounded.Add,
                                enabled = !layoutLocked,
                                active = false,
                                modifier = Modifier.weight(1f),
                                onClick = onOpenAddComponent
                            )
                            NineKeyButton(
                                title = "主题外观",
                                icon = Icons.Rounded.Palette,
                                modifier = Modifier.weight(1f),
                                onClick = onOpenThemeAppearance
                            )
                            if (onOpenPanelSize != null) {
                                NineKeyButton(
                                    title = "面板尺寸",
                                    icon = Icons.Rounded.Tune,
                                    modifier = Modifier.weight(1f),
                                    onClick = onOpenPanelSize
                                )
                            }
                        }
                    }
                }

                // 分区 3：常用工具与系统入口
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "工具与控制",
                        color = secondaryText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = groupBg,
                        border = BorderStroke(0.8.dp, borderColor)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            NineKeyButton(
                                title = "文件浏览",
                                icon = Icons.Rounded.Folder,
                                modifier = Modifier.weight(1f),
                                onClick = onOpenFileBrowser
                            )
                            NineKeyButton(
                                title = if (layoutLocked) "解锁布局" else "锁定布局",
                                icon = Icons.Rounded.Lock,
                                active = layoutLocked,
                                modifier = Modifier.weight(1f),
                                onClick = onToggleLayoutLock
                            )
                            NineKeyButton(
                                title = "临时关闭",
                                icon = Icons.Rounded.VisibilityOff,
                                modifier = Modifier.weight(1f),
                                onClick = onTempDisable
                            )
                            NineKeyButton(
                                title = "进入设置",
                                icon = Icons.Rounded.Settings,
                                modifier = Modifier.weight(1f),
                                onClick = onOpenSettings
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(32.dp),
        shape = RoundedCornerShape(8.dp),
        color = bg,
        border = BorderStroke(0.6.dp, border)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) textColor else disabledColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun NineKeyButton(
    title: String,
    icon: ImageVector,
    active: Boolean = false,
    enabled: Boolean = true,
    destructive: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = LocalPanelPalette.current
    val dangerColor = Color(0xFFEF4444)
    val contentColor = if (!enabled) {
        if (palette.isDark) Color(0xFF555D6B) else Color(0xFFBDBDBD)
    } else if (destructive) {
        dangerColor
    } else if (active) {
        palette.accent
    } else {
        if (palette.isDark) Color(0xFFF0F2F5) else Color(0xFF1F2329)
    }
    val circleBg = when {
        active -> palette.accent.copy(alpha = 0.18f)
        destructive && enabled -> dangerColor.copy(alpha = 0.12f)
        palette.isDark -> Color(0xFF2D3440)
        else -> Color.White
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (active) palette.accent.copy(alpha = 0.10f) else Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(circleBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = title,
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

// -----------------------------------------------------------------------------------------
// 「添加组件」全分类选择器弹窗 (AddComponentModal)
// -----------------------------------------------------------------------------------------
