package com.example.globalminusone.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.webkit.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.globalminusone.ui.AppIcons
import com.example.globalminusone.ui.panel.IndependentBrowserSession
import com.example.globalminusone.ui.panel.BrowserTab
import com.example.globalminusone.ui.panel.ownPanelScrollGestures
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * Supplies an explicit UI_MODE_NIGHT_YES / UI_MODE_NIGHT_NO Configuration to WebView so
 * Chromium's `AwDarkMode` activates Blink C++ Algorithmic Darkening and `@media (prefers-color-scheme: dark)`
 * on Android 13-16 (`targetSdk = 36`) even when the host Service/device is in daytime mode,
 * while delegating window tokens, IME, and system services to `base`.
 */
private class NightModeWebViewContext(
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

private enum class ReaderPaperTheme(val label: String, val bg: Color, val text: Color, val muted: Color) {
    NIGHT("夜间", Color(0xFF181A20), Color(0xFFE4E8F0), Color(0xFF9AA3B2)),
    SEPIA("护眼", Color(0xFFF5EFE0), Color(0xFF2B251C), Color(0xFF6E6353)),
    LIGHT("纯白", Color(0xFFFFFFFF), Color(0xFF1E222A), Color(0xFF646E7D))
}

@Composable
internal fun IndependentBrowser(
    session: IndependentBrowserSession,
    visible: Boolean,
    onHostInputAllowed: (Boolean) -> Unit,
    onBackHandlerChanged: ((() -> Boolean)?) -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val feedback = remember { SnackbarHostState() }
    val feedbackScope = rememberCoroutineScope()
    val closePanelForExternal = com.example.globalminusone.ui.panel.LocalClosePanelForExternalApp.current
    fun showFeedback(message: String) {
        feedback.currentSnackbarData?.dismiss()
        feedbackScope.launch { feedback.showSnackbar(message, duration = SnackbarDuration.Short) }
    }
    val library = remember(context) { BrowserLibrary(context.applicationContext) }
    val privateProfile = remember(context) { PrivateWebProfile(context.applicationContext) }
    val privateAvailable = remember(privateProfile) { privateProfile.supported() }
    var privateMode by remember { mutableStateOf(false) }
    var privateTab by remember { mutableStateOf(BrowserTab()) }
    LaunchedEffect(session.preferencesGeneration) { library.reload() }
    val tab = if (privateMode) privateTab else remember(session.activeId) { session.active }
    var native by remember { mutableStateOf<WebView?>(null) }
    var nativeFrame by remember { mutableStateOf<FrameLayout?>(null) }
    var nativeNightContext by remember { mutableStateOf<NightModeWebViewContext?>(null) }
    var pendingRestoreScrollY by remember { mutableIntStateOf(0) }

    var address by remember { mutableStateOf(tab.url.takeUnless { it == "about:blank" }.orEmpty()) }
    DisposableEffect(privateMode) {
        val modeSnapshot = privateMode
        onDispose { if (modeSnapshot) privateProfile.finishIfUnbound() }
    }
    LaunchedEffect(visible) {
        if (!visible && privateMode) {
            privateMode = false
            privateTab = BrowserTab()
            address = ""
        }
    }
    var editingAddress by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(100) }
    var canForward by remember { mutableStateOf(false) }
    var tabsOpen by remember { mutableStateOf(false) }
    var libraryPane by remember { mutableStateOf<String?>(null) }
    var confirmHistoryClear by remember { mutableStateOf(false) }
    var confirmDataClear by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var zoomOpen by remember { mutableStateOf(false) }
    var cancelZoom by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pagePercent by remember { mutableIntStateOf(100) }
    var error by remember { mutableStateOf(tab.restorationError) }
    var providerAvailable by remember { mutableStateOf(true) }
    var findOpen by remember { mutableStateOf(false) }
    var findText by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf("0 / 0") }
    val findFocusRequester = remember { FocusRequester() }

    // Native Compose Reader Mode states
    var readerArticle by remember { mutableStateOf<ReaderArticle?>(null) }
    var readerLoading by remember { mutableStateOf(false) }
    var readerTheme by remember { mutableStateOf(if (session.nightMode) ReaderPaperTheme.NIGHT else ReaderPaperTheme.SEPIA) }

    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val hostInput by rememberUpdatedState(onHostInputAllowed)
    val currentlyVisible by rememberUpdatedState(visible)

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

    fun applyCspColorFilterFallback(view: WebView?, frame: FrameLayout?, active: Boolean) {
        val container = frame ?: return
        val changed = (container.tag as? Boolean) != active
        container.tag = active
        // Mutually exclusive with Blink Algorithmic Darkening to prevent double-inverting #121212 back into light gray
        setWebViewAlgorithmicDarkening(view, allow = session.nightMode && !active)
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
        syncDom: Boolean
    ) {
        val targetView = view ?: return
        val desiredNight = session.nightMode
        if (!desiredNight) {
            applyCspColorFilterFallback(targetView, frame, false)
            applyNativeWebViewDarkSettings(targetView, frame, nightCtx, false)
            if (syncDom && tab.url != "about:blank" && tab.javascriptEnabled) {
                runCatching {
                    targetView.evaluateJavascript(BrowserDomStyles.nightModeDomSyncScript(false), null)
                }
            }
            return
        }
        applyNativeWebViewDarkSettings(targetView, frame, nightCtx, true)
        if (tab.url == "about:blank") {
            applyCspColorFilterFallback(targetView, frame, false)
            return
        }
        if (!tab.javascriptEnabled) {
            applyCspColorFilterFallback(targetView, frame, true)
            return
        }
        if (!syncDom) return
        runCatching {
            targetView.evaluateJavascript(BrowserDomStyles.nightModeDomSyncScript(true)) { rawResult ->
                if (native !== targetView || session.nightMode != desiredNight) return@evaluateJavascript
                val status = rawResult?.trim()?.trim('"').orEmpty()
                when (status) {
                    "css_applied" -> applyCspColorFilterFallback(targetView, frame, false)
                    "no_root" -> applyCspColorFilterFallback(targetView, frame, false)
                    else -> applyCspColorFilterFallback(targetView, frame, true)
                }
            }
        }.onFailure {
            applyCspColorFilterFallback(targetView, frame, true)
        }
    }

    fun toggleNightModeWithFeedback() {
        val next = !session.nightMode
        session.nightMode = next
        if (next) readerTheme = ReaderPaperTheme.NIGHT
        else if (readerTheme == ReaderPaperTheme.NIGHT) readerTheme = ReaderPaperTheme.SEPIA
        syncWebViewNightMode(native, nativeFrame, nativeNightContext, syncDom = true)
        Toast.makeText(context, if (next) "已开启网页夜间模式" else "已关闭网页夜间模式", Toast.LENGTH_SHORT).show()
    }

    fun zoom(requested: Int? = null) {
        if (requested != null) tab.pageZoomTarget = requested.coerceIn(25, 500)
        val view = native ?: return
        cancelZoom?.invoke()
        if (!tab.javascriptEnabled && requested != null) {
            val current = BrowserPageZoom.percent(view.scale, view.resources.displayMetrics.density)
            view.zoomBy(BrowserPageZoom.factor(current, requested))
            cancelZoom = null
            return
        }
        cancelZoom = view.applyPageZoom(tab.forcePageZoom, requested, { native === view && !tab.renderFailed }) { error = it }
    }

    fun navigationState(view: WebView) {
        canForward = view.canGoForward()
    }

    fun leaveEditor() {
        keyboard?.hide()
        focus.clearFocus()
        editingAddress = false
    }

    fun togglePrivateMode() {
        leaveEditor()
        if (privateMode) {
            val deletion = privateProfile.finishIfUnbound()
            privateMode = false
            privateTab = BrowserTab()
            address = session.active.url.takeUnless { it == "about:blank" }.orEmpty()
            showFeedback(when (deletion) {
                true -> "独立无痕网站数据已删除"
                false -> "独立无痕数据删除未确认；下次启动将重试"
                null -> "已关闭无痕窗口；正在删除独立网站数据"
            })
        } else {
            val started = runCatching { privateProfile.start() }.getOrDefault(false)
            if (!started) {
                showFeedback("当前网页组件不支持独立无痕存储")
                return
            }
            privateTab = BrowserTab()
            privateMode = true
            address = ""
            showFeedback("已进入独立无痕窗口")
        }
        tabsOpen = false
        libraryPane = null
        confirmDataClear = false
        confirmHistoryClear = false
        error = null
        progress = 100
    }

    fun extractReaderContent(view: WebView?) {
        val targetView = view ?: return
        if (!tab.readerMode || tab.url == "about:blank") return
        if (!tab.javascriptEnabled) {
            readerArticle = ReaderArticle(tab.title, "", emptyList())
            readerLoading = false
            return
        }
        if (progress < 100) {
            readerLoading = true
            return
        }
        readerLoading = true
        runCatching {
            targetView.evaluateJavascript(BrowserDomStyles.extractReaderArticleScript()) { raw ->
                if (native === targetView) {
                    readerArticle = parseReaderArticleJson(raw, tab.title) ?: ReaderArticle(tab.title, "", emptyList())
                    readerLoading = false
                }
            }
        }.onFailure {
            readerArticle = ReaderArticle(tab.title, "", emptyList())
            readerLoading = false
        }
    }

    LaunchedEffect(session.nightMode, native, nativeFrame, tab.javascriptEnabled) {
        if (session.nightMode) readerTheme = ReaderPaperTheme.NIGHT
        else if (readerTheme == ReaderPaperTheme.NIGHT) readerTheme = ReaderPaperTheme.SEPIA
        syncWebViewNightMode(native, nativeFrame, nativeNightContext, syncDom = true)
    }

    LaunchedEffect(tab.readerMode, native, tab.url, tab.javascriptEnabled) {
        if (tab.readerMode && tab.url != "about:blank") {
            extractReaderContent(native)
        } else {
            readerArticle = null
            readerLoading = false
        }
    }

    LaunchedEffect(session.noImageMode, native) {
        native?.settings?.let { ws ->
            ws.loadsImagesAutomatically = true
            if (ws.blockNetworkImage != session.noImageMode) {
                ws.blockNetworkImage = session.noImageMode
            }
        }
    }

    LaunchedEffect(findOpen) {
        if (findOpen) {
            hostInput(true)
            runCatching { findFocusRequester.requestFocus() }
            if (findText.isNotEmpty()) native?.findAllAsync(findText)
        }
    }

    fun navigate(value: String, engine: BrowserSearchEngine = session.searchEngine) {
        val resolved = resolveBrowserDestination(value, engine)
        if (resolved == null) {
            error = "网址无效：仅支持 http/https，或输入搜索词"
            return
        }
        val target = desktopPreferredUrl(resolved, tab.desktop)
        leaveEditor()
        error = null
        tab.restorationError = null
        findOpen = false
        tab.blockedCount = 0
        pendingRestoreScrollY = 0
        if (target == "about:blank") {
            tab.readerMode = false
            readerArticle = null
        }
        if (native != null && !tab.renderFailed) {
            tab.url = target
            syncWebViewNightMode(native, nativeFrame, nativeNightContext, syncDom = false)
            native?.loadUrl(target)
        } else {
            tab.state = null
            tab.url = target
            tab.renderFailed = false
            providerAvailable = true
        }
        address = target.takeUnless { it == "about:blank" }.orEmpty()
    }

    fun openInExternalBrowser(url: String) {
        val safeUrl = resolveBrowserDestination(url)?.takeIf {
            it != "about:blank" && (it.startsWith("https://") || it.startsWith("http://"))
        }
        if (safeUrl == null) {
            error = "仅支持在外部浏览器打开有效的 http/https 网页"
            return
        }
        runCatching {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(safeUrl)).apply {
                addCategory(android.content.Intent.CATEGORY_BROWSABLE)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onSuccess {
            leaveEditor()
            Toast.makeText(context, "已交给外部浏览器打开", Toast.LENGTH_SHORT).show()
            // Reveal the launched task while keeping this browser session/tab intact.
            closePanelForExternal?.invoke()
        }.onFailure {
            val message = "无法启动外部浏览器：${it.message ?: "未找到可用浏览器"}"
            error = message
            showFeedback(message)
        }
    }

    LaunchedEffect(tab.url, editingAddress) {
        if (!editingAddress) address = tab.url.takeUnless { it == "about:blank" }.orEmpty()
    }
    DisposableEffect(visible, native, tab.url == "about:blank") {
        val view = native
        if (visible && tab.url != "about:blank") view?.onResume() else {
            cancelZoom?.invoke(); cancelZoom = null
            if (!visible) view?.clearFocus()
            view?.onPause()
        }
        onDispose { runCatching { view?.onPause() } }
    }
    LaunchedEffect(visible) {
        if (!visible) {
            menuOpen = false; zoomOpen = false; tabsOpen = false; libraryPane = null
            confirmHistoryClear = false; confirmDataClear = false
            leaveEditor()
        }
    }
    DisposableEffect(Unit) { hostInput(true); onDispose { hostInput(true) } }

    val back by rememberUpdatedState<() -> Boolean>({
        when {
            confirmDataClear -> { confirmDataClear = false; true }
            confirmHistoryClear -> { confirmHistoryClear = false; true }
            menuOpen -> { menuOpen = false; true }
            zoomOpen -> { zoomOpen = false; true }
            tabsOpen -> { tabsOpen = false; true }
            libraryPane != null -> { libraryPane = null; true }
            tab.readerMode && tab.url != "about:blank" -> { tab.readerMode = false; true }
            findOpen -> { findOpen = false; native?.clearMatches(); leaveEditor(); true }
            editingAddress -> { leaveEditor(); true }
            else -> handleBrowserPageBack(tab.url,
                { native?.canGoBack() == true }, { native?.goBack() }, { navigate("about:blank") })
        }
    })
    DisposableEffect(visible) {
        onBackHandlerChanged(if (visible) ({ back() }) else null)
        onDispose { onBackHandlerChanged(null) }
    }

    val useDarkChrome = session.nightMode
    com.example.globalminusone.theme.SettingsTheme(darkTheme = useDarkChrome) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            @Composable
            fun AddressAndEngineBar() {
                if (!findOpen && tab.url != "about:blank") {
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 6.dp, end = 2.dp).height(46.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isHttps = tab.url.startsWith("https://", ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = when {
                                    privateMode || session.incognitoMode -> MaterialTheme.colorScheme.tertiaryContainer
                                    isHttps -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                    else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                                },
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Text(
                                    text = when {
                                        privateMode -> "独立无痕"
                                        session.incognitoMode -> "暂停历史"
                                        isHttps -> "🔒"
                                        else -> "⚠"
                                    },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            BrowserAddressField(
                                address,
                                { address = it },
                                { hostInput(true) },
                                { navigate(address) },
                                Modifier.weight(1f),
                                { editingAddress = it },
                                placeholder = "使用 ${session.searchEngine.label} 搜索或输入网址"
                            )
                            if (!editingAddress) {
                                IconButton(
                                    onClick = { toggleNightModeWithFeedback() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        AppIcons.DarkMode,
                                        contentDescription = if (session.nightMode) "关闭夜间模式" else "开启夜间模式",
                                        modifier = Modifier.size(18.dp),
                                        tint = if (session.nightMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    if (editingAddress) navigate(address)
                                    else {
                                        leaveEditor(); error = null
                                        if (progress < 100) { native?.stopLoading(); progress = 100 }
                                        else native?.reload()
                                    }
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    if (editingAddress) AppIcons.ArrowForward else if (progress < 100) AppIcons.Close else AppIcons.Refresh,
                                    contentDescription = if (editingAddress) "前往或搜索" else if (progress < 100) "停止加载" else "刷新",
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                        if (editingAddress) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("引擎:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                BrowserSearchEngine.entries.forEach { engine ->
                                    val selected = session.searchEngine == engine
                                    Surface(
                                        onClick = {
                                            session.searchEngine = engine
                                            if (address.isNotBlank()) navigate(address, engine)
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
                                    ) {
                                        Text(
                                            text = engine.label,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Column(Modifier.fillMaxSize()) {
                if (!session.bottomAddressBar) AddressAndEngineBar()
                if (findOpen) Row(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp).height(46.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BrowserFindField(
                        findText,
                        { query ->
                            findText = query
                            if (query.isEmpty()) {
                                matches = "0 / 0"
                                native?.clearMatches()
                            } else {
                                native?.findAllAsync(query)
                            }
                        },
                        { hostInput(true) },
                        Modifier.weight(1f).focusRequester(findFocusRequester)
                    )
                    Text(matches, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp))
                    TextButton(
                        onClick = { native?.findNext(false) },
                        enabled = findText.isNotBlank(),
                        modifier = Modifier.width(42.dp).heightIn(min = 44.dp),
                        contentPadding = PaddingValues(2.dp)
                    ) { Text("上个", fontSize = 12.sp) }
                    TextButton(
                        onClick = { native?.findNext(true) },
                        enabled = findText.isNotBlank(),
                        modifier = Modifier.width(42.dp).heightIn(min = 44.dp),
                        contentPadding = PaddingValues(2.dp)
                    ) { Text("下个", fontSize = 12.sp) }
                    IconButton(
                        onClick = { findOpen = false; native?.clearMatches(); leaveEditor() },
                        modifier = Modifier.size(36.dp)
                    ) { Icon(AppIcons.Close, "关闭查找", Modifier.size(18.dp)) }
                }
                if (progress in 1..99) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth().height(2.dp)
                    )
                }
                error?.let {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                it,
                                Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(onClick = { error = null }, modifier = Modifier.size(30.dp)) {
                                Icon(AppIcons.Close, "关闭错误提示", Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (providerAvailable && !tab.renderFailed && (tab.url != "about:blank" || native != null || tab.state != null)) {
                        key(privateMode) { AndroidView(
                                factory = { androidContext ->
                                    FrameLayout(androidContext).apply {
                                        tag = if (privateMode) privateProfile.activeName else null
                                        var creating: WebView? = null
                                        try {
                                            val hostFrame = this
                                            val defaultUa = runCatching { WebSettings.getDefaultUserAgent(androidContext) }.getOrNull()
                                            val initialNight = session.nightMode
                                            val nightCtx = NightModeWebViewContext(androidContext, initialNight)
                                            val view = (runCatching { WebView(nightCtx) }.getOrElse { WebView(androidContext) }).also { creating = it }
                                            if (privateMode) privateProfile.bind(view)
                                            nativeNightContext = nightCtx
                                            nativeFrame = hostFrame
                                            val token = tab.callbacks.attach()
                                            val blockedCounter = AtomicInteger(tab.blockedCount)
                                            val blockedUiSyncPending = AtomicBoolean(false)
                                            val nightStageSynced = AtomicInteger(0)
                                            view.tag = token
                                            val resolvedDefaultUa = defaultUa ?: view.settings.userAgentString.orEmpty()
                                            view.settings.apply {
                                                javaScriptEnabled = tab.javascriptEnabled
                                                domStorageEnabled = true
                                                allowFileAccess = false
                                                allowContentAccess = false
                                                @Suppress("DEPRECATION")
                                                runCatching {
                                                    allowFileAccessFromFileURLs = false
                                                    allowUniversalAccessFromFileURLs = false
                                                }
                                                @Suppress("DEPRECATION")
                                                saveFormData = false
                                                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                                setSupportZoom(true); builtInZoomControls = true; displayZoomControls = false
                                                useWideViewPort = true; loadWithOverviewMode = true
                                                loadsImagesAutomatically = true
                                                blockNetworkImage = session.noImageVolatile
                                                textZoom = tab.textZoom
                                                userAgentString = if (tab.desktop) desktopAgent(resolvedDefaultUa) else resolvedDefaultUa
                                            }
                                            CookieManager.getInstance().setAcceptThirdPartyCookies(view, false)
                                            applyNativeWebViewDarkSettings(view, hostFrame, nightCtx, initialNight)
                                            view.setDownloadListener { downloadUrl, _, _, _, _ ->
                                                if (tab.callbacks.accepts(token)) {
                                                    val safeDownload = resolveBrowserDestination(downloadUrl)
                                                    error = if (safeDownload != null && safeDownload != "about:blank") {
                                                        "已拦截自动下载请求，如需下载请在菜单选择「外部打开」"
                                                    } else {
                                                        "已拦截不安全的下载请求"
                                                    }
                                                }
                                            }
                                            view.setOnTouchListener { _, event ->
                                                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                                                    hostInput(true); focus.clearFocus()
                                                }
                                                false
                                            }
                                            view.setFindListener { index, count, _ ->
                                                if (tab.callbacks.accepts(token)) {
                                                    matches = if (count == 0) "0 / 0" else "${index + 1} / $count"
                                                }
                                            }
                                            view.webViewClient = object : WebViewClient() {
                                                override fun shouldInterceptRequest(web: WebView, request: WebResourceRequest): WebResourceResponse? {
                                                    if (!request.isForMainFrame &&
                                                        BrowserAdBlocker.shouldBlockRequest(request.url?.toString(), session.adBlockVolatile)
                                                    ) {
                                                        val latestCount = blockedCounter.incrementAndGet()
                                                        if (blockedUiSyncPending.compareAndSet(false, true)) {
                                                            web.postDelayed({
                                                                blockedUiSyncPending.set(false)
                                                                if (tab.callbacks.accepts(token)) {
                                                                    tab.blockedCount = blockedCounter.get()
                                                                }
                                                            }, 250L)
                                                        } else if (latestCount == 1) {
                                                            web.post {
                                                                if (tab.callbacks.accepts(token)) tab.blockedCount = blockedCounter.get()
                                                            }
                                                        }
                                                        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                                                    }
                                                    return super.shouldInterceptRequest(web, request)
                                                }

                                                override fun shouldOverrideUrlLoading(web: WebView, request: WebResourceRequest): Boolean {
                                                    val allowed = request.url.scheme?.lowercase() in setOf("http", "https") ||
                                                        request.url.toString() == "about:blank"
                                                    if (!allowed && request.isForMainFrame && tab.callbacks.accepts(token)) {
                                                        error = "已拦截打开外部应用的请求，留在当前浏览器"
                                                    }
                                                    return !allowed
                                                }

                                                override fun onPageStarted(web: WebView, url: String, icon: Bitmap?) {
                                                    if (!tab.callbacks.accepts(token)) return
                                                    blockedCounter.set(0)
                                                    nightStageSynced.set(0)
                                                    tab.blockedCount = 0
                                                    tab.url = url
                                                    error = null
                                                    progress = 5
                                                    navigationState(web)
                                                    val needImmediateFilter = session.nightMode && !tab.javascriptEnabled && url != "about:blank"
                                                    applyCspColorFilterFallback(web, hostFrame, needImmediateFilter)
                                                    applyNativeWebViewDarkSettings(web, hostFrame, nightCtx, session.nightMode)
                                                    if (session.nightMode && tab.javascriptEnabled && url != "about:blank") {
                                                        web.postDelayed({
                                                            if (tab.callbacks.accepts(token)) {
                                                                syncWebViewNightMode(web, hostFrame, nightCtx, syncDom = true)
                                                            }
                                                        }, 90L)
                                                    }
                                                }

                                                override fun onPageFinished(web: WebView, url: String) {
                                                    if (!tab.callbacks.accepts(token)) return
                                                    tab.url = web.url ?: url
                                                    tab.blockedCount = blockedCounter.get()
                                                    progress = 100
                                                    navigationState(web)
                                                    if (error == null && !session.incognitoMode && hostFrame.tag !is String) {
                                                        library.visit(web.title.orEmpty(), tab.url)
                                                    }
                                                    if (tab.javascriptEnabled && tab.url != "about:blank") {
                                                        runCatching {
                                                            web.evaluateJavascript(BrowserDomStyles.desktopViewportScript(tab.desktop), null)
                                                        }
                                                    }
                                                    syncWebViewNightMode(web, hostFrame, nightCtx, syncDom = true)
                                                    if (tab.url != "about:blank") {
                                                        web.postDelayed({
                                                            if (tab.callbacks.accepts(token)) {
                                                                syncWebViewNightMode(web, hostFrame, nightCtx, syncDom = true)
                                                            }
                                                        }, 300L)
                                                    }
                                                    if (pendingRestoreScrollY > 0) {
                                                        val targetY = pendingRestoreScrollY
                                                        pendingRestoreScrollY = 0
                                                        web.postDelayed({
                                                            if (tab.callbacks.accepts(token)) web.scrollTo(0, targetY)
                                                        }, 80L)
                                                    }
                                                    if (tab.readerMode && tab.url != "about:blank") {
                                                        extractReaderContent(web)
                                                    }
                                                    cancelZoom?.invoke()
                                                    cancelZoom = null
                                                    if (currentlyVisible && tab.url != "about:blank" && (tab.forcePageZoom || tab.pageZoomTarget != null)) {
                                                        cancelZoom = web.applyPageZoom(tab.forcePageZoom, tab.pageZoomTarget, { tab.callbacks.accepts(token) }) { error = it }
                                                    }
                                                }

                                                override fun onScaleChanged(web: WebView, oldScale: Float, newScale: Float) {
                                                    if (tab.callbacks.accepts(token)) {
                                                        pagePercent = BrowserPageZoom.percent(newScale, web.resources.displayMetrics.density)
                                                    }
                                                }

                                                override fun onReceivedError(web: WebView, request: WebResourceRequest, problem: WebResourceError) {
                                                    if (request.isForMainFrame && tab.callbacks.accepts(token)) {
                                                        error = "网页加载失败（${problem.errorCode}）：${problem.description}"
                                                        progress = 100
                                                    }
                                                }

                                                override fun onReceivedHttpError(web: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                                                    if (request.isForMainFrame && tab.callbacks.accepts(token)) {
                                                        error = "服务器返回 HTTP ${response.statusCode}"
                                                    }
                                                }

                                                override fun onReceivedSslError(web: WebView, handler: SslErrorHandler, problem: android.net.http.SslError) {
                                                    handler.cancel()
                                                    if (tab.callbacks.accepts(token)) {
                                                        error = "网站证书验证失败，已停止加载"
                                                        progress = 100
                                                    }
                                                }

                                                override fun onRenderProcessGone(web: WebView, detail: RenderProcessGoneDetail): Boolean {
                                                    if (tab.callbacks.accepts(token)) {
                                                        tab.renderFailed = true
                                                        tab.state = null
                                                        progress = 100
                                                        error = "网页渲染进程已退出"
                                                    }
                                                    return true
                                                }
                                            }
                                            view.webChromeClient = object : WebChromeClient() {
                                                override fun onProgressChanged(web: WebView, value: Int) {
                                                    if (tab.callbacks.accepts(token)) {
                                                        if (value == 100 || value == 0 || abs(value - progress) >= 6) {
                                                            progress = value
                                                        }
                                                        navigationState(web)
                                                        if (session.nightMode && tab.javascriptEnabled && tab.url != "about:blank") {
                                                            val stage = when {
                                                                value >= 70 -> 2
                                                                value >= 25 -> 1
                                                                else -> 0
                                                            }
                                                            if (stage > nightStageSynced.get() && nightStageSynced.compareAndSet(nightStageSynced.get(), stage)) {
                                                                syncWebViewNightMode(web, hostFrame, nightCtx, syncDom = true)
                                                            }
                                                        }
                                                    }
                                                }

                                                override fun onReceivedTitle(web: WebView, title: String?) {
                                                    if (tab.callbacks.accepts(token)) {
                                                        tab.title = if (tab.url == "about:blank") "新标签页" else title?.takeIf { it.isNotBlank() } ?: "网页"
                                                    }
                                                }
                                            }
                                            addView(view, FrameLayout.LayoutParams(-1, -1))
                                            native = view
                                            if (tab.state?.let { view.restoreState(it) } == null) view.loadUrl(tab.url)
                                            navigationState(view)
                                        } catch (failure: Exception) {
                                            var deletion: Boolean? = null
                                            creating?.let { view ->
                                                (view.tag as? Long)?.let(tab.callbacks::detach)
                                                runCatching { removeView(view) }
                                                if (runCatching { view.destroy() }.isSuccess && privateMode) {
                                                    deletion = privateProfile.onViewDestroyed(view)
                                                }
                                                if (native === view) native = null
                                            }
                                            if (privateMode) {
                                                deletion = deletion ?: privateProfile.finishIfUnbound()
                                                privateMode = false
                                                privateTab = BrowserTab()
                                                if (deletion == false) showFeedback("独立无痕数据删除未确认；下次启动将重试")
                                            }
                                            providerAvailable = false
                                            error = "无法创建系统网页组件：${failure.message ?: failure.javaClass.simpleName}"
                                        }
                                    }
                                },
                                update = { frame ->
                                    val targetA11y = if (visible && tab.url != "about:blank") {
                                        View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                                    } else {
                                        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                                    }
                                    if (frame.importantForAccessibility != targetA11y) {
                                        frame.importantForAccessibility = targetA11y
                                    }
                                    if (!visible) {
                                        (frame.getChildAt(0) as? WebView)?.onPause()
                                    }
                                },
                                onRelease = { frame ->
                                    val view = frame.getChildAt(0) as? WebView
                                    if (view != null) {
                                        val token = view.tag as? Long
                                        val wasPrivate = frame.tag is String
                                        try {
                                            if (token != null && tab.callbacks.detach(token) && !tab.renderFailed && !wasPrivate) {
                                                if (tab.url != "about:blank") {
                                                    tab.pageZoomTarget = BrowserPageZoom.percent(view.scale, view.resources.displayMetrics.density)
                                                }
                                                tab.state = Bundle().also { view.saveState(it) }
                                            }
                                        } catch (failure: Exception) {
                                            tab.state = null
                                            tab.restorationError = "标签导航状态未能保存：${failure.message ?: failure.javaClass.simpleName}"
                                        } finally {
                                            runCatching { applyCspColorFilterFallback(view, frame, false) }
                                            if (native === view) {
                                                runCatching { cancelZoom?.invoke() }
                                                cancelZoom = null
                                                nativeNightContext = null
                                                if (nativeFrame === frame) nativeFrame = null
                                            }
                                            try {
                                                runCatching { view.setLayerType(View.LAYER_TYPE_NONE, null) }
                                                runCatching { view.setOnTouchListener(null); view.setFindListener(null); view.setDownloadListener(null) }
                                                runCatching { view.webChromeClient = null; view.webViewClient = WebViewClient() }
                                                runCatching { view.stopLoading(); view.onPause() }
                                            } finally {
                                                runCatching { frame.removeView(view) }
                                                val destroyed = runCatching { view.destroy() }.isSuccess
                                                if (native === view) native = null
                                                if (wasPrivate) {
                                                    val deleted = if (destroyed) privateProfile.onViewDestroyed(view) else null
                                                    if (deleted != null) showFeedback(if (deleted) "独立无痕网站数据已删除" else
                                                        "独立无痕数据删除未确认；下次启动将重试")
                                                    else if (!destroyed) showFeedback("独立无痕窗口销毁未确认；下次启动将重试清理")
                                                    if (privateMode) {
                                                        privateMode = false
                                                        privateTab = BrowserTab()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize().ownPanelScrollGestures(visible, lockFromDown = true)
                            ) }
                    } else if (tab.url != "about:blank") {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(if (tab.renderFailed) "网页渲染进程已退出" else "系统网页组件不可用", style = MaterialTheme.typography.titleMedium)
                            Button(onClick = { tab.renderFailed = false; providerAvailable = true; tab.state = null; error = null }) {
                                Text("重新加载当前网页")
                            }
                        }
                    }

                    // Native Compose Reader Mode Overlay (immune to CSP, popups, and broken mobile CSS)
                    if (tab.readerMode && tab.url != "about:blank") {
                        ReaderModeOverlay(
                            article = readerArticle,
                            loading = readerLoading,
                            javascriptEnabled = tab.javascriptEnabled,
                            paperTheme = readerTheme,
                            fontSp = session.readerFontSp,
                            onSelectTheme = { readerTheme = it },
                            onDecreaseFont = { session.updateReaderFontSp(session.readerFontSp - 2) },
                            onIncreaseFont = { session.updateReaderFontSp(session.readerFontSp + 2) },
                            onEnableJsAndExtract = {
                                tab.javascriptEnabled = true
                                native?.settings?.javaScriptEnabled = true
                                extractReaderContent(native)
                            },
                            onRefreshExtract = { extractReaderContent(native) },
                            onExit = { tab.readerMode = false }
                        )
                    }

                    if (tab.url == "about:blank") {
                        BrowserHome(
                            query = address,
                            onQueryChange = { address = it },
                            requestHostInput = { hostInput(true) },
                            onSearch = { navigate(address) },
                            onEditingChanged = { editingAddress = it },
                            bookmarks = if (privateMode) emptyList() else library.bookmarks,
                            onOpen = { navigate(it) },
                            onBookmarks = { if (!privateMode) { leaveEditor(); libraryPane = "书签" } },
                            selectedEngine = session.searchEngine,
                            onSelectEngine = { session.searchEngine = it },
                            nightMode = session.nightMode,
                            onToggleNightMode = { toggleNightModeWithFeedback() },
                            incognitoMode = session.incognitoMode,
                            onToggleIncognito = {
                                session.incognitoMode = !session.incognitoMode
                                Toast.makeText(
                                    context,
                                    if (session.incognitoMode) "已暂停本地历史；登录、Cookie 和网站数据仍共享" else "已恢复记录本地历史",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            privateMode = privateMode,
                            privateAvailable = privateMode || privateAvailable,
                            onTogglePrivate = { togglePrivateMode() }
                        )
                    }
                }

                if (session.bottomAddressBar) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .25f))
                    AddressAndEngineBar()
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
                Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    BrowserBarButton(
                        if (findOpen) "收起查找" else if (tab.readerMode && tab.url != "about:blank") "退出阅读" else "后退",
                        AppIcons.KeyboardArrowLeft,
                        findOpen || tab.url != "about:blank",
                        Modifier.weight(1f)
                    ) {
                        leaveEditor()
                        when {
                            findOpen -> { findOpen = false; native?.clearMatches() }
                            tab.readerMode && tab.url != "about:blank" -> tab.readerMode = false
                            else -> handleBrowserPageBack(tab.url,
                                { native?.canGoBack() == true }, { native?.goBack() }, { navigate("about:blank") })
                        }
                    }
                    BrowserBarButton("前进", AppIcons.KeyboardArrowRight, canForward, Modifier.weight(1f)) {
                        leaveEditor(); native?.goForward()
                    }
                    BrowserBarButton("主页", AppIcons.Home, true, Modifier.weight(1f)) { navigate("about:blank") }
                    IconButton(
                        onClick = { leaveEditor(); tabsOpen = true },
                        enabled = !privateMode,
                        modifier = Modifier.weight(1f).height(48.dp).semantics { contentDescription = "标签页 ${session.tabs.size}" }
                    ) {
                        Box(
                            Modifier.size(22.dp).border(
                                1.5.dp,
                                if (privateMode || session.incognitoMode) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                RoundedCornerShape(5.dp)
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${session.tabs.size}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    BrowserBarButton("菜单", AppIcons.Menu, true, Modifier.weight(1f)) {
                        leaveEditor(); menuOpen = true
                    }
                }
            }

            if (menuOpen) {
                val bookmarked = library.bookmarks.any { it.url == tab.url }
                val commonTools = listOf(
                    BrowserTool("夜间模式", AppIcons.DarkMode, active = session.nightMode, group = BrowserToolGroup.DISPLAY) {
                        toggleNightModeWithFeedback()
                    },
                    BrowserTool("阅读提纯", AppIcons.EditNote, enabled = tab.url != "about:blank", active = tab.readerMode, group = BrowserToolGroup.DISPLAY) {
                        tab.readerMode = !tab.readerMode
                    },
                    BrowserTool("桌面网页", AppIcons.Language, active = tab.desktop, group = BrowserToolGroup.DISPLAY) {
                        tab.desktop = !tab.desktop
                        tab.pageZoomTarget = null
                        native?.let { view ->
                            val baseUa = runCatching { WebSettings.getDefaultUserAgent(context) }
                                .getOrDefault(view.settings.userAgentString.orEmpty())
                            view.settings.userAgentString = if (tab.desktop) desktopAgent(baseUa) else baseUa
                            view.settings.useWideViewPort = true
                            view.settings.loadWithOverviewMode = true
                            if (tab.url != "about:blank") {
                                val nextUrl = desktopPreferredUrl(tab.url, tab.desktop)
                                if (nextUrl != tab.url) {
                                    tab.url = nextUrl
                                    address = nextUrl
                                    view.loadUrl(nextUrl)
                                } else {
                                    if (!tab.desktop && tab.javascriptEnabled) {
                                        runCatching { view.evaluateJavascript(BrowserDomStyles.desktopViewportScript(false), null) }
                                    }
                                    view.reload()
                                }
                            }
                        }
                        Toast.makeText(context, if (tab.desktop) "已切换为桌面版网页" else "已恢复为手机版网页", Toast.LENGTH_SHORT).show()
                    },
                    BrowserTool("暂停本地历史", AppIcons.Shield, active = session.incognitoMode, group = BrowserToolGroup.PRIVACY) {
                        session.incognitoMode = !session.incognitoMode
                        Toast.makeText(
                            context,
                            if (session.incognitoMode) "已暂停本地历史；登录、Cookie 和网站数据仍共享" else "已恢复记录本地历史",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    BrowserTool(if (privateMode) "关闭独立无痕" else if (privateAvailable) "独立无痕" else "无痕不可用",
                        AppIcons.Shield, enabled = privateMode || privateAvailable, active = privateMode,
                        group = BrowserToolGroup.PRIVACY) { togglePrivateMode() },
                    BrowserTool("新标签", AppIcons.Add, enabled = !privateMode, group = BrowserToolGroup.LIBRARY) {
                        if (!session.openTab()) tabsOpen = true
                    },
                    BrowserTool("书签", BrowserIcons.Bookmark, enabled = !privateMode, group = BrowserToolGroup.LIBRARY) { libraryPane = "书签" },
                    BrowserTool("历史", AppIcons.History, enabled = !privateMode, group = BrowserToolGroup.LIBRARY) { libraryPane = "历史" },
                    BrowserTool(
                        if (bookmarked) "已收藏" else "添加收藏",
                        BrowserIcons.Star,
                        enabled = !privateMode && tab.url != "about:blank" && error == null,
                        active = bookmarked,
                        group = BrowserToolGroup.LIBRARY
                    ) {
                        if (bookmarked) {
                            library.removeBookmark(tab.url)
                            showFeedback("已取消收藏")
                        } else {
                            library.bookmark(tab.title, tab.url)
                            showFeedback("已加入书签收藏")
                        }
                    },
                    BrowserTool(
                        if (session.adBlockEnabled) {
                            if (tab.blockedCount > 99) "拦截99+" else "拦截(${tab.blockedCount})"
                        } else "广告拦截",
                        AppIcons.Shield,
                        active = session.adBlockEnabled,
                        group = BrowserToolGroup.PRIVACY
                    ) {
                        session.adBlockEnabled = !session.adBlockEnabled
                        if (tab.url != "about:blank") native?.reload()
                        Toast.makeText(
                            context,
                            if (session.adBlockEnabled) "已开启广告与追踪器拦截" else "已关闭广告拦截",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    BrowserTool("无图省流", AppIcons.Image, active = session.noImageMode, group = BrowserToolGroup.PRIVACY) {
                        session.noImageMode = !session.noImageMode
                        native?.let { view ->
                            view.settings.loadsImagesAutomatically = true
                            view.settings.blockNetworkImage = session.noImageMode
                            if (!session.noImageMode && tab.url != "about:blank") view.reload()
                        }
                        Toast.makeText(
                            context,
                            if (session.noImageMode) "已开启无图省流（拦截网络图片）" else "已恢复加载网络图片",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    BrowserTool("字号缩放", AppIcons.Search, enabled = native != null && tab.url != "about:blank", group = BrowserToolGroup.DISPLAY) {
                        zoomOpen = true
                    },
                    BrowserTool("页内查找", AppIcons.Search, enabled = native != null && tab.url != "about:blank", active = findOpen) {
                        findOpen = !findOpen
                        if (!findOpen) {
                            native?.clearMatches()
                            leaveEditor()
                        }
                    },
                    BrowserTool(if (tab.javascriptEnabled) "JS:开启" else "JS:已禁", AppIcons.Code, active = tab.javascriptEnabled, group = BrowserToolGroup.PRIVACY) {
                        tab.javascriptEnabled = !tab.javascriptEnabled
                        native?.let { view ->
                            view.settings.javaScriptEnabled = tab.javascriptEnabled
                            if (tab.url != "about:blank") view.reload()
                        }
                        Toast.makeText(
                            context,
                            if (tab.javascriptEnabled) "已允许当前标签执行 JS 脚本" else "已禁止当前标签执行 JS 脚本",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    BrowserTool(if (session.bottomAddressBar) "顶栏地址" else "底栏地址", AppIcons.SwapVert, active = session.bottomAddressBar, group = BrowserToolGroup.DISPLAY) {
                        session.bottomAddressBar = !session.bottomAddressBar
                    },
                    BrowserTool("复制链接", AppIcons.ContentCopy, enabled = tab.url != "about:blank") {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText(tab.title, tab.url))
                        Toast.makeText(context, "已复制当前网址", Toast.LENGTH_SHORT).show()
                    },
                    BrowserTool("外部打开", AppIcons.OpenInNew, enabled = tab.url != "about:blank") {
                        openInExternalBrowser(tab.url)
                    },
                    BrowserTool("清理数据…", AppIcons.Delete, enabled = !privateMode, destructive = true, group = BrowserToolGroup.PRIVACY) {
                        confirmDataClear = true
                    },
                    BrowserTool(
                        if (progress < 100) "停止加载" else "刷新页面",
                        if (progress < 100) AppIcons.Close else AppIcons.Refresh,
                        enabled = tab.url != "about:blank"
                    ) {
                        error = null
                        if (progress < 100) { native?.stopLoading(); progress = 100 } else native?.reload()
                    }
                )
                CompactBrowserPanel("", { menuOpen = false }) {
                    CompactBrowserToolMenu({ menuOpen = false }, commonTools) { tool ->
                        menuOpen = false
                        tool.action()
                    }
                }
            }

            if (zoomOpen) CompactBrowserPanel("字号与页面缩放 ($pagePercent%)", { zoomOpen = false }) {
                Column(
                    Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("网页文字大小 ${tab.textZoom}%", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        FilledTonalButton(
                            onClick = { tab.textZoom = (tab.textZoom - 10).coerceAtLeast(50); native?.settings?.textZoom = tab.textZoom },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) { Text("A−") }
                        Spacer(Modifier.width(6.dp))
                        TextButton(
                            onClick = { tab.textZoom = 100; native?.settings?.textZoom = 100 },
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) { Text("100%") }
                        Spacer(Modifier.width(6.dp))
                        FilledTonalButton(
                            onClick = { tab.textZoom = (tab.textZoom + 10).coerceAtMost(200); native?.settings?.textZoom = tab.textZoom },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) { Text("A＋") }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    Text("整体画面比例（也可双指捏合缩放）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { zoom((pagePercent - 25).coerceAtLeast(25)) }, modifier = Modifier.weight(1f).heightIn(min = 44.dp)) { Text("−25%") }
                        TextButton(onClick = { zoom(100) }, modifier = Modifier.weight(1f).heightIn(min = 44.dp)) { Text("重置 100%") }
                        TextButton(onClick = { zoom((pagePercent + 25).coerceAtMost(500)) }, modifier = Modifier.weight(1f).heightIn(min = 44.dp)) { Text("＋25%") }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("强制突破网页禁止缩放限制", Modifier.weight(1f), fontSize = 13.sp)
                        Switch(checked = tab.forcePageZoom, onCheckedChange = { tab.forcePageZoom = it; zoom() })
                    }
                }
            }

            if (tabsOpen) CompactBrowserPanel("标签页 ${session.tabs.size}/8", { tabsOpen = false }, footer = {
                if (session.tabs.size > 1) {
                    TextButton(onClick = { session.closeAllTabs(); tabsOpen = false }) {
                        Text("关闭全部", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = { if (session.openTab()) tabsOpen = false }, enabled = session.tabs.size < 8) { Text("＋ 新标签页") }
            }) {
                LazyColumn(Modifier.heightIn(max = 340.dp).padding(horizontal = 8.dp)) {
                    items(session.tabs, key = { it.id }) { entry ->
                        val badges = buildList {
                            if (entry.desktop) add("桌面")
                            if (!entry.javascriptEnabled) add("禁JS")
                            if (entry.readerMode) add("阅读")
                            if (entry.blockedCount > 0) add("拦${entry.blockedCount}")
                        }.joinToString(" · ")
                        OutlinedCard(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (entry.id == session.activeId) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = { session.selectTab(entry.id); tabsOpen = false },
                                    modifier = Modifier.weight(1f).heightIn(min = 46.dp)
                                ) {
                                    Column(Modifier.fillMaxWidth()) {
                                        Text(
                                            (if (entry.id == session.activeId) "● " else "") + entry.title + (if (badges.isNotEmpty()) " [$badges]" else ""),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(entry.url, maxLines = 1, style = MaterialTheme.typography.bodySmall, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                IconButton(onClick = { session.closeTab(entry.id) }, modifier = Modifier.size(44.dp)) {
                                    Icon(AppIcons.Close, contentDescription = "关闭标签")
                                }
                            }
                        }
                    }
                }
            }

            libraryPane?.let { pane ->
                val entries = if (pane == "书签") library.bookmarks else library.history
                CompactBrowserPanel(pane, { libraryPane = null }, footer = if (pane == "历史" && entries.isNotEmpty()) ({
                    TextButton(onClick = { confirmHistoryClear = true }) { Text("清空历史", color = MaterialTheme.colorScheme.error) }
                }) else null) {
                    if (entries.isEmpty()) {
                        Text(
                            if (pane == "书签") "还没有书签，可在菜单中收藏当前网页" else "还没有浏览记录",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else LazyColumn(Modifier.heightIn(max = 340.dp)) {
                        items(entries, key = { it.url }) { entry ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = { libraryPane = null; navigate(entry.url) },
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                ) {
                                    Column(Modifier.fillMaxWidth()) {
                                        Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(entry.url, maxLines = 1, style = MaterialTheme.typography.bodySmall, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                if (pane == "书签") {
                                    TextButton(onClick = { library.removeBookmark(entry.url) }) {
                                        Text("移除", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (confirmHistoryClear) CompactBrowserPanel("清空浏览历史？", { confirmHistoryClear = false }, footer = {
                TextButton(onClick = { confirmHistoryClear = false }) { Text("取消") }
                TextButton(
                    onClick = { library.clearHistory(); confirmHistoryClear = false },
                    enabled = library.history.isNotEmpty()
                ) {
                    Text("确认清空", color = MaterialTheme.colorScheme.error)
                }
            }) {
                Text("将删除独立浏览器的 ${library.history.size} 条浏览历史，无法撤销。", modifier = Modifier.padding(16.dp))
            }

            if (confirmDataClear) CompactBrowserPanel("清理网页缓存与 Cookie？", { confirmDataClear = false }, footer = {
                TextButton(onClick = { confirmDataClear = false }) { Text("取消") }
                TextButton(
                    onClick = {
                        confirmDataClear = false
                        runCatching {
                            native?.clearCache(true)
                            native?.clearFormData()
                            CookieManager.getInstance().removeAllCookies(null)
                            CookieManager.getInstance().flush()
                        }
                        Toast.makeText(context, "已清理网页缓存与 Cookie", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("确认清理", color = MaterialTheme.colorScheme.error)
                }
            }) {
                Text("将清除独立浏览器的网页缓存、表单数据及全部网站登录状态（Cookie），无法撤销。", modifier = Modifier.padding(16.dp))
            }
            SnackbarHost(feedback, Modifier.align(Alignment.BottomCenter).imePadding().padding(12.dp))
        }
        }
    }
}

@Composable
private fun ReaderModeOverlay(
    article: ReaderArticle?,
    loading: Boolean,
    javascriptEnabled: Boolean,
    paperTheme: ReaderPaperTheme,
    fontSp: Int,
    onSelectTheme: (ReaderPaperTheme) -> Unit,
    onDecreaseFont: () -> Unit,
    onIncreaseFont: () -> Unit,
    onEnableJsAndExtract: () -> Unit,
    onRefreshExtract: () -> Unit,
    onExit: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(paperTheme.bg)
    ) {
        Surface(
            color = paperTheme.text.copy(alpha = 0.06f),
            contentColor = paperTheme.text,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "阅读提纯",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = paperTheme.text,
                    modifier = Modifier.padding(end = 4.dp)
                )
                ReaderPaperTheme.entries.forEach { option ->
                    val selected = option == paperTheme
                    Surface(
                        onClick = { onSelectTheme(option) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) paperTheme.text.copy(alpha = 0.16f) else Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(1.dp, paperTheme.muted.copy(alpha = 0.35f))
                    ) {
                        Text(
                            option.label,
                            fontSize = 11.sp,
                            color = paperTheme.text,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = onDecreaseFont,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(30.dp).widthIn(min = 34.dp)
                ) { Text("A−", fontSize = 12.sp, color = paperTheme.text) }
                TextButton(
                    onClick = onIncreaseFont,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(30.dp).widthIn(min = 34.dp)
                ) { Text("A＋", fontSize = 12.sp, color = paperTheme.text) }
                TextButton(
                    onClick = onExit,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(30.dp)
                ) { Text("退出", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = paperTheme.text) }
            }
        }
        HorizontalDivider(color = paperTheme.muted.copy(alpha = 0.2f))

        when {
            loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp, color = paperTheme.text)
                        Text("正在提炼网页正文…", fontSize = 13.sp, color = paperTheme.muted)
                    }
                }
            }
            !javascriptEnabled -> {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("当前标签已关闭 JS 脚本，无法读取网页正文", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = paperTheme.text)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onEnableJsAndExtract) { Text("开启 JS 并提取") }
                            OutlinedButton(onClick = onExit) { Text("返回原网页", color = paperTheme.text) }
                        }
                    }
                }
            }
            article == null || article.blocks.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("当前页面未检测到可提纯的长文段落", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = paperTheme.text)
                        Text("适用于新闻、博客、百科、技术文档等正文页面。", fontSize = 12.sp, color = paperTheme.muted)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onRefreshExtract) { Text("重新提取", color = paperTheme.text) }
                            Button(onClick = onExit) { Text("返回原网页") }
                        }
                    }
                }
            }
            else -> {
                SelectionContainer(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(key = "reader-header") {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = article.title,
                                    fontSize = (fontSp + 4).sp,
                                    lineHeight = (fontSp + 10).sp,
                                    fontWeight = FontWeight.Bold,
                                    color = paperTheme.text
                                )
                                if (article.site.isNotBlank()) {
                                    Text(
                                        text = "${article.site} · 共 ${article.blocks.size} 段",
                                        fontSize = 11.sp,
                                        color = paperTheme.muted
                                    )
                                }
                                HorizontalDivider(
                                    modifier = Modifier.padding(top = 6.dp),
                                    color = paperTheme.muted.copy(alpha = 0.25f)
                                )
                            }
                        }
                        items(article.blocks.size, key = { "rb-$it" }) { idx ->
                            val block = article.blocks[idx]
                            if (block.isHeading) {
                                Text(
                                    text = block.text,
                                    fontSize = (fontSp + 2).sp,
                                    lineHeight = (fontSp + 8).sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = paperTheme.text,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            } else {
                                Text(
                                    text = block.text,
                                    fontSize = fontSp.sp,
                                    lineHeight = (fontSp * 1.72f).sp,
                                    color = paperTheme.text
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal class BrowserTool(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val active: Boolean = false,
    val destructive: Boolean = false,
    val group: BrowserToolGroup = BrowserToolGroup.PAGE,
    val action: () -> Unit
)

@Composable
private fun BrowserBarButton(label: String, icon: ImageVector, enabled: Boolean, modifier: Modifier, action: () -> Unit) {
    IconButton(onClick = action, enabled = enabled, modifier = modifier.height(48.dp)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp))
    }
}

@Composable
internal fun BrowserNavigationButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    modifier: Modifier,
    active: Boolean = false,
    destructive: Boolean = false,
    action: () -> Unit
) {
    val containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        destructive -> MaterialTheme.colorScheme.error
        active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        onClick = action,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        modifier = modifier.heightIn(min = 58.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(5.dp))
            Text(
                label,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun BrowserFindField(
    value: String,
    onValueChange: (String) -> Unit,
    requestHostInput: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    BrowserAddressField(
        value,
        onValueChange,
        requestHostInput,
        { keyboard?.hide(); focusManager.clearFocus() },
        modifier,
        placeholder = "查找网页正文",
        keyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
        imeAction = ImeAction.Search
    )
}

/** Genuine Desktop Chrome User-Agent without Android '; wv' or 'Mobile' tokens so sites serve full desktop layouts. */
internal fun desktopAgent(agent: String): String {
    val chromeMatch = Regex("Chrome/[0-9.]+").find(agent)?.value ?: "Chrome/130.0.0.0"
    return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) $chromeMatch Safari/537.36"
}
