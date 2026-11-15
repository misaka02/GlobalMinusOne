package com.example.globalminusone

import com.example.globalminusone.system.*
import org.junit.Assert.*
import org.junit.Test

class RootBootstrapPolicyTest {
    private class Handle(var alive: Boolean = true, var root: Boolean = true)
    private class MainApi : RootMainShellApi<Handle> {
        var command = "/system/bin/false"
        var timeout = 0L
        var rootProcesses = 0
        var cachedHandle: Handle? = null
        var failAuthorization = false
        override fun configure(command: String, timeoutSeconds: Long) { this.command = command; timeout = timeoutSeconds }
        override fun main(): Handle {
            cachedHandle?.takeIf { it.alive }?.let { return it }
            check(command != "/system/bin/false") { "Explicit auth required; no su attempted" }
            rootProcesses++
            check(timeout == 30L)
            if (failAuthorization) error("denied")
            return Handle().also { cachedHandle = it }
        }
        override fun cached() = cachedHandle?.takeIf { it.alive }
        override fun aliveRoot(shell: Handle) = shell.alive && shell.root
        override fun close(shell: Handle) { shell.alive = false }
    }
    @Test fun sharedMainShellReusedAndDeadRootServiceGetShellCannotPrompt() {
        val api = MainApi(); val bootstrap = RootShellBootstrap(api)
        val handle = bootstrap.explicitOpen("/system/bin/su")
        assertEquals(1, api.rootProcesses); assertTrue(bootstrap.isCurrent(handle))
        assertEquals("/system/bin/false", api.command)
        // RootService's internal main() resolves the same cache; opening the panel does not create su.
        assertSame(handle, api.main()); assertSame(handle, bootstrap.explicitOpen("/system/bin/su"))
        assertEquals(1, api.rootProcesses)
        api.close(handle)
        assertFalse(bootstrap.isCurrent(handle))
        assertTrue(runCatching { api.main() }.isFailure)
        assertEquals(1, api.rootProcesses)
        assertNotSame(handle, bootstrap.explicitOpen("/system/bin/su"))
        assertEquals(2, api.rootProcesses)
    }
    @Test fun rejectedAuthorizationAlsoLeavesImplicitBootstrapBlocked() {
        val api = MainApi().apply { failAuthorization = true }; val bootstrap = RootShellBootstrap(api)
        assertTrue(runCatching { bootstrap.explicitOpen("/system/bin/su") }.isFailure)
        assertEquals("/system/bin/false", api.command); assertEquals(1L, api.timeout)
        assertTrue(runCatching { api.main() }.isFailure)
        assertEquals(1, api.rootProcesses)
    }
}
