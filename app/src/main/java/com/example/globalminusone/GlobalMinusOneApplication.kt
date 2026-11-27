package com.example.globalminusone

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import com.example.globalminusone.service.OverlayService
import com.example.globalminusone.service.GlobalAccessibilityService
import com.example.globalminusone.system.AppAndIntentLauncher
import com.example.globalminusone.system.SystemAccessibilityActions
import com.example.globalminusone.system.SystemAccessibilityActionsOwner
import com.example.globalminusone.system.ScreenToolRequests
import com.example.globalminusone.system.ScreenToolRequestsOwner
import com.example.globalminusone.system.PanelVisibilityRequests
import com.example.globalminusone.system.PanelVisibilityRequestsOwner
import com.example.globalminusone.system.trimCardImageCache
import com.example.globalminusone.ui.panel.cards.UserIconCache
import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi

internal enum class MemoryTrimAction { NONE, SOFT_TRIM, SEVERE_TRIM }

/**
 * Screen-off and backgrounding dispatch `TRIM_MEMORY_UI_HIDDEN` / `MODERATE`;
 * evicting overlay icon bitmaps there makes the first swipe after unlock jank.
 * Trim only under real memory pressure.
 */
internal fun resolveMemoryTrimAction(level: Int): MemoryTrimAction = when {
    level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> MemoryTrimAction.SOFT_TRIM
    level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> MemoryTrimAction.SOFT_TRIM
    else -> MemoryTrimAction.NONE
}

class GlobalMinusOneApplication : Application(), PanelVisibilityRequestsOwner, SystemAccessibilityActionsOwner, ScreenToolRequestsOwner {
    override val panelVisibilityRequests: PanelVisibilityRequests = object : PanelVisibilityRequests {
        override fun open(context: Context) {
            OverlayService.start(context, OverlayService.ACTION_OPEN_PANEL)
        }

        override fun close(context: Context) {
            OverlayService.start(context, OverlayService.ACTION_CLOSE_PANEL)
        }
    }

    override fun currentSystemAccessibilityActions(): SystemAccessibilityActions? = GlobalAccessibilityService.instance

    override val screenToolRequests: ScreenToolRequests = object : ScreenToolRequests {
        override fun regionScreenshot(context: Context): Boolean =
            requestCapture(context, OverlayService.ACTION_REGION_SCREENSHOT)

        override fun screenAssistant(context: Context): Boolean =
            requestCapture(context, OverlayService.ACTION_SCREEN_ASSISTANT)

        private fun requestCapture(context: Context, action: String): Boolean =
            if (context is OverlayService) context.requestCapture(action) else OverlayService.start(context, action)

        override fun breenoTouch(context: Context) {
            OverlayService.start(context, OverlayService.ACTION_BREENO_TOUCH)
        }

        override fun supportsPrivilegedScreenCapture(context: Context): Boolean {
            val config = com.example.globalminusone.data.MinusOneRepository.getInstance(context).state.value.advancedConfig
            return config.rootModeEnabled || config.screenAssistantConfig.usePrivilegedCapture ||
                com.example.globalminusone.system.privilege.UserRootSession.authorizedEpoch() != null ||
                com.example.globalminusone.screenassistant.ScreenCaptureHub.shizukuAuthorized()
        }
    }

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

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when (resolveMemoryTrimAction(level)) {
            MemoryTrimAction.NONE -> Unit
            MemoryTrimAction.SOFT_TRIM -> trimCachedMemory(severe = false)
            MemoryTrimAction.SEVERE_TRIM -> trimCachedMemory(severe = true)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        trimCachedMemory(severe = true)
    }

    private fun trimCachedMemory(severe: Boolean) {
        trimCardImageCache(clearAll = severe)
        UserIconCache.trimMemory(clearAll = severe)
        AppAndIntentLauncher.trimMemoryCaches(severe = severe)
    }
}
