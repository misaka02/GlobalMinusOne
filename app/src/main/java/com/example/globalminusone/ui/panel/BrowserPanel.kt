package com.example.globalminusone.ui.panel

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.globalminusone.browser.BrowserCallbackLease
import com.example.globalminusone.browser.BrowserSearchEngine
import com.example.globalminusone.browser.IndependentBrowser
import com.example.globalminusone.browser.resolveBrowserDestination
import java.util.UUID

internal object PanelBrowserBackHandler {
    var handler: (() -> Boolean)? = null
    fun handleBack() = handler?.invoke() == true
}

internal class BrowserTab(initialUrl: String = "about:blank") {
    val id = UUID.randomUUID().toString()
    var url by mutableStateOf(initialUrl)
    var title by mutableStateOf("新标签页")
    var state: Bundle? = null
    val callbacks = BrowserCallbackLease()
    var desktop by mutableStateOf(false)
    var javascriptEnabled by mutableStateOf(true)
    var readerMode by mutableStateOf(false)
    var blockedCount by mutableIntStateOf(0)
    var textZoom by mutableIntStateOf(100)
    var forcePageZoom by mutableStateOf(false)
    var pageZoomTarget: Int? = null
    var renderFailed by mutableStateOf(false)
    var restorationError by mutableStateOf<String?>(null)
}

/** Only the active tab owns a WebView. Suspended tabs keep navigation state, not native views. */
internal class IndependentBrowserSession(
    context: Context? = null,
    sharedPrefsOverride: SharedPreferences? = null
) {
    private val prefs: SharedPreferences? = sharedPrefsOverride
        ?: context?.applicationContext?.getSharedPreferences("independent_browser_prefs", Context.MODE_PRIVATE)
    val tabs = mutableStateListOf(BrowserTab())
    var activeId by mutableStateOf(tabs.first().id)

    private fun readPersistedFloat(key: String, fallback: Float): Float {
        val p = prefs ?: return fallback
        if (!p.contains(key)) return fallback
        val v = runCatching { p.getFloat(key, fallback) }.getOrDefault(fallback)
        return if (v.isFinite()) v else fallback
    }

    var windowWidthFraction by mutableFloatStateOf(
        readPersistedFloat(KEY_WINDOW_WIDTH, DEFAULT_WINDOW_WIDTH_FRACTION).coerceIn(0.20f, 1f)
    )
        private set
    var windowHeightFraction by mutableFloatStateOf(
        readPersistedFloat(KEY_WINDOW_HEIGHT, DEFAULT_WINDOW_HEIGHT_FRACTION).coerceIn(0.20f, 1f)
    )
        private set
    var windowXFraction by mutableFloatStateOf(
        readPersistedFloat(KEY_WINDOW_X, DEFAULT_WINDOW_X_FRACTION).coerceIn(0f, 1f - windowWidthFraction)
    )
        private set
    var windowYFraction by mutableFloatStateOf(
        readPersistedFloat(KEY_WINDOW_Y, DEFAULT_WINDOW_Y_FRACTION).coerceIn(0f, 1f - windowHeightFraction)
    )
        private set

    fun updateWindowGeometry(widthFraction: Float, heightFraction: Float, xFraction: Float, yFraction: Float) {
        if (!widthFraction.isFinite() || !heightFraction.isFinite() || !xFraction.isFinite() || !yFraction.isFinite()) return
        val w = widthFraction.coerceIn(0.20f, 1f)
        val h = heightFraction.coerceIn(0.20f, 1f)
        val x = xFraction.coerceIn(0f, (1f - w).coerceAtLeast(0f))
        val y = yFraction.coerceIn(0f, (1f - h).coerceAtLeast(0f))
        windowWidthFraction = w
        windowHeightFraction = h
        windowXFraction = x
        windowYFraction = y
        prefs?.edit()
            ?.putFloat(KEY_WINDOW_WIDTH, w)
            ?.putFloat(KEY_WINDOW_HEIGHT, h)
            ?.putFloat(KEY_WINDOW_X, x)
            ?.putFloat(KEY_WINDOW_Y, y)
            ?.apply()
    }

    private var _searchEngine by mutableStateOf(
        prefs?.getString("search_engine", null)?.let { saved ->
            BrowserSearchEngine.entries.firstOrNull { it.name == saved }
        } ?: BrowserSearchEngine.BAIDU
    )
    var searchEngine: BrowserSearchEngine
        get() = _searchEngine
        set(value) {
            _searchEngine = value
            prefs?.edit()?.putString("search_engine", value.name)?.apply()
        }

    private var _nightMode by mutableStateOf(prefs?.getBoolean("night_mode", false) ?: false)
    var nightMode: Boolean
        get() = _nightMode
        set(value) {
            _nightMode = value
            prefs?.edit()?.putBoolean("night_mode", value)?.apply()
        }

    @Volatile var adBlockVolatile: Boolean = prefs?.getBoolean("ad_block", true) ?: true
        private set
    private var _adBlockEnabled by mutableStateOf(adBlockVolatile)
    var adBlockEnabled: Boolean
        get() = _adBlockEnabled
        set(value) {
            adBlockVolatile = value
            _adBlockEnabled = value
            prefs?.edit()?.putBoolean("ad_block", value)?.apply()
        }

    private var _bottomAddressBar by mutableStateOf(prefs?.getBoolean("bottom_bar", false) ?: false)
    var bottomAddressBar: Boolean
        get() = _bottomAddressBar
        set(value) {
            _bottomAddressBar = value
            prefs?.edit()?.putBoolean("bottom_bar", value)?.apply()
        }

    @Volatile var noImageVolatile: Boolean = prefs?.getBoolean("no_image", false) ?: false
        private set
    private var _noImageMode by mutableStateOf(noImageVolatile)
    var noImageMode: Boolean
        get() = _noImageMode
        set(value) {
            noImageVolatile = value
            _noImageMode = value
            prefs?.edit()?.putBoolean("no_image", value)?.apply()
        }

    var incognitoMode by mutableStateOf(false)
    var readerFontSp by mutableIntStateOf(prefs?.getInt("reader_font_sp", 17)?.coerceIn(13, 28) ?: 17)

    var preferencesGeneration by mutableLongStateOf(0L)
        private set

    /** Reload committed backup preferences without discarding tabs or writing old caches back. */
    fun reloadSavedPreferences() {
        _searchEngine = prefs?.getString("search_engine", null)?.let { saved ->
            BrowserSearchEngine.entries.firstOrNull { it.name == saved }
        } ?: BrowserSearchEngine.BAIDU
        _nightMode = prefs?.getBoolean("night_mode", false) ?: false
        adBlockVolatile = prefs?.getBoolean("ad_block", true) ?: true
        _adBlockEnabled = adBlockVolatile
        _bottomAddressBar = prefs?.getBoolean("bottom_bar", false) ?: false
        noImageVolatile = prefs?.getBoolean("no_image", false) ?: false
        _noImageMode = noImageVolatile
        readerFontSp = prefs?.getInt("reader_font_sp", 17)?.coerceIn(13, 28) ?: 17
        windowWidthFraction = readPersistedFloat(KEY_WINDOW_WIDTH, DEFAULT_WINDOW_WIDTH_FRACTION).coerceIn(0.20f, 1f)
        windowHeightFraction = readPersistedFloat(KEY_WINDOW_HEIGHT, DEFAULT_WINDOW_HEIGHT_FRACTION).coerceIn(0.20f, 1f)
        windowXFraction = readPersistedFloat(KEY_WINDOW_X, DEFAULT_WINDOW_X_FRACTION).coerceIn(0f, 1f - windowWidthFraction)
        windowYFraction = readPersistedFloat(KEY_WINDOW_Y, DEFAULT_WINDOW_Y_FRACTION).coerceIn(0f, 1f - windowHeightFraction)
        preferencesGeneration++
    }

    fun updateReaderFontSp(value: Int) {
        val clamped = value.coerceIn(13, 28)
        readerFontSp = clamped
        prefs?.edit()?.putInt("reader_font_sp", clamped)?.apply()
    }

    val active get() = tabs.firstOrNull { it.id == activeId } ?: tabs.first()
    fun openTab(url: String = "about:blank"): Boolean {
        if (tabs.size >= 8) return false
        val added = BrowserTab(url); tabs.add(added); activeId = added.id
        return true
    }
    fun selectTab(id: String): Boolean {
        if (tabs.none { it.id == id }) return false
        activeId = id; return true
    }
    fun closeTab(id: String): Boolean {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return false
        if (tabs.size == 1) tabs.add(BrowserTab())
        tabs.removeAt(index)
        if (activeId == id) activeId = tabs[(index - 1).coerceAtLeast(0).coerceAtMost(tabs.lastIndex)].id
        return true
    }
    fun closeAllTabs() {
        val fresh = BrowserTab()
        tabs.clear()
        tabs.add(fresh)
        activeId = fresh.id
    }

    companion object {
        const val KEY_WINDOW_WIDTH = "window_width_fraction"
        const val KEY_WINDOW_HEIGHT = "window_height_fraction"
        const val KEY_WINDOW_X = "window_x_fraction"
        const val KEY_WINDOW_Y = "window_y_fraction"
        const val DEFAULT_WINDOW_WIDTH_FRACTION = 0.88f
        const val DEFAULT_WINDOW_HEIGHT_FRACTION = 0.72f
        const val DEFAULT_WINDOW_X_FRACTION = 0.06f
        const val DEFAULT_WINDOW_Y_FRACTION = 0.10f
    }
}

internal fun browserDestination(input: String): String? = resolveBrowserDestination(input)

@Composable
internal fun BrowserPanel(
    session: IndependentBrowserSession,
    visible: Boolean,
    onHostInputAllowed: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    val useDarkChrome = session.nightMode
    var independentBack by remember { mutableStateOf<(() -> Boolean)?>(null) }
    val latestClose by rememberUpdatedState(onClose)
    val latestBack by rememberUpdatedState(independentBack)
    DisposableEffect(visible) {
        val back: () -> Boolean = { if (latestBack?.invoke() != true) latestClose(); true }
        if (visible) PanelBrowserBackHandler.handler = back
        onDispose { if (PanelBrowserBackHandler.handler === back) PanelBrowserBackHandler.handler = null }
    }
    MaterialTheme(colorScheme = if (useDarkChrome) darkColorScheme() else lightColorScheme()) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            key(session.activeId) {
                IndependentBrowser(session, visible,
                    { if (visible) onHostInputAllowed(it) }, { independentBack = it }, Modifier.fillMaxSize())
            }
        }
    }
}

