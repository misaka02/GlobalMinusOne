package com.example.globalminusone.service.overlay

import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView

internal data class PanelWindowInstance(
    val root: FrameLayout,
    val composeView: ComposeView,
    val lifecycleOwner: OverlayComposeLifecycleOwner,
    val params: WindowManager.LayoutParams
)

internal fun interface PanelWindowFactory {
    fun create(
        targetType: Int,
        targetManager: WindowManager,
        freshParams: WindowManager.LayoutParams
    ): PanelWindowInstance
}

/**
 * Owns the active Compose panel window instance and rebuilds a clean View + LayoutParams(token=null)
 * + LifecycleOwner whenever the window layer (`TYPE_APPLICATION_OVERLAY` <-> `TYPE_ACCESSIBILITY_OVERLAY`)
 * or owning `WindowManager` changes.
 */
internal class AndroidPanelWindowBinding : PanelWindowBackend<FrameLayout, WindowManager> {
    override lateinit var defaultManager: WindowManager
        private set
    override lateinit var manager: WindowManager
        private set
    private lateinit var templateParams: WindowManager.LayoutParams
    private var factory: PanelWindowFactory? = null
    private var instance: PanelWindowInstance? = null

    val hasView: Boolean get() = instance != null
    val hasParams: Boolean get() = instance != null || ::templateParams.isInitialized
    override val view: FrameLayout get() = checkNotNull(instance).root
    val params: WindowManager.LayoutParams get() = instance?.params ?: templateParams
    override val type: Int get() = params.type

    fun configure(
        defaultManager: WindowManager,
        initialParams: WindowManager.LayoutParams,
        factory: PanelWindowFactory
    ) {
        this.defaultManager = defaultManager
        if (!::manager.isInitialized) this.manager = defaultManager
        this.templateParams = initialParams
        this.factory = factory
    }

    override fun prepareForAttach(targetType: Int, targetManager: WindowManager) {
        val current = instance
        val snapshotParams = current?.params ?: templateParams
        val wasVisible = current?.root?.visibility == View.VISIBLE
        if (current != null) {
            disposeInstance(current)
            instance = null
        }
        val freshParams = cloneLayoutParamsForFreshAttach(snapshotParams, targetType)
        val created = checkNotNull(factory) { "PanelWindowFactory not configured" }
            .create(targetType, targetManager, freshParams)
        created.root.visibility = if (wasVisible) View.VISIBLE else View.GONE
        instance = created
        manager = targetManager
        templateParams = created.params
    }

    override fun update(view: FrameLayout) = manager.updateViewLayout(view, params)
    override fun removeImmediate(view: FrameLayout) = manager.removeViewImmediate(view)
    override fun add(view: FrameLayout) = manager.addView(view, params)

    fun updateLayout() {
        val current = instance ?: return
        manager.updateViewLayout(current.root, current.params)
    }

    fun destroy() {
        val current = instance ?: return
        instance = null
        runCatching { manager.removeViewImmediate(current.root) }
            .onFailure { runCatching { manager.removeView(current.root) } }
        disposeInstance(current)
    }

    private fun disposeInstance(target: PanelWindowInstance) {
        runCatching { target.composeView.disposeComposition() }
        runCatching { target.lifecycleOwner.onPause() }
        runCatching { target.lifecycleOwner.onStop() }
        runCatching { target.lifecycleOwner.onDestroy() }
    }
}
