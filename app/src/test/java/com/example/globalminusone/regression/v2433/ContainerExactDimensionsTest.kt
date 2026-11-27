package com.example.globalminusone.regression.v2433

import com.example.globalminusone.data.*
import org.junit.Assert.*
import org.junit.Test
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.policy.ContainerGridChildBounds
import com.example.globalminusone.data.ContainerLayoutType
import com.example.globalminusone.data.policy.componentDisplayHeightDp
import com.example.globalminusone.data.policy.containerGridChildBounds
import com.example.globalminusone.data.policy.containerGridContentWidthDp
import com.example.globalminusone.data.policy.containerIconSizeDp
import com.example.globalminusone.data.policy.containerPopupWidthDp

class ContainerExactDimensionsTest {
    private val children = List(8) { ComponentItem(id = "app-$it", type = ComponentType.APP_ICON,
        x = 7, y = 13, w = 3, h = 2, autoHeight = false) }

    private fun layout(icon: Int, gap: Int, viewport: Float = 320f): List<ContainerGridChildBounds> {
        val folder = ComponentItem(type = ComponentType.FOLDER, containerColumns = 4,
            containerComponents = children, containerIconSizeDp = icon, containerItemGapDp = gap)
        val width = containerGridContentWidthDp(folder, viewport)
        return containerGridChildBounds(children, 32, width, 10f, configuredHeightUnitDp = 10,
            iconColumns = 4, iconSizeDp = icon, itemGapDp = gap)
    }

    @Test fun actualIconBoundsGrowFor32Then48Then64WithoutSavedGeometryChanges() {
        val layouts = listOf(32, 48, 64).map { layout(it, 8) }
        assertEquals(listOf(32, 48, 64), layouts.map { it.first().iconRenderSizeDp })
        // GRID now removes the old fixed 4dp padding above/below each icon.
        assertEquals(listOf(48f, 64f, 80f), layouts.map { it.first().height })
        layouts.forEach { bounds ->
            assertTrue(bounds.all { it.width >= it.iconRenderSizeDp!! + 4 })
            assertEquals(children, bounds.map { it.item })
            assertEquals(4, bounds.count { it.y == 0f })
        }
    }

    @Test fun gaps4Then8Then12RemainExactInBothDirections() {
        val layouts = listOf(4, 8, 12).map { layout(48, it) }
        assertEquals(listOf(4f, 8f, 12f), layouts.map { it[1].x - it[0].x - it[0].width })
        assertEquals(listOf(4f, 8f, 12f), layouts.map { it[4].y - it[0].y - it[0].height })
        assertEquals(3, layouts.map { it[1].x }.distinct().size)
        assertEquals(3, layouts.map { it[0].width }.distinct().size)
    }

    @Test fun narrowViewportKeepsColumnsAndLargeIconsWithScrollableContentExtent() {
        val shown = layout(128, 12, viewport = 180f)
        assertEquals(4, shown.count { it.y == 0f })
        assertTrue(shown.first().width >= 132f)
        assertEquals(128, shown.first().iconRenderSizeDp)
        assertEquals(564f, shown[3].x + shown[3].width, .001f)
        val folder = ComponentItem(type = ComponentType.FOLDER, containerColumns = 4,
            containerComponents = children, containerIconSizeDp = 128, containerItemGapDp = 12)
        assertEquals(588f, containerPopupWidthDp(folder, 800f), .001f)
        assertEquals(320f, containerPopupWidthDp(folder, 320f), .001f)
        assertEquals(260f, containerPopupWidthDp(folder.copy(containerPopupWidthDp = 260), 800f), .001f)
    }

    @Test fun containerHeightBudgetsTheSameExactBottomAsRenderedGrid() {
        val parent = ComponentItem(type = ComponentType.CONTAINER_BOX, containerLayout = ContainerLayoutType.GRID,
            containerColumns = 4, containerComponents = children, containerIconSizeDp = 48,
            containerItemGapDp = 8, showTitle = false, autoHeight = true)
        // 344dp outer - 24dp wrapper = 320dp grid. Two real 64dp rows + 8dp gap + 20dp wrapper.
        assertEquals(156f, componentDisplayHeightDp(parent, 344f, 32, configuredHeightUnitDp = 10), .001f)
    }

    @Test fun complexCardsSpansAndNestedContainerDataRemainPresent() {
        val nested = ComponentItem(type = ComponentType.CONTAINER_BOX, w = 32, h = 9,
            autoHeight = false, containerComponents = children)
        val spanning = ComponentItem(type = ComponentType.STAT_RAM, gridSpanX = 2, gridSpanY = 2)
        val input = listOf(children[0], spanning, nested)
        val shown = containerGridChildBounds(input, 32, 320f, 10f, iconColumns = 4,
            iconSizeDp = 48, itemGapDp = 8)
        assertEquals(input, shown.map { it.item })
        assertEquals(156f, shown[1].width, .001f)
        assertTrue(shown[1].height >= 136f) // Two 64dp icon rows plus one 8dp row gap.
        assertTrue(shown[2].y >= shown.take(2).maxOf { it.y + it.height } + 8f)
        assertEquals(320f, shown[2].width, .001f)
        assertEquals(children, shown[2].item.containerComponents)
    }
}
