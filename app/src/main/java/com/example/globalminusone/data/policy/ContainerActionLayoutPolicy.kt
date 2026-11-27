package com.example.globalminusone.data.policy

import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ContainerLayoutType
import com.example.globalminusone.data.MAX_GRID_GAP_DP
import com.example.globalminusone.data.MIN_GRID_GAP_DP
import com.example.globalminusone.data.QuickActionItem

const val LEGACY_ACTION_VIEWPORT_MAX_DP = 352f

/** Folder-local display geometry in dp; legacy nulls follow their original grid slot. */
data class ContainerActionGeometry(val x: Float, val y: Float, val width: Float, val height: Float)

data class ContainerActionGridPlacement(val action: QuickActionItem, val col: Int, val row: Int)

fun resolveContainerActionGridPlacements(actions: List<QuickActionItem>, columns: Int): List<ContainerActionGridPlacement> {
    val cols = columns.coerceIn(1, 8)
    if (actions.none { it.gridCol >= 0 && it.gridRow >= 0 }) {
        return actions.mapIndexed { index, action -> ContainerActionGridPlacement(action, index % cols, index / cols) }
    }
    val occupied = mutableListOf<BooleanArray>()
    fun ensure(rows: Int) { while (occupied.size < rows) occupied.add(BooleanArray(cols)) }
    fun free(c: Int, r: Int): Boolean { ensure(r + 1); return !occupied[r][c] }
    fun mark(c: Int, r: Int) { ensure(r + 1); occupied[r][c] = true }
    val placed = arrayOfNulls<Pair<Int, Int>>(actions.size)
    actions.forEachIndexed { idx, action ->
        if (action.gridCol >= 0 && action.gridRow >= 0) {
            val prefC = action.gridCol.coerceIn(0, cols - 1)
            val prefR = action.gridRow.coerceAtLeast(0)
            var pC = prefC; var pR = prefR
            if (!free(prefC, prefR)) {
                var r = prefR
                loop@ while (true) {
                    for (c in prefC until cols) if (free(c, r)) { pC = c; pR = r; break@loop }
                    if (r == prefR) for (c in 0 until prefC) if (free(c, r)) { pC = c; pR = r; break@loop }
                    r++
                }
            }
            mark(pC, pR); placed[idx] = pC to pR
        }
    }
    actions.forEachIndexed { idx, _ ->
        if (placed[idx] == null) {
            var r = 0; var pC = 0; var pR = 0
            loop@ while (true) {
                for (c in 0 until cols) if (free(c, r)) { pC = c; pR = r; break@loop }
                r++
            }
            mark(pC, pR); placed[idx] = pC to pR
        }
    }
    return actions.mapIndexed { idx, action ->
        val (c, r) = placed[idx] ?: (idx % cols to idx / cols)
        ContainerActionGridPlacement(action, c, r)
    }
}

/** Places or swaps a legacy QuickActionItem at ([targetCol], [targetRow]) in 2D without collapsing holes. */
fun commitContainerActionGridPlacement(
    actions: List<QuickActionItem>,
    movedId: String,
    columns: Int,
    targetCol: Int,
    targetRow: Int
): List<QuickActionItem> {
    val cols = columns.coerceIn(1, 8)
    val current = resolveContainerActionGridPlacements(actions, cols)
    val moving = current.firstOrNull { it.action.id == movedId } ?: return actions
    val destCol = targetCol.coerceIn(0, cols - 1)
    val destRow = targetRow.coerceAtLeast(0)
    if (destCol == moving.col && destRow == moving.row && moving.action.gridCol == destCol && moving.action.gridRow == destRow) {
        return actions
    }
    val victim = current.firstOrNull { it.action.id != movedId && it.col == destCol && it.row == destRow }
    val pinned = current.map { slot ->
        when {
            slot.action.id == movedId -> slot.action.copy(gridCol = destCol, gridRow = destRow)
            victim != null && slot.action.id == victim.action.id -> slot.action.copy(gridCol = moving.col, gridRow = moving.row)
            else -> slot.action.copy(gridCol = slot.col, gridRow = slot.row)
        }
    }
    val prioritized = pinned.filter { it.id == movedId } + pinned.filter { it.id != movedId }
    val settled = resolveContainerActionGridPlacements(prioritized, cols).associate { it.action.id to (it.col to it.row) }
    return pinned.map { action ->
        val (c, r) = settled[action.id] ?: (action.gridCol to action.gridRow)
        action.copy(gridCol = c, gridRow = r)
    }
}

/** GRID uses separate exact horizontal/vertical gaps; legacy FREE action geometry is independent. */
fun containerActionGridGeometry(item: ComponentItem, index: Int, columns: Int, widthDp: Float,
    fontScale: Float = 1f): ContainerActionGeometry {
    val cols = columns.coerceIn(1, 8)
    val gap = item.containerItemGapDp.coerceIn(MIN_GRID_GAP_DP, MAX_GRID_GAP_DP)
    val width = ((widthDp - (cols - 1) * gap) / cols).coerceAtLeast(1f)
    val height = legacyContainerActionTileHeightDp(item.copy(containerLayout = ContainerLayoutType.GRID), fontScale)
    val placements = resolveContainerActionGridPlacements(item.containerActions, cols)
    val slot = placements.getOrNull(index)
    val col = slot?.col ?: (index % cols)
    val row = slot?.row ?: (index / cols)
    val pitchX = (width + gap).coerceAtLeast(8f)
    val pitchY = (height + containerRowGapDp(item)).coerceAtLeast(12f)
    return ContainerActionGeometry(col * pitchX, row * pitchY, width, height)
}

fun containerActionGeometry(
    action: QuickActionItem,
    index: Int,
    columns: Int,
    widthDp: Float,
    itemSizeDp: Int = 0,
    gapDp: Int = 0
): ContainerActionGeometry {
    val cols = columns.coerceIn(1, 8)
    val available = widthDp.coerceAtLeast(1f)
    val cell = available / cols
    val size = itemSizeDp.takeIf { it > 0 }?.coerceIn(48, 240) ?: 88
    val gap = gapDp.coerceIn(MIN_GRID_GAP_DP, MAX_GRID_GAP_DP)
    val width = (action.wDp?.coerceIn(24, 1200)?.toFloat() ?: (cell - gap)).coerceIn(1f, available)
    val height = (action.hDp?.coerceIn(24, LEGACY_ACTION_VIEWPORT_MAX_DP.toInt())?.toFloat() ?: size.toFloat())
        .coerceAtMost(LEGACY_ACTION_VIEWPORT_MAX_DP)
    val maxFreeY = (LEGACY_ACTION_VIEWPORT_MAX_DP - height).coerceAtLeast(0f)
    val x = (action.xDp?.coerceAtLeast(0)?.toFloat() ?: (index % cols * cell)).coerceIn(0f, (available - width).coerceAtLeast(0f))
    val y = action.yDp?.coerceAtLeast(0)?.toFloat()?.coerceIn(0f, maxFreeY) ?: (index / cols * (size + gap)).toFloat()
    return ContainerActionGeometry(x, y, width, height)
}

fun commitContainerActionGeometry(action: QuickActionItem, xDp: Int, yDp: Int, wDp: Int, hDp: Int): QuickActionItem {
    val clampedH = hDp.coerceIn(24, LEGACY_ACTION_VIEWPORT_MAX_DP.toInt())
    val maxY = (LEGACY_ACTION_VIEWPORT_MAX_DP.toInt() - clampedH).coerceAtLeast(0)
    return action.copy(
        xDp = xDp.coerceIn(0, 100000),
        yDp = yDp.coerceIn(0, maxY),
        wDp = wDp.coerceIn(24, 1200),
        hDp = clampedH
    )
}
