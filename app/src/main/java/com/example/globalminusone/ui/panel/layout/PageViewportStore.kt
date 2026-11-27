package com.example.globalminusone.ui.panel.layout

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.collect

/** Screen position belongs to the real page, not one of the pager's equivalent virtual copies. */
internal class PageViewportMemory {
    var freeScrollPx: Int = 0
    var editScrollPx: Int = 0
    var linearAnchor: LinearViewportAnchor? = null
    var restoreRevision: Long = 0
        private set

    fun recordLinearAnchor(anchor: LinearViewportAnchor, expectedRevision: Long): Boolean {
        if (restoreRevision != expectedRevision) return false
        linearAnchor = anchor
        return true
    }

    fun recordEditingViewport(scrollPx: Int, anchor: LinearViewportAnchor?, expectedRevision: Long): Boolean {
        if (restoreRevision != expectedRevision) return false
        editScrollPx = scrollPx
        if (anchor != null) linearAnchor = anchor
        return true
    }

    internal fun checkpoint() = PageViewportCheckpoint(freeScrollPx, editScrollPx, linearAnchor)
    internal fun restore(checkpoint: PageViewportCheckpoint) {
        freeScrollPx = checkpoint.freeScrollPx
        editScrollPx = checkpoint.editScrollPx
        linearAnchor = checkpoint.linearAnchor
        restoreRevision++ // invalidate collectors from the discarded editing canvas.
    }
}

internal data class PageViewportCheckpoint(val freeScrollPx: Int, val editScrollPx: Int, val linearAnchor: LinearViewportAnchor?)

internal class PageViewportStore {
    private val pages = mutableMapOf<String, PageViewportMemory>()
    private var editingCheckpoint: MutableMap<String, PageViewportCheckpoint>? = null
    fun page(id: String) = pages.getOrPut(id) {
        PageViewportMemory().also { editingCheckpoint?.putIfAbsent(id, it.checkpoint()) }
    }
    fun retain(ids: Set<String>) { pages.keys.retainAll(ids) }

    /** Capture before editing children can publish anchors for their draft list. Repeated entry is harmless. */
    fun beginEditing() {
        if (editingCheckpoint == null) editingCheckpoint = pages.mapValues { it.value.checkpoint() }.toMutableMap()
    }

    /** A save keeps the final viewport. Only an explicit discard rolls back page-local screen positions. */
    fun finishEditing(save: Boolean) {
        val checkpoint = editingCheckpoint ?: return
        editingCheckpoint = null
        if (!save) checkpoint.forEach { (id, position) -> page(id).restore(position) }
    }
}

internal class ViewportVisit {
    var active = false
    var editing = false
    var layoutAtRequest: Any? = null
    var restoreRevision: Long = -1
}

internal fun initialLinearViewport(memory: PageViewportMemory, ids: List<String>): LazyListState {
    val position = memory.linearAnchor?.let { lazyPositionForAnchor(it, ids) } ?: (0 to 0)
    return LazyListState(position.first, position.second)
}

internal fun initialFreeViewport(memory: PageViewportMemory) = ScrollState(memory.freeScrollPx.coerceAtLeast(0))

internal val LocalPageViewport = staticCompositionLocalOf<PageViewportMemory?> { null }
internal val LocalPageViewportActive = compositionLocalOf { true }

/**
 * Isolated leaf observer so `LocalPageViewportActive` transitions during page
 * swipes never recompose the parent layout tree or its cards.
 */
@Composable
internal fun FreePageViewportObserver(
    pageId: String,
    viewportMemory: PageViewportMemory,
    viewportRevision: Long,
    scrollState: ScrollState
) {
    val pageActive = LocalPageViewportActive.current
    val visit = remember(pageId) { ViewportVisit() }
    val resumeOffset = if (pageActive && !visit.active) viewportMemory.freeScrollPx else null
    SideEffect {
        resumeOffset?.let { scrollState.dispatchRawDelta((it - scrollState.value).toFloat()) }
        visit.active = pageActive
    }
    LaunchedEffect(pageId, pageActive, viewportRevision) {
        if (pageActive) snapshotFlow { scrollState.value }.collect {
            if (visit.active && viewportMemory.restoreRevision == viewportRevision) {
                viewportMemory.freeScrollPx = it
            }
        }
    }
}

@Composable
internal fun LinearPageViewportObserver(
    pageId: String,
    componentIds: List<String>,
    viewport: PageViewportMemory,
    list: LazyListState,
    visit: ViewportVisit
) {
    val active = LocalPageViewportActive.current
    val restoreRevision = viewport.restoreRevision
    val needsRestore = active && (!visit.active || visit.editing || visit.restoreRevision != restoreRevision)
    val restorePosition = if (needsRestore) {
        viewport.linearAnchor?.let { lazyPositionForAnchor(it, componentIds) }
    } else null
    SideEffect {
        if (needsRestore) {
            // A remeasure request applies the saved position before painting; no visible scrollTo effect.
            restorePosition?.let { (index, offset) ->
                visit.layoutAtRequest = list.layoutInfo
                list.requestScrollToItem(index, offset)
            }
        }
        visit.active = active
        visit.editing = false
        visit.restoreRevision = restoreRevision
    }
    LaunchedEffect(pageId, componentIds, active, restoreRevision) {
        if (!active) return@LaunchedEffect
        snapshotFlow { list.layoutInfo }.collect { info ->
            if (visit.active && !visit.editing && info !== visit.layoutAtRequest && info.visibleItemsInfo.isNotEmpty()) {
                anchorFromLazyPosition(componentIds, list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)?.let {
                    viewport.recordLinearAnchor(it, restoreRevision)
                }
            }
        }
    }
}
