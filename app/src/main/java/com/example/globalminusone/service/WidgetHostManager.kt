package com.example.globalminusone.service

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.RemoteViews
import com.example.globalminusone.R
import com.example.globalminusone.data.WidgetHostContract
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * 原生 AppWidgetHost 安全托管容器。
 * 针对 Android 12~16 悬浮窗 Service 中部分第三方小部件 InflateException、资源缺失导致的崩溃做了全方位保护。
 */
class WidgetHostManager private constructor(context: Context) {

    private val appContext = context.applicationContext

    val appWidgetManager: AppWidgetManager =
        AppWidgetManager.getInstance(appContext)

    val appWidgetHost: AppWidgetHost =
        SafeAppWidgetHost(appContext, HOST_ID)

    private val hostViewCache = ConcurrentHashMap<Int, SafeAppWidgetHostView>()

    @Volatile
    private var listening = false

    /**
     * 自定义防崩溃 AppWidgetHost
     */
    class SafeAppWidgetHost(
        context: Context,
        hostId: Int
    ) : AppWidgetHost(context, hostId) {

        override fun onCreateView(
            context: Context,
            appWidgetId: Int,
            appWidget: AppWidgetProviderInfo?
        ): AppWidgetHostView {
            val themedContext = ContextThemeWrapper(context, R.style.Theme_GlobalMinusOne)
            return SafeAppWidgetHostView(themedContext)
        }
    }

    /**
     * 自定义防崩溃 AppWidgetHostView。
     * 解决第三方桌面小部件在远程更新、绘制与布局时的异常穿透，以及编辑拦截与静止长按透传。
     */
    class SafeAppWidgetHostView(context: Context) : AppWidgetHostView(context) {

        @Volatile
        var blockInteractionForEdit: Boolean = false

        @Volatile
        var onHostLongPress: (() -> Unit)? = null

        private var reportedSize: SizeF? = null
        private var loggedFirstRemoteViews = false
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        private val longPressTimeoutMs = ViewConfiguration.getLongPressTimeout().toLong()
        private var downX = 0f
        private var downY = 0f
        private var trackingTouch = false
        private var longPressTriggered = false
        private var touchedScrollableView: View? = null
        private var scrollDecisionMade = false
        private val reportSize = Runnable { reportMeasuredSize() }
        private val forceRelayout = Runnable {
            if (isAttachedToWindow && width > 0 && height > 0) {
                measure(
                    MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
                )
                layout(left, top, right, bottom)
                invalidate()
            }
        }
        private val longPressRunnable = Runnable {
            if (!trackingTouch || blockInteractionForEdit) return@Runnable
            val callback = onHostLongPress ?: return@Runnable
            longPressTriggered = true
            trackingTouch = false
            touchedScrollableView = null
            cancelChildTouch(downX, downY)
            callback()
        }

        fun clearHostCallbacks() {
            onHostLongPress = null
            cancelPendingLongPress()
            resetTouchRouting()
        }

        private fun cancelPendingLongPress() {
            trackingTouch = false
            removeCallbacks(longPressRunnable)
        }

        private fun resetTouchRouting() {
            touchedScrollableView = null
            scrollDecisionMade = false
            parent?.requestDisallowInterceptTouchEvent(false)
        }

        private fun cancelChildTouch(x: Float, y: Float) {
            val now = android.os.SystemClock.uptimeMillis()
            val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, x, y, 0)
            super.dispatchTouchEvent(cancel)
            cancel.recycle()
        }

        private fun findVerticallyScrollableChild(root: ViewGroup, x: Float, y: Float): View? {
            for (i in root.childCount - 1 downTo 0) {
                val child = root.getChildAt(i) ?: continue
                if (child.visibility != View.VISIBLE) continue
                val localX = x + root.scrollX - child.left
                val localY = y + root.scrollY - child.top
                if (localX < 0f || localX >= child.width || localY < 0f || localY >= child.height) continue
                if (child is ViewGroup) {
                    findVerticallyScrollableChild(child, localX, localY)?.let { return it }
                }
                if (child.canScrollVertically(1) || child.canScrollVertically(-1)) {
                    return child
                }
            }
            return null
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            scheduleSizeReport()
            scheduleForceRelayout()
        }

        override fun onDetachedFromWindow() {
            removeCallbacks(reportSize)
            removeCallbacks(forceRelayout)
            cancelPendingLongPress()
            resetTouchRouting()
            reportedSize = null
            super.onDetachedFromWindow()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            scheduleSizeReport()
        }

        private fun scheduleSizeReport() {
            removeCallbacks(reportSize)
            post(reportSize)
        }

        private fun scheduleForceRelayout() {
            removeCallbacks(forceRelayout)
            post(forceRelayout)
        }

        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            if (blockInteractionForEdit) {
                cancelPendingLongPress()
                resetTouchRouting()
                return false
            }
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.x
                    downY = ev.y
                    longPressTriggered = false
                    scrollDecisionMade = false
                    touchedScrollableView = findVerticallyScrollableChild(this, ev.x, ev.y)
                    val hasLongPress = onHostLongPress != null
                    trackingTouch = hasLongPress
                    removeCallbacks(longPressRunnable)
                    if (hasLongPress) {
                        postDelayed(longPressRunnable, longPressTimeoutMs)
                    }
                    if (touchedScrollableView != null) {
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    val handled = super.dispatchTouchEvent(ev)
                    if (!handled) {
                        cancelPendingLongPress()
                        if (touchedScrollableView != null) {
                            resetTouchRouting()
                        }
                    }
                    return handled
                }
                MotionEvent.ACTION_MOVE -> {
                    if (longPressTriggered) return true
                    val dx = ev.x - downX
                    val dy = ev.y - downY
                    if (trackingTouch && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        cancelPendingLongPress()
                    }
                    val target = touchedScrollableView
                    if (!scrollDecisionMade && target != null) {
                        val decision = resolveWidgetTouchScrollAction(
                            hasScrollableChild = true,
                            dx = dx,
                            dy = dy,
                            touchSlop = touchSlop,
                            canScrollUpContent = target.canScrollVertically(-1),
                            canScrollDownContent = target.canScrollVertically(1)
                        )
                        if (decision != null) {
                            scrollDecisionMade = true
                            if (decision) {
                                parent?.requestDisallowInterceptTouchEvent(true)
                            } else {
                                cancelChildTouch(ev.x, ev.y)
                                resetTouchRouting()
                                return false
                            }
                        }
                    }
                    return super.dispatchTouchEvent(ev)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    cancelPendingLongPress()
                    resetTouchRouting()
                    if (longPressTriggered) {
                        longPressTriggered = false
                        return true
                    }
                    return super.dispatchTouchEvent(ev)
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    cancelPendingLongPress()
                    return super.dispatchTouchEvent(ev)
                }
                else -> return super.dispatchTouchEvent(ev)
            }
        }

        private fun reportMeasuredSize() {
            if (!isAttachedToWindow || appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
            val contentWidth = width - paddingLeft - paddingRight
            val contentHeight = height - paddingTop - paddingBottom
            if (contentWidth <= 0 || contentHeight <= 0) return
            val density = resources.displayMetrics.density
            val size = SizeF(contentWidth / density, contentHeight / density)
            if (size == reportedSize) return

            // This host removes framework widget padding. Report the actual content area
            // directly and preserve any provider extras stored on the widget options bundle.
            val existing = runCatching {
                AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
            }.getOrNull()
            val options = (if (existing != null) Bundle(existing) else Bundle()).apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, size.width.toInt())
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, size.width.toInt())
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, size.height.toInt())
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, size.height.toInt())
                putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(size))
                }
            }
            try {
                updateAppWidgetOptions(options)
                reportedSize = size
                Log.d(TAG, "Widget $appWidgetId size reported: $size dp")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to report size for widget $appWidgetId", e)
            }
        }

        override fun updateAppWidget(remoteViews: RemoteViews?) {
            try {
                super.updateAppWidget(remoteViews)
                requestLayout()
                invalidate()
                scheduleForceRelayout()
                if (remoteViews != null && !loggedFirstRemoteViews) {
                    loggedFirstRemoteViews = true
                    Log.d(TAG, "Widget $appWidgetId received RemoteViews, children=$childCount")
                }
            } catch (e: Throwable) {
                Log.e(TAG, "SafeAppWidgetHostView: updateAppWidget caught exception", e)
            }
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            try {
                super.onLayout(changed, left, top, right, bottom)
            } catch (e: Throwable) {
                Log.e(TAG, "SafeAppWidgetHostView: onLayout caught exception", e)
            }
        }

        override fun dispatchDraw(canvas: Canvas) {
            try {
                super.dispatchDraw(canvas)
            } catch (e: Throwable) {
                Log.e(TAG, "SafeAppWidgetHostView: dispatchDraw caught exception", e)
            }
        }
    }

    fun startListening() {
        if (listening) return
        try {
            appWidgetHost.startListening()
            listening = true
        } catch (e: Throwable) {
            Log.w(TAG, "startListening error", e)
        }
    }

    /** Stops binder updates while keeping cached host views intact for instant resume. */
    fun pauseListening() {
        if (!listening) return
        listening = false
        try {
            appWidgetHost.stopListening()
        } catch (e: Throwable) {
            Log.w(TAG, "pauseListening error", e)
        }
    }

    fun stopListening() {
        listening = false
        try {
            appWidgetHost.stopListening()
        } catch (e: Throwable) {
            Log.w(TAG, "stopListening error", e)
        }
        hostViewCache.values.forEach { view ->
            view.clearHostCallbacks()
            (view.parent as? ViewGroup)?.removeView(view)
        }
        hostViewCache.clear()
    }

    fun allocateWidgetId(): Int = appWidgetHost.allocateAppWidgetId()

    fun deleteWidgetId(appWidgetId: Int) {
        try {
            hostViewCache.remove(appWidgetId)?.let { view ->
                view.clearHostCallbacks()
                (view.parent as? ViewGroup)?.removeView(view)
            }
            appWidgetHost.deleteAppWidgetId(appWidgetId)
        } catch (e: Exception) {
            Log.w(TAG, "deleteWidgetId error for id: $appWidgetId", e)
        }
    }

    fun createHostView(context: Context, appWidgetId: Int): AppWidgetHostView? {
        startListening()
        val cached = hostViewCache[appWidgetId]
        if (cached != null) {
            return cached
        }

        val info = try {
            appWidgetManager.getAppWidgetInfo(appWidgetId)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to get AppWidgetInfo for widgetId: $appWidgetId", e)
            null
        } ?: return null

        return try {
            // SafeAppWidgetHost.onCreateView applies Theme_GlobalMinusOne once; pass applicationContext
            // to avoid double-wrapping or retaining a transient Activity/Window context.
            val hostView = appWidgetHost.createView(appContext, appWidgetId, info)
                as? SafeAppWidgetHostView ?: return null
            hostView.setPadding(0, 0, 0, 0)
            hostViewCache[appWidgetId] = hostView
            hostView
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to create AppWidgetHostView for widgetId: $appWidgetId", e)
            null
        }
    }

    companion object {
        const val HOST_ID = WidgetHostContract.HOST_ID
        private const val TAG = "WidgetHostManager"

        @Volatile
        private var INSTANCE: WidgetHostManager? = null

        fun getInstance(context: Context): WidgetHostManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WidgetHostManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

/**
 * Pure policy deciding whether an AppWidget touch gesture should claim vertical scrolling for an
 * inner Android scrollable view (`true`), yield back to the outer Compose pager/scroll (`false`),
 * or wait for enough drag past touchSlop (`null`).
 */
internal fun resolveWidgetTouchScrollAction(
    hasScrollableChild: Boolean,
    dx: Float,
    dy: Float,
    touchSlop: Float,
    canScrollUpContent: Boolean,
    canScrollDownContent: Boolean
): Boolean? {
    if (!hasScrollableChild) return false
    val absDx = abs(dx)
    val absDy = abs(dy)
    if (absDx <= touchSlop && absDy <= touchSlop) return null
    if (absDx > absDy) return false
    return when {
        dy < 0f -> canScrollDownContent
        dy > 0f -> canScrollUpContent
        else -> false
    }
}
