package com.example.globalminusone.regression.v2414

import com.example.globalminusone.system.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class RootCancellationSessionTest {
    @After fun reset() { UserRootSession.resetForTests() }
    private fun entered(request: RootProcessRequest, timedOut: Boolean = false) = RootProcessOutcome(
        if (timedOut) 124 else 0, "complete".toByteArray(), "${request.entryMarker}:0\n", timedOut)

    @Test fun cancellingUiReadDrainsBeforeReusingSameHealthyGrant() = runBlocking {
        val started = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        var calls = 0; var drained = false
        UserRootSession.resetForTests(RootExecutor { request ->
            calls++
            if (request.entryMarker == null) RootProcessOutcome(0, "0".toByteArray()) else {
                if (calls == 2) { started.complete(Unit); finish.await(); drained = true }
                entered(request)
            }
        }, { "/synthetic/su" })
        assertTrue(UserRootSession.requestAuthorization().succeeded)
        val epoch = UserRootSession.authorizedEpoch()
        val read = async { UserRootSession.runFileRequest(RootReadRequest.ReadFile("/synthetic/file", 128)) }
        started.await(); read.cancel(); yield()
        assertFalse(drained); assertFalse(read.isCompleted)
        finish.complete(Unit); read.join()
        assertTrue(drained); assertTrue(read.isCancelled)
        assertEquals(epoch, UserRootSession.authorizedEpoch())
        assertTrue(UserRootSession.runPrivilegeAction(PrivilegeAction.WIFI_STATE).succeeded)
        assertEquals(3, calls)
    }
    @Test fun aRealServerTimeoutDuringCancelledReadStillInvalidatesGrant() = runBlocking {
        val started = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        var calls = 0
        UserRootSession.resetForTests(RootExecutor { request ->
            calls++
            if (request.entryMarker == null) RootProcessOutcome(0, "0".toByteArray()) else {
                started.complete(Unit); finish.await(); entered(request, timedOut = true)
            }
        }, { "/synthetic/su" })
        UserRootSession.requestAuthorization()
        val read = async { UserRootSession.runFileRequest(RootReadRequest.ReadFile("/synthetic/file", 128)) }
        started.await(); read.cancel(); finish.complete(Unit); read.join()
        assertEquals(RootStatus.TimedOut, UserRootSession.status.value)
        assertNull(UserRootSession.authorizedEpoch())
        assertFalse(UserRootSession.runPrivilegeAction(PrivilegeAction.WIFI_STATE).succeeded)
        assertEquals(2, calls)
    }
    @Test fun protocolFailureDuringDrainCannotLeaveAReusableGrant() = runBlocking {
        val started = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        UserRootSession.resetForTests(RootExecutor { request ->
            if (request.entryMarker == null) RootProcessOutcome(0, "0".toByteArray()) else {
                started.complete(Unit); finish.await(); throw java.io.IOException("synthetic broken pipe")
            }
        }, { "/synthetic/su" })
        UserRootSession.requestAuthorization()
        val read = async { UserRootSession.runFileRequest(RootReadRequest.ReadFile("/synthetic/file", 128)) }
        started.await(); read.cancel(); finish.complete(Unit); read.join()
        assertEquals(RootStatus.Error, UserRootSession.status.value)
        assertNull(UserRootSession.authorizedEpoch())
    }
    @Test fun explicitRevocationCannotBeOverwrittenByAClosingPipeFailure() = runBlocking {
        val started = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        UserRootSession.resetForTests(RootExecutor { request ->
            if (request.entryMarker == null) RootProcessOutcome(0, "0".toByteArray()) else {
                started.complete(Unit); finish.await(); throw java.io.IOException("synthetic revoked pipe")
            }
        }, { "/synthetic/su" })
        UserRootSession.requestAuthorization()
        val read = async { runCatching { UserRootSession.runFileRequest(RootReadRequest.ReadFile("/synthetic/file", 128)) } }
        started.await(); UserRootSession.revokeAuthorization(); finish.complete(Unit)
        assertTrue(read.await().isFailure)
        assertEquals(RootStatus.NotRequested, UserRootSession.status.value)
        assertNull(UserRootSession.authorizedEpoch())
    }
}
