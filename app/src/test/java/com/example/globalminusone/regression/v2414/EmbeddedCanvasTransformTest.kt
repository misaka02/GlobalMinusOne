package com.example.globalminusone.regression.v2414

import com.example.globalminusone.browser.EmbeddedCanvasTransform
import org.junit.Assert.*
import org.junit.Test

class EmbeddedCanvasTransformTest {
    @Test fun resizingHostKeepsCanvasAspectAndFitsWithoutCrop() {
        for ((width, height) in listOf(720 to 480, 240 to 640, 360 to 640, 1000 to 1200)) {
            val fit = EmbeddedCanvasTransform.fit(width, height, 360, 640)
            val actualWidth = width * fit.scaleX
            val actualHeight = height * fit.scaleY
            assertTrue(actualWidth <= width + .001f)
            assertTrue(actualHeight <= height + .001f)
            assertEquals(360f / 640, actualWidth / actualHeight, .0001f)
            assertTrue(kotlin.math.abs(actualWidth - width) < .01f || kotlin.math.abs(actualHeight - height) < .01f)
        }
    }

    @Test fun displayedCenterAndEdgesMapBackToSameRemotePixelsAcrossHostResize() {
        val canvasWidth = 360; val canvasHeight = 640
        for ((width, height) in listOf(720 to 480, 240 to 640, 360 to 640)) {
            val fit = EmbeddedCanvasTransform.fit(width, height, canvasWidth, canvasHeight)
            for (fraction in listOf(0f, .25f, .5f, 1f)) {
                // Only the texture is transformed. Native MotionEvents keep these
                // host coordinates, including the letterbox offset.
                val screenX = width / 2f + (width * fraction - width / 2f) * fit.scaleX
                val screenY = height / 2f + (height * fraction - height / 2f) * fit.scaleY
                assertEquals(canvasWidth * fraction, fit.mapHostX(screenX, width, canvasWidth), .001f)
                assertEquals(canvasHeight * fraction, fit.mapHostY(screenY, height, canvasHeight), .001f)
            }
        }
    }

    @Test fun letterboxDoesNotSendEdgeButtonTouchesToRemoteApp() {
        val fit = EmbeddedCanvasTransform.fit(720, 480, 360, 640)
        assertFalse(fit.contains(0f, 240f, 720, 480))
        assertFalse(fit.contains(719f, 240f, 720, 480))
        assertTrue(fit.contains(360f, 240f, 720, 480))
        assertEquals(0f, fit.mapHostX(-20f, 720, 360), 0f)
        assertEquals(360f, fit.mapHostX(740f, 720, 360), 0f)
    }

    @Test fun pointerOvershootIsClampedAtRemoteCanvasBoundary() {
        assertEquals(0f, EmbeddedCanvasTransform.mapLocal(-12f, 200, 360), 0f)
        assertEquals(360f, EmbeddedCanvasTransform.mapLocal(215f, 200, 360), 0f)
    }
}
