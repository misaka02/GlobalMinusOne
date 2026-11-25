package com.example.globalminusone.browser

import org.junit.Assert.*
import org.junit.Test
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class EmbeddedBackendCleanupTest {
    private class Queue : Executor {
        val work = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { work.add(command) }
        fun next() = work.removeFirst().run()
    }

    @Test fun blockedRootDoesNotBlockShizukuOrPretendItsCallReturned() {
        val root = Executors.newSingleThreadExecutor()
        val shizuku = Executors.newSingleThreadExecutor()
        val started = CountDownLatch(1); val release = CountDownLatch(1)
        val rootDone = CountDownLatch(1); val shizukuDone = CountDownLatch(1)
        val rootReturned = AtomicBoolean(false)
        val clock = AtomicLong(10)
        try {
            val cleanup = EmbeddedBackendCleanup(root, shizuku, clock::get)
            val ticket = cleanup.submit(EmbeddedBackendCleanup.Backend.ROOT, {
                started.countDown(); release.await(); "closed"
            }) { rootReturned.set(true); rootDone.countDown() }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            clock.set(30_010)
            cleanup.submit(EmbeddedBackendCleanup.Backend.SHIZUKU, { "closed: cleanup_unconfirmed" }) {
                assertEquals("closed: cleanup_unconfirmed", it.getOrThrow()); shizukuDone.countDown()
            }
            assertTrue(shizukuDone.await(5, TimeUnit.SECONDS))
            assertEquals(EmbeddedBackendCleanup.Phase.RUNNING, ticket.snapshot().phase)
            assertEquals(30_000L, ticket.snapshot().elapsedMillis)
            assertFalse(rootReturned.get())
            release.countDown()
            assertTrue(rootDone.await(5, TimeUnit.SECONDS))
            assertEquals(EmbeddedBackendCleanup.Phase.RETURNED, ticket.snapshot().phase)
        } finally {
            release.countDown(); root.shutdownNow(); shizuku.shutdownNow()
            assertTrue(root.awaitTermination(5, TimeUnit.SECONDS))
            assertTrue(shizuku.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test fun sameBackendIsOrderedAndHandoffFollowsActualReturn() {
        val root = Queue(); val shizuku = Queue(); val order = mutableListOf<String>()
        val cleanup = EmbeddedBackendCleanup(root, shizuku)
        val first = cleanup.submit(EmbeddedBackendCleanup.Backend.ROOT, { order.add("close"); "closed" }) { order.add("unbind") }
        cleanup.submit(EmbeddedBackendCleanup.Backend.ROOT, { order.add("next"); "closed" })
        assertEquals(2, cleanup.snapshot(EmbeddedBackendCleanup.Backend.ROOT).queued)
        assertEquals(EmbeddedBackendCleanup.Phase.QUEUED, first.snapshot().phase)
        assertTrue(order.isEmpty())
        root.next(); assertEquals(listOf("close", "unbind"), order)
        assertEquals(1, cleanup.snapshot(EmbeddedBackendCleanup.Backend.ROOT).queued)
        root.next(); assertEquals(listOf("close", "unbind", "next"), order)
        assertNull(cleanup.snapshot(EmbeddedBackendCleanup.Backend.ROOT).active)
    }

    @Test fun failureAndCallbackFailureRemainExplicitAndNextCloseStillRuns() {
        val root = Queue(); val cleanup = EmbeddedBackendCleanup(root, Queue())
        var returned = 0
        val failed = cleanup.submit(EmbeddedBackendCleanup.Backend.ROOT, { error("remote died") }) {
            assertTrue(it.isFailure); returned++; error("handoff failed")
        }
        root.next()
        assertEquals(1, returned)
        assertEquals(EmbeddedBackendCleanup.Phase.FAILED, failed.snapshot().phase)
        assertEquals("remote died", failed.snapshot().failure)
        assertEquals("handoff failed", failed.snapshot().handoffFailure)
        val next = cleanup.submit(EmbeddedBackendCleanup.Backend.ROOT, { "closed: cleanup_unconfirmed" })
        root.next()
        assertEquals(EmbeddedBackendCleanup.Phase.RETURNED, next.snapshot().phase)
        assertEquals("closed: cleanup_unconfirmed", next.snapshot().reply)
    }

    @Test fun rejectedWorkNeverRunsBinderCloseOnTheCallingThread() {
        val rejected = Executor { throw RejectedExecutionException("ended") }
        val cleanup = EmbeddedBackendCleanup(rejected, Queue())
        var closed = false; var handoffs = 0
        val ticket = cleanup.submit(EmbeddedBackendCleanup.Backend.ROOT, { closed = true; "closed" }) {
            assertTrue(it.isFailure); handoffs++
        }
        assertFalse(closed); assertEquals(1, handoffs)
        assertEquals(EmbeddedBackendCleanup.Phase.FAILED, ticket.snapshot().phase)
        assertEquals(0, cleanup.snapshot(EmbeddedBackendCleanup.Backend.ROOT).queued)
    }
}
