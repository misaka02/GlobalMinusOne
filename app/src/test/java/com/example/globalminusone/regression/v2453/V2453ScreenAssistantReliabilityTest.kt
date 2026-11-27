package com.example.globalminusone.regression.v2453

import com.example.globalminusone.screenassistant.AccessibilityScreenSnapshot
import com.example.globalminusone.screenassistant.BreenoLaunchOutcome
import com.example.globalminusone.screenassistant.ExtractedSubImageCandidate
import com.example.globalminusone.screenassistant.ScreenTextBlock
import com.example.globalminusone.service.capture.CaptureSurfaceState
import com.example.globalminusone.service.capture.CapturedFrame
import com.example.globalminusone.service.capture.OverlayCaptureBackend
import com.example.globalminusone.service.capture.OverlayCaptureConfig
import com.example.globalminusone.service.capture.OverlayCaptureSession
import com.example.globalminusone.service.capture.OverlayCaptureSettings
import com.example.globalminusone.service.capture.OverlayCaptureUi
import com.example.globalminusone.service.capture.OverlayScreenCapturePipeline
import com.example.globalminusone.service.capture.runBoundedBlockingWork
import com.example.globalminusone.system.privilege.RootExecutor
import com.example.globalminusone.system.privilege.RootProcessOutcome
import com.example.globalminusone.system.privilege.RootStatus
import com.example.globalminusone.system.privilege.UserRootSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class V2453ScreenAssistantReliabilityTest {

    @Test
    fun capturePipeline_allowsActivePrivilegedSessionEvenWithoutAccessibilityAndEvenOnPreApi30() = runBlocking {
        val parentJob = SupervisorJob(coroutineContext[Job])
        val scope = CoroutineScope(coroutineContext + parentJob)
        try {
            val messages = mutableListOf<String>()
            val prompts = mutableListOf<Boolean>()
            var preferNativeArg: Boolean? = null
            var deliveredFrame: String? = null

            val backend = object : OverlayCaptureBackend<String, String> {
                override val sdkInt: Int = 26
                override val hasOverlayPermission: Boolean = true
                override val currentAccessibility: String? = null
                override val hasActivePrivilegedSession: Boolean = true
                override fun snapshot(source: String) = AccessibilityScreenSnapshot(emptyList(), emptyList())
                override suspend fun capturePrivileged(
                    baseSnapshot: AccessibilityScreenSnapshot,
                    extraObstructions: List<ExtractedSubImageCandidate>,
                    allowRootPrompt: Boolean
                ): CapturedFrame<String>? = null
                override suspend fun capturePrivileged(
                    baseSnapshot: AccessibilityScreenSnapshot,
                    extraObstructions: List<ExtractedSubImageCandidate>,
                    allowRootPrompt: Boolean,
                    preferNativePixelsWithPrivilegedHierarchy: Boolean
                ): CapturedFrame<String> {
                    preferNativeArg = preferNativePixelsWithPrivilegedHierarchy
                    return CapturedFrame("shizuku-frame", AccessibilityScreenSnapshot(emptyList(), emptyList()))
                }
                override fun captureNative(source: String, reply: (String?, Int) -> Unit) = reply(null, 0)
                override fun isRecycled(frame: String) = false
                override fun recycle(frame: String) {}
                override suspend fun launchBreeno(allowPrivilegedShell: Boolean) = BreenoLaunchOutcome(true, false, "ok", emptyList())
            }
            val settings = object : OverlayCaptureSettings {
                override fun readCaptureConfig() = OverlayCaptureConfig(usePrivilegedCapture = false, rootModeEnabled = false)
                override fun trackedForegroundPackage() = ""
                override fun allowBreenoPrivilegedShell() = false
            }
            val ui = object : OverlayCaptureUi {
                override val destroying = false
                override val panelVisible = false
                override fun dismissCaptureTools() {}
                override fun prepareCaptureSurface() = CaptureSurfaceState(false, false, emptyList())
                override fun restoreCaptureSurface() {}
                override fun requestAccessibilityPermission(closePanel: Boolean) { prompts += closePanel }
                override fun animateClosePanel() {}
                override fun showMessage(message: String, long: Boolean) { messages += message }
                override fun reportCaptureFailure(failure: Throwable) {}
            }

            val pipeline = OverlayScreenCapturePipeline(scope, OverlayCaptureSession(), backend, settings, ui)
            val job = pipeline.capture("全局识屏") { frame, _ -> deliveredFrame = frame }
            assertNotNull(job)
            job?.join()
            assertTrue(prompts.isEmpty())
            assertTrue(messages.isEmpty())
            assertEquals(false, preferNativeArg)
            assertEquals("shizuku-frame", deliveredFrame)
        } finally {
            parentJob.cancelAndJoin()
        }
    }

    @Test
    fun capturePipeline_selectsFastNativePixelsOnlyWhenAutoAndAccessibilityPresent() = runBlocking {
        val parentJob = SupervisorJob(coroutineContext[Job])
        val scope = CoroutineScope(coroutineContext + parentJob)
        try {
            val recordedFlags = mutableListOf<Boolean>()
            var explicitCapture = false

            val backend = object : OverlayCaptureBackend<String, String> {
                override val sdkInt: Int = 36
                override val hasOverlayPermission: Boolean = true
                override val currentAccessibility: String = "a11y"
                override val hasActivePrivilegedSession: Boolean = true
                override fun snapshot(source: String) = AccessibilityScreenSnapshot(emptyList(), emptyList())
                override suspend fun capturePrivileged(
                    baseSnapshot: AccessibilityScreenSnapshot,
                    extraObstructions: List<ExtractedSubImageCandidate>,
                    allowRootPrompt: Boolean
                ): CapturedFrame<String>? = null
                override suspend fun capturePrivileged(
                    baseSnapshot: AccessibilityScreenSnapshot,
                    extraObstructions: List<ExtractedSubImageCandidate>,
                    allowRootPrompt: Boolean,
                    preferNativePixelsWithPrivilegedHierarchy: Boolean
                ): CapturedFrame<String> {
                    recordedFlags += preferNativePixelsWithPrivilegedHierarchy
                    return CapturedFrame("ok", AccessibilityScreenSnapshot(emptyList(), emptyList()))
                }
                override fun captureNative(source: String, reply: (String?, Int) -> Unit) = reply("native", 0)
                override fun isRecycled(frame: String) = false
                override fun recycle(frame: String) {}
                override suspend fun launchBreeno(allowPrivilegedShell: Boolean) = BreenoLaunchOutcome(true, false, "ok", emptyList())
            }
            val settings = object : OverlayCaptureSettings {
                override fun readCaptureConfig() = OverlayCaptureConfig(usePrivilegedCapture = explicitCapture, rootModeEnabled = false)
                override fun trackedForegroundPackage() = ""
                override fun allowBreenoPrivilegedShell() = false
            }
            val ui = object : OverlayCaptureUi {
                override val destroying = false
                override val panelVisible = false
                override fun dismissCaptureTools() {}
                override fun prepareCaptureSurface() = CaptureSurfaceState(false, false, emptyList())
                override fun restoreCaptureSurface() {}
                override fun requestAccessibilityPermission(closePanel: Boolean) {}
                override fun animateClosePanel() {}
                override fun showMessage(message: String, long: Boolean) {}
                override fun reportCaptureFailure(failure: Throwable) {}
            }

            val pipeline = OverlayScreenCapturePipeline(scope, OverlayCaptureSession(), backend, settings, ui)
            pipeline.capture("全局识屏") { _, _ -> }!!.join()
            explicitCapture = true
            pipeline.capture("全局识屏") { _, _ -> }!!.join()
            assertEquals(listOf(true, false), recordedFlags)
        } finally {
            parentJob.cancelAndJoin()
        }
    }

    @Test
    fun capturePipeline_privilegesUnavailableAndMissingA11y_showsDiagnosticHintWithoutSettingLoop() = runBlocking {
        val parentJob = SupervisorJob(coroutineContext[Job])
        val scope = CoroutineScope(coroutineContext + parentJob)
        try {
            val messages = mutableListOf<String>()
            val prompts = mutableListOf<Boolean>()

            val backend = object : OverlayCaptureBackend<String, String> {
                override val sdkInt: Int = 36
                override val hasOverlayPermission: Boolean = true
                override val currentAccessibility: String? = null
                override val hasActivePrivilegedSession: Boolean = true
                override fun snapshot(source: String) = AccessibilityScreenSnapshot(emptyList(), emptyList())
                override suspend fun capturePrivileged(
                    baseSnapshot: AccessibilityScreenSnapshot,
                    extraObstructions: List<ExtractedSubImageCandidate>,
                    allowRootPrompt: Boolean
                ): CapturedFrame<String>? = null
                override fun captureNative(source: String, reply: (String?, Int) -> Unit) = reply(null, 0)
                override fun isRecycled(frame: String) = false
                override fun recycle(frame: String) {}
                override suspend fun launchBreeno(allowPrivilegedShell: Boolean) = BreenoLaunchOutcome(true, false, "ok", emptyList())
            }
            val settings = object : OverlayCaptureSettings {
                override fun readCaptureConfig() = OverlayCaptureConfig(usePrivilegedCapture = false, rootModeEnabled = false)
                override fun trackedForegroundPackage() = ""
                override fun allowBreenoPrivilegedShell() = false
            }
            val ui = object : OverlayCaptureUi {
                override val destroying = false
                override val panelVisible = false
                override fun dismissCaptureTools() {}
                override fun prepareCaptureSurface() = CaptureSurfaceState(false, false, emptyList())
                override fun restoreCaptureSurface() {}
                override fun requestAccessibilityPermission(closePanel: Boolean) { prompts += closePanel }
                override fun animateClosePanel() {}
                override fun showMessage(message: String, long: Boolean) { messages += message }
                override fun reportCaptureFailure(failure: Throwable) {}
            }

            val pipeline = OverlayScreenCapturePipeline(scope, OverlayCaptureSession(), backend, settings, ui)
            pipeline.capture("全局识屏") { _, _ -> }!!.join()
            assertTrue(prompts.isEmpty())
            assertEquals(listOf("Shizuku/Root 原画质截屏未就绪，且无障碍服务未连接"), messages)
        } finally {
            parentJob.cancelAndJoin()
        }
    }

    @Test
    fun capturePipeline_snapshotRunsOnWorkerDispatcherAndSlowSnapshotTimesOutCleanly() = runBlocking {
        val parentJob = SupervisorJob(coroutineContext[Job])
        val scope = CoroutineScope(coroutineContext + parentJob)
        val releaseSlowSnapshot = CountDownLatch(1)
        var snapshotThreadName = ""
        val callerThreadName = Thread.currentThread().name
        var deliveredFrame: String? = null
        var deliveredSnapshot: AccessibilityScreenSnapshot? = null
        try {
            val backend = object : OverlayCaptureBackend<String, String> {
                override val sdkInt: Int = 36
                override val hasOverlayPermission: Boolean = true
                override val currentAccessibility: String = "a11y"
                override val hasActivePrivilegedSession: Boolean = false
                override fun snapshot(source: String): AccessibilityScreenSnapshot {
                    snapshotThreadName = Thread.currentThread().name
                    releaseSlowSnapshot.await(2, TimeUnit.SECONDS)
                    return AccessibilityScreenSnapshot(listOf(ScreenTextBlock("x", "late", 0, 0, 10, 10)), emptyList())
                }
                override suspend fun captureSnapshot(source: String): AccessibilityScreenSnapshot =
                    runBoundedBlockingWork(timeoutMs = 200L) { snapshot(source) }
                        ?: AccessibilityScreenSnapshot(emptyList(), emptyList())
                override suspend fun capturePrivileged(
                    baseSnapshot: AccessibilityScreenSnapshot,
                    extraObstructions: List<ExtractedSubImageCandidate>,
                    allowRootPrompt: Boolean
                ): CapturedFrame<String>? = null
                override fun captureNative(source: String, reply: (String?, Int) -> Unit) = reply("native-ok", 0)
                override fun isRecycled(frame: String) = false
                override fun recycle(frame: String) {}
                override suspend fun launchBreeno(allowPrivilegedShell: Boolean) = BreenoLaunchOutcome(true, false, "ok", emptyList())
            }
            val settings = object : OverlayCaptureSettings {
                override fun readCaptureConfig() = OverlayCaptureConfig(usePrivilegedCapture = false, rootModeEnabled = false)
                override fun trackedForegroundPackage() = "com.demo.app"
                override fun allowBreenoPrivilegedShell() = false
            }
            val ui = object : OverlayCaptureUi {
                override val destroying = false
                override val panelVisible = false
                override fun dismissCaptureTools() {}
                override fun prepareCaptureSurface() = CaptureSurfaceState(false, false, emptyList())
                override fun restoreCaptureSurface() {}
                override fun requestAccessibilityPermission(closePanel: Boolean) {}
                override fun animateClosePanel() {}
                override fun showMessage(message: String, long: Boolean) {}
                override fun reportCaptureFailure(failure: Throwable) {}
            }

            val pipeline = OverlayScreenCapturePipeline(
                scope = scope,
                captureSession = OverlayCaptureSession(),
                backend = backend,
                settings = settings,
                ui = ui
            )
            pipeline.capture("全局识屏") { frame, snap ->
                deliveredFrame = frame
                deliveredSnapshot = snap
            }!!.join()
            assertTrue(snapshotThreadName.isNotBlank() && snapshotThreadName != callerThreadName)
            assertEquals("native-ok", deliveredFrame)
            assertEquals("com.demo.app", deliveredSnapshot?.foregroundPackage)
            assertTrue(deliveredSnapshot?.textNodes?.isEmpty() == true)
        } finally {
            releaseSlowSnapshot.countDown()
            parentJob.cancelAndJoin()
        }
    }

    @Test
    fun userRootSession_uiDumpUsesCompressedBoundedInnerTimeoutWithoutRevokingRootGrant() = runBlocking {
        val observedCommands = mutableListOf<String>()
        try {
            UserRootSession.resetForTests(
                probe = { "/system/bin/su" },
                executor = RootExecutor { request ->
                    observedCommands += request.command
                    val prefix = request.entryMarker?.let { "$it:0\n" }.orEmpty()
                    when {
                        request.command.contains("id -u") ->
                            RootProcessOutcome(0, "0\n".toByteArray(), prefix, timedOut = false)
                        request.command.contains("uiautomator dump") ->
                            RootProcessOutcome(124, byteArrayOf(), prefix, timedOut = false)
                        else ->
                            RootProcessOutcome(0, byteArrayOf(), prefix, timedOut = false)
                    }
                }
            )
            val auth = UserRootSession.requestAuthorization()
            assertTrue(auth.succeeded)
            val epochBefore = UserRootSession.authorizedEpoch()
            assertNotNull(epochBefore)

            assertNull(UserRootSession.dumpUiHierarchyXml())

            assertEquals(RootStatus.Granted, UserRootSession.status.value)
            assertEquals(epochBefore, UserRootSession.authorizedEpoch())
            assertTrue(observedCommands.any { it.contains("uiautomator dump --compressed") && it.contains("/system/bin/timeout") })
        } finally {
            UserRootSession.resetForTests()
        }
    }
}
