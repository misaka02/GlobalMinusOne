package com.example.globalminusone.regression.v2410

import com.example.globalminusone.browser.ChromeLaunchPlan
import org.junit.Assert.*
import org.junit.Test

class ChromeLaunchPlanTest {
    @Test fun newWindowUsesChromiumFreshInstanceParametersOnVerifiedSecondaryDisplay() {
        ChromeLaunchPlan.requireBrowser(33, 130, 4, true, true)
        val launch = ChromeLaunchPlan.create(true, 17, -1, true)
        assertEquals(17, launch.displayId)
        assertEquals(-1, launch.taskId)
        assertEquals(0x18800000, launch.flags) // NEW_TASK | MULTIPLE_TASK | EXCLUDE_FROM_RECENTS
        assertEquals(0, launch.flags and 0x00080000) // No NEW_DOCUMENT restoration
        assertEquals(0, launch.flags and 0x00001000) // No LAUNCH_ADJACENT positioning
        assertTrue(launch.preferNew)
        assertTrue(launch.createNewTab)
        assertEquals("com.android.chrome.prefer_new", ChromeLaunchPlan.PREFER_NEW)
        assertEquals("create_new_tab", ChromeLaunchPlan.CREATE_NEW_TAB)
    }

    @Test fun navigationSelectsOnlyTheAlreadyOwnedTaskWithoutCreatingAnotherWindow() {
        val launch = ChromeLaunchPlan.create(false, 17, 65, true)
        assertEquals(65, launch.taskId)
        assertEquals(0x30000000, launch.flags) // NEW_TASK | SINGLE_TOP
        assertFalse(launch.preferNew)
        assertFalse(launch.createNewTab)
    }

    @Test fun nativeAnchorAndBrowserCapabilitiesAreRequiredBeforeCreatingLaunchPlan() {
        fun rejected(action: () -> Unit) {
            try { action(); fail("Unsupported launch was accepted") } catch (_: IllegalStateException) { }
        }
        rejected { ChromeLaunchPlan.create(true, 0, -1, true) }
        rejected { ChromeLaunchPlan.create(true, 17, -1, false) }
        rejected { ChromeLaunchPlan.create(false, 17, -1, true) }
        rejected { ChromeLaunchPlan.requireBrowser(30, 130, 4, true, true) }
        rejected { ChromeLaunchPlan.requireBrowser(33, 119, 4, true, true) }
        rejected { ChromeLaunchPlan.requireBrowser(33, 130, 2, true, true) }
        rejected { ChromeLaunchPlan.requireBrowser(33, 130, 4, false, true) }
        rejected { ChromeLaunchPlan.requireBrowser(33, 130, 4, true, false) }
    }
}
