package com.example.globalminusone.ui.panel

internal data class EditToolsAnchor(val x: Float = 0f, val y: Float = 0f) {
    fun normalized() = EditToolsAnchor(x.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f,
        y.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f)
}

internal fun editToolsPosition(anchor: EditToolsAnchor, width: Float, height: Float, hostWidth: Float, hostHeight: Float): EditToolsPosition {
    val safe = anchor.normalized()
    return EditToolsPosition(safe.x * (hostWidth - width).coerceAtLeast(0f), safe.y * (hostHeight - height).coerceAtLeast(0f))
}

internal fun editToolsAnchor(position: EditToolsPosition, previous: EditToolsAnchor,
    width: Float, height: Float, hostWidth: Float, hostHeight: Float): EditToolsAnchor {
    val gapX = (hostWidth - width).coerceAtLeast(0f)
    val gapY = (hostHeight - height).coerceAtLeast(0f)
    return EditToolsAnchor(if (gapX > 0f) position.x / gapX else previous.x,
        if (gapY > 0f) position.y / gapY else previous.y).normalized()
}
