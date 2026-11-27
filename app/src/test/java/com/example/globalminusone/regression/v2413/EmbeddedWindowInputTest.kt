package com.example.globalminusone.regression.v2413

import android.view.WindowManager.LayoutParams
import com.example.globalminusone.service.embedded.applicationWindowInputFlags
import com.example.globalminusone.service.embedded.embeddedHostFocusFlags
import org.junit.Assert.*
import org.junit.Test

class EmbeddedWindowInputTest {
    private val base = LayoutParams.FLAG_NOT_TOUCH_MODAL or LayoutParams.FLAG_HARDWARE_ACCELERATED
    @Test fun remoteEditorDoesNotStealFocusOrCoverTheSystemKeyboard() {
        val flags = embeddedHostFocusFlags(base, true)
        assertTrue(flags and LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        assertTrue(flags and LayoutParams.FLAG_ALT_FOCUSABLE_IM != 0)
        assertEquals(base, embeddedHostFocusFlags(flags, false))
    }
    @Test fun hiddenHostCannotInterceptTouchesAndRestorationPreservesWindowOptions() {
        val hidden = applicationWindowInputFlags(embeddedHostFocusFlags(base, true), true, true)
        assertTrue(hidden and LayoutParams.FLAG_NOT_TOUCHABLE != 0)
        assertTrue(hidden and LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        assertEquals(0, hidden and LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        assertEquals(base, applicationWindowInputFlags(hidden, false, false))
        assertEquals(embeddedHostFocusFlags(base, true), applicationWindowInputFlags(hidden, false, true))
    }
    @Test fun returningToHostEditorClearsBothFocusFlagsWithoutEnablingAHiddenPanel() {
        val hidden = base or LayoutParams.FLAG_NOT_TOUCHABLE or LayoutParams.FLAG_NOT_FOCUSABLE or LayoutParams.FLAG_ALT_FOCUSABLE_IM
        assertEquals(base or LayoutParams.FLAG_NOT_TOUCHABLE, embeddedHostFocusFlags(hidden, false))
    }
}
