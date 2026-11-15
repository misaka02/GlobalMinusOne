package com.example.globalminusone.data

import kotlin.math.ceil
import kotlin.math.max

data class ContainerIconVerticalSpacing(val paddingDp: Int, val labelGapDp: Int)

fun containerRowGapDp(item: ComponentItem): Int =
    (if (item.containerRowGapDp == ROW_GAP_INHERIT) item.containerItemGapDp else item.containerRowGapDp)
        .coerceIn(MIN_GRID_GAP_DP, MAX_GRID_GAP_DP)

fun containerIconVerticalSpacing(item: ComponentItem): ContainerIconVerticalSpacing =
    ContainerIconVerticalSpacing(
        item.containerItemPaddingVerticalDp.coerceIn(0, 48),
        item.containerIconLabelGapDp.coerceIn(MIN_ICON_LABEL_GAP_DP, MAX_ICON_LABEL_GAP_DP)
    )

/** Display coordinates in dp. They must never replace a child's saved FREE geometry. */
data class ContainerGridChildBounds(
    val item: ComponentItem,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val iconRenderSizeDp: Int? = null,
    val col: Int = 0,
    val row: Int = 0,
    val spanCols: Int = 1,
    val spanRows: Int = 1
)

/** Keep requested icons at their real size; a narrow viewport scrolls instead of shrinking them. */
fun containerGridContentWidthDp(item: ComponentItem, viewportWidthDp: Float, columns: Int = item.containerColumns): Float {
    val slots = columns.coerceIn(1, 8)
    val icon = containerIconSizeDp(item)?.toFloat() ?: 0f
    val folderMargin = item.containerComponents.filter { it.type == ComponentType.FOLDER }
        .maxOfOrNull { (it.marginLeftDp + it.marginRightDp).toFloat() } ?: 0f
    val minimum = if (icon > 0f) slots * (icon + max(4f, folderMargin)) +
        (slots - 1) * item.containerItemGapDp.coerceIn(MIN_GRID_GAP_DP, MAX_GRID_GAP_DP) else 0f
    return max(viewportWidthDp.coerceAtLeast(1f), minimum.coerceAtLeast(1f))
}

/** GRID rendering and auto-height measurement share these exact dp bounds, without fine-grid gap rounding. */
fun containerGridChildBounds(children: List<ComponentItem>, columns: Int, widthDp: Float, stepYDp: Float,
    configuredHeightUnitDp: Int = 0, fontScale: Float = 1f, iconColumns: Int = 4,
    compactExpansion: Map<String, Float> = emptyMap(), itemGapDp: Int = 0, itemSizeDp: Int = 0,
    iconSizeDp: Int = 0, rowGapDp: Int = ROW_GAP_INHERIT, itemPaddingVerticalDp: Int = 0,
    iconLabelGapDp: Int = 2): List<ContainerGridChildBounds> {
    val count = columns.coerceIn(8, 64)
    val width = widthDp.coerceAtLeast(1f)
    val unitX = width / count
    val slots = iconColumns.coerceIn(1, 8)
    val gap = itemGapDp.coerceIn(MIN_GRID_GAP_DP, MAX_GRID_GAP_DP).toFloat()
    val rowGap = (if (rowGapDp == ROW_GAP_INHERIT) itemGapDp else rowGapDp)
        .coerceIn(MIN_GRID_GAP_DP, MAX_GRID_GAP_DP).toFloat()
    val padding = itemPaddingVerticalDp.coerceIn(0, 48).toFloat()
    val labelGap = iconLabelGapDp.coerceIn(MIN_ICON_LABEL_GAP_DP, MAX_ICON_LABEL_GAP_DP).toFloat()
    val scale = fontScale.takeIf { it.isFinite() && it > 0f } ?: 1f
    val cellWidth = ((width - gap * (slots - 1)) / slots).coerceAtLeast(1f)
    val requestedIcon = iconSizeDp.takeIf { it > 0 }?.coerceIn(1, 192)?.toFloat()
        ?: itemSizeDp.takeIf { it > 0 }?.let { (it * .45f).toInt().coerceIn(24, 96).toFloat() }
    fun iconHeight(child: ComponentItem): Float {
        val icon = requestedIcon ?: if (child.type == ComponentType.FOLDER) minOf(62f,
            (cellWidth - child.marginLeftDp - child.marginRightDp).coerceAtLeast(1f))
            else if (child.type == ComponentType.APP_ICON) 36f else 34f
        if (child.type == ComponentType.FOLDER) {
            val title = if (child.showTitle && child.title.isNotBlank()) max(24f, 20f * scale) + labelGap
                else if (!child.showTitle && child.title.isNotBlank()) 20f * scale + labelGap else 0f
            return (icon + title + child.marginTopDp + child.marginBottomDp + padding * 2).coerceAtLeast(24f)
        }
        val subtitle = if (child.type != ComponentType.APP_ICON && child.subtitle.isNotBlank()) 12f * scale else 0f
        return (icon + 14f * scale + subtitle + labelGap + padding * 2).coerceAtLeast(24f)
    }
    val legacySize = itemSizeDp.takeIf { it > 0 }?.coerceIn(48, 240)?.toFloat() ?: 0f
    val defaultRowHeight = maxOf(legacySize, ((requestedIcon ?: 36f) + 14f * scale + labelGap + padding * 2).coerceAtLeast(24f))

    data class MeasuredChild(
        val child: ComponentItem,
        val standardIcon: Boolean,
        val spanCols: Int,
        val spanRows: Int,
        val childWidth: Float,
        val baseHeight: Float,
        val explicitSpanHeight: Float
    )

    val measured = children.map { child ->
        val standardIcon = isContainerGridIconItem(child)
        val span = if (child.gridSpanX >= GRID_SPAN_FULL_ROW) slots else child.gridSpanX.coerceIn(0, slots)
        val tile = !standardIcon && span == 0 && isContainerGridTile(child.type) && child.w == child.type.defaultW
        val occupiedSlots = when {
            span > 0 -> span
            standardIcon || tile -> 1
            else -> ceil(child.w.coerceIn(1, count) * unitX / (cellWidth + gap).coerceAtLeast(8f)).toInt().coerceIn(1, slots)
        }
        val childWidth = when {
            span > 0 -> (cellWidth * span + gap * (span - 1)).coerceAtLeast(16f)
            standardIcon || tile -> if (itemSizeDp > 0 && iconSizeDp <= 0) minOf(cellWidth, legacySize) else cellWidth
            else -> child.w.coerceIn(1, count) * unitX
        }.coerceIn(1f, width)
        val height = if (standardIcon) {
            max(iconHeight(child), legacySize)
        } else {
            val logicalWidth = ceil(childWidth / unitX).toInt().coerceIn(1, count)
            componentDisplayHeightDp(child.copy(w = logicalWidth), childWidth, count, configuredHeightUnitDp,
                scale, compactExpansion = compactExpansion)
        }
        val singleRowHeight = if (standardIcon) height else defaultRowHeight
        val spanRows = child.gridSpanY.coerceIn(0, 12).let { if (it > 0) it else 1 }
        val spanHeight = if (child.gridSpanY > 0) singleRowHeight * spanRows + rowGap * (spanRows - 1) else 0f
        MeasuredChild(child, standardIcon, occupiedSlots.coerceIn(1, slots), spanRows, childWidth, height, spanHeight)
    }

    // Fast path: preserve exact sequential row-streaming coordinates when no child uses manual 2D cells
    if (children.none { it.gridCol >= 0 && it.gridRow >= 0 }) {
        var x = 0f; var y = 0f; var rowHeight = 0f; var slotsInRow = 0; var rowIdx = 0
        return measured.map { m ->
            val rawSpan = if (m.child.gridSpanX >= GRID_SPAN_FULL_ROW) slots else m.child.gridSpanX.coerceIn(0, slots)
            val isStdOrTile = m.standardIcon || (!m.standardIcon && rawSpan == 0 && isContainerGridTile(m.child.type) && m.child.w == m.child.type.defaultW)
            val occupied = when { rawSpan > 0 -> rawSpan; isStdOrTile -> 1; else -> 0 }
            if (x + m.childWidth > width + .01f || (occupied > 0 && slotsInRow + occupied > slots)) {
                x = 0f; y += (rowHeight + rowGap).coerceAtLeast(12f); rowHeight = 0f; slotsInRow = 0; rowIdx++
            }
            val placedCol = slotsInRow.coerceIn(0, (slots - m.spanCols).coerceAtLeast(0))
            val placedHeight = maxOf(m.baseHeight, m.explicitSpanHeight)
            ContainerGridChildBounds(
                item = m.child,
                x = x,
                y = y,
                width = m.childWidth,
                height = placedHeight,
                iconRenderSizeDp = if (m.standardIcon) requestedIcon?.toInt() else null,
                col = placedCol,
                row = rowIdx,
                spanCols = m.spanCols,
                spanRows = m.spanRows
            ).also {
                x += m.childWidth + gap
                rowHeight = max(rowHeight, it.height)
                slotsInRow += if (occupied > 0) occupied else m.spanCols
            }
        }
    }

    // Explicit 2D free cell placement inside GRID container
    val occupiedGrid = mutableListOf<BooleanArray>()
    fun ensureGridRows(target: Int) {
        while (occupiedGrid.size < target) occupiedGrid.add(BooleanArray(slots))
    }
    fun canPlace(col: Int, row: Int, sx: Int, sy: Int): Boolean {
        if (col < 0 || col + sx > slots || row < 0) return false
        ensureGridRows(row + sy)
        for (r in row until row + sy) {
            val bits = occupiedGrid[r]
            for (c in col until col + sx) if (bits[c]) return false
        }
        return true
    }
    fun markOccupied(col: Int, row: Int, sx: Int, sy: Int) {
        ensureGridRows(row + sy)
        for (r in row until row + sy) {
            val bits = occupiedGrid[r]
            for (c in col until col + sx) bits[c] = true
        }
    }

    val placements = arrayOfNulls<Pair<Int, Int>>(measured.size)
    // Pass 1: Pinned 2D coordinates first
    measured.forEachIndexed { index, m ->
        if (m.child.gridCol >= 0 && m.child.gridRow >= 0) {
            val prefCol = m.child.gridCol.coerceIn(0, (slots - m.spanCols).coerceAtLeast(0))
            val prefRow = m.child.gridRow.coerceAtLeast(0)
            var pCol = prefCol
            var pRow = prefRow
            if (!canPlace(prefCol, prefRow, m.spanCols, m.spanRows)) {
                var r = prefRow
                searchPinned@ while (true) {
                    for (c in prefCol..(slots - m.spanCols)) {
                        if (canPlace(c, r, m.spanCols, m.spanRows)) { pCol = c; pRow = r; break@searchPinned }
                    }
                    if (r == prefRow && prefCol > 0) {
                        for (c in 0 until prefCol) {
                            if (canPlace(c, r, m.spanCols, m.spanRows)) { pCol = c; pRow = r; break@searchPinned }
                        }
                    }
                    r++
                }
            }
            markOccupied(pCol, pRow, m.spanCols, m.spanRows)
            placements[index] = pCol to pRow
        }
    }
    // Pass 2: Auto-flow remaining children
    measured.forEachIndexed { index, m ->
        if (placements[index] == null) {
            var r = 0
            var pCol = 0
            var pRow = 0
            searchAuto@ while (true) {
                for (c in 0..(slots - m.spanCols)) {
                    if (canPlace(c, r, m.spanCols, m.spanRows)) { pCol = c; pRow = r; break@searchAuto }
                }
                r++
            }
            markOccupied(pCol, pRow, m.spanCols, m.spanRows)
            placements[index] = pCol to pRow
        }
    }

    val totalRows = placements.maxOfOrNullIndexed(measured) { idx, (_, r) -> r + measured[idx].spanRows } ?: 1
    val rowHeights = FloatArray(totalRows) { defaultRowHeight }
    measured.forEachIndexed { idx, m ->
        val (_, r) = placements[idx] ?: (0 to 0)
        if (m.spanRows == 1) {
            rowHeights[r] = max(rowHeights[r], maxOf(m.baseHeight, m.explicitSpanHeight))
        }
    }
    val rowTops = FloatArray(totalRows)
    for (r in 1 until totalRows) {
        rowTops[r] = rowTops[r - 1] + (rowHeights[r - 1] + rowGap).coerceAtLeast(12f)
    }

    return measured.mapIndexed { idx, m ->
        val (pCol, pRow) = placements[idx] ?: (0 to 0)
        val multiHeight = if (m.spanRows > 1) {
            var sum = 0f
            for (r in pRow until (pRow + m.spanRows).coerceAtMost(totalRows)) {
                sum += rowHeights[r]
            }
            maxOf(m.baseHeight, m.explicitSpanHeight, sum + rowGap * (m.spanRows - 1))
        } else {
            maxOf(m.baseHeight, m.explicitSpanHeight)
        }
        ContainerGridChildBounds(
            item = m.child,
            x = pCol * (cellWidth + gap).coerceAtLeast(8f),
            y = rowTops[pRow],
            width = m.childWidth,
            height = multiHeight,
            iconRenderSizeDp = if (m.standardIcon) requestedIcon?.toInt() else null,
            col = pCol,
            row = pRow,
            spanCols = m.spanCols,
            spanRows = m.spanRows
        )
    }
}

private inline fun <T> Array<T?>.maxOfOrNullIndexed(reference: List<*>, selector: (Int, T) -> Int): Int? {
    var best: Int? = null
    for (i in indices) {
        val v = this[i] ?: continue
        val score = selector(i, v)
        if (best == null || score > best) best = score
    }
    return best
}

/** Places a dragged child at an arbitrary 2D cell inside a GRID container/folder without collapsing empty cells. */
fun resolveContainerGridDropPlacements(
    children: List<ComponentItem>,
    columns: Int,
    widthDp: Float,
    stepYDp: Float,
    configuredHeightUnitDp: Int = 0,
    fontScale: Float = 1f,
    iconColumns: Int = 4,
    compactExpansion: Map<String, Float> = emptyMap(),
    itemGapDp: Int = 0,
    itemSizeDp: Int = 0,
    iconSizeDp: Int = 0,
    rowGapDp: Int = ROW_GAP_INHERIT,
    itemPaddingVerticalDp: Int = 0,
    iconLabelGapDp: Int = 2,
    movedId: String,
    targetCol: Int,
    targetRow: Int
): List<ComponentItem> {
    val current = containerGridChildBounds(
        children, columns, widthDp, stepYDp, configuredHeightUnitDp, fontScale,
        iconColumns, compactExpansion, itemGapDp, itemSizeDp, iconSizeDp,
        rowGapDp, itemPaddingVerticalDp, iconLabelGapDp
    )
    val byId = current.associateBy { it.item.id }
    val moving = byId[movedId] ?: return children
    val slots = iconColumns.coerceIn(1, 8)
    val destCol = targetCol.coerceIn(0, (slots - moving.spanCols).coerceAtLeast(0))
    val destRow = targetRow.coerceAtLeast(0)
    if (destCol == moving.col && destRow == moving.row &&
        moving.item.gridCol == destCol && moving.item.gridRow == destRow
    ) {
        return children
    }

    fun overlaps(c1: Int, r1: Int, sx1: Int, sy1: Int, c2: Int, r2: Int, sx2: Int, sy2: Int): Boolean {
        return c1 < c2 + sx2 && c1 + sx1 > c2 && r1 < r2 + sy2 && r1 + sy1 > r2
    }

    val victims = current.filter {
        it.item.id != movedId && overlaps(destCol, destRow, moving.spanCols, moving.spanRows, it.col, it.row, it.spanCols, it.spanRows)
    }
    val directSwapVictim = if (victims.size == 1 &&
        victims[0].spanCols == moving.spanCols &&
        victims[0].spanRows == moving.spanRows &&
        !overlaps(destCol, destRow, moving.spanCols, moving.spanRows, moving.col, moving.row, victims[0].spanCols, victims[0].spanRows) &&
        current.none { other ->
            other.item.id != movedId && other.item.id != victims[0].item.id &&
                overlaps(moving.col, moving.row, victims[0].spanCols, victims[0].spanRows, other.col, other.row, other.spanCols, other.spanRows)
        }
    ) victims[0] else null

    val pinned = children.map { child ->
        val slot = byId[child.id]
        when {
            child.id == movedId -> child.copy(gridCol = destCol, gridRow = destRow)
            directSwapVictim != null && child.id == directSwapVictim.item.id ->
                child.copy(gridCol = moving.col, gridRow = moving.row)
            slot != null -> child.copy(gridCol = slot.col, gridRow = slot.row)
            else -> child
        }
    }
    val prioritized = pinned.filter { it.id == movedId } + pinned.filter { it.id != movedId }
    val settled = containerGridChildBounds(
        prioritized, columns, widthDp, stepYDp, configuredHeightUnitDp, fontScale,
        iconColumns, compactExpansion, itemGapDp, itemSizeDp, iconSizeDp,
        rowGapDp, itemPaddingVerticalDp, iconLabelGapDp
    ).associate { it.item.id to (it.col to it.row) }
    return pinned.map { child ->
        val (c, r) = settled[child.id] ?: (child.gridCol to child.gridRow)
        child.copy(gridCol = c, gridRow = r)
    }
}
