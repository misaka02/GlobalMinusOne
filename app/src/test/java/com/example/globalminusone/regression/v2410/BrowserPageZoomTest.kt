package com.example.globalminusone.regression.v2410

import com.example.globalminusone.browser.BrowserPageZoom
import org.junit.Assert.*
import org.junit.Test

class BrowserPageZoomTest {
    @Test fun nativeScaleIsNormalizedByDensityRatherThanReportedAsTextZoom() {
        assertEquals(100, BrowserPageZoom.percent(3f, 3f))
        assertEquals(50, BrowserPageZoom.percent(1.5f, 3f))
        assertEquals(150, BrowserPageZoom.percent(4.5f, 3f))
    }

    @Test fun requestedPageScaleUsesCurrentNativeScaleAndBounds() {
        assertEquals(2f, BrowserPageZoom.factor(75, 150), 0.001f)
        assertEquals(0.5f, BrowserPageZoom.factor(200, 100), 0.001f)
        assertEquals(0.25f, BrowserPageZoom.factor(100, -1), 0.001f)
        assertEquals(5f, BrowserPageZoom.factor(100, 900), 0.001f)
    }

    @Test fun invalidNativeMeasurementsDoNotEmitNaNOrZeroDivisors() {
        assertEquals(100, BrowserPageZoom.percent(Float.NaN, 3f))
        assertEquals(100, BrowserPageZoom.percent(3f, 0f))
        assertEquals(100, BrowserPageZoom.percent(Float.POSITIVE_INFINITY, 3f))
        try { BrowserPageZoom.factor(0, 100); fail("Zero current scale was accepted") }
        catch (_: IllegalArgumentException) { }
    }
}
