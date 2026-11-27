package com.example.globalminusone.regression

import com.example.globalminusone.data.TriggerPosition
import com.example.globalminusone.service.overlay.SidePullRecognition
import com.example.globalminusone.service.overlay.SidePullTriggerPolicy
import org.junit.Assert.*
import org.junit.Test

class SidePullTriggerPolicyTest {
    @Test fun physicalInsetKeepsEightDpGapAndTwentyFourDpMinimum() {
        assertEquals(24, SidePullTriggerPolicy.insetOffsetPx(0, 1f))
        assertEquals(38, SidePullTriggerPolicy.insetOffsetPx(30, 1f))
        assertEquals(48, SidePullTriggerPolicy.insetOffsetPx(0, 2f))
        assertEquals(112, SidePullTriggerPolicy.insetOffsetPx(96, 2f))
        assertEquals(30, SidePullTriggerPolicy.insetOffsetPx(20, 1.25f))
        assertEquals(32, SidePullTriggerPolicy.insetOffsetPx(23, 1.01f))
    }

    @Test fun leftRightAndBothAreSupportedButTopBottomAreNot() {
        assertTrue(SidePullTriggerPolicy.supports(TriggerPosition.LEFT))
        assertTrue(SidePullTriggerPolicy.supports(TriggerPosition.RIGHT))
        assertTrue(SidePullTriggerPolicy.supports(TriggerPosition.BOTH))
        assertFalse(SidePullTriggerPolicy.supports(TriggerPosition.TOP))
        assertFalse(SidePullTriggerPolicy.supports(TriggerPosition.BOTTOM))
    }

    @Test fun eachPhysicalSideUsesItsOwnSystemGestureInset() {
        assertEquals(56, SidePullTriggerPolicy.sideInsetOffsetPx(TriggerPosition.LEFT, 40, 80, 2f))
        assertEquals(96, SidePullTriggerPolicy.sideInsetOffsetPx(TriggerPosition.RIGHT, 40, 80, 2f))
        assertThrows(IllegalArgumentException::class.java) { SidePullTriggerPolicy.sideInsetOffsetPx(TriggerPosition.TOP, 40, 80, 2f) }
    }

    @Test fun predominantlyDownwardDistanceUsesDpThresholdForBothSides() {
        assertEquals(SidePullRecognition.WAITING, recognition(3f, 47f))
        assertEquals(SidePullRecognition.FOLLOW, recognition(3f, 48f))
        assertEquals(SidePullRecognition.FOLLOW, recognition(-3f, 48f))
        assertEquals(SidePullRecognition.WAITING, recognition(0f, 95f, density = 2f))
        assertEquals(SidePullRecognition.FOLLOW, recognition(0f, 96f, density = 2f))
    }

    @Test fun horizontalAndUpwardMovementCancelRatherThanOpeningThePanel() {
        assertEquals(SidePullRecognition.CANCEL, recognition(80f, 8f))
        assertEquals(SidePullRecognition.CANCEL, recognition(-80f, 8f))
        assertEquals(SidePullRecognition.CANCEL, recognition(2f, -60f))
        assertEquals(SidePullRecognition.CANCEL, recognition(60f, 65f))
    }

    @Test fun smallJitterWaitsAndReturningBelowThresholdCannotCommit() {
        assertEquals(SidePullRecognition.WAITING, recognition(1f, -2f))
        assertEquals(SidePullRecognition.FOLLOW, recognition(0f, 80f))
        assertEquals(SidePullRecognition.WAITING, recognition(0f, 20f))
        assertEquals(SidePullRecognition.CANCEL, recognition(Float.NaN, 80f))
    }

    @Test fun followUsesVerticalDistanceWithoutRequiringAnInwardHorizontalDrag() {
        assertTrue(SidePullTriggerPolicy.followProgress(80f, 1000) > 0f)
        assertEquals(0f, SidePullTriggerPolicy.followProgress(-80f, 1000), .0001f)
        assertEquals(1f, SidePullTriggerPolicy.followProgress(1000f, 1000), .0001f)
        assertTrue(SidePullTriggerPolicy.followProgress(80f, 1000) > SidePullTriggerPolicy.followProgress(20f, 1000))
    }

    private fun recognition(dx: Float, dy: Float, density: Float = 1f) =
        SidePullTriggerPolicy.sidePullRecognition(dx, dy, 8f, 48, density)
}
