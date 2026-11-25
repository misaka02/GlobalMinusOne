package com.example.globalminusone.service.screenassistant

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import com.example.globalminusone.screenassistant.ScreenAssistantPolicy
import com.example.globalminusone.screenassistant.ScreenTextBlock
import com.example.globalminusone.screenassistant.ScreenTextSelectionPolicy
import com.example.globalminusone.screenassistant.SubImageResult
import com.example.globalminusone.screenassistant.TextSelectionSpan
import com.example.globalminusone.service.ScreenAssistantMode
import com.example.globalminusone.service.screenassistant.ScreenAssistantPresentation.FocusState
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Unified single-canvas gesture arbiter:
 * - Any tap/drag handles text selection, cover/photo spotlight, circle-to-snap, pinch-zoom, and underlying scroll without tab switching.
 */
internal class ScreenAssistantGestureController(
    context: Context,
    private val bitmap: Bitmap,
    private val isCroppedRegion: Boolean,
    private val presentation: ScreenAssistantPresentation,
    private val geometry: ScreenAssistantViewportGeometry,
    private val renderer: ScreenAssistantCanvasRenderer,
    private val isCurrent: () -> Boolean,
    private val extractedSubImagesProvider: () -> List<SubImageResult>,
    private val viewScreenLocation: (IntArray) -> Unit,
    private val viewSize: () -> Pair<Float, Float>,
    private val canScrollUnderlying: () -> Boolean,
    private val onScrollUnderlying: (Float, Float, Float, Float) -> Unit,
    private val onHideFloatingBar: () -> Unit,
    private val onCanvasTouchActiveChanged: (Boolean) -> Unit,
    private val onStateUpdated: () -> Unit,
    private val onSubImageAdjusted: (Int, Int, Int, Int, Int) -> Unit,
    private val onSnapSubImageAtRegion: (List<Pair<Int, Int>>) -> Unit,
    private val onInvalidate: () -> Unit
) {
    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private var draggingSelect = false
    private var draggingPan = false
    private var draggingUnderlyingScroll = false
    private var scrollCurX = 0f
    private var scrollCurY = 0f
    private var multiTouchSession = false
    private var prevFocusX = 0f
    private var prevFocusY = 0f
    private var prevPanX = 0f
    private var prevPanY = 0f
    private var lastTapUpTimeMs = 0L
    private var lastTapUpX = 0f
    private var lastTapUpY = 0f
    private var draggingHandle = 0 // 0=none, 1=start, 2=end
    private var anchorBlockIdx = -1
    private var anchorCharIdx = 0
    private var downBlockIdx = -1
    private var downCharIdx = 0
    private var downX = 0f
    private var downY = 0f
    private var dragViewX = 0f
    private var dragViewY = 0f
    private var dragBx = 0f
    private var dragBy = 0f

    private var draggingSubHandle = 0 // 0=none, 1..8
    private var draggingSubIndex = -1
    private var subStartL = 0
    private var subStartT = 0
    private var subStartR = 0
    private var subStartB = 0
    private var subPreviewL = 0
    private var subPreviewT = 0
    private var subPreviewR = 0
    private var subPreviewB = 0
    private var subStartBx = 0f
    private var subStartBy = 0f
    private var draggingSnapStroke = false
    private val snapStrokePoints = ArrayList<Pair<Int, Int>>(32)
    private val snapViewPoints = ArrayList<Pair<Float, Float>>(32)

    private val scaleGestureDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val oldScale = presentation.zoomScale.coerceIn(1f, 3.5f)
                val newScale = (oldScale * detector.scaleFactor).coerceIn(1f, 3.5f)
                val ratio = newScale / oldScale
                if (abs(ratio - 1f) > 0.0005f) {
                    val (vw, vh) = viewSize()
                    val cx = vw * 0.5f + presentation.panX
                    val cy = vh * 0.5f + presentation.panY
                    presentation.zoomScale = newScale
                    presentation.panX += (detector.focusX - cx) * (1f - ratio)
                    presentation.panY += (detector.focusY - cy) * (1f - ratio)
                    onHideFloatingBar()
                    onInvalidate()
                }
                return true
            }
        }
    )

    fun snapshotOverlayState() = ScreenAssistantCanvasRenderer.FrameOverlayState(
        dragSelectOrHandle = draggingSelect || draggingHandle != 0,
        dragViewX = dragViewX,
        dragViewY = dragViewY,
        dragBx = dragBx,
        dragBy = dragBy,
        draggingSubHandle = draggingSubHandle,
        draggingSubIndex = draggingSubIndex,
        subPreviewL = subPreviewL,
        subPreviewT = subPreviewT,
        subPreviewR = subPreviewR,
        subPreviewB = subPreviewB,
        draggingUnderlyingScroll = draggingUnderlyingScroll,
        scrollDownX = downX,
        scrollDownY = downY,
        scrollCurX = scrollCurX,
        scrollCurY = scrollCurY,
        draggingSnapStroke = draggingSnapStroke,
        snapStrokePoints = snapStrokePoints
    )

    private fun hitBlockAt(bx: Float, by: Float): ScreenTextBlock? {
        val sx = (geometry.bitmapRectOnView.width() / bitmap.width.coerceAtLeast(1)).coerceAtLeast(0.25f)
        val sy = (geometry.bitmapRectOnView.height() / bitmap.height.coerceAtLeast(1)).coerceAtLeast(0.25f)
        val padX = dp(5f) / sx
        val padY = dp(4f) / sy
        val activeBlocks = if (presentation.focusState == FocusState.TRANSLATE) {
            presentation.translateModeBlocks()
        } else {
            presentation.blocks
        }
        val idx = ScreenTextSelectionPolicy.findBestDragBlockIndex(activeBlocks, bx, by, stickyIdx = -1, hitPadX = padX, hitPadY = padY)
        val cand = activeBlocks.getOrNull(idx) ?: return null
        val l = min(cand.leftPx, cand.renderLeftPx).toFloat() - padX
        val t = min(cand.topPx, cand.renderTopPx).toFloat() - padY
        val r = max(cand.rightPx, cand.renderRightPx).toFloat() + padX
        val b = max(cand.bottomPx, cand.renderBottomPx).toFloat() + padY
        return cand.takeIf { bx in l..r && by in t..b }
    }

    private fun hitSmallestSubImageAt(bx: Float, by: Float): Int {
        val sx = (geometry.bitmapRectOnView.width() / bitmap.width.coerceAtLeast(1)).coerceAtLeast(0.25f)
        val sy = (geometry.bitmapRectOnView.height() / bitmap.height.coerceAtLeast(1)).coerceAtLeast(0.25f)
        val padX = dp(3f) / sx
        val padY = dp(3f) / sy
        val subs = extractedSubImagesProvider()
        var bestIdx = -1
        var bestArea = Long.MAX_VALUE
        subs.forEachIndexed { idx, sub ->
            val c = sub.candidate
            if (c.leftPx < 0 || c.rightPx <= c.leftPx || c.topPx < 0 || c.bottomPx <= c.topPx) return@forEachIndexed
            if (bx in (c.leftPx - padX)..(c.rightPx + padX) && by in (c.topPx - padY)..(c.bottomPx + padY)) {
                val area = c.areaPx
                if (area < bestArea) {
                    bestArea = area
                    bestIdx = idx
                }
            }
        }
        return bestIdx
    }

    private fun nearestBlockIndex(bx: Float, by: Float, stickyIdx: Int = -1): Int {
        val sx = (geometry.bitmapRectOnView.width() / bitmap.width.coerceAtLeast(1)).coerceAtLeast(0.25f)
        val sy = (geometry.bitmapRectOnView.height() / bitmap.height.coerceAtLeast(1)).coerceAtLeast(0.25f)
        return ScreenTextSelectionPolicy.findBestDragBlockIndex(
            blocks = presentation.blocks,
            bx = bx,
            by = by,
            stickyIdx = stickyIdx,
            hitPadX = dp(8f) / sx,
            hitPadY = dp(8f) / sy
        )
    }

    private fun isCircleSnapGesture(): Boolean {
        if (snapViewPoints.size < 6) return false
        var minX = Float.MAX_VALUE; var maxX = Float.MIN_VALUE; var minY = Float.MAX_VALUE; var maxY = Float.MIN_VALUE
        var pathLen = 0f
        for (i in snapViewPoints.indices) {
            val (px, py) = snapViewPoints[i]
            if (px < minX) minX = px; if (px > maxX) maxX = px
            if (py < minY) minY = py; if (py > maxY) maxY = py
            if (i > 0) pathLen += hypot(px - snapViewPoints[i - 1].first, py - snapViewPoints[i - 1].second)
        }
        val spanX = maxX - minX; val spanY = maxY - minY
        if (min(spanX, spanY) < dp(26f)) return false
        val chord = hypot(snapViewPoints.last().first - snapViewPoints.first().first, snapViewPoints.last().second - snapViewPoints.first().second)
        val maxSpan = max(spanX, spanY).coerceAtLeast(1f)
        return pathLen >= maxSpan * 1.60f || (pathLen >= dp(88f) && chord <= maxSpan * 0.72f)
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isCurrent()) return false
        scaleGestureDetector.onTouchEvent(event)
        val action = event.actionMasked
        if (event.pointerCount >= 2) {
            draggingSelect = false; draggingHandle = 0; draggingSubHandle = 0; draggingSubIndex = -1
            draggingSnapStroke = false; snapStrokePoints.clear(); snapViewPoints.clear()
            draggingPan = true
            var sx = 0f; var sy = 0f
            for (p in 0 until event.pointerCount) { sx += event.getX(p); sy += event.getY(p) }
            val fx = sx / event.pointerCount; val fy = sy / event.pointerCount
            if (!multiTouchSession || action == MotionEvent.ACTION_POINTER_DOWN) {
                multiTouchSession = true; draggingUnderlyingScroll = false
                prevFocusX = fx; prevFocusY = fy
                onHideFloatingBar()
            } else if (action == MotionEvent.ACTION_MOVE && !scaleGestureDetector.isInProgress) {
                presentation.panX += (fx - prevFocusX)
                presentation.panY += (fy - prevFocusY)
                prevFocusX = fx; prevFocusY = fy
                onInvalidate()
            } else {
                prevFocusX = fx; prevFocusY = fy
            }
            if (action == MotionEvent.ACTION_POINTER_UP && event.pointerCount == 2) {
                val rem = if (event.actionIndex == 0) 1 else 0
                prevFocusX = event.getX(rem); prevFocusY = event.getY(rem)
            }
            return true
        }
        if (multiTouchSession) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                multiTouchSession = false; draggingPan = false
                onCanvasTouchActiveChanged(false)
                onStateUpdated()
            }
            return true
        }

        val bx = geometry.viewToBitmapX(event.x)
        val by = geometry.viewToBitmapY(event.y)
        dragViewX = event.x; dragViewY = event.y; dragBx = bx; dragBy = by
        val currentBlocks = presentation.blocks

        return when (action) {
            MotionEvent.ACTION_DOWN -> handleDown(event, bx, by, currentBlocks)
            MotionEvent.ACTION_MOVE -> handleMove(event, bx, by, currentBlocks)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handleUpOrCancel(event, action, bx, by)
            else -> true
        }
    }

    private fun handleDown(event: MotionEvent, bx: Float, by: Float, currentBlocks: List<ScreenTextBlock>): Boolean {
        onCanvasTouchActiveChanged(true)
        downX = event.x; downY = event.y; scrollCurX = event.x; scrollCurY = event.y
        prevFocusX = event.x; prevFocusY = event.y; prevPanX = presentation.panX; prevPanY = presentation.panY
        draggingSelect = false; draggingPan = false; draggingUnderlyingScroll = false
        draggingSnapStroke = false; snapStrokePoints.clear(); snapViewPoints.clear()
        draggingHandle = 0; draggingSubHandle = 0; draggingSubIndex = -1
        anchorBlockIdx = -1; anchorCharIdx = 0; downBlockIdx = -1; downCharIdx = 0

        if (!presentation.canvasMoveMode && presentation.focusState == FocusState.IMAGE_SELECTED && presentation.adjustingSubImageCrop) {
            val activeIdx = presentation.activeSubImageIndex
            val c = extractedSubImagesProvider().getOrNull(activeIdx)?.candidate
            if (c != null && c.leftPx >= 0 && c.rightPx > c.leftPx && c.bottomPx > c.topPx) {
                val vr = geometry.bitmapRectToViewRectF(c.leftPx, c.topPx, c.rightPx, c.bottomPx)
                val pts = arrayOf(
                    1 to (vr.left to vr.top), 2 to (vr.centerX() to vr.top), 3 to (vr.right to vr.top), 4 to (vr.right to vr.centerY()),
                    5 to (vr.right to vr.bottom), 6 to (vr.centerX() to vr.bottom), 7 to (vr.left to vr.bottom), 8 to (vr.left to vr.centerY())
                )
                var bestH = 0; var bestD = dp(22f)
                for ((id, pt) in pts) {
                    val d = hypot(event.x - pt.first, event.y - pt.second)
                    if (d <= bestD) { bestD = d; bestH = id }
                }
                if (bestH != 0) {
                    draggingSubHandle = bestH; draggingSubIndex = activeIdx
                    subStartL = c.leftPx; subStartT = c.topPx; subStartR = c.rightPx; subStartB = c.bottomPx
                    subPreviewL = c.leftPx; subPreviewT = c.topPx; subPreviewR = c.rightPx; subPreviewB = c.bottomPx
                    subStartBx = bx; subStartBy = by
                    onHideFloatingBar(); onInvalidate()
                    return true
                }
            }
        }

        if (!presentation.canvasMoveMode && presentation.focusState == FocusState.TEXT_SELECTED && renderer.hasHandles) {
            val ordered = currentBlocks.mapNotNull { b -> presentation.selectionSpans[b.id]?.let { b to it } }
            if (ordered.isNotEmpty()) {
                val hitR = dp(32f)
                val dS = hypot(event.x - renderer.startHandleX, event.y - renderer.startHandleY)
                val dE = hypot(event.x - renderer.endHandleX, event.y - renderer.endHandleY)
                if (dS <= hitR || dE <= hitR) {
                    val pickStart = if (abs(dS - dE) <= dp(4f)) event.x <= (renderer.startHandleX + renderer.endHandleX) * 0.5f else dS < dE
                    if (pickStart) {
                        draggingHandle = 1
                        anchorBlockIdx = currentBlocks.indexOfFirst { it.id == ordered.last().first.id }
                        anchorCharIdx = ordered.last().second.endIndex
                    } else {
                        draggingHandle = 2
                        anchorBlockIdx = currentBlocks.indexOfFirst { it.id == ordered.first().first.id }
                        anchorCharIdx = ordered.first().second.startIndex
                    }
                    onHideFloatingBar(); onInvalidate()
                    return true
                }
            }
        }

        if (!presentation.canvasMoveMode && presentation.focusState != FocusState.TRANSLATE) {
            val hit = hitBlockAt(bx, by)
            if (hit != null) {
                downBlockIdx = currentBlocks.indexOf(hit)
                val br = geometry.blockRenderRectBitmap(hit)
                downCharIdx = ScreenTextSelectionPolicy.pointToCharIndex(hit.text, bx, br.left, br.right, hit.charSpans)
            }
            val maxBx = (bitmap.width - 1).coerceAtLeast(0)
            val maxBy = (bitmap.height - 1).coerceAtLeast(0)
            snapViewPoints.add(event.x to event.y)
            snapStrokePoints.add(bx.roundToInt().coerceIn(0, maxBx) to by.roundToInt().coerceIn(0, maxBy))
        }
        return true
    }

    private fun handleMove(event: MotionEvent, bx: Float, by: Float, currentBlocks: List<ScreenTextBlock>): Boolean {
        if (draggingSubHandle != 0) {
            val next = ScreenAssistantPolicy.applySubImageHandleDrag(
                subStartL, subStartT, subStartR, subStartB, draggingSubHandle,
                (bx - subStartBx).roundToInt(), (by - subStartBy).roundToInt(), bitmap.width, bitmap.height, minSizePx = 48
            )
            subPreviewL = next[0]; subPreviewT = next[1]; subPreviewR = next[2]; subPreviewB = next[3]
            onInvalidate()
            return true
        }
        if (draggingHandle != 0 && anchorBlockIdx >= 0) {
            val sy = (geometry.bitmapRectOnView.height() / bitmap.height.coerceAtLeast(1)).coerceAtLeast(0.25f)
            val queryBy = by - dp(16f) / sy
            dragBy = queryBy
            val targetIdx = nearestBlockIndex(bx, queryBy, stickyIdx = anchorBlockIdx)
            if (targetIdx in currentBlocks.indices) {
                val tb = currentBlocks[targetIdx]
                val br = geometry.blockRenderRectBitmap(tb)
                val ch = ScreenTextSelectionPolicy.pointToCharIndex(tb.text, bx, br.left, br.right, tb.charSpans)
                presentation.selectTextSpans(
                    ScreenTextSelectionPolicy.computeContinuousSelectionSpans(currentBlocks, anchorBlockIdx, anchorCharIdx, targetIdx, ch)
                )
                onInvalidate()
            }
            return true
        }
        if (!presentation.canvasMoveMode && presentation.focusState != FocusState.TRANSLATE && downBlockIdx < 0) {
            if (snapViewPoints.isNotEmpty() && snapViewPoints.size < 160 && hypot(event.x - snapViewPoints.last().first, event.y - snapViewPoints.last().second) >= dp(4f)) {
                snapViewPoints.add(event.x to event.y)
                snapStrokePoints.add(bx.roundToInt().coerceIn(0, (bitmap.width - 1).coerceAtLeast(0)) to by.roundToInt().coerceIn(0, (bitmap.height - 1).coerceAtLeast(0)))
            }
            if (!draggingSnapStroke && presentation.zoomScale <= 1.05f && isCircleSnapGesture()) {
                draggingSnapStroke = true; draggingUnderlyingScroll = false; draggingPan = false
                presentation.panX = prevPanX; presentation.panY = prevPanY
                onHideFloatingBar(); onInvalidate()
                return true
            }
            if (draggingSnapStroke) { onInvalidate(); return true }
        }
        if (draggingUnderlyingScroll) {
            scrollCurX = event.x; scrollCurY = event.y; onInvalidate(); return true
        }
        if (draggingPan) {
            presentation.panX = prevPanX + (event.x - downX); presentation.panY = prevPanY + (event.y - downY)
            onInvalidate(); return true
        }
        if (!draggingSelect && (abs(event.x - downX) > dp(7f) || abs(event.y - downY) > dp(7f))) {
            val intent = ScreenAssistantPolicy.classifySingleFingerDrag(
                modeKey = if (downBlockIdx >= 0 && presentation.focusState != FocusState.TRANSLATE) "TEXT_SELECT" else presentation.mode.name,
                isCroppedRegion = isCroppedRegion,
                zoomScale = presentation.zoomScale,
                hasActiveTextTarget = downBlockIdx >= 0,
                hasActiveSubHandle = draggingSubHandle != 0,
                canScrollUnderlying = canScrollUnderlying(),
                canvasMoveMode = presentation.canvasMoveMode
            )
            when (intent) {
                ScreenAssistantPolicy.CanvasSingleFingerDragAction.TEXT_SELECTION -> {
                    draggingSelect = true; onHideFloatingBar()
                }
                ScreenAssistantPolicy.CanvasSingleFingerDragAction.UNDERLYING_SCROLL -> {
                    draggingUnderlyingScroll = true; scrollCurX = event.x; scrollCurY = event.y
                    onHideFloatingBar(); onInvalidate(); return true
                }
                else -> {
                    draggingPan = true; onHideFloatingBar()
                    presentation.panX = prevPanX + (event.x - downX); presentation.panY = prevPanY + (event.y - downY)
                    onInvalidate(); return true
                }
            }
        }
        if (draggingSelect && downBlockIdx in currentBlocks.indices) {
            val targetIdx = nearestBlockIndex(bx, by, stickyIdx = downBlockIdx)
            if (targetIdx in currentBlocks.indices) {
                val tb = currentBlocks[targetIdx]
                val br = geometry.blockRenderRectBitmap(tb)
                val ch = ScreenTextSelectionPolicy.pointToCharIndex(tb.text, bx, br.left, br.right, tb.charSpans)
                presentation.selectTextSpans(
                    ScreenTextSelectionPolicy.computeContinuousSelectionSpans(currentBlocks, downBlockIdx, downCharIdx, targetIdx, ch)
                )
                onInvalidate()
            }
        }
        return true
    }

    private fun handleUpOrCancel(event: MotionEvent, action: Int, bx: Float, by: Float): Boolean {
        onCanvasTouchActiveChanged(false)
        if (draggingSnapStroke) {
            val pts = snapStrokePoints.toList()
            draggingSnapStroke = false; snapStrokePoints.clear(); snapViewPoints.clear(); onInvalidate()
            if (action == MotionEvent.ACTION_UP && pts.size >= 4) onSnapSubImageAtRegion(pts) else onStateUpdated()
            return true
        }
        if (draggingUnderlyingScroll) {
            val dist = hypot(event.x - downX, event.y - downY)
            draggingUnderlyingScroll = false; snapStrokePoints.clear(); snapViewPoints.clear(); onInvalidate()
            if (action == MotionEvent.ACTION_UP && dist >= dp(28f)) {
                val loc = IntArray(2); viewScreenLocation(loc)
                onScrollUnderlying(downX + loc[0], downY + loc[1], event.x + loc[0], event.y + loc[1])
            } else if (action == MotionEvent.ACTION_UP) {
                onStateUpdated()
            }
            return true
        }
        if (draggingSubHandle != 0) {
            val idx = draggingSubIndex; val l = subPreviewL; val t = subPreviewT; val r = subPreviewR; val b = subPreviewB
            draggingSubHandle = 0; draggingSubIndex = -1
            if (action == MotionEvent.ACTION_UP && idx >= 0 && r - l >= 48 && b - t >= 48) {
                onSubImageAdjusted(idx, l, t, r, b)
            } else {
                onInvalidate(); onStateUpdated()
            }
            return true
        }
        if (draggingPan) { draggingPan = false; onStateUpdated(); return true }
        if (draggingHandle != 0 || draggingSelect) {
            draggingHandle = 0; draggingSelect = false; anchorBlockIdx = -1; downBlockIdx = -1
            onStateUpdated()
            return true
        }
        snapStrokePoints.clear(); snapViewPoints.clear()
        if (action == MotionEvent.ACTION_UP) handleUnifiedTap(event, bx, by)
        return true
    }

    private fun handleUnifiedTap(event: MotionEvent, bx: Float, by: Float) {
        if (presentation.focusState == FocusState.TRANSLATE) {
            val hit = hitBlockAt(bx, by)
            val nowMs = SystemClock.uptimeMillis()
            val dbl = hit == null && nowMs - lastTapUpTimeMs <= 300L && abs(event.x - lastTapUpX) <= dp(28f) && abs(event.y - lastTapUpY) <= dp(28f)
            lastTapUpTimeMs = nowMs; lastTapUpX = event.x; lastTapUpY = event.y
            when {
                hit != null -> if (presentation.activeTranslateBlockId == hit.id) {
                    if (!presentation.showingOriginalIds.remove(hit.id)) presentation.showingOriginalIds.add(hit.id)
                } else presentation.activeTranslateBlockId = hit.id
                dbl -> { presentation.resetViewport(); presentation.activeTranslateBlockId = null }
                else -> presentation.activeTranslateBlockId = null
            }
            onStateUpdated()
            return
        }
        if (presentation.previewingActiveSubImage) {
            presentation.previewingActiveSubImage = false
            onStateUpdated()
            return
        }

        val hitText = hitBlockAt(bx, by)
        val hitImgIdx = hitSmallestSubImageAt(bx, by)
        val hitImg = extractedSubImagesProvider().getOrNull(hitImgIdx)?.candidate

        val inImageWorkflow = presentation.mode == ScreenAssistantMode.SUB_IMAGES ||
            presentation.focusState == FocusState.IMAGE_SELECTED
        val hitImgArea = hitImg?.areaPx ?: 0L
        val hitTextArea = hitText?.let { it.widthPx.toLong() * it.heightPx.toLong() } ?: 0L
        val preferImageOverCoverText = hitImg != null && hitText != null && (
            inImageWorkflow ||
                (presentation.focusState != FocusState.TEXT_SELECTED &&
                    !hitText.isDirectRenderedText &&
                    hitImgArea >= (hitTextArea * 3L) / 2L)
            )

        when {
            hitImgIdx >= 0 && (hitText == null || preferImageOverCoverText) -> {
                if (presentation.focusState == FocusState.IMAGE_SELECTED && presentation.activeSubImageIndex == hitImgIdx) {
                    presentation.previewingActiveSubImage = true
                } else {
                    presentation.selectSubImage(hitImgIdx)
                }
            }
            hitText != null && (presentation.focusState != FocusState.IMAGE_SELECTED || hitImgIdx < 0) -> {
                val br = geometry.blockRenderRectBitmap(hitText)
                val ch = ScreenTextSelectionPolicy.pointToCharIndex(hitText.text, bx, br.left, br.right, hitText.charSpans)
                val (s, e) = ScreenTextSelectionPolicy.semanticTokenRangeAtChar(hitText.text, ch)
                val existing = presentation.selectionSpans[hitText.id]
                if (presentation.selectionSpans.size == 1 && existing != null) {
                    if (existing.startIndex == s && existing.endIndex == e && (s > 0 || e < hitText.text.length)) {
                        presentation.selectTextSpans(mapOf(hitText.id to ScreenTextSelectionPolicy.fullSpan(hitText)))
                    } else {
                        presentation.clearFocus()
                    }
                } else {
                    presentation.selectTextSpans(mapOf(hitText.id to TextSelectionSpan(hitText.id, s, e)))
                }
            }
            else -> presentation.clearFocus()
        }
        onStateUpdated()
    }
}
