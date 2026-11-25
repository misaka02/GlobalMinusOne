package com.example.globalminusone

import com.example.globalminusone.system.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class RootSessionTest {
    @After fun reset() { UserRootSession.resetForTests() }
    private fun entered(r: RootProcessRequest, exit: Int = 0, text: String = "") =
        RootProcessOutcome(exit, text.toByteArray(), "${r.entryMarker}:0\n")
    private fun install(block: suspend (RootProcessRequest) -> RootProcessOutcome) {
        UserRootSession.resetForTests(RootExecutor(block), { "/synthetic/su" })
    }
    @Test fun noImplicitAuthorizationAndUnavailableDoesNotExecuteSu() = runBlocking {
        var calls = 0
        UserRootSession.resetForTests(RootExecutor { calls++; RootProcessOutcome(0) }, { null })
        assertEquals(RootStatus.NotRequested, UserRootSession.status.value)
        assertFalse(UserRootSession.runPrivilegeAction(PrivilegeAction.WIFI_ON).succeeded)
        assertEquals(0, calls)
        assertEquals(RootStatus.Unavailable, UserRootSession.requestAuthorization().status)
        assertEquals(0, calls)
    }
    @Test fun authorizationDistinguishesDenialTimeoutAndLaunchFailure() = runBlocking {
        install { assertEquals(30_000L, it.timeoutMs); RootProcessOutcome(1, stderr = "denied") }
        assertEquals(RootStatus.Denied, UserRootSession.requestAuthorization().status)
        install { RootProcessOutcome(null, timedOut = true) }
        assertEquals(RootStatus.TimedOut, UserRootSession.requestAuthorization().status)
        install { error("launch failed") }
        assertEquals(RootStatus.Error, UserRootSession.requestAuthorization().status)
    }
    @Test fun entryProtocolDistinguishesFileErrorFromRevokedSuAndStopsRetries() = runBlocking {
        var calls = 0
        install { r ->
            calls++
            when (calls) {
                1 -> RootProcessOutcome(0, "0\n".toByteArray())
                2 -> entered(r, 73).copy(stderr = "${r.entryMarker}:0\nSELinux file denial")
                else -> RootProcessOutcome(1, stderr = "su denied")
            }
        }
        assertTrue(UserRootSession.requestAuthorization().succeeded)
        assertFalse(UserRootSession.runPrivilegeAction(PrivilegeAction.WIFI_STATE).succeeded)
        assertEquals(RootStatus.Granted, UserRootSession.status.value)
        assertEquals(RootStatus.Denied, UserRootSession.runPrivilegeAction(PrivilegeAction.WIFI_STATE).status)
        UserRootSession.runPrivilegeAction(PrivilegeAction.WIFI_STATE)
        assertEquals(3, calls)
    }
    @Test fun uidSentinelIsRequiredEvenWhenSuReturnsExitZero() = runBlocking {
        var calls = 0
        install { if (++calls == 1) RootProcessOutcome(0, "0".toByteArray()) else RootProcessOutcome(0, "fake success".toByteArray()) }
        UserRootSession.requestAuthorization()
        val result = UserRootSession.runPrivilegeAction(PrivilegeAction.BLUETOOTH_ON)
        assertFalse(result.succeeded); assertEquals("", result.stdout)
        assertEquals(RootStatus.Error, UserRootSession.status.value)
    }
    @Test fun serverTimeoutEndsSessionWithoutAutomaticRetry() = runBlocking {
        var calls = 0
        install { r -> if (++calls == 1) RootProcessOutcome(0, "0".toByteArray())
            else entered(r, 124).copy(timedOut = true) }
        UserRootSession.requestAuthorization()
        assertEquals(RootStatus.TimedOut, UserRootSession.runPrivilegeAction(PrivilegeAction.WIFI_STATE).status)
        UserRootSession.runPrivilegeAction(PrivilegeAction.WIFI_STATE)
        assertEquals(2, calls)
    }
    @Test fun screenReadRecoversAnEarlierGrantOnceAfterTransportLoss() = runBlocking {
        var opens = 0
        install { request ->
            if (request.entryMarker == null) {
                opens++
                RootProcessOutcome(0, "0\n".toByteArray())
            } else {
                entered(request, 124).copy(timedOut = true)
            }
        }
        assertTrue(UserRootSession.requestAuthorization().succeeded)
        val oldEpoch = UserRootSession.authorizedEpoch()!!
        assertFalse(UserRootSession.runBoundedCommand("echo cache_probe", 4096, 2_000L).leaseCurrent)
        assertTrue(UserRootSession.connectForScreenRead(allowConfiguredConnect = false))
        assertFalse(UserRootSession.isAuthorizationCurrent(oldEpoch))
        assertEquals(2, opens)
        assertFalse(UserRootSession.runBoundedCommand("echo cache_probe", 4096, 2_000L).leaseCurrent)
        assertFalse(UserRootSession.connectForScreenRead(allowConfiguredConnect = true))
        assertEquals(2, opens)
    }

    @Test fun screenReadRespectsAColdSettingAndNeverReopensAfterUserRevocationOrDenial() = runBlocking {
        var opens = 0
        install { opens++; RootProcessOutcome(0, "0\n".toByteArray()) }
        assertFalse(UserRootSession.connectForScreenRead(allowConfiguredConnect = false))
        assertEquals(0, opens)
        assertTrue(UserRootSession.connectForScreenRead(allowConfiguredConnect = true))
        UserRootSession.revokeAuthorization()
        assertFalse(UserRootSession.connectForScreenRead(allowConfiguredConnect = true))
        assertEquals(1, opens)
        install { opens++; RootProcessOutcome(1) }
        assertFalse(UserRootSession.connectForScreenRead(allowConfiguredConnect = true))
        assertEquals(RootStatus.Denied, UserRootSession.status.value)
        assertFalse(UserRootSession.connectForScreenRead(allowConfiguredConnect = true))
        assertEquals(2, opens)
    }
    @Test fun configuredScreenReadCanRecoverAColdTransportFailureOnce() = runBlocking {
        var attempts = 0
        install {
            if (++attempts == 1) error("Synthetic socket closed at startup")
            RootProcessOutcome(0, "0\n".toByteArray())
        }
        assertFalse(UserRootSession.connectForScreenRead(allowConfiguredConnect = true))
        assertEquals(RootStatus.Error, UserRootSession.status.value)
        assertTrue(UserRootSession.connectForScreenRead(allowConfiguredConnect = true))
        assertEquals(2, attempts)
    }
    @Test fun revokeDiscardsLateAuthorizationAndLateFileBytes() = runBlocking {
        val started = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        install { started.complete(Unit); finish.await(); RootProcessOutcome(0, "0".toByteArray()) }
        val auth = async { UserRootSession.requestAuthorization() }
        started.await(); UserRootSession.revokeAuthorization(); finish.complete(Unit)
        assertFalse(auth.await().succeeded); assertEquals(RootStatus.NotRequested, UserRootSession.status.value)
        val readStarted = CompletableDeferred<Unit>(); val readFinish = CompletableDeferred<Unit>()
        install { r -> if (r.entryMarker == null) RootProcessOutcome(0, "0".toByteArray()) else {
            readStarted.complete(Unit); readFinish.await(); entered(r, text = "private")
        } }
        UserRootSession.requestAuthorization()
        val read = async { runCatching { UserRootSession.runFileRequest(RootReadRequest.ReadFile("/data/private", 128)) } }
        readStarted.await(); UserRootSession.revokeAuthorization(); readFinish.complete(Unit)
        assertTrue(read.await().isFailure); assertEquals(RootStatus.NotRequested, UserRootSession.status.value)
    }
    @Test fun fixedActionsUseWatchdogAndOutputLimitsDiscardPartialData() = runBlocking {
        install { r -> if (r.entryMarker == null) RootProcessOutcome(0, "0".toByteArray()) else {
            assertEquals(9_000L, r.timeoutMs)
            assertTrue(r.command.contains("id -u")); assertNotNull(r.entryMarker)
            entered(r).copy(bytes = ByteArray(r.maxBytes + 1))
        } }
        UserRootSession.requestAuthorization()
        val result = UserRootSession.runPrivilegeAction(PrivilegeAction.BLUETOOTH_STATE)
        assertFalse(result.succeeded); assertEquals("", result.stdout)
        assertTrue(result.reason.contains("上限"))
    }
    @Test fun unapprovedRootMutationsRefuseBeforeAnyExecutorCall() = runBlocking {
        var calls = 0
        install { calls++; RootProcessOutcome(0, "0".toByteArray()) }
        UserRootSession.requestAuthorization()
        val root = RootFileIo.location("/data")
        val entry = BrowserEntry(root, "data", true, "directory", null, null)
        val operations: List<suspend () -> Any?> = listOf(
            { RootFileIo.createDirectory(root, "new") }, { RootFileIo.createText(root, "a", byteArrayOf()) },
            { RootFileIo.write(root, byteArrayOf()) }, { RootFileIo.rename(entry, root, "renamed") },
            { RootFileIo.delete(entry) }, { RootFileIo.copy(entry, root, false) }, { RootFileIo.copy(entry, root, true) }
        )
        operations.forEach { operation ->
            val error = runCatching { operation() }.exceptionOrNull()
            assertNotNull(error)
        }
        assertEquals(1, calls)
    }
    @Test fun quotedPathsAndNulListingNeverInterpretFilenameSyntax() {
        assertEquals("'/a b/o'\"'\"'k;\$(id)'", rootShellPath("/a b/o'k;\$(id)"))
        assertEquals("/data/x", normalizeRootPath("//data//x/"))
        listOf("relative", "/a/../data", "/a/./x", "/x\nq", "/x\u0000q").forEach {
            assertTrue(runCatching { rootShellPath(it) }.isFailure)
        }
        val rows = decodeRootListing("f\u000012 1700000000\u0000//a b/'q.txt\u0000l\u00001 1\u0000/link\u0000".toByteArray()) { "text/plain" }
        assertEquals("/a b/'q.txt", rows[0].location.path); assertEquals(12L, rows[0].size)
        assertTrue(rows[1].blockedReason!!.contains("符号链接"))
        assertTrue(runCatching { decodeRootListing(byteArrayOf(0xC3.toByte(), 0x28)) { "" } }.isFailure)
        assertTrue(runCatching { decodeRootListing("f\u00001 1\u0000/x".toByteArray()) { "" } }.isFailure)
    }
    @Test fun authorizationIsReusedAndRevocationInvalidatesLeaseAndNotifiesOffCallerThread() = runBlocking {
        var authorizations = 0
        install { r -> if (r.entryMarker == null) { authorizations++; RootProcessOutcome(0, "0".toByteArray()) } else entered(r) }
        UserRootSession.requestAuthorization(); val epoch = UserRootSession.authorizedEpoch()!!
        UserRootSession.requestAuthorization()
        assertEquals(1, authorizations); assertTrue(UserRootSession.isAuthorizationCurrent(epoch))
        val signaled = java.util.concurrent.CountDownLatch(1)
        val callbackThread = java.util.concurrent.atomic.AtomicReference<Thread>()
        val handle = UserRootSession.registerRevocationListener { callbackThread.set(Thread.currentThread()); signaled.countDown() }
        try {
            UserRootSession.revokeAuthorization()
            assertFalse(UserRootSession.isAuthorizationCurrent(epoch))
            assertTrue(signaled.await(5, java.util.concurrent.TimeUnit.SECONDS))
            assertNotSame(Thread.currentThread(), callbackThread.get())
        } finally { handle.close() }
    }
}
