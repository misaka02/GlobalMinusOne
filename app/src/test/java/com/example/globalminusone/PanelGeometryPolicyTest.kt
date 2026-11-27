package com.example.globalminusone

import com.example.globalminusone.data.*
import org.junit.Assert.*
import org.junit.Test
import com.example.globalminusone.data.ThemeConfig
import com.example.globalminusone.data.policy.movedPanelTheme
import com.example.globalminusone.data.policy.normalizedPanelGeometry
import com.example.globalminusone.data.policy.panelBounds

class PanelGeometryPolicyTest {
    @Test fun oldDefaultsKeepFullHeightAndFollowOpeningSide() {
        val left = panelBounds(1000f, 2000f, ThemeConfig(), false)
        val right = panelBounds(1000f, 2000f, ThemeConfig(), true)
        assertEquals(700f, left.width, 0f)
        assertEquals(2000f, left.height, 0f)
        assertEquals(0f, left.x, 0f)
        assertEquals(300f, right.x, 0f)
    }
    @Test fun movingEitherSideClampsInsideScreenAndUsesRemainingSpace() {
        listOf(false, true).forEach { right ->
            val theme = ThemeConfig(panelWidthPercent = 60, panelHeightPercent = 60)
            val moved = movedPanelTheme(theme, 1000f, 2000f, right, 9000f, 9000f)
            val bounds = panelBounds(1000f, 2000f, moved, right)
            assertEquals(400f, bounds.x, 0.1f)
            assertEquals(800f, bounds.y, 0.1f)
            assertTrue(bounds.x + bounds.width <= 1000f)
            assertTrue(bounds.y + bounds.height <= 2000f)
        }
    }
    @Test fun invalidImportedGeometryCannotEscapeScreenOrDivideAtFullSize() {
        val bounded = ThemeConfig(panelWidthPercent = -9, panelHeightPercent = 500,
            panelHorizontalOffsetPercent = 900, panelVerticalPositionPercent = -20).normalizedPanelGeometry()
        assertEquals(30, bounded.panelWidthPercent)
        assertEquals(100, bounded.panelHeightPercent)
        val moved = movedPanelTheme(ThemeConfig(panelWidthPercent = 100), 1000f, 2000f, true, 10f, 10f)
        assertEquals(0, moved.panelHorizontalOffsetPercent)
        assertEquals(0f, panelBounds(1000f, 2000f, moved, true).x, 0f)
    }
}
