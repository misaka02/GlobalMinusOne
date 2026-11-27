package com.example.globalminusone.regression.v2410

import com.example.globalminusone.ui.panel.layout.LinearViewportAnchor
import com.example.globalminusone.ui.panel.layout.PageViewportStore
import com.example.globalminusone.ui.panel.layout.initialFreeViewport
import com.example.globalminusone.ui.panel.layout.initialLinearViewport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PageViewportStateTest {
    @Test
    fun virtualCopiesShareTheirRealPageMemoryButDifferentPagesStayIsolated() {
        val store = PageViewportStore()

        val firstCopy = store.page("page-a")
        firstCopy.freeScrollPx = 480
        val secondCopy = store.page("page-a")
        val otherPage = store.page("page-b")

        assertSame(firstCopy, secondCopy)
        assertNotSame(firstCopy, otherPage)
        assertEquals(480, secondCopy.freeScrollPx)
        assertEquals(0, otherPage.freeScrollPx)
    }

    @Test
    fun retainDropsRemovedPagesAndARecreatedPageGetsFreshMemory() {
        val store = PageViewportStore()
        val retained = store.page("kept")
        val removed = store.page("removed")
        removed.linearAnchor = LinearViewportAnchor("card-old", 90, 2)

        store.retain(setOf("kept"))

        assertSame(retained, store.page("kept"))
        assertNotSame(removed, store.page("removed"))
        assertNull(store.page("removed").linearAnchor)
    }

    @Test
    fun freeViewportStartsAtTheSavedScrollPosition() {
        val store = PageViewportStore()
        store.page("free-page").freeScrollPx = 1_275

        val newScrollState = initialFreeViewport(store.page("free-page"))

        assertEquals(1_275, newScrollState.value)
    }

    @Test
    fun linearViewportStartsAtSavedAnchorAndUsesFallbackWhenAnchorCardWasRemoved() {
        val store = PageViewportStore()
        val memory = store.page("linear-page")
        memory.linearAnchor = LinearViewportAnchor("card-b", 37, 1)

        val restored = initialLinearViewport(memory, listOf("card-a", "card-b", "card-c"))
        assertEquals(1, restored.firstVisibleItemIndex)
        assertEquals(37, restored.firstVisibleItemScrollOffset)

        memory.linearAnchor = LinearViewportAnchor("deleted-card", 51, 2)
        val restoredWithFallback = initialLinearViewport(memory, listOf("card-a", "card-b", "card-c"))
        assertEquals(2, restoredWithFallback.firstVisibleItemIndex)
        assertEquals(51, restoredWithFallback.firstVisibleItemScrollOffset)
    }
}
