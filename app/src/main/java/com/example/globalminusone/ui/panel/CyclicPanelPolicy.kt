package com.example.globalminusone.ui.panel

internal fun realPanelPage(virtual: Int, count: Int): Int = Math.floorMod(virtual, count.coerceAtLeast(1))
internal fun centeredPanelPage(real: Int, count: Int): Int = if (count <= 1) 0 else count + Math.floorMod(real, count)
internal fun nearestPanelPage(real: Int, count: Int, current: Int): Int {
    if (count <= 1) return 0
    val index = Math.floorMod(real, count)
    return (0..2).map { it * count + index }.minBy { kotlin.math.abs(it - current) }
}
