package com.example.globalminusone.browser

import org.junit.Assert.*
import org.junit.Test

class EmbeddedViewportReflowTest {
    @Test fun freeAspectProducesDifferentAppConfigurationWithUsableLogicalHeight() {
        val tall = EmbeddedAppViewportPolicy.reflow(450, 900, 480)
        val wide = EmbeddedAppViewportPolicy.reflow(900, 450, 480)
        assertTrue(tall.width * 160f / tall.densityDpi >= 360f)
        assertTrue(tall.height * 160f / tall.densityDpi >= 640f)
        assertTrue(wide.width * 160f / wide.densityDpi >= 640f)
        assertTrue(wide.height * 160f / wide.densityDpi >= 360f)
        assertEquals(.5f, tall.width.toFloat() / tall.height, .002f)
        assertEquals(2f, wide.width.toFloat() / wide.height, .002f)
        assertNotEquals(tall, wide)
        // Returning to complete-phone mode selects the original viewport.
        assertEquals(EmbeddedAppViewportPolicy.Viewport(1080, 2400, 480),
            EmbeddedAppViewportPolicy.create(1080, 2400, 480))
    }

    @Test fun latestGeometryConvergesWithoutQueuingIntermediateDragSizes() {
        val queue = EmbeddedViewportResizeQueue()
        val old = EmbeddedAppViewportPolicy.create(1080, 2400, 480)
        val first = EmbeddedAppViewportPolicy.reflow(500, 800, 480)
        val middle = EmbeddedAppViewportPolicy.reflow(700, 800, 480)
        val latest = EmbeddedAppViewportPolicy.reflow(900, 700, 480)
        queue.offer(first)
        assertEquals(first, queue.take(old))
        queue.offer(middle); queue.offer(latest)
        assertNull(queue.take(old))
        queue.complete(first, true)
        assertEquals(latest, queue.take(first))
        queue.complete(latest, true)
        assertNull(queue.take(latest))
    }

    @Test fun returningToPhoneModeDuringResizeConvergesToOriginalCommittedCanvas() {
        val queue = EmbeddedViewportResizeQueue()
        val phone = EmbeddedAppViewportPolicy.create(1080, 2400, 480)
        val reflow = EmbeddedAppViewportPolicy.reflow(900, 500, 480)
        queue.offer(reflow)
        assertEquals(reflow, queue.take(phone))
        queue.offer(phone)
        assertNull(queue.take(phone))
        queue.complete(reflow, true)
        assertEquals(phone, queue.take(reflow))
        queue.complete(phone, true)
        assertNull(queue.take(phone))
    }

    @Test fun failedSizeDoesNotSpinAndNewSizeOrExplicitRetryCanProceed() {
        val queue = EmbeddedViewportResizeQueue()
        val old = EmbeddedAppViewportPolicy.create(1080, 2400, 480)
        val failed = EmbeddedAppViewportPolicy.reflow(600, 700, 480)
        val next = EmbeddedAppViewportPolicy.reflow(900, 700, 480)
        queue.offer(failed); assertEquals(failed, queue.take(old))
        queue.complete(failed, false)
        assertNull(queue.take(old))
        queue.retry(); assertEquals(failed, queue.take(old))
        queue.offer(next); queue.complete(failed, false)
        assertEquals(next, queue.take(old))
    }

    @Test fun committedCanvasKeepsRenderingAndTouchInverseOnSamePixelGrid() {
        for ((w, h) in listOf(450 to 900, 900 to 450, 800 to 800)) {
            val canvas = EmbeddedAppViewportPolicy.reflow(w, h, 480)
            val matrix = EmbeddedCanvasTransform.fit(w, h, canvas.width, canvas.height)
            assertEquals(w.toFloat() / h, canvas.width.toFloat() / canvas.height, .003f)
            for (fraction in listOf(.1f, .5f, .9f)) {
                assertEquals(canvas.width * fraction, matrix.mapHostX(w * fraction, w, canvas.width), 2f)
                assertEquals(canvas.height * fraction, matrix.mapHostY(h * fraction, h, canvas.height), 2f)
            }
        }
    }

    @Test fun transactionRollsBackDisplayAndRootAfterRootFailureWithoutLaunchPorts() {
        val before = EmbeddedDisplayResizeTransaction.Geometry(1080, 2400, 480)
        val after = EmbeddedDisplayResizeTransaction.Geometry(1600, 1080, 480)
        val calls = mutableListOf<String>()
        val port = object : EmbeddedDisplayResizeTransaction.Port {
            override fun resizeDisplay(g: EmbeddedDisplayResizeTransaction.Geometry) { calls += "display:${g.width}" }
            override fun resizeRoot(g: EmbeddedDisplayResizeTransaction.Geometry) {
                calls += "root:${g.width}"
                if (g.width == 1600) throw IllegalStateException("injected root failure")
            }
        }
        try {
            EmbeddedDisplayResizeTransaction.replace(before, after, port)
            fail("Expected failure")
        } catch (failure: EmbeddedDisplayResizeTransaction.Failure) { assertTrue(failure.rollbackConfirmed) }
        assertEquals(listOf("display:1600", "root:1600", "display:1080", "root:1080"), calls)
    }

    @Test fun unconfirmedRollbackIsReportedInsteadOfPretendingOldGeometrySucceeded() {
        val before = EmbeddedDisplayResizeTransaction.Geometry(1080, 2400, 480)
        val after = EmbeddedDisplayResizeTransaction.Geometry(1600, 1080, 480)
        val calls = mutableListOf<Int>()
        val port = object : EmbeddedDisplayResizeTransaction.Port {
            override fun resizeDisplay(g: EmbeddedDisplayResizeTransaction.Geometry) {
                calls += g.width
                if (g.width == 1080) throw IllegalStateException("injected rollback failure")
            }
            override fun resizeRoot(g: EmbeddedDisplayResizeTransaction.Geometry) { throw IllegalStateException("injected new root failure") }
        }
        try {
            EmbeddedDisplayResizeTransaction.replace(before, after, port)
            fail("Expected failure")
        } catch (failure: EmbeddedDisplayResizeTransaction.Failure) { assertFalse(failure.rollbackConfirmed) }
        assertEquals(listOf(1600, 1080), calls)
    }
}
