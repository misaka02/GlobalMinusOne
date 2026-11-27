package com.example.globalminusone.system.privilege

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Weakly consistent collections can change between size and iterator reads. */
internal fun <T> snapshotPendingRetirements(pending: Iterable<T>): List<T> {
    val snapshot = ArrayList<T>()
    for (task in pending) snapshot.add(task)
    return snapshot
}

/** Stream close can wait for an active pipe reader/writer. Never perform it under a UI/session lock. */
internal class RootShellRetirement<S : Any>(private val close: (S) -> Unit) {
    private val pending = ConcurrentHashMap<S, FutureTask<Unit>>()
    private val worker = Executors.newCachedThreadPool { task ->
        Thread(task, "GmoRootClose").apply { isDaemon = true }
    }
    fun retire(shell: S) {
        val task = object : FutureTask<Unit>({ close(shell) }, Unit) {
            override fun done() { pending.remove(shell, this) }
        }
        if (pending.putIfAbsent(shell, task) == null) worker.execute(task)
    }
    /** Called only by an explicit open on Dispatchers.IO; no retry or shell creation happens here. */
    fun awaitPending(timeoutMs: Long) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (true) {
            val current = snapshotPendingRetirements(pending.values)
            if (current.isEmpty()) return
            current.forEach { task ->
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0) throw TimeoutException("旧Root管道仍在关闭；本次未重新启动su，请稍后显式授权")
                task.get(remaining, TimeUnit.NANOSECONDS)
            }
        }
    }
}
