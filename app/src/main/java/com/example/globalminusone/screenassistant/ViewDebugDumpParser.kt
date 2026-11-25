package com.example.globalminusone.screenassistant

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class ViewDebugNode(
    val descriptor: String,
    val className: String,
    val hashHex: String,
    val viewId: String,
    val leftPx: Int,
    val topPx: Int,
    val rightPx: Int,
    val bottomPx: Int,
    val visible: Boolean,
    val depth: Int,
    val childCount: Int
) {
    val widthPx: Int get() = (rightPx - leftPx).coerceAtLeast(0)
    val heightPx: Int get() = (bottomPx - topPx).coerceAtLeast(0)
    val areaPx: Long get() = widthPx.toLong() * heightPx.toLong()
}

/**
 * Pure-Kotlin parser for AOSP `ViewDebug.REMOTE_COMMAND_DUMP` streams and
 * image-node selector for `ViewDebug.REMOTE_COMMAND_CAPTURE`.
 */
internal object ViewDebugDumpParser {
    private val DESCRIPTOR_REGEX = Regex("^[A-Za-z0-9_.\$]{1,180}@[0-9a-fA-F]{1,8}$")

    fun isValidViewDescriptor(raw: String): Boolean = DESCRIPTOR_REGEX.matches(raw.trim())

    private data class AncestorState(
        val indent: Int,
        val nodeIndex: Int,
        val screenX: Int,
        val screenY: Int,
        val scrollX: Int,
        val scrollY: Int,
        val visible: Boolean
    )

    fun parseDump(
        rawText: String,
        windowLeft: Int,
        windowTop: Int,
        screenW: Int,
        screenH: Int
    ): List<ViewDebugNode> {
        if (rawText.isBlank()) return emptyList()
        val nodes = ArrayList<ViewDebugNode>(96)
        val childCounts = ArrayList<Int>(96)
        val stack = ArrayList<AncestorState>(24)
        var pos = 0
        val n = rawText.length

        while (pos < n) {
            var indent = 0
            while (pos < n && rawText[pos] == ' ') {
                indent++
                pos++
            }
            if (pos >= n) break
            if (rawText[pos] == '\r' || rawText[pos] == '\n') {
                pos++
                continue
            }

            // Read descriptor token up to whitespace or EOL
            val tokenStart = pos
            while (pos < n && rawText[pos] != ' ' && rawText[pos] != '\n' && rawText[pos] != '\r') {
                pos++
            }
            val descriptor = rawText.substring(tokenStart, pos)
            if (descriptor == "DONE.") break

            var viewId = ""
            var selfVisible = true
            var alpha = 1f
            var locX: Int? = null
            var locY: Int? = null
            var mLeft: Int? = null
            var mTop: Int? = null
            var mRight: Int? = null
            var mBottom: Int? = null
            var widthVal: Int? = null
            var heightVal: Int? = null
            var scrollX = 0
            var scrollY = 0
            var transX = 0f
            var transY = 0f

            // Parse length-prefixed `<key>=<len>,<value>` fields without splitting values on spaces.
            while (pos < n) {
                val ch = rawText[pos]
                if (ch == '\r' || ch == '\n') {
                    pos++
                    if (ch == '\r' && pos < n && rawText[pos] == '\n') pos++
                    break
                }
                if (ch == ' ') {
                    pos++
                    continue
                }
                val eqIdx = rawText.indexOf('=', pos)
                val lineEnd = findLineEnd(rawText, pos)
                if (eqIdx < 0 || eqIdx >= lineEnd) {
                    pos = lineEnd
                    continue
                }
                val key = rawText.substring(pos, eqIdx)
                pos = eqIdx + 1
                var len = 0
                var hasDigits = false
                while (pos < n && rawText[pos] in '0'..'9') {
                    len = (len * 10) + (rawText[pos] - '0')
                    hasDigits = true
                    pos++
                }
                if (!hasDigits || pos >= n || rawText[pos] != ',') {
                    pos = lineEnd
                    continue
                }
                pos++ // skip ','
                val valEnd = min(n, pos + len.coerceAtLeast(0))
                val value = rawText.substring(pos, valEnd)
                pos = valEnd

                when (key) {
                    "mID", "id", "layout:mID" -> {
                        if (value != "NO_ID" && value != "null") viewId = value
                    }
                    "getVisibility()", "misc:getVisibility()", "mVisibility" -> {
                        selfVisible = value.equals("VISIBLE", ignoreCase = true) || value == "0"
                    }
                    "drawing:getAlpha()", "getAlpha()" -> {
                        alpha = value.toFloatOrNull() ?: 1f
                    }
                    "layout:getLocationOnScreen_x()", "getLocationOnScreen_x()" -> {
                        locX = value.toIntOrNull()
                    }
                    "layout:getLocationOnScreen_y()", "getLocationOnScreen_y()" -> {
                        locY = value.toIntOrNull()
                    }
                    "layout:mLeft", "mLeft" -> mLeft = value.toIntOrNull()
                    "layout:mTop", "mTop" -> mTop = value.toIntOrNull()
                    "layout:mRight", "mRight" -> mRight = value.toIntOrNull()
                    "layout:mBottom", "mBottom" -> mBottom = value.toIntOrNull()
                    "layout:getWidth()", "getWidth()" -> widthVal = value.toIntOrNull()
                    "layout:getHeight()", "getHeight()" -> heightVal = value.toIntOrNull()
                    "scrolling:mScrollX", "layout:mScrollX", "mScrollX" -> {
                        scrollX = value.toIntOrNull() ?: 0
                    }
                    "scrolling:mScrollY", "layout:mScrollY", "mScrollY" -> {
                        scrollY = value.toIntOrNull() ?: 0
                    }
                    "drawing:getTranslationX()", "getTranslationX()", "translationX" -> {
                        transX = value.toFloatOrNull() ?: 0f
                    }
                    "drawing:getTranslationY()", "getTranslationY()", "translationY" -> {
                        transY = value.toFloatOrNull() ?: 0f
                    }
                }
            }

            if (!isValidViewDescriptor(descriptor)) continue
            while (stack.isNotEmpty() && stack.last().indent >= indent) {
                stack.removeAt(stack.lastIndex)
            }
            val parent = stack.lastOrNull()
            if (parent != null && parent.nodeIndex in childCounts.indices) {
                childCounts[parent.nodeIndex] = childCounts[parent.nodeIndex] + 1
            }

            val atIdx = descriptor.indexOf('@')
            val clsName = descriptor.substring(0, atIdx)
            val hashHex = descriptor.substring(atIdx + 1)

            val w = (widthVal ?: if (mLeft != null && mRight != null) mRight - mLeft else 0).coerceAtLeast(0)
            val h = (heightVal ?: if (mTop != null && mBottom != null) mBottom - mTop else 0).coerceAtLeast(0)

            val sx = locX ?: if (parent != null) {
                parent.screenX + (mLeft ?: 0) - parent.scrollX + transX.roundToInt()
            } else {
                windowLeft + (mLeft ?: 0) + transX.roundToInt()
            }
            val sy = locY ?: if (parent != null) {
                parent.screenY + (mTop ?: 0) - parent.scrollY + transY.roundToInt()
            } else {
                windowTop + (mTop ?: 0) + transY.roundToInt()
            }

            val effectiveVisible = (parent?.visible ?: true) && selfVisible && alpha > 0.01f && w > 0 && h > 0
            val l = sx.coerceAtLeast(0)
            val t = sy.coerceAtLeast(0)
            val r = if (screenW > 0) (sx + w).coerceAtMost(screenW) else (sx + w)
            val b = if (screenH > 0) (sy + h).coerceAtMost(screenH) else (sy + h)

            val idx = nodes.size
            nodes += ViewDebugNode(
                descriptor = descriptor,
                className = clsName,
                hashHex = hashHex,
                viewId = viewId,
                leftPx = l,
                topPx = t,
                rightPx = r,
                bottomPx = b,
                visible = effectiveVisible && r > l && b > t,
                depth = stack.size,
                childCount = 0
            )
            childCounts += 0
            stack += AncestorState(indent, idx, sx, sy, scrollX, scrollY, effectiveVisible)
        }

        return nodes.mapIndexed { idx, node -> node.copy(childCount = childCounts[idx]) }
    }

    private fun findLineEnd(text: String, start: Int): Int {
        var i = start
        while (i < text.length && text[i] != '\n' && text[i] != '\r') i++
        return i
    }

    /**
     * Selects visible native image-bearing Views whose bounds match visible screen photo slots
     * or explicitly expose ImageView / Drawee / PhotoView / BiliImage classes.
     */
    fun selectCaptureTargets(
        nodes: List<ViewDebugNode>,
        verifiedLeaves: List<ExtractedSubImageCandidate>,
        maxTargets: Int = 12
    ): List<Pair<ViewDebugNode, ExtractedSubImageCandidate?>> {
        if (nodes.isEmpty()) return emptyList()
        data class ScoredTarget(
            val node: ViewDebugNode,
            val leaf: ExtractedSubImageCandidate?,
            val score: Float
        )

        val candidates = ArrayList<ScoredTarget>()
        for (node in nodes) {
            if (!node.visible || node.widthPx < 48 || node.heightPx < 48) continue
            val lowerCls = node.className.lowercase()
            if (lowerCls.contains("textview") ||
                lowerCls.contains("edittext") ||
                lowerCls.contains("webview") ||
                lowerCls.contains("recyclerview") ||
                lowerCls.contains("scrollview") ||
                lowerCls.contains("viewpager")
            ) continue

            val explicitImgCls = lowerCls.contains("imageview") ||
                lowerCls.contains("photoview") ||
                lowerCls.contains("drawee") ||
                lowerCls.contains("asyncimage") ||
                lowerCls.contains("biliimage") ||
                lowerCls.contains("imagefilterview")
            val lowerId = node.viewId.lowercase()
            val hasMediaId = lowerId.contains("cover") ||
                lowerId.contains("thumb") ||
                lowerId.contains("poster") ||
                lowerId.contains("image") ||
                lowerId.contains("img") ||
                lowerId.contains("pic") ||
                lowerId.contains("photo") ||
                lowerId.contains("avatar") ||
                lowerId.contains("banner")
            val isWrapperId = lowerId.contains("container") ||
                lowerId.contains("layout") ||
                lowerId.contains("wrapper") ||
                lowerId.contains("root") ||
                lowerId.contains("holder") ||
                lowerId.contains("item")

            val nodeRect = ExtractedSubImageCandidate(
                id = node.descriptor,
                leftPx = node.leftPx,
                topPx = node.topPx,
                rightPx = node.rightPx,
                bottomPx = node.bottomPx,
                sourceTag = "ViewDebug",
                confidence = 0.9f
            )
            var matchedLeaf: ExtractedSubImageCandidate? = null
            var bestIoU = 0f
            for (leaf in verifiedLeaves) {
                val iou = ScreenAssistantPolicy.computeIoU(nodeRect, leaf)
                if (iou > bestIoU) {
                    bestIoU = iou
                    matchedLeaf = leaf
                }
            }

            val isEligible = explicitImgCls ||
                (hasMediaId && !isWrapperId) ||
                (bestIoU >= 0.58f && node.childCount <= 1)
            if (!isEligible) continue

            val score = (if (explicitImgCls) 2.0f else if (hasMediaId) 1.2f else 0.7f) + bestIoU
            candidates += ScoredTarget(node, if (bestIoU >= 0.45f) matchedLeaf else null, score)
        }

        candidates.sortWith(compareByDescending<ScoredTarget> { it.score }.thenByDescending { it.node.areaPx })
        val chosen = ArrayList<Pair<ViewDebugNode, ExtractedSubImageCandidate?>>(maxTargets)
        for (item in candidates) {
            if (chosen.size >= maxTargets) break
            val itemRect = ExtractedSubImageCandidate(
                id = item.node.descriptor,
                leftPx = item.node.leftPx,
                topPx = item.node.topPx,
                rightPx = item.node.rightPx,
                bottomPx = item.node.bottomPx,
                sourceTag = "ViewDebug",
                confidence = 0.9f
            )
            val dup = chosen.any { (prev, _) ->
                val prevRect = ExtractedSubImageCandidate(
                    id = prev.descriptor,
                    leftPx = prev.leftPx,
                    topPx = prev.topPx,
                    rightPx = prev.rightPx,
                    bottomPx = prev.bottomPx,
                    sourceTag = "ViewDebug",
                    confidence = 0.9f
                )
                ScreenAssistantPolicy.computeIoU(prevRect, itemRect) >= 0.65f
            }
            if (!dup) chosen += item.node to item.leaf
        }
        return chosen
    }
}
