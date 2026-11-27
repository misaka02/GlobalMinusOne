package com.example.globalminusone.ui.panel.editor

import com.example.globalminusone.data.policy.PanelBounds
import com.example.globalminusone.data.ThemeConfig
import com.example.globalminusone.data.policy.normalizedPanelGeometry
import com.example.globalminusone.data.policy.panelBounds
import kotlin.math.roundToInt

internal enum class PanelGeometryHandle { MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
internal data class PanelGeometryFinish(val theme: ThemeConfig, val persist: Boolean)

/** Screen-space deltas from the initial pointer, independent of the moving handle's layout. */
internal class PanelGeometryGesture(private val start: ThemeConfig, private val screenWidth: Float,
    private val screenHeight: Float, private val right: Boolean, private val handle: PanelGeometryHandle) {
    private val bounds = panelBounds(screenWidth, screenHeight, start, right)
    private var latest = start
    private var finished = false
    fun update(dx: Float, dy: Float): ThemeConfig? {
        if (finished || screenWidth <= 0 || screenHeight <= 0 || !dx.isFinite() || !dy.isFinite()) return null
        val minW = screenWidth * .3f
        val minH = screenHeight * .3f
        var left = bounds.x; var top = bounds.y
        var end = bounds.x + bounds.width; var bottom = bounds.y + bounds.height
        if (handle == PanelGeometryHandle.MOVE) {
            left = (bounds.x + dx).coerceIn(0f, screenWidth - bounds.width)
            top = (bounds.y + dy).coerceIn(0f, screenHeight - bounds.height)
            end = left + bounds.width; bottom = top + bounds.height
        } else {
            if (handle == PanelGeometryHandle.TOP_LEFT || handle == PanelGeometryHandle.BOTTOM_LEFT)
                left = (bounds.x + dx).coerceIn(0f, end - minW)
            else end = (end + dx).coerceIn(left + minW, screenWidth)
            if (handle == PanelGeometryHandle.TOP_LEFT || handle == PanelGeometryHandle.TOP_RIGHT)
                top = (bounds.y + dy).coerceIn(0f, bottom - minH)
            else bottom = (bottom + dy).coerceIn(top + minH, screenHeight)
        }
        latest = themeForPanelBounds(start, PanelBounds(left, top, end - left, bottom - top), screenWidth, screenHeight, right)
        return latest
    }
    /** The same result drives preview and persistence; duplicate terminal events cannot write twice. */
    fun finish(cancelled: Boolean): PanelGeometryFinish? {
        if (finished) return null
        finished = true
        return PanelGeometryFinish(if (cancelled) start else latest, !cancelled && latest != start)
    }
}

internal fun themeForPanelBounds(base: ThemeConfig, bounds: PanelBounds, width: Float, height: Float, right: Boolean): ThemeConfig {
    if (width <= 0 || height <= 0) return base
    val widthPercent = (bounds.width / width * 100).roundToInt().coerceIn(30, 100)
    val heightPercent = (bounds.height / height * 100).roundToInt().coerceIn(30, 100)
    val gapX = width - width * widthPercent / 100f
    val gapY = height - height * heightPercent / 100f
    val x = bounds.x.coerceIn(0f, gapX)
    val y = bounds.y.coerceIn(0f, gapY)
    return base.copy(panelWidthPercent = widthPercent, panelHeightPercent = heightPercent,
        panelHorizontalOffsetPercent = if (gapX < 1f) 0 else ((if (right) gapX - x else x) / gapX * 100).roundToInt(),
        panelVerticalPositionPercent = if (gapY < 1f) 50 else (y / gapY * 100).roundToInt()).normalizedPanelGeometry()
}
