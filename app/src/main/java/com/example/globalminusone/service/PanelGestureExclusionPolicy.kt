package com.example.globalminusone.service

internal data class PanelEdgeRect(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** The actual edge trigger is the only exclusion owner; the full panel never reserves a second band. */
internal fun triggerEdgeExclusions(width: Int, height: Int, density: Float, visible: Boolean,
    verticalEdge: Boolean): List<PanelEdgeRect> {
    if (!visible || !verticalEdge || width <= 0 || height <= 0 || !density.isFinite() || density <= 0) return emptyList()
    val extent = minOf((200 * density).toInt().coerceAtLeast(0), height)
    if (extent == 0) return emptyList()
    val start = (height - extent) / 2
    return listOf(PanelEdgeRect(0, start, width, start + extent))
}
