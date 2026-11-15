package com.example.globalminusone.regression.v2416

import com.example.globalminusone.browser.ChromeLaunchPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ChromeAppWindowRouteTest {
    @Test fun appWindowCreatesNativeChromeHomeOnlyInItsNewVerifiedTask() {
        val initial = ChromeLaunchPlan.forAppWindow(true, 23, -1, true)
        assertTrue(initial.nativeHome)
        assertFalse(initial.preferNew)
        assertFalse(initial.createNewTab)
        assertEquals(0x18800000, initial.flags)
        assertEquals(0, initial.flags and 0x00080000) // No document-task restoration.

        val navigation = ChromeLaunchPlan.forAppWindow(false, 23, 71, true)
        assertFalse(navigation.nativeHome)
        assertEquals(71, navigation.taskId)
        assertEquals(0x30000000, navigation.flags)

        try {
            ChromeLaunchPlan.forAppWindow(true, 23, -1, false)
            fail("A Chrome app window started without a verified owned root")
        } catch (_: IllegalStateException) { }
    }
}
