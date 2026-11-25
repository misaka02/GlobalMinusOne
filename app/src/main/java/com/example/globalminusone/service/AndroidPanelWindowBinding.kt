package com.example.globalminusone.service

import android.view.WindowManager
import android.widget.FrameLayout

/** Delayed resource binding preserves setup evaluation and native WindowManager semantics. */
internal class AndroidPanelWindowBinding : PanelWindowBackend<FrameLayout, WindowManager> {
    override lateinit var defaultManager: WindowManager
        private set
    override lateinit var manager: WindowManager
    override lateinit var view: FrameLayout
        private set
    lateinit var params: WindowManager.LayoutParams
        private set
    val hasView: Boolean get() = ::view.isInitialized
    val hasParams: Boolean get() = ::params.isInitialized
    override var type: Int
        get() = params.type
        set(value) { params.type = value }

    fun useDefaultManager(manager: WindowManager) {
        defaultManager = manager
        this.manager = manager
    }

    fun bindParams(params: WindowManager.LayoutParams) {
        this.params = params
    }

    fun bindView(view: FrameLayout) {
        this.view = view
    }

    override fun update(view: FrameLayout) = manager.updateViewLayout(view, params)
    override fun removeImmediate(view: FrameLayout) = manager.removeViewImmediate(view)
    override fun add(view: FrameLayout) = manager.addView(view, params)
    fun updateLayout() = manager.updateViewLayout(view, params)
    fun remove() = manager.removeView(view)
}
