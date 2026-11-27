package com.example.globalminusone.service.overlay

import android.content.Context
import android.os.Build
import android.os.IBinder
import android.view.KeyEvent
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

internal interface PanelInputHost {
    val remoteOwnsInput: Boolean
    val authorizationPending: Boolean
    fun hideHostIme(windowToken: IBinder?)
    fun clearComposeInputFocus()
    fun handleModalBack(): Boolean
    fun handleBrowserBack(): Boolean
    fun handleFileBack(): Boolean
    fun handleEditorBack(): Boolean
    fun animateClosePanel()
}

/** The IME back sequence survives pre-IME dispatch until its corresponding UP. */
internal class PanelImeBackState {
    private var imeBackDownTime: Long? = null

    fun recordDown(repeatCount: Int, imeDownTime: () -> Long?) {
        if (repeatCount == 0) imeBackDownTime = imeDownTime()
    }

    fun matches(downTime: Long): Boolean = imeBackDownTime == downTime

    fun consumeUp(downTime: Long): Boolean {
        val fromIme = imeBackDownTime == downTime
        imeBackDownTime = null
        return fromIme
    }

    fun reset() {
        imeBackDownTime = null
    }
}

internal class PanelInputFrameLayout(context: Context, private val host: PanelInputHost) : FrameLayout(context) {
    private val imeBackState = PanelImeBackState()

    private fun handleHostImeBack(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_BACK) return false
        if (host.remoteOwnsInput || host.authorizationPending) {
            imeBackState.reset()
            return false
        }
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                imeBackState.recordDown(event.repeatCount) {
                    // Remember the IME before it can hide during pre-IME dispatch.
                    // Keep focus until UP, so that UP cannot reach the underlying app.
                    if (ViewCompat.getRootWindowInsets(this)
                            ?.isVisible(WindowInsetsCompat.Type.ime()) == true) {
                        event.downTime
                    } else null
                }
                return imeBackState.matches(event.downTime)
            }
            KeyEvent.ACTION_UP -> {
                val fromIme = imeBackState.consumeUp(event.downTime)
                if (!fromIme) return false
                if (!event.isCanceled) {
                    host.hideHostIme(windowToken)
                    host.clearComposeInputFocus()
                }
                return true
            }
        }
        return false
    }

    override fun dispatchKeyEventPreIme(event: KeyEvent): Boolean {
        if (handleHostImeBack(event)) return true
        return super.dispatchKeyEventPreIme(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (handleHostImeBack(event)) return true
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            if (event.isCanceled) return true
            if (host.handleModalBack()) return true
            if (host.handleBrowserBack()) return true
            if (host.handleFileBack()) return true
            if (host.handleEditorBack()) return true
            host.animateClosePanel()
            return true
        }
        return dispatchWithCallFallbackGuard(Build.VERSION.SDK_INT, event.keyCode, event.action) {
            super.dispatchKeyEvent(event)
        }
    }
}
