package com.example.globalminusone.ui.panel

import org.junit.Assert.*
import org.junit.Test
import com.example.globalminusone.ui.panel.gesture.PanelGestureAxis
import com.example.globalminusone.ui.panel.gesture.PanelGestureOwnership

class PanelGestureOwnershipTest {
    @Test fun nestedCardKeepsOwnershipUntilAllOwnersRelease() {
        val ownership = PanelGestureOwnership()
        val card = Any()
        val drawer = Any()
        ownership.acquire(card)
        ownership.acquire(drawer)
        ownership.release(card)
        assertTrue(ownership.active)
        ownership.release(drawer)
        assertFalse(ownership.active)
    }

    @Test fun disposingAnUnrelatedCardDoesNotUnlockTheCurrentGesture() {
        val ownership = PanelGestureOwnership()
        val active = Any()
        ownership.acquire(active)
        ownership.release(Any())
        assertTrue(ownership.active)
        ownership.release(active)
        ownership.release(active)
        assertFalse(ownership.active)
    }

    @Test fun verticalDrawerClaimBlocksPageScrollButLeavesHorizontalPagingAvailable() {
        val ownership = PanelGestureOwnership()
        val drawer = Any()
        ownership.acquire(drawer, PanelGestureAxis.Vertical, blocksDescendants = false)
        assertTrue(ownership.active)
        assertTrue(ownership.verticalActive)
        assertFalse(ownership.horizontalActive)
        ownership.release(drawer)
        assertFalse(ownership.verticalActive)
        assertFalse(ownership.active)
    }

    @Test fun horizontalSliderClaimLeavesVerticalPageScrollAvailable() {
        val ownership = PanelGestureOwnership()
        val slider = Any()
        ownership.acquire(slider, PanelGestureAxis.Horizontal)
        assertTrue(ownership.horizontalActive)
        assertFalse(ownership.verticalActive)
        assertFalse(ownership.descendantVerticalBlocked)
        ownership.release(slider)
        assertFalse(ownership.horizontalActive)
        assertFalse(ownership.active)
    }

    @Test fun releasingOneAxisOrAnUnrelatedOwnerPreservesTheOtherGesture() {
        val ownership = PanelGestureOwnership()
        val vertical = Any()
        val horizontal = Any()
        ownership.acquire(vertical, PanelGestureAxis.Vertical)
        ownership.acquire(horizontal, PanelGestureAxis.Horizontal)
        assertTrue(ownership.verticalActive)
        assertTrue(ownership.horizontalActive)
        ownership.release(Any())
        ownership.release(horizontal)
        assertTrue(ownership.verticalActive)
        assertTrue(ownership.descendantVerticalBlocked)
        assertFalse(ownership.horizontalActive)
        ownership.release(horizontal)
        assertTrue(ownership.verticalActive)
        ownership.release(vertical)
        assertFalse(ownership.active)
    }

    @Test fun viewportScrollClaimProtectsItFromAncestorsWithoutDisablingItself() {
        val ownership = PanelGestureOwnership()
        val viewport = Any()
        ownership.acquire(viewport, PanelGestureAxis.Vertical, blocksDescendants = false)
        assertTrue(ownership.verticalActive)
        assertFalse(ownership.descendantVerticalBlocked)
        // Updating a viewport's claim must not turn it into an exclusive child gesture.
        ownership.acquire(viewport, PanelGestureAxis.Vertical, blocksDescendants = false)
        assertFalse(ownership.descendantVerticalBlocked)
        ownership.release(viewport)
        assertFalse(ownership.verticalActive)
    }

    @Test fun cancellingExclusiveChildRestoresNestedViewportWithoutDroppingItsClaim() {
        val ownership = PanelGestureOwnership()
        val viewport = Any()
        val child = Any()
        ownership.acquire(viewport, PanelGestureAxis.Vertical, blocksDescendants = false)
        ownership.acquire(child, PanelGestureAxis.Both)
        assertTrue(ownership.descendantVerticalBlocked)
        assertTrue(ownership.horizontalActive)
        ownership.release(child) // Same release used by gesture cancellation/finally.
        assertFalse(ownership.descendantVerticalBlocked)
        assertFalse(ownership.horizontalActive)
        assertTrue(ownership.verticalActive)
        ownership.release(viewport)
        assertFalse(ownership.active)
    }
}
