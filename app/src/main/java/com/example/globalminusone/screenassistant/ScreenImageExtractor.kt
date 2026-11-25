package com.example.globalminusone.screenassistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class SubImageResult(
    val candidate: ExtractedSubImageCandidate,
    val bitmap: Bitmap,
    val rawEncodedBytes: ByteArray? = null,
    val rawExtension: String = "png",
    val rawMimeType: String = "image/png",
    val isUnderlyingOriginal: Boolean = false
)

internal enum class ScreenImageScope {
    FULL_SCREEN,
    SELECTED_REGION
}

internal data class ScreenImageExtractionResult(
    val images: List<SubImageResult>,
    val originalSourceNote: String
)

/**
 * Clean Screen Image Extractor:
 * 1) Non-root true-bounds leaf cropping + photographic verification (always active);
 * 2) Optional experimental LSPosed system-framework ViewDebug (`DUMP`/`CAPTURE`) single-layer
 *    extraction when explicitly enabled by the user (default OFF, zero third-party app injection).
 */
internal object ScreenImageExtractor {

    private fun hasPhotographicContent(
        screenMid: IntArray,
        midW: Int,
        midH: Int,
        fullW: Int,
        fullH: Int,
        candidate: ExtractedSubImageCandidate,
        strictChromeFilter: Boolean
    ): Boolean {
        if (midW < 8 || midH < 8 || screenMid.size < midW * midH || fullW <= 0 || fullH <= 0) return true
        val x0 = ((candidate.leftPx * midW) / fullW).coerceIn(0, midW - 1)
        val y0 = ((candidate.topPx * midH) / fullH).coerceIn(0, midH - 1)
        val x1 = ((candidate.rightPx * midW) / fullW).coerceIn(x0 + 1, midW)
        val y1 = ((candidate.bottomPx * midH) / fullH).coerceIn(y0 + 1, midH)
        var n = 0
        var lumaSum = 0L
        var lumaSqSum = 0L
        var satSum = 0L
        var edgeSum = 0L
        var edgeCnt = 0
        fun pxLuma(p: Int): Int {
            val r = (p ushr 16) and 0xFF
            val g = (p ushr 8) and 0xFF
            val b = p and 0xFF
            return (r * 77 + g * 150 + b * 29) ushr 8
        }
        for (y in y0 until y1) {
            val row = y * midW
            for (x in x0 until x1) {
                val p = screenMid[row + x]
                val r = (p ushr 16) and 0xFF
                val g = (p ushr 8) and 0xFF
                val b = p and 0xFF
                val mx = max(r, max(g, b))
                val mn = min(r, min(g, b))
                satSum += (mx - mn)
                val luma = (r * 77 + g * 150 + b * 29) ushr 8
                lumaSum += luma
                lumaSqSum += luma.toLong() * luma.toLong()
                if (x > x0) {
                    edgeSum += abs(luma - pxLuma(screenMid[row + x - 1]))
                    edgeCnt++
                }
                if (y > y0) {
                    edgeSum += abs(luma - pxLuma(screenMid[row - midW + x]))
                    edgeCnt++
                }
                n++
            }
        }
        if (n < 9) return false
        val meanLuma = lumaSum.toFloat() / n
        val variance = (lumaSqSum.toFloat() / n) - meanLuma * meanLuma
        val meanSat = satSum.toFloat() / n
        val meanEdgeDiff = if (edgeCnt > 0) edgeSum.toFloat() / edgeCnt else 0f
        return if (!strictChromeFilter) {
            variance >= 65f || meanSat >= 9f
        } else {
            (variance >= 140f || (variance >= 75f && meanSat >= 16f)) && meanEdgeDiff >= 5.5f
        }
    }

    internal fun decodeBoundedPngBitmap(bytes: ByteArray, maxDim: Int = 1920): Bitmap? {
        if (!SystemViewCaptureBridge.looksLikePng(bytes)) return null
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        if (opts.outWidth < 44 || opts.outHeight < 44) return null
        var ss = 1
        val mx = max(opts.outWidth, opts.outHeight)
        while ((mx / (ss * 2)) >= maxDim) ss *= 2
        val bmp = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply {
                inScaled = false
                inSampleSize = ss
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        ) ?: return null
        if (!hasNonBlankRenderedPixels(bmp)) {
            if (!bmp.isRecycled) bmp.recycle()
            return null
        }
        return bmp
    }

    internal fun hasNonBlankRenderedPixels(bmp: Bitmap): Boolean {
        if (bmp.isRecycled || bmp.width < 16 || bmp.height < 16) return false
        val grid = 20
        var opaqueCount = 0
        var sum = 0.0
        var sumSq = 0.0
        val total = grid * grid
        for (gy in 0 until grid) {
            val y = ((gy + 0.5f) * bmp.height / grid).toInt().coerceIn(0, bmp.height - 1)
            for (gx in 0 until grid) {
                val x = ((gx + 0.5f) * bmp.width / grid).toInt().coerceIn(0, bmp.width - 1)
                val p = bmp.getPixel(x, y)
                val a = (p ushr 24) and 0xFF
                if (a >= 16) opaqueCount++
                val lum = (((p ushr 16) and 0xFF) * 77 + ((p ushr 8) and 0xFF) * 150 + (p and 0xFF) * 29) ushr 8
                sum += lum
                sumSq += lum * lum
            }
        }
        if (opaqueCount * 100 < total * 12) return false
        val mean = sum / total
        val variance = (sumSq / total) - mean * mean
        return variance >= 18.0
    }

    private fun projectViewNodesToScope(
        nodes: List<ViewDebugNode>,
        crop: Rect?,
        targetW: Int,
        targetH: Int
    ): List<ViewDebugNode> {
        if (crop == null) return nodes
        return nodes.mapNotNull { n ->
            if (!n.visible || n.areaPx <= 0L) return@mapNotNull null
            val il = max(n.leftPx, crop.left)
            val it = max(n.topPx, crop.top)
            val ir = min(n.rightPx, crop.right)
            val ib = min(n.bottomPx, crop.bottom)
            if (ir <= il || ib <= it) return@mapNotNull null
            val inter = (ir - il).toLong() * (ib - it).toLong()
            if (inter * 100L < n.areaPx * 60L) return@mapNotNull null
            val nl = (il - crop.left).coerceIn(0, targetW)
            val nt = (it - crop.top).coerceIn(0, targetH)
            val nr = (ir - crop.left).coerceIn(0, targetW)
            val nb = (ib - crop.top).coerceIn(0, targetH)
            if (nr - nl < 44 || nb - nt < 44) return@mapNotNull null
            n.copy(leftPx = nl, topPx = nt, rightPx = nr, bottomPx = nb)
        }
    }

    suspend fun extractSubImages(
        bitmap: Bitmap,
        snapshot: AccessibilityScreenSnapshot,
        context: Context,
        allowLsposedViewCapture: Boolean,
        scope: ScreenImageScope,
        snapshotDeferred: Deferred<AccessibilityScreenSnapshot>? = null,
        snapshotCrop: Rect? = null,
        onPreliminaryResults: (suspend (List<SubImageResult>) -> Unit)? = null
    ): ScreenImageExtractionResult {
        val allocated = mutableListOf<Bitmap>()
        val handedOff = HashSet<Bitmap>()
        try {
            return coroutineScope {
                if (bitmap.width < 64 || bitmap.height < 64) {
                    return@coroutineScope ScreenImageExtractionResult(emptyList(), "画面尺寸无效")
                }

                val midW = min(360, bitmap.width)
                val midH = ((bitmap.height.toFloat() / bitmap.width) * midW).toInt().coerceIn(48, 960)
                val midPx = IntArray(midW * midH)
                withContext(Dispatchers.Default) {
                    val scaled = if (bitmap.width == midW && bitmap.height == midH) {
                        bitmap
                    } else {
                        Bitmap.createScaledBitmap(bitmap, midW, midH, true)
                    }
                    try {
                        scaled.getPixels(midPx, 0, midW, 0, 0, midW, midH)
                    } finally {
                        if (scaled !== bitmap && !scaled.isRecycled) scaled.recycle()
                    }
                }

                val enriched = snapshotDeferred?.let { withTimeoutOrNull(750L) { runCatching { it.await() }.getOrNull() } }
                val activeSnapshot = if (enriched != null) {
                    ScreenCaptureHub.projectSnapshotToCrop(enriched, snapshotCrop)
                } else {
                    snapshot
                }

                val leaves = verifiedMediaLeaves(
                    bitmap.width,
                    bitmap.height,
                    activeSnapshot.textNodes,
                    activeSnapshot.mediaNodes,
                    activeSnapshot.overlayObstructions
                )
                val verifiedPrelimLeaves = leaves.filter { c ->
                    hasPhotographicContent(midPx, midW, midH, bitmap.width, bitmap.height, c, c.confidence < 0.90f)
                }
                if (onPreliminaryResults != null && verifiedPrelimLeaves.isNotEmpty()) {
                    val prelim = withContext(Dispatchers.Default) { materializeCrops(bitmap, verifiedPrelimLeaves, allocated) }
                    if (prelim.isNotEmpty()) {
                        synchronized(allocated) { prelim.forEach { handedOff += it.bitmap } }
                        onPreliminaryResults(prelim)
                    }
                }

                val capturedSingles = ArrayList<SubImageResult>()
                var captureNote = ""

                if (allowLsposedViewCapture) {
                    val startNs = System.nanoTime()
                    val ping = SystemViewCaptureBridge.ping()
                    if (!ping.active) {
                        captureNote = ping.note
                    } else {
                        val dump = SystemViewCaptureBridge.dumpTopWindow()
                        if (!dump.ok) {
                            captureNote = "ViewDebug DUMP 失败：${dump.diagnostic}"
                        } else {
                            val dm = context.resources.displayMetrics
                            val rawNodes = withContext(Dispatchers.Default) {
                                ViewDebugDumpParser.parseDump(
                                    rawText = dump.dumpText,
                                    windowLeft = dump.windowFrame.left,
                                    windowTop = dump.windowFrame.top,
                                    screenW = max(dm.widthPixels, dump.windowFrame.right),
                                    screenH = max(dm.heightPixels, dump.windowFrame.bottom)
                                )
                            }
                            val projected = projectViewNodesToScope(rawNodes, snapshotCrop, bitmap.width, bitmap.height)
                            val filteredNodes = projected.filter { v ->
                                val cand = ExtractedSubImageCandidate(
                                    id = v.descriptor,
                                    leftPx = v.leftPx,
                                    topPx = v.topPx,
                                    rightPx = v.rightPx,
                                    bottomPx = v.bottomPx,
                                    sourceTag = "ViewDebug",
                                    confidence = 0.9f
                                )
                                hasPhotographicContent(midPx, midW, midH, bitmap.width, bitmap.height, cand, false)
                            }
                            val targets = ViewDebugDumpParser.selectCaptureTargets(
                                nodes = filteredNodes,
                                verifiedLeaves = verifiedPrelimLeaves,
                                maxTargets = 12
                            )
                            if (targets.isEmpty()) {
                                captureNote = "${dump.packageName} 无原生可抓图 View 节点 (${rawNodes.size}节点)"
                            } else {
                                val batch = SystemViewCaptureBridge.captureViews(
                                    targetPkg = dump.packageName,
                                    descriptors = targets.map { it.first.descriptor }
                                )
                                if (!batch.ok) {
                                    captureNote = "ViewDebug CAPTURE 失败：${batch.diagnostic}"
                                } else {
                                    val byDesc = targets.associateBy { it.first.descriptor }
                                    var blankRejects = 0
                                    var firstErr = ""
                                    withContext(Dispatchers.Default) {
                                        for (item in batch.items) {
                                            coroutineContext.ensureActive()
                                            if (item.errCode != 0 || item.pngBytes.isEmpty()) {
                                                if (firstErr.isEmpty() && item.errMsg.isNotBlank()) firstErr = item.errMsg
                                                continue
                                            }
                                            val targetPair = byDesc[item.descriptor] ?: continue
                                            val decoded = decodeBoundedPngBitmap(item.pngBytes, maxDim = 1920)
                                            if (decoded == null) {
                                                blankRejects++
                                                continue
                                            }
                                            synchronized(allocated) { allocated += decoded }
                                            val vNode = targetPair.first
                                            val baseCand = targetPair.second ?: ExtractedSubImageCandidate(
                                                id = "vd_${vNode.hashHex}",
                                                leftPx = vNode.leftPx.coerceIn(0, bitmap.width - 2),
                                                topPx = vNode.topPx.coerceIn(0, bitmap.height - 2),
                                                rightPx = vNode.rightPx.coerceIn(vNode.leftPx.coerceIn(0, bitmap.width - 2) + 2, bitmap.width),
                                                bottomPx = vNode.bottomPx.coerceIn(vNode.topPx.coerceIn(0, bitmap.height - 2) + 2, bitmap.height),
                                                sourceTag = "单层无角标提图",
                                                confidence = 0.96f
                                            )
                                            capturedSingles += SubImageResult(
                                                candidate = baseCand.copy(sourceTag = "单层无角标提图", confidence = 0.96f),
                                                bitmap = decoded,
                                                rawEncodedBytes = item.pngBytes,
                                                rawExtension = "png",
                                                rawMimeType = "image/png",
                                                isUnderlyingOriginal = true
                                            )
                                        }
                                    }
                                    val elapsedMs = max(1L, (System.nanoTime() - startNs) / 1_000_000L)
                                    captureNote = when {
                                        capturedSingles.isNotEmpty() ->
                                            "LSPosed 单层 ${capturedSingles.size}/${targets.size} (${elapsedMs}ms)"
                                        blankRejects > 0 ->
                                            "目标返回 $blankRejects 份空白/硬件位图，已回退裁图 ($firstErr)".trim()
                                        else ->
                                            "ViewDebug 0/${targets.size} 成功${if (firstErr.isNotBlank()) ": $firstErr" else ""} ($elapsedMs ms)"
                                    }
                                }
                            }
                        }
                    }
                }

                val capturedBoxes = capturedSingles.map { it.candidate }
                val remainingLeaves = leaves.filter { leaf ->
                    capturedBoxes.none { ScreenAssistantPolicy.computeIoU(it, leaf) >= 0.35f }
                }
                val nonOrigSlots = ArrayList<ExtractedSubImageCandidate>(remainingLeaves.size)
                remainingLeaves.forEach { c ->
                    val strictChrome = c.confidence < 0.90f
                    if (nonOrigSlots.none { ScreenAssistantPolicy.computeIoU(it, c) >= 0.42f } &&
                        hasPhotographicContent(midPx, midW, midH, bitmap.width, bitmap.height, c, strictChrome)
                    ) {
                        val relabeled = if (c.sourceTag == "界面视觉节点" || c.sourceTag == "界面媒体容器") {
                            c.copy(sourceTag = "界面精准提图")
                        } else {
                            c
                        }
                        nonOrigSlots += relabeled
                    }
                }

                val crops = withContext(Dispatchers.Default) { materializeCrops(bitmap, nonOrigSlots, allocated) }
                val combined = (capturedSingles + crops)
                    .sortedWith(compareBy({ it.candidate.topPx / 48 }, { it.candidate.leftPx }))
                    .take(48)
                val finalNote = when {
                    !allowLsposedViewCapture -> "非Root裁图 · 节点 ${crops.size}张"
                    capturedSingles.isNotEmpty() -> "单层 ${capturedSingles.size}张 · 裁图 ${crops.size}张 ($captureNote)"
                    else -> "节点 ${leaves.size}张 · $captureNote"
                }
                synchronized(allocated) { combined.forEach { handedOff += it.bitmap } }
                ScreenImageExtractionResult(combined, finalNote)
            }
        } finally {
            synchronized(allocated) {
                allocated.forEach { if (it !in handedOff && !it.isRecycled) it.recycle() }
            }
        }
    }

    fun snapObject(
        w: Int,
        h: Int,
        points: List<Pair<Int, Int>>,
        mediaNodes: List<ExtractedSubImageCandidate>,
        textNodes: List<ScreenTextBlock> = emptyList(),
        screenMid: IntArray? = null,
        midW: Int = 0,
        midH: Int = 0
    ): ExtractedSubImageCandidate? {
        if (points.isEmpty() || w < 32 || h < 32) return null
        val xs = points.map { it.first }
        val ys = points.map { it.second }
        val cx = (xs.min() + xs.max()) * 0.5f
        val cy = (ys.min() + ys.max()) * 0.5f
        mediaNodes.filter {
            (it.sourceTag == "界面媒体容器" || (!it.sourceTag.contains("容器") && !it.sourceTag.contains("网页"))) &&
                cx in it.leftPx.toFloat()..it.rightPx.toFloat() &&
                cy in it.topPx.toFloat()..it.bottomPx.toFloat()
        }.minByOrNull { it.areaPx }?.let { return it }

        if (points.size >= 3) {
            val l = xs.min().coerceIn(0, w - 44)
            val t = ys.min().coerceIn(0, h - 44)
            return ExtractedSubImageCandidate(
                "lasso_${l}_$t",
                l,
                t,
                xs.max().coerceIn(l + 44, w),
                ys.max().coerceIn(t + 44, h),
                "手势圈选",
                0.90f
            )
        }
        return null
    }

    private fun verifiedMediaLeaves(
        fullW: Int,
        fullH: Int,
        textBlocks: List<ScreenTextBlock>,
        mediaNodes: List<ExtractedSubImageCandidate>,
        obstructions: List<ExtractedSubImageCandidate>
    ): List<ExtractedSubImageCandidate> {
        val minSide = max(44, min(fullW, fullH) / 14)
        val fullArea = fullW.toLong() * fullH.toLong().coerceAtLeast(1L)
        return mediaNodes.filter { n ->
            val aspect = n.widthPx.toFloat() / n.heightPx.coerceAtLeast(1).toFloat()
            val areaRatio = n.areaPx.toFloat() / fullArea.toFloat()
            val tCov = textCoverage(n, textBlocks)
            val isMediaContainer = n.sourceTag == "界面媒体容器"
            val tagAllowed = isMediaContainer || (!n.sourceTag.contains("容器") && !n.sourceTag.contains("网页"))
            val textOk = when {
                isMediaContainer -> tCov < 0.25f && areaRatio <= 0.65f
                n.sourceTag == "界面视觉节点" -> tCov < 0.28f
                else -> tCov < 0.40f
            }
            val allowLarge = areaRatio <= 0.82f || (n.confidence >= 0.92f && tCov < 0.08f && areaRatio <= 0.995f)
            n.confidence >= 0.78f &&
                tagAllowed &&
                n.widthPx >= minSide &&
                n.heightPx >= minSide &&
                aspect in 0.16f..6.0f &&
                allowLarge &&
                textOk &&
                obstructions.none { ScreenAssistantPolicy.computeIoU(n, it) >= 0.35f }
        }.sortedByDescending { it.confidence }
    }

    private fun materializeCrops(
        bitmap: Bitmap,
        slots: List<ExtractedSubImageCandidate>,
        allocated: MutableList<Bitmap>
    ): List<SubImageResult> = slots.mapNotNull { c ->
        val l = c.leftPx.coerceIn(0, bitmap.width - 2)
        val t = c.topPx.coerceIn(0, bitmap.height - 2)
        val r = c.rightPx.coerceIn(l + 2, bitmap.width)
        val b = c.bottomPx.coerceIn(t + 2, bitmap.height)
        val crop = runCatching { Bitmap.createBitmap(bitmap, l, t, r - l, b - t) }.getOrNull() ?: return@mapNotNull null
        synchronized(allocated) { allocated += crop }
        SubImageResult(c.copy(leftPx = l, topPx = t, rightPx = r, bottomPx = b), crop)
    }

    private fun textCoverage(c: ExtractedSubImageCandidate, tbs: List<ScreenTextBlock>): Float {
        var area = 0L
        for (t in tbs) {
            val il = max(c.leftPx, t.leftPx)
            val it = max(c.topPx, t.topPx)
            val ir = min(c.rightPx, t.rightPx)
            val ib = min(c.bottomPx, t.bottomPx)
            if (ir > il && ib > it) area += (ir - il).toLong() * (ib - it).toLong()
        }
        return area.toFloat() / c.areaPx.coerceAtLeast(1L).toFloat()
    }
}
