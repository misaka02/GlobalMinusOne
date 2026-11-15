package com.example.globalminusone.service

import android.os.Build
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/** Dialogs and Popup menus have independent ViewRoots, outside the Activity/root
 * FrameLayout dispatcher. Register on their actual Compose view, after normal
 * child dispatch; no focus changes, input interception or global key filter. */
@Composable
internal fun Android12WindowKeyGuard() {
    // Spell out the lower bound so API analysis also verifies listener safety.
    if (Build.VERSION.SDK_INT < 31 || Build.VERSION.SDK_INT > 32) return
    val view = LocalView.current
    DisposableEffect(view) {
        val listener = View.OnUnhandledKeyEventListener { _, event ->
            isAndroid12CallFallback(Build.VERSION.SDK_INT, event.keyCode, event.action)
        }
        view.addOnUnhandledKeyEventListener(listener)
        onDispose { view.removeOnUnhandledKeyEventListener(listener) }
    }
}
