package com.example.globalminusone.regression.v2450

import com.example.globalminusone.data.ScreenAssistantConfig
import com.example.globalminusone.data.ScreenTranslateEngine
import com.example.globalminusone.screenassistant.ExtractedSubImageCandidate
import com.example.globalminusone.screenassistant.ScreenAssistantPolicy
import com.example.globalminusone.screenassistant.ScreenCaptureHub
import com.example.globalminusone.screenassistant.ScreenTextBlock
import com.example.globalminusone.ui.settings.PermissionVisualKind
import com.example.globalminusone.ui.settings.SettingsSearchIndex
import com.example.globalminusone.ui.settings.resolvePermissionVisualKind
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V2450FixesTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun permissionVisualKind_distinguishesGrantedWarningMissingAndSystemPages() {
        assertEquals(PermissionVisualKind.GRANTED, resolvePermissionVisualKind("悬浮窗»", granted = true))
        assertEquals(PermissionVisualKind.MISSING, resolvePermissionVisualKind("手机存储»", granted = false))
        assertEquals(PermissionVisualKind.WARNING, resolvePermissionVisualKind("辅助(需重开)»", granted = false))
        assertEquals(PermissionVisualKind.SYSTEM_PAGE, resolvePermissionVisualKind("设备管理»", granted = false))
        assertEquals(PermissionVisualKind.SYSTEM_PAGE, resolvePermissionVisualKind("后台弹出界面»", granted = true))
    }

    @Test
    fun mergeAndDeduplicateSubImageCandidates_suppressesContainedNoiseFragments() {
        val candidates = listOf(
            ExtractedSubImageCandidate("cover_full", 80, 200, 620, 640, "界面图片控件", 0.94f),
            ExtractedSubImageCandidate("inner_fragment", 180, 320, 480, 520, "画面图像区域", 0.86f),
            ExtractedSubImageCandidate("side_card", 680, 200, 1020, 640, "界面图片控件", 0.90f)
        )
        val merged = ScreenAssistantPolicy.mergeAndDeduplicateSubImageCandidates(candidates)
        assertEquals(2, merged.size)
        assertTrue(merged.any { it.id == "cover_full" })
        assertTrue(merged.any { it.id == "side_card" })
        assertFalse(merged.any { it.id == "inner_fragment" })
    }

    @Test
    fun uiAutomatorSnapshotParser_recognizesComposeImageRoleAndAsyncImage() {
        val xml = """
            <hierarchy rotation="0">
              <node package="com.example.app" class="coil.compose.AsyncImage" bounds="[60,200][500,620]" />
              <node package="com.example.app" class="android.view.View" role-description="image" bounds="[540,200][1000,620]" />
            </hierarchy>
        """.trimIndent()
        val parsed = ScreenCaptureHub.parseXml(xml, screenW = 1080, screenH = 2160)
        assertEquals(2, parsed.mediaNodes.size)
    }

    @Test
    fun bilibiliIndexTranslate_serializesCleanlyAndStripsThinkingBlocks() {
        val cfg = ScreenAssistantConfig(translateEngine = ScreenTranslateEngine.BILIBILI_INDEX)
        val encoded = json.encodeToString(cfg)
        val decoded = json.decodeFromString<ScreenAssistantConfig>(encoded)
        assertEquals(ScreenTranslateEngine.BILIBILI_INDEX, decoded.translateEngine)

        val rawResponse = """
            <think>
            translating item 0 and 1 carefully...
            </think>
            ```json
            {"0":"设置与权限","1":"保存成功"}
            ```
        """.trimIndent()
        val parsed = ScreenAssistantPolicy.parseBatchTranslationJson(rawResponse, expectedCount = 2)
        assertEquals("设置与权限", parsed[0])
        assertEquals("保存成功", parsed[1])

        val unclosed = "<think>draft still thinking\n"
        assertEquals("", ScreenAssistantPolicy.stripThinkingTags(unclosed))
        assertTrue(ScreenAssistantPolicy.isBilibiliIndexEndpoint("https://index-translate.bilibili.com/v1"))
    }

    @Test
    fun settingsSearch_indexesBilibiliAndDeepSeekTranslationEntries() {
        val biliHits = SettingsSearchIndex.search("bilibili")
        val deepseekHits = SettingsSearchIndex.search("deepseek")
        assertTrue(biliHits.any { it.id == "ocr_translate_engine" })
        assertTrue(deepseekHits.any { it.id == "ocr_translate_engine" })
    }
}
