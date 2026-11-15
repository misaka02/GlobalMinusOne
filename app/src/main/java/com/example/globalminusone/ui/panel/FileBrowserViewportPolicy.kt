package com.example.globalminusone.ui.panel

/** Keep three compact file rows available; overflow tools scroll independently above them. */
internal fun fileBrowserControlsHeightDp(availableHeight: Float, selecting: Boolean, rowHeightDp: Float = 44f, editorHeightDp: Float = 0f, selectionToolbarHeightDp: Float = 56f): Float {
    val height = (availableHeight - editorHeightDp.coerceAtLeast(0f)).coerceAtLeast(0f)
    val reserved = 3f * rowHeightDp.coerceAtLeast(44f) + 16f + if (selecting) selectionToolbarHeightDp.coerceAtLeast(0f) else 0f
    return (height - reserved).coerceAtLeast(40f).coerceAtMost(minOf(180f, height * .4f))
}

/** Finger down moves toward the start; do not claim an impossible scroll at a boundary. */
internal fun fileListCanScroll(deltaY: Float, canScrollBackward: Boolean, canScrollForward: Boolean): Boolean = when {
    deltaY > 0 -> canScrollBackward
    deltaY < 0 -> canScrollForward
    else -> false
}

/** Use available dp divided by font scale, so both density and larger text receive space. */
internal fun fileBrowserNarrowChrome(widthDp: Float, fontScale: Float): Boolean =
    widthDp / fontScale.coerceAtLeast(1f) < 320f

internal fun fileBrowserToolbarColumns(widthDp: Float, fontScale: Float, actionCount: Int): Int =
    if (widthDp / fontScale.coerceAtLeast(1f) < 176f) minOf(2, actionCount).coerceAtLeast(1)
    else actionCount.coerceAtLeast(1)
