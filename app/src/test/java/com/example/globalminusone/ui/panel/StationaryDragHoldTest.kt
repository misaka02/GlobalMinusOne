package com.example.globalminusone.ui.panel

import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test
import com.example.globalminusone.ui.panel.gesture.PanelGestureAxis
import com.example.globalminusone.ui.panel.gesture.PanelGestureOwnership
import com.example.globalminusone.ui.panel.gesture.StationaryHoldPolicy
import com.example.globalminusone.ui.panel.editor.withStationaryDragHold

class StationaryDragHoldTest {
    @Test fun downAndWaitingReserveTheHoldWithoutBlockingEitherScrollAxis() {
        val ownership = PanelGestureOwnership()
        val background = Any()
        val result = withStationaryDragHold(ownership, 100L) {
            assertFalse(ownership.active)
            assertFalse(ownership.verticalActive)
            assertFalse(ownership.horizontalActive)
            assertFalse(ownership.claimHold(100L, background))
            "tap before hold completes"
        }
        assertEquals("tap before hold completes", result)
        assertFalse(ownership.active)
        assertTrue(ownership.claimHold(101L, background))
        ownership.releaseHold(background)
    }

    @Test fun completedHoldAcquiresBothAxesBeforeTheDragCallbackAndReleasesOnEnd() {
        val ownership = PanelGestureOwnership()
        withStationaryDragHold(ownership, 100L) { confirmHold ->
            assertFalse(ownership.active)
            confirmHold()
            // These are the conditions seen by onDragStart in the actual detector.
            assertTrue(ownership.horizontalActive)
            assertTrue(ownership.verticalActive)
            assertTrue(ownership.descendantVerticalBlocked)
        }
        assertFalse(ownership.horizontalActive)
        assertFalse(ownership.verticalActive)
        assertFalse(ownership.descendantVerticalBlocked)
    }

    @Test fun consumedMovementCancelsTheWaitingHoldWithoutEverLockingScroll() {
        val ownership = PanelGestureOwnership()
        withStationaryDragHold(ownership, 100L) {
            val policy = StationaryHoldPolicy(1f, 2f)
            // Even movement within the jitter budget must yield to an active scroll.
            assertFalse(policy.observe(.1f, 0f, 1, true, consumedMovement = true))
            assertTrue(policy.cancelled)
            assertFalse(ownership.active)
        }
        assertFalse(ownership.active)
    }

    @Test fun cancellationAndCallbackExceptionReleaseTheDragButPreserveAnotherOwner() {
        for (failure in listOf(CancellationException("pointer input cancelled"), IllegalStateException("drag callback failed"))) {
            val ownership = PanelGestureOwnership()
            val viewport = Any()
            ownership.acquire(viewport, PanelGestureAxis.Vertical, blocksDescendants = false)
            try {
                withStationaryDragHold(ownership, 100L) { confirmHold ->
                    confirmHold()
                    assertTrue(ownership.horizontalActive)
                    assertTrue(ownership.descendantVerticalBlocked)
                    throw failure
                }
                fail("Expected the cancellation or callback exception to propagate")
            } catch (actual: RuntimeException) {
                assertSame(failure, actual)
            }
            assertFalse(ownership.horizontalActive)
            assertFalse(ownership.descendantVerticalBlocked)
            assertTrue(ownership.verticalActive)
            ownership.release(viewport)
            assertFalse(ownership.active)
        }
    }
}
