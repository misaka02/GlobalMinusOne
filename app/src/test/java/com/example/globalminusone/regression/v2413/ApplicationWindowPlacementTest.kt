package com.example.globalminusone.regression.v2413

import com.example.globalminusone.service.ApplicationWindowMode
import com.example.globalminusone.service.ApplicationWindowPlacement
import org.junit.Assert.*
import org.junit.Test

class ApplicationWindowPlacementTest {
    @Test fun minimizingAndDockingNeverReplaceRestoredWindowGeometry() {
        val placement = ApplicationWindowPlacement(1080, 2200, 3f)
        placement.moveWindow(placement.window, 20, 30)
        val window = placement.window
        placement.minimize()
        placement.moveIcon(400, 1200)
        placement.dock(false)
        assertEquals(ApplicationWindowMode.RIGHT_EDGE, placement.mode)
        assertEquals(1080, placement.iconBounds().x + placement.iconBounds().width)
        assertEquals(96, placement.iconBounds().width)
        placement.restore()
        assertEquals(ApplicationWindowMode.WINDOW, placement.mode)
        assertEquals(window, placement.window)
    }
    @Test fun iconDraggingSnapsOnlyAtEdgesAndRemainsUsableOnSmallScreens() {
        val placement = ApplicationWindowPlacement(1000, 1800, 2f)
        placement.minimize()
        placement.moveIcon(300, 600)
        placement.finishIconMove()
        assertEquals(ApplicationWindowMode.ICON, placement.mode)
        placement.moveIcon(-900, -900)
        placement.finishIconMove()
        assertEquals(ApplicationWindowMode.LEFT_EDGE, placement.mode)
        assertEquals(0, placement.iconBounds().y)
        placement.floatIcon()
        assertEquals(112, placement.iconBounds().width)
        placement.moveIcon(Int.MAX_VALUE / 2, Int.MAX_VALUE / 2)
        placement.finishIconMove()
        assertEquals(ApplicationWindowMode.RIGHT_EDGE, placement.mode)
        assertTrue(placement.iconBounds().y + placement.iconBounds().height <= 1800)
        placement.reconfigure(40, 60)
        assertTrue(placement.iconBounds().width <= 40)
        assertTrue(placement.iconBounds().y + placement.iconBounds().height <= 60)
    }
    @Test fun closedWindowCannotBeResurrectedByQueuedRestoreOrDrag() {
        val placement = ApplicationWindowPlacement(1000, 1800, 2f)
        val before = placement.window
        placement.close()
        placement.restore(); placement.minimize(); placement.dock(true); placement.floatIcon(); placement.moveIcon(200, 300)
        placement.moveWindow(before, 90, 90); placement.resizeWindow(before, -900, -900)
        assertEquals(ApplicationWindowMode.CLOSED, placement.mode)
        assertEquals(before, placement.window)
    }
    @Test fun absoluteDragDeltasDoNotAccumulateAfterTheWindowMoves() {
        val placement = ApplicationWindowPlacement(1000, 1800, 1f)
        val start = placement.window
        placement.moveWindow(start, 10, 20)
        placement.moveWindow(start, 20, 40)
        assertEquals(start.x + 20, placement.window.x)
        assertEquals(start.y + 40, placement.window.y)
        placement.resizeWindow(placement.window, -9999, -9999)
        assertEquals(240, placement.window.width)
        assertEquals(300, placement.window.height)
    }
    @Test fun rotationClampsBothSurfacesWithoutDiscardingDockSide() {
        val placement = ApplicationWindowPlacement(1080, 2200, 3f)
        placement.minimize(); placement.moveIcon(900, 2000); placement.dock(false)
        placement.reconfigure(2200, 1080)
        assertEquals(ApplicationWindowMode.RIGHT_EDGE, placement.mode)
        val icon = placement.iconBounds(); val window = placement.window
        assertEquals(2200, icon.x + icon.width)
        assertTrue(icon.y >= 0 && icon.y + icon.height <= 1080)
        assertTrue(window.x >= 0 && window.y >= 0)
        assertTrue(window.x + window.width <= 2200 && window.y + window.height <= 1080)
        placement.restore()
        assertEquals(window, placement.window)
    }

    @Test fun contentAspectRatioLockKeepsInnerAppViewportProportionalAcrossResizeAndRotation() {
        val placement = ApplicationWindowPlacement(1080, 2200, 2f)
        val chromeW = 6
        val chromeH = 262
        placement.lockContentAspectRatio(canvasWidth = 900, canvasHeight = 1440, chromeW = chromeW, chromeH = chromeH)
        assertTrue(placement.aspectRatioLocked)
        val expectedRatio = 900f / 1440f
        val initialContentRatio = (placement.window.width - chromeW).toFloat() / (placement.window.height - chromeH)
        assertEquals(expectedRatio, initialContentRatio, 0.01f)

        val start = placement.window
        placement.resizeWindow(start, -220, -40)
        val resizedContentRatio = (placement.window.width - chromeW).toFloat() / (placement.window.height - chromeH)
        assertEquals(expectedRatio, resizedContentRatio, 0.01f)

        placement.reconfigure(2200, 1080)
        val rotatedContentRatio = (placement.window.width - chromeW).toFloat() / (placement.window.height - chromeH)
        assertEquals(expectedRatio, rotatedContentRatio, 0.01f)

        placement.setAspectRatioLocked(false)
        assertFalse(placement.aspectRatioLocked)
        val freeStart = placement.window
        placement.resizeWindow(freeStart, 180, -30)
        assertEquals(freeStart.width + 180, placement.window.width)
    }
}
