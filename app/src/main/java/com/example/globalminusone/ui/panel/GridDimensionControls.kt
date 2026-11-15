package com.example.globalminusone.ui.panel

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.MAX_GRID_GAP_DP
import com.example.globalminusone.data.MIN_GRID_GAP_DP
import kotlin.math.roundToInt

/** Compact exact controls shared by page grids and container grids; icon size is actual dp. */
@Composable
internal fun GridDimensionControls(
    columnCount: Int,
    iconSizeDp: Int,
    gapDp: Int,
    onColumns: (Int) -> Unit,
    onIconSize: (Int) -> Unit,
    onGap: (Int) -> Unit,
    modifier: Modifier = Modifier,
    maxColumns: Int = 12,
    allowAutoColumns: Boolean = true,
    onRequestInput: ((Int) -> Unit)? = null,
    rowGapDp: Int? = null,
    onRowGap: ((Int) -> Unit)? = null
) {
    val palette = LocalPanelPalette.current
    val menuPalette = LocalPanelMenuPalette.current
    var input by remember { mutableStateOf<Int?>(null) }
    var quickExpanded by remember { mutableStateOf(true) }
    val expandedControls = remember { BringIntoViewRequester() }
    var expandedSize by remember { mutableStateOf(IntSize.Zero) }
    var quickSelection by remember { mutableStateOf(0) }
    LaunchedEffect(quickExpanded, quickSelection, expandedSize) {
        if (quickExpanded && expandedSize.height > 0) {
            // Wait for the expanded content to be measured. Request the presets and stepper,
            // not just the already visible first tab row above them.
            withFrameNanos { }
            expandedControls.bringIntoView()
        }
    }
    val minimumColumns = if (allowAutoColumns) 0 else 1
    val hasRowGap = rowGapDp != null && onRowGap != null
    val labels = if (hasRowGap) listOf("列数", "图标", "横向间距", "纵向间距") else listOf("列数", "图标", "间距")
    val quickKind = quickSelection.coerceIn(0, labels.lastIndex)
    val values = listOf(
        columnCount.coerceIn(minimumColumns, maxColumns),
        iconSizeDp.coerceIn(0, 192),
        gapDp.coerceIn(MIN_GRID_GAP_DP, MAX_GRID_GAP_DP)
    ) + if (hasRowGap) listOf(rowGapDp!!.coerceIn(MIN_GRID_GAP_DP, MAX_GRID_GAP_DP)) else emptyList()
    val accent = menuPalette?.accent ?: palette.accent
    val secondary = menuPalette?.secondary ?: palette.textSecondary
    val chip = menuPalette?.chip ?: palette.accent.copy(alpha = .08f)
    val selectedChip = menuPalette?.selectedChip ?: palette.accent.copy(alpha = .18f)
    fun setValue(kind: Int, value: Int) {
        when (kind) {
            0 -> onColumns(value)
            1 -> onIconSize(value)
            2 -> onGap(value)
            3 -> onRowGap?.invoke(value)
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val displayedValues = listOf(
            if (columnCount == 0 && allowAutoColumns) "自动" else "${columnCount.coerceIn(minimumColumns, maxColumns)}列",
            if (iconSizeDp <= 0) "自动" else "${iconSizeDp}dp",
            "${gapDp}dp"
        ) + if (hasRowGap) listOf("${rowGapDp}dp") else emptyList()

        // 单排统一指标卡：点击直接弹出精确数值输入
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            labels.indices.forEach { index ->
                Surface(
                    onClick = { if (onRequestInput != null) onRequestInput(index) else input = index },
                    modifier = Modifier.weight(1f).heightIn(min = 46.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = chip
                ) {
                    Column(
                        Modifier.padding(horizontal = 4.dp, vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            labels[index].replace("横向间距", "横距").replace("纵向间距", "纵距"),
                            fontSize = 10.sp,
                            color = secondary,
                            maxLines = 1
                        )
                        Text(
                            displayedValues[index],
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = accent,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Surface(
            onClick = { quickExpanded = !quickExpanded },
            modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp),
            shape = RoundedCornerShape(9.dp),
            color = chip
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (quickExpanded) "收起快捷微调 ▴" else "展开滑条与预设 ▾",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = accent
                )
                Surface(
                    onClick = { if (onRequestInput != null) onRequestInput(quickKind) else input = quickKind },
                    shape = RoundedCornerShape(6.dp),
                    color = accent.copy(alpha = 0.14f)
                ) {
                    Text(
                        "精确输入",
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accent
                    )
                }
            }
        }

        if (quickExpanded) {
            // 单行分组标签切换（不再与上方指标卡产生 2x2 双重堆叠）
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(9.dp),
                color = chip
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    labels.indices.forEach { kind ->
                        val activeTab = quickKind == kind
                        Surface(
                            onClick = { quickSelection = kind },
                            modifier = Modifier.weight(1f).height(30.dp),
                            shape = RoundedCornerShape(7.dp),
                            color = if (activeTab) accent else androidx.compose.ui.graphics.Color.Transparent
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    labels[kind].replace("横向间距", "横距").replace("纵向间距", "纵距"),
                                    fontSize = 11.sp,
                                    fontWeight = if (activeTab) FontWeight.Bold else FontWeight.Medium,
                                    color = if (activeTab) androidx.compose.ui.graphics.Color.White else secondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            Column(
                Modifier.fillMaxWidth()
                    .bringIntoViewRequester(expandedControls)
                    .onSizeChanged { expandedSize = it },
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val minimum = when (quickKind) {
                    0 -> minimumColumns
                    1 -> 0
                    else -> MIN_GRID_GAP_DP
                }
                val maximum = when (quickKind) { 0 -> maxColumns; 1 -> 192; else -> MAX_GRID_GAP_DP }
                val currentValue = values[quickKind].coerceIn(minimum, maximum)
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Slider(
                        value = currentValue.toFloat(),
                        onValueChange = { setValue(quickKind, it.roundToInt().coerceIn(minimum, maximum)) },
                        valueRange = minimum.toFloat()..maximum.toFloat(),
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        onClick = { if (onRequestInput != null) onRequestInput(quickKind) else input = quickKind },
                        shape = RoundedCornerShape(7.dp),
                        color = chip
                    ) {
                        Text(
                            displayedValues[quickKind],
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                    }
                }
                val presets = when (quickKind) {
                    0 -> (if (allowAutoColumns) listOf(0) else emptyList()) + (2..6).filter { it <= maxColumns }
                    1 -> listOf(0, 24, 32, 40, 48, 64)
                    else -> listOf(-8, -4, 0, 4, 8, 12)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    presets.forEach { value ->
                        val isSelected = values[quickKind] == value
                        Surface(
                            onClick = { setValue(quickKind, value) },
                            modifier = Modifier.weight(1f).heightIn(min = 34.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) selectedChip else chip,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.55f)) else null
                        ) {
                            Box(Modifier.padding(horizontal = 2.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = when {
                                        value == 0 && quickKind in 0..1 -> "自动"
                                        quickKind == 0 -> "${value}列"
                                        else -> "$value"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) accent else secondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(-1, 1).forEach { delta ->
                        val enabled = values[quickKind] + delta in minimum..maximum
                        Surface(
                            onClick = { setValue(quickKind, values[quickKind] + delta) },
                            enabled = enabled,
                            modifier = Modifier.weight(1f).heightIn(min = 38.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = chip
                        ) {
                            Box(Modifier.padding(4.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    "${labels[quickKind].replace("横向间距", "横距").replace("纵向间距", "纵距")} ${if (delta < 0) "−1" else "+1"}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (enabled) accent else menuPalette?.disabled ?: secondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    input?.let { kind ->
        GridDimensionInput(kind, columnCount, iconSizeDp, gapDp, onColumns, onIconSize, onGap,
            onDismiss = { input = null }, maxColumns = maxColumns, allowAutoColumns = allowAutoColumns,
            rowGapDp = rowGapDp, onRowGap = onRowGap)
    }
}

/** Can be owned outside a dismissing menu so its input survives the menu closing. */
@Composable
internal fun GridDimensionInput(
    kind: Int,
    columnCount: Int,
    iconSizeDp: Int,
    gapDp: Int,
    onColumns: (Int) -> Unit,
    onIconSize: (Int) -> Unit,
    onGap: (Int) -> Unit,
    onDismiss: () -> Unit,
    maxColumns: Int = 12,
    allowAutoColumns: Boolean = true,
    rowGapDp: Int? = null,
    onRowGap: ((Int) -> Unit)? = null
) {
    when (kind) {
        0 -> ExactNumberInput(
            if (allowAutoColumns) "列数（0 为自动）" else "列数", columnCount,
            if (allowAutoColumns) 0 else 1, maxColumns, onColumns, onDismiss
        )
        1 -> ExactNumberInput("图标 dp（0 为自动）", iconSizeDp, 0, 192, onIconSize, onDismiss)
        2 -> ExactNumberInput(if (rowGapDp != null && onRowGap != null) "横向间距 dp" else "间距 dp", gapDp, MIN_GRID_GAP_DP, MAX_GRID_GAP_DP, onGap, onDismiss)
        3 -> if (rowGapDp != null && onRowGap != null) ExactNumberInput("纵向间距 dp", rowGapDp, MIN_GRID_GAP_DP, MAX_GRID_GAP_DP, onRowGap, onDismiss)
    }
}
