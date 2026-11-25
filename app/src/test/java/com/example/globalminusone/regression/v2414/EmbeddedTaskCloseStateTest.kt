package com.example.globalminusone.regression.v2414

import com.example.globalminusone.browser.EmbeddedTaskCloseState
import com.example.globalminusone.browser.EmbeddedTaskCloseState.*
import org.junit.Assert.*
import org.junit.Test

class EmbeddedTaskCloseStateTest {
    private fun own(root: Boolean = false) = Evidence(root, "launch-a", "com.android.chrome")
    private class Backend(var tasks: Map<Int, Evidence>, var complete: Boolean = true) : Port {
        val removed = mutableListOf<Int>()
        var reads = 0
        val rejected = mutableSetOf<Int>()
        val throwing = mutableSetOf<Int>()
        var queryFails = false
        var currentEvidence: Map<Int, Evidence>? = null
        override fun snapshot(): Snapshot {
            reads++
            if (queryFails) error("grant unavailable")
            return Snapshot(tasks, complete)
        }
        override fun revalidate(taskId: Int): Evidence? = (currentEvidence ?: tasks)[taskId]
        override fun remove(taskId: Int): Boolean {
            removed += taskId
            if (taskId in throwing) error("binder failed")
            return taskId !in rejected
        }
    }
    private fun state(candidates: Set<Int> = emptySet(), preexisting: Set<Int> = emptySet()) =
        EmbeddedTaskCloseState("launch-a", "com.android.chrome", preexisting, candidates)

    @Test fun partialFalseAndThrowRetainIndependentOutcomesAndRetryOnlyUnconfirmed() {
        val backend = Backend(mapOf(1 to own(), 2 to own(), 3 to own()))
        backend.rejected += 2
        backend.throwing += 3
        val state = state()
        val first = state.verify(backend)
        assertEquals(Outcome.REMOVED, first.outcomes[1])
        assertEquals(Outcome.REMOVE_REJECTED, first.outcomes[2])
        assertEquals(Outcome.REMOVE_FAILED, first.outcomes[3])
        assertFalse(first.confirmed())
        backend.rejected.clear()
        backend.throwing.clear()
        assertTrue(state.verify(backend).confirmed())
        assertEquals(1, backend.removed.count { it == 1 })
        assertEquals(Outcome.REMOVE_REJECTED, first.outcomes[2]) // Immutable historical result.
    }

    @Test fun reusedIdCannotBeDeletedFromOldLedgerEvenWhenPackageIsStillChrome() {
        val state = state(setOf(7))
        val backend = Backend(mapOf(7 to own()))
        backend.rejected += 7
        state.verify(backend)
        backend.tasks = mapOf(7 to Evidence(false, "other-launch", "com.android.chrome"))
        val result = state.verify(backend)
        assertEquals(listOf(7), backend.removed)
        assertEquals(Outcome.IDENTITY_UNCONFIRMED, result.outcomes[7])
        assertFalse(result.confirmed())
        assertEquals("launch-a", result.session)
    }

    @Test fun idReusedBetweenSnapshotAndRemovalFailsFreshPerItemAuthority() {
        val backend = Backend(mapOf(7 to own()))
        backend.currentEvidence = mapOf(7 to Evidence(false, "replacement-launch", "com.android.chrome"))
        val result = state(setOf(7)).verify(backend)
        assertEquals(Outcome.IDENTITY_UNCONFIRMED, result.outcomes[7])
        assertFalse(result.confirmed())
        assertTrue(backend.removed.isEmpty())
    }

    @Test fun escapedOrFinishedTaskCanBeRemovedWithFreshMatchingSessionAndPackage() {
        val backend = Backend(mapOf(7 to Evidence(true, null, "com.android.chrome")))
        backend.currentEvidence = mapOf(7 to own(root = false))
        val result = state(setOf(7)).verify(backend)
        assertEquals(Outcome.REMOVED, result.outcomes[7])
        assertEquals(listOf(7), backend.removed)
        assertTrue(result.confirmed())
        assertFalse(result.ownershipEvidence[7]!!.currentRoot)
    }

    @Test fun freshSessionMarkerStillNeedsMatchingPackageAndKnownIdentity() {
        val backend = Backend(mapOf(7 to own(), 8 to own()))
        backend.currentEvidence = mapOf(7 to Evidence(false, "launch-a", "other.app"))
        val result = state(setOf(7, 8)).verify(backend)
        assertEquals(Outcome.IDENTITY_UNCONFIRMED, result.outcomes[7])
        assertEquals(Outcome.IDENTITY_UNCONFIRMED, result.outcomes[8])
        assertTrue(backend.removed.isEmpty())
        assertFalse(result.confirmed())
    }

    @Test fun finishedRootChildWithoutMarkerIsRetainedButNeverBlindlyRemoved() {
        val state = state(setOf(8))
        val backend = Backend(mapOf(8 to Evidence(true, null, "other.nested.app")))
        backend.rejected += 8
        state.verify(backend)
        backend.tasks = mapOf(8 to Evidence(false, null, "other.nested.app"))
        assertEquals(Outcome.IDENTITY_UNCONFIRMED, state.verify(backend).outcomes[8])
        assertEquals(listOf(8), backend.removed)
    }

    @Test fun preexistingAndUserTabsAreExcludedEvenFromCurrentRoot() {
        val backend = Backend(mapOf(1 to own(true), 2 to Evidence(false, null, "com.android.chrome"),
            3 to Evidence(false, "launch-a", "other.app"), 4 to own()))
        val result = state(setOf(1), setOf(1)).verify(backend)
        assertEquals(listOf(4), backend.removed)
        assertEquals(setOf(4), result.outcomes.keys)
        assertEquals(setOf(1), result.preexisting)
    }

    @Test fun absentOnlyConfirmedByCompleteRunningAndRecentSnapshot() {
        val state = state(setOf(12))
        val backend = Backend(emptyMap(), complete = false)
        assertFalse(state.verify(backend).confirmed())
        backend.complete = true
        val result = state.verify(backend)
        assertEquals(Outcome.ABSENT, result.outcomes[12])
        assertFalse(result.confirmed()) // Missed discovery before root removal stays explicit.
        assertTrue(backend.removed.isEmpty())
    }

    @Test fun completeAbsenceCanConfirmKnownCandidateWithoutRemovingAnotherTask() {
        val backend = Backend(emptyMap())
        assertTrue(state(setOf(12)).verify(backend).confirmed())
        assertTrue(backend.removed.isEmpty())
    }

    @Test fun repeatedCloseIsBoundedAndGrantLossDoesNotEraseEvidence() {
        val state = state(setOf(12), setOf(5))
        val backend = Backend(emptyMap())
        backend.queryFails = true
        repeat(6) { assertFalse(state.verify(backend).confirmed()) }
        assertEquals(2, backend.reads)
        val result = state.result()
        assertTrue(result.snapshotUnconfirmed)
        assertEquals(setOf(12), result.outcomes.keys)
        assertEquals(setOf(5), result.preexisting)
        assertEquals(2, result.passes)
    }

    @Test fun independentNewLaunchCannotOverwriteOldEvidence() {
        val old = state(setOf(9))
        val backend = Backend(mapOf(9 to own()))
        backend.rejected += 9
        val result = old.verify(backend)
        val next = EmbeddedTaskCloseState("launch-b", "com.android.chrome", setOf(9), emptySet())
        assertTrue(next.verify(backend).confirmed())
        assertFalse(result.confirmed())
        assertEquals(setOf(9), result.outcomes.keys)
        assertEquals("launch-a", result.session)
    }
}
