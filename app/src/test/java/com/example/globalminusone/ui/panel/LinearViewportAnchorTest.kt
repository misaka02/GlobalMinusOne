package com.example.globalminusone.regression

import com.example.globalminusone.ui.panel.layout.LinearViewportAnchor
import com.example.globalminusone.ui.panel.layout.LinearCardSpan
import com.example.globalminusone.ui.panel.layout.anchorFromLazyPosition
import com.example.globalminusone.ui.panel.layout.anchorAtLinearScroll
import com.example.globalminusone.ui.panel.layout.lazyPositionForAnchor
import com.example.globalminusone.ui.panel.layout.linearScrollForAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinearViewportAnchorTest {
    private val ids = listOf("a", "b", "c")
    private val spans = listOf(
        LinearCardSpan("a", startPx = 0, heightPx = 100),
        LinearCardSpan("b", startPx = 112, heightPx = 70),
        LinearCardSpan("c", startPx = 190, heightPx = 90)
    )

    @Test
    fun anchorRoundTripsBetweenLazyAndEditingScrollCoordinates() {
        val anchor = checkNotNull(anchorFromLazyPosition(ids, index = 1, offsetPx = 24))

        assertEquals(LinearViewportAnchor("b", offsetPx = 24, fallbackIndex = 1), anchor)
        assertEquals(136, linearScrollForAnchor(anchor, spans))
        assertEquals(LinearViewportAnchor("b", offsetPx = 24, fallbackIndex = 1), anchorAtLinearScroll(spans, 136))
        assertEquals(1 to 24, lazyPositionForAnchor(anchor, ids))
    }

    @Test
    fun anchorAtGapUsesNextCardAndRoundTripsToItsStart() {
        val anchor = checkNotNull(anchorAtLinearScroll(spans, scrollPx = 105))

        assertEquals(LinearViewportAnchor("b", offsetPx = 0, fallbackIndex = 1), anchor)
        assertEquals(112, linearScrollForAnchor(anchor, spans))
    }

    @Test
    fun removedCardFallsBackToNearestAvailableIndexInBothLayouts() {
        val removed = LinearViewportAnchor("b", offsetPx = 30, fallbackIndex = 1)
        val remainingIds = listOf("a", "c")
        val remainingSpans = listOf(
            LinearCardSpan("a", startPx = 0, heightPx = 40),
            LinearCardSpan("c", startPx = 50, heightPx = 60)
        )

        assertEquals(1 to 30, lazyPositionForAnchor(removed, remainingIds))
        assertEquals(80, linearScrollForAnchor(removed, remainingSpans))
        assertEquals(LinearViewportAnchor("c", offsetPx = 30, fallbackIndex = 1), anchorAtLinearScroll(remainingSpans, 80))
    }

    @Test
    fun emptyLayoutsHaveNoAnchorOrRestorePosition() {
        val anchor = LinearViewportAnchor("gone", offsetPx = 10, fallbackIndex = 0)
        assertNull(anchorFromLazyPosition(emptyList(), 0, 0))
        assertNull(lazyPositionForAnchor(anchor, emptyList()))
        assertNull(anchorAtLinearScroll(emptyList(), 0))
        assertNull(linearScrollForAnchor(anchor, emptyList()))
    }
}
