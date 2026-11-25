package com.example.globalminusone.browser

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.ui.graphics.Color

/**
 * Supplies an explicit UI_MODE_NIGHT_YES / UI_MODE_NIGHT_NO Configuration to WebView so
 * Chromium's `AwDarkMode` activates Blink C++ Algorithmic Darkening and `@media (prefers-color-scheme: dark)`
 * on Android 13-16 (`targetSdk = 36`) even when the host Service/device is in daytime mode,
 * while delegating window tokens, IME, and system services to `base`.
 */
internal class NightModeWebViewContext(
    base: Context,
    initialNightMode: Boolean
) : ContextWrapper(base) {
    init {
        // Ensure WebView provider APK assets are globally registered before creating Configuration contexts.
        runCatching { WebSettings.getDefaultUserAgent(base) }
    }

    private var currentNight: Boolean = initialNightMode
    private var configContext: Context = createNightConfigContext(base, initialNightMode)

    var nightMode: Boolean
        get() = currentNight
        set(value) {
            if (currentNight != value) {
                currentNight = value
                configContext = createNightConfigContext(baseContext, value)
            }
        }

    override fun getResources(): Resources = configContext.resources

    override fun getTheme(): Resources.Theme = configContext.theme

    companion object {
        private fun createNightConfigContext(base: Context, night: Boolean): Context {
            val cfg = Configuration(base.resources.configuration)
            val nightBits = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            cfg.uiMode = (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
            val cfgCtx = runCatching { base.createConfigurationContext(cfg) }.getOrDefault(base)
            val themeRes = if (night) {
                android.R.style.Theme_Material_NoActionBar
            } else {
                android.R.style.Theme_Material_Light_NoActionBar
            }
            return android.view.ContextThemeWrapper(cfgCtx, themeRes)
        }
    }
}

internal enum class ReaderPaperTheme(val label: String, val bg: Color, val text: Color, val muted: Color) {
    NIGHT("夜间", Color(0xFF181A20), Color(0xFFE4E8F0), Color(0xFF9AA3B2)),
    SEPIA("护眼", Color(0xFFF5EFE0), Color(0xFF2B251C), Color(0xFF6E6353)),
    LIGHT("纯白", Color(0xFFFFFFFF), Color(0xFF1E222A), Color(0xFF646E7D))
}

internal object BrowserWebViewDarkController {
    fun isCspFallbackActive(frame: FrameLayout?): Boolean = (frame?.tag as? Boolean) == true

    fun setWebViewAlgorithmicDarkening(view: WebView?, allow: Boolean) {
        val target = view ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                if (target.settings.isAlgorithmicDarkeningAllowed != allow) {
                    target.settings.isAlgorithmicDarkeningAllowed = allow
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            runCatching {
                val mode = if (allow) WebSettings.FORCE_DARK_ON else WebSettings.FORCE_DARK_OFF
                if (target.settings.forceDark != mode) {
                    target.settings.forceDark = mode
                }
            }
        }
    }

    fun applyCspColorFilterFallback(
        view: WebView?,
        frame: FrameLayout?,
        active: Boolean,
        sessionNightMode: Boolean
    ) {
        val container = frame ?: return
        val changed = (container.tag as? Boolean) != active
        container.tag = active
        // Mutually exclusive with Blink Algorithmic Darkening to prevent double-inverting #121212 back into light gray
        setWebViewAlgorithmicDarkening(view, allow = sessionNightMode && !active)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val effect = if (active) {
                android.graphics.RenderEffect.createColorFilterEffect(
                    android.graphics.ColorMatrixColorFilter(BrowserNightModeEngine.HUE_PRESERVING_DARK_MATRIX)
                )
            } else {
                null
            }
            container.setRenderEffect(effect)
        } else {
            if (active) {
                val paint = android.graphics.Paint().apply {
                    colorFilter = android.graphics.ColorMatrixColorFilter(BrowserNightModeEngine.HUE_PRESERVING_DARK_MATRIX)
                }
                container.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
            } else if (container.layerType != View.LAYER_TYPE_NONE) {
                container.setLayerType(View.LAYER_TYPE_NONE, null)
            }
        }
        if (changed) {
            view?.invalidate()
            container.invalidate()
        }
    }

    fun applyNativeWebViewDarkSettings(
        view: WebView,
        frame: FrameLayout?,
        nightCtx: NightModeWebViewContext?,
        enabled: Boolean
    ) {
        nightCtx?.nightMode = enabled
        view.setBackgroundColor(if (enabled) BrowserNightModeEngine.DARK_SURFACE_ARGB else android.graphics.Color.WHITE)
        if (view.layerType != View.LAYER_TYPE_NONE) {
            view.setLayerType(View.LAYER_TYPE_NONE, null)
        }
        setWebViewAlgorithmicDarkening(view, allow = enabled && !isCspFallbackActive(frame))
        nightCtx?.resources?.configuration?.let { cfg ->
            runCatching { view.dispatchConfigurationChanged(cfg) }
        }
        view.invalidate()
    }

    fun syncWebViewNightMode(
        view: WebView?,
        frame: FrameLayout?,
        nightCtx: NightModeWebViewContext?,
        nightMode: Boolean,
        url: String,
        javascriptEnabled: Boolean,
        syncDom: Boolean,
        isCurrentSessionTarget: (WebView, Boolean) -> Boolean
    ) {
        val targetView = view ?: return
        if (!nightMode) {
            applyCspColorFilterFallback(targetView, frame, active = false, sessionNightMode = false)
            applyNativeWebViewDarkSettings(targetView, frame, nightCtx, false)
            if (syncDom && url != "about:blank" && javascriptEnabled) {
                runCatching {
                    targetView.evaluateJavascript(BrowserDomStyles.nightModeDomSyncScript(false), null)
                }
            }
            return
        }
        applyNativeWebViewDarkSettings(targetView, frame, nightCtx, true)
        if (url == "about:blank") {
            applyCspColorFilterFallback(targetView, frame, active = false, sessionNightMode = true)
            return
        }
        if (!javascriptEnabled) {
            applyCspColorFilterFallback(targetView, frame, active = true, sessionNightMode = true)
            return
        }
        if (!syncDom) return
        runCatching {
            targetView.evaluateJavascript(BrowserDomStyles.nightModeDomSyncScript(true)) { rawResult ->
                if (!isCurrentSessionTarget(targetView, nightMode)) return@evaluateJavascript
                val status = rawResult?.trim()?.trim('"').orEmpty()
                when (status) {
                    "css_applied" -> applyCspColorFilterFallback(targetView, frame, active = false, sessionNightMode = true)
                    "no_root" -> applyCspColorFilterFallback(targetView, frame, active = false, sessionNightMode = true)
                    else -> applyCspColorFilterFallback(targetView, frame, active = true, sessionNightMode = true)
                }
            }
        }.onFailure {
            applyCspColorFilterFallback(targetView, frame, active = true, sessionNightMode = true)
        }
    }
}
