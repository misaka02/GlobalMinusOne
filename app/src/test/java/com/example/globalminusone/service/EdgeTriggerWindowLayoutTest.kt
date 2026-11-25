package com.example.globalminusone.service

import com.example.globalminusone.data.TriggerConfig
import com.example.globalminusone.data.TriggerPosition
import com.example.globalminusone.data.TriggerSide
import com.example.globalminusone.data.TriggerStyle
import com.example.globalminusone.data.TriggerWay
import org.junit.Assert.*
import org.junit.Test

class EdgeTriggerWindowLayoutTest {
    private fun layout(
        position: TriggerPosition,
        config: TriggerConfig = TriggerConfig(),
        width: Int = 1000,
        height: Int = 2000,
        density: Float = 2f,
        insets: Pair<Int, Int> = 0 to 0,
        ime: Int = 0
    ) = calculateEdgeTriggerWindowLayout(position, width, height, density, config, insets, ime)

    @Test fun leftAndRightUsePhysicalLeftCoordinatesWithTheSameVerticalGeometry() {
        assertEquals(EdgeTriggerWindowLayout(30, 300, 0, 1190), layout(TriggerPosition.LEFT))
        assertEquals(EdgeTriggerWindowLayout(30, 300, 970, 1190), layout(TriggerPosition.RIGHT))
    }

    @Test fun topAndBottomSwapPercentAxesAndKeepZeroGravityRelativeY() {
        val expected = EdgeTriggerWindowLayout(150, 60, 595, 0)
        assertEquals(expected, layout(TriggerPosition.TOP))
        assertEquals(expected, layout(TriggerPosition.BOTTOM))
    }

    @Test fun percentagesClampBeforePixelSizingAndOffsetPlacement() {
        val high = TriggerConfig(touchWidthPercent = 999, touchHeightPercent = 999, touchOffsetPercent = 999)
        assertEquals(EdgeTriggerWindowLayout(200, 2000, 800, 0), layout(TriggerPosition.RIGHT, high))
        assertEquals(EdgeTriggerWindowLayout(1000, 400, 0, 0), layout(TriggerPosition.TOP, high))
        val low = high.copy(touchWidthPercent = -8, touchHeightPercent = -8, touchOffsetPercent = -8)
        assertEquals(EdgeTriggerWindowLayout(20, 100, 0, 0), layout(TriggerPosition.LEFT, low))
        assertEquals(EdgeTriggerWindowLayout(72, 20, 0, 0), layout(TriggerPosition.BOTTOM, low))
    }

    @Test fun fractionalDensityRoundsMinimumDimensionsAndThenOffset() {
        val config = TriggerConfig(touchWidthPercent = 1, touchHeightPercent = 5, touchOffsetPercent = 33)
        assertEquals(EdgeTriggerWindowLayout(13, 45, 0, 52),
            layout(TriggerPosition.LEFT, config, 101, 203, 1.25f))
        assertEquals(EdgeTriggerWindowLayout(45, 13, 18, 0),
            layout(TriggerPosition.TOP, config, 101, 203, 1.25f))
    }

    @Test fun sidePullAutomaticAvoidanceUsesEachPhysicalSidesGestureInset() {
        val config = TriggerConfig(triggerWay = TriggerWay.SIDE_PULLDOWN)
        assertEquals(EdgeTriggerWindowLayout(30, 300, 76, 1190),
            layout(TriggerPosition.LEFT, config, insets = 60 to 100))
        assertEquals(EdgeTriggerWindowLayout(30, 300, 854, 1190),
            layout(TriggerPosition.RIGHT, config, insets = 60 to 100))
        assertEquals(48, layout(TriggerPosition.LEFT, config).x)
    }

    @Test fun explicitSidePullOffsetOverridesInsetsAndClampsAtTheAvailableHorizontalSpace() {
        val config = TriggerConfig(triggerWay = TriggerWay.SIDE_PULLDOWN, sidePullHorizontalOffsetDp = 12)
        assertEquals(15, layout(TriggerPosition.LEFT, config, 101, 203, 1.25f, 90 to 90).x)
        assertEquals(73, layout(TriggerPosition.RIGHT, config, 101, 203, 1.25f, 90 to 90).x)
        val excessive = config.copy(sidePullHorizontalOffsetDp = 999)
        assertEquals(88, layout(TriggerPosition.LEFT, excessive, 101, 203, 1.25f).x)
        assertEquals(0, layout(TriggerPosition.RIGHT, excessive, 101, 203, 1.25f).x)
        assertEquals(0, layout(TriggerPosition.LEFT, config.copy(sidePullHorizontalOffsetDp = 0)).x)
    }

    @Test fun allNonSidePullWaysIgnoreInsetsAndCustomHorizontalOffset() {
        for (way in TriggerWay.entries.filter { it != TriggerWay.SIDE_PULLDOWN }) {
            val config = TriggerConfig(triggerWay = way, sidePullHorizontalOffsetDp = 160)
            assertEquals("$way left", 0, layout(TriggerPosition.LEFT, config, insets = 900 to 900).x)
            assertEquals("$way right", 970, layout(TriggerPosition.RIGHT, config, insets = 900 to 900).x)
        }
    }

    @Test fun imeClipsSideHeightBeforeClampingTheVerticalOffset() {
        val long = TriggerConfig(touchWidthPercent = 4, touchHeightPercent = 80, touchOffsetPercent = 100)
        assertEquals(EdgeTriggerWindowLayout(20, 600, 0, 0),
            layout(TriggerPosition.LEFT, long, 500, 1000, 1f, ime = 400))
        assertEquals(EdgeTriggerWindowLayout(20, 200, 480, 400),
            layout(TriggerPosition.RIGHT, long.copy(touchHeightPercent = 20), 500, 1000, 1f, ime = 400))
        // Inset eligibility belongs to the service; the pure adapter consumes the supplied pixels.
        assertEquals(600, layout(TriggerPosition.LEFT, long.copy(avoidInputMethod = false),
            500, 1000, 1f, ime = 400).height)
    }

    @Test fun imeAvailableHeightKeepsTheSeventyTwoDpFloorAndDoesNotChangeHorizontalBars() {
        val config = TriggerConfig(touchHeightPercent = 100, touchOffsetPercent = 100)
        assertEquals(EdgeTriggerWindowLayout(30, 144, 0, 0),
            layout(TriggerPosition.LEFT, config, ime = 3000))
        for (position in listOf(TriggerPosition.TOP, TriggerPosition.BOTTOM)) {
            assertEquals(layout(position, config), layout(position, config, ime = 3000))
        }
    }

    @Test fun extremelyShortScreenRetainsMinimumSizesAndNegativeOffsetsWithoutExtraNormalization() {
        assertEquals(EdgeTriggerWindowLayout(20, 72, 0, -36),
            layout(TriggerPosition.RIGHT, width = 8, height = 20))
        assertEquals(EdgeTriggerWindowLayout(72, 20, -45, 0),
            layout(TriggerPosition.TOP, width = 8, height = 20))
        assertEquals(-36, layout(TriggerPosition.LEFT, width = 8, height = 20, ime = 100).y)
    }

    @Test fun bothRejectsEveryTriggerWayWithTheOriginalMessage() {
        for (way in TriggerWay.entries) {
            val failure = runCatching { layout(TriggerPosition.BOTH, TriggerConfig(triggerWay = way)) }.exceptionOrNull()
            assertTrue("$way", failure is IllegalArgumentException)
            assertEquals("BOTH is handled as separate LEFT and RIGHT triggers", failure?.message)
        }
    }

    @Test fun explicitPositionAndModernPercentFieldsRemainAuthoritativeAcrossLegacyEnumCombinations() {
        for (way in TriggerWay.entries) {
            for (position in TriggerPosition.entries.filter { it != TriggerPosition.BOTH }) {
                val original = TriggerConfig(triggerWay = way, triggerPosition = position)
                val expected = layout(position, original)
                for (side in TriggerSide.entries) for (style in TriggerStyle.entries) {
                    val legacy = original.copy(triggerPosition = TriggerPosition.BOTH, side = side, style = style,
                        verticalPositionPercent = 0f, heightDp = 1, widthDp = 999)
                    assertEquals("$way/$position/$side/$style", expected, layout(position, legacy))
                }
            }
        }
    }
}
