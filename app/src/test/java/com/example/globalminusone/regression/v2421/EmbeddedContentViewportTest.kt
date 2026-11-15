package com.example.globalminusone.regression.v2421

import com.example.globalminusone.browser.EmbeddedCanvasTransform
import com.example.globalminusone.browser.EmbeddedContentViewport
import com.example.globalminusone.service.ApplicationContentViewportStore
import com.example.globalminusone.service.ApplicationWindowPlacement
import com.example.globalminusone.service.ApplicationWindowRect
import org.junit.Assert.*
import org.junit.Test

class EmbeddedContentViewportTest {
    @Test fun zoomAndPanHaveOneInverseForEveryPointerWithoutChangingCanvasOrHost() {
        for ((w, h) in listOf(500 to 1000, 900 to 500, 360 to 1200)) {
            for (zoom in listOf(1f, 1.25f, 1.5f, 2f)) {
                for (pan in listOf(-1f, 0f, 1f)) {
                    val transform = EmbeddedCanvasTransform.fit(w, h, 1080, 2400,
                        EmbeddedContentViewport(zoom, pan, -pan))
                    assertEquals(w * transform.scaleX / 1080, h * transform.scaleY / 2400, .0001f)
                    // Both pointer coordinates map through the identical matrix.
                    for ((x, y) in listOf(w * .4f to h * .4f, w * .6f to h * .6f)) {
                        val remoteX = transform.mapHostX(x, w, 1080)
                        val remoteY = transform.mapHostY(y, h, 2400)
                        if (transform.contains(x, y, w, h)) {
                            val shownX = (remoteX / 1080 - .5f) * w * transform.scaleX + w / 2f + transform.translationX
                            val shownY = (remoteY / 2400 - .5f) * h * transform.scaleY + h / 2f + transform.translationY
                            assertEquals(x, shownX, .002f)
                            assertEquals(y, shownY, .002f)
                        }
                    }
                    assertFalse(transform.contains(-1f, 10f, w, h))
                    assertFalse(transform.contains(w + 1f, 10f, w, h))
                }
            }
        }
    }

    @Test fun panningStopsAtCanvasEdgesAndCannotExposeExtraBlankSpace() {
        val state = EmbeddedContentViewport(2f).moved(10000f, -10000f, 500, 1000, 1080, 2400)
        assertEquals(1f, state.panX, 0f)
        assertEquals(-1f, state.panY, 0f)
        val transform = EmbeddedCanvasTransform.fit(500, 1000, 1080, 2400, state)
        assertEquals(0f, 500 * (1 - transform.scaleX) / 2 + transform.translationX, .001f)
        assertEquals(1000f, 1000 * (1 + transform.scaleY) / 2 + transform.translationY, .001f)
        val letterbox = EmbeddedContentViewport(1.25f).moved(100f, 0f, 900, 500, 1080, 2400)
        assertEquals(0f, letterbox.panX, 0f)
        val fit = EmbeddedCanvasTransform.fit(900, 500, 1080, 2400, letterbox)
        assertFalse(fit.contains(0f, 250f, 900, 500))
    }

    @Test fun fitResetsHiddenPanAndMalformedPersistenceCannotInjectInvalidMatrices() {
        val store = ApplicationContentViewportStore(null)
        store.save("qq", EmbeddedContentViewport(1.5f, .4f, -.8f))
        assertEquals(EmbeddedContentViewport(), store.load("other.app"))
        val encoded = ApplicationContentViewportStore.encode(store.load("qq"))
        assertEquals(store.load("qq"), ApplicationContentViewportStore.decode(encoded))
        assertEquals(EmbeddedContentViewport(), EmbeddedContentViewport(1f, 1f, -1f).sanitized())
        for (raw in listOf(null, "", "2,NaN,0", "Infinity,0,0", "2,0", "bad,0,0")) {
            assertEquals(EmbeddedContentViewport(), ApplicationContentViewportStore.decode(raw))
        }
    }

    @Test fun savedWindowGeometryStillOverridesTheMoreReadableNewDefault() {
        val placement = ApplicationWindowPlacement(1080, 2400, 3f)
        assertEquals(756, placement.window.width)
        assertEquals(1440, placement.window.height)
        val saved = ApplicationWindowRect(44, 77, 670, 1152)
        placement.restoreSavedWindow(saved)
        assertEquals(saved, placement.window)
    }
}
