package com.example.globalminusone.ui.panel

import org.junit.Assert.*
import org.junit.Test

class FolderHoldIntentTest {
    @Test fun stationaryHoldKeepsMenuIntentUntilRealMovement() {
        val intent = HeldDragIntent(8f)
        assertFalse(intent.move(1f, -1f))
        assertFalse(intent.move(-1f, 1f))
        assertFalse(intent.move(4f, 4f))
        assertTrue(intent.move(3f, 3f))
        assertEquals(7f, intent.x, 0f)
        assertEquals(7f, intent.y, 0f)
    }

    @Test fun reverseOrInvalidSensorDeltaDoesNotBecomeAFalseDrag() {
        val intent = HeldDragIntent(8f)
        assertFalse(intent.move(Float.NaN, 200f))
        assertFalse(intent.move(-4f, 0f))
        assertFalse(intent.move(4f, 0f))
        assertTrue(intent.move(0f, -9f))
    }
}
