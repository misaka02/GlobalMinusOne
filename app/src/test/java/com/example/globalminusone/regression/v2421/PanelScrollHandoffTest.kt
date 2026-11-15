package com.example.globalminusone.regression.v2421

import com.example.globalminusone.ui.panel.PanelGestureAxis
import com.example.globalminusone.ui.panel.PanelGestureOwnership
import com.example.globalminusone.ui.panel.StationaryHoldPolicy
import com.example.globalminusone.ui.panel.movementForHold
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import org.junit.Assert.*
import org.junit.Test

class PanelScrollHandoffTest {
    @Test fun consumedMotionCancelsHoldAndAbsoluteStartingPositionDoesNotChangeItsDistance() {
        listOf(10f, 300f, 1500f).forEach { start ->
            val change = PointerInputChange(PointerId(1L), 10L, Offset(30f, start - 20f), true,
                0L, Offset(30f, start), true, isInitiallyConsumed = false)
            change.consume()
            assertEquals(Offset(0f, -20f), change.movementForHold())
            val hold = StationaryHoldPolicy(3f, 12f)
            val delta = change.movementForHold()
            assertFalse(hold.observe(delta.x, delta.y, 1, true, change.isConsumed))
        }
    }

    @Test fun nestedViewportCanReachItsBoundaryWithoutRestartingParentScrollRecognizer() {
        val owners = PanelGestureOwnership()
        val drawer = Any()
        assertFalse(owners.exclusiveVerticalActive)
        owners.acquire(drawer, PanelGestureAxis.Vertical, blocksDescendants = false)
        assertTrue(owners.verticalActive)
        assertFalse(owners.exclusiveVerticalActive)
        assertFalse(owners.descendantVerticalBlocked)
        owners.release(drawer)
        assertFalse(owners.exclusiveVerticalActive)
        owners.acquire(drawer, PanelGestureAxis.Vertical, blocksDescendants = false)
        assertFalse(owners.exclusiveVerticalActive)
        owners.release(drawer)
    }

    @Test fun holdingSliderCannotEnterEditingButVerticalPageScrollingRemainsAvailable() {
        val owners = PanelGestureOwnership()
        val slider = Any()
        val card = Any()
        owners.acquire(slider, PanelGestureAxis.Horizontal)
        assertTrue(owners.exclusiveHorizontalActive)
        assertFalse(owners.exclusiveVerticalActive)
        assertFalse(owners.claimEditHold(1L, card))
        owners.release(slider)
        assertTrue(owners.claimEditHold(2L, card))
        owners.releaseHold(card)
    }

    @Test fun explicitDragTemporarilyBlocksBothAxesWithoutDestroyingNestedViewportClaim() {
        val owners = PanelGestureOwnership()
        val viewport = Any()
        val dragged = Any()
        owners.acquire(viewport, PanelGestureAxis.Vertical, blocksDescendants = false)
        owners.acquire(dragged)
        assertTrue(owners.exclusiveVerticalActive)
        assertTrue(owners.exclusiveHorizontalActive)
        owners.release(dragged)
        assertFalse(owners.exclusiveVerticalActive)
        assertFalse(owners.exclusiveHorizontalActive)
        assertTrue(owners.verticalActive)
        owners.release(viewport)
        assertFalse(owners.active)
    }
}
