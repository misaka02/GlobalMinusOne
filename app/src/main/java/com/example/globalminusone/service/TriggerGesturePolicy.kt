package com.example.globalminusone.service

internal enum class TriggerSwipeDirection { UP, DOWN, LEFT, RIGHT }

/** Action distance is a user preference; touch slop remains the independent tap/move boundary. */
internal object TriggerGesturePolicy {
    const val FLING_VELOCITY_PROGRESS_PER_SEC = 1.15f

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

    fun followExtent(
        direction: TriggerSwipeDirection,
        panelWidth: Float,
        screenHeight: Float
    ): Float = when (direction) {
        TriggerSwipeDirection.LEFT, TriggerSwipeDirection.RIGHT -> panelWidth
        else -> screenHeight * .7f
    }.takeIf { it.isFinite() && it > 0f } ?: 1f

    fun followProgress(
        direction: TriggerSwipeDirection,
        dx: Float,
        dy: Float,
        panelWidth: Float,
        screenHeight: Float
    ): Float = (projectedDistance(direction, dx, dy) / followExtent(direction, panelWidth, screenHeight)).coerceIn(0f, 1f)

    fun followProgressFromOffset(
        direction: TriggerSwipeDirection,
        dx: Float,
        dy: Float,
        startProjectedPx: Float,
        panelWidth: Float,
        screenHeight: Float
    ): Float {
        if (!startProjectedPx.isFinite()) return 0f
        val delta = (projectedDistance(direction, dx, dy) - startProjectedPx.coerceAtLeast(0f)).coerceAtLeast(0f)
        return (delta / followExtent(direction, panelWidth, screenHeight)).coerceIn(0f, 1f)
    }

    fun velocityToProgressPerSec(
        direction: TriggerSwipeDirection,
        vxPxPerSec: Float,
        vyPxPerSec: Float,
        panelWidth: Float,
        screenHeight: Float
    ): Float {
        if (!vxPxPerSec.isFinite() || !vyPxPerSec.isFinite()) return 0f
        return (projectedDistance(direction, vxPxPerSec, vyPxPerSec) / followExtent(direction, panelWidth, screenHeight))
            .coerceIn(-24f, 24f)
    }

    fun shouldSettleOpen(
        progress: Float,
        velocityProgressPerSec: Float,
        inwardDistancePx: Float,
        minimumDistanceDp: Int,
        density: Float
    ): Boolean {
        if (!progress.isFinite() || !velocityProgressPerSec.isFinite()) {
            return reachesSwipeDistance(inwardDistancePx, minimumDistanceDp, density)
        }
        if (velocityProgressPerSec >= FLING_VELOCITY_PROGRESS_PER_SEC) return true
        if (velocityProgressPerSec <= -FLING_VELOCITY_PROGRESS_PER_SEC) return false
        return progress >= 0.42f || reachesSwipeDistance(inwardDistancePx, minimumDistanceDp, density)
    }

    fun reachesSwipeDistance(distancePx: Float, minimumDistanceDp: Int, density: Float): Boolean =
        distancePx >= minimumDistanceDp.coerceIn(16, 160) * density

    fun isTap(hasMoved: Boolean, distancePx: Float, touchSlopPx: Float): Boolean =
        !hasMoved && distancePx < touchSlopPx
}
