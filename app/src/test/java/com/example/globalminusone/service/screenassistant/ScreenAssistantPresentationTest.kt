package com.example.globalminusone.service.screenassistant

import com.example.globalminusone.screenassistant.ScreenTextBlock
import com.example.globalminusone.screenassistant.TextSelectionSpan
import com.example.globalminusone.service.ScreenAssistantMode
import com.example.globalminusone.service.screenassistant.ScreenAssistantPresentation.Work
import org.junit.Assert.*
import org.junit.Test

class ScreenAssistantPresentationTest {
    private fun state() = ScreenAssistantPresentation(ScreenAssistantMode.TEXT_SELECT)
    private fun block(id: String, text: String, top: Int = 0, translated: String? = null) =
        ScreenTextBlock(id, text, 0, top, 100, top + 20, translatedText = translated)

    @Test fun switchingModesPreservesCachedWorkAndSelectionButLeavesPlainText() {
        val state = state()
        state.blocks = listOf(block("a", "hello"))
        state.selectionSpans["a"] = TextSelectionSpan("a", 1, 4)
        state.showPlainTextLayer = true
        val operations = Work.entries.map { state.begin(it) }
        val current = operations.filter(state::accepts)
        state.showingOriginalIds += "a"
        state.switchMode(ScreenAssistantMode.TRANSLATE_INPLACE)
        state.switchMode(ScreenAssistantMode.SUB_IMAGES)
        assertFalse(state.showPlainTextLayer)
        assertEquals("ell", state.selectedText())
        assertEquals(setOf("a"), state.showingOriginalIds)
        assertTrue(current.all(state::accepts))
    }

    @Test fun repeatedRecognitionInvalidatesTextWorkAndPreservesImageExtraction() {
        val state = state()
        val recognition = state.begin(Work.RECOGNITION)
        val translation = state.begin(Work.TRANSLATION)
        val extraction = state.begin(Work.EXTRACTION)
        val inpaint = state.begin(Work.INPAINT)
        state.translating = true
        state.translateStatusText = "old engine"
        val newer = state.begin(Work.RECOGNITION)
        assertTrue(state.accepts(newer))
        listOf(recognition, translation, inpaint).forEach { assertFalse(state.accepts(it)) }
        assertTrue(state.accepts(extraction))
        assertFalse(state.ocrDone)
        assertFalse(state.translating)
        assertEquals("", state.translateStatusText)
    }

    @Test fun retranslationInvalidatesInpaintWithoutDiscardingExtraction() {
        val state = state()
        val extraction = state.begin(Work.EXTRACTION)
        val oldTranslation = state.begin(Work.TRANSLATION)
        val oldInpaint = state.begin(Work.INPAINT)
        val newTranslation = state.begin(Work.TRANSLATION)
        assertFalse(state.accepts(oldTranslation))
        assertFalse(state.accepts(oldInpaint))
        assertTrue(state.accepts(extraction))
        assertTrue(state.accepts(newTranslation))
    }

    @Test fun operationCannotCrossCaptureEvenWithTheSameRevision() {
        val first = state()
        val second = state()
        val old = first.begin(Work.RECOGNITION)
        second.begin(Work.RECOGNITION)
        assertFalse(second.accepts(old))
    }

    @Test fun newBlocksRetainOnlyValidSelectionAndRebuildTokens() {
        val state = state()
        state.selectionSpans["a"] = TextSelectionSpan("a", 0, 5)
        state.selectionSpans["gone"] = TextSelectionSpan("gone", 0, 4)
        state.showingOriginalIds += listOf("a", "gone")
        state.tokenSelectedIndices += 99
        state.acceptBlocks(listOf(block("a", "hello world")))
        assertEquals(setOf("a"), state.selectionSpans.keys)
        assertEquals(setOf("a"), state.showingOriginalIds)
        assertEquals("hello", state.selectedText())
        assertTrue(state.activeTokens.isNotEmpty())
        assertTrue(state.tokenSelectedIndices.isEmpty())
        assertEquals("hello", state.tokenSelectionOutput())
    }

    @Test fun nativePlainTextUsesTranslationAndPreservesParagraphGaps() {
        val state = state()
        state.blocks = listOf(block("a", "hello", translated = "你好"), block("b", "world", top = 80))
        assertEquals("hello\n\nworld", state.plainText())
        state.switchMode(ScreenAssistantMode.TRANSLATE_INPLACE)
        assertEquals("你好\n\nworld", state.plainText())
        state.blocks = emptyList()
        assertEquals("正在识别…", state.plainText())
        state.ocrDone = true
        assertEquals("当前画面未识别到可提取文字", state.plainText())
    }

    @Test fun collapsedAndPlainTextVisibilityRetainExistingActionSemantics() {
        val state = state()
        state.blocks = listOf(block("a", "hello"))
        state.selectionSpans["a"] = TextSelectionSpan("a", 0, 5)
        state.rebuildTokens()
        state.showTokenTuner = true
        assertTrue(state.visibility().selectionPreview)
        assertTrue(state.visibility().tokens)
        state.showPlainTextLayer = true
        assertFalse(state.visibility().tokens)
        assertFalse(state.visibility().selectionPreview)
        assertTrue(state.visibility().textActions)
        state.panelCollapsed = true
        assertTrue(state.visibility().plainText)
        assertFalse(state.visibility().textActions)
        state.switchMode(ScreenAssistantMode.SUB_IMAGES)
        state.panelCollapsed = false
        assertFalse(state.visibility().images)
        assertFalse(state.visibility().subImageActions)
        state.showSubImageGallery = true
        // Sub-image bottom drawer is intentionally removed in favor of direct canvas image tap.
        assertFalse(state.visibility().images)
        assertFalse(state.visibility().subImageActions)
        assertFalse(state.visibility().translationActions)
        state.adjustingSubImageCrop = true
        state.switchMode(ScreenAssistantMode.TEXT_SELECT)
        assertFalse(state.adjustingSubImageCrop)
    }
    @Test fun newTranslationClearsPreviousTargetEvenWhenNewProviderFails() {
        val state = state()
        state.switchMode(ScreenAssistantMode.TRANSLATE_INPLACE)
        state.blocks = listOf(block("a", "hello", translated = "old target"))
        state.showingOriginalIds += "a"
        state.activeTranslateBlockId = "a"
        val oldPaint = state.begin(Work.INPAINT)
        state.begin(Work.TRANSLATION)
        assertNull(state.blocks.single().translatedText)
        assertEquals("hello", state.plainText())
        assertTrue(state.showingOriginalIds.isEmpty())
        assertNull(state.activeTranslateBlockId)
        assertFalse(state.accepts(oldPaint))
        // Failure publishes only a status; the previous target remains absent.
        state.translateStatusText = "failed"
        assertEquals("hello", state.editorDisplayText())
    }
}
