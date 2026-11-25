package com.example.globalminusone.data

/** Sibling insertion and nested writeback; measurements are supplied synchronously by the owner. */
internal object ComponentInsertionOperations {
    data class Candidate(val components: List<ComponentItem>, val placedId: String)

    fun placeMenu(page: PageConfig, containerId: String?, item: ComponentItem, beforeId: String?,
        columns: Int, nextY: () -> Int, rows: (ComponentItem) -> Int): Candidate? {
        val container = containerId?.let { page.components.findComponent(it) }
        if (containerId != null && container?.type?.isContainerType() != true) return null
        val width = item.w.coerceIn(1, columns)
        val placed = item.copy(w = width, x = item.x.coerceIn(0, columns - width), y = nextY())
        val components = insert(page = page, container = container, item = placed, beforeId = beforeId,
            columns = columns, rows = rows(placed)) ?: return null
        return Candidate(components, placed.id)
    }

    /** The container is resolved from this page by the caller; null explicitly means the page root. */
    fun insert(page: PageConfig, container: ComponentItem?, item: ComponentItem, beforeId: String?,
        columns: Int, rows: Int): List<ComponentItem>? {
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
}
