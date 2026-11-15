package com.example.globalminusone.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import com.example.globalminusone.data.SuspensionBallConfig
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Owns only a small non-touchable cursor and, on long press, a small explicit exit button. */
internal class AccessibilityMouseController(
    private val context: Context,
    private val windows: WindowManager,
    private val screenSize: () -> Pair<Int, Int>,
    private val config: () -> SuspensionBallConfig,
    private val onModeChanged: (Boolean) -> Unit,
    private val onBallRestoreFailure: () -> Unit
) {
    var active: Boolean = false
        private set
    private val handler = Handler(Looper.getMainLooper())
    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var ball: View? = null
    private var cursor: View? = null
    private var exitButton: View? = null
    private var cursorX = 0f
    private var cursorY = 0f
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var moved = false
    private var longPressed = false
    private var tracking = false
    private var waitForLift = false
    private var longPress: Runnable? = null
    private var pendingDispatch: Runnable? = null
    private var clickTimeout: Runnable? = null
    private var clickPending = false
    private var ballWasNotTouchable = false
    private var ballVisibilityBeforeClick = View.VISIBLE
    private var clickSerial = 0

    fun enter(source: View): Boolean {
        if (active) return true
        if (GlobalAccessibilityService.instance == null || !GlobalAccessibilityService.connectedState.value) {
            toast("鼠标点击需要已连接的辅助服务，请先在应用主页检查权限")
            return false
        }
        if (!config().mouseMode || !source.isAttachedToWindow || source.visibility != View.VISIBLE) return false
        val (width, height) = screenSize()
        cursorX = width / 2f
        cursorY = height / 2f
        val view = CursorView(context)
        try {
            windows.addView(view, cursorParams())
        } catch (_: Exception) {
            toast("无法显示鼠标指针")
            return false
        }
        ball = source
        cursor = view
        active = true
        // Entry came from a held drag, never turn its final UP into a click.
        waitForLift = true
        onModeChanged(true)
        toast("鼠标已开启：滑动球移动指针，短点点击，双指或长按退出")
        return true
    }

    fun exit() {
        cancelLongPress()
        clearClickCallbacks()
        clickSerial++
        val restored = restoreBallTouchability()
        clickPending = false
        dismissExitButton()
        cursor?.let { runCatching { windows.removeViewImmediate(it) } }
        cursor = null
        val wasActive = active
        active = false
        tracking = false
        waitForLift = false
        ball = null
        if (wasActive) onModeChanged(false)
        if (!restored) onBallRestoreFailure()
    }

    /** Reconfiguration must not accidentally restore touchability during an injected click. */
    fun preserveClickFlags(params: WindowManager.LayoutParams) {
        if (active && clickPending) params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    }

    fun keepBallHiddenForClick(view: View): Boolean = active && clickPending && view === ball

    fun onTouch(event: MotionEvent): Boolean {
        if (!active) return false
        if (event.pointerCount > 1 || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            exit()
            return true
        }
        if (waitForLift) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                waitForLift = false
            }
            return true
        }
        if (clickPending) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dismissExitButton()
                tracking = true
                moved = false
                longPressed = false
                downX = event.rawX
                downY = event.rawY
                lastX = event.rawX
                lastY = event.rawY
                cancelLongPress()
                longPress = Runnable {
                    longPress = null
                    if (active && tracking && !moved) {
                        longPressed = true
                        showExitButton()
                    }
                }.also { handler.postDelayed(it, ViewConfiguration.getLongPressTimeout().toLong()) }
            }
            MotionEvent.ACTION_MOVE -> if (tracking) {
                if (hypot(event.rawX - downX, event.rawY - downY) > slop) {
                    moved = true
                    cancelLongPress()
                }
                if (!longPressed) {
                    val (width, height) = screenSize()
                    val prefs = config()
                    val point = AccessibilityMousePolicy.move(cursorX, cursorY,
                        event.rawX - lastX, event.rawY - lastY, width, height, prefs.mouseRange, prefs.mouseSpeed)
                    cursorX = point.first
                    cursorY = point.second
                    cursor?.let {
                        if (runCatching { windows.updateViewLayout(it, cursorParams()) }.isFailure) exit()
                    }
                }
                lastX = event.rawX
                lastY = event.rawY
            }
            MotionEvent.ACTION_UP -> {
                cancelLongPress()
                val click = tracking && !moved && !longPressed &&
                    hypot(event.rawX - downX, event.rawY - downY) <= slop &&
                    event.eventTime - event.downTime < ViewConfiguration.getLongPressTimeout()
                tracking = false
                if (click) injectClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelLongPress()
                tracking = false
            }
        }
        return true
    }

    private fun injectClick() {
        val service = GlobalAccessibilityService.instance
        val source = ball
        val params = source?.layoutParams as? WindowManager.LayoutParams
        if (service == null || !GlobalAccessibilityService.connectedState.value) {
            exit()
            toast("辅助服务已断开，已退出鼠标模式")
            return
        }
        if (source == null || params == null || !source.isAttachedToWindow) {
            exit()
            return
        }
        ballWasNotTouchable = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0
        ballVisibilityBeforeClick = source.visibility
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        try {
            windows.updateViewLayout(source, params)
        } catch (_: Exception) {
            exit()
            return
        }
        clickPending = true
        // Hide our cursor as well: no application overlay may obscure the click target.
        source.visibility = View.INVISIBLE
        cursor?.visibility = View.GONE
        val serial = ++clickSerial
        val path = Path().apply { moveTo(cursorX, cursorY) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 60L)).build()
        clickTimeout = Runnable { finishClick(serial) }.also { handler.postDelayed(it, 1000L) }
        // Give WindowManager a frame to apply NOT_TOUCHABLE before input injection.
        pendingDispatch = Runnable {
            pendingDispatch = null
            if (!active || serial != clickSerial) return@Runnable
            if (GlobalAccessibilityService.instance !== service || !GlobalAccessibilityService.connectedState.value) {
                exit()
                return@Runnable
            }
            val accepted = runCatching {
                service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) { finishClick(serial) }
                    override fun onCancelled(gestureDescription: GestureDescription?) { finishClick(serial) }
                }, handler)
            }.getOrDefault(false)
            if (!accepted) {
                finishClick(serial)
                toast("辅助服务未接受本次点击")
            }
        }.also { handler.postDelayed(it, 48L) }
    }

    private fun finishClick(serial: Int) {
        if (serial != clickSerial || !clickPending) return
        clearClickCallbacks()
        val restored = restoreBallTouchability()
        clickPending = false
        if (!restored) {
            exit()
            onBallRestoreFailure()
            return
        }
        if (active && ball?.visibility == View.VISIBLE) cursor?.visibility = View.VISIBLE
    }

    private fun restoreBallTouchability(): Boolean {
        val source = ball ?: return true
        val params = source.layoutParams as? WindowManager.LayoutParams ?: return true
        if (!clickPending && params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE == 0) return true
        params.flags = if (ballWasNotTouchable) params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            else params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        val restored = !source.isAttachedToWindow || runCatching { windows.updateViewLayout(source, params) }.isSuccess
        if (clickPending) source.visibility = ballVisibilityBeforeClick
        return restored
    }

    private fun clearClickCallbacks() {
        pendingDispatch?.let(handler::removeCallbacks)
        clickTimeout?.let(handler::removeCallbacks)
        pendingDispatch = null
        clickTimeout = null
    }

    private fun cancelLongPress() {
        longPress?.let(handler::removeCallbacks)
        longPress = null
    }

    private fun cursorParams(): WindowManager.LayoutParams = overlayParams(dp(32), dp(40), touchable = false).apply {
        // The drawn arrow tip sits 2dp inside its view; inject exactly at that visible tip.
        val tipInset = 2f * context.resources.displayMetrics.density
        x = (cursorX - tipInset).roundToInt()
        y = (cursorY - tipInset).roundToInt()
        // Stay below Android's default untrusted-overlay obscuring limit for manual touches too.
        alpha = 0.7f
    }

    private fun overlayParams(width: Int, height: Int, touchable: Boolean): WindowManager.LayoutParams =
        WindowManager.LayoutParams(width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
        }

    private fun showExitButton() {
        if (!active || exitButton != null) return
        val sourceParams = ball?.layoutParams as? WindowManager.LayoutParams ?: return
        val (width, height) = screenSize()
        val button = Button(context).apply {
            text = "退出鼠标模式"
            contentDescription = "退出鼠标模式，恢复普通悬浮球"
            setOnClickListener { exit() }
        }
        val params = overlayParams(dp(160), dp(56), touchable = true).apply {
            x = (if (sourceParams.x > width / 2) sourceParams.x - dp(164) else sourceParams.x + sourceParams.width)
                .coerceIn(0, (width - dp(160)).coerceAtLeast(0))
            y = sourceParams.y.coerceIn(0, (height - dp(56)).coerceAtLeast(0))
        }
        if (runCatching { windows.addView(button, params) }.isSuccess) exitButton = button
        else { exit(); toast("已退出鼠标模式") }
    }

    private fun dismissExitButton() {
        exitButton?.let { runCatching { windows.removeViewImmediate(it) } }
        exitButton = null
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()
    private fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    private class CursorView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
        override fun onDraw(canvas: Canvas) {
            val d = resources.displayMetrics.density
            path.reset()
            path.moveTo(2f * d, 2f * d)
            path.lineTo(2f * d, 29f * d)
            path.lineTo(9f * d, 23f * d)
            path.lineTo(15f * d, 36f * d)
            path.lineTo(20f * d, 33f * d)
            path.lineTo(14f * d, 21f * d)
            path.lineTo(25f * d, 21f * d)
            path.close()
            paint.color = Color.BLACK
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f * d
            paint.strokeJoin = Paint.Join.ROUND
            canvas.drawPath(path, paint)
            paint.color = Color.WHITE
            paint.style = Paint.Style.FILL
            canvas.drawPath(path, paint)
        }
    }
}
