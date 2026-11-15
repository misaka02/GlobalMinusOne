package com.example.globalminusone.regression.v2418

import com.example.globalminusone.browser.ChromeViewportPolicy
import org.junit.Assert.*
import org.junit.Test

class EmbeddedViewportFloorTest {
    @Test fun smallestHostStillHasPhoneSizedLogicalCanvas() {
        for ((hostW, hostH) in listOf(120 to 120, 160 to 240, 652 to 1100)) {
            val (w, h) = ChromeViewportPolicy.appCanvasSize(hostW, hostH)
            val dpi = ChromeViewportPolicy.appWindowDensityDpi(w, h, 480)
            assertTrue(w * 160f / dpi >= 376f)
            assertTrue(h * 160f / dpi >= 580f)
            assertTrue(w >= hostW && h >= hostH)
        }
    }
}
