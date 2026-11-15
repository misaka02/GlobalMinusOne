package com.example.globalminusone.browser

/** Projection preference only: never changes the remote display/task geometry. */
internal data class EmbeddedContentViewport(
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f
) {
    fun sanitized(): EmbeddedContentViewport {
        val z = zoom.takeIf { it.isFinite() }?.coerceIn(1f, 2f) ?: 1f
        if (z == 1f) return EmbeddedContentViewport()
        return EmbeddedContentViewport(z,
            panX.takeIf { it.isFinite() }?.coerceIn(-1f, 1f) ?: 0f,
            panY.takeIf { it.isFinite() }?.coerceIn(-1f, 1f) ?: 0f)
    }

    fun moved(dx: Float, dy: Float, hostWidth: Int, hostHeight: Int,
              canvasWidth: Int, canvasHeight: Int): EmbeddedContentViewport {
        val current = sanitized()
        val fit = EmbeddedCanvasTransform.fit(hostWidth, hostHeight, canvasWidth, canvasHeight, current)
        val overflowX = ((hostWidth * fit.scaleX - hostWidth) / 2).coerceAtLeast(0f)
        val overflowY = ((hostHeight * fit.scaleY - hostHeight) / 2).coerceAtLeast(0f)
        return current.copy(
            panX = if (overflowX > 0) current.panX + dx / overflowX else 0f,
            panY = if (overflowY > 0) current.panY + dy / overflowY else 0f
        ).sanitized()
    }
}
