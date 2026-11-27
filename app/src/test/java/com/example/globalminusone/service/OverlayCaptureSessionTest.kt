package com.example.globalminusone.service

import com.example.globalminusone.screenassistant.AccessibilityScreenSnapshot
import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Test
import com.example.globalminusone.service.capture.OverlayCaptureSession

class OverlayCaptureSessionTest {
    private fun snapshot(packageName: String = "synthetic.app") =
        AccessibilityScreenSnapshot(emptyList(), emptyList(), foregroundPackage = packageName)

    @Test fun regionCancellationLeavesBreenoAndParentActive() {
        val parent = Job()
        try {
            val region = Job(parent)
            val breeno = Job(parent)
            val session = OverlayCaptureSession()
            session.trackRegionCapture(region)
            session.trackBreenoLaunch(breeno)
            session.cancelRegionCapture()
            assertTrue(region.isCancelled)
            assertTrue(breeno.isActive)
            assertTrue(parent.isActive)
            session.cancelRegionCapture() // Repeating cancellation does not affect the other lane.
            assertTrue(breeno.isActive)
        } finally {
            parent.cancel()
        }
    }

    @Test fun breenoCancellationLeavesRegionAndParentActive() {
        val parent = Job()
        try {
            val region = Job(parent)
            val breeno = Job(parent)
            val session = OverlayCaptureSession()
            session.trackRegionCapture(region)
            session.trackBreenoLaunch(breeno)
            session.cancelBreenoLaunch()
            assertTrue(breeno.isCancelled)
            assertTrue(region.isActive)
            assertTrue(parent.isActive)
        } finally {
            parent.cancel()
        }
    }

    @Test fun trackingReplacementJobsDoesNotCancelOldCallerOwnedJobs() {
        val parent = Job()
        try {
            val oldRegion = Job(parent)
            val newRegion = Job(parent)
            val oldBreeno = Job(parent)
            val newBreeno = Job(parent)
            val session = OverlayCaptureSession()
            session.trackRegionCapture(oldRegion)
            session.trackBreenoLaunch(oldBreeno)
            session.trackRegionCapture(newRegion)
            session.trackBreenoLaunch(newBreeno)
            assertTrue(listOf(oldRegion, newRegion, oldBreeno, newBreeno).all { it.isActive })
            session.cancelRegionCapture()
            session.cancelBreenoLaunch()
            assertTrue(newRegion.isCancelled)
            assertTrue(newBreeno.isCancelled)
            assertTrue(oldRegion.isActive)
            assertTrue(oldBreeno.isActive)
            // The caller still owns cancellation of jobs it replaced without cancelling first.
            oldRegion.cancel()
            oldBreeno.cancel()
            assertTrue(parent.isActive)
        } finally {
            parent.cancel()
        }
    }

    @Test fun generationAdvanceInvalidatesOldGenerationWithoutTouchingJobsOrSnapshot() {
        val parent = Job()
        try {
            val session = OverlayCaptureSession()
            val region = Job(parent)
            val breeno = Job(parent)
            val retained = snapshot()
            session.trackRegionCapture(region)
            session.trackBreenoLaunch(breeno)
            session.retainSnapshot(retained)
            assertTrue(session.isCurrentGeneration(0L))
            assertEquals(1L, session.nextGeneration())
            assertFalse(session.isCurrentGeneration(0L))
            assertTrue(session.isCurrentGeneration(1L))
            assertEquals(2L, session.nextGeneration())
            assertFalse(session.isCurrentGeneration(1L))
            assertTrue(session.isCurrentGeneration(2L))
            assertSame(retained, session.snapshotOrNull())
            assertTrue(region.isActive)
            assertTrue(breeno.isActive)
        } finally {
            parent.cancel()
        }
    }

    @Test fun snapshotReadsRetainReferenceAndClearOnlyDropsSnapshot() {
        val parent = Job()
        try {
            val session = OverlayCaptureSession()
            assertNull(session.snapshotOrNull())
            val region = Job(parent)
            val breeno = Job(parent)
            session.trackRegionCapture(region)
            session.trackBreenoLaunch(breeno)
            val generation = session.nextGeneration()
            val first = snapshot("first.app")
            session.retainSnapshot(first)
            assertSame(first, session.snapshotOrNull())
            assertSame(first, session.snapshotOrNull()) // Reading must not consume the crop source.
            val replacement = snapshot("replacement.app")
            session.retainSnapshot(replacement)
            assertSame(replacement, session.snapshotOrNull())
            session.clearSnapshot()
            assertNull(session.snapshotOrNull())
            assertTrue(session.isCurrentGeneration(generation))
            assertTrue(region.isActive)
            assertTrue(breeno.isActive)
            session.cancelRegionCapture()
            session.cancelBreenoLaunch()
            assertTrue(region.isCancelled)
            assertTrue(breeno.isCancelled)
        } finally {
            parent.cancel()
        }
    }

    @Test fun cancellationInvalidatesGenerationButKeepsRetainedSnapshotIncludingWhenNoJobWasTracked() {
        val session = OverlayCaptureSession()
        val retained = snapshot()
        session.retainSnapshot(retained)
        session.cancelRegionCapture()
        session.cancelBreenoLaunch()
        assertFalse(session.isCurrentGeneration(0L))
        assertSame(retained, session.snapshotOrNull())
        val region = Job()
        val breeno = Job()
        session.trackRegionCapture(region)
        session.trackBreenoLaunch(breeno)
        val generation = session.nextGeneration()
        session.cancelRegionCapture()
        session.cancelBreenoLaunch()
        assertTrue(region.isCancelled)
        assertTrue(breeno.isCancelled)
        assertFalse(session.isCurrentGeneration(generation))
        assertSame(retained, session.snapshotOrNull())
    }

    @Test fun separateSessionsKeepJobsGenerationsAndSnapshotsIndependent() {
        val parent = Job()
        try {
            val first = OverlayCaptureSession()
            val second = OverlayCaptureSession()
            val firstRegion = Job(parent)
            val firstBreeno = Job(parent)
            val secondRegion = Job(parent)
            val secondBreeno = Job(parent)
            first.trackRegionCapture(firstRegion)
            first.trackBreenoLaunch(firstBreeno)
            second.trackRegionCapture(secondRegion)
            second.trackBreenoLaunch(secondBreeno)
            val firstSnapshot = snapshot("first.app")
            val secondSnapshot = snapshot("second.app")
            first.retainSnapshot(firstSnapshot)
            second.retainSnapshot(secondSnapshot)
            first.nextGeneration()
            first.cancelRegionCapture()
            first.cancelBreenoLaunch()
            first.clearSnapshot()
            assertTrue(firstRegion.isCancelled)
            assertTrue(firstBreeno.isCancelled)
            assertTrue(secondRegion.isActive)
            assertTrue(secondBreeno.isActive)
            assertTrue(second.isCurrentGeneration(0L))
            assertSame(secondSnapshot, second.snapshotOrNull())
            assertTrue(parent.isActive)
        } finally {
            parent.cancel()
        }
    }
}
