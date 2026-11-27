package com.example.globalminusone

import com.example.globalminusone.service.overlay.TriggerGesturePolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerGesturePolicyTest {
    @Test
    fun swipeThreshold_usesConfiguredDpAndDensity() {
        assertFalse(TriggerGesturePolicy.reachesSwipeDistance(95f, 48, 2f))
        assertTrue(TriggerGesturePolicy.reachesSwipeDistance(96f, 48, 2f))
        assertFalse(TriggerGesturePolicy.reachesSwipeDistance(96f, 160, 2f))
        assertTrue(TriggerGesturePolicy.reachesSwipeDistance(320f, 160, 2f))
        // Outward distance cannot commit an inward panel drag.
        assertFalse(TriggerGesturePolicy.reachesSwipeDistance(-320f, 160, 2f))
    }

    @Test
    fun movementBelowConfiguredThreshold_isStillNotATap() {
        assertFalse(TriggerGesturePolicy.reachesSwipeDistance(40f, 160, 1f))
        assertFalse(TriggerGesturePolicy.isTap(true, 40f, 8f))
        // Returning to the origin after moving must not activate a tap action either.
        assertFalse(TriggerGesturePolicy.isTap(true, 0f, 8f))
        assertTrue(TriggerGesturePolicy.isTap(false, 4f, 8f))
        assertFalse(TriggerGesturePolicy.isTap(false, 9f, 8f))
    }
}
