package com.example.globalminusone.data.policy

import com.example.globalminusone.data.TriggerSide

data class DetachedCandidatePanelPlacement(
    val x: Int,
    val y: Int,
    val widthPx: Int,
    val maxHeightPx: Int,
    val placedAbove: Boolean
)

data class SideFlushPodCorners(
    val leftDp: Int,
    val rightDp: Int
)

/**
 * Pure geometry helpers for the detached multi-row candidate grid overlay:
 * computes comfortable grid columns based on panel width and positions the
 * overlay independently of the edge-docked keyboard pod so candidates never
 * stay cramped inside a single horizontal row.
 */
fun computeCandidateGridColumns(
    panelWidthDp: Float,
    showLabels: Boolean
): Int = if (showLabels) {
    when {
        panelWidthDp >= 340f -> 5
        panelWidthDp >= 250f -> 4
        panelWidthDp >= 190f -> 3
        else -> 2
    }
} else {
    when {
        panelWidthDp >= 330f -> 6
        panelWidthDp >= 275f -> 5
        panelWidthDp >= 225f -> 4
        else -> 3
    }
}

fun resolveDetachedCandidateWidthDp(hostWidthDp: Float): Float {
    val safeHostW = hostWidthDp.coerceAtLeast(160f)
    return (safeHostW * 0.72f).coerceIn(248f, 296f).coerceAtMost(safeHostW)
}

@Suppress("LongParameterList")
fun computeDetachedCandidatePlacement(
    hostWidthPx: Int,
    hostHeightPx: Int,
    anchorTopPx: Int,
    anchorBottomPx: Int,
    safeTopPx: Int,
    safeBottomPx: Int,
    horizontalMarginPx: Int,
    gapPx: Int,
    maxPanelHeightPx: Int,
    minPreferredHeightPx: Int,
    measuredPanelHeightPx: Int = -1,
    activeSide: TriggerSide = TriggerSide.RIGHT,
    maxPanelWidthPx: Int = 0,
    measuredPanelWidthPx: Int = -1
): DetachedCandidatePanelPlacement {
    val safeHostW = hostWidthPx.coerceAtLeast(160)
    val margin = horizontalMarginPx.coerceAtLeast(0)
    val minWidthFloor = minOf(safeHostW, 160)
    val maxAvailW = if (maxPanelWidthPx > 0) {
        (safeHostW - margin).coerceAtLeast(minWidthFloor)
    } else {
        (safeHostW - margin * 2).coerceAtLeast(minWidthFloor)
    }
    val widthPx = if (maxPanelWidthPx > 0) {
        minOf(maxAvailW, maxPanelWidthPx).coerceAtLeast(minWidthFloor)
    } else {
        maxAvailW
    }
    val effectiveW = if (measuredPanelWidthPx > 0) {
        measuredPanelWidthPx.coerceAtMost(widthPx)
    } else {
        widthPx
    }
    val x = if (maxPanelWidthPx > 0) {
        if (activeSide == TriggerSide.LEFT) margin else (safeHostW - margin - effectiveW).coerceAtLeast(0)
    } else {
        ((safeHostW - effectiveW) / 2).coerceAtLeast(0)
    }

    val minY = safeTopPx.coerceAtLeast(0)
    val maxBottom = (hostHeightPx - safeBottomPx).coerceAtLeast(minY)
    val spaceAbove = (anchorTopPx - gapPx - minY).coerceAtLeast(0)
    val spaceBelow = (maxBottom - anchorBottomPx - gapPx).coerceAtLeast(0)
    val placedAbove = spaceAbove >= minPreferredHeightPx || spaceAbove >= spaceBelow
    val availHeight = if (placedAbove) spaceAbove else spaceBelow
    val clampedMaxH = minOf(availHeight, maxPanelHeightPx.coerceAtLeast(0)).coerceAtLeast(0)
    val effectiveH = if (measuredPanelHeightPx > 0) {
        measuredPanelHeightPx.coerceAtMost(clampedMaxH)
    } else {
        clampedMaxH
    }
    val y = if (placedAbove) {
        (anchorTopPx - gapPx - effectiveH).coerceAtLeast(minY)
    } else {
        val maxY = (maxBottom - effectiveH).coerceAtLeast(minY)
        (anchorBottomPx + gapPx).coerceAtMost(maxY)
    }
    return DetachedCandidatePanelPlacement(
        x = x,
        y = y,
        widthPx = widthPx,
        maxHeightPx = clampedMaxH,
        placedAbove = placedAbove
    )
}

/** Resolves flush-docked vs rounded corner edges for a side-anchored pod. */
fun resolveSideFlushPodCorners(activeSide: TriggerSide, cornerRadiusDp: Int): SideFlushPodCorners {
    val safeRadius = cornerRadiusDp.coerceIn(0, 36)
    return if (activeSide == TriggerSide.RIGHT) {
        SideFlushPodCorners(leftDp = safeRadius, rightDp = 0)
    } else {
        SideFlushPodCorners(leftDp = 0, rightDp = safeRadius)
    }
}

/** Finds first literal case-insensitive query range inside [label] for accent highlighting. */
fun findDirectQueryHighlightRange(label: String, query: String): IntRange? {
    val q = query.trim()
    if (q.isEmpty() || label.isEmpty()) return null
    val idx = label.indexOf(q, ignoreCase = true)
    return if (idx >= 0) idx until (idx + q.length) else null
}

/**
 * Ranks launcher packages by blended frecency (recency rank + capped launch count) so
 * frequently launched and recently opened apps stay in the closest thumb slots.
 */
fun computeFrecencyRankOrder(
    counts: Map<String, Int>,
    recentOrder: List<String>,
    maxItems: Int = 36
): List<String> {
    val safeMax = maxItems.coerceAtLeast(1)
    val recencyScores = HashMap<String, Int>(recentOrder.size * 2 + 1)
    recentOrder.forEachIndexed { idx, pkg ->
        val clean = pkg.trim()
        if (clean.isNotEmpty() && clean !in recencyScores) {
            recencyScores[clean] = (recentOrder.size - idx) * 2
        }
    }
    val allPkgs = LinkedHashSet<String>(recencyScores.size + counts.size)
    allPkgs.addAll(recencyScores.keys)
    counts.keys.forEach { raw -> raw.trim().takeIf { it.isNotEmpty() }?.let(allPkgs::add) }
    return allPkgs.sortedWith(
        compareByDescending<String> { pkg ->
            val countBoost = (counts[pkg] ?: 0).coerceIn(0, 20) * 3
            (recencyScores[pkg] ?: 0) + countBoost
        }.thenBy { pkg ->
            val idx = recentOrder.indexOf(pkg)
            if (idx >= 0) idx else Int.MAX_VALUE
        }
    ).take(safeMax)
}
