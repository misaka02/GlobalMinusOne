package com.example.globalminusone.data

internal data class PageListChange(val pages: List<PageConfig>, val activeIndex: Int)

/** Page algorithms only; lock, edit transaction and persistence acceptance stay in the repository. */
internal object PageOperations {
    fun createPage(layoutType: PageLayoutType, name: String, pageNumber: Int, newId: () -> String): PageConfig {
        val resolvedName = if (name.isBlank()) {
            "${layoutType.label} $pageNumber"
        } else {
            name
        }
        // 新建页默认附带一个底栏操作栏组件
        val defaultComponents = when (layoutType) {
            PageLayoutType.FREE -> listOf(
                ComponentItem(
                    id = newId(),
                    type = ComponentType.OPERATION_BAR,
                    title = "操作栏",
                    showTitle = false,
                    x = 1,
                    y = 44,
                    w = 30,
                    h = 4
                )
            )
            PageLayoutType.GRID -> listOf(
                ComponentItem(
                    id = newId(),
                    type = ComponentType.OPERATION_BAR,
                    title = "操作栏",
                    showTitle = false,
                    w = 32,
                    h = 4,
                    gridSpanX = GRID_SPAN_FULL_ROW,
                    gridSpanY = 1
                )
            )
            PageLayoutType.LINEAR -> listOf(
                ComponentItem(
                    id = newId(),
                    type = ComponentType.OPERATION_BAR,
                    title = "操作栏",
                    showTitle = false,
                    w = 32,
                    h = 4
                )
            )
        }
        return PageConfig(
            id = newId(),
            name = resolvedName,
            layoutType = layoutType,
            components = defaultComponents
        )
    }

    fun delete(pages: List<PageConfig>, activeIndex: Int, pageIndex: Int): PageListChange? {
        if (pages.size <= 1 || pageIndex !in pages.indices) return null
        val updatedPages = pages.filterIndexed { index, _ -> index != pageIndex }
        val newCurrent = when {
            activeIndex >= updatedPages.size -> updatedPages.size - 1
            activeIndex == pageIndex -> activeIndex.coerceAtMost(updatedPages.size - 1)
            activeIndex > pageIndex -> activeIndex - 1
            else -> activeIndex
        }
        return PageListChange(updatedPages, newCurrent)
    }

    fun swapLeft(pages: List<PageConfig>, pageIndex: Int): PageListChange? {
        if (pageIndex <= 0 || pageIndex >= pages.size) return null
        return swap(pages, pageIndex, pageIndex - 1)
    }

    fun swapRight(pages: List<PageConfig>, pageIndex: Int): PageListChange? {
        if (pageIndex < 0 || pageIndex >= pages.size - 1) return null
        return swap(pages, pageIndex, pageIndex + 1)
    }

    private fun swap(pages: List<PageConfig>, pageIndex: Int, targetIndex: Int): PageListChange {
        val mutable = pages.toMutableList()
        val temp = mutable[pageIndex]
        mutable[pageIndex] = mutable[targetIndex]
        mutable[targetIndex] = temp
        return PageListChange(mutable, targetIndex)
    }

    /** Null rejects index/tree validation; exceptions from the caller's transform still propagate. */
    fun update(pages: List<PageConfig>, pageIndex: Int, transform: (PageConfig) -> PageConfig): List<PageConfig>? {
        if (pageIndex !in pages.indices) return null
        val updated = pages.toMutableList()
        updated[pageIndex] = transform(updated[pageIndex])
        if (runCatching { validateComponentTrees(updated) }.isFailure) return null
        return updated
    }

    fun gridStyle(page: PageConfig, itemSizeDp: Int? = null, itemGapDp: Int? = null,
        columnCount: Int? = null, iconSizeDp: Int? = null, rowGapDp: Int? = null): PageConfig {
        val nextRowGap = rowGapDp?.let {
            if (it == ROW_GAP_INHERIT) ROW_GAP_INHERIT else it.coerceIn(MIN_GRID_GAP_DP, MAX_GRID_GAP_DP)
        } ?: page.gridRowGapDp
        return page.copy(
            gridItemSizeDp = (itemSizeDp ?: page.gridItemSizeDp).coerceIn(44, 160),
            gridItemGapDp = (itemGapDp ?: page.gridItemGapDp).coerceIn(MIN_GRID_GAP_DP, MAX_GRID_GAP_DP),
            gridRowGapDp = nextRowGap,
            gridColumnCount = (columnCount ?: page.gridColumnCount).coerceIn(0, 12),
            gridIconSizeDp = (iconSizeDp ?: page.gridIconSizeDp).coerceIn(0, 192)
        )
    }

    /** The repository rejects locked pages, invalid indices and same-type requests before calling. */
    fun convertLayout(targetPage: PageConfig, newType: PageLayoutType, gridColumns: Int): PageConfig {
        val columns = gridColumns.coerceIn(8, 64)
        val orderedSource = if (targetPage.layoutType == PageLayoutType.FREE) {
            targetPage.components.sortedWith(compareBy({ it.y }, { it.x }))
        } else {
            targetPage.components
        }
        val updatedComponents = when (newType) {
            PageLayoutType.FREE -> {
                var currentY = 0
                orderedSource.map { comp ->
                    val w = if (targetPage.layoutType != PageLayoutType.LINEAR && comp.w in 1..columns) {
                        comp.w
                    } else {
                        comp.type.defaultW.coerceIn(1, columns)
                    }
                    val h = if (comp.h > 0) comp.h else comp.type.defaultH.coerceAtLeast(2)
                    val y = currentY
                    currentY += h + 1
                    comp.copy(x = 0, y = y, w = w, h = h)
                }
            }
            PageLayoutType.LINEAR -> {
                orderedSource.map { comp -> comp.copy(w = columns) }
            }
            PageLayoutType.GRID -> {
                orderedSource.map { comp ->
                    val naturalW = if (targetPage.layoutType == PageLayoutType.LINEAR) {
                        comp.type.defaultW.coerceIn(1, columns)
                    } else {
                        comp.w.coerceIn(1, columns)
                    }
                    comp.copy(w = naturalW)
                }
            }
        }
        return targetPage.copy(layoutType = newType, components = updatedComponents)
    }
}
