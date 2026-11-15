package com.example.globalminusone.regression.v2413

import com.example.globalminusone.split.*
import org.junit.Assert.*
import org.junit.Test

class AppSplitPolicyTest {
    @Test fun automaticPublicRouteRequires12LAndNeverStartsFromPip() {
        assertFalse(canRequestAdjacentSplit(31, false))
        assertTrue(canRequestAdjacentSplit(32, false))
        assertTrue(canRequestAdjacentSplit(36, false))
        assertFalse(canRequestAdjacentSplit(36, true))
    }
    @Test fun selectedAppIntentAlwaysRequestsAdjacentAndNeverDocumentOrMultipleTask() {
        assertEquals(0x10001000, adjacentSplitLaunchFlags())
        assertEquals(0, adjacentSplitLaunchFlags() and 0x08080000)
    }
    @Test fun returnFromStartActivityAloneDoesNotReportMultiWindow() {
        val tracker = SplitLaunchTracker()
        val token = tracker.begin(100)
        assertEquals(SplitLaunchOutcome.WAITING, tracker.observe(token, false, 200))
        assertEquals(SplitLaunchOutcome.UNCONFIRMED, tracker.observe(token, false, 5100))
        assertNull(tracker.observe(token, true, 5200))
    }
    @Test fun multiWindowEvidenceWaitsForTransitionAndOnlyReportsOnce() {
        val tracker = SplitLaunchTracker()
        val token = tracker.begin(0)
        assertEquals(SplitLaunchOutcome.WAITING, tracker.observe(token, true, 300))
        assertEquals(SplitLaunchOutcome.MULTI_WINDOW_REPORTED, tracker.observe(token, true, 800))
        assertNull(tracker.observe(token, true, 1000))
    }
    @Test fun newRequestDiscardsOldTimeoutAndOldFailure() {
        val tracker = SplitLaunchTracker()
        val old = tracker.begin(0)
        val fresh = tracker.begin(3000)
        assertNull(tracker.observe(old, false, 5000))
        assertNull(tracker.fail(old))
        assertEquals(SplitLaunchOutcome.WAITING, tracker.observe(fresh, false, 5000))
        assertEquals(SplitLaunchOutcome.MULTI_WINDOW_REPORTED, tracker.observe(fresh, true, 5500))
    }
    @Test fun cancellationAndFailureAreTerminal() {
        val tracker = SplitLaunchTracker()
        val cancelled = tracker.begin(0)
        tracker.cancel()
        assertNull(tracker.observe(cancelled, true, 1000))
        val failed = tracker.begin(2000)
        assertEquals(SplitLaunchOutcome.FAILED, tracker.fail(failed))
        assertNull(tracker.observe(failed, true, 3000))
    }
    @Test fun sourceWindowReplacementRejectsTaskReusingLaunchModes() {
        assertTrue(canReplaceSplitSource(0))
        assertTrue(canReplaceSplitSource(1))
        assertFalse(canReplaceSplitSource(2))
        assertFalse(canReplaceSplitSource(3))
        assertFalse(canReplaceSplitSource(4))
    }
}
