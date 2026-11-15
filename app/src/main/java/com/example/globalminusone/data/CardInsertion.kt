package com.example.globalminusone.data

/** Stable sibling identity survives a picker; a deleted anchor never falls back to the bottom. */
internal fun insertCardAbove(siblings: List<ComponentItem>, item: ComponentItem, beforeId: String?,
    free: Boolean, columns: Int, rows: Int = item.h): List<ComponentItem>? {
    if (beforeId == null) return siblings + item
    val index = siblings.indexOfFirst { it.id == beforeId }
    if (index < 0) return null
    val anchor = siblings[index]
    val width = item.w.coerceIn(1, columns)
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
