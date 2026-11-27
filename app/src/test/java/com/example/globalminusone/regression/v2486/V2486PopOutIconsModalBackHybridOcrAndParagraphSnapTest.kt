package com.example.globalminusone.regression.v2486

import com.example.globalminusone.data.policy.AppDrawerCardSpec
import com.example.globalminusone.data.policy.AppKeyboardLauncherSpec
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.policy.LauncherCardPresentationMode
import com.example.globalminusone.data.policy.defaultPageGridSpan
import com.example.globalminusone.data.policy.derivePageGridMetrics
import com.example.globalminusone.data.policy.isContainerGridIconItem
import com.example.globalminusone.data.policy.isSingleIconLauncherItem
import com.example.globalminusone.data.policy.resolvePageGridSpan
import com.example.globalminusone.screenassistant.ScreenOcrCoordinator
import com.example.globalminusone.screenassistant.ScreenTextBlock
import com.example.globalminusone.screenassistant.ScreenTextInpainter
import com.example.globalminusone.screenassistant.ScreenTextSelectionPolicy
import com.example.globalminusone.ui.panel.PanelModalBackHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V2486PopOutIconsModalBackHybridOcrAndParagraphSnapTest {

    @Test
    fun modalBackHandlerDismissesTopFloatingPodFirstBeforeUnderlyingFolderOrPanel() {
        PanelModalBackHandler.clearForTest()
        try {
            var folderDismissed = false
            var podDismissed = false

            PanelModalBackHandler.register("folder_container", PanelModalBackHandler.PRIORITY_FOLDER_BASE) {
                folderDismissed = true
                true
            }
            PanelModalBackHandler.register("floating_keyboard_pod", PanelModalBackHandler.PRIORITY_DIALOG + 5) {
                podDismissed = true
                true
            }

            // First Back: only floating pod consumes Back; folder/panel must stay open.
            assertTrue(PanelModalBackHandler.handleBack())
            assertTrue("Top floating keyboard/drawer pod must dismiss on first Back", podDismissed)
            assertFalse("Underlying folder/panel must NOT close on first Back", folderDismissed)

            // Simulate pod unregistering upon dismissal, then second Back closes folder.
            PanelModalBackHandler.unregister("floating_keyboard_pod")
            assertTrue(PanelModalBackHandler.handleBack())
            assertTrue("Folder must close on second Back after pod dismissed", folderDismissed)

            PanelModalBackHandler.unregister("folder_container")
            assertFalse("No modal layers left should return false so service may close panel", PanelModalBackHandler.handleBack())
        } finally {
            PanelModalBackHandler.clearForTest()
        }
    }

    @Test
    fun popOutSingleIconLaunchersUseCompact1x1GridAndPreserveLegacyInlineMode() {
        val gridMetrics = derivePageGridMetrics(
            availableWidthDp = 320f,
            itemSizeDp = 56,
            itemGapDp = 8,
            columnCount = 4
        )

        val popKeyboard = ComponentItem(
            id = "kb_pop",
            type = ComponentType.APP_KEYBOARD_LAUNCHER,
            w = 8,
            h = 8,
            gridSpanX = 1,
            gridSpanY = 1,
            intentUri = AppKeyboardLauncherSpec(
                presentationMode = LauncherCardPresentationMode.POPUP_ICON
            ).encodeToIntentUri()
        )
        val popDrawer = ComponentItem(
            id = "drawer_pop",
            type = ComponentType.APP_DRAWER_CARD,
            w = 8,
            h = 8,
            gridSpanX = 1,
            gridSpanY = 1,
            intentUri = AppDrawerCardSpec(
                presentationMode = LauncherCardPresentationMode.POPUP_ICON
            ).encodeToIntentUri()
        )
        val legacyInlineKeyboard = ComponentItem(
            id = "kb_inline",
            type = ComponentType.APP_KEYBOARD_LAUNCHER,
            w = 32,
            h = 16,
            gridSpanX = 4,
            gridSpanY = 4,
            intentUri = AppKeyboardLauncherSpec().encodeToIntentUri()
        )
        val inlineDrawer = ComponentItem(
            id = "drawer_inline",
            type = ComponentType.APP_DRAWER_CARD,
            w = 32,
            h = 18,
            gridSpanX = 4,
            gridSpanY = 4,
            intentUri = AppDrawerCardSpec(
                presentationMode = LauncherCardPresentationMode.INLINE_CARD
            ).encodeToIntentUri()
        )

        assertTrue(isSingleIconLauncherItem(popKeyboard))
        assertTrue(isSingleIconLauncherItem(popDrawer))
        assertTrue(isContainerGridIconItem(popKeyboard))
        assertTrue(isContainerGridIconItem(popDrawer))
        assertEquals(1 to 1, resolvePageGridSpan(popKeyboard, gridMetrics))
        assertEquals(1 to 1, resolvePageGridSpan(popDrawer, gridMetrics))

        // Legacy inline keyboard omits &mode= by default and preserves full-width card layout.
        assertFalse(legacyInlineKeyboard.intentUri.contains("mode="))
        assertFalse(isSingleIconLauncherItem(legacyInlineKeyboard))
        assertFalse(isSingleIconLauncherItem(inlineDrawer))
        assertFalse(isContainerGridIconItem(legacyInlineKeyboard))
        assertFalse(isContainerGridIconItem(inlineDrawer))
        assertEquals(4 to 4, defaultPageGridSpan(ComponentType.APP_KEYBOARD_LAUNCHER, 4))
        assertEquals(4 to 4, resolvePageGridSpan(legacyInlineKeyboard, gridMetrics))

        val roundTripDrawerPop = AppDrawerCardSpec.fromIntentUri(popDrawer.intentUri)
        val roundTripDrawerInline = AppDrawerCardSpec.fromIntentUri(inlineDrawer.intentUri)
        assertEquals(LauncherCardPresentationMode.POPUP_ICON, roundTripDrawerPop.presentationMode)
        assertEquals(LauncherCardPresentationMode.INLINE_CARD, roundTripDrawerInline.presentationMode)
    }

    @Test
    fun hybridOcrFusionRecoversMissedHierarchyTextWithoutDuplicatesAndClustersParagraphs() {
        val visualBlocks = listOf(
            ScreenTextBlock("v_title", "Headline News Today", 30, 24, 380, 68, "ocr")
        )
        val hierarchyNodes = listOf(
            ScreenTextBlock("h_dup", "Headline News Today", 32, 26, 378, 66, "accessibility"),
            ScreenTextBlock("h_small", "详情 99+", 330, 92, 415, 114, "accessibility")
        )
        val merged = ScreenOcrCoordinator.mergeHierarchyAndVisualOcrBlocks(
            hierarchy = hierarchyNodes,
            visual = visualBlocks
        )
        assertEquals("Should contain visual headline + missed tiny native node without duplicate", 2, merged.size)
        assertTrue(merged.any { it.text == "Headline News Today" })
        assertTrue(merged.any { it.text == "详情 99+" })

        val darkInk = 0xFF202124.toInt()
        val whiteBg = 0xFFFFFFFF.toInt()
        val header = ScreenTextBlock(
            id = "b0",
            text = "Featured Article",
            leftPx = 120,
            topPx = 40,
            rightPx = 360,
            bottomPx = 80,
            sourceTag = "ocr",
            textColorArgb = darkInk,
            bgColorArgb = whiteBg,
            bgBottomColorArgb = whiteBg,
            alignmentHint = ScreenTextInpainter.ALIGN_CENTER,
            inkHeightPx = 34,
            sourceLineCount = 1
        )
        val line1 = ScreenTextBlock(
            id = "b1",
            text = "The quick brown fox",
            leftPx = 36,
            topPx = 110,
            rightPx = 420,
            bottomPx = 136,
            sourceTag = "ocr",
            textColorArgb = darkInk,
            bgColorArgb = whiteBg,
            bgBottomColorArgb = whiteBg,
            alignmentHint = ScreenTextInpainter.ALIGN_LEFT,
            inkHeightPx = 20,
            sourceLineCount = 1
        )
        val line2 = line1.copy(id = "b2", text = "jumps over the lazy", topPx = 142, bottomPx = 168, rightPx = 416)
        val line3 = line1.copy(id = "b3", text = "dog every morning.", topPx = 174, bottomPx = 200, rightPx = 260)
        val cjk1 = ScreenTextBlock(
            id = "c1",
            text = "深度学习模型在图像",
            leftPx = 36,
            topPx = 260,
            rightPx = 410,
            bottomPx = 286,
            sourceTag = "ocr",
            textColorArgb = darkInk,
            bgColorArgb = whiteBg,
            bgBottomColorArgb = whiteBg,
            alignmentHint = ScreenTextInpainter.ALIGN_LEFT,
            inkHeightPx = 20,
            sourceLineCount = 1
        )
        val cjk2 = cjk1.copy(id = "c2", text = "识别中取得了突破", topPx = 294, bottomPx = 320, rightPx = 320)
        val footer = line1.copy(
            id = "f1",
            text = "Advertisement",
            topPx = 500,
            bottomPx = 524,
            textColorArgb = 0xFFE53935.toInt(),
            bgColorArgb = 0xFF1E1E1E.toInt(),
            bgBottomColorArgb = 0xFF1E1E1E.toInt()
        )

        val clustered = ScreenTextSelectionPolicy.clusterParagraphBlocks(
            listOf(line2, header, cjk2, line1, footer, cjk1, line3)
        )
        assertEquals("Should cluster into 4 blocks (header + Latin 3-line para + CJK 2-line para + footer)", 4, clustered.size)

        val enPara = clustered.first { it.text.contains("quick brown fox") }
        assertEquals(3, enPara.sourceLineCount)
        assertEquals("The quick brown fox jumps over the lazy dog every morning.", enPara.text)

        val cjkPara = clustered.first { it.text.contains("深度学习") }
        assertEquals(2, cjkPara.sourceLineCount)
        assertEquals("深度学习模型在图像识别中取得了突破", cjkPara.text)
    }
}
