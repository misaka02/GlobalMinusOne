package com.example.globalminusone.data

import kotlin.math.ceil
import kotlin.math.max

/** Creation-time geometry only. Never migrate saved cards or change serialization defaults. */
internal fun prepareStatisticsCard(item: ComponentItem, columns: Int, hostWidthDp: Float,
    heightUnitDp: Int, fontScale: Float, fillRow: Boolean): ComponentItem {
    if (item.type.category != ComponentCategory.STATISTICS) return item
    val count = columns.coerceIn(8, 64)
    val width = hostWidthDp.takeIf { it.isFinite() && it > 0f } ?: 320f
    val scale = fontScale.takeIf { it.isFinite() && it > 0f } ?: 1f
    val unitX = (width / count).coerceAtLeast(0.1f)
    val unitY = heightUnitDp.takeIf { it > 0 }?.toFloat() ?: unitX
    val header = if (item.showTitle && item.title.isNotBlank()) max(24f, 20f * scale) + 6f else 0f
    val padding = item.paddingTopDp.coerceAtLeast(6) + item.paddingBottomDp.coerceAtLeast(6) +
        item.marginTopDp + item.marginBottomDp
    val contentHeight = if (item.type == ComponentType.STAT_BATTERY_DETAIL) 76f * scale else 58f * scale
    val minimumWidth = if (item.type == ComponentType.STAT_BATTERY_DETAIL) 240f else 112f * scale
    return item.copy(
        w = if (fillRow) count else maxOf(item.w, ceil(minimumWidth / unitX).toInt()).coerceIn(1, count),
        h = maxOf(item.h, ceil((contentHeight + header + padding) / unitY).toInt()).coerceIn(1, 100_000)
    )
}

internal fun prepareFileShortcutCard(item: ComponentItem, columns: Int, hostWidthDp: Float,
    heightUnitDp: Int, fontScale: Float): ComponentItem {
    if (item.type != ComponentType.FILE_SHORTCUT) return item
    val scale = fontScale.takeIf { it.isFinite() && it > 0f } ?: 1f
    val unitY = heightUnitDp.takeIf { it > 0 }?.toFloat()
        ?: ((hostWidthDp.takeIf { it.isFinite() && it > 0f } ?: 320f) / columns.coerceIn(8, 64)).coerceAtLeast(.1f)
    val header = if (item.showTitle && item.title.isNotBlank()) max(24f, 20f * scale) + 6f else 0f
    val wrapper = item.paddingTopDp.coerceAtLeast(6) + item.paddingBottomDp.coerceAtLeast(6) +
        item.marginTopDp + item.marginBottomDp
    return item.copy(h = maxOf(item.h, ceil((34f + 14f * scale + 10f + header + wrapper) / unitY).toInt()))
}
