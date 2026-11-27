package com.example.globalminusone.service.overlay

import com.example.globalminusone.data.TriggerConfig
import com.example.globalminusone.data.TriggerPosition
import com.example.globalminusone.data.TriggerWay
import kotlin.math.roundToInt

/** Pixel geometry only. The service retains inset acquisition, gravity and window ownership. */
internal data class EdgeTriggerWindowLayout(val width: Int, val height: Int, val x: Int, val y: Int)

internal fun calculateEdgeTriggerWindowLayout(
    position: TriggerPosition,
    screenWidth: Int,
    screenHeight: Int,
    density: Float,
    config: TriggerConfig,
    systemGestureInsets: Pair<Int, Int>,
    imeBottomPx: Int
): EdgeTriggerWindowLayout {
    val availableVerticalHeight = (screenHeight - imeBottomPx).coerceAtLeast((72 * density).roundToInt())
    return when (position) {
        TriggerPosition.LEFT, TriggerPosition.RIGHT -> {
            val widthPx = (screenWidth * (config.touchWidthPercent.coerceIn(1, 20) / 100f)).roundToInt()
                .coerceAtLeast((10 * density).roundToInt())
            val heightPx = (screenHeight * (config.touchHeightPercent.coerceIn(5, 100) / 100f)).roundToInt()
                .coerceAtLeast((36 * density).roundToInt())
                .coerceAtMost(availableVerticalHeight)
            val baseY = ((screenHeight - heightPx) * (config.touchOffsetPercent.coerceIn(0, 100) / 100f)).roundToInt()
            val yPos = if (imeBottomPx > 0) baseY.coerceAtMost((availableVerticalHeight - heightPx).coerceAtLeast(0)) else baseY
            val xPos = SidePullTriggerPolicy.resolveTriggerBarLeftX(
                position = position,
                screenWidth = screenWidth,
                barWidthPx = widthPx,
                sidePull = config.triggerWay == TriggerWay.SIDE_PULLDOWN,
                leftInsetPx = systemGestureInsets.first,
                rightInsetPx = systemGestureInsets.second,
                customOffsetDp = config.sidePullHorizontalOffsetDp,
                density = density
            )
            EdgeTriggerWindowLayout(widthPx, heightPx, xPos, yPos)
        }
        TriggerPosition.TOP, TriggerPosition.BOTTOM -> {
            val widthPx = (screenWidth * (config.touchHeightPercent.coerceIn(5, 100) / 100f)).roundToInt()
                .coerceAtLeast((36 * density).roundToInt())
            val heightPx = (screenHeight * (config.touchWidthPercent.coerceIn(1, 20) / 100f)).roundToInt()
                .coerceAtLeast((10 * density).roundToInt())
            val xPos = ((screenWidth - widthPx) * (config.touchOffsetPercent.coerceIn(0, 100) / 100f)).roundToInt()
            EdgeTriggerWindowLayout(widthPx, heightPx, xPos, 0)
        }
        TriggerPosition.BOTH -> {
            throw IllegalArgumentException("BOTH is handled as separate LEFT and RIGHT triggers")
        }
    }
}
