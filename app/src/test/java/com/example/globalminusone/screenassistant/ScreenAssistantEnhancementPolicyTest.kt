package com.example.globalminusone.screenassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenAssistantEnhancementPolicyTest {

    @Test
    fun selectPreferredTile_handlesMultipleCandidatesAndPrefersActiveSysuiTile() {
        val candidates = listOf(
            "com.bad.pkg/.FakeTile",
            "com.coloros.directui/.RecognizeTileService",
            "com.oplus.directui/com.oplus.directui.OplusDirectTileService"
        )
        // Without sysui hint: deterministic package order picks coloros first (never null like singleOrNull).
        assertEquals(
            "com.coloros.directui/.RecognizeTileService",
            BreenoTilePolicy.selectPreferredTile(candidates, activeSysuiTiles = null)
        )
        // When user actually placed oplus tile in Quick Settings (short form), prefer the active one.
        assertEquals(
            "com.oplus.directui/com.oplus.directui.OplusDirectTileService",
            BreenoTilePolicy.selectPreferredTile(
                candidates,
                activeSysuiTiles = "wifi,bt,custom(com.oplus.directui/.OplusDirectTileService),cell"
            )
        )
        assertNull(BreenoTilePolicy.selectPreferredTile(listOf("com.other.app/.Tile;rm -rf /")))
    }

    @Test
    fun privilegePolicy_plansShizukuAndRootWithoutUnnecessaryPrompts() {
        assertTrue(
            BreenoPrivilegePolicy.planExecutionOrder(
                rootGranted = false,
                shizukuAuthorized = false,
                shizukuAvailable = true,
                allowPrivilegedShell = false
            ).isEmpty()
        )
        assertEquals(
            listOf(BreenoPrivilegeChannel.SHIZUKU, BreenoPrivilegeChannel.ROOT),
            BreenoPrivilegePolicy.planExecutionOrder(
                rootGranted = false,
                shizukuAuthorized = false,
                shizukuAvailable = true,
                allowPrivilegedShell = true,
                rootDeniedPreviously = false
            )
        )
        assertEquals(
            listOf(BreenoPrivilegeChannel.SHIZUKU),
            BreenoPrivilegePolicy.planExecutionOrder(
                rootGranted = false,
                shizukuAuthorized = true,
                shizukuAvailable = true,
                allowPrivilegedShell = false
            )
        )
        assertEquals(
            listOf(BreenoPrivilegeChannel.ROOT, BreenoPrivilegeChannel.SHIZUKU),
            BreenoPrivilegePolicy.planExecutionOrder(
                rootGranted = true,
                shizukuAuthorized = true,
                shizukuAvailable = true,
                allowPrivilegedShell = true
            )
        )
        assertEquals(
            emptyList<BreenoPrivilegeChannel>(),
            BreenoPrivilegePolicy.planExecutionOrder(
                rootGranted = false,
                shizukuAuthorized = false,
                shizukuAvailable = false,
                allowPrivilegedShell = true,
                rootDeniedPreviously = true
            )
        )
    }

    @Test
    fun ocrGeometry_downscalesOversizedBitmapsAndRescalesBoxes() {
        assertEquals(1440 to 3200, OcrExtensionGeometry.computeScaledDimensions(1440, 3200))

        val (sw, sh) = OcrExtensionGeometry.computeScaledDimensions(5000, 5000, maxDimension = 4096, maxPixels = 16_000_000L)
        assertTrue("scaled width=$sw", sw in 1..4096)
        assertTrue("scaled height=$sh", sh in 1..4096)
        assertTrue("scaled area=${sw.toLong() * sh}", sw.toLong() * sh <= 16_000_000L)

        val mapped = OcrExtensionGeometry.rescaleRect(
            left = sw / 4,
            top = sh / 4,
            right = sw / 2,
            bottom = sh / 2,
            sourceWidth = sw,
            sourceHeight = sh,
            targetWidth = 5000,
            targetHeight = 5000
        )
        assertNotNull(mapped)
        assertTrue(mapped!!.left in 1245..1255)
        assertTrue(mapped.right in 2495..2505)
        assertNull(OcrExtensionGeometry.rescaleRect(10, 10, 10, 20, 100, 100, 200, 200))
    }

    @Test
    fun joinSelectedTokens_preservesSpacingNaturally() {
        val tokens = ScreenAssistantPolicy.tokenizeForSelection("今天天气真好 Hello ColorOS 16 第3章")
        val fallback = "今天天气真好\nHello ColorOS 16 第3章"
        assertEquals(fallback, ScreenAssistantPolicy.joinSelectedTokens(tokens, emptySet(), fallback))

        // Select "今", "天", "天", "气" (indices 0, 1, 2, 3) -> contiguous CJK without spaces.
        assertEquals("今天天气", ScreenAssistantPolicy.joinSelectedTokens(tokens, setOf(0, 1, 2, 3), fallback))

        // Select "Hello", "ColorOS", "16" -> ASCII tokens retain single spaces.
        val helloIdx = tokens.indexOf("Hello")
        assertEquals(
            "Hello ColorOS 16",
            ScreenAssistantPolicy.joinSelectedTokens(tokens, setOf(helloIdx, helloIdx + 1, helloIdx + 2), fallback)
        )

        // Select "今天" + skip "天气" + "真好" -> gap separator preserved.
        assertEquals("今天 真好", ScreenAssistantPolicy.joinSelectedTokens(tokens, setOf(0, 1, 4, 5), fallback))

        // Contiguous "第" + "3" + "章" -> "第3章"
        val chIdx = tokens.indexOf("第")
        assertEquals(
            "第3章",
            ScreenAssistantPolicy.joinSelectedTokens(tokens, setOf(chIdx, chIdx + 1, chIdx + 2), fallback)
        )
    }

    @Test
    fun pickRobustBackgroundArgb_rejectsOutliersAndProducesOpaqueFill() {
        val lightSamples = List(18) { 0xFFF8F9FA.toInt() } + List(5) { 0xFF111111.toInt() }
        val bgLight = ScreenAssistantPolicy.pickRobustBackgroundArgb(lightSamples, alpha = 255)
        assertEquals(0xFF, (bgLight ushr 24) and 0xFF)
        assertTrue(((bgLight ushr 16) and 0xFF) >= 0xF0)
        assertTrue(((bgLight ushr 8) and 0xFF) >= 0xF0)
        assertTrue((bgLight and 0xFF) >= 0xF0)

        val darkSamples = List(18) { 0xFF1A1D24.toInt() } + List(4) { 0xFFEEEEEE.toInt() }
        val bgDark = ScreenAssistantPolicy.pickRobustBackgroundArgb(darkSamples, alpha = 255)
        assertEquals(0xFF, (bgDark ushr 24) and 0xFF)
        assertTrue(((bgDark ushr 16) and 0xFF) <= 0x28)
    }

    @Test
    fun splitDelimitedBatch_handlesCaseFullwidthAndSpacingVariants() {
        val originals = listOf("Hello", "Settings", "Battery")
        val raw = "|| g0 || 你好\n｜｜G1｜｜ 系统设置\n||G 2|| 电池与性能"
        val parsed = ScreenAssistantPolicy.splitDelimitedBatch(raw, originals)
        assertEquals(listOf("你好", "系统设置", "电池与性能"), parsed)
    }

    @Test
    fun inferMyMemorySourceLang_detectsScriptsAccurately() {
        assertEquals("ko", ScreenAssistantPolicy.inferMyMemorySourceLang("안녕하세요", "zh-CN"))
        assertEquals("ja", ScreenAssistantPolicy.inferMyMemorySourceLang("システム設定", "en"))
        assertEquals("ru", ScreenAssistantPolicy.inferMyMemorySourceLang("Привет мир", "en"))
        assertEquals("de", ScreenAssistantPolicy.inferMyMemorySourceLang("Einstellungen öffnen", "en"))
        assertEquals("fr", ScreenAssistantPolicy.inferMyMemorySourceLang("Paramètres système", "en"))
        assertEquals("zh-CN", ScreenAssistantPolicy.inferMyMemorySourceLang("全局负一屏", "en"))
        assertEquals("en", ScreenAssistantPolicy.inferMyMemorySourceLang("Screen Assistant", "zh-CN"))
    }
}
