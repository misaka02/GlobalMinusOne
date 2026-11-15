package com.example.globalminusone.browser

import org.junit.Assert.*
import org.junit.Test

class EmbeddedWindowResizeBoundaryTest {
    private val phone = EmbeddedAppViewportPolicy.Viewport(1080, 2400, 480)

    @Test fun continuousHostResizeKeepsCompleteCanvasAndCommitsOnlyFinalReflow() {
        val queue = EmbeddedViewportResizeQueue()
        queue.hostGestureActive = true
        val hosts = listOf(420 to 760, 600 to 700, 900 to 450)
        for ((w, h) in hosts) {
            queue.offer(EmbeddedAppViewportPolicy.reflow(w, h, 480))
            // TextureView's host-size callback must restore these remote pixels,
            // even though the outer window has a very different aspect ratio.
            val buffer = queue.bufferViewport(phone)
            assertEquals(phone, buffer)
            assertNull(queue.take(phone))
            val fit = EmbeddedCanvasTransform.fit(w, h, buffer.width, buffer.height)
            assertTrue(w * fit.scaleX <= w + .01f)
            assertTrue(h * fit.scaleY <= h + .01f)
            assertEquals(phone.width.toFloat() / phone.height,
                w * fit.scaleX / (h * fit.scaleY), .0001f)
        }
        queue.hostGestureActive = false
        val latest = EmbeddedAppViewportPolicy.reflow(900, 450, 480)
        assertEquals(latest, queue.take(phone))
        assertEquals(latest, queue.bufferViewport(phone))
        queue.complete(latest, true)
        assertNull(queue.take(latest))
        assertEquals(latest, queue.bufferViewport(latest))
    }

    @Test fun hostSizeCallbackDuringIpcRestoresInFlightPixelsInsteadOfHostOrPendingSize() {
        val queue = EmbeddedViewportResizeQueue()
        val first = EmbeddedAppViewportPolicy.reflow(900, 450, 480)
        val pending = EmbeddedAppViewportPolicy.reflow(500, 850, 480)
        queue.offer(first)
        assertEquals(first, queue.take(phone))
        queue.hostGestureActive = true
        queue.offer(pending)
        assertEquals(first, queue.bufferViewport(phone))
        queue.complete(first, true)
        assertEquals(first, queue.bufferViewport(first))
        assertNull(queue.take(first))
        queue.hostGestureActive = false
        assertEquals(pending, queue.take(first))
    }

    @Test fun cancelledHostResizeAndPositionOnlyMoveDoNotResizeTheApp() {
        val queue = EmbeddedViewportResizeQueue()
        assertTrue(queue.offer(phone))
        assertFalse(queue.offer(phone))
        assertNull(queue.take(phone))
        queue.hostGestureActive = true
        queue.offer(EmbeddedAppViewportPolicy.reflow(900, 450, 480))
        queue.offer(phone) // ACTION_CANCEL restores the original host geometry.
        queue.hostGestureActive = false
        assertNull(queue.take(phone))
        assertEquals(phone, queue.bufferViewport(phone))
    }

    @Test fun failedResizeRestoresCommittedBufferAndResetReleasesHostGesture() {
        val queue = EmbeddedViewportResizeQueue()
        val target = EmbeddedAppViewportPolicy.reflow(900, 450, 480)
        queue.offer(target)
        assertEquals(target, queue.take(phone))
        assertEquals(target, queue.bufferViewport(phone))
        queue.complete(target, false)
        assertEquals(phone, queue.bufferViewport(phone))
        assertNull(queue.take(phone))
        queue.hostGestureActive = true
        queue.reset()
        assertFalse(queue.hostGestureActive)
        queue.offer(target)
        assertEquals(target, queue.take(phone))
    }
}
