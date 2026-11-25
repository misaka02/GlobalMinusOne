package com.example.globalminusone.ui.panel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.LayoutMutationResult
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.PageConfig

/** Only layout/tree data: no configuration owner, persistence, clipboard, profiles or platform services. */
@Immutable
internal data class CardLayoutSnapshot(
    val pages: List<PageConfig>, val editMode: Boolean, val layoutLocked: Boolean,
    val selectedComponentId: String?, val gridColumns: Int, val heightUnitDp: Int,
    val animationDurationMs: Int
)
internal fun GlobalMinusOneState.cardLayoutSnapshot() = CardLayoutSnapshot(
    pages, editMode, advancedConfig.layoutLocked, selectedComponentId,
    advancedConfig.gridColumns, advancedConfig.heightUnitDp, themeConfig.animationDurationMs
)

internal interface CardLayoutActions {
    val current: CardLayoutSnapshot
    val imageAccessRevisions: Map<String, Long>
    @Composable fun observe(): CardLayoutSnapshot
    fun setEditMode(enabled: Boolean): Boolean
    fun selectComponent(componentId: String?)
    fun updateComponent(pageIndex: Int, componentId: String, transform: (ComponentItem) -> ComponentItem): Boolean
    fun removeComponent(pageIndex: Int, componentId: String): Boolean
    fun clearContainerActions(pageIndex: Int, containerId: String): Boolean
    fun transferComponent(sourcePageId: String, componentId: String, targetPageId: String, targetContainerId: String?, copy: Boolean): Boolean
    fun placeChildInContainerGrid(pageIndex: Int, containerId: String, movedId: String, targetCol: Int, targetRow: Int,
        widthDp: Float, stepYDp: Float, iconColumns: Int, fontScale: Float): Boolean
    fun placeActionInContainerGrid(pageIndex: Int, containerId: String, movedId: String, targetCol: Int, targetRow: Int, iconColumns: Int): Boolean
    fun compactContainerGridPlacements(pageIndex: Int, containerId: String, widthDp: Float, stepYDp: Float, iconColumns: Int): Boolean
}

@Composable
internal fun rememberCardLayoutActions(repository: MinusOneRepository): CardLayoutActions = remember(repository) { cardLayoutActions(repository) }

internal fun cardLayoutActions(repository: MinusOneRepository): CardLayoutActions =
    object : CardLayoutActions {
        private val streams = CardStateStreams(repository.state)
        override val current: CardLayoutSnapshot get() = repository.state.value.cardLayoutSnapshot()
        override val imageAccessRevisions: Map<String, Long> get() = repository.imageAccessRevisions.value
        @Composable override fun observe(): CardLayoutSnapshot = streams.observeLayout()
        override fun setEditMode(enabled: Boolean) = repository.setEditMode(enabled)
        override fun selectComponent(componentId: String?) = repository.selectComponent(componentId)
        override fun updateComponent(pageIndex: Int, componentId: String, transform: (ComponentItem) -> ComponentItem) = repository.updateComponent(pageIndex, componentId, transform)
        override fun removeComponent(pageIndex: Int, componentId: String): Boolean {
            val pageId = repository.state.value.pages.getOrNull(pageIndex)?.id ?: return false
            return repository.tryRemoveComponent(pageId, componentId) == LayoutMutationResult.APPLIED
        }
        override fun clearContainerActions(pageIndex: Int, containerId: String) = repository.tryClearContainerActions(pageIndex, containerId)
        override fun transferComponent(sourcePageId: String, componentId: String, targetPageId: String, targetContainerId: String?, copy: Boolean) = repository.transferComponent(sourcePageId, componentId, targetPageId, targetContainerId, copy)
        override fun placeChildInContainerGrid(pageIndex: Int, containerId: String, movedId: String, targetCol: Int, targetRow: Int, widthDp: Float, stepYDp: Float, iconColumns: Int, fontScale: Float) = repository.placeChildInContainerGrid(pageIndex, containerId, movedId, targetCol, targetRow, widthDp, stepYDp, iconColumns, fontScale)
        override fun placeActionInContainerGrid(pageIndex: Int, containerId: String, movedId: String, targetCol: Int, targetRow: Int, iconColumns: Int) = repository.placeActionInContainerGrid(pageIndex, containerId, movedId, targetCol, targetRow, iconColumns)
        override fun compactContainerGridPlacements(pageIndex: Int, containerId: String, widthDp: Float, stepYDp: Float, iconColumns: Int) = repository.compactContainerGridPlacements(pageIndex, containerId, widthDp, stepYDp, iconColumns)
    }
