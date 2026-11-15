package com.example.globalminusone

import com.example.globalminusone.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContainerPopupPolicyTest {
    @Test fun overlayVisibleFrameCannotWidenPopupBeyondActualHostOrDisplay() {
        val bounds = containerPopupBounds(-100, 24, 1000, 1400, 480, 818, 480, 854, 12)
        assertEquals(ContainerPopupBounds(12, 36, 468, 806), bounds)
        val folder = ComponentItem(type = ComponentType.FOLDER, containerColumns = 4, containerItemSizeDp = 88,
            containerComponents = List(12) { ComponentItem(type = ComponentType.APP_ICON) })
        // Runtime case: 480px at density1.5 must never keep the preferred 376dp width.
        assertEquals(304f, containerPopupWidthDp(folder, (bounds.right - bounds.left) / 1.5f), 0f)
        assertEquals(ContainerPopupBounds(12, 312, 324, 488),
            containerPopupBounds(0, 300, 1000, 500, 336, 818, 480, 854, 12))
    }
    @Test fun shortIconFoldersUseOccupiedColumnsAndRespectNarrowWindows() {
        val folder = ComponentItem(type = ComponentType.FOLDER, containerColumns = 6,
            containerComponents = listOf(ComponentItem(type = ComponentType.APP_ICON)))
        assertEquals(2, containerPopupColumns(folder))
        assertEquals(180f, containerPopupWidthDp(folder, 400f), 0f)
        assertEquals(120f, containerPopupWidthDp(folder, 120f), 0f)
        val many = folder.copy(containerComponents = List(8) { ComponentItem(type = ComponentType.APP_ICON) })
        assertEquals(6, containerPopupColumns(many))
        assertEquals(264f, containerPopupWidthDp(many, 400f), 0f)
    }

    @Test fun mixedCardsKeepUsableWidthAndTheirMeasuredHeight() {
        val text = ComponentItem(type = ComponentType.TEXT_LABEL, w = 24, h = 1, autoHeight = true,
            textContent = "第一行\n第二行\n第三行\n第四行")
        val folder = ComponentItem(type = ComponentType.FOLDER, containerComponents = listOf(text))
        assertEquals(360f, containerPopupWidthDp(folder, 500f), 0f)
        val displayed = arrangeContainerChildren(listOf(text), 24, 336f, 14f, iconColumns = 2).single()
        assertTrue(displayed.h * 14f >= componentDisplayHeightDp(text, 336f, 24))
    }

    @Test fun popupStaysNearTriggerInsteadOfJumpingToItsOppositeSide() {
        assertEquals(ContainerPopupPosition(40, 24), containerPopupPosition(100, 80, 180, 120,
            200, 180, 8, 24, 392, 600, 8))
        assertEquals(ContainerPopupPosition(192, 420), containerPopupPosition(340, 500, 390, 550,
            200, 180, 8, 24, 392, 600, 8))
        // Keyboard leaves only 216px: a tall popup stays within the remaining visible area.
        val p = containerPopupPosition(0, 180, 40, 220, 200, 216, 8, 24, 392, 240, 8)
        assertEquals(ContainerPopupPosition(8, 24), p)
    }
}
