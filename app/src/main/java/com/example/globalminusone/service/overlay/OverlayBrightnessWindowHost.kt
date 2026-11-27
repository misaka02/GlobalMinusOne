package com.example.globalminusone.service.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat

/**
 * Owns the lightweight `1x1` transparent `TYPE_APPLICATION_OVERLAY` window used to enforce a
 * panel-requested `screenBrightness` override over foreground video apps (e.g. Bilibili),
 * together with `ACTION_SCREEN_OFF` cleanup.
 */
internal class OverlayBrightnessWindowHost(
    private val context: Context,
    private val windowManager: WindowManager,
    private val isDestroying: () -> Boolean,
    private val isAuthorizationPending: () -> Boolean,
    private val syncPanelBrightness: (Float) -> Unit,
    private val onScreenOff: () -> Unit
) {
    private var overrideView: View? = null
    private var screenOffRegistered = false
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) onScreenOff()
        }
    }

    fun registerScreenOff() {
        if (screenOffRegistered) return
        screenOffRegistered = runCatching {
            ContextCompat.registerReceiver(
                context,
                screenOffReceiver,
                IntentFilter(Intent.ACTION_SCREEN_OFF),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            true
        }.getOrDefault(false)
    }

    fun apply(brightness: Float) {
        syncPanelBrightness(brightness)
        if (brightness >= 0f && !isDestroying() && Settings.canDrawOverlays(context)) {
            val hidden = isAuthorizationPending()
            val params = WindowManager.LayoutParams(
                1,
                1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                alpha = 1f
                screenBrightness = brightness
            }
            overrideView = ensureOverlayWindowRegistered(
                existing = overrideView,
                create = {
                    View(context).apply {
                        setBackgroundColor(Color.TRANSPARENT)
                        visibility = if (hidden) View.GONE else View.VISIBLE
                    }
                },
                update = { windowManager.updateViewLayout(it, params) },
                remove = { windowManager.removeViewImmediate(it) },
                add = { windowManager.addView(it, params) },
                onFailure = { Log.e("OverlayService", "Cannot register brightness override window", it) }
            )
            overrideView?.visibility = if (hidden) View.GONE else View.VISIBLE
        } else {
            removeOverrideView()
        }
    }

    fun destroy() {
        if (screenOffRegistered) {
            screenOffRegistered = false
            runCatching { context.unregisterReceiver(screenOffReceiver) }
        }
        removeOverrideView()
    }

    private fun removeOverrideView() {
        overrideView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
            overrideView = null
        }
    }
}
