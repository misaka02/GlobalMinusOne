package com.example.globalminusone.service.capture

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.core.content.FileProvider
import com.example.globalminusone.system.media.ImageClipboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import com.example.globalminusone.service.ScreenAssistantMode
import com.example.globalminusone.service.overlay.dispatchWithCallFallbackGuard
import com.example.globalminusone.service.overlay.screenshotOrientationMatches

internal enum class CropDragHandle {
    NONE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    LEFT,
    TOP,
    RIGHT,
    BOTTOM,
    INSIDE_MOVE,
    NEW_RECT
}

internal data class CropBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) * 0.5f
    val centerY: Float get() = (top + bottom) * 0.5f
    fun contains(x: Float, y: Float): Boolean = x >= left && x < right && y >= top && y < bottom
    fun toRectF(): RectF = RectF(left, top, right, bottom)
}

internal data class PixelCropRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

private fun RectF.toCropBox(): CropBox = CropBox(left, top, right, bottom)

/** Own the input immediately, even when the parent scope was already cancelled. */
internal fun <T, R : Any> launchRegionExport(
    scope: CoroutineScope,
    input: T,
    write: suspend (T) -> R?,
    release: (T) -> Unit,
    discard: suspend (R) -> Unit,
    complete: (R?) -> Unit
): Job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
    var output: R? = null
    var delivered = false
    try {
        coroutineContext.ensureActive()
        withContext(Dispatchers.IO) { output = write(input) }
        coroutineContext.ensureActive()
        complete(output)
        delivered = true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Log.e("RegionScreenshotOverlay", "Screenshot export failed", failure)
    } finally {
        release(input)
        if (!delivered) output?.let { result ->
            withContext(NonCancellable + Dispatchers.IO) { runCatching { discard(result) } }
        }
    }
}

/**
 * Pure, unit-testable geometry engine for interactive region screenshot cropping, handle hit-testing,
 * minimum-size enforcement, view-to-bitmap pixel coordinate mapping, and floating toolbar placement.
 */
internal object RegionCropGeometry {

    fun defaultInitialSelection(viewWidth: Float, viewHeight: Float): CropBox {
        val w = viewWidth.coerceAtLeast(64f)
        val h = viewHeight.coerceAtLeast(64f)
        return CropBox(
            w * 0.16f,
            h * 0.22f,
            w * 0.84f,
            h * 0.68f
        )
    }

    fun hitTestHandle(rect: CropBox, x: Float, y: Float, touchRadiusPx: Float): CropDragHandle {
        if (rect.width <= 1f || rect.height <= 1f) return CropDragHandle.NEW_RECT
        val r = touchRadiusPx.coerceAtLeast(16f)
        val nearLeft = abs(x - rect.left) <= r
        val nearRight = abs(x - rect.right) <= r
        val nearTop = abs(y - rect.top) <= r
        val nearBottom = abs(y - rect.bottom) <= r
        val withinX = x in (rect.left - r)..(rect.right + r)
        val withinY = y in (rect.top - r)..(rect.bottom + r)

        return when {
            nearLeft && nearTop -> CropDragHandle.TOP_LEFT
            nearRight && nearTop -> CropDragHandle.TOP_RIGHT
            nearLeft && nearBottom -> CropDragHandle.BOTTOM_LEFT
            nearRight && nearBottom -> CropDragHandle.BOTTOM_RIGHT
            nearLeft && withinY -> CropDragHandle.LEFT
            nearRight && withinY -> CropDragHandle.RIGHT
            nearTop && withinX -> CropDragHandle.TOP
            nearBottom && withinX -> CropDragHandle.BOTTOM
            rect.contains(x, y) -> CropDragHandle.INSIDE_MOVE
            else -> CropDragHandle.NEW_RECT
        }
    }

    fun applyDrag(
        startRect: CropBox,
        handle: CropDragHandle,
        downX: Float,
        downY: Float,
        currentX: Float,
        currentY: Float,
        viewWidth: Float,
        viewHeight: Float,
        minSizePx: Float
    ): CropBox {
        val maxW = viewWidth.coerceAtLeast(minSizePx)
        val maxH = viewHeight.coerceAtLeast(minSizePx)
        val minS = minSizePx.coerceAtLeast(16f).coerceAtMost(min(maxW, maxH))
        val dx = currentX - downX
        val dy = currentY - downY

        return when (handle) {
            CropDragHandle.NONE -> startRect
            CropDragHandle.INSIDE_MOVE -> {
                val w = startRect.width.coerceIn(minS, maxW)
                val h = startRect.height.coerceIn(minS, maxH)
                val newLeft = (startRect.left + dx).coerceIn(0f, maxW - w)
                val newTop = (startRect.top + dy).coerceIn(0f, maxH - h)
                CropBox(newLeft, newTop, newLeft + w, newTop + h)
            }
            CropDragHandle.NEW_RECT -> {
                val x0 = downX.coerceIn(0f, maxW)
                val y0 = downY.coerceIn(0f, maxH)
                val x1 = currentX.coerceIn(0f, maxW)
                val y1 = currentY.coerceIn(0f, maxH)
                var left = min(x0, x1)
                var right = max(x0, x1)
                var top = min(y0, y1)
                var bottom = max(y0, y1)
                if (right - left < minS) {
                    if (left + minS <= maxW) right = left + minS else left = (right - minS).coerceAtLeast(0f)
                }
                if (bottom - top < minS) {
                    if (top + minS <= maxH) bottom = top + minS else top = (bottom - minS).coerceAtLeast(0f)
                }
                CropBox(left, top, right, bottom)
            }
            else -> {
                var left = startRect.left
                var top = startRect.top
                var right = startRect.right
                var bottom = startRect.bottom

                if (handle == CropDragHandle.TOP_LEFT || handle == CropDragHandle.BOTTOM_LEFT || handle == CropDragHandle.LEFT) {
                    left = (startRect.left + dx).coerceIn(0f, (right - minS).coerceAtLeast(0f))
                }
                if (handle == CropDragHandle.TOP_RIGHT || handle == CropDragHandle.BOTTOM_RIGHT || handle == CropDragHandle.RIGHT) {
                    right = (startRect.right + dx).coerceIn((left + minS).coerceAtMost(maxW), maxW)
                }
                if (handle == CropDragHandle.TOP_LEFT || handle == CropDragHandle.TOP_RIGHT || handle == CropDragHandle.TOP) {
                    top = (startRect.top + dy).coerceIn(0f, (bottom - minS).coerceAtLeast(0f))
                }
                if (handle == CropDragHandle.BOTTOM_LEFT || handle == CropDragHandle.BOTTOM_RIGHT || handle == CropDragHandle.BOTTOM) {
                    bottom = (startRect.bottom + dy).coerceIn((top + minS).coerceAtMost(maxH), maxH)
                }
                CropBox(left, top, right, bottom)
            }
        }
    }

    fun viewRectToBitmapRect(
        selection: CropBox,
        viewWidth: Int,
        viewHeight: Int,
        bitmapWidth: Int,
        bitmapHeight: Int
    ): PixelCropRect = viewRectToBitmapRectInBounds(
        selection = selection,
        imageLeft = 0f,
        imageTop = 0f,
        imageRight = viewWidth.coerceAtLeast(1).toFloat(),
        imageBottom = viewHeight.coerceAtLeast(1).toFloat(),
        bitmapWidth = bitmapWidth,
        bitmapHeight = bitmapHeight
    )

    fun viewRectToBitmapRectInBounds(
        selection: CropBox,
        imageLeft: Float,
        imageTop: Float,
        imageRight: Float,
        imageBottom: Float,
        bitmapWidth: Int,
        bitmapHeight: Int
    ): PixelCropRect {
        if (bitmapWidth <= 0 || bitmapHeight <= 0) return PixelCropRect(0, 0, 0, 0)
        val dw = imageRight - imageLeft
        val dh = imageBottom - imageTop
        if (dw <= 0f || dh <= 0f || selection.left >= imageRight || selection.right <= imageLeft ||
            selection.top >= imageBottom || selection.bottom <= imageTop
        ) return PixelCropRect(0, 0, 0, 0)
        val scaleX = bitmapWidth.toFloat() / dw
        val scaleY = bitmapHeight.toFloat() / dh

        val left = ((selection.left - imageLeft) * scaleX).roundToInt().coerceIn(0, bitmapWidth - 1)
        val top = ((selection.top - imageTop) * scaleY).roundToInt().coerceIn(0, bitmapHeight - 1)
        val right = ((selection.right - imageLeft) * scaleX).roundToInt().coerceIn(left + 1, bitmapWidth)
        val bottom = ((selection.bottom - imageTop) * scaleY).roundToInt().coerceIn(top + 1, bitmapHeight)
        return PixelCropRect(left, top, right, bottom)
    }

    fun computeToolbarPosition(
        selection: CropBox,
        toolbarWidth: Int,
        toolbarHeight: Int,
        viewWidth: Int,
        viewHeight: Int,
        marginPx: Int
    ): Pair<Float, Float> {
        val maxX = (viewWidth - toolbarWidth - marginPx).coerceAtLeast(marginPx).toFloat()
        val x = (selection.centerX - toolbarWidth / 2f).coerceIn(marginPx.toFloat(), maxX)

        val belowY = selection.bottom + marginPx
        val aboveY = selection.top - toolbarHeight - marginPx
        val insideBottomY = (selection.bottom - toolbarHeight - marginPx)
            .coerceIn(marginPx.toFloat(), (viewHeight - toolbarHeight - marginPx).coerceAtLeast(marginPx).toFloat())

        val y = when {
            belowY + toolbarHeight + marginPx <= viewHeight -> belowY
            aboveY >= marginPx -> aboveY
            else -> insideBottomY
        }
        return x to y
    }
}

/**
 * Interactive full-screen Region Screenshot (`区域截屏`) overlay window:
 * - Freezes the captured screen bitmap 1:1 on screen without calling system full-screen screenshot UI.
 * - Supports dragging 4 corners, 4 edges, moving inside, or dragging a brand-new selection anywhere outside.
 * - Includes optional pen annotation (`标注` + `撤销`), `全选`, `分享` (via FileProvider), `保存` (to MediaStore Pictures/Screenshots + Clipboard), and `取消`/Back.
 */
internal class RegionScreenshotOverlay(
    private val context: Context,
    private val windowManager: WindowManager,
    private val scope: CoroutineScope,
    private val onAnalyzeRegion: ((Bitmap, Rect, ScreenAssistantMode, ScreenAssistantFrameSnapshot) -> Unit)? = null
) {
    private var rootContainer: FrameLayout? = null
    private var sourceBitmap: Bitmap? = null
    private var backCallback: OnBackInvokedCallback? = null
    private var savingOrSharing = false
    private var exportJob: Job? = null
    private var feedbackBannerView: TextView? = null
    private var hideFeedbackRunnable: Runnable? = null
    private var frameSnapshot: ScreenAssistantFrameSnapshot? = null

    val isShowing: Boolean
        get() = rootContainer != null

    fun show(capturedBitmap: Bitmap, snapshot: ScreenAssistantFrameSnapshot) {
        dismiss()
        frameSnapshot = snapshot
        if (capturedBitmap.isRecycled) {
            dismiss()
            return
        }
        val screenshotWidth = capturedBitmap.width
        val screenshotHeight = capturedBitmap.height
        val displayBounds = if (Build.VERSION.SDK_INT >= 30) windowManager.currentWindowMetrics.bounds else {
            val dm = context.resources.displayMetrics
            Rect(0, 0, dm.widthPixels, dm.heightPixels)
        }
        if (!screenshotOrientationMatches(screenshotWidth, screenshotHeight, displayBounds.width(), displayBounds.height())) {
            capturedBitmap.recycle()
            dismiss()
            Toast.makeText(context, "屏幕方向已改变，请重新截屏", Toast.LENGTH_SHORT).show()
            return
        }
        sourceBitmap = try {
            if (capturedBitmap.config == Bitmap.Config.HARDWARE) {
                capturedBitmap.copy(Bitmap.Config.ARGB_8888, false)
                    ?.also { capturedBitmap.recycle() }
                    ?: throw IllegalStateException("无法读取截屏像素")
            } else capturedBitmap
        } catch (failure: Throwable) {
            if (!capturedBitmap.isRecycled) capturedBitmap.recycle()
            dismiss()
            Log.e(TAG, "Screenshot conversion failed", failure)
            Toast.makeText(context, "无法读取截图，请重试", Toast.LENGTH_SHORT).show()
            return
        }
        savingOrSharing = false

        val density = context.resources.displayMetrics.density
        val minCropPx = 36f * density
        val handleHitRadiusPx = 24f * density
        val toolbarMarginPx = (12f * density).roundToInt()

        val selection = RegionCropGeometry.defaultInitialSelection(
            screenshotWidth.toFloat(),
            screenshotHeight.toFloat()
        ).toRectF()
        val annotations = ArrayList<Path>()
        var activeStroke: Path? = null
        var annotateMode = false

        var toolbarRef: LinearLayout? = null
        lateinit var toolbar: LinearLayout
        lateinit var annotateBtn: TextView
        lateinit var undoBtn: TextView
        lateinit var cancelBtn: TextView
        lateinit var canvasView: View

        val feedbackBanner = TextView(context).apply {
            visibility = View.GONE
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding((14f * density).roundToInt(), (8f * density).roundToInt(), (14f * density).roundToInt(), (8f * density).roundToInt())
            elevation = 8f * density
        }
        feedbackBannerView = feedbackBanner

        fun showOverlayFeedback(message: String, isError: Boolean = false) {
            hideFeedbackRunnable?.let { feedbackBanner.removeCallbacks(it) }
            feedbackBanner.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20f * density
                setColor(if (isError) Color.parseColor("#EA8C1D18") else Color.parseColor("#EA1B5E20"))
                setStroke(max(1, (1f * density).roundToInt()), Color.parseColor(if (isError) "#66EF9A9A" else "#66A5D6A7"))
            }
            feedbackBanner.text = message
            feedbackBanner.visibility = View.VISIBLE
            feedbackBanner.bringToFront()
            val hideTask = Runnable {
                feedbackBanner.visibility = View.GONE
            }
            hideFeedbackRunnable = hideTask
            feedbackBanner.postDelayed(hideTask, 2600L)
        }

        fun positionToolbar() {
            val tb = toolbarRef ?: return
            if (tb.width <= 0 || tb.height <= 0) return
            val vw = if (canvasView.width > 0) canvasView.width else screenshotWidth
            val vh = if (canvasView.height > 0) canvasView.height else screenshotHeight
            val (tx, ty) = RegionCropGeometry.computeToolbarPosition(
                selection = selection.toCropBox(),
                toolbarWidth = tb.width,
                toolbarHeight = tb.height,
                viewWidth = vw,
                viewHeight = vh,
                marginPx = toolbarMarginPx
            )
            tb.translationX = tx
            tb.translationY = ty
        }

        canvasView = object : View(context) {
            private val bitmapDst = RectF()
            private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
            private val dimPaint = Paint().apply {
                color = Color.argb(142, 0, 0, 0)
                style = Paint.Style.FILL
            }
            private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#4DA3FF")
                style = Paint.Style.STROKE
                strokeWidth = 2f * density
            }
            private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(110, 255, 255, 255)
                style = Paint.Style.STROKE
                strokeWidth = 1f * density
                pathEffect = DashPathEffect(floatArrayOf(4f * density, 4f * density), 0f)
            }
            private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 3.5f * density
                strokeCap = Paint.Cap.ROUND
            }
            private val handleAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#4DA3FF")
                style = Paint.Style.FILL
            }
            private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(205, 18, 22, 30)
                style = Paint.Style.FILL
            }
            private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
                typeface = Typeface.DEFAULT_BOLD
            }
            private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FF4D4F")
                style = Paint.Style.STROKE
                strokeWidth = 3.2f * density
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            private var activeHandle = CropDragHandle.NONE
            private val dragStartRect = RectF(); private val badgeRect = RectF()
            private var downX = 0f
            private var downY = 0f
            private var isDraggingSelection = false

            override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                super.onSizeChanged(w, h, oldw, oldh)
                if (w > 0 && h > 0 && (oldw == 0 || oldh == 0)) {
                    selection.set(RegionCropGeometry.defaultInitialSelection(w.toFloat(), h.toFloat()).toRectF())
                    post { positionToolbar() }
                }
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val bmp = sourceBitmap ?: return
                if (bmp.isRecycled) return
                val vw = width.takeIf { it > 0 } ?: bmp.width
                val vh = height.takeIf { it > 0 } ?: bmp.height

                val fit = com.example.globalminusone.screenassistant.ScreenTextSelectionPolicy.computeFullDisplayRect(
                    vw.toFloat(), vh.toFloat(), bmp.width, bmp.height
                )
                bitmapDst.set(fit[0], fit[1], fit[2], fit[3])
                canvas.drawBitmap(bmp, null, bitmapDst, bitmapPaint)

                // Draw any user pen annotations clipped inside selection
                if (annotations.isNotEmpty() || activeStroke != null) {
                    val save = canvas.save()
                    canvas.clipRect(selection)
                    for (p in annotations) canvas.drawPath(p, strokePaint)
                    activeStroke?.let { canvas.drawPath(it, strokePaint) }
                    canvas.restoreToCount(save)
                }

                // 4-rect outside dim mask (zero PorterDuff layer allocation)
                canvas.drawRect(0f, 0f, vw.toFloat(), selection.top, dimPaint)
                canvas.drawRect(0f, selection.bottom, vw.toFloat(), vh.toFloat(), dimPaint)
                canvas.drawRect(0f, selection.top, selection.left, selection.bottom, dimPaint)
                canvas.drawRect(selection.right, selection.top, vw.toFloat(), selection.bottom, dimPaint)

                // Rule-of-thirds guides
                val thirdW = selection.width() / 3f
                val thirdH = selection.height() / 3f
                for (i in 1..2) {
                    val gx = selection.left + thirdW * i
                    val gy = selection.top + thirdH * i
                    canvas.drawLine(gx, selection.top, gx, selection.bottom, gridPaint)
                    canvas.drawLine(selection.left, gy, selection.right, gy, gridPaint)
                }

                // Selection border
                canvas.drawRect(selection, borderPaint)

                // Corner L-brackets + mid-edge pill handles
                val arm = min(16f * density, min(selection.width(), selection.height()) * 0.3f)
                // Top-left
                canvas.drawLine(selection.left, selection.top, selection.left + arm, selection.top, handlePaint)
                canvas.drawLine(selection.left, selection.top, selection.left, selection.top + arm, handlePaint)
                // Top-right
                canvas.drawLine(selection.right - arm, selection.top, selection.right, selection.top, handlePaint)
                canvas.drawLine(selection.right, selection.top, selection.right, selection.top + arm, handlePaint)
                // Bottom-left
                canvas.drawLine(selection.left, selection.bottom - arm, selection.left, selection.bottom, handlePaint)
                canvas.drawLine(selection.left, selection.bottom, selection.left + arm, selection.bottom, handlePaint)
                // Bottom-right
                canvas.drawLine(selection.right - arm, selection.bottom, selection.right, selection.bottom, handlePaint)
                canvas.drawLine(selection.right, selection.bottom - arm, selection.right, selection.bottom, handlePaint)

                val dotR = 4.2f * density
                canvas.drawCircle(selection.centerX(), selection.top, dotR, handleAccentPaint)
                canvas.drawCircle(selection.centerX(), selection.bottom, dotR, handleAccentPaint)
                canvas.drawCircle(selection.left, selection.centerY(), dotR, handleAccentPaint)
                canvas.drawCircle(selection.right, selection.centerY(), dotR, handleAccentPaint)

                // Pixel dimension + hint badge
                val bmpRect = RegionCropGeometry.viewRectToBitmapRectInBounds(
                    selection.toCropBox(),
                    fit[0], fit[1], fit[2], fit[3], bmp.width, bmp.height
                )
                val modeTag = if (annotateMode) " · 标注模式" else " · 拖动四角/边框缩放"
                val label = "${bmpRect.width} × ${bmpRect.height} px$modeTag"
                val padH = 10f * density
                val padV = 6f * density
                val textW = badgeTextPaint.measureText(label)
                val fm = badgeTextPaint.fontMetrics
                val textH = fm.descent - fm.ascent
                val badgeW = textW + padH * 2f
                val badgeH = textH + padV * 2f
                val badgeX = selection.left.coerceIn(8f * density, (vw - badgeW - 8f * density).coerceAtLeast(8f * density))
                val preferredAboveY = selection.top - badgeH - 8f * density
                val badgeY = if (preferredAboveY >= 24f * density) preferredAboveY else (selection.top + 8f * density)
                badgeRect.set(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH)
                canvas.drawRoundRect(badgeRect, 8f * density, 8f * density, badgeBgPaint)
                canvas.drawText(label, badgeX + padH, badgeY + padV - fm.ascent, badgeTextPaint)
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (savingOrSharing) return true
                val vw = width.takeIf { it > 0 }?.toFloat() ?: return false
                val vh = height.takeIf { it > 0 }?.toFloat() ?: return false
                val x = event.x.coerceIn(0f, vw)
                val y = event.y.coerceIn(0f, vh)

                if (annotateMode && selection.contains(x, y)) {
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            activeStroke = Path().apply { moveTo(x, y) }
                            toolbar.visibility = View.INVISIBLE
                            invalidate()
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            activeStroke?.lineTo(x, y)
                            invalidate()
                            return true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            activeStroke?.let {
                                it.lineTo(x, y)
                                annotations.add(it)
                            }
                            activeStroke = null
                            undoBtn.visibility = if (annotations.isNotEmpty()) View.VISIBLE else View.GONE
                            toolbar.visibility = View.VISIBLE
                            toolbar.post { positionToolbar() }
                            invalidate()
                            return true
                        }
                    }
                }

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = x
                        downY = y
                        dragStartRect.set(selection)
                        activeHandle = RegionCropGeometry.hitTestHandle(selection.toCropBox(), x, y, handleHitRadiusPx)
                        isDraggingSelection = activeHandle != CropDragHandle.NONE
                        if (isDraggingSelection) {
                            if (activeHandle == CropDragHandle.NEW_RECT) {
                                annotations.clear()
                                undoBtn.visibility = View.GONE
                            }
                            toolbar.visibility = View.INVISIBLE
                            invalidate()
                        }
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!isDraggingSelection) return false
                        val updated = RegionCropGeometry.applyDrag(
                            startRect = dragStartRect.toCropBox(),
                            handle = activeHandle,
                            downX = downX,
                            downY = downY,
                            currentX = x,
                            currentY = y,
                            viewWidth = vw,
                            viewHeight = vh,
                            minSizePx = minCropPx
                        )
                        selection.set(updated.toRectF())
                        invalidate()
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        activeHandle = CropDragHandle.NONE
                        isDraggingSelection = false
                        toolbar.visibility = View.VISIBLE
                        positionToolbar()
                        invalidate()
                        return true
                    }
                }
                return super.onTouchEvent(event)
            }
        }.apply {
            contentDescription = "区域截屏画布"
        }

        fun buildCroppedBitmapWithRect(includeAnnotations: Boolean = true): Pair<Bitmap, Rect>? {
            val bmp = sourceBitmap ?: return null
            if (bmp.isRecycled) return null
            val vw = canvasView.width.takeIf { it > 0 } ?: bmp.width
            val vh = canvasView.height.takeIf { it > 0 } ?: bmp.height
            val fit = com.example.globalminusone.screenassistant.ScreenTextSelectionPolicy.computeFullDisplayRect(
                vw.toFloat(), vh.toFloat(), bmp.width, bmp.height
            )
            val crop = RegionCropGeometry.viewRectToBitmapRectInBounds(
                selection.toCropBox(),
                fit[0], fit[1], fit[2], fit[3], bmp.width, bmp.height
            )
            if (crop.width <= 0 || crop.height <= 0) return null

            val cropRect = Rect(crop.left, crop.top, crop.left + crop.width, crop.top + crop.height)
            // Always allocate independent software pixels: full selection must never alias source.
            val out = try {
                Bitmap.createBitmap(crop.width, crop.height, Bitmap.Config.ARGB_8888)
            } catch (failure: Throwable) {
                Log.e(TAG, "Unable to allocate region screenshot", failure)
                return null
            }
            try {
                val c = Canvas(out)
                val cropPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
                c.drawBitmap(bmp, cropRect, Rect(0, 0, crop.width, crop.height), cropPaint)
                if (!includeAnnotations || annotations.isEmpty()) return out to cropRect
                val scaleX = bmp.width.toFloat() / (fit[2] - fit[0]).coerceAtLeast(1f)
                val scaleY = bmp.height.toFloat() / (fit[3] - fit[1]).coerceAtLeast(1f)
                c.save()
                c.translate(-crop.left.toFloat(), -crop.top.toFloat())
                c.scale(scaleX, scaleY)
                c.translate(-fit[0], -fit[1])
                val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#FF4D4F")
                    style = Paint.Style.STROKE
                    strokeWidth = 3.2f * density
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                for (path in annotations) c.drawPath(path, pen)
                c.restore()
                return out to cropRect
            } catch (failure: Throwable) {
                out.recycle()
                Log.e(TAG, "Unable to crop region screenshot", failure)
                return null
            }
        }

        fun buildCroppedBitmap(): Bitmap? = buildCroppedBitmapWithRect()?.first

        fun launchRegionAnalysis(mode: ScreenAssistantMode) {
            if (savingOrSharing) return
            val callback = onAnalyzeRegion ?: return
            val snapshot = frameSnapshot ?: return
            val result = buildCroppedBitmapWithRect(includeAnnotations = mode != ScreenAssistantMode.SUB_IMAGES)
            if (result == null) {
                Toast.makeText(context, "裁剪区域无效", Toast.LENGTH_SHORT).show()
                return
            }
            savingOrSharing = true
            frameSnapshot = null // The assistant now owns this capture's enrichment.
            dismiss()
            try {
                callback(result.first, result.second, mode, snapshot)
            } catch (failure: Throwable) {
                snapshot.enriched?.cancel()
                if (!result.first.isRecycled) result.first.recycle()
                Log.e(TAG, "Region analysis handoff failed", failure)
                Toast.makeText(context, "无法打开屏幕识别，请重试", Toast.LENGTH_SHORT).show()
            }
        }

        fun makeActionChip(
            text: String,
            primary: Boolean = false,
            accentActive: Boolean = false,
            onClick: () -> Unit
        ): TextView = TextView(context).apply {
            this.text = text
            contentDescription = "区域截屏_$text"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val hPad = (12 * density).roundToInt()
            val vPad = (8 * density).roundToInt()
            setPadding(hPad, vPad, hPad, vPad)
            setTextColor(
                when {
                    primary -> Color.WHITE
                    accentActive -> Color.parseColor("#FFD666")
                    else -> Color.parseColor("#EEF2FA")
                }
            )
            background = GradientDrawable().apply {
                cornerRadius = 16f * density
                setColor(
                    when {
                        primary -> Color.parseColor("#2B7DE9")
                        accentActive -> Color.parseColor("#3B2B1A")
                        else -> Color.TRANSPARENT
                    }
                )
            }
            setOnClickListener { onClick() }
        }

        toolbar = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val pad = (6 * density).roundToInt()
            setPadding(pad, pad, pad, pad)
            elevation = 10f * density
            background = GradientDrawable().apply {
                cornerRadius = 18f * density
                setColor(Color.argb(236, 22, 26, 35))
                setStroke((1f * density).roundToInt().coerceAtLeast(1), Color.argb(90, 255, 255, 255))
            }
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> positionToolbar() }
        }
        toolbarRef = toolbar

        val ocrBtn = if (onAnalyzeRegion != null) {
            makeActionChip("识字", accentActive = true) { launchRegionAnalysis(ScreenAssistantMode.TEXT_SELECT) }
        } else null
        val translateBtn = if (onAnalyzeRegion != null) {
            makeActionChip("翻译", accentActive = true) { launchRegionAnalysis(ScreenAssistantMode.TRANSLATE_INPLACE) }
        } else null
        val subImageBtn = if (onAnalyzeRegion != null) {
            makeActionChip("提图", accentActive = true) { launchRegionAnalysis(ScreenAssistantMode.SUB_IMAGES) }
        } else null

        cancelBtn = makeActionChip("取消") { dismiss() }
        val fullBtn = makeActionChip("全选") {
            val vw = (canvasView.width.takeIf { it > 0 } ?: screenshotWidth).toFloat()
            val vh = (canvasView.height.takeIf { it > 0 } ?: screenshotHeight).toFloat()
            selection.set(0f, 0f, vw, vh)
            canvasView.invalidate()
            toolbar.post { positionToolbar() }
        }
        undoBtn = makeActionChip("撤销") {
            if (annotations.isNotEmpty()) {
                annotations.removeAt(annotations.lastIndex)
                if (annotations.isEmpty()) undoBtn.visibility = View.GONE
                canvasView.invalidate()
                toolbar.post { positionToolbar() }
            }
        }.apply { visibility = View.GONE }

        annotateBtn = makeActionChip("标注") {
            annotateMode = !annotateMode
            annotateBtn.setTextColor(if (annotateMode) Color.parseColor("#FF7875") else Color.parseColor("#EEF2FA"))
            canvasView.invalidate()
        }

        val shareAction = share@{
            if (savingOrSharing) return@share
            val cropped = buildCroppedBitmap()
            if (cropped == null) {
                showOverlayFeedback("裁剪区域无效", isError = true)
                return@share
            }
            savingOrSharing = true
            exportJob = launchRegionExport(
                scope = scope,
                input = cropped,
                write = { writeCroppedToShareCache(context, it) },
                release = { if (!it.isRecycled) it.recycle() },
                discard = { context.contentResolver.delete(it, null, null) }
            ) { shareUri ->
                exportJob = null
                savingOrSharing = false
                if (shareUri != null) {
                    runCatching {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            clipData = ClipData.newRawUri("RegionScreenshot", shareUri)
                            putExtra(Intent.EXTRA_STREAM, shareUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        val chooser = Intent.createChooser(send, "分享区域截图").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(chooser)
                    }.onSuccess {
                        dismiss()
                    }.onFailure {
                        showOverlayFeedback("无法调起系统分享: ${it.message}", isError = true)
                        Toast.makeText(context, "无法调起系统分享: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    showOverlayFeedback("生成分享图片失败", isError = true)
                    Toast.makeText(context, "生成分享图片失败", Toast.LENGTH_SHORT).show()
                }
            }
            exportJob?.invokeOnCompletion {
                toolbar.post { savingOrSharing = false }
            }
        }
        val copyImageAction = copy@{
            if (savingOrSharing) return@copy
            val cropped = buildCroppedBitmap()
            if (cropped == null) {
                showOverlayFeedback("裁剪区域无效", isError = true)
                return@copy
            }
            val w = cropped.width
            val h = cropped.height
            savingOrSharing = true
            exportJob = launchRegionExport(
                scope = scope,
                input = cropped,
                write = {
                    try {
                        ImageClipboard.copy(context, it)
                        true
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        Log.w("RegionScreenshotOverlay", "Cannot copy screenshot", failure)
                        false
                    }
                },
                release = { if (!it.isRecycled) it.recycle() },
                discard = { /* Published clipboard image is retained independently of this overlay. */ }
            ) { copied ->
                exportJob = null
                savingOrSharing = false
                if (copied == true) {
                    cancelBtn.text = "完成"
                    showOverlayFeedback("✓ 已复制区域截图 ($w×$h)，可继续保存、分享或识字")
                    Toast.makeText(context, "已复制区域截图到剪贴板", Toast.LENGTH_SHORT).show()
                } else {
                    showOverlayFeedback("复制图片失败，请重试", isError = true)
                    Toast.makeText(context, "复制图片失败", Toast.LENGTH_SHORT).show()
                }
            }
            exportJob?.invokeOnCompletion {
                toolbar.post { savingOrSharing = false }
            }
        }
        val copyBtn = makeActionChip("复制") { copyImageAction() }
        val shareDirectBtn = makeActionChip("分享") { shareAction() }

        val saveBtn = makeActionChip("保存", primary = true) {
            if (savingOrSharing) return@makeActionChip
            val cropped = buildCroppedBitmap()
            if (cropped == null) {
                showOverlayFeedback("裁剪区域无效", isError = true)
                return@makeActionChip
            }
            val w = cropped.width
            val h = cropped.height
            savingOrSharing = true
            exportJob = launchRegionExport(
                scope = scope,
                input = cropped,
                write = { saveCroppedToMediaStore(context, it) },
                release = { if (!it.isRecycled) it.recycle() },
                discard = { context.contentResolver.delete(it, null, null) }
            ) { savedUri ->
                exportJob = null
                savingOrSharing = false
                if (savedUri != null) {
                    runCatching {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newUri(context.contentResolver, "RegionScreenshot", savedUri))
                    }
                    cancelBtn.text = "完成"
                    showOverlayFeedback("✓ 已保存至相册并写入剪贴板 ($w×$h)，可继续其他操作")
                    Toast.makeText(context, "区域截图已保存至相册 ($w×$h)", Toast.LENGTH_SHORT).show()
                } else {
                    showOverlayFeedback("保存区域截图失败，请检查存储权限", isError = true)
                    Toast.makeText(context, "保存区域截图失败", Toast.LENGTH_SHORT).show()
                }
            }
            exportJob?.invokeOnCompletion {
                toolbar.post { savingOrSharing = false }
            }
        }

        val gapLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = (4 * density).roundToInt() }
        val wrapLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            if (ocrBtn != null && translateBtn != null && subImageBtn != null) {
                addView(ocrBtn, gapLp)
                addView(translateBtn, gapLp)
                addView(subImageBtn, gapLp)
            }
            addView(annotateBtn, gapLp)
            addView(undoBtn, wrapLp)
        }
        val bottomRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(cancelBtn, gapLp)
            addView(fullBtn, gapLp)
            addView(copyBtn, gapLp)
            addView(shareDirectBtn, gapLp)
            addView(saveBtn, wrapLp)
        }
        toolbar.addView(
            topRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (4 * density).roundToInt() }
        )
        toolbar.addView(bottomRow, wrapLp)

        val container = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                        dismiss()
                    }
                    return true
                }
                return dispatchWithCallFallbackGuard(android.os.Build.VERSION.SDK_INT, event.keyCode, event.action) {
                    super.dispatchKeyEvent(event)
                }
            }
        }.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            addView(canvasView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(toolbar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
            addView(
                feedbackBanner,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    topMargin = (52f * density).roundToInt()
                    marginStart = (16f * density).roundToInt()
                    marginEnd = (16f * density).roundToInt()
                }
            )
        }

        val params = WindowManager.LayoutParams(
            screenshotWidth,
            screenshotHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = 0
            y = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                fitInsetsTypes = 0
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        try {
            windowManager.addView(container, params)
            rootContainer = container
            container.requestFocus()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val cb = OnBackInvokedCallback { dismiss() }
                runCatching {
                    container.findOnBackInvokedDispatcher()?.registerOnBackInvokedCallback(
                        OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                        cb
                    )
                    backCallback = cb
                }
            }
            container.post { positionToolbar() }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to show RegionScreenshotOverlay", t)
            dismiss()
        }
    }

    fun dismiss() {
        frameSnapshot?.enriched?.cancel()
        frameSnapshot = null
        exportJob?.cancel()
        exportJob = null
        val view = rootContainer
        rootContainer = null
        if (view != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                backCallback?.let { cb ->
                    runCatching { view.findOnBackInvokedDispatcher()?.unregisterOnBackInvokedCallback(cb) }
                }
            }
            backCallback = null
            runCatching { windowManager.removeView(view) }
        }
        sourceBitmap?.let { bmp ->
            sourceBitmap = null
            if (!bmp.isRecycled) bmp.recycle()
        }
        val banner = feedbackBannerView
        val hideTask = hideFeedbackRunnable
        if (banner != null && hideTask != null) {
            banner.removeCallbacks(hideTask)
        }
        hideFeedbackRunnable = null
        feedbackBannerView = null
        savingOrSharing = false
    }

    companion object {
        private const val TAG = "RegionScreenshotOverlay"

        private fun timestampToken(): String =
            SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())

        private fun saveCroppedToMediaStore(context: Context, bitmap: Bitmap): Uri? {
            val fileName = "Screenshot_Region_${timestampToken()}.png"
            val resolver = context.contentResolver
            var insertedUri: Uri? = null
            return try {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.WIDTH, bitmap.width)
                    put(MediaStore.Images.Media.HEIGHT, bitmap.height)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Screenshots")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
                insertedUri = uri
                val ok = resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } == true
                if (!ok) {
                    runCatching { resolver.delete(uri, null, null) }
                    return null
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                    check(resolver.update(uri, done, null, null) > 0) { "Unable to publish screenshot" }
                }
                uri
            } catch (t: Throwable) {
                insertedUri?.let { uri -> runCatching { resolver.delete(uri, null, null) } }
                Log.e(TAG, "saveCroppedToMediaStore failed", t)
                null
            }
        }

        private fun writeCroppedToShareCache(context: Context, bitmap: Bitmap): Uri? {
            var createdFile: File? = null
            return try {
                val dir = File(context.cacheDir, "region_screenshots").apply { mkdirs() }
                dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(4)?.forEach {
                    runCatching { it.delete() }
                }
                val file = File(dir, "Region_${timestampToken()}.png")
                createdFile = file
                FileOutputStream(file).use { out ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "Unable to encode screenshot" }
                }
                FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            } catch (t: Throwable) {
                createdFile?.let { file -> runCatching { file.delete() } }
                Log.e(TAG, "writeCroppedToShareCache failed", t)
                null
            }
        }
    }
}
