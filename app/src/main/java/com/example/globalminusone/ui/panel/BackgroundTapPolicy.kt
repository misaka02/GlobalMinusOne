package com.example.globalminusone.ui.panel

import kotlin.math.hypot

/** A full-window background has no scrollable parent to cancel a click for it. Track movement explicitly. */
internal class BackgroundTapPolicy(private val downX: Float, private val downY: Float,
    private val downTimeMillis: Long, touchSlopPx: Float, private val longPressMillis: Long) {
    private val slop = touchSlopPx.takeIf { it.isFinite() && it >= 0f } ?: 0f
    private var previousX = downX
    private var previousY = downY
    private var travelled = 0f
    var cancelled = !downX.isFinite() || !downY.isFinite()
        private set

    fun observe(x: Float, y: Float, timeMillis: Long, pointerCount: Int,
        consumed: Boolean = false, inBounds: Boolean = true) {
        if (cancelled) return
        if (!x.isFinite() || !y.isFinite() || pointerCount != 1 || consumed || !inBounds ||
            timeMillis < downTimeMillis || timeMillis - downTimeMillis >= longPressMillis) {
            cancelled = true
            return
        }
        travelled += hypot(x - previousX, y - previousY)
        previousX = x; previousY = y
        if (travelled > slop || hypot(x - downX, y - downY) > slop) cancelled = true
    }

    fun cancel() { cancelled = true }
}
