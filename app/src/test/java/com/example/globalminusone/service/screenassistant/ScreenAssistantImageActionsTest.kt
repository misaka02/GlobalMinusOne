package com.example.globalminusone.service.screenassistant

import com.example.globalminusone.screenassistant.ScreenAssistantWorkSession
import com.example.globalminusone.service.screenassistant.ScreenAssistantImageActions.Action
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class ScreenAssistantImageActionsTest {
    private class Image(val bytes: ByteArray, val mime: String)
    private open class Port : ScreenAssistantImageActions.Port<Image, String> {
        val calls = mutableListOf<Pair<String, Image>>()
        val messages = mutableListOf<String>()
        val discarded = mutableListOf<String>()
        var saveResult = true
        var shareResult: String? = "content://original"
        override suspend fun copy(image: Image) { calls += "copy" to image }
        override suspend fun save(image: Image): Boolean { calls += "save" to image; return saveResult }
        override suspend fun prepareShare(image: Image): String? { calls += "prepare" to image; return shareResult }
        override fun discardPreparedShare(prepared: String) { discarded += prepared }
        override fun share(image: Image, prepared: String) {
            assertEquals(shareResult, prepared)
            calls += "share" to image
        }
        override fun feedback(message: String) { messages += message }
    }

    @Test fun copySaveShareUseOnePathAndPreserveTheOriginalPayload() = runBlocking {
        val session = ScreenAssistantWorkSession(this) { _, _ -> fail("unexpected failure") }
        try {
            val port = Port()
            val actions = ScreenAssistantImageActions(session, port)
            val bytes = byteArrayOf(1, 2, 3)
            val original = Image(bytes, "image/webp")
            Action.entries.forEach { actions.dispatch(it, original)!!.join() }
            assertEquals(listOf("copy", "save", "prepare", "share"), port.calls.map { it.first })
            assertTrue(port.calls.all { it.second === original })
            assertSame(bytes, port.calls.last().second.bytes)
            assertEquals("image/webp", port.calls.last().second.mime)
            assertEquals(listOf("已复制图片", "已保存至相册"), port.messages)
        } finally { session.close() }
    }

    @Test fun screenshotCopyAndFailedGallerySaveKeepTheirFeedback() = runBlocking {
        val session = ScreenAssistantWorkSession(this) { _, _ -> fail("unexpected failure") }
        try {
            val port = Port().apply { saveResult = false }
            val actions = ScreenAssistantImageActions(session, port)
            val image = Image(byteArrayOf(), "image/png")
            actions.dispatch(Action.COPY, image, "已复制截图")!!.join()
            actions.dispatch(Action.SAVE, image)!!.join()
            assertEquals(listOf("已复制截图", "保存失败"), port.messages)
        } finally { session.close() }
    }

    @Test fun dismissDuringSharePreparationCannotOpenAChooser() = runBlocking {
        val finish = CompletableDeferred<Unit>()
        val port = object : Port() {
            override suspend fun prepareShare(image: Image): String? = withContext(NonCancellable) {
                finish.await()
                super.prepareShare(image)
            }
        }
        val session = ScreenAssistantWorkSession(this) { _, _ -> fail("unexpected failure") }
        val actions = ScreenAssistantImageActions(session, port)
        val job = actions.dispatch(Action.SHARE, Image(byteArrayOf(), "image/jpeg"))!!
        yield()
        session.close()
        finish.complete(Unit)
        job.join()
        assertFalse(port.calls.any { it.first == "share" })
        assertEquals(listOf("content://original"), port.discarded)
        assertTrue(port.messages.isEmpty())
    }

    @Test fun dismissDuringCopyOrSaveSuppressesLateSuccessFeedback() = runBlocking {
        for (action in listOf(Action.COPY, Action.SAVE)) {
            val finish = CompletableDeferred<Unit>()
            val port = object : Port() {
                override suspend fun copy(image: Image) = withContext(NonCancellable) { finish.await(); super.copy(image) }
                override suspend fun save(image: Image): Boolean = withContext(NonCancellable) { finish.await(); super.save(image) }
            }
            val session = ScreenAssistantWorkSession(this) { _, _ -> fail("unexpected failure") }
            val job = ScreenAssistantImageActions(session, port).dispatch(action, Image(byteArrayOf(), "image/png"))!!
            yield()
            session.close()
            finish.complete(Unit)
            job.join()
            assertTrue(port.messages.isEmpty())
        }
    }

    @Test fun previousScreenshotButtonsCannotBorrowTheReplacementSession() = runBlocking {
        val firstSession = ScreenAssistantWorkSession(this) { _, _ -> fail("unexpected failure") }
        val oldPort = Port()
        val oldActions = ScreenAssistantImageActions(firstSession, oldPort)
        firstSession.close()
        val replacementSession = ScreenAssistantWorkSession(this) { _, _ -> fail("unexpected failure") }
        try {
            val newPort = Port()
            val newActions = ScreenAssistantImageActions(replacementSession, newPort)
            val image = Image(byteArrayOf(), "image/png")
            Action.entries.forEach { assertNull(oldActions.dispatch(it, image)) }
            newActions.dispatch(Action.COPY, image)!!.join()
            assertTrue(oldPort.calls.isEmpty())
            assertEquals(listOf("copy"), newPort.calls.map { it.first })
        } finally { replacementSession.close() }
    }

    @Test fun providerFailureUsesTheExistingSessionErrorBoundaryAndAllowsRetry() = runBlocking {
        val failures = mutableListOf<String>()
        val session = ScreenAssistantWorkSession(this) { label, _ -> failures += label }
        try {
            val port = object : Port() {
                override suspend fun copy(image: Image) { error("clipboard unavailable") }
            }
            val actions = ScreenAssistantImageActions(session, port)
            val image = Image(byteArrayOf(), "image/png")
            actions.dispatch(Action.COPY, image)!!.join()
            actions.dispatch(Action.SAVE, image)!!.join()
            assertEquals(listOf("复制图片"), failures)
            assertEquals(listOf("已保存至相册"), port.messages)
            assertTrue(session.isActive)
        } finally { session.close() }
    }

    @Test fun missingShareUriDoesNotOpenAnExternalActivity() = runBlocking {
        val session = ScreenAssistantWorkSession(this) { _, _ -> fail("unexpected failure") }
        try {
            val port = Port().apply { shareResult = null }
            ScreenAssistantImageActions(session, port).dispatch(Action.SHARE, Image(byteArrayOf(), "image/png"))!!.join()
            assertEquals(listOf("prepare"), port.calls.map { it.first })
            assertTrue(port.messages.isEmpty())
        } finally { session.close() }
    }
}
