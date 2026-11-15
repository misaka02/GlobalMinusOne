package com.example.globalminusone.ui.panel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.PageLayoutType
import kotlin.math.roundToInt

/** Root overlay: never participates in the panel Column's content measurement. */
@Composable
fun EditModeTopBanner(
    currentPage: Int,
    totalPages: Int,
    isLinearPage: Boolean,
    layoutType: PageLayoutType = if (isLinearPage) PageLayoutType.LINEAR else PageLayoutType.FREE,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onToggleLayoutType: () -> Unit,
    onSelectLayoutType: ((PageLayoutType) -> Unit)? = null,
    onAdd: () -> Unit,
    onDone: () -> Unit,
    onGlobalProperties: () -> Unit,
    onPanelGeometry: () -> Unit,
    onTriggerZone: () -> Unit = {},
    onCardProperties: (() -> Unit)? = null,
    panelGeometryActive: Boolean = false,
    triggerZoneActive: Boolean = false,
    onPanelNumbers: () -> Unit = {},
    onNewPage: (() -> Unit)? = null,
    onCompactGrid: (() -> Unit)? = null,
    pageName: String = ""
) {
    val palette = LocalPanelPalette.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val preferences = remember(context) { context.applicationContext.getSharedPreferences("edit_tools", android.content.Context.MODE_PRIVATE) }
    var menuOpen by remember { mutableStateOf(false) }
    var collapsed by remember(preferences) {
        mutableStateOf(runCatching { preferences.getBoolean("collapsed", false) }.getOrDefault(false))
    }
    var anchor by remember(preferences) {
        mutableStateOf(
            EditToolsAnchor(
                runCatching { preferences.getFloat("x", 0f) }.getOrDefault(0f),
                runCatching { preferences.getFloat("y", 0f) }.getOrDefault(0f)
            ).normalized()
        )
    }
    var dragPosition by remember { mutableStateOf<EditToolsPosition?>(null) }
    var toolSize by remember { mutableStateOf(IntSize.Zero) }
    var gripCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp)) {
        val hostWidth = constraints.maxWidth.toFloat()
        val hostHeight = constraints.maxHeight.toFloat()
        val fallbackWidthDp = if (collapsed) 172.dp else 348.dp
        val toolWidth = toolSize.width.takeIf { it > 0 }?.toFloat() ?: with(density) { fallbackWidthDp.toPx() }.coerceAtMost(hostWidth)
        val toolHeight = toolSize.height.takeIf { it > 0 }?.toFloat() ?: with(density) { 40.dp.toPx() }
        val clamped = dragPosition ?: editToolsPosition(anchor, toolWidth, toolHeight, hostWidth, hostHeight)
        val surfaceColor = if (palette.isDark) Color(0xFF1F242E).copy(alpha = 0.94f) else Color(0xFFFCFDFF).copy(alpha = 0.95f)
        val groupChipColor = if (palette.isDark) Color(0xFF2B313D) else Color(0xFFF1F4F8)
        val subtleBorder = palette.cardBorder.copy(alpha = 0.72f)
        val layoutShort = when (layoutType) {
            PageLayoutType.LINEAR -> "线性"
            PageLayoutType.GRID -> "网格"
            PageLayoutType.FREE -> "自由"
        }

        Surface(
            Modifier
                .offset { IntOffset(clamped.x.roundToInt(), clamped.y.roundToInt()) }
                .then(if (collapsed) Modifier.wrapContentWidth() else Modifier.widthIn(max = 360.dp).fillMaxWidth())
                .onSizeChanged { toolSize = it },
            shape = RoundedCornerShape(14.dp),
            color = surfaceColor,
            border = BorderStroke(0.8.dp, subtleBorder),
            shadowElevation = 4.dp
        ) {
            Row(
                Modifier.heightIn(min = 38.dp).padding(start = 2.dp, end = 6.dp, top = 3.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    Modifier
                        .size(width = 26.dp, height = 34.dp)
                        .onGloballyPositioned { gripCoordinates = it }
                        .semantics { contentDescription = "拖动编辑工具条" }
                        .pointerInput(hostWidth, hostHeight, toolSize) {
                            var origin = Offset.Zero
                            var start = editToolsPosition(anchor, toolWidth, toolHeight, hostWidth, hostHeight)
                            try {
                                detectDragGestures(
                                    onDragStart = { local ->
                                        menuOpen = false
                                        origin = gripCoordinates?.localToRoot(local) ?: Offset.Zero
                                        start = editToolsPosition(anchor, toolWidth, toolHeight, hostWidth, hostHeight)
                                        dragPosition = start
                                    },
                                    onDragEnd = {
                                        anchor = editToolsAnchor(dragPosition ?: start, anchor, toolWidth, toolHeight, hostWidth, hostHeight)
                                        preferences.edit().putFloat("x", anchor.x).putFloat("y", anchor.y).apply()
                                        dragPosition = null
                                    },
                                    onDragCancel = { dragPosition = null }
                                ) { change, _ ->
                                    change.consume()
                                    gripCoordinates?.takeIf { it.isAttached }?.localToRoot(change.position)?.let { root ->
                                        val delta = root - origin
                                        dragPosition = clampEditToolsPosition(
                                            EditToolsPosition(start.x + delta.x, start.y + delta.y),
                                            toolWidth, toolHeight, hostWidth, hostHeight
                                        )
                                    }
                                }
                            } finally {
                                dragPosition = null
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("⠿", color = palette.textSecondary, fontSize = 17.sp)
                }

                if (collapsed) {
                    Surface(
                        onClick = {
                            collapsed = false
                            preferences.edit().putBoolean("collapsed", false).apply()
                        },
                        shape = RoundedCornerShape(9.dp),
                        color = groupChipColor
                    ) {
                        Text(
                            text = "工具 ▸",
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            color = palette.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                    Surface(
                        onClick = { menuOpen = false; onDone() },
                        shape = RoundedCornerShape(9.dp),
                        color = palette.accent
                    ) {
                        Text(
                            text = "完成",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                } else {
                // 页面 + 排版 + 高级工具统一入口胶囊
                Box(Modifier.weight(1f)) {
                    Surface(
                        onClick = { menuOpen = true },
                        shape = RoundedCornerShape(10.dp),
                        color = if (panelGeometryActive || triggerZoneActive) palette.accent.copy(alpha = 0.14f) else groupChipColor,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 34.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when {
                                    panelGeometryActive -> "边框 ✓"
                                    triggerZoneActive -> "触发区 ✓"
                                    else -> "${currentPage + 1}/$totalPages · $layoutShort ▾"
                                },
                                color = if (panelGeometryActive || triggerZoneActive) palette.accent else palette.textPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        com.example.globalminusone.service.Android12WindowKeyGuard()

                        if (onSelectLayoutType != null) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("第 ${currentPage + 1} 页排版", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = palette.accent)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(
                                        PageLayoutType.LINEAR to "线性",
                                        PageLayoutType.GRID to "网格",
                                        PageLayoutType.FREE to "自由"
                                    ).forEach { (mode, label) ->
                                        val active = layoutType == mode
                                        Surface(
                                            onClick = {
                                                menuOpen = false
                                                onSelectLayoutType(mode)
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (active) palette.accent else groupChipColor
                                        ) {
                                            Text(
                                                text = label,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                fontSize = 11.sp,
                                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                                color = if (active) Color.White else palette.textPrimary
                                            )
                                        }
                                    }
                                }
                            }
                            if (onCompactGrid != null) {
                                DropdownMenuItem(
                                    text = { Text("网格紧凑对齐") },
                                    onClick = { menuOpen = false; onCompactGrid() }
                                )
                            }
                            HorizontalDivider()
                        } else {
                            DropdownMenuItem(
                                text = { Text("切换排版（当前：$layoutShort）") },
                                onClick = { menuOpen = false; onToggleLayoutType() }
                            )
                            if (onCompactGrid != null) {
                                DropdownMenuItem(
                                    text = { Text("网格紧凑对齐") },
                                    onClick = { menuOpen = false; onCompactGrid() }
                                )
                            }
                            HorizontalDivider()
                        }

                        Text(
                            text = "属性与外观",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textSecondary
                        )
                        DropdownMenuItem(
                            text = { Text("已选卡片属性") },
                            onClick = { menuOpen = false; onCardProperties?.invoke() },
                            enabled = onCardProperties != null
                        )
                        DropdownMenuItem(
                            text = { Text("全局主题与外观") },
                            onClick = { menuOpen = false; onGlobalProperties() }
                        )
                        HorizontalDivider()
                        Text(
                            text = "窗口与触发区",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textSecondary
                        )
                        DropdownMenuItem(
                            text = { Text(if (panelGeometryActive) "退出面板边框调整" else "拖动调整面板大小") },
                            onClick = { menuOpen = false; onPanelGeometry() }
                        )
                        DropdownMenuItem(
                            text = { Text("精确面板数值…") },
                            onClick = { menuOpen = false; onPanelNumbers() }
                        )
                        DropdownMenuItem(
                            text = { Text(if (triggerZoneActive) "退出触发区位置调整" else "调整触发区域位置") },
                            onClick = { menuOpen = false; onTriggerZone() }
                        )
                        HorizontalDivider()
                        Text(
                            text = "页面切换",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textSecondary
                        )
                        DropdownMenuItem(
                            text = { Text("上一页") },
                            onClick = { menuOpen = false; onPrevPage() },
                            enabled = totalPages > 1
                        )
                        DropdownMenuItem(
                            text = { Text("下一页") },
                            onClick = { menuOpen = false; onNextPage() },
                            enabled = totalPages > 1
                        )
                        if (onNewPage != null) {
                            DropdownMenuItem(
                                text = { Text("新建页面…") },
                                onClick = { menuOpen = false; onNewPage() }
                            )
                        }
                    }
                }

                    Surface(
                        onClick = { menuOpen = false; onAdd() },
                        shape = RoundedCornerShape(9.dp),
                        color = groupChipColor
                    ) {
                        Text(
                            text = "＋添加",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            color = palette.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }

                    Surface(
                        onClick = {
                            menuOpen = false
                            if (onCardProperties != null) onCardProperties() else onGlobalProperties()
                        },
                        shape = RoundedCornerShape(9.dp),
                        color = if (onCardProperties != null) palette.accent.copy(alpha = 0.14f) else groupChipColor
                    ) {
                        Text(
                            text = if (onCardProperties != null) "属性" else "外观",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            color = if (onCardProperties != null) palette.accent else palette.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }

                if (triggerZoneActive || panelGeometryActive) {
                    Surface(
                        onClick = {
                            menuOpen = false
                            if (triggerZoneActive) onTriggerZone() else onPanelGeometry()
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = palette.accent.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = if (triggerZoneActive) "收起触发" else "收起边框",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                            color = palette.accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }

                Surface(
                    onClick = {
                        menuOpen = false
                        collapsed = true
                        preferences.edit().putBoolean("collapsed", true).apply()
                    },
                    shape = RoundedCornerShape(9.dp),
                    color = groupChipColor
                ) {
                    Text(
                        text = "收起",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        color = palette.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }

                Surface(
                    onClick = { menuOpen = false; onDone() },
                    shape = RoundedCornerShape(9.dp),
                    color = palette.accent
                ) {
                    Text(
                        text = "完成",
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                }
            }
        }
    }
}

internal data class EditToolsPosition(val x: Float, val y: Float)
internal fun clampEditToolsPosition(position: EditToolsPosition, width: Float, height: Float, hostWidth: Float, hostHeight: Float) =
    EditToolsPosition(
        position.x.coerceIn(0f, (hostWidth - width).coerceAtLeast(0f)),
        position.y.coerceIn(0f, (hostHeight - height).coerceAtLeast(0f))
    )
