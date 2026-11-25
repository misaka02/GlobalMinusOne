package com.example.globalminusone.screenassistant

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** Represents a character range [startIndex, endIndex) selected directly inside a [ScreenTextBlock]. */
data class TextSelectionSpan(
    val blockId: String,
    val startIndex: Int,
    val endIndex: Int
)

/**
 * Pure-Kotlin geometry, typography, color quantization, and selection helpers for seamless
 * in-image text selection and near-imperceptible in-place translation rendering.
 */
internal object ScreenTextSelectionPolicy {

    /** Group visually overlapping rows before ordering horizontally, independent of pixel density. */
    fun inReadingOrder(blocks: List<ScreenTextBlock>): List<ScreenTextBlock> {
        val rows = mutableListOf<MutableList<ScreenTextBlock>>()
        for (block in blocks.sortedWith(compareBy({ it.topPx }, { it.leftPx }))) {
            val row = rows.lastOrNull()
            val anchor = row?.firstOrNull()
            val sameRow = anchor?.let {
                val overlap = min(it.bottomPx, block.bottomPx) - max(it.topPx, block.topPx)
                val height = min(it.heightPx, block.heightPx)
                overlap.toLong() * 2 >= height && max(it.heightPx, block.heightPx).toLong() <= height.toLong() * 2
            } == true
            if (row != null && sameRow) row.add(block) else rows.add(mutableListOf(block))
        }
        return rows.flatMap { row -> row.sortedBy { it.leftPx } }
    }

    fun fullSpan(block: ScreenTextBlock): TextSelectionSpan =
        TextSelectionSpan(block.id, 0, block.text.length)

    fun sliceBlockText(block: ScreenTextBlock, span: TextSelectionSpan): String {
        if (block.text.isEmpty()) return ""
        val start = span.startIndex.coerceIn(0, block.text.length)
        val end = span.endIndex.coerceIn(start, block.text.length)
        return block.text.substring(start, end)
    }

    fun mergeSelectedSpansText(
        blocks: List<ScreenTextBlock>,
        spans: Map<String, TextSelectionSpan>
    ): String {
        if (spans.isEmpty()) return ""
        return blocks.mapNotNull { block ->
            val span = spans[block.id] ?: return@mapNotNull null
            sliceBlockText(block, span).trim().takeIf { it.isNotEmpty() }
        }.joinToString("\n")
    }

    private fun charAdvanceWeight(ch: Char): Float = when {
        ch.isWhitespace() || ch in ".,!?;:'\"()[]|/\\-" -> 0.55f
        ch.code >= 0x2E80 -> 1.95f
        else -> 1.0f
    }

    private fun cumulativeWeights(text: String): FloatArray {
        val weights = FloatArray(text.length + 1)
        var acc = 0f
        for (i in text.indices) {
            acc += charAdvanceWeight(text[i])
            weights[i + 1] = acc
        }
        return weights
    }

    /** Maps a horizontal coordinate inside [leftPx, rightPx] to a character boundary [0, text.length], using exact char boxes when available. */
    fun pointToCharIndex(
        text: String,
        x: Float,
        leftPx: Float,
        rightPx: Float,
        charSpans: List<TextCharSpan> = emptyList()
    ): Int {
        if (text.isEmpty()) return 0
        if (charSpans.isNotEmpty()) {
            val ordered = charSpans.sortedBy { it.startIndex }
            if (x <= ordered.first().leftPx) return ordered.first().startIndex.coerceIn(0, text.length)
            if (x >= ordered.last().rightPx) return ordered.last().endIndex.coerceIn(0, text.length)
            for (i in ordered.indices) {
                val span = ordered[i]
                val s = span.startIndex.coerceIn(0, text.length)
                val e = span.endIndex.coerceIn(s, text.length)
                if (x < span.rightPx || i == ordered.lastIndex) {
                    if (e - s <= 1) {
                        val mid = (span.leftPx + span.rightPx) * 0.5f
                        return if (x < mid) s else e
                    }
                    val sw = (span.rightPx - span.leftPx).coerceAtLeast(1).toFloat()
                    val subIdx = pointToCharIndex(text.substring(s, e), x, span.leftPx.toFloat(), span.leftPx + sw, emptyList())
                    return (s + subIdx).coerceIn(0, text.length)
                }
                val next = ordered.getOrNull(i + 1)
                if (next != null && x < next.leftPx) {
                    val gapMid = (span.rightPx + next.leftPx) * 0.5f
                    return if (x <= gapMid) e else next.startIndex.coerceIn(0, text.length)
                }
            }
        }
        val width = (rightPx - leftPx).coerceAtLeast(1f)
        val fraction = ((x - leftPx) / width).coerceIn(0f, 1f)
        val weights = cumulativeWeights(text)
        val total = weights[text.length].coerceAtLeast(1f)
        val targetWeight = fraction * total
        var bestIdx = 0
        var bestDiff = Float.MAX_VALUE
        for (i in weights.indices) {
            val diff = abs(weights[i] - targetWeight)
            if (diff < bestDiff) {
                bestDiff = diff
                bestIdx = i
            }
        }
        return bestIdx
    }

    /** Computes tight [topPx, bottomPx] from the overlapping charSpans of a selected substring. */
    fun spanToTightVerticalBounds(
        block: ScreenTextBlock,
        startIndex: Int,
        endIndex: Int
    ): Pair<Float, Float> {
        val baseTop = block.renderTopPx.toFloat()
        val baseBot = block.renderBottomPx.coerceAtLeast(block.renderTopPx + 1).toFloat()
        if (block.charSpans.isEmpty() || endIndex <= startIndex) return baseTop to baseBot
        var minTop = Float.MAX_VALUE
        var maxBot = -1f
        for (cs in block.charSpans) {
            if (cs.endIndex > startIndex && cs.startIndex < endIndex) {
                minTop = min(minTop, cs.topPx.toFloat())
                maxBot = max(maxBot, cs.bottomPx.toFloat())
            }
        }
        return if (maxBot > minTop) minTop to maxBot else baseTop to baseBot
    }

    /** Computes [startX, endX] in the same coordinate space as [leftPx, rightPx] for a selected substring span. */
    fun spanToHorizontalBounds(
        text: String,
        startIndex: Int,
        endIndex: Int,
        leftPx: Float,
        rightPx: Float,
        charSpans: List<TextCharSpan> = emptyList()
    ): Pair<Float, Float> {
        if (text.isEmpty()) return leftPx to rightPx
        val s = startIndex.coerceIn(0, text.length)
        val e = endIndex.coerceIn(s, text.length)
        if (charSpans.isNotEmpty() && e > s) {
            fun boundaryToX(charPos: Int, isEnd: Boolean): Float? {
                val ordered = charSpans.sortedBy { it.startIndex }
                if (charPos <= ordered.first().startIndex) return ordered.first().leftPx.toFloat()
                if (charPos >= ordered.last().endIndex) return ordered.last().rightPx.toFloat()
                for (span in ordered) {
                    if (charPos in span.startIndex..span.endIndex) {
                        val len = (span.endIndex - span.startIndex).coerceAtLeast(1)
                        if (charPos == span.startIndex && !isEnd) return span.leftPx.toFloat()
                        if (charPos == span.endIndex && isEnd) return span.rightPx.toFloat()
                        val frac = (charPos - span.startIndex).toFloat() / len.toFloat()
                        return span.leftPx + frac * (span.rightPx - span.leftPx).toFloat()
                    }
                }
                return null
            }
            val xl = boundaryToX(s, isEnd = false)
            val xr = boundaryToX(e, isEnd = true)
            if (xl != null && xr != null && xr >= xl) {
                return if (xr - xl < 4f) xl to min(rightPx, xl + 4f) else xl to xr
            }
        }
        val width = (rightPx - leftPx).coerceAtLeast(1f)
        val weights = cumulativeWeights(text)
        val total = weights[text.length].coerceAtLeast(1f)
        val l = leftPx + (weights[s] / total) * width
        val r = leftPx + (weights[e] / total) * width
        return if (e > s && r - l < 4f) l to min(rightPx, l + 4f) else l to r
    }

    /** Builds a clean single-line selection range when dragging horizontally from [downX] to [curX]. */
    fun horizontalDragRange(
        text: String,
        downX: Float,
        curX: Float,
        leftPx: Float,
        rightPx: Float,
        charSpans: List<TextCharSpan> = emptyList()
    ): Pair<Int, Int> {
        if (text.isEmpty()) return 0 to 0
        val a = pointToCharIndex(text, downX, leftPx, rightPx, charSpans)
        val b = pointToCharIndex(text, curX, leftPx, rightPx, charSpans)
        var start = min(a, b)
        var end = max(a, b)
        if (start == end) {
            if (end < text.length) end++ else start = (start - 1).coerceAtLeast(0)
        }
        return start to end
    }

    /** Skips overwriting unchanged labels, pure digits/timestamps, and punctuation tokens during translation. */
    fun shouldRenderTranslatedOverlay(original: String, translated: String?): Boolean {
        if (translated.isNullOrBlank()) return false
        val orig = original.trim()
        val tr = translated.trim()
        if (orig.isEmpty() || orig.equals(tr, ignoreCase = true)) return false
        if (orig.none { Character.isLetter(it) }) return false
        val origNorm = orig.filter { Character.isLetterOrDigit(it) }
        val trNorm = tr.filter { Character.isLetterOrDigit(it) }
        if (origNorm.isNotEmpty() && origNorm.equals(trNorm, ignoreCase = true)) return false
        return true
    }

    /** Estimates natural UI font pixel size matching surrounding typography rather than blowing up to 32sp. */
    fun estimateNaturalTextSizePx(
        blockWidthPx: Float,
        blockHeightPx: Float,
        origLength: Int,
        density: Float
    ): Float {
        val d = density.coerceAtLeast(1f)
        val minPx = 10f * d
        val singleLineCap = 22f * d
        val displayHeadlineCap = 29f * d
        val len = origLength.coerceAtLeast(1)
        val singleLinePx = blockHeightPx * 0.68f
        if (blockHeightPx <= 38f * d && len <= 28) {
            return singleLinePx.coerceIn(minPx, singleLineCap)
        }
        if (blockHeightPx > 38f * d && len <= 16 && blockWidthPx < blockHeightPx * 7.5f) {
            return singleLinePx.coerceIn(minPx, displayHeadlineCap)
        }
        val areaEstimate = sqrt((blockWidthPx * blockHeightPx * 0.65f) / len.toFloat())
        return areaEstimate.coerceIn(10.5f * d, 19f * d)
    }

    /** Finds dominant uniform color mode using 5-bit RGB quantization + cluster averaging. */
    fun selectDominantColorMode(argbSamples: List<Int>, alpha: Int = 255): Int {
        val outAlpha = (alpha.coerceIn(0, 255) shl 24)
        if (argbSamples.isEmpty()) return outAlpha or 0xFFFFFF
        val counts = HashMap<Int, Int>(min(argbSamples.size, 64))
        val sumR = HashMap<Int, Long>(min(argbSamples.size, 64))
        val sumG = HashMap<Int, Long>(min(argbSamples.size, 64))
        val sumB = HashMap<Int, Long>(min(argbSamples.size, 64))
        for (c in argbSamples) {
            val r = (c ushr 16) and 0xFF
            val g = (c ushr 8) and 0xFF
            val b = c and 0xFF
            val key = ((r ushr 3) shl 10) or ((g ushr 3) shl 5) or (b ushr 3)
            counts[key] = (counts[key] ?: 0) + 1
            sumR[key] = (sumR[key] ?: 0L) + r
            sumG[key] = (sumG[key] ?: 0L) + g
            sumB[key] = (sumB[key] ?: 0L) + b
        }
        val bestKey = counts.maxByOrNull { it.value }?.key ?: return outAlpha or 0xFFFFFF
        val br = (bestKey ushr 10) and 0x1F
        val bg = (bestKey ushr 5) and 0x1F
        val bb = bestKey and 0x1F
        var totalCount = 0L
        var accR = 0L
        var accG = 0L
        var accB = 0L
        for ((k, cnt) in counts) {
            val kr = (k ushr 10) and 0x1F
            val kg = (k ushr 5) and 0x1F
            val kb = k and 0x1F
            if (abs(kr - br) <= 1 && abs(kg - bg) <= 1 && abs(kb - bb) <= 1) {
                totalCount += cnt
                accR += sumR[k] ?: 0L
                accG += sumG[k] ?: 0L
                accB += sumB[k] ?: 0L
            }
        }
        if (totalCount <= 0L) return outAlpha or 0xFFFFFF
        val r = (accR / totalCount).toInt().coerceIn(0, 255)
        val g = (accG / totalCount).toInt().coerceIn(0, 255)
        val b = (accB / totalCount).toInt().coerceIn(0, 255)
        return outAlpha or (r shl 16) or (g shl 8) or b
    }

    fun colorManhattanDistance(a: Int, b: Int): Int {
        val dr = abs(((a ushr 16) and 0xFF) - ((b ushr 16) and 0xFF))
        val dg = abs(((a ushr 8) and 0xFF) - ((b ushr 8) and 0xFF))
        val db = abs((a and 0xFF) - (b and 0xFF))
        return dr + dg + db
    }

    fun estimateBackgroundUniformity(argbSamples: List<Int>, dominantBgArgb: Int): Boolean {
        if (argbSamples.isEmpty()) return true
        val matching = argbSamples.count { colorManhattanDistance(it, dominantBgArgb) <= 36 }
        return matching.toFloat() / argbSamples.size.toFloat() >= 0.70f
    }

    private fun relativeLuminance(argb: Int): Float {
        fun linear(ch: Int): Double {
            val s = ch / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        val r = linear((argb ushr 16) and 0xFF)
        val g = linear((argb ushr 8) and 0xFF)
        val b = linear(argb and 0xFF)
        return (0.2126 * r + 0.7152 * g + 0.0722 * b).toFloat()
    }

    fun wcagContrast(fgArgb: Int, bgArgb: Int): Float {
        val l1 = relativeLuminance(fgArgb)
        val l2 = relativeLuminance(bgArgb)
        val hi = max(l1, l2)
        val lo = min(l1, l2)
        return (hi + 0.05f) / (lo + 0.05f)
    }

    /** Preserves original ink hue when contrast is crisp; falls back to readable ink on low-contrast patches. */
    fun estimateReadableInkColor(foregroundSamples: List<Int>, bgArgb: Int): Int {
        if (foregroundSamples.isNotEmpty()) {
            val fgMode = selectDominantColorMode(foregroundSamples, alpha = 255)
            if (wcagContrast(fgMode, bgArgb) >= 4.2f) {
                return fgMode
            }
        }
        return if (relativeLuminance(bgArgb) >= 0.38f) 0xFF1A1D24.toInt() else 0xFFF8FAFC.toInt()
    }

    /** Computes [left, top] position for an on-canvas floating toolbar strictly inside safe viewport bounds. */
    fun computeFloatingBarPosition(
        anchorLeft: Float,
        anchorTop: Float,
        anchorRight: Float,
        anchorBottom: Float,
        barWidth: Float,
        barHeight: Float,
        safeLeft: Float,
        safeTop: Float,
        safeRight: Float,
        safeBottom: Float,
        gapPx: Float
    ): Pair<Float, Float> {
        val margin = gapPx.coerceAtLeast(4f)
        val minX = safeLeft + margin
        val maxX = max(minX, safeRight - barWidth - margin)
        val centerX = (anchorLeft + anchorRight) * 0.5f - barWidth * 0.5f
        val x = centerX.coerceIn(minX, maxX)

        val aboveY = anchorTop - barHeight - gapPx
        val belowY = anchorBottom + gapPx
        val maxY = max(safeTop + margin, safeBottom - barHeight - margin)
        val y = when {
            aboveY >= safeTop + margin -> aboveY.coerceAtMost(maxY)
            belowY <= safeBottom - barHeight - margin -> belowY.coerceAtLeast(safeTop + margin)
            else -> max(safeTop + margin, min(anchorTop + gapPx, maxY))
        }
        return x to y
    }

    private fun isTokenGroupChar(ch: Char): Boolean =
        ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch in "._-/:@"

    private fun isClauseBreakChar(ch: Char): Boolean =
        ch.isWhitespace() || ch in ",.!?;:'\"()[]{}|/\\<>，。！？；：、（）【】《》“”‘’·—|"

    /**
     * Picks the meaningful word/clause range under tap index [charIdx] inside [text]:
     * ASCII runs/numbers/URLs keep together; punctuation-separated CJK clauses keep intact.
     */
    fun semanticTokenRangeAtChar(text: String, charIdx: Int): Pair<Int, Int> {
        if (text.isEmpty()) return 0 to 0
        val idx = charIdx.coerceIn(0, text.length - 1)
        val ch = text[idx]
        if (ch.isWhitespace()) {
            val nonWs = text.indexOfFirst { !it.isWhitespace() }
            return if (nonWs < 0) 0 to text.length else semanticTokenRangeAtChar(text, nonWs)
        }
        if (isTokenGroupChar(ch)) {
            var s = idx
            var e = idx + 1
            while (s > 0 && isTokenGroupChar(text[s - 1])) s--
            while (e < text.length && isTokenGroupChar(text[e])) e++
            return s to e
        }
        if (isClauseBreakChar(ch)) {
            val leftNonBreak = (idx - 1 downTo 0).firstOrNull { !isClauseBreakChar(text[it]) }
            val rightNonBreak = (idx + 1 until text.length).firstOrNull { !isClauseBreakChar(text[it]) }
            val nearest = leftNonBreak ?: rightNonBreak ?: return idx to (idx + 1)
            return semanticTokenRangeAtChar(text, nearest)
        }
        // CJK or non-ASCII clause: span until delimiter/ASCII boundary so "订单号：12345" selects cleanly.
        var s = idx
        var e = idx + 1
        while (s > 0 && !isClauseBreakChar(text[s - 1]) && !isTokenGroupChar(text[s - 1])) s--
        while (e < text.length && !isClauseBreakChar(text[e]) && !isTokenGroupChar(text[e])) e++
        return s to e
    }

    /**
     * Picks the best target block while dragging selection across text lines.
     * Unlike rigid sticky-row locks, dragging horizontally onto another block on the same visual line
     * switches cleanly to that block; staying on the same row outside other blocks stays locked on [stickyIdx].
     */
    fun findBestDragBlockIndex(
        blocks: List<ScreenTextBlock>,
        bx: Float,
        by: Float,
        stickyIdx: Int = -1,
        hitPadX: Float = 8f,
        hitPadY: Float = 8f
    ): Int {
        if (blocks.isEmpty()) return -1
        // 1. Direct hit wins first (prefers smallest area block when nested), allowing horizontal same-row travel.
        val directHit = blocks.indices
            .filter { idx ->
                val b = blocks[idx]
                val l = min(b.leftPx, b.renderLeftPx).toFloat() - hitPadX
                val t = min(b.topPx, b.renderTopPx).toFloat() - hitPadY
                val r = max(b.rightPx, b.renderRightPx).toFloat() + hitPadX
                val bt = max(b.bottomPx, b.renderBottomPx).toFloat() + hitPadY
                bx in l..r && by in t..bt
            }
            .minByOrNull { idx ->
                val b = blocks[idx]
                val w = (b.renderRightPx - b.renderLeftPx).coerceAtLeast(1).toLong()
                val h = (b.renderBottomPx - b.renderTopPx).coerceAtLeast(1).toLong()
                w * h
            }
        if (directHit != null) return directHit

        // 2. If finger stays vertically within the sticky row band (and didn't directly enter a neighbor), keep line stability.
        if (stickyIdx in blocks.indices) {
            val sb = blocks[stickyIdx]
            val band = max(14f, (sb.renderBottomPx - sb.renderTopPx) * 0.48f)
            if (by in (sb.renderTopPx - band)..(sb.renderBottomPx + band)) {
                return stickyIdx
            }
        }

        // 3. Nearest block strongly prioritizing vertical line proximity over horizontal distance.
        var bestIdx = -1
        var bestScore = Float.MAX_VALUE
        for (idx in blocks.indices) {
            val b = blocks[idx]
            val l = b.renderLeftPx.toFloat()
            val t = b.renderTopPx.toFloat()
            val r = b.renderRightPx.coerceAtLeast(b.renderLeftPx + 1).toFloat()
            val bt = b.renderBottomPx.coerceAtLeast(b.renderTopPx + 1).toFloat()
            val dy = when {
                by < t -> t - by
                by > bt -> by - bt
                else -> 0f
            }
            val dx = when {
                bx < l -> l - bx
                bx > r -> bx - r
                else -> 0f
            }
            val score = dy * 4.5f + dx
            if (score < bestScore) {
                bestScore = score
                bestIdx = idx
            }
        }
        return bestIdx
    }

    /** Computes continuous bidirectional character-accurate selection across sorted reading-order [blocks], excluding disjoint columns. */
    fun computeContinuousSelectionSpans(
        blocks: List<ScreenTextBlock>,
        anchorBlockIdx: Int,
        anchorCharIdx: Int,
        curBlockIdx: Int,
        curCharIdx: Int
    ): LinkedHashMap<String, TextSelectionSpan> {
        val result = LinkedHashMap<String, TextSelectionSpan>()
        if (blocks.isEmpty()) return result
        val aB = anchorBlockIdx.coerceIn(0, blocks.lastIndex)
        val cB = curBlockIdx.coerceIn(0, blocks.lastIndex)
        val aC = anchorCharIdx.coerceIn(0, blocks[aB].text.length)
        val cC = curCharIdx.coerceIn(0, blocks[cB].text.length)

        val forward = aB < cB || (aB == cB && aC <= cC)
        val startB = if (forward) aB else cB
        val startC = if (forward) aC else cC
        val endB = if (forward) cB else aB
        val endC = if (forward) cC else aC

        val firstBlock = blocks[startB]
        val lastBlock = blocks[endB]
        val colLeft = min(firstBlock.renderLeftPx, lastBlock.renderLeftPx) - 24
        val colRight = max(firstBlock.renderRightPx, lastBlock.renderRightPx) + 24

        for (i in startB..endB) {
            val b = blocks[i]
            val len = b.text.length
            if (len <= 0) continue
            when {
                startB == endB -> {
                    val s = startC.coerceIn(0, (len - 1).coerceAtLeast(0))
                    val e = max(s + 1, endC).coerceIn(s + 1, len)
                    result[b.id] = TextSelectionSpan(b.id, s, e)
                }
                i == startB -> {
                    val s = startC.coerceIn(0, (len - 1).coerceAtLeast(0))
                    if (s < len) result[b.id] = TextSelectionSpan(b.id, s, len)
                }
                i == endB -> {
                    val e = endC.coerceIn(1, len)
                    result[b.id] = TextSelectionSpan(b.id, 0, e)
                }
                else -> {
                    // Exclude unrelated side-by-side columns when sweeping vertically down a narrow column.
                    val horizontallyInBand = b.renderRightPx >= colLeft && b.renderLeftPx <= colRight
                    if (horizontallyInBand) {
                        result[b.id] = fullSpan(b)
                    }
                }
            }
        }
        return result
    }

    /**
     * Keeps cropped region overlays crisp: renders normal crops at <= 1:1 native display pixels
     * instead of stretching up to 2.5x into blurry blocks, and caps tiny snippets at <= 1.35x.
     */
    fun computeCroppedDisplayScale(
        availW: Float,
        availH: Float,
        bmpWidth: Int,
        bmpHeight: Int
    ): Float {
        val bw = bmpWidth.coerceAtLeast(1).toFloat()
        val bh = bmpHeight.coerceAtLeast(1).toFloat()
        val fitScale = min(availW.coerceAtLeast(1f) / bw, availH.coerceAtLeast(1f) / bh)
        val tinyBoostCap = if (max(bmpWidth, bmpHeight) < 260) 1.35f else 1.0f
        return fitScale.coerceAtMost(tinyBoostCap)
    }

    /**
     * Maps a full-screen screenshot onto [viewW] x [viewH] without distortion.
     * Exact size match maps 1:1 to [0, 0, viewW, viewH]; slight mismatches fit preserving aspect ratio.
     */
    fun computeFullDisplayRect(
        viewW: Float,
        viewH: Float,
        bmpWidth: Int,
        bmpHeight: Int
    ): FloatArray {
        val vw = viewW.coerceAtLeast(1f)
        val vh = viewH.coerceAtLeast(1f)
        val bw = bmpWidth.coerceAtLeast(1).toFloat()
        val bh = bmpHeight.coerceAtLeast(1).toFloat()
        if (abs(vw - bw) <= 2f && abs(vh - bh) <= 2f) {
            return floatArrayOf(0f, 0f, vw, vh)
        }
        val scale = min(vw / bw, vh / bh)
        val dw = bw * scale
        val dh = bh * scale
        val left = (vw - dw) * 0.5f
        val top = (vh - dh) * 0.5f
        return floatArrayOf(left, top, left + dw, top + dh)
    }

    /**
     * Bounds the horizontal drawing slot for an in-place translated block so translations never
     * fly sideways across adjacent columns or cover images:
     * - Multi-line clustered paragraphs (`sourceLineCount > 1`) stay strictly inside `[brLeft..brRight]`.
     * - Single-line labels expand at most `min(lineH * 1.8f, brWidth * 0.28f)` and stop >= 8px
     *   before neighboring text blocks on the same vertical band.
     */
    fun computeTranslateSlotHorizontalBounds(
        block: ScreenTextBlock,
        brLeft: Int,
        brTop: Int,
        brRight: Int,
        brBottom: Int,
        allBlocks: List<ScreenTextBlock>,
        bitmapWidth: Int
    ): Pair<Int, Int> {
        val safeW = bitmapWidth.coerceAtLeast(16)
        val clampedL = brLeft.coerceIn(0, safeW - 12)
        val clampedR = brRight.coerceIn(clampedL + 12, safeW)
        val isSingleLine = block.sourceLineCount <= 1 && !block.text.contains('\n')
        if (!isSingleLine) {
            return clampedL to clampedR
        }

        val neighborLeft = allBlocks
            .asSequence()
            .filter { other ->
                other.id != block.id &&
                    other.topPx < brBottom &&
                    other.bottomPx > brTop &&
                    other.leftPx > clampedR
            }
            .minOfOrNull { it.leftPx } ?: (safeW - 4)
        val neighborRightOfLeft = allBlocks
            .asSequence()
            .filter { other ->
                other.id != block.id &&
                    other.topPx < brBottom &&
                    other.bottomPx > brTop &&
                    other.rightPx < clampedL
            }
            .maxOfOrNull { it.rightPx } ?: 4

        val brWidth = (clampedR - clampedL).coerceAtLeast(1)
        val lineH = (block.inkHeightPx.takeIf { it > 0 } ?: (brBottom - brTop)).coerceAtLeast(10)
        val maxSlack = min(lineH * 1.8f, brWidth * 0.28f).toInt().coerceAtLeast(0)

        return when (block.alignmentHint) {
            1 -> { // ALIGN_CENTER
                val origCenterX = (clampedL + clampedR) * 0.5f
                val halfAvailL = (origCenterX - (neighborRightOfLeft + 8)).coerceAtLeast(brWidth * 0.5f)
                val halfAvailR = ((neighborLeft - 8) - origCenterX).coerceAtLeast(brWidth * 0.5f)
                val symHalf = minOf(halfAvailL, halfAvailR, brWidth * 0.62f).coerceAtLeast(brWidth * 0.5f)
                val slotL = (origCenterX - symHalf).toInt().coerceIn(0, safeW - 12)
                val slotR = (origCenterX + symHalf).toInt().coerceIn(slotL + 12, safeW)
                slotL to slotR
            }
            2 -> { // ALIGN_RIGHT
                val slotR = clampedR.coerceIn(14, safeW)
                val slotL = max(neighborRightOfLeft + 8, clampedL - maxSlack).coerceIn(0, slotR - 12)
                slotL to slotR
            }
            else -> { // ALIGN_LEFT
                val slotL = clampedL.coerceIn(0, safeW - 14)
                val maxClearR = (neighborLeft - 8).coerceIn(min(slotL + 12, safeW), safeW)
                val slotR = min(maxClearR, max(clampedR, block.rightPx) + maxSlack)
                    .coerceAtLeast(min(slotL + 12, safeW))
                slotL to slotR
            }
        }
    }

    fun clusterParagraphBlocks(blocks: List<ScreenTextBlock>): List<ScreenTextBlock> {
        if (blocks.size <= 1) return blocks
        val ordered = inReadingOrder(blocks)
        val out = ArrayList<ScreenTextBlock>(ordered.size)
        for (next in ordered) {
            val prev = out.lastOrNull()
            if (prev == null) { out += next; continue }
            val prevH = (prev.inkHeightPx.takeIf { it > 0 } ?: prev.heightPx).coerceAtLeast(10)
            val nextH = (next.inkHeightPx.takeIf { it > 0 } ?: next.heightPx).coerceAtLeast(10)
            val lineH = max(prevH, nextH).coerceAtLeast(12)
            val minLineH = min(prevH, nextH).coerceAtLeast(10)
            val gapY = next.topPx - prev.bottomPx
            val sameHeight = lineH <= (minLineH * 1.45f).toInt()
            val compatibleBold = prev.isBoldHint == next.isBoldHint || abs(prevH - nextH) <= max(3, (minLineH * 0.16f).toInt())
            val sameLeft = abs(prev.renderLeftPx - next.renderLeftPx) <= max(28, lineH * 2)
            val horizOverlap = min(prev.rightPx, next.rightPx) - max(prev.leftPx, next.leftPx)
            val sameColumn = sameLeft || (horizOverlap >= min(prev.widthPx, next.widthPx) / 2 && abs(prev.leftPx - next.leftPx) <= max(42, lineH * 3))
            val shortUiLabels = prev.widthPx <= lineH * 4 && next.widthPx <= lineH * 4
            val sameStyle = colorManhattanDistance(prev.textColorArgb, next.textColorArgb) <= 56 &&
                colorManhattanDistance(prev.bgColorArgb, next.bgColorArgb) <= 48
            val canMerge = prev.uniformBg && next.uniformBg &&
                sameHeight && compatibleBold && sameColumn && sameStyle &&
                !shortUiLabels && gapY in -(lineH / 3)..(lineH * 1.25f).toInt()
            if (canMerge) {
                val last = prev.text.lastOrNull()
                val first = next.text.firstOrNull()
                val asciiJoin = last != null && first != null &&
                    last.code in 0x20..0x7E && first.code in 0x20..0x7E &&
                    (last.isLetterOrDigit() || last in ",.;:!?") && first.isLetterOrDigit()
                val sep = if (asciiJoin) " " else ""
                val prevCx = (prev.leftPx + prev.rightPx) * 0.5f
                val nextCx = (next.leftPx + next.rightPx) * 0.5f
                val keepCenter = prev.alignmentHint == 1 && next.alignmentHint == 1 &&
                    abs(prevCx - nextCx) <= max(18f, prev.widthPx * 0.10f)
                out[out.lastIndex] = prev.copy(
                    text = prev.text + sep + next.text,
                    leftPx = min(prev.leftPx, next.leftPx), topPx = min(prev.topPx, next.topPx),
                    rightPx = max(prev.rightPx, next.rightPx), bottomPx = max(prev.bottomPx, next.bottomPx),
                    renderLeftPx = min(prev.renderLeftPx, next.renderLeftPx), renderTopPx = min(prev.renderTopPx, next.renderTopPx),
                    renderRightPx = max(prev.renderRightPx, next.renderRightPx), renderBottomPx = max(prev.renderBottomPx, next.renderBottomPx),
                    alignmentHint = if (keepCenter) 1 else 0,
                    sourceLineCount = prev.sourceLineCount + next.sourceLineCount
                )
            } else {
                out += next
            }
        }
        return out
    }
}
