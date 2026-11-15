package com.example.globalminusone.service

import kotlin.math.hypot

/** Screen pixels in, screen pixels out: both axes always share one relative-motion gain. */
internal object AccessibilityMousePolicy {
    const val EDGE_DWELL_MS = 600L

    fun gain(width: Int, height: Int, rangePercent: Int, speed: String): Float {
        val shortSide = minOf(width, height).coerceAtLeast(1).toFloat()
        val longSide = maxOf(width, height).coerceAtLeast(1).toFloat()
        val travel = shortSide * rangePercent.coerceIn(20, 80) / 100f
        val multiplier = when (speed) {
            "慢" -> 0.65f
            "快" -> 1.5f
            else -> 1f
        }
        return longSide / travel * multiplier
    }

    fun move(x: Float, y: Float, dx: Float, dy: Float, width: Int, height: Int,
             rangePercent: Int, speed: String): Pair<Float, Float> {
        val gain = gain(width, height, rangePercent, speed)
        return (x + dx * gain).coerceIn(0f, (width - 1).coerceAtLeast(0).toFloat()) to
            (y + dy * gain).coerceIn(0f, (height - 1).coerceAtLeast(0).toFloat())
    }

    fun atEdge(x: Int, y: Int, ballSize: Int, width: Int, height: Int, margin: Int): Boolean =
        x <= margin || y <= margin || x + ballSize >= width - margin || y + ballSize >= height - margin

    fun dwellStillValid(anchorX: Float, anchorY: Float, x: Float, y: Float, slop: Float): Boolean =
        hypot(x - anchorX, y - anchorY) <= slop
}
