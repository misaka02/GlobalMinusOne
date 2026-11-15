package com.example.globalminusone.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.example.globalminusone.browser.ChromeSurfaceView
import com.example.globalminusone.browser.EmbeddedContentViewport
import com.example.globalminusone.browser.EmbeddedAppTargets
import com.example.globalminusone.browser.EmbeddedWindowRegistry
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.system.UserRootSession
import com.example.globalminusone.ui.panel.EmbeddedAppPanel
import com.example.globalminusone.ui.panel.EmbeddedApplicationTarget
import com.example.globalminusone.ui.panel.FLOATING_WINDOW_BORDER_DP
import com.example.globalminusone.ui.panel.FLOATING_WINDOW_CORNER_DP
import com.example.globalminusone.ui.panel.LocalPanelPalette
import com.example.globalminusone.ui.panel.PanelPalette
import com.example.globalminusone.ui.panel.calculatePanelPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Independent floating application windows (`小窗`) hosted in `WindowManager`:
 * The historical ChromeSurfaceView name is a shared authorized Surface/input
 * bridge for Chrome and ordinary apps; it does not imply a browser-only task.
 * Task ownership and cleanup remain in the existing privileged backend.
 * - Supports multiple simultaneous open `小窗` without force-minimizing sibling windows;
 * - Tracks the single currently operated `activeWindow` so system Back/navigation gestures
 *   (`OnBackInvokedCallback`, `KEYCODE_BACK`, and GlobalMinusOne's gesture Back) target ONLY
 *   the active `小窗`, while touching outside defocuses all `小窗` (`FLAG_NOT_FOCUSABLE`) with
 *   a clear visual outline distinction so system gestures act solely on the outside screen;
 * - Persists user-adjusted window size, position, and aspect-ratio preference across sessions;
 * - Coalesces drag/resize `WindowManager.updateViewLayout` calls per VSYNC frame for smooth motion.
 */
internal class EmbeddedApplicationWindowHost(
    private val context: Context,
    private val repository: MinusOneRepository,
    private val scope: CoroutineScope,
    private val onAuthorizationPending: (Boolean) -> Unit
) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val themed = ContextThemeWrapper(context, android.R.style.Theme_Material_Light_NoActionBar)
    private val density = context.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).roundToInt()
    private val geometryStore = ApplicationWindowGeometryStore(
        context.applicationContext.getSharedPreferences("embedded_app_window_prefs", Context.MODE_PRIVATE)
    )
    private val contentViewportStore = ApplicationContentViewportStore(
        context.applicationContext.getSharedPreferences("embedded_app_window_prefs", Context.MODE_PRIVATE)
    )
    private val windows = EmbeddedWindowRegistry<android.content.ComponentName, ApplicationWindow>()
    private var activeWindow: ApplicationWindow? = null
    private var destroyed = false
    private var authorizationUiHidden = false
    private fun reportAuthorizationPending() {
        onAuthorizationPending(windows.values().any { it.authorizationPending() })
    }

    fun open(requested: EmbeddedApplicationTarget) {
        if (destroyed) return
        val target = requested.copy(component = EmbeddedAppTargets.canonical(requested.component))
        windows[target.component]?.let { existing ->
            existing.restore()
            setActiveWindow(existing)
            return
        }
        val existingVisibleRects = windows.values()
            .filter { it.isVisibleWindow() }
            .map { it.currentWindowRect() }
        val next = windows.add(target.component) { slot ->
            ApplicationWindow(target, slot, existingVisibleRects)
        }
        if (next == null) {
            Toast.makeText(context, "最多同时保留6个应用小窗，请先关闭一个", Toast.LENGTH_LONG).show()
            return
        }
        try {
            next.attach()
            setActiveWindow(next)
        } catch (_: Exception) {
            if (activeWindow === next) activeWindow = null
            windows.remove(target.component, next)
            next.dispose()
            Toast.makeText(context, "无法创建应用小窗，请检查悬浮窗权限", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Routes GlobalMinusOne's own edge/ball Back gesture to the currently focused `小窗`
     * whenever the user is operating inside a `小窗`; returns `false` when unfocused (user touched outside)
     * so Back acts normally on the outside foreground application.
     */
    fun performBackOnActiveWindow(): Boolean {
        val focused = activeWindow ?: return false
        if (!focused.isVisibleWindow()) return false
        return focused.dispatchBackAction()
    }

    private fun setActiveWindow(next: ApplicationWindow?) {
        val validNext = next?.takeIf { !it.isDisposed() && it.isVisibleWindow() }
        val previous = activeWindow
        if (previous === validNext) {
            validNext?.refreshFocusAndOutline()
            return
        }
        activeWindow = validNext
        previous?.refreshFocusAndOutline()
        validNext?.refreshFocusAndOutline()
    }

    private fun isPointInsideAnyOtherVisibleWindow(exclude: ApplicationWindow, rawX: Float, rawY: Float): Boolean {
        return windows.values().any { other ->
            other !== exclude && other.isVisibleWindow() && other.containsScreenPoint(rawX, rawY)
        }
    }

    fun onConfigurationChanged() { windows.values().forEach { it.reconfigure() } }
    fun setAuthorizationUiHidden(hidden: Boolean) {
        if (destroyed || authorizationUiHidden == hidden) return
        authorizationUiHidden = hidden
        if (hidden) setActiveWindow(null)
        windows.values().forEach { it.refreshVisibility() }
    }
    fun close() {
        activeWindow = null
        windows.clear().forEach { it.dispose() }
    }
    fun destroy() { destroyed = true; close() }

    private fun displayBounds(): Rect = if (Build.VERSION.SDK_INT >= 30) {
        val metrics = wm.currentWindowMetrics
        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
        )
        Rect(insets.left, insets.top, metrics.bounds.width() - insets.right, metrics.bounds.height() - insets.bottom)
    } else {
        @Suppress("DEPRECATION")
        val metrics = context.resources.displayMetrics
        Rect(0, dp(24), metrics.widthPixels, metrics.heightPixels - dp(24))
    }

    private data class FullDisplayGestureMetrics(
        val fullWidth: Int,
        val fullHeight: Int,
        val leftGesturePx: Int,
        val rightGesturePx: Int,
        val topSystemPx: Int,
        val bottomGesturePx: Int,
        val imeBottomPx: Int
    )

    private fun currentDisplayGestureMetrics(): FullDisplayGestureMetrics {
        if (Build.VERSION.SDK_INT >= 30) {
            val metrics = wm.currentWindowMetrics
            val bounds = metrics.bounds
            val wi = metrics.windowInsets
            val sysGestures = wi.getInsetsIgnoringVisibility(WindowInsets.Type.systemGestures())
            val sysBars = wi.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            val ime = wi.getInsets(WindowInsets.Type.ime())
            return FullDisplayGestureMetrics(
                fullWidth = bounds.width().coerceAtLeast(1),
                fullHeight = bounds.height().coerceAtLeast(1),
                leftGesturePx = maxOf(dp(36), sysGestures.left, sysBars.left),
                rightGesturePx = maxOf(dp(36), sysGestures.right, sysBars.right),
                topSystemPx = maxOf(dp(36), sysBars.top, sysGestures.top),
                bottomGesturePx = maxOf(dp(40), sysGestures.bottom, sysBars.bottom),
                imeBottomPx = ime.bottom.coerceAtLeast(0)
            )
        }
        @Suppress("DEPRECATION")
        val dm = context.resources.displayMetrics
        return FullDisplayGestureMetrics(
            fullWidth = dm.widthPixels.coerceAtLeast(1),
            fullHeight = dm.heightPixels.coerceAtLeast(1),
            leftGesturePx = dp(36),
            rightGesturePx = dp(36),
            topSystemPx = dp(36),
            bottomGesturePx = dp(40),
            imeBottomPx = 0
        )
    }

    private inner class ApplicationWindow(
        val target: EmbeddedApplicationTarget,
        slot: Int,
        existingVisibleRects: List<ApplicationWindowRect>
    ) {
        private var safe = displayBounds()
        private var userUnlockedAspectRatio = false
        private val loadedSavedState = geometryStore.load(target.component.packageName)
        private val placement = ApplicationWindowPlacement(safe.width(), safe.height(), density).apply {
            if (loadedSavedState != null) {
                userUnlockedAspectRatio = loadedSavedState.userUnlockedAspectRatio
                restoreSavedWindow(loadedSavedState.toRect(safe.width(), safe.height()))
            }
            cascadeIfOverlapping(existingVisibleRects, dp(22))
            moveIcon(iconX, dp(24 + slot * 64))
            restore()
        }
        private var minimized by mutableStateOf(false)
        private var disposed = false
        private var updateFailed = false
        // Keep the view/composition and remote session, but own no WM surface while parked.
        private val rootRegistration = ApplicationWindowRegistration()
        private val iconRegistration = ApplicationWindowRegistration()
        private val rootParked: Boolean get() = !rootRegistration.registered
        private var updatingRoot = false
        private var awaitingAuthorization = false
        private var inputAllowed = true
        private var contentBack: (() -> Boolean)? = null
        private var native: ChromeSurfaceView? = null
        private var contentViewport = contentViewportStore.load(target.component.packageName)
        private var movingContent = false
        private var geometryGestureActive = false
        private var contentHintLabel: TextView? = null
        private var confirmedParams: WindowManager.LayoutParams? = null
        private var confirmedMode = ApplicationWindowMode.WINDOW
        private var confirmedRect = placement.window
        private var iconJob: Job? = null
        private var windowRetry: Job? = null
        private var recoveryAttempts = 0
        private var iconUpdateWarningShown = false
        private var popup: PopupMenu? = null
        private var latestPalette: PanelPalette? = null
        private var frameDrawable: GradientDrawable? = null
        private var borderDrawable: GradientDrawable? = null
        private var columnLayout: LinearLayout? = null
        private var headerLayout: LinearLayout? = null
        private var titleLabel: TextView? = null
        private var resizeHandleView: TextView? = null
        private val chromeButtons = mutableListOf<TextView>()
        private var pendingAnimatedLayoutSync = false
        private val animatedLayoutSync = Runnable {
            pendingAnimatedLayoutSync = false
            if (!disposed && !updateFailed && !isHidden()) {
                applyWindowBounds()
                updateRoot()
            }
        }
        private var lastBackHandledUptimeMs = 0L
        private var backCallbackRegistered = false
        private val predictiveBackCallback: Any? = if (Build.VERSION.SDK_INT >= 33) {
            OnBackInvokedCallback { dispatchBackAction() }
        } else null

        private val owner = OverlayComposeLifecycleOwner()
        private val params = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            windowAnimations = 0
            @Suppress("DEPRECATION")
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }

        private val root = object : FrameLayout(themed) {
            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        if (!isHidden() && activeWindow !== this@ApplicationWindow) {
                            setActiveWindow(this@ApplicationWindow)
                        }
                    }
                    MotionEvent.ACTION_OUTSIDE -> {
                        if (activeWindow === this@ApplicationWindow && !isHidden()) {
                            val gm = currentDisplayGestureMetrics()
                            val hitOther = isPointInsideAnyOtherVisibleWindow(
                                this@ApplicationWindow,
                                ev.rawX,
                                ev.rawY
                            )
                            if (shouldOutsideTouchDefocusWindow(
                                    rawX = ev.rawX,
                                    rawY = ev.rawY,
                                    fullDisplayWidth = gm.fullWidth,
                                    fullDisplayHeight = gm.fullHeight,
                                    leftGestureEdgePx = gm.leftGesturePx,
                                    rightGestureEdgePx = gm.rightGesturePx,
                                    topSystemEdgePx = gm.topSystemPx,
                                    bottomGestureEdgePx = gm.bottomGesturePx,
                                    imeBottomPx = gm.imeBottomPx,
                                    insideAnotherVisibleWindow = hitOther
                                )
                            ) {
                                popup?.dismiss()
                                setActiveWindow(null)
                            }
                        }
                        return false
                    }
                }
                return super.dispatchTouchEvent(ev)
            }

            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && !isHidden() && isFocusedWindow()) {
                    if (event.action == KeyEvent.ACTION_DOWN) return true
                    if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                        dispatchBackAction()
                        return true
                    }
                }
                return dispatchWithCallFallbackGuard(Build.VERSION.SDK_INT, event.keyCode, event.action) {
                    super.dispatchKeyEvent(event)
                }
            }
        }

        private val compose = ComposeView(themed).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            clipChildren = true
            clipToPadding = true
            outlineProvider = ViewOutlineProvider.BOUNDS
            clipToOutline = true
        }
        private val icon = ApplicationIconView()
        private val iconParams = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.LEFT }

        private val rootAuthorizer = ChromeSurfaceView.RootAuthorizer { done ->
            scope.launch {
                if (disposed) { done.accept(false); return@launch }
                val granted = UserRootSession.authorizedEpoch() != null ||
                    runCatching { UserRootSession.requestAuthorization().succeeded }.getOrDefault(false)
                done.accept(!disposed && granted)
            }
        }

        fun isDisposed(): Boolean = disposed
        private fun isHidden(): Boolean = minimized || awaitingAuthorization || authorizationUiHidden
        fun isVisibleWindow(): Boolean = !disposed && !updateFailed && !isHidden()
        private fun isFocusedWindow(): Boolean = activeWindow === this && isVisibleWindow()
        fun currentWindowRect(): ApplicationWindowRect = placement.window

        fun containsScreenPoint(rawX: Float, rawY: Float): Boolean {
            if (!isVisibleWindow()) return false
            val left = safe.left + placement.window.x
            val top = safe.top + placement.window.y
            val right = left + placement.window.width
            val bottom = top + placement.window.height
            return rawX >= left && rawX <= right && rawY >= top && rawY <= bottom
        }

        fun dispatchBackAction(): Boolean {
            if (disposed || isHidden()) return false
            val now = SystemClock.uptimeMillis()
            if (now - lastBackHandledUptimeMs < 65L) return true
            lastBackHandledUptimeMs = now
            if (movingContent) {
                setMovingContent(false)
                return true
            }
            if (contentBack?.invoke() != true) minimize()
            return true
        }

        private fun saveCurrentGeometry() {
            if (disposed || safe.width() <= 0 || safe.height() <= 0) return
            geometryStore.save(
                target.component.packageName,
                SavedApplicationWindowState.fromRect(
                    rect = placement.window,
                    screenWidth = safe.width(),
                    screenHeight = safe.height(),
                    userUnlockedAspectRatio = userUnlockedAspectRatio
                )
            )
        }

        private fun fixedChromeWidthPx(): Int = dp(FLOATING_WINDOW_BORDER_DP) * 2
        private fun fixedChromeHeightPx(): Int = dp(FLOATING_WINDOW_BORDER_DP) * 2 + dp(HEADER_HEIGHT_DP) + dp(RESIZE_BAR_HEIGHT_DP)

        private fun syncLockedCanvasAspectRatio() {
            if (disposed || updateFailed) return
            val view = native ?: return
            val (canvasW, canvasH) = (if (userUnlockedAspectRatio) view.canvasSize()
                else view.fullPhoneCanvasSize() ?: view.canvasSize()) ?: return
            val liveChromeW = if (view.width > 0) (placement.window.width - view.width).coerceAtLeast(0) else fixedChromeWidthPx()
            val liveChromeH = if (view.height > 0) (placement.window.height - view.height).coerceAtLeast(0) else fixedChromeHeightPx()
            if (userUnlockedAspectRatio) {
                placement.updateChromeInsets(liveChromeW, liveChromeH, snapWindowIfLocked = false)
                return
            }
            val before = placement.window
            placement.lockContentAspectRatio(canvasW, canvasH, liveChromeW, liveChromeH, snapWindow = false)
            if (!minimized && placement.window != before) {
                applyWindowBounds()
                updateRoot()
                saveCurrentGeometry()
            }
        }

        private fun syncPredictiveBackRegistration() {
            if (Build.VERSION.SDK_INT < 33) return
            val cb = predictiveBackCallback as? OnBackInvokedCallback ?: return
            val shouldRegister = !disposed && isFocusedWindow() && root.isAttachedToWindow
            if (shouldRegister == backCallbackRegistered) return
            val dispatcher = root.findOnBackInvokedDispatcher() ?: return
            runCatching {
                if (shouldRegister) {
                    dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb)
                    backCallbackRegistered = true
                } else {
                    dispatcher.unregisterOnBackInvokedCallback(cb)
                    backCallbackRegistered = false
                }
            }
        }

        private fun updateVisualChrome() {
            val palette = latestPalette
            val isDark = palette?.isDark == true
            val focused = isFocusedWindow()
            val background = if (isDark) Color.rgb(28, 30, 36) else Color.rgb(247, 248, 250)
            val headerBg = when {
                isDark && focused -> Color.rgb(40, 46, 58)
                isDark -> Color.rgb(32, 34, 40)
                focused -> Color.rgb(230, 237, 248)
                else -> Color.rgb(241, 243, 246)
            }
            val textColor = when {
                isDark && focused -> Color.rgb(240, 244, 252)
                isDark -> Color.argb(176, 210, 216, 226)
                focused -> Color.rgb(22, 26, 34)
                else -> Color.argb(176, 68, 76, 88)
            }
            val strokePx = if (focused) {
                (2.4f * density).roundToInt().coerceAtLeast(2)
            } else {
                (1.2f * density).roundToInt().coerceAtLeast(1)
            }
            val strokeColor = if (focused) {
                palette?.accent?.copy(alpha = 0.95f)?.toArgb() ?: Color.rgb(59, 130, 246)
            } else {
                if (isDark) Color.argb(115, 132, 142, 158) else Color.argb(125, 152, 162, 178)
            }
            frameDrawable?.setColor(background)
            borderDrawable?.setStroke(strokePx, strokeColor)
            columnLayout?.setBackgroundColor(background)
            headerLayout?.setBackgroundColor(headerBg)
            titleLabel?.text = if (focused) "● ${target.label}" else target.label
            chromeButtons.forEach { it.setTextColor(textColor) }
            resizeHandleView?.setTextColor(
                if (focused) strokeColor else (if (isDark) Color.rgb(122, 132, 148) else Color.rgb(138, 148, 162))
            )
            root.elevation = if (isHidden()) 0f else dp(if (focused) 12 else 3).toFloat()
        }

        fun refreshFocusAndOutline() {
            if (disposed || updateFailed) return
            updateVisualChrome()
            applyWindowFlags()
            syncPredictiveBackRegistration()
        }

        fun attach() {
            val bg = Color.rgb(247, 248, 250)
            val fg = Color.rgb(27, 30, 35)
            val frame = GradientDrawable().apply {
                cornerRadius = dp(FLOATING_WINDOW_CORNER_DP).toFloat()
                setColor(bg)
            }
            val border = GradientDrawable().apply {
                cornerRadius = dp(FLOATING_WINDOW_CORNER_DP).toFloat()
                setColor(Color.TRANSPARENT)
                setStroke((2.4f * density).roundToInt().coerceAtLeast(2), Color.rgb(59, 130, 246))
            }
            frameDrawable = frame
            borderDrawable = border
            root.background = frame
            root.foreground = border
            root.outlineProvider = ViewOutlineProvider.BACKGROUND
            root.clipToOutline = true
            root.clipChildren = true
            root.setPadding(
                dp(FLOATING_WINDOW_BORDER_DP),
                dp(FLOATING_WINDOW_BORDER_DP),
                dp(FLOATING_WINDOW_BORDER_DP),
                dp(FLOATING_WINDOW_BORDER_DP)
            )
            val column = LinearLayout(themed).apply {
                orientation = LinearLayout.VERTICAL
                clipChildren = true
                clipToPadding = true
                outlineProvider = ViewOutlineProvider.BOUNDS
                clipToOutline = true
                setBackgroundColor(bg)
            }
            columnLayout = column
            val header = LinearLayout(themed).apply {
                gravity = Gravity.CENTER_VERTICAL
                clipChildren = true
                clipToPadding = true
                setBackgroundColor(Color.rgb(230, 237, 248))
            }
            headerLayout = header

            fun addHeaderButton(text: String, fontSp: Float, widthDp: Int, description: String, click: (View) -> Unit): TextView {
                val button = TextView(themed).apply {
                    this.text = text
                    textSize = fontSp
                    gravity = Gravity.CENTER
                    setTextColor(fg)
                    contentDescription = description
                    isFocusable = false
                    setBackgroundResource(android.R.drawable.list_selector_background)
                    setOnClickListener {
                        setActiveWindow(this@ApplicationWindow)
                        click(it)
                    }
                }
                chromeButtons.add(button)
                header.addView(button, LinearLayout.LayoutParams(dp(widthDp), dp(HEADER_HEIGHT_DP)))
                return button
            }

            addHeaderButton("←", 17f, 34, "小窗内返回") { dispatchBackAction() }

            val label = TextView(themed).apply {
                text = "● ${target.label}"
                setTextColor(fg)
                textSize = 13f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(2), 0, dp(2), 0)
                contentDescription = "拖动${target.label}小窗"
            }
            titleLabel = label
            chromeButtons.add(label)
            header.addView(label, LinearLayout.LayoutParams(0, dp(HEADER_HEIGHT_DP), 1f))
            installDrag(label, resize = false)

            addHeaderButton("−", 19f, 36, "缩成悬浮图标") { minimize() }
            addHeaderButton("⋮", 17f, 34, "小窗操作") { showMenu(it) }
            addHeaderButton("×", 19f, 36, "关闭${target.label}小窗") { closeThis() }

            column.addView(header, LinearLayout.LayoutParams(-1, dp(HEADER_HEIGHT_DP)))
            column.addView(compose, LinearLayout.LayoutParams(-1, 0, 1f))

            val footerBar = FrameLayout(themed)
            val hintLabel = TextView(themed).apply {
                text = "按住右下角缩放"
                textSize = 10.5f
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setPadding(dp(10), 0, dp(4), 0)
                setTextColor(Color.argb(120, 110, 120, 135))
            }
            contentHintLabel = hintLabel
            hintLabel.maxLines = 1
            hintLabel.ellipsize = android.text.TextUtils.TruncateAt.END
            hintLabel.setOnClickListener { if (movingContent) setMovingContent(false) }
            installDrag(hintLabel, resize = false, enabled = { !movingContent })
            updateContentHint()
            val resize = TextView(themed).apply {
                text = "◢"
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                textSize = 15f
                setTextColor(Color.rgb(59, 130, 246))
                setPadding(dp(16), 0, dp(10), 0)
                contentDescription = "拖动调整小窗大小"
            }
            resizeHandleView = resize
            installDrag(resize, resize = true)
            footerBar.addView(hintLabel, FrameLayout.LayoutParams(-1, -1, Gravity.START or Gravity.CENTER_VERTICAL).apply {
                rightMargin = dp(72)
            })
            footerBar.addView(resize, FrameLayout.LayoutParams(dp(72), -1, Gravity.END or Gravity.CENTER_VERTICAL))
            column.addView(footerBar, LinearLayout.LayoutParams(-1, dp(RESIZE_BAR_HEIGHT_DP)))

            root.addView(column, FrameLayout.LayoutParams(-1, -1))
            root.isFocusable = true
            root.isFocusableInTouchMode = true
            owner.onCreate()
            owner.attachToView(root)
            owner.attachToView(compose)
            owner.onStart()
            owner.onResume()

            compose.setContent {
                val state by repository.state.collectAsState()
                val palette = calculatePanelPalette(state.themeConfig, state.appearanceConfig)
                SideEffect {
                    latestPalette = palette
                    updateVisualChrome()
                }
                CompositionLocalProvider(LocalPanelPalette provides palette) {
                    MaterialTheme(colorScheme = if (palette.isDark) darkColorScheme() else lightColorScheme()) {
                        EmbeddedAppPanel(
                            component = target.component,
                            minimized = minimized,
                            showBottomBackBar = false,
                            rootAuthorizer = rootAuthorizer,
                            onNativeViewChanged = { view ->
                                native?.setContentViewportListener(null)
                                native?.setContentPanModeListener(null)
                                native?.setCanvasSizeListener(null)
                                native = view
                                view?.setHostGeometryChanging(geometryGestureActive)
                                view?.setContentReflowEnabled(userUnlockedAspectRatio)
                                view?.setCanvasSizeListener { syncLockedCanvasAspectRatio() }
                                view?.setContentViewport(contentViewport)
                                view?.setContentViewportListener { changed ->
                                    contentViewport = changed
                                    contentViewportStore.save(target.component.packageName, changed)
                                    updateContentHint()
                                }
                                view?.setContentPanModeListener { moving ->
                                    movingContent = moving
                                    updateContentHint()
                                }
                                view?.setHostPresentationVisible(!isHidden() && !rootParked)
                            },
                            onConnectionChanged = { connected ->
                                if (connected) {
                                    syncLockedCanvasAspectRatio()
                                }
                                // A backend error must not undo the user's minimized/docked mode.
                                // Keep the icon; restore explicitly to inspect/retry the connection.
                            },
                            onHostInputAllowed = {
                                inputAllowed = it
                                applyWindowFlags()
                            },
                            onExternalAuthorization = { pending ->
                                if (!disposed && awaitingAuthorization != pending) {
                                    awaitingAuthorization = pending
                                    applyWindowFlags()
                                    reportAuthorizationPending()
                                }
                            },
                            onBackHandlerChanged = { contentBack = it }
                        )
                    }
                }
            }
            applyWindowBounds()
            updateRoot()
            updateVisualChrome()
            applyWindowFlags()
            syncPredictiveBackRegistration()
            iconJob = scope.launch {
                val drawable = withContext(Dispatchers.IO) {
                    runCatching { context.packageManager.getActivityIcon(target.component) }.getOrNull()
                }
                if (!disposed) {
                    icon.drawable = drawable
                    icon.invalidate()
                }
            }
        }

        private fun scheduleCoalescedLayoutSync() {
            if (isHidden()) return
            applyWindowBounds()
            if (pendingAnimatedLayoutSync || disposed || updateFailed) return
            pendingAnimatedLayoutSync = true
            root.postOnAnimation(animatedLayoutSync)
        }

        private fun cancelCoalescedLayoutSync() {
            root.removeCallbacks(animatedLayoutSync)
            pendingAnimatedLayoutSync = false
        }

        private fun flushCoalescedLayoutSync() {
            cancelCoalescedLayoutSync()
            applyWindowBounds()
            updateRoot()
        }

        private fun installDrag(view: View, resize: Boolean, enabled: () -> Boolean = { true }) {
            var originX = 0f
            var originY = 0f
            var start: ApplicationWindowRect? = null
            view.setOnTouchListener { _, event ->
                if (!enabled()) return@setOnTouchListener false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        setActiveWindow(this)
                        popup?.dismiss()
                        recoveryAttempts = 0
                        originX = event.rawX
                        originY = event.rawY
                        if (resize) {
                            val live = native
                            if (live != null && live.width > 0 && live.height > 0) {
                                placement.updateChromeInsets(
                                    (placement.window.width - live.width).coerceAtLeast(0),
                                    (placement.window.height - live.height).coerceAtLeast(0),
                                    snapWindowIfLocked = false
                                )
                            } else {
                                placement.updateChromeInsets(fixedChromeWidthPx(), fixedChromeHeightPx(), snapWindowIfLocked = false)
                            }
                        }
                        start = placement.window
                        geometryGestureActive = true
                        native?.setHostGeometryChanging(true)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        start?.let { s ->
                            val dx = (event.rawX - originX).roundToInt()
                            val dy = (event.rawY - originY).roundToInt()
                            if (resize) placement.resizeWindow(s, dx, dy) else placement.moveWindow(s, dx, dy)
                            scheduleCoalescedLayoutSync()
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (start != null) {
                            start = null
                            flushCoalescedLayoutSync()
                            saveCurrentGeometry()
                            geometryGestureActive = false
                            native?.setHostGeometryChanging(false)
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        start?.let {
                            placement.moveWindow(it, 0, 0)
                            flushCoalescedLayoutSync()
                        }
                        start = null
                        geometryGestureActive = false
                        native?.setHostGeometryChanging(false)
                        true
                    }
                    else -> true
                }
            }
        }

        private fun applyWindowBounds() {
            val rect = placement.window
            params.x = safe.left + rect.x
            params.y = safe.top + rect.y
            // A parked window has no WindowManager surface. Retain its measured geometry
            // instead of a zero-sized layout that can invalidate Compose/Texture output.
            params.width = rect.width
            params.height = rect.height
        }

        private fun updateRoot() {
            if (disposed || updateFailed || updatingRoot) return
            updatingRoot = true
            try {
                if (isHidden()) {
                    // GONE/alpha/0-size alone leave an OEM WindowState/Surface behind.
                    // The explicit lifecycle composition strategy keeps the same native
                    // view and app task alive across this temporary detach.
                    if (rootRegistration.registered) {
                        // Unregister against the old ViewRoot dispatcher before detach.
                        syncPredictiveBackRegistration()
                        rootRegistration.hide { wm.removeViewImmediate(root) }
                    }
                    rememberAppliedWindow()
                    return
                }
                val wasParked = rootParked
                if (wasParked) {
                    root.visibility = View.VISIBLE
                    root.alpha = 1f
                    columnLayout?.visibility = View.VISIBLE
                }
                rootRegistration.show(
                    add = { wm.addView(root, params) },
                    update = { wm.updateViewLayout(root, params) }
                )
                if (wasParked) {
                    native?.setHostPresentationVisible(true)
                    syncPredictiveBackRegistration()
                }
                rememberAppliedWindow()
                windowRetry?.cancel()
                windowRetry = null
                recoveryAttempts = 0
            } catch (_: Exception) {
                // A failed hide/reattach must not roll minimized/authorization state
                // back to WINDOW and accidentally revive the full application.
                if (isHidden() || rootParked) {
                    native?.setHostPresentationVisible(false)
                    root.alpha = 0f
                    root.visibility = View.GONE
                    if (isHidden()) {
                        // Hide at the WM level as well while a rejected removal
                        // retries; a View's GONE/alpha alone can leave a last frame.
                        params.alpha = 0f
                        params.flags = applicationWindowFocusFlags(params.flags, true, false, false)
                        runCatching { rootRegistration.updateIfRegistered { wm.updateViewLayout(root, params) } }
                    }
                    scheduleWindowRecovery()
                    return
                }
                val previous = confirmedParams
                val hostPermissionValid = android.provider.Settings.canDrawOverlays(context)
                if (previous != null && hostPermissionValid) {
                    params.copyFrom(previous)
                    val restored = runCatching { wm.updateViewLayout(root, params) }.isSuccess
                    if (restored) {
                        placement.restore()
                        placement.moveWindow(confirmedRect, 0, 0)
                        when (confirmedMode) {
                            ApplicationWindowMode.LEFT_EDGE -> placement.dock(true)
                            ApplicationWindowMode.RIGHT_EDGE -> placement.dock(false)
                            ApplicationWindowMode.ICON -> placement.minimize()
                            else -> Unit
                        }
                        minimized = confirmedMode != ApplicationWindowMode.WINDOW
                        native?.setHostPresentationVisible(!isHidden() && !rootParked)
                        if (recoveryAttempts == 0) {
                            Toast.makeText(context, "窗口更新未完成，已保留应用并恢复原尺寸", Toast.LENGTH_SHORT).show()
                        }
                        return
                    }
                }
                if (hostPermissionValid) {
                    placement.restore()
                    placement.moveWindow(confirmedRect, 0, 0)
                    when (confirmedMode) {
                        ApplicationWindowMode.LEFT_EDGE -> placement.dock(true)
                        ApplicationWindowMode.RIGHT_EDGE -> placement.dock(false)
                        ApplicationWindowMode.ICON -> placement.minimize()
                        else -> Unit
                    }
                    minimized = confirmedMode != ApplicationWindowMode.WINDOW
                    native?.setHostPresentationVisible(!isHidden() && !rootParked)
                    if (minimized) updateIcon()
                    if (recoveryAttempts == 0) {
                        Toast.makeText(context, "窗口更新暂未完成，应用会话仍保留", Toast.LENGTH_SHORT).show()
                    }
                    scheduleWindowRecovery()
                    return
                }
                updateFailed = true
                root.visibility = View.GONE
                native?.stop()
                removeIcon()
                scope.launch {
                    closeThis()
                    Toast.makeText(context, "小窗状态更新失败，已关闭本次窗口", Toast.LENGTH_SHORT).show()
                }
            } finally {
                updatingRoot = false
            }
        }

        private fun scheduleWindowRecovery() {
            if (disposed || updateFailed || windowRetry != null || recoveryAttempts >= 3) return
            val attempt = ++recoveryAttempts
            windowRetry = scope.launch {
                delay(250L * attempt)
                windowRetry = null
                updateRoot()
            }
        }

        private fun rememberAppliedWindow() {
            confirmedParams = WindowManager.LayoutParams().apply { copyFrom(params) }
            confirmedMode = placement.mode
            confirmedRect = placement.window
        }

        private fun applyWindowFlags() {
            if (disposed || updateFailed) return
            val hidden = isHidden()
            if (hidden) {
                cancelCoalescedLayoutSync()
                geometryGestureActive = false
                native?.setHostGeometryChanging(false)
                setMovingContent(false)
            }
            val focused = !hidden && activeWindow === this
            // Host visibility is independent from Compose's requested suspension. A
            // delayed AndroidView update cannot reattach output to a parked window.
            native?.setHostPresentationVisible(!hidden && !rootParked)
            applyWindowBounds()
            params.alpha = if (hidden) 0f else 1f
            params.flags = applicationWindowFocusFlags(
                current = params.flags,
                hidden = hidden,
                remoteInput = !inputAllowed,
                windowFocused = focused
            )
            if (hidden) {
                root.alpha = 0f
                root.elevation = 0f
                columnLayout?.visibility = View.GONE
                if (root.visibility != View.GONE) root.visibility = View.GONE
            } else {
                columnLayout?.visibility = View.VISIBLE
                if (root.visibility != View.VISIBLE) root.visibility = View.VISIBLE
                root.alpha = 1f
                root.elevation = dp(if (focused) 12 else 3).toFloat()
            }
            root.importantForAccessibility = if (hidden) {
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            } else {
                View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            }
            updateRoot()
            syncPredictiveBackRegistration()
            if (focused && !root.hasFocus()) {
                root.post { if (!disposed && isFocusedWindow() && !root.hasFocus()) root.requestFocus() }
            }
            if (awaitingAuthorization || authorizationUiHidden) removeIcon() else if (minimized) updateIcon()
        }

        fun minimize() {
            if (disposed || updateFailed || awaitingAuthorization || authorizationUiHidden) return
            saveCurrentGeometry()
            popup?.dismiss()
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(root.windowToken, 0)
            placement.minimize()
            minimized = true
            native?.setHostPresentationVisible(false)
            if (activeWindow === this) {
                val fallback = windows.values().lastOrNull { it !== this && it.isVisibleWindow() }
                setActiveWindow(fallback)
            }
            root.clearFocus()
            updateVisualChrome()
            applyWindowFlags()
        }

        fun restore() {
            if (disposed || updateFailed || awaitingAuthorization || authorizationUiHidden) return
            recoveryAttempts = 0
            popup?.dismiss()
            placement.restore()
            removeIcon()
            minimized = false
            applyWindowBounds()
            setActiveWindow(this)
            updateVisualChrome()
            applyWindowFlags()
            native?.setHostPresentationVisible(!isHidden() && !rootParked)
            root.post {
                if (!disposed && !updateFailed && !isHidden()) {
                    native?.setHostPresentationVisible(!isHidden() && !rootParked)
                }
            }
        }

        private fun dock(left: Boolean) {
            if (disposed || updateFailed || awaitingAuthorization || authorizationUiHidden) return
            saveCurrentGeometry()
            popup?.dismiss()
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(root.windowToken, 0)
            placement.dock(left)
            minimized = true
            native?.setHostPresentationVisible(false)
            if (activeWindow === this) {
                val fallback = windows.values().lastOrNull { it !== this && it.isVisibleWindow() }
                setActiveWindow(fallback)
            }
            root.clearFocus()
            updateVisualChrome()
            applyWindowFlags()
        }

        private fun closeThis() {
            saveCurrentGeometry()
            if (windows.remove(target.component, this)) {
                if (activeWindow === this) {
                    val fallback = windows.values().lastOrNull { it.isVisibleWindow() }
                    setActiveWindow(fallback)
                }
                dispose()
            }
        }

        private fun updateContentHint() {
            contentHintLabel?.text = if (movingContent) "退出移动"
                else "内容${if (contentViewport.zoom == 1f) "适配" else "${(contentViewport.zoom * 100).roundToInt()}%"} · 右下角缩放"
            contentHintLabel?.contentDescription = if (movingContent) "正在移动画面，拖动调整位置，点按返回应用操作" else "右下角调整窗口大小，内容显示选项在小窗菜单"
        }

        private fun setMovingContent(moving: Boolean) {
            val allowed = moving && contentViewport.zoom > 1f && !isHidden()
            movingContent = allowed
            native?.setContentPanMode(allowed)
            updateContentHint()
        }

        private fun setContentZoom(zoom: Float) {
            setMovingContent(false)
            contentViewport = if (zoom == 1f) EmbeddedContentViewport()
                else contentViewport.copy(zoom = zoom).sanitized()
            native?.setContentViewport(contentViewport)
            contentViewportStore.save(target.component.packageName, contentViewport)
            updateContentHint()
        }

        private fun showMenu(anchor: View) {
            popup?.dismiss()
            if (minimized && iconRegistration.registered) {
                iconParams.flags = iconParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                runCatching { wm.updateViewLayout(icon, iconParams) }
                icon.isFocusableInTouchMode = true
                icon.requestFocus()
            }
            popup = PopupMenu(themed, anchor).apply {
                setOnDismissListener {
                    iconParams.flags = iconParams.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    if (iconRegistration.registered) runCatching { wm.updateViewLayout(icon, iconParams) }
                    icon.clearFocus()
                }
                menu.add(if (minimized) "还原小窗" else "缩成悬浮图标").setOnMenuItemClickListener {
                    if (minimized) restore() else minimize()
                    true
                }
                if (!minimized) {
                    val display = menu.addSubMenu("内容显示")
                    listOf(1f, 1.25f, 1.5f, 2f).forEach { zoom ->
                        display.add(if (zoom == 1f) "完整适配" else "放大至${(zoom * 100).roundToInt()}%（可移动）").apply {
                            isCheckable = true
                            isChecked = contentViewport.zoom == zoom
                            setOnMenuItemClickListener { setContentZoom(zoom); true }
                        }
                    }
                    display.add(if (movingContent) "返回应用操作" else "移动画面").apply {
                        isEnabled = contentViewport.zoom > 1f
                        setOnMenuItemClickListener { setMovingContent(!movingContent); true }
                    }
                }
                if (!minimized && (placement.hasContentAspectRatio || native?.canvasSize() != null)) {
                    menu.add(if (placement.aspectRatioLocked) "自由比例（应用重排）" else "锁定比例（完整手机画布）").setOnMenuItemClickListener {
                        val lockNow = !placement.aspectRatioLocked
                        userUnlockedAspectRatio = !lockNow
                        val view = native
                        if (lockNow && view != null) {
                            val fullCanvas = view.fullPhoneCanvasSize() ?: view.canvasSize()
                            if (fullCanvas != null) {
                                placement.lockContentAspectRatio(fullCanvas.first, fullCanvas.second,
                                    if (view.width > 0) (placement.window.width - view.width).coerceAtLeast(0) else fixedChromeWidthPx(),
                                    if (view.height > 0) (placement.window.height - view.height).coerceAtLeast(0) else fixedChromeHeightPx(),
                                    snapWindow = false)
                            }
                        }
                        placement.setAspectRatioLocked(lockNow, snapWindow = lockNow)
                        native?.setContentReflowEnabled(!lockNow)
                        if (lockNow) {
                            applyWindowBounds()
                            updateRoot()
                        }
                        saveCurrentGeometry()
                        true
                    }
                }
                if (minimized && placement.mode != ApplicationWindowMode.ICON) {
                    menu.add("自由悬浮").setOnMenuItemClickListener {
                        placement.floatIcon()
                        updateIcon()
                        true
                    }
                }
                menu.add("贴到左侧").setOnMenuItemClickListener { dock(true); true }
                menu.add("贴到右侧").setOnMenuItemClickListener { dock(false); true }
                val closeTitle = android.text.SpannableString("关闭小窗").apply {
                    setSpan(
                        android.text.style.ForegroundColorSpan(Color.rgb(220, 53, 69)),
                        0,
                        length,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                menu.add(closeTitle).setOnMenuItemClickListener { closeThis(); true }
                show()
            }
        }

        private fun updateIcon() {
            if (disposed || updateFailed || awaitingAuthorization || authorizationUiHidden || !minimized) return
            val bounds = placement.iconBounds()
            iconParams.x = safe.left + bounds.x
            iconParams.y = safe.top + bounds.y
            iconParams.width = bounds.width
            iconParams.height = bounds.height
            icon.contentDescription = "${target.label}小窗，点按还原，长按管理，拖到边缘贴边"
            icon.alpha = 1f
            icon.visibility = View.VISIBLE
            try {
                iconRegistration.show(
                    add = { wm.addView(icon, iconParams) },
                    update = { wm.updateViewLayout(icon, iconParams) }
                )
                icon.invalidate()
                iconUpdateWarningShown = false
            } catch (_: Exception) {
                if (iconRegistration.registered) {
                    // An existing icon may still be visible at its last accepted
                    // coordinates. An update error is never a request to restore.
                    if (!iconUpdateWarningShown) {
                        iconUpdateWarningShown = true
                        Toast.makeText(context, "图标位置暂未更新，保留原收纳状态", Toast.LENGTH_SHORT).show()
                    }
                    return
                }
                // A first add can be rejected after an OEM has partly registered
                // the View. Confirm its removal before exposing the large host.
                val iconCleared = try {
                    wm.removeViewImmediate(icon)
                    true
                } catch (_: IllegalArgumentException) {
                    // WindowManager has no registration for this exact View.
                    icon.parent == null
                } catch (_: Exception) { false }
                if (!iconCleared) {
                    icon.alpha = 0f
                    icon.visibility = View.GONE
                    Toast.makeText(context, "悬浮图标暂不可用，应用会话与收纳状态仍保留", Toast.LENGTH_SHORT).show()
                    return
                }
                icon.alpha = 1f
                icon.visibility = View.VISIBLE
                placement.restore()
                minimized = false
                setActiveWindow(this)
                applyWindowFlags()
                native?.setHostPresentationVisible(!isHidden() && !rootParked)
                Toast.makeText(context, "悬浮图标无法显示，已还原小窗", Toast.LENGTH_SHORT).show()
            }
        }

        private fun removeIcon() {
            runCatching { iconRegistration.hide { wm.removeViewImmediate(icon) } }
        }

        fun reconfigure() {
            if (disposed) return
            recoveryAttempts = 0
            popup?.dismiss()
            safe = displayBounds()
            placement.reconfigure(safe.width(), safe.height())
            applyWindowBounds()
            updateRoot()
            if (minimized) updateIcon()
        }

        fun dispose() {
            if (disposed) return
            if (Build.VERSION.SDK_INT >= 33 && backCallbackRegistered) {
                val cb = predictiveBackCallback as? OnBackInvokedCallback
                if (cb != null) {
                    runCatching { root.findOnBackInvokedDispatcher()?.unregisterOnBackInvokedCallback(cb) }
                }
                backCallbackRegistered = false
            }
            disposed = true
            cancelCoalescedLayoutSync()
            placement.close()
            popup?.dismiss()
            popup = null
            iconJob?.cancel()
            windowRetry?.cancel()
            windowRetry = null
            if (awaitingAuthorization) {
                awaitingAuthorization = false
                reportAuthorizationPending()
            }
            removeIcon()
            native?.release()
            native?.setContentViewportListener(null)
            native?.setContentPanModeListener(null)
            native?.setCanvasSizeListener(null)
            runCatching { compose.disposeComposition() }
            runCatching { rootRegistration.hide { wm.removeViewImmediate(root) } }
            owner.onPause()
            owner.onStop()
            owner.onDestroy()
            contentBack = null
            native = null
            icon.drawable = null
        }

        fun authorizationPending() = awaitingAuthorization && !disposed
        fun refreshVisibility() {
            updateVisualChrome()
            applyWindowFlags()
        }

        private inner class ApplicationIconView : View(themed) {
            var drawable: Drawable? = null
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            private var downX = 0f
            private var downY = 0f
            private var startX = 0
            private var startY = 0
            private var dragged = false
            private var longPressed = false
            private val slop = ViewConfiguration.get(context).scaledTouchSlop
            private val longPress = Runnable { if (!dragged) { longPressed = true; performLongClick() } }

            init {
                isClickable = true
                isFocusable = true
                setOnClickListener { restore() }
                setOnLongClickListener { showMenu(this); true }
                elevation = dp(6).toFloat()
            }

            override fun onDraw(canvas: Canvas) {
                val edge = placement.mode in setOf(ApplicationWindowMode.LEFT_EDGE, ApplicationWindowMode.RIGHT_EDGE)
                paint.color = Color.rgb(242, 245, 250)
                canvas.drawRoundRect(
                    1f,
                    1f,
                    width - 1f,
                    height - 1f,
                    dp(if (edge) 12 else 28).toFloat(),
                    dp(if (edge) 12 else 28).toFloat(),
                    paint
                )
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = dp(1).toFloat()
                paint.color = Color.rgb(115, 133, 157)
                canvas.drawRoundRect(
                    1f,
                    1f,
                    width - 1f,
                    height - 1f,
                    dp(if (edge) 12 else 28).toFloat(),
                    dp(if (edge) 12 else 28).toFloat(),
                    paint
                )
                paint.style = Paint.Style.FILL
                val size = minOf(dp(if (edge) 22 else 36), width - dp(6), height - dp(6)).coerceAtLeast(1)
                val x = (width - size) / 2
                val y = (height - size) / 2
                val image = drawable
                if (image != null) {
                    image.setBounds(x, y, x + size, y + size)
                    image.draw(canvas)
                } else {
                    paint.color = Color.rgb(38, 59, 86)
                    paint.textSize = dp(18).toFloat()
                    paint.textAlign = Paint.Align.CENTER
                    canvas.drawText(
                        target.label.take(1),
                        width / 2f,
                        height / 2f - (paint.ascent() + paint.descent()) / 2,
                        paint
                    )
                }
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        val bounds = placement.iconBounds()
                        startX = bounds.x
                        startY = bounds.y
                        dragged = false
                        longPressed = false
                        postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - downX
                        val dy = event.rawY - downY
                        if (!longPressed && (dragged || hypot(dx, dy) > slop)) {
                            dragged = true
                            removeCallbacks(longPress)
                            placement.moveIcon(startX + dx.roundToInt(), startY + dy.roundToInt())
                            updateIcon()
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        removeCallbacks(longPress)
                        if (dragged) {
                            placement.finishIconMove()
                            updateIcon()
                        } else if (!longPressed) {
                            performClick()
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        removeCallbacks(longPress)
                        if (dragged) {
                            placement.finishIconMove()
                            updateIcon()
                        }
                    }
                }
                return true
            }

            override fun onDetachedFromWindow() {
                removeCallbacks(longPress)
                super.onDetachedFromWindow()
            }
        }
    }

    companion object {
        private const val HEADER_HEIGHT_DP = 44
        private const val RESIZE_BAR_HEIGHT_DP = 28
    }
}
