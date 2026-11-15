package com.example.globalminusone.ui.panel

import org.junit.Assert.*
import org.junit.Test

class CyclicPanelPolicyTest {
    @Test fun bothEndSwipesMapToTheOppositeRealPageAndRecenterWithoutChangingIt() {
        for (count in 2..8) {
            val afterLast = centeredPanelPage(count - 1, count) + 1
            assertEquals(0, realPanelPage(afterLast, count))
            assertEquals(count - 1, realPanelPage(centeredPanelPage(0, count) - 1, count))
            for (virtual in 0 until count * 3) {
                val real = realPanelPage(virtual, count)
                assertEquals(real, realPanelPage(centeredPanelPage(real, count), count))
            }
        }
    }
    @Test fun explicitNavigationChoosesAnEquivalentClosestPageWithoutOutOfRangeTargets() {
        assertEquals(0, nearestPanelPage(99, 1, 0))
        assertEquals(0, realPanelPage(-1, 1))
        for (count in 2..8) for (current in 0 until count * 3) for (real in 0 until count) {
            val target = nearestPanelPage(real, count, current)
            assertTrue(target in 0 until count * 3)
            assertEquals(real, realPanelPage(target, count))
            assertTrue((0..2).all { kotlin.math.abs(target - current) <= kotlin.math.abs(it * count + real - current) })
        }
    }
}
