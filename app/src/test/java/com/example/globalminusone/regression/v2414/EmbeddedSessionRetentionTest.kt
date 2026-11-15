package com.example.globalminusone.regression.v2414

import com.example.globalminusone.browser.*
import org.junit.Assert.*
import org.junit.Test

class EmbeddedSessionRetentionTest {
    @Test fun missingTaskNeedsTwoIndependentHeartbeatSamples() {
        val presence = EmbeddedTaskPresence()
        assertFalse(presence.observe(false, false, 0)) // Resize/attach are not heartbeats.
        assertFalse(presence.observe(false, true, 1_000))
        assertFalse(presence.observe(false, true, 1_001)) // Duplicate status in same frame.
        assertFalse(presence.observe(false, false, 7_000))
        assertTrue(presence.observe(false, true, 7_000))
    }

    @Test fun returningTaskOrUnknownSnapshotBreaksMissingSequence() {
        val presence = EmbeddedTaskPresence()
        assertFalse(presence.observe(false, true, 0))
        assertFalse(presence.observe(true, false, 5_000))
        assertFalse(presence.observe(false, true, 10_000))
        presence.reset() // Engine uses this on an uncertain IPC query.
        assertFalse(presence.observe(false, true, 20_000))
        assertTrue(presence.observe(false, true, 25_000))
    }
    @Test fun finishedTaskKeepsItsOwnershipEvidenceUntilThisWindowCloses() {
        val tasks = EmbeddedOwnedTasks()
        tasks.observe(71, false, true, false) // Chrome need not preserve marker extras.
        val snapshot = tasks.snapshot()
        snapshot.clear() // A consumer must not erase the live evidence.
        assertEquals(setOf(71), tasks.snapshot())
        tasks.clear()
        assertTrue(tasks.snapshot().isEmpty())
    }

    @Test fun preexistingOrUnprovenTasksNeverBecomeCleanupCandidates() {
        val tasks = EmbeddedOwnedTasks()
        tasks.observe(12, true, true, true)
        tasks.observe(13, false, false, false)
        tasks.observe(14, false, false, true)
        assertEquals(setOf(14), tasks.snapshot())
    }
    @Test fun idleTransportKeepsActualLeaseUntilExplicitRetirement() {
        val lease = EmbeddedLeaseUsage(idleMs = null)
        assertTrue(lease.acquire(0)); lease.release(10)
        assertFalse(lease.expired(Long.MAX_VALUE))
        assertTrue(lease.acquire(Long.MAX_VALUE))
        assertEquals(1, lease.users)
        lease.retire()
        assertEquals(0, lease.users)
        assertFalse(lease.acquire(Long.MAX_VALUE))
    }

    @Test fun liveGrantAndBinderSurviveOperationRejectionButRealLossRetires() {
        assertFalse(EmbeddedConnectionPolicy.mustClose(true, true))
        assertTrue(EmbeddedConnectionPolicy.mustClose(false, true))
        assertTrue(EmbeddedConnectionPolicy.mustClose(true, false))
        assertTrue(EmbeddedConnectionPolicy.mustClose(true, true, confirmedClosed = true))
    }

    @Test fun surfaceReplacementRejectsOldAckAndReattachesWithoutBackgroundPolling() {
        val state = EmbeddedPresentationState()
        state.initialOutput()
        val old = state.revision
        state.outputLost()
        assertFalse(state.attached)
        assertFalse(state.confirmAttached(old))
        assertFalse(state.shouldHeartbeat(true, false))
        assertTrue(state.confirmAttached(state.revision))
        state.change(true)
        assertFalse(state.shouldHeartbeat(true, false))
        assertFalse(state.confirmAttached(state.revision))
        state.disposed()
        assertFalse(state.shouldHeartbeat(false, false))
    }

    @Test fun multipleTargetsReuseExistingSessionAndDoNotEvictAtLimit() {
        val registry = EmbeddedWindowRegistry<String, Any>(2)
        val first = registry.add("first") { Any() }!!
        val second = registry.add("second") { Any() }!!
        assertSame(first, registry.add("first") { error("must not recreate") })
        assertNull(registry.add("third") { error("must not create at limit") })
        assertFalse(registry.remove("first", Any()))
        assertSame(second, registry["second"])
        assertTrue(registry.remove("first", first))
        assertNotNull(registry.add("third") { slot -> assertEquals(0, slot); Any() })
        assertSame(second, registry["second"])
    }

    @Test fun destroyReturnsEachOwnedHostOnceAndClearsRegistry() {
        val registry = EmbeddedWindowRegistry<String, Any>()
        val one = registry.add("a") { Any() }!!
        val two = registry.add("b") { Any() }!!
        val closed = registry.clear()
        assertEquals(2, closed.size)
        assertTrue(closed.any { it === one }); assertTrue(closed.any { it === two })
        assertTrue(registry.values().isEmpty()); assertTrue(registry.clear().isEmpty())
    }

    @Test fun onlyInitialLaunchCreatesChromeWindowAndNewTaskIsExcludedFromRecents() {
        val initial = ChromeLaunchPlan.create(true, 7, -1, true)
        val navigation = ChromeLaunchPlan.create(false, 7, 91, true)
        assertTrue(initial.preferNew); assertTrue(initial.createNewTab)
        assertEquals(0x00800000, initial.flags and 0x00800000)
        assertFalse(navigation.preferNew); assertFalse(navigation.createNewTab)
        assertEquals(91, navigation.taskId)
        assertEquals(0, navigation.flags and 0x08000000)
        assertEquals(0x00800000, EmbeddedAppLaunchPlan.flags(33, 0, 0, true, true) and 0x00800000)
    }
}
