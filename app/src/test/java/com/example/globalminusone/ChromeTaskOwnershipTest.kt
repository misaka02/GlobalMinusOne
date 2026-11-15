package com.example.globalminusone

import com.example.globalminusone.browser.ChromeTaskOwnership.belongsToSession
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChromeTaskOwnershipTest {
    private val chrome = "com.android.chrome"
    @Test fun ordinaryAndPreviousSessionChromeTasksArePreserved() {
        assertFalse(belongsToSession("current", null, false, chrome, chrome))
        assertFalse(belongsToSession("current", "previous", false, chrome, chrome))
    }
    @Test fun onlyThisSessionsMarkedNewLaunchCanBeReclaimed() {
        assertTrue(belongsToSession("current", "current", false, chrome, chrome))
        assertFalse(belongsToSession("current", null, false, chrome, chrome))
        assertFalse(belongsToSession("current", "current", true, chrome, chrome))
    }
    @Test fun packageMismatchCannotBeReclaimedEvenWithKnownId() {
        assertFalse(belongsToSession("current", "current", true, chrome, "another.app"))
    }
    @Test fun closedSessionCannotReclaimAnything() {
        assertFalse(belongsToSession(null, "current", true, chrome, chrome))
    }
}
