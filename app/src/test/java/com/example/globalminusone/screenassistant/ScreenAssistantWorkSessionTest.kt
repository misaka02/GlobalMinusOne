package com.example.globalminusone.screenassistant

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class ScreenAssistantWorkSessionTest {
    @Test fun engineFailureDoesNotCancelOtherToolsOrHostService() = runBlocking {
        val host = Job()
        val failures = mutableListOf<String>()
        val session = ScreenAssistantWorkSession(CoroutineScope(coroutineContext + host)) { label, _ -> failures += label }
        session.launch("OCR") { error("provider unavailable") }.join()
        var imagesReady = false
        session.launch("images") { imagesReady = true }.join()
        assertEquals(listOf("OCR"), failures)
        assertTrue(imagesReady)
        assertTrue(host.isActive)
        session.close()
        host.cancelAndJoin()
    }

    @Test fun closingSessionCancelsEveryToolAndRejectsLateUiUpdates() = runBlocking {
        val failures = mutableListOf<String>()
        val session = ScreenAssistantWorkSession(this) { label, _ -> failures += label }
        val pending = CompletableDeferred<Unit>()
        var callbacks = 0
        val ocr = session.launch("OCR") { pending.await(); callbacks++ }
        val translate = session.launch("translation") { pending.await(); callbacks++ }
        yield()
        session.close()
        pending.complete(Unit)
        ocr.join(); translate.join()
        session.launch("late export") { callbacks++ }.join()
        assertEquals(0, callbacks)
        assertTrue(failures.isEmpty())
        assertFalse(session.isActive)
    }
}
