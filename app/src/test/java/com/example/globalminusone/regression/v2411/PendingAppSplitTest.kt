package com.example.globalminusone.regression.v2411

import com.example.globalminusone.system.PendingAppSplit
import org.junit.Assert.*
import org.junit.Test

class PendingAppSplitTest {
    @Test fun unrelatedWindowsCannotSplitAnotherAppAndTheTargetOnlyTriggersOnce() {
        val request = PendingAppSplit("test.app", 5000)
        assertFalse(request.acceptWindow("com.android.systemui", 100))
        assertTrue(request.pending)
        assertTrue(request.acceptWindow("test.app", 200))
        assertFalse(request.acceptWindow("test.app", 300))
    }
    @Test fun aLateForegroundEventCannotTriggerAnOldUserRequest() {
        val request = PendingAppSplit("test.app", 5000)
        assertFalse(request.acceptWindow("test.app", 5000))
        assertFalse(request.pending)
        assertFalse(request.acceptWindow("test.app", 4000))
    }
    @Test fun cancellationPreventsAllLaterEvents() {
        val request = PendingAppSplit("test.app", 5000)
        request.cancel()
        assertFalse(request.acceptWindow("test.app", 200))
    }
}
