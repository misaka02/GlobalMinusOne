package com.example.globalminusone.screenassistant

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure-Kotlin background erasure (uniform-bg gradient fill vs textured 4-way inpainting),
 * typography extraction (tight foreground box, border-gap stripping, ink height, alignment),
 * and ink-valley CTC per-character span refinement (zero Android Color static mocking required).
 */
internal object ScreenTextInpainter {

    const val ALIGN_LEFT = 0
    const val ALIGN_CENTER = 1
    const val ALIGN_RIGHT = 2
    private const val COLOR_WHITE = 0xFFFFFFFF.toInt()
    private const val COLOR_BLACK = 0xFF000000.toInt()

    internal data class BlockTypographyStyle(
        val bgTopArgb: Int,
        val bgBottomArgb: Int,
        val textColorArgb: Int,
        val uniformBg: Boolean,
        val tightLeft: Int,
        val tightTop: Int,
        val tightRight: Int,
        val tightBottom: Int,
        val isBoldHint: Boolean,
        val inkHeightPx: Int,
        val sourceLineCount: Int,
        val alignmentHint: Int
    )

    fun analyzeBlockStyle(bitmap: Bitmap, block: ScreenTextBlock): BlockTypographyStyle {
        val w = bitmap.width; val h = bitmap.height
        val l = block.leftPx.coerceIn(0, w - 1); val t = block.topPx.coerceIn(0, h - 1)
        val r = block.rightPx.coerceIn(l + 1, w); val b = block.bottomPx.coerceIn(t + 1, h)
        val rw = r - l; val rh = b - t
        if (rw < 4 || rh < 4) {
            return BlockTypographyStyle(COLOR_WHITE, COLOR_WHITE, COLOR_BLACK, true, l, t, r, b, false, rh, 1, ALIGN_LEFT)
        }
        val region = IntArray(rw * rh)
        bitmap.getPixels(region, 0, rw, l, t, rw, rh)
        return analyzeBlockTypographyFromRegion(region, rw, rh, l, t, l, t, r, b, tightenBounds = true, explicitLineCount = block.sourceLineCount)
    }

    internal fun analyzeBlockTypographyFromRegion(
        regionPixels: IntArray,
        regionWidth: Int,
        regionHeight: Int,
        offsetX: Int,
        offsetY: Int,
        blockLeft: Int,
        blockTop: Int,
        blockRight: Int,
        blockBottom: Int,
        tightenBounds: Boolean,
        explicitLineCount: Int = 0
    ): BlockTypographyStyle {
        val bl = blockLeft.coerceIn(offsetX, offsetX + regionWidth - 1)
        val bt = blockTop.coerceIn(offsetY, offsetY + regionHeight - 1)
        val br = blockRight.coerceIn(bl + 1, offsetX + regionWidth)
        val bb = blockBottom.coerceIn(bt + 1, offsetY + regionHeight)
        val rx0 = bl - offsetX; val ry0 = bt - offsetY; val rw = br - bl; val rh = bb - bt
        if (rw < 4 || rh < 4 || regionPixels.size < regionWidth * regionHeight) {
            return BlockTypographyStyle(COLOR_WHITE, COLOR_WHITE, COLOR_BLACK, true, bl, bt, br, bb, false, rh, max(1, explicitLineCount), ALIGN_LEFT)
        }

        val topSamples = ArrayList<Int>(rw); val botSamples = ArrayList<Int>(rw); val borderSamples = ArrayList<Int>(rw * 2 + rh * 2)
        for (dx in 0 until rw step max(1, rw / 24)) {
            val tc = regionPixels[ry0 * regionWidth + rx0 + dx]; val bc = regionPixels[(ry0 + rh - 1) * regionWidth + rx0 + dx]
            topSamples += tc; botSamples += bc; borderSamples += tc; borderSamples += bc
        }
        for (dy in 0 until rh step max(1, rh / 12)) {
            borderSamples += regionPixels[(ry0 + dy) * regionWidth + rx0]
            borderSamples += regionPixels[(ry0 + dy) * regionWidth + rx0 + rw - 1]
        }
        val bgTop = dominantMedianColor(topSamples)
        val bgBot = dominantMedianColor(botSamples)
        val uniformBg = borderSamples.count { min(colorDiff(it, bgTop), colorDiff(it, bgBot)) <= 32 } * 100 >= borderSamples.size * 76 &&
            colorDiff(bgTop, bgBot) <= 140

        val fgThresh = if (uniformBg) 34 else 42
        val rowInk = IntArray(rh); val colInk = IntArray(rw)
        var minFgX = rw; var maxFgX = -1; var minFgY = rh; var maxFgY = -1
        var inkCount = 0

        for (dy in 0 until rh) {
            val rowBg = lerpArgb(bgTop, bgBot, dy.toFloat() / max(1, rh - 1))
            val rowBgL = luma(rowBg)
            val rowOff = (ry0 + dy) * regionWidth + rx0
            for (dx in 0 until rw) {
                val c = regionPixels[rowOff + dx]
                if (colorDiff(c, rowBg) > fgThresh || abs(luma(c) - rowBgL) > fgThresh) {
                    rowInk[dy]++; colInk[dx]++; inkCount++
                    if (dx < minFgX) minFgX = dx; if (dx > maxFgX) maxFgX = dx
                    if (dy < minFgY) minFgY = dy; if (dy > maxFgY) maxFgY = dy
                }
            }
        }
        if (inkCount == 0) {
            val contrastTxt = if (ScreenTextSelectionPolicy.wcagContrast(COLOR_BLACK, bgTop) >= 4.2f) COLOR_BLACK else COLOR_WHITE
            return BlockTypographyStyle(bgTop, bgBot, contrastTxt, uniformBg, bl, bt, br, bb, false, (rh * 0.68f).roundToInt(), max(1, explicitLineCount), ALIGN_LEFT)
        }

        val (cl, cr) = stripBorderLineRuns(colInk, minFgX, maxFgX)
        val fgL = cl.coerceIn(0, rw - 1); val fgR = (cr + 1).coerceIn(fgL + 1, rw)
        if (fgL > minFgX || cr < maxFgX) {
            rowInk.fill(0)
            minFgY = rh; maxFgY = -1
            for (dy in 0 until rh) {
                val rowBg = lerpArgb(bgTop, bgBot, dy.toFloat() / max(1, rh - 1))
                val rowBgL = luma(rowBg)
                val rowOff = (ry0 + dy) * regionWidth + rx0
                for (dx in fgL until fgR) {
                    val c = regionPixels[rowOff + dx]
                    if (colorDiff(c, rowBg) > fgThresh || abs(luma(c) - rowBgL) > fgThresh) {
                        rowInk[dy]++
                        if (dy < minFgY) minFgY = dy
                        if (dy > maxFgY) maxFgY = dy
                    }
                }
            }
            if (maxFgY < minFgY) { minFgY = 0; maxFgY = rh - 1 }
        }
        val (ct, cb) = stripBorderLineRuns(rowInk, minFgY, maxFgY)
        val fgT = ct.coerceIn(0, rh - 1); val fgB = (cb + 1).coerceIn(fgT + 1, rh)

        val rawBands = detectInkBands(rowInk, fgT, fgB - 1, minBandSize = 2)
        val lineCount = if (explicitLineCount > 0) explicitLineCount else rawBands.size.coerceAtLeast(1)
        val inkHeightPx = if (explicitLineCount == 1) {
            (fgB - fgT).coerceAtLeast(6)
        } else if (rawBands.isNotEmpty()) {
            val hs = rawBands.map { it.second - it.first + 1 }.sorted()
            hs[hs.size / 2]
        } else fgB - fgT

        var textColor = sampleDominantTextColor(regionPixels, regionWidth, rx0, ry0, fgL, fgT, fgR, fgB, bgTop, bgBot, rh, fgThresh)
            ?: if (luma(bgTop) > 128) COLOR_BLACK else COLOR_WHITE
        if (ScreenTextSelectionPolicy.wcagContrast(textColor, bgTop) < 4.2f) {
            textColor = if (ScreenTextSelectionPolicy.wcagContrast(COLOR_BLACK, bgTop) >= ScreenTextSelectionPolicy.wcagContrast(COLOR_WHITE, bgTop)) COLOR_BLACK else COLOR_WHITE
        }

        val boxArea = max(1, (fgR - fgL) * (fgB - fgT))
        val isBoldHint = inkCount.toFloat() / boxArea.toFloat() >= 0.34f
        val padL = fgL; val padR = rw - fgR
        val alignmentHint = when {
            rw >= 80 && padL >= 18 && padR >= 18 && abs(padL - padR) <= max(6, rw / 12) -> ALIGN_CENTER
            padL > padR * 2 + 12 && padL >= 20 -> ALIGN_RIGHT
            else -> ALIGN_LEFT
        }
        val canTighten = tightenBounds && uniformBg && (fgL > 4 || fgT > 4 || rw - fgR > 4 || rh - fgB > 4)
        val tightL = if (canTighten) bl + max(0, fgL - 2) else bl
        val tightT = if (canTighten) bt + max(0, fgT - 2) else bt
        val tightR = if (canTighten) bl + min(rw, fgR + 2) else br
        val tightB = if (canTighten) bt + min(rh, fgB + 2) else bb

        return BlockTypographyStyle(
            bgTopArgb = bgTop, bgBottomArgb = bgBot, textColorArgb = textColor, uniformBg = uniformBg,
            tightLeft = tightL, tightTop = tightT, tightRight = tightR, tightBottom = tightB,
            isBoldHint = isBoldHint, inkHeightPx = inkHeightPx.coerceIn(6, rh),
            sourceLineCount = lineCount, alignmentHint = alignmentHint
        )
    }

    fun enrichBlocksFromBitmap(bitmap: Bitmap, blocks: List<ScreenTextBlock>): List<ScreenTextBlock> {
        if (blocks.isEmpty()) return blocks
        val w = bitmap.width; val h = bitmap.height
        return blocks.map { block ->
            val style = analyzeBlockStyle(bitmap, block)
            val l = style.tightLeft.coerceIn(0, w - 1); val t = style.tightTop.coerceIn(0, h - 1)
            val r = style.tightRight.coerceIn(l + 1, w); val b = style.tightBottom.coerceIn(t + 1, h)
            val rw = r - l; val rh = b - t
            val refinedSpans = if (rw >= 4 && rh >= 4) {
                val region = IntArray(rw * rh).also { bitmap.getPixels(it, 0, rw, l, t, rw, rh) }
                val ink = computeInkColumnProfile(region, rw, rh, style.bgTopArgb, style.bgBottomArgb)
                synthesizePerCharSpansPure(block.text, l, t, r, b, block.charSpans, ink)
            } else block.charSpans

            block.copy(
                bgColorArgb = style.bgTopArgb, bgBottomColorArgb = style.bgBottomArgb,
                textColorArgb = style.textColorArgb, uniformBg = style.uniformBg,
                renderLeftPx = l, renderTopPx = t, renderRightPx = r, renderBottomPx = b,
                charSpans = refinedSpans, isBoldHint = style.isBoldHint,
                alignmentHint = style.alignmentHint, inkHeightPx = style.inkHeightPx,
                sourceLineCount = style.sourceLineCount
            )
        }
    }

    fun computeInkColumnProfile(pixels: IntArray, width: Int, height: Int, bgTop: Int, bgBottom: Int): FloatArray {
        val profile = FloatArray(width)
        if (width <= 0 || height <= 0 || pixels.size < width * height) return profile
        for (x in 0 until width) {
            var count = 0f
            for (y in 0 until height) {
                val bg = lerpArgb(bgTop, bgBottom, y.toFloat() / max(1, height - 1))
                val c = pixels[y * width + x]
                if (colorDiff(c, bg) > 32 || abs(luma(c) - luma(bg)) > 24) count += 1f
            }
            profile[x] = count
        }
        return profile
    }

    internal fun synthesizePerCharSpansPure(
        text: String,
        leftPx: Int,
        topPx: Int,
        rightPx: Int,
        bottomPx: Int,
        existingSpans: List<TextCharSpan>,
        inkColumns: FloatArray? = null
    ): List<TextCharSpan> {
        val n = text.length
        if (n <= 0 || rightPx <= leftPx) return emptyList()
        if (n == 1) return listOf(TextCharSpan(0, 1, leftPx, topPx, rightPx, bottomPx))

        val rw = rightPx - leftPx
        val dividers = IntArray(n + 1)
        dividers[0] = leftPx; dividers[n] = rightPx

        val anchorDividers = IntArray(n + 1) { -1 }.apply { this[0] = leftPx; this[n] = rightPx }
        var prevEndIdx = 0; var prevCenterX = leftPx - 1
        for (span in existingSpans.sortedBy { it.startIndex }) {
            val sIdx = span.startIndex; val eIdx = span.endIndex
            val cx = (span.leftPx + span.rightPx) / 2
            if (sIdx !in 0 until n || eIdx !in (sIdx + 1)..n || span.rightPx <= span.leftPx) continue
            if (sIdx < prevEndIdx || cx < prevCenterX) continue
            val sl = span.leftPx.coerceIn(leftPx, rightPx - 1)
            val sr = span.rightPx.coerceIn(sl + 1, rightPx)
            if (sIdx > 0 && anchorDividers[sIdx] < 0) anchorDividers[sIdx] = sl
            if (eIdx < n) anchorDividers[eIdx] = sr
            if (eIdx - sIdx > 1) {
                val subW = (0 until (eIdx - sIdx)).sumOf { k -> charAdvanceWeight(text[sIdx + k]) }.coerceAtLeast(1.0)
                var subAcc = 0.0
                for (k in 1 until (eIdx - sIdx)) {
                    subAcc += charAdvanceWeight(text[sIdx + k - 1])
                    anchorDividers[sIdx + k] = (sl + (subAcc / subW) * (sr - sl)).roundToInt()
                }
            }
            prevEndIdx = eIdx; prevCenterX = cx
        }

        var p = 0
        while (p < n) {
            var q = p + 1
            while (q < n && anchorDividers[q] < 0) q++
            val spanPx = (anchorDividers[q] - anchorDividers[p]).coerceAtLeast(q - p)
            val totalW = (p until q).sumOf { charAdvanceWeight(text[it]) }.coerceAtLeast(1.0)
            var acc = 0.0
            for (k in p + 1 until q) {
                acc += charAdvanceWeight(text[k - 1])
                anchorDividers[k] = (anchorDividers[p] + (acc / totalW) * spanPx).roundToInt()
            }
            p = q
        }

        val avgCharW = (rw.toFloat() / n.coerceAtLeast(1)).coerceAtLeast(4f)
        val snapRadius = (avgCharW * 0.45f).roundToInt().coerceIn(3, 22)
        for (i in 1 until n) {
            val baseX = anchorDividers[i].coerceIn(dividers[i - 1] + 1, rightPx - (n - i))
            var chosen = baseX
            if (inkColumns != null && inkColumns.size >= rw) {
                val baseRel = (baseX - leftPx).coerceIn(1, rw - 1)
                val lo = max(dividers[i - 1] - leftPx + 1, baseRel - snapRadius).coerceIn(1, rw - 1)
                val hi = min(rightPx - leftPx - (n - i), baseRel + snapRadius).coerceIn(lo, rw - 1)
                var minInk = Float.MAX_VALUE
                for (rx in lo..hi) minInk = min(minInk, inkColumns[rx])
                val valleyCols = (lo..hi).filter { inkColumns[it] <= minInk + 0.05f }
                if (valleyCols.isNotEmpty()) chosen = leftPx + valleyCols[valleyCols.size / 2]
            }
            dividers[i] = chosen.coerceIn(dividers[i - 1] + 1, rightPx - (n - i))
        }

        return List(n) { i ->
            TextCharSpan(i, i + 1, dividers[i], topPx, max(dividers[i] + 1, dividers[i + 1]), bottomPx)
        }
    }

    fun createInpaintedScreenBitmap(source: Bitmap, blocks: List<ScreenTextBlock>): Bitmap {
        val w = source.width; val h = source.height
        val mutable = source.copy(Bitmap.Config.ARGB_8888, true)
        if (blocks.isEmpty()) return mutable
        val pixels = IntArray(w * h)
        mutable.getPixels(pixels, 0, w, 0, 0, w, h)
        inpaintTextRegionsInArgb(pixels, w, h, blocks)
        mutable.setPixels(pixels, 0, w, 0, 0, w, h)
        return mutable
    }

    internal fun inpaintTextRegionsInArgb(pixels: IntArray, width: Int, height: Int, blocks: List<ScreenTextBlock>) {
        if (width < 4 || height < 4 || pixels.size < width * height) return
        val seen = HashSet<String>(blocks.size)
        for (block in blocks) {
            val key = "${block.leftPx}_${block.topPx}_${block.rightPx}_${block.bottomPx}"
            if (seen.add(key)) inpaintSingleBoxInArgb(pixels, width, height, block)
        }
    }

    internal fun inpaintSingleBoxInArgb(pixels: IntArray, width: Int, height: Int, block: ScreenTextBlock) {
        val pad = 4
        val rawL = min(block.leftPx, block.renderLeftPx)
        val rawT = min(block.topPx, block.renderTopPx)
        val rawR = max(block.rightPx, block.renderRightPx)
        val rawB = max(block.bottomPx, block.renderBottomPx)
        val l = (rawL - pad).coerceIn(0, width - 1); val t = (rawT - pad).coerceIn(0, height - 1)
        val r = (rawR + pad).coerceIn(l + 1, width); val b = (rawB + pad).coerceIn(t + 1, height)
        val bw = r - l; val bh = b - t
        if (bw < 2 || bh < 2) return

        val outerT = max(0, t - 2); val outerB = min(height - 1, b + 1)
        val outerL = max(0, l - 2); val outerR = min(width - 1, r + 1)
        val topBorder = ArrayList<Int>(16)
        val botBorder = ArrayList<Int>(16)
        val allBorder = ArrayList<Int>(48)
        for (x in l until r step max(1, bw / 12)) {
            val tc = pixels[outerT * width + x]
            val bc = pixels[outerB * width + x]
            topBorder += tc; botBorder += bc
            allBorder += tc; allBorder += bc
        }
        for (y in t until b step max(1, bh / 8)) {
            allBorder += pixels[y * width + outerL]
            allBorder += pixels[y * width + outerR]
        }
        val topAnchor = dominantMedianColor(topBorder)
        val botAnchor = dominantMedianColor(botBorder)
        var bgDiffMax = 0
        for (c in topBorder) bgDiffMax = max(bgDiffMax, colorDiff(c, topAnchor))
        for (c in botBorder) bgDiffMax = max(bgDiffMax, colorDiff(c, botAnchor))
        val uniform = bgDiffMax <= 26 && colorDiff(topAnchor, botAnchor) <= 64 && colorDiff(topAnchor, pixels[t * width + outerL]) <= 36

        if (uniform) {
            for (dy in 0 until bh) {
                val fy = dy.toFloat() / max(1, bh - 1)
                val vert = lerpArgb(topAnchor, botAnchor, fy)
                val leftC = pixels[(t + dy) * width + outerL]; val rightC = pixels[(t + dy) * width + outerR]
                val dstRow = (t + dy) * width
                val alphaY = if (dy == 0 || dy == bh - 1) 0.78f else 1.0f
                for (dx in 0 until bw) {
                    val fx = dx.toFloat() / max(1, bw - 1)
                    val f = lerpArgb(vert, lerpArgb(leftC, rightC, fx), 0.20f)
                    val rimAlpha = if (dx == 0 || dx == bw - 1) min(alphaY, 0.78f) else alphaY
                    pixels[dstRow + l + dx] = if (rimAlpha >= 0.99f) f else lerpArgb(pixels[dstRow + l + dx], f, rimAlpha)
                }
            }
            return
        }

        // Textured / photo background: use dominant sampled background tone cleanly so neighboring dark text does not streak across the patch.
        val fillColor = if (block.bgColorArgb != COLOR_WHITE &&
            ScreenTextSelectionPolicy.wcagContrast(block.bgColorArgb, block.textColorArgb) >= 3.5f
        ) {
            block.bgColorArgb
        } else {
            dominantMedianColor(allBorder)
        }
        for (dy in 0 until bh) {
            val dstRow = (t + dy) * width
            for (dx in 0 until bw) {
                val onRim = dy == 0 || dy == bh - 1 || dx == 0 || dx == bw - 1
                pixels[dstRow + l + dx] = if (onRim) lerpArgb(pixels[dstRow + l + dx], fillColor, 0.78f) else fillColor
            }
        }
    }

    internal fun lerpArgb(a: Int, b: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f); val inv = 1f - f
        return packArgb(
            (chA(a) * inv + chA(b) * f).roundToInt().coerceIn(0, 255),
            (chR(a) * inv + chR(b) * f).roundToInt().coerceIn(0, 255),
            (chG(a) * inv + chG(b) * f).roundToInt().coerceIn(0, 255),
            (chB(a) * inv + chB(b) * f).roundToInt().coerceIn(0, 255)
        )
    }

    private fun dominantMedianColor(samples: List<Int>): Int {
        if (samples.isEmpty()) return COLOR_WHITE
        val buckets = HashMap<Int, ArrayList<Int>>(16)
        for (c in samples) {
            val key = ((chR(c) shr 4) shl 8) or ((chG(c) shr 4) shl 4) or (chB(c) shr 4)
            buckets.getOrPut(key) { ArrayList() }.add(c)
        }
        val group = buckets.maxByOrNull { it.value.size }?.value ?: return samples[0]
        val m = group.size / 2
        return packRgb(group.map { chR(it) }.sorted()[m], group.map { chG(it) }.sorted()[m], group.map { chB(it) }.sorted()[m])
    }

    private fun stripBorderLineRuns(hist: IntArray, minIdx: Int, maxIdx: Int): Pair<Int, Int> {
        var lo = minIdx; var hi = maxIdx
        while (lo + 6 <= hi && hist[lo] > 0) {
            var end = lo
            while (end + 1 <= hi && end - lo < 2 && hist[end + 1] > 0) end++
            if (end - lo <= 1 && (end + 1..min(hi, end + 3)).all { hist[it] == 0 }) {
                var next = end + 1; while (next <= hi && hist[next] == 0) next++
                if (next - end >= 3 && next <= hi) { lo = next; continue }
            }
            break
        }
        while (hi - 6 >= lo && hist[hi] > 0) {
            var start = hi
            while (start - 1 >= lo && hi - start < 2 && hist[start - 1] > 0) start--
            if (hi - start <= 1 && (max(lo, start - 3) until start).all { hist[it] == 0 }) {
                var prev = start - 1; while (prev >= lo && hist[prev] == 0) prev--
                if (start - prev >= 3 && prev >= lo) { hi = prev; continue }
            }
            break
        }
        return lo to hi
    }

    private fun detectInkBands(rowInk: IntArray, startY: Int, endY: Int, minBandSize: Int): List<Pair<Int, Int>> {
        val bands = ArrayList<Pair<Int, Int>>(); var runStart = -1
        for (y in startY..endY) {
            if (rowInk[y] > 0) { if (runStart < 0) runStart = y }
            else if (runStart >= 0) { if (y - runStart >= minBandSize) bands += runStart to (y - 1); runStart = -1 }
        }
        if (runStart >= 0 && endY - runStart + 1 >= minBandSize) bands += runStart to endY
        return bands
    }

    private fun sampleDominantTextColor(
        pixels: IntArray, rw: Int, rx0: Int, ry0: Int, l: Int, t: Int, r: Int, b: Int, bgTop: Int, bgBot: Int, rh: Int, fgThresh: Int
    ): Int? {
        var bestLumDist = -1; val candidates = ArrayList<Int>()
        for (dy in t until b) {
            val rowBg = lerpArgb(bgTop, bgBot, dy.toFloat() / max(1, rh - 1)); val bgL = luma(rowBg)
            val rowOff = (ry0 + dy) * rw + rx0
            for (dx in l until r) {
                val c = pixels[rowOff + dx]
                if (colorDiff(c, rowBg) > fgThresh) { candidates += c; bestLumDist = max(bestLumDist, abs(luma(c) - bgL)) }
            }
        }
        if (candidates.isEmpty()) return null
        val refBgL = luma(bgTop); val floor = (bestLumDist * 0.45f).roundToInt()
        var rs = 0L; var gs = 0L; var bs = 0L; var n = 0
        for (c in candidates) if (abs(luma(c) - refBgL) >= floor) { rs += chR(c); gs += chG(c); bs += chB(c); n++ }
        return if (n > 0) packRgb((rs / n).toInt(), (gs / n).toInt(), (bs / n).toInt()) else null
    }

    private fun charAdvanceWeight(ch: Char): Double = when {
        ch.isWhitespace() -> 0.45
        ch.code in 0x20..0x7E -> if (ch in "ijlItf!.,:;|'\"") 0.45 else 0.72
        else -> 1.0
    }

    private fun chA(c: Int): Int = (c ushr 24) and 0xFF
    private fun chR(c: Int): Int = (c ushr 16) and 0xFF
    private fun chG(c: Int): Int = (c ushr 8) and 0xFF
    private fun chB(c: Int): Int = c and 0xFF
    private fun packArgb(a: Int, r: Int, g: Int, b: Int): Int = (a shl 24) or (r shl 16) or (g shl 8) or b
    private fun packRgb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun colorDiff(a: Int, b: Int): Int = abs(chR(a) - chR(b)) + abs(chG(a) - chG(b)) + abs(chB(a) - chB(b))

    private fun luma(c: Int): Int = (chR(c) * 77 + chG(c) * 150 + chB(c) * 29) shr 8
}
