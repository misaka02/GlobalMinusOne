package com.example.globalminusone.service.screenassistant

import com.example.globalminusone.screenassistant.ScreenAssistantPolicy
import com.example.globalminusone.screenassistant.ScreenTextBlock
import com.example.globalminusone.screenassistant.ScreenTextSelectionPolicy
import com.example.globalminusone.screenassistant.TextSelectionSpan
import com.example.globalminusone.service.ScreenAssistantMode
import kotlin.math.max
import kotlin.math.min

/** Main-thread presentation for one capture. It owns no View, Bitmap, window, or coroutine scope. */
internal class ScreenAssistantPresentation(initialMode: ScreenAssistantMode) {
    /** Unified Breeno Spotlight interaction state across a single canvas. */
    enum class FocusState { IDLE, TEXT_SELECTED, IMAGE_SELECTED, TRANSLATE }

    var mode = initialMode
        private set
    var blocks: List<ScreenTextBlock> = emptyList()
    var translatedBlocks: List<ScreenTextBlock> = emptyList()
    val selectionSpans = linkedMapOf<String, TextSelectionSpan>()

    val focusState: FocusState
        get() = when {
            mode == ScreenAssistantMode.TRANSLATE_INPLACE -> FocusState.TRANSLATE
            selectionSpans.isNotEmpty() -> FocusState.TEXT_SELECTED
            activeSubImageIndex >= 0 -> FocusState.IMAGE_SELECTED
            else -> FocusState.IDLE
        }

    fun translateModeBlocks(): List<ScreenTextBlock> = translatedBlocks.ifEmpty { blocks }
    val showingOriginalIds = hashSetOf<String>()
    val tokenSelectedIndices = linkedSetOf<Int>()
    var activeTokens: List<String> = emptyList()
        private set
    var ocrDone = false
    var subImagesDone = false
    var subImageFailure = ""
    var originalSourceNote = ""
    var ocrStatusText = "正在识别文字…"
    var lastOcrNote = ""
    var lastOcrEngineUsed = ""
    var translating = false
    var translateStatusText = ""
    var showAllOriginalInTranslate = false
    var holdOriginalPreview = false
    var showTextHighlights = true
    var showTextSpotlight = false
    var showTextToolsDock = false
    var showPlainTextLayer = false
    var showTokenTuner = false
    var activeSubImageIndex = -1
    var previewingActiveSubImage = false
    var adjustingSubImageCrop = false
    var showSubImageGallery = false
    var activeTranslateBlockId: String? = null
    var zoomScale = 1f
    var panX = 0f
    var panY = 0f
    var hudOffsetX = 0f
    var hudOffsetY = 0f
    var canvasMoveMode = false
    var panelCollapsed = false
    var dockAtTop = false
    var canvasTouchActive = false

    fun resetViewport() {
        zoomScale = 1f
        panX = 0f
        panY = 0f
    }

    enum class Work { RECOGNITION, TRANSLATION, EXTRACTION, INPAINT }
    class Operation internal constructor(internal val owner: ScreenAssistantPresentation, internal val work: Work, internal val revision: Long)
    private val revisions = LongArray(Work.entries.size)

    fun begin(work: Work): Operation {
        if (work == Work.RECOGNITION) {
            // Translation and inpainting use OCR output. Image extraction uses the captured frame.
            revisions[Work.RECOGNITION.ordinal]++
            revisions[Work.TRANSLATION.ordinal]++
            revisions[Work.INPAINT.ordinal]++
            ocrDone = false
            translating = false
            translatedBlocks = emptyList()
            showingOriginalIds.clear()
            translateStatusText = ""
            lastOcrNote = ""
            lastOcrEngineUsed = ""
            ocrStatusText = "正在识别文字…"
            activeTranslateBlockId = null
            resetViewport()
        } else {
            revisions[work.ordinal]++
            if (work == Work.TRANSLATION) {
                revisions[Work.INPAINT.ordinal]++
                // A failed new request must never leave a previous engine/target translation visible.
                blocks = blocks.map { it.copy(translatedText = null) }
                translatedBlocks = emptyList()
                showingOriginalIds.clear()
                activeTranslateBlockId = null
            }
            if (work == Work.EXTRACTION) {
                subImagesDone = false
                subImageFailure = ""
                originalSourceNote = ""
                previewingActiveSubImage = false
                adjustingSubImageCrop = false
            }
        }
        return Operation(this, work, revisions[work.ordinal])
    }

    fun accepts(operation: Operation): Boolean = operation.owner === this && revisions[operation.work.ordinal] == operation.revision

    fun missingTranslationInputStatus(): String = lastOcrNote.ifBlank { "当前未识别到可翻译文字" }

    fun selectTextSpans(spans: Map<String, TextSelectionSpan>) {
        activeSubImageIndex = -1
        adjustingSubImageCrop = false
        previewingActiveSubImage = false
        showSubImageGallery = false
        selectionSpans.clear()
        selectionSpans.putAll(spans)
        if (selectionSpans.isEmpty()) showTokenTuner = false
        rebuildTokens()
    }

    fun selectSubImage(index: Int) {
        selectionSpans.clear()
        showTokenTuner = false
        showPlainTextLayer = false
        activeTokens = emptyList()
        tokenSelectedIndices.clear()
        activeSubImageIndex = index
        previewingActiveSubImage = false
        adjustingSubImageCrop = false
    }

    fun clearFocus() {
        selectionSpans.clear()
        activeSubImageIndex = -1
        adjustingSubImageCrop = false
        previewingActiveSubImage = false
        showTokenTuner = false
        activeTokens = emptyList()
        tokenSelectedIndices.clear()
    }

    fun switchMode(target: ScreenAssistantMode) {
        mode = target
        showPlainTextLayer = false
        previewingActiveSubImage = false
        adjustingSubImageCrop = false
        if (target != ScreenAssistantMode.TRANSLATE_INPLACE) {
            activeTranslateBlockId = null
        }
    }

    fun selectedText(): String = ScreenTextSelectionPolicy.mergeSelectedSpansText(blocks, selectionSpans)

    fun tokenSelectionOutput(): String = if (!showTokenTuner || tokenSelectedIndices.isEmpty()) {
        selectedText()
    } else {
        ScreenAssistantPolicy.joinSelectedTokens(activeTokens, tokenSelectedIndices, selectedText())
    }

    fun rebuildTokens() {
        tokenSelectedIndices.clear()
        activeTokens = ScreenAssistantPolicy.tokenizeForSelection(selectedText())
    }

    fun acceptBlocks(recognized: List<ScreenTextBlock>) {
        val oldBlocksById = blocks.associateBy { it.id }
        val prevSpans = LinkedHashMap(selectionSpans)
        blocks = recognized
        translatedBlocks = emptyList()
        val byNewId = recognized.associateBy { it.id }
        val validIds = byNewId.keys

        if (prevSpans.isNotEmpty()) {
            val retained = linkedMapOf<String, TextSelectionSpan>()
            for ((oldId, span) in prevSpans) {
                val sameIdNew = byNewId[oldId]
                if (sameIdNew != null && sameIdNew.text.isNotEmpty()) {
                    val s = span.startIndex.coerceIn(0, (sameIdNew.text.length - 1).coerceAtLeast(0))
                    val e = span.endIndex.coerceIn(s + 1, sameIdNew.text.length)
                    retained[oldId] = TextSelectionSpan(oldId, s, e)
                    continue
                }
                val oldBlock = oldBlocksById[oldId] ?: continue
                val matched = recognized
                    .asSequence()
                    .filter { nb -> nb.text.isNotEmpty() && blockIou(oldBlock, nb) >= 0.55f }
                    .maxByOrNull { nb -> blockIou(oldBlock, nb) }
                if (matched != null &&
                    (matched.text == oldBlock.text || matched.text.contains(oldBlock.text) || oldBlock.text.contains(matched.text))
                ) {
                    val s = span.startIndex.coerceIn(0, (matched.text.length - 1).coerceAtLeast(0))
                    val e = span.endIndex.coerceIn(s + 1, matched.text.length)
                    retained[matched.id] = TextSelectionSpan(matched.id, s, e)
                }
            }
            selectionSpans.clear()
            selectionSpans.putAll(retained)
        }
        showingOriginalIds.retainAll(validIds)
        activeTranslateBlockId = null
        rebuildTokens()
    }

    private fun blockIou(a: ScreenTextBlock, b: ScreenTextBlock): Float {
        val il = max(a.leftPx, b.leftPx)
        val it = max(a.topPx, b.topPx)
        val ir = min(a.rightPx, b.rightPx)
        val ib = min(a.bottomPx, b.bottomPx)
        if (ir <= il || ib <= it) return 0f
        val inter = (ir - il).toFloat() * (ib - it).toFloat()
        val areaA = (a.rightPx - a.leftPx).coerceAtLeast(1).toFloat() * (a.bottomPx - a.topPx).coerceAtLeast(1)
        val areaB = (b.rightPx - b.leftPx).coerceAtLeast(1).toFloat() * (b.bottomPx - b.topPx).coerceAtLeast(1)
        val union = areaA + areaB - inter
        return if (union <= 0f) 0f else inter / union
    }

    fun plainText(): String {
        val text = StringBuilder()
        var previousBottom = -1
        val displayList = if (mode == ScreenAssistantMode.TRANSLATE_INPLACE) translateModeBlocks() else blocks
        for (block in displayList) {
            if (text.isNotEmpty()) {
                text.append(if (previousBottom >= 0 && block.topPx - previousBottom > block.heightPx * 1.15f) "\n\n" else "\n")
            }
            text.append(if (mode == ScreenAssistantMode.TRANSLATE_INPLACE && !block.translatedText.isNullOrBlank()) block.translatedText else block.text)
            previousBottom = block.bottomPx
        }
        return text.toString().ifBlank { if (ocrDone) "当前画面未识别到可提取文字" else "正在识别…" }
    }

    /** Scrollable text card content: bilingual original+translation view in translate mode, plain text otherwise. */
    fun editorDisplayText(): String {
        if (mode != ScreenAssistantMode.TRANSLATE_INPLACE) return plainText()
        val text = StringBuilder()
        var previousBottom = -1
        for (block in translateModeBlocks()) {
            if (text.isNotEmpty()) {
                text.append(if (previousBottom >= 0 && block.topPx - previousBottom > block.heightPx * 1.15f) "\n\n" else "\n")
            }
            val tr = block.translatedText?.takeIf { it.isNotBlank() }
            if (tr != null && tr != block.text) {
                text.append("【原文】").append(block.text).append('\n').append("【译文】").append(tr)
            } else {
                text.append(block.text)
            }
            previousBottom = block.bottomPx
        }
        return text.toString().ifBlank { if (ocrDone) "当前画面未识别到可提取文字" else "正在识别…" }
    }

    data class Visibility(
        val plainText: Boolean,
        val selectionPreview: Boolean,
        val tokens: Boolean,
        val textActions: Boolean,
        val translationActions: Boolean,
        val images: Boolean,
        val progress: Boolean,
        val subImageActions: Boolean = images
    )

    fun usesCompactTopPill(): Boolean = !panelCollapsed && (
        (mode == ScreenAssistantMode.SUB_IMAGES && !showSubImageGallery) ||
            (mode == ScreenAssistantMode.TEXT_SELECT && !showTextToolsDock && !showPlainTextLayer && !showTokenTuner)
        )

    fun visibility() = Visibility(
        plainText = showPlainTextLayer,
        selectionPreview = !panelCollapsed && mode == ScreenAssistantMode.TEXT_SELECT && selectionSpans.isNotEmpty() && !showPlainTextLayer && (showTextToolsDock || showTokenTuner),
        tokens = !panelCollapsed && mode != ScreenAssistantMode.TRANSLATE_INPLACE && showTokenTuner && activeTokens.isNotEmpty() && !showPlainTextLayer,
        textActions = !panelCollapsed && mode == ScreenAssistantMode.TEXT_SELECT && (showTextToolsDock || showPlainTextLayer || showTokenTuner),
        translationActions = !panelCollapsed && mode == ScreenAssistantMode.TRANSLATE_INPLACE,
        images = false,
        progress = when (mode) {
            ScreenAssistantMode.SUB_IMAGES -> !subImagesDone
            ScreenAssistantMode.TRANSLATE_INPLACE -> !ocrDone || translating
            ScreenAssistantMode.TEXT_SELECT -> !ocrDone || (focusState == FocusState.IMAGE_SELECTED && !subImagesDone)
        },
        subImageActions = false
    )

    fun statusText(imageCount: Int, originalCount: Int): String = when {
        mode == ScreenAssistantMode.TRANSLATE_INPLACE && translating -> translateStatusText.ifBlank { "正在无痕替换译文…" }
        mode == ScreenAssistantMode.TRANSLATE_INPLACE && translateStatusText.isNotBlank() -> translateStatusText
        focusState == FocusState.IMAGE_SELECTED -> when {
            !subImagesDone -> if (imageCount > 0) "已提取 $imageCount 张图片 · 正在处理…" else "正在提取画面图片…"
            originalCount > 0 -> "已选图片（共 $imageCount 张，含 $originalCount 张单层图）"
            else -> imageSourceStatus("已选图片（共 $imageCount 张 · 仅裁图，点「保存」存图）")
        }
        mode == ScreenAssistantMode.SUB_IMAGES -> when {
            !subImagesDone -> if (imageCount > 0) "已初检 $imageCount 张画面图片 · 正在处理…" else "正在提取画面图片…"
            subImageFailure.isNotBlank() -> subImageFailure
            imageCount == 0 -> imageSourceStatus("提图完成 · 未发现明显照片或图片控件")
            originalCount > 0 -> "已提 $imageCount 张（含 $originalCount 张单层无角标图）· 点卡片保存"
            else -> imageSourceStatus("当前 $imageCount 张为画面裁图")
        }
        !ocrDone && !subImagesDone -> "正在识别文字与提取图片…"
        !ocrDone -> ocrStatusText
        lastOcrNote.isNotBlank() -> lastOcrNote
        else -> ""
    }

    private fun imageSourceStatus(message: String): String =
        if (originalSourceNote.isBlank()) message else "$message\n$originalSourceNote"
}
