package com.example.globalminusone.regression.v2449

import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.ContainerLayoutType
import com.example.globalminusone.data.policy.GRID_SPAN_FULL_ROW
import com.example.globalminusone.data.policy.arrangePageGridComponents
import com.example.globalminusone.data.policy.componentDisplayHeightDp
import com.example.globalminusone.screenassistant.ScreenTextBlock
import com.example.globalminusone.screenassistant.ScreenTextInpainter
import com.example.globalminusone.screenassistant.TextCharSpan
import com.example.globalminusone.ui.settings.SettingsSearchIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V2449FixesTest {

    @Test
    fun compactContainerUsesExactHeightWithoutReservingExtraGridRowAndAnimatesContinuously() {
        val children = List(4) { idx ->
            ComponentItem(id = "c$idx", type = ComponentType.APP_ICON, title = "App $idx")
        }
        val compact = ComponentItem(
            id = "compact1",
            type = ComponentType.CONTAINER_BOX,
            title = "常用",
            containerCompact = true,
            autoHeight = true,
            containerLayout = ContainerLayoutType.GRID,
            containerColumns = 4,
            gridSpanX = GRID_SPAN_FULL_ROW,
            containerComponents = children
        )
        val below = ComponentItem(
            id = "below",
            type = ComponentType.APP_ICON,
            title = "下方图标",
            gridSpanX = 1,
            gridSpanY = 1
        )
        val widthDp = 320f
        val collapsedLayout = arrangePageGridComponents(
            components = listOf(compact, below),
            availableWidthDp = widthDp,
            columnCount = 4,
            compactExpansion = emptyMap()
        )
        val expandedLayout = arrangePageGridComponents(
            components = listOf(compact, below),
            availableWidthDp = widthDp,
            columnCount = 4,
            compactExpansion = mapOf(compact.id to 1f)
        )
        val collapsedCompact = collapsedLayout.placements.first { it.id == compact.id }
        val expandedCompact = expandedLayout.placements.first { it.id == compact.id }
        val expandedBelow = expandedLayout.placements.first { it.id == below.id }

        val expectedOpenDp = componentDisplayHeightDp(
            item = compact,
            outerWidthDp = widthDp,
            gridColumns = 32,
            compactExpansion = mapOf(compact.id to 1f)
        )
        assertEquals(expectedOpenDp, expandedCompact.heightDp, 0.05f)
        assertTrue(
            "Collapsed container should not stretch to full grid cell height when compact",
            collapsedCompact.heightDp < expandedLayout.metrics.cellHeightDp
        )
        assertEquals(
            expandedCompact.yDp + expandedCompact.heightDp + expandedLayout.metrics.rowGapDp,
            expandedBelow.yDp,
            0.05f
        )

        // Verify monotonicity & zero discrete spanY jump across expansion 0f..1f
        var prevHeight = collapsedCompact.heightDp
        var prevBelowY = collapsedLayout.placements.first { it.id == below.id }.yDp
        for (step in 1..20) {
            val f = step / 20f
            val mid = arrangePageGridComponents(
                components = listOf(compact, below),
                availableWidthDp = widthDp,
                columnCount = 4,
                compactExpansion = mapOf(compact.id to f)
            )
            val h = mid.placements.first { it.id == compact.id }.heightDp
            val by = mid.placements.first { it.id == below.id }.yDp
            assertTrue("Height must increase smoothly without jumps (step=$step, $prevHeight -> $h)", h >= prevHeight)
            assertTrue("Below card Y must track smoothly ($prevBelowY -> $by)", by >= prevBelowY - 0.01f)
            assertTrue("Below step delta must remain bounded (delta=${by - prevBelowY})", (by - prevBelowY) < 12f)
            prevHeight = h
            prevBelowY = by
        }
    }

    @Test
    fun synthesizePerCharSpansRejectsCorruptedJumpSpansAndPreservesOuterBounds() {
        val text = "Hello world hello"
        // Simulate a corrupted jump span from duplicate 'l' earlier in the line jumping ahead to later chars
        val corrupted = listOf(
            TextCharSpan(0, 1, 100, 10, 114, 40),
            TextCharSpan(1, 2, 114, 10, 126, 40),
            TextCharSpan(14, 15, 290, 10, 304, 40), // skipped 2..13 -> should bridge cleanly or reject backward/broken jumps
            TextCharSpan(15, 16, 120, 10, 134, 40) // backward jump! Must be dropped
        )
        val result = ScreenTextInpainter.synthesizePerCharSpansPure(
            text = text,
            leftPx = 100,
            topPx = 10,
            rightPx = 340,
            bottomPx = 40,
            existingSpans = corrupted
        )
        assertEquals(text.length, result.size)
        assertEquals(100, result.first().leftPx)
        assertEquals(340, result.last().rightPx)
        for (i in 0 until result.lastIndex) {
            assertTrue(
                "Char spans must increase monotonically without clipping (${result[i]} vs ${result[i + 1]})",
                result[i].rightPx <= result[i + 1].leftPx + 1 && result[i + 1].rightPx > result[i].rightPx
            )
        }
    }

    @Test
    fun settingsSearchIndexFindsTargetScreensForCommonQueries() {
        val ocrHits = SettingsSearchIndex.search("ocr")
        assertTrue(ocrHits.any { it.targetScreen == "SCREEN_ASSISTANT" && it.expandOcrSection })

        val ballHits = SettingsSearchIndex.search("悬浮球")
        assertTrue(ballHits.any { it.targetScreen == "SB_SETTINGS" })

        val themeHits = SettingsSearchIndex.search("深色")
        assertTrue(themeHits.any { it.targetScreen == "THEME" })

        val permHits = SettingsSearchIndex.search("无障碍")
        assertTrue(permHits.any { it.targetScreen == "PERMISSIONS" })

        assertTrue(SettingsSearchIndex.search("   ").isEmpty())
    }
}
