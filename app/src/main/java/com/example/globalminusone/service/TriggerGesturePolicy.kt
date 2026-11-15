package com.example.globalminusone.service

internal enum class TriggerSwipeDirection { UP, DOWN, LEFT, RIGHT }

/** Action distance is a user preference; touch slop remains the independent tap/move boundary. */
internal object TriggerGesturePolicy {
    /** The configured opening gesture determines the input axis, independently of panel placement. */
    fun projectedDistance(direction: TriggerSwipeDirection, dx: Float, dy: Float): Float {
        if (!dx.isFinite() || !dy.isFinite()) return 0f
        return when (direction) {
            TriggerSwipeDirection.UP -> -dy
            TriggerSwipeDirection.DOWN -> dy
            TriggerSwipeDirection.LEFT -> -dx
            TriggerSwipeDirection.RIGHT -> dx
        }
    }

    fun followProgress(direction: TriggerSwipeDirection, dx: Float, dy: Float,
        panelWidth: Float, screenHeight: Float): Float {
        val extent = when (direction) {
            TriggerSwipeDirection.LEFT, TriggerSwipeDirection.RIGHT -> panelWidth
            else -> screenHeight * .7f
        }.takeIf { it.isFinite() && it > 0f } ?: 1f
        return (projectedDistance(direction, dx, dy) / extent).coerceIn(0f, 1f)
    }

    fun reachesSwipeDistance(distancePx: Float, minimumDistanceDp: Int, density: Float): Boolean =
        distancePx >= minimumDistanceDp.coerceIn(16, 160) * density

    fun isTap(hasMoved: Boolean, distancePx: Float, touchSlopPx: Float): Boolean =
        !hasMoved && distancePx < touchSlopPx
}
