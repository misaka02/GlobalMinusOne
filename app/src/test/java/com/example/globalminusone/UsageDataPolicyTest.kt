package com.example.globalminusone

import com.example.globalminusone.ui.panel.*
import org.junit.Assert.*
import org.junit.Test

class UsageDataPolicyTest {
    @Test fun clipsMidnightAndIncludesCurrentlyForegroundApp() {
        val result = summarizeAppUsage(listOf(
            AppUsageTransition("a", 50, true), AppUsageTransition("a", 120, false),
            AppUsageTransition("b", 140, true)
        ), start = 100, end = 200)
        assertEquals(80L, result.foregroundMillis)
        assertEquals(listOf("b", "a"), result.recentPackages)
    }
    @Test fun screenOffAndShutdownEndForegroundWithoutInventingTime() {
        val result = summarizeAppUsage(listOf(
            AppUsageTransition("a", 110, true), AppUsageTransition("a", 115, true),
            AppUsageTransition(null, 130, false), AppUsageTransition("a", 140, false)
        ), start = 100, end = 200)
        assertEquals(20L, result.foregroundMillis)
        assertEquals(listOf("a"), result.recentPackages)
    }
    @Test fun emptyEventsProduceNoSyntheticUsageOrApps() {
        val result = summarizeAppUsage(emptyList(), 100, 200)
        assertEquals(0L, result.foregroundMillis)
        assertTrue(result.recentPackages.isEmpty())
    }
}
