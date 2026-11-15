package com.example.globalminusone.regression.v2414

import com.example.globalminusone.split.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Retires the old focus/move fake: those operations are no longer a production pairing route. */
class SplitPairControllerTest {
    private val origin = SplitOrigin("app.source", 11, 0, 1)
    private class Observer(var focused: Int = 11, var denied: Boolean = false) : SplitTaskExecutor {
        val requests = mutableListOf<SplitSystemRequest>()
        override suspend fun execute(request: SplitSystemRequest): SplitSystemResult {
            requests += request
            return when (request) {
                SplitSystemRequest.VendorProbe -> if (denied) SplitSystemResult(4, "", "permission denied")
                    else SplitSystemResult(0, VENDOR_SPLIT_CAPABLE, "")
                SplitSystemRequest.Snapshot -> SplitSystemResult(0, encodeSplitSnapshot(SplitTaskSnapshot(listOf(
                    SplitTask(11, null, 0, 0, "fullscreen", true, "standard", "app.source", SplitRect(0, 0, 1000, 1000))), focused)), "")
                is SplitSystemRequest.VendorEdge -> SplitSystemResult(0, VENDOR_SPLIT_ACCEPTED, "")
                else -> throw AssertionError("Legacy mutation must never be used: $request")
            }
        }
    }
    @Test fun deniedVendorCapabilityDoesNotReadOrMutateTasks() = runBlocking {
        val observer = Observer(denied = true)
        assertFalse(SplitPairController(observer).pair(origin, "app.target/.Main", "app.target").confirmed)
        assertEquals(listOf(SplitSystemRequest.VendorProbe), observer.requests)
    }
    @Test fun changedForegroundIsNotRepairedByFocusOrOrdinaryLaunch() = runBlocking {
        val observer = Observer(focused = 99)
        assertFalse(SplitPairController(observer).pair(origin, "app.target/.Main", "app.target").confirmed)
        assertEquals(listOf(SplitSystemRequest.VendorProbe, SplitSystemRequest.Snapshot), observer.requests)
    }
    @Test fun acceptanceWithoutObservedPairNeverConfirmsAndNeverRollsBackPersonalTasks() = runBlocking {
        val observer = Observer()
        assertFalse(SplitPairController(observer).pair(origin, "app.target/.Main", "app.target").confirmed)
        assertEquals(1, observer.requests.count { it is SplitSystemRequest.VendorEdge })
        assertTrue(observer.requests.all { it == SplitSystemRequest.VendorProbe || it == SplitSystemRequest.Snapshot || it is SplitSystemRequest.VendorEdge })
    }
}
