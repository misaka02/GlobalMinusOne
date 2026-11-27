package com.example.globalminusone

import com.example.globalminusone.service.accessibility.AccessibilityMousePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityMousePolicyTest {
    @Test
    fun equalPhysicalMotion_hasEqualGainOnBothAxes() {
        val point = AccessibilityMousePolicy.move(100f, 100f, 20f, 20f, 1080, 2400, 40, "一般")
        assertEquals(point.first, point.second, 0.001f)
        assertEquals(AccessibilityMousePolicy.gain(1080, 2400, 40, "一般"),
            AccessibilityMousePolicy.gain(2400, 1080, 40, "一般"), 0.001f)
    }

    @Test
    fun largerTravelMeansFinerMotion_andSpeedOrderIsStable() {
        val coarse = AccessibilityMousePolicy.gain(1080, 2400, 20, "一般")
        val fine = AccessibilityMousePolicy.gain(1080, 2400, 80, "一般")
        assertEquals(coarse / 4f, fine, 0.001f)
        assertTrue(AccessibilityMousePolicy.gain(1080, 2400, 40, "快") >
            AccessibilityMousePolicy.gain(1080, 2400, 40, "一般"))
        assertTrue(AccessibilityMousePolicy.gain(1080, 2400, 40, "一般") >
            AccessibilityMousePolicy.gain(1080, 2400, 40, "慢"))
        assertEquals(AccessibilityMousePolicy.gain(1080, 2400, 20, "一般"),
            AccessibilityMousePolicy.gain(1080, 2400, -10, "一般"), 0.001f)
        assertEquals(AccessibilityMousePolicy.gain(1080, 2400, 80, "一般"),
            AccessibilityMousePolicy.gain(1080, 2400, 200, "一般"), 0.001f)
    }

    @Test
    fun relativePointerMotion_staysInsideDisplayIncludingDegenerateBounds() {
        assertEquals(0f to 2399f,
            AccessibilityMousePolicy.move(400f, 400f, -1000f, 1000f, 1080, 2400, 40, "快"))
        assertEquals(0f to 0f,
            AccessibilityMousePolicy.move(10f, 10f, 100f, 100f, 0, 0, 40, "一般"))
    }

    @Test
    fun edgeDwell_requiresAnEdgeAndRestartsAfterSignificantMotion() {
        assertFalse(AccessibilityMousePolicy.atEdge(300, 500, 80, 1080, 2400, 12))
        assertTrue(AccessibilityMousePolicy.atEdge(8, 500, 80, 1080, 2400, 12))
        assertTrue(AccessibilityMousePolicy.atEdge(1000, 500, 80, 1080, 2400, 12))
        assertTrue(AccessibilityMousePolicy.atEdge(300, 2320, 80, 1080, 2400, 12))
        assertTrue(AccessibilityMousePolicy.dwellStillValid(0f, 500f, 2f, 503f, 8f))
        assertFalse(AccessibilityMousePolicy.dwellStillValid(0f, 500f, 0f, 520f, 8f))
        assertEquals(600L, AccessibilityMousePolicy.EDGE_DWELL_MS)
    }
}
