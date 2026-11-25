package com.example.globalminusone.ui.panel

import com.example.globalminusone.data.PageConfig
import kotlin.math.hypot
import kotlin.math.roundToInt

/** A hold tolerates only tiny sensor jitter, never a scroll or an out-and-back path. */
internal class StationaryHoldPolicy(private val maxDriftPx: Float, private val maxPathPx: Float) {
    private var x = 0f
    private var y = 0f
    private var path = 0f
    var cancelled = false
        private set
    fun observe(dx: Float, dy: Float, pointers: Int, pressed: Boolean, consumedMovement: Boolean): Boolean {
        return recordMotion(dx, dy, pointers != 1 || !pressed || consumedMovement)
    }
    /** UP may contain the last movement even when no MOVE was delivered. */
    fun observeRelease(dx: Float, dy: Float, pointers: Int, consumed: Boolean): Boolean {
        return recordMotion(dx, dy, pointers != 1 || consumed)
    }
    private fun recordMotion(dx: Float, dy: Float, invalid: Boolean): Boolean {
        x += dx; y += dy; path += hypot(dx, dy)
        cancelled = cancelled || invalid ||
            hypot(x, y) > maxDriftPx || path > maxPathPx
        return !cancelled
    }
}

/** Linear dragging changes order only: every component object and every saved bound survives. */
internal fun reorderLinearDrag(page: PageConfig, id: String, deltaY: Float, rowHeightPx: Float): PageConfig {
    val from = page.components.indexOfFirst { it.id == id }
    if (from < 0 || rowHeightPx <= 0f || !deltaY.isFinite()) return page
    val to = (from + (deltaY / rowHeightPx).roundToInt()).coerceIn(0, page.components.lastIndex)
    if (from == to) return page
    val ordered = page.components.toMutableList()
    ordered.add(to, ordered.removeAt(from))
    return page.copy(components = ordered)
}

/** The target comes from measured visible rows, so different card heights do not skew ordering. */
internal fun moveLinearPreview(order: List<String>, moving: String, target: String): List<String> {
    val from = order.indexOf(moving)
    val to = order.indexOf(target)
    if (from < 0 || to < 0 || from == to) return order
    return order.toMutableList().apply { add(to, removeAt(from)) }
}

internal fun commitCardGeometry(
    item: com.example.globalminusone.data.ComponentItem,
    x: Int, y: Int, w: Int, h: Int, resizeWidth: Boolean, resizeHeight: Boolean
): com.example.globalminusone.data.ComponentItem = item.copy(
    x = x, y = y,
    w = if (resizeWidth) w else item.w,
    h = if (resizeHeight) h else item.h,
    autoHeight = if (resizeHeight) false else item.autoHeight
)

/**
 * Resizing a non-icon card inside a GRID container clears explicit grid spans on the resized axes
 * so the dragged pixel/grid dimensions take effect immediately rather than being pinned back by gridSpanX/Y.
 */
internal fun commitContainerGridChildResize(
    item: com.example.globalminusone.data.ComponentItem,
    w: Int, yDeltaOrUnused: Int = 0, h: Int, resizeWidth: Boolean, resizeHeight: Boolean
): com.example.globalminusone.data.ComponentItem = item.copy(
    w = if (resizeWidth) w else item.w,
    h = if (resizeHeight) h else item.h,
    gridSpanX = if (resizeWidth) 0 else item.gridSpanX,
    gridSpanY = if (resizeHeight) 0 else item.gridSpanY,
    autoHeight = if (resizeHeight) false else item.autoHeight
)

internal fun restoreDefaultCardSize(item: com.example.globalminusone.data.ComponentItem, columns: Int) =
    item.copy(w = item.type.defaultW.coerceIn(1, (columns - item.x).coerceAtLeast(1)),
        h = item.type.defaultH, gridSpanX = 0, gridSpanY = 0, autoHeight = item.type in setOf(
            com.example.globalminusone.data.ComponentType.APP_ICON, com.example.globalminusone.data.ComponentType.CUSTOM_SHORTCUT,
            com.example.globalminusone.data.ComponentType.STATIC_SHORTCUT))
internal fun canCommitLayout(editMode: Boolean, layoutLocked: Boolean): Boolean = editMode && !layoutLocked

internal fun com.example.globalminusone.data.GlobalMinusOneState.hasLinearCardTools(): Boolean =
    editMode && !advancedConfig.layoutLocked && selectedComponentId != null &&
        pages.getOrNull(currentPageIndex)?.let { page ->
            page.layoutType == com.example.globalminusone.data.PageLayoutType.LINEAR &&
                page.components.any { it.id == selectedComponentId }
        } == true

internal fun isSuccessfulDragRelease(previousPressed: Boolean, pressed: Boolean, consumed: Boolean): Boolean =
    previousPressed && !pressed && !consumed

/** A toolbar changes the viewport origin, not saved card coordinates. Keep a scrolled card
 * at the same screen position; an untouched page at the top stays at the top. */
internal fun preserveViewportScroll(scroll: Int, previousHeight: Int, nextHeight: Int): Int =
    if (scroll <= 0) 0 else (scroll + previousHeight - nextHeight).coerceAtLeast(0)

/** Commit exactly the displayed order, retaining current objects and rejecting a stale tree. */
internal fun commitLinearPreview(page: PageConfig, order: List<String>?): PageConfig {
    if (order == null || order.size != page.components.size || order.toSet().size != order.size ||
        order.toSet() != page.components.map { it.id }.toSet()) return page
    val current = page.components.associateBy { it.id }
    return page.copy(components = order.map { current.getValue(it) })
}

internal fun stableContentExtent(content: Float, scroll: Int, viewport: Float): Float =
    maxOf(content, scroll.coerceAtLeast(0) + viewport.coerceAtLeast(0f))

internal fun resizedLinearHeight(startRows: Int, dy: Float, unitPx: Float, maxRows: Int): Int =
    (startRows + (dy / unitPx.coerceAtLeast(1f)).roundToInt()).coerceIn(1, maxRows.coerceIn(1, 120))

internal fun resizedCardSize(w: Int, h: Int, dx: Float, dy: Float, sx: Float, sy: Float, columns: Int): Pair<Int, Int> =
    (w + (dx / sx.coerceAtLeast(1f)).roundToInt()).coerceIn(1, columns.coerceAtLeast(1)) to
        (h + (dy / sy.coerceAtLeast(1f)).roundToInt()).coerceIn(1, 120)
