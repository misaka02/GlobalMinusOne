package com.example.globalminusone.screenassistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Install the separately delivered OCR extension first; then test actual cross-package inference. */
@RunWith(AndroidJUnit4::class)
class OfflineOcrModelTest {
    @Test fun manuallyInstalledExtensionRecognizesGeneratedImageOffline() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val result = withTimeout(30_000L) { ScreenAssistantAttachmentManager.verifyLocalOcrModel(context) }
        assertTrue(result.detail, result.ready)
    }
}
