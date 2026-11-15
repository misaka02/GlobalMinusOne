package com.example.globalminusone.browser

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Complete-phone mode keeps a stable canvas; reflow mode changes the same
 * display's real geometry while retaining usable minimum logical dimensions. */
internal object EmbeddedAppViewportPolicy {
    data class Viewport(val width: Int, val height: Int, val densityDpi: Int)
    private const val MAX_EDGE = 4096
    private const val MAX_PIXELS = 8_388_608L

    /** Real reflow preserves host aspect while guaranteeing a usable logical
     * viewport. Extra pixels reduce density-independent crowding, not the host
     * window; the existing projection maps this canvas back into the content box. */
    fun reflow(hostWidth: Int, hostHeight: Int, hostDensityDpi: Int): Viewport {
        val w = hostWidth.coerceAtLeast(1).toDouble()
        val h = hostHeight.coerceAtLeast(1).toDouble()
        val density = hostDensityDpi.coerceIn(72, 640)
        val minWidthDp = if (w > h) 640.0 else 360.0
        val minHeightDp = if (w > h) 360.0 else 640.0
        val grow = max(1.0, max(minWidthDp * density / (160 * w), minHeightDp * density / (160 * h)))
        val rasterScale = min(1.0, min(MAX_EDGE / max(w * grow, h * grow),
            sqrt((MAX_PIXELS - 8192).toDouble() / (w * grow * h * grow))))
        val dpi = floor(density * rasterScale).toInt().coerceIn(72, 640)
        val width = ceil(w * grow * rasterScale).toInt().coerceIn(64, MAX_EDGE)
        val height = ceil(h * grow * rasterScale).toInt().coerceIn(64, MAX_EDGE)
        return Viewport(width, height, dpi)
    }

    fun create(displayWidth: Int, displayHeight: Int, hostDensityDpi: Int): Viewport {
        val w = displayWidth.coerceAtLeast(1).toDouble()
        val h = displayHeight.coerceAtLeast(1).toDouble()
        val hostDpi = hostDensityDpi.coerceIn(72, 640)
        // Normal phones keep their native resolution and density, reducing the
        // mismatch with third-party ApplicationContext's cached primary metrics.
        val shortDp = max(360.0, min(w, h) * 160 / hostDpi)
        val longDp = max(720.0, shortDp * max(w, h) / min(w, h))
        val edgeDpi = MAX_EDGE * 160 / longDp
        val areaDpi = sqrt(MAX_PIXELS / (shortDp * longDp)) * 160
        var dpi = floor(min(hostDpi.toDouble(), min(edgeDpi, areaDpi))).toInt().coerceIn(72, 640)
        fun pixels(dp: Double) = ceil(dp * dpi / 160).toInt().coerceIn(64, MAX_EDGE)
        // Ceil of each edge can add a row/column beyond the pixel budget.
        while (dpi > 72 && pixels(shortDp).toLong() * pixels(longDp) > MAX_PIXELS) dpi--
        val shortPx = pixels(shortDp)
        var longPx = pixels(longDp)
        if (shortPx.toLong() * longPx > MAX_PIXELS) {
            // Pathological source metrics at the legal density floor: stay within
            // backend bounds even when their full logical area cannot be retained.
            longPx = min(longPx, (MAX_PIXELS / shortPx).toInt())
        }
        return if (w <= h) Viewport(shortPx, longPx, dpi) else Viewport(longPx, shortPx, dpi)
    }
}
