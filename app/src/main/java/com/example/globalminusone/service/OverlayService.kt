package com.example.globalminusone.service

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
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
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
import com.example.globalminusone.data.OverlayLayerStatus
import com.example.globalminusone.data.resolvedPanelOpenCloseMs
import com.example.globalminusone.data.ShowSideMode
import com.example.globalminusone.data.SuspensionBallConfig
import com.example.globalminusone.data.TriggerConfig
import com.example.globalminusone.data.TriggerPosition
import com.example.globalminusone.data.TriggerSide
import com.example.globalminusone.data.TriggerWay
import com.example.globalminusone.system.SystemController
import com.example.globalminusone.system.AppAndIntentLauncher
import com.example.globalminusone.ui.panel.MinusOneOverlayContainer
import com.example.globalminusone.ui.panel.cards.UserIconCache
import com.example.globalminusone.ui.panel.container.collectComponentTreeIcons
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
import kotlin.math.roundToInt
import com.example.globalminusone.service.accessibility.AccessibilityMouseController
import com.example.globalminusone.service.capture.AndroidOverlayCaptureBackend
import com.example.globalminusone.service.overlay.AccessibilityWindowTarget
import com.example.globalminusone.service.overlay.AndroidPanelWindowBinding
import com.example.globalminusone.service.capture.CaptureSurfaceState
import com.example.globalminusone.service.overlay.EdgeTriggerHost
import com.example.globalminusone.service.overlay.EdgeTriggerView
import com.example.globalminusone.service.embedded.EmbeddedApplicationWindowHost
import com.example.globalminusone.service.overlay.OverlayBrightnessWindowHost
import com.example.globalminusone.service.overlay.OverlayPowerGuard
import com.example.globalminusone.service.capture.OverlayCaptureConfig
import com.example.globalminusone.service.capture.OverlayCaptureSession
import com.example.globalminusone.service.capture.OverlayCaptureSettings
import com.example.globalminusone.service.capture.OverlayCaptureUi
import com.example.globalminusone.service.overlay.AndroidManagedOverlaySlot
import com.example.globalminusone.service.capture.OverlayScreenCapturePipeline
import com.example.globalminusone.service.overlay.OverlayVisibilityPolicy
import com.example.globalminusone.service.overlay.OverlayWindowCoordination
import com.example.globalminusone.service.overlay.PanelInputHost
import com.example.globalminusone.service.overlay.PanelMotionController
import com.example.globalminusone.service.overlay.PanelWindowBlurPolicy
import com.example.globalminusone.service.overlay.PanelWindowFactory
import com.example.globalminusone.service.overlay.PanelWindowHost
import com.example.globalminusone.service.overlay.PanelWindowInstance
import com.example.globalminusone.service.overlay.applyPanelRefreshRateHint
import com.example.globalminusone.service.overlay.collectOverlayLayerStatus
import com.example.globalminusone.service.overlay.configureFullscreenCutout
import com.example.globalminusone.service.overlay.createPanelWindowInitialParams
import com.example.globalminusone.service.overlay.createPanelWindowInstance
import com.example.globalminusone.service.overlay.resolveAccessibilityWindowTarget
import com.example.globalminusone.service.overlay.resolveDisplayMaxRefreshRate
import com.example.globalminusone.service.capture.RegionScreenshotOverlay
import com.example.globalminusone.service.capture.ScreenAssistantBrowseResumePill
import com.example.globalminusone.service.capture.ScreenAssistantFrameSnapshot
import com.example.globalminusone.service.capture.ScreenAssistantOverlay
import com.example.globalminusone.service.overlay.SuspensionBallHost
import com.example.globalminusone.service.overlay.SuspensionBallView
import com.example.globalminusone.service.overlay.calculateEdgeTriggerWindowLayout
import com.example.globalminusone.service.embedded.embeddedHostFocusFlags
import com.example.globalminusone.service.overlay.ensureOverlayWindowRegistered
import com.example.globalminusone.service.overlay.handleOverlayStart
import com.example.globalminusone.service.overlay.overlayUpdates
import com.example.globalminusone.service.overlay.replaceWindowInputAdjustment

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
    private val panelWindowBinding = AndroidPanelWindowBinding()
    private val panelWindowHost = PanelWindowHost(panelWindowBinding) { message, failure ->
        Log.e(TAG, message, failure)
    }
    private lateinit var repository: MinusOneRepository
    private lateinit var systemController: SystemController
    private lateinit var widgetHostManager: WidgetHostManager
    private lateinit var applicationWindows: EmbeddedApplicationWindowHost
    private var activeAccessibilityTarget: AccessibilityWindowTarget? = null

    private val panelComposeView: android.widget.FrameLayout get() = panelWindowBinding.view
    private val panelParams: WindowManager.LayoutParams get() = panelWindowBinding.params

    private val triggerSlots by lazy {
        listOf(TriggerPosition.LEFT, TriggerPosition.RIGHT, TriggerPosition.TOP, TriggerPosition.BOTTOM)
            .associateWith { AndroidManagedOverlaySlot<EdgeTriggerView>(this, windowManager) { v -> v.updateGestureExclusion(false) } }
    }
    private val suspensionBallSlot by lazy {
        AndroidManagedOverlaySlot<SuspensionBallView>(this, windowManager) { it.cleanup() }
    }
    private val leftTriggerView: EdgeTriggerView? get() = triggerSlots[TriggerPosition.LEFT]?.view
    private val rightTriggerView: EdgeTriggerView? get() = triggerSlots[TriggerPosition.RIGHT]?.view
    private val topTriggerView: EdgeTriggerView? get() = triggerSlots[TriggerPosition.TOP]?.view
    private val bottomTriggerView: EdgeTriggerView? get() = triggerSlots[TriggerPosition.BOTTOM]?.view
    private val suspensionBallView: SuspensionBallView? get() = suspensionBallSlot.view
    private var accessibilityMouse: AccessibilityMouseController? = null
    private var eyeProtectionView: View? = null
    private var brightnessWindowHost: OverlayBrightnessWindowHost? = null
    private var powerGuard: OverlayPowerGuard? = null
    private var regionScreenshotOverlay: RegionScreenshotOverlay? = null
    private var screenAssistantOverlay: ScreenAssistantOverlay? = null
    private var browseResumePill: ScreenAssistantBrowseResumePill? = null
    private var underlyingScrollJob: Job? = null
    private val captureSession = OverlayCaptureSession()

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val openProgressState = mutableFloatStateOf(0f)
    private val activeSideState = mutableStateOf(TriggerSide.LEFT)

    private val panelMotionController = PanelMotionController { openProgressState.floatValue = it }
    private val windowCoordination = OverlayWindowCoordination()
    private var initialized = false
    private var foregroundReady = false
    private val panelRegistered: Boolean get() = panelWindowHost.registered
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
            onBallRestoreFailure = { recreateSuspensionBallAfterMouseFailure() },
            updateSourceLayout = { view, params -> suspensionBallHost.updateBallLayout(view, params) }
        )
        if (!::widgetHostManager.isInitialized) widgetHostManager = WidgetHostManager.getInstance(this)
        if (powerGuard == null) {
            powerGuard = OverlayPowerGuard(this, serviceScope, widgetHostManager,
                hasWidgets = { repository.retainedWidgetIdsForHost().isNotEmpty() },
                onScreenInteractiveChanged = { suspensionBallView?.setScreenInteractive(it) }
            ).also { it.register() }
        }

        if (!::applicationWindows.isInitialized) applicationWindows = EmbeddedApplicationWindowHost(this, repository, serviceScope) { pending ->
            setExternalAuthorizationPending(windowCoordination.requestAuthorization(
                OverlayWindowCoordination.AuthorizationSource.APPLICATION_WINDOWS, pending
            ))
        }
        systemController.activeEmbeddedWindowBackHandler = {
            applicationWindows.performBackOnActiveWindow()
        }
        if (screenAssistantOverlay == null) {
            screenAssistantOverlay = ScreenAssistantOverlay(
                context = this,
                windowManager = windowManager,
                repository = repository,
                scope = serviceScope,
                onSwitchToRegionCrop = { capturedBmp, snapshot ->
                    regionScreenshotOverlay?.show(capturedBmp, snapshot) ?: run {
                        snapshot.enriched?.cancel()
                        capturedBmp.recycle()
                    }
                },
                onScrollUnderlying = ::scrollUnderlyingAndReopenScreenAssistant,
                onPauseToBrowseUnderlying = ::pauseScreenAssistantForManualBrowse
            )
        }
        if (regionScreenshotOverlay == null) {
            regionScreenshotOverlay = RegionScreenshotOverlay(
                context = this,
                windowManager = windowManager,
                scope = serviceScope,
                onAnalyzeRegion = ::openCroppedScreenAssistant
            )
        }
        if (!panelWindowBinding.hasParams) setupPanelOverlayWindow() else recoverPanelWindow()
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
        if (brightnessWindowHost == null) {
            brightnessWindowHost = OverlayBrightnessWindowHost(
                context = this,
                windowManager = windowManager,
                isDestroying = { windowCoordination.destroying },
                isAuthorizationPending = { windowCoordination.authorizationPending },
                syncPanelBrightness = { brightness ->
                    if (panelWindowBinding.hasParams && panelParams.screenBrightness != brightness) {
                        panelParams.screenBrightness = brightness
                        if (panelRegistered && panelWindowBinding.hasView && panelComposeView.isAttachedToWindow) {
                            runCatching { panelWindowBinding.updateLayout() }
                        }
                    }
                },
                onScreenOff = {
                    if (::systemController.isInitialized) systemController.clearOverlayBrightnessOverride()
                }
            )
        }
        brightnessWindowHost?.registerScreenOff()
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
            systemController.overlayBrightnessOverride.collect { brightness ->
                brightnessWindowHost?.apply(brightness)
            }
        }
        serviceScope.launch {
            GlobalAccessibilityService.connectedState.collect { connected ->
                if (!connected) accessibilityMouse?.exit()
                resetAccessibilityLayerFailures()
                if (initialized && panelWindowBinding.hasParams &&
                    (repository.state.value.advancedConfig.highestOverlayLevel ||
                        panelWindowHost.activeType == WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
                ) {
                    recoverPanelWindow()
                    updateTriggerViews()
                    updateSuspensionBallView()
                    publishRunningState()
                } else if (initialized) {
                    publishOverlayLayerStatus()
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
        val action = intent?.action
        val warmFastAction = (action == ACTION_OPEN_PANEL || action == ACTION_CLOSE_PANEL ||
            action == ACTION_TOGGLE_PANEL || action == ACTION_REGION_SCREENSHOT ||
            action == ACTION_SCREEN_ASSISTANT || action == ACTION_BREENO_TOUCH)
        val accepted = handleOverlayStart(
            startId = startId,
            shouldRun = action != ACTION_STOP &&
                repository.state.value.triggerConfig.serviceEnabled && Settings.canDrawOverlays(this),
            assertForeground = ::assertForeground,
            prepareWindows = {
                if (warmFastAction && initialized && windowsReady()) {
                    true
                } else {
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
                }
            },
            publishRunning = repository::setOverlayServiceRunning,
            stopById = ::stopSelfResult,
            onFailure = { failure -> Log.e(TAG, "Cannot start overlay windows", failure) }
        )
        if (!accepted) return START_NOT_STICKY
        when (action) {
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
            ACTION_REGION_SCREENSHOT, ACTION_SCREEN_ASSISTANT -> {
                requestCapture(action)
            }
            ACTION_BREENO_TOUCH -> {
                startBreenoTouch()
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
        captureSession.cancelRegionCapture()
        captureSession.cancelBreenoLaunch()
        underlyingScrollJob?.cancel()
        underlyingScrollJob = null
        hideBrowseResumePill()
        regionScreenshotOverlay?.dismiss()
        screenAssistantOverlay?.dismiss()
        captureSession.clearSnapshot()
        val isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        val isPanelOpenOrOpening = openProgressState.floatValue > 0f ||
            (panelWindowBinding.hasView && panelComposeView.visibility == View.VISIBLE)
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

        panelMotionController.cancel(resetVelocity = true)
        tempDisableJob?.cancel()
        if (packageReceiverRegistered) {
            packageReceiverRegistered = false
            runCatching { unregisterReceiver(packageChangeReceiver) }
        }
        brightnessWindowHost?.destroy()
        brightnessWindowHost = null
        captureSession.cancelRegionCapture()
        captureSession.cancelBreenoLaunch()
        underlyingScrollJob?.cancel()
        underlyingScrollJob = null
        hideBrowseResumePill()
        com.example.globalminusone.screenassistant.ShizukuBreenoSession.close()
        regionScreenshotOverlay?.dismiss()
        screenAssistantOverlay?.dismiss()
        captureSession.clearSnapshot()
        regionScreenshotOverlay = null
        screenAssistantOverlay = null
        if (::systemController.isInitialized) {
            systemController.activeEmbeddedWindowBackHandler = null
        }
        if (::applicationWindows.isInitialized) applicationWindows.destroy()

        removeAllEdgeTriggers()
        removeSuspensionBallWindow()

        eyeProtectionView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            eyeProtectionView = null
        }

        panelWindowBinding.destroy()
        repository.updateOverlayLayerStatus(OverlayLayerStatus())

        powerGuard?.destroy()
        powerGuard = null
        if (::widgetHostManager.isInitialized) widgetHostManager.stopListening()

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
                powerGuard?.syncWidgetPresence()
                val prev = previous
                val changes = overlayUpdates(prev, current)
                val overlayLayerChanged = prev != null &&
                    prev.advancedConfig.highestOverlayLevel != current.advancedConfig.highestOverlayLevel
                if (overlayLayerChanged) {
                    resetAccessibilityLayerFailures()
                    if (panelWindowBinding.hasParams) recoverPanelWindow()
                    updateTriggerViews()
                    updateSuspensionBallView()
                }
                if (changes.triggers && !overlayLayerChanged) {
                    updateTriggerViews(); checkTempDisabled()
                } else if (changes.triggers) {
                    checkTempDisabled()
                }
                if (changes.ball && !overlayLayerChanged) {
                    updateSuspensionBallView()
                }
                if (changes.eye) updateEyeProtectionWindow()
                if (changes.blur) applyBlurSettings()
                if (changes.visibility) updateTriggerViewsVisibility()
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
            repository.currentForegroundPackage.collect { pkg ->
                systemController.onForegroundPackageChanged(pkg)
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
        panelWindowBinding.configure(
            defaultManager = windowManager,
            initialParams = createPanelWindowInitialParams(),
            factory = PanelWindowFactory { targetType, targetManager, freshParams ->
                buildPanelWindowInstance(targetType, targetManager, freshParams)
            }
        )
        applyBlurSettings(updateLayout = false)
        recoverPanelWindow()
    }

    private fun buildPanelWindowInstance(
        targetType: Int,
        targetManager: WindowManager,
        freshParams: WindowManager.LayoutParams
    ): PanelWindowInstance {
        val windowContext = if (targetType == WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY) {
            activeAccessibilityTarget?.takeIf { it.windowManager === targetManager }?.context ?: this
        } else this
        var clearHostInputFocus: (() -> Unit)? = null
        val inputHost = object : PanelInputHost {
            override val remoteOwnsInput: Boolean get() = windowCoordination.remoteOwnsInput
            override val authorizationPending: Boolean get() = windowCoordination.authorizationPending
            override fun hideHostIme(windowToken: IBinder?) {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(windowToken, 0)
            }
            override fun clearComposeInputFocus() { clearHostInputFocus?.invoke() }
            override fun handleModalBack(): Boolean = com.example.globalminusone.ui.panel.PanelModalBackHandler.handleBack()
            override fun handleBrowserBack(): Boolean = com.example.globalminusone.ui.panel.PanelBrowserBackHandler.handleBack()
            override fun handleFileBack(): Boolean = com.example.globalminusone.system.file.PanelFileBrowser.handleBack()
            override fun handleEditorBack(): Boolean = com.example.globalminusone.ui.panel.gesture.PanelEditorBackHandler.handleBack()
            override fun animateClosePanel() = this@OverlayService.animateClosePanel()
        }
        return createPanelWindowInstance(windowContext, freshParams, inputHost) {
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
                onOpenEmbeddedApplication = { target -> applicationWindows.open(target); animateClosePanel() },
                onBrowserHostInputAllowed = { allowed -> setBrowserInputOwner(!allowed) },
                onFloatingWorkspaceInputMode = { active -> setFloatingWorkspaceInputMode(active) },
                onRequestClose = { animateClosePanel() },
                onOpenMainApp = { openMainApp(); animateClosePanel() },
                onRequestBindWidget = { pageId, containerId, beforeId, targetGridCell ->
                    animateClosePanel(); openWidgetPicker(pageId, containerId, beforeId, targetGridCell)
                }
            )
        }
    }

    private fun resetAccessibilityLayerFailures() {
        panelWindowHost.resetAccessibilityFailure()
        triggerSlots.values.forEach { it.resetAccessibilityFailure() }
        suspensionBallSlot.resetAccessibilityFailure()
    }

    private fun recoverPanelWindow() {
        val target = resolveAccessibilityWindowTarget(applicationInfo.theme)
        activeAccessibilityTarget = target
        try {
            panelWindowHost.recover(
                highestOverlay = repository.state.value.advancedConfig.highestOverlayLevel,
                accessibilityAvailable = target != null,
                resolveAccessibilityManager = { target?.windowManager }
            )
        } finally {
            activeAccessibilityTarget = null
        }
        applyBlurSettings(updateLayout = false)
        if (panelWindowBinding.hasView && panelComposeView.isAttachedToWindow) {
            runCatching { panelWindowBinding.updateLayout() }
            panelComposeView.requestLayout()
            panelComposeView.invalidate()
        }
        publishOverlayLayerStatus()
    }

    private fun publishOverlayLayerStatus() {
        if (!::repository.isInitialized) return
        repository.updateOverlayLayerStatus(
            collectOverlayLayerStatus(repository.state.value, panelWindowHost, triggerSlots, suspensionBallSlot)
        )
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
            if (panelComposeView.isAttachedToWindow) panelWindowBinding.updateLayout()
        } catch (failure: Exception) {
            panelParams.softInputMode = previousMode
            Log.e(TAG, "Cannot update floating workspace input mode", failure)
        }
    }

    private fun applyBlurSettings(updateLayout: Boolean = true): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !panelWindowBinding.hasParams) return false
        val targetRadius = PanelWindowBlurPolicy.effectiveBlurRadius(
            configuredRadius = repository.state.value.themeConfig.backgroundBlurRadius,
            authorizationPending = windowCoordination.authorizationPending,
            panelVisible = panelWindowBinding.hasView && panelComposeView.visibility == View.VISIBLE,
            panelClosing = windowCoordination.panelClosing,
            openProgress = openProgressState.floatValue
        )
        val previousFlags = panelParams.flags
        val previousRadius = panelParams.blurBehindRadius
        if (targetRadius > 0) {
            panelParams.flags = panelParams.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            panelParams.blurBehindRadius = targetRadius
        } else {
            panelParams.flags = panelParams.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
            panelParams.blurBehindRadius = 0
        }
        val changed = panelParams.flags != previousFlags || panelParams.blurBehindRadius != previousRadius
        if (updateLayout && changed && panelRegistered && panelWindowBinding.hasView && panelComposeView.isAttachedToWindow) {
            try {
                panelWindowBinding.updateLayout()
            } catch (_: Exception) {}
        }
        return changed
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
        syncPanelFocusAndBlur(updateFocus = true, updateBlur = false)
    }

    private fun syncPanelFocusAndBlur(
        updateFocus: Boolean = true,
        updateBlur: Boolean = true,
        forceLayoutUpdate: Boolean = false
    ) {
        if (!panelWindowBinding.hasView) return
        val previousFlags = panelParams.flags
        val supportsBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val previousBlurRadius = if (supportsBlur) panelParams.blurBehindRadius else 0
        val focusChanged = if (updateFocus && !windowCoordination.remoteOwnsInput && !windowCoordination.authorizationPending) {
            val focusFlags = embeddedHostFocusFlags(previousFlags, remoteInput = false)
            if (focusFlags != previousFlags) {
                panelParams.flags = focusFlags
                true
            } else false
        } else false
        val blurChanged = if (updateBlur) applyBlurSettings(updateLayout = false) else false
        if ((focusChanged || blurChanged || forceLayoutUpdate) && panelComposeView.isAttachedToWindow) {
            try {
                panelWindowBinding.updateLayout()
            } catch (failure: Exception) {
                panelParams.flags = previousFlags
                if (updateBlur && supportsBlur) panelParams.blurBehindRadius = previousBlurRadius
                if (focusChanged) {
                    Log.e(TAG, "Cannot restore browser host input window", failure)
                    Toast.makeText(this, "无法激活输入窗口，请关闭浏览窗口后重试", Toast.LENGTH_SHORT).show()
                }
                return
            }
            // Restore window eligibility before a DOWN reaches an editor. Do not
            // steal its Compose/native focus from a later onFocusChanged callback.
            if (focusChanged && !panelComposeView.hasFocus()) panelComposeView.requestFocus()
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
                if (panelComposeView.isAttachedToWindow) panelWindowBinding.updateLayout()
            } catch (failure: Exception) {
                panelParams.flags = previousFlags
                windowCoordination.setRemoteInputOwner(false)
                Log.e(TAG, "Cannot transfer browser input ownership", failure)
                Toast.makeText(this, "无法切换 Chrome 输入窗口，请断开后重试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setExternalAuthorizationPending(pending: Boolean) {
        val initialAlpha = if (panelWindowBinding.hasParams) panelParams.alpha else 1f
        if (!windowCoordination.transitionAuthorization(pending, initialAlpha)) return
        // Detaching the panel during service teardown stops the Surface and calls
        // back here. Never recreate an auxiliary window during teardown.
        if (windowCoordination.destroying) return
        if (::applicationWindows.isInitialized) applicationWindows.setAuthorizationUiHidden(pending)
        if (panelWindowBinding.hasParams) {
            if (pending) {
                panelParams.alpha = 0f
                panelParams.flags = panelParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            } else {
                panelParams.alpha = windowCoordination.authorizationAlpha
                if (panelWindowBinding.hasView && windowCoordination.canRestoreHostFocus(
                        panelComposeView.visibility == View.VISIBLE, openProgressState.floatValue
                    )
                ) {
                    panelParams.flags = panelParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                    if (!windowCoordination.remoteOwnsInput) panelParams.flags = embeddedHostFocusFlags(panelParams.flags, remoteInput = false)
                }
            }
        }
        // Even a small trigger outside the dialog can mark a permission touch as
        // partially obscured. The filter also owns a separate full-screen window.
        // Only the panel must retain its Surface; auxiliary windows may be GONE.
        updateTriggerViewsVisibility()
        updateEyeProtectionWindow()
        if (::systemController.isInitialized) {
            brightnessWindowHost?.apply(systemController.overlayBrightnessOverride.value)
        }
        if (!panelWindowBinding.hasView) return
        if (!pending && !windowCoordination.panelClosing && panelComposeView.visibility == View.VISIBLE) {
            syncPanelFocusAndBlur(forceLayoutUpdate = true)
        } else {
            applyBlurSettings(updateLayout = false)
            if (panelComposeView.isAttachedToWindow) panelWindowBinding.updateLayout()
        }
    }

    private fun ensurePanelGoneAndUnfocused() {
        powerGuard?.onPanelExpandedChanged(false)
        if (!panelWindowBinding.hasView) return
        val targetFlags = panelParams.flags or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val wasVisible = panelComposeView.visibility != View.GONE
        val flagsChanged = panelParams.flags != targetFlags
        panelParams.flags = targetFlags
        panelComposeView.visibility = View.GONE
        val refreshChanged = applyPanelRefreshRateHint(panelParams, active = false, maxRefreshRate = 0f)
        val blurChanged = applyBlurSettings(updateLayout = false)
        if (flagsChanged || wasVisible || blurChanged || refreshChanged) {
            try {
                panelWindowBinding.updateLayout()
            } catch (_: Exception) {}
        }
    }

    private fun preparePanelForDragging() {
        panelMotionController.cancel(resetVelocity = false)
        windowCoordination.beginOpening()
        if (!panelRegistered || !panelWindowBinding.hasView || !panelComposeView.isAttachedToWindow) {
            recoverPanelWindow()
        }
        if (!panelWindowBinding.hasView) return
        if (panelComposeView.visibility != View.VISIBLE) {
            panelComposeView.visibility = View.VISIBLE
        }
        val flags = if (windowCoordination.authorizationPending) panelParams.flags
            else panelParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        val flagsChanged = panelParams.flags != flags
        panelParams.flags = flags
        val refreshChanged = applyPanelRefreshRateHint(panelParams, active = true, maxRefreshRate = resolveDisplayMaxRefreshRate(this))
        val blurChanged = applyBlurSettings(updateLayout = false)
        if (flagsChanged || blurChanged || refreshChanged) {
            try {
                panelWindowBinding.updateLayout()
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

    fun animateOpenPanel(side: TriggerSide? = null, initialVelocity: Float = 0f) {
        if (OverlayVisibilityPolicy.isBlacklisted(repository.state.value, repository.currentForegroundPackage.value)) {
            updateTriggerViewsVisibility()
            return
        }
        val resolvedSide = side ?: resolveTriggerOpenSide(repository.state.value.triggerConfig.triggerPosition)
        activeSideState.value = resolvedSide
        preparePanelForDragging()

        panelMotionController.animateTo(
            target = 1f,
            initialVelocity = initialVelocity,
            animationDurationMs = repository.state.value.themeConfig.resolvedPanelOpenCloseMs(),
            closing = false
        ) {
            syncPanelFocusAndBlur()
            repository.setPanelExpanded(true)
            powerGuard?.onPanelExpandedChanged(true)
            updateTriggerViewsVisibility()
        }
    }

    fun animateClosePanel(initialVelocity: Float = 0f) {
        windowCoordination.beginClosing()
        if (panelWindowBinding.hasParams) {
            panelParams.flags = panelParams.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            applyBlurSettings(updateLayout = false)
            runCatching { panelWindowBinding.updateLayout() }
        }

        val finishClose = {
            ensurePanelGoneAndUnfocused()
            repository.setPanelExpanded(false)
            updateTriggerViewsVisibility()
        }
        val start = panelMotionController.currentProgress
        if (start <= 0.01f && initialVelocity <= 0.05f) {
            panelMotionController.snapTo(0f)
            finishClose()
            return
        }
        panelMotionController.animateTo(
            target = 0f,
            initialVelocity = initialVelocity,
            animationDurationMs = repository.state.value.themeConfig.resolvedPanelOpenCloseMs(),
            closing = true,
            onEnd = finishClose
        )
    }

    private fun openMainApp(openScreen: String? = null) {
        AppAndIntentLauncher.launchMainSettings(this, openScreen)
    }

    private val capturePipeline = OverlayScreenCapturePipeline(
        scope = serviceScope,
        captureSession = captureSession,
        backend = AndroidOverlayCaptureBackend(this),
        settings = object : OverlayCaptureSettings {
            override fun readCaptureConfig(): OverlayCaptureConfig {
                val advConfig = repository.state.value.advancedConfig
                val saConfig = advConfig.screenAssistantConfig
                return OverlayCaptureConfig(saConfig.usePrivilegedCapture, advConfig.rootModeEnabled)
            }
            override fun trackedForegroundPackage(): String =
                repository.currentForegroundPackage.value.takeIf { it.isNotBlank() && it != packageName }.orEmpty()
            override fun allowBreenoPrivilegedShell(): Boolean {
                val state = repository.state.value.advancedConfig
                return state.rootModeEnabled || state.screenAssistantConfig.breenoUsePrivilegedShell
            }
        },
        ui = object : OverlayCaptureUi {
            override val destroying: Boolean get() = windowCoordination.destroying
            override val panelVisible: Boolean
                get() = panelWindowBinding.hasView && panelComposeView.visibility == View.VISIBLE
            override fun dismissCaptureTools() {
                regionScreenshotOverlay?.dismiss()
                screenAssistantOverlay?.dismiss()
            }
            override fun prepareCaptureSurface(): CaptureSurfaceState {
                // Close ONLY the expanded panel immediately (no blur-fade race), keeping floating ball, triggers, and mouse intact.
                val closedPanelNow = closePanelImmediatelyForCapture()
                val orbObstructions = collectSelfFloatingOrbObstructions()
                val eyeProtectionWasVisible = eyeProtectionView?.visibility == View.VISIBLE
                if (eyeProtectionWasVisible) {
                    eyeProtectionView?.visibility = View.GONE
                }
                return CaptureSurfaceState(closedPanelNow, eyeProtectionWasVisible, orbObstructions)
            }
            override fun restoreCaptureSurface() {
                if (repository.state.value.advancedConfig.eyeProtectionEnabled) {
                    eyeProtectionView?.visibility = if (windowCoordination.authorizationPending) View.GONE else View.VISIBLE
                }
                updateTriggerViewsVisibility()
            }
            override fun requestAccessibilityPermission(closePanel: Boolean) {
                if (closePanel) closePanelImmediatelyForCapture()
                systemController.promptAccessibilityPermission()
            }
            override fun animateClosePanel() = this@OverlayService.animateClosePanel()
            override fun showMessage(message: String, long: Boolean) {
                Toast.makeText(this@OverlayService, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
            }
            override fun reportCaptureFailure(failure: Throwable) {
                Log.e(TAG, "Screen capture tool failed", failure)
            }
        }
    )

    /** The panel already runs in this service. Keep its capture requests in the same lifecycle. */
    internal fun requestCapture(action: String): Boolean {
        if (!initialized || windowCoordination.destroying || !foregroundReady ||
            !repository.state.value.triggerConfig.serviceEnabled
        ) {
            Toast.makeText(this, "悬浮服务尚未就绪，请重新打开面板后重试", Toast.LENGTH_SHORT).show()
            return false
        }
        return when (action) {
            ACTION_REGION_SCREENSHOT -> startRegionScreenshot()
            ACTION_SCREEN_ASSISTANT -> startScreenAssistant()
            else -> false
        }
    }

    private fun startRegionScreenshot(): Boolean {
        underlyingScrollJob?.cancel()
        underlyingScrollJob = null
        hideBrowseResumePill()
        return capturePipeline.captureWithEnrichment(
            featureLabel = "区域截屏",
            onEnrichedSnapshot = null
        ) { bitmap, snapshot, enriched ->
            checkNotNull(regionScreenshotOverlay).show(bitmap, ScreenAssistantFrameSnapshot(snapshot, enriched))
        } != null
    }

    private fun openCroppedScreenAssistant(
        bitmap: android.graphics.Bitmap,
        cropRect: android.graphics.Rect,
        mode: ScreenAssistantMode,
        source: ScreenAssistantFrameSnapshot
    ) {
        captureSession.clearSnapshot()
        val shown = screenAssistantOverlay?.show(
            bitmap = bitmap,
            snapshot = source.initial,
            initialMode = mode,
            isCroppedRegion = true,
            snapshotDeferred = source.enriched,
            snapshotCrop = android.graphics.Rect(cropRect).apply {
                source.displayCrop?.let { offset(it.left, it.top) }
            }
        ) == true
        if (!shown) {
            source.enriched?.cancel()
            if (!bitmap.isRecycled) bitmap.recycle()
            Toast.makeText(this, "未能打开全局识屏窗口，请重试", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startScreenAssistant(
        initialMode: ScreenAssistantMode = ScreenAssistantMode.TEXT_SELECT,
        cancelPendingScroll: Boolean = true
    ): Boolean {
        if (cancelPendingScroll) {
            underlyingScrollJob?.cancel()
            underlyingScrollJob = null
        }
        hideBrowseResumePill()
        val advConfig = repository.state.value.advancedConfig
        val ocrCfg = advConfig.screenAssistantConfig
        if (initialMode != ScreenAssistantMode.SUB_IMAGES &&
            (ocrCfg.ocrEngine == com.example.globalminusone.data.ScreenOcrEngine.AUTO ||
                ocrCfg.ocrEngine.isInstallFreeEngine())
        ) {
            com.example.globalminusone.screenassistant.NativeOcrRunner.prewarmAsync(
                context = this,
                config = ocrCfg,
                allowRootAutoConnect = advConfig.rootModeEnabled || ocrCfg.usePrivilegedCapture
            )
        }
        val captureStarted = android.os.SystemClock.elapsedRealtime()
        return capturePipeline.captureWithEnrichment(
            featureLabel = "全局识屏",
            onEnrichedSnapshot = { enriched ->
                captureSession.retainSnapshot(enriched)
            }
        ) { bitmap, snapshot, deferred ->
            val frameDelivered = android.os.SystemClock.elapsedRealtime()
            captureSession.retainSnapshot(snapshot)
            check(screenAssistantOverlay?.show(
                bitmap = bitmap,
                snapshot = snapshot,
                initialMode = initialMode,
                isCroppedRegion = false,
                snapshotDeferred = deferred
            ) == true) { "全局识屏窗口未挂载" }
            Log.d(TAG, "Screen assistant capture=${frameDelivered - captureStarted}ms " +
                "window=${android.os.SystemClock.elapsedRealtime() - frameDelivered}ms")
        } != null
    }

    private fun scrollUnderlyingAndReopenScreenAssistant(
        mode: ScreenAssistantMode,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float
    ) {
        if (!initialized || windowCoordination.destroying) return
        underlyingScrollJob?.cancel()
        underlyingScrollJob = null

        val accService = GlobalAccessibilityService.instance
        val rootAlreadyGranted = com.example.globalminusone.system.privilege.UserRootSession.authorizedEpoch() != null
        val adv = repository.state.value.advancedConfig
        val preferPrivileged = adv.screenAssistantConfig.usePrivilegedCapture || adv.rootModeEnabled
        val canTryRoot = rootAlreadyGranted || (
            preferPrivileged &&
                com.example.globalminusone.system.privilege.UserRootSession.status.value ==
                com.example.globalminusone.system.privilege.RootStatus.NotRequested
            )
        if (accService == null && !canTryRoot) {
            pauseScreenAssistantForManualBrowse(
                savedMode = mode,
                toastMessage = "未开启无障碍手势，已暂隐识屏；滑动原页面后点右上「继续」重新识别"
            )
            return
        }

        captureSession.cancelRegionCapture()
        screenAssistantOverlay?.dismiss()
        hideBrowseResumePill()
        captureSession.clearSnapshot()

        underlyingScrollJob = serviceScope.launch {
            delay(55L)
            var scrolled = false
            if (accService != null) {
                val gate = kotlinx.coroutines.CompletableDeferred<Boolean>()
                accService.dispatchSwipeGesture(startX, startY, endX, endY, durationMs = 280L) { ok ->
                    gate.complete(ok)
                }
                scrolled = withTimeoutOrNull(950L) { gate.await() } == true
            }
            if (!scrolled && canTryRoot) {
                val rootReady = if (com.example.globalminusone.system.privilege.UserRootSession.authorizedEpoch() != null) {
                    true
                } else {
                    runCatching { com.example.globalminusone.system.privilege.UserRootSession.requestAuthorization().succeeded }.getOrDefault(false)
                }
                if (rootReady) {
                    scrolled = com.example.globalminusone.system.privilege.UserRootSession.injectTouchSwipe(
                        x0 = startX.roundToInt(),
                        y0 = startY.roundToInt(),
                        x1 = endX.roundToInt(),
                        y1 = endY.roundToInt(),
                        durationMs = 280
                    )
                }
            }
            if (!scrolled) {
                if (initialized && !windowCoordination.destroying) {
                    showBrowseResumePill(mode)
                    Toast.makeText(
                        this@OverlayService,
                        "底层滑动受限，已暂隐识屏；手动滑动后点右上继续",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@launch
            }
            delay(340L)
            if (initialized && !windowCoordination.destroying) {
                underlyingScrollJob = null
                startScreenAssistant(
                    initialMode = mode,
                    cancelPendingScroll = false
                )
            }
        }
    }

    private fun pauseScreenAssistantForManualBrowse(
        savedMode: ScreenAssistantMode,
        toastMessage: String = "已暂隐识屏，可自由滑动/点击原页面；点右上「继续」重新识屏"
    ) {
        if (!initialized || windowCoordination.destroying) return
        underlyingScrollJob?.cancel()
        underlyingScrollJob = null
        captureSession.cancelRegionCapture()
        screenAssistantOverlay?.dismiss()
        captureSession.clearSnapshot()
        showBrowseResumePill(savedMode)
        Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT).show()
    }

    private fun showBrowseResumePill(savedMode: ScreenAssistantMode) {
        val pill = browseResumePill ?: ScreenAssistantBrowseResumePill(
            context = this,
            windowManager = windowManager,
            screenSize = ::getScreenSize,
            canAttach = { initialized && !windowCoordination.destroying },
            onResume = { mode -> startScreenAssistant(initialMode = mode) }
        ).also { browseResumePill = it }
        pill.show(savedMode)
    }

    private fun hideBrowseResumePill() {
        browseResumePill?.hide()
    }

    private fun startBreenoTouch() {
        underlyingScrollJob?.cancel()
        underlyingScrollJob = null
        hideBrowseResumePill()
        capturePipeline.launchBreeno()
    }

    private fun closePanelImmediatelyForCapture(): Boolean {
        val wasVisible = (panelWindowBinding.hasView && panelComposeView.visibility == View.VISIBLE) ||
            openProgressState.floatValue > 0.01f
        if (!wasVisible) return false
        windowCoordination.beginClosing()
        panelMotionController.snapTo(0f)
        if (panelWindowBinding.hasView) {
            ensurePanelGoneAndUnfocused()
        }
        repository.setPanelExpanded(false)
        updateTriggerViewsVisibility()
        return true
    }

    private fun collectSelfFloatingOrbObstructions(): List<com.example.globalminusone.screenassistant.ExtractedSubImageCandidate> {
        val ball = suspensionBallView ?: return emptyList()
        if (!ball.isAttachedToWindow || ball.visibility != View.VISIBLE || ball.width <= 0 || ball.height <= 0) {
            return emptyList()
        }
        val loc = IntArray(2)
        ball.getLocationOnScreen(loc)
        return listOf(
            com.example.globalminusone.screenassistant.ExtractedSubImageCandidate(
                id = "self_orb_0",
                leftPx = loc[0].coerceAtLeast(0),
                topPx = loc[1].coerceAtLeast(0),
                rightPx = (loc[0] + ball.width).coerceAtLeast(loc[0] + 1),
                bottomPx = (loc[1] + ball.height).coerceAtLeast(loc[1] + 1),
                sourceTag = "本机悬浮球",
                confidence = 1f
            )
        )
    }

    private fun openWidgetPicker(pageId: String, containerId: String?, beforeId: String?, targetGridCell: Pair<Int, Int>? = null) {
        try {
            val intent = Intent(this, WidgetPickerActivity::class.java).apply {
                putExtra("target_page_id", pageId)
                putExtra("container_id", containerId)
                putExtra("before_component_id", beforeId)
                targetGridCell?.let { putExtra("target_grid_col", it.first); putExtra("target_grid_row", it.second) }
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
                val insets = if (panelWindowBinding.hasView) ViewCompat.getRootWindowInsets(panelComposeView)
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
        val layout = calculateEdgeTriggerWindowLayout(
            position, screenWidth, screenHeight, density, config, systemGestureInsets, imeBottomPx
        )
        return WindowManager.LayoutParams(
            layout.width, layout.height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = when (position) {
                TriggerPosition.LEFT, TriggerPosition.RIGHT -> Gravity.LEFT or Gravity.TOP
                TriggerPosition.TOP -> Gravity.START or Gravity.TOP
                TriggerPosition.BOTTOM -> Gravity.START or Gravity.BOTTOM
                TriggerPosition.BOTH -> throw IllegalArgumentException("BOTH is handled as separate LEFT and RIGHT triggers")
            }
            x = layout.x
            y = layout.y
        }
    }

    private fun ensureOrUpdateEdgeTrigger(
        position: TriggerPosition,
        screenWidth: Int,
        screenHeight: Int,
        config: TriggerConfig,
        accessibilityTarget: AccessibilityWindowTarget?
    ) {
        val slot = triggerSlots[position] ?: return
        val params = getTriggerLayoutParams(position, screenWidth, screenHeight, config)
        slot.sync(
            highestOverlay = repository.state.value.advancedConfig.highestOverlayLevel,
            accessibilityTarget = accessibilityTarget,
            params = params,
            createView = { ctx ->
                EdgeTriggerView(ctx, position, edgeTriggerHost).apply {
                    if (windowCoordination.authorizationPending) visibility = View.GONE
                }
            },
            onAfterUpdate = { it.invalidate() },
            onFailure = { Log.e(TAG, "Cannot register EdgeTriggerView for $position", it) }
        )
    }

    private fun removeEdgeTrigger(position: TriggerPosition) {
        triggerSlots[position]?.remove()
    }

    private fun removeAllEdgeTriggers() {
        triggerSlots.values.forEach { it.remove() }
    }

    private fun updateTriggerViews() {
        val config = repository.state.value.triggerConfig
        val (screenWidth, screenHeight) = getScreenSize()

        if (config.triggerWay == TriggerWay.NONE || config.triggerWay == TriggerWay.SUSPENSION_BALL || !config.serviceEnabled) {
            removeAllEdgeTriggers()
            updateTriggerViewsVisibility()
            publishOverlayLayerStatus()
            return
        }

        // SLIDE 或 SECOND_SLIDE
        val accTarget = if (repository.state.value.advancedConfig.highestOverlayLevel) {
            resolveAccessibilityWindowTarget(applicationInfo.theme)
        } else null
        val activePositions = if (config.triggerPosition == TriggerPosition.BOTH) {
            setOf(TriggerPosition.LEFT, TriggerPosition.RIGHT)
        } else {
            setOf(config.triggerPosition)
        }
        triggerSlots.keys.forEach { pos ->
            if (pos in activePositions) ensureOrUpdateEdgeTrigger(pos, screenWidth, screenHeight, config, accTarget)
            else removeEdgeTrigger(pos)
        }

        updateTriggerViewsVisibility()
        publishOverlayLayerStatus()
    }

    // --- 悬浮球视图 (SuspensionBallView) 管理 ---

    private fun removeSuspensionBallWindow() = suspensionBallSlot.remove()

    private fun recreateSuspensionBallAfterMouseFailure() {
        if (windowCoordination.destroying || suspensionBallSlot.view == null) return
        removeSuspensionBallWindow()
        updateSuspensionBallView()
        publishRunningState()
    }

    private fun updateSuspensionBallView() {
        val config = repository.state.value.triggerConfig
        if (config.triggerWay != TriggerWay.SUSPENSION_BALL || !config.serviceEnabled) {
            removeSuspensionBallWindow()
            publishOverlayLayerStatus()
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
        val accTarget = if (repository.state.value.advancedConfig.highestOverlayLevel) {
            resolveAccessibilityWindowTarget(applicationInfo.theme)
        } else null
        val active = suspensionBallSlot.sync(
            highestOverlay = repository.state.value.advancedConfig.highestOverlayLevel,
            accessibilityTarget = accTarget,
            params = params,
            createView = { ctx -> SuspensionBallView(ctx, suspensionBallHost).apply { visibility = View.GONE } },
            onFailure = { Log.e(TAG, "Cannot register suspension ball window", it) }
        )
        active?.let {
            it.updateBreatheMode(sbConfig.breatheMode)
            it.invalidate()
            if (it !== previous) it.scheduleIdleFade()
        }
        updateTriggerViewsVisibility()
        publishOverlayLayerStatus()
    }

    private fun updateTriggerViewsVisibility() {
        val state = repository.state.value
        val config = state.triggerConfig
        val currentPkg = repository.currentForegroundPackage.value
        val isBlacklisted = OverlayVisibilityPolicy.isBlacklisted(state, currentPkg)
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val isExpanded = openProgressState.floatValue > 0f
        val isPanelOpenOrOpening = isExpanded || (panelWindowBinding.hasView && panelComposeView.visibility == View.VISIBLE)
        if ((isBlacklisted || OverlayVisibilityPolicy.shouldAutoClosePanelInLandscape(state, isLandscape, isPanelOpenOrOpening)) &&
            panelWindowBinding.hasView && panelComposeView.visibility == View.VISIBLE && !windowCoordination.panelClosing) {
            animateClosePanel()
        }
        val shouldShow = !windowCoordination.authorizationPending &&
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

    private val edgeTriggerHost = object : EdgeTriggerHost {
        override val triggerConfig: TriggerConfig
            get() = repository.state.value.triggerConfig
        override val panelWidthPercent: Int
            get() = repository.state.value.themeConfig.panelWidthPercent
        override var activeSide: TriggerSide
            get() = activeSideState.value
            set(value) { activeSideState.value = value }
        override fun setOpenProgress(progress: Float) = panelMotionController.setDragProgress(progress)
        override fun getScreenSize(): Pair<Int, Int> = this@OverlayService.getScreenSize()
        override fun resolveTriggerOpenSide(position: TriggerPosition, action: GestureAction?): TriggerSide =
            this@OverlayService.resolveTriggerOpenSide(position, action)
        override fun preparePanelForDragging() = this@OverlayService.preparePanelForDragging()
        override fun animateOpenPanel(side: TriggerSide, initialVelocity: Float) =
            this@OverlayService.animateOpenPanel(side, initialVelocity)
        override fun animateClosePanel(initialVelocity: Float) =
            this@OverlayService.animateClosePanel(initialVelocity)
        override fun performTickHaptic(useSystemVibrate: Boolean) = systemController.performTickHaptic(useSystemVibrate)
        override fun executeGestureAction(action: GestureAction, onOpenPanelSide: (TriggerSide) -> Unit): Boolean =
            systemController.executeGestureAction(action, onOpenPanelSide)
        override fun getTriggerLayoutParams(
            position: TriggerPosition,
            screenWidth: Int,
            screenHeight: Int,
            config: TriggerConfig,
            callbackInsets: WindowInsetsCompat?
        ): WindowManager.LayoutParams =
            this@OverlayService.getTriggerLayoutParams(position, screenWidth, screenHeight, config, callbackInsets)
        override fun updateTriggerLayout(view: View, params: WindowManager.LayoutParams) {
            val edgeView = view as? EdgeTriggerView
            val slot = edgeView?.let { ev -> triggerSlots.values.firstOrNull { it.view === ev } }
            if (slot != null) slot.updateLayout(view, params) else windowManager.updateViewLayout(view, params)
        }
    }

    private val suspensionBallHost = object : SuspensionBallHost {
        override val suspensionBallConfig: SuspensionBallConfig
            get() = repository.state.value.suspensionBallConfig
        override val triggerConfig: TriggerConfig
            get() = repository.state.value.triggerConfig
        override val destroying: Boolean
            get() = windowCoordination.destroying
        override val mouseActive: Boolean?
            get() = accessibilityMouse?.active
        override fun enterMouse(view: View): Boolean? = accessibilityMouse?.enter(view)
        override fun exitMouse() { accessibilityMouse?.exit() }
        override fun onMouseTouch(event: MotionEvent): Boolean? = accessibilityMouse?.onTouch(event)
        override fun launchIdleFade(block: suspend CoroutineScope.() -> Unit): Job = serviceScope.launch(block = block)
        override fun savePosition(xPercent: Float, yPercent: Float) {
            repository.updateSuspensionBallConfig {
                it.copy(posXPercent = xPercent, posYPercent = yPercent)
            }
        }
        override fun getScreenSize(): Pair<Int, Int> = this@OverlayService.getScreenSize()
        override fun performTickHaptic(useSystemVibrate: Boolean) = systemController.performTickHaptic(useSystemVibrate)
        override fun executeGestureAction(action: GestureAction, onOpenPanelSide: (TriggerSide) -> Unit): Boolean =
            systemController.executeGestureAction(action, onOpenPanelSide)
        override fun animateOpenPanel(side: TriggerSide) = this@OverlayService.animateOpenPanel(side)
        override fun updateBallLayout(view: View, params: WindowManager.LayoutParams) =
            suspensionBallSlot.updateLayout(view, params)
    }

    // --- 前台服务常驻通知 ---

    private fun createNotificationChannel() {
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
        const val ACTION_SCREEN_ASSISTANT = "com.example.globalminusone.action.SCREEN_ASSISTANT"
        const val ACTION_BREENO_TOUCH = "com.example.globalminusone.action.BREENO_TOUCH"

        fun start(context: Context, action: String = ACTION_START): Boolean {
            if (action == ACTION_STOP) {
                stop(context)
                return true
            }
            if (!Settings.canDrawOverlays(context) ||
                !MinusOneRepository.getInstance(context).state.value.triggerConfig.serviceEnabled) {
                if (action == ACTION_SCREEN_ASSISTANT || action == ACTION_REGION_SCREENSHOT) {
                    Toast.makeText(context, "请先启用全局负一屏服务并授权悬浮窗", Toast.LENGTH_SHORT).show()
                }
                return false
            }
            val intent = Intent(context, OverlayService::class.java).setAction(action)
            return try {
                ContextCompat.startForegroundService(context, intent)
                true
            } catch (failure: SecurityException) {
                reportStartRejected(context, failure)
                false
            } catch (failure: IllegalStateException) {
                // Includes ForegroundServiceStartNotAllowedException on API31+.
                reportStartRejected(context, failure)
                false
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
