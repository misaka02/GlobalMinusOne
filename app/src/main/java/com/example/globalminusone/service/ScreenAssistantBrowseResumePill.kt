package com.example.globalminusone.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Service-owned floating pill shown while screen assistant is paused for manual browsing. */
internal class ScreenAssistantBrowseResumePill(
    private val context: Context,
    private val windowManager: WindowManager,
    private val screenSize: () -> Pair<Int, Int>,
    private val canAttach: () -> Boolean,
    private val onResume: (ScreenAssistantMode) -> Unit
) {
    private var pillView: View? = null

    fun show(savedMode: ScreenAssistantMode) {
        hide()
        if (!canAttach()) return
        val density = context.resources.displayMetrics.density
        fun dpPx(v: Float): Int = (v * density).roundToInt()

        val label = when (savedMode) {
            ScreenAssistantMode.TEXT_SELECT -> "↻ 继续识字"
            ScreenAssistantMode.TRANSLATE_INPLACE -> "↻ 继续翻译"
            ScreenAssistantMode.SUB_IMAGES -> "↻ 继续提图"
        }

        val (screenW, _) = screenSize()
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenW - dpPx(156f)).coerceAtLeast(dpPx(12f))
            y = dpPx(92f)
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            elevation = dpPx(10f).toFloat()
            background = GradientDrawable().apply {
                cornerRadius = dpPx(22f).toFloat()
                setColor(Color.argb(232, 20, 27, 39))
                setStroke(dpPx(1f), Color.argb(120, 124, 228, 255))
            }
        }

        val resumeBtn = TextView(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#8CE8FF"))
            setPadding(dpPx(14f), dpPx(9f), dpPx(10f), dpPx(9f))
            isClickable = true
            setOnClickListener {
                hide()
                onResume(savedMode)
            }
        }
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var dragged = false
        val slopPx = dpPx(8f).toFloat()
        resumeBtn.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = ev.rawX
                    downRawY = ev.rawY
                    startX = lp.x
                    startY = lp.y
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downRawX
                    val dy = ev.rawY - downRawY
                    if (!dragged && hypot(dx, dy) >= slopPx) {
                        dragged = true
                    }
                    if (dragged) {
                        val (curW, curH) = screenSize()
                        val maxX = (curW - container.width.coerceAtLeast(dpPx(120f))).coerceAtLeast(0)
                        val maxY = (curH - container.height.coerceAtLeast(dpPx(44f))).coerceAtLeast(0)
                        val nextX = (startX + dx.roundToInt()).coerceIn(0, maxX)
                        val nextY = (startY + dy.roundToInt()).coerceIn(0, maxY)
                        if (lp.x != nextX || lp.y != nextY) {
                            lp.x = nextX
                            lp.y = nextY
                            runCatching { windowManager.updateViewLayout(container, lp) }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragged) v.performClick()
                    true
                }
                else -> false
            }
        }

        val divider = View(context).apply {
            setBackgroundColor(Color.argb(55, 255, 255, 255))
        }
        val closeBtn = TextView(context).apply {
            text = "✕"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setTextColor(Color.argb(215, 232, 238, 248))
            setPadding(dpPx(10f), dpPx(9f), dpPx(12f), dpPx(9f))
            setOnClickListener { hide() }
        }

        container.addView(resumeBtn)
        container.addView(divider, LinearLayout.LayoutParams(dpPx(1f), dpPx(16f)))
        container.addView(closeBtn)

        runCatching {
            windowManager.addView(container, lp)
            pillView = container
        }.onFailure { err ->
            Log.w(TAG, "Failed to attach browse resume pill", err)
        }
    }

    fun hide() {
        val pill = pillView ?: return
        pillView = null
        runCatching { windowManager.removeViewImmediate(pill) }
            .onFailure { runCatching { windowManager.removeView(pill) } }
    }

    private companion object {
        const val TAG = "BrowseResumePill"
    }
}
