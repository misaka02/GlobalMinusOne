package com.example.globalminusone.regression.v2411

import com.example.globalminusone.browser.EmbeddedAppLaunchPlan
import com.example.globalminusone.browser.EmbeddedResizePolicy
import org.junit.Assert.*
import org.junit.Test

class EmbeddedAppLaunchPlanTest {
    @Test fun supportedComponentAlwaysGetsANewTaskWithoutDocumentOrAdjacentFlags() {
        for (mode in listOf(0, 1, 4)) assertEquals(0x18800000, EmbeddedAppLaunchPlan.flags(33, mode, 0, true, true))
    }
    @Test fun globalSingletonAndDocumentReuseAreRejectedBeforeLaunch() {
        fun rejected(mode: Int, document: Int = 0, exported: Boolean = true, resize: Boolean = true, sdk: Int = 33) {
            try { EmbeddedAppLaunchPlan.flags(sdk, mode, document, exported, resize); fail("Unsafe component accepted") }
            catch (_: IllegalStateException) { }
        }
        rejected(2); rejected(3); rejected(0, document = 1)
        rejected(0, exported = false); rejected(0, resize = false); rejected(0, sdk = 30)
    }
    @Test fun activityResizeDeclarationOverridesApplicationAndSdkDefaults() {
        assertTrue(EmbeddedResizePolicy.resizable(23, false, true))
        assertFalse(EmbeddedResizePolicy.resizable(36, true, false))
        assertFalse(EmbeddedResizePolicy.resizable(23, null, null))
        assertTrue(EmbeddedResizePolicy.resizable(24, null, null))
        assertFalse(EmbeddedResizePolicy.resizable(36, false, null))
    }
}
