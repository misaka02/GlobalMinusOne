package com.example.globalminusone.screenassistant

import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.GestureAction
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.ScreenAssistantConfig
import com.example.globalminusone.data.ScreenOcrEngine
import com.example.globalminusone.data.ScreenTranslateEngine
import com.example.globalminusone.data.TraditionalTranslateProvider
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenAssistantPolicyTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun tokenizeForSelection_splitsCjkCharsWhileKeepingWordsAndUrlsIntact() {
        val tokens = ScreenAssistantPolicy.tokenizeForSelection("打开 github.com/open-source 测试 123!")
        assertEquals(
            listOf("打", "开", "github.com/open-source", "测", "试", "123", "!"),
            tokens
        )
    }

    @Test
    fun pickReadableForeground_choosesContrastInkByLuminance() {
        val darkInk = ScreenAssistantPolicy.pickReadableForeground(0xFFF5F7FA.toInt())
        val lightInk = ScreenAssistantPolicy.pickReadableForeground(0xFF141820.toInt())
        assertEquals(0xFF181B20.toInt(), darkInk)
        assertEquals(0xFFFFFFFF.toInt(), lightInk)
    }

    @Test
    fun mergeAndDeduplicateSubImageCandidates_dropsTinyAndMergesHighIoUBoxes() {
        val candidates = listOf(
            ExtractedSubImageCandidate("tiny", 0, 0, 40, 40, "visual", 0.99f),
            ExtractedSubImageCandidate("a1", 100, 100, 300, 260, "visual", 0.72f),
            ExtractedSubImageCandidate("a2", 106, 104, 304, 264, "a11y", 0.95f),
            ExtractedSubImageCandidate("b1", 420, 105, 680, 265, "visual", 0.85f)
        )
        val merged = ScreenAssistantPolicy.mergeAndDeduplicateSubImageCandidates(candidates)
        assertEquals(2, merged.size)
        assertEquals("a2", merged[0].id)
        assertEquals("b1", merged[1].id)
    }

    @Test
    fun parseVisionOcrJson_supportsNormalizedBoxesInsideMarkdownFences() {
        val raw = """
            Here is the OCR result:
            ```json
            [
              {"text": "Hello World", "box_2d": [100, 50, 200, 500]},
              {"text": "标题栏", "x": 0.1, "y": 0.05, "w": 0.5, "h": 0.08}
            ]
            ```
        """.trimIndent()
        val blocks = ScreenAssistantPolicy.parseVisionOcrJson(raw, imageWidth = 1000, imageHeight = 2000)
        assertEquals(2, blocks.size)
        assertEquals("Hello World", blocks[0].text)
        assertEquals(50, blocks[0].leftPx)
        assertEquals(200, blocks[0].topPx)
        assertEquals(500, blocks[0].rightPx)
        assertEquals(400, blocks[0].bottomPx)
        assertEquals("标题栏", blocks[1].text)
        assertEquals(100, blocks[1].leftPx)
        assertEquals(100, blocks[1].topPx)
    }

    @Test
    fun parseCustomHttpOcrJson_supportsPolygonBoxes() {
        val raw = """
            {
              "code": 100,
              "data": [
                {"text": "RapidOCR Line", "box": [[20, 40], [220, 40], [220, 90], [20, 90]]}
              ]
            }
        """.trimIndent()
        val blocks = ScreenAssistantPolicy.parseCustomHttpOcrJson(raw, imageWidth = 1080, imageHeight = 2400)
        assertEquals(1, blocks.size)
        assertEquals("RapidOCR Line", blocks[0].text)
        assertEquals(20, blocks[0].leftPx)
        assertEquals(40, blocks[0].topPx)
        assertEquals(220, blocks[0].rightPx)
        assertEquals(90, blocks[0].bottomPx)
    }

    @Test
    fun parseBatchTranslationJson_supportsArrayObjectAndNestedForms() {
        val arr = ScreenAssistantPolicy.parseBatchTranslationJson("[\"你好\", \"世界\"]", expectedCount = 2)
        assertEquals(mapOf(0 to "你好", 1 to "世界"), arr)

        val obj = ScreenAssistantPolicy.parseBatchTranslationJson("""{"translations":[{"id":0,"translation":"确认"},{"id":9,"translation":"越界忽略"}]}""", expectedCount = 2)
        assertEquals(mapOf(0 to "确认"), obj)

        val map = ScreenAssistantPolicy.parseBatchTranslationJson("""{"0": "第一行", "1": "第二行"}""", expectedCount = 2)
        assertEquals(mapOf(0 to "第一行", 1 to "第二行"), map)
    }

    @Test
    fun parseLexiconAndTranslateOffline_supportsTsvPipeAndJson() {
        val tsv = """
            # comment
            hello world	你好世界
            settings ||| 设置
        """.trimIndent()
        val tsvEntries = ScreenAssistantPolicy.parseLexiconContent(tsv)
        assertEquals(2, tsvEntries.size)

        val jsonMap = ScreenAssistantPolicy.parseLexiconContent("""{"Download":"下载","Cancel":"取消"}""")
        assertEquals(2, jsonMap.size)

        val lexicon = (tsvEntries + jsonMap).associate { it.source to it.target }
        val translated = ScreenAssistantPolicy.translateWithLexicon(
            listOf("Hello World", "Download, Cancel!"),
            lexicon
        )
        assertEquals("你好世界", translated[0])
        assertEquals("下载, 取消!", translated[1])
    }

    @Test
    fun screenAssistantConfigSerialization_excludesSecretsAndPreservesEnums() {
        val state = GlobalMinusOneState(
            advancedConfig = com.example.globalminusone.data.AdvancedConfig(
                screenAssistantConfig = ScreenAssistantConfig(
                    ocrEngine = ScreenOcrEngine.MLKIT_UNBUNDLED,
                    translateEngine = ScreenTranslateEngine.LLM_API,
                    traditionalProvider = TraditionalTranslateProvider.BING_WEB,
                    targetLanguage = "zh-CN",
                    llmBaseUrl = "https://api.openai.com/v1",
                    llmModel = "gpt-4o-mini"
                )
            )
        )
        val encoded = json.encodeToString(state)
        assertFalse(encoded.contains("apiKey", ignoreCase = true))
        assertFalse(encoded.contains("secret", ignoreCase = true))

        val decoded = json.decodeFromString<GlobalMinusOneState>(encoded)
        assertEquals(ScreenOcrEngine.MLKIT_UNBUNDLED, decoded.advancedConfig.screenAssistantConfig.ocrEngine)
        assertEquals(ScreenTranslateEngine.LLM_API, decoded.advancedConfig.screenAssistantConfig.translateEngine)
    }

    @Test
    fun newComponentTypesAndGestureActions_haveUniqueCnumsAndBreenoTargets() {
        val types = listOf(
            ComponentType.ACTION_REGION_SCREENSHOT,
            ComponentType.ACTION_SCREEN_ASSISTANT,
            ComponentType.ACTION_BREENO_TOUCH
        )
        assertEquals(listOf(65, 66, 67), types.map { it.cnum })
        types.forEach { assertEquals(it, ComponentType.fromCnum(it.cnum)) }
        assertTrue(GestureAction.entries.containsAll(listOf(
            GestureAction.REGION_SCREENSHOT,
            GestureAction.SCREEN_ASSISTANT,
            GestureAction.BREENO_TOUCH
        )))
        assertTrue(ScreenAssistantPolicy.knownBreenoCandidates.any { it.packageName == "com.coloros.directui" })
    }
}
