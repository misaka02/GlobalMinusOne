package com.example.globalminusone.screenassistant

import com.example.globalminusone.system.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import com.example.globalminusone.system.privilege.RootExecutor
import com.example.globalminusone.system.privilege.RootProcessOutcome
import com.example.globalminusone.system.privilege.UserRootSession

class BreenoSystemProtocolTest {
    private val protocol = BreenoSystemProtocol
    private val started get() = "Starting service: Intent { act=${protocol.action} cmp=${protocol.component} (has extras) }\n"
    @After fun reset() = UserRootSession.resetForTests()

    @Test fun realActionAndFullScreenExtraAreFixedAndCannotBeInjected() {
        val cmd = protocol.command(protocol.component)
        assertTrue(cmd.contains("start-foreground-service"))
        assertTrue(cmd.contains("--es extra_entrance_function 'full_screen_ocr'"))
        assertTrue(cmd.contains("--es triggered_app 'com.coloros.smartsidebar'"))
        assertTrue(runCatching { protocol.command(protocol.component + "; id") }.isFailure)
        assertFalse(cmd.contains("monkey"))
    }

    @Test fun zeroExitPermissionErrorsAndEmptyPreloadResponsesNeverCountAsAccepted() {
        assertTrue(protocol.accepted(0, started, ""))
        assertFalse(protocol.accepted(0, started + "Error: Requires permission oppo.permission.OPPO_COMPONENT_SAFE", ""))
        assertFalse(protocol.accepted(0, "", ""))
        assertFalse(protocol.accepted(0, "Starting service: Intent { cmp=com.heytap.speechassist/.Voice }", ""))
        assertFalse(protocol.accepted(0, started, "Permission Denial"))
        assertFalse(protocol.accepted(1, started, ""))
    }

    @Test fun directServiceUsesExistingLeaseAndRejectsAmFakeSuccess() = runBlocking {
        var auth = 0
        var response = started + "Error: Requires permission"
        UserRootSession.resetForTests(RootExecutor { r ->
            if (r.entryMarker == null) { auth++; RootProcessOutcome(0, "0".toByteArray()) }
            else {
                assertTrue(r.command.contains(protocol.action))
                RootProcessOutcome(0, response.toByteArray(), "${r.entryMarker}:0\n")
            }
        }, { "/synthetic/su" })
        UserRootSession.requestAuthorization()
        assertFalse(UserRootSession.runBreenoDirectCommand(listOf(protocol.servicePackage), serviceComponent = protocol.component).succeeded)
        response = started
        UserRootSession.requestAuthorization()
        assertTrue(UserRootSession.runBreenoDirectCommand(listOf(protocol.servicePackage), serviceComponent = protocol.component).succeeded)
        assertEquals(1, auth)
    }

    @Test fun unrecognizedServiceNeverRequestsRootOrExecutes() = runBlocking {
        var calls = 0
        UserRootSession.resetForTests(RootExecutor { calls++; RootProcessOutcome(0) }, { "/synthetic/su" })
        assertFalse(UserRootSession.runBreenoDirectCommand(listOf(protocol.servicePackage), serviceComponent = "com.coloros.directui/.DirectService").succeeded)
        assertEquals(0, calls)
    }
}
