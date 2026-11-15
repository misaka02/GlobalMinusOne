package com.example.globalminusone.browser

import kotlin.math.min

/** Fits the remote texture inside fixed host bounds. Touches remain in host
 * coordinates because TextureView.setTransform does not transform the View. */
internal data class EmbeddedCanvasTransform(
    val scaleX: Float, val scaleY: Float,
    val translationX: Float = 0f, val translationY: Float = 0f
) {
    fun contains(x: Float, y: Float, hostWidth: Int, hostHeight: Int): Boolean =
        x >= 0f && x < hostWidth && y >= 0f && y < hostHeight &&
            x >= hostWidth * (1f - scaleX) / 2 + translationX &&
            x < hostWidth * (1f + scaleX) / 2 + translationX &&
            y >= hostHeight * (1f - scaleY) / 2 + translationY &&
            y < hostHeight * (1f + scaleY) / 2 + translationY

    fun mapHostX(value: Float, hostWidth: Int, canvasWidth: Int): Float =
        mapLocal((value - translationX - hostWidth * (1f - scaleX) / 2) / scaleX, hostWidth, canvasWidth)

    fun mapHostY(value: Float, hostHeight: Int, canvasHeight: Int): Float =
        mapLocal((value - translationY - hostHeight * (1f - scaleY) / 2) / scaleY, hostHeight, canvasHeight)

    companion object {
        fun fit(hostWidth: Int, hostHeight: Int, canvasWidth: Int, canvasHeight: Int,
                viewport: EmbeddedContentViewport = EmbeddedContentViewport()): EmbeddedCanvasTransform {
            require(minOf(hostWidth, hostHeight, canvasWidth, canvasHeight) > 0)
            val unit = min(hostWidth.toFloat() / canvasWidth, hostHeight.toFloat() / canvasHeight)
            val state = viewport.sanitized()
            val sx = unit * canvasWidth / hostWidth * state.zoom
            val sy = unit * canvasHeight / hostHeight * state.zoom
            return EmbeddedCanvasTransform(sx, sy,
                ((hostWidth * sx - hostWidth) / 2).coerceAtLeast(0f) * state.panX,
                ((hostHeight * sy - hostHeight) / 2).coerceAtLeast(0f) * state.panY)
        }
        fun mapLocal(value: Float, hostExtent: Int, canvasExtent: Int): Float =
            (value * canvasExtent / hostExtent).coerceIn(0f, canvasExtent.toFloat())
    }
}
