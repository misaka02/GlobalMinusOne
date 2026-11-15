package com.example.globalminusone

import com.example.globalminusone.ui.panel.visibleFileWindowHeight
import org.junit.Assert.assertEquals
import org.junit.Test

class FileWindowGeometryTest {
    @Test fun imeIntersectsTheVisibleParentInsteadOfSubtractingFromAnAlreadyResizedParent() {
        assertEquals(538f, visibleFileWindowHeight(1003f, 144f, 1280f, 598), 0f)
        assertEquals(538f, visibleFileWindowHeight(538f, 144f, 1280f, 598), 0f)
        assertEquals(400f, visibleFileWindowHeight(400f, 144f, 1280f, 598), 0f)
    }
    @Test fun hidingImeRestoresTheOriginalAvailableAreaAndExtremeInsetsStayValid() {
        assertEquals(1003f, visibleFileWindowHeight(1003f, 144f, 1280f, 0), 0f)
        assertEquals(1f, visibleFileWindowHeight(1003f, 900f, 1280f, 598), 0f)
    }
}
