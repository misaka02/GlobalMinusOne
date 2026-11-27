package com.example.globalminusone.regression.v2410

import com.example.globalminusone.ui.panel.file.workspaceWindowBounds
import org.junit.Assert.*
import org.junit.Test

class WorkspaceWindowBoundsTest {
    @Test fun workspaceCanMoveBeyondTheFormerSidePanel() {
        val window = workspaceWindowBounds(1080f, 1920f, 1920f, 220f, 360f, .25f, .4f, .7f, .5f)
        assertTrue(window.x > 1080f * .6f)
        assertTrue(window.x + window.width <= 1080f)
        assertEquals(960f, window.y, .01f)
    }
    @Test fun draggingToScreenEdgesKeepsTheWholeWindowReachable() {
        val window = workspaceWindowBounds(1080f, 1920f, 1920f, 220f, 360f, .5f, .4f, 2f, -1f)
        assertEquals(1080f, window.x + window.width, .01f)
        assertEquals(0f, window.y, .01f)
    }
    @Test fun keyboardAvoidanceChangesTheDisplayedRectangleWithoutChangingSavedFractions() {
        val normal = workspaceWindowBounds(1080f, 1920f, 1920f, 220f, 360f, .9f, .8f, .05f, .1f)
        val keyboard = workspaceWindowBounds(1080f, 1920f, 800f, 220f, 360f, .9f, .8f, .05f, .1f)
        assertTrue(normal.height > keyboard.height)
        assertEquals(800f, keyboard.y + keyboard.height, .01f)
        assertEquals(normal.width, keyboard.width, .01f)
    }
    @Test fun invalidImportedFractionsDoNotCreateAnUnreachableWindow() {
        val window = workspaceWindowBounds(320f, 480f, 480f, 220f, 360f,
            Float.NaN, Float.POSITIVE_INFINITY, Float.NaN, Float.NEGATIVE_INFINITY)
        assertTrue(window.width.isFinite() && window.height.isFinite() && window.x.isFinite() && window.y.isFinite())
        assertTrue(window.x >= 0f && window.x + window.width <= 320f)
        assertTrue(window.y >= 0f && window.y + window.height <= 480f)
    }
}
