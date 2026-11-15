package com.example.globalminusone

import com.example.globalminusone.browser.ChromeViewportPolicy
import org.junit.Assert.*
import org.junit.Test

class ChromeViewportPolicyTest {
    @Test fun measuredMainPanelGetsEnoughLogicalContentForNativeControls() {
        val dpi = ChromeViewportPolicy.densityDpi(447, 551, 320)
        assertEquals(183, dpi)
        assertTrue(447 * 160.0 / dpi >= 320)
        assertTrue(551 * 160.0 / dpi >= 480)
    }

    @Test fun measuredResizeAndHostDensityChangesHaveConsistentContentScale() {
        assertEquals(167, ChromeViewportPolicy.densityDpi(432, 501, 320))
        assertEquals(183, ChromeViewportPolicy.densityDpi(447, 551, 320))
        assertEquals(160, ChromeViewportPolicy.densityDpi(447, 551, 160))
    }

    @Test fun fullSizeWindowsKeepEffectiveHostDensityAndRespectWidthInLandscape() {
        assertEquals(320, ChromeViewportPolicy.densityDpi(720, 1280, 320))
        assertEquals(160, ChromeViewportPolicy.densityDpi(320, 960, 320))
        assertEquals(240, ChromeViewportPolicy.densityDpi(1920, 720, 320))
    }

    @Test fun flooringNeverCutsBelowMinimumLogicalAreaWhenDpiFloorFits() {
        for (width in listOf(144, 319, 447, 720, 2048)) {
            for (height in listOf(216, 479, 551, 1280, 2048)) {
                val dpi = ChromeViewportPolicy.densityDpi(width, height, 320)
                assertTrue(dpi in 72..320)
                assertTrue(width * 160.0 / dpi >= 320)
                assertTrue(height * 160.0 / dpi >= 480)
            }
        }
    }

    @Test fun tinyOrInvalidMeasurementsUseLegalFloorWithoutOverflow() {
        assertEquals(72, ChromeViewportPolicy.densityDpi(64, 64, 320))
        assertEquals(72, ChromeViewportPolicy.densityDpi(0, 0, 320))
        assertEquals(72, ChromeViewportPolicy.densityDpi(Int.MIN_VALUE, 551, 320))
        assertEquals(72, ChromeViewportPolicy.densityDpi(447, Int.MIN_VALUE, 320))
        assertEquals(72, ChromeViewportPolicy.densityDpi(447, 551, 0))
        assertEquals(72, ChromeViewportPolicy.densityDpi(447, 551, Int.MIN_VALUE))
        assertEquals(640, ChromeViewportPolicy.densityDpi(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE))
    }
}
