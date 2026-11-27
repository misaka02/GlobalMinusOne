package com.example.globalminusone.data.operations

import com.example.globalminusone.data.ComponentItem

/** Stable sibling identity survives a picker; a deleted anchor never falls back to the bottom. */
internal fun insertCardAbove(
    siblings: List<ComponentItem>,
    item: ComponentItem,
    beforeId: String?,
    free: Boolean,
    columns: Int,
    rows: Int = item.h,
    grid: Boolean = false,
    gridSpanY: Int = 1
): List<ComponentItem>? {
    if (beforeId == null) return siblings + item
    val index = siblings.indexOfFirst { it.id == beforeId }
    if (index < 0) return null
    val anchor = siblings[index]
    val width = item.w.coerceIn(1, columns)
    if (!free && grid && siblings.any { it.gridCol >= 0 && it.gridRow >= 0 }) {
        val anchorRow = if (anchor.gridRow >= 0) {
            anchor.gridRow
        } else {
            siblings.take(index).mapNotNull { it.gridRow.takeIf { r -> r >= 0 } }.maxOrNull()?.plus(1) ?: 0
        }
        val anchorCol = if (anchor.gridCol >= 0) anchor.gridCol else 0
        val shiftRows = (if (gridSpanY > 0) gridSpanY else item.gridSpanY.takeIf { it > 0 } ?: 1).coerceIn(1, 12)
        val shifted = siblings.map { s ->
            if (s.gridRow >= anchorRow) s.copy(gridRow = s.gridRow + shiftRows) else s
        }
        val inserted = item.copy(gridCol = anchorCol, gridRow = anchorRow)
        return shifted.toMutableList().apply { add(index, inserted) }
    }
    val inserted = if (free) item.copy(x = anchor.x.coerceIn(0, columns - width), y = anchor.y, w = width) else item
    val shifted = if (free) siblings.map { if (it.y >= anchor.y) it.copy(y = it.y + rows.coerceAtLeast(1)) else it } else siblings
    return shifted.toMutableList().apply { add(index, inserted) }
}

internal fun List<ComponentItem>.parentContainerId(componentId: String): String? {
    for (item in this) {
        if (item.containerComponents.any { it.id == componentId }) return item.id
        item.containerComponents.parentContainerId(componentId)?.let { return it }
    }
    return null
}
