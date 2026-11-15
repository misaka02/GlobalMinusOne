package com.example.globalminusone.regression.v2416

import com.example.globalminusone.ui.panel.IndependentBrowserSession
import org.junit.Assert.*
import org.junit.Test

class BrowserReturnStateTest {
    @Test fun switchingTabsPreservesEachIndependentPageAndItsDisplaySettings() {
        val workspace = IndependentBrowserSession()
        val first = workspace.active.id
        workspace.active.url = "https://example.org/independent"
        workspace.active.desktop = true
        workspace.active.textZoom = 150
        assertTrue(workspace.openTab("https://example.org/second"))
        val second = workspace.active.id
        assertTrue(workspace.selectTab(first))
        assertEquals("https://example.org/independent", workspace.active.url)
        assertTrue(workspace.active.desktop)
        assertEquals(150, workspace.active.textZoom)
        assertTrue(workspace.selectTab(second))
        assertEquals("https://example.org/second", workspace.active.url)
        assertFalse(workspace.active.desktop)
    }
    @Test fun closingAnInactiveTabKeepsTheCurrentPageAndClosingTheLastCreatesHome() {
        val workspace = IndependentBrowserSession()
        val first = workspace.active.id
        workspace.openTab("https://example.org/read")
        val current = workspace.active.id
        assertTrue(workspace.closeTab(first))
        assertEquals(current, workspace.active.id)
        assertTrue(workspace.closeTab(current))
        assertEquals(1, workspace.tabs.size)
        assertEquals("about:blank", workspace.active.url)
        assertNotEquals(current, workspace.active.id)
    }
    @Test fun tabLimitAndStaleTabActionsDoNotReplaceTheCurrentPage() {
        val workspace = IndependentBrowserSession()
        repeat(7) { assertTrue(workspace.openTab("https://example.org/$it")) }
        val current = workspace.active.id
        assertFalse(workspace.openTab("https://example.org/overflow"))
        assertFalse(workspace.selectTab("missing"))
        assertFalse(workspace.closeTab("missing"))
        assertEquals(current, workspace.active.id)
        assertEquals(8, workspace.tabs.size)
    }
}
