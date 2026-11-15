package com.example.globalminusone.regression.v2419

import com.example.globalminusone.MemoryPreferences
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.TriggerPosition
import com.example.globalminusone.data.TriggerWay
import com.example.globalminusone.service.SidePullTriggerPolicy
import com.example.globalminusone.service.overlayUpdates
import com.example.globalminusone.ui.panel.IndependentBrowserSession
import org.junit.Assert.*
import org.junit.Test

class BrowserAndTriggerPersistenceTest {
    @Test fun browserGeometrySurvivesTwoSessionRecreationsAndTabOperations() {
        val prefs = MemoryPreferences()
        val first = IndependentBrowserSession(sharedPrefsOverride = prefs)
        assertEquals(1, first.tabs.size)
        first.updateWindowGeometry(.65f, .58f, .21f, .32f)
        first.openTab("https://example.org/read")
        first.closeAllTabs()
        val second = IndependentBrowserSession(sharedPrefsOverride = prefs)
        assertEquals(.65f, second.windowWidthFraction, .00001f)
        assertEquals(.58f, second.windowHeightFraction, .00001f)
        assertEquals(.21f, second.windowXFraction, .00001f)
        assertEquals(.32f, second.windowYFraction, .00001f)
        second.updateWindowGeometry(.72f, .64f, .25f, .14f)
        val third = IndependentBrowserSession(sharedPrefsOverride = prefs)
        assertEquals(.72f, third.windowWidthFraction, .00001f)
        assertEquals(.64f, third.windowHeightFraction, .00001f)
        assertEquals(.25f, third.windowXFraction, .00001f)
        assertEquals(.14f, third.windowYFraction, .00001f)
    }

    @Test fun corruptSavedGeometryStaysOnScreenAndInvalidUpdatesDoNotOverwriteIt() {
        val prefs = MemoryPreferences(mapOf(
            IndependentBrowserSession.KEY_WINDOW_WIDTH to 3f,
            IndependentBrowserSession.KEY_WINDOW_HEIGHT to Float.NaN,
            IndependentBrowserSession.KEY_WINDOW_X to .8f,
            IndependentBrowserSession.KEY_WINDOW_Y to Float.POSITIVE_INFINITY
        ))
        val session = IndependentBrowserSession(sharedPrefsOverride = prefs)
        assertEquals(1f, session.windowWidthFraction, 0f)
        assertEquals(0f, session.windowXFraction, 0f)
        assertTrue(session.windowYFraction + session.windowHeightFraction <= 1f)
        val writes = prefs.writeCount
        session.updateWindowGeometry(Float.NaN, .5f, 0f, 0f)
        assertEquals(writes, prefs.writeCount)
        session.updateWindowGeometry(.7f, .6f, 10f, 10f)
        val restarted = IndependentBrowserSession(sharedPrefsOverride = prefs)
        assertEquals(.3f, restarted.windowXFraction, .00001f)
        assertEquals(.4f, restarted.windowYFraction, .00001f)
    }

    @Test fun sidePullSideAndHorizontalPositionPersistAndRequestLiveTriggerUpdate() {
        val prefs = MemoryPreferences()
        val repository = MinusOneRepository(prefs)
        repository.updateTriggerConfig {
            it.withPosition(TriggerPosition.RIGHT).copy(triggerWay = TriggerWay.SIDE_PULLDOWN,
                sidePullHorizontalOffsetDp = 75)
        }
        val before = repository.state.value
        repository.updateTriggerConfig { it.copy(sidePullHorizontalOffsetDp = 140) }
        assertTrue(overlayUpdates(before, repository.state.value).triggers)
        val config = MinusOneRepository(prefs).state.value.triggerConfig
        assertEquals(TriggerPosition.RIGHT, config.triggerPosition)
        assertEquals(140, config.sidePullHorizontalOffsetDp)
        val right = SidePullTriggerPolicy.resolveTriggerBarLeftX(TriggerPosition.RIGHT,
            1080, 30, true, 72, 72, config.sidePullHorizontalOffsetDp, 3f)
        assertEquals(630, right)
        repository.updateTriggerConfig { it.withPosition(TriggerPosition.BOTH).copy(sidePullHorizontalOffsetDp = -1) }
        val restored = MinusOneRepository(prefs).state.value.triggerConfig
        assertEquals(TriggerPosition.BOTH, restored.triggerPosition)
        assertEquals(-1, restored.sidePullHorizontalOffsetDp)
        assertEquals(96, SidePullTriggerPolicy.resolveTriggerBarLeftX(TriggerPosition.LEFT,
            1080, 30, true, 72, 72, restored.sidePullHorizontalOffsetDp, 3f))
    }
}
