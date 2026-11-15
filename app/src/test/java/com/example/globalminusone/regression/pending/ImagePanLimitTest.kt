package com.example.globalminusone.regression.pending

import com.example.globalminusone.ui.panel.imagePanLimit
import org.junit.Assert.assertEquals
import org.junit.Test

class ImagePanLimitTest {
    @Test fun widePhotoDoesNotDragIntoItsLetterboxing() {
        val limit = imagePanLimit(300, 400, 1600, 900, 2f)
        assertEquals(150f, limit.x, .01f)
        assertEquals(0f, limit.y, .01f)
    }
    @Test fun portraitPhotoAndWindowResizeUseActualImageExtent() {
        val portrait = imagePanLimit(300, 400, 900, 1600, 2f)
        assertEquals(75f, portrait.x, .01f)
        assertEquals(200f, portrait.y, .01f)
        val resized = imagePanLimit(600, 400, 900, 1600, 2f)
        assertEquals(0f, resized.x, .01f)
        assertEquals(200f, resized.y, .01f)
    }
    @Test fun fittedOrUnmeasuredImageStaysCentered() {
        assertEquals(0f, imagePanLimit(300, 400, 1600, 900, 1f).x, 0f)
        assertEquals(0f, imagePanLimit(300, 400, 1600, 900, 1f).y, 0f)
        assertEquals(0f, imagePanLimit(0, 400, 1600, 900, 2f).x, 0f)
    }
}
