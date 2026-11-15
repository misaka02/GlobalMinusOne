package com.example.globalminusone.ui.panel

import org.junit.Assert.*
import org.junit.Test

class NewInteractionPolicyTest {
    @Test fun addressBarAcceptsWebUrlsAndAddsASchemeToDomains() {
        assertEquals("https://example.com/path", browserDestination(" example.com/path "))
        assertEquals("http://localhost:8080/test", browserDestination("localhost:8080/test"))
        assertEquals("http://192.168.1.2/x", browserDestination("http://192.168.1.2/x"))
    }
    @Test fun searchDoesNotBecomeAnArbitrarySchemeOrUnescapedUrl() {
        assertEquals("https://www.baidu.com/s?wd=%E6%96%87%E4%BB%B6+%26+notes", browserDestination("文件 & notes"))
        assertNull(browserDestination("javascript:alert(1)"))
        assertNull(browserDestination("file:///data/local/private.txt"))
        assertNull(browserDestination("intent://another-app"))
    }
    @Test fun blankAddressOpensStartPage() { assertEquals("about:blank", browserDestination(" ")) }
    @Test fun draggingInsideViewportDoesNotScroll() {
        assertEquals(0f, dragEdgeVelocity(200f, 500f, 64f, 620f), 0f)
        assertEquals(0f, dragEdgeVelocity(100f, 0f, 64f, 620f), 0f)
    }
    @Test fun edgeScrollIsGradualSymmetricAndCappedOutsideViewport() {
        val top = dragEdgeVelocity(32f, 500f, 64f, 620f)
        assertTrue(top < 0f && top > -620f)
        assertEquals(-top, dragEdgeVelocity(468f, 500f, 64f, 620f), .001f)
        assertEquals(-620f, dragEdgeVelocity(-500f, 500f, 64f, 620f), 0f)
        assertEquals(620f, dragEdgeVelocity(1000f, 500f, 64f, 620f), 0f)
    }
    @Test fun measuredDropTargetReordersOnlyTheRequestedId() {
        val original = listOf("short", "tall", "container", "note")
        assertEquals(listOf("tall", "container", "short", "note"), moveLinearPreview(original, "short", "container"))
        assertEquals(listOf("note", "short", "tall", "container"), moveLinearPreview(original, "note", "short"))
        assertEquals(listOf("short", "tall", "container", "note"), original)
    }
    @Test fun staleDropTargetsCannotLoseOrDuplicateACard() {
        val original = listOf("a", "b", "c")
        assertEquals(original, moveLinearPreview(original, "deleted", "b"))
        assertEquals(original, moveLinearPreview(original, "a", "deleted"))
        assertEquals(original, moveLinearPreview(original, "b", "b"))
    }
}
