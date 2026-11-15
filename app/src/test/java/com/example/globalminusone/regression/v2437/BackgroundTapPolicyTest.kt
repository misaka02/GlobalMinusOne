package com.example.globalminusone.regression.v2437

import com.example.globalminusone.ui.panel.BackgroundTapPolicy
import org.junit.Assert.*
import org.junit.Test

class BackgroundTapPolicyTest {
    private fun tap() = BackgroundTapPolicy(470f, 400f, 100L, 8f, 500L)

    @Test fun aShortSinglePointerTapAllowsOnlySmallSensorJitter() {
        val gesture = tap()
        gesture.observe(472f, 401f, 120, 1)
        gesture.observe(471f, 400f, 180, 1)
        assertFalse(gesture.cancelled)
    }

    @Test fun backgroundDragCancelsWithoutAnyScrollableConsumingTheMotion() {
        val gesture = tap()
        gesture.observe(450f, 400f, 130, 1, consumed = false)
        assertTrue(gesture.cancelled)
    }

    @Test fun aSwipeReturningToItsDownPositionCanNeverBecomeATapOnUp() {
        val gesture = tap()
        gesture.observe(400f, 400f, 130, 1)
        gesture.observe(470f, 400f, 200, 1)
        assertTrue(gesture.cancelled)
    }

    @Test fun anUpOnlyPositionJumpAlsoCancelsWhenNoMoveWasDelivered() {
        val gesture = tap()
        gesture.observe(350f, 400f, 150, 1)
        assertTrue(gesture.cancelled)
    }

    @Test fun accumulatedWigglingCannotHideBehindSmallFinalDisplacement() {
        val gesture = tap()
        gesture.observe(474f, 400f, 120, 1)
        gesture.observe(470f, 400f, 140, 1)
        gesture.observe(474f, 400f, 160, 1)
        assertTrue(gesture.cancelled)
    }

    @Test fun aSecondPointerOrConsumedGestureCancelsPermanently() {
        val second = tap()
        second.observe(470f, 400f, 120, 2)
        second.observe(470f, 400f, 140, 1)
        assertTrue(second.cancelled)
        val consumed = tap()
        consumed.observe(470f, 400f, 120, 1, consumed = true)
        assertTrue(consumed.cancelled)
    }

    @Test fun leavingTheWindowOrCancellingDoesNotRearmOnReturn() {
        val outside = tap()
        outside.observe(470f, 400f, 120, 1, inBounds = false)
        outside.observe(470f, 400f, 140, 1)
        assertTrue(outside.cancelled)
        val cancelled = tap()
        cancelled.cancel()
        cancelled.observe(470f, 400f, 140, 1)
        assertTrue(cancelled.cancelled)
    }

    @Test fun aLongPressOrInvalidPointerDataCannotTriggerBackgroundClose() {
        val held = tap()
        held.observe(470f, 400f, 600, 1)
        assertTrue(held.cancelled)
        val invalid = tap()
        invalid.observe(Float.NaN, 400f, 120, 1)
        assertTrue(invalid.cancelled)
    }
}
