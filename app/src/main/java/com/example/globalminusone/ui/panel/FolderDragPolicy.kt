package com.example.globalminusone.ui.panel

import androidx.compose.ui.geometry.Offset

/** Sample in a stable window/screen space, never relative to the tile following the finger. */
internal class StableDragMotion(initial: Offset) {
    private var previous = initial
    fun sample(point: Offset): Offset {
        if (!point.x.isFinite() || !point.y.isFinite()) return Offset.Zero
        val delta = point - previous
        previous = point
        return delta
    }
}

internal data class FolderDragSlot(val id: String, val x: Float, val y: Float,
    val width: Float, val height: Float)

/** Freeze the hit slots at DOWN/drag-start. Animated siblings are previews, not new
 * hit targets: feeding their changing positions back into ordering causes oscillation. */
internal class FolderDragOrder(private val moving: String, private val slots: List<FolderDragSlot>) {
    private val order = slots.map { it.id }
    private val footprint = slots.firstOrNull { it.id == moving }
    fun preview(x: Float, y: Float): List<String> {
        val tile = footprint ?: return order
        if (!x.isFinite() || !y.isFinite()) return order
        val cx = x + tile.width / 2f
        val cy = y + tile.height / 2f
        val target = slots.indices.minWithOrNull(compareBy<Int> { index ->
            val slot = slots[index]
            val dx = (slot.x - cx).coerceAtLeast(0f) + (cx - slot.x - slot.width).coerceAtLeast(0f)
            val dy = (slot.y - cy).coerceAtLeast(0f) + (cy - slot.y - slot.height).coerceAtLeast(0f)
            dx * dx + dy * dy
        }.thenBy { index ->
            val slot = slots[index]
            val dx = cx - slot.x - slot.width / 2f
            val dy = cy - slot.y - slot.height / 2f
            dx * dx + dy * dy
        }) ?: return order
        val from = order.indexOf(moving)
        if (from < 0 || from == target) return order
        return order.toMutableList().apply { add(target, removeAt(from)) }
    }
}
