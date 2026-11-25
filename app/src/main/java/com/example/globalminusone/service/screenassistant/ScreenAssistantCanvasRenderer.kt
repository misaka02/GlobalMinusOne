package com.example.globalminusone.service.screenassistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import com.example.globalminusone.screenassistant.ScreenTextBlock
import com.example.globalminusone.screenassistant.ScreenTextInpainter
import com.example.globalminusone.screenassistant.ScreenTextSelectionPolicy
import com.example.globalminusone.screenassistant.SubImageResult
import com.example.globalminusone.screenassistant.TextSelectionSpan
import com.example.globalminusone.service.screenassistant.ScreenAssistantPresentation.FocusState
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure rendering engine for the unified Breeno canvas:
 * - IDLE: 100% sharp original capture + whisper-light hairlines on tappable sub-images.
 * - TEXT_SELECTED: soft frosted blur behind sharp rounded text chips, blue spans, rotated teardrops, 2x loupe.
 * - IMAGE_SELECTED: soft frosted blur behind ONLY the active white-bordered cutout (+ crop handles / lightbox).
 * - TRANSLATE: background-inpainted screenshot + typography-matched source translations.
 */
internal class ScreenAssistantCanvasRenderer(
    private val context: Context,
    private val bitmap: Bitmap,
    private val isCroppedRegion: Boolean,
    private val geometry: ScreenAssistantViewportGeometry
) {
    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = max(1.4f, 1.5f * density)
    }
    private val hairlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = max(1f, 1.0f * density)
    }
    private val handleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2B7DE9") }
    private val handleRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = max(1.6f, 1.8f * density)
        color = Color.WHITE
    }
    private val loupeRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = max(1.8f, 2f * density)
        color = Color.WHITE
    }
    private val loupeOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = max(3.5f, 4f * density)
        color = Color.argb(95, 0, 0, 0)
    }
    private val cutoutHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = max(3.5f, 4f * density)
        color = Color.argb(64, 0, 0, 0)
    }
    private val tagBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(220, 22, 28, 40) }
    private val tagTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, context.resources.displayMetrics)
        typeface = Typeface.DEFAULT_BOLD
    }
    private val snapStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = max(2.6f, 2.8f * density)
        color = Color.parseColor("#36CFC9")
    }

    private val loupeClipPath = Path()
    private val cutoutClipPath = Path()
    private val cutoutSrcRect = Rect()
    private val handlePath = Path()
    private val handleOvalRect = RectF()
    private val snapStrokePath = Path()
    private val previewRectF = RectF()
    private val pillRectF = RectF()

    data class FrameOverlayState(
        val dragSelectOrHandle: Boolean = false,
        val dragViewX: Float = 0f,
        val dragViewY: Float = 0f,
        val dragBx: Float = 0f,
        val dragBy: Float = 0f,
        val draggingSubHandle: Int = 0,
        val draggingSubIndex: Int = -1,
        val subPreviewL: Int = 0,
        val subPreviewT: Int = 0,
        val subPreviewR: Int = 0,
        val subPreviewB: Int = 0,
        val draggingUnderlyingScroll: Boolean = false,
        val scrollDownX: Float = 0f,
        val scrollDownY: Float = 0f,
        val scrollCurX: Float = 0f,
        val scrollCurY: Float = 0f,
        val draggingSnapStroke: Boolean = false,
        val snapStrokePoints: List<Pair<Int, Int>> = emptyList()
    )

    var hasHandles = false
        private set
    var startHandleX = 0f
        private set
    var startHandleY = 0f
        private set
    var endHandleX = 0f
        private set
    var endHandleY = 0f
        private set

    fun drawFrame(
        canvas: Canvas,
        viewW: Float,
        viewH: Float,
        safeInsets: Rect,
        topReservedBottomPx: Int,
        presentation: ScreenAssistantPresentation,
        inpaintedBitmap: Bitmap?,
        blurredBitmap: Bitmap?,
        extractedSubImages: List<SubImageResult>,
        overlayState: FrameOverlayState
    ) {
        if (bitmap.isRecycled) return
        hasHandles = false
        canvas.drawColor(if (isCroppedRegion) Color.argb(232, 12, 14, 20) else Color.BLACK)
        updateViewportRect(viewW, viewH, safeInsets, presentation)

        val focus = presentation.focusState
        val showTranslated = focus == FocusState.TRANSLATE &&
            !presentation.holdOriginalPreview &&
            !presentation.showAllOriginalInTranslate
        val blurReady = blurredBitmap?.takeIf { !it.isRecycled }
        // Keep IDLE & TEXT_SELECTED 100% sharp; apply frosted background blur ONLY when focusing an image.
        val useSoftBlur = focus == FocusState.IMAGE_SELECTED &&
            extractedSubImages.getOrNull(presentation.activeSubImageIndex)?.candidate?.leftPx?.let { it >= 0 } == true

        val baseBitmap = when {
            useSoftBlur && blurReady != null -> blurReady
            showTranslated && inpaintedBitmap?.isRecycled == false -> inpaintedBitmap
            else -> bitmap
        }
        canvas.drawBitmap(baseBitmap, null, geometry.bitmapRectOnView, bitmapPaint)

        when (focus) {
            FocusState.IDLE -> drawIdleState(canvas, presentation, extractedSubImages)
            FocusState.TEXT_SELECTED -> drawTextSelectedState(canvas, viewW, viewH, topReservedBottomPx, safeInsets.top, presentation, overlayState)
            FocusState.IMAGE_SELECTED -> drawImageSelectedState(canvas, viewW, viewH, safeInsets, presentation, extractedSubImages, overlayState)
            FocusState.TRANSLATE -> drawTranslateState(canvas, presentation, inpaintedBitmap)
        }

        if (overlayState.draggingSnapStroke && overlayState.snapStrokePoints.size >= 2) {
            snapStrokePath.reset()
            overlayState.snapStrokePoints.forEachIndexed { idx, (px, py) ->
                val pr = geometry.bitmapRectToViewRectF(px, py, px + 1, py + 1)
                if (idx == 0) snapStrokePath.moveTo(pr.left, pr.top) else snapStrokePath.lineTo(pr.left, pr.top)
            }
            canvas.drawPath(snapStrokePath, snapStrokePaint)
        }
        if (overlayState.draggingUnderlyingScroll) {
            drawUnderlyingScrollGuide(canvas, viewW, safeInsets.top, overlayState)
        }
    }

    private fun updateViewportRect(vw: Float, vh: Float, safeInsets: Rect, presentation: ScreenAssistantPresentation) {
        val baseL: Float; val baseT: Float; val baseW: Float; val baseH: Float
        if (isCroppedRegion) {
            val padH = dp(12f)
            val topReserved = safeInsets.top + dp(56f)
            val botReserved = safeInsets.bottom + dp(136f)
            val availW = (vw - padH * 2f).coerceAtLeast(64f)
            val availH = (vh - topReserved - botReserved).coerceAtLeast(64f)
            val ratio = ScreenTextSelectionPolicy.computeCroppedDisplayScale(availW, availH, bitmap.width, bitmap.height)
            baseW = bitmap.width * ratio
            baseH = bitmap.height * ratio
            baseL = (vw - baseW) * 0.5f
            baseT = topReserved + (availH - baseH) * 0.5f
        } else {
            val fit = ScreenTextSelectionPolicy.computeFullDisplayRect(vw, vh, bitmap.width, bitmap.height)
            baseL = fit[0]; baseT = fit[1]; baseW = fit[2] - fit[0]; baseH = fit[3] - fit[1]
        }
        val zoom = presentation.zoomScale.coerceIn(1f, 3.5f)
        val dw = baseW * zoom
        val dh = baseH * zoom
        val extraPanX = max(dp(120f), min(vw, dw) * 0.42f)
        val extraPanY = max(dp(160f), min(vh, dh) * 0.45f)
        val maxPanX = max(0f, (dw - vw) * 0.5f) + extraPanX
        val maxPanY = max(0f, (dh - vh) * 0.5f) + extraPanY
        presentation.panX = presentation.panX.coerceIn(-maxPanX, maxPanX)
        presentation.panY = presentation.panY.coerceIn(-maxPanY, maxPanY)
        val cx = baseL + baseW * 0.5f + presentation.panX
        val cy = baseT + baseH * 0.5f + presentation.panY
        geometry.bitmapRectOnView.set(cx - dw * 0.5f, cy - dh * 0.5f, cx + dw * 0.5f, cy + dh * 0.5f)
    }

    private fun drawIdleState(canvas: Canvas, presentation: ScreenAssistantPresentation, subs: List<SubImageResult>) {
        // Breeno IDLE: 100% sharp screenshot with soft light highlights on recognized text & real images.
        if (presentation.showTextHighlights) {
            fillPaint.shader = null
            fillPaint.color = Color.argb(34, 43, 125, 233)
            hairlinePaint.strokeWidth = max(1f, 0.9f * density)
            hairlinePaint.color = Color.argb(72, 96, 174, 255)
            for (block in presentation.blocks) {
                val br = geometry.blockRenderRectBitmap(block)
                val r = geometry.bitmapRectToViewRectF(br.left.roundToInt(), br.top.roundToInt(), br.right.roundToInt(), br.bottom.roundToInt())
                val corner = min(dp(4f), min(r.width(), r.height()) * 0.25f)
                canvas.drawRoundRect(r, corner, corner, fillPaint)
                canvas.drawRoundRect(r, corner, corner, hairlinePaint)
            }
        }
        for (sub in subs) {
            val c = sub.candidate
            if (c.leftPx < 0 || c.rightPx <= c.leftPx || c.topPx < 0 || c.bottomPx <= c.topPx) continue
            val r = geometry.bitmapRectToViewRectF(c.leftPx, c.topPx, c.rightPx, c.bottomPx)
            val cornerR = min(dp(10f), min(r.width(), r.height()) * 0.14f)
            hairlinePaint.strokeWidth = max(1.8f, 1.8f * density)
            hairlinePaint.color = Color.argb(92, 0, 0, 0)
            canvas.drawRoundRect(r, cornerR, cornerR, hairlinePaint)
            hairlinePaint.strokeWidth = max(1.25f, 1.25f * density)
            hairlinePaint.color = if (sub.isUnderlyingOriginal) Color.parseColor("#6EE7B7") else Color.parseColor("#7DD3FC")
            canvas.drawRoundRect(r, cornerR, cornerR, hairlinePaint)
        }
    }

    private fun drawTextSelectedState(
        canvas: Canvas,
        vw: Float,
        vh: Float,
        topReservedBottomPx: Int,
        safeTopPx: Int,
        presentation: ScreenAssistantPresentation,
        overlayState: FrameOverlayState
    ) {
        fillPaint.shader = null
        for (block in presentation.blocks) {
            val span = presentation.selectionSpans[block.id] ?: continue
            val sr = geometry.spanViewRectF(block, span)
            fillPaint.color = Color.argb(88, 43, 125, 233)
            canvas.drawRoundRect(sr, dp(3f), dp(3f), fillPaint)
            hairlinePaint.strokeWidth = max(1f, 1.0f * density)
            hairlinePaint.color = Color.argb(210, 77, 163, 255)
            canvas.drawLine(sr.left + dp(1f), sr.bottom, sr.right - dp(1f), sr.bottom, hairlinePaint)
        }

        val ordered = presentation.blocks.mapNotNull { b -> presentation.selectionSpans[b.id]?.let { b to it } }
        if (ordered.isNotEmpty()) {
            val firstRect = geometry.spanViewRectF(ordered.first().first, ordered.first().second)
            val lastRect = geometry.spanViewRectF(ordered.last().first, ordered.last().second)
            val rPx = dp(8f)
            val rotDeg = 32f
            val rad = Math.toRadians(rotDeg.toDouble())
            val dist = rPx * 1.4142135f
            val tipDx = (dist * kotlin.math.sin(rad)).toFloat()
            val tipDy = (dist * kotlin.math.cos(rad)).toFloat()
            startHandleX = (firstRect.left - tipDx).coerceIn(dp(12f), max(dp(12f), vw - dp(12f)))
            startHandleY = (firstRect.bottom + tipDy).coerceAtMost(vh - dp(12f))
            endHandleX = (lastRect.right + tipDx).coerceIn(dp(12f), max(dp(12f), vw - dp(12f)))
            endHandleY = (lastRect.bottom + tipDy).coerceAtMost(vh - dp(12f))
            hasHandles = true

            val prevW = borderPaint.strokeWidth
            borderPaint.color = Color.parseColor("#2B7DE9")
            borderPaint.strokeWidth = max(1.8f, 2.0f * density)
            canvas.drawLine(firstRect.left, firstRect.top, firstRect.left, firstRect.bottom, borderPaint)
            canvas.drawLine(lastRect.right, lastRect.top, lastRect.right, lastRect.bottom, borderPaint)
            borderPaint.strokeWidth = prevW
            drawTeardropHandle(canvas, startHandleX, startHandleY, rPx, rotDeg)
            drawTeardropHandle(canvas, endHandleX, endHandleY, rPx, -rotDeg)
        }
        if (overlayState.dragSelectOrHandle) drawLoupe(canvas, vw, vh, topReservedBottomPx, safeTopPx, presentation, overlayState)
    }

    private fun drawImageSelectedState(
        canvas: Canvas,
        vw: Float,
        vh: Float,
        safeInsets: Rect,
        presentation: ScreenAssistantPresentation,
        subs: List<SubImageResult>,
        overlayState: FrameOverlayState
    ) {
        fillPaint.shader = null
        fillPaint.color = Color.argb(26, 16, 20, 28)
        canvas.drawRect(geometry.bitmapRectOnView, fillPaint)
        val idx = presentation.activeSubImageIndex
        val activeSub = subs.getOrNull(idx) ?: return
        val c = activeSub.candidate
        if (c.leftPx >= 0 && c.rightPx > c.leftPx && c.topPx >= 0 && c.bottomPx > c.topPx) {
            val adjusting = overlayState.draggingSubHandle != 0 && idx == overlayState.draggingSubIndex
            val dL = (if (adjusting) overlayState.subPreviewL else c.leftPx).coerceIn(0, (bitmap.width - 2).coerceAtLeast(0))
            val dT = (if (adjusting) overlayState.subPreviewT else c.topPx).coerceIn(0, (bitmap.height - 2).coerceAtLeast(0))
            val dR = (if (adjusting) overlayState.subPreviewR else c.rightPx).coerceIn(dL + 1, bitmap.width.coerceAtLeast(dL + 1))
            val dB = (if (adjusting) overlayState.subPreviewB else c.bottomPx).coerceIn(dT + 1, bitmap.height.coerceAtLeast(dT + 1))
            val r = geometry.bitmapRectToViewRectF(dL, dT, dR, dB)
            val cornerR = min(dp(10f), min(r.width(), r.height()) * 0.14f)

            canvas.save()
            cutoutClipPath.reset()
            cutoutClipPath.addRoundRect(r, cornerR, cornerR, Path.Direction.CW)
            canvas.clipPath(cutoutClipPath)
            cutoutSrcRect.set(dL, dT, dR, dB)
            canvas.drawBitmap(bitmap, cutoutSrcRect, r, bitmapPaint)
            canvas.restore()

            canvas.drawRoundRect(r, cornerR, cornerR, cutoutHaloPaint)
            val prevW = borderPaint.strokeWidth
            borderPaint.color = Color.WHITE
            borderPaint.strokeWidth = max(2f, dp(2.0f))
            canvas.drawRoundRect(r, cornerR, cornerR, borderPaint)
            borderPaint.strokeWidth = prevW

            if (adjusting) {
                val dim = "${dR - dL}×${dB - dT}"
                pillRectF.set(r.left + dp(4f), r.top + dp(4f), r.left + dp(4f) + tagTextPaint.measureText(dim) + dp(16f), r.top + dp(24f))
                canvas.drawRoundRect(pillRectF, dp(10f), dp(10f), tagBgPaint)
                canvas.drawText(dim, pillRectF.left + dp(8f), pillRectF.top + dp(14f), tagTextPaint)
            }
            if (presentation.adjustingSubImageCrop || adjusting) {
                val hRad = dp(5.5f)
                borderPaint.color = Color.parseColor("#2B7DE9")
                val pts = arrayOf(r.left to r.top, r.centerX() to r.top, r.right to r.top, r.right to r.centerY(),
                    r.right to r.bottom, r.centerX() to r.bottom, r.left to r.bottom, r.left to r.centerY())
                for ((hx, hy) in pts) {
                    canvas.drawCircle(hx, hy, hRad, handleRingPaint)
                    canvas.drawCircle(hx, hy, hRad - dp(1.4f), borderPaint)
                }
            }
        }
        if ((c.leftPx < 0 || presentation.previewingActiveSubImage) && !activeSub.bitmap.isRecycled) {
            fillPaint.shader = null
            fillPaint.color = Color.argb(204, 8, 12, 18)
            canvas.drawRect(0f, 0f, vw, vh, fillPaint)
            val topSafe = safeInsets.top + dp(68f)
            val botSafe = safeInsets.bottom + dp(155f)
            val availW = (vw - dp(36f)).coerceAtLeast(80f)
            val availH = (vh - topSafe - botSafe).coerceAtLeast(80f)
            val sc = ScreenTextSelectionPolicy.computeCroppedDisplayScale(availW, availH, activeSub.bitmap.width, activeSub.bitmap.height)
            val dw = activeSub.bitmap.width * sc; val dh = activeSub.bitmap.height * sc
            previewRectF.set((vw - dw) * 0.5f, topSafe + (availH - dh) * 0.5f, (vw + dw) * 0.5f, topSafe + (availH + dh) * 0.5f)
            borderPaint.color = if (activeSub.isUnderlyingOriginal) Color.parseColor("#52C41A") else Color.parseColor("#66B2FF")
            canvas.drawBitmap(activeSub.bitmap, null, previewRectF, bitmapPaint)
            canvas.drawRoundRect(previewRectF, dp(8f), dp(8f), borderPaint)
            if (presentation.previewingActiveSubImage && c.leftPx >= 0) {
                val cap = "实际提取预览 · ${activeSub.bitmap.width}×${activeSub.bitmap.height} · 点击返回选框"
                val cw = tagTextPaint.measureText(cap) + dp(20f)
                val cl = ((vw - cw) * 0.5f).coerceAtLeast(dp(12f))
                val ct = (previewRectF.top - dp(28f)).coerceAtLeast(safeInsets.top + dp(10f))
                pillRectF.set(cl, ct, (cl + cw).coerceAtMost(vw - dp(12f)), ct + dp(22f))
                canvas.drawRoundRect(pillRectF, dp(11f), dp(11f), tagBgPaint)
                canvas.drawText(cap, pillRectF.left + dp(10f), pillRectF.top + dp(15f), tagTextPaint)
            }
        }
    }

    private fun drawTranslateState(canvas: Canvas, presentation: ScreenAssistantPresentation, inpaintedBitmap: Bitmap?) {
        if (presentation.holdOriginalPreview || presentation.showAllOriginalInTranslate) return
        val hasInpainted = inpaintedBitmap?.isRecycled == false
        val syScale = (geometry.bitmapRectOnView.height() / bitmap.height.coerceAtLeast(1)).coerceAtLeast(0.25f)
        val activeBlocks = presentation.translateModeBlocks()
        for (block in activeBlocks) {
            val translated = block.translatedText?.takeIf { it.isNotBlank() } ?: continue
            if (!ScreenTextSelectionPolicy.shouldRenderTranslatedOverlay(block.text, translated)) continue
            val br = geometry.blockRenderRectBitmap(block)
            val (slotL, slotR) = ScreenTextSelectionPolicy.computeTranslateSlotHorizontalBounds(
                block, br.left.roundToInt(), br.top.roundToInt(), br.right.roundToInt(), br.bottom.roundToInt(), activeBlocks, bitmap.width
            )
            val r = geometry.bitmapRectToViewRectF(slotL, br.top.roundToInt(), slotR, br.bottom.roundToInt())
            if (block.id in presentation.showingOriginalIds) {
                val src = Rect(
                    min(block.leftPx, block.renderLeftPx).coerceIn(0, bitmap.width - 1),
                    min(block.topPx, block.renderTopPx).coerceIn(0, bitmap.height - 1),
                    max(block.rightPx, block.renderRightPx).coerceIn(1, bitmap.width),
                    max(block.bottomPx, block.renderBottomPx).coerceIn(1, bitmap.height)
                )
                val origR = geometry.bitmapRectToViewRectF(src.left, src.top, src.right, src.bottom)
                canvas.drawBitmap(bitmap, src, origR, bitmapPaint)
                hairlinePaint.color = Color.argb(180, 82, 196, 26)
                canvas.drawRoundRect(origR, dp(3f), dp(3f), hairlinePaint)
            } else {
                if (!hasInpainted || !block.uniformBg) {
                    val drawR = RectF(r.left - dp(2f), r.top - dp(1.5f), r.right + dp(2f), r.bottom + dp(1.5f))
                    val alpha = if (!block.uniformBg) 232 else 252
                    val cTop = Color.argb(alpha, Color.red(block.bgColorArgb), Color.green(block.bgColorArgb), Color.blue(block.bgColorArgb))
                    val cBot = Color.argb(alpha, Color.red(block.bgBottomColorArgb), Color.green(block.bgBottomColorArgb), Color.blue(block.bgBottomColorArgb))
                    fillPaint.shader = LinearGradient(drawR.left, drawR.top, drawR.left, drawR.bottom, cTop, cBot, Shader.TileMode.CLAMP)
                    canvas.drawRoundRect(drawR, dp(4f), dp(4f), fillPaint)
                    fillPaint.shader = null
                }
                drawSourceMatchedTranslatedText(canvas, translated, r, block, syScale)
            }
            if (block.id == presentation.activeTranslateBlockId) {
                fillPaint.shader = null
                fillPaint.color = Color.argb(40, 77, 163, 255)
                canvas.drawRoundRect(r, dp(4f), dp(4f), fillPaint)
                borderPaint.color = Color.parseColor("#4DA3FF")
                canvas.drawRoundRect(r, dp(4f), dp(4f), borderPaint)
            }
        }
    }

    private fun drawTeardropHandle(canvas: Canvas, cx: Float, cy: Float, radius: Float, rotDeg: Float) {
        val rad = Math.toRadians(rotDeg.toDouble())
        val dist = radius * 1.4142135f
        val tipX = cx + (dist * kotlin.math.sin(rad)).toFloat()
        val tipY = cy - (dist * kotlin.math.cos(rad)).toFloat()
        handleOvalRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        handlePath.reset()
        handlePath.moveTo(tipX, tipY)
        handlePath.arcTo(handleOvalRect, -45f + rotDeg, 270f, false)
        handlePath.close()
        canvas.drawPath(handlePath, handleFillPaint)
        canvas.drawPath(handlePath, handleRingPaint)
    }

    private fun drawLoupe(
        canvas: Canvas,
        vw: Float,
        vh: Float,
        topReservedBottomPx: Int,
        safeTopPx: Int,
        presentation: ScreenAssistantPresentation,
        st: FrameOverlayState
    ) {
        if (bitmap.isRecycled) return
        val lw = dp(138f); val lh = dp(48f)
        val minTop = (topReservedBottomPx.takeIf { it > safeTopPx } ?: (safeTopPx + dp(56f).roundToInt())).toFloat() + dp(8f)
        val cx = st.dragViewX.coerceIn(dp(12f) + lw * 0.5f, max(dp(12f) + lw * 0.5f, vw - dp(12f) - lw * 0.5f))
        var cyTop = st.dragViewY - dp(72f) - lh
        if (cyTop < minTop) cyTop = min(vh - lh - dp(12f), st.dragViewY + dp(40f))
        previewRectF.set(cx - lw * 0.5f, cyTop, cx + lw * 0.5f, cyTop + lh)
        val sx = (geometry.bitmapRectOnView.width() / bitmap.width.coerceAtLeast(1)).coerceAtLeast(0.25f)
        val sy = (geometry.bitmapRectOnView.height() / bitmap.height.coerceAtLeast(1)).coerceAtLeast(0.25f)
        val halfSrcW = (lw / 2f / sx) * 0.5f; val halfSrcH = (lh / 2f / sy) * 0.5f
        val sL = (st.dragBx - halfSrcW).roundToInt().coerceIn(0, (bitmap.width - 4).coerceAtLeast(0))
        val sT = (st.dragBy - halfSrcH).roundToInt().coerceIn(0, (bitmap.height - 4).coerceAtLeast(0))
        cutoutSrcRect.set(sL, sT, (sL + (halfSrcW * 2f).roundToInt()).coerceIn(sL + 2, bitmap.width), (sT + (halfSrcH * 2f).roundToInt()).coerceIn(sT + 2, bitmap.height))
        canvas.drawRoundRect(previewRectF, lh * 0.5f, lh * 0.5f, loupeOuterPaint)
        canvas.save()
        loupeClipPath.reset(); loupeClipPath.addRoundRect(previewRectF, lh * 0.5f, lh * 0.5f, Path.Direction.CW)
        canvas.clipPath(loupeClipPath)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(bitmap, cutoutSrcRect, previewRectF, bitmapPaint)
        borderPaint.color = Color.argb(215, 43, 125, 233)
        canvas.drawLine(previewRectF.centerX(), previewRectF.top + dp(6f), previewRectF.centerX(), previewRectF.bottom - dp(6f), borderPaint)
        canvas.restore()
        canvas.drawRoundRect(previewRectF, lh * 0.5f, lh * 0.5f, loupeRingPaint)
        val snippet = presentation.selectedText().replace("\n", " ").trim()
        if (snippet.isNotEmpty()) {
            val pv = if (snippet.length > 22) "已选: …${snippet.takeLast(20)}" else "已选: $snippet"
            val tw = tagTextPaint.measureText(pv) + dp(18f)
            val pl = (previewRectF.centerX() - tw * 0.5f).coerceIn(dp(12f), max(dp(12f), vw - dp(12f) - tw))
            val pt = if (previewRectF.top >= minTop + dp(26f)) previewRectF.top - dp(24f) else previewRectF.bottom + dp(6f)
            pillRectF.set(pl, pt, pl + tw, pt + dp(20f))
            canvas.drawRoundRect(pillRectF, dp(10f), dp(10f), tagBgPaint)
            canvas.drawText(pv, pillRectF.left + dp(9f), pillRectF.top + dp(14f), tagTextPaint)
        }
    }

    private fun drawUnderlyingScrollGuide(canvas: Canvas, vw: Float, safeTopPx: Int, st: FrameOverlayState) {
        val dx = st.scrollCurX - st.scrollDownX; val dy = st.scrollCurY - st.scrollDownY
        val thresh = dp(28f)
        val ready = hypot(dx, dy) >= thresh
        val accent = if (ready) Color.parseColor("#6CE5FF") else Color.argb(180, 210, 224, 244)
        val prevW = borderPaint.strokeWidth; val prevFill = handleFillPaint.color; val prevBg = tagBgPaint.color
        borderPaint.color = accent; borderPaint.strokeWidth = dp(2.8f)
        canvas.drawLine(st.scrollDownX, st.scrollDownY, st.scrollCurX, st.scrollCurY, borderPaint)
        borderPaint.strokeWidth = prevW
        handleFillPaint.color = accent
        canvas.drawCircle(st.scrollDownX, st.scrollDownY, dp(4.5f), handleFillPaint)
        canvas.drawCircle(st.scrollCurX, st.scrollCurY, dp(7f), handleFillPaint)
        canvas.drawCircle(st.scrollCurX, st.scrollCurY, dp(7f), handleRingPaint)
        handleFillPaint.color = prevFill
        val label = com.example.globalminusone.screenassistant.ScreenAssistantPolicy.formatScrollHintLabel(dx, dy, thresh)
        val pw = tagTextPaint.measureText(label) + dp(24f); val ph = dp(30f)
        val pl = ((vw - pw) * 0.5f).coerceAtLeast(dp(12f)); val pt = safeTopPx + dp(58f)
        pillRectF.set(pl, pt, pl + pw, pt + ph)
        tagBgPaint.color = if (ready) Color.argb(232, 16, 48, 72) else Color.argb(215, 22, 28, 40)
        canvas.drawRoundRect(pillRectF, ph * 0.5f, ph * 0.5f, tagBgPaint)
        tagBgPaint.color = prevBg
        hairlinePaint.color = accent
        canvas.drawRoundRect(pillRectF, ph * 0.5f, ph * 0.5f, hairlinePaint)
        val fm = tagTextPaint.fontMetrics
        canvas.drawText(label, pillRectF.left + dp(12f), pillRectF.centerY() - (fm.ascent + fm.descent) * 0.5f, tagTextPaint)
    }

    private fun drawSourceMatchedTranslatedText(canvas: Canvas, translated: String, rect: RectF, block: ScreenTextBlock, syScale: Float) {
        val boxW = rect.width().toInt().coerceAtLeast(18); val boxH = rect.height().toInt().coerceAtLeast(12)
        textPaint.color = block.textColorArgb
        textPaint.typeface = if (block.isBoldHint) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        textPaint.textScaleX = 1f
        if (!block.uniformBg) {
            val sc = if (ScreenTextSelectionPolicy.wcagContrast(block.textColorArgb, Color.BLACK) > 4f) Color.argb(140, 0, 0, 0) else Color.argb(120, 255, 255, 255)
            textPaint.setShadowLayer(2f * density, 0f, density, sc)
        } else textPaint.clearShadowLayer()
        val align = when (block.alignmentHint) {
            ScreenTextInpainter.ALIGN_CENTER -> Layout.Alignment.ALIGN_CENTER
            ScreenTextInpainter.ALIGN_RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_NORMAL
        }
        val lc = block.sourceLineCount.coerceAtLeast(1); val perLine = rect.height() / lc.toFloat()
        val inkH = if (block.inkHeightPx > 0) block.inkHeightPx * syScale else perLine * 0.82f
        val dm = context.resources.displayMetrics
        val maxPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 34f, dm)
        val minPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 8.5f, dm)
        var sizePx = (inkH * 1.04f).coerceIn(minPx, max(minPx, min(maxPx, perLine * 1.12f)))
        val single = lc == 1 && !translated.contains('\n'); var sxChosen = 1f; var layout: StaticLayout
        val lineMult = if (single) 1.02f else 1.12f
        while (true) {
            textPaint.textSize = sizePx; textPaint.textScaleX = 1f
            val maxLines = if (single) 1 else (boxH / (sizePx * lineMult).coerceAtLeast(1f)).toInt().coerceAtLeast(lc)
            val probe = StaticLayout.Builder.obtain(translated, 0, translated.length, textPaint, boxW).setAlignment(align).setIncludePad(false).setLineSpacing(0f, lineMult).build()
            if (single) {
                if (probe.lineCount == 1 && probe.height <= boxH + dp(6f)) { layout = probe; break }
                var fitted: StaticLayout? = null
                for (st in floatArrayOf(0.95f, 0.90f, 0.85f)) {
                    textPaint.textScaleX = st
                    val pt = StaticLayout.Builder.obtain(translated, 0, translated.length, textPaint, boxW).setAlignment(align).setIncludePad(false).setLineSpacing(0f, lineMult).build()
                    if (pt.lineCount == 1 && pt.height <= boxH + dp(6f)) { sxChosen = st; fitted = pt; break }
                }
                textPaint.textScaleX = 1f
                if (fitted != null) { layout = fitted; break }
            } else if (probe.height <= boxH + dp(6f) && probe.lineCount <= maxLines) { layout = probe; break }
            if (sizePx <= minPx) {
                textPaint.textScaleX = if (single) 0.84f else 1f; sxChosen = textPaint.textScaleX
                val eff = if (single) 1 else (boxH / (sizePx * lineMult).coerceAtLeast(1f)).toInt().coerceAtLeast(1)
                layout = StaticLayout.Builder.obtain(translated, 0, translated.length, textPaint, boxW).setAlignment(align).setIncludePad(false)
                    .setLineSpacing(0f, lineMult).setMaxLines(eff).setEllipsize(TextUtils.TruncateAt.END).build()
                break
            }
            sizePx = (sizePx * 0.89f).coerceAtLeast(minPx)
        }
        textPaint.textScaleX = sxChosen
        canvas.save()
        val dy = if (layout.lineCount == 1) {
            val fm = textPaint.fontMetrics; val mid = layout.getLineBaseline(0).toFloat() + (fm.ascent + fm.descent) * 0.45f
            (rect.centerY() - mid).coerceIn(rect.top - dp(4f), max(rect.top - dp(4f), rect.bottom - minPx * 0.5f))
        } else rect.top
        canvas.translate(rect.left, dy)
        canvas.clipRect(0f, -dp(2f), rect.width(), rect.height() + dp(2f))
        layout.draw(canvas); canvas.restore()
        textPaint.textScaleX = 1f; textPaint.clearShadowLayer()
    }
}
