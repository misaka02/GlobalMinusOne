package com.example.globalminusone.service

import android.graphics.Rect
import com.example.globalminusone.screenassistant.AccessibilityScreenSnapshot
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job

internal data class ScreenAssistantFrameSnapshot(
    val initial: AccessibilityScreenSnapshot,
    val enriched: Deferred<AccessibilityScreenSnapshot>?,
    val displayCrop: Rect? = null
)

/** Called in the service's existing main-thread order; the service owns scheduling and resources. */
internal class OverlayCaptureSession {
    private var regionCaptureJob: Job? = null
    private var regionEnrichmentJob: Job? = null
    private var breenoLaunchJob: Job? = null
    private var screenCaptureGeneration = 0L
    private var lastScreenAccessibilitySnapshot: AccessibilityScreenSnapshot? = null

    fun cancelRegionCapture() {
        // Invalidate callbacks even when a backend does not cooperate with cancellation.
        screenCaptureGeneration++
        regionCaptureJob?.cancel()
        regionEnrichmentJob?.cancel()
    }

    fun cancelBreenoLaunch() {
        breenoLaunchJob?.cancel()
    }

    fun trackRegionCapture(job: Job) {
        regionCaptureJob = job
    }

    fun trackRegionEnrichment(job: Job) {
        regionEnrichmentJob = job
    }

    fun trackBreenoLaunch(job: Job) {
        breenoLaunchJob = job
    }

    fun nextGeneration(): Long = ++screenCaptureGeneration

    fun isCurrentGeneration(generation: Long): Boolean = generation == screenCaptureGeneration

    fun snapshotOrNull(): AccessibilityScreenSnapshot? = lastScreenAccessibilitySnapshot

    fun retainSnapshot(snapshot: AccessibilityScreenSnapshot) {
        lastScreenAccessibilitySnapshot = snapshot
    }

    fun clearSnapshot() {
        lastScreenAccessibilitySnapshot = null
    }
}
