package com.example.globalminusone.regression.v2455

import com.example.globalminusone.screenassistant.ScreenAssistantPolicy
import com.example.globalminusone.screenassistant.ScreenAssistantPolicy.CanvasSingleFingerDragAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V2455DownloadScrollAndPassThroughTest {

    @Test
    fun translateAndSubImageModesScrollUnderlyingPageAt1xFullscreen() {
        assertEquals(
            CanvasSingleFingerDragAction.UNDERLYING_SCROLL,
            ScreenAssistantPolicy.classifySingleFingerDrag(
                modeKey = "TRANSLATE_INPLACE",
                isCroppedRegion = false,
                zoomScale = 1.0f,
                hasActiveTextTarget = false,
                hasActiveSubHandle = false,
                canScrollUnderlying = true
            )
        )
        assertEquals(
            CanvasSingleFingerDragAction.UNDERLYING_SCROLL,
            ScreenAssistantPolicy.classifySingleFingerDrag(
                modeKey = "SUB_IMAGES",
                isCroppedRegion = false,
                zoomScale = 1.0f,
                hasActiveTextTarget = false,
                hasActiveSubHandle = false,
                canScrollUnderlying = true
            )
        )
        assertEquals(
            CanvasSingleFingerDragAction.SUB_IMAGE_HANDLE,
            ScreenAssistantPolicy.classifySingleFingerDrag(
                modeKey = "SUB_IMAGES",
                isCroppedRegion = false,
                zoomScale = 1.0f,
                hasActiveTextTarget = false,
                hasActiveSubHandle = true,
                canScrollUnderlying = true
            )
        )
    }

    @Test
    fun textSelectModeRoutesEmptySpaceDragToUnderlyingScrollAndTextHitToSelection() {
        assertEquals(
            CanvasSingleFingerDragAction.UNDERLYING_SCROLL,
            ScreenAssistantPolicy.classifySingleFingerDrag(
                modeKey = "TEXT_SELECT",
                isCroppedRegion = false,
                zoomScale = 1.0f,
                hasActiveTextTarget = false,
                hasActiveSubHandle = false,
                canScrollUnderlying = true
            )
        )
        assertEquals(
            CanvasSingleFingerDragAction.TEXT_SELECTION,
            ScreenAssistantPolicy.classifySingleFingerDrag(
                modeKey = "TEXT_SELECT",
                isCroppedRegion = false,
                zoomScale = 1.0f,
                hasActiveTextTarget = true,
                hasActiveSubHandle = false,
                canScrollUnderlying = true
            )
        )
    }

    @Test
    fun zoomedOrCroppedCanvasKeepsViewportPanInsteadOfScrollingUnderlyingWindow() {
        assertEquals(
            CanvasSingleFingerDragAction.VIEWPORT_PAN,
            ScreenAssistantPolicy.classifySingleFingerDrag(
                modeKey = "TRANSLATE_INPLACE",
                isCroppedRegion = false,
                zoomScale = 1.45f,
                hasActiveTextTarget = false,
                hasActiveSubHandle = false,
                canScrollUnderlying = true
            )
        )
        assertEquals(
            CanvasSingleFingerDragAction.VIEWPORT_PAN,
            ScreenAssistantPolicy.classifySingleFingerDrag(
                modeKey = "SUB_IMAGES",
                isCroppedRegion = true,
                zoomScale = 1.0f,
                hasActiveTextTarget = false,
                hasActiveSubHandle = false,
                canScrollUnderlying = true
            )
        )
    }

    @Test
    fun scrollHintLabelShowsProgressAndDirectionalReleasePrompt() {
        assertEquals(
            "继续滑动以滚动原页面",
            ScreenAssistantPolicy.formatScrollHintLabel(dx = 4f, dy = -12f, triggerThresholdPx = 56f)
        )
        assertEquals(
            "松手向下翻页 ↑",
            ScreenAssistantPolicy.formatScrollHintLabel(dx = 10f, dy = -180f, triggerThresholdPx = 56f)
        )
        assertEquals(
            "松手向上翻页 ↓",
            ScreenAssistantPolicy.formatScrollHintLabel(dx = -8f, dy = 190f, triggerThresholdPx = 56f)
        )
        assertEquals(
            "松手向右翻页 ←",
            ScreenAssistantPolicy.formatScrollHintLabel(dx = -160f, dy = 12f, triggerThresholdPx = 56f)
        )
        assertEquals(
            "松手向左翻页 →",
            ScreenAssistantPolicy.formatScrollHintLabel(dx = 160f, dy = -12f, triggerThresholdPx = 56f)
        )
    }
}
