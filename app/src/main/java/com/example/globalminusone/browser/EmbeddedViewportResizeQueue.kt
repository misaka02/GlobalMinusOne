package com.example.globalminusone.browser

/** UI-thread single-flight queue. Intermediate drag sizes never form an IPC backlog. */
internal class EmbeddedViewportResizeQueue {
    private var desired: EmbeddedAppViewportPolicy.Viewport? = null
    private var failed: EmbeddedAppViewportPolicy.Viewport? = null
    var inFlight: EmbeddedAppViewportPolicy.Viewport? = null
        private set
    var hostGestureActive = false
    /** Distinguish a new size from repeated layouts of the same target. */
    fun offer(viewport: EmbeddedAppViewportPolicy.Viewport): Boolean {
        if (desired == viewport) return false
        desired = viewport
        return true
    }
    /** TextureView overwrites the buffer default with host pixels on each size
     * change. Always restore remote pixels, including during an in-flight IPC. */
    fun bufferViewport(committed: EmbeddedAppViewportPolicy.Viewport) = inFlight ?: committed
    fun take(committed: EmbeddedAppViewportPolicy.Viewport): EmbeddedAppViewportPolicy.Viewport? {
        val next = desired ?: return null
        if (hostGestureActive || inFlight != null || next == committed || next == failed) return null
        inFlight = next
        return next
    }
    fun complete(viewport: EmbeddedAppViewportPolicy.Viewport, success: Boolean) {
        if (inFlight != viewport) return
        inFlight = null
        failed = if (success) null else viewport
    }
    fun retry() { failed = null }
    fun reset() { desired = null; failed = null; inFlight = null; hostGestureActive = false }
}
