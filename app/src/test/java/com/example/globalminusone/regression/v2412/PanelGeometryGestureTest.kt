package com.example.globalminusone.regression.v2412

import com.example.globalminusone.data.ThemeConfig
import com.example.globalminusone.data.panelBounds
import com.example.globalminusone.ui.panel.*
import org.junit.Assert.*
import org.junit.Test

class PanelGeometryGestureTest {
    private val initial = ThemeConfig(panelWidthPercent = 60, panelHeightPercent = 50,
        panelHorizontalOffsetPercent = 25, panelVerticalPositionPercent = 50)

    @Test fun movingUsesScreenCoordinatesOnEitherPanelSideAndCommitsThePreviewOnce() {
        for (right in listOf(false, true)) {
            val before = panelBounds(1000f, 2000f, initial, right)
            val drag = PanelGeometryGesture(initial, 1000f, 2000f, right, PanelGeometryHandle.MOVE)
            val preview = requireNotNull(drag.update(100f, 100f))
            val after = panelBounds(1000f, 2000f, preview, right)
            assertEquals(before.x + 100f, after.x, 1f)
            assertEquals(before.y + 100f, after.y, 1f)
            val result = requireNotNull(drag.finish(false))
            assertEquals(preview, result.theme)
            assertTrue(result.persist)
            assertNull(drag.finish(false))
            assertNull(drag.update(200f, 200f))
        }
    }
    @Test fun cornerResizePreservesTheOppositeCornerWithinStoredPercentPrecision() {
        val before = panelBounds(1000f, 2000f, initial, false)
        val drag = PanelGeometryGesture(initial, 1000f, 2000f, false, PanelGeometryHandle.TOP_LEFT)
        val result = requireNotNull(drag.update(100f, 200f))
        val after = panelBounds(1000f, 2000f, result, false)
        assertEquals(before.x + before.width, after.x + after.width, 10f)
        assertEquals(before.y + before.height, after.y + after.height, 20f)
        assertTrue(after.width < before.width)
        assertTrue(after.height < before.height)
    }
    @Test fun extremeResizesRemainOnScreenAndRespectMinimumSize() {
        PanelGeometryHandle.entries.filter { it != PanelGeometryHandle.MOVE }.forEach { handle ->
            for (delta in listOf(-100000f, 100000f)) {
                val drag = PanelGeometryGesture(initial, 1000f, 2000f, false, handle)
                val bounds = panelBounds(1000f, 2000f, requireNotNull(drag.update(delta, delta)), false)
                assertTrue(bounds.width >= 300f && bounds.height >= 600f)
                assertTrue(bounds.x >= 0 && bounds.y >= 0)
                assertTrue(bounds.x + bounds.width <= 1000.1f && bounds.y + bounds.height <= 2000.1f)
            }
        }
    }
    @Test fun cancellationRollsBackAndAnUnmovedHandleDoesNotPersist() {
        val drag = PanelGeometryGesture(initial, 1000f, 2000f, false, PanelGeometryHandle.BOTTOM_RIGHT)
        drag.update(100f, 100f)
        val cancelled = requireNotNull(drag.finish(true))
        assertEquals(initial, cancelled.theme)
        assertFalse(cancelled.persist)
        assertFalse(requireNotNull(PanelGeometryGesture(initial, 1000f, 2000f, false,
            PanelGeometryHandle.MOVE).finish(false)).persist)
    }
    @Test fun floatingToolsStayReachableAfterTheHostShrinks() {
        assertEquals(EditToolsPosition(0f, 0f), clampEditToolsPosition(EditToolsPosition(500f, 500f), 360f, 48f, 300f, 40f))
        assertEquals(EditToolsPosition(0f, 352f), clampEditToolsPosition(EditToolsPosition(-10f, 500f), 200f, 48f, 300f, 400f))
    }
}
