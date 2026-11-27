package com.example.globalminusone.service.overlay

import android.content.Context
import android.view.View
import android.view.WindowManager

internal class AndroidManagedOverlaySlot<V : View>(
    private val defaultContext: Context,
    private val defaultManager: WindowManager,
    private val onBeforeRemove: (V) -> Unit = {}
) {
    private val slot = ManagedOverlaySlot<V, WindowManager>(defaultManager)
    val view: V? get() = slot.view
    val registeredType: Int? get() = slot.registeredType
    val lastAccessibilityError: String? get() = slot.lastAccessibilityError

    fun resetAccessibilityFailure() = slot.resetAccessibilityFailure()

    fun sync(
        highestOverlay: Boolean,
        accessibilityTarget: AccessibilityWindowTarget?,
        params: WindowManager.LayoutParams,
        createView: (Context) -> V,
        onAfterUpdate: (V) -> Unit = {},
        onFailure: (Throwable) -> Unit
    ): V? = slot.sync(
        highestOverlay = highestOverlay,
        accessibilityAvailable = accessibilityTarget != null,
        resolveAccessibilityManager = { accessibilityTarget?.windowManager },
        createView = { mgr ->
            val ctx = if (accessibilityTarget != null && mgr === accessibilityTarget.windowManager) {
                accessibilityTarget.context
            } else {
                defaultContext
            }
            createView(ctx)
        },
        add = { mgr, v, targetType ->
            mgr.addView(v, cloneLayoutParamsForFreshAttach(params, targetType))
        },
        update = { mgr, v, targetType ->
            preserveAttachedTokenForUpdate(v, params, targetType)
            mgr.updateViewLayout(v, params)
            onAfterUpdate(v)
        },
        removeImmediate = { mgr, v -> mgr.removeViewImmediate(v) },
        onBeforeRemove = onBeforeRemove,
        onFailure = onFailure
    )

    fun updateLayout(view: View, params: WindowManager.LayoutParams) {
        @Suppress("UNCHECKED_CAST")
        val typed = view as? V
        if (typed != null && slot.view === typed) {
            slot.updateLayout(typed) { mgr, v, regType ->
                preserveAttachedTokenForUpdate(v, params, regType)
                mgr.updateViewLayout(v, params)
            }
        } else {
            defaultManager.updateViewLayout(view, params)
        }
    }

    fun remove() = slot.remove(onBeforeRemove = onBeforeRemove) { mgr, v -> mgr.removeViewImmediate(v) }
}

/**
 * Tracks a single floating auxiliary window (`EdgeTriggerView` / `SuspensionBallView`) across
 * `TYPE_APPLICATION_OVERLAY` and `TYPE_ACCESSIBILITY_OVERLAY`, recreating Views on layer switches
 * so each WindowManager owns a View built with matching Context and token-free LayoutParams.
 */
internal class ManagedOverlaySlot<V : Any, M : Any>(
    private val defaultManager: M
) {
    var view: V? = null
        private set
    var registeredManager: M? = null
        private set
    var registeredType: Int? = null
        private set
    var lastAccessibilityError: String? = null
        private set
    private var accessibilityFailed = false

    fun resetAccessibilityFailure() {
        accessibilityFailed = false
        lastAccessibilityError = null
    }

    fun sync(
        highestOverlay: Boolean,
        accessibilityAvailable: Boolean,
        resolveAccessibilityManager: () -> M?,
        createView: (M) -> V,
        add: (M, V, Int) -> Unit,
        update: (M, V, Int) -> Unit,
        removeImmediate: (M, V) -> Unit,
        onBeforeRemove: (V) -> Unit = {},
        onFailure: (Throwable) -> Unit = {}
    ): V? {
        val accManager = if (highestOverlay && accessibilityAvailable && !accessibilityFailed) {
            resolveAccessibilityManager()
        } else null
        val useAccessibility = accManager != null
        val desiredType = if (useAccessibility) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        }
        val desiredManager = accManager ?: defaultManager

        val currentView = view
        if (currentView != null && (registeredType != desiredType || registeredManager !== desiredManager)) {
            detachFromCurrentManager(currentView, onBeforeRemove, removeImmediate)
            view = null
        }

        val existingSameLayer = view
        if (existingSameLayer != null && registeredManager === desiredManager && registeredType == desiredType) {
            val updated = runCatching { update(desiredManager, existingSameLayer, desiredType) }
            if (updated.isSuccess) {
                if (useAccessibility) lastAccessibilityError = null
                return existingSameLayer
            }
            detachFromCurrentManager(existingSameLayer, onBeforeRemove, removeImmediate)
            view = null
        }

        val activeView = createView(desiredManager)
        val added = runCatching { add(desiredManager, activeView, desiredType) }
        if (added.isSuccess) {
            view = activeView
            registeredManager = desiredManager
            registeredType = desiredType
            if (useAccessibility) lastAccessibilityError = null
            return activeView
        }

        val err = added.exceptionOrNull()!!
        onFailure(err)
        runCatching { onBeforeRemove(activeView) }
        runCatching { removeImmediate(desiredManager, activeView) }
        if (!useAccessibility) {
            view = null
            registeredManager = null
            registeredType = null
            return null
        }

        accessibilityFailed = true
        lastAccessibilityError = formatOverlayFailureReason(err)
        val fallbackType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val fallbackView = createView(defaultManager)
        val fallbackAdded = runCatching { add(defaultManager, fallbackView, fallbackType) }
        if (fallbackAdded.isSuccess) {
            view = fallbackView
            registeredManager = defaultManager
            registeredType = fallbackType
            return fallbackView
        }
        fallbackAdded.exceptionOrNull()?.let(onFailure)
        runCatching { onBeforeRemove(fallbackView) }
        runCatching { removeImmediate(defaultManager, fallbackView) }
        view = null
        registeredManager = null
        registeredType = null
        return null
    }

    fun updateLayout(
        target: V,
        update: (M, V, Int) -> Unit
    ) {
        if (view !== target) return
        val regType = registeredType ?: WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        update(registeredManager ?: defaultManager, target, regType)
    }

    fun remove(
        onBeforeRemove: (V) -> Unit = {},
        removeImmediate: (M, V) -> Unit
    ) {
        val current = view ?: return
        detachFromCurrentManager(current, onBeforeRemove, removeImmediate)
        view = null
    }

    private fun detachFromCurrentManager(
        current: V,
        onBeforeRemove: (V) -> Unit,
        removeImmediate: (M, V) -> Unit
    ) {
        runCatching { onBeforeRemove(current) }
        runCatching { removeImmediate(registeredManager ?: defaultManager, current) }
        registeredManager = null
        registeredType = null
    }
}
