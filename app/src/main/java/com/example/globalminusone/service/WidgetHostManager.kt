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
import android.widget.RemoteViews
import com.example.globalminusone.R
import java.util.concurrent.ConcurrentHashMap

/**
 * 原生 AppWidgetHost 安全托管容器。
 * 针对 Android 12~16 悬浮窗 Service 中部分第三方小部件 InflateException、资源缺失导致的崩溃做了全方位保护。
 */
class WidgetHostManager private constructor(context: Context) {

    val appWidgetManager: AppWidgetManager =
        AppWidgetManager.getInstance(context.applicationContext)

    val appWidgetHost: AppWidgetHost =
        SafeAppWidgetHost(context.applicationContext, HOST_ID)

    private val hostViewCache = ConcurrentHashMap<Int, SafeAppWidgetHostView>()

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
     * 解决第三方桌面小部件在远程更新、绘制与布局时的异常穿透。
     */
    class SafeAppWidgetHostView(context: Context) : AppWidgetHostView(context) {

        private var reportedSize: SizeF? = null
        private var loggedFirstRemoteViews = false
        private val reportSize = Runnable { reportMeasuredSize() }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            scheduleSizeReport()
        }

        override fun onDetachedFromWindow() {
            removeCallbacks(reportSize)
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

        private fun reportMeasuredSize() {
            if (!isAttachedToWindow || appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
            val contentWidth = width - paddingLeft - paddingRight
            val contentHeight = height - paddingTop - paddingBottom
            if (contentWidth <= 0 || contentHeight <= 0) return
            val density = resources.displayMetrics.density
            val size = SizeF(contentWidth / density, contentHeight / density)
            if (size == reportedSize) return

            // This host removes framework widget padding. Report the actual content area
            // directly: updateAppWidgetSize would subtract the default padding again.
            val options = Bundle().apply {
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
        try {
            appWidgetHost.startListening()
        } catch (e: Throwable) {
            Log.w(TAG, "startListening error", e)
        }
    }

    fun stopListening() {
        try {
            appWidgetHost.stopListening()
        } catch (e: Throwable) {
            Log.w(TAG, "stopListening error", e)
        }
    }

    fun allocateWidgetId(): Int = appWidgetHost.allocateAppWidgetId()

    fun deleteWidgetId(appWidgetId: Int) {
        try {
            hostViewCache.remove(appWidgetId)
            appWidgetHost.deleteAppWidgetId(appWidgetId)
        } catch (e: Exception) {
            Log.w(TAG, "deleteWidgetId error for id: $appWidgetId", e)
        }
    }

    fun createHostView(context: Context, appWidgetId: Int): AppWidgetHostView? {
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
            val themedContext = ContextThemeWrapper(context, R.style.Theme_GlobalMinusOne)
            // createView registers the listener and delivers the current RemoteViews.
            // A manually constructed fallback would never receive provider updates.
            val hostView = appWidgetHost.createView(themedContext, appWidgetId, info)
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
        const val HOST_ID = 2026
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
