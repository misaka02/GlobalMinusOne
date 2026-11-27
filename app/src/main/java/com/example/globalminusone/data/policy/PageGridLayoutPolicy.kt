package com.example.globalminusone.data.policy

import androidx.compose.runtime.Immutable
import kotlin.math.ceil
import kotlin.math.roundToInt
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.MAX_GRID_GAP_DP
import com.example.globalminusone.data.MIN_GRID_GAP_DP
import com.example.globalminusone.data.ROW_GAP_INHERIT

/** Sentinel value meaning "always span all currently available columns on this grid". */
const val GRID_SPAN_FULL_ROW = 99

@Immutable
data class PageGridMetrics(
    val availableWidthDp: Float,
    val itemSizeDp: Int,
    val itemGapDp: Int,
    val columns: Int,
    val cellWidthDp: Float,
    val cellHeightDp: Float,
    val iconRenderSizeDp: Int,
    val rowGapDp: Int = itemGapDp
)

@Immutable
data class PageGridPlacement(
    val id: String,
    val item: ComponentItem,
    val col: Int,
    val row: Int,
    val spanX: Int,
    val spanY: Int,
    val xDp: Float,
    val yDp: Float,
    val widthDp: Float,
    val heightDp: Float
)

@Immutable
data class PageGridLayoutResult(
    val metrics: PageGridMetrics,
    val placements: List<PageGridPlacement>,
    val totalRows: Int,
    val contentHeightDp: Float
)

/**
 * Fixed columns remain authoritative; automatic columns retain legacy cell-size behaviour.
 * Requested spacing and icon size are bounded by the actual cell so narrow panels never overflow.
 */
fun derivePageGridMetrics(
    availableWidthDp: Float,
    itemSizeDp: Int = 58,
    itemGapDp: Int = 8,
    fontScale: Float = 1f,
    columnCount: Int = 0,
    iconSizeDp: Int = 0,
    rowGapDp: Int = ROW_GAP_INHERIT
): PageGridMetrics {
    val safeScale = fontScale.takeIf { it.isFinite() && it > 0f } ?: 1f
    val width = availableWidthDp.takeIf { it.isFinite() && it > 0f }?.coerceAtLeast(64f) ?: 268f
    val size = itemSizeDp.coerceIn(44, 160)
    val requestedGap = itemGapDp.coerceIn(MIN_GRID_GAP_DP, MAX_GRID_GAP_DP)
    val preferredSize = if (iconSizeDp > 0) maxOf(44f, iconSizeDp.coerceAtMost(192) / .68f + 4f) else size.toFloat()
    val columns = if (columnCount > 0) columnCount.coerceIn(1, 12)
        else ((width + requestedGap) / (preferredSize + requestedGap).coerceAtLeast(16f)).toInt().coerceIn(1, 12)
    val minCell = minOf(24f, width / columns)
    val maxGap = if (columns == 1) MAX_GRID_GAP_DP else ((width - columns * minCell) / (columns - 1)).toInt().coerceAtLeast(0)
    val gap = minOf(requestedGap, maxGap)
    val cellWidth = ((width - gap * (columns - 1)) / columns).coerceAtLeast(1f)
    val requestedIcon = if (iconSizeDp > 0) iconSizeDp.coerceIn(1, 192) else (size * .54f).toInt().coerceIn(24, 84)
    val iconSize = minOf(requestedIcon, (cellWidth - 4f).toInt().coerceAtLeast(1))
    val minLabelCellHeight = iconSize + 18f * safeScale + 12f
    val cellHeight = if (iconSizeDp > 0) minLabelCellHeight else maxOf(size.toFloat(), minLabelCellHeight)
    return PageGridMetrics(
        availableWidthDp = width,
        itemSizeDp = size,
        itemGapDp = gap,
        columns = columns,
        cellWidthDp = cellWidth,
        cellHeightDp = cellHeight,
        iconRenderSizeDp = iconSize,
        rowGapDp = if (rowGapDp == ROW_GAP_INHERIT) gap else rowGapDp.coerceIn(MIN_GRID_GAP_DP, MAX_GRID_GAP_DP)
    )
}

@Immutable
data class FittedPageGridStyle(val columnCount: Int, val itemSizeDp: Int, val itemGapDp: Int, val iconSizeDp: Int)

/** Choosing a column count fits both spacing and actual icon dp once, using the current width. */
fun fitPageGridColumns(availableWidthDp: Float, columns: Int): FittedPageGridStyle {
    val width = availableWidthDp.takeIf { it.isFinite() && it > 0f }?.coerceAtLeast(64f) ?: 268f
    val count = columns.coerceIn(1, 12)
    val gap = (width / count * .08f).roundToInt().coerceIn(0, 32)
    val cell = ((width - gap * (count - 1)) / count).coerceAtLeast(1f)
    return FittedPageGridStyle(count, cell.roundToInt().coerceIn(44, 160), gap,
        (cell * .68f).roundToInt().coerceIn(1, 192))
}

fun defaultPageGridSpan(type: ComponentType, totalColumns: Int): Pair<Int, Int> {
    val cols = totalColumns.coerceIn(1, 12)
    if (isContainerGridIcon(type) || isContainerGridTile(type)) {
        return 1 to 1
    }
    return when (type) {
        ComponentType.STAT_BATTERY_BAR,
        ComponentType.STAT_RAM,
        ComponentType.STAT_ROM,
        ComponentType.STAT_UPTIME,
        ComponentType.STAT_SCREEN_TIME,
        ComponentType.STAT_APP_COUNT,
        ComponentType.STAT_NOTIF_COUNT,
        ComponentType.STAT_DATA_TRAFFIC,
        ComponentType.STAT_UNLOCK_COUNT,
        ComponentType.FILE_SHORTCUT -> minOf(2, cols) to 1

        ComponentType.SINGLE_IMAGE,
        ComponentType.PHOTO_CUBE -> minOf(2, cols) to 2

        ComponentType.BRIGHTNESS_BAR,
        ComponentType.VOLUME_BAR,
        ComponentType.OPERATION_BAR,
        ComponentType.WEBSITE_LINK,
        ComponentType.HITOKOTO_CARD,
        ComponentType.TEXT_LABEL -> cols to 1

        ComponentType.APP_DRAWER_CARD,
        ComponentType.APP_KEYBOARD_LAUNCHER -> cols to 4

        ComponentType.APP_WIDGET,
        ComponentType.CONTAINER_BOX,
        ComponentType.NOTE_CARD,
        ComponentType.CLIPBOARD_CARD,
        ComponentType.STAT_BATTERY_DETAIL,
        ComponentType.RECENT_APPS_CARD,
        ComponentType.FILE_BROWSER -> cols to 2

        ComponentType.SUB_PANEL_ENTRY -> 1 to 1

        else -> minOf(2, cols) to 1
    }
}

fun resolvePageGridSpan(
    item: ComponentItem,
    metrics: PageGridMetrics,
    gridColumns: Int = 32,
    configuredHeightUnitDp: Int = 0,
    fontScale: Float = 1f,
    compactExpansion: Map<String, Float> = emptyMap()
): Pair<Int, Int> {
    val cols = metrics.columns.coerceIn(1, 12)
    val (defX, defY) = if (isSingleIconLauncherItem(item)) {
        1 to 1
    } else {
        defaultGridSpanForOrientation(item.type, item.orientation)
            ?: defaultPageGridSpan(item.type, cols)
    }
    val spanX = when {
        item.gridSpanX >= GRID_SPAN_FULL_ROW -> cols
        item.gridSpanX > 0 -> item.gridSpanX.coerceIn(1, cols)
        else -> defX.coerceIn(1, cols)
    }
    val slotWidthDp = (spanX * metrics.cellWidthDp + (spanX - 1) * metrics.itemGapDp).coerceAtLeast(24f)
    val spanY = when {
        item.gridSpanY > 0 && !(item.type == ComponentType.CONTAINER_BOX && item.containerCompact) ->
            item.gridSpanY.coerceIn(1, 12)
        (item.type == ComponentType.CONTAINER_BOX && (item.autoHeight || item.containerCompact)) ||
            (item.type == ComponentType.TEXT_LABEL && item.autoHeight) -> {
            val neededDp = componentDisplayHeightDp(
                item = item,
                outerWidthDp = slotWidthDp,
                gridColumns = gridColumns,
                configuredHeightUnitDp = configuredHeightUnitDp,
                fontScale = fontScale,
                compactExpansion = compactExpansion
            )
            val pitchY = (metrics.cellHeightDp + metrics.rowGapDp).coerceAtLeast(16f)
            ceil(((neededDp + metrics.rowGapDp).coerceAtLeast(1f)) / pitchY).toInt().coerceIn(1, 12)
        }
        else -> defY.coerceIn(1, 12)
    }
    return spanX to spanY
}

/**
 * Places components onto a 2D occupancy grid using first-fit bin packing.
 * Small 1x1 cards fill slots beside taller 2x2 widgets/cards without any overlap.
 */
fun arrangePageGridComponents(
    components: List<ComponentItem>,
    availableWidthDp: Float,
    itemSizeDp: Int = 58,
    itemGapDp: Int = 8,
    gridColumns: Int = 32,
    configuredHeightUnitDp: Int = 0,
    fontScale: Float = 1f,
    compactExpansion: Map<String, Float> = emptyMap(),
    spanOverrides: Map<String, Pair<Int, Int>> = emptyMap(),
    columnCount: Int = 0,
    iconSizeDp: Int = 0,
    fillGaps: Boolean = true,
    rowGapDp: Int = ROW_GAP_INHERIT
): PageGridLayoutResult {
    val metrics = derivePageGridMetrics(availableWidthDp, itemSizeDp, itemGapDp, fontScale, columnCount, iconSizeDp, rowGapDp)
    val cols = metrics.columns
    val occupied = mutableListOf<BooleanArray>()

    fun ensureRows(targetRows: Int) {
        while (occupied.size < targetRows) {
            occupied.add(BooleanArray(cols))
        }
    }

    fun canPlace(col: Int, row: Int, spanX: Int, spanY: Int): Boolean {
        if (col < 0 || col + spanX > cols || row < 0) return false
        ensureRows(row + spanY)
        for (r in row until row + spanY) {
            val rowBits = occupied[r]
            for (c in col until col + spanX) {
                if (rowBits[c]) return false
            }
        }
        return true
    }

    fun markOccupied(col: Int, row: Int, spanX: Int, spanY: Int) {
        ensureRows(row + spanY)
        for (r in row until row + spanY) {
            val rowBits = occupied[r]
            for (c in col until col + spanX) {
                rowBits[c] = true
            }
        }
    }

    val spans = components.map { item ->
        val rawSpan = spanOverrides[item.id] ?: resolvePageGridSpan(
            item = item,
            metrics = metrics,
            gridColumns = gridColumns,
            configuredHeightUnitDp = configuredHeightUnitDp,
            fontScale = fontScale,
            compactExpansion = compactExpansion
        )
        rawSpan.first.coerceIn(1, cols) to rawSpan.second.coerceIn(1, 12)
    }

    val placementCoords = arrayOfNulls<Pair<Int, Int>>(components.size)

    // Pass 1: Reserve explicitly pinned 2D grid cells (free placement anywhere on the grid)
    components.forEachIndexed { index, item ->
        if (item.gridCol >= 0 && item.gridRow >= 0) {
            val (spanX, spanY) = spans[index]
            val prefCol = item.gridCol.coerceIn(0, (cols - spanX).coerceAtLeast(0))
            val prefRow = item.gridRow.coerceAtLeast(0)
            var placedCol = prefCol
            var placedRow = prefRow
            if (!canPlace(prefCol, prefRow, spanX, spanY)) {
                var r = prefRow
                findPinned@ while (true) {
                    for (c in prefCol..(cols - spanX)) {
                        if (canPlace(c, r, spanX, spanY)) {
                            placedCol = c
                            placedRow = r
                            break@findPinned
                        }
                    }
                    if (r == prefRow && prefCol > 0) {
                        for (c in 0 until prefCol) {
                            if (canPlace(c, r, spanX, spanY)) {
                                placedCol = c
                                placedRow = r
                                break@findPinned
                            }
                        }
                    }
                    r++
                }
            }
            markOccupied(placedCol, placedRow, spanX, spanY)
            placementCoords[index] = placedCol to placedRow
        }
    }

    // Pass 2: Auto-flow remaining unpinned cards into remaining empty cells
    var maxBottomRow = 0
    var orderedCursor = 0
    components.forEachIndexed { index, _ ->
        if (placementCoords[index] == null) {
            val (spanX, spanY) = spans[index]
            var placedCol = 0
            var placedRow = 0
            var rowCursor = if (fillGaps) 0 else orderedCursor / cols
            search@ while (true) {
                val firstCol = if (!fillGaps && rowCursor == orderedCursor / cols) orderedCursor % cols else 0
                for (c in firstCol..(cols - spanX)) {
                    if (canPlace(c, rowCursor, spanX, spanY)) {
                        placedCol = c
                        placedRow = rowCursor
                        break@search
                    }
                }
                rowCursor++
            }
            markOccupied(placedCol, placedRow, spanX, spanY)
            orderedCursor = placedRow * cols + placedCol + spanX
            placementCoords[index] = placedCol to placedRow
        }
    }

    val placements = ArrayList<PageGridPlacement>(components.size)
    val pitchX = (metrics.cellWidthDp + metrics.itemGapDp).coerceAtLeast(8f)
    val pitchY = (metrics.cellHeightDp + metrics.rowGapDp).coerceAtLeast(8f)

    fun isVariableHeightContainer(item: ComponentItem): Boolean =
        item.type == ComponentType.CONTAINER_BOX && (item.containerCompact || (item.autoHeight && item.gridSpanY <= 0))

    val itemWidthsDp = FloatArray(components.size)
    val itemHeightsDp = FloatArray(components.size)
    val variableFlags = BooleanArray(components.size)
    components.forEachIndexed { index, item ->
        val (_, placedRow) = placementCoords[index] ?: (0 to 0)
        val (spanX, spanY) = spans[index]
        maxBottomRow = maxOf(maxBottomRow, placedRow + spanY)
        val wDp = (spanX * metrics.cellWidthDp + (spanX - 1) * metrics.itemGapDp).coerceAtLeast(16f)
        val isVar = isVariableHeightContainer(item)
        variableFlags[index] = isVar
        itemWidthsDp[index] = wDp
        itemHeightsDp[index] = if (isVar) {
            componentDisplayHeightDp(
                item = item,
                outerWidthDp = wDp,
                gridColumns = gridColumns,
                configuredHeightUnitDp = configuredHeightUnitDp,
                fontScale = fontScale,
                compactExpansion = compactExpansion
            ).coerceAtLeast(36f)
        } else {
            (spanY * metrics.cellHeightDp + (spanY - 1) * metrics.rowGapDp).coerceAtLeast(16f)
        }
    }

    val rowHasRegular = BooleanArray(maxBottomRow.coerceAtLeast(1))
    val finishingByEndRow = Array(maxBottomRow + 1) { mutableListOf<Int>() }
    components.forEachIndexed { index, _ ->
        val (_, placedRow) = placementCoords[index] ?: (0 to 0)
        val (_, spanY) = spans[index]
        val endRow = (placedRow + spanY).coerceAtMost(maxBottomRow)
        finishingByEndRow[endRow].add(index)
        if (!variableFlags[index]) {
            for (r in placedRow until endRow) {
                rowHasRegular[r] = true
            }
        }
    }

    val rowStartY = FloatArray(maxBottomRow + 1)
    for (r in 0 until maxBottomRow) {
        val prevY = rowStartY[r]
        val rowEmpty = r >= occupied.size || occupied[r].none { it }
        var nextY = if (rowHasRegular[r] || rowEmpty) {
            prevY + metrics.cellHeightDp + metrics.rowGapDp
        } else {
            prevY
        }
        var hasFinishingHere = false
        for (idx in finishingByEndRow[r + 1]) {
            val (_, startRow) = placementCoords[idx] ?: (0 to 0)
            val bottomWithGap = rowStartY[startRow] + itemHeightsDp[idx] + metrics.rowGapDp
            if (bottomWithGap > nextY) nextY = bottomWithGap
            hasFinishingHere = true
        }
        if (!rowHasRegular[r] && !rowEmpty && !hasFinishingHere) {
            // Intermediate row inside a multi-row span before the item finishes: keep grid rhythm for any later startRow.
            nextY = maxOf(nextY, prevY + metrics.cellHeightDp + metrics.rowGapDp)
        }
        rowStartY[r + 1] = nextY
    }

    var maxContentBottomDp = 0f
    components.forEachIndexed { index, item ->
        val (placedCol, placedRow) = placementCoords[index] ?: (0 to 0)
        val (spanX, spanY) = spans[index]
        val xDp = placedCol * pitchX
        val yDp = rowStartY[placedRow]
        val widthDp = itemWidthsDp[index]
        val heightDp = itemHeightsDp[index]
        maxContentBottomDp = maxOf(maxContentBottomDp, yDp + heightDp)

        placements += PageGridPlacement(
            id = item.id,
            item = item,
            col = placedCol,
            row = placedRow,
            spanX = spanX,
            spanY = spanY,
            xDp = xDp,
            yDp = yDp,
            widthDp = widthDp,
            heightDp = heightDp
        )
    }

    val contentHeightDp = if (maxBottomRow <= 0) {
        metrics.cellHeightDp
    } else {
        val emptyTrailFloor = if (rowHasRegular[maxBottomRow - 1] || occupied.getOrNull(maxBottomRow - 1)?.none { it } == true) {
            (rowStartY[maxBottomRow] - metrics.rowGapDp).coerceAtLeast(metrics.cellHeightDp)
        } else {
            metrics.cellHeightDp
        }
        maxOf(metrics.cellHeightDp, maxContentBottomDp, emptyTrailFloor)
    }

    return PageGridLayoutResult(
        metrics = metrics,
        placements = placements,
        totalRows = maxBottomRow,
        contentHeightDp = contentHeightDp
    )
}

/**
 * Places [movedId] at any arbitrary 2D cell ([targetCol], [targetRow]) on a Grid page.
 * Dropping onto an empty cell leaves all other cards at their exact cells (preserving gaps).
 * Dropping directly onto another equal-span card swaps their cells; otherwise overlapped cards nudge to nearest free cells.
 */
fun resolvePageGridDropPlacements(
    components: List<ComponentItem>,
    availableWidthDp: Float,
    itemSizeDp: Int = 58,
    itemGapDp: Int = 8,
    gridColumns: Int = 32,
    configuredHeightUnitDp: Int = 0,
    fontScale: Float = 1f,
    compactExpansion: Map<String, Float> = emptyMap(),
    columnCount: Int = 0,
    iconSizeDp: Int = 0,
    fillGaps: Boolean = true,
    rowGapDp: Int = ROW_GAP_INHERIT,
    movedId: String,
    targetCol: Int,
    targetRow: Int
): List<ComponentItem> {
    val current = arrangePageGridComponents(
        components = components,
        availableWidthDp = availableWidthDp,
        itemSizeDp = itemSizeDp,
        itemGapDp = itemGapDp,
        gridColumns = gridColumns,
        configuredHeightUnitDp = configuredHeightUnitDp,
        fontScale = fontScale,
        compactExpansion = compactExpansion,
        columnCount = columnCount,
        iconSizeDp = iconSizeDp,
        fillGaps = fillGaps,
        rowGapDp = rowGapDp
    )
    val byId = current.placements.associateBy { it.id }
    val moving = byId[movedId] ?: return components
    val cols = current.metrics.columns
    val destCol = targetCol.coerceIn(0, (cols - moving.spanX).coerceAtLeast(0))
    val destRow = targetRow.coerceAtLeast(0)

    if (destCol == moving.col && destRow == moving.row &&
        moving.item.gridCol == destCol && moving.item.gridRow == destRow
    ) {
        return components
    }

    fun overlaps(c1: Int, r1: Int, sx1: Int, sy1: Int, c2: Int, r2: Int, sx2: Int, sy2: Int): Boolean {
        return c1 < c2 + sx2 && c1 + sx1 > c2 && r1 < r2 + sy2 && r1 + sy1 > r2
    }

    val victims = current.placements.filter {
        it.id != movedId && overlaps(destCol, destRow, moving.spanX, moving.spanY, it.col, it.row, it.spanX, it.spanY)
    }

    // Check clean 1-to-1 swap when dropping onto a single equal-sized card
    val directSwapVictim = if (victims.size == 1 &&
        victims[0].spanX == moving.spanX &&
        victims[0].spanY == moving.spanY &&
        !overlaps(destCol, destRow, moving.spanX, moving.spanY, moving.col, moving.row, victims[0].spanX, victims[0].spanY) &&
        current.placements.none { other ->
            other.id != movedId && other.id != victims[0].id &&
                overlaps(moving.col, moving.row, victims[0].spanX, victims[0].spanY, other.col, other.row, other.spanX, other.spanY)
        }
    ) victims[0] else null

    if (victims.isNotEmpty() && directSwapVictim == null) {
        return resolveUnequalGridDisplacement(
            components, byId, moving, victims, cols, destCol, destRow,
            availableWidthDp, itemSizeDp, itemGapDp, gridColumns, configuredHeightUnitDp,
            fontScale, compactExpansion, columnCount, iconSizeDp, rowGapDp
        )
    }

    // Pin all items at their resolved/desired cells, giving priority to the moved card first
    val pinned = components.map { item ->
        val slot = byId[item.id]
        when {
            item.id == movedId -> item.copy(gridCol = destCol, gridRow = destRow)
            directSwapVictim != null && item.id == directSwapVictim.id ->
                item.copy(gridCol = moving.col, gridRow = moving.row)
            slot != null -> item.copy(gridCol = slot.col, gridRow = slot.row)
            else -> item
        }
    }

    val prioritized = pinned.filter { it.id == movedId } + pinned.filter { it.id != movedId }
    val settled = arrangePageGridComponents(
        prioritized, availableWidthDp, itemSizeDp, itemGapDp, gridColumns,
        configuredHeightUnitDp, fontScale, compactExpansion, columnCount = columnCount,
        iconSizeDp = iconSizeDp, fillGaps = true, rowGapDp = rowGapDp
    )
    val settledCoords = settled.placements.associate { it.id to (it.col to it.row) }
    return pinned.map { item ->
        val (c, r) = settledCoords[item.id] ?: (item.gridCol to item.gridRow)
        item.copy(gridCol = c, gridRow = r)
    }
}

fun resizedPageGridSpan(
    startSpanX: Int, startSpanY: Int, dxPx: Float, dyPx: Float,
    pitchXPx: Float, pitchYPx: Float, maxColumns: Int, maxRows: Int = 10
): Pair<Int, Int> {
    val safeCols = maxColumns.coerceIn(1, 12)
    val safeRows = maxRows.coerceIn(1, 12)
    val deltaCols = if (dxPx.isFinite()) (dxPx / pitchXPx.coerceAtLeast(8f)).roundToInt() else 0
    val deltaRows = if (dyPx.isFinite()) (dyPx / pitchYPx.coerceAtLeast(8f)).roundToInt() else 0
    return (startSpanX + deltaCols).coerceIn(1, safeCols) to (startSpanY + deltaRows).coerceIn(1, safeRows)
}

/** Moves [movedId] by ([deltaCol], [deltaRow]) cells inside a GRID page using true 2D coordinates. */
fun nudgePageGridComponent(
    components: List<ComponentItem>, availableWidthDp: Float, itemSizeDp: Int = 58,
    itemGapDp: Int = 8, gridColumns: Int = 32, configuredHeightUnitDp: Int = 0,
    fontScale: Float = 1f, compactExpansion: Map<String, Float> = emptyMap(),
    columnCount: Int = 0, iconSizeDp: Int = 0, fillGaps: Boolean = true,
    rowGapDp: Int = ROW_GAP_INHERIT, movedId: String, deltaCol: Int, deltaRow: Int
): List<ComponentItem> {
    val current = arrangePageGridComponents(
        components, availableWidthDp, itemSizeDp, itemGapDp, gridColumns,
        configuredHeightUnitDp, fontScale, compactExpansion, columnCount = columnCount,
        iconSizeDp = iconSizeDp, fillGaps = fillGaps, rowGapDp = rowGapDp
    )
    val slot = current.placements.firstOrNull { it.id == movedId } ?: return components
    val maxCol = (current.metrics.columns - slot.spanX).coerceAtLeast(0)
    val targetCol = (slot.col + deltaCol).coerceIn(0, maxCol)
    val targetRow = (slot.row + deltaRow).coerceAtLeast(0)
    return resolvePageGridDropPlacements(
        components, availableWidthDp, itemSizeDp, itemGapDp, gridColumns,
        configuredHeightUnitDp, fontScale, compactExpansion, columnCount,
        iconSizeDp, fillGaps, rowGapDp, movedId, targetCol, targetRow
    )
}

/**
 * Resolves how many grid rows should be rendered.
 * When [autoRows] is enabled, eliminates empty trailing rows statically and expands by 1 only
 * while actively dragging so cards have a drop target without permanent blank rows.
 */
fun resolveVisibleGridRows(
    occupiedRows: Int,
    configuredRowCount: Int,
    autoRows: Boolean,
    isDragging: Boolean,
    previewDropRow: Int? = null
): Int {
    val baseOccupied = occupiedRows.coerceAtLeast(1)
    val dropFloor = if (isDragging) (previewDropRow ?: 0).coerceAtLeast(0) + 2 else 0
    return if (autoRows) {
        if (isDragging) maxOf(baseOccupied + 1, dropFloor) else baseOccupied
    } else {
        val manual = configuredRowCount.coerceIn(1, 24)
        maxOf(baseOccupied, manual, dropFloor)
    }
}
