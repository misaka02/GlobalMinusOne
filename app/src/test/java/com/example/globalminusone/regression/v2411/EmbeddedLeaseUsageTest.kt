package com.example.globalminusone.regression.v2411

import com.example.globalminusone.browser.EmbeddedLeaseUsage
import org.junit.Assert.*
import org.junit.Test

class EmbeddedLeaseUsageTest {
    @Test fun closingOneWindowDoesNotRetireAnotherWindowsTransport() {
        val usage = EmbeddedLeaseUsage(120)
        assertTrue(usage.acquire(0)); assertTrue(usage.acquire(0))
        usage.release(10)
        assertEquals(1, usage.users)
        assertFalse(usage.expired(1000))
        usage.release(1000)
        assertFalse(usage.expired(1119)); assertTrue(usage.expired(1120))
    }
    @Test fun reopeningInsideIdleWindowReusesTheBindingAndCancelsItsOldDeadline() {
        val usage = EmbeddedLeaseUsage(120)
        assertTrue(usage.acquire(0)); usage.release(10)
        assertTrue(usage.acquire(129)); assertFalse(usage.expired(130))
        usage.release(200)
        assertFalse(usage.expired(319)); assertFalse(usage.acquire(320))
    }
    @Test fun revocationRetiresEvenAnActiveLeaseAndLateReleaseCannotReviveIt() {
        val usage = EmbeddedLeaseUsage(120)
        assertTrue(usage.acquire(0)); usage.retire(); usage.release(1)
        assertEquals(0, usage.users)
        assertTrue(usage.expired(1)); assertFalse(usage.acquire(2))
    }
}
