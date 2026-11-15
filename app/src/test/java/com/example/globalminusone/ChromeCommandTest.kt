package com.example.globalminusone.regression

import com.example.globalminusone.browser.ChromeCommand
import org.junit.Assert.*
import org.junit.Test

class ChromeCommandTest {
    @Test fun fixedCommandWireRoundTripsOnlyWhitelistedOperations() {
        ChromeCommand.values().forEach { assertSame(it, ChromeCommand.fromWire(it.ordinal)) }
    }
    @Test fun arbitraryKeyValuesCannotBecomePrivilegedCommands() {
        listOf(-1, ChromeCommand.values().size, 82, Int.MAX_VALUE).forEach { value ->
            assertTrue(runCatching { ChromeCommand.fromWire(value) }.exceptionOrNull() is IllegalArgumentException)
        }
    }
}
