package com.example.globalminusone.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.globalminusone.data.GestureAction
import com.example.globalminusone.data.TriggerConfig
import com.example.globalminusone.data.TriggerPosition
import com.example.globalminusone.data.TriggerSide
import com.example.globalminusone.data.TriggerWay
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Service-owned operations needed by an edge trigger, with configuration read at use time. */
internal interface EdgeTriggerHost {
    val triggerConfig: TriggerConfig
    val panelWidthPercent: Int
    var activeSide: TriggerSide
    fun setOpenProgress(progress: Float)
    fun getScreenSize(): Pair<Int, Int>
    fun resolveTriggerOpenSide(position: TriggerPosition, action: GestureAction? = null): TriggerSide
    fun preparePanelForDragging()
    fun animateOpenPanel(side: TriggerSide, initialVelocity: Float = 0f)
    fun animateClosePanel(initialVelocity: Float = 0f)
    fun performTickHaptic(useSystemVibrate: Boolean = false)
    fun executeGestureAction(action: GestureAction, onOpenPanelSide: (TriggerSide) -> Unit): Boolean
    fun getTriggerLayoutParams(
        position: TriggerPosition,
        screenWidth: Int,
        screenHeight: Int,
        config: TriggerConfig,
        callbackInsets: WindowInsetsCompat? = null
    ): WindowManager.LayoutParams
    fun updateTriggerLayout(view: View, params: WindowManager.LayoutParams)
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
internal class EdgeTriggerView(
    context: Context,
    val position: TriggerPosition,
    private val host: EdgeTriggerHost
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
    private var dragStartProjectedPx = 0f
    private var velocityTracker: VelocityTracker? = null

    // 二次滑动防误触状态
    private var lastFirstTouchTimeMs = 0L
    private var firstTouchX = 0f
    private var firstTouchY = 0f
    private var isSecondSlideHighlighted = false
    private var secondSlideResetRunnable: Runnable? = null

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (hasMoved || (gestureMode != GestureMode.IDLE && gestureMode != GestureMode.CLICK_WAITING)) return false
            val config = host.triggerConfig
            if (config.triggerWay == TriggerWay.SIDE_PULLDOWN) return false
            val act = config.singleTapAction
            if (act != GestureAction.NONE) {
                if (config.vibrate) host.performTickHaptic()
                host.executeGestureAction(act) { side -> host.animateOpenPanel(side) }
                return true
            }
            return false
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (hasMoved || (gestureMode != GestureMode.IDLE && gestureMode != GestureMode.CLICK_WAITING)) return false
            val config = host.triggerConfig
            if (config.triggerWay == TriggerWay.SIDE_PULLDOWN) return false
            val act = config.doubleTapAction
            if (act != GestureAction.NONE) {
                if (config.vibrate) host.performTickHaptic()
                host.executeGestureAction(act) { side -> host.animateOpenPanel(side) }
                return true
            }
            return false
        }

        override fun onLongPress(e: MotionEvent) {
            if (hasMoved || (gestureMode != GestureMode.CLICK_WAITING && gestureMode != GestureMode.IDLE)) return
            val config = host.triggerConfig
            if (config.triggerWay == TriggerWay.SIDE_PULLDOWN) return
            val act = config.longPressAction
            if (act != GestureAction.NONE) {
                if (config.vibrate) host.performTickHaptic()
                host.executeGestureAction(act) { side -> host.animateOpenPanel(side) }
            }
        }
    })

    private fun obtainVelocityTracker(event: MotionEvent) {
        velocityTracker?.clear() ?: run { velocityTracker = VelocityTracker.obtain() }
        velocityTracker?.addMovement(event)
    }

    private fun recycleVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    override fun onDetachedFromWindow() {
        secondSlideResetRunnable?.let { removeCallbacks(it) }
        secondSlideResetRunnable = null
        recycleVelocityTracker()
        super.onDetachedFromWindow()
    }

    fun updateGestureExclusion(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val exclusion = triggerEdgeExclusions(width, height, resources.displayMetrics.density,
                enabled && visibility == View.VISIBLE && host.triggerConfig.triggerWay != TriggerWay.SIDE_PULLDOWN,
                position == TriggerPosition.LEFT || position == TriggerPosition.RIGHT)
                .map { Rect(it.left, it.top, it.right, it.bottom) }
            ViewCompat.setSystemGestureExclusionRects(this, exclusion)
        }
    }

    override fun onApplyWindowInsets(insets: android.view.WindowInsets): android.view.WindowInsets {
        val applied = super.onApplyWindowInsets(insets)
        val config = host.triggerConfig
        val needsInsetUpdate = (config.triggerWay == TriggerWay.SIDE_PULLDOWN && SidePullTriggerPolicy.supports(position)) ||
            (config.avoidInputMethod && (position == TriggerPosition.LEFT || position == TriggerPosition.RIGHT))
        if (needsInsetUpdate) {
            val (screenWidth, screenHeight) = host.getScreenSize()
            val compatInsets = WindowInsetsCompat.toWindowInsetsCompat(insets, this)
            val target = host.getTriggerLayoutParams(position, screenWidth, screenHeight, config, compatInsets)
            val currentLp = layoutParams as? WindowManager.LayoutParams
            if (currentLp?.x != target.x || currentLp.y != target.y || currentLp.height != target.height) post {
                val latest = host.triggerConfig
                if (isAttachedToWindow) {
                    val (width, height) = host.getScreenSize()
                    runCatching { host.updateTriggerLayout(this, host.getTriggerLayoutParams(position, width, height, latest, compatInsets)) }
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
        val config = host.triggerConfig
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
        val config = host.triggerConfig
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
                            host.performTickHaptic(config.useSystemVibrate)
                        }
                        secondSlideResetRunnable?.let { removeCallbacks(it) }
                        val resetRunnable = Runnable {
                            isSecondSlideHighlighted = false
                            invalidate()
                        }
                        secondSlideResetRunnable = resetRunnable
                        postDelayed(resetRunnable, timeoutMs)

                        recycleVelocityTracker()
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

                obtainVelocityTracker(event)
                downRawX = event.rawX
                downRawY = event.rawY
                dragStartProjectedPx = 0f
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
                velocityTracker?.addMovement(event)

                if (sidePull) {
                    if (gestureMode == GestureMode.IGNORED) return true
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    val recognition = SidePullTriggerPolicy.sidePullRecognition(dx, dy, touchSlop.toFloat(), config.minimumSwipeDistanceDp, density)
                    if (!SidePullTriggerPolicy.supports(position) || recognition == SidePullRecognition.CANCEL) {
                        if (gestureMode == GestureMode.FOLLOW_PANEL_DRAG) host.animateClosePanel()
                        gestureMode = GestureMode.IGNORED; hasMoved = true
                        return true
                    }
                    if (hypot(dx, dy) > touchSlop) hasMoved = true
                    if (recognition == SidePullRecognition.FOLLOW && gestureMode != GestureMode.FOLLOW_PANEL_DRAG) {
                        host.activeSide = host.resolveTriggerOpenSide(position)
                        dragStartProjectedPx = dy.coerceAtLeast(0f)
                        gestureMode = GestureMode.FOLLOW_PANEL_DRAG
                        host.preparePanelForDragging()
                        host.setOpenProgress(0f)
                        if (config.vibrate && !hasVibrated) { host.performTickHaptic(); hasVibrated = true }
                    }
                    if (gestureMode == GestureMode.FOLLOW_PANEL_DRAG) {
                        host.setOpenProgress(SidePullTriggerPolicy.followProgress(dy - dragStartProjectedPx, host.getScreenSize().second))
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
                if (gestureMode == GestureMode.CLICK_WAITING && distance > touchSlop) {
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
                        val side = host.resolveTriggerOpenSide(position, action)
                        host.activeSide = side
                        followDirection = dir
                        dragStartProjectedPx = TriggerGesturePolicy.projectedDistance(dir, dx, dy).coerceAtLeast(0f)
                        gestureMode = GestureMode.FOLLOW_PANEL_DRAG
                        host.preparePanelForDragging()
                        host.setOpenProgress(0f)
                        if (config.vibrate && !hasVibrated) {
                            host.performTickHaptic()
                            hasVibrated = true
                        }
                    } else if (TriggerGesturePolicy.reachesSwipeDistance(distance, config.minimumSwipeDistanceDp, density)) {
                        if (action != GestureAction.NONE) {
                            gestureMode = GestureMode.SWIPE_ACTION_PENDING
                            pendingSwipeAction = action
                        } else {
                            gestureMode = GestureMode.IGNORED
                        }
                    }
                }

                when (gestureMode) {
                    GestureMode.FOLLOW_PANEL_DRAG -> {
                        val (screenWidth, screenHeight) = host.getScreenSize()
                        val panelFraction = (host.panelWidthPercent / 100f).coerceIn(0.3f, 1f)
                        val panelWidth = screenWidth.toFloat() * panelFraction

                        val progress = TriggerGesturePolicy.followProgressFromOffset(
                            followDirection, dx, dy, dragStartProjectedPx, panelWidth, screenHeight.toFloat()
                        )
                        host.setOpenProgress(progress)
                    }
                    GestureMode.SWIPE_ACTION_PENDING -> {
                        val dist = hypot(dx, dy)
                        if (TriggerGesturePolicy.reachesSwipeDistance(dist, config.minimumSwipeDistanceDp, density)) {
                            gestureMode = GestureMode.SWIPE_ACTION_TRIGGERED
                            if (config.vibrate) {
                                host.performTickHaptic()
                            }
                            host.executeGestureAction(pendingSwipeAction) { side ->
                                host.animateOpenPanel(side)
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
                    recycleVelocityTracker()
                    gestureMode = GestureMode.IDLE
                    return true
                }

                velocityTracker?.addMovement(event)
                velocityTracker?.computeCurrentVelocity(1000)
                val vx = velocityTracker?.xVelocity ?: 0f
                val vy = velocityTracker?.yVelocity ?: 0f
                recycleVelocityTracker()

                if (sidePull) {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    val (_, screenHeight) = host.getScreenSize()
                    val vProg = (vy / (screenHeight.coerceAtLeast(1) * 0.35f)).coerceIn(-24f, 24f)
                    if (gestureMode == GestureMode.FOLLOW_PANEL_DRAG) {
                        val progress = SidePullTriggerPolicy.followProgress(dy - dragStartProjectedPx, screenHeight)
                        val shouldOpen = SidePullTriggerPolicy.supports(position) &&
                            vProg > -TriggerGesturePolicy.FLING_VELOCITY_PROGRESS_PER_SEC && (
                                vProg >= TriggerGesturePolicy.FLING_VELOCITY_PROGRESS_PER_SEC ||
                                    progress >= 0.42f ||
                                    SidePullTriggerPolicy.sidePullRecognition(dx, dy, touchSlop.toFloat(), config.minimumSwipeDistanceDp, density) == SidePullRecognition.FOLLOW
                                )
                        if (shouldOpen) {
                            if (config.vibrate && !hasVibrated) host.performTickHaptic()
                            host.animateOpenPanel(host.resolveTriggerOpenSide(position), vProg)
                        } else {
                            host.animateClosePanel(vProg)
                        }
                    } else {
                        val shouldOpen = gestureMode != GestureMode.IGNORED && SidePullTriggerPolicy.supports(position) &&
                            SidePullTriggerPolicy.sidePullRecognition(dx, dy, touchSlop.toFloat(), config.minimumSwipeDistanceDp, density) == SidePullRecognition.FOLLOW
                        if (shouldOpen) {
                            if (config.vibrate && !hasVibrated) host.performTickHaptic()
                            host.animateOpenPanel(host.resolveTriggerOpenSide(position))
                        }
                    }
                    gestureMode = GestureMode.IDLE
                    return true
                }

                when (gestureMode) {
                    GestureMode.FOLLOW_PANEL_DRAG -> {
                        val dx = event.rawX - downRawX
                        val dy = event.rawY - downRawY
                        val (screenWidth, screenHeight) = host.getScreenSize()
                        val panelFraction = (host.panelWidthPercent / 100f).coerceIn(0.3f, 1f)
                        val panelWidth = screenWidth.toFloat() * panelFraction
                        val inwardDistance = TriggerGesturePolicy.projectedDistance(followDirection, dx, dy)
                        val progress = TriggerGesturePolicy.followProgressFromOffset(
                            followDirection, dx, dy, dragStartProjectedPx, panelWidth, screenHeight.toFloat()
                        )
                        val vProg = TriggerGesturePolicy.velocityToProgressPerSec(
                            followDirection, vx, vy, panelWidth, screenHeight.toFloat()
                        )
                        if (TriggerGesturePolicy.shouldSettleOpen(progress, vProg, inwardDistance, config.minimumSwipeDistanceDp, density)) {
                            host.animateOpenPanel(host.activeSide, vProg)
                        } else {
                            host.animateClosePanel(vProg)
                        }
                    }
                    GestureMode.CLICK_WAITING -> {
                        val dx = event.rawX - downRawX
                        val dy = event.rawY - downRawY
                        val dist = hypot(dx, dy)
                        if (TriggerGesturePolicy.isTap(hasMoved, dist, touchSlop.toFloat()) && config.doubleTapAction == GestureAction.NONE && config.singleTapAction != GestureAction.NONE) {
                            if (config.vibrate) {
                                host.performTickHaptic()
                            }
                            host.executeGestureAction(config.singleTapAction) { side ->
                                host.animateOpenPanel(side)
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
                recycleVelocityTracker()
                invalidate()
                if (gestureMode == GestureMode.FOLLOW_PANEL_DRAG) {
                    host.animateClosePanel()
                }
                gestureMode = GestureMode.IDLE
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}

