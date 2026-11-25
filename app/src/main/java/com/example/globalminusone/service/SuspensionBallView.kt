package com.example.globalminusone.service

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Choreographer
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import com.example.globalminusone.data.GestureAction
import com.example.globalminusone.data.SuspensionBallConfig
import com.example.globalminusone.data.TriggerConfig
import com.example.globalminusone.data.TriggerSide
import com.example.globalminusone.ui.motion.SpringPhysics1D
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Service-owned operations needed by the suspension ball, read or invoked at use time. */
internal interface SuspensionBallHost {
    val suspensionBallConfig: SuspensionBallConfig
    val triggerConfig: TriggerConfig
    val destroying: Boolean
    val mouseActive: Boolean?
    fun enterMouse(view: View): Boolean?
    fun exitMouse()
    fun onMouseTouch(event: MotionEvent): Boolean?
    fun launchIdleFade(block: suspend CoroutineScope.() -> Unit): Job
    fun savePosition(xPercent: Float, yPercent: Float)
    fun getScreenSize(): Pair<Int, Int>
    fun performTickHaptic(useSystemVibrate: Boolean = false)
    fun executeGestureAction(action: GestureAction, onOpenPanelSide: (TriggerSide) -> Unit): Boolean
    fun animateOpenPanel(side: TriggerSide)
    fun updateBallLayout(view: View, params: WindowManager.LayoutParams)
}

/**
 * 自定义悬浮球 View
 */
internal class SuspensionBallView(context: Context, private val host: SuspensionBallHost) : View(context) {

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
    private var breatheAnimator: ValueAnimator? = null
    private var idleFadeJob: Job? = null
    private var snapFrameCallback: Choreographer.FrameCallback? = null
    private var velocityTracker: VelocityTracker? = null
    private var edgeDwell: Runnable? = null
    private var edgeAnchorX = 0f
    private var edgeAnchorY = 0f
    private var mouseEntryAttempted = false
    private var suppressNormalUntilUp = false

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (host.mouseActive == true || suppressNormalUntilUp || isDragging || hasMoved) return false
            val sbConfig = host.suspensionBallConfig
            val act = sbConfig.singleTapAction
            if (act != GestureAction.NONE) {
                if (host.triggerConfig.vibrate) host.performTickHaptic()
                host.executeGestureAction(act) { side -> host.animateOpenPanel(side) }
                return true
            }
            return false
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (host.mouseActive == true || suppressNormalUntilUp || isDragging || hasMoved) return false
            val sbConfig = host.suspensionBallConfig
            val act = sbConfig.doubleTapAction
            if (act != GestureAction.NONE) {
                if (host.triggerConfig.vibrate) host.performTickHaptic()
                host.executeGestureAction(act) { side -> host.animateOpenPanel(side) }
                return true
            }
            return false
        }

        override fun onLongPress(e: MotionEvent) {
            if (host.mouseActive != true && !suppressNormalUntilUp && !hasMoved) {
                if (host.triggerConfig.vibrate) {
                    host.performTickHaptic()
                }
                isDragging = true
                invalidate()
            }
        }
    })

    private fun cancelSnapAnimation() {
        snapFrameCallback?.let { Choreographer.getInstance().removeFrameCallback(it) }
        snapFrameCallback = null
    }

    private fun recycleVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    private fun addRawMovement(event: MotionEvent) {
        val tracker = velocityTracker ?: return
        val raw = MotionEvent.obtain(event)
        raw.offsetLocation(event.rawX - event.x, event.rawY - event.y)
        tracker.addMovement(raw)
        raw.recycle()
    }

    private fun startSpringSnap(
        lp: WindowManager.LayoutParams,
        startX: Int,
        targetX: Int,
        initialVelocityPxPerSec: Float
    ) {
        cancelSnapAnimation()
        if (startX == targetX) return
        val spring = SpringPhysics1D(
            angularFrequency = 30f,
            positionTolerance = 0.5f,
            velocityTolerance = 10f
        )
        val clampedVelocity = initialVelocityPxPerSec.coerceIn(-3200f, 3200f)
        var startFrameNanos = -1L
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (snapFrameCallback !== this) return
                if (!isAttachedToWindow || host.destroying) {
                    snapFrameCallback = null
                    return
                }
                if (startFrameNanos < 0L) {
                    startFrameNanos = frameTimeNanos
                }
                val elapsedSec = ((frameTimeNanos - startFrameNanos) / 1_000_000_000f).coerceAtLeast(0f)
                val sample = spring.sample(
                    startPosition = startX.toFloat(),
                    targetPosition = targetX.toFloat(),
                    initialVelocity = clampedVelocity,
                    elapsedSeconds = elapsedSec
                )
                val nextX = if (sample.settled) targetX else sample.position.roundToInt()
                if (lp.x != nextX) {
                    lp.x = nextX
                    runCatching { host.updateBallLayout(this@SuspensionBallView, lp) }
                }
                if (sample.settled) {
                    snapFrameCallback = null
                } else {
                    Choreographer.getInstance().postFrameCallback(this)
                }
            }
        }
        snapFrameCallback = callback
        Choreographer.getInstance().postFrameCallback(callback)
    }

    fun cleanup() {
        cancelMouseEntry()
        host.exitMouse()
        cancelSnapAnimation()
        recycleVelocityTracker()
        animate().cancel()
        alpha = 1.0f
        breatheAnimator?.cancel()
        breatheAnimator = null
        scaleX = 1.0f
        scaleY = 1.0f
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
        if (enabled && isAttachedToWindow && visibility == View.VISIBLE && host.mouseActive != true) {
            val freqSec = host.suspensionBallConfig.breatheFrequencySeconds.coerceIn(1, 10)
            breatheAnimator = ValueAnimator.ofFloat(0.92f, 1.06f).apply {
                duration = freqSec * 700L
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener {
                    val s = it.animatedValue as Float
                    scaleX = s
                    scaleY = s
                }
                start()
            }
        } else {
            scaleX = 1.0f
            scaleY = 1.0f
        }
    }

    private fun resetIdleFadeTimer() {
        idleFadeJob?.cancel()
        idleFadeJob = null
        animate().cancel()
        if (alpha < 1.0f) {
            alpha = 1.0f
        }
    }

    fun scheduleIdleFade() {
        idleFadeJob?.cancel()
        val sbConfig = host.suspensionBallConfig
        if (sbConfig.autoFadeSeconds > 0 && host.mouseActive != true) {
            idleFadeJob = host.launchIdleFade {
                delay(sbConfig.autoFadeSeconds * 1000L)
                if (!isAttachedToWindow || visibility != View.VISIBLE || host.mouseActive == true) return@launchIdleFade
                animate().alpha(0.4f).setDuration(260L).start()
            }
        }
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        // View's constructor can dispatch this before Kotlin fields initialize.
        if (!isAttachedToWindow) return
        updateBreatheMode(host.suspensionBallConfig.breatheMode)
        if (visibility == View.VISIBLE) scheduleIdleFade() else {
            idleFadeJob?.cancel()
            animate().cancel()
        }
    }

    fun onMouseModeChanged(active: Boolean) {
        cancelMouseEntry()
        cancelSnapAnimation()
        recycleVelocityTracker()
        if (!active && isTouched) suppressNormalUntilUp = true
        isDragging = false
        hasMoved = true
        val cancel = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        gestureDetector.onTouchEvent(cancel)
        cancel.recycle()
        resetIdleFadeTimer()
        updateBreatheMode(host.suspensionBallConfig.breatheMode)
        contentDescription = if (active) "鼠标触控球，滑动移动指针，短点点击，双指或长按退出" else "悬浮球"
        if (!active && !host.destroying) {
            val config = host.suspensionBallConfig
            val (screenWidth, screenHeight) = host.getScreenSize()
            val sizePx = (config.radiusDp * 2 * resources.displayMetrics.density).roundToInt()
            (layoutParams as? WindowManager.LayoutParams)?.let { lp ->
                lp.x = ((screenWidth - sizePx).coerceAtLeast(0) * config.posXPercent).roundToInt()
                lp.y = ((screenHeight - sizePx).coerceAtLeast(0) * config.posYPercent).roundToInt()
                runCatching { host.updateBallLayout(this, lp) }
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
        val config = host.suspensionBallConfig
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
                !host.suspensionBallConfig.mouseMode) return@Runnable
            val (width, height) = host.getScreenSize()
            if (!AccessibilityMousePolicy.atEdge(current.x, current.y, sizePx, width, height, edgeMargin)) return@Runnable
            mouseEntryAttempted = true
            host.enterMouse(this) // Temporary edge control position does not replace the saved normal ball position.
        }.also { postDelayed(it, AccessibilityMousePolicy.EDGE_DWELL_MS) }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val sbConfig = host.suspensionBallConfig
        val density = resources.displayMetrics.density
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val radius = minOf(w, h) / 2f

        val baseAlpha = (sbConfig.alpha * 255).roundToInt().coerceIn(0, 255)

        // 外圈柔和微光
        glowPaint.color = 0xFF2196F3.toInt()
        glowPaint.alpha = (baseAlpha * 0.25f).roundToInt()
        canvas.drawCircle(cx, cy, radius * 0.96f, glowPaint)

        // 悬浮球主体
        paint.color = if (host.mouseActive == true) 0xFF00695C.toInt() else if (isDragging) 0xFF1976D2.toInt() else 0xFF2B303B.toInt()
        paint.alpha = baseAlpha
        canvas.drawCircle(cx, cy, radius * 0.82f, paint)

        // 内部装饰光环
        ringPaint.style = Paint.Style.STROKE
        ringPaint.strokeWidth = 2f * density
        ringPaint.color = 0xFF64B5F6.toInt()
        ringPaint.alpha = (baseAlpha * 0.7f).roundToInt()
        canvas.drawCircle(cx, cy, radius * 0.55f, ringPaint)

        // 中心微圆心或四宫格图标
        ringPaint.style = Paint.Style.FILL
        ringPaint.color = Color.WHITE
        ringPaint.alpha = baseAlpha
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
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        resetIdleFadeTimer()
        if (host.mouseActive == true) {
            isTouched = event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL
            return host.onMouseTouch(event) ?: true
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
            cancelSnapAnimation()
            recycleVelocityTracker()
            cancelMouseEntry()
        }
        gestureDetector.onTouchEvent(event)

        val (screenWidth, screenHeight) = host.getScreenSize()
        val density = resources.displayMetrics.density
        val sbConfig = host.suspensionBallConfig
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
                velocityTracker = VelocityTracker.obtain()
                addRawMovement(event)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                addRawMovement(event)
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
                        if (lp.x != newX || lp.y != newY) {
                            lp.x = newX
                            lp.y = newY
                            try {
                                host.updateBallLayout(this, lp)
                            } catch (_: Exception) { cancelMouseEntry() }
                        }
                        if (isAttachedToWindow) {
                            runCatching { scheduleMouseEntry(lp, screenWidth, screenHeight, sizePx) }
                                .onFailure { cancelMouseEntry() }
                        }
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                cancelMouseEntry()
                isTouched = false
                addRawMovement(event)
                val vxPxPerSec = velocityTracker?.let {
                    it.computeCurrentVelocity(1000, 6000f)
                    it.xVelocity
                } ?: 0f
                recycleVelocityTracker()
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                val absDx = abs(dx)
                val absDy = abs(dy)
                val dist = hypot(dx, dy)

                if (isDragging) {
                    isDragging = false
                    if (hasMoved || dist > touchSlop) {
                        // 持久化拖拽后的百分比位置 (BUG-003: 支持速度感知物理弹簧贴边)
                        val lp = layoutParams as? WindowManager.LayoutParams
                        val maxX = (screenWidth - sizePx).coerceAtLeast(1).toFloat()
                        val maxY = (screenHeight - sizePx).coerceAtLeast(1).toFloat()
                        val curX = (lp?.x ?: 0).toFloat()
                        val curY = (lp?.y ?: 0).toFloat()
                        val rawPosX = (curX / maxX).coerceIn(0.02f, 0.98f)
                        val newPosX = OverlayVisibilityPolicy.snapSuspensionBallXPercentWithVelocity(
                            posXPercent = rawPosX,
                            velocityXFractionPerSec = vxPxPerSec / maxX,
                            snapToEdge = sbConfig.snapToEdge
                        )
                        val newPosY = (curY / maxY).coerceIn(0.02f, 0.98f)
                        if (sbConfig.snapToEdge && lp != null) {
                            val targetX = (newPosX * maxX).roundToInt()
                            startSpringSnap(lp, lp.x, targetX, vxPxPerSec)
                        }
                        host.savePosition(newPosX, newPosY)
                    } else {
                        // 长按未发生明显拖动，执行长按绑定动作
                        val act = sbConfig.longPressAction
                        if (act != GestureAction.NONE) {
                            host.executeGestureAction(act) { side -> host.animateOpenPanel(side) }
                        }
                    }
                } else if (TriggerGesturePolicy.reachesSwipeDistance(dist, host.triggerConfig.minimumSwipeDistanceDp, density)) {
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
                        if (host.triggerConfig.vibrate) {
                            host.performTickHaptic()
                        }
                        host.executeGestureAction(act) { side -> host.animateOpenPanel(side) }
                    }
                } else {
                    // 单击判断
                    if (sbConfig.doubleTapAction == GestureAction.NONE && sbConfig.singleTapAction != GestureAction.NONE && TriggerGesturePolicy.isTap(hasMoved, dist, touchSlop.toFloat())) {
                        if (host.triggerConfig.vibrate) {
                            host.performTickHaptic()
                        }
                        host.executeGestureAction(sbConfig.singleTapAction) { side -> host.animateOpenPanel(side) }
                    }
                }
                invalidate()
                scheduleIdleFade()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelMouseEntry()
                recycleVelocityTracker()
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

