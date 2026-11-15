package com.example.globalminusone.regression

import com.example.globalminusone.browser.BrowserAdBlocker
import com.example.globalminusone.browser.BrowserCallbackLease
import com.example.globalminusone.browser.BrowserLink
import com.example.globalminusone.browser.BrowserNightModeEngine
import com.example.globalminusone.browser.BrowserSearchEngine
import com.example.globalminusone.browser.desktopAgent
import com.example.globalminusone.browser.desktopPreferredUrl
import com.example.globalminusone.browser.parseReaderArticleJson
import com.example.globalminusone.browser.recordBrowserVisit
import com.example.globalminusone.browser.resolveBrowserDestination
import org.junit.Assert.*
import org.junit.Test

class BrowserNavigationTest {
    @Test fun searchAndDomainUseDifferentActualDestinations() {
        assertEquals("https://www.baidu.com/s?wd=hello+world", resolveBrowserDestination("hello world"))
        assertEquals("https://cn.bing.com/search?q=hello+world", resolveBrowserDestination("hello world", BrowserSearchEngine.BING))
        assertEquals("https://duckduckgo.com/?q=kotlin+compose", resolveBrowserDestination("kotlin compose", BrowserSearchEngine.DUCKDUCKGO))
        assertEquals("https://example.com/path?q=a%20b#part", resolveBrowserDestination("example.com/path?q=a%20b#part"))
        assertEquals("about:blank", resolveBrowserDestination("  "))
    }
    @Test fun adBlockerFiltersKnownTrackerHostsSafely() {
        assertTrue(BrowserAdBlocker.shouldBlockHost("pagead2.googlesyndication.com"))
        assertTrue(BrowserAdBlocker.shouldBlockHost("hm.baidu.com"))
        assertTrue(BrowserAdBlocker.shouldBlockHost("sub.doubleclick.net."))
        assertFalse(BrowserAdBlocker.shouldBlockHost("www.baidu.com"))
        assertFalse(BrowserAdBlocker.shouldBlockHost("github.com"))
        assertFalse(BrowserAdBlocker.shouldBlockHost(null))
    }
    @Test fun localEndpointsPreservePortAndPath() {
        assertEquals("http://10.0.2.2:8765/panel-check", resolveBrowserDestination("10.0.2.2:8765/panel-check"))
        assertEquals("http://localhost:8080/", resolveBrowserDestination("localhost:8080/"))
        assertEquals("http://[::1]:8080/a", resolveBrowserDestination("[::1]:8080/a"))
    }
    @Test fun explicitUrlsAreValidatedBeforeNativeLoad() {
        listOf("http://", "https:///path", "https://example.com:99999/", "http://example.com/a b",
            "javascript:alert(1)", "file:///sdcard/private", "intent://example", "https://user:secret@example.com/",
            "https://example.com\n").forEach { assertNull(it, resolveBrowserDestination(it)) }
    }
    @Test fun internationalHostnameIsEncodedWithoutDoubleEncodingExistingEscapes() {
        assertEquals("https://xn--fsqu00a.xn--0zwm56d/a%20b", resolveBrowserDestination("https://例子.测试/a%20b"))
    }
    @Test fun replacingViewRejectsOldCallbacksAndOldReleaseCannotDetachNewView() {
        val lease = BrowserCallbackLease()
        val old = lease.attach()
        assertTrue(lease.accepts(old))
        val current = lease.attach()
        assertFalse(lease.accepts(old))
        assertFalse(lease.detach(old))
        assertTrue(lease.accepts(current))
        assertTrue(lease.detach(current))
        assertFalse(lease.accepts(current))
    }
    @Test fun visitMovesExistingUrlToFrontUpdatesTitleAndBoundsHistory() {
        val records = listOf(BrowserLink("old a", "https://a.test/"), BrowserLink("b", "https://b.test/"))
        val revisited = recordBrowserVisit(records, "new a", "https://a.test/", 2)
        assertEquals(listOf(BrowserLink("new a", "https://a.test/"), records[1]), revisited)
        val next = recordBrowserVisit(revisited, "c", "https://c.test/", 2)
        assertEquals(listOf("https://c.test/", "https://a.test/"), next.map { it.url })
    }
    @Test fun homeAndInvalidUrlDoNotEnterRecords() {
        val records = listOf(BrowserLink("saved", "https://saved.test/"))
        assertSame(records, recordBrowserVisit(records, "home", "about:blank"))
        assertSame(records, recordBrowserVisit(records, "bad", "javascript:alert(1)"))
    }
    @Test fun nightModeColorMatrixDarkensWhiteLightensBlackAndPreservesHues() {
        val (wr, wg, wb) = BrowserNightModeEngine.transformRgb(255, 255, 255)
        assertTrue("White should become dark charcoal: ($wr,$wg,$wb)", wr in 16..36 && wg in 16..38 && wb in 20..44)

        val (br, bg, bb) = BrowserNightModeEngine.transformRgb(0, 0, 0)
        assertTrue("Black text should become soft off-white: ($br,$bg,$bb)", br in 220..242 && bg in 220..244 && bb in 224..248)

        val (blueR, blueG, blueB) = BrowserNightModeEngine.transformRgb(0, 102, 255)
        assertTrue("Blue link hue must stay blue: ($blueR,$blueG,$blueB)", blueB > blueG && blueG > blueR)

        val (redR, redG, redB) = BrowserNightModeEngine.transformRgb(230, 40, 40)
        assertTrue("Red badge hue must stay red: ($redR,$redG,$redB)", redR > redG + 80 && redR > redB + 80)

        val (greenR, greenG, greenB) = BrowserNightModeEngine.transformRgb(34, 197, 94)
        assertTrue("Green status hue must stay green: ($greenR,$greenG,$greenB)", greenG > greenB && greenB > greenR)
    }
    @Test fun readerArticleJsonParserHandlesWebViewQuotedJsonAndHeadings() {
        val rawWebViewQuoted = "\"{\\\"title\\\":\\\"深度长文\\\",\\\"site\\\":\\\"example.com\\\",\\\"blocks\\\":[{\\\"t\\\":\\\"第一章\\\",\\\"h\\\":true},{\\\"t\\\":\\\"这是提纯后的正文内容段落。\\\",\\\"h\\\":false}]}\""
        val parsed = parseReaderArticleJson(rawWebViewQuoted, "备用标题")
        assertNotNull(parsed)
        assertEquals("深度长文", parsed!!.title)
        assertEquals("example.com", parsed.site)
        assertEquals(2, parsed.blocks.size)
        assertTrue(parsed.blocks[0].isHeading)
        assertEquals("这是提纯后的正文内容段落。", parsed.blocks[1].text)
    }
    @Test fun desktopUserAgentRemovesAndroidWebViewAndMobileTokens() {
        val androidWvUa = "Mozilla/5.0 (Linux; Android 16; PJX110 Build/BP2A; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/131.0.6778.81 Mobile Safari/537.36"
        val desktop = desktopAgent(androidWvUa)
        assertFalse(desktop.contains("wv"))
        assertFalse(desktop.contains("Mobile"))
        assertFalse(desktop.contains("Android"))
        assertTrue(desktop.contains("Chrome/131.0.6778.81"))
    }
    @Test fun desktopPreferredUrlUpgradesKnownMobileSubdomainsWithoutBreakingUnknownOnes() {
        assertEquals("https://www.baidu.com/s?wd=test", desktopPreferredUrl("https://m.baidu.com/s?wd=test", desktop = true))
        assertEquals("https://zh.wikipedia.org/wiki/Kotlin", desktopPreferredUrl("https://zh.m.wikipedia.org/wiki/Kotlin", desktop = true))
        assertEquals("https://www.bilibili.com/video/BV1", desktopPreferredUrl("https://m.bilibili.com/video/BV1", desktop = true))
        assertEquals("https://m.baidu.com/s?wd=test", desktopPreferredUrl("https://m.baidu.com/s?wd=test", desktop = false))
        assertEquals("https://m.customsite.example/page", desktopPreferredUrl("https://m.customsite.example/page", desktop = true))
    }
}
