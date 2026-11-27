package com.example.globalminusone.service

import android.accessibilityservice.AccessibilityService
import com.example.globalminusone.screenassistant.AccessibilityScreenSnapshot
import com.example.globalminusone.screenassistant.BreenoLaunchOutcome
import com.example.globalminusone.screenassistant.ExtractedSubImageCandidate
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import com.example.globalminusone.service.capture.CaptureSurfaceState
import com.example.globalminusone.service.capture.CapturedFrame
import com.example.globalminusone.service.capture.OverlayCaptureBackend
import com.example.globalminusone.service.capture.OverlayCaptureConfig
import com.example.globalminusone.service.capture.OverlayCaptureSession
import com.example.globalminusone.service.capture.OverlayCaptureSettings
import com.example.globalminusone.service.capture.OverlayCaptureUi
import com.example.globalminusone.service.capture.OverlayScreenCapturePipeline

class OverlayScreenCapturePipelineTest {
    private class Frame(var recycled: Boolean = false) { var recycleCalls = 0 }
    private class Fixture(parent: CoroutineScope) {
        val parentJob = SupervisorJob(parent.coroutineContext[Job])
        val scope = CoroutineScope(parent.coroutineContext + parentJob)
        val session = OverlayCaptureSession()
        val events = mutableListOf<String>()
        var config = OverlayCaptureConfig(false, false)
        var accessibility: String? = "initial"
        var accessibilityReads = 0
        var sdk = 36
        var permission = true
        var rawSnapshot = snapshot()
        var trackedPackage = "tracked.app"
        var surface = CaptureSurfaceState(false, false, emptyList())
        var destroying = false
        var visible = false
        var panelReads = 0
        var shellAllowed = false
        var fastCapture = false
        var activePrivilege = false
        var nativeFast: suspend (String) -> Pair<Frame?, Int> = { Frame() to 0 }
        var enrich: suspend () -> AccessibilityScreenSnapshot = { snapshot() }
        var onDismiss: () -> Unit = {}
        var onPrepare: () -> Unit = {}
        var onSnapshot: () -> Unit = {}
        var privileged: suspend (AccessibilityScreenSnapshot, Boolean) -> CapturedFrame<Frame>? = { _, _ -> null }
        var native: (String, (Frame?, Int) -> Unit) -> Unit = { _, reply -> reply(Frame(), 0) }
        var launch: suspend (Boolean) -> BreenoLaunchOutcome = { BreenoLaunchOutcome(true, false, "ok", emptyList()) }
        val messages = mutableListOf<Pair<String, Boolean>>()
        val failures = mutableListOf<Throwable>()
        val backend = object : OverlayCaptureBackend<Frame, String> {
            override val sdkInt: Int get() { events += "sdk"; return sdk }
            override val hasOverlayPermission: Boolean get() { events += "permission"; return permission }
            override val currentAccessibility: String? get() { events += "accessibility"; accessibilityReads++; return accessibility }
            override val supportsFastDeferredCapture: Boolean get() = fastCapture
            override val hasActivePrivilegedSession: Boolean get() = activePrivilege
            override suspend fun captureNativeFast(source: String): Pair<Frame?, Int> {
                events += "fast:$source"
                return nativeFast(source)
            }
            override suspend fun collectEnrichedSnapshot(source: String?, trackedPackage: String,
                extraObstructions: List<ExtractedSubImageCandidate>, config: OverlayCaptureConfig
            ): AccessibilityScreenSnapshot = enrich()
            override fun snapshot(source: String): AccessibilityScreenSnapshot {
                events += "snapshot:$source"; onSnapshot(); return rawSnapshot
            }
            override suspend fun capturePrivileged(baseSnapshot: AccessibilityScreenSnapshot, extraObstructions: List<ExtractedSubImageCandidate>, allowRootPrompt: Boolean): CapturedFrame<Frame>? {
                events += "privileged"; assertEquals(surface.orbObstructions, extraObstructions)
                return privileged(baseSnapshot, allowRootPrompt)
            }
            override fun captureNative(source: String, reply: (Frame?, Int) -> Unit) { events += "native:$source"; native(source, reply) }
            override fun isRecycled(frame: Frame): Boolean { events += "isRecycled"; return frame.recycled }
            override fun recycle(frame: Frame) { events += "recycle"; frame.recycled = true; frame.recycleCalls++ }
            override suspend fun launchBreeno(allowPrivilegedShell: Boolean): BreenoLaunchOutcome { events += "breeno:$allowPrivilegedShell"; return launch(allowPrivilegedShell) }
        }
        val settings = object : OverlayCaptureSettings {
            override fun readCaptureConfig(): OverlayCaptureConfig { events += "config"; return config }
            override fun trackedForegroundPackage(): String { events += "tracked"; return trackedPackage }
            override fun allowBreenoPrivilegedShell(): Boolean { events += "shellSetting"; return shellAllowed }
        }
        val ui = object : OverlayCaptureUi {
            override val destroying: Boolean get() = this@Fixture.destroying
            override val panelVisible: Boolean get() { panelReads++; return visible }
            override fun dismissCaptureTools() { events += "dismiss"; onDismiss() }
            override fun prepareCaptureSurface(): CaptureSurfaceState { events += "prepare"; onPrepare(); return surface }
            override fun restoreCaptureSurface() { events += "restore" }
            override fun requestAccessibilityPermission(closePanel: Boolean) { events += "prompt:$closePanel" }
            override fun animateClosePanel() { events += "closeAnimated" }
            override fun showMessage(message: String, long: Boolean) { events += "message"; messages += message to long }
            override fun reportCaptureFailure(failure: Throwable) { events += "failure"; failures += failure }
        }
        val pipeline = OverlayScreenCapturePipeline(scope, session, backend, settings, ui)
        suspend fun close() = parentJob.cancelAndJoin()
    }
    private fun fixture(block: suspend Fixture.() -> Unit) = runBlocking {
        val f = Fixture(this)
        try { f.block() } finally { f.close() }
    }
    private companion object {
        fun snapshot(pkg: String = "") = AccessibilityScreenSnapshot(emptyList(), emptyList(), foregroundPackage = pkg)
    }

    @Test fun screenAssistantFastPathFallsBackToLiveAccessibilityWhenPreferredCaptureFails() = fixture {
        fastCapture = true
        config = OverlayCaptureConfig(usePrivilegedCapture = true, rootModeEnabled = false)
        for (thrown in listOf(false, true)) {
            events.clear(); messages.clear(); failures.clear()
            accessibility = "original-service"
            val frame = Frame()
            privileged = { _, _ ->
                accessibility = "reconnected-service"
                if (thrown) throw java.io.IOException("privileged session disconnected")
                null
            }
            nativeFast = { frame to 0 }
            var mounts = 0
            pipeline.captureWithEnrichment("全局识屏") { received, _, _ ->
                mounts++
                assertSame(frame, received)
            }!!.join()
            assertEquals(1, mounts)
            assertTrue(events.indexOf("privileged") in 1 until events.indexOf("fast:reconnected-service"))
            assertEquals(if (thrown) 1 else 0, failures.size)
            assertTrue(messages.isEmpty())
            assertEquals(0, frame.recycleCalls)
        }
    }

    @Test fun fastCaptureRespectsProtectedWindowAndDoesNotRetryWithPrivilege() = fixture {
        fastCapture = true
        activePrivilege = true
        nativeFast = { null to AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW }
        pipeline.capture("全局识屏") { _, _ -> fail("protected result must not open") }!!.join()
        assertFalse(events.contains("privileged"))
        assertTrue(messages.single().first.contains("系统安全限制"))
    }

    @Test fun cancellationWhileWaitingForHierarchyReleasesFrameWithoutMounting() = runTest {
        val fixture = Fixture(this)
        try {
            with(fixture) {
                fastCapture = true
                val pixelsReady = CompletableDeferred<Unit>()
                val frame = Frame()
                nativeFast = { pixelsReady.complete(Unit); frame to 0 }
                enrich = { awaitCancellation() }
                val job = pipeline.captureWithEnrichment("全局识屏") { _, _, _ -> fail("cancelled frame mounted") }!!
                pixelsReady.await()
                job.cancelAndJoin()
                assertEquals(1, frame.recycleCalls)
                assertTrue(messages.isEmpty())
            }
        } finally {
            fixture.close()
        }
    }

    @Test fun failedWindowMountRecyclesFastCaptureAndReportsFailure() = fixture {
        fastCapture = true
        val frame = Frame()
        val mountFailure = IllegalStateException("全局识屏窗口未挂载")
        nativeFast = { frame to 0 }
        pipeline.captureWithEnrichment("全局识屏") { _, _, _ -> throw mountFailure }!!.join()
        assertEquals(1, frame.recycleCalls)
        assertSame(mountFailure, failures.single())
        assertEquals("全局识屏未完成，请重试" to true, messages.single())
    }

    @Test fun privilegedPixelsGetTheShellBeforeHierarchyAndWindowDoesNotWaitForIt() = runTest {
        val fixture = Fixture(this)
        try {
            with(fixture) {
                fastCapture = true
                config = OverlayCaptureConfig(usePrivilegedCapture = true, rootModeEnabled = false)
                val frame = Frame()
                val pixelsReady = CompletableDeferred<Unit>()
                val hierarchyReady = CompletableDeferred<AccessibilityScreenSnapshot>()
                val mounted = CompletableDeferred<Deferred<AccessibilityScreenSnapshot>>()
                privileged = { _, _ -> pixelsReady.await(); CapturedFrame(frame, snapshot()) }
                enrich = { events += "hierarchy"; hierarchyReady.await() }
                val job = pipeline.captureWithEnrichment("全局识屏") { received, _, deferred ->
                    assertSame(frame, received)
                    mounted.complete(checkNotNull(deferred))
                }!!
                while (!events.contains("privileged")) yield()
                assertFalse(events.contains("hierarchy"))
                pixelsReady.complete(Unit)
                val deferred = mounted.await()
                job.join()
                assertTrue(events.contains("hierarchy"))
                assertFalse(deferred.isCompleted)
                session.cancelRegionCapture()
                deferred.join()
                assertTrue(deferred.isCancelled)
            }
        } finally {
            fixture.close()
        }
    }

    @Test fun cancellingHandedOffSnapshotCancelsProducerWithoutClosingVisibleFrame() = runTest {
        val fixture = Fixture(this)
        try {
            with(fixture) {
                fastCapture = true
                val frame = Frame()
                nativeFast = { frame to 0 }
                var producerStopped = false
                enrich = { try { awaitCancellation() } finally { producerStopped = true } }
                val mounted = CompletableDeferred<Deferred<AccessibilityScreenSnapshot>>()
                pipeline.captureWithEnrichment("识屏") { _, _, deferred -> mounted.complete(checkNotNull(deferred)) }!!.join()
                val pending = mounted.await()
                pending.cancel()
                yield()
                assertTrue(pending.isCancelled)
                assertTrue(producerStopped)
                assertEquals(0, frame.recycleCalls)
                assertTrue(failures.isEmpty())
            }
        } finally { fixture.close() }
    }

    @Test fun cancelledEnrichmentCannotPublishNonCooperativeLateSnapshot() = runTest {
        val fixture = Fixture(this)
        try {
            with(fixture) {
                fastCapture = true
                val release = CompletableDeferred<Unit>()
                enrich = { withContext(NonCancellable) { release.await(); snapshot("late.app") } }
                var lateCallbacks = 0
                val mounted = CompletableDeferred<Deferred<AccessibilityScreenSnapshot>>()
                pipeline.captureWithEnrichment("识屏", onEnrichedSnapshot = { lateCallbacks++ }) { _, _, deferred ->
                    mounted.complete(checkNotNull(deferred))
                }!!.join()
                val pending = mounted.await()
                session.cancelRegionCapture()
                release.complete(Unit)
                pending.join()
                yield()
                assertTrue(pending.isCancelled)
                assertEquals(0, lateCallbacks)
            }
        } finally { fixture.close() }
    }

    @Test fun slowAuthorizedShellCannotHoldBackAvailableAccessibilityOrDeliverLatePixels() = runTest {
        val fixture = Fixture(this)
        try {
            with(fixture) {
                fastCapture = true
                activePrivilege = true
                config = OverlayCaptureConfig(usePrivilegedCapture = true, rootModeEnabled = false)
                val late = Frame()
                val native = Frame()
                val shellReturned = CompletableDeferred<Unit>()
                privileged = { _, _ ->
                    val outcome = withContext(NonCancellable) {
                        delay(3_000L)
                        CapturedFrame(late, snapshot())
                    }
                    shellReturned.complete(Unit)
                    outcome
                }
                nativeFast = { native to 0 }
                val frames = mutableListOf<Frame>()
                pipeline.capture("全局识屏") { frame, _ -> frames += frame }!!.join()
                assertEquals(listOf(native), frames)
                assertFalse(shellReturned.isCompleted)
                shellReturned.await()
                yield()
                assertEquals(listOf(native), frames)
                assertEquals(1, late.recycleCalls)
            }
        } finally {
            fixture.close()
        }
    }

    @Test fun missingAccessibilityPromptsBeforeSdkOrPermissionAndLeavesOldWorkUntouched() = fixture {
        accessibility = null
        val region = Job(); val breeno = Job(); val retained = snapshot("retained")
        session.trackRegionCapture(region); session.trackBreenoLaunch(breeno); session.retainSnapshot(retained)
        assertNull(pipeline.capture("识屏") { _, _ -> fail("early exit") })
        assertEquals(listOf("config", "accessibility", "prompt:true"), events)
        assertFalse(region.isCancelled); assertFalse(breeno.isCancelled)
        assertSame(retained, session.snapshotOrNull())
        region.cancel(); breeno.cancel()
    }

    @Test fun sdkThenOverlayPermissionEarlyExitsPreserveSession() = fixture {
        val region = Job(); val breeno = Job(); val retained = snapshot("retained")
        session.trackRegionCapture(region); session.trackBreenoLaunch(breeno); session.retainSnapshot(retained)
        sdk = 29; permission = false
        assertNull(pipeline.capture("截图") { _, _ -> fail("SDK exit") })
        assertEquals(listOf("config", "accessibility", "sdk", "message"), events)
        assertTrue(messages.single().first.contains("Android 11")); assertFalse(messages.single().second)
        events.clear(); messages.clear(); sdk = 36
        assertNull(pipeline.capture("截图") { _, _ -> fail("permission exit") })
        assertEquals(listOf("config", "accessibility", "sdk", "permission", "message"), events)
        assertEquals("截图需要悬浮窗权限" to false, messages.single())
        assertFalse(region.isCancelled); assertFalse(breeno.isCancelled); assertSame(retained, session.snapshotOrNull())
        region.cancel(); breeno.cancel()
    }

    @Test fun acceptedCaptureCancelsBothBeforeDismissAndClearsBeforePreparingAndTracksItsJob() = fixture {
        val region = Job(); val breeno = Job(); val retained = snapshot("retained")
        session.trackRegionCapture(region); session.trackBreenoLaunch(breeno); session.retainSnapshot(retained)
        onDismiss = { assertTrue(region.isCancelled); assertTrue(breeno.isCancelled); assertSame(retained, session.snapshotOrNull()) }
        onPrepare = { assertNull(session.snapshotOrNull()); assertTrue(session.isCurrentGeneration(1)) }
        lateinit var reply: (Frame?, Int) -> Unit
        native = { _, callback -> reply = callback }
        val job = pipeline.capture("识屏") { _, _ -> fail("cancelled") }!!
        assertTrue(session.isCurrentGeneration(2))
        yield()
        session.cancelRegionCapture(); job.join()
        assertTrue(job.isCancelled)
        val late = Frame(); reply(late, 0); assertEquals(1, late.recycleCalls)
        assertEquals(listOf("config", "accessibility", "sdk", "permission", "dismiss", "prepare"), events.take(6))
    }

    @Test fun privilegedSuccessUsesInitialConfigAndTransfersFrameWithoutRecycling() = fixture {
        config = OverlayCaptureConfig(true, true); accessibility = null; sdk = 26
        val frame = Frame(); val privilegedSnapshot = snapshot("privileged")
        privileged = { base, allowRoot ->
            assertEquals("tracked.app", base.foregroundPackage); assertTrue(allowRoot)
            CapturedFrame(frame, privilegedSnapshot)
        }
        val job = pipeline.capture("识屏") { delivered, snap ->
            assertSame(frame, delivered); assertSame(privilegedSnapshot, snap); events += "deliver"
        }!!
        config = OverlayCaptureConfig(false, false)
        job.join()
        assertEquals(2, accessibilityReads); assertEquals(1, events.count { it == "config" })
        assertEquals(0, frame.recycleCalls); assertFalse(events.any { it.startsWith("native:") })
        assertEquals(listOf("deliver", "restore"), events.takeLast(2)); assertTrue(messages.isEmpty())
    }

    @Test fun privilegedNullFallsBackToSecondAccessibilityAndMergesOrbAndTrackedPackage() = fixture {
        config = OverlayCaptureConfig(true, false)
        val orb = ExtractedSubImageCandidate("orb", 1, 2, 10, 20, "self", 1f)
        surface = CaptureSurfaceState(false, false, listOf(orb))
        val frame = Frame()
        native = { source, reply -> assertEquals("second", source); reply(frame, 0) }
        val job = pipeline.capture("识屏") { delivered, snap ->
            assertSame(frame, delivered); assertEquals("tracked.app", snap.foregroundPackage)
            assertEquals(listOf(orb), snap.overlayObstructions)
        }!!
        accessibility = "second"
        job.join()
        assertEquals(2, accessibilityReads)
        assertTrue(events.indexOf("tracked") < events.indexOf("snapshot:second"))
        assertTrue(events.indexOf("privileged") < events.indexOf("native:second"))
        assertEquals(0, frame.recycleCalls)
    }

    @Test fun disappearingAccessibilityUsesDistinctPrivilegedAndNativeHints() = fixture {
        for (usePrivileged in listOf(false, true)) {
            config = OverlayCaptureConfig(usePrivileged, false); accessibility = "initial"
            events.clear(); messages.clear()
            val job = pipeline.capture("识屏") { _, _ -> fail("no accessibility") }!!
            accessibility = null
            job.join()
            if (usePrivileged) {
                assertEquals("Shizuku/Root 原画质截屏未就绪，且无障碍服务未连接" to true, messages.single())
                assertFalse(events.contains("prompt:false"))
            } else {
                assertEquals("请先开启无障碍服务以使用识屏" to true, messages.single())
                assertTrue(events.indexOf("message") < events.indexOf("prompt:false"))
            }
            assertEquals("restore", events.last())
        }
    }

    @Test fun nativeSuccessPreservesSnapshotPackageAndHandsOffOwnership() = fixture {
        rawSnapshot = snapshot("actual.app")
        val frame = Frame()
        native = { _, reply -> reply(frame, 0) }
        pipeline.capture("截图") { delivered, snap -> assertSame(frame, delivered); assertEquals("actual.app", snap.foregroundPackage) }!!.join()
        assertEquals(0, frame.recycleCalls); assertFalse(events.contains("isRecycled"))
        assertEquals("restore", events.last()); assertTrue(messages.isEmpty())
    }

    @Test fun privilegedStaleOrDestroyedFramesRecycleOnlyWhenNeededAndSkipRestore() = fixture {
        config = OverlayCaptureConfig(true, false)
        for (destroy in listOf(false, true)) for (alreadyRecycled in listOf(false, true)) {
            destroying = false; events.clear()
            val frame = Frame(alreadyRecycled)
            privileged = { _, _ ->
                if (destroy) destroying = true else session.nextGeneration()
                CapturedFrame(frame, snapshot())
            }
            pipeline.capture("截图") { _, _ -> fail("stale") }!!.join()
            assertEquals(if (alreadyRecycled) 0 else 1, frame.recycleCalls)
            assertFalse(events.contains("restore")); assertTrue(messages.isEmpty())
        }
    }

    @Test fun nativeStaleOrDestroyedFramesRecycleUnconditionallyAndSkipRestore() = fixture {
        for (destroy in listOf(false, true)) {
            destroying = false; events.clear()
            val frame = Frame(true)
            native = { _, reply ->
                if (destroy) destroying = true else session.nextGeneration()
                reply(frame, 0)
            }
            pipeline.capture("截图") { _, _ -> fail("stale") }!!.join()
            assertEquals(1, frame.recycleCalls); assertFalse(events.contains("isRecycled"))
            assertFalse(events.contains("restore"))
        }
    }

    @Test fun nativeCancellationDiscardsLateAndAlreadyQueuedRepliesWithoutFailureToast() = fixture {
        for (replyBeforeCancel in listOf(false, true)) {
            events.clear()
            lateinit var reply: (Frame?, Int) -> Unit
            native = { _, callback -> reply = callback }
            val job = pipeline.capture("截图") { _, _ -> fail("cancelled delivery") }!!
            yield()
            val frame = Frame()
            if (replyBeforeCancel) reply(frame, 0)
            job.cancelAndJoin()
            if (!replyBeforeCancel) reply(frame, 0)
            yield()
            assertEquals(1, frame.recycleCalls)
            assertTrue(messages.isEmpty()); assertTrue(failures.isEmpty()); assertTrue(events.contains("restore"))
        }
    }

    @Test fun deliveryFailureReleasesPrivilegedAndNativeFramesThenReportsAndRestores() = fixture {
        for (usePrivileged in listOf(false, true)) {
            events.clear(); messages.clear(); failures.clear(); config = OverlayCaptureConfig(usePrivileged, false)
            val frame = Frame(); val failure = AssertionError("delivery")
            privileged = { _, _ -> CapturedFrame(frame, snapshot()) }; native = { _, reply -> reply(frame, 0) }
            pipeline.capture("截图") { _, _ -> throw failure }!!.join()
            assertEquals(1, frame.recycleCalls); assertSame(failure, failures.single())
            assertEquals("截图未完成，请重试" to true, messages.single())
            assertEquals(listOf("isRecycled", "recycle", "failure", "message", "restore"), events.takeLast(5))
        }
    }

    @Test fun deliveryCancellationIsRethrownAndReleasesFrameWithoutFailureToast() = fixture {
        val frame = Frame(); native = { _, reply -> reply(frame, 0) }
        val job = pipeline.capture("截图") { _, _ -> throw CancellationException("delivery cancelled") }!!
        job.join()
        assertTrue(job.isCancelled); assertEquals(1, frame.recycleCalls)
        assertTrue(messages.isEmpty()); assertTrue(failures.isEmpty()); assertEquals("restore", events.last())
    }

    @Test fun nativeErrorCodesKeepPermissionSecureAndGenericHints() = fixture {
        for ((code, expected) in listOf(
            AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS to "请重新开关一次无障碍服务以启用截图权限",
            AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW to "当前页面受系统安全限制，可开启 Shizuku/Root 截屏或切换页面后重试",
            97 to "截图捕获画面失败 (97)"
        )) {
            messages.clear(); native = { _, reply -> reply(null, code) }
            pipeline.capture("截图") { _, _ -> fail("error") }!!.join()
            assertEquals(expected to true, messages.single())
        }
        assertTrue(failures.isEmpty())
    }

    @Test fun nativeTimeoutKeepsHintAndDiscardsLateFrame() = fixture {
        lateinit var reply: (Frame?, Int) -> Unit
        native = { _, callback -> reply = callback }
        pipeline.capture("截图") { _, _ -> fail("timeout") }!!.join()
        assertEquals("截图捕获超时，请重试" to true, messages.single())
        val frame = Frame(); reply(frame, 0)
        assertEquals(1, frame.recycleCalls); assertTrue(failures.isEmpty()); assertTrue(events.contains("restore"))
    }

    @Test fun breenoCancelsInOrderAndReadsSettingsAfterWaitWithOnlyFailureToast() = fixture {
        for (success in listOf(false, true)) {
            events.clear(); messages.clear(); shellAllowed = false
            val breeno = Job(); val region = Job(); val retained = snapshot("retained")
            session.trackBreenoLaunch(breeno); session.trackRegionCapture(region); session.retainSnapshot(retained)
            onDismiss = { assertTrue(breeno.isCancelled); assertTrue(region.isCancelled); assertSame(retained, session.snapshotOrNull()) }
            launch = { allowed -> assertTrue(allowed); BreenoLaunchOutcome(success, true, "diagnostic", emptyList()) }
            val job = pipeline.launchBreeno()
            assertNull(session.snapshotOrNull()); assertEquals(listOf("dismiss", "closeAnimated"), events)
            yield(); assertFalse(events.contains("shellSetting"))
            shellAllowed = true
            job.join()
            assertEquals(listOf("shellSetting", "breeno:true") + if (success) emptyList() else listOf("message"), events.drop(2))
            assertEquals(if (success) emptyList<Pair<String, Boolean>>() else listOf("diagnostic" to true), messages)
        }
    }

    @Test fun breenoVisiblePanelWaitIsBoundedAndDestroySkipsSettingsAndLaunch() = fixture {
        visible = true
        pipeline.launchBreeno().join()
        assertEquals(20, panelReads); assertEquals(listOf("dismiss", "closeAnimated", "shellSetting", "breeno:false"), events)
        events.clear(); visible = false; destroying = true
        pipeline.launchBreeno().join()
        assertEquals(listOf("dismiss", "closeAnimated"), events); assertTrue(messages.isEmpty())
    }
}
