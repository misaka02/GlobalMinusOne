package com.example.globalminusone.regression.v2436

import com.example.globalminusone.service.overlay.triggerEdgeExclusions
import org.junit.Assert.*
import org.junit.Test

class PanelEdgeExclusionTest {
    @Test fun hiddenOrUnmeasuredWindowDoesNotTakeSystemGestures() {
        assertTrue(triggerEdgeExclusions(24, 900, 2f, false, true).isEmpty())
        assertTrue(triggerEdgeExclusions(0, 900, 2f, true, true).isEmpty())
        assertTrue(triggerEdgeExclusions(480, 24, 2f, true, false).isEmpty())
    }

    @Test fun onlyTheActualTriggerReservesOneBoundedBandInsteadOfTwoFullPanelBands() {
        // R8 supersedes the ineffective panel-wide back-gesture workaround.
        val rects = triggerEdgeExclusions(24, 900, 2f, true, true)
        assertEquals(1, rects.size)
        assertEquals(0, rects.single().left)
        assertEquals(24, rects.single().right)
        assertEquals(250, rects.single().top)
        assertEquals(650, rects.single().bottom)
    }

    @Test fun shortTriggersRemainFullyProtectedWhileLongTriggersFitTheSamePerEdgeBudget() {
        val short = triggerEdgeExclusions(15, 128, 1.5f, true, true).single()
        assertEquals(0, short.top)
        assertEquals(128, short.bottom)
        for (density in listOf(.75f, 1f, 1.333f, 1.5f, 2f, 3f)) {
            val rect = triggerEdgeExclusions(24, 900, density, true, true).single()
            assertTrue(rect.top >= 0 && rect.bottom <= 900)
            assertTrue((rect.bottom - rect.top) / density <= 200f)
        }
        assertTrue(triggerEdgeExclusions(24, 900, Float.NaN, true, true).isEmpty())
    }
}
