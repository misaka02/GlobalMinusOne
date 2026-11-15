package com.example.globalminusone.regression.v2423

import com.example.globalminusone.data.*
import org.junit.Assert.*
import org.junit.Test

class ContainerCurtainPolicyTest {
    private val child = ComponentItem(id = "note", type = ComponentType.NOTE_CARD, w = 24, h = 18, autoHeight = false)
    private val compact = ComponentItem(id = "curtain", type = ComponentType.CONTAINER_BOX,
        containerCompact = true, autoHeight = false, w = 24, h = 70, containerComponents = listOf(child))

    @Test fun curtainUsesOneHeaderAndAnAnimatedBodyWithoutRewritingStoredGeometry() {
        val closed = componentDisplayHeightDp(compact, 240f, 24)
        val open = componentDisplayHeightDp(compact, 240f, 24, compactExpansion = mapOf(compact.id to 1f))
        val half = componentDisplayHeightDp(compact, 240f, 24, compactExpansion = mapOf(compact.id to .5f))
        assertEquals(52f, closed, 0f) // 48dp header + the default 2dp top/bottom margins
        assertEquals((closed + open) / 2, half, .001f)
        assertTrue(open > 228f)
        assertTrue(open <= 52f + CONTAINER_VIEWPORT_MAX_DP + 41f)
        assertEquals(70, compact.h)
        assertFalse(compact.autoHeight)
        assertEquals(child, compact.containerComponents.single())
        assertEquals(closed, componentDisplayHeightDp(compact, 240f, 24, compactExpansion = mapOf(compact.id to Float.NaN)), 0f)
    }

    @Test fun curtainAndFollowingCardsArePushedDownTransitivelyAndCollapseRestoresPositions() {
        val first = compact.copy(y = 3)
        val second = compact.copy(id = "second", y = 10)
        val last = child.copy(id = "last", y = 18)
        val original = listOf(last, second, first) // saved order need not match vertical order
        val collapsed = original.map { it.copy(h = 5) }
        val expanded = collapsed.map { if (it.id == first.id) it.copy(h = 14) else if (it.id == second.id) it.copy(h = 8) else it }
        val shown = displaceExpandedContainerRows(original, collapsed, expanded).associateBy { it.id }
        assertEquals(3, shown.getValue(first.id).y)
        assertEquals(19, shown.getValue(second.id).y)
        assertEquals(30, shown.getValue(last.id).y)
        assertEquals(original.map { it.y }, displaceExpandedContainerRows(original, collapsed, collapsed).map { it.y })
        assertEquals(listOf(18, 10, 3), original.map { it.y })
    }

    @Test fun nestedCurtainExpansionIsIncludedInAutomaticParentAndGridFootprint() {
        val parent = ComponentItem(type = ComponentType.CONTAINER_BOX, w = 24,
            containerComponents = listOf(compact.copy(y = 0), child.copy(id = "next", y = 6)))
        val fractions = mapOf(compact.id to 1f)
        assertTrue(componentDisplayHeightDp(parent, 240f, 24, compactExpansion = fractions) >
            componentDisplayHeightDp(parent, 240f, 24))
        val grid = arrangeContainerChildren(listOf(compact, child.copy(id = "next")), 24, 240f, 10f,
            compactExpansion = fractions)
        assertTrue(grid[1].y >= grid[0].y + grid[0].h)
        assertEquals(70, compact.h)
        assertTrue(compactContainerHeaderHeightDp(2f) >= 64f)
    }

    @Test fun folderPrefersConfiguredPanelEvenWhenTriggerIsOnItsBottomOrOutsideIt() {
        val panel = ContainerPopupBounds(600, 180, 980, 780)
        val bottom = containerPopupPosition(890, 700, 960, 760, 300, 400, 8, 24, 992, 976, 8, panel)
        assertEquals(ContainerPopupPosition(640, 380), bottom)
        val offPanel = containerPopupPosition(10, 20, 40, 50, 300, 400, 8, 24, 992, 976, 8, panel)
        assertEquals(ContainerPopupPosition(640, 180), offPanel)
        val keyboard = containerPopupPosition(890, 700, 960, 760, 300, 200, 8, 24, 992, 460, 8, panel)
        assertEquals(ContainerPopupPosition(640, 260), keyboard)
        val oversized = containerPopupPosition(100, 100, 120, 120, 500, 900, 8, 24, 992, 976, 8, panel)
        assertTrue(oversized.x in 8..492)
        assertTrue(oversized.y in 24..76)
    }
}
