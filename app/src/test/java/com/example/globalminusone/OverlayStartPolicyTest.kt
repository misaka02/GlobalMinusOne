package com.example.globalminusone

import com.example.globalminusone.service.overlay.handleOverlayStart
import org.junit.Assert.*
import org.junit.Test

class OverlayStartPolicyTest {
    @Test fun coldAndWarmRequestsEachAcknowledgeForegroundBeforeWindowWork() {
        val events = mutableListOf<String>()
        repeat(2) { index ->
            assertTrue(handleOverlayStart(index + 1, true,
                assertForeground = { events += "foreground" },
                prepareWindows = { events += "windows"; true },
                publishRunning = { events += "running:$it" },
                stopById = { fail("Accepted start must not stop"); false },
                onFailure = { throw AssertionError(it) }))
        }
        assertEquals(listOf("foreground", "windows", "running:true",
            "foreground", "windows", "running:true"), events)
    }

    @Test fun stopAndDisabledStickyRequestsStillAcknowledgeForegroundWithoutInitializing() {
        // shouldRun=false covers explicit STOP, disabled persisted config and revoked permission.
        repeat(3) { id ->
            val events = mutableListOf<String>()
            assertFalse(handleOverlayStart(id + 1, false,
                assertForeground = { events += "foreground" },
                prepareWindows = { fail("Rejected request must not initialize windows"); true },
                publishRunning = { events += "running:$it" },
                stopById = { events += "stop:$it"; true },
                onFailure = { throw AssertionError(it) }))
            assertEquals(listOf("foreground", "running:false", "stop:${id + 1}"), events)
        }
    }

    @Test fun olderStopDoesNotCancelNewerQueuedStartAndWarmRequestPromotesAgain() {
        var latestStartId = 8
        var stopped = false
        var promotions = 0
        var running = false
        fun deliver(id: Int, allowed: Boolean) = handleOverlayStart(id, allowed,
            assertForeground = { promotions++ }, prepareWindows = { true },
            publishRunning = { running = it },
            stopById = { requested -> (requested == latestStartId).also { if (it) stopped = true } },
            onFailure = { throw AssertionError(it) })
        assertFalse(deliver(7, false))
        assertFalse(stopped)
        assertTrue(deliver(latestStartId, true))
        assertTrue(running)
        assertEquals(2, promotions)
        latestStartId = 9
        assertFalse(deliver(9, false))
        assertTrue(stopped)
        assertFalse(running)
    }

    @Test fun foregroundFailureNeverInitializesOrPublishesRunning() {
        val failure = SecurityException("foreground denied")
        var recorded: Exception? = null
        var running: Boolean? = null
        var stopId: Int? = null
        assertFalse(handleOverlayStart(4, true,
            assertForeground = { throw failure },
            prepareWindows = { fail("No windows before foreground"); true },
            publishRunning = { running = it }, stopById = { stopId = it; true },
            onFailure = { recorded = it }))
        assertSame(failure, recorded)
        assertEquals(false, running)
        assertEquals(4, stopId)
    }

    @Test fun failedWindowRegistrationAndInitializationFailureNeverPublishSuccess() {
        for (throws in listOf(false, true)) {
            val published = mutableListOf<Boolean>()
            var stops = 0
            var failures = 0
            assertFalse(handleOverlayStart(2, true, assertForeground = {},
                prepareWindows = { if (throws) throw IllegalStateException("init"); false },
                publishRunning = { published += it }, stopById = { stops++; true },
                onFailure = { failures++ }))
            assertEquals(listOf(false), published)
            assertEquals(1, stops)
            assertEquals(if (throws) 1 else 0, failures)
        }
    }
}
