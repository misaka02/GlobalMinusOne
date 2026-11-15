package com.example.globalminusone.system

import org.junit.Assert.*
import org.junit.Test

class BrightnessPolicyTest {
    @Test fun missingOrOemScaleIsUnknownInsteadOfBeingMisreadAsAPublicBrightnessLevel() {
        listOf(null, -1, 0, 256, 4095).forEach { assertNull(publicBrightnessPercent(it)) }
        assertEquals(0, publicBrightnessPercent(1))
        assertEquals(100, publicBrightnessPercent(255))
    }
    @Test fun automaticUnknownAndUnpermittedStatesNeverAllowAManualWrite() {
        listOf(null, -1, 2).forEach { assertEquals(BrightnessMode.UNKNOWN, brightnessMode(it)) }
        val level = publicBrightnessPercent(128)
        assertFalse(BrightnessState(level, brightnessMode(null), true).canWrite)
        assertFalse(BrightnessState(level, brightnessMode(1), true).canWrite)
        assertFalse(BrightnessState(level, brightnessMode(0), false).canWrite)
        assertFalse(BrightnessState(null, brightnessMode(0), true).canWrite)
        assertFalse(BrightnessState(101, brightnessMode(0), true).canWrite)
        assertTrue(BrightnessState(level, brightnessMode(0), true).canWrite)
    }
    @Test fun contradictoryBoundsRejectSmallValuesUntilChangedPublicBoundsAreExplicit() {
        val guard = PublicBrightnessScaleGuard()
        assertNull(guard.percent(127, resourceMin = 1, resourceMax = 4095))
        assertEquals(publicBrightnessPercent(127), guard.percent(127, resourceMin = 1, resourceMax = 255))
        assertNull(PublicBrightnessScaleGuard().percent(127, resourceMin = 200, resourceMax = 100))
    }
    @Test fun observedNonPublicScaleStaysRejectedForThisControllerLifetime() {
        val guard = PublicBrightnessScaleGuard()
        assertNotNull(guard.percent(127))
        guard.observeRaw(4095) // Also covers a non-public value seen only during write acknowledgement.
        assertNull(guard.percent(127))
        // No persistence: a separate controller/device does not inherit this evidence.
        assertNotNull(PublicBrightnessScaleGuard().percent(127, resourceMin = 10, resourceMax = 255))
    }
    @Test fun scaleContradictionRecoversOnlyAfterChangedExplicitPublicBounds() {
        val guard = PublicBrightnessScaleGuard()
        assertNull(guard.percent(127, resourceMin = 0, resourceMax = 4095))
        assertNull(guard.percent(127, resourceMin = 0, resourceMax = 4095))
        assertNull(guard.percent(127))
        assertEquals(publicBrightnessPercent(127), guard.percent(127, resourceMin = 0, resourceMax = 255))
        guard.observeRaw(1023)
        assertNull(guard.percent(127, resourceMin = 0, resourceMax = 255))
    }
    @Test fun validPercentMappingIsMonotoneAndKeepsThePublicMinimumAboveBlack() {
        assertEquals(1, publicBrightnessValue(-100))
        assertEquals(255, publicBrightnessValue(500))
        val values = (0..100).map(::publicBrightnessValue)
        assertTrue(values.zipWithNext().all { (a, b) -> b >= a && a in 1..255 && b in 1..255 })
        for (percent in 0..100) assertTrue(kotlin.math.abs(publicBrightnessPercent(publicBrightnessValue(percent))!! - percent) <= 1)
    }
}
