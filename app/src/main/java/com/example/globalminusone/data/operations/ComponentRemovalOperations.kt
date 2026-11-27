package com.example.globalminusone.data.operations

import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ContainerLayoutType
import com.example.globalminusone.data.PageConfig
import com.example.globalminusone.data.policy.arrangePageGridComponents
import com.example.globalminusone.data.policy.containerGridChildBounds
import com.example.globalminusone.data.policy.containerIconSizeDp
import com.example.globalminusone.data.policy.containerRowGapDp
import com.example.globalminusone.data.isContainerType

/** Removal preserves pinned sibling layout; the repository owns widget cleanup and publication. */
internal object ComponentRemovalOperations {
    fun remove(
        items: List<ComponentItem>,
        targetId: String,
        isGridScope: Boolean,
        pageConfig: PageConfig,
        gridColumns: Int,
        heightUnitDp: Int,
        fontScale: () -> Float,
        pageGridWidthDp: Float,
        container: ComponentItem? = null
    ): List<ComponentItem> {
        val directMatch = items.any { it.id == targetId }
        val remaining = items.filterNot { it.id == targetId }
        if (directMatch) {
            if (!isGridScope || remaining.none { it.gridCol >= 0 && it.gridRow >= 0 }) {
                return remaining
            }
            val resolvedCoords: Map<String, Pair<Int, Int>> = if (container == null) {
                arrangePageGridComponents(
                    components = items,
                    availableWidthDp = pageGridWidthDp,
                    itemSizeDp = pageConfig.gridItemSizeDp,
                    itemGapDp = pageConfig.gridItemGapDp,
                    gridColumns = gridColumns,
                    configuredHeightUnitDp = heightUnitDp,
                    fontScale = fontScale(),
                    columnCount = pageConfig.gridColumnCount,
                    iconSizeDp = pageConfig.gridIconSizeDp,
                    fillGaps = pageConfig.gridFillGaps,
                    rowGapDp = pageConfig.gridRowGapDp
                ).placements.associate { it.id to (it.col to it.row) }
            } else {
                val stepY = if (heightUnitDp > 0) heightUnitDp.toFloat()
                    else pageGridWidthDp / gridColumns.coerceIn(8, 64)
                containerGridChildBounds(
                    children = items,
                    columns = gridColumns,
                    widthDp = pageGridWidthDp,
                    stepYDp = stepY,
                    configuredHeightUnitDp = heightUnitDp,
                    fontScale = fontScale(),
                    iconColumns = container.containerColumns.coerceIn(1, 8),
                    itemGapDp = container.containerItemGapDp,
                    itemSizeDp = container.containerItemSizeDp,
                    iconSizeDp = container.containerIconSizeDp,
                    rowGapDp = containerRowGapDp(container),
                    itemPaddingVerticalDp = container.containerItemPaddingVerticalDp,
                    iconLabelGapDp = container.containerIconLabelGapDp
                ).associate { it.item.id to (it.col to it.row) }
            }
            return remaining.map { child ->
                if (child.gridCol >= 0 && child.gridRow >= 0) child
                else resolvedCoords[child.id]?.let { (c, r) -> child.copy(gridCol = c, gridRow = r) } ?: child
            }
        }
        return items.map { child ->
            if (!child.type.isContainerType() || child.containerComponents.isEmpty()) child
            else child.copy(
                containerComponents = remove(
                    items = child.containerComponents,
                    targetId = targetId,
                    isGridScope = child.containerLayout == ContainerLayoutType.GRID,
                    pageConfig = pageConfig,
                    gridColumns = gridColumns,
                    heightUnitDp = heightUnitDp,
                    fontScale = fontScale,
                    pageGridWidthDp = pageGridWidthDp,
                    container = child
                )
            )
        }
    }
}
