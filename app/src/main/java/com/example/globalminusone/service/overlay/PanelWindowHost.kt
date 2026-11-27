package com.example.globalminusone.service.overlay

import android.view.WindowManager

/** Native resources stay behind this port; registration owns layer state and rebuild transitions. */
internal interface PanelWindowBackend<V : Any, M : Any> {
    val defaultManager: M
    val manager: M
    val view: V
    val type: Int
    fun prepareForAttach(targetType: Int, targetManager: M)
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
    var lastAccessibilityError: String? = null
        private set
    private var accessibilityLayerFailed = false

    val activeType: Int? get() = if (registered) backend.type else null

    fun resetAccessibilityFailure() {
        accessibilityLayerFailed = false
        lastAccessibilityError = null
    }

    fun recover(
        highestOverlay: Boolean,
        accessibilityAvailable: Boolean,
        resolveAccessibilityManager: () -> M?
    ) {
        val accManager = if (highestOverlay && accessibilityAvailable && !accessibilityLayerFailed) {
            resolveAccessibilityManager()
        } else null
        val useAccessibilityLayer = accManager != null
        val desiredType = if (useAccessibilityLayer) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        }
        val desiredManager = accManager ?: backend.defaultManager

        if (registered && (backend.type != desiredType || backend.manager !== desiredManager)) {
            val removal = runCatching { backend.removeImmediate(backend.view) }
            if (removal.exceptionOrNull()?.let { it !is IllegalArgumentException } == true) {
                onFailure("Cannot migrate panel overlay", removal.exceptionOrNull())
                return
            }
            registered = false
        }

        if (registered) {
            val updated = runCatching { backend.update(backend.view) }
            if (updated.isSuccess) {
                if (useAccessibilityLayer) lastAccessibilityError = null
                return
            }
            onFailure("Cannot register panel overlay window", updated.exceptionOrNull())
            val removal = runCatching { backend.removeImmediate(backend.view) }
            val removeErr = removal.exceptionOrNull()
            if (removeErr != null) {
                onFailure("Cannot register panel overlay window", removeErr)
                if (removeErr !is IllegalArgumentException) {
                    registered = false
                    return
                }
            }
            registered = false
        }

        var capturedError: Exception? = null
        backend.prepareForAttach(desiredType, desiredManager)
        registered = ensureOverlayWindowRegistered(
            existing = null,
            create = { backend.view },
            update = { backend.update(it) },
            remove = { backend.removeImmediate(it) },
            add = { backend.add(it) },
            onFailure = { err ->
                capturedError = err
                onFailure("Cannot register panel overlay window", err)
            }
        ) != null
        if (registered) {
            if (useAccessibilityLayer) lastAccessibilityError = null
            return
        }
        if (!useAccessibilityLayer) return

        accessibilityLayerFailed = true
        lastAccessibilityError = formatOverlayFailureReason(capturedError)
        backend.prepareForAttach(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, backend.defaultManager)
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
