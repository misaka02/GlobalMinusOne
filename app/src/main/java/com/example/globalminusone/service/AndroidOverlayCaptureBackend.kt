package com.example.globalminusone.service

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.Settings
import com.example.globalminusone.screenassistant.AccessibilityScreenSnapshot
import com.example.globalminusone.screenassistant.BreenoLaunchOutcome
import com.example.globalminusone.screenassistant.BreenoTouchBridge
import com.example.globalminusone.screenassistant.ExtractedSubImageCandidate
import com.example.globalminusone.screenassistant.ScreenCaptureHub
import com.example.globalminusone.screenassistant.awaitScreenCaptureResult
import com.example.globalminusone.system.UserRootSession
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/** The original service Context is retained; platform state is resolved only at each call. */
internal class AndroidOverlayCaptureBackend(private val context: Context) :
    OverlayCaptureBackend<Bitmap, GlobalAccessibilityService> {
    override val sdkInt: Int get() = Build.VERSION.SDK_INT
    override val hasOverlayPermission: Boolean get() = Settings.canDrawOverlays(context)
    override val currentAccessibility: GlobalAccessibilityService? get() = GlobalAccessibilityService.instance
    override val hasActivePrivilegedSession: Boolean
        get() = UserRootSession.authorizedEpoch() != null || ScreenCaptureHub.shizukuAuthorized()
    override val supportsFastDeferredCapture: Boolean get() = true
    override fun snapshot(source: GlobalAccessibilityService): AccessibilityScreenSnapshot = source.captureAccessibilitySnapshot()
    override suspend fun captureSnapshot(source: GlobalAccessibilityService): AccessibilityScreenSnapshot =
        runBoundedBlockingWork(timeoutMs = 520L) { source.captureAccessibilitySnapshot() }
            ?: AccessibilityScreenSnapshot(emptyList(), emptyList())

    override suspend fun captureNativeFast(source: GlobalAccessibilityService): Pair<Bitmap?, Int> {
        return withTimeoutOrNull(2_000L) {
            awaitScreenCaptureResult<Pair<Bitmap?, Int>>(
                discard = { pair -> pair.first?.let { if (!it.isRecycled) it.recycle() } }
            ) { reply ->
                source.captureSilentScreenBitmap { bmp, err -> reply(bmp to err) }
            }
        } ?: (null to -1)
    }

    private fun resolveRealDisplaySize(): Pair<Int, Int> {
        val dm = context.resources.displayMetrics
        if (Build.VERSION.SDK_INT >= 30) {
            val bounds = (context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager)
                ?.currentWindowMetrics?.bounds
            if (bounds != null && bounds.width() > 0 && bounds.height() > 0) {
                return bounds.width() to bounds.height()
            }
        }
        return dm.widthPixels to dm.heightPixels
    }

    override suspend fun collectEnrichedSnapshot(
        source: GlobalAccessibilityService?,
        trackedPackage: String,
        extraObstructions: List<ExtractedSubImageCandidate>,
        config: OverlayCaptureConfig
    ): AccessibilityScreenSnapshot = coroutineScope {
        val (winW, winH) = resolveRealDisplaySize()
        val a11yDeferred = async {
            source?.let { captureSnapshot(it) }
                ?: AccessibilityScreenSnapshot(emptyList(), emptyList())
        }
        val uiTreeDeferred = async {
            ScreenCaptureHub.captureUiTreeSnapshot(
                context = context,
                screenW = winW,
                screenH = winH,
                allowRootAutoConnect = config.rootModeEnabled
            )
        }
        val rawA11y = a11yDeferred.await()
        val uiTimeout = if (rawA11y.mediaNodes.isNotEmpty() || rawA11y.textNodes.size >= 3) 320L else 1_400L
        val uiTree = withTimeoutOrNull(uiTimeout) { uiTreeDeferred.await() } ?: run {
            uiTreeDeferred.cancel()
            AccessibilityScreenSnapshot(emptyList(), emptyList())
        }
        val merged = ScreenCaptureHub.mergeSnapshots(
            primary = uiTree,
            uiDump = rawA11y,
            extraObstructions = extraObstructions
        )
        if (!ScreenCaptureHub.isSafeInspectablePackage(merged.foregroundPackage) &&
            ScreenCaptureHub.isSafeInspectablePackage(trackedPackage)
        ) merged.copy(foregroundPackage = trackedPackage) else merged
    }

    override suspend fun capturePrivileged(
        baseSnapshot: AccessibilityScreenSnapshot,
        extraObstructions: List<ExtractedSubImageCandidate>,
        allowRootPrompt: Boolean
    ): CapturedFrame<Bitmap>? = capturePrivileged(
        baseSnapshot = baseSnapshot,
        extraObstructions = extraObstructions,
        allowRootPrompt = allowRootPrompt,
        preferNativePixelsWithPrivilegedHierarchy = false
    )

    override suspend fun capturePrivileged(
        baseSnapshot: AccessibilityScreenSnapshot,
        extraObstructions: List<ExtractedSubImageCandidate>,
        allowRootPrompt: Boolean,
        preferNativePixelsWithPrivilegedHierarchy: Boolean
    ): CapturedFrame<Bitmap>? {
        val a11yService = GlobalAccessibilityService.instance
        if (preferNativePixelsWithPrivilegedHierarchy && a11yService != null && Build.VERSION.SDK_INT >= 30) {
            val fastOutcome = captureNativePixelsWithPrivilegedHierarchy(
                service = a11yService,
                baseSnapshot = baseSnapshot,
                extraObstructions = extraObstructions
            )
            if (fastOutcome != null) return fastOutcome
        }
        return ScreenCaptureHub.captureDisplay(
            context = context,
            baseSnapshot = baseSnapshot,
            extraObstructions = extraObstructions,
            allowRootPrompt = allowRootPrompt
        )?.let { CapturedFrame(it.bitmap, it.snapshot) }
    }

    private suspend fun captureNativePixelsWithPrivilegedHierarchy(
        service: GlobalAccessibilityService,
        baseSnapshot: AccessibilityScreenSnapshot,
        extraObstructions: List<ExtractedSubImageCandidate>
    ): CapturedFrame<Bitmap>? = coroutineScope {
        val (winW, winH) = resolveRealDisplaySize()
        val nativeDeferred = async {
            withTimeoutOrNull(2_000L) {
                awaitScreenCaptureResult<Pair<Bitmap?, Int>>(
                    discard = { pair -> pair.first?.let { if (!it.isRecycled) it.recycle() } }
                ) { reply ->
                    service.captureSilentScreenBitmap { bmp, err -> reply(bmp to err) }
                }
            }
        }
        val uiTreeDeferred = async {
            ScreenCaptureHub.captureUiTreeSnapshot(
                context = context,
                screenW = winW,
                screenH = winH
            )
        }
        var acquiredBitmap: Bitmap? = null
        try {
            val nativeRes = nativeDeferred.await()
            val bmp = nativeRes?.first
            if (bmp != null && !bmp.isRecycled) {
                acquiredBitmap = bmp
                val uiTreeSnap = withTimeoutOrNull(1_400L) { uiTreeDeferred.await() }
                val merged = if (uiTreeSnap != null) {
                    ScreenCaptureHub.mergeSnapshots(
                        primary = uiTreeSnap,
                        uiDump = baseSnapshot,
                        extraObstructions = extraObstructions
                    )
                } else {
                    ScreenCaptureHub.mergeSnapshots(
                        primary = baseSnapshot,
                        uiDump = AccessibilityScreenSnapshot(emptyList(), emptyList()),
                        extraObstructions = extraObstructions
                    )
                }
                val result = CapturedFrame(bmp, merged)
                acquiredBitmap = null
                return@coroutineScope result
            }
            uiTreeDeferred.cancel()
            null
        } catch (t: Throwable) {
            acquiredBitmap?.takeIf { !it.isRecycled }?.recycle()
            throw t
        }
    }
    override fun captureNative(source: GlobalAccessibilityService, reply: (Bitmap?, Int) -> Unit) =
        source.captureSilentScreenBitmap { bitmap, error -> reply(bitmap, error) }
    override fun isRecycled(frame: Bitmap): Boolean = frame.isRecycled
    override fun recycle(frame: Bitmap) = frame.recycle()
    override suspend fun launchBreeno(allowPrivilegedShell: Boolean): BreenoLaunchOutcome =
        BreenoTouchBridge.launch(context = context, allowPrivilegedShell = allowPrivilegedShell)
}
