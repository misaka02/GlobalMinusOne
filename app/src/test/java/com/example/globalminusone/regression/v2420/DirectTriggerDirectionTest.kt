package com.example.globalminusone.regression.v2420

import com.example.globalminusone.service.TriggerGesturePolicy
import com.example.globalminusone.service.TriggerSwipeDirection
import org.junit.Assert.*
import org.junit.Test

class DirectTriggerDirectionTest {
    @Test fun configuredVerticalOpeningUsesVerticalMotionOnASideBar() {
        val progress = TriggerGesturePolicy.followProgress(TriggerSwipeDirection.DOWN,
            dx = 0f, dy = 350f, panelWidth = 600f, screenHeight = 1000f)
        assertEquals(.5f, progress, .0001f)
        assertTrue(TriggerGesturePolicy.reachesSwipeDistance(
            TriggerGesturePolicy.projectedDistance(TriggerSwipeDirection.DOWN, 0f, 350f), 48, 2f))
        assertEquals(.5f, TriggerGesturePolicy.followProgress(TriggerSwipeDirection.UP,
            0f, -350f, 600f, 1000f), .0001f)
    }

    @Test fun horizontalOpeningAndReturningPastOriginUseTheRecognizedDirection() {
        assertEquals(.5f, TriggerGesturePolicy.followProgress(TriggerSwipeDirection.LEFT,
            -300f, 0f, 600f, 1000f), .0001f)
        assertEquals(.5f, TriggerGesturePolicy.followProgress(TriggerSwipeDirection.RIGHT,
            300f, 0f, 600f, 1000f), .0001f)
        assertEquals(0f, TriggerGesturePolicy.followProgress(TriggerSwipeDirection.RIGHT,
            -30f, 400f, 600f, 1000f), 0f)
        assertFalse(TriggerGesturePolicy.reachesSwipeDistance(
            TriggerGesturePolicy.projectedDistance(TriggerSwipeDirection.RIGHT, -30f, 400f), 48, 2f))
    }
}
