package com.example.globalminusone.regression.v2427

import com.example.globalminusone.data.*
import org.junit.Assert.*
import org.junit.Test

class CompactContainerBudgetTest {
    private val actions = List(4) { QuickActionItem(id = "a$it", title = "应用$it", intentUri = "test:$it") }
    private val compact = ComponentItem(id = "compact", type = ComponentType.CONTAINER_BOX,
        w = 24, containerCompact = true, containerLayout = ContainerLayoutType.GRID,
        containerActions = actions)

    private fun expanded(item: ComponentItem, fontScale: Float = 1f, unit: Int = 0): Float =
        componentDisplayHeightDp(item, 240f, 24, unit, fontScale, compactExpansion = mapOf(item.id to 1f))

    @Test fun fourActionsFitTheirActualRowsWithoutBottomFooterWasteAndEmptyKeepsAddTarget() {
        // Non-empty collapsible containers move '+' to header:
        // margins 4 + header 48 + divider 1 + vertical padding 12 + 1 action row 58 = 123.
        assertEquals(123f, expanded(compact), .001f)
        assertEquals(58f, legacyContainerActionGridHeightDp(compact), 0f)
        assertEquals(48f, compactContainerAddRowHeightDp(), 0f)
        val viewport = expanded(compact) - 4f - 48f - 1f
        assertEquals(12f + 58f, viewport, .001f)
        // Empty container reserves only vertical padding 12 + empty add touch target 48 = 60.
        assertEquals(48f, expanded(compact.copy(containerActions = emptyList())) -
            (4f + 48f + 1f + 12f), .001f)
    }

    @Test fun secondRowKeepsInterRowGapAndDropsOnlyTheUnusedTrailingGap() {
        val twoRows = compact.copy(containerActions = actions + actions.map { it.copy(id = "b${it.id}") },
            containerItemGapDp = 12)
        assertEquals(128f, legacyContainerActionGridHeightDp(twoRows), 0f) // 58 + 12 + 58
        assertEquals(193f, expanded(twoRows), .001f) // 4 + 48 + 1 + 12 + 128
    }

    @Test fun fontScaleGrowsBothRenderedLabelsAndTouchTargets() {
        assertEquals(67f, legacyContainerActionTileHeightDp(compact, 1.5f), 0f)
        assertEquals(136f, expanded(compact, 1.5f), .001f) // 4 + 52 + 1 + 12 + 67
        assertEquals(68f, compactContainerAddRowHeightDp(3f), 0f)
        assertEquals(199f, expanded(compact, 3f), .001f) // 4 + 88 + 1 + 12 + 94
    }

    @Test fun explicitCellsAndFreeSizesStayAuthoritativeWhileAllAutomaticGridRowsFollowContent() {
        assertEquals(120f, legacyContainerActionTileHeightDp(compact.copy(containerItemSizeDp = 120)), 0f)
        // These flags size the container, not an explicitly sized internal action cell.
        assertEquals(58f, legacyContainerActionTileHeightDp(compact.copy(autoHeight = false)), 0f)
        assertEquals(58f, legacyContainerActionTileHeightDp(compact.copy(containerCompact = false)), 0f)
        assertEquals(58f, legacyContainerActionTileHeightDp(compact.copy(type = ComponentType.FOLDER, containerCompact = false)), 0f)
        assertEquals(88f, legacyContainerActionTileHeightDp(compact.copy(containerLayout = ContainerLayoutType.FREE)), 0f)
    }

    @Test fun arbitraryManualChildrenStaySizedWhileTheCurtainViewportRemainsFinite() {
        val child = ComponentItem(id = "note", type = ComponentType.NOTE_CARD, w = 24, h = 12, autoHeight = false)
        val parent = compact.copy(containerActions = emptyList(), containerComponents = listOf(child))
        assertEquals(120f, componentDisplayHeightDp(child, 220f, 24, 10), 0f)
        assertEquals(185f, expanded(parent, unit = 10), .001f) // 52 + 1 + 12 + 120
        assertEquals(533f, expanded(parent.copy(containerComponents = listOf(child.copy(h = 120))), unit = 10), .001f)
        assertEquals(12, parent.containerComponents.single().h)
        assertFalse(parent.containerComponents.single().autoHeight)
        val half = componentDisplayHeightDp(parent, 240f, 24, 10,
            compactExpansion = mapOf(parent.id to .5f))
        assertEquals((52f + 185f) / 2f, half, .001f)
    }
}
