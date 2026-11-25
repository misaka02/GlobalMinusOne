package com.example.globalminusone.screenassistant

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt

data class TextCharSpan(val startIndex: Int, val endIndex: Int, val leftPx: Int, val topPx: Int, val rightPx: Int, val bottomPx: Int)

data class ScreenTextBlock(
    val id: String, val text: String, val leftPx: Int, val topPx: Int, val rightPx: Int, val bottomPx: Int,
    val translatedText: String? = null, val bgColorArgb: Int = 0xEE1F2329.toInt(), val textColorArgb: Int = 0xFFFFFFFF.toInt(),
    val sourceTag: String = "", val bgBottomColorArgb: Int = bgColorArgb, val uniformBg: Boolean = true,
    val renderLeftPx: Int = leftPx, val renderTopPx: Int = topPx, val renderRightPx: Int = rightPx, val renderBottomPx: Int = bottomPx,
    val charSpans: List<TextCharSpan> = emptyList(), val isBoldHint: Boolean = false, val alignmentHint: Int = 0,
    val inkHeightPx: Int = 0, val sourceLineCount: Int = 1, val isDirectRenderedText: Boolean = false
) {
    val widthPx: Int get() = (rightPx - leftPx).coerceAtLeast(1)
    val heightPx: Int get() = (bottomPx - topPx).coerceAtLeast(1)
}

data class ExtractedSubImageCandidate(
    val id: String, val leftPx: Int, val topPx: Int, val rightPx: Int, val bottomPx: Int, val sourceTag: String, val confidence: Float = 0.8f
) {
    val widthPx: Int get() = (rightPx - leftPx).coerceAtLeast(1)
    val heightPx: Int get() = (bottomPx - topPx).coerceAtLeast(1)
    val areaPx: Long get() = widthPx.toLong() * heightPx.toLong()
}

data class LexiconEntry(val source: String, val target: String)
data class BreenoCandidateTarget(val packageName: String, val className: String?, val label: String)

internal object ScreenAssistantPolicy {
    private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }
    val knownBreenoCandidates = listOf(BreenoCandidateTarget("com.coloros.directui", null, "ColorOS 系统识屏包"), BreenoCandidateTarget("com.oplus.directui", null, "Oplus 系统识屏包"))

    private fun isAsciiWordChar(ch: Char) = ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9'
    private fun isInlineConnector(text: String, idx: Int) = text[idx] in charArrayOf('.', '-', '_', '/', ':', '@') && text.getOrNull(idx + 1)?.let(::isAsciiWordChar) == true

    fun tokenizeForSelection(text: String): List<String> {
        val result = ArrayList<String>(); var idx = 0
        while (idx < text.length) {
            val ch = text[idx]
            when {
                ch.isWhitespace() -> idx++
                isAsciiWordChar(ch) -> {
                    val start = idx++
                    while (idx < text.length && (isAsciiWordChar(text[idx]) || isInlineConnector(text, idx))) idx++
                    result.add(text.substring(start, idx))
                }
                else -> { result.add(ch.toString()); idx++ }
            }
        }
        return result
    }

    fun joinSelectedTokens(tokens: List<String>, selectedIndices: Set<Int>, fallbackFullText: String): String {
        val ordered = tokens.indices.filter { it in selectedIndices }
        if (tokens.isEmpty() || ordered.isEmpty()) return fallbackFullText
        val out = StringBuilder(); var prevIdx = -1; var prevTok = ""
        for (idx in ordered) {
            val tok = tokens[idx]
            if (prevIdx >= 0) {
                val needGap = idx != prevIdx + 1 || (prevTok.lastOrNull()?.let(::isAsciiWordChar) == true && tok.firstOrNull()?.let(::isAsciiWordChar) == true)
                if (needGap) out.append(' ')
            }
            out.append(tok); prevIdx = idx; prevTok = tok
        }
        return out.toString().trim()
    }

    fun pickRobustBackgroundArgb(samplesArgb: List<Int>, alpha: Int = 255): Int {
        if (samplesArgb.isEmpty()) return ((alpha.coerceIn(0, 255) and 0xFF) shl 24) or 0xFFFFFF
        val sorted = samplesArgb.sortedBy { c -> ((c ushr 16) and 0xFF) * 299 + ((c ushr 8) and 0xFF) * 587 + (c and 0xFF) * 114 }
        val n = sorted.size; val core = sorted.subList(if (n >= 4) n / 4 else 0, (if (n >= 4) (n * 3) / 4 else n - 1).coerceAtLeast(if (n >= 4) n / 4 else 0) + 1)
        val mid = core.size / 2
        val r = core.map { (it ushr 16) and 0xFF }.sorted()[mid]; val g = core.map { (it ushr 8) and 0xFF }.sorted()[mid]; val b = core.map { it and 0xFF }.sorted()[mid]
        return (alpha.coerceIn(0, 255) shl 24) or (r shl 16) or (g shl 8) or b
    }

    private val delimitedMarkerRegex = Regex("\\s*[|｜]{2}\\s*[gG]\\s*(\\d+)\\s*[|｜]{2}\\s*")
    private val linePrefixMarkerRegex = Regex("^[|｜]{1,2}\\s*[gG]\\s*\\d+\\s*[|｜]{1,2}\\s*")

    fun splitDelimitedBatch(translated: String, originals: List<String>): List<String>? {
        if (originals.isEmpty()) return emptyList()
        val matches = delimitedMarkerRegex.findAll(translated).toList()
        if (matches.size == originals.size) {
            val map = HashMap<Int, String>(matches.size)
            for (i in matches.indices) {
                val key = matches[i].groupValues[1].toIntOrNull() ?: continue
                map[key] = translated.substring(matches[i].range.last + 1, if (i + 1 < matches.size) matches[i + 1].range.first else translated.length).trim()
            }
            if (map.size == originals.size) return originals.indices.map { map[it]?.ifBlank { null } ?: originals[it] }
        }
        val lines = translated.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return if (lines.size == originals.size && matches.isNotEmpty()) lines.mapIndexed { i, l -> l.replace(linePrefixMarkerRegex, "").trim().ifBlank { originals[i] } } else null
    }

    private val urlOnlyRegex = Regex("""^(?:https?://|www\.)\S+$""", RegexOption.IGNORE_CASE)
    private val latinWordRegex = Regex("""[A-Za-z\u00C0-\u024F]{2,}""")
    private val technicalAcronyms = setOf("CPU", "GPU", "RAM", "ROM", "USB", "NFC", "GPS", "VPN", "DNS", "SSH", "FTP", "HTTP", "HTTPS", "URL", "URI", "API", "SDK", "JDK", "APK", "IPA", "OCR", "LLM", "PDF", "PNG", "JPG", "JPEG", "WEBP", "GIF", "SVG", "MP4", "MP3", "HDR", "FPS", "WIFI", "WLAN", "LTE", "NR", "LED", "LCD", "OLED", "HDMI", "SATA", "NVME", "IOS", "IPAD", "MACOS", "AI", "ML", "UI", "UX", "ID", "VIP", "HD", "PRO", "MAX")
    private val commonAllCapsUiWords = setOf("HOME", "MENU", "BACK", "NEXT", "SKIP", "DONE", "SAVE", "EDIT", "MORE", "OPEN", "PLAY", "STOP", "COPY", "SEND", "HELP", "INFO", "NEWS", "SHOP", "CART", "USER", "LIST", "FEED", "POST", "LIKE", "SYNC", "SCAN", "EXIT", "FIND", "SORT", "VIEW", "TAGS", "NONE", "FREE", "PAID", "AUTO", "DARK")

    private fun isMixedChineseUiOnly(text: String): Boolean {
        val cjk = text.count { it.code in 0x4E00..0x9FFF }; if (cjk == 0) return false
        val toks = latinWordRegex.findAll(text).map { it.value }.toList(); if (toks.isEmpty()) return true
        val letters = toks.sumOf { it.length }; if (letters > cjk * 2) return false
        return toks.all { t -> t.uppercase() in technicalAcronyms || (t.all { it.isUpperCase() } && t.length <= 5) || (cjk >= 3 && t.length <= 4 && toks.size <= 2 && letters <= cjk) }
    }

    fun needsTranslation(text: String, targetLanguage: String): Boolean {
        val t = text.trim(); if (t.isEmpty() || t.none { Character.isLetter(it) } || urlOnlyRegex.matches(t)) return false
        val tgt = targetLanguage.trim().lowercase()
        val hasKana = t.any { it.code in 0x3040..0x30FF || it.code in 0x31F0..0x31FF }
        val hasHangul = t.any { it.code in 0xAC00..0xD7A3 || it.code in 0x1100..0x11FF || it.code in 0x3130..0x318F }
        val hasCyr = t.any { it.code in 0x0400..0x04FF }; val hasAr = t.any { it.code in 0x0600..0x06FF }
        val hasCjk = t.any { it.code in 0x4E00..0x9FFF }; val hasLat = latinWordRegex.containsMatchIn(t)
        return when {
            tgt.startsWith("zh") -> if (hasKana || hasHangul || hasCyr || hasAr) true else if (hasCjk) !isMixedChineseUiOnly(t) else hasLat || t.any { it in 'A'..'Z' || it in 'a'..'z' || it.code in 0x00C0..0x024F || it.code >= 0x0370 }
            tgt.startsWith("en") -> hasCjk || hasKana || hasHangul || hasCyr || hasAr || t.any { it.code in 0x00C0..0x024F || it.code >= 0x0370 }
            tgt.startsWith("ja") -> !hasKana || hasLat || hasHangul || hasCyr || hasAr
            tgt.startsWith("ko") -> !hasHangul
            else -> true
        }
    }

    fun isLikelyForeignUntranslated(original: String, candidate: String, targetLanguage: String): Boolean {
        if (!needsTranslation(original, targetLanguage)) return false
        val orig = original.trim(); val cand = candidate.trim()
        if (cand.isEmpty() || !orig.equals(cand, true)) return false
        if (!targetLanguage.trim().lowercase().startsWith("zh")) return true
        val toks = latinWordRegex.findAll(orig).map { it.value }.toList()
        val nonAcro = toks.count { it.all { c -> c.isUpperCase() } && it.uppercase() !in technicalAcronyms }
        val hasAlpha = toks.any { (it.length >= 3 && it.any(Char::isLowerCase)) || (it.length >= 5 && it.uppercase() !in technicalAcronyms) || it.uppercase() in commonAllCapsUiWords } || nonAcro >= 2
        return hasAlpha || orig.any { it.code in 0x3040..0x30FF || it.code in 0xAC00..0xD7A3 || it.code in 0x0400..0x04FF || it.code in 0x0600..0x06FF }
    }

    fun hasOnlyUntranslatedOutput(blocks: List<ScreenTextBlock>, targetLanguage: String): Boolean =
        blocks.none { b -> b.translatedText.orEmpty().trim().let { it.isNotEmpty() && !it.equals(b.text.trim(), true) } } &&
            blocks.any { isLikelyForeignUntranslated(it.text, it.text, targetLanguage) }

    fun inferMyMemorySourceLang(text: String, targetLang: String): String = when {
        text.any { it.code in 0xAC00..0xD7A3 || it.code in 0x1100..0x11FF || it.code in 0x3130..0x318F } -> "ko"
        text.any { it.code in 0x3040..0x30FF || it.code in 0x31F0..0x31FF } -> "ja"
        text.any { it.code in 0x0400..0x04FF } -> "ru"
        text.any { it.code in 0x0600..0x06FF } -> "ar"
        text.any { it.code in 0x4E00..0x9FFF } -> if (targetLang.startsWith("zh", true) && latinWordRegex.containsMatchIn(text)) "en" else "zh-CN"
        text.any { it in "äöüÄÖÜß" } -> "de"
        text.any { it in "ñÑ¿¡" } -> "es"
        text.any { it in "éèêëàâçùûüôöïîÉÈÊËÀÂÇÙÛÜÔÖÏÎœŒ" } -> "fr"
        else -> "en"
    }

    fun pickReadableForeground(bgColorArgb: Int): Int {
        val lum = 0.299f * ((bgColorArgb ushr 16) and 0xFF) + 0.587f * ((bgColorArgb ushr 8) and 0xFF) + 0.114f * (bgColorArgb and 0xFF)
        return if (lum >= 145f) 0xFF181B20.toInt() else 0xFFFFFFFF.toInt()
    }

    private fun interArea(a: ExtractedSubImageCandidate, b: ExtractedSubImageCandidate): Long {
        val il = max(a.leftPx, b.leftPx); val it = max(a.topPx, b.topPx); val ir = min(a.rightPx, b.rightPx); val ib = min(a.bottomPx, b.bottomPx)
        return if (ir <= il || ib <= it) 0L else (ir - il).toLong() * (ib - it).toLong()
    }

    fun computeIoU(a: ExtractedSubImageCandidate, b: ExtractedSubImageCandidate): Float {
        val inter = interArea(a, b); if (inter <= 0L) return 0f
        val union = a.areaPx + b.areaPx - inter; return if (union <= 0L) 0f else inter.toFloat() / union.toFloat()
    }

    fun mergeAndDeduplicateSubImageCandidates(
        candidates: List<ExtractedSubImageCandidate>, iouThreshold: Float = 0.52f, minSizePx: Int = 64, maxResults: Int = 24
    ): List<ExtractedSubImageCandidate> {
        val sized = candidates.filter { it.widthPx >= minSizePx && it.heightPx >= minSizePx }
        val nonWrapper = sized.filterNot { w ->
            val uiLeaf = w.confidence >= 0.85f && !w.sourceTag.contains("容器") && !w.sourceTag.contains("网页") && w.sourceTag != "画面图像区域"
            val children = sized.filter { c -> c !== w && c.areaPx * 100L <= w.areaPx * 55L && c.areaPx * 200L >= w.areaPx && interArea(w, c) * 100L >= c.areaPx * 70L }
            if (children.size < 2) return@filterNot false
            val anyD = ArrayList<ExtractedSubImageCandidate>(); val hcD = ArrayList<ExtractedSubImageCandidate>(); val uiD = ArrayList<ExtractedSubImageCandidate>()
            fun overlaps(list: List<ExtractedSubImageCandidate>, c: ExtractedSubImageCandidate) = list.any { p -> computeIoU(p, c) >= 0.25f || interArea(p, c) * 100L >= min(p.areaPx, c.areaPx).coerceAtLeast(1L) * 45L }
            for (c in children.sortedByDescending { it.confidence }) {
                if (!overlaps(anyD, c)) anyD += c
                if (c.confidence >= 0.72f && !overlaps(hcD, c)) hcD += c
                if (c.confidence >= 0.72f && c.sourceTag != "画面图像区域" && !overlaps(uiD, c)) uiD += c
            }
            if (uiLeaf) uiD.size >= 2 || hcD.size >= 3 else hcD.size >= 2 || anyD.size >= 3
        }
        val kept = ArrayList<ExtractedSubImageCandidate>()
        for (c in nonWrapper.sortedWith(compareByDescending<ExtractedSubImageCandidate> { it.confidence }.thenByDescending { it.areaPx })) {
            val dup = kept.any { k ->
                if (computeIoU(k, c) >= iouThreshold) return@any true
                val frac = interArea(k, c).toFloat() / min(k.areaPx, c.areaPx).coerceAtLeast(1L).toFloat()
                (c.areaPx <= k.areaPx && frac >= 0.62f && !(k.areaPx >= c.areaPx * 6L && c.confidence >= 0.75f)) ||
                    (c.confidence < k.confidence - 0.12f && c.confidence < 0.78f && frac >= 0.68f)
            }
            if (!dup) kept += c
        }
        return kept.sortedWith(compareBy({ it.topPx / 48 }, { it.leftPx })).take(maxResults)
    }

    fun applySubImageHandleDrag(left: Int, top: Int, right: Int, bottom: Int, handle: Int, dxPx: Int, dyPx: Int, maxW: Int, maxH: Int, minSizePx: Int = 48): IntArray {
        val mw = min(minSizePx, maxW.coerceAtLeast(2)); val mh = min(minSizePx, maxH.coerceAtLeast(2))
        var l = left.coerceIn(0, maxW - mw); var t = top.coerceIn(0, maxH - mh); var r = right.coerceIn(l + mw, maxW); var b = bottom.coerceIn(t + mh, maxH)
        when (handle) { 1, 7, 8 -> l = (left + dxPx).coerceIn(0, r - mw) }
        when (handle) { 3, 4, 5 -> r = (right + dxPx).coerceIn(l + mw, maxW) }
        when (handle) { 1, 2, 3 -> t = (top + dyPx).coerceIn(0, b - mh) }
        when (handle) { 5, 6, 7 -> b = (bottom + dyPx).coerceIn(t + mh, maxH) }
        return intArrayOf(l, t, r, b)
    }

    const val BILIBILI_INDEX_BASE_URL = "https://index-translate.bilibili.com/v1"
    const val BILIBILI_INDEX_MODEL = "Index-Translate-35B-A3B"
    const val DEEPSEEK_BASE_URL = "https://api.deepseek.com/v1"
    const val DEEPSEEK_CHAT_MODEL = "deepseek-chat"
    private val THINK_BLOCK_REGEX = Regex("""<think>[\s\S]*?(?:</think>|$)""", RegexOption.IGNORE_CASE)

    fun isBilibiliIndexEndpoint(rawUrl: String) = rawUrl.contains("index-translate.bilibili.com", true)
    fun stripThinkingTags(raw: String) = THINK_BLOCK_REGEX.replace(raw, "").trim()

    fun stripJsonEnvelope(raw: String): String {
        val t = stripThinkingTags(raw)
        val f0 = t.indexOf("```")
        if (f0 >= 0) { val b0 = t.indexOf('\n', f0); val f1 = t.lastIndexOf("```"); if (b0 > f0 && f1 > b0) return t.substring(b0 + 1, f1).trim() }
        val start = listOf(t.indexOf('{'), t.indexOf('[')).filter { it >= 0 }.minOrNull() ?: return t
        val end = t.lastIndexOf(if (t[start] == '{') '}' else ']')
        return if (end > start) t.substring(start, end + 1) else t
    }

    fun parseVisionOcrJson(rawJson: String, imageWidth: Int, imageHeight: Int): List<ScreenTextBlock> {
        val root = runCatching { lenientJson.parseToJsonElement(stripJsonEnvelope(rawJson)) }.getOrNull() ?: return emptyList()
        val arr = (root as? JsonArray) ?: (root as? JsonObject)?.let { (it["blocks"] ?: it["items"] ?: it["data"]) as? JsonArray } ?: return emptyList()
        val w = imageWidth.coerceAtLeast(1); val h = imageHeight.coerceAtLeast(1)
        return arr.mapIndexedNotNull { i, el ->
            val obj = el as? JsonObject ?: return@mapIndexedNotNull null
            val txt = (obj["text"] ?: obj["content"])?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifBlank { return@mapIndexedNotNull null }
            parseFlexRect(obj, w, h)?.let { ScreenTextBlock("vision_$i", txt, it[0], it[1], it[2], it[3], sourceTag = "Vision-LLM") }
        }
    }

    private fun parseFlexRect(obj: JsonObject, w: Int, h: Int): IntArray? {
        (obj["box_2d"] as? JsonArray ?: obj["bbox"] as? JsonArray)?.takeIf { it.size >= 4 }?.let { b ->
            val v = FloatArray(4) { i -> b[i].jsonPrimitive.floatOrNull ?: return null }; val mx = v.max()
            return when {
                mx <= 1.05f -> clampRect((v[1] * w).toInt(), (v[0] * h).toInt(), (v[3] * w).toInt(), (v[2] * h).toInt(), w, h)
                mx <= 1005f -> clampRect((v[1] / 1000f * w).toInt(), (v[0] / 1000f * h).toInt(), (v[3] / 1000f * w).toInt(), (v[2] / 1000f * h).toInt(), w, h)
                else -> clampRect(v[0].toInt(), v[1].toInt(), v[2].toInt(), v[3].toInt(), w, h)
            }
        }
        val xn = obj["x"]?.jsonPrimitive?.floatOrNull; val yn = obj["y"]?.jsonPrimitive?.floatOrNull
        val wn = (obj["w"] ?: obj["width"])?.jsonPrimitive?.floatOrNull; val hn = (obj["h"] ?: obj["height"])?.jsonPrimitive?.floatOrNull
        if (xn != null && yn != null && wn != null && hn != null) {
            return if (maxOf(xn, yn, wn, hn) <= 1.05f) clampRect((xn * w).toInt(), (yn * h).toInt(), ((xn + wn) * w).toInt(), ((yn + hn) * h).toInt(), w, h)
            else clampRect(xn.toInt(), yn.toInt(), (xn + wn).toInt(), (yn + hn).toInt(), w, h)
        }
        val l = obj["left"]?.jsonPrimitive?.floatOrNull; val t = obj["top"]?.jsonPrimitive?.floatOrNull
        val r = obj["right"]?.jsonPrimitive?.floatOrNull; val bt = obj["bottom"]?.jsonPrimitive?.floatOrNull
        if (l != null && t != null && r != null && bt != null) {
            return if (maxOf(l, t, r, bt) <= 1.05f) clampRect((l * w).toInt(), (t * h).toInt(), (r * w).toInt(), (bt * h).toInt(), w, h)
            else clampRect(l.toInt(), t.toInt(), r.toInt(), bt.toInt(), w, h)
        }
        return null
    }

    private fun clampRect(l: Int, t: Int, r: Int, b: Int, w: Int, h: Int): IntArray? {
        val cl = l.coerceIn(0, w - 1); val ct = t.coerceIn(0, h - 1); val cr = r.coerceIn(cl + 1, w); val cb = b.coerceIn(ct + 1, h)
        return if (cr - cl < 4 || cb - ct < 4) null else intArrayOf(cl, ct, cr, cb)
    }

    fun parseCustomHttpOcrJson(rawJson: String, imageWidth: Int, imageHeight: Int): List<ScreenTextBlock> {
        val cleaned = stripJsonEnvelope(rawJson); val root = runCatching { lenientJson.parseToJsonElement(cleaned) }.getOrNull() ?: return emptyList()
        if (root is JsonArray) return parseVisionOcrJson(cleaned, imageWidth, imageHeight)
        val data = ((root as? JsonObject)?.let { it["data"] ?: it["blocks"] ?: it["results"] } as? JsonArray) ?: return emptyList()
        val w = imageWidth.coerceAtLeast(1); val h = imageHeight.coerceAtLeast(1)
        return data.mapIndexedNotNull { idx, el ->
            val item = el as? JsonObject ?: return@mapIndexedNotNull null
            val txt = (item["text"] ?: item["content"] ?: item["words"])?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifBlank { return@mapIndexedNotNull null }
            val poly = item["box"] as? JsonArray
            val rect = if (poly != null && poly.size >= 4 && poly[0] is JsonArray) {
                val xs = poly.mapNotNull { (it as? JsonArray)?.getOrNull(0)?.jsonPrimitive?.floatOrNull }
                val ys = poly.mapNotNull { (it as? JsonArray)?.getOrNull(1)?.jsonPrimitive?.floatOrNull }
                if (xs.isEmpty() || ys.isEmpty()) null else clampRect(xs.min().toInt(), ys.min().toInt(), xs.max().toInt(), ys.max().toInt(), w, h)
            } else parseFlexRect(item, w, h)
            rect?.let { ScreenTextBlock("http_$idx", txt, it[0], it[1], it[2], it[3], sourceTag = "HTTP-OCR") }
        }
    }

    fun parseBatchTranslationJson(rawJson: String, expectedCount: Int): Map<Int, String> {
        val root = runCatching { lenientJson.parseToJsonElement(stripJsonEnvelope(rawJson)) }.getOrNull() ?: return emptyMap()
        val out = linkedMapOf<Int, String>()
        when (root) {
            is JsonArray -> root.forEachIndexed { idx, el ->
                when (el) {
                    is JsonPrimitive -> el.contentOrNull?.takeIf { it.isNotBlank() }?.let { out[idx] = it }
                    is JsonObject -> {
                        val id = el["id"]?.jsonPrimitive?.intOrNull ?: idx
                        (el["translation"] ?: el["text"] ?: el["target"])?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { out[id] = it }
                    }
                    else -> Unit
                }
            }
            is JsonObject -> {
                (root["translations"] ?: root["items"] ?: root["data"])?.let { if (it is JsonArray) return parseBatchTranslationJson(it.toString(), expectedCount) }
                for ((k, v) in root) k.trim().toIntOrNull()?.let { id -> (v as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { out[id] = it } }
            }
            else -> Unit
        }
        return out.filterKeys { it in 0 until expectedCount }
    }

    fun parseLexiconContent(rawContent: String): List<LexiconEntry> {
        val t = rawContent.trim(); if (t.isEmpty()) return emptyList()
        if (t.startsWith("{") || t.startsWith("[")) {
            val parsed = runCatching { lenientJson.parseToJsonElement(t) }.getOrNull()
            if (parsed is JsonObject) return parsed.entries.mapNotNull { (k, v) ->
                val src = k.trim(); val tgt = (v as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
                if (src.isNotEmpty() && tgt.isNotEmpty()) LexiconEntry(src, tgt) else null
            }
            if (parsed is JsonArray) return parsed.mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                val src = (obj["source"] ?: obj["en"] ?: obj["word"] ?: obj["src"])?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val tgt = (obj["target"] ?: obj["zh"] ?: obj["translation"] ?: obj["tgt"])?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (src.isNotEmpty() && tgt.isNotEmpty()) LexiconEntry(src, tgt) else null
            }
        }
        return t.lineSequence().mapNotNull { line ->
            val cl = line.trim(); if (cl.isEmpty() || cl.startsWith("#")) return@mapNotNull null
            val sep = when { cl.contains("|||") -> "|||"; cl.contains('\t') -> "\t"; else -> return@mapNotNull null }
            val idx = cl.indexOf(sep); val src = cl.substring(0, idx).trim(); val tgt = cl.substring(idx + sep.length).trim()
            if (src.isNotEmpty() && tgt.isNotEmpty()) LexiconEntry(src, tgt) else null
        }.toList()
    }

    fun translateWithLexicon(texts: List<String>, lexicon: Map<String, String>): List<String> {
        if (lexicon.isEmpty()) return texts
        val lower = lexicon.entries.associate { (k, v) -> k.trim().lowercase() to v }
        val punct = setOf('.', ',', '!', '?', ':', ';', '"', '\'', '(', ')')
        return texts.map { raw ->
            val trimmed = raw.trim(); lower[trimmed.lowercase()]?.let { return@map it }
            var changed = false
            val out = trimmed.split(Regex("\\s+")).map { tok ->
                val core = tok.trim { it in punct }; val hit = lower[core.lowercase()]
                if (hit != null) { changed = true; if (core.length == tok.length) hit else tok.replace(core, hit) } else tok
            }
            if (changed) out.joinToString(" ") else raw
        }
    }

    enum class CanvasSingleFingerDragAction { TEXT_SELECTION, SUB_IMAGE_HANDLE, UNDERLYING_SCROLL, VIEWPORT_PAN }

    fun classifySingleFingerDrag(
        modeKey: String, isCroppedRegion: Boolean, zoomScale: Float, hasActiveTextTarget: Boolean,
        hasActiveSubHandle: Boolean, canScrollUnderlying: Boolean, canvasMoveMode: Boolean = false
    ): CanvasSingleFingerDragAction = when {
        canvasMoveMode -> CanvasSingleFingerDragAction.VIEWPORT_PAN
        modeKey == "SUB_IMAGES" && hasActiveSubHandle -> CanvasSingleFingerDragAction.SUB_IMAGE_HANDLE
        modeKey == "TEXT_SELECT" && hasActiveTextTarget -> CanvasSingleFingerDragAction.TEXT_SELECTION
        zoomScale > 1.05f || isCroppedRegion -> CanvasSingleFingerDragAction.VIEWPORT_PAN
        canScrollUnderlying -> CanvasSingleFingerDragAction.UNDERLYING_SCROLL
        else -> CanvasSingleFingerDragAction.VIEWPORT_PAN
    }

    fun formatScrollHintLabel(dx: Float, dy: Float, triggerThresholdPx: Float): String = when {
        hypot(dx.toDouble(), dy.toDouble()).toFloat() < triggerThresholdPx -> "继续滑动以滚动原页面"
        abs(dy) >= abs(dx) -> if (dy < 0f) "松手向下翻页 ↑" else "松手向上翻页 ↓"
        dx < 0f -> "松手向右翻页 ←"
        else -> "松手向左翻页 →"
    }
}

internal object OcrExtensionGeometry {
    data class ScaledRect(val left: Int, val top: Int, val right: Int, val bottom: Int)

    fun computeScaledDimensions(width: Int, height: Int, maxDimension: Int = 3200, maxPixels: Long = 8_000_000L): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 1 to 1
        val cap = maxDimension.coerceAtLeast(1)
        val scale = min(min(1.0, cap.toDouble() / max(width, height)), (width.toDouble() * height).let { if (it > maxPixels.coerceAtLeast(1L)) sqrt(maxPixels / it) else 1.0 })
        return if (scale >= 1.0) width to height else floor(width * scale).toInt().coerceIn(1, cap) to floor(height * scale).toInt().coerceIn(1, cap)
    }

    fun rescaleRect(left: Int, top: Int, right: Int, bottom: Int, sourceWidth: Int, sourceHeight: Int, targetWidth: Int, targetHeight: Int): ScaledRect? {
        if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0 || right <= left || bottom <= top) return null
        val cl = left.coerceIn(0, sourceWidth); val ct = top.coerceIn(0, sourceHeight); val cr = right.coerceIn(0, sourceWidth); val cb = bottom.coerceIn(0, sourceHeight)
        if (cr <= cl || cb <= ct) return null
        val rl = round(cl.toDouble() * targetWidth / sourceWidth).toInt().coerceIn(0, targetWidth)
        val rt = round(ct.toDouble() * targetHeight / sourceHeight).toInt().coerceIn(0, targetHeight)
        val rr = round(cr.toDouble() * targetWidth / sourceWidth).toInt().coerceIn(0, targetWidth)
        val rb = round(cb.toDouble() * targetHeight / sourceHeight).toInt().coerceIn(0, targetHeight)
        return if (rr <= rl || rb <= rt) null else ScaledRect(rl, rt, rr, rb)
    }
}
