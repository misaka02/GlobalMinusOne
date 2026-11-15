package com.example.globalminusone.regression.v2420

import com.example.globalminusone.browser.EmbeddedAppViewportPolicy
import com.example.globalminusone.browser.EmbeddedCanvasTransform
import org.junit.Assert.*
import org.junit.Test

class EmbeddedAppViewportPolicyTest {
    @Test fun phoneLayoutKeepsNativeHeightAndDpiInsteadOfCompactFirstHostHeight() {
        val viewport = EmbeddedAppViewportPolicy.create(1080, 2400, 480)
        assertEquals(1080, viewport.width)
        assertEquals(2400, viewport.height)
        assertEquals(480, viewport.densityDpi)
        assertEquals(800f, viewport.height * 160f / viewport.densityDpi, .001f)
        // QQ's Activity and cached primary-display metrics no longer differ by
        // the compact window's height/density on this ordinary phone geometry.
        val highResolution = EmbeddedAppViewportPolicy.create(1440, 3200, 640)
        assertEquals(1440, highResolution.width)
        assertEquals(3200, highResolution.height)
        assertEquals(640, highResolution.densityDpi)
    }

    @Test fun landscapeUsesTheSameLogicalCanvasWithAxesSwapped() {
        val portrait = EmbeddedAppViewportPolicy.create(1080, 2400, 480)
        val landscape = EmbeddedAppViewportPolicy.create(2400, 1080, 480)
        assertEquals(portrait.width, landscape.height)
        assertEquals(portrait.height, landscape.width)
        assertEquals(portrait.densityDpi, landscape.densityDpi)
    }

    @Test fun oversizedPhoneReducesRasterDensityWithoutShorteningLogicalContent() {
        val viewport = EmbeddedAppViewportPolicy.create(2160, 4800, 640)
        assertTrue(viewport.width <= 4096 && viewport.height <= 4096)
        assertTrue(viewport.width.toLong() * viewport.height <= 8_388_608L)
        assertEquals(540f, viewport.width * 160f / viewport.densityDpi, 2f)
        assertEquals(1200f, viewport.height * 160f / viewport.densityDpi, 2f)
    }

    @Test fun compactAndFreeAspectHostsKeepUniformScaleForTopMiddleAndBottom() {
        val viewport = EmbeddedAppViewportPolicy.create(1080, 2400, 480)
        for ((hostWidth, hostHeight) in listOf(652 to 1100, 420 to 470, 900 to 500, 360 to 1200)) {
            val fit = EmbeddedCanvasTransform.fit(hostWidth, hostHeight, viewport.width, viewport.height)
            val shownWidth = hostWidth * fit.scaleX
            val shownHeight = hostHeight * fit.scaleY
            val left = (hostWidth - shownWidth) / 2
            val top = (hostHeight - shownHeight) / 2
            assertTrue(shownWidth <= hostWidth + .001f)
            assertTrue(shownHeight <= hostHeight + .001f)
            assertEquals(shownWidth / viewport.width, shownHeight / viewport.height, .0001f)
            // Verify interior rows as well as endpoints; the middle receives the
            // same scale as the chrome, and maps to the same remote pixel rows.
            for (row in listOf(0f, 56f, 700f, 1200f, 1800f, 2344f, 2400f)) {
                val hostY = top + row / viewport.height * shownHeight
                assertEquals(row, fit.mapHostY(hostY, hostHeight, viewport.height), .002f)
            }
            assertEquals(540f, fit.mapHostX(left + shownWidth / 2, hostWidth, viewport.width), .002f)
        }
    }
}
