package com.example.globalminusone.service.overlay

import com.example.globalminusone.data.TriggerPosition
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.roundToInt

internal enum class SidePullRecognition { WAITING, FOLLOW, CANCEL }

/** Physical-side placement and downward recognition, shared by the production View and JVM tests. */
internal object SidePullTriggerPolicy {
    fun supports(position: TriggerPosition): Boolean = position == TriggerPosition.LEFT ||
        position == TriggerPosition.RIGHT || position == TriggerPosition.BOTH

    fun insetOffsetPx(systemGestureInsetPx: Int, density: Float): Int {
        val scale = density.takeIf { it.isFinite() && it > 0f } ?: 1f
        return ceil(maxOf(24f * scale, systemGestureInsetPx.coerceAtLeast(0) + 8f * scale)).toInt()
    }

    fun sideInsetOffsetPx(position: TriggerPosition, leftInsetPx: Int, rightInsetPx: Int, density: Float): Int {
        require(position == TriggerPosition.LEFT || position == TriggerPosition.RIGHT) { "双侧应分别计算左右触发条" }
        return insetOffsetPx(if (position == TriggerPosition.LEFT) leftInsetPx else rightInsetPx, density)
    }

    /**
     * Resolves the horizontal distance from the physical left/right screen edge in pixels:
     * - `customOffsetDp >= 0`: explicit user-chosen horizontal offset (`0dp` = flush at screen edge, up to `160dp`).
     * - `customOffsetDp < 0` (default `-1`): automatic system back-gesture inset avoidance.
     */
    fun resolveEdgeInsetPx(
        position: TriggerPosition,
        leftInsetPx: Int,
        rightInsetPx: Int,
        customOffsetDp: Int,
        density: Float
    ): Int {
        require(position == TriggerPosition.LEFT || position == TriggerPosition.RIGHT) { "双侧应分别计算左右触发条" }
        val scale = density.takeIf { it.isFinite() && it > 0f } ?: 1f
        if (customOffsetDp >= 0) {
            return (customOffsetDp.coerceIn(0, 160) * scale).roundToInt()
        }
        return sideInsetOffsetPx(position, leftInsetPx, rightInsetPx, scale)
    }

    /**
     * Computes deterministic physical `Gravity.LEFT` `x` coordinate in screen pixels for BOTH
     * `TriggerPosition.LEFT` and `TriggerPosition.RIGHT`, avoiding OEM `Gravity.RIGHT` + `FLAG_LAYOUT_NO_LIMITS`
     * placement quirks on ColorOS / Android 16.
     */
    fun resolveTriggerBarLeftX(
        position: TriggerPosition,
        screenWidth: Int,
        barWidthPx: Int,
        sidePull: Boolean,
        leftInsetPx: Int,
        rightInsetPx: Int,
        customOffsetDp: Int,
        density: Float
    ): Int {
        require(position == TriggerPosition.LEFT || position == TriggerPosition.RIGHT) { "仅支持左右侧触发条水平坐标计算" }
        val maxX = (screenWidth.coerceAtLeast(1) - barWidthPx.coerceAtLeast(1)).coerceAtLeast(0)
        val edgeInset = if (sidePull) {
            resolveEdgeInsetPx(position, leftInsetPx, rightInsetPx, customOffsetDp, density)
        } else {
            0
        }
        return when (position) {
            TriggerPosition.LEFT -> edgeInset.coerceIn(0, maxX)
            TriggerPosition.RIGHT -> (maxX - edgeInset).coerceIn(0, maxX)
            else -> 0
        }
    }

    fun sidePullRecognition(dx: Float, dy: Float, touchSlopPx: Float, minimumDistanceDp: Int, density: Float): SidePullRecognition {
        if (!dx.isFinite() || !dy.isFinite()) return SidePullRecognition.CANCEL
        if (hypot(dx, dy) <= touchSlopPx.coerceAtLeast(0f)) return SidePullRecognition.WAITING
        if (dy <= 0f || dy < abs(dx) * 1.2f) return SidePullRecognition.CANCEL
        return if (TriggerGesturePolicy.reachesSwipeDistance(dy, minimumDistanceDp, density))
            SidePullRecognition.FOLLOW else SidePullRecognition.WAITING
    }

    fun followProgress(dy: Float, screenHeightPx: Int): Float =
        (dy.coerceAtLeast(0f) / (screenHeightPx.coerceAtLeast(1) * .35f)).coerceIn(0f, 1f)
}
