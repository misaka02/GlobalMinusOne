package com.example.globalminusone.regression.v2442

import com.example.globalminusone.browser.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class BrowserConcurrencyPolicyTest {
    @Test fun surfaceAndTextureReleaseWaitForAnInFlightTransferAndFinalClose() {
        val worker = Executors.newSingleThreadExecutor()
        val entered = CountDownLatch(1)
        val unblock = CountDownLatch(1)
        val released = CountDownLatch(2)
        val order = Collections.synchronizedList(mutableListOf<String>())
        try {
            worker.execute {
                order += "transfer-start"; entered.countDown()
                check(unblock.await(2, TimeUnit.SECONDS))
                order += "transfer-end"
            }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            worker.execute { order += "remote-close" }
            EmbeddedSerialCleanup.release(worker) { order += "surface-release"; released.countDown() }
            EmbeddedSerialCleanup.finish(worker)
            // TextureView can call destroy after final owner release queued shutdown.
            EmbeddedSerialCleanup.release(worker) { order += "texture-release"; released.countDown() }
            assertEquals(listOf("transfer-start"), order.toList())
            unblock.countDown()
            assertTrue(released.await(2, TimeUnit.SECONDS))
            assertTrue(worker.awaitTermination(2, TimeUnit.SECONDS))
            assertEquals(listOf("transfer-start", "transfer-end", "remote-close", "surface-release", "texture-release"), order.toList())
        } finally { unblock.countDown(); worker.shutdownNow() }
    }

    @Test fun lateTextureDestroyStillReleasesExactlyOnceAfterQueueTermination() {
        val worker = Executors.newSingleThreadExecutor()
        EmbeddedSerialCleanup.finish(worker)
        assertTrue(worker.awaitTermination(2, TimeUnit.SECONDS))
        val releases = AtomicInteger()
        EmbeddedSerialCleanup.release(worker) { releases.incrementAndGet() }
        assertEquals(1, releases.get())
    }

    @Test fun aTextureWithoutAnyTransferNeedsNoWorkerToRelease() {
        val releases = AtomicInteger()
        EmbeddedSerialCleanup.release(null) { releases.incrementAndGet() }
        assertEquals(1, releases.get())
    }

    @Test fun staleLaunchFailureCannotCancelOrFinishItsReplacement() {
        val gate = EmbeddedLaunchGate()
        val old = gate.begin()
        gate.cancel()
        val replacement = gate.begin()
        assertFalse(gate.isCurrent(old))
        assertTrue(gate.isCurrent(replacement))
        gate.finish(old)
        assertTrue(gate.isOpening)
        gate.finish(replacement)
        assertFalse(gate.isOpening)
    }

    @Test fun concurrentRetirementCancelsOpeningWithoutWaitingForPlatformWork() {
        val gate = EmbeddedLaunchGate()
        val old = gate.begin()
        val platformEntered = CountDownLatch(1)
        val platformRelease = CountDownLatch(1)
        val platform = Executors.newSingleThreadExecutor()
        try {
            platform.execute {
                platformEntered.countDown()
                check(platformRelease.await(2, TimeUnit.SECONDS))
            }
            assertTrue(platformEntered.await(2, TimeUnit.SECONDS))
            gate.retire()
            assertFalse(gate.isCurrent(old))
            assertFalse(gate.isOpening)
            try { gate.begin(); fail("retired session must never open again") }
            catch (_: IllegalStateException) { }
        } finally { platformRelease.countDown(); platform.shutdownNow() }
    }

    @Test fun secondOpenIsRejectedUntilTheCurrentAttemptEnds() {
        val gate = EmbeddedLaunchGate()
        val current = gate.begin()
        try { gate.begin(); fail("parallel launch must be rejected") }
        catch (_: IllegalStateException) { }
        assertTrue(gate.isCurrent(current))
        gate.finish(current)
        assertTrue(gate.isCurrent(gate.begin()))
    }

    @Test fun hiddenOrParkedWindowsStopPollingButKeepTheirSession() {
        val state = EmbeddedPresentationState()
        state.initialOutput()
        assertTrue(state.shouldHeartbeat(true, true))
        assertFalse(state.shouldHeartbeat(true, false))
        state.change(true)
        assertFalse(state.shouldHeartbeat(true, true))
        assertTrue(state.keepOnHostLoss(false))
        state.change(false)
        assertTrue(state.shouldHeartbeat(true, true))
        assertFalse(state.shouldHeartbeat(false, true))
    }

    @Test fun browserVersionParsingSupportsNormalChannelsAndRejectsUnknownFormats() {
        assertEquals(130, EmbeddedBrowserVersion.major("130.0.6723.1"))
        assertEquals(130, EmbeddedBrowserVersion.major("130.0-beta"))
        assertEquals(130, EmbeddedBrowserVersion.major(" 130.2 "))
        listOf(null, "", "Chrome/130.0", "beta.130", "130beta.0", "-130", "99999999999999999999").forEach {
            assertEquals("unsupported version: $it", 0, EmbeddedBrowserVersion.major(it))
        }
        try {
            ChromeLaunchPlan.requireBrowser(33, EmbeddedBrowserVersion.major("beta.130"), 4, true, true)
            fail("unknown version must not bypass compatibility checks")
        } catch (_: IllegalStateException) { }
    }
}
