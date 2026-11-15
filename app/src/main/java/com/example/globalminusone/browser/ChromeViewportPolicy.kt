package com.example.globalminusone.browser

/** Density for native Chrome content, independent of the host's physical touch coordinates. */
internal object ChromeViewportPolicy {
    private const val MIN_DPI = 72
    private const val MAX_DPI = 640
    private const val MIN_WIDTH_DP = 320
    private const val MIN_HEIGHT_DP = 480
    private const val MIN_APP_WINDOW_WIDTH_DP = 376
    private const val MIN_APP_WINDOW_HEIGHT_DP = 580

    fun densityDpi(widthPx: Int, heightPx: Int, hostDensityDpi: Int): Int {
        // Long arithmetic also handles untrusted/overflow-sized measurements safely.
        // Below 144 x 216 px, the legal 72 dpi floor cannot guarantee both minimums.
        val widthLimit = widthPx.coerceAtLeast(0).toLong() * 160 / MIN_WIDTH_DP
        val heightLimit = heightPx.coerceAtLeast(0).toLong() * 160 / MIN_HEIGHT_DP
        return minOf(hostDensityDpi.coerceIn(MIN_DPI, MAX_DPI).toLong(), widthLimit, heightLimit)
            .coerceIn(MIN_DPI.toLong(), MAX_DPI.toLong()).toInt()
    }

    /** The 72dpi floor needs at least this many pixels to fit the app's dp viewport.
     * Small hosts scale this canvas down, rather than opening a sub-phone layout
     * whose own toolbar/actions have already overflowed before the host can clip. */
    fun appCanvasSize(hostWidth: Int, hostHeight: Int): Pair<Int, Int> {
        val minWidth = (MIN_APP_WINDOW_WIDTH_DP * MIN_DPI + 159) / 160
        val minHeight = (MIN_APP_WINDOW_HEIGHT_DP * MIN_DPI + 159) / 160
        return hostWidth.coerceIn(minWidth, 2048) to hostHeight.coerceIn(minHeight, 2048)
    }

    /**
     * Density for general third-party Android apps opened inside `小窗`:
     * Modern Android & ColorOS apps assume at least `sw360dp` (`376dp x 580dp` comfortable phone viewport);
     * using `320dp` causes apps' own horizontal toolbar / bottom action buttons to overflow the viewport.
     */
    fun appWindowDensityDpi(widthPx: Int, heightPx: Int, hostDensityDpi: Int): Int {
        val widthLimit = widthPx.coerceAtLeast(0).toLong() * 160 / MIN_APP_WINDOW_WIDTH_DP
        val heightLimit = heightPx.coerceAtLeast(0).toLong() * 160 / MIN_APP_WINDOW_HEIGHT_DP
        return minOf(hostDensityDpi.coerceIn(MIN_DPI, MAX_DPI).toLong(), widthLimit, heightLimit)
            .coerceIn(MIN_DPI.toLong(), MAX_DPI.toLong()).toInt()
    }
}
