package com.example.globalminusone.regression.v2411

import com.example.globalminusone.browser.*
import org.junit.Assert.*
import org.junit.Test

class BrowserHomeTest {
    @Test fun homepageRejectsNonWebAndMalformedStoredLinks() {
        val valid = BrowserLink("站点", "https://example.com")
        assertEquals(listOf(valid), browserHomeBookmarks(listOf(valid,
            BrowserLink("空白", "about:blank"), BrowserLink("脚本", "javascript:alert(1)"),
            BrowserLink("文件", "file:///private"), BrowserLink("搜索词", "ordinary query"))))
    }
    @Test fun homepageDeduplicatesAndBoundsBookmarkTiles() {
        val links = (1..12).map { BrowserLink("站点$it", "https://example.com/$it") }
        assertEquals(links.take(8), browserHomeBookmarks(listOf(links.first()) + links))
    }
    @Test fun emptyTitlesUseAddressAndLongLabelsAreBounded() {
        val url = "https://example.com"
        assertEquals(url, browserHomeBookmarks(listOf(BrowserLink("", url))).single().title)
        assertEquals(80, browserHomeBookmarks(listOf(BrowserLink("字".repeat(100), url))).single().title.length)
    }
    @Test fun monogramKeepsSupplementaryUnicodeIntact() {
        assertEquals("网", browserHomeInitial("  "))
        assertEquals("中", browserHomeInitial(" 中文"))
        assertEquals("🌐", browserHomeInitial("🌐站点"))
    }
}
