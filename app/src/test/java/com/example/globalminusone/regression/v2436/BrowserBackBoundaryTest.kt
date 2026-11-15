package com.example.globalminusone.regression.v2436

import com.example.globalminusone.browser.handleBrowserPageBack
import org.junit.Assert.*
import org.junit.Test

class BrowserBackBoundaryTest {
    /** Models WebView's retained back/forward list, including a manually loaded home entry. */
    private class Navigation(initial: List<String>, initialIndex: Int = initial.lastIndex) {
        val entries = initial.toMutableList()
        var index = initialIndex
        var homeLoads = 0
        var nativeBacks = 0
        val url get() = entries[index]

        fun back() = handleBrowserPageBack(url, { index > 0 }, {
            nativeBacks++
            index--
        }, {
            homeLoads++
            while (entries.lastIndex > index) entries.removeAt(entries.lastIndex)
            entries.add("about:blank")
            index++
        })
    }

    @Test fun exhaustedWebHistoryReturnsHomeThenYieldsToBrowserClose() {
        val navigation = Navigation(listOf("https://www.baidu.com/", "https://example.org/article"))
        assertTrue(navigation.back())
        assertEquals("https://www.baidu.com/", navigation.url)
        assertEquals(0, navigation.homeLoads)
        assertTrue(navigation.back())
        assertEquals("about:blank", navigation.url)
        assertEquals(1, navigation.homeLoads)
        // Home still has a native back entry to Baidu: it must yield to the host, not replay it.
        repeat(5) { assertFalse(navigation.back()) }
        assertEquals(1, navigation.nativeBacks)
        assertEquals(1, navigation.homeLoads)
    }

    @Test fun nativeBackToOriginalHomeDoesNotInsertAnotherHomeHistoryEntry() {
        val navigation = Navigation(listOf("about:blank", "https://www.baidu.com/"))
        assertTrue(navigation.back())
        assertEquals("about:blank", navigation.url)
        assertFalse(navigation.back())
        assertEquals(0, navigation.homeLoads)
        assertEquals(2, navigation.entries.size)
    }

    @Test fun manuallyOpenedHomeExitsEvenWithBackAndForwardEntries() {
        val navigation = Navigation(listOf("https://example.org/", "about:blank", "https://www.baidu.com/"), 1)
        assertFalse(navigation.back())
        assertEquals(1, navigation.index)
        assertEquals(0, navigation.nativeBacks)
        assertEquals(0, navigation.homeLoads)
    }

    @Test fun uncreatedOrFailedWebViewStillReturnsToHomeBeforeExit() {
        var url = "https://www.baidu.com/"
        assertTrue(handleBrowserPageBack(url, { false }, { fail("No native view") }, { url = "about:blank" }))
        assertFalse(handleBrowserPageBack(url, { fail("Home must not inspect native history"); false },
            { fail("Home must not traverse history") }, { fail("Home must not reload") }))
    }
}
