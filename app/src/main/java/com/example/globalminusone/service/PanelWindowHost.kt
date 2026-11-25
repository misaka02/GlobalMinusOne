package com.example.globalminusone.service

import android.view.WindowManager

/** Native resources stay behind this port; registration owns only its two state flags. */
internal interface PanelWindowBackend<V : Any, M : Any> {
    val defaultManager: M
    var manager: M
    val view: V
    var type: Int
    fun update(view: V)
    fun removeImmediate(view: V)
    fun add(view: V)
}

internal class PanelWindowHost<V : Any, M : Any>(
    private val backend: PanelWindowBackend<V, M>,
    private val onFailure: (String, Throwable?) -> Unit
) {
    var registered = false
        private set
    private var accessibilityLayerFailed = false

    fun resetAccessibilityFailure() {
        accessibilityLayerFailed = false
    }

    fun recover(
        highestOverlay: Boolean,
        accessibilityAvailable: Boolean,
        resolveAccessibilityManager: () -> M?
    ) {
        val useAccessibilityLayer = highestOverlay && accessibilityAvailable && !accessibilityLayerFailed
        val desiredType = if (useAccessibilityLayer) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val desiredManager = if (useAccessibilityLayer) {
            resolveAccessibilityManager() ?: backend.defaultManager
        } else backend.defaultManager
        if (registered && backend.type != desiredType) {
            val removal = runCatching { backend.removeImmediate(backend.view) }
            if (removal.exceptionOrNull()?.let { it !is IllegalArgumentException } == true) {
                onFailure("Cannot migrate panel overlay", removal.exceptionOrNull())
                return
            }
            registered = false
        }
        backend.type = desiredType
        backend.manager = desiredManager
        registered = ensureOverlayWindowRegistered(
            existing = if (registered) backend.view else null,
            create = { backend.view },
            update = { backend.update(it) },
            remove = { backend.removeImmediate(it) },
            add = { backend.add(it) },
            onFailure = { onFailure("Cannot register panel overlay window", it) }
        ) != null
        if (registered || !useAccessibilityLayer) return
        accessibilityLayerFailed = true
        backend.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        backend.manager = backend.defaultManager
        registered = ensureOverlayWindowRegistered(
            existing = null,
            create = { backend.view },
            update = { backend.update(it) },
            remove = { backend.removeImmediate(it) },
            add = { backend.add(it) },
            onFailure = { onFailure("Cannot restore ordinary panel overlay", it) }
        ) != null
    }
}
