package com.example.globalminusone.service

import android.accessibilityservice.AccessibilityService
import com.example.globalminusone.screenassistant.AccessibilityScreenSnapshot
import com.example.globalminusone.screenassistant.BreenoLaunchOutcome
import com.example.globalminusone.screenassistant.ExtractedSubImageCandidate
import com.example.globalminusone.screenassistant.ScreenCaptureHub
import com.example.globalminusone.screenassistant.awaitScreenCaptureResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

internal data class OverlayCaptureConfig(val usePrivilegedCapture: Boolean, val rootModeEnabled: Boolean)
internal data class CapturedFrame<F : Any>(val frame: F, val snapshot: AccessibilityScreenSnapshot)
internal data class CaptureSurfaceState(
    val closedPanelNow: Boolean,
    val eyeProtectionWasVisible: Boolean,
    val orbObstructions: List<ExtractedSubImageCandidate>
)

internal interface OverlayCaptureSettings {
    fun readCaptureConfig(): OverlayCaptureConfig
    fun trackedForegroundPackage(): String
    fun allowBreenoPrivilegedShell(): Boolean
}

internal interface OverlayCaptureBackend<F : Any, A : Any> {
    val sdkInt: Int
    val hasOverlayPermission: Boolean
    val currentAccessibility: A?
    val hasActivePrivilegedSession: Boolean get() = false
    val supportsFastDeferredCapture: Boolean get() = false
    fun snapshot(source: A): AccessibilityScreenSnapshot
    suspend fun captureSnapshot(source: A): AccessibilityScreenSnapshot = snapshot(source)
    suspend fun collectEnrichedSnapshot(
        source: A?,
        trackedPackage: String,
        extraObstructions: List<ExtractedSubImageCandidate>,
        config: OverlayCaptureConfig
    ): AccessibilityScreenSnapshot = AccessibilityScreenSnapshot(emptyList(), emptyList())
    suspend fun captureNativeFast(source: A): Pair<F?, Int> = null to -1
    suspend fun capturePrivileged(
        baseSnapshot: AccessibilityScreenSnapshot,
        extraObstructions: List<ExtractedSubImageCandidate>,
        allowRootPrompt: Boolean
    ): CapturedFrame<F>?
    suspend fun capturePrivileged(
        baseSnapshot: AccessibilityScreenSnapshot,
        extraObstructions: List<ExtractedSubImageCandidate>,
        allowRootPrompt: Boolean,
        preferNativePixelsWithPrivilegedHierarchy: Boolean
    ): CapturedFrame<F>? = capturePrivileged(baseSnapshot, extraObstructions, allowRootPrompt)
    fun captureNative(source: A, reply: (F?, Int) -> Unit)
    fun isRecycled(frame: F): Boolean
    fun recycle(frame: F)
    suspend fun launchBreeno(allowPrivilegedShell: Boolean): BreenoLaunchOutcome
}

internal interface OverlayCaptureUi {
    val destroying: Boolean
    val panelVisible: Boolean
    fun dismissCaptureTools()
    fun prepareCaptureSurface(): CaptureSurfaceState
    fun restoreCaptureSurface()
    fun requestAccessibilityPermission(closePanel: Boolean)
    fun animateClosePanel()
    fun showMessage(message: String, long: Boolean)
    fun reportCaptureFailure(failure: Throwable)
}

private val snapshotWorkerExecutor: Executor by lazy {
    ThreadPoolExecutor(
        0,
        4,
        30L,
        TimeUnit.SECONDS,
        SynchronousQueue(),
        { runnable -> Thread(runnable, "gmo-a11y-snapshot").apply { isDaemon = true } }
    )
}

/**
 * Runs blocking Binder snapshot collection on a bounded worker thread so the service main thread
 * is never stalled and hangs time out promptly even when IPC calls do not observe cancellation.
 */
internal suspend fun <T> runBoundedBlockingWork(
    timeoutMs: Long,
    executor: Executor = snapshotWorkerExecutor,
    block: () -> T
): T? {
    val future = try {
        CompletableFuture.supplyAsync({ block() }, executor)
    } catch (_: Throwable) {
        return null
    }
    return withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine<T?> { continuation ->
            future.whenComplete { value, error ->
                if (!continuation.isActive) return@whenComplete
                if (error != null) continuation.resume(null) else continuation.resume(value)
            }
        }
    }
}

/** Scheduling uses the service's existing scope and session; frames transfer only on successful delivery. */
internal class OverlayScreenCapturePipeline<F : Any, A : Any>(
    private val scope: CoroutineScope,
    private val captureSession: OverlayCaptureSession,
    private val backend: OverlayCaptureBackend<F, A>,
    private val settings: OverlayCaptureSettings,
    private val ui: OverlayCaptureUi
) {
    fun capture(
        featureLabel: String,
        onCaptured: (F, AccessibilityScreenSnapshot) -> Unit
    ): Job? = captureWithEnrichment(featureLabel, onEnrichedSnapshot = null) { frame, snap, _ ->
        onCaptured(frame, snap)
    }

    fun captureWithEnrichment(
        featureLabel: String,
        onEnrichedSnapshot: ((AccessibilityScreenSnapshot) -> Unit)? = null,
        onCaptured: (F, AccessibilityScreenSnapshot, Deferred<AccessibilityScreenSnapshot>?) -> Unit
    ): Job? {
        val config = settings.readCaptureConfig()
        val hasPrivilegeAtEntry = config.usePrivilegedCapture || config.rootModeEnabled || backend.hasActivePrivilegedSession
        val a11y = backend.currentAccessibility
        if (a11y == null && !hasPrivilegeAtEntry) {
            ui.requestAccessibilityPermission(closePanel = true)
            return null
        }
        if (backend.sdkInt < 30 && !hasPrivilegeAtEntry) {
            ui.showMessage("${featureLabel}需要 Android 11 及以上系统无障碍截屏，或开启 Shizuku/Root 原画质截屏", long = false)
            return null
        }
        if (!backend.hasOverlayPermission) {
            ui.showMessage("${featureLabel}需要悬浮窗权限", long = false)
            return null
        }
        captureSession.cancelRegionCapture()
        captureSession.cancelBreenoLaunch()
        ui.dismissCaptureTools()
        captureSession.clearSnapshot()
        val (closedPanelNow, eyeProtectionWasVisible, orbObstructions) = ui.prepareCaptureSurface()

        val generation = captureSession.nextGeneration()
        val job = scope.launch {
            var enrichmentJob: Job? = null
            try {
                if (eyeProtectionWasVisible) {
                    delay(90L)
                } else if (closedPanelNow) {
                    delay(50L)
                }
                val serviceNow = backend.currentAccessibility
                val trackedPkg = settings.trackedForegroundPackage()

                if (backend.supportsFastDeferredCapture) {
                    val baseFastSnap = AccessibilityScreenSnapshot(
                        textNodes = emptyList(),
                        mediaNodes = emptyList(),
                        foregroundPackage = trackedPkg,
                        overlayObstructions = orbObstructions
                    )
                    // Start view-tree collection BEFORE overlay mounting while target app is still foreground & visible.
                    val enrichmentDeferred = CompletableDeferred<AccessibilityScreenSnapshot>()
                    val enrichment = scope.launch {
                        try {
                            val snap = try {
                                backend.collectEnrichedSnapshot(serviceNow, trackedPkg, orbObstructions, config)
                            } catch (cancelled: CancellationException) {
                                enrichmentDeferred.cancel(cancelled)
                                throw cancelled
                            } catch (_: Exception) {
                                AccessibilityScreenSnapshot(
                                    textNodes = emptyList(),
                                    mediaNodes = emptyList(),
                                    foregroundPackage = trackedPkg,
                                    overlayObstructions = orbObstructions
                                )
                            }
                            currentCoroutineContext().ensureActive()
                            enrichmentDeferred.complete(snap)
                            if (captureSession.isCurrentGeneration(generation) && !ui.destroying) {
                                onEnrichedSnapshot?.invoke(snap)
                            }
                        } finally {
                            if (!enrichmentDeferred.isCompleted) enrichmentDeferred.cancel()
                        }
                    }
                    enrichment.invokeOnCompletion { if (!enrichmentDeferred.isCompleted) enrichmentDeferred.cancel() }
                    enrichmentDeferred.invokeOnCompletion { if (enrichmentDeferred.isCancelled) enrichment.cancel() }
                    enrichmentJob = enrichment
                    captureSession.trackRegionEnrichment(enrichment)

                    var capturedFrame: F? = null
                    var capturedSnap: AccessibilityScreenSnapshot = baseFastSnap
                    var nativeError: Int? = null
                    var nativeSource = serviceNow

                    suspend fun captureNativePixels(source: A?) {
                        if (source == null || backend.sdkInt < 30) return
                        val (frame, error) = backend.captureNativeFast(source)
                        capturedFrame = frame
                        if (frame == null) nativeError = error
                    }

                    if (!config.usePrivilegedCapture) captureNativePixels(nativeSource)
                    if (capturedFrame == null && nativeError != AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW &&
                        (config.usePrivilegedCapture || config.rootModeEnabled || backend.hasActivePrivilegedSession)
                    ) {
                        suspend fun capturePreferred(): CapturedFrame<F>? = try {
                            backend.capturePrivileged(
                                baseSnapshot = baseFastSnap,
                                extraObstructions = orbObstructions,
                                allowRootPrompt = config.rootModeEnabled,
                                preferNativePixelsWithPrivilegedHierarchy = false
                            )
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            ui.reportCaptureFailure(failure)
                            null
                        }
                        val privOutcome = if (nativeSource != null && backend.sdkInt >= 30 &&
                            config.usePrivilegedCapture && backend.hasActivePrivilegedSession
                        ) awaitPreferredPixels(::capturePreferred) else capturePreferred()
                        if (privOutcome != null) {
                            capturedFrame = privOutcome.frame
                            capturedSnap = privOutcome.snapshot
                        }
                    }
                    if (capturedFrame == null && config.usePrivilegedCapture &&
                        !ui.destroying && captureSession.isCurrentGeneration(generation)
                    ) {
                        nativeSource = backend.currentAccessibility
                        captureNativePixels(nativeSource)
                    }
                    val frameOut = capturedFrame
                    if (frameOut != null) {
                        var delivered = false
                        try {
                            if (ui.destroying || !captureSession.isCurrentGeneration(generation)) return@launch
                            val readyEnriched = withTimeoutOrNull(450L) { enrichmentDeferred.await() }
                            val initialSnap = if (readyEnriched != null) {
                                ScreenCaptureHub.mergeSnapshots(
                                    primary = readyEnriched,
                                    uiDump = capturedSnap,
                                    extraObstructions = orbObstructions
                                )
                            } else capturedSnap
                            currentCoroutineContext().ensureActive()
                            if (ui.destroying || !captureSession.isCurrentGeneration(generation)) return@launch
                            onCaptured(frameOut, initialSnap, enrichmentDeferred)
                            delivered = true
                        } finally {
                            if (!delivered) {
                                enrichmentJob?.cancel()
                                if (!backend.isRecycled(frameOut)) backend.recycle(frameOut)
                            }
                        }
                        return@launch
                    }
                    enrichmentJob?.cancel()
                    if (ui.destroying || !captureSession.isCurrentGeneration(generation)) return@launch
                    if (nativeSource == null) {
                        val hint = if (config.usePrivilegedCapture || config.rootModeEnabled || backend.hasActivePrivilegedSession) {
                            "Shizuku/Root 原画质截屏未就绪，且无障碍服务未连接"
                        } else {
                            "请先开启无障碍服务以使用${featureLabel}"
                        }
                        ui.showMessage(hint, long = true)
                        if (!(config.usePrivilegedCapture || config.rootModeEnabled || backend.hasActivePrivilegedSession)) {
                            ui.requestAccessibilityPermission(closePanel = false)
                        }
                        return@launch
                    }
                    val hint = when (nativeError) {
                        AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS ->
                            "请重新开关一次无障碍服务以启用${featureLabel}权限"
                        AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW ->
                            "当前页面受系统安全限制，请切换到允许截图的页面后重试"
                        null -> if (backend.sdkInt < 30) "原画质截屏未就绪，当前系统不支持无障碍截图"
                            else "${featureLabel}捕获超时，请重试"
                        -1 -> "${featureLabel}捕获超时，请重试"
                        else -> "${featureLabel}捕获画面失败 ($nativeError)"
                    }
                    ui.showMessage(hint, long = true)
                    return@launch
                }

                val rawSnapshot = serviceNow?.let { backend.captureSnapshot(it) }
                    ?: AccessibilityScreenSnapshot(emptyList(), emptyList())
                val baseSnapshot = if (!ScreenCaptureHub.isSafeInspectablePackage(rawSnapshot.foregroundPackage) && trackedPkg.isNotBlank()) {
                    rawSnapshot.copy(foregroundPackage = trackedPkg)
                } else {
                    rawSnapshot
                }

                val hasPrivilege = config.usePrivilegedCapture || backend.hasActivePrivilegedSession
                val privilegedOutcome = if (hasPrivilege) {
                    backend.capturePrivileged(
                        baseSnapshot = baseSnapshot,
                        extraObstructions = orbObstructions,
                        allowRootPrompt = config.usePrivilegedCapture && config.rootModeEnabled,
                        preferNativePixelsWithPrivilegedHierarchy = !config.usePrivilegedCapture && serviceNow != null
                    )
                } else null

                if (privilegedOutcome != null) {
                    if (ui.destroying || !captureSession.isCurrentGeneration(generation)) {
                        if (!backend.isRecycled(privilegedOutcome.frame)) backend.recycle(privilegedOutcome.frame)
                        return@launch
                    }
                    try {
                        onCaptured(privilegedOutcome.frame, privilegedOutcome.snapshot, null)
                    } catch (failure: Throwable) {
                        if (!backend.isRecycled(privilegedOutcome.frame)) backend.recycle(privilegedOutcome.frame)
                        throw failure
                    }
                    return@launch
                }

                if (serviceNow == null) {
                    val hint = if (hasPrivilege) {
                        "Shizuku/Root 原画质截屏未就绪，且无障碍服务未连接"
                    } else {
                        "请先开启无障碍服务以使用${featureLabel}"
                    }
                    ui.showMessage(hint, long = true)
                    if (!hasPrivilege) ui.requestAccessibilityPermission(closePanel = false)
                    return@launch
                }

                val mergedSnapshot = ScreenCaptureHub.mergeSnapshots(
                    primary = baseSnapshot,
                    uiDump = AccessibilityScreenSnapshot(emptyList(), emptyList()),
                    extraObstructions = orbObstructions
                )
                val result = withTimeoutOrNull(5_000L) {
                    awaitScreenCaptureResult<Pair<F?, Int>>(
                        discard = { it.first?.let { frame -> backend.recycle(frame) } }
                    ) { reply ->
                        backend.captureNative(serviceNow) { bitmap, error -> reply(bitmap to error) }
                    }
                }
                if (ui.destroying || !captureSession.isCurrentGeneration(generation)) {
                    result?.first?.let { backend.recycle(it) }
                    return@launch
                }
                val bitmap = result?.first
                if (bitmap != null) {
                    try {
                        onCaptured(bitmap, mergedSnapshot, null)
                    } catch (failure: Throwable) {
                        if (!backend.isRecycled(bitmap)) backend.recycle(bitmap)
                        throw failure
                    }
                } else {
                    val hint = when (result?.second) {
                        AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS ->
                            "请重新开关一次无障碍服务以启用${featureLabel}权限"
                        AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW ->
                            "当前页面受系统安全限制，可开启 Shizuku/Root 截屏或切换页面后重试"
                        null -> "${featureLabel}捕获超时，请重试"
                        else -> "${featureLabel}捕获画面失败 (${result.second})"
                    }
                    ui.showMessage(hint, long = true)
                }
            } catch (cancelled: CancellationException) {
                enrichmentJob?.cancel()
                throw cancelled
            } catch (failure: Throwable) {
                enrichmentJob?.cancel()
                ui.reportCaptureFailure(failure)
                ui.showMessage("${featureLabel}未完成，请重试", long = true)
            } finally {
                if (captureSession.isCurrentGeneration(generation) && !ui.destroying) {
                    ui.restoreCaptureSurface()
                }
            }
        }
        captureSession.trackRegionCapture(job)
        return job
    }

    private suspend fun awaitPreferredPixels(operation: suspend () -> CapturedFrame<F>?): CapturedFrame<F>? {
        var worker: Job? = null
        var pendingFrame: CapturedFrame<F>? = null
        fun release(captured: CapturedFrame<F>?) {
            captured?.frame?.let { if (!backend.isRecycled(it)) backend.recycle(it) }
        }
        return try {
            withTimeoutOrNull(1_200L) {
                awaitScreenCaptureResult<CapturedFrame<F>?>(discard = ::release) { receive ->
                    worker = scope.launch { receive(operation()) }
                }.also { pendingFrame = it }
            }?.also { pendingFrame = null }
        } finally {
            worker?.cancel()
            release(pendingFrame)
        }
    }

    fun launchBreeno(): Job {
        captureSession.cancelBreenoLaunch()
        captureSession.cancelRegionCapture()
        ui.dismissCaptureTools()
        captureSession.clearSnapshot()
        ui.animateClosePanel()
        val job = scope.launch {
            var waitedMs = 0L
            while (waitedMs < 480L && ui.panelVisible) {
                delay(25L)
                waitedMs += 25L
            }
            delay(80L)
            if (ui.destroying) return@launch
            val outcome = backend.launchBreeno(settings.allowBreenoPrivilegedShell())
            // Keep target screen unobstructed once dispatch succeeds; surface diagnostic toast only on failure.
            if (!outcome.launched) {
                ui.showMessage(outcome.message, long = true)
            }
        }
        captureSession.trackBreenoLaunch(job)
        return job
    }

}
