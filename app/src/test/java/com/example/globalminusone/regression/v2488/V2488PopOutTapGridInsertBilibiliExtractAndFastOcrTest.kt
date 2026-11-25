package com.example.globalminusone.regression.v2488

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntRect
import com.example.globalminusone.data.ComponentInsertionOperations
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.ContainerLayoutType
import com.example.globalminusone.data.FloatingPodHorizontalAnchor
import com.example.globalminusone.data.LauncherCardPresentationMode
import com.example.globalminusone.data.PageConfig
import com.example.globalminusone.data.PageLayoutType
import com.example.globalminusone.data.TriggerSide
import com.example.globalminusone.data.arrangePageGridComponents
import com.example.globalminusone.data.resolveKeyboardCardColors
import com.example.globalminusone.data.withCardPresentationMode
import com.example.globalminusone.screenassistant.AccessibilityScreenSnapshot
import com.example.globalminusone.screenassistant.ExtractedSubImageCandidate
import com.example.globalminusone.screenassistant.NativeOcrRunner
import com.example.globalminusone.screenassistant.ScreenOcrCoordinator
import com.example.globalminusone.screenassistant.ScreenTextBlock
import com.example.globalminusone.ui.AppIcons as Icons
import com.example.globalminusone.ui.panel.CardColorPalette
import com.example.globalminusone.ui.panel.FloatingPodKind
import com.example.globalminusone.ui.panel.FunctionalPodOverlayState
import com.example.globalminusone.ui.panel.KeyboardCandidateOverlayHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

class V2488PopOutTapGridInsertBilibiliExtractAndFastOcrTest {

    @Test
    fun insertCardAboveOnPinnedGridPagePlacesNewCardAboveAnchorInsteadOfBottom() {
        val c1 = ComponentItem(
            id = "c1",
            type = ComponentType.APP_ICON,
            title = "微信",
            gridCol = 0,
            gridRow = 0,
            gridSpanX = 1,
            gridSpanY = 1
        )
        val c2 = ComponentItem(
            id = "c2",
            type = ComponentType.BRIGHTNESS_BAR,
            title = "亮度条",
            gridCol = 0,
            gridRow = 2,
            gridSpanX = 2,
            gridSpanY = 1
        )
        val c3 = ComponentItem(
            id = "c3",
            type = ComponentType.VOLUME_BAR,
            title = "音量条",
            gridCol = 0,
            gridRow = 3,
            gridSpanX = 2,
            gridSpanY = 1
        )
        val page = PageConfig(
            id = "p_grid",
            name = "网格页",
            layoutType = PageLayoutType.GRID,
            components = listOf(c1, c2, c3)
        )

        val newPop = withCardPresentationMode(
            ComponentItem(id = "new_pop", type = ComponentType.APP_DRAWER_CARD, title = "应用抽屉"),
            LauncherCardPresentationMode.POPUP_ICON
        )
        val result = ComponentInsertionOperations.placeMenu(
            page = page,
            containerId = null,
            item = newPop,
            beforeId = "c2",
            columns = 32,
            nextY = { 20 },
            rows = { 2 }
        )
        assertNotNull(result)
        val byId = result!!.components.associateBy { it.id }
        val inserted = byId.getValue("new_pop")
        val movedC2 = byId.getValue("c2")
        val movedC3 = byId.getValue("c3")
        assertEquals(0, inserted.gridCol)
        assertEquals(2, inserted.gridRow)
        assertEquals(3, movedC2.gridRow)
        assertEquals(4, movedC3.gridRow)

        val arranged = arrangePageGridComponents(components = result.components, availableWidthDp = 360f)
        val posById = arranged.placements.associateBy { it.item.id }
        assertTrue(
            "Inserted card must render ABOVE anchor c2, not pushed to bottom (${posById["new_pop"]?.row} vs ${posById["c2"]?.row})",
            posById.getValue("new_pop").row < posById.getValue("c2").row
        )
        assertTrue(
            "Shifted c2 must stay above c3",
            posById.getValue("c2").row < posById.getValue("c3").row
        )

        // Container GRID insert above child2
        val child1 = ComponentItem(id = "ch1", type = ComponentType.APP_ICON, title = "A", gridCol = 0, gridRow = 0)
        val child2 = ComponentItem(id = "ch2", type = ComponentType.APP_ICON, title = "B", gridCol = 1, gridRow = 1)
        val folder = ComponentItem(
            id = "f1",
            type = ComponentType.FOLDER,
            title = "文件夹",
            containerLayout = ContainerLayoutType.GRID,
            containerColumns = 4,
            containerComponents = listOf(child1, child2)
        )
        val folderPage = page.copy(components = listOf(folder))
        val updatedTree = ComponentInsertionOperations.insert(
            page = folderPage,
            container = folder,
            item = newPop.copy(id = "ch_new"),
            beforeId = "ch2",
            columns = 32,
            rows = 2
        )
        assertNotNull(updatedTree)
        val updatedFolder = updatedTree!!.single()
        val fById = updatedFolder.containerComponents.associateBy { it.id }
        assertEquals(1, fById.getValue("ch_new").gridCol)
        assertEquals(1, fById.getValue("ch_new").gridRow)
        assertEquals(2, fById.getValue("ch2").gridRow)
    }

    @Test
    fun buildWasmSkipNativeBlocksAcceptsWrappedMultiLineTextAndEncodesUpTo96Boxes() {
        val mediaThumb = ExtractedSubImageCandidate("m1", 36, 140, 396, 365, "媒体缩略图区域", 0.94f)
        val multiLineTitle = ScreenTextBlock(
            id = "a11y_title_2line",
            text = "Claude研究模型 vs 黎曼\n（全能力对比）",
            leftPx = 420,
            topPx = 150,
            rightPx = 980,
            bottomPx = 265,
            sourceTag = "无障碍节点",
            isDirectRenderedText = true
        )
        val mediaText = ScreenTextBlock(
            id = "a11y_badge",
            text = "1:02",
            leftPx = 310,
            topPx = 320,
            rightPx = 385,
            bottomPx = 355,
            sourceTag = "无障碍节点",
            isDirectRenderedText = true
        )
        val skip = ScreenOcrCoordinator.buildSkipNativeBlocks(
            AccessibilityScreenSnapshot(
                textNodes = listOf(multiLineTitle, mediaText),
                mediaNodes = listOf(mediaThumb)
            )
        )
        assertEquals(listOf(multiLineTitle), skip)

        val manyNodes = (0 until 105).map { idx ->
            ScreenTextBlock(
                id = "a11y_$idx",
                text = "标题行$idx",
                leftPx = 420,
                topPx = 10 + idx * 20,
                rightPx = 980,
                bottomPx = 28 + idx * 20,
                sourceTag = "无障碍节点",
                isDirectRenderedText = true
            )
        }
        val cappedSkip = ScreenOcrCoordinator.buildSkipNativeBlocks(
            AccessibilityScreenSnapshot(textNodes = manyNodes, mediaNodes = emptyList())
        )
        assertEquals(96, cappedSkip.size)
        val encoded = NativeOcrRunner.encodeSkipBoxes(
            blocks = cappedSkip,
            srcWidth = 1080,
            srcHeight = 2400,
            frameWidth = 720,
            frameHeight = 1600
        )
        assertNotNull(encoded)
        assertEquals(96 * 4, encoded!!.size)
    }

    @Test
    fun popOutWrapperUsesCompositionTrackedStateAndGridTargetIncludesSingleIcon() {
        val host = KeyboardCandidateOverlayHost()
        val kbdColors = resolveKeyboardCardColors(0xFF1F2329.toInt(), 0xFF2196F3.toInt(), fallbackDark = false)
        val cardPalette = CardColorPalette(
            textPrimary = Color(0xFF1F2329),
            textSecondary = Color(0xFF4E5969),
            titleColor = Color(0xFF1F2329),
            textPlaceholder = Color(0xFF86909C),
            iconBackground = Color(0xFFF2F3F5),
            trackColor = Color(0xFFE5E6EB),
            accent = Color(0xFF2196F3)
        )
        val drawerToken = Any()
        var dismissed = false
        host.publishFunctionalPod(
            FunctionalPodOverlayState(
                ownerToken = drawerToken,
                ownerCardId = "drawer_1",
                kind = FloatingPodKind.APP_DRAWER,
                title = "应用抽屉",
                icon = Icons.Rounded.Apps,
                anchorBoundsInWindow = IntRect.Zero,
                activeSide = TriggerSide.RIGHT,
                horizontalAnchor = FloatingPodHorizontalAnchor.DOCK_ACTIVE_SIDE,
                surfaceColor = Color.White,
                accentColor = Color(0xFF2196F3),
                kbdColors = kbdColors,
                cardColors = cardPalette,
                appMenuEnv = null,
                widthDp = -1f,
                heightDp = -1f,
                showHeader = true,
                onDismiss = { dismissed = true },
                content = {}
            )
        )
        assertNotNull("Zero initial anchor must still publish pod to overlayHost", host.activeFunctionalPod)
        host.activeFunctionalPod!!.onDismiss()
        assertTrue(dismissed)

        fun resolveSourceFile(rel: String): File {
            val fromApp = File(rel)
            return if (fromApp.isFile) fromApp else File("app/$rel")
        }

        val functionalPodSrc = resolveSourceFile("src/main/java/com/example/globalminusone/ui/panel/FloatingFunctionalPod.kt").readText()
        val hostPodSrc = resolveSourceFile("src/main/java/com/example/globalminusone/ui/panel/FloatingPodOverlayHost.kt").readText()
        assertFalse(
            "FloatingFunctionalPod must not isolate pod publishing inside SideEffect",
            functionalPodSrc.contains("SideEffect {")
        )
        assertFalse(
            "FloatingPodOverlayHost must not isolate pod publishing inside SideEffect",
            hostPodSrc.contains("SideEffect {")
        )
        assertTrue(
            "KeyboardCandidateOverlayLayer must use BoxWithConstraints fallback dimensions",
            hostPodSrc.contains("BoxWithConstraints(")
        )

        val gridSrc = resolveSourceFile("src/main/java/com/example/globalminusone/ui/panel/GridLayoutPage.kt").readText()
        assertTrue(
            "GridLayoutPage appTarget must include single-icon pop-out launchers",
            gridSrc.contains("isAppContextMenuCardType(savedItem.type) ||") &&
                gridSrc.contains("isSingleIconLauncherItem(savedItem)")
        )

        val nestedSrc = resolveSourceFile("src/main/java/com/example/globalminusone/ui/panel/NestedContainerComponents.kt").readText()
        assertTrue(
            "NestedContainerComponents appTarget must include single-icon pop-out launchers",
            nestedSrc.contains("isAppContextMenuCardType(child.type) ||") &&
                nestedSrc.contains("isSingleIconLauncherItem(child)")
        )
    }
}
