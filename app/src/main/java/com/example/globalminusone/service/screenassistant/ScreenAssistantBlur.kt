package com.example.globalminusone.service.screenassistant

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Lightweight software blur for the Breeno-style Spotlight backdrop in [ScreenAssistantCanvasView].
 * Operates on a downscaled proxy bitmap (~216x480, ~4ms, API 26-36 safe) to render a soft frosted depth-of-field veil.
 */
internal object ScreenAssistantBlur {

    fun createSpotlightBackdrop(source: Bitmap, maxSide: Int = 480, radius: Int = 3): Bitmap? {
        if (source.isRecycled || source.width < 8 || source.height < 8) return null
        val scale = min(1f, maxSide.toFloat() / max(source.width, source.height).toFloat())
        val pw = (source.width * scale).roundToInt().coerceIn(8, source.width)
        val ph = (source.height * scale).roundToInt().coerceIn(8, source.height)
        val scaled = if (pw == source.width && ph == source.height) {
            source.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            Bitmap.createScaledBitmap(source, pw, ph, true)
        } ?: return null
        return try {
            val pixels = IntArray(pw * ph)
            scaled.getPixels(pixels, 0, pw, 0, 0, pw, ph)
            blurArgb(pixels, pw, ph, radius)
            Bitmap.createBitmap(pixels, pw, ph, Bitmap.Config.ARGB_8888)
        } finally {
            if (scaled !== source && !scaled.isRecycled) scaled.recycle()
        }
    }

    /** Two-pass separable box blur approximating a Gaussian depth-of-field blur. */
    fun blurArgb(pixels: IntArray, width: Int, height: Int, radius: Int, passes: Int = 2) {
        if (width < 2 || height < 2 || radius <= 0 || pixels.size < width * height) return
        val scratch = IntArray(width * height)
        repeat(passes.coerceIn(1, 3)) {
            boxPassHorizontal(pixels, scratch, width, height, radius)
            boxPassVertical(scratch, pixels, width, height, radius)
        }
    }

    private fun boxPassHorizontal(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int) {
        val win = radius * 2 + 1
        for (y in 0 until h) {
            val rowOff = y * w
            var sr = 0
            var sg = 0
            var sb = 0
            for (k in -radius..radius) {
                val c = src[rowOff + k.coerceIn(0, w - 1)]
                sr += (c ushr 16) and 0xFF
                sg += (c ushr 8) and 0xFF
                sb += c and 0xFF
            }
            for (x in 0 until w) {
                dst[rowOff + x] = (0xFF shl 24) or ((sr / win) shl 16) or ((sg / win) shl 8) or (sb / win)
                val remC = src[rowOff + (x - radius).coerceIn(0, w - 1)]
                val addC = src[rowOff + (x + radius + 1).coerceIn(0, w - 1)]
                sr += ((addC ushr 16) and 0xFF) - ((remC ushr 16) and 0xFF)
                sg += ((addC ushr 8) and 0xFF) - ((remC ushr 8) and 0xFF)
                sb += (addC and 0xFF) - (remC and 0xFF)
            }
        }
    }

    private fun boxPassVertical(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int) {
        val win = radius * 2 + 1
        for (x in 0 until w) {
            var sr = 0
            var sg = 0
            var sb = 0
            for (k in -radius..radius) {
                val c = src[k.coerceIn(0, h - 1) * w + x]
                sr += (c ushr 16) and 0xFF
                sg += (c ushr 8) and 0xFF
                sb += c and 0xFF
            }
            for (y in 0 until h) {
                dst[y * w + x] = (0xFF shl 24) or ((sr / win) shl 16) or ((sg / win) shl 8) or (sb / win)
                val remC = src[(y - radius).coerceIn(0, h - 1) * w + x]
                val addC = src[(y + radius + 1).coerceIn(0, h - 1) * w + x]
                sr += ((addC ushr 16) and 0xFF) - ((remC ushr 16) and 0xFF)
                sg += ((addC ushr 8) and 0xFF) - ((remC ushr 8) and 0xFF)
                sb += (addC and 0xFF) - (remC and 0xFF)
            }
        }
    }
}
