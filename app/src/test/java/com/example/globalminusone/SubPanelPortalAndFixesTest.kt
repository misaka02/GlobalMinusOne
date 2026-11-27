package com.example.globalminusone

import androidx.compose.ui.geometry.Rect
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.PageConfig
import com.example.globalminusone.data.PageLayoutType
import com.example.globalminusone.data.ThemeConfig
import com.example.globalminusone.data.operations.PageOperations
import com.example.globalminusone.data.operations.SubPanelConfig
import com.example.globalminusone.data.operations.SubPanelOperations
import com.example.globalminusone.data.operations.resolveSubPanelTheme
import com.example.globalminusone.ui.motion.SubPanelTransitionDirection
import com.example.globalminusone.ui.motion.computeSubPanelPortalTransform
import com.example.globalminusone.ui.panel.layout.PageViewportStore
import com.example.globalminusone.ui.panel.resolveSubPanelPortalProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubPanelPortalAndFixesTest {

    @Test
    fun portalTransformOriginatesFromEntryCardAndConvergesToIdentity() {
        val panelRect = Rect(300f, 100f, 1080f, 1900f)
        val anchorRect = Rect(400f, 1500f, 560f, 1660f)

        val start = computeSubPanelPortalTransform(
            progress = 0f,
            direction = SubPanelTransitionDirection.ENTER_FROM_CARD,
            anchorBounds = anchorRect,
            panelBounds = panelRect
        )
        assertEquals(anchorRect.center.x - panelRect.center.x, start.translationX, 0.01f)
        assertEquals(anchorRect.center.y - panelRect.center.y, start.translationY, 0.01f)
        assertTrue(start.scale in 0.22f..0.72f)
        assertTrue(start.portalRimAlpha > 0.4f)

        val mid = computeSubPanelPortalTransform(
            progress = 0.5f,
            direction = SubPanelTransitionDirection.ENTER_FROM_CARD,
            anchorBounds = anchorRect,
            panelBounds = panelRect
        )
        assertTrue(mid.scale > start.scale && mid.scale < 1f)
        assertEquals((anchorRect.center.x - panelRect.center.x) * 0.5f, mid.translationX, 0.01f)

        val end = computeSubPanelPortalTransform(
            progress = 1f,
            direction = SubPanelTransitionDirection.ENTER_FROM_CARD,
            anchorBounds = anchorRect,
            panelBounds = panelRect
        )
        assertEquals(1f, end.scale, 0.0001f)
        assertEquals(0f, end.translationX, 0.0001f)
        assertEquals(0f, end.translationY, 0.0001f)
        assertEquals(1f, end.alpha, 0.0001f)
        assertEquals(0f, end.portalRimAlpha, 0.0001f)

        val fallbackStart = computeSubPanelPortalTransform(
            progress = 0f,
            direction = SubPanelTransitionDirection.ENTER_FROM_CARD,
            anchorBounds = null,
            panelBounds = panelRect
        )
        assertEquals(0.86f, fallbackStart.scale, 0.0001f)
        assertEquals(0f, fallbackStart.translationX, 0.0001f)

        val exitStart = computeSubPanelPortalTransform(
            progress = 0f,
            direction = SubPanelTransitionDirection.EXIT_TO_PARENT,
            anchorBounds = anchorRect,
            panelBounds = panelRect
        )
        assertEquals(0.96f, exitStart.scale, 0.0001f)
        assertEquals(0.82f, exitStart.alpha, 0.0001f)

        val exitEnd = computeSubPanelPortalTransform(
            progress = 1f,
            direction = SubPanelTransitionDirection.EXIT_TO_PARENT,
            anchorBounds = anchorRect,
            panelBounds = panelRect
        )
        assertEquals(1f, exitEnd.scale, 0.0001f)
        assertEquals(1f, exitEnd.alpha, 0.0001f)
    }

    @Test
    fun portalProgressStartsAtZeroSynchronouslyAndViewportRetainsAcrossSubPanels() {
        // Pending LaunchedEffect (startedSequence != sequence) must report 0f so Frame 0 never snaps 1f -> 0f.
        assertEquals(
            0f,
            resolveSubPanelPortalProgress(
                sequence = 2,
                startedSequence = 1,
                direction = SubPanelTransitionDirection.EXIT_TO_PARENT,
                animMs = 240,
                animatedValue = 1f
            ),
            0.0001f
        )
        assertEquals(
            0.45f,
            resolveSubPanelPortalProgress(
                sequence = 2,
                startedSequence = 2,
                direction = SubPanelTransitionDirection.EXIT_TO_PARENT,
                animMs = 240,
                animatedValue = 0.45f
            ),
            0.0001f
        )
        assertEquals(
            1f,
            resolveSubPanelPortalProgress(
                sequence = 2,
                startedSequence = 1,
                direction = SubPanelTransitionDirection.NONE,
                animMs = 240,
                animatedValue = 0.2f
            ),
            0.0001f
        )

        val viewports = PageViewportStore()
        viewports.page("root-1").freeScrollPx = 480
        viewports.page("sub-1").freeScrollPx = 120
        viewports.retain(setOf("root-1", "sub-1"))
        assertEquals(480, viewports.page("root-1").freeScrollPx)
        assertEquals(120, viewports.page("sub-1").freeScrollPx)
        viewports.retain(setOf("root-1"))
        assertEquals(0, viewports.page("sub-1").freeScrollPx)
    }

    @Test
    fun subPanelOperationsAndAppearanceProjectionMaintainIsolation() {
        var nextId = 1
        val genId = { "id-${nextId++}" }
        val base = GlobalMinusOneState(
            pages = listOf(PageConfig(id = "root-1", name = "主页", subPanelId = "")),
            themeConfig = ThemeConfig(panelBgAlphaPercent = 85, isDarkMode = false)
        )

        val createdPair = SubPanelOperations.create(
            current = base,
            name = "工具集",
            inheritGeometry = false,
            defaultLayout = PageLayoutType.GRID,
            newId = genId
        )
        assertNotNull(createdPair)
        val (withSub, subPanel) = createdPair!!
        assertEquals(1, withSub.subPanels.size)
        assertEquals(2, withSub.pages.size)
        assertEquals(subPanel.id, withSub.pages[1].subPanelId)

        // Cannot delete the sole page inside a sub-panel.
        assertNull(PageOperations.delete(withSub.pages, activeIndex = 1, pageIndex = 1))

        // Updating custom appearance including background alpha overrides resolved theme cleanly.
        val updatedState = SubPanelOperations.update(withSub, subPanel.id) { cur ->
            cur.copy(inheritAppearance = false, isDarkMode = true, panelBgAlphaPercent = 40, name = "工作区")
        }!!
        val updatedSub = updatedState.subPanels.single()
        val projected = resolveSubPanelTheme(updatedState.themeConfig, updatedSub)
        assertTrue(projected.isDarkMode)
        assertEquals(40, projected.panelBgAlphaPercent)

        // Entry card attached to root page is pruned when sub-panel is deleted while main panel stays intact.
        val withEntry = updatedState.copy(
            pages = updatedState.pages.map { page ->
                if (page.id == "root-1") {
                    page.copy(
                        components = listOf(
                            ComponentItem(
                                id = "entry-1",
                                type = ComponentType.SUB_PANEL_ENTRY,
                                title = "工作区",
                                subPanelId = subPanel.id
                            )
                        )
                    )
                } else page
            }
        )
        val afterDelete = SubPanelOperations.delete(withEntry, subPanel.id)!!
        assertTrue(afterDelete.subPanels.isEmpty())
        assertEquals(1, afterDelete.pages.size)
        assertTrue(afterDelete.pages.single().components.isEmpty())
    }
}
