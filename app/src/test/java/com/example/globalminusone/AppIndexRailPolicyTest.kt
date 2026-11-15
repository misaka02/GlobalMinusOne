package com.example.globalminusone

import com.example.globalminusone.ui.panel.appIndexAtPosition
import com.example.globalminusone.ui.panel.appIndexAtRailPosition
import com.example.globalminusone.ui.panel.appIndexRailTicks
import org.junit.Assert.*
import org.junit.Test

class AppIndexRailPolicyTest {
    @Test fun shortRailKeepsEndpointsReadableWithoutReducingTouchCoverage() {
        val height = 64
        val minTickHeight = 20
        val groups = 27
        val ticks = appIndexRailTicks(groups, height / minTickHeight)
        assertEquals(0, ticks.first())
        assertEquals(groups - 1, ticks.last())
        assertTrue(ticks.size * minTickHeight <= height)
        assertEquals(ticks.distinct().sorted(), ticks)
        listOf(0, 13, 26).forEachIndexed { slot, group ->
            val center = (slot + 0.5f) * height / ticks.size
            assertEquals(group, appIndexAtRailPosition(center, height.toFloat(), groups, ticks))
        }
        // A finger crossing the short rail can still reach every group, including
        // those intentionally absent from its three displayed labels.
        val reachable = (0..height).mapNotNull {
            appIndexAtRailPosition(it.toFloat(), height.toFloat(), groups, ticks)
        }.toSet()
        assertEquals((0 until groups).toSet(), reachable)
        val dense = appIndexRailTicks(groups, groups)
        (-1..height * 4 + 1).forEach {
            val y = it / 4f
            assertEquals(appIndexAtPosition(y, height.toFloat(), groups),
                appIndexAtRailPosition(y, height.toFloat(), groups, dense))
        }
    }
}
