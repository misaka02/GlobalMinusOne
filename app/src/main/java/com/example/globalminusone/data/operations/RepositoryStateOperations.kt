package com.example.globalminusone.data.operations

import com.example.globalminusone.data.BoundWidgetInfo
import com.example.globalminusone.data.CardConfig
import com.example.globalminusone.data.CardType
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.ContainerLayoutType
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.PageConfig
import com.example.globalminusone.data.PageLayoutType
import com.example.globalminusone.data.ThemeConfig
import com.example.globalminusone.data.isContainerType

/** Pure state transformations extracted from [MinusOneRepository]; callers own locking, validation and persistence. */
internal object RepositoryStateOperations {
    fun lightDarkPresetTheme(
        current: ThemeConfig,
        darkMode: Boolean,
        cardAlpha: Int,
        panelAlpha: Int
    ): ThemeConfig = if (!darkMode) {
        current.copy(
            isDarkMode = false,
            followSystemDark = false,
            maskColorHex = 0xFF000000L,
            maskAlphaPercent = 20,
            panelBgColorHex = 0xFFF7F8FAL,
            panelBgAlphaPercent = panelAlpha,
            defaultCardBgColorHex = 0xFFFFFFFFL,
            cardBgAlphaPercent = cardAlpha,
            defaultTextColorHex = 0xFF1F2329L,
            textAlphaPercent = 100,
            titleColorHex = null,
            titleAlphaPercent = 100
        )
    } else {
        current.copy(
            isDarkMode = true,
            followSystemDark = false,
            maskColorHex = 0xFF000000L,
            maskAlphaPercent = 45,
            panelBgColorHex = 0xFF14171FL,
            panelBgAlphaPercent = panelAlpha,
            defaultCardBgColorHex = 0xFF222630L,
            cardBgAlphaPercent = cardAlpha,
            defaultTextColorHex = 0xFFF5F7FAL,
            textAlphaPercent = 100,
            titleColorHex = null,
            titleAlphaPercent = 100
        )
    }

    fun appendImportedFolderState(
        current: GlobalMinusOneState,
        folder: ComponentItem,
        targetPageId: String,
        targetContainerId: String?,
        nextY: (PageConfig, String?) -> Int
    ): GlobalMinusOneState? {
        if (current.advancedConfig.layoutLocked || current.editMode || folder.type != ComponentType.FOLDER) return null
        val index = current.pages.indexOfFirst { it.id == targetPageId }
        if (index < 0) return null
        val page = current.pages[index]
        val parent = targetContainerId?.let { page.components.findComponent(it) }
        if (targetContainerId != null && parent?.type?.isContainerType() != true) return null
        val columns = current.advancedConfig.gridColumns
        val width = folder.w.coerceIn(1, columns)
        val free = parent?.containerLayout?.let { it == ContainerLayoutType.FREE } ?: (page.layoutType == PageLayoutType.FREE)
        val placed = folder.copy(
            w = width,
            x = folder.x.coerceIn(0, columns - width),
            y = if (free) nextY(page, targetContainerId) else 0
        )
        val components = if (parent == null) {
            page.components + placed
        } else {
            page.components.transformComponent(parent.id) { it.copy(containerComponents = it.containerComponents + placed) }
        }
        val pages = current.pages.toMutableList().apply { this[index] = page.copy(components = components) }
        if (runCatching { validateComponentTrees(pages) }.isFailure) return null
        return current.copy(pages = pages)
    }

    fun appendImportedContainersState(
        current: GlobalMinusOneState,
        containers: List<ComponentItem>,
        targetPageId: String,
        targetContainerId: String?,
        nextY: (PageConfig, String?) -> Int
    ): GlobalMinusOneState? {
        if (containers.isEmpty() || current.advancedConfig.layoutLocked || current.editMode) return null
        if (containers.any { !it.type.isContainerType() }) return null
        val index = current.pages.indexOfFirst { it.id == targetPageId }
        if (index < 0) return null
        var workingPage = current.pages[index]
        val initialParent = targetContainerId?.let { workingPage.components.findComponent(it) }
        if (targetContainerId != null && initialParent?.type?.isContainerType() != true) return null
        val columns = current.advancedConfig.gridColumns.coerceIn(8, 64)
        val free = initialParent?.containerLayout?.let { it == ContainerLayoutType.FREE }
            ?: (workingPage.layoutType == PageLayoutType.FREE)
        var rowX = 0
        var rowY = if (free) nextY(workingPage, targetContainerId) else 0
        var rowMaxH = 0
        for (item in containers) {
            val width = if (!free && targetContainerId == null && item.type == ComponentType.CONTAINER_BOX) {
                columns
            } else {
                item.w.coerceIn(1, columns)
            }
            val placed = if (!free) {
                item.copy(w = width, x = 0, y = 0)
            } else if (item.type == ComponentType.FOLDER && width * 2 <= columns) {
                if (rowX + width > columns) {
                    rowX = 0
                    rowY += rowMaxH.coerceAtLeast(8) + 1
                    rowMaxH = 0
                }
                val folderPlaced = item.copy(w = width, x = rowX, y = rowY)
                rowX += width
                rowMaxH = maxOf(rowMaxH, folderPlaced.h.coerceAtLeast(8))
                folderPlaced
            } else {
                val y = nextY(workingPage, targetContainerId)
                val placedBox = item.copy(w = width, x = item.x.coerceIn(0, (columns - width).coerceAtLeast(0)), y = y)
                rowX = 0
                rowMaxH = 0
                placedBox
            }
            val updatedComponents = if (targetContainerId == null) {
                workingPage.components + placed
            } else {
                workingPage.components.transformComponent(targetContainerId) {
                    it.copy(containerComponents = it.containerComponents + placed)
                }
            }
            workingPage = workingPage.copy(components = updatedComponents)
            if (free && rowX == 0) rowY = nextY(workingPage, targetContainerId)
        }
        val pages = current.pages.toMutableList().apply { this[index] = workingPage }
        if (runCatching { validateComponentTrees(pages) }.isFailure) return null
        return current.copy(pages = pages)
    }

    fun addBoundWidgetState(
        current: GlobalMinusOneState,
        widgetInfo: BoundWidgetInfo,
        pageId: String?,
        containerId: String?,
        beforeId: String?,
        nextY: (PageConfig, String?) -> Int,
        insertionRows: (ComponentItem) -> Int,
        targetGridCell: Pair<Int, Int>? = null,
        availableWidthDp: Float = 268f,
        fontScale: Float = 1f
    ): GlobalMinusOneState? {
        if (current.advancedConfig.layoutLocked) return null
        val pageIdx = if (pageId != null) current.pages.indexOfFirst { it.id == pageId } else current.currentPageIndex
        val targetPage = current.pages.getOrNull(pageIdx) ?: return null
        val container = containerId?.let { targetPage.components.findComponent(it)?.takeIf { node -> node.type.isContainerType() } }
        if (containerId != null && container == null) return null
        val widgetComponent = ComponentItem(
            id = "widget_${widgetInfo.appWidgetId}",
            type = ComponentType.APP_WIDGET,
            title = widgetInfo.label.ifBlank { "桌面小部件" },
            widgetId = widgetInfo.appWidgetId,
            w = current.advancedConfig.gridColumns,
            h = 14,
            y = nextY(targetPage, containerId)
        )
        val inserted = ComponentInsertionOperations.insert(
            page = targetPage, container = container, item = widgetComponent, beforeId = beforeId,
            columns = current.advancedConfig.gridColumns, rows = insertionRows(widgetComponent),
            targetGridCell = targetGridCell, availableWidthDp = availableWidthDp,
            fontScale = fontScale, heightUnitDp = current.advancedConfig.heightUnitDp
        ) ?: return null
        val pages = current.pages.toMutableList().apply { this[pageIdx] = targetPage.copy(components = inserted) }
        if (runCatching { validateComponentTrees(pages) }.isFailure) return null
        val newCard = CardConfig(
            id = "widget_${widgetInfo.appWidgetId}",
            type = CardType.APP_WIDGET,
            title = widgetInfo.label.ifBlank { "桌面小部件 #${widgetInfo.appWidgetId}" },
            enabled = true,
            order = current.cards.size,
            widgetId = widgetInfo.appWidgetId
        )
        return current.copy(
            pages = pages,
            boundWidgets = current.boundWidgets.filterNot { it.appWidgetId == widgetInfo.appWidgetId } + widgetInfo,
            cards = if (container == null) current.cards + newCard else current.cards,
            selectedComponentId = widgetComponent.id
        )
    }

    fun updateWidgetHeightState(current: GlobalMinusOneState, appWidgetId: Int, heightDp: Int): GlobalMinusOneState {
        if (current.advancedConfig.layoutLocked) return current
        val updatedWidgets = current.boundWidgets.map {
            if (it.appWidgetId == appWidgetId) it.copy(heightDp = heightDp.coerceIn(80, 520)) else it
        }
        val updatedPages = current.pages.map { page ->
            page.copy(components = page.components.mapComponentTree { comp ->
                if (comp.widgetId == appWidgetId) comp.copy(h = (heightDp / 20).coerceIn(4, 30)) else comp
            })
        }
        return current.copy(boundWidgets = updatedWidgets, pages = updatedPages)
    }

    fun moveLegacyCard(current: GlobalMinusOneState, cardId: String, moveUp: Boolean): GlobalMinusOneState {
        if (current.advancedConfig.layoutLocked) return current
        val sorted = current.cards.sortedBy { it.order }.toMutableList()
        val idx = sorted.indexOfFirst { it.id == cardId }
        if (idx == -1) return current
        val targetIdx = if (moveUp) idx - 1 else idx + 1
        if (targetIdx !in sorted.indices) return current
        val temp = sorted[idx]
        sorted[idx] = sorted[targetIdx]
        sorted[targetIdx] = temp
        val reindexed = sorted.mapIndexed { index, card -> card.copy(order = index) }
        return current.copy(cards = reindexed)
    }

    fun removeLegacyCard(current: GlobalMinusOneState, cardId: String): GlobalMinusOneState {
        if (current.advancedConfig.layoutLocked) return current
        val target = current.cards.find { it.id == cardId }
        val updatedWidgets = if (target?.widgetId != null) {
            current.boundWidgets.filterNot { it.appWidgetId == target.widgetId }
        } else {
            current.boundWidgets
        }
        val remaining = current.cards
            .filterNot { it.id == cardId }
            .sortedBy { it.order }
            .mapIndexed { idx, card -> card.copy(order = idx) }
        return current.copy(cards = remaining, boundWidgets = updatedWidgets)
    }

    fun updateLegacyCustomImageCard(current: GlobalMinusOneState, uriString: String?, heightDp: Int): GlobalMinusOneState {
        if (current.advancedConfig.layoutLocked) return current
        val hasImageCard = current.cards.any { it.type == CardType.CUSTOM_IMAGE }
        val updatedCards = if (hasImageCard) {
            current.cards.map {
                if (it.type == CardType.CUSTOM_IMAGE) {
                    it.copy(
                        enabled = uriString != null,
                        customImageUri = uriString,
                        customImageHeightDp = heightDp
                    )
                } else it
            }
        } else {
            current.cards + CardConfig(
                id = "card_custom_image",
                type = CardType.CUSTOM_IMAGE,
                enabled = uriString != null,
                order = current.cards.size,
                customImageUri = uriString,
                customImageHeightDp = heightDp
            )
        }
        return current.copy(cards = updatedCards)
    }
}
