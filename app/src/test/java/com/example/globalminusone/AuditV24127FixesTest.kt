package com.example.globalminusone

import android.content.SharedPreferences
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.screenassistant.awaitScreenCaptureResult
import com.example.globalminusone.system.file.BrowserEntry
import com.example.globalminusone.system.file.BrowserLocation
import com.example.globalminusone.system.file.FileCategory
import com.example.globalminusone.system.file.FileSearchCursor
import com.example.globalminusone.system.file.FileSearchResult
import com.example.globalminusone.system.media.AsyncBrightnessWriter
import com.example.globalminusone.ui.panel.file.FileManagementAnalyzerState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class AuditV24127FixesTest {

    @Test
    fun asyncBrightnessWriterCoalescesRapidEnqueuesAndSerializesCommit() = runBlocking {
        val ioExecutor = Executors.newSingleThreadExecutor()
        val ioDispatcher = ioExecutor.asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val written = mutableListOf<Int>()
        try {
            val writer = AsyncBrightnessWriter(
                scope = scope,
                ioDispatcher = ioDispatcher,
                mainDispatcher = Dispatchers.Unconfined
            ) { pct ->
                synchronized(written) { written += pct }
                if (pct == 10) {
                    firstStarted.countDown()
                    releaseFirst.await(2, TimeUnit.SECONDS)
                }
                true
            }
            writer.enqueue(10)
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS))
            writer.enqueue(20)
            writer.enqueue(50)
            writer.enqueue(80)
            releaseFirst.countDown()

            val commitResult = CompletableDeferred<Boolean>()
            writer.commit(95) { ok -> commitResult.complete(ok) }
            assertTrue(commitResult.await())

            synchronized(written) {
                assertEquals(10, written.first())
                assertEquals(95, written.last())
                assertFalse(20 in written)
                assertFalse(50 in written)
            }
        } finally {
            scope.cancel()
            ioExecutor.shutdownNow()
        }
    }

    @Test
    fun awaitScreenCaptureResultDiscardsCancelledOrDuplicateDeliveriesOnce() = runBlocking {
        val discarded = mutableListOf<String>()
        var lateReply: ((String) -> Unit)? = null
        val started = CompletableDeferred<Unit>()
        val cancelledJob = launch(start = CoroutineStart.UNDISPATCHED) {
            awaitScreenCaptureResult<String>(discard = { discarded += it }) { reply ->
                lateReply = reply
                started.complete(Unit)
            }
        }
        started.await()
        cancelledJob.cancel()
        cancelledJob.join()
        lateReply!!.invoke("late-bitmap")
        assertEquals(listOf("late-bitmap"), discarded)

        discarded.clear()
        val winner = awaitScreenCaptureResult<String>(discard = { discarded += it }) { reply ->
            reply("first")
            reply("duplicate")
        }
        assertEquals("first", winner)
        assertEquals(listOf("duplicate"), discarded)

        val raceDiscardCount = AtomicInteger(0)
        val job2 = launch(Dispatchers.Unconfined) {
            awaitScreenCaptureResult<String>(discard = { raceDiscardCount.incrementAndGet() }) { reply ->
                launch(Dispatchers.Default) { reply("raced") }
            }
        }
        job2.cancel()
        job2.join()
        assertTrue(raceDiscardCount.get() <= 1)
    }

    @Test
    fun fileManagementAnalyzerAppendSearchMergesDeduplicatesAndRejectsStaleOwner() {
        val state = FileManagementAnalyzerState()
        state.bindRoot("root-1")
        state.updateQuery("report")
        val owner = state.searchOwner()

        fun entry(name: String) = BrowserEntry(
            location = BrowserLocation(title = name, path = "/sdcard/$name"),
            name = name,
            isDirectory = false,
            mimeType = "text/plain",
            size = 12L,
            modified = 1L
        )
        val cursor = FileSearchCursor(
            rootKey = "root-1",
            permissions = "",
            query = "report",
            category = FileCategory.ALL,
            pending = emptyList(),
            seen = emptySet(),
            rows = emptyList(),
            index = 0,
            visited = 10,
            directories = 2,
            skippedDirectories = 1
        )
        state.publishSearch(owner, FileSearchResult(listOf(entry("a.txt"), entry("b.txt")), cursor, 1, "limit"))
        state.appendSearch(owner, FileSearchResult(listOf(entry("b.txt"), entry("c.txt")), null, 3, null))

        val merged = state.results.value
        assertNotNull(merged)
        assertEquals(listOf("a.txt", "b.txt", "c.txt"), merged!!.entries.map { it.name })
        assertEquals(3, merged.skippedDirectories)
        assertFalse(merged.limited)

        state.updateQuery("other")
        state.appendSearch(owner, FileSearchResult(listOf(entry("stale.txt")), null, 0, null))
        assertNull(state.results.value)
    }

    @Test
    fun repositoryAcknowledgePersistenceErrorClearsFeedbackState() {
        val disk = MemoryPreferences()
        val failingPrefs = object : SharedPreferences by disk {
            var failApply = false
            override fun edit(): SharedPreferences.Editor {
                val delegate = disk.edit()
                return object : SharedPreferences.Editor by delegate {
                    override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                        delegate.putString(key, value)
                        return this
                    }
                    override fun remove(key: String?): SharedPreferences.Editor {
                        delegate.remove(key)
                        return this
                    }
                    override fun apply() {
                        delegate.apply()
                        if (failApply) {
                            failApply = false
                            throw IllegalStateException("disk error")
                        }
                    }
                }
            }
        }
        val repo = MinusOneRepository(failingPrefs)
        failingPrefs.failApply = true
        repo.updateNoteText("unsaved")
        assertNotNull(repo.persistenceError.value)
        repo.acknowledgePersistenceError()
        assertNull(repo.persistenceError.value)
    }
}
