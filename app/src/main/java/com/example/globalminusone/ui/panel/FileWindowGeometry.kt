package com.example.globalminusone.ui.panel

internal const val FLOATING_WINDOW_MIN_HEIGHT_DP = 240

internal data class WorkspaceWindowBounds(val x: Float, val y: Float, val width: Float, val height: Float)

/** Fractions are measured against the full overlay, never the side panel's rectangle. */
internal fun workspaceWindowBounds(overlayWidth: Float, overlayHeight: Float, visibleHeight: Float,
    minWidth: Float, minHeight: Float, widthFraction: Float, heightFraction: Float,
    xFraction: Float, yFraction: Float): WorkspaceWindowBounds {
    fun Float.valid(fallback: Float) = takeIf { it.isFinite() } ?: fallback
    val sw = overlayWidth.valid(1f).coerceAtLeast(1f)
    val sh = overlayHeight.valid(1f).coerceAtLeast(1f)
    val shown = visibleHeight.valid(sh).coerceIn(1f, sh)
    val width = (widthFraction.valid(.9f) * sw).coerceIn(minWidth.valid(1f).coerceIn(1f, sw), sw)
    val height = (heightFraction.valid(.55f) * sh).coerceIn(minHeight.valid(1f).coerceIn(1f, sh), sh).coerceAtMost(shown)
    return WorkspaceWindowBounds((xFraction.valid(.05f) * sw).coerceIn(0f, sw - width),
        (yFraction.valid(.18f) * sh).coerceIn(0f, shown - height), width, height)
}

/** Intersect with the actual screen area above IME; an already resized parent is not inset twice. */
internal fun visibleFileWindowHeight(parentHeight: Float, parentTopOnScreen: Float, screenHeight: Float, imeBottom: Int): Float {
    if (imeBottom <= 0) return parentHeight.coerceAtLeast(1f)
    return minOf(parentHeight, screenHeight - imeBottom - parentTopOnScreen).coerceAtLeast(1f)
}

/** Collapse changes only presentation, never the stored expanded size or position. */
internal fun floatingWindowPresentationBounds(expanded: WorkspaceWindowBounds, overlayHeight: Float,
    visibleHeight: Float, titleHeight: Float, yFraction: Float, contentVisible: Boolean): WorkspaceWindowBounds {
    if (contentVisible) return expanded
    val overlay = overlayHeight.takeIf { it.isFinite() }?.coerceAtLeast(1f) ?: 1f
    val available = (visibleHeight.takeIf { it.isFinite() } ?: overlay).coerceIn(1f, overlay)
    val height = (titleHeight.takeIf { it.isFinite() } ?: 48f).coerceIn(1f, available)
    val y = (yFraction.takeIf { it.isFinite() } ?: .18f) * overlay
    return expanded.copy(y = y.coerceIn(0f, available - height), height = height)
}
