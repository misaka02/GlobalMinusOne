package com.example.globalminusone.regression.v2487

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntRect
import com.example.globalminusone.data.AppDrawerCardSpec
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.FloatingPodHorizontalAnchor
import com.example.globalminusone.data.LauncherCardPresentationMode
import com.example.globalminusone.data.POPOUT_CAPABLE_CARD_TYPES
import com.example.globalminusone.data.TriggerSide
import com.example.globalminusone.data.computeFloatingPodPlacement
import com.example.globalminusone.data.isSingleIconLauncherItem
import com.example.globalminusone.data.parseCardPresentationMode
import com.example.globalminusone.data.resolveKeyboardCardColors
import com.example.globalminusone.data.withCardPresentationMode
import com.example.globalminusone.screenassistant.AccessibilityScreenSnapshot
import com.example.globalminusone.screenassistant.ExtractedSubImageCandidate
import com.example.globalminusone.screenassistant.NativeOcrRunner
import com.example.globalminusone.screenassistant.ScreenOcrCoordinator
import com.example.globalminusone.screenassistant.ScreenTextBlock
import com.example.globalminusone.screenassistant.ScreenTextInpainter
import com.example.globalminusone.screenassistant.ScreenTextSelectionPolicy
import com.example.globalminusone.service.ScreenAssistantMode
import com.example.globalminusone.service.screenassistant.ScreenAssistantPresentation
import com.example.globalminusone.ui.AppIcons as Icons
import com.example.globalminusone.ui.panel.CardColorPalette
import com.example.globalminusone.ui.panel.FloatingPodKind
import com.example.globalminusone.ui.panel.FunctionalPodOverlayState
import com.example.globalminusone.ui.panel.KeyboardCandidateOverlayHost
import com.example.globalminusone.ui.panel.KeyboardCandidateOverlayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class V2487PopOutPodsMenuUnificationFastOcrAndWhiteBgTest {

    @Test
    fun floatingPodPlacementAlignsSliderToIconXAndKeepsTopTriggerNearTop() {
        val hostW = 1080
        val hostH = 2400
        val edgeMargin = 24
        val safeTop = 96
        val safeBottom = 96

        // 1. Brightness/Volume slider at middle column near top:
        //    must align horizontally with trigger center X (not pushed flush to left/right edge!)
        //    and keep Y near top (must NOT jump to bottom).
        val sliderW = 210
        val sliderH = 620
        val midIconL = 430
        val midIconR = 590
        val topIconT = 160
        val topIconB = 300
        val sliderTopPlacement = computeFloatingPodPlacement(
            hostWidthPx = hostW,
            hostHeightPx = hostH,
            podWidthPx = sliderW,
            podHeightPx = sliderH,
            anchorLeftPx = midIconL,
            anchorTopPx = topIconT,
            anchorRightPx = midIconR,
            anchorBottomPx = topIconB,
            activeSide = TriggerSide.RIGHT,
            horizontalAnchor = FloatingPodHorizontalAnchor.ALIGN_TRIGGER_X,
            edgeMarginPx = edgeMargin,
            safeTopPx = safeTop,
            safeBottomPx = safeBottom
        )
        val expectedCenteredX = (midIconL + midIconR) / 2 - sliderW / 2
        assertEquals(expectedCenteredX, sliderTopPlacement.x)
        assertTrue(
            "Top-screen slider trigger must stay near top safe margin instead of bottom (y=${sliderTopPlacement.y})",
            sliderTopPlacement.y <= 320
        )

        // 2. Keyboard/Drawer placed in column 0 on a RIGHT-side panel:
        //    DOCK_ACTIVE_SIDE must dock cleanly to right edge rather than flying to left edge!
        val podW = 820
        val podH = 740
        val leftColIconL = 120
        val leftColIconR = 260
        val rightDockedTopPlacement = computeFloatingPodPlacement(
            hostWidthPx = hostW,
            hostHeightPx = hostH,
            podWidthPx = podW,
            podHeightPx = podH,
            anchorLeftPx = leftColIconL,
            anchorTopPx = 180,
            anchorRightPx = leftColIconR,
            anchorBottomPx = 320,
            activeSide = TriggerSide.RIGHT,
            horizontalAnchor = FloatingPodHorizontalAnchor.DOCK_ACTIVE_SIDE,
            edgeMarginPx = edgeMargin,
            safeTopPx = safeTop,
            safeBottomPx = safeBottom
        )
        assertEquals(hostW - podW - edgeMargin, rightDockedTopPlacement.x)
        assertEquals(safeTop, rightDockedTopPlacement.y)

        // 3. Same pod triggered near screen bottom stays at bottom thumb zone, and middle stays middle.
        val midYPlacement = computeFloatingPodPlacement(
            hostWidthPx = hostW,
            hostHeightPx = hostH,
            podWidthPx = podW,
            podHeightPx = podH,
            anchorLeftPx = 800,
            anchorTopPx = 1100,
            anchorRightPx = 940,
            anchorBottomPx = 1240,
            activeSide = TriggerSide.LEFT,
            horizontalAnchor = FloatingPodHorizontalAnchor.DOCK_ACTIVE_SIDE,
            edgeMarginPx = edgeMargin,
            safeTopPx = safeTop,
            safeBottomPx = safeBottom
        )
        assertEquals(edgeMargin, midYPlacement.x)
        assertTrue(midYPlacement.y in 700..900)
    }

    @Test
    fun overlayHostMutualExclusionDismissesPreviousAndIgnoresOffscreenVirtualCopies() {
        val host = KeyboardCandidateOverlayHost()
        val tokenKbdActive = Any()
        val tokenKbdOffscreenCopy = Any()
        val tokenDrawerActive = Any()
        val tokenDrawerOffscreenCopy = Any()
        val kbdColors = resolveKeyboardCardColors(0xFFFFFFFF.toInt(), 0xFF2196F3.toInt(), fallbackDark = true)
        val cardPalette = CardColorPalette(
            textPrimary = Color.White,
            textSecondary = Color.Gray,
            titleColor = Color.White,
            textPlaceholder = Color.LightGray,
            iconBackground = Color.DarkGray,
            trackColor = Color.Gray,
            accent = Color(0xFF2196F3)
        )

        var kbdDismissedCount = 0
        var drawerDismissedCount = 0

        host.publish(
            KeyboardCandidateOverlayState(
                ownerCardId = "kbd_1",
                anchorBoundsInWindow = IntRect(0, 0, 100, 100),
                candidates = emptyList(),
                query = "",
                showAppLabels = true,
                isLoadingApps = false,
                kbdColors = kbdColors,
                accentColor = Color(0xFF2196F3),
                activeSide = TriggerSide.RIGHT,
                surfaceColor = Color.Black,
                podExpanded = true,
                ownerToken = tokenKbdActive,
                onLaunchApp = {},
                onDismissOverlay = { kbdDismissedCount++ }
            )
        )
        assertNotNull(host.activeState)

        // Simulated offscreen LoopPagerPolicy duplicate disposing with the same ownerCardId must NOT clear active keyboard!
        host.unregister(tokenKbdOffscreenCopy)
        assertNotNull("Offscreen virtual page token must not close active keyboard", host.activeState)

        // Opening App Drawer cleanly dismisses active Keyboard (invoking its callback so SideEffect won't re-open it!).
        host.publishFunctionalPod(
            FunctionalPodOverlayState(
                ownerToken = tokenDrawerActive,
                ownerCardId = "drawer_1",
                kind = FloatingPodKind.APP_DRAWER,
                title = "应用抽屉",
                icon = Icons.Rounded.Apps,
                anchorBoundsInWindow = IntRect(0, 0, 100, 100),
                activeSide = TriggerSide.RIGHT,
                horizontalAnchor = FloatingPodHorizontalAnchor.DOCK_ACTIVE_SIDE,
                surfaceColor = Color.Black,
                accentColor = Color(0xFF2196F3),
                kbdColors = kbdColors,
                cardColors = cardPalette,
                appMenuEnv = null,
                widthDp = 300f,
                heightDp = 420f,
                onDismiss = { drawerDismissedCount++ },
                content = {}
            )
        )
        assertEquals(1, kbdDismissedCount)
        assertNull(host.activeState)
        assertNotNull(host.activeFunctionalPod)

        // Offscreen drawer copy calling unregister must NOT dismiss the open App Drawer!
        host.unregisterFunctionalPod(tokenDrawerOffscreenCopy)
        assertNotNull("Offscreen copy must not dismiss active App Drawer", host.activeFunctionalPod)

        // Re-opening keyboard dismisses open App Drawer cleanly.
        host.publish(
            KeyboardCandidateOverlayState(
                ownerCardId = "kbd_1",
                anchorBoundsInWindow = IntRect(0, 0, 100, 100),
                candidates = emptyList(),
                query = "",
                showAppLabels = true,
                isLoadingApps = false,
                kbdColors = kbdColors,
                accentColor = Color(0xFF2196F3),
                activeSide = TriggerSide.RIGHT,
                surfaceColor = Color.Black,
                podExpanded = true,
                ownerToken = tokenKbdActive,
                onLaunchApp = {},
                onDismissOverlay = { kbdDismissedCount++ }
            )
        )
        assertEquals(1, drawerDismissedCount)
        assertNull(host.activeFunctionalPod)
        assertNotNull(host.activeState)
    }

    @Test
    fun allSevenFunctionalCardsRoundTripPopOutAndLegacy1x1DrawerWorksWithoutBreakingV2480() {
        assertEquals(
            setOf(
                ComponentType.APP_KEYBOARD_LAUNCHER,
                ComponentType.APP_DRAWER_CARD,
                ComponentType.BRIGHTNESS_BAR,
                ComponentType.VOLUME_BAR,
                ComponentType.RECENT_APPS_CARD,
                ComponentType.CLIPBOARD_CARD,
                ComponentType.NOTE_CARD,
                ComponentType.APP_WIDGET,
                ComponentType.STAT_BATTERY_DETAIL,
                ComponentType.SINGLE_IMAGE,
                ComponentType.PHOTO_CUBE,
                ComponentType.HITOKOTO_CARD
            ),
            POPOUT_CAPABLE_CARD_TYPES
        )

        for (type in POPOUT_CAPABLE_CARD_TYPES) {
            val base = ComponentItem(type = type, title = type.label)
            val pop = withCardPresentationMode(base, LauncherCardPresentationMode.POPUP_ICON)
            assertTrue("Expected POPUP_ICON for $type", isSingleIconLauncherItem(pop))
            assertEquals(1, pop.gridSpanX)
            assertEquals(1, pop.gridSpanY)
            assertEquals(LauncherCardPresentationMode.POPUP_ICON, parseCardPresentationMode(pop))

            val inline = withCardPresentationMode(pop, LauncherCardPresentationMode.INLINE_CARD)
            assertFalse("Expected INLINE_CARD for $type", isSingleIconLauncherItem(inline))
            assertEquals(LauncherCardPresentationMode.INLINE_CARD, parseCardPresentationMode(inline))
        }

        // Legacy compact 1x1 drawer (h <= 14, empty intentUri) auto-promotes cleanly.
        val legacyCompactDrawer = ComponentItem(
            type = ComponentType.APP_DRAWER_CARD,
            w = 8,
            h = 8,
            gridSpanX = 1,
            gridSpanY = 1,
            intentUri = ""
        )
        assertTrue(isSingleIconLauncherItem(legacyCompactDrawer))

        // V2480 shrunk wide drawer inside folder (h = 40 defaultH, empty intentUri) retains inline classification.
        val v2480WideShrunkDrawer = ComponentItem(
            type = ComponentType.APP_DRAWER_CARD,
            w = 8,
            h = ComponentType.APP_DRAWER_CARD.defaultH,
            gridSpanX = 1,
            gridSpanY = 1,
            intentUri = ""
        )
        assertFalse(isSingleIconLauncherItem(v2480WideShrunkDrawer))

        // Explicit inline drawer ignores grid 1x1 auto-promotion.
        val explicitInlineDrawer = ComponentItem(
            type = ComponentType.APP_DRAWER_CARD,
            w = 8,
            h = 8,
            gridSpanX = 1,
            gridSpanY = 1,
            intentUri = AppDrawerCardSpec(LauncherCardPresentationMode.INLINE_CARD).encodeToIntentUri()
        )
        assertFalse(isSingleIconLauncherItem(explicitInlineDrawer))
    }

    @Test
    fun translationBackfillLocksMultiLineParagraphWidthAndPreservesRawSelectionBlocks() {
        val multiLinePara = ScreenTextBlock(
            id = "para_0",
            text = "The quick brown fox jumps over the lazy dog in the morning.",
            leftPx = 40,
            topPx = 300,
            rightPx = 420,
            bottomPx = 450, // height 150px; v2.4.86 had expanded by 150*6 = +900px sideways!
            renderLeftPx = 40,
            renderTopPx = 300,
            renderRightPx = 420,
            renderBottomPx = 450,
            alignmentHint = ScreenTextInpainter.ALIGN_LEFT,
            inkHeightPx = 34,
            sourceLineCount = 3
        )
        val rightColumnLabel = ScreenTextBlock(
            id = "col2",
            text = "Sidebar",
            leftPx = 560,
            topPx = 310,
            rightPx = 760,
            bottomPx = 344,
            inkHeightPx = 28,
            sourceLineCount = 1
        )
        val (slotL, slotR) = ScreenTextSelectionPolicy.computeTranslateSlotHorizontalBounds(
            block = multiLinePara,
            brLeft = 40,
            brTop = 300,
            brRight = 420,
            brBottom = 450,
            allBlocks = listOf(multiLinePara, rightColumnLabel),
            bitmapWidth = 1080
        )
        assertEquals("Multi-line paragraph must NOT fly sideways beyond its erased rect", 40, slotL)
        assertEquals(420, slotR)

        // Single-line label expands conservatively and stops >= 8px before rightColumnLabel (560).
        val singleLeftLabel = ScreenTextBlock(
            id = "btn_l",
            text = "Settings",
            leftPx = 440,
            topPx = 310,
            rightPx = 540,
            bottomPx = 344,
            inkHeightPx = 26,
            sourceLineCount = 1
        )
        val (sL, sR) = ScreenTextSelectionPolicy.computeTranslateSlotHorizontalBounds(
            block = singleLeftLabel,
            brLeft = 440,
            brTop = 310,
            brRight = 540,
            brBottom = 344,
            allBlocks = listOf(singleLeftLabel, rightColumnLabel),
            bitmapWidth = 1080
        )
        assertEquals(440, sL)
        assertTrue("Single-line translation must never invade neighbor block", sR <= rightColumnLabel.leftPx - 8)

        // ParagraphClusterer must NOT merge short UI buttons stacked vertically or across intervening columns.
        val shortBtn1 = ScreenTextBlock("u1", "Edit", 40, 100, 110, 130, inkHeightPx = 24)
        val shortBtn2 = ScreenTextBlock("u2", "Save", 40, 138, 110, 168, inkHeightPx = 24)
        assertEquals(2, ScreenTextSelectionPolicy.clusterParagraphBlocks(listOf(shortBtn1, shortBtn2)).size)

        // Presentation keeps raw TEXT_SELECT blocks intact separately from clustered translatedBlocks.
        val pres = ScreenAssistantPresentation(ScreenAssistantMode.TEXT_SELECT)
        pres.acceptBlocks(listOf(singleLeftLabel, rightColumnLabel))
        pres.translatedBlocks = listOf(singleLeftLabel.copy(id = "para_0", translatedText = "设置"))
        assertEquals(2, pres.blocks.size)
        assertEquals(1, pres.translateModeBlocks().size)
    }

    @Test
    fun fastWasmOcrUsesBoundedJpegSizeAndSafeNativeSkipBoxes() {
        val (fullW, fullH) = NativeOcrRunner.computeFrameDimensions(1260, 2800)
        assertTrue("Max dimension must stay <= 1440px for <1s OCR", maxOf(fullW, fullH) <= 1440)
        assertTrue("Total pixels must stay <= 1.65MP", fullW.toLong() * fullH.toLong() <= 1_650_000L)

        // Small crops preserve 1:1 native pixels.
        val (cropW, cropH) = NativeOcrRunner.computeFrameDimensions(600, 400)
        assertEquals(600, cropW)
        assertEquals(400, cropH)

        // buildSkipNativeBlocks excludes contentDescription wrappers and text overlapping media thumbnails.
        val mediaThumb = ExtractedSubImageCandidate("m1", 36, 120, 396, 345, "媒体缩略图区域", 0.94f)
        val directUiTitle = ScreenTextBlock(
            id = "a11y_0",
            text = "如何证明黎曼猜想",
            leftPx = 420,
            topPx = 140,
            rightPx = 980,
            bottomPx = 188,
            sourceTag = "无障碍节点",
            isDirectRenderedText = true
        )
        val thumbOverlayBadge = ScreenTextBlock(
            id = "a11y_1",
            text = "2:24",
            leftPx = 310,
            topPx = 300,
            rightPx = 385,
            bottomPx = 335,
            sourceTag = "无障碍节点",
            isDirectRenderedText = true
        )
        val descOnlyContainer = ScreenTextBlock(
            id = "a11y_2",
            text = "Card accessibility summary",
            leftPx = 30,
            topPx = 110,
            rightPx = 1040,
            bottomPx = 360,
            sourceTag = "无障碍节点",
            isDirectRenderedText = false
        )
        val snap = AccessibilityScreenSnapshot(
            textNodes = listOf(directUiTitle, thumbOverlayBadge, descOnlyContainer),
            mediaNodes = listOf(mediaThumb)
        )
        val skipBlocks = ScreenOcrCoordinator.buildSkipNativeBlocks(snap)
        assertEquals(listOf(directUiTitle), skipBlocks)

        val mergedWithEmptyVisual = ScreenOcrCoordinator.mergeHierarchyAndVisualOcrBlocks(
            hierarchy = listOf(directUiTitle),
            visual = emptyList()
        )
        assertEquals(1, mergedWithEmptyVisual.size)
        assertEquals(directUiTitle.text, mergedWithEmptyVisual.single().text)
    }
}
