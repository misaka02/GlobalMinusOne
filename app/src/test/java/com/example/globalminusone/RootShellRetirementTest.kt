package com.example.globalminusone

import com.example.globalminusone.system.privilege.RootShellRetirement
import com.example.globalminusone.system.privilege.snapshotPendingRetirements
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class RootShellRetirementTest {
    @Test fun snapshotUsesIteratorEvenWhenConcurrentCollectionReportsOneElement() {
        fun staleSizeCollection(values: List<Int>) = object : AbstractCollection<Int>() {
            override val size: Int get() = 1
            override fun iterator(): Iterator<Int> = values.iterator()
        }
        // Models done() removing the last task after size was read. Collection.toList()
        // uses iterator.next() without hasNext() for this stale singleton size.
        assertEquals(emptyList<Int>(), snapshotPendingRetirements(staleSizeCollection(emptyList())))
        // The same snapshot must retain every observed task when retire() adds work.
        assertEquals(listOf(4, 7), snapshotPendingRetirements(staleSizeCollection(listOf(4, 7))))
    }

    @Test fun blockedStreamCloseRunsOffCallerAndExplicitReopenWaitIsBounded() {
        val started = CountDownLatch(1); val finish = CountDownLatch(1)
        val caller = Thread.currentThread()
        val closeThread = java.util.concurrent.atomic.AtomicReference<Thread>()
        val retirement = RootShellRetirement<Any> {
            closeThread.set(Thread.currentThread()); started.countDown(); finish.await()
        }
        try {
            retirement.retire(Any())
            assertTrue(started.await(5, TimeUnit.SECONDS))
            assertNotSame(caller, closeThread.get())
            assertTrue(runCatching { retirement.awaitPending(30) }.exceptionOrNull() is TimeoutException)
        } finally { finish.countDown() }
        retirement.awaitPending(5_000)
    }
    @Test fun completedRetirementAllowsNextExplicitOpenWithoutAnotherClose() {
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val retirement = RootShellRetirement<Any> { calls.incrementAndGet() }
        retirement.retire(Any()); retirement.awaitPending(5_000); retirement.awaitPending(5_000)
        assertEquals(1, calls.get())
    }
}
