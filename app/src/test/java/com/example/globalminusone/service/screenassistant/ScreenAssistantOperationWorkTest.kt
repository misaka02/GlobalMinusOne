package com.example.globalminusone.service.screenassistant

import com.example.globalminusone.screenassistant.ScreenAssistantWorkSession
import com.example.globalminusone.service.ScreenAssistantMode
import com.example.globalminusone.service.screenassistant.ScreenAssistantPresentation.Work
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class ScreenAssistantOperationWorkTest {
    @Test fun olderRecognitionCannotOverwriteNewerCompletionEvenIfProviderIgnoresCancellation() = runBlocking {
        val state = ScreenAssistantPresentation(ScreenAssistantMode.TEXT_SELECT)
        val session = ScreenAssistantWorkSession(this) { _, _ -> fail("unexpected failure") }
        try {
            val work = ScreenAssistantOperationWork(session, state) { _, _ -> fail("unexpected failure") }
            val oldResult = CompletableDeferred<String>()
            val publications = mutableListOf<String>()
            val old = work.launch("识别", state.begin(Work.RECOGNITION), produce = {
                withContext(NonCancellable) { oldResult.await() }
            }) { publications += it }!!
            yield()
            work.launch("识别", state.begin(Work.RECOGNITION), produce = { "new" }) { publications += it }!!.join()
            oldResult.complete("old")
            old.join()
            assertEquals(listOf("new"), publications)
        } finally { session.close() }
    }

    @Test fun staleFailureDoesNotChangeCurrentStatusOrReportAnError() = runBlocking {
        val state = ScreenAssistantPresentation(ScreenAssistantMode.TRANSLATE_INPLACE)
        val failures = mutableListOf<String>()
        val session = ScreenAssistantWorkSession(this) { label, _ -> failures += label }
        try {
            val work = ScreenAssistantOperationWork(session, state) { label, _ -> failures += label }
            val finish = CompletableDeferred<Unit>()
            val old = work.launch("翻译", state.begin(Work.TRANSLATION), produce = {
                withContext(NonCancellable) { finish.await(); error("late provider failure") }
            }) { fail("unexpected publication") }!!
            yield()
            state.begin(Work.RECOGNITION)
            state.translateStatusText = "current"
            finish.complete(Unit)
            old.join()
            assertTrue(failures.isEmpty())
            assertEquals("current", state.translateStatusText)
        } finally { session.close() }
    }

    @Test fun replacementExtractionAndTranslationRejectTheirPreviousDependents() = runBlocking {
        val state = ScreenAssistantPresentation(ScreenAssistantMode.SUB_IMAGES)
        val session = ScreenAssistantWorkSession(this) { _, _ -> fail("unexpected failure") }
        try {
            val work = ScreenAssistantOperationWork(session, state) { _, _ -> fail("unexpected failure") }
            val finish = CompletableDeferred<String>()
            val publications = mutableListOf<String>()
            val extraction = work.launch("提图", state.begin(Work.EXTRACTION), produce = { finish.await() }) { publications += it }!!
            val repair = work.launch("修复", state.begin(Work.INPAINT), produce = { finish.await() }) { publications += it }!!
            yield()
            state.begin(Work.EXTRACTION)
            state.begin(Work.TRANSLATION)
            finish.complete("stale")
            extraction.join(); repair.join()
            assertTrue(publications.isEmpty())
        } finally { session.close() }
    }

    @Test fun closingCaptureSuppressesEveryToolCompletionAndFailure() = runBlocking {
        for (kind in Work.entries) {
            val state = ScreenAssistantPresentation(ScreenAssistantMode.TEXT_SELECT)
            val failures = mutableListOf<String>()
            val publications = mutableListOf<String>()
            val session = ScreenAssistantWorkSession(this) { label, _ -> failures += label }
            val work = ScreenAssistantOperationWork(session, state) { label, _ -> failures += label }
            val finish = CompletableDeferred<String>()
            val pending = work.launch(kind.name, state.begin(kind), produce = {
                withContext(NonCancellable) { finish.await() }
            }) { publications += it }!!
            yield()
            session.close()
            finish.complete("late")
            pending.join()
            assertTrue(publications.isEmpty())
            assertTrue(failures.isEmpty())
        }
    }

    @Test fun switchingModeDoesNotDiscardUsefulTranslationOrExtraction() = runBlocking {
        val state = ScreenAssistantPresentation(ScreenAssistantMode.TEXT_SELECT)
        val session = ScreenAssistantWorkSession(this) { _, _ -> fail("unexpected failure") }
        try {
            val work = ScreenAssistantOperationWork(session, state) { _, _ -> fail("unexpected failure") }
            val finish = CompletableDeferred<String>()
            val publications = mutableListOf<String>()
            val translation = work.launch("翻译", state.begin(Work.TRANSLATION), produce = { finish.await() }) { publications += "translation:$it" }!!
            val extraction = work.launch("提图", state.begin(Work.EXTRACTION), produce = { finish.await() }) { publications += "images:$it" }!!
            yield()
            state.switchMode(ScreenAssistantMode.SUB_IMAGES)
            finish.complete("cached")
            translation.join(); extraction.join()
            assertEquals(setOf("translation:cached", "images:cached"), publications.toSet())
        } finally { session.close() }
    }

    @Test fun currentFailureIsReportedAndOtherToolsStillRun() = runBlocking {
        val state = ScreenAssistantPresentation(ScreenAssistantMode.TEXT_SELECT)
        val failures = mutableListOf<String>()
        val session = ScreenAssistantWorkSession(this) { _, _ -> fail("operation failure escaped") }
        try {
            val work = ScreenAssistantOperationWork(session, state) { label, _ -> failures += label }
            work.launch("识别", state.begin(Work.RECOGNITION), produce = { error("provider") }) { fail("unexpected publication") }!!.join()
            var extracted = false
            work.launch("提图", state.begin(Work.EXTRACTION), produce = { true }) { extracted = it }!!.join()
            assertEquals(listOf("识别"), failures)
            assertTrue(extracted)
            assertTrue(session.isActive)
        } finally { session.close() }
    }

    @Test fun staleOrClosedCallbackCannotStartAnotherProviderRequest() = runBlocking {
        val state = ScreenAssistantPresentation(ScreenAssistantMode.TEXT_SELECT)
        val session = ScreenAssistantWorkSession(this) { _, _ -> fail("unexpected failure") }
        val work = ScreenAssistantOperationWork(session, state) { _, _ -> fail("unexpected failure") }
        val old = state.begin(Work.RECOGNITION)
        val newer = state.begin(Work.RECOGNITION)
        var calls = 0
        assertNull(work.launch("old", old, produce = { calls++ }) {})
        session.close()
        assertNull(work.launch("closed", newer, produce = { calls++ }) {})
        assertEquals(0, calls)
    }

    @Test fun globalImageTabPublishesWhileOcrIsStillPendingOrRestarts() = runBlocking {
        val state = ScreenAssistantPresentation(ScreenAssistantMode.TEXT_SELECT)
        val session = ScreenAssistantWorkSession(this) { _, _ -> fail("unexpected failure") }
        try {
            val work = ScreenAssistantOperationWork(session, state) { _, _ -> fail("unexpected failure") }
            state.begin(Work.RECOGNITION)
            state.switchMode(ScreenAssistantMode.SUB_IMAGES)
            val completed = CompletableDeferred<String>()
            var original = ""
            val extraction = work.launch("提图", state.begin(Work.EXTRACTION), produce = { completed.await() }) {
                state.subImagesDone = true
                original = it
            }!!
            yield()
            state.begin(Work.RECOGNITION)
            completed.complete("original.webp")
            extraction.join()
            assertEquals("original.webp", original)
            assertFalse(state.visibility().progress)
            assertFalse(state.ocrDone)
        } finally {
            session.close()
        }
    }
}
