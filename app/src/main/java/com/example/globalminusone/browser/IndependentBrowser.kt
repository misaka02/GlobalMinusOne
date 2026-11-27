package com.example.globalminusone.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.webkit.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.globalminusone.ui.AppIcons
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlinx.coroutines.launch

private val DetachingWebViewClient = object : WebViewClient() {
    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean = true
}

@Composable
internal fun IndependentBrowser(
    session: IndependentBrowserSession,
    visible: Boolean,
    onHostInputAllowed: (Boolean) -> Unit,
    onBackHandlerChanged: ((() -> Boolean)?) -> Unit,
    modifier: Modifier,
    onClosePanelForExternalApp: (() -> Unit)? = null,
    webViewportModifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val feedback = remember { SnackbarHostState() }
    val feedbackScope = rememberCoroutineScope()
    val closePanelForExternal = onClosePanelForExternalApp
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

    fun syncNightMode(view: WebView?, frame: FrameLayout?, nightCtx: NightModeWebViewContext?, syncDom: Boolean) {
        BrowserWebViewDarkController.syncWebViewNightMode(
            view = view,
            frame = frame,
            nightCtx = nightCtx,
            nightMode = session.nightMode,
            url = tab.url,
            javascriptEnabled = tab.javascriptEnabled,
            syncDom = syncDom,
            isCurrentSessionTarget = { targetView, expectedNight ->
                native === targetView && session.nightMode == expectedNight
            }
        )
    }

    fun toggleNightModeWithFeedback() {
        val next = !session.nightMode
        session.nightMode = next
        if (next) readerTheme = ReaderPaperTheme.NIGHT
        else if (readerTheme == ReaderPaperTheme.NIGHT) readerTheme = ReaderPaperTheme.SEPIA
        syncNightMode(native, nativeFrame, nativeNightContext, syncDom = true)
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
            showFeedback(
                when (deletion) {
                    true -> "独立无痕网站数据已删除"
                    false -> "独立无痕数据删除未确认；下次启动将重试"
                    null -> "已关闭无痕窗口；正在删除独立网站数据"
                }
            )
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
        syncNightMode(native, nativeFrame, nativeNightContext, syncDom = true)
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
            syncNightMode(native, nativeFrame, nativeNightContext, syncDom = false)
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
            else -> handleBrowserPageBack(
                tab.url,
                { native?.canGoBack() == true },
                { native?.goBack() },
                { navigate("about:blank") }
            )
        }
    })
    DisposableEffect(visible) {
        var registered = visible
        onBackHandlerChanged(if (visible) ({ if (registered) back() else false }) else null)
        onDispose { registered = false; onBackHandlerChanged(null) }
    }

    @Composable
    fun renderTopOrBottomAddressBar() {
        if (!findOpen && tab.url != "about:blank") {
            BrowserAddressAndEngineBar(
                address = address,
                onAddressChange = { address = it },
                editingAddress = editingAddress,
                onEditingChanged = { editingAddress = it },
                url = tab.url,
                privateMode = privateMode,
                incognitoMode = session.incognitoMode,
                nightMode = session.nightMode,
                progress = progress,
                selectedEngine = session.searchEngine,
                onRequestHostInput = { hostInput(true) },
                onNavigate = { target, engine -> navigate(target, engine) },
                onToggleNightMode = { toggleNightModeWithFeedback() },
                onStopLoading = { leaveEditor(); error = null; native?.stopLoading(); progress = 100 },
                onReload = { leaveEditor(); error = null; native?.reload() },
                onSelectEngine = { engine ->
                    session.searchEngine = engine
                    if (address.isNotBlank()) navigate(address, engine)
                }
            )
        }
    }

    val useDarkChrome = session.nightMode
    com.example.globalminusone.theme.SettingsTheme(darkTheme = useDarkChrome) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxSize()) {
                    if (!session.bottomAddressBar) renderTopOrBottomAddressBar()
                    if (findOpen) {
                        Row(
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
                                Modifier.weight(1f), editorFocusRequester = findFocusRequester
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
                            key(privateMode) {
                                AndroidView(
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
                                                BrowserWebViewDarkController.applyNativeWebViewDarkSettings(view, hostFrame, nightCtx, initialNight)
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
                                                        BrowserWebViewDarkController.applyCspColorFilterFallback(web, hostFrame, needImmediateFilter, session.nightMode)
                                                        BrowserWebViewDarkController.applyNativeWebViewDarkSettings(web, hostFrame, nightCtx, session.nightMode)
                                                        if (session.nightMode && tab.javascriptEnabled && url != "about:blank") {
                                                            web.postDelayed({
                                                                if (tab.callbacks.accepts(token)) {
                                                                    syncNightMode(web, hostFrame, nightCtx, syncDom = true)
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
                                                        syncNightMode(web, hostFrame, nightCtx, syncDom = true)
                                                        if (tab.url != "about:blank") {
                                                            web.postDelayed({
                                                                if (tab.callbacks.accepts(token)) {
                                                                    syncNightMode(web, hostFrame, nightCtx, syncDom = true)
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
                                                                    syncNightMode(web, hostFrame, nightCtx, syncDom = true)
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
                                                runCatching {
                                                    BrowserWebViewDarkController.applyCspColorFilterFallback(view, frame, false, session.nightMode)
                                                }
                                                if (native === view) {
                                                    runCatching { cancelZoom?.invoke() }
                                                    cancelZoom = null
                                                    nativeNightContext = null
                                                    if (nativeFrame === frame) nativeFrame = null
                                                }
                                                try {
                                                    runCatching { view.setLayerType(View.LAYER_TYPE_NONE, null) }
                                                    runCatching { view.setOnTouchListener(null); view.setFindListener(null); view.setDownloadListener(null) }
                                                    runCatching { view.webChromeClient = null; view.webViewClient = DetachingWebViewClient }
                                                    runCatching { view.stopLoading(); view.onPause() }
                                                } finally {
                                                    runCatching { frame.removeView(view) }
                                                    val destroyed = runCatching { view.destroy() }.isSuccess
                                                    if (native === view) native = null
                                                    if (wasPrivate) {
                                                        val deleted = if (destroyed) privateProfile.onViewDestroyed(view) else null
                                                        if (deleted != null) {
                                                            showFeedback(
                                                                if (deleted) "独立无痕网站数据已删除" else "独立无痕数据删除未确认；下次启动将重试"
                                                            )
                                                        } else if (!destroyed) {
                                                            showFeedback("独立无痕窗口销毁未确认；下次启动将重试清理")
                                                        }
                                                        if (privateMode) {
                                                            privateMode = false
                                                            privateTab = BrowserTab()
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize().then(webViewportModifier)
                                )
                            }
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
                        renderTopOrBottomAddressBar()
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
                                else -> handleBrowserPageBack(
                                    tab.url,
                                    { native?.canGoBack() == true },
                                    { native?.goBack() },
                                    { navigate("about:blank") }
                                )
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
                        BrowserTool(
                            if (privateMode) "关闭独立无痕" else if (privateAvailable) "独立无痕" else "无痕不可用",
                            AppIcons.Shield,
                            enabled = privateMode || privateAvailable,
                            active = privateMode,
                            group = BrowserToolGroup.PRIVACY
                        ) { togglePrivateMode() },
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
                        CompactBrowserToolMenu(commonTools) { tool ->
                            menuOpen = false
                            tool.action()
                        }
                    }
                }

                if (zoomOpen) {
                    BrowserZoomSheet(
                        pagePercent = pagePercent,
                        textZoom = tab.textZoom,
                        forcePageZoom = tab.forcePageZoom,
                        onDismiss = { zoomOpen = false },
                        onTextZoomChange = { nextZoom ->
                            tab.textZoom = nextZoom
                            native?.settings?.textZoom = nextZoom
                        },
                        onPageZoomRequest = { req -> zoom(req) },
                        onForcePageZoomChange = { checked ->
                            tab.forcePageZoom = checked
                            zoom()
                        }
                    )
                }

                if (tabsOpen) {
                    BrowserTabsSheet(session = session, onDismiss = { tabsOpen = false })
                }

                libraryPane?.let { pane ->
                    BrowserLibrarySheet(
                        pane = pane,
                        entries = if (pane == "书签") library.bookmarks else library.history,
                        onDismiss = { libraryPane = null },
                        onRequestHistoryClear = { confirmHistoryClear = true },
                        onOpenUrl = { url -> libraryPane = null; navigate(url) },
                        onRemoveBookmark = { url -> library.removeBookmark(url) }
                    )
                }

                if (confirmHistoryClear) {
                    BrowserClearHistoryDialog(
                        historySize = library.history.size,
                        onDismiss = { confirmHistoryClear = false },
                        onConfirmClear = { library.clearHistory(); confirmHistoryClear = false }
                    )
                }

                if (confirmDataClear) {
                    BrowserClearDataDialog(
                        onDismiss = { confirmDataClear = false },
                        onConfirmClear = {
                            confirmDataClear = false
                            runCatching {
                                native?.clearCache(true)
                                native?.clearFormData()
                                CookieManager.getInstance().removeAllCookies(null)
                                CookieManager.getInstance().flush()
                            }
                            Toast.makeText(context, "已清理网页缓存与 Cookie", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                SnackbarHost(feedback, Modifier.align(Alignment.BottomCenter).imePadding().padding(12.dp))
            }
        }
    }
}
