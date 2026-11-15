package com.example.globalminusone.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.LocalFocusManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.globalminusone.MainActivity
import com.example.globalminusone.R
import com.example.globalminusone.WidgetPickerActivity
import com.example.globalminusone.data.GestureAction
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.ShowSideMode
import com.example.globalminusone.data.TriggerConfig
import com.example.globalminusone.data.TriggerPosition
import com.example.globalminusone.data.TriggerSide
import com.example.globalminusone.data.TriggerWay
import com.example.globalminusone.system.SystemController
import com.example.globalminusone.system.AppAndIntentLauncher
import com.example.globalminusone.ui.panel.MinusOneOverlayContainer
import com.example.globalminusone.ui.panel.UserIconCache
import com.example.globalminusone.ui.panel.collectComponentTreeIcons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * 全局负一屏核心前台悬浮窗与手势服务。
 * 1. 双窗口解耦设计：
 *    - 边缘手势条 / 悬浮球：占用极小区域，注册 Android 10+ 200dp 系统手势排除区（SystemGestureExclusionRects）；
 *    - 主面板全屏容器：底层采用透明 ComposeView，硬件加速平移图层；
 * 2. 支持 4 种触发方式（SLIDE、SECOND_SLIDE 二次滑动防误触、SUSPENSION_BALL 悬浮球、NONE）；
 * 3. 完整支持 5 种触发位置（LEFT、RIGHT、BOTH、TOP、BOTTOM）与百分比尺寸偏移；
 * 4. 触发条与悬浮球 7 向手势识别与系统动作绑定（单击/双击/长按/上滑/下滑/左滑/右滑）；
 * 5. 全局护眼滤镜窗口与临时关闭定时恢复。
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var panelWindowManager: WindowManager
    private var accessibilityLayerFailed = false
    private lateinit var repository: MinusOneRepository
    private lateinit var systemController: SystemController
    private lateinit var widgetHostManager: WidgetHostManager
    private lateinit var applicationWindows: EmbeddedApplicationWindowHost
    private lateinit var lifecycleOwner: OverlayComposeLifecycleOwner

    private lateinit var panelComposeView: android.widget.FrameLayout
    private lateinit var panelParams: WindowManager.LayoutParams

    private var leftTriggerView: EdgeTriggerView? = null
    private var rightTriggerView: EdgeTriggerView? = null
    private var topTriggerView: EdgeTriggerView? = null
    private var bottomTriggerView: EdgeTriggerView? = null

    private var suspensionBallView: SuspensionBallView? = null
    private var accessibilityMouse: AccessibilityMouseController? = null
    private var eyeProtectionView: View? = null
    private var regionScreenshotOverlay: RegionScreenshotOverlay? = null
    private var regionCaptureJob: Job? = null
    private var regionCaptureInProgress = false

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val openProgressState = mutableFloatStateOf(0f)
    private val activeSideState = mutableStateOf(TriggerSide.LEFT)

    private var panelAnimator: ValueAnimator? = null
    private val windowCoordination = OverlayWindowCoordination()
    private var initialized = false
    private var foregroundReady = false
    private var panelRegistered = false
    private var tempDisableJob: Job? = null
    private var packageReceiverRegistered = false
    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val pkg = intent?.data?.schemeSpecificPart?.trim().orEmpty()
            when (intent?.action) {
                Intent.ACTION_PACKAGE_ADDED,
                Intent.ACTION_PACKAGE_REMOVED,
                Intent.ACTION_PACKAGE_REPLACED -> AppAndIntentLauncher.onPackageChanged(pkg)
            }
        }
    }
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "OverlayService onCreate")

        createNotificationChannel()
        // A cold start is promoted immediately; warm starts are promoted again below.
        try {
            assertForeground()
        } catch (failure: Exception) {
            Log.e(TAG, "Cannot promote overlay service during creation", failure)
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        repository = MinusOneRepository.getInstance(this)
        repository.setOverlayServiceRunning(false)
    }

    private fun assertForeground() {
        foregroundReady = false
        val notification = buildNotification()
        val fgsType = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
        foregroundReady = true
    }

    private fun initializeWindows() {
        if (initialized) return
        if (!::systemController.isInitialized) systemController = SystemController(this)
        if (accessibilityMouse == null) accessibilityMouse = AccessibilityMouseController(
            this, windowManager, ::getScreenSize,
            config = { repository.state.value.suspensionBallConfig },
            onModeChanged = { active -> suspensionBallView?.onMouseModeChanged(active) },
            onBallRestoreFailure = { recreateSuspensionBallAfterMouseFailure() }
        )
        if (!::widgetHostManager.isInitialized) {
            widgetHostManager = WidgetHostManager.getInstance(this)
            widgetHostManager.startListening()
        }

        if (!::applicationWindows.isInitialized) applicationWindows = EmbeddedApplicationWindowHost(this, repository, serviceScope) { pending ->
            setExternalAuthorizationPending(windowCoordination.requestAuthorization(
                OverlayWindowCoordination.AuthorizationSource.APPLICATION_WINDOWS, pending
            ))
        }
        systemController.activeEmbeddedWindowBackHandler = {
            applicationWindows.performBackOnActiveWindow()
        }
        if (regionScreenshotOverlay == null) regionScreenshotOverlay = RegionScreenshotOverlay(this, windowManager, serviceScope)
        if (!::panelComposeView.isInitialized) setupPanelOverlayWindow() else recoverPanelWindow()
        if (!packageReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
            packageReceiverRegistered = runCatching {
                ContextCompat.registerReceiver(this, packageChangeReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
                true
            }.getOrDefault(false)
        }
        // Mark once before starting collectors, which can immediately emit current state.
        initialized = true
        serviceScope.launch {
            systemController.externalAuthorizationPending.collect { pending ->
                setExternalAuthorizationPending(windowCoordination.requestAuthorization(
                    OverlayWindowCoordination.AuthorizationSource.SYSTEM_CONTROLS, pending
                ))
            }
        }
        serviceScope.launch {
            GlobalAccessibilityService.connectedState.collect { connected ->
                if (!connected) accessibilityMouse?.exit()
                accessibilityLayerFailed = false
                if (initialized && ::panelComposeView.isInitialized && repository.state.value.advancedConfig.highestOverlayLevel) {
                    recoverPanelWindow()
                }
            }
        }
        observeRepository()
    }

    private fun windowsReady(): Boolean {
        if (!initialized || !foregroundReady || windowCoordination.destroying || !panelRegistered) return false
        val config = repository.state.value.triggerConfig
        if (!config.serviceEnabled || !Settings.canDrawOverlays(this)) return false
        val triggerReady = when (config.triggerWay) {
            TriggerWay.NONE -> true
            TriggerWay.SUSPENSION_BALL -> suspensionBallView != null
            else -> when (config.triggerPosition) {
                TriggerPosition.LEFT -> leftTriggerView != null
                TriggerPosition.RIGHT -> rightTriggerView != null
                TriggerPosition.TOP -> topTriggerView != null
                TriggerPosition.BOTTOM -> bottomTriggerView != null
                TriggerPosition.BOTH -> leftTriggerView != null && rightTriggerView != null
            }
        }
        return triggerReady && (!repository.state.value.advancedConfig.eyeProtectionEnabled || eyeProtectionView != null)
    }

    private fun publishRunningState() {
        repository.setOverlayServiceRunning(windowsReady())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val accepted = handleOverlayStart(
            startId = startId,
            shouldRun = intent?.action != ACTION_STOP &&
                repository.state.value.triggerConfig.serviceEnabled && Settings.canDrawOverlays(this),
            assertForeground = ::assertForeground,
            prepareWindows = {
                val wasInitialized = initialized
                initializeWindows()
                if (wasInitialized) {
                    // Reconcile real WM registration on every explicit/warm/sticky start.
                    recoverPanelWindow()
                }
                updateTriggerViews()
                updateSuspensionBallView()
                updateEyeProtectionWindow()
                windowsReady()
            },
            publishRunning = repository::setOverlayServiceRunning,
            stopById = ::stopSelfResult,
            onFailure = { failure -> Log.e(TAG, "Cannot start overlay windows", failure) }
        )
        if (!accepted) return START_NOT_STICKY
        when (intent?.action) {
            ACTION_OPEN_PANEL -> {
                animateOpenPanel()
            }
            ACTION_CLOSE_PANEL -> {
                animateClosePanel()
            }
            ACTION_TOGGLE_PANEL -> {
                if (openProgressState.floatValue > 0.3f) {
                    animateClosePanel()
                } else {
                    animateOpenPanel()
                }
            }
            ACTION_ENTER_EDIT_MODE -> {
                if (repository.setEditMode(true)) animateOpenPanel()
            }
            ACTION_REGION_SCREENSHOT -> {
                startRegionScreenshot()
            }
            ACTION_START -> {
                // 启动并保持运行
            }
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!initialized || windowCoordination.destroying) return
        accessibilityMouse?.exit()
        regionCaptureJob?.cancel()
        regionScreenshotOverlay?.dismiss()
        val isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        val isPanelOpenOrOpening = openProgressState.floatValue > 0f ||
            (::panelComposeView.isInitialized && panelComposeView.visibility == View.VISIBLE)
        if (OverlayVisibilityPolicy.shouldAutoClosePanelInLandscape(repository.state.value, isLandscape, isPanelOpenOrOpening) && !windowCoordination.panelClosing) {
            animateClosePanel()
        }
        if (::applicationWindows.isInitialized) applicationWindows.onConfigurationChanged()
        updateTriggerViews()
        updateSuspensionBallView()
        publishRunningState()
    }

    override fun onDestroy() {
        windowCoordination.beginDestroying()
        foregroundReady = false
        accessibilityMouse?.exit()
        Log.d(TAG, "OverlayService onDestroy")
        repository.setOverlayServiceRunning(false)
        repository.setPanelExpanded(false)

        cancelPanelAnimator()
        tempDisableJob?.cancel()
        if (packageReceiverRegistered) {
            packageReceiverRegistered = false
            runCatching { unregisterReceiver(packageChangeReceiver) }
        }
        regionCaptureJob?.cancel()
        regionScreenshotOverlay?.dismiss()
        regionScreenshotOverlay = null
        if (::systemController.isInitialized) {
            systemController.activeEmbeddedWindowBackHandler = null
        }
        if (::applicationWindows.isInitialized) applicationWindows.destroy()

        leftTriggerView = removeEdgeTrigger(leftTriggerView)
        rightTriggerView = removeEdgeTrigger(rightTriggerView)
        topTriggerView = removeEdgeTrigger(topTriggerView)
        bottomTriggerView = removeEdgeTrigger(bottomTriggerView)

        suspensionBallView?.let {
            it.cleanup()
            try { windowManager.removeView(it) } catch (_: Exception) {}
            suspensionBallView = null
        }

        eyeProtectionView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            eyeProtectionView = null
        }

        if (::panelComposeView.isInitialized) {
            try { panelWindowManager.removeView(panelComposeView) } catch (_: Exception) {}
        }

        if (::lifecycleOwner.isInitialized) {
            lifecycleOwner.onPause()
            lifecycleOwner.onStop()
            lifecycleOwner.onDestroy()
        }

        if (::widgetHostManager.isInitialized) {
            widgetHostManager.stopListening()
        }

        if (::systemController.isInitialized) {
            systemController.release()
        }

        serviceScope.cancel()
        super.onDestroy()
    }

    // --- 状态与前台包名监听 ---

    private fun observeRepository() {
        serviceScope.launch {
            var previous: com.example.globalminusone.data.GlobalMinusOneState? = null
            var retainedWidgetIds = emptySet<Int>()
            repository.state.collect { current ->
                val currentRetainedWidgetIds = repository.retainedWidgetIdsForHost()
                val removedWidgetIds = retainedWidgetIds - currentRetainedWidgetIds
                removedWidgetIds.forEach(widgetHostManager::deleteWidgetId)
                retainedWidgetIds = currentRetainedWidgetIds
                val changes = overlayUpdates(previous, current)
                if (previous?.advancedConfig?.highestOverlayLevel != current.advancedConfig.highestOverlayLevel) {
                    accessibilityLayerFailed = false
                    if (::panelComposeView.isInitialized) recoverPanelWindow()
                }
                if (changes.triggers) { updateTriggerViews(); checkTempDisabled() }
                if (changes.ball) updateSuspensionBallView()
                if (changes.eye) updateEyeProtectionWindow()
                if (changes.blur) applyBlurSettings()
                if (changes.visibility) updateTriggerViewsVisibility()
                val prev = previous
                if (prev == null || prev.pages != current.pages ||
                    prev.appAppearanceOverrides != current.appAppearanceOverrides
                ) {
                    val snapshotPages = current.pages
                    val snapshotOverrides = current.appAppearanceOverrides
                    val snapshotRevisions = repository.imageAccessRevisions.value
                    serviceScope.launch(Dispatchers.IO) {
                        val packages = linkedSetOf<String>()
                        val uris = linkedSetOf<String>()
                        snapshotPages.forEach { page ->
                            collectComponentTreeIcons(page.components, packages, uris)
                        }
                        snapshotOverrides.values.forEach { override ->
                            override.iconUri.trim().takeIf { it.isNotEmpty() }?.let { uris.add(it) }
                        }
                        AppAndIntentLauncher.syncPinnedPackages(packages)
                        coroutineScope {
                            if (uris.isNotEmpty()) launch { UserIconCache.prewarm(applicationContext, uris, snapshotRevisions) }
                            if (packages.isNotEmpty()) launch { AppAndIntentLauncher.prewarmPackages(applicationContext, packages) }
                        }
                    }
                }
                publishRunningState()
                previous = current
            }
        }
        serviceScope.launch {
            repository.currentForegroundPackage.collect {
                updateTriggerViewsVisibility()
            }
        }
    }

    private fun checkTempDisabled() {
        val config = repository.state.value.triggerConfig
        val now = System.currentTimeMillis()
        if (config.isTempDisabled(now)) {
            val remainingMs = config.tempDisabledUntilMs - now
            tempDisableJob?.cancel()
            tempDisableJob = serviceScope.launch {
                delay(remainingMs)
                updateTriggerViewsVisibility()
            }
        }
    }

    private fun configureFullscreenCutout(params: WindowManager.LayoutParams) {
        if (Build.VERSION.SDK_INT >= 30) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            params.fitInsetsTypes = 0
        } else if (Build.VERSION.SDK_INT >= 28) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    // --- 全局护眼滤镜窗口 ---

    private fun updateEyeProtectionWindow() {
        val advanced = repository.state.value.advancedConfig
        if (advanced.eyeProtectionEnabled) {
            val color = advanced.eyeProtectionColorHex.toInt()
            val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    configureFullscreenCutout(this)
                }
            eyeProtectionView = ensureOverlayWindowRegistered(
                existing = eyeProtectionView,
                create = { View(this).apply {
                    setBackgroundColor(color)
                    visibility = if (windowCoordination.authorizationPending) View.GONE else View.VISIBLE
                } },
                update = { windowManager.updateViewLayout(it, params) },
                remove = { windowManager.removeViewImmediate(it) },
                add = { windowManager.addView(it, params) },
                onFailure = { Log.e(TAG, "Cannot register eye protection window", it) }
            )
            eyeProtectionView?.let {
                it.setBackgroundColor(color)
                it.visibility = if (windowCoordination.authorizationPending) View.GONE else View.VISIBLE
            }
        } else {
            eyeProtectionView?.let {
                try {
                    windowManager.removeView(it)
                } catch (_: Exception) {}
                eyeProtectionView = null
            }
        }
    }

    // --- 全屏负一屏面板宿主窗口 ---

    private fun setupPanelOverlayWindow() {
        panelWindowManager = windowManager
        panelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            configureFullscreenCutout(this)
            @Suppress("DEPRECATION")
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        applyBlurSettings()

        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
        var clearHostInputFocus: (() -> Unit)? = null

        panelComposeView = object : android.widget.FrameLayout(this) {
            private var imeBackDownTime: Long? = null

            private fun handleHostImeBack(event: KeyEvent): Boolean {
                if (event.keyCode != KeyEvent.KEYCODE_BACK) return false
                if (windowCoordination.remoteOwnsInput || windowCoordination.authorizationPending) {
                    imeBackDownTime = null
                    return false
                }
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> {
                        if (event.repeatCount == 0) {
                            // Remember the IME before it can hide during pre-IME dispatch.
                            // Keep focus until UP, so that UP cannot reach the underlying app.
                            imeBackDownTime = if (ViewCompat.getRootWindowInsets(this)
                                    ?.isVisible(WindowInsetsCompat.Type.ime()) == true) {
                                event.downTime
                            } else null
                        }
                        return imeBackDownTime == event.downTime
                    }
                    KeyEvent.ACTION_UP -> {
                        val fromIme = imeBackDownTime == event.downTime
                        imeBackDownTime = null
                        if (!fromIme) return false
                        if (!event.isCanceled) {
                            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                                .hideSoftInputFromWindow(windowToken, 0)
                            clearHostInputFocus?.invoke()
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
                    if (com.example.globalminusone.ui.panel.PanelModalBackHandler.handleBack()) return true
                    if (com.example.globalminusone.ui.panel.PanelBrowserBackHandler.handleBack()) return true
                    if (com.example.globalminusone.system.PanelFileBrowser.handleBack()) return true
                    if (com.example.globalminusone.ui.panel.PanelEditorBackHandler.handleBack()) return true
                    animateClosePanel()
                    return true
                }
                return dispatchWithCallFallbackGuard(Build.VERSION.SDK_INT, event.keyCode, event.action) {
                    super.dispatchKeyEvent(event)
                }
            }
        }.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            visibility = View.GONE
            addView(
                composeView,
                android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }

        lifecycleOwner = OverlayComposeLifecycleOwner()
        lifecycleOwner.onCreate()
        lifecycleOwner.attachToView(panelComposeView)
        lifecycleOwner.attachToView(composeView)
        lifecycleOwner.onStart()
        lifecycleOwner.onResume()

        composeView.setContent {
                val focusManager = LocalFocusManager.current
                DisposableEffect(focusManager) {
                    clearHostInputFocus = { focusManager.clearFocus(force = true) }
                    onDispose { clearHostInputFocus = null }
                }
                MinusOneOverlayContainer(
                    repository = repository,
                    systemController = systemController,
                    widgetHostManager = widgetHostManager,
                    openProgressProvider = { openProgressState.floatValue },
                    activeSide = activeSideState.value,
                    onOpenEmbeddedApplication = { target ->
                        applicationWindows.open(target)
                        animateClosePanel()
                    },
                    onBrowserHostInputAllowed = { allowed -> setBrowserInputOwner(!allowed) },
                    onFloatingWorkspaceInputMode = { active -> setFloatingWorkspaceInputMode(active) },
                    onRequestClose = { animateClosePanel() },
                    onOpenMainApp = {
                        animateClosePanel()
                        openMainApp()
                    },
                    onRequestBindWidget = { pageId, containerId, beforeId ->
                        animateClosePanel()
                        openWidgetPicker(pageId, containerId, beforeId)
                    }
                )
        }

        recoverPanelWindow()
    }

    private fun recoverPanelWindow() {
        val accessibility = GlobalAccessibilityService.instance
        val useAccessibilityLayer = repository.state.value.advancedConfig.highestOverlayLevel &&
            accessibility != null && !accessibilityLayerFailed
        val desiredType = if (useAccessibilityLayer) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val desiredManager = if (useAccessibilityLayer) {
            accessibility?.getSystemService(WINDOW_SERVICE) as? WindowManager ?: windowManager
        } else windowManager
        if (panelRegistered && panelParams.type != desiredType) {
            val removal = runCatching { panelWindowManager.removeViewImmediate(panelComposeView) }
            if (removal.exceptionOrNull()?.let { it !is IllegalArgumentException } == true) {
                Log.e(TAG, "Cannot migrate panel overlay", removal.exceptionOrNull())
                return
            }
            panelRegistered = false
        }
        panelParams.type = desiredType
        panelWindowManager = desiredManager
        panelRegistered = ensureOverlayWindowRegistered(
            existing = if (panelRegistered) panelComposeView else null,
            create = { panelComposeView },
            update = { panelWindowManager.updateViewLayout(it, panelParams) },
            remove = { panelWindowManager.removeViewImmediate(it) },
            add = { panelWindowManager.addView(it, panelParams) },
            onFailure = { Log.e(TAG, "Cannot register panel overlay window", it) }
        ) != null
        if (panelRegistered || !useAccessibilityLayer) return
        accessibilityLayerFailed = true
        panelParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        panelWindowManager = windowManager
        panelRegistered = ensureOverlayWindowRegistered(
            existing = null,
            create = { panelComposeView },
            update = { panelWindowManager.updateViewLayout(it, panelParams) },
            remove = { panelWindowManager.removeViewImmediate(it) },
            add = { panelWindowManager.addView(it, panelParams) },
            onFailure = { Log.e(TAG, "Cannot restore ordinary panel overlay", it) }
        ) != null
    }

    private fun setFloatingWorkspaceInputMode(active: Boolean) {
        // API 30+ supplies IME insets even with ADJUST_NOTHING. Floating windows
        // already avoid those insets themselves; platform resize/pan would move
        // the shared host a second time and can leave its title above the screen.
        // Older releases retain RESIZE so their IME inset compatibility works.
        @Suppress("DEPRECATION")
        val adjustment = if (active && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        } else WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        val previousMode = panelParams.softInputMode
        val mode = replaceWindowInputAdjustment(
            previousMode, WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST, adjustment
        )
        if (previousMode == mode) return
        panelParams.softInputMode = mode
        try {
            if (panelComposeView.isAttachedToWindow) panelWindowManager.updateViewLayout(panelComposeView, panelParams)
        } catch (failure: Exception) {
            panelParams.softInputMode = previousMode
            Log.e(TAG, "Cannot update floating workspace input mode", failure)
        }
    }

    private fun applyBlurSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blurRadius = repository.state.value.themeConfig.backgroundBlurRadius
            if (blurRadius > 0 && !windowCoordination.authorizationPending) {
                panelParams.flags = panelParams.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                panelParams.blurBehindRadius = blurRadius
            } else {
                panelParams.flags = panelParams.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
            }
        }
    }

    private fun cancelPanelAnimator() {
        panelAnimator?.removeAllListeners()
        panelAnimator?.cancel()
        panelAnimator = null
    }

    private fun getScreenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            Pair(bounds.width(), bounds.height())
        } else {
            @Suppress("DEPRECATION")
            val dm = resources.displayMetrics
            Pair(dm.widthPixels, dm.heightPixels)
        }
    }

    fun ensurePanelFocusableForInput() {
        if (windowCoordination.remoteOwnsInput || windowCoordination.authorizationPending) return
        val focusFlags = embeddedHostFocusFlags(panelParams.flags, remoteInput = false)
        if (panelParams.flags != focusFlags) {
            val previousFlags = panelParams.flags
            panelParams.flags = focusFlags
            try {
                panelWindowManager.updateViewLayout(panelComposeView, panelParams)
            } catch (failure: Exception) {
                panelParams.flags = previousFlags
                Log.e(TAG, "Cannot restore browser host input window", failure)
                Toast.makeText(this, "无法激活输入窗口，请关闭浏览窗口后重试", Toast.LENGTH_SHORT).show()
                return
            }
            // Restore window eligibility before a DOWN reaches an editor. Do not
            // steal its Compose/native focus from a later onFocusChanged callback.
            if (!panelComposeView.hasFocus()) panelComposeView.requestFocus()
        }
    }

    private fun setBrowserInputOwner(ownsInput: Boolean) {
        windowCoordination.setRemoteInputOwner(ownsInput)
        if (!ownsInput) {
            if (windowCoordination.canRestoreHostFocus(panelComposeView.visibility == View.VISIBLE, openProgressState.floatValue)) {
                ensurePanelFocusableForInput()
            }
            return
        }
        // A focusable host steals the IME before the forwarded key DOWN reaches
        // Chrome. Keep all window controls touchable, but let Chrome own input.
        val flags = embeddedHostFocusFlags(panelParams.flags, remoteInput = true)
        if (panelParams.flags != flags) {
            val previousFlags = panelParams.flags
            panelParams.flags = flags
            try {
                if (panelComposeView.isAttachedToWindow) panelWindowManager.updateViewLayout(panelComposeView, panelParams)
            } catch (failure: Exception) {
                panelParams.flags = previousFlags
                windowCoordination.setRemoteInputOwner(false)
                Log.e(TAG, "Cannot transfer browser input ownership", failure)
                Toast.makeText(this, "无法切换 Chrome 输入窗口，请断开后重试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setExternalAuthorizationPending(pending: Boolean) {
        if (!windowCoordination.transitionAuthorization(pending, panelParams.alpha)) return
        // Detaching the panel during service teardown stops the Surface and calls
        // back here. Never recreate an auxiliary window during teardown.
        if (windowCoordination.destroying) return
        if (::applicationWindows.isInitialized) applicationWindows.setAuthorizationUiHidden(pending)
        if (pending) {
            // Keep the Surface alive while exposing the external permission UI.
            // INVISIBLE/GONE would destroy it and cancel the pending request.
            panelParams.alpha = 0f
            panelParams.flags = panelParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        } else {
            panelParams.alpha = windowCoordination.authorizationAlpha
            if (windowCoordination.canRestoreHostFocus(panelComposeView.visibility == View.VISIBLE, openProgressState.floatValue)) {
                panelParams.flags = panelParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                if (!windowCoordination.remoteOwnsInput) panelParams.flags = embeddedHostFocusFlags(panelParams.flags, remoteInput = false)
            }
        }
        // Even a small trigger outside the dialog can mark a permission touch as
        // partially obscured. The filter also owns a separate full-screen window.
        // Only the panel must retain its Surface; auxiliary windows may be GONE.
        updateTriggerViewsVisibility()
        updateEyeProtectionWindow()
        applyBlurSettings()
        if (panelComposeView.isAttachedToWindow) panelWindowManager.updateViewLayout(panelComposeView, panelParams)
        if (!pending && !windowCoordination.panelClosing && panelComposeView.visibility == View.VISIBLE) ensurePanelFocusableForInput()
    }

    private fun ensurePanelGoneAndUnfocused() {
        val targetFlags = panelParams.flags or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val needUpdate = panelParams.flags != targetFlags || panelComposeView.visibility != View.GONE
        panelParams.flags = targetFlags
        panelComposeView.visibility = View.GONE
        if (needUpdate) {
            try {
                panelWindowManager.updateViewLayout(panelComposeView, panelParams)
            } catch (_: Exception) {}
        }
    }

    private fun preparePanelForDragging() {
        cancelPanelAnimator()
        windowCoordination.beginOpening()
        if (panelComposeView.visibility != View.VISIBLE) {
            panelComposeView.visibility = View.VISIBLE
        }
        val flags = if (windowCoordination.authorizationPending) panelParams.flags
            else panelParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        if (panelParams.flags != flags) {
            panelParams.flags = flags
            try {
                panelWindowManager.updateViewLayout(panelComposeView, panelParams)
            } catch (_: Exception) {}
        }
    }

    private fun resolveTriggerOpenSide(position: TriggerPosition, action: GestureAction? = null): TriggerSide {
        val showSideMode = repository.state.value.triggerConfig.defaultShowSide
        return when (showSideMode) {
            ShowSideMode.LEFT -> TriggerSide.LEFT
            ShowSideMode.RIGHT -> TriggerSide.RIGHT
            ShowSideMode.FOLLOW_TRIGGER -> {
                when {
                    action == GestureAction.RIGHT_OPEN_NOS -> TriggerSide.RIGHT
                    action == GestureAction.LEFT_OPEN_NOS -> TriggerSide.LEFT
                    position == TriggerPosition.RIGHT -> TriggerSide.RIGHT
                    else -> TriggerSide.LEFT
                }
            }
        }
    }

    fun animateOpenPanel(side: TriggerSide? = null) {
        if (OverlayVisibilityPolicy.isBlacklisted(repository.state.value, repository.currentForegroundPackage.value)) {
            updateTriggerViewsVisibility()
            return
        }
        cancelPanelAnimator()
        val resolvedSide = side ?: resolveTriggerOpenSide(repository.state.value.triggerConfig.triggerPosition)
        activeSideState.value = resolvedSide
        preparePanelForDragging()
        applyBlurSettings()

        val start = openProgressState.floatValue
        val rawDurationMs = repository.state.value.themeConfig.animationDurationMs
        if (rawDurationMs <= 0) {
            openProgressState.floatValue = 1f
            ensurePanelFocusableForInput()
            repository.setPanelExpanded(true)
            updateTriggerViewsVisibility()
            return
        }
        val durationMs = rawDurationMs.toLong().coerceIn(80L, 800L)
        val anim = ValueAnimator.ofFloat(start, 1f).apply {
            duration = ((1f - start) * durationMs.toFloat()).toLong().coerceIn(80L, durationMs)
            interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener {
                val currentProgress = it.animatedValue as Float
                openProgressState.floatValue = currentProgress
                if (currentProgress >= 0.98f &&
                    (panelParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0
                ) {
                    ensurePanelFocusableForInput()
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (cancelled) return
                    openProgressState.floatValue = 1f
                    ensurePanelFocusableForInput()
                    repository.setPanelExpanded(true)
                    updateTriggerViewsVisibility()
                }
            })
        }
        panelAnimator = anim
        anim.start()
    }

    fun animateClosePanel() {
        windowCoordination.beginClosing()
        cancelPanelAnimator()
        panelParams.flags = panelParams.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        try {
            panelWindowManager.updateViewLayout(panelComposeView, panelParams)
        } catch (_: Exception) {}

        val start = openProgressState.floatValue
        if (start <= 0.01f) {
            openProgressState.floatValue = 0f
            ensurePanelGoneAndUnfocused()
            repository.setPanelExpanded(false)
            updateTriggerViewsVisibility()
            return
        }

        val rawDurationMs = repository.state.value.themeConfig.animationDurationMs
        if (rawDurationMs <= 0) {
            openProgressState.floatValue = 0f
            ensurePanelGoneAndUnfocused()
            repository.setPanelExpanded(false)
            updateTriggerViewsVisibility()
            return
        }
        val durationMs = rawDurationMs.toLong().coerceIn(80L, 800L)
        val anim = ValueAnimator.ofFloat(start, 0f).apply {
            duration = (start * (durationMs * 0.85f)).toLong().coerceIn(60L, durationMs)
            interpolator = AccelerateInterpolator(1.6f)
            addUpdateListener {
                val currentProgress = it.animatedValue as Float
                openProgressState.floatValue = currentProgress
                if (currentProgress <= 0.01f) {
                    ensurePanelGoneAndUnfocused()
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (cancelled) return
                    openProgressState.floatValue = 0f
                    ensurePanelGoneAndUnfocused()
                    repository.setPanelExpanded(false)
                    updateTriggerViewsVisibility()
                }
            })
        }
        panelAnimator = anim
        anim.start()
    }

    private fun openMainApp() {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("force_settings", true)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch MainActivity", e)
        }
    }

    private fun startRegionScreenshot() {
        val a11y = GlobalAccessibilityService.instance
        if (a11y == null) {
            animateClosePanel()
            systemController.promptAccessibilityPermission()
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Toast.makeText(this, "区域截屏需要 Android 11 及以上系统无障碍截屏支持", Toast.LENGTH_SHORT).show()
            return
        }
        regionCaptureJob?.cancel()
        regionScreenshotOverlay?.dismiss()
        regionCaptureInProgress = true
        eyeProtectionView?.visibility = View.GONE
        animateClosePanel()
        updateTriggerViewsVisibility()

        regionCaptureJob = serviceScope.launch {
            // Wait until the MinusOne panel closing animation finishes and SurfaceFlinger composites the clean screen.
            var waitedMs = 0L
            while (waitedMs < 460L && ::panelComposeView.isInitialized && panelComposeView.visibility == View.VISIBLE) {
                delay(25L)
                waitedMs += 25L
            }
            delay(65L)

            val serviceNow = GlobalAccessibilityService.instance
            if (serviceNow == null) {
                regionCaptureInProgress = false
                if (repository.state.value.advancedConfig.eyeProtectionEnabled) {
                    eyeProtectionView?.visibility = if (windowCoordination.authorizationPending) View.GONE else View.VISIBLE
                }
                updateTriggerViewsVisibility()
                systemController.promptAccessibilityPermission()
                return@launch
            }

            serviceNow.captureSilentScreenBitmap { bitmap, errorCode ->
                regionCaptureInProgress = false
                if (repository.state.value.advancedConfig.eyeProtectionEnabled) {
                    eyeProtectionView?.visibility = if (windowCoordination.authorizationPending) View.GONE else View.VISIBLE
                }
                updateTriggerViewsVisibility()

                if (windowCoordination.destroying) {
                    bitmap?.recycle()
                    return@captureSilentScreenBitmap
                }
                if (bitmap != null) {
                    regionScreenshotOverlay?.show(bitmap)
                } else {
                    val hint = if (errorCode == android.accessibilityservice.AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS) {
                        "请重新开关一次无障碍服务以启用区域截屏权限"
                    } else {
                        "区域截屏捕获画面失败 ($errorCode)"
                    }
                    Toast.makeText(this@OverlayService, hint, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openWidgetPicker(pageId: String, containerId: String?, beforeId: String?) {
        try {
            val intent = Intent(this, WidgetPickerActivity::class.java).apply {
                putExtra("target_page_id", pageId)
                putExtra("container_id", containerId)
                putExtra("before_component_id", beforeId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch WidgetPickerActivity", e)
        }
    }

    // --- 边缘手势触发条 (EdgeTriggerView) 管理 ---

    private fun getTriggerLayoutParams(
        position: TriggerPosition,
        screenWidth: Int,
        screenHeight: Int,
        config: TriggerConfig,
        callbackInsets: WindowInsetsCompat? = null
    ): WindowManager.LayoutParams {
        val density = resources.displayMetrics.density
        val sidePull = config.triggerWay == TriggerWay.SIDE_PULLDOWN
        val systemGestureInsets = if (sidePull) runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val insets = windowManager.currentWindowMetrics.windowInsets
                    .getInsets(android.view.WindowInsets.Type.systemGestures())
                insets.left to insets.right
            } else {
                val insets = if (::panelComposeView.isInitialized) ViewCompat.getRootWindowInsets(panelComposeView)
                    ?.getInsets(WindowInsetsCompat.Type.systemGestures()) else null
                (insets?.left ?: 0) to (insets?.right ?: 0)
            }
        }.getOrDefault(0 to 0) else (0 to 0)
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        val imeBottomPx = if (config.avoidInputMethod && callbackInsets != null) {
            callbackInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        } else if (config.avoidInputMethod && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                windowManager.currentWindowMetrics.windowInsets
                    .getInsets(android.view.WindowInsets.Type.ime()).bottom
            }.getOrDefault(0)
        } else 0
        val availableVerticalHeight = (screenHeight - imeBottomPx).coerceAtLeast((72 * density).roundToInt())

        return when (position) {
            TriggerPosition.LEFT -> {
                val widthPx = (screenWidth * (config.touchWidthPercent.coerceIn(1, 20) / 100f)).roundToInt()
                    .coerceAtLeast((10 * density).roundToInt())
                val heightPx = (screenHeight * (config.touchHeightPercent.coerceIn(5, 100) / 100f)).roundToInt()
                    .coerceAtLeast((36 * density).roundToInt())
                    .coerceAtMost(availableVerticalHeight)
                val baseY = ((screenHeight - heightPx) * (config.touchOffsetPercent.coerceIn(0, 100) / 100f)).roundToInt()
                val yPos = if (imeBottomPx > 0) baseY.coerceAtMost((availableVerticalHeight - heightPx).coerceAtLeast(0)) else baseY
                WindowManager.LayoutParams(
                    widthPx, heightPx,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    flags, PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.LEFT or Gravity.TOP
                    x = SidePullTriggerPolicy.resolveTriggerBarLeftX(
                        position = TriggerPosition.LEFT,
                        screenWidth = screenWidth,
                        barWidthPx = widthPx,
                        sidePull = sidePull,
                        leftInsetPx = systemGestureInsets.first,
                        rightInsetPx = systemGestureInsets.second,
                        customOffsetDp = config.sidePullHorizontalOffsetDp,
                        density = density
                    )
                    y = yPos
                }
            }
            TriggerPosition.RIGHT -> {
                val widthPx = (screenWidth * (config.touchWidthPercent.coerceIn(1, 20) / 100f)).roundToInt()
                    .coerceAtLeast((10 * density).roundToInt())
                val heightPx = (screenHeight * (config.touchHeightPercent.coerceIn(5, 100) / 100f)).roundToInt()
                    .coerceAtLeast((36 * density).roundToInt())
                    .coerceAtMost(availableVerticalHeight)
                val baseY = ((screenHeight - heightPx) * (config.touchOffsetPercent.coerceIn(0, 100) / 100f)).roundToInt()
                val yPos = if (imeBottomPx > 0) baseY.coerceAtMost((availableVerticalHeight - heightPx).coerceAtLeast(0)) else baseY
                WindowManager.LayoutParams(
                    widthPx, heightPx,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    flags, PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.LEFT or Gravity.TOP
                    x = SidePullTriggerPolicy.resolveTriggerBarLeftX(
                        position = TriggerPosition.RIGHT,
                        screenWidth = screenWidth,
                        barWidthPx = widthPx,
                        sidePull = sidePull,
                        leftInsetPx = systemGestureInsets.first,
                        rightInsetPx = systemGestureInsets.second,
                        customOffsetDp = config.sidePullHorizontalOffsetDp,
                        density = density
                    )
                    y = yPos
                }
            }
            TriggerPosition.TOP -> {
                val widthPx = (screenWidth * (config.touchHeightPercent.coerceIn(5, 100) / 100f)).roundToInt()
                    .coerceAtLeast((36 * density).roundToInt())
                val heightPx = (screenHeight * (config.touchWidthPercent.coerceIn(1, 20) / 100f)).roundToInt()
                    .coerceAtLeast((10 * density).roundToInt())
                val xPos = ((screenWidth - widthPx) * (config.touchOffsetPercent.coerceIn(0, 100) / 100f)).roundToInt()
                WindowManager.LayoutParams(
                    widthPx, heightPx,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    flags, PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.START or Gravity.TOP
                    x = xPos
                    y = 0
                }
            }
            TriggerPosition.BOTTOM -> {
                val widthPx = (screenWidth * (config.touchHeightPercent.coerceIn(5, 100) / 100f)).roundToInt()
                    .coerceAtLeast((36 * density).roundToInt())
                val heightPx = (screenHeight * (config.touchWidthPercent.coerceIn(1, 20) / 100f)).roundToInt()
                    .coerceAtLeast((10 * density).roundToInt())
                val xPos = ((screenWidth - widthPx) * (config.touchOffsetPercent.coerceIn(0, 100) / 100f)).roundToInt()
                WindowManager.LayoutParams(
                    widthPx, heightPx,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    flags, PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.START or Gravity.BOTTOM
                    x = xPos
                    y = 0
                }
            }
            TriggerPosition.BOTH -> {
                throw IllegalArgumentException("BOTH is handled as separate LEFT and RIGHT triggers")
            }
        }
    }

    private fun ensureOrUpdateEdgeTrigger(
        existing: EdgeTriggerView?,
        position: TriggerPosition,
        screenWidth: Int,
        screenHeight: Int,
        config: TriggerConfig
    ): EdgeTriggerView? {
        val params = getTriggerLayoutParams(position, screenWidth, screenHeight, config)
        return ensureOverlayWindowRegistered(
            existing = existing,
            create = { EdgeTriggerView(this, position).apply {
                if (windowCoordination.authorizationPending) visibility = View.GONE
            } },
            update = { windowManager.updateViewLayout(it, params); it.invalidate() },
            remove = { it.updateGestureExclusion(false); windowManager.removeViewImmediate(it) },
            add = { windowManager.addView(it, params) },
            onFailure = { Log.e(TAG, "Cannot register EdgeTriggerView for $position", it) }
        )
    }

    private fun removeEdgeTrigger(view: EdgeTriggerView?): EdgeTriggerView? {
        view?.let {
            it.updateGestureExclusion(false)
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
        }
        return null
    }

    private fun updateTriggerViews() {
        val config = repository.state.value.triggerConfig
        val (screenWidth, screenHeight) = getScreenSize()

        if (config.triggerWay == TriggerWay.NONE || config.triggerWay == TriggerWay.SUSPENSION_BALL || !config.serviceEnabled) {
            leftTriggerView = removeEdgeTrigger(leftTriggerView)
            rightTriggerView = removeEdgeTrigger(rightTriggerView)
            topTriggerView = removeEdgeTrigger(topTriggerView)
            bottomTriggerView = removeEdgeTrigger(bottomTriggerView)
            updateTriggerViewsVisibility()
            return
        }

        // SLIDE 或 SECOND_SLIDE
        when (config.triggerPosition) {
            TriggerPosition.LEFT -> {
                leftTriggerView = ensureOrUpdateEdgeTrigger(leftTriggerView, TriggerPosition.LEFT, screenWidth, screenHeight, config)
                rightTriggerView = removeEdgeTrigger(rightTriggerView)
                topTriggerView = removeEdgeTrigger(topTriggerView)
                bottomTriggerView = removeEdgeTrigger(bottomTriggerView)
            }
            TriggerPosition.RIGHT -> {
                rightTriggerView = ensureOrUpdateEdgeTrigger(rightTriggerView, TriggerPosition.RIGHT, screenWidth, screenHeight, config)
                leftTriggerView = removeEdgeTrigger(leftTriggerView)
                topTriggerView = removeEdgeTrigger(topTriggerView)
                bottomTriggerView = removeEdgeTrigger(bottomTriggerView)
            }
            TriggerPosition.BOTH -> {
                leftTriggerView = ensureOrUpdateEdgeTrigger(leftTriggerView, TriggerPosition.LEFT, screenWidth, screenHeight, config)
                rightTriggerView = ensureOrUpdateEdgeTrigger(rightTriggerView, TriggerPosition.RIGHT, screenWidth, screenHeight, config)
                topTriggerView = removeEdgeTrigger(topTriggerView)
                bottomTriggerView = removeEdgeTrigger(bottomTriggerView)
            }
            TriggerPosition.TOP -> {
                topTriggerView = ensureOrUpdateEdgeTrigger(topTriggerView, TriggerPosition.TOP, screenWidth, screenHeight, config)
                leftTriggerView = removeEdgeTrigger(leftTriggerView)
                rightTriggerView = removeEdgeTrigger(rightTriggerView)
                bottomTriggerView = removeEdgeTrigger(bottomTriggerView)
            }
            TriggerPosition.BOTTOM -> {
                bottomTriggerView = ensureOrUpdateEdgeTrigger(bottomTriggerView, TriggerPosition.BOTTOM, screenWidth, screenHeight, config)
                leftTriggerView = removeEdgeTrigger(leftTriggerView)
                rightTriggerView = removeEdgeTrigger(rightTriggerView)
                topTriggerView = removeEdgeTrigger(topTriggerView)
            }
        }

        updateTriggerViewsVisibility()
    }

    // --- 悬浮球视图 (SuspensionBallView) 管理 ---

    private fun recreateSuspensionBallAfterMouseFailure() {
        if (windowCoordination.destroying) return
        val previous = suspensionBallView ?: return
        suspensionBallView = null
        previous.cleanup()
        runCatching { windowManager.removeViewImmediate(previous) }
        updateSuspensionBallView()
        publishRunningState()
    }

    private fun updateSuspensionBallView() {
        val config = repository.state.value.triggerConfig
        if (config.triggerWay != TriggerWay.SUSPENSION_BALL || !config.serviceEnabled) {
            suspensionBallView?.let {
                it.cleanup()
                try {
                    windowManager.removeView(it)
                } catch (_: Exception) {}
                suspensionBallView = null
            }
            return
        }

        val sbConfig = repository.state.value.suspensionBallConfig
        if (!sbConfig.mouseMode) accessibilityMouse?.exit()
        val (screenWidth, screenHeight) = getScreenSize()
        val density = resources.displayMetrics.density
        val sizePx = (sbConfig.radiusDp * 2 * density).roundToInt()

        val posX = ((screenWidth - sizePx) * sbConfig.posXPercent).roundToInt().coerceIn(0, (screenWidth - sizePx).coerceAtLeast(0))
        val posY = ((screenHeight - sizePx) * sbConfig.posYPercent).roundToInt().coerceIn(0, (screenHeight - sizePx).coerceAtLeast(0))

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        val params = WindowManager.LayoutParams(
            sizePx, sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            x = posX
            y = posY
        }

        accessibilityMouse?.preserveClickFlags(params)
        val previous = suspensionBallView
        suspensionBallView = ensureOverlayWindowRegistered(
            existing = previous,
            create = { SuspensionBallView(this).apply { visibility = View.GONE } },
            update = { windowManager.updateViewLayout(it, params) },
            remove = { it.cleanup(); windowManager.removeViewImmediate(it) },
            add = { windowManager.addView(it, params) },
            onFailure = { Log.e(TAG, "Cannot register suspension ball window", it) }
        )
        suspensionBallView?.let {
            it.updateBreatheMode(sbConfig.breatheMode)
            it.invalidate()
            if (it !== previous) it.scheduleIdleFade()
        }
        updateTriggerViewsVisibility()
    }

    private fun updateTriggerViewsVisibility() {
        val state = repository.state.value
        val config = state.triggerConfig
        val currentPkg = repository.currentForegroundPackage.value
        val isBlacklisted = OverlayVisibilityPolicy.isBlacklisted(state, currentPkg)
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val isExpanded = openProgressState.floatValue > 0f
        val isPanelOpenOrOpening = isExpanded || (::panelComposeView.isInitialized && panelComposeView.visibility == View.VISIBLE)
        if ((isBlacklisted || OverlayVisibilityPolicy.shouldAutoClosePanelInLandscape(state, isLandscape, isPanelOpenOrOpening)) &&
            ::panelComposeView.isInitialized && panelComposeView.visibility == View.VISIBLE && !windowCoordination.panelClosing) {
            animateClosePanel()
        }
        val shouldShow = !windowCoordination.authorizationPending &&
                !regionCaptureInProgress &&
                OverlayVisibilityPolicy.shouldShowTrigger(state, currentPkg, isLandscape, isExpanded)

        val sidePull = config.triggerWay == TriggerWay.SIDE_PULLDOWN
        val isEdgeBar = config.triggerWay == TriggerWay.SLIDE || config.triggerWay == TriggerWay.SECOND_SLIDE || sidePull
        applyTriggerVisibility(leftTriggerView, shouldShow && isEdgeBar && (config.triggerPosition == TriggerPosition.LEFT || config.triggerPosition == TriggerPosition.BOTH))
        applyTriggerVisibility(rightTriggerView, shouldShow && isEdgeBar && (config.triggerPosition == TriggerPosition.RIGHT || config.triggerPosition == TriggerPosition.BOTH))
        applyTriggerVisibility(topTriggerView, shouldShow && isEdgeBar && !sidePull && config.triggerPosition == TriggerPosition.TOP)
        applyTriggerVisibility(bottomTriggerView, shouldShow && isEdgeBar && !sidePull && config.triggerPosition == TriggerPosition.BOTTOM)
        applyTriggerVisibility(suspensionBallView, shouldShow && config.triggerWay == TriggerWay.SUSPENSION_BALL)
    }

    private fun applyTriggerVisibility(view: View?, shouldShow: Boolean) {
        if (view == null) return
        if (shouldShow) {
            view.visibility = if (accessibilityMouse?.keepBallHiddenForClick(view) == true) View.INVISIBLE else View.VISIBLE
            (view as? EdgeTriggerView)?.updateGestureExclusion(true)
        } else {
            if (view is SuspensionBallView) accessibilityMouse?.exit()
            view.visibility = View.GONE
            (view as? EdgeTriggerView)?.updateGestureExclusion(false)
        }
    }

    private enum class GestureMode {
        IDLE,
        SECOND_SLIDE_PRIMING,
        CLICK_WAITING,
        FOLLOW_PANEL_DRAG,
        SWIPE_ACTION_PENDING,
        SWIPE_ACTION_TRIGGERED,
        IGNORED
    }



    /**
     * 自定义边缘手势触发条 View
     */
    inner class EdgeTriggerView(
        context: Context,
        val position: TriggerPosition
    ) : View(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var downRawX = 0f
        private var downRawY = 0f
        private var isTouched = false
        private var hasVibrated = false
        private var hasMoved = false
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

        private var gestureMode = GestureMode.IDLE
        private var pendingSwipeAction = GestureAction.NONE
        private var followDirection = TriggerSwipeDirection.RIGHT

        // 二次滑动防误触状态
        private var lastFirstTouchTimeMs = 0L
        private var firstTouchX = 0f
        private var firstTouchY = 0f
        private var isSecondSlideHighlighted = false
        private var secondSlideResetRunnable: Runnable? = null

        private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (hasMoved || (gestureMode != GestureMode.IDLE && gestureMode != GestureMode.CLICK_WAITING)) return false
                val config = repository.state.value.triggerConfig
                if (config.triggerWay == TriggerWay.SIDE_PULLDOWN) return false
                val act = config.singleTapAction
                if (act != GestureAction.NONE) {
                    if (config.vibrate) systemController.performTickHaptic()
                    systemController.executeGestureAction(act) { side -> animateOpenPanel(side) }
                    return true
                }
                return false
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (hasMoved || (gestureMode != GestureMode.IDLE && gestureMode != GestureMode.CLICK_WAITING)) return false
                val config = repository.state.value.triggerConfig
                if (config.triggerWay == TriggerWay.SIDE_PULLDOWN) return false
                val act = config.doubleTapAction
                if (act != GestureAction.NONE) {
                    if (config.vibrate) systemController.performTickHaptic()
                    systemController.executeGestureAction(act) { side -> animateOpenPanel(side) }
                    return true
                }
                return false
            }

            override fun onLongPress(e: MotionEvent) {
                if (hasMoved || (gestureMode != GestureMode.CLICK_WAITING && gestureMode != GestureMode.IDLE)) return
                val config = repository.state.value.triggerConfig
                if (config.triggerWay == TriggerWay.SIDE_PULLDOWN) return
                val act = config.longPressAction
                if (act != GestureAction.NONE) {
                    if (config.vibrate) systemController.performTickHaptic()
                    systemController.executeGestureAction(act) { side -> animateOpenPanel(side) }
                }
            }
        })

        fun updateGestureExclusion(enabled: Boolean) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val exclusion = triggerEdgeExclusions(width, height, resources.displayMetrics.density,
                    enabled && visibility == View.VISIBLE && repository.state.value.triggerConfig.triggerWay != TriggerWay.SIDE_PULLDOWN,
                    position == TriggerPosition.LEFT || position == TriggerPosition.RIGHT)
                    .map { Rect(it.left, it.top, it.right, it.bottom) }
                ViewCompat.setSystemGestureExclusionRects(this, exclusion)
            }
        }

        override fun onApplyWindowInsets(insets: android.view.WindowInsets): android.view.WindowInsets {
            val applied = super.onApplyWindowInsets(insets)
            val config = repository.state.value.triggerConfig
            val needsInsetUpdate = (config.triggerWay == TriggerWay.SIDE_PULLDOWN && SidePullTriggerPolicy.supports(position)) ||
                (config.avoidInputMethod && (position == TriggerPosition.LEFT || position == TriggerPosition.RIGHT))
            if (needsInsetUpdate) {
                val (screenWidth, screenHeight) = getScreenSize()
                val compatInsets = WindowInsetsCompat.toWindowInsetsCompat(insets, this)
                val target = getTriggerLayoutParams(position, screenWidth, screenHeight, config, compatInsets)
                val currentLp = layoutParams as? WindowManager.LayoutParams
                if (currentLp?.x != target.x || currentLp.y != target.y || currentLp.height != target.height) post {
                    val latest = repository.state.value.triggerConfig
                    if (isAttachedToWindow) {
                        val (width, height) = getScreenSize()
                        runCatching { windowManager.updateViewLayout(this, getTriggerLayoutParams(position, width, height, latest, compatInsets)) }
                    }
                }
            }
            return applied
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            super.onLayout(changed, left, top, right, bottom)
            if (visibility == View.VISIBLE) {
                updateGestureExclusion(true)
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val config = repository.state.value.triggerConfig
            val colorInt = config.touchColorHex.toInt()

            val baseAlpha = if (config.touchVisible) config.touchAlpha else 0f
            val effectiveAlpha = when {
                isSecondSlideHighlighted -> 1.0f
                isTouched -> if (config.touchVisible) config.touchAlpha else 0.45f
                else -> baseAlpha
            }

            if (effectiveAlpha <= 0.001f) return

            val alphaInt = (effectiveAlpha * 255).roundToInt().coerceIn(0, 255)
            paint.color = colorInt
            paint.alpha = alphaInt

            val w = width.toFloat()
            val h = height.toFloat()
            val radius = minOf(w, h) / 2f
            canvas.drawRoundRect(0f, 0f, w, h, radius, radius, paint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val config = repository.state.value.triggerConfig
            val density = resources.displayMetrics.density
            val sidePull = config.triggerWay == TriggerWay.SIDE_PULLDOWN

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val now = android.os.SystemClock.elapsedRealtime()

                    // 二次滑动防误触判定 (BUG-017: 支持自定义二次滑出时间窗口)
                    if (config.triggerWay == TriggerWay.SECOND_SLIDE) {
                        val timeoutMs = config.secondSlideTimeoutMs.coerceIn(400, 3000).toLong()
                        if (lastFirstTouchTimeMs == 0L || now - lastFirstTouchTimeMs !in 0..timeoutMs ||
                            hypot(event.rawX - firstTouchX, event.rawY - firstTouchY) > 48f * density) {
                            // 第一次触摸：仅临时高亮 timeoutMs，不执行手势
                            lastFirstTouchTimeMs = now
                            firstTouchX = event.rawX
                            firstTouchY = event.rawY
                            isSecondSlideHighlighted = true
                            invalidate()
                            if (config.vibrate) {
                                systemController.performTickHaptic(config.useSystemVibrate)
                            }
                            secondSlideResetRunnable?.let { removeCallbacks(it) }
                            val resetRunnable = Runnable {
                                isSecondSlideHighlighted = false
                                invalidate()
                            }
                            secondSlideResetRunnable = resetRunnable
                            postDelayed(resetRunnable, timeoutMs)

                            gestureMode = GestureMode.SECOND_SLIDE_PRIMING
                            return true
                        } else {
                            // timeoutMs 内的第二次触摸：正常执行
                            lastFirstTouchTimeMs = 0L
                            isSecondSlideHighlighted = false
                            secondSlideResetRunnable?.let { removeCallbacks(it) }
                            invalidate()
                        }
                    }

                    downRawX = event.rawX
                    downRawY = event.rawY
                    isTouched = true
                    hasVibrated = false
                    hasMoved = false
                    gestureMode = GestureMode.CLICK_WAITING
                    pendingSwipeAction = GestureAction.NONE

                    if (!sidePull) gestureDetector.onTouchEvent(event)
                    invalidate()
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (gestureMode == GestureMode.SECOND_SLIDE_PRIMING) {
                        return true
                    }

                    if (sidePull) {
                        if (gestureMode == GestureMode.IGNORED) return true
                        val dx = event.rawX - downRawX
                        val dy = event.rawY - downRawY
                        val recognition = SidePullTriggerPolicy.sidePullRecognition(dx, dy, touchSlop.toFloat(), config.minimumSwipeDistanceDp, density)
                        if (!SidePullTriggerPolicy.supports(position) || recognition == SidePullRecognition.CANCEL) {
                            if (gestureMode == GestureMode.FOLLOW_PANEL_DRAG) animateClosePanel()
                            gestureMode = GestureMode.IGNORED; hasMoved = true
                            return true
                        }
                        if (hypot(dx, dy) > touchSlop) hasMoved = true
                        if (recognition == SidePullRecognition.FOLLOW && gestureMode != GestureMode.FOLLOW_PANEL_DRAG) {
                            activeSideState.value = resolveTriggerOpenSide(position)
                            gestureMode = GestureMode.FOLLOW_PANEL_DRAG
                            preparePanelForDragging()
                            if (config.vibrate && !hasVibrated) { systemController.performTickHaptic(); hasVibrated = true }
                        }
                        if (gestureMode == GestureMode.FOLLOW_PANEL_DRAG) {
                            openProgressState.floatValue = SidePullTriggerPolicy.followProgress(dy, getScreenSize().second)
                        }
                        return true
                    }

                    gestureDetector.onTouchEvent(event)

                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    val absDx = abs(dx)
                    val absDy = abs(dy)

                    val distance = hypot(dx, dy)
                    if (distance > touchSlop) hasMoved = true
                    if (gestureMode == GestureMode.CLICK_WAITING) {
                        if (TriggerGesturePolicy.reachesSwipeDistance(distance, config.minimumSwipeDistanceDp, density)) {
                            val dir = when {
                                dy < 0 && absDy > absDx * 1.2f -> TriggerSwipeDirection.UP
                                dy > 0 && absDy > absDx * 1.2f -> TriggerSwipeDirection.DOWN
                                dx < 0 && absDx >= absDy -> TriggerSwipeDirection.LEFT
                                else -> TriggerSwipeDirection.RIGHT
                            }
                            val action = when (dir) {
                                TriggerSwipeDirection.UP -> config.swipeUpAction
                                TriggerSwipeDirection.DOWN -> config.swipeDownAction
                                TriggerSwipeDirection.LEFT -> config.swipeLeftAction
                                TriggerSwipeDirection.RIGHT -> config.swipeRightAction
                            }

                            if (action == GestureAction.LEFT_OPEN_NOS || action == GestureAction.RIGHT_OPEN_NOS) {
                                val side = resolveTriggerOpenSide(position, action)
                                activeSideState.value = side
                                followDirection = dir
                                gestureMode = GestureMode.FOLLOW_PANEL_DRAG
                                preparePanelForDragging()
                                if (config.vibrate && !hasVibrated) {
                                    systemController.performTickHaptic()
                                    hasVibrated = true
                                }
                            } else if (action != GestureAction.NONE) {
                                gestureMode = GestureMode.SWIPE_ACTION_PENDING
                                pendingSwipeAction = action
                            } else {
                                gestureMode = GestureMode.IGNORED
                            }
                        }
                    }

                    when (gestureMode) {
                        GestureMode.FOLLOW_PANEL_DRAG -> {
                            val (screenWidth, screenHeight) = getScreenSize()
                            val panelFraction = (repository.state.value.themeConfig.panelWidthPercent / 100f).coerceIn(0.3f, 1f)
                            val panelWidth = screenWidth.toFloat() * panelFraction

                            val progress = TriggerGesturePolicy.followProgress(
                                followDirection, dx, dy, panelWidth, screenHeight.toFloat()
                            )
                            openProgressState.floatValue = progress
                        }
                        GestureMode.SWIPE_ACTION_PENDING -> {
                            val dist = hypot(dx, dy)
                            if (TriggerGesturePolicy.reachesSwipeDistance(dist, config.minimumSwipeDistanceDp, density)) {
                                gestureMode = GestureMode.SWIPE_ACTION_TRIGGERED
                                if (config.vibrate) {
                                    systemController.performTickHaptic()
                                }
                                systemController.executeGestureAction(pendingSwipeAction) { side ->
                                    animateOpenPanel(side)
                                }
                            }
                        }
                        else -> {}
                    }
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    isTouched = false
                    invalidate()

                    if (gestureMode == GestureMode.SECOND_SLIDE_PRIMING) {
                        gestureMode = GestureMode.IDLE
                        return true
                    }

                    if (sidePull) {
                        val dx = event.rawX - downRawX
                        val dy = event.rawY - downRawY
                        val shouldOpen = gestureMode != GestureMode.IGNORED && SidePullTriggerPolicy.supports(position) &&
                            SidePullTriggerPolicy.sidePullRecognition(dx, dy, touchSlop.toFloat(), config.minimumSwipeDistanceDp, density) == SidePullRecognition.FOLLOW
                        if (shouldOpen) {
                            if (config.vibrate && !hasVibrated) systemController.performTickHaptic()
                            animateOpenPanel(resolveTriggerOpenSide(position))
                        } else if (gestureMode == GestureMode.FOLLOW_PANEL_DRAG) animateClosePanel()
                        gestureMode = GestureMode.IDLE
                        return true
                    }

                    when (gestureMode) {
                        GestureMode.FOLLOW_PANEL_DRAG -> {
                            val dx = event.rawX - downRawX
                            val dy = event.rawY - downRawY
                            val inwardDistance = TriggerGesturePolicy.projectedDistance(followDirection, dx, dy)
                            val shouldOpen = TriggerGesturePolicy.reachesSwipeDistance(inwardDistance, config.minimumSwipeDistanceDp, density)
                            if (shouldOpen) {
                                animateOpenPanel(activeSideState.value)
                            } else {
                                animateClosePanel()
                            }
                        }
                        GestureMode.CLICK_WAITING -> {
                            val dx = event.rawX - downRawX
                            val dy = event.rawY - downRawY
                            val dist = hypot(dx, dy)
                            if (TriggerGesturePolicy.isTap(hasMoved, dist, touchSlop.toFloat()) && config.doubleTapAction == GestureAction.NONE && config.singleTapAction != GestureAction.NONE) {
                                if (config.vibrate) {
                                    systemController.performTickHaptic()
                                }
                                systemController.executeGestureAction(config.singleTapAction) { side ->
                                    animateOpenPanel(side)
                                }
                            } else {
                                gestureDetector.onTouchEvent(event)
                            }
                        }
                        else -> {
                            gestureDetector.onTouchEvent(event)
                        }
                    }

                    gestureMode = GestureMode.IDLE
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
                    isTouched = false
                    invalidate()
                    if (gestureMode == GestureMode.FOLLOW_PANEL_DRAG) {
                        animateClosePanel()
                    }
                    gestureMode = GestureMode.IDLE
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }

    /**
     * 自定义悬浮球 View
     */
    inner class SuspensionBallView(context: Context) : View(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        private var downRawX = 0f
        private var downRawY = 0f
        private var downLpX = 0
        private var downLpY = 0

        private var isDragging = false
        private var hasMoved = false
        private var isTouched = false
        private var breatheScale = 1.0f
        private var breatheAnimator: ValueAnimator? = null
        private var idleFadeJob: Job? = null
        private var idleFadeAnimator: ValueAnimator? = null
        private var snapAnimator: ValueAnimator? = null
        private var edgeDwell: Runnable? = null
        private var edgeAnchorX = 0f
        private var edgeAnchorY = 0f
        private var mouseEntryAttempted = false
        private var suppressNormalUntilUp = false
        private var currentAlphaFraction = 1.0f

        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

        private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (accessibilityMouse?.active == true || suppressNormalUntilUp || isDragging || hasMoved) return false
                val sbConfig = repository.state.value.suspensionBallConfig
                val act = sbConfig.singleTapAction
                if (act != GestureAction.NONE) {
                    if (repository.state.value.triggerConfig.vibrate) systemController.performTickHaptic()
                    systemController.executeGestureAction(act) { side -> animateOpenPanel(side) }
                    return true
                }
                return false
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (accessibilityMouse?.active == true || suppressNormalUntilUp || isDragging || hasMoved) return false
                val sbConfig = repository.state.value.suspensionBallConfig
                val act = sbConfig.doubleTapAction
                if (act != GestureAction.NONE) {
                    if (repository.state.value.triggerConfig.vibrate) systemController.performTickHaptic()
                    systemController.executeGestureAction(act) { side -> animateOpenPanel(side) }
                    return true
                }
                return false
            }

            override fun onLongPress(e: MotionEvent) {
                if (accessibilityMouse?.active != true && !suppressNormalUntilUp && !hasMoved) {
                    if (repository.state.value.triggerConfig.vibrate) {
                        systemController.performTickHaptic()
                    }
                    isDragging = true
                    invalidate()
                }
            }
        })

        fun cleanup() {
            cancelMouseEntry()
            accessibilityMouse?.exit()
            snapAnimator?.cancel()
            snapAnimator = null
            idleFadeAnimator?.cancel()
            idleFadeAnimator = null
            breatheAnimator?.cancel()
            breatheAnimator = null
            idleFadeJob?.cancel()
            idleFadeJob = null
        }

        override fun onDetachedFromWindow() {
            cleanup()
            super.onDetachedFromWindow()
        }

        fun updateBreatheMode(enabled: Boolean) {
            breatheAnimator?.cancel()
            breatheAnimator = null
            if (enabled && isAttachedToWindow && visibility == View.VISIBLE && accessibilityMouse?.active != true) {
                val freqSec = repository.state.value.suspensionBallConfig.breatheFrequencySeconds.coerceIn(1, 10)
                breatheAnimator = ValueAnimator.ofFloat(0.92f, 1.06f).apply {
                    duration = freqSec * 700L
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = AccelerateDecelerateInterpolator()
                    addUpdateListener {
                        breatheScale = it.animatedValue as Float
                        invalidate()
                    }
                    start()
                }
            } else {
                breatheScale = 1.0f
                invalidate()
            }
        }

        private fun resetIdleFadeTimer() {
            idleFadeJob?.cancel()
            idleFadeAnimator?.cancel()
            idleFadeAnimator = null
            if (currentAlphaFraction < 1.0f) {
                currentAlphaFraction = 1.0f
                invalidate()
            }
        }

        fun scheduleIdleFade() {
            idleFadeJob?.cancel()
            val sbConfig = repository.state.value.suspensionBallConfig
            if (sbConfig.autoFadeSeconds > 0 && accessibilityMouse?.active != true) {
                idleFadeJob = serviceScope.launch {
                    delay(sbConfig.autoFadeSeconds * 1000L)
                    if (!isAttachedToWindow || visibility != View.VISIBLE || accessibilityMouse?.active == true) return@launch
                    val anim = ValueAnimator.ofFloat(currentAlphaFraction, 0.4f).apply {
                        duration = 300L
                        addUpdateListener {
                            currentAlphaFraction = it.animatedValue as Float
                            invalidate()
                        }
                    }
                    idleFadeAnimator = anim
                    anim.start()
                }
            }
        }

        override fun onVisibilityChanged(changedView: View, visibility: Int) {
            super.onVisibilityChanged(changedView, visibility)
            // View's constructor can dispatch this before Kotlin fields initialize.
            if (!isAttachedToWindow) return
            updateBreatheMode(repository.state.value.suspensionBallConfig.breatheMode)
            if (visibility == View.VISIBLE) scheduleIdleFade() else {
                idleFadeJob?.cancel()
                idleFadeAnimator?.cancel()
            }
        }

        fun onMouseModeChanged(active: Boolean) {
            cancelMouseEntry()
            if (!active && isTouched) suppressNormalUntilUp = true
            isDragging = false
            hasMoved = true
            val cancel = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
            gestureDetector.onTouchEvent(cancel)
            cancel.recycle()
            resetIdleFadeTimer()
            updateBreatheMode(repository.state.value.suspensionBallConfig.breatheMode)
            contentDescription = if (active) "鼠标触控球，滑动移动指针，短点点击，双指或长按退出" else "悬浮球"
            if (!active && !windowCoordination.destroying) {
                val config = repository.state.value.suspensionBallConfig
                val (screenWidth, screenHeight) = getScreenSize()
                val sizePx = (config.radiusDp * 2 * resources.displayMetrics.density).roundToInt()
                (layoutParams as? WindowManager.LayoutParams)?.let { lp ->
                    lp.x = ((screenWidth - sizePx).coerceAtLeast(0) * config.posXPercent).roundToInt()
                    lp.y = ((screenHeight - sizePx).coerceAtLeast(0) * config.posYPercent).roundToInt()
                    runCatching { windowManager.updateViewLayout(this, lp) }
                }
                scheduleIdleFade()
            }
            invalidate()
        }

        private fun cancelMouseEntry() {
            edgeDwell?.let { removeCallbacks(it) }
            edgeDwell = null
        }

        private fun scheduleMouseEntry(lp: WindowManager.LayoutParams, screenWidth: Int, screenHeight: Int, sizePx: Int) {
            val config = repository.state.value.suspensionBallConfig
            val edgeMargin = (12 * resources.displayMetrics.density).roundToInt()
            if (!config.mouseMode || mouseEntryAttempted || !isDragging || !hasMoved ||
                !AccessibilityMousePolicy.atEdge(lp.x, lp.y, sizePx, screenWidth, screenHeight, edgeMargin)) {
                cancelMouseEntry()
                return
            }
            if (edgeDwell != null && AccessibilityMousePolicy.dwellStillValid(
                    edgeAnchorX, edgeAnchorY, lp.x.toFloat(), lp.y.toFloat(), touchSlop.toFloat())) return
            cancelMouseEntry()
            edgeAnchorX = lp.x.toFloat()
            edgeAnchorY = lp.y.toFloat()
            edgeDwell = Runnable {
                edgeDwell = null
                val current = layoutParams as? WindowManager.LayoutParams ?: return@Runnable
                if (!isAttachedToWindow || visibility != View.VISIBLE || !isTouched || !isDragging ||
                    !repository.state.value.suspensionBallConfig.mouseMode) return@Runnable
                val (width, height) = getScreenSize()
                if (!AccessibilityMousePolicy.atEdge(current.x, current.y, sizePx, width, height, edgeMargin)) return@Runnable
                mouseEntryAttempted = true
                accessibilityMouse?.enter(this) // Temporary edge control position does not replace the saved normal ball position.
            }.also { postDelayed(it, AccessibilityMousePolicy.EDGE_DWELL_MS) }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val sbConfig = repository.state.value.suspensionBallConfig
            val density = resources.displayMetrics.density
            val w = width.toFloat()
            val h = height.toFloat()
            val cx = w / 2f
            val cy = h / 2f
            val radius = minOf(w, h) / 2f

            val alpha = (sbConfig.alpha * currentAlphaFraction * 255).roundToInt().coerceIn(0, 255)

            canvas.save()
            if (sbConfig.breatheMode) {
                canvas.scale(breatheScale, breatheScale, cx, cy)
            }

            // 外圈柔和微光
            glowPaint.color = 0xFF2196F3.toInt()
            glowPaint.alpha = (alpha * 0.25f).roundToInt()
            canvas.drawCircle(cx, cy, radius * 0.96f, glowPaint)

            // 悬浮球主体
            paint.color = if (accessibilityMouse?.active == true) 0xFF00695C.toInt() else if (isDragging) 0xFF1976D2.toInt() else 0xFF2B303B.toInt()
            paint.alpha = alpha
            canvas.drawCircle(cx, cy, radius * 0.82f, paint)

            // 内部装饰光环
            ringPaint.style = Paint.Style.STROKE
            ringPaint.strokeWidth = 2f * density
            ringPaint.color = 0xFF64B5F6.toInt()
            ringPaint.alpha = (alpha * 0.7f).roundToInt()
            canvas.drawCircle(cx, cy, radius * 0.55f, ringPaint)

            // 中心微圆心或四宫格图标
            ringPaint.style = Paint.Style.FILL
            ringPaint.color = Color.WHITE
            ringPaint.alpha = alpha
            if (sbConfig.showIcon) {
                val dotOffset = 3.6f * density
                val dotRadius = 2.0f * density
                canvas.drawCircle(cx - dotOffset, cy - dotOffset, dotRadius, ringPaint)
                canvas.drawCircle(cx + dotOffset, cy - dotOffset, dotRadius, ringPaint)
                canvas.drawCircle(cx - dotOffset, cy + dotOffset, dotRadius, ringPaint)
                canvas.drawCircle(cx + dotOffset, cy + dotOffset, dotRadius, ringPaint)
            } else {
                canvas.drawCircle(cx, cy, 3f * density, ringPaint)
            }

            canvas.restore()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            resetIdleFadeTimer()
            if (accessibilityMouse?.active == true) {
                isTouched = event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL
                return accessibilityMouse?.onTouch(event) ?: true
            }
            if (suppressNormalUntilUp) {
                if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL ||
                    event.actionMasked == MotionEvent.ACTION_DOWN) {
                    suppressNormalUntilUp = false
                    isTouched = false
                }
                // A new DOWN starts a genuinely new gesture; residual MOVE/UP stay consumed.
                if (event.actionMasked != MotionEvent.ACTION_DOWN) return true
            }
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                hasMoved = false
                isDragging = false
                mouseEntryAttempted = false
                snapAnimator?.cancel()
                snapAnimator = null
                cancelMouseEntry()
            }
            gestureDetector.onTouchEvent(event)

            val (screenWidth, screenHeight) = getScreenSize()
            val density = resources.displayMetrics.density
            val sbConfig = repository.state.value.suspensionBallConfig
            val sizePx = (sbConfig.radiusDp * 2 * density).roundToInt()

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    val lp = layoutParams as? WindowManager.LayoutParams
                    downLpX = lp?.x ?: 0
                    downLpY = lp?.y ?: 0
                    isDragging = false
                    hasMoved = false
                    isTouched = true
                    currentAlphaFraction = 1.0f
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    val dist = hypot(dx, dy)
                    if (dist > touchSlop) {
                        hasMoved = true
                    }

                    if (isDragging) {
                        val lp = layoutParams as? WindowManager.LayoutParams
                        if (lp != null) {
                            val maxX = (screenWidth - sizePx).coerceAtLeast(0)
                            val maxY = (screenHeight - sizePx).coerceAtLeast(0)
                            val newX = (downLpX + dx).roundToInt().coerceIn(0, maxX)
                            val newY = (downLpY + dy).roundToInt().coerceIn(0, maxY)
                            lp.x = newX
                            lp.y = newY
                            try {
                                windowManager.updateViewLayout(this, lp)
                                scheduleMouseEntry(lp, screenWidth, screenHeight, sizePx)
                            } catch (_: Exception) { cancelMouseEntry() }
                        }
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    cancelMouseEntry()
                    isTouched = false
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    val absDx = abs(dx)
                    val absDy = abs(dy)
                    val dist = hypot(dx, dy)

                    if (isDragging) {
                        isDragging = false
                        if (hasMoved || dist > touchSlop) {
                            // 持久化拖拽后的百分比位置 (BUG-003: 支持自动贴边吸附动画)
                            val lp = layoutParams as? WindowManager.LayoutParams
                            val maxX = (screenWidth - sizePx).coerceAtLeast(1).toFloat()
                            val maxY = (screenHeight - sizePx).coerceAtLeast(1).toFloat()
                            val curX = (lp?.x ?: 0).toFloat()
                            val curY = (lp?.y ?: 0).toFloat()
                            val rawPosX = (curX / maxX).coerceIn(0.02f, 0.98f)
                            val newPosX = OverlayVisibilityPolicy.snapSuspensionBallXPercent(rawPosX, sbConfig.snapToEdge)
                            val newPosY = (curY / maxY).coerceIn(0.02f, 0.98f)
                            if (sbConfig.snapToEdge && lp != null) {
                                val targetX = (newPosX * maxX).roundToInt()
                                val startX = lp.x
                                if (startX != targetX) {
                                    snapAnimator?.cancel()
                                    snapAnimator = ValueAnimator.ofInt(startX, targetX).apply {
                                        duration = 180L
                                        interpolator = DecelerateInterpolator()
                                        addUpdateListener { anim ->
                                            if (isAttachedToWindow) {
                                                lp.x = anim.animatedValue as Int
                                                runCatching { windowManager.updateViewLayout(this@SuspensionBallView, lp) }
                                            }
                                        }
                                        start()
                                    }
                                }
                            }
                            repository.updateSuspensionBallConfig {
                                it.copy(posXPercent = newPosX, posYPercent = newPosY)
                            }
                        } else {
                            // 长按未发生明显拖动，执行长按绑定动作
                            val act = sbConfig.longPressAction
                            if (act != GestureAction.NONE) {
                                systemController.executeGestureAction(act) { side -> animateOpenPanel(side) }
                            }
                        }
                    } else if (TriggerGesturePolicy.reachesSwipeDistance(dist, repository.state.value.triggerConfig.minimumSwipeDistanceDp, density)) {
                        // 7 向滑动手势判断
                        val dir = when {
                            dy < 0 && absDy > absDx * 1.2f -> TriggerSwipeDirection.UP
                            dy > 0 && absDy > absDx * 1.2f -> TriggerSwipeDirection.DOWN
                            dx < 0 && absDx >= absDy -> TriggerSwipeDirection.LEFT
                            else -> TriggerSwipeDirection.RIGHT
                        }
                        val act = when (dir) {
                            TriggerSwipeDirection.UP -> sbConfig.swipeUpAction
                            TriggerSwipeDirection.DOWN -> sbConfig.swipeDownAction
                            TriggerSwipeDirection.LEFT -> sbConfig.swipeLeftAction
                            TriggerSwipeDirection.RIGHT -> sbConfig.swipeRightAction
                        }
                        if (act != GestureAction.NONE) {
                            if (repository.state.value.triggerConfig.vibrate) {
                                systemController.performTickHaptic()
                            }
                            systemController.executeGestureAction(act) { side -> animateOpenPanel(side) }
                        }
                    } else {
                        // 单击判断
                        if (sbConfig.doubleTapAction == GestureAction.NONE && sbConfig.singleTapAction != GestureAction.NONE && TriggerGesturePolicy.isTap(hasMoved, dist, touchSlop.toFloat())) {
                            if (repository.state.value.triggerConfig.vibrate) {
                                systemController.performTickHaptic()
                            }
                            systemController.executeGestureAction(sbConfig.singleTapAction) { side -> animateOpenPanel(side) }
                        }
                    }
                    invalidate()
                    scheduleIdleFade()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    cancelMouseEntry()
                    isDragging = false
                    isTouched = false
                    invalidate()
                    scheduleIdleFade()
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }

    // --- 前台服务常驻通知 ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "全局负一屏常驻服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "维持全局负一屏边缘手势与桌面小部件运行"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("force_settings", true)
        }
        val mainPendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = Intent(this, OverlayService::class.java).setAction(ACTION_TOGGLE_PANEL)
        val togglePendingIntent = PendingIntent.getService(
            this,
            1,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("全局负一屏运行中")
            .setContentText("从屏幕边缘向内轻扫即可呼出负一屏")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(mainPendingIntent)
            .addAction(0, "立即呼出负一屏", togglePendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val TAG = "OverlayService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "global_minus_one_overlay"

        const val ACTION_START = "com.example.globalminusone.action.START"
        const val ACTION_STOP = "com.example.globalminusone.action.STOP"
        const val ACTION_OPEN_PANEL = "com.example.globalminusone.action.OPEN_PANEL"
        const val ACTION_CLOSE_PANEL = "com.example.globalminusone.action.CLOSE_PANEL"
        const val ACTION_TOGGLE_PANEL = "com.example.globalminusone.action.TOGGLE_PANEL"
        const val ACTION_ENTER_EDIT_MODE = "com.example.globalminusone.action.ENTER_EDIT_MODE"
        const val ACTION_REGION_SCREENSHOT = "com.example.globalminusone.action.REGION_SCREENSHOT"

        fun start(context: Context, action: String = ACTION_START) {
            if (action == ACTION_STOP) {
                stop(context)
                return
            }
            if (!Settings.canDrawOverlays(context) ||
                !MinusOneRepository.getInstance(context).state.value.triggerConfig.serviceEnabled) return
            val intent = Intent(context, OverlayService::class.java).setAction(action)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (failure: SecurityException) {
                reportStartRejected(context, failure)
            } catch (failure: IllegalStateException) {
                // Includes ForegroundServiceStartNotAllowedException on API31+.
                reportStartRejected(context, failure)
            }
        }

        fun stop(context: Context) {
            // Keep stop ordered with pending starts. A raw stopService can cancel a
            // startForegroundService request before its foreground acknowledgement.
            val intent = Intent(context, OverlayService::class.java).setAction(ACTION_STOP)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (failure: SecurityException) {
                stopAfterRequestRejected(context, failure)
            } catch (failure: IllegalStateException) {
                stopAfterRequestRejected(context, failure)
            }
        }

        private fun reportStartRejected(context: Context, failure: RuntimeException) {
            Log.e(TAG, "System rejected overlay foreground start", failure)
            val appContext = context.applicationContext
            MinusOneRepository.getInstance(appContext).setOverlayServiceRunning(false)
            ContextCompat.getMainExecutor(appContext).execute {
                Toast.makeText(appContext, "系统未允许启动悬浮服务，请回到应用后重试", Toast.LENGTH_LONG).show()
            }
        }

        private fun stopAfterRequestRejected(context: Context, failure: RuntimeException) {
            Log.w(TAG, "System rejected ordered stop; stopping own service directly", failure)
            // A rejected foreground request has no startId to acknowledge. Preserve
            // the ability to stop our own service without starting/restarting it.
            context.stopService(Intent(context, OverlayService::class.java))
            MinusOneRepository.getInstance(context).setOverlayServiceRunning(false)
        }
    }
}
