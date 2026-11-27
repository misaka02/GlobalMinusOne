package com.example.globalminusone.data.operations

import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ContainerLayoutType
import com.example.globalminusone.data.PageConfig
import com.example.globalminusone.data.PageLayoutType
import com.example.globalminusone.data.policy.arrangePageGridComponents
import com.example.globalminusone.data.policy.defaultGridSpanForOrientation
import com.example.globalminusone.data.policy.defaultPageGridSpan
import com.example.globalminusone.data.isContainerType
import com.example.globalminusone.data.policy.isSingleIconLauncherItem
import com.example.globalminusone.data.policy.resolvePageGridDropPlacements

/** Sibling insertion and nested writeback; measurements are supplied synchronously by the owner. */
internal object ComponentInsertionOperations {
    data class Candidate(val components: List<ComponentItem>, val placedId: String)

    fun placeMenu(page: PageConfig, containerId: String?, item: ComponentItem, beforeId: String?,
        columns: Int, nextY: () -> Int, rows: (ComponentItem) -> Int,
        targetGridCell: Pair<Int, Int>? = null, availableWidthDp: Float = 268f,
        fontScale: Float = 1f, heightUnitDp: Int = 0): Candidate? {
        val container = containerId?.let { page.components.findComponent(it) }
        if (containerId != null && container?.type?.isContainerType() != true) return null
        val width = item.w.coerceIn(1, columns)
        val placed = item.copy(w = width, x = item.x.coerceIn(0, columns - width), y = nextY())
        val components = insert(page = page, container = container, item = placed, beforeId = beforeId,
            columns = columns, rows = rows(placed), targetGridCell = targetGridCell,
            availableWidthDp = availableWidthDp, fontScale = fontScale, heightUnitDp = heightUnitDp) ?: return null
        return Candidate(components, placed.id)
    }

    /** The container is resolved from this page by the caller; null explicitly means the page root. */
    fun insert(page: PageConfig, container: ComponentItem?, item: ComponentItem, beforeId: String?,
        columns: Int, rows: Int, targetGridCell: Pair<Int, Int>? = null,
        availableWidthDp: Float = 268f, fontScale: Float = 1f, heightUnitDp: Int = 0): List<ComponentItem>? {
        val siblings = container?.containerComponents ?: page.components
        val isFree = if (container == null) {
            page.layoutType == PageLayoutType.FREE
        } else {
            container.containerLayout == ContainerLayoutType.FREE
        }
        val isGrid = if (container == null) {
            page.layoutType == PageLayoutType.GRID
        } else {
            container.containerLayout == ContainerLayoutType.GRID
        }
        if (container == null && isGrid && targetGridCell != null) {
            return insertIntoPageGridCell(page, siblings, item, targetGridCell, availableWidthDp, columns, heightUnitDp, fontScale)
        }
        val gridCols = if (container == null) 4 else container.containerColumns.coerceIn(1, 8)
        val spanY = when {
            isSingleIconLauncherItem(item) -> 1
            item.gridSpanY > 0 -> item.gridSpanY
            else -> defaultGridSpanForOrientation(item.type, item.orientation)?.second
                ?: defaultPageGridSpan(item.type, gridCols).second
        }
        val children = insertCardAbove(
            siblings = siblings,
            item = item,
            beforeId = beforeId,
            free = isFree,
            columns = columns,
            rows = rows,
            grid = isGrid,
            gridSpanY = spanY
        ) ?: return null
        return if (container == null) children else
            page.components.transformComponent(container.id) { it.copy(containerComponents = children) }
    }

    private fun insertIntoPageGridCell(
        page: PageConfig,
        existing: List<ComponentItem>,
        item: ComponentItem,
        targetCell: Pair<Int, Int>,
        availableWidthDp: Float,
        gridColumns: Int,
        heightUnitDp: Int,
        fontScale: Float
    ): List<ComponentItem> {
        val existingLayout = arrangePageGridComponents(
            components = existing,
            availableWidthDp = availableWidthDp,
            itemSizeDp = page.gridItemSizeDp,
            itemGapDp = page.gridItemGapDp,
            gridColumns = gridColumns,
            configuredHeightUnitDp = heightUnitDp,
            fontScale = fontScale,
            columnCount = page.gridColumnCount,
            iconSizeDp = page.gridIconSizeDp,
            fillGaps = page.gridFillGaps,
            rowGapDp = page.gridRowGapDp
        )
        val coords = existingLayout.placements.associate { it.id to (it.col to it.row) }
        val pinnedExisting = existing.map { comp ->
            val (c, r) = coords[comp.id] ?: (comp.gridCol to comp.gridRow)
            comp.copy(gridCol = c, gridRow = r)
        }
        val safeCol = targetCell.first.coerceAtLeast(0)
        val safeRow = targetCell.second.coerceAtLeast(0)
        val appended = pinnedExisting + item.copy(gridCol = safeCol, gridRow = safeRow)
        return resolvePageGridDropPlacements(
            components = appended,
            availableWidthDp = availableWidthDp,
            itemSizeDp = page.gridItemSizeDp,
            itemGapDp = page.gridItemGapDp,
            gridColumns = gridColumns,
            configuredHeightUnitDp = heightUnitDp,
            fontScale = fontScale,
            columnCount = page.gridColumnCount,
            iconSizeDp = page.gridIconSizeDp,
            fillGaps = page.gridFillGaps,
            rowGapDp = page.gridRowGapDp,
            movedId = item.id,
            targetCol = safeCol,
            targetRow = safeRow
        )
    }
}
