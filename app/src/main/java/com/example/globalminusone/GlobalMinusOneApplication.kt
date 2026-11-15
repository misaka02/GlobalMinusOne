package com.example.globalminusone

import android.app.Application
import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi

class GlobalMinusOneApplication : Application() {
    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate() {
        super.onCreate()
        // Foundation 1.10.6 keeps a raw drag state across input streams. A lost
        // tracked pointer can leave that state waiting for an UP it cannot see.
        // Select the library's suspending detector before any Activity/overlay
        // creates nodes. Native scrolling, nested scroll and decay are unchanged.
        ComposeFoundationFlags.isNonSuspendingPointerInputInDraggableEnabled = false
        // No WebView initialization when no private profile is registered. A previous
        // crash is cleaned at the process entry shared by activities and services.
        runCatching { com.example.globalminusone.browser.PrivateWebProfile.cleanupStaleOnStartup(this) }
    }
}
