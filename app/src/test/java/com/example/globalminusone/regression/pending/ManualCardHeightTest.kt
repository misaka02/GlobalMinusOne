package com.example.globalminusone.regression.pending

import com.example.globalminusone.data.*
import org.junit.Assert.*
import org.junit.Test

class ManualCardHeightTest {
    @Test fun validManualDrawerHeightSurvivesBeyondTheAutomaticViewportLimit() {
        val drawer = ComponentItem(type = ComponentType.APP_DRAWER_CARD, w = 32, h = 80, autoHeight = false)
        assertEquals(800f, componentDisplayHeightDp(drawer, 320f, 32), .01f)
        assertEquals(80, componentDisplayRows(drawer, 320f, 10f, 32))
        assertEquals(400f, componentDisplayHeightDp(drawer.copy(h = 40), 320f, 32), .01f)
    }

    @Test fun nestedManualCardsHaveTheirOwnHeightWhileTheAutoContainerStaysBounded() {
        val note = ComponentItem(type = ComponentType.NOTE_CARD, w = 32, h = 80, autoHeight = false)
        val container = ComponentItem(type = ComponentType.CONTAINER_BOX, w = 32, showTitle = false,
            containerComponents = listOf(note), autoHeight = true)
        assertEquals(800f, componentDisplayHeightDp(note, 320f, 32), .01f)
        assertTrue(componentDisplayHeightDp(container, 320f, 32) <= CONTAINER_VIEWPORT_MAX_DP + 24f)
        val arranged = arrangeContainerChildren(listOf(note, note.copy(id = "next")), 32, 320f, 10f)
        assertEquals(80, arranged.first().h)
        assertEquals(81, arranged.last().y)
        assertEquals(80, note.h)
    }

    @Test fun automaticOrInvalidLegacyDrawersStillHaveAFiniteViewport() {
        val drawer = ComponentItem(type = ComponentType.APP_DRAWER_CARD, w = 32, h = 10000)
        assertEquals(CONTAINER_VIEWPORT_MAX_DP, componentDisplayHeightDp(drawer, 320f, 32), .01f)
        assertEquals(CONTAINER_VIEWPORT_MAX_DP, componentDisplayHeightDp(drawer.copy(h = 80, autoHeight = true), 320f, 32), .01f)
        assertEquals(10000, drawer.h)
    }
}
