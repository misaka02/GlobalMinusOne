package com.example.globalminusone.regression

import com.example.globalminusone.ui.panel.IndependentBrowserSession
import com.example.globalminusone.MemoryPreferences
import com.example.globalminusone.browser.BrowserSearchEngine
import org.junit.Assert.*
import org.junit.Test

class IndependentBrowserSessionTest {
    @Test fun restoredPreferencesRefreshExistingSessionWithoutClosingOrResettingTabs() {
        val prefs = MemoryPreferences()
        val session = IndependentBrowserSession(sharedPrefsOverride = prefs)
        session.openTab("https://kept.example/")
        val active = session.active
        active.desktop = true
        active.textZoom = 135
        prefs.edit().putString("search_engine", BrowserSearchEngine.BING.name)
            .putBoolean("night_mode", true).putBoolean("ad_block", false)
            .putBoolean("bottom_bar", true).putBoolean("no_image", true)
            .putInt("reader_font_sp", 22).putFloat("window_width_fraction", 0.6f)
            .putFloat("window_height_fraction", 0.7f).putFloat("window_x_fraction", 0.2f)
            .putFloat("window_y_fraction", 0.15f).commit()
        val committed = prefs.all.toMap()
        session.reloadSavedPreferences()
        assertSame(active, session.active)
        assertEquals(2, session.tabs.size)
        assertTrue(active.desktop)
        assertEquals(135, active.textZoom)
        assertEquals(BrowserSearchEngine.BING, session.searchEngine)
        assertTrue(session.nightMode)
        assertFalse(session.adBlockEnabled)
        assertFalse(session.adBlockVolatile)
        assertTrue(session.bottomAddressBar)
        assertTrue(session.noImageVolatile)
        assertEquals(22, session.readerFontSp)
        assertEquals(0.6f, session.windowWidthFraction)
        assertEquals(0.15f, session.windowYFraction)
        assertEquals(1L, session.preferencesGeneration)
        assertEquals(committed, prefs.all)
        prefs.edit().clear().commit()
        session.reloadSavedPreferences()
        assertFalse(session.nightMode)
        assertFalse(session.noImageMode)
        assertEquals(IndependentBrowserSession.DEFAULT_WINDOW_WIDTH_FRACTION, session.windowWidthFraction)
        assertSame(active, session.active)
    }
    @Test fun closingInactiveTabKeepsCurrentSelectionAndItsState() {
        val session = IndependentBrowserSession()
        val first = session.active
        session.openTab("https://second.test/")
        val second = session.active
        second.desktop = true; second.textZoom = 130
        assertTrue(session.closeTab(first.id))
        assertSame(second, session.active)
        assertTrue(session.active.desktop)
        assertEquals(130, session.active.textZoom)
    }
    @Test fun closingCurrentTabSelectsNeighborAndLastCloseCreatesUsableHome() {
        val session = IndependentBrowserSession()
        val first = session.active
        session.openTab("https://second.test/")
        assertTrue(session.closeTab(session.activeId))
        assertSame(first, session.active)
        assertTrue(session.closeTab(first.id))
        assertEquals(1, session.tabs.size)
        assertEquals("about:blank", session.active.url)
        assertNotEquals(first.id, session.activeId)
    }
    @Test fun tabLimitAndInvalidSelectionPreserveExistingActiveTab() {
        val session = IndependentBrowserSession()
        repeat(7) { assertTrue(session.openTab("https://tab$it.test/")) }
        val selected = session.active
        assertFalse(session.openTab())
        assertFalse(session.selectTab("missing"))
        assertFalse(session.closeTab("missing"))
        assertEquals(8, session.tabs.size)
        assertSame(selected, session.active)
    }
}
