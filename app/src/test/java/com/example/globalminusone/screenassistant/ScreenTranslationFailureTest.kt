package com.example.globalminusone.screenassistant

import com.example.globalminusone.service.ScreenAssistantMode
import com.example.globalminusone.service.screenassistant.ScreenAssistantPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTranslationFailureTest {
    @Test fun emptyTranslationInputPreservesRecognitionDiagnosticUntilNewRecognition() {
        val presentation = ScreenAssistantPresentation(ScreenAssistantMode.TRANSLATE_INPLACE)
        presentation.lastOcrNote = "OCR 识别过程中扩展连接断开"
        assertEquals(presentation.lastOcrNote, presentation.missingTranslationInputStatus())
        presentation.begin(ScreenAssistantPresentation.Work.RECOGNITION)
        assertEquals("当前未识别到可翻译文字", presentation.missingTranslationInputStatus())
    }

    @Test fun originalEchoAndBlankOutputAreNotSuccessfulTranslations() {
        assertTrue(ScreenAssistantPolicy.hasOnlyUntranslatedOutput(listOf(
            block("Download model now", "Download model now"), block("Open settings", "")
        ), "zh-CN"))
    }

    @Test fun validPartialResultsAndUnchangedTermsRemainUsable() {
        assertFalse(ScreenAssistantPolicy.hasOnlyUntranslatedOutput(listOf(
            block("Download model now", "立即下载模型"), block("Open settings", "Open settings")
        ), "zh-CN"))
        assertFalse(ScreenAssistantPolicy.hasOnlyUntranslatedOutput(listOf(
            block("CPU", "CPU"), block("系统设置", "系统设置")
        ), "zh-CN"))
    }

    private fun block(text: String, translated: String) = ScreenTextBlock(
        id = text, text = text, leftPx = 0, topPx = 0, rightPx = 100, bottomPx = 20,
        translatedText = translated,
    )
}
