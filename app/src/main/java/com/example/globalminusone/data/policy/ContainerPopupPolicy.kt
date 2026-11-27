package com.example.globalminusone.data.policy

import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.ContainerLayoutType
import com.example.globalminusone.data.MAX_GRID_GAP_DP
import com.example.globalminusone.data.MIN_GRID_GAP_DP

private const val WIDE_CARD_REFERENCE_POPUP_WIDTH_DP = 360f
private const val POPUP_HORIZONTAL_CHROME_DP = 24f
private const val WIDE_CARD_REFERENCE_INNER_DP = WIDE_CARD_REFERENCE_POPUP_WIDTH_DP - POPUP_HORIZONTAL_CHROME_DP
private const val SHRUNK_WIDE_MIN_POPUP_WIDTH_DP = 128f

private fun isExplicitlyShrunkWideChild(
    child: ComponentItem,
    configuredColumns: Int,
    gridColumns: Int
): Boolean {
    if (isContainerPopupCompactItem(child)) return false
    val safeGridCols = gridColumns.coerceIn(8, 64)
    if (child.gridSpanX >= GRID_SPAN_FULL_ROW) return false
    if (child.gridSpanX in 1 until configuredColumns) {
        return child.w < safeGridCols
    }
    if (child.gridSpanX == 0) {
        if (child.type == ComponentType.TEXT_LABEL && child.autoHeight && child.w >= 24) return false
        return child.w in 1 until safeGridCols
    }
    return false
}

private fun shrunkWideFolderBounds(item: ComponentItem, gridColumns: Int): List<ContainerGridChildBounds>? {
    if (item.containerLayout != ContainerLayoutType.GRID || item.containerPopupWidthDp > 0) return null
    val configured = item.containerColumns.coerceIn(1, 8)
    val wideChildren = item.containerComponents.filterNot(::isContainerPopupCompactItem)
    if (wideChildren.isEmpty()) return null
    val safeGridCols = gridColumns.coerceIn(8, 64)
    if (!wideChildren.all { isExplicitlyShrunkWideChild(it, configured, safeGridCols) }) return null
    return containerGridChildBounds(
        children = item.containerComponents,
        columns = safeGridCols,
        widthDp = WIDE_CARD_REFERENCE_INNER_DP,
        stepYDp = WIDE_CARD_REFERENCE_INNER_DP / safeGridCols,
        iconColumns = configured,
        itemGapDp = item.containerItemGapDp,
        itemSizeDp = item.containerItemSizeDp,
        iconSizeDp = item.containerIconSizeDp,
        rowGapDp = containerRowGapDp(item),
        itemPaddingVerticalDp = item.containerItemPaddingVerticalDp,
        iconLabelGapDp = item.containerIconLabelGapDp
    )
}

private fun shrunkWideFolderContentWidthDp(item: ComponentItem, gridColumns: Int): Float? {
    val measured = shrunkWideFolderBounds(item, gridColumns) ?: return null
    val configured = item.containerColumns.coerceIn(1, 8)
    val childrenMaxRight = measured.maxOfOrNull { it.x + it.width } ?: return null
    val actionsWidth = if (item.containerActions.isNotEmpty()) {
        val actionCols = minOf(item.containerActions.size, configured).coerceAtLeast(1)
        actionCols * containerPopupIconCellWidthDp(item) +
            (actionCols - 1) * item.containerItemGapDp.coerceIn(MIN_GRID_GAP_DP, MAX_GRID_GAP_DP)
    } else 0f
    return maxOf(childrenMaxRight, actionsWidth).coerceAtMost(WIDE_CARD_REFERENCE_INNER_DP)
}

fun containerPopupAdaptiveReferenceInnerWidthDp(
    item: ComponentItem,
    availableWidthDp: Float,
    gridColumns: Int = 32
): Float {
    shrunkWideFolderContentWidthDp(item, gridColumns) ?: return 0f
    return WIDE_CARD_REFERENCE_INNER_DP.coerceAtMost((availableWidthDp - POPUP_HORIZONTAL_CHROME_DP).coerceAtLeast(1f))
}

fun containerPopupColumns(item: ComponentItem, gridColumns: Int = 32): Int {
    val configured = item.containerColumns.coerceIn(1, 8)
    // Wide non-compact cards (such as APP_DRAWER_CARD / APP_KEYBOARD_LAUNCHER / PHOTO_CUBE / sliders)
    // honor the folder's full configured column width unless all wide cards were explicitly shrunk.
    if (item.containerComponents.any { !isContainerPopupCompactItem(it) }) {
        val shrunkBounds = shrunkWideFolderBounds(item, gridColumns) ?: return configured
        val childExtent = shrunkBounds.maxOfOrNull { it.col + it.spanCols } ?: 1
        val actionExtent = if (item.containerActions.isNotEmpty()) minOf(item.containerActions.size, configured) else 0
        return maxOf(childExtent, actionExtent).coerceIn(1, configured)
    }
    val pinnedExtent = maxOf(
        item.containerComponents.filter { it.gridCol >= 0 }
            .maxOfOrNull { it.gridCol + it.gridSpanX.coerceIn(1, configured) } ?: 0,
        item.containerActions.filter { it.gridCol >= 0 }
            .maxOfOrNull { it.gridCol + 1 } ?: 0
    )
    val minForCount = maxOf(item.containerActions.size + item.containerComponents.size, pinnedExtent)
        .coerceAtLeast(if (configured == 1) 1 else 2)
    return minOf(minForCount, configured)
}

/** Automatic cells follow the actual icon footprint; an explicit legacy cell size remains authoritative. */
private fun containerPopupIconCellWidthDp(item: ComponentItem): Float {
    val configuredIcon = containerIconSizeDp(item)?.toFloat()
    val childrenWidth = item.containerComponents.maxOfOrNull { child ->
        val icon = configuredIcon ?: when (child.type) {
            ComponentType.FOLDER -> 62f
            ComponentType.APP_ICON -> 36f
            else -> 34f
        }
        val padding = if (child.type == ComponentType.FOLDER)
            maxOf(4f, (child.marginLeftDp + child.marginRightDp).toFloat()) else 4f
        icon + padding
    } ?: 0f
    val actionsWidth = if (item.containerActions.isNotEmpty()) (configuredIcon ?: 38f) + 4f else 0f
    val legacyCell = item.containerItemSizeDp.takeIf { it > 0 }?.coerceIn(48, 240)?.toFloat() ?: 0f
    return maxOf(childrenWidth, actionsWidth, legacyCell, if (childrenWidth == 0f && actionsWidth == 0f) 40f else 0f)
}

/** Compact grid-friendly children don't force an icon folder popup out to the 360dp wide-card width. */
fun isContainerPopupCompactItem(child: ComponentItem): Boolean =
    isContainerGridIconItem(child) ||
        (child.gridSpanX <= 1 && (isContainerGridTile(child.type) || resolveComponentVertical(child)))

private fun containerPopupCompactCellWidthDp(item: ComponentItem): Float {
    val iconWidth = containerPopupIconCellWidthDp(item)
    val tileWidth = item.containerComponents.maxOfOrNull { child ->
        when {
            isContainerGridIconItem(child) -> 0f
            resolveComponentVertical(child) -> 68f
            else -> 80f
        }
    } ?: 0f
    return maxOf(iconWidth, tileWidth)
}

private const val DEFAULT_MIN_POPUP_WIDTH_DP = 180f
private const val NARROW_COMPACT_POPUP_WIDTH_DP = 104f
private const val MIN_MANUAL_POPUP_WIDTH_DP = 92

/** Display-only width: a short icon row does not reserve unused columns unless [forceColumns] is specified (e.g., edit mode). */
fun containerPopupWidthDp(
    item: ComponentItem,
    availableWidthDp: Float,
    forceColumns: Int? = null,
    gridColumns: Int = 32
): Float {
    val iconOnly = item.containerComponents.all(::isContainerGridIconItem)
    val compactOnly = item.containerComponents.isNotEmpty() && item.containerComponents.all(::isContainerPopupCompactItem)
    val columns = forceColumns?.coerceIn(1, 8) ?: containerPopupColumns(item, gridColumns)
    val singleCompactGrid = item.containerLayout == ContainerLayoutType.GRID && columns == 1 && (iconOnly || compactOnly)
    val shrunkWideInner = if (forceColumns == null && !iconOnly && !compactOnly) {
        shrunkWideFolderContentWidthDp(item, gridColumns)
    } else null
    val autoFloor = when {
        singleCompactGrid -> NARROW_COMPACT_POPUP_WIDTH_DP
        shrunkWideInner != null -> SHRUNK_WIDE_MIN_POPUP_WIDTH_DP
        else -> DEFAULT_MIN_POPUP_WIDTH_DP
    }
    val desired = when {
        item.containerPopupWidthDp > 0 -> item.containerPopupWidthDp.coerceIn(MIN_MANUAL_POPUP_WIDTH_DP, 1200).toFloat()
        item.containerLayout == ContainerLayoutType.GRID && iconOnly ->
            (columns * containerPopupIconCellWidthDp(item) +
                (columns - 1) * item.containerItemGapDp.coerceIn(MIN_GRID_GAP_DP, MAX_GRID_GAP_DP) + 24f)
                .coerceAtLeast(autoFloor)
        item.containerLayout == ContainerLayoutType.GRID && compactOnly ->
            (columns * containerPopupCompactCellWidthDp(item) +
                (columns - 1) * item.containerItemGapDp.coerceIn(MIN_GRID_GAP_DP, MAX_GRID_GAP_DP) + 24f)
                .coerceAtLeast(if (singleCompactGrid) NARROW_COMPACT_POPUP_WIDTH_DP else 164f)
        shrunkWideInner != null ->
            (shrunkWideInner + POPUP_HORIZONTAL_CHROME_DP).coerceAtLeast(SHRUNK_WIDE_MIN_POPUP_WIDTH_DP)
        else -> WIDE_CARD_REFERENCE_POPUP_WIDTH_DP
    }
    val finalFloor = when {
        item.containerPopupWidthDp > 0 -> MIN_MANUAL_POPUP_WIDTH_DP.toFloat()
        singleCompactGrid -> NARROW_COMPACT_POPUP_WIDTH_DP
        shrunkWideInner != null -> SHRUNK_WIDE_MIN_POPUP_WIDTH_DP
        else -> DEFAULT_MIN_POPUP_WIDTH_DP
    }
    return desired.coerceAtLeast(finalFloor).coerceAtMost(availableWidthDp.coerceAtLeast(1f))
}

/** Zero means content height, with the available screen/panel height as the final limit. */
fun containerPopupHeightDp(item: ComponentItem, availableHeightDp: Float): Float =
    (if (item.containerPopupHeightDp > 0) item.containerPopupHeightDp.coerceIn(160, 1200).toFloat()
    else availableHeightDp).coerceAtMost(availableHeightDp.coerceAtLeast(1f))

data class ContainerCompactMetrics(
    val widthDp: Float,
    val stepYDp: Float,
    val columns: Int
)

/** Real inner width (dp), row unit height (dp), and column count used when compacting container grids. */
fun containerCompactTargetMetrics(
    item: ComponentItem,
    availableWidthDp: Float,
    editingInside: Boolean,
    gridColumns: Int,
    configuredHeightUnitDp: Int
): ContainerCompactMetrics {
    val safeCols = gridColumns.coerceIn(8, 64)
    val safeAvailable = availableWidthDp.takeIf { it.isFinite() && it > 0f } ?: 320f
    val widthDp = if (item.type == ComponentType.FOLDER) {
        val cols = if (editingInside) item.containerColumns.coerceIn(1, 8) else containerPopupColumns(item, safeCols)
        val forceCols = if (editingInside && item.containerLayout == ContainerLayoutType.GRID) cols else null
        (containerPopupWidthDp(item, safeAvailable, forceColumns = forceCols, gridColumns = safeCols) - 24f).coerceAtLeast(68f)
    } else {
        val fraction = item.w.coerceIn(1, safeCols).toFloat() / safeCols
        val horizontalInset = item.marginLeftDp + item.marginRightDp +
            item.paddingLeftDp.coerceIn(0, 48) + item.paddingRightDp.coerceIn(0, 48) + 16f
        (safeAvailable * fraction - horizontalInset).coerceAtLeast(120f)
    }
    val popupCols = if (item.type == ComponentType.FOLDER && !editingInside) {
        containerPopupColumns(item, safeCols)
    } else {
        item.containerColumns.coerceIn(1, 8)
    }
    val stepY = if (configuredHeightUnitDp > 0) configuredHeightUnitDp.toFloat() else widthDp / safeCols
    return ContainerCompactMetrics(widthDp = widthDp, stepYDp = stepY, columns = popupCols)
}

data class ContainerPopupPosition(val x: Int, val y: Int)

data class ContainerPopupBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** Overlay visible frames can exceed the actual host view/display; always intersect before measuring. */
fun containerPopupBounds(frameLeft: Int, frameTop: Int, frameRight: Int, frameBottom: Int,
    hostWidth: Int, hostHeight: Int, displayWidth: Int, displayHeight: Int, gap: Int): ContainerPopupBounds {
    val width = minOf(hostWidth.coerceAtLeast(1), displayWidth.coerceAtLeast(1))
    val height = minOf(hostHeight.coerceAtLeast(1), displayHeight.coerceAtLeast(1))
    val left = (frameLeft.coerceAtLeast(0) + gap).coerceIn(0, width - 1)
    val top = (frameTop.coerceAtLeast(0) + gap).coerceIn(0, height - 1)
    val right = (minOf(frameRight, width) - gap).coerceIn(left + 1, width)
    val bottom = (minOf(frameBottom, height) - gap).coerceIn(top + 1, height)
    return ContainerPopupBounds(left, top, right, bottom)
}

/** Prefer the configured panel footprint, permitting overlap with the trigger rather than
 * fleeing to its opposite side. Screen/IME bounds remain the final hard constraint. */
fun containerPopupPosition(anchorLeft: Int, anchorTop: Int, anchorRight: Int, anchorBottom: Int,
    width: Int, height: Int, left: Int, top: Int, right: Int, bottom: Int, gap: Int,
    panel: ContainerPopupBounds? = null): ContainerPopupPosition {
    val preferredLeft = maxOf(left, panel?.left ?: left)
    val preferredRight = minOf(right, panel?.right ?: right).coerceAtLeast(preferredLeft)
    val preferredTop = maxOf(top, panel?.top ?: top)
    val preferredBottom = minOf(bottom, panel?.bottom ?: bottom).coerceAtLeast(preferredTop)
    val centerX = if (panel != null) (preferredLeft.toLong() + preferredRight) / 2 else (anchorLeft.toLong() + anchorRight) / 2
    val centerY = (anchorTop.toLong() + anchorBottom) / 2
    fun position(center: Long, size: Int, a: Int, b: Int, hardA: Int, hardB: Int): Int {
        val desired = if (size <= b - a) (center - size / 2).coerceIn(a.toLong(), (b - size).toLong())
            else (a.toLong() + b - size) / 2
        return desired.coerceIn(hardA.toLong(), maxOf(hardA, hardB - size).toLong()).toInt()
    }
    val x = position(centerX, width, preferredLeft, preferredRight, left, right)
    val y = position(centerY, height, preferredTop, preferredBottom, top, bottom)
    return ContainerPopupPosition(x, y)
}
