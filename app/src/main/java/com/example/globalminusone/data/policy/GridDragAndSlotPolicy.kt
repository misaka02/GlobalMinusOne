package com.example.globalminusone.data.policy

import androidx.compose.runtime.Immutable
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ROW_GAP_INHERIT
import kotlin.math.abs
import kotlin.math.roundToInt

@Immutable
data class EmptyGridCell(
    val col: Int,
    val row: Int,
    val xDp: Float,
    val yDp: Float,
    val widthDp: Float,
    val heightDp: Float
)

@Immutable
class GridRowBands(
    val topsDp: FloatArray,
    val heightsDp: FloatArray,
    val fallbackHeightDp: Float,
    val rowGapDp: Float
) {
    fun topOf(row: Int): Float {
        if (row <= 0) return 0f
        if (row < topsDp.size) return topsDp[row]
        val lastIdx = (topsDp.size - 1).coerceAtLeast(0)
        val lastBottom = if (topsDp.isEmpty()) 0f else topsDp[lastIdx] + heightsDp[lastIdx] + rowGapDp
        val extra = (row - topsDp.size) * (fallbackHeightDp + rowGapDp)
        return lastBottom + extra
    }

    fun heightOf(row: Int): Float =
        if (row in heightsDp.indices) heightsDp[row] else fallbackHeightDp
}

fun computeGridRowBands(layoutResult: PageGridLayoutResult, visualRows: Int): GridRowBands {
    val rows = visualRows.coerceAtLeast(1)
    val metrics = layoutResult.metrics
    val pitchY = (metrics.cellHeightDp + metrics.rowGapDp).coerceAtLeast(8f)
    val rowStarts = FloatArray(rows) { Float.NaN }
    val singleRowHeights = FloatArray(rows) { metrics.cellHeightDp }
    for (p in layoutResult.placements) {
        if (p.row in 0 until rows) {
            if (rowStarts[p.row].isNaN() || p.yDp < rowStarts[p.row]) {
                rowStarts[p.row] = p.yDp
            }
            if (p.spanY == 1 && p.heightDp > singleRowHeights[p.row]) {
                singleRowHeights[p.row] = p.heightDp
            }
        }
    }
    for (r in 0 until rows) {
        if (rowStarts[r].isNaN()) {
            rowStarts[r] = if (r == 0) 0f else rowStarts[r - 1] + singleRowHeights[r - 1] + metrics.rowGapDp
        }
    }
    return GridRowBands(
        topsDp = rowStarts,
        heightsDp = singleRowHeights,
        fallbackHeightDp = metrics.cellHeightDp,
        rowGapDp = metrics.rowGapDp.toFloat().coerceAtLeast(0f)
    )
}

fun computeEmptyGridCells(layoutResult: PageGridLayoutResult, visualRows: Int): List<EmptyGridCell> {
    val cols = layoutResult.metrics.columns.coerceIn(1, 12)
    val rows = visualRows.coerceAtLeast(1)
    val occupied = Array(rows) { BooleanArray(cols) }
    for (p in layoutResult.placements) {
        val rEnd = (p.row + p.spanY).coerceAtMost(rows)
        val cEnd = (p.col + p.spanX).coerceAtMost(cols)
        for (r in p.row.coerceAtLeast(0) until rEnd) {
            for (c in p.col.coerceAtLeast(0) until cEnd) {
                occupied[r][c] = true
            }
        }
    }
    val pitchX = (layoutResult.metrics.cellWidthDp + layoutResult.metrics.itemGapDp).coerceAtLeast(8f)
    val bands = computeGridRowBands(layoutResult, rows)
    val result = ArrayList<EmptyGridCell>(rows * cols)
    for (r in 0 until rows) {
        val top = bands.topOf(r)
        val h = bands.heightOf(r)
        for (c in 0 until cols) {
            if (!occupied[r][c]) {
                result += EmptyGridCell(
                    col = c,
                    row = r,
                    xDp = c * pitchX,
                    yDp = top,
                    widthDp = layoutResult.metrics.cellWidthDp,
                    heightDp = h
                )
            }
        }
    }
    return result
}

fun resolveGridDropCellWithHysteresis(
    xDp: Float,
    yDp: Float,
    spanX: Int,
    metrics: PageGridMetrics,
    rowBands: GridRowBands,
    previousCell: Pair<Int, Int>?,
    deadbandFraction: Float = 0.18f,
    maxRow: Int = 64
): Pair<Int, Int> {
    val pitchX = (metrics.cellWidthDp + metrics.itemGapDp).coerceAtLeast(16f)
    val maxCol = (metrics.columns - spanX.coerceAtLeast(1)).coerceAtLeast(0)
    val rawColFrac = if (xDp.isFinite()) xDp / pitchX else 0f
    val col = if (previousCell == null || abs(rawColFrac - previousCell.first) >= 0.5f + deadbandFraction) {
        rawColFrac.roundToInt()
    } else {
        previousCell.first
    }.coerceIn(0, maxCol)

    val safeY = if (yDp.isFinite()) yDp.coerceAtLeast(0f) else 0f
    val scanLimit = maxOf(rowBands.topsDp.size + 4, (previousCell?.second ?: 0) + 4).coerceAtMost(maxRow + 1)
    var bestRow = 0
    var bestDist = Float.MAX_VALUE
    for (r in 0 until scanLimit) {
        val dist = abs(safeY - rowBands.topOf(r))
        if (dist < bestDist) {
            bestDist = dist
            bestRow = r
        }
    }
    val row = if (previousCell != null && bestRow != previousCell.second) {
        val prevTop = rowBands.topOf(previousCell.second)
        val prevPitch = (rowBands.heightOf(previousCell.second) + rowBands.rowGapDp).coerceAtLeast(16f)
        if (abs(safeY - prevTop) < prevPitch * (0.5f + deadbandFraction)) previousCell.second else bestRow
    } else {
        bestRow
    }.coerceIn(0, maxRow)

    return col to row
}

/**
 * Smart displacement when dropping [movedId] onto occupied unequal-span or multi-victim cells:
 * prioritizes vacated origin slots before falling back to nearest free grid cells.
 */
internal fun resolveUnequalGridDisplacement(
    components: List<ComponentItem>,
    placementsById: Map<String, PageGridPlacement>,
    moving: PageGridPlacement,
    victims: List<PageGridPlacement>,
    cols: Int,
    destCol: Int,
    destRow: Int,
    availableWidthDp: Float,
    itemSizeDp: Int,
    itemGapDp: Int,
    gridColumns: Int,
    configuredHeightUnitDp: Int,
    fontScale: Float,
    compactExpansion: Map<String, Float>,
    columnCount: Int,
    iconSizeDp: Int,
    rowGapDp: Int
): List<ComponentItem> {
    val occupied = mutableListOf<BooleanArray>()
    fun ensureRows(rowsNeeded: Int) {
        while (occupied.size < rowsNeeded) occupied.add(BooleanArray(cols))
    }
    fun canPlace(col: Int, row: Int, sx: Int, sy: Int): Boolean {
        if (col < 0 || col + sx > cols || row < 0) return false
        ensureRows(row + sy)
        for (r in row until row + sy) for (c in col until col + sx) if (occupied[r][c]) return false
        return true
    }
    fun mark(col: Int, row: Int, sx: Int, sy: Int) {
        ensureRows(row + sy)
        for (r in row until row + sy) for (c in col until col + sx) occupied[r][c] = true
    }

    val victimIds = victims.mapTo(HashSet()) { it.id }
    val resolvedCoords = HashMap<String, Pair<Int, Int>>(placementsById.size)
    mark(destCol, destRow, moving.spanX, moving.spanY)
    resolvedCoords[moving.id] = destCol to destRow

    for ((id, slot) in placementsById) {
        if (id != moving.id && id !in victimIds) {
            mark(slot.col, slot.row, slot.spanX, slot.spanY)
            resolvedCoords[id] = slot.col to slot.row
        }
    }

    val origColEnd = moving.col + moving.spanX
    val origRowEnd = moving.row + moving.spanY
    for (victim in victims.sortedWith(compareBy({ it.row }, { it.col }))) {
        val searchRows = maxOf(occupied.size, origRowEnd, destRow + moving.spanY) + victim.spanY + 8
        var bestCol = 0
        var bestRow = occupied.size
        var bestScore = Long.MAX_VALUE
        for (r in 0 until searchRows) {
            for (c in 0..(cols - victim.spanX).coerceAtLeast(0)) {
                if (!canPlace(c, r, victim.spanX, victim.spanY)) continue
                val fitsOrigin = c >= moving.col && c + victim.spanX <= origColEnd &&
                    r >= moving.row && r + victim.spanY <= origRowEnd
                val dRow = abs(r - victim.row)
                val dCol = abs(c - victim.col)
                val score = (if (fitsOrigin) 0L else 1_000_000L) + dRow * 100L + dCol * 10L + r
                if (score < bestScore) {
                    bestScore = score
                    bestCol = c
                    bestRow = r
                }
            }
        }
        mark(bestCol, bestRow, victim.spanX, victim.spanY)
        resolvedCoords[victim.id] = bestCol to bestRow
    }

    val pinned = components.map { item ->
        val (c, r) = resolvedCoords[item.id] ?: (item.gridCol to item.gridRow)
        item.copy(gridCol = c, gridRow = r)
    }
    val prioritized = pinned.filter { it.id == moving.id } + pinned.filter { it.id != moving.id }
    val settled = arrangePageGridComponents(
        components = prioritized,
        availableWidthDp = availableWidthDp,
        itemSizeDp = itemSizeDp,
        itemGapDp = itemGapDp,
        gridColumns = gridColumns,
        configuredHeightUnitDp = configuredHeightUnitDp,
        fontScale = fontScale,
        compactExpansion = compactExpansion,
        columnCount = columnCount,
        iconSizeDp = iconSizeDp,
        fillGaps = true,
        rowGapDp = rowGapDp
    )
    val finalCoords = settled.placements.associate { it.id to (it.col to it.row) }
    return pinned.map { item ->
        val (c, r) = finalCoords[item.id] ?: (item.gridCol to item.gridRow)
        item.copy(gridCol = c, gridRow = r)
    }
}

/**
 * Resolves vertical reorder during linear-page dragging with a midpoint deadband
 * so uneven card heights never oscillate between adjacent slots.
 */
fun resolveLinearDragOrder(
    order: List<String>,
    movingId: String,
    draggedCenterY: Float,
    starts: Map<String, Float>,
    heights: Map<String, Float>,
    hysteresisFraction: Float = 0.15f
): List<String> {
    val currentIndex = order.indexOf(movingId)
    if (currentIndex < 0) return order
    val deadband = hysteresisFraction.coerceIn(0f, 0.35f)
    var targetIndex = currentIndex

    for (i in currentIndex + 1 until order.size) {
        val otherId = order[i]
        val top = starts[otherId] ?: continue
        val h = (heights[otherId] ?: 1f).coerceAtLeast(1f)
        if (draggedCenterY > top + h * (0.5f + deadband)) {
            targetIndex = i
        } else {
            break
        }
    }
    if (targetIndex == currentIndex) {
        for (i in currentIndex - 1 downTo 0) {
            val otherId = order[i]
            val top = starts[otherId] ?: continue
            val h = (heights[otherId] ?: 1f).coerceAtLeast(1f)
            if (draggedCenterY < top + h * (0.5f - deadband)) {
                targetIndex = i
            } else {
                break
            }
        }
    }
    if (targetIndex == currentIndex) return order
    return order.toMutableList().apply { add(targetIndex, removeAt(currentIndex)) }
}
