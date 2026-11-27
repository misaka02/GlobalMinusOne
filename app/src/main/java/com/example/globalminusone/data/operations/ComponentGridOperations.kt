package com.example.globalminusone.data.operations

import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ContainerLayoutType
import com.example.globalminusone.data.PageConfig
import com.example.globalminusone.data.PageLayoutType
import com.example.globalminusone.data.policy.arrangePageGridComponents
import com.example.globalminusone.data.policy.commitContainerActionGridPlacement
import com.example.globalminusone.data.policy.containerGridChildBounds
import com.example.globalminusone.data.policy.containerIconSizeDp
import com.example.globalminusone.data.policy.containerRowGapDp
import com.example.globalminusone.data.policy.nudgePageGridComponent
import com.example.globalminusone.data.policy.resolveContainerGridDropPlacements
import com.example.globalminusone.data.policy.resolvePageGridDropPlacements

/** Pure layout orchestration; repository owns target lookup, edit transactions and write acceptance. */
internal object ComponentGridOperations {
    fun placeComponentInPageGrid(
        page: PageConfig,
        movedId: String,
        targetCol: Int,
        targetRow: Int,
        availableWidthDp: Float,
        fontScale: Float,
        gridColumns: Int,
        heightUnitDp: Int
    ): PageConfig {
        if (page.layoutType != PageLayoutType.GRID) return page
        val resolved = resolvePageGridDropPlacements(
            components = page.components,
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
            movedId = movedId,
            targetCol = targetCol,
            targetRow = targetRow
        )
        return page.copy(components = resolved)
    }

    fun nudgeComponentInPageGrid(
        page: PageConfig,
        movedId: String,
        deltaCol: Int,
        deltaRow: Int,
        availableWidthDp: Float,
        fontScale: Float,
        gridColumns: Int,
        heightUnitDp: Int
    ): PageConfig {
        if (page.layoutType != PageLayoutType.GRID) return page
        val updated = nudgePageGridComponent(
            components = page.components,
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
            movedId = movedId,
            deltaCol = deltaCol,
            deltaRow = deltaRow
        )
        return page.copy(components = updated)
    }

    fun compactPageGridPlacements(
        page: PageConfig,
        availableWidthDp: Float,
        fontScale: Float,
        gridColumns: Int,
        heightUnitDp: Int
    ): PageConfig {
        if (page.layoutType != PageLayoutType.GRID) return page
        val current = arrangePageGridComponents(
            components = page.components,
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
        val coords = current.placements.associate { it.id to (it.row to it.col) }
        val compacted = page.components
            .sortedWith(compareBy({ coords[it.id]?.first ?: Int.MAX_VALUE }, { coords[it.id]?.second ?: Int.MAX_VALUE }))
            .map { it.copy(gridCol = -1, gridRow = -1) }
        return page.copy(components = compacted)
    }

    fun placeChildInContainerGrid(
        container: ComponentItem,
        movedId: String,
        targetCol: Int,
        targetRow: Int,
        widthDp: Float,
        stepYDp: Float,
        iconColumns: Int,
        fontScale: Float,
        gridColumns: Int,
        heightUnitDp: Int
    ): ComponentItem {
        if (container.containerLayout != ContainerLayoutType.GRID) return container
        val resolved = resolveContainerGridDropPlacements(
            children = container.containerComponents,
            columns = gridColumns,
            widthDp = widthDp,
            stepYDp = stepYDp,
            configuredHeightUnitDp = heightUnitDp,
            fontScale = fontScale,
            iconColumns = iconColumns,
            itemGapDp = container.containerItemGapDp,
            itemSizeDp = container.containerItemSizeDp,
            iconSizeDp = container.containerIconSizeDp,
            rowGapDp = containerRowGapDp(container),
            itemPaddingVerticalDp = container.containerItemPaddingVerticalDp,
            iconLabelGapDp = container.containerIconLabelGapDp,
            movedId = movedId,
            targetCol = targetCol,
            targetRow = targetRow
        )
        return container.copy(containerComponents = resolved)
    }

    fun placeActionInContainerGrid(
        container: ComponentItem,
        movedId: String,
        targetCol: Int,
        targetRow: Int,
        iconColumns: Int
    ): ComponentItem {
        if (container.containerLayout != ContainerLayoutType.GRID) return container
        return container.copy(
            containerActions = commitContainerActionGridPlacement(
                actions = container.containerActions,
                movedId = movedId,
                columns = iconColumns,
                targetCol = targetCol,
                targetRow = targetRow
            )
        )
    }

    fun compactContainerGridPlacements(
        container: ComponentItem,
        widthDp: Float,
        stepYDp: Float,
        iconColumns: Int,
        fontScale: Float,
        gridColumns: Int,
        heightUnitDp: Int
    ): ComponentItem {
        if (container.containerLayout != ContainerLayoutType.GRID) return container
        val bounds = containerGridChildBounds(
            children = container.containerComponents,
            columns = gridColumns,
            widthDp = widthDp,
            stepYDp = stepYDp,
            configuredHeightUnitDp = heightUnitDp,
            fontScale = fontScale,
            iconColumns = iconColumns,
            itemGapDp = container.containerItemGapDp,
            itemSizeDp = container.containerItemSizeDp,
            iconSizeDp = container.containerIconSizeDp,
            rowGapDp = containerRowGapDp(container),
            itemPaddingVerticalDp = container.containerItemPaddingVerticalDp,
            iconLabelGapDp = container.containerIconLabelGapDp
        ).associate { it.item.id to (it.row to it.col) }
        val compacted = container.containerComponents
            .sortedWith(compareBy({ bounds[it.id]?.first ?: Int.MAX_VALUE }, { bounds[it.id]?.second ?: Int.MAX_VALUE }))
            .map { it.copy(gridCol = -1, gridRow = -1) }
        return container.copy(containerComponents = compacted)
    }
}
