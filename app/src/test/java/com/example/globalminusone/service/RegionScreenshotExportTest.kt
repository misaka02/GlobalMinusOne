package com.example.globalminusone.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionScreenshotExportTest {
    @Test
    fun alreadyCancelledScopeStillReleasesCropWithoutWriting() = runBlocking {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        scope.cancel()
        var released = 0
        var wrote = false
        var completed = false
        launchRegionExport(
            scope, "crop",
            write = { wrote = true; "uri" },
            release = { released++ },
            discard = { error("No output should exist") },
            complete = { completed = true }
        ).join()
        assertEquals(1, released)
        assertFalse(wrote)
        assertFalse(completed)
    }

    @Test
    fun dismissedWhileEncodingDiscardsOutputAndNeverShares() = runBlocking {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        val encodingStarted = CompletableDeferred<Unit>()
        val finishEncoding = CompletableDeferred<Unit>()
        var released = 0
        val discarded = mutableListOf<String>()
        var completed = false
        val job = launchRegionExport(
            scope, "crop",
            write = {
                // Bitmap.compress is blocking: cancellation cannot interrupt the write itself.
                withContext(NonCancellable) {
                    encodingStarted.complete(Unit)
                    finishEncoding.await()
                }
                "pending-uri"
            },
            release = { released++ },
            discard = { discarded += it },
            complete = { completed = true }
        )
        encodingStarted.await()
        job.cancel()
        finishEncoding.complete(Unit)
        job.join()
        scope.cancel()
        assertEquals(1, released)
        assertEquals(listOf("pending-uri"), discarded)
        assertFalse(completed)
    }

    @Test
    fun completedExportReleasesCropAndKeepsDeliveredImage() = runBlocking {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        var released = 0
        var completed = false
        launchRegionExport(
            scope, "crop",
            write = { "saved-uri" },
            release = { released++ },
            discard = { error("Delivered image must be retained") },
            complete = { uri -> assertEquals("saved-uri", uri); completed = true }
        ).join()
        scope.cancel()
        assertEquals(1, released)
        assertTrue(completed)
    }
}
