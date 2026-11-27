package com.example.globalminusone.regression.v2428

import com.example.globalminusone.ui.panel.file.FLOATING_WINDOW_MIN_HEIGHT_DP
import com.example.globalminusone.ui.panel.file.floatingWindowPresentationBounds
import com.example.globalminusone.ui.panel.file.workspaceWindowBounds
import org.junit.Assert.*
import org.junit.Test

class FloatingWindowPresentationTest {
    @Test fun collapseExposesSmallScreenWithoutOverwritingExpandedGeometry() {
        val expanded = workspaceWindowBounds(320f, 569f, 569f, 220f, FLOATING_WINDOW_MIN_HEIGHT_DP.toFloat(), .9f, .55f, .05f, .18f)
        val collapsed = floatingWindowPresentationBounds(expanded, 569f, 569f, 48f, .18f, false)
        assertEquals(48f, collapsed.height, .001f)
        assertEquals(expanded.width, collapsed.width, 0f)
        assertEquals(expanded.x, collapsed.x, 0f)
        assertEquals(expanded.y, collapsed.y, .001f)
        assertEquals(312.95f, expanded.height, .001f)
        assertEquals(expanded, floatingWindowPresentationBounds(expanded, 569f, 569f, 48f, .18f, true))
    }

    @Test fun compactExpandedWindowCanResizeBelowFormer360DpFloor() {
        val window = workspaceWindowBounds(320f, 569f, 569f, 220f, FLOATING_WINDOW_MIN_HEIGHT_DP.toFloat(), .9f, .1f, .05f, .18f)
        assertEquals(240f, window.height, 0f)
    }

    @Test fun collapsedTitleCanReachBottomAndExpansionRemainsOnscreen() {
        val expanded = workspaceWindowBounds(320f, 569f, 569f, 220f, FLOATING_WINDOW_MIN_HEIGHT_DP.toFloat(), .9f, .55f, .05f, 1f)
        val collapsed = floatingWindowPresentationBounds(expanded, 569f, 569f, 48f, 1f, false)
        assertEquals(569f, collapsed.y + collapsed.height, .001f)
        assertTrue(expanded.y + expanded.height <= 569f)
    }

    @Test fun temporaryImeOcclusionKeepsCollapsedTitleReachable() {
        val expanded = workspaceWindowBounds(320f, 569f, 100f, 220f, FLOATING_WINDOW_MIN_HEIGHT_DP.toFloat(), .9f, .55f, .05f, .8f)
        val collapsed = floatingWindowPresentationBounds(expanded, 569f, 100f, 48f, .8f, false)
        assertEquals(100f, collapsed.y + collapsed.height, .001f)
        val tiny = floatingWindowPresentationBounds(expanded, 569f, 20f, 48f, .8f, false)
        assertEquals(20f, tiny.height, 0f)
        assertEquals(0f, tiny.y, 0f)
    }

    @Test fun invalidPresentationInputsCannotLoseTheTitle() {
        val expanded = workspaceWindowBounds(320f, 569f, 569f, 220f, FLOATING_WINDOW_MIN_HEIGHT_DP.toFloat(), .9f, .55f, .05f, .18f)
        val window = floatingWindowPresentationBounds(expanded, Float.NaN, Float.POSITIVE_INFINITY,
            Float.NaN, Float.NEGATIVE_INFINITY, false)
        assertTrue(window.height.isFinite() && window.y.isFinite())
        assertTrue(window.y >= 0f && window.height >= 1f)
    }
}
