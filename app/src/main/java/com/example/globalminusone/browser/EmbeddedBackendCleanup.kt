package com.example.globalminusone.browser

import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger

/** Two process-owned serial lanes. A blocked Binder never blocks the other backend's cleanup.
 * No watchdog, retry thread or timeout cancellation: RETURNED only means the call returned.
 */
internal class EmbeddedBackendCleanup(
    root: Executor,
    shizuku: Executor,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L }
) {
    enum class Backend { ROOT, SHIZUKU }
    enum class Phase { QUEUED, RUNNING, RETURNED, FAILED }
    data class Snapshot(val phase: Phase, val elapsedMillis: Long, val reply: String?,
        val failure: String?, val handoffFailure: String?)
    data class LaneSnapshot(val queued: Int, val active: Snapshot?)

    class Ticket internal constructor(private val nowMillis: () -> Long) {
        private var phase = Phase.QUEUED
        private var since = nowMillis()
        private var reply: String? = null
        private var failure: String? = null
        private var handoffFailure: String? = null
        @Synchronized internal fun start() { phase = Phase.RUNNING; since = nowMillis() }
        @Synchronized internal fun returned(result: Result<String>) {
            phase = if (result.isSuccess) Phase.RETURNED else Phase.FAILED
            since = nowMillis()
            reply = result.getOrNull()
            failure = result.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName }
        }
        @Synchronized internal fun handoffFailed(error: Throwable) {
            handoffFailure = error.message ?: error.javaClass.simpleName
        }
        @Synchronized fun snapshot() = Snapshot(phase, (nowMillis() - since).coerceAtLeast(0), reply, failure, handoffFailure)
    }
    private class Lane(val executor: Executor) {
        val queued = AtomicInteger()
        @Volatile var active: Ticket? = null
    }
    private val rootLane = Lane(root)
    private val shizukuLane = Lane(shizuku)

    init { require(root !== shizuku) { "Cleanup backends require independent serial executors" } }
    private fun lane(backend: Backend) = if (backend == Backend.ROOT) rootLane else shizukuLane

    fun snapshot(backend: Backend): LaneSnapshot {
        val lane = lane(backend)
        return LaneSnapshot(lane.queued.get(), lane.active?.snapshot())
    }

    fun submit(backend: Backend, close: () -> String, onReturned: (Result<String>) -> Unit = {}): Ticket {
        val lane = lane(backend)
        val ticket = Ticket(nowMillis)
        lane.queued.incrementAndGet()
        fun handoff(result: Result<String>) {
            runCatching { onReturned(result) }.exceptionOrNull()?.let(ticket::handoffFailed)
        }
        try {
            lane.executor.execute {
                lane.queued.decrementAndGet()
                lane.active = ticket
                ticket.start()
                try {
                    val result = runCatching(close)
                    ticket.returned(result)
                    handoff(result)
                } finally { lane.active = null }
            }
        } catch (ended: RejectedExecutionException) {
            lane.queued.decrementAndGet()
            val result = Result.failure<String>(ended)
            ticket.returned(result)
            handoff(result)
        }
        return ticket
    }
}
