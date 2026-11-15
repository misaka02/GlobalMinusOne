package com.example.globalminusone.browser

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.AttributeSet
import android.view.InputDevice
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import com.example.globalminusone.system.UserRootSession
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import rikka.shizuku.Shizuku
import java.io.Closeable
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

/**
 * Native Chrome / embedded app output rendered via in-hierarchy `TextureView`.
 * Unlike `SurfaceView`'s separate `SurfaceControl` punch-hole layer, `TextureView` participates
 * directly in the host window's hardware-accelerated hierarchy. Fit the texture itself,
 * keeping the native View bounds fixed for ancestor clipping and touch dispatch.
 */
class ChromeSurfaceView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    TextureView(context, attrs), TextureView.SurfaceTextureListener {
    enum class Backend { PUBLIC, SHIZUKU, ROOT }
    fun interface StatusListener { fun onStatus(status: String) }
    fun interface ConnectionListener { fun onConnectionChanged(connected: Boolean) }
    fun interface RootAuthorizer { fun requestAuthorization(done: Consumer<Boolean>) }

    private val main = Handler(Looper.getMainLooper())
    private var statusListener: StatusListener? = null
    private var connectionListener: ConnectionListener? = null
    private var rootAuthorizer: RootAuthorizer? = null
    private var hostInputListener: Consumer<Boolean>? = null
    private var hostInputAllowed = true
    private var modeActive = true
    private val presentation = EmbeddedPresentationState()
    private var attachingRevision = -1L
    private var released = false
    private var authorizationListener: Consumer<Boolean>? = null
    private var awaitingAuthorization = false
    @Volatile private var generation = 0L
    @Volatile private var selected: Backend? = null
    private var requestedUrl = "about:blank"
    private var requestedPackage = "com.android.chrome"
    private var requestedComponent: ComponentName? = null
    private var backendLease: EmbeddedBackendPool.Entry? = null
    private var mayRequestAuthorization = true
    private var connection: ServiceConnection? = null
    private var shizukuArgs: Shizuku.UserServiceArgs? = null
    private var permissionListener: Shizuku.OnRequestPermissionResultListener? = null
    private var shizukuDeath: Shizuku.OnBinderDeadListener? = null
    private var rootRevocation: Closeable? = null
    private var rootBindTask: Shell.Task? = null
    @Volatile private var rootEpoch: Long? = null
    private var binderDeath: IBinder.DeathRecipient? = null
    private var client: ChromeBridge.Client? = null
    private var opened = false
    private var opening = false
    private var connecting = false
    private var connectionTimeout: Runnable? = null
    private var io: ExecutorService? = null
    private val queuedTouch = AtomicInteger()
    private var displayWidth = 0
    private var displayHeight = 0
    private var displayDensityDpi = 0
    private var appViewport: EmbeddedAppViewportPolicy.Viewport? = null
    private var contentReflowEnabled = false
    private val resizeQueue = EmbeddedViewportResizeQueue()
    private var canvasSizeListener: (() -> Unit)? = null
    private val resizeDrain = Runnable { drainViewportResize() }
    private var projectionHostWidth = 0
    private var projectionHostHeight = 0
    private var contentViewport = EmbeddedContentViewport()
    private var contentViewportListener: ((EmbeddedContentViewport) -> Unit)? = null
    private var contentPanMode = false
    private var contentPanModeListener: ((Boolean) -> Unit)? = null
    private var contentPanPointer = -1
    private var contentPanStart = EmbeddedContentViewport()
    private var contentPanX = 0f
    private var contentPanY = 0f
    private var gestureDownTime = 0L
    private var gestureActive = false
    @Volatile private var injectedGesture = false
    private var lastTouch: MotionEvent? = null
    private var status = "未启动Chrome容器"
    private var externalOverlayNotice: String? = null
    @Volatile private var activeSurfaceTexture: SurfaceTexture? = null
    private var currentOutputSurface: Surface? = null

    private val heartbeat = object : Runnable {
        override fun run() {
            if (selected == null || !presentation.shouldHeartbeat(opened, isShown && modeActive)) return
            refreshStatus()
            if (!presentation.suspended && !presentation.attached) attachOutputIfReady()
            main.postDelayed(this, heartbeatDelay())
        }
    }
    private fun resumeHeartbeat() {
        main.removeCallbacks(heartbeat)
        if (!released && presentation.shouldHeartbeat(opened, isShown && modeActive)) {
            refreshStatus()
            main.postDelayed(heartbeat, heartbeatDelay())
        }
    }
    private fun heartbeatDelay() = if (modeActive && !presentation.suspended && isShown) 5_000L else 30_000L

    private fun isOutputTextureReady(): Boolean = !released && (activeSurfaceTexture != null || surfaceTexture != null)

    private fun acquireTransferSurface(w: Int, h: Int): Pair<Surface, Surface?>? {
        val st = activeSurfaceTexture ?: surfaceTexture ?: return null
        activeSurfaceTexture = st
        if (w > 0 && h > 0) runCatching { st.setDefaultBufferSize(w, h) }
        val fresh = runCatching { Surface(st) }.getOrNull()?.takeIf { it.isValid } ?: return null
        val previous = currentOutputSurface
        currentOutputSurface = fresh
        return fresh to previous
    }

    private fun releaseLocalOutputSurface() {
        val previous = currentOutputSurface
        currentOutputSurface = null
        if (previous != null) EmbeddedSerialCleanup.release(io) { runCatching { previous.release() } }
    }

    fun hasOutput(): Boolean = opened && presentation.attached && !presentation.suspended && isOutputTextureReady()
    internal fun fullPhoneCanvasSize(): Pair<Int, Int>? = appViewport?.let { it.width to it.height }
    fun canvasSize(): Pair<Int, Int>? = if (displayWidth > 0 && displayHeight > 0) displayWidth to displayHeight else null
    internal fun hasExternalOverlayNotice(): Boolean = externalOverlayNotice != null
    private fun notifyOutputChanged() { statusListener?.onStatus(status) }

    init {
        surfaceTextureListener = this
        isOpaque = false
        // Input belongs to Chrome / the remote app (including its local IME), not this output view.
        // Taking host focus on every DOWN dismisses the remote keyboard before
        // the queued touch can reach its key.
        isFocusable = false
        isFocusableInTouchMode = false
    }

    fun setStatusListener(listener: StatusListener?) = ui { statusListener = listener; listener?.onStatus(status) }
    fun setConnectionListener(listener: ConnectionListener?) = ui {
        connectionListener = listener
        listener?.onConnectionChanged(opened)
    }
    private fun updateConnectionState(value: Boolean) {
        if (opened == value) return
        opened = value
        connectionListener?.onConnectionChanged(value)
    }
    fun setRootAuthorizer(authorizer: RootAuthorizer?) = ui { rootAuthorizer = authorizer }
    fun setHostInputListener(listener: Consumer<Boolean>?) = ui {
        hostInputListener = listener
        listener?.accept(effectiveHostInput())
    }
    private fun effectiveHostInput() = if (modeActive && !presentation.suspended && presentation.attached) hostInputAllowed else true
    /** A host visibility gate cannot be undone by a delayed Compose update. */
    fun setHostPresentationVisible(visible: Boolean) = ui {
        presentation.hostVisible = visible
        syncPresentationSuspension()
    }
    /** Panel-local suspension; never changes app/task identity or grant. */
    fun setPresentationSuspended(suspended: Boolean) = ui {
        presentation.panelSuspended = suspended
        syncPresentationSuspension()
    }
    private fun syncPresentationSuspension() {
        val suspended = presentation.requestedSuspension
        if (presentation.suspended == suspended) {
            if (!suspended) attachOutputIfReady()
            return
        }
        presentation.change(suspended); attachingRevision = -1
        notifyOutputChanged()
        updatePresentationPlacement()
        if (suspended) {
            resizeQueue.hostGestureActive = false
            setContentPanMode(false)
            if (gestureActive) {
                val remote = client
                if (remote != null) lastTouch?.let { old -> MotionEvent.obtain(old).also { cancel ->
                    cancel.action = MotionEvent.ACTION_CANCEL; enqueueTouch(remote, generation, cancel)
                } }
            }
            gestureActive = false; lastTouch?.recycle(); lastTouch = null
            parent?.requestDisallowInterceptTouchEvent(false)
            clearFocus()
            windowToken?.let { token ->
                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
                    ?.hideSoftInputFromWindow(token, 0)
            }
            detachOutput()
        } else {
            refreshStatus()
            connectIfReady(); openIfReady(); attachOutputIfReady()
            fitCanvasToHost()
        }
        hostInputListener?.accept(effectiveHostInput())
        main.removeCallbacks(heartbeat)
        if (presentation.shouldHeartbeat(opened, isShown && modeActive)) main.postDelayed(heartbeat, heartbeatDelay())
    }
    private fun detachOutput() {
        val remote = client ?: return
        if (!opened) return
        val token = generation; val revision = presentation.revision
        val oldLocalSurface = currentOutputSurface
        currentOutputSurface = null
        executor().execute {
            try {
                if (token != generation || revision != presentation.revision) return@execute
                if (!leaseValid(token)) { fail(token, SecurityException("授权已失效")); return@execute }
                runCatching { remote.outputSurface(null, 0, 0, 0) }.onSuccess { result -> ui { if (token == generation) consumeClosed(token, result) } }
                    .onFailure { if (revision == presentation.revision) operationFailed(token, it) }
            } finally {
                if (oldLocalSurface != null) runCatching { oldLocalSurface.release() }
            }
        }
    }
    private fun attachOutputIfReady() {
        val remote = client ?: return
        if (released || !opened || resizeQueue.inFlight != null || presentation.suspended || presentation.attached || !isOutputTextureReady() || !isShown || attachingRevision == presentation.revision) return
        val token = generation; val revision = presentation.revision
        val w = displayWidth; val h = displayHeight; val dpi = displayDensityDpi
        if (w < 1 || h < 1) { attachingRevision = -1; return }
        val (target, previousLocal) = acquireTransferSurface(w, h) ?: return
        attachingRevision = revision
        updateCanvasScale()
        executor().execute {
            try {
                if (token != generation || revision != presentation.revision) return@execute
                if (!leaseValid(token)) { fail(token, SecurityException("授权已失效")); return@execute }
                runCatching { remote.outputSurface(target, w, h, dpi) }.onSuccess { result -> ui {
                    if (token == generation && !consumeClosed(token, result) && presentation.confirmAttached(revision)) {
                        attachingRevision = -1; updateCanvasScale(); updateStatus(result); hostInputListener?.accept(effectiveHostInput())
                        requestViewportResize()
                    }
                } }.onFailure { error -> ui {
                    if (revision == presentation.revision) { attachingRevision = -1; operationFailed(token, error) }
                } }
            } finally {
                if (previousLocal != null && previousLocal !== target) runCatching { previousLocal.release() }
            }
        }
    }
    /** Mode switching suspends host input, not the SurfaceTexture, task or native navigation stack. */
    fun setModeActive(active: Boolean) = ui {
        if (modeActive == active) return@ui
        modeActive = active
        if (!active) setContentPanMode(false)
        main.removeCallbacks(heartbeat)
        if (presentation.shouldHeartbeat(opened, isShown && modeActive)) main.postDelayed(heartbeat, heartbeatDelay())
        updatePresentationPlacement()
        if (active) resumeHeartbeat()
        if (!active && gestureActive) {
            val remote = client
            if (remote != null) lastTouch?.let { old -> MotionEvent.obtain(old).also { cancel ->
                cancel.action = MotionEvent.ACTION_CANCEL; enqueueTouch(remote, generation, cancel)
            } }
            gestureActive = false; lastTouch?.recycle(); lastTouch = null
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        hostInputListener?.accept(effectiveHostInput())
    }
    private fun updatePresentationPlacement() {
        val shown = modeActive && !presentation.suspended
        alpha = if (shown) 1f else 0f
        translationX = 0f
        importantForAccessibility = if (shown) IMPORTANT_FOR_ACCESSIBILITY_AUTO else IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }
    fun requestHostInput() = ui { hostInput(true) }
    internal fun setCanvasSizeListener(listener: (() -> Unit)?) = ui { canvasSizeListener = listener }
    /** Moving/resizing the outer window only projects its existing canvas.
     * Commit real app reflow after the host gesture's final layout. */
    internal fun setHostGeometryChanging(changing: Boolean) = ui {
        if (resizeQueue.hostGestureActive == changing) return@ui
        resizeQueue.hostGestureActive = changing
        main.removeCallbacks(resizeDrain)
        if (!changing) post {
            requestViewportResize()
            // The host's final traversal has completed. Don't leave an extra
            // debounce pause between stretching the outer box and real reflow.
            main.removeCallbacks(resizeDrain)
            main.post(resizeDrain)
        }
    }
    internal fun setContentReflowEnabled(enabled: Boolean) = ui {
        if (contentReflowEnabled == enabled) return@ui
        contentReflowEnabled = enabled
        resizeQueue.retry()
        requestViewportResize()
    }
    private fun requestViewportResize() {
        if (requestedComponent == null || !opened || width < 120 || height < 120) return
        val target = if (contentReflowEnabled) EmbeddedAppViewportPolicy.reflow(width, height, resources.displayMetrics.densityDpi)
            else appViewport ?: return
        val changed = resizeQueue.offer(target)
        if (!changed && target == EmbeddedAppViewportPolicy.Viewport(displayWidth, displayHeight, displayDensityDpi)) return
        main.removeCallbacks(resizeDrain)
        // The final gesture layout selects one geometry. Other hosts without an
        // explicit resize gesture retain the brief debounce/single-flight queue.
        if (!resizeQueue.hostGestureActive) main.postDelayed(resizeDrain, 100)
    }
    private fun drainViewportResize() {
        val remote = client ?: return
        if (!opened || presentation.suspended || !isShown || !isOutputTextureReady() ||
            !presentation.attached || attachingRevision != -1L) return
        val committed = EmbeddedAppViewportPolicy.Viewport(displayWidth, displayHeight, displayDensityDpi)
        val next = resizeQueue.take(committed) ?: return
        val token = generation
        setContentPanMode(false)
        cancelApplicationGesture()
        notifyOutputChanged()
        runCatching { restoreRemoteBufferSize() }.onFailure {
            resizeQueue.complete(next, false)
            loseOutput()
            updateStatus("画布缓冲暂不可用，保留原尺寸和会话")
            attachOutputIfReady()
            return
        }
        executor().execute {
            val result = runCatching {
                if (!leaseValid(token)) throw SecurityException("授权已失效")
                remote.resize(next.width, next.height, next.densityDpi)
            }
            ui {
                if (token != generation) return@ui
                resizeQueue.complete(next, result.isSuccess)
                result.onSuccess { status ->
                    displayWidth = next.width; displayHeight = next.height; displayDensityDpi = next.densityDpi
                    runCatching { restoreRemoteBufferSize() }
                    updateCanvasScale()
                    updateStatus(status)
                    canvasSizeListener?.invoke()
                }.onFailure { failure ->
                    activeSurfaceTexture?.let { runCatching { it.setDefaultBufferSize(committed.width, committed.height) } }
                    // Backend attempted rollback. Reattach only the last confirmed
                    // geometry; an unconfirmed backend rollback rejects input/output
                    // until the same-session output recovery succeeds.
                    loseOutput()
                    operationFailed(token, failure)
                }
                attachOutputIfReady()
                notifyOutputChanged()
                main.removeCallbacks(resizeDrain)
                main.post(resizeDrain)
            }
        }
    }
    internal fun setContentViewportListener(listener: ((EmbeddedContentViewport) -> Unit)?) = ui {
        contentViewportListener = listener
    }
    internal fun setContentViewport(viewport: EmbeddedContentViewport) = ui {
        val next = viewport.sanitized()
        if (next == contentViewport) return@ui
        cancelApplicationGesture()
        finishContentPan(commit = false)
        contentViewport = next
        updateCanvasScale()
    }
    internal fun setContentPanModeListener(listener: ((Boolean) -> Unit)?) = ui {
        contentPanModeListener = listener
        listener?.invoke(contentPanMode)
    }
    internal fun setContentPanMode(enabled: Boolean) = ui {
        if (contentPanMode == enabled) return@ui
        cancelApplicationGesture()
        finishContentPan(commit = true)
        contentPanMode = enabled
        contentPanModeListener?.invoke(enabled)
    }
    private fun cancelApplicationGesture() {
        lastTouch?.let { old -> client?.let { remote ->
            MotionEvent.obtain(old).also { cancel ->
                cancel.action = MotionEvent.ACTION_CANCEL
                enqueueTouch(remote, generation, cancel)
            }
        } }
        gestureActive = false
        lastTouch?.recycle(); lastTouch = null
        parent?.requestDisallowInterceptTouchEvent(false)
    }
    private fun finishContentPan(commit: Boolean) {
        if (contentPanPointer == -1) return
        contentPanPointer = -1
        if (commit) contentViewportListener?.invoke(contentViewport)
        else { contentViewport = contentPanStart; updateCanvasScale() }
        parent?.requestDisallowInterceptTouchEvent(false)
    }
    private fun handleContentPan(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                contentPanPointer = event.getPointerId(0)
                contentPanX = event.x; contentPanY = event.y
                contentPanStart = contentViewport
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(contentPanPointer)
                if (index >= 0) {
                    contentViewport = contentPanStart.moved(
                        event.getX(index) - contentPanX, event.getY(index) - contentPanY,
                        width, height, displayWidth, displayHeight)
                    updateCanvasScale()
                }
            }
            // Pan mode has explicit ownership. Additional fingers never leak a
            // partial remote pinch; release ownership when the primary finger lifts.
            MotionEvent.ACTION_POINTER_UP -> if (event.getPointerId(event.actionIndex) == contentPanPointer) {
                finishContentPan(commit = true)
            }
            MotionEvent.ACTION_UP -> finishContentPan(commit = true)
            MotionEvent.ACTION_CANCEL -> {
                finishContentPan(commit = false)
                setContentPanMode(false)
            }
        }
        return true
    }
    fun activeBackend(): Backend? = selected
    fun setExternalAuthorizationListener(listener: Consumer<Boolean>?) = ui {
        authorizationListener = listener
        listener?.accept(awaitingAuthorization)
    }
    private fun externalAuthorization(waiting: Boolean) {
        if (awaitingAuthorization == waiting) return
        awaitingAuthorization = waiting
        authorizationListener?.accept(waiting)
    }
    private fun hostInput(allowed: Boolean) {
        if (hostInputAllowed == allowed) return
        hostInputAllowed = allowed
        if (modeActive && !presentation.suspended) hostInputListener?.accept(allowed)
    }

    @JvmOverloads
    fun startBackend(mode: Backend, url: String, chromePackage: String = "com.android.chrome") = ui {
        startBrowser(mode, url, chromePackage, true)
    }
    private fun startBrowser(mode: Backend, url: String, chromePackage: String, requestAuthorization: Boolean) {
        if (released) return
        if (!validUrl(url)) {
            updateStatus("网址仅支持完整http/https或about:blank；未请求授权"); return
        }
        if (selected == mode && requestedComponent == null && requestedPackage == chromePackage && (opened || opening || connecting)) {
            if (opened && url != requestedUrl && url != "about:blank") navigate(url)
            else { attachOutputIfReady(); refreshStatus() }
            return
        }
        stopInternal()
        mayRequestAuthorization = requestAuthorization
        if (requestAuthorization) EmbeddedBackendPool.rememberSelection(context, mode)
        selected = mode; requestedUrl = url; requestedPackage = chromePackage; requestedComponent = null
        updateStatus("等待可用显示区域…")
        connectIfReady()
    }
    fun resumeAuthorized(url: String, chromePackage: String = "com.android.chrome", preferred: Backend? = null) = ui {
        val mode = if (preferred == null) EmbeddedBackendPool.authorizedSelection(context)
            else preferred.takeIf(EmbeddedBackendPool::isAuthorized)
        if (mode != null) startBrowser(mode, url, chromePackage, false)
        else if (preferred != null) updateStatus("上次 ${preferred.name} 授权已失效，请明确选择连接方式；没有重新请求权限")
    }
    /** Explicit component only. Unsupported launch modes fail before any app activity starts. */
    fun connectApp(component: ComponentName, backend: Backend) = ui { startApp(component, backend, true) }
    fun resumeAuthorizedApp(component: ComponentName) = ui {
        EmbeddedBackendPool.authorizedSelection(context)?.let { startApp(component, it, false) }
    }
    private fun startApp(component: ComponentName, backend: Backend, requestAuthorization: Boolean) {
        if (released) return
        val target = EmbeddedAppTargets.canonical(component)
        if (selected == backend && requestedComponent == target && (opened || opening || connecting)) {
            attachOutputIfReady(); refreshStatus(); return
        }
        stopInternal()
        mayRequestAuthorization = requestAuthorization
        if (requestAuthorization) EmbeddedBackendPool.rememberSelection(context, backend)
        selected = backend; requestedComponent = target; requestedPackage = target.packageName
        requestedUrl = "about:blank"
        updateStatus("等待应用小窗显示区域…"); connectIfReady()
    }
    fun endBackendSession(backend: Backend) = ui { stopInternal(); EmbeddedBackendPool.end(backend); updateStatus("已结束小窗后端连接") }
    fun navigate(url: String) = ui {
        if (!validUrl(url)) { updateStatus("网址无效，尚未导航"); return@ui }
        val current = client
        if (current == null || !opened) updateStatus("请先明确选择并启动一个后端")
        else { requestedUrl = url; hostInput(false); call(current, generation) { it.navigate(url) } }
    }
    fun back() = ui { client?.takeIf { opened && !presentation.suspended }?.let { hostInput(false); call(it, generation) { remote -> remote.back() } } }
    fun command(command: ChromeCommand) = ui {
        client?.takeIf { opened && !presentation.suspended }?.let { hostInput(false); call(it, generation) { remote -> remote.command(command) } }
    }
    fun refreshStatus() = ui { client?.takeIf { opened }?.let { call(it, generation) { remote -> remote.status() } } }
    fun stop() = ui { stopInternal(); updateStatus("Chrome容器已停止") }
    /** Final owner disposal, unlike a transient native View detach. */
    fun release() = ui {
        if (released) return@ui
        stopInternal(); releaseLocalOutputSurface(); activeSurfaceTexture = null; released = true; presentation.disposed()
        // Shut down only after queued transfers/close have returned. TextureView may
        // deliver its destroy callback while those operations are still draining.
        EmbeddedSerialCleanup.finish(io)
    }
    fun retryPresentation() = ui {
        resizeQueue.retry()
        if (opened && resizeQueue.inFlight == null) { loseOutput(); attachOutputIfReady(); requestViewportResize() }
    }
    fun onHostStopped() = ui { loseOutput() }

    private fun ui(action: () -> Unit) { if (Looper.myLooper() == Looper.getMainLooper()) action() else main.post { action() } }
    private fun updateStatus(value: String) {
        if (value.startsWith("external_overlay:")) externalOverlayNotice = value.substringAfter(':').trim()
        else if (value.startsWith("display=") || value.startsWith("closed") || value.startsWith("不可用：")) externalOverlayNotice = null
        status = (if (value.startsWith("external_overlay:") || value == "accepted") externalOverlayNotice ?: value else value).take(1000)
        statusListener?.onStatus(status)
    }
    private fun validUrl(url: String): Boolean = url.length <= 8192 && url.none { it.code < 32 || it.code == 127 } &&
        (url == "about:blank" || runCatching {
            val uri = android.net.Uri.parse(url)
            uri.scheme?.lowercase(Locale.ROOT) in setOf("https", "http") && !uri.host.isNullOrBlank()
        }.getOrDefault(false))
    private fun executor(): ExecutorService = io?.takeUnless { it.isShutdown } ?: Executors.newSingleThreadExecutor { action ->
        Thread(action, "GmoChromeSurface").apply { isDaemon = true }
    }.also { io = it }
    private fun leaseValid(token: Long): Boolean = token == generation && selected != null && when (selected) {
        Backend.ROOT -> rootEpoch?.let(UserRootSession::isAuthorizationCurrent) == true
        Backend.SHIZUKU -> runCatching { Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
        else -> true
    }
    private fun fail(token: Long, error: Throwable) = ui {
        if (token != generation) return@ui
        stopInternal(); updateStatus("不可用：${error.message ?: error.javaClass.simpleName}")
    }
    private fun consumeClosed(token: Long, result: String): Boolean {
        if (!result.startsWith("closed")) return false
        fail(token, IllegalStateException(result)); return true
    }
    /** Presentation/input rejection is not revocation. Only real grant/binder loss
     * or a status/open failure can end the owned task. */
    private fun operationFailed(token: Long, error: Throwable) = ui {
        if (token != generation) return@ui
        if (EmbeddedConnectionPolicy.mustClose(leaseValid(token), client?.binder()?.isBinderAlive == true && error !is android.os.DeadObjectException))
            fail(token, error)
        else updateStatus("操作未完成，连接仍保留：${error.message ?: error.javaClass.simpleName}")
    }
    private fun call(remote: ChromeBridge.Client, token: Long, work: (ChromeBridge.Client) -> String) {
        executor().execute {
            if (!leaseValid(token)) { fail(token, IllegalStateException("授权已结束或会话已关闭")); return@execute }
            runCatching { work(remote) }.onSuccess { result -> ui { if (token == generation) {
                if (result.startsWith("closed")) fail(token, IllegalStateException(result)) else {
                    if (result.startsWith("output_unavailable:")) loseOutput()
                    updateStatus(result)
                }
            } } }.onFailure { operationFailed(token, it) }
        }
    }

    private fun connectIfReady() {
        if (released) return
        val mode = selected ?: return
        if (presentation.suspended || !isOutputTextureReady() || width < 1 || height < 1 || !isShown || connecting || client != null) return
        val token = generation
        connecting = true
        EmbeddedBackendPool.acquire(mode)?.let { cached ->
            backendLease = cached; rootEpoch = cached.epoch
            if (mode == Backend.ROOT) rootRevocation = UserRootSession.registerRevocationListener {
                fail(token, SecurityException("Root会话已撤销，应用小窗已释放"))
            }
            connectedSession(token, cached.factory)
            return
        }
        when (mode) {
            Backend.PUBLIC -> connected(token, ChromeBridge.Server(context.applicationContext, false))
            Backend.SHIZUKU -> {
                if (!Shizuku.pingBinder()) { fail(token, IllegalStateException("Shizuku服务未运行，请在Shizuku中启动后重试")); return }
                shizukuDeath = Shizuku.OnBinderDeadListener { fail(token, IllegalStateException("Shizuku连接已断开")) }
                    .also { Shizuku.addBinderDeadListener(it) }
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) bindShizuku(token)
                else {
                    if (!mayRequestAuthorization) { fail(token, SecurityException("Shizuku授权已失效，请明确选择后重试")); return }
                    val code = permissionCodes.incrementAndGet()
                    permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
                        if (requestCode == code) ui {
                            if (token != generation) return@ui
                            externalAuthorization(false)
                            permissionListener?.let(Shizuku::removeRequestPermissionResultListener); permissionListener = null
                            if (grantResult == PackageManager.PERMISSION_GRANTED) bindShizuku(token)
                            else fail(token, SecurityException("Shizuku授权已拒绝；没有启动Chrome"))
                        }
                    }.also { Shizuku.addRequestPermissionResultListener(it) }
                    updateStatus("等待您明确授予Shizuku权限…")
                    externalAuthorization(true)
                    runCatching { Shizuku.requestPermission(code) }.onFailure { fail(token, it) }
                }
            }
            Backend.ROOT -> {
                val epoch = UserRootSession.authorizedEpoch()
                if (epoch != null) bindRoot(token, epoch)
                else {
                    if (!mayRequestAuthorization) { fail(token, SecurityException("Root会话已失效，请明确选择后重试")); return }
                    val authorizer = rootAuthorizer
                    if (authorizer == null) { fail(token, SecurityException("请先明确授予本次共享Root会话")); return }
                    updateStatus("等待您明确授予Root权限…")
                    externalAuthorization(true)
                    runCatching { authorizer.requestAuthorization(Consumer { granted -> ui {
                        if (token != generation) return@ui
                        externalAuthorization(false)
                        val lease = UserRootSession.authorizedEpoch()
                        if (granted && lease != null) bindRoot(token, lease)
                        else fail(token, SecurityException("Root未授权；没有启动Chrome"))
                    } }) }.onFailure { fail(token, it) }
                }
            }
        }
    }
    private fun bindShizuku(token: Long) {
        if (token != generation) return
        val args = Shizuku.UserServiceArgs(ComponentName(context.packageName, ShizukuChromeService::class.java.name))
            .daemon(false).tag("chrome-${UUID.randomUUID()}").processNameSuffix("chrome_surface").version(1)
        shizukuArgs = args
        val conn = newConnection(token, Backend.SHIZUKU, args)
        connection = conn
        updateStatus("连接Shizuku Chrome服务…")
        runCatching { Shizuku.bindUserService(args, conn) }.onFailure { fail(token, it) }
        bindTimeout(token)
    }
    private fun bindRoot(token: Long, epoch: Long) {
        if (token != generation || !UserRootSession.isAuthorizationCurrent(epoch)) { fail(token, SecurityException("Root会话已失效")); return }
        rootEpoch = epoch
        rootRevocation = UserRootSession.registerRevocationListener { fail(token, SecurityException("Root会话已撤销，Chrome容器已释放")) }
        if (!UserRootSession.isAuthorizationCurrent(epoch)) { fail(token, SecurityException("Root会话已失效")); return }
        val retained = java.util.concurrent.atomic.AtomicBoolean()
        val conn = newConnection(token, Backend.ROOT, null, retained)
        connection = conn
        updateStatus("连接已授权Root Chrome服务…")
        runCatching {
            val task = RootService.bindOrTask(Intent().setComponent(ComponentName(context.packageName,
                "com.example.globalminusone.browser.RootChromeService")),
                java.util.concurrent.Executor { action -> main.post(action) }, conn)
            rootBindTask = task
            if (task != null) executor().execute {
                runCatching {
                    val shell = Shell.getCachedShell()
                    if (!leaseValid(token) || shell == null || !shell.isRoot)
                        throw SecurityException("Root缓存会话已结束，请明确重新授权")
                    shell.execTask(task)
                    if (!leaseValid(token) && !retained.get()) throw SecurityException("Root会话在启动期间结束")
                }.onFailure { if (!retained.get()) RootService.cancelBindTask(task); fail(token, it) }
            }
        }.onFailure { fail(token, it) }
        if (!UserRootSession.isAuthorizationCurrent(epoch)) fail(token, SecurityException("Root会话在连接期间结束"))
        bindTimeout(token)
    }
    private fun cancelConnectionTimeout() {
        connectionTimeout?.let(main::removeCallbacks)
        connectionTimeout = null
    }
    private fun bindTimeout(token: Long) {
        if (token != generation || !connecting || client != null) return
        cancelConnectionTimeout()
        val timeout = Runnable {
            connectionTimeout = null
            if (token == generation && connecting && client == null)
                fail(token, IllegalStateException("Chrome服务连接超时"))
        }
        connectionTimeout = timeout
        main.postDelayed(timeout, 10000)
    }
    private fun newConnection(token: Long, mode: Backend, args: Shizuku.UserServiceArgs?, retained: java.util.concurrent.atomic.AtomicBoolean? = null): ServiceConnection {
        val target = java.lang.ref.WeakReference(this)
        val handedToPool = java.util.concurrent.atomic.AtomicBoolean(false)
        return object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                val view = target.get()
                if (view == null) { EmbeddedBackendPool.unbind(mode, this, args); return }
                view.ui {
                    if (handedToPool.get()) return@ui
                    if (token != view.generation || !view.leaseValid(token)) { EmbeddedBackendPool.unbind(mode, this, args); return@ui }
                    try {
                        handedToPool.set(true)
                        val cached = EmbeddedBackendPool.adopt(mode, binder, this, args, view.rootEpoch)
                        retained?.set(true)
                        view.backendLease = cached
                        view.connection = null; view.shizukuArgs = null; view.rootBindTask = null
                        view.connectedSession(token, cached.factory)
                    } catch (error: Exception) { view.fail(token, error) }
                }
            }
            override fun onServiceDisconnected(name: ComponentName) { if (!handedToPool.get()) target.get()?.fail(token, IllegalStateException("小窗服务已断开")) }
            override fun onBindingDied(name: ComponentName) { if (!handedToPool.get()) target.get()?.fail(token, IllegalStateException("小窗服务绑定已失效")) }
            override fun onNullBinding(name: ComponentName) { if (!handedToPool.get()) target.get()?.fail(token, IllegalStateException("小窗服务未提供有效连接")) }
        }
    }
    private fun connectedSession(token: Long, factory: ChromeBridge.Client) {
        // The cached factory is still a synchronous cross-process Binder client.
        // Share the open/close/output queue so a restart cannot overtake its old close.
        executor().execute {
            if (!leaseValid(token)) return@execute
            runCatching { factory.newSession() }.onSuccess { session ->
                ui { connected(token, session.binder(), session) }
            }.onFailure { fail(token, it) }
        }
    }
    private fun connected(token: Long, binder: IBinder, session: ChromeBridge.Client? = null) {
        if (!leaseValid(token)) {
            session?.let(EmbeddedBackendPool::closeSession)
            fail(token, SecurityException("授权已失效，未打开窗口")); return
        }
        cancelConnectionTimeout()
        val remote = session ?: ChromeBridge.Client(binder)
        client = remote; connecting = false
        binderDeath = IBinder.DeathRecipient { fail(token, IllegalStateException("Chrome显示进程已结束")) }
        runCatching { binder.linkToDeath(binderDeath!!, 0) }.onFailure { fail(token, it); return }
        openIfReady()
    }
    private fun dimensions(): Pair<Int, Int> {
        if (requestedComponent == null) return width.coerceIn(64, 2048) to height.coerceIn(64, 2048)
        val viewport = appViewport ?: run {
            val metrics = resources.displayMetrics
            val fullBounds = if (android.os.Build.VERSION.SDK_INT >= 30) runCatching {
                (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager)
                    .maximumWindowMetrics.bounds
            }.getOrNull() else null
            EmbeddedAppViewportPolicy.create(
                fullBounds?.width() ?: metrics.widthPixels,
                fullBounds?.height() ?: metrics.heightPixels,
                metrics.densityDpi
            ).also { appViewport = it }
        }
        return viewport.width to viewport.height
    }
    private fun densityDpi(widthPx: Int, heightPx: Int): Int =
        appViewport?.densityDpi ?: ChromeViewportPolicy.densityDpi(
            widthPx, heightPx, resources.displayMetrics.densityDpi
        )
    private fun openIfReady() {
        val remote = client ?: return
        if (presentation.suspended || opened || opening || !isOutputTextureReady() || !isShown) return
        if (width < 120 || height < 120 || isLayoutRequested) {
            post { if (!released && !opened && !opening) openIfReady() }
            return
        }
        val token = generation
        val outputRevision = presentation.revision
        val (w, h) = dimensions()
        val dpi = densityDpi(w, h)
        val (target, previousLocal) = acquireTransferSurface(w, h) ?: return
        displayWidth = w; displayHeight = h; displayDensityDpi = dpi
        opening = true
        val url = requestedUrl; val pkg = requestedPackage; val component = requestedComponent
        executor().execute {
            try {
                if (!leaseValid(token)) { fail(token, SecurityException("授权已失效")); return@execute }
                runCatching { if (component == null) remote.open(target, w, h, dpi, pkg, url) else remote.openApp(target, w, h, dpi, component) }.onSuccess { result -> ui {
                    if (token != generation || consumeClosed(token, result)) return@ui
                    updateConnectionState(true); opening = false
                    if (isOutputTextureReady()) presentation.confirmAttached(outputRevision)
                    updateStatus(result)
                    if (!presentation.attached) { detachOutput(); attachOutputIfReady() }
                    hostInput(false)
                    fitCanvasToHost()
                    requestViewportResize()
                    main.removeCallbacks(heartbeat)
                    if (presentation.shouldHeartbeat(opened, isShown && modeActive)) main.postDelayed(heartbeat, heartbeatDelay())
                } }.onFailure { fail(token, it) }
            } finally {
                if (previousLocal != null && previousLocal !== target) runCatching { previousLocal.release() }
            }
        }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        activeSurfaceTexture = surface
        runCatching { restoreRemoteBufferSize() }
        connectIfReady()
        openIfReady()
        attachOutputIfReady()
        fitCanvasToHost()
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        activeSurfaceTexture = surface
        // super.onSizeChanged has just installed the compact host dimensions.
        // Skipping this during resize sends the full VD into the wrong buffer.
        runCatching { restoreRemoteBufferSize() }
        connectIfReady()
        openIfReady()
        attachOutputIfReady()
        fitCanvasToHost()
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        if (activeSurfaceTexture === surface) activeSurfaceTexture = null
        // Texture replacement (resize, visibility, window reattach) does not end the owned task.
        loseOutput()
        releaseLocalOutputSurface()
        // Retain the texture until queued Binder marshalling and detach have finished.
        // Returning false transfers release responsibility from TextureView to us.
        EmbeddedSerialCleanup.release(io) { runCatching { surface.release() } }
        return false
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    private fun restoreRemoteBufferSize() {
        if (displayWidth < 1 || displayHeight < 1) return
        val viewport = resizeQueue.bufferViewport(
            EmbeddedAppViewportPolicy.Viewport(displayWidth, displayHeight, displayDensityDpi))
        (activeSurfaceTexture ?: surfaceTexture)?.setDefaultBufferSize(viewport.width, viewport.height)
    }

    private fun loseOutput() {
        setContentPanMode(false)
        presentation.outputLost(); attachingRevision = -1
        notifyOutputChanged()
        gestureActive = false; parent?.requestDisallowInterceptTouchEvent(false)
        lastTouch?.let { old -> client?.let { remote -> MotionEvent.obtain(old).also { cancel ->
            cancel.action = MotionEvent.ACTION_CANCEL; enqueueTouch(remote, generation, cancel)
        } } }; lastTouch?.recycle(); lastTouch = null
        hostInputListener?.accept(effectiveHostInput())
        detachOutput()
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (right > left && bottom > top) fitCanvasToHost()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (!isVisible) main.removeCallbacks(heartbeat)
        if (isVisible && !released && !presentation.suspended) {
            post {
                if (!released && !presentation.suspended) {
                    connectIfReady()
                    openIfReady()
                    attachOutputIfReady()
                    fitCanvasToHost()
                    resumeHeartbeat()
                }
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w < 1 || h < 1) return
        clipBounds = Rect(0, 0, w, h)
        connectIfReady()
        fitCanvasToHost()
    }
    private fun fitCanvasToHost() {
        if (opened && width > 0 && height > 0) {
            if (projectionHostWidth == width && projectionHostHeight == height) return
            projectionHostWidth = width; projectionHostHeight = height
            requestViewportResize()
            finishContentPan(commit = true)
            if (gestureActive) {
                lastTouch?.let { old -> MotionEvent.obtain(old).also { cancel ->
                    cancel.action = MotionEvent.ACTION_CANCEL; client?.let { enqueueTouch(it, generation, cancel) } ?: cancel.recycle()
                } }
                gestureActive = false
                lastTouch?.recycle(); lastTouch = null
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            updateCanvasScale()
        }
    }
    private fun updateCanvasScale() {
        if (minOf(width, height, displayWidth, displayHeight) < 1) return
        val fit = EmbeddedCanvasTransform.fit(width, height, displayWidth, displayHeight, contentViewport)
        // TextureView transform acts inside fixed View bounds; View.scale would also
        // transform hit bounds and depend on unclipped AndroidView ancestor layers.
        scaleX = 1f; scaleY = 1f
        setTransform(Matrix().apply {
            setScale(fit.scaleX, fit.scaleY, width / 2f, height / 2f)
            postTranslate(fit.translationX, fit.translationY)
        })
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!modeActive || presentation.suspended || !presentation.attached) return false
        if (resizeQueue.inFlight != null) return true
        val remote = client ?: return false
        if (!opened || width < 1 || height < 1) return false
        if (event.pointerCount !in 1..5) {
            cancelApplicationGesture()
            finishContentPan(commit = false)
            return true
        }
        val action = event.actionMasked
        if (action !in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP,
                MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL)) return false
        val fit = EmbeddedCanvasTransform.fit(width, height, displayWidth, displayHeight, contentViewport)
        if (contentPanMode) return handleContentPan(event)
        if (action == MotionEvent.ACTION_POINTER_DOWN &&
            !fit.contains(event.getX(event.actionIndex), event.getY(event.actionIndex), width, height)) {
            // A new finger in letterbox padding is not a remote edge tap/pinch.
            cancelApplicationGesture()
            return true
        }
        if (action == MotionEvent.ACTION_DOWN) {
            if (!fit.contains(event.x, event.y, width, height)) return false
            hostInput(false)
            gestureDownTime = event.downTime
            gestureActive = true
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        if (!gestureActive) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) parent?.requestDisallowInterceptTouchEvent(false)
            return true
        }
        val properties = Array(event.pointerCount) { MotionEvent.PointerProperties().also { p -> event.getPointerProperties(it, p) } }
        val coords = Array(event.pointerCount) { index -> MotionEvent.PointerCoords().also { p ->
            event.getPointerCoords(index, p)
            p.x = fit.mapHostX(p.x, width, displayWidth)
            p.y = fit.mapHostY(p.y, height, displayHeight)
        } }
        val copy = MotionEvent.obtain(gestureDownTime, event.eventTime, event.action, event.pointerCount,
            properties, coords, event.metaState, event.buttonState, 1f, 1f, event.deviceId, event.edgeFlags,
            InputDevice.SOURCE_TOUCHSCREEN, event.flags)
        val token = generation
        lastTouch?.recycle(); lastTouch = MotionEvent.obtain(copy)
        enqueueTouch(remote, token, copy)
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            gestureActive = false; lastTouch?.recycle(); lastTouch = null
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }
    private fun enqueueTouch(remote: ChromeBridge.Client, token: Long, copy: MotionEvent) {
        val revision = presentation.revision
        if (copy.actionMasked == MotionEvent.ACTION_MOVE && queuedTouch.get() >= 64) copy.recycle()
        else {
            queuedTouch.incrementAndGet()
            executor().execute {
                try {
                    if (!leaseValid(token)) fail(token, SecurityException("授权已失效"))
                    else if ((copy.actionMasked == MotionEvent.ACTION_CANCEL && injectedGesture) ||
                        (copy.actionMasked != MotionEvent.ACTION_CANCEL && !presentation.suspended && revision == presentation.revision)) {
                        remote.touch(copy)
                        if (token == generation) when (copy.actionMasked) {
                            MotionEvent.ACTION_DOWN -> injectedGesture = true
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> injectedGesture = false
                        }
                    }
                } catch (error: Exception) {
                    if (injectedGesture && copy.actionMasked != MotionEvent.ACTION_CANCEL) {
                        MotionEvent.obtain(copy).also { cancel ->
                            cancel.action = MotionEvent.ACTION_CANCEL
                            try { remote.touch(cancel) } catch (_: Exception) { } finally { cancel.recycle() }
                        }
                    }
                    injectedGesture = false
                    ui { if (token == generation) {
                        gestureActive = false; lastTouch?.recycle(); lastTouch = null
                        parent?.requestDisallowInterceptTouchEvent(false)
                    } }
                    operationFailed(token, error)
                }
                finally { copy.recycle(); queuedTouch.decrementAndGet() }
            }
        }
    }
    private fun unbind(mode: Backend?, conn: ServiceConnection?, args: Shizuku.UserServiceArgs?) {
        EmbeddedBackendPool.unbind(mode, conn, args)
    }
    private fun stopInternal() {
        setContentPanMode(false)
        generation++
        main.removeCallbacks(resizeDrain)
        resizeQueue.reset()
        appViewport = null
        projectionHostWidth = 0; projectionHostHeight = 0
        displayWidth = 0; displayHeight = 0; displayDensityDpi = 0
        presentation.closed(); attachingRevision = -1
        injectedGesture = false
        cancelConnectionTimeout()
        main.removeCallbacks(heartbeat)
        val remote = client; client = null
        val retained = backendLease; backendLease = null
        val oldMode = selected; selected = null
        val oldConnection = connection; connection = null
        val oldArgs = shizukuArgs; shizukuArgs = null
        val oldLocalSurface = currentOutputSurface; currentOutputSurface = null
        binderDeath?.let { death -> remote?.let { runCatching { it.binder().unlinkToDeath(death, 0) } } }; binderDeath = null
        permissionListener?.let(Shizuku::removeRequestPermissionResultListener); permissionListener = null
        shizukuDeath?.let(Shizuku::removeBinderDeadListener); shizukuDeath = null
        runCatching { rootRevocation?.close() }; rootRevocation = null; rootEpoch = null
        rootBindTask?.let { RootService.cancelBindTask(it) }; rootBindTask = null
        updateConnectionState(false); opening = false; connecting = false
        externalAuthorization(false)
        hostInput(true)
        gestureActive = false; lastTouch?.recycle(); lastTouch = null
        parent?.requestDisallowInterceptTouchEvent(false)
        val cleanupGeneration = generation
        if (remote != null) executor().execute {
            try {
                runCatching { remote.close() }.onSuccess { result ->
                    if (result.startsWith("closed:")) ui {
                        if (generation == cleanupGeneration && selected == null) updateStatus(result.removePrefix("closed:").trim())
                    }
                }.onFailure { error -> ui {
                    if (generation == cleanupGeneration && selected == null) updateStatus("Chrome 回收未确认：${error.message.orEmpty()}")
                } }
            }
            finally {
                if (oldLocalSurface != null) runCatching { oldLocalSurface.release() }
                main.post { if (retained != null) EmbeddedBackendPool.release(retained) else unbind(oldMode, oldConnection, oldArgs) }
            }
        } else {
            if (oldLocalSurface != null) EmbeddedSerialCleanup.release(io) { runCatching { oldLocalSurface.release() } }
            if (retained != null) EmbeddedBackendPool.release(retained) else unbind(oldMode, oldConnection, oldArgs)
        }
    }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        main.removeCallbacks(heartbeat)
        if (visibility != VISIBLE) loseOutput()
        else { connectIfReady(); openIfReady(); attachOutputIfReady(); fitCanvasToHost(); resumeHeartbeat() }
    }
    override fun onDetachedFromWindow() {
        main.removeCallbacks(heartbeat)
        if (!released) loseOutput()
        super.onDetachedFromWindow()
    }
    companion object {
        private val permissionCodes = AtomicInteger(20400)
    }
}

/** Actual presentation revision state: stale attach acknowledgements cannot revive a minimized view. */
internal class EmbeddedPresentationState {
    var hostVisible = true
    var panelSuspended = false
    val requestedSuspension: Boolean get() = panelSuspended || !hostVisible
    @Volatile var suspended = false
        private set
    @Volatile var revision = 0L
        private set
    var attached = false
        private set
    fun change(suspended: Boolean) { this.suspended = suspended; attached = false; revision++ }
    fun initialOutput() { attached = !suspended }
    fun outputLost() { attached = false; revision++ }
    fun confirmAttached(expected: Long): Boolean {
        if (expected != revision || suspended) return false
        attached = true; return true
    }
    fun closed() { attached = false; revision++ }
    fun disposed() { suspended = false; closed() }
    fun shouldHeartbeat(opened: Boolean, shown: Boolean) = opened && shown && !suspended
    fun keepOnHostLoss(awaitingAuthorization: Boolean) = suspended || awaitingAuthorization
}
