package com.example.globalminusone.ui.panel.layout

import org.junit.Assert.assertEquals
import org.junit.Test

class ResidentLoopPagerPolicyTest {

    @Test
    fun `resolveResidentVisiblePages wraps smoothly at both ends`() {
        assertEquals(
            listOf(ResidentVisibleSlot(0, 0f, 1f)),
            resolveResidentVisiblePages(0f, pageCount = 1)
        )

        // Swiping forward from page 0 to page 1 (2-page panel)
        val twoForward = resolveResidentVisiblePages(0.3f, pageCount = 2)
        assertEquals(2, twoForward.size)
        assertEquals(ResidentVisibleSlot(0, 0.3f, 1f), twoForward[0])
        assertEquals(ResidentVisibleSlot(1, -0.7f, 2f), twoForward[1])

        // Swiping backward from page 0 wraps seamlessly to last page (2-page panel)
        val twoBackward = resolveResidentVisiblePages(-0.3f, pageCount = 2)
        assertEquals(2, twoBackward.size)
        assertEquals(ResidentVisibleSlot(1, 0.7f, 1f), twoBackward[0])
        assertEquals(ResidentVisibleSlot(0, -0.3f, 2f), twoBackward[1])

        // 3-page settled and wrap-forward from 2 -> 0
        assertEquals(
            listOf(ResidentVisibleSlot(2, 0f, 1f)),
            resolveResidentVisiblePages(2.0f, pageCount = 3)
        )
        val wrapForward = resolveResidentVisiblePages(2.6f, pageCount = 3)
        assertEquals(2, wrapForward.size)
        assertEquals(2, wrapForward[0].realIndex)
        assertEquals(0.6f, wrapForward[0].pageOffset, 1e-5f)
        assertEquals(1f, wrapForward[0].zIndex, 1e-5f)
        assertEquals(0, wrapForward[1].realIndex)
        assertEquals(-0.4f, wrapForward[1].pageOffset, 1e-5f)
        assertEquals(2f, wrapForward[1].zIndex, 1e-5f)
    }

    @Test
    fun `resolveResidentSnapTarget respects velocity and 0_38f positional threshold`() {
        // Fast forward/backward flicks
        assertEquals(1, resolveResidentSnapTarget(0.12f, dragStartPage = 0, velocityPagesPerSec = 1.4f))
        assertEquals(-1, resolveResidentSnapTarget(-0.12f, dragStartPage = 0, velocityPagesPerSec = -1.4f))
        // Reverse-flick cancels advancing when pulled past negative threshold
        assertEquals(0, resolveResidentSnapTarget(-0.2f, dragStartPage = 0, velocityPagesPerSec = 1.4f))
        assertEquals(0, resolveResidentSnapTarget(0.2f, dragStartPage = 0, velocityPagesPerSec = -1.4f))

        // Slow drags obey 0.38f threshold
        assertEquals(0, resolveResidentSnapTarget(0.37f, dragStartPage = 0, velocityPagesPerSec = 0.2f))
        assertEquals(1, resolveResidentSnapTarget(0.38f, dragStartPage = 0, velocityPagesPerSec = 0.2f))
        assertEquals(-1, resolveResidentSnapTarget(-0.38f, dragStartPage = 0, velocityPagesPerSec = -0.2f))
    }

    @Test
    fun `resolveCircularStep chooses shortest arc unless direction overrides`() {
        assertEquals(-1, resolveCircularStep(currentReal = 0, targetReal = 2, pageCount = 3, direction = 0))
        assertEquals(2, resolveCircularStep(currentReal = 0, targetReal = 2, pageCount = 3, direction = 1))
        assertEquals(1, resolveCircularStep(currentReal = 2, targetReal = 0, pageCount = 3, direction = 0))
        assertEquals(-2, resolveCircularStep(currentReal = 2, targetReal = 0, pageCount = 3, direction = -1))
        assertEquals(0, resolveCircularStep(currentReal = 0, targetReal = 0, pageCount = 1, direction = 1))
    }
}
