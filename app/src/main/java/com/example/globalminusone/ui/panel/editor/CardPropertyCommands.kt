package com.example.globalminusone.ui.panel.editor

import com.example.globalminusone.data.*
import com.example.globalminusone.data.LayoutMutationResult
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.operations.componentPath
import com.example.globalminusone.ui.panel.gesture.restoreDefaultCardSize

internal data class PropertyCardTarget(val pageId: String, val componentId: String)
internal enum class CardPropertyAction { DELETE, RESET_STYLE, RESTORE_SIZE }

/** The repository owns atomic identity resolution and acceptance; size derives from its current tree. */
internal fun executeCardPropertyAction(repository: MinusOneRepository, target: PropertyCardTarget,
    action: CardPropertyAction): LayoutMutationResult = when (action) {
    CardPropertyAction.DELETE -> repository.tryRemoveComponent(target.pageId, target.componentId)
    CardPropertyAction.RESET_STYLE -> repository.tryResetComponentStyle(target.pageId, target.componentId)
    CardPropertyAction.RESTORE_SIZE -> repository.tryUpdateComponent(target.pageId, target.componentId) { current ->
        val state = repository.state.value
        val index = state.pages.indexOfFirst { it.id == target.pageId }
        val components = state.pages[index].components
        val parentId = components.componentPath(current.id)?.dropLast(1)?.lastOrNull()
        repository.prepareNewComponent(index, restoreDefaultCardSize(current, state.advancedConfig.gridColumns), parentId)
    }
}
