package com.example.globalminusone.service

import android.content.SharedPreferences
import com.example.globalminusone.browser.EmbeddedContentViewport
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal enum class ApplicationWindowMode { WINDOW, ICON, LEFT_EDGE, RIGHT_EDGE, CLOSED }
internal data class ApplicationWindowRect(val x: Int, val y: Int, val width: Int, val height: Int)

internal data class SavedApplicationWindowState(
    val xFraction: Float,
    val yFraction: Float,
    val widthFraction: Float,
    val heightFraction: Float,
    val userUnlockedAspectRatio: Boolean = false
) {
    fun toRect(screenWidth: Int, screenHeight: Int): ApplicationWindowRect {
        val sw = screenWidth.coerceAtLeast(1)
        val sh = screenHeight.coerceAtLeast(1)
        return ApplicationWindowRect(
            x = (xFraction.coerceIn(0f, 0.92f) * sw).roundToInt(),
            y = (yFraction.coerceIn(0f, 0.92f) * sh).roundToInt(),
            width = (widthFraction.coerceIn(0.20f, 1f) * sw).roundToInt(),
            height = (heightFraction.coerceIn(0.18f, 1f) * sh).roundToInt()
        )
    }

    companion object {
        fun fromRect(
            rect: ApplicationWindowRect,
            screenWidth: Int,
            screenHeight: Int,
            userUnlockedAspectRatio: Boolean
        ): SavedApplicationWindowState {
            val sw = screenWidth.coerceAtLeast(1).toFloat()
            val sh = screenHeight.coerceAtLeast(1).toFloat()
            return SavedApplicationWindowState(
                xFraction = (rect.x / sw).coerceIn(0f, 0.95f),
                yFraction = (rect.y / sh).coerceIn(0f, 0.95f),
                widthFraction = (rect.width / sw).coerceIn(0.20f, 1f),
                heightFraction = (rect.height / sh).coerceIn(0.18f, 1f),
                userUnlockedAspectRatio = userUnlockedAspectRatio
            )
        }
    }
}

/**
 * Persists user-adjusted floating `小窗` size, position, and aspect-ratio lock preference
 * per package with a global last-used fallback so reopening any `小窗` remembers its previous bounds.
 */
internal class ApplicationWindowGeometryStore(private val prefs: SharedPreferences?) {
    private val memoryCache = mutableMapOf<String, SavedApplicationWindowState>()

    fun load(packageName: String): SavedApplicationWindowState? {
        val pkgKey = packageKey(packageName)
        return readKey(pkgKey) ?: readKey(KEY_GLOBAL_LAST)
    }

    fun save(packageName: String, state: SavedApplicationWindowState) {
        val pkgKey = packageKey(packageName)
        memoryCache[pkgKey] = state
        memoryCache[KEY_GLOBAL_LAST] = state
        prefs?.edit()
            ?.putString(pkgKey, encode(state))
            ?.putString(KEY_GLOBAL_LAST, encode(state))
            ?.apply()
    }

    private fun readKey(key: String): SavedApplicationWindowState? {
        memoryCache[key]?.let { return it }
        val raw = prefs?.getString(key, null) ?: return null
        return decode(raw)?.also { memoryCache[key] = it }
    }

    private fun packageKey(packageName: String): String = "pkg_${packageName.trim()}"

    companion object {
        private const val KEY_GLOBAL_LAST = "global_last_window_geometry_v1"

        internal fun encode(state: SavedApplicationWindowState): String =
            "${state.xFraction},${state.yFraction},${state.widthFraction},${state.heightFraction},${if (state.userUnlockedAspectRatio) 1 else 0}"

        internal fun decode(raw: String): SavedApplicationWindowState? {
            val parts = raw.split(',')
            if (parts.size < 4) return null
            val xf = parts[0].toFloatOrNull() ?: return null
            val yf = parts[1].toFloatOrNull() ?: return null
            val wf = parts[2].toFloatOrNull() ?: return null
            val hf = parts[3].toFloatOrNull() ?: return null
            if (!xf.isFinite() || !yf.isFinite() || !wf.isFinite() || !hf.isFinite()) return null
            if (wf <= 0.1f || hf <= 0.1f) return null
            val unlocked = parts.getOrNull(4) == "1"
            return SavedApplicationWindowState(
                xFraction = xf.coerceIn(0f, 0.95f),
                yFraction = yf.coerceIn(0f, 0.95f),
                widthFraction = wf.coerceIn(0.20f, 1f),
                heightFraction = hf.coerceIn(0.18f, 1f),
                userUnlockedAspectRatio = unlocked
            )
        }
    }
}

/** Separate window/icon geometry: minimizing never overwrites the restored application's bounds. */
internal class ApplicationWindowPlacement(width: Int, height: Int, private val unit: Float) {
    var screenWidth = width.coerceAtLeast(1); private set
    var screenHeight = height.coerceAtLeast(1); private set
    var mode = ApplicationWindowMode.WINDOW; private set
    var window = defaultCompactWindow(screenWidth, screenHeight); private set
    val iconSize get() = (56 * unit).roundToInt().coerceAtLeast(1).coerceAtMost(minOf(screenWidth, screenHeight))
    val edgeWidth get() = (32 * unit).roundToInt().coerceIn(1, iconSize)
    var iconX = (width - iconSize).coerceAtLeast(0); private set
    var iconY = ((height - iconSize) / 2).coerceAtLeast(0); private set
    var aspectRatioLocked = false; private set
    var contentAspectRatio: Float? = null; private set
    var chromeWidthPx = 0; private set
    var chromeHeightPx = 0; private set
    val hasContentAspectRatio: Boolean get() = contentAspectRatio != null

    init { window = clamp(window) }

    private fun defaultCompactWindow(w: Int, h: Int): ApplicationWindowRect {
        val targetW = (w * DEFAULT_WIDTH_FRACTION).roundToInt()
        val targetH = (h * DEFAULT_HEIGHT_FRACTION).roundToInt()
        val targetX = ((w - targetW) / 2).coerceAtLeast(0)
        val targetY = (h * DEFAULT_TOP_FRACTION).roundToInt()
        return ApplicationWindowRect(targetX, targetY, targetW, targetH)
    }

    /**
     * Keeps `240*unit x 300*unit` on spacious screens, while allowing narrow/high-density phones
     * (e.g. 360dp sw @ density=3.0) to scale `小窗` down smoothly to ~42% width x 25% height.
     */
    private fun minWindowWidth(): Int {
        val dpCap = (240 * unit).roundToInt()
        val shortEdge = minOf(screenWidth, screenHeight)
        val responsiveCap = (shortEdge * 0.42f).roundToInt().coerceAtLeast((136 * unit).roundToInt())
        return minOf(dpCap, responsiveCap).coerceIn(1, screenWidth)
    }

    private fun minWindowHeight(): Int {
        val dpCap = (300 * unit).roundToInt()
        val responsiveCap = (screenHeight * 0.25f).roundToInt().coerceAtLeast((180 * unit).roundToInt())
        return minOf(dpCap, responsiveCap).coerceIn(1, screenHeight)
    }

    private fun clamp(rect: ApplicationWindowRect): ApplicationWindowRect {
        val w = rect.width.coerceIn(minWindowWidth(), screenWidth)
        val h = rect.height.coerceIn(minWindowHeight(), screenHeight)
        return ApplicationWindowRect(
            rect.x.coerceIn(0, screenWidth - w),
            rect.y.coerceIn(0, screenHeight - h),
            w,
            h
        )
    }

    fun restoreSavedWindow(savedRect: ApplicationWindowRect) {
        if (mode == ApplicationWindowMode.CLOSED) return
        window = clamp(savedRect)
    }

    fun cascadeIfOverlapping(existingWindows: Iterable<ApplicationWindowRect>, stepPx: Int) {
        if (mode == ApplicationWindowMode.CLOSED || stepPx <= 0) return
        var candidate = window
        val threshold = (stepPx / 2).coerceAtLeast(6)
        repeat(5) {
            val overlapsOrigin = existingWindows.any { other ->
                kotlin.math.abs(other.x - candidate.x) <= threshold &&
                    kotlin.math.abs(other.y - candidate.y) <= threshold
            }
            if (!overlapsOrigin) {
                window = candidate
                return
            }
            val nextX = if (candidate.x + stepPx + candidate.width <= screenWidth) candidate.x + stepPx else (stepPx / 2).coerceAtMost((screenWidth - candidate.width).coerceAtLeast(0))
            val nextY = if (candidate.y + stepPx + candidate.height <= screenHeight) candidate.y + stepPx else (stepPx / 2).coerceAtMost((screenHeight - candidate.height).coerceAtLeast(0))
            val shifted = clamp(candidate.copy(x = nextX, y = nextY))
            if (shifted == candidate) return
            candidate = shifted
        }
        window = candidate
    }

    /**
     * Locks the inner content viewport `(window.width - chromeW) : (window.height - chromeH)`
     * to the remote app's fixed canvas aspect ratio so GPU scaling fills 100% of the content box
     * without letterboxing or mutating the remote VirtualDisplay.
     */
    fun lockContentAspectRatio(
        canvasWidth: Int,
        canvasHeight: Int,
        chromeW: Int = chromeWidthPx,
        chromeH: Int = chromeHeightPx,
        snapWindow: Boolean = true
    ) {
        if (mode == ApplicationWindowMode.CLOSED || canvasWidth <= 0 || canvasHeight <= 0) return
        chromeWidthPx = chromeW.coerceAtLeast(0)
        chromeHeightPx = chromeH.coerceAtLeast(0)
        contentAspectRatio = (canvasWidth.toFloat() / canvasHeight.toFloat()).coerceIn(0.25f, 4.0f)
        aspectRatioLocked = true
        if (snapWindow && mode == ApplicationWindowMode.WINDOW) {
            window = snapToLockedRatioAreaPreserving(window)
        }
    }

    fun updateChromeInsets(chromeW: Int, chromeH: Int, snapWindowIfLocked: Boolean = true) {
        if (mode == ApplicationWindowMode.CLOSED) return
        chromeWidthPx = chromeW.coerceAtLeast(0)
        chromeHeightPx = chromeH.coerceAtLeast(0)
        if (snapWindowIfLocked && aspectRatioLocked && mode == ApplicationWindowMode.WINDOW) {
            window = snapToLockedRatioAreaPreserving(window)
        }
    }

    fun setAspectRatioLocked(locked: Boolean, snapWindow: Boolean = false) {
        if (mode == ApplicationWindowMode.CLOSED) return
        if (locked && contentAspectRatio == null) {
            val cw = (window.width - chromeWidthPx).coerceAtLeast(1)
            val ch = (window.height - chromeHeightPx).coerceAtLeast(1)
            contentAspectRatio = (cw.toFloat() / ch.toFloat()).coerceIn(0.25f, 4.0f)
        }
        aspectRatioLocked = locked
        if (locked && snapWindow && mode == ApplicationWindowMode.WINDOW) {
            window = snapToLockedRatioAreaPreserving(window)
        }
    }

    /**
     * Area-preserving snap around `base`'s visual center so locking aspect ratio never suddenly
     * bloats a compact window to full screen height or shrinks a large window to minimum bounds.
     */
    private fun snapToLockedRatioAreaPreserving(base: ApplicationWindowRect): ApplicationWindowRect {
        val ratio = contentAspectRatio ?: return clamp(base)
        val cw = chromeWidthPx.coerceIn(0, (screenWidth - 32).coerceAtLeast(0))
        val ch = chromeHeightPx.coerceIn(0, (screenHeight - 32).coerceAtLeast(0))
        val minContentW = (minWindowWidth() - cw).coerceAtLeast(32).toFloat()
        val maxContentW = (screenWidth - cw).coerceAtLeast(minContentW.roundToInt()).toFloat()
        val minContentH = (minWindowHeight() - ch).coerceAtLeast(32).toFloat()
        val maxContentH = (screenHeight - ch).coerceAtLeast(minContentH.roundToInt()).toFloat()
        val hLow = maxOf(minContentH, minContentW / ratio)
        val hHigh = minOf(maxContentH, maxContentW / ratio)
        if (hLow > hHigh) return clamp(base)

        val curContentW = (base.width - cw).coerceAtLeast(32).toFloat()
        val curContentH = (base.height - ch).coerceAtLeast(32).toFloat()
        val area = curContentW * curContentH
        val desiredH = sqrt((area / ratio).coerceAtLeast(1f))
        val contentH = desiredH.coerceIn(hLow, hHigh)
        val contentW = contentH * ratio
        val newW = (contentW + cw).roundToInt().coerceIn(minWindowWidth(), screenWidth)
        val newH = (contentH + ch).roundToInt().coerceIn(minWindowHeight(), screenHeight)
        val centerX = base.x + base.width / 2
        val centerY = base.y + base.height / 2
        return clamp(
            ApplicationWindowRect(
                x = centerX - newW / 2,
                y = centerY - newH / 2,
                width = newW,
                height = newH
            )
        )
    }

    fun moveWindow(start: ApplicationWindowRect, dx: Int, dy: Int) {
        if (mode == ApplicationWindowMode.WINDOW) window = clamp(start.copy(x = start.x + dx, y = start.y + dy))
    }

    /**
     * Smooth, continuous monotonic resize:
     * When `aspectRatioLocked` is active, projects `(dx, dy)` onto the locked aspect-ratio diagonal
     * `H_desired = ((W0 + dx) * ratio + (H0 + dy)) / (ratio^2 + 1)` with top-left `(start.x, start.y)`
     * strictly anchored. Eliminates the `|dx| >= |dy|` axis-flip discontinuity and origin-shift jumps.
     */
    fun resizeWindow(start: ApplicationWindowRect, dx: Int, dy: Int) {
        if (mode != ApplicationWindowMode.WINDOW) return
        val maxW = (screenWidth - start.x).coerceAtLeast(minWindowWidth())
        val maxH = (screenHeight - start.y).coerceAtLeast(minWindowHeight())
        val ratio = contentAspectRatio
        if (!aspectRatioLocked || ratio == null) {
            window = clamp(
                start.copy(
                    width = (start.width + dx).coerceAtMost(maxW),
                    height = (start.height + dy).coerceAtMost(maxH)
                )
            )
            return
        }

        val cw = chromeWidthPx.coerceIn(0, (maxW - 32).coerceAtLeast(0))
        val ch = chromeHeightPx.coerceIn(0, (maxH - 32).coerceAtLeast(0))
        val minContentW = (minWindowWidth() - cw).coerceAtLeast(32).toFloat()
        val maxContentW = (maxW - cw).coerceAtLeast(minContentW.roundToInt()).toFloat()
        val minContentH = (minWindowHeight() - ch).coerceAtLeast(32).toFloat()
        val maxContentH = (maxH - ch).coerceAtLeast(minContentH.roundToInt()).toFloat()

        val startContentW = (start.width - cw).coerceAtLeast(32).toFloat()
        val startContentH = (start.height - ch).coerceAtLeast(32).toFloat()
        val desiredContentH = ((startContentW + dx) * ratio + (startContentH + dy)) / (ratio * ratio + 1f)

        val hLow = maxOf(minContentH, minContentW / ratio)
        val hHigh = minOf(maxContentH, maxContentW / ratio)
        val contentH = if (hLow <= hHigh) {
            desiredContentH.coerceIn(hLow, hHigh)
        } else {
            // Even near the far bottom-right screen edge, clamp smoothly at minimum valid content height
            // without unlocking full-screen bounds or jumping to huge dimensions.
            hLow.coerceAtMost((screenHeight - ch).coerceAtLeast(32).toFloat())
        }
        val contentW = contentH * ratio
        window = clamp(
            ApplicationWindowRect(
                x = start.x,
                y = start.y,
                width = (contentW + cw).roundToInt(),
                height = (contentH + ch).roundToInt()
            )
        )
    }

    fun minimize() { if (mode != ApplicationWindowMode.CLOSED) mode = ApplicationWindowMode.ICON }
    fun restore() { if (mode != ApplicationWindowMode.CLOSED) mode = ApplicationWindowMode.WINDOW }
    fun close() { mode = ApplicationWindowMode.CLOSED }
    fun floatIcon() {
        if (mode == ApplicationWindowMode.CLOSED) return
        mode = ApplicationWindowMode.ICON
        iconX = iconX.coerceIn(0, screenWidth - iconSize)
    }
    fun dock(left: Boolean) {
        if (mode == ApplicationWindowMode.CLOSED) return
        mode = if (left) ApplicationWindowMode.LEFT_EDGE else ApplicationWindowMode.RIGHT_EDGE
        iconX = if (left) 0 else screenWidth - iconSize
    }
    fun moveIcon(x: Int, y: Int) {
        if (mode == ApplicationWindowMode.CLOSED) return
        mode = ApplicationWindowMode.ICON
        iconX = x.coerceIn(0, screenWidth - iconSize)
        iconY = y.coerceIn(0, screenHeight - iconSize)
    }
    fun finishIconMove() {
        val snap = (24 * unit).roundToInt()
        if (iconX <= snap) dock(true)
        else if (iconX + iconSize >= screenWidth - snap) dock(false)
    }
    fun iconBounds(): ApplicationWindowRect = when (mode) {
        ApplicationWindowMode.LEFT_EDGE -> ApplicationWindowRect(0, iconY, edgeWidth, iconSize)
        ApplicationWindowMode.RIGHT_EDGE -> ApplicationWindowRect(screenWidth - edgeWidth, iconY, edgeWidth, iconSize)
        else -> ApplicationWindowRect(iconX, iconY, iconSize, iconSize)
    }
    fun reconfigure(width: Int, height: Int) {
        val previousWidth = screenWidth; val previousHeight = screenHeight
        screenWidth = width.coerceAtLeast(1); screenHeight = height.coerceAtLeast(1)
        val scaled = clamp(ApplicationWindowRect((window.x.toFloat() / previousWidth * screenWidth).roundToInt(),
            (window.y.toFloat() / previousHeight * screenHeight).roundToInt(),
            (window.width.toFloat() / previousWidth * screenWidth).roundToInt(),
            (window.height.toFloat() / previousHeight * screenHeight).roundToInt()))
        window = if (aspectRatioLocked && contentAspectRatio != null) {
            snapToLockedRatioAreaPreserving(scaled)
        } else scaled
        iconX = (iconX.toFloat() / previousWidth * screenWidth).roundToInt().coerceIn(0, screenWidth - iconSize)
        iconY = (iconY.toFloat() / previousHeight * screenHeight).roundToInt().coerceIn(0, screenHeight - iconSize)
    }

    companion object {
        const val DEFAULT_WIDTH_FRACTION = 0.70f
        const val DEFAULT_HEIGHT_FRACTION = 0.60f
        const val DEFAULT_TOP_FRACTION = 0.14f
    }
}

/** Per-package projection, separate from geometry/global-last-window fallback. */
internal class ApplicationContentViewportStore(private val prefs: SharedPreferences?) {
    private val cache = mutableMapOf<String, EmbeddedContentViewport>()
    fun load(packageName: String): EmbeddedContentViewport = cache.getOrPut(packageName) {
        decode(runCatching { prefs?.getString(key(packageName), null) }.getOrNull())
    }
    fun save(packageName: String, viewport: EmbeddedContentViewport) {
        val state = viewport.sanitized()
        cache[packageName] = state
        prefs?.edit()?.putString(key(packageName), encode(state))?.apply()
    }
    private fun key(packageName: String) = "content_viewport_v1_${packageName.trim()}"
    companion object {
        internal fun encode(viewport: EmbeddedContentViewport): String = viewport.sanitized().let {
            "${it.zoom},${it.panX},${it.panY}"
        }
        internal fun decode(raw: String?): EmbeddedContentViewport {
            val parts = raw?.split(',') ?: return EmbeddedContentViewport()
            if (parts.size != 3) return EmbeddedContentViewport()
            val values = parts.map { it.toFloatOrNull() }
            if (values.any { it == null || !it.isFinite() }) return EmbeddedContentViewport()
            return EmbeddedContentViewport(values[0]!!, values[1]!!, values[2]!!).sanitized()
        }
    }
}
