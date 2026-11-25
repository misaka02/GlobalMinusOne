package com.example.globalminusone.service.screenassistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.example.globalminusone.screenassistant.ScreenTextBlock
import com.example.globalminusone.screenassistant.ScreenTextSelectionPolicy
import com.example.globalminusone.screenassistant.SubImageResult
import com.example.globalminusone.screenassistant.TextSelectionSpan
import kotlin.math.roundToInt

/** Shared bitmap <-> view coordinate mapping for canvas rendering, loupe, and floating action capsule. */
internal class ScreenAssistantViewportGeometry(
    private val bitmapWidth: Int,
    private val bitmapHeight: Int
) {
    val bitmapRectOnView = RectF()

    fun viewToBitmapX(vx: Float): Float {
        if (bitmapRectOnView.width() <= 1f) return vx
        return (vx - bitmapRectOnView.left) * bitmapWidth.toFloat() / bitmapRectOnView.width()
    }

    fun viewToBitmapY(vy: Float): Float {
        if (bitmapRectOnView.height() <= 1f) return vy
        return (vy - bitmapRectOnView.top) * bitmapHeight.toFloat() / bitmapRectOnView.height()
    }

    fun bitmapRectToViewRectF(l: Int, t: Int, r: Int, b: Int): RectF {
        val sx = bitmapRectOnView.width() / bitmapWidth.coerceAtLeast(1).toFloat()
        val sy = bitmapRectOnView.height() / bitmapHeight.coerceAtLeast(1).toFloat()
        return RectF(
            bitmapRectOnView.left + l * sx,
            bitmapRectOnView.top + t * sy,
            bitmapRectOnView.left + r * sx,
            bitmapRectOnView.top + b * sy
        )
    }

    fun blockRenderRectBitmap(block: ScreenTextBlock): RectF =
        RectF(
            block.renderLeftPx.toFloat(),
            block.renderTopPx.toFloat(),
            block.renderRightPx.coerceAtLeast(block.renderLeftPx + 1).toFloat(),
            block.renderBottomPx.coerceAtLeast(block.renderTopPx + 1).toFloat()
        )

    fun spanViewRectF(block: ScreenTextBlock, span: TextSelectionSpan): RectF {
        val br = blockRenderRectBitmap(block)
        val (sl, sr) = ScreenTextSelectionPolicy.spanToHorizontalBounds(
            text = block.text,
            startIndex = span.startIndex,
            endIndex = span.endIndex,
            leftPx = br.left,
            rightPx = br.right,
            charSpans = block.charSpans
        )
        val (st, sb) = ScreenTextSelectionPolicy.spanToTightVerticalBounds(
            block = block,
            startIndex = span.startIndex,
            endIndex = span.endIndex
        )
        return bitmapRectToViewRectF(sl.roundToInt(), st.roundToInt(), sr.roundToInt(), sb.roundToInt())
    }
}

/**
 * Thin interactive overlay canvas delegating rendering to [ScreenAssistantCanvasRenderer]
 * and touch arbitration to [ScreenAssistantGestureController].
 */
internal class ScreenAssistantCanvasView(
    context: Context,
    private val bitmap: Bitmap,
    isCroppedRegion: Boolean,
    private val presentation: ScreenAssistantPresentation,
    geometry: ScreenAssistantViewportGeometry,
    isCurrent: () -> Boolean,
    private val inpaintedBitmapProvider: () -> Bitmap?,
    private val blurredBitmapProvider: () -> Bitmap? = { null },
    private val extractedSubImagesProvider: () -> List<SubImageResult>,
    private val safeInsetsProvider: () -> Rect,
    private val topBarBottomProvider: () -> Int,
    onHideFloatingBar: () -> Unit,
    onStateUpdated: () -> Unit,
    onSubImageAdjusted: (Int, Int, Int, Int, Int) -> Unit = { _, _, _, _, _ -> },
    private val onScrollUnderlying: ((Float, Float, Float, Float) -> Unit)? = null,
    onCanvasTouchActiveChanged: (Boolean) -> Unit = {},
    onSnapSubImageAtRegion: (List<Pair<Int, Int>>) -> Unit = {}
) : View(context) {

    private val renderer = ScreenAssistantCanvasRenderer(context, bitmap, isCroppedRegion, geometry)
    private val gestureController = ScreenAssistantGestureController(
        context = context,
        bitmap = bitmap,
        isCroppedRegion = isCroppedRegion,
        presentation = presentation,
        geometry = geometry,
        renderer = renderer,
        isCurrent = isCurrent,
        extractedSubImagesProvider = extractedSubImagesProvider,
        viewScreenLocation = ::getLocationOnScreen,
        viewSize = { width.toFloat().coerceAtLeast(1f) to height.toFloat().coerceAtLeast(1f) },
        canScrollUnderlying = { onScrollUnderlying != null },
        onScrollUnderlying = { x0, y0, x1, y1 -> onScrollUnderlying?.invoke(x0, y0, x1, y1) },
        onHideFloatingBar = onHideFloatingBar,
        onCanvasTouchActiveChanged = onCanvasTouchActiveChanged,
        onStateUpdated = {
            onStateUpdated()
            invalidate()
        },
        onSubImageAdjusted = onSubImageAdjusted,
        onSnapSubImageAtRegion = onSnapSubImageAtRegion,
        onInvalidate = ::invalidate
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (bitmap.isRecycled) return
        renderer.drawFrame(
            canvas = canvas,
            viewW = width.toFloat().coerceAtLeast(1f),
            viewH = height.toFloat().coerceAtLeast(1f),
            safeInsets = safeInsetsProvider(),
            topReservedBottomPx = topBarBottomProvider(),
            presentation = presentation,
            inpaintedBitmap = inpaintedBitmapProvider(),
            blurredBitmap = blurredBitmapProvider(),
            extractedSubImages = extractedSubImagesProvider(),
            overlayState = gestureController.snapshotOverlayState()
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = gestureController.onTouchEvent(event)
}
