package com.example.globalminusone.service.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.OverlayLayerStatus
import com.example.globalminusone.data.OverlayWindowLayer
import com.example.globalminusone.data.TriggerPosition
import com.example.globalminusone.data.TriggerWay
import com.example.globalminusone.service.GlobalAccessibilityService

internal data class AccessibilityWindowTarget(
    val context: Context,
    val windowManager: WindowManager
)

internal fun resolveAccessibilityWindowTarget(themeResId: Int): AccessibilityWindowTarget? {
    if (!GlobalAccessibilityService.connectedState.value) return null
    val accService = GlobalAccessibilityService.instance ?: return null
    val wm = accService.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return null
    val ctx: Context = if (themeResId != 0) ContextThemeWrapper(accService, themeResId) else accService
    return AccessibilityWindowTarget(context = ctx, windowManager = wm)
}

internal fun configureFullscreenCutout(params: WindowManager.LayoutParams) {
    if (Build.VERSION.SDK_INT >= 30) {
        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        params.fitInsetsTypes = 0
    } else if (Build.VERSION.SDK_INT >= 28) {
        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
}

internal fun createPanelWindowInitialParams(): WindowManager.LayoutParams = WindowManager.LayoutParams(
    WindowManager.LayoutParams.MATCH_PARENT,
    WindowManager.LayoutParams.MATCH_PARENT,
    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
    PixelFormat.TRANSLUCENT
).apply {
    gravity = Gravity.TOP or Gravity.START
    configureFullscreenCutout(this)
    @Suppress("DEPRECATION")
    softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
}

internal fun resolveDisplayMaxRefreshRate(context: Context): Float {
    val display = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        runCatching { context.display }.getOrNull()
    } else null) ?: (context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
        ?.getDisplay(Display.DEFAULT_DISPLAY)
    @Suppress("DEPRECATION")
    return display?.supportedModes?.maxOfOrNull { it.refreshRate }?.takeIf { it > 0f }
        ?: display?.refreshRate?.takeIf { it > 0f }
        ?: 0f
}

/**
 * Votes for peak refresh rate and disables ARR power-saving throttling while the
 * panel is visible so LTPO displays do not drop to 60Hz/30Hz after screen-off,
 * then releases the vote when hidden so idle power stays at minimum Hz.
 */
internal fun applyPanelRefreshRateHint(
    params: WindowManager.LayoutParams,
    active: Boolean,
    maxRefreshRate: Float
): Boolean {
    val targetRate = if (active && maxRefreshRate > 0f) maxRefreshRate else 0f
    var changed = false
    @Suppress("DEPRECATION")
    if (params.preferredRefreshRate != targetRate) {
        params.preferredRefreshRate = targetRate
        changed = true
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        if (params.frameRateBoostOnTouchEnabled != active) {
            params.frameRateBoostOnTouchEnabled = active
            changed = true
        }
        val balanced = !active
        if (params.isFrameRatePowerSavingsBalanced != balanced) {
            params.isFrameRatePowerSavingsBalanced = balanced
            changed = true
        }
    }
    return changed
}

internal fun createPanelWindowInstance(
    windowContext: Context,
    freshParams: WindowManager.LayoutParams,
    inputHost: PanelInputHost,
    content: @Composable () -> Unit
): PanelWindowInstance {
    val composeView = ComposeView(windowContext).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }
    val root = PanelInputFrameLayout(windowContext, inputHost).apply {
        isFocusable = true
        isFocusableInTouchMode = true
        visibility = View.GONE
        addView(composeView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }
    val owner = OverlayComposeLifecycleOwner().apply {
        onCreate(); attachToView(root); attachToView(composeView); onStart(); onResume()
    }
    composeView.setContent(content)
    return PanelWindowInstance(root = root, composeView = composeView, lifecycleOwner = owner, params = freshParams)
}

/**
 * Clones layout geometry and visual flags onto a brand-new LayoutParams instance
 * with `token = null` so WindowManagerImpl never inherits a stale IBinder token
 * when attaching across `TYPE_APPLICATION_OVERLAY` <-> `TYPE_ACCESSIBILITY_OVERLAY`.
 */
internal fun cloneLayoutParamsForFreshAttach(
    source: WindowManager.LayoutParams,
    targetType: Int
): WindowManager.LayoutParams = WindowManager.LayoutParams(
    source.width,
    source.height,
    targetType,
    source.flags,
    source.format
).apply {
    gravity = source.gravity
    x = source.x
    y = source.y
    alpha = source.alpha
    dimAmount = source.dimAmount
    screenBrightness = source.screenBrightness
    @Suppress("DEPRECATION")
    softInputMode = source.softInputMode
    windowAnimations = source.windowAnimations
    title = source.title
    token = null
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) layoutInDisplayCutoutMode = source.layoutInDisplayCutoutMode
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) fitInsetsTypes = source.fitInsetsTypes
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) blurBehindRadius = source.blurBehindRadius
    @Suppress("DEPRECATION")
    preferredRefreshRate = source.preferredRefreshRate
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        frameRateBoostOnTouchEnabled = source.frameRateBoostOnTouchEnabled
        isFrameRatePowerSavingsBalanced = source.isFrameRatePowerSavingsBalanced
    }
}

/**
 * Stamps the registered layer type and retains the currently attached window token
 * when pushing inset/layout updates via `updateViewLayout`.
 */
internal fun preserveAttachedTokenForUpdate(view: View, params: WindowManager.LayoutParams, registeredType: Int) {
    params.type = registeredType
    params.token = (view.layoutParams as? WindowManager.LayoutParams)?.token
}

internal fun formatOverlayFailureReason(error: Throwable?): String? {
    error ?: return null
    val name = error.javaClass.simpleName.ifBlank { "Exception" }
    val msg = error.message.orEmpty().trim().take(80)
    return if (msg.isNotEmpty()) "$name: $msg" else name
}

internal fun collectOverlayLayerStatus(
    state: GlobalMinusOneState,
    panelHost: PanelWindowHost<*, *>,
    triggerSlots: Map<TriggerPosition, AndroidManagedOverlaySlot<EdgeTriggerView>>,
    ballSlot: AndroidManagedOverlaySlot<SuspensionBallView>
): OverlayLayerStatus {
    val accConnected = GlobalAccessibilityService.connectedState.value && GlobalAccessibilityService.instance != null
    val activeTriggerTypes = if (!state.triggerConfig.serviceEnabled) emptyList() else when (state.triggerConfig.triggerWay) {
        TriggerWay.NONE -> emptyList()
        TriggerWay.SUSPENSION_BALL -> listOf(ballSlot.registeredType)
        else -> if (state.triggerConfig.triggerPosition == TriggerPosition.BOTH) {
            listOf(triggerSlots[TriggerPosition.LEFT]?.registeredType, triggerSlots[TriggerPosition.RIGHT]?.registeredType)
        } else {
            listOf(triggerSlots[state.triggerConfig.triggerPosition]?.registeredType)
        }
    }
    val lastError = panelHost.lastAccessibilityError
        ?: triggerSlots.values.firstNotNullOfOrNull { it.lastAccessibilityError }
        ?: ballSlot.lastAccessibilityError
    return buildOverlayLayerStatus(
        requestedAccessibilityLayer = state.advancedConfig.highestOverlayLevel,
        accessibilityConnected = accConnected,
        panelWindowType = panelHost.activeType,
        activeTriggerTypes = activeTriggerTypes,
        lastError = lastError
    )
}

internal fun buildOverlayLayerStatus(
    requestedAccessibilityLayer: Boolean,
    accessibilityConnected: Boolean,
    panelWindowType: Int?,
    activeTriggerTypes: List<Int?>,
    lastError: String?
): OverlayLayerStatus {
    val panelLayer = when (panelWindowType) {
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY -> OverlayWindowLayer.ACCESSIBILITY
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY -> OverlayWindowLayer.APPLICATION
        else -> OverlayWindowLayer.UNREGISTERED
    }
    val triggerLayer = when {
        activeTriggerTypes.isEmpty() -> OverlayWindowLayer.NONE
        activeTriggerTypes.all { it == WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY } -> OverlayWindowLayer.ACCESSIBILITY
        activeTriggerTypes.any { it == null } -> OverlayWindowLayer.UNREGISTERED
        else -> OverlayWindowLayer.APPLICATION
    }
    return OverlayLayerStatus(
        requestedAccessibilityLayer = requestedAccessibilityLayer,
        accessibilityConnected = accessibilityConnected,
        panelLayer = panelLayer,
        triggerLayer = triggerLayer,
        lastError = lastError
    )
}
