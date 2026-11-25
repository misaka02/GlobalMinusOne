package com.example.globalminusone.ui.panel

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf

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
