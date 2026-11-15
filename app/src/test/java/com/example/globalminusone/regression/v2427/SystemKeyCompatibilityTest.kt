package com.example.globalminusone.service

import org.junit.Assert.*
import org.junit.Test

class SystemKeyCompatibilityTest {
    @Test fun onlyUnhandledAndroid12CallFallsBackToSafeConsumption() {
        for (sdk in 26..36) for (key in listOf(4, 5, 6, 79, 85, 86, 126, 127)) for (action in 0..2) {
            var dispatched = 0
            val handled = dispatchWithCallFallbackGuard(sdk, key, action) { dispatched++; false }
            assertEquals(1, dispatched)
            assertEquals("sdk=$sdk key=$key action=$action", sdk in 31..32 && key == 5 && action in 0..1, handled)
        }
    }

    @Test fun childrenReceiveCallKeysAndTheirSuccessfulHandlingIsPreserved() {
        var calls = 0
        assertTrue(dispatchWithCallFallbackGuard(31, 5, 0) { calls++; true })
        assertEquals(1, calls)
        assertTrue(dispatchWithCallFallbackGuard(36, 79, 1) { true })
    }

    private fun frameworkFailure() = SecurityException("Permission Denial: android.intent.action.CLOSE_SYSTEM_DIALOGS").apply {
        stackTrace = arrayOf(StackTraceElement("com.android.internal.policy.PhoneFallbackEventHandler", "startCallActivity", "PhoneFallbackEventHandler.java", 275))
    }

    @Test fun exactNestedFrameworkFailureIsHandledButOtherFailuresRemainVisible() {
        assertTrue(dispatchWithCallFallbackGuard(31, 5, 1) { throw frameworkFailure() })
        val unrelated = SecurityException("Other permission denied")
        val fake = SecurityException("CLOSE_SYSTEM_DIALOGS") // no framework stack
        for (failure in listOf(unrelated, fake)) {
            try { dispatchWithCallFallbackGuard(31, 5, 1) { throw failure }; fail("Swallowed unrelated error") }
            catch (caught: SecurityException) { assertSame(failure, caught) }
        }
        for ((sdk, key) in listOf(36 to 5, 31 to 4, 32 to 79)) {
            val failure = frameworkFailure()
            try { dispatchWithCallFallbackGuard(sdk, key, 1) { throw failure }; fail("Guard escaped its scope") }
            catch (caught: SecurityException) { assertSame(failure, caught) }
        }
    }
}
