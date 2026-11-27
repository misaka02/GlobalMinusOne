package com.example.globalminusone.ui.panel.cards

import kotlin.math.roundToInt
import kotlin.math.floor

/** Sampled landmarks; all intervening groups remain reachable by interpolation. */
internal fun appIndexRailTicks(groupCount: Int, capacity: Int): List<Int> {
    if (groupCount <= 0 || capacity <= 0) return emptyList()
    val count = capacity.coerceAtMost(groupCount)
    if (count == 1) return listOf(0)
    return List(count) { slot ->
        (slot.toDouble() * (groupCount - 1) / (count - 1)).roundToInt()
    }
}

/** Match the evenly laid out tick centers, interpolating through omitted groups. */
internal fun appIndexAtRailPosition(y: Float, height: Float, groupCount: Int, ticks: List<Int>): Int? {
    // The compact first–last range has no individual landmarks. A dense rail
    // preserves the original boundary behavior exactly, including float rounding.
    if (ticks.size < 2 || ticks.size == groupCount) return appIndexAtPosition(y, height, groupCount)
    if (height <= 0f || groupCount <= 0 || !y.isFinite()) return null
    val position = (y / height * ticks.size - 0.5f).coerceIn(0f, ticks.lastIndex.toFloat())
    val lower = floor(position).toInt()
    val upper = (lower + 1).coerceAtMost(ticks.lastIndex)
    return (ticks[lower] + (ticks[upper] - ticks[lower]) * (position - lower))
        .roundToInt().coerceIn(0, groupCount - 1)
}
