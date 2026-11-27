package com.example.globalminusone

import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.PageConfig
import com.example.globalminusone.data.PageLayoutType
import com.example.globalminusone.data.operations.ComponentInsertionOperations
import com.example.globalminusone.data.policy.arrangePageGridComponents
import com.example.globalminusone.data.policy.computeEmptyGridCells
import com.example.globalminusone.data.policy.computeGridRowBands
import com.example.globalminusone.data.policy.resolveGridDropCellWithHysteresis
import com.example.globalminusone.data.policy.resolveLinearDragOrder
import com.example.globalminusone.data.policy.resolvePageGridDropPlacements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GridDragAndSlotPolicyTest {
    private fun appItem(id: String, col: Int = -1, row: Int = -1, spanX: Int = 1, spanY: Int = 1) =
        ComponentItem(
            id = id,
            type = ComponentType.APP_ICON,
            title = id,
            packageName = "pkg.$id",
            gridCol = col,
            gridRow = row,
            gridSpanX = spanX,
            gridSpanY = spanY
        )

    @Test
    fun emptyGridCells_excludesOccupiedSpansAndIncludesMiddleHoles() {
        val items = listOf(
            appItem("a", col = 0, row = 0, spanX = 2, spanY = 1),
            appItem("b", col = 3, row = 0, spanX = 1, spanY = 2),
            appItem("c", col = 1, row = 1, spanX = 1, spanY = 1)
        )
        val layout = arrangePageGridComponents(
            components = items,
            availableWidthDp = 320f,
            columnCount = 4,
            iconSizeDp = 44
        )
        val empties = computeEmptyGridCells(layout, visualRows = 2).map { it.col to it.row }
        assertEquals(
            listOf(2 to 0, 0 to 1, 2 to 1),
            empties
        )
    }

    @Test
    fun dropCellHysteresis_preventsBoundaryPingPongWhileAllowingClearMoves() {
        val items = listOf(appItem("a", col = 1, row = 0))
        val layout = arrangePageGridComponents(items, availableWidthDp = 320f, columnCount = 4, iconSizeDp = 44)
        val bands = computeGridRowBands(layout, visualRows = 2)
        val pitchX = layout.metrics.cellWidthDp + layout.metrics.itemGapDp

        // Near right edge of cell (1, 0): fraction 1.58 is inside deadband (0.50 + 0.18 = 0.68)
        val held = resolveGridDropCellWithHysteresis(
            xDp = pitchX * 1.58f,
            yDp = 0f,
            spanX = 1,
            metrics = layout.metrics,
            rowBands = bands,
            previousCell = 1 to 0
        )
        assertEquals(1 to 0, held)

        // Past deadband (1.72 >= 1.68): switches cleanly to column 2
        val moved = resolveGridDropCellWithHysteresis(
            xDp = pitchX * 1.72f,
            yDp = 0f,
            spanX = 1,
            metrics = layout.metrics,
            rowBands = bands,
            previousCell = 1 to 0
        )
        assertEquals(2 to 0, moved)
    }

    @Test
    fun unequalSpanDrop_movesTwoOneByOneVictimsIntoVacatedTwoByOneOrigin() {
        val wide = appItem("wide", col = 0, row = 0, spanX = 2, spanY = 1)
        val v1 = appItem("v1", col = 2, row = 0, spanX = 1, spanY = 1)
        val v2 = appItem("v2", col = 3, row = 0, spanX = 1, spanY = 1)
        val settled = resolvePageGridDropPlacements(
            components = listOf(wide, v1, v2),
            availableWidthDp = 320f,
            columnCount = 4,
            iconSizeDp = 44,
            movedId = "wide",
            targetCol = 2,
            targetRow = 0
        )
        val resolvedWide = settled.first { it.id == "wide" }
        val resolvedV1 = settled.first { it.id == "v1" }
        val resolvedV2 = settled.first { it.id == "v2" }
        assertEquals(2 to 0, resolvedWide.gridCol to resolvedWide.gridRow)
        assertEquals(0, resolvedV1.gridRow)
        assertEquals(0, resolvedV2.gridRow)
        assertEquals(setOf(0, 1), setOf(resolvedV1.gridCol, resolvedV2.gridCol))
    }

    @Test
    fun linearDragOrder_unevenCardHeightsDoNotPingPongAfterSwap() {
        val starts = mapOf("tall" to 0f, "short" to 210f)
        val heights = mapOf("tall" to 200f, "short" to 40f)
        // Center stays slightly inside short card without crossing deadband (210 + 40 * 0.65 = 236)
        assertEquals(
            listOf("tall", "short"),
            resolveLinearDragOrder(listOf("tall", "short"), "tall", 232f, starts, heights)
        )
        // Crosses threshold -> swaps cleanly
        val swapped = resolveLinearDragOrder(listOf("tall", "short"), "tall", 240f, starts, heights)
        assertEquals(listOf("short", "tall"), swapped)
        // After swap, "short" moves up to y=0..40 and "tall" starts at y=50; keeping finger center at 240
        // MUST NOT swap "tall" back above "short"
        val postSwapStarts = mapOf("short" to 0f, "tall" to 50f)
        assertEquals(
            listOf("short", "tall"),
            resolveLinearDragOrder(swapped, "tall", 240f, postSwapStarts, heights)
        )
    }

    @Test
    fun insertIntoEmptyGridCell_pinsExistingAndPlacesAtExactCell() {
        val a = appItem("a", col = -1, row = -1)
        val b = appItem("b", col = -1, row = -1)
        val page = PageConfig(
            id = "p1",
            name = "网格页",
            layoutType = PageLayoutType.GRID,
            gridColumnCount = 4,
            gridIconSizeDp = 44,
            components = listOf(a, b)
        )
        val candidate = ComponentInsertionOperations.placeMenu(
            page = page,
            containerId = null,
            item = appItem("added"),
            beforeId = null,
            columns = 32,
            nextY = { 0 },
            rows = { 4 },
            targetGridCell = 2 to 1,
            availableWidthDp = 320f,
            fontScale = 1f,
            heightUnitDp = 0
        )!!
        val placedA = candidate.components.first { it.id == "a" }
        val placedB = candidate.components.first { it.id == "b" }
        val placedAdded = candidate.components.first { it.id == "added" }
        assertEquals(0 to 0, placedA.gridCol to placedA.gridRow)
        assertEquals(1 to 0, placedB.gridCol to placedB.gridRow)
        assertEquals(2 to 1, placedAdded.gridCol to placedAdded.gridRow)
        assertTrue(candidate.components.size == 3)
    }
}
