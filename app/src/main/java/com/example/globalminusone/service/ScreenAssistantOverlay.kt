package com.example.globalminusone.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.ScreenAssistantConfig
import com.example.globalminusone.data.ScreenOcrEngine
import com.example.globalminusone.data.ScreenTranslateEngine
import com.example.globalminusone.screenassistant.AccessibilityScreenSnapshot
import com.example.globalminusone.screenassistant.ScreenAssistantAttachmentManager
import com.example.globalminusone.screenassistant.ScreenAssistantPolicy
import com.example.globalminusone.screenassistant.ScreenAssistantWorkSession
import com.example.globalminusone.screenassistant.ScreenCaptureHub
import com.example.globalminusone.screenassistant.ScreenImageExtractor
import com.example.globalminusone.screenassistant.ScreenImageScope
import com.example.globalminusone.screenassistant.ScreenOcrCoordinator
import com.example.globalminusone.screenassistant.ScreenTextInpainter
import com.example.globalminusone.screenassistant.ScreenTextSelectionPolicy
import com.example.globalminusone.screenassistant.ScreenTranslateEngineImpl
import com.example.globalminusone.screenassistant.SubImageResult
import com.example.globalminusone.service.screenassistant.ScreenAssistantBlur
import com.example.globalminusone.service.screenassistant.ScreenAssistantCanvasView
import com.example.globalminusone.service.screenassistant.ScreenAssistantHudController
import com.example.globalminusone.service.screenassistant.ScreenAssistantImageActions
import com.example.globalminusone.service.screenassistant.ScreenAssistantImageExport
import com.example.globalminusone.service.screenassistant.ScreenAssistantOperationWork
import com.example.globalminusone.service.screenassistant.ScreenAssistantPresentation
import com.example.globalminusone.service.screenassistant.ScreenAssistantPresentation.FocusState
import com.example.globalminusone.service.screenassistant.ScreenAssistantPresentation.Work
import com.example.globalminusone.service.screenassistant.ScreenAssistantViewportGeometry
import com.example.globalminusone.system.AppAndIntentLauncher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Full-screen unified Screen Assistant overlay:
 * - Parallel one-step blur + OCR + original-first sub-image extraction upon opening.
 * - Fixed one-handed bottom dock morphing across IDLE / TEXT_SELECTED / IMAGE_SELECTED / TRANSLATE.
 */
internal class ScreenAssistantOverlay(
    private val context: Context,
    private val windowManager: WindowManager,
    private val repository: MinusOneRepository,
    private val scope: CoroutineScope,
    private val onSwitchToRegionCrop: ((Bitmap, ScreenAssistantFrameSnapshot) -> Unit)? = null,
    private val onScrollUnderlying: ((ScreenAssistantMode, Float, Float, Float, Float) -> Unit)? = null,
    private val onPauseToBrowseUnderlying: ((ScreenAssistantMode) -> Unit)? = null
) {
    private var rootContainer: FrameLayout? = null
    private var backCallback: OnBackInvokedCallback? = null
    private var sourceBitmap: Bitmap? = null
    private var blurredBackdropBitmap: Bitmap? = null
    private var inpaintedBitmap: Bitmap? = null
    private var extractedSubImages: List<SubImageResult> = emptyList()
    private var activeSession: ScreenAssistantWorkSession? = null
    private var activeSnapshotDeferred: Deferred<AccessibilityScreenSnapshot>? = null
    private var latestScreenSnapshot = AccessibilityScreenSnapshot(emptyList(), emptyList())

    val isShowing: Boolean get() = rootContainer != null

    fun show(
        bitmap: Bitmap,
        snapshot: AccessibilityScreenSnapshot = AccessibilityScreenSnapshot(emptyList(), emptyList()),
        initialMode: ScreenAssistantMode = ScreenAssistantMode.TEXT_SELECT,
        isCroppedRegion: Boolean = false,
        snapshotDeferred: Deferred<AccessibilityScreenSnapshot>? = null,
        snapshotCrop: Rect? = null
    ): Boolean {
        dismiss()
        if (bitmap.isRecycled) return false
        return try {
            attachCapture(bitmap, snapshot, initialMode, isCroppedRegion, snapshotDeferred, snapshotCrop)
            isShowing
        } catch (failure: Exception) {
            Log.e(TAG, "Failed to open ScreenAssistantOverlay", failure)
            dismiss()
            false
        }
    }

    private fun attachCapture(
        bitmap: Bitmap,
        initialSnapshot: AccessibilityScreenSnapshot,
        initialMode: ScreenAssistantMode,
        isCroppedRegion: Boolean,
        snapshotDeferred: Deferred<AccessibilityScreenSnapshot>?,
        snapshotCrop: Rect?
    ) {
        val snapshot = ScreenCaptureHub.projectSnapshotToCrop(initialSnapshot, snapshotCrop)
        latestScreenSnapshot = snapshot
        sourceBitmap = bitmap
        activeSnapshotDeferred = snapshotDeferred
        inpaintedBitmap = null
        val density = context.resources.displayMetrics.density
        fun dp(v: Float) = (v * density).roundToInt()

        val presentation = ScreenAssistantPresentation(initialMode)
        var reportFailure: (String, Throwable) -> Unit = { _, _ -> }
        val session = ScreenAssistantWorkSession(scope) { label, failure ->
            reportFailure(label, failure)
        }
        activeSession = session
        fun isCurrent() = activeSession === session && session.isActive
        if (!isCurrent()) {
            dismiss()
            return
        }

        val opWork = ScreenAssistantOperationWork(session, presentation) { l, f ->
            reportFailure(l, f)
        }
        val geometry = ScreenAssistantViewportGeometry(bitmap.width, bitmap.height)
        val safeInsets = Rect(0, dp(28f), 0, dp(24f))
        var configOverride: ScreenAssistantConfig? = null
        var translationJob: Job? = null
        var inpaintJob: Job? = null
        var recognitionJob: Job? = null
        var extractionJob: Job? = null
        var cachedSnapMidPx: IntArray? = null
        var cachedSnapMidW = 0
        var cachedSnapMidH = 0
        var uiReady = false
        var blurStarted = false
        fun cfg() = configOverride ?: repository.state.value.advancedConfig.screenAssistantConfig

        lateinit var hud: ScreenAssistantHudController
        lateinit var canvasView: ScreenAssistantCanvasView
        var triggerOcr: (() -> Unit)? = null
        var triggerSubImages: ((Boolean) -> Unit)? = null

        val imageActions = ScreenAssistantImageActions(
            session,
            ScreenAssistantImageExport(
                context,
                { msg -> if (uiReady) hud.showBanner(msg) },
                ::dismiss
            )
        )

        fun computeFocusAnchorRect(): RectF? = when (presentation.focusState) {
            FocusState.TEXT_SELECTED -> {
                var union: RectF? = null
                for ((blockId, span) in presentation.selectionSpans) {
                    val b = presentation.blocks.firstOrNull { it.id == blockId } ?: continue
                    val r = geometry.spanViewRectF(b, span)
                    if (union == null) union = RectF(r) else union.union(r)
                }
                union
            }
            FocusState.IMAGE_SELECTED -> {
                val c = extractedSubImages.getOrNull(presentation.activeSubImageIndex)?.candidate
                if (c != null && c.leftPx >= 0 && c.rightPx > c.leftPx && c.bottomPx > c.topPx) {
                    geometry.bitmapRectToViewRectF(c.leftPx, c.topPx, c.rightPx, c.bottomPx)
                } else null
            }
            else -> null
        }

        fun refreshAll() {
            if (!isCurrent() || !uiReady) return
            hud.refresh(computeFocusAnchorRect())
            canvasView.invalidate()
        }

        fun applySafeWindowInsets(insets: WindowInsets?) {
            if (Build.VERSION.SDK_INT >= 30 && insets != null) {
                val ins = insets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() or
                        WindowInsets.Type.displayCutout() or
                        WindowInsets.Type.mandatorySystemGestures()
                )
                safeInsets.set(ins.left, max(dp(24f), ins.top), ins.right, max(dp(24f), ins.bottom))
            } else if (insets != null) {
                @Suppress("DEPRECATION")
                safeInsets.set(
                    insets.systemWindowInsetLeft,
                    max(dp(24f), insets.systemWindowInsetTop),
                    insets.systemWindowInsetRight,
                    max(dp(24f), insets.systemWindowInsetBottom)
                )
            }
        }

        fun ensureBlur() {
            if (blurStarted || !isCurrent() || bitmap.isRecycled) return
            blurStarted = true
            session.launch("景深虚化") {
                val blurred = withContext(Dispatchers.Default) {
                    ScreenAssistantBlur.createSpotlightBackdrop(bitmap)
                }
                if (isCurrent()) {
                    blurredBackdropBitmap = blurred
                    if (uiReady && presentation.focusState != FocusState.IDLE) canvasView.invalidate()
                } else if (blurred != null && blurred !== bitmap && !blurred.isRecycled) {
                    blurred.recycle()
                }
            }
        }

        fun ensureInpaint() {
            if (!isCurrent()) return
            val active = presentation.translateModeBlocks()
            if (active.none { ScreenTextSelectionPolicy.shouldRenderTranslatedOverlay(it.text, it.translatedText) }) return
            inpaintJob?.cancel()
            inpaintJob = opWork.launch(
                "背景修复",
                presentation.begin(Work.INPAINT),
                produce = {
                    withContext(Dispatchers.Default) {
                        ScreenTextInpainter.createInpaintedScreenBitmap(bitmap, active)
                    }
                }
            ) {
                inpaintedBitmap = it
                canvasView.invalidate()
            }
        }

        fun runTranslation(force: Boolean = false) {
            if (!isCurrent() || (presentation.translating && !force)) return
            presentation.switchMode(ScreenAssistantMode.TRANSLATE_INPLACE)
            if (!presentation.ocrDone) {
                presentation.translateStatusText = "正在等待文字识别完成…"
                refreshAll()
                return
            }
            if (presentation.blocks.isEmpty()) {
                presentation.translateStatusText = presentation.missingTranslationInputStatus()
                refreshAll()
                return
            }
            if (!force &&
                presentation.translatedBlocks.isNotEmpty() &&
                presentation.translatedBlocks.all { !it.translatedText.isNullOrBlank() }
            ) {
                presentation.translateStatusText = "原位翻译完成 · 点文字块对照原文"
                if (inpaintedBitmap == null) ensureInpaint()
                refreshAll()
                return
            }
            val op = presentation.begin(Work.TRANSLATION)
            val curCfg = cfg()
            inpaintJob?.cancel()
            inpaintedBitmap = null
            presentation.translating = true
            presentation.translateStatusText = "正在使用 ${curCfg.translateEngine.label} 翻译…"
            refreshAll()
            translationJob?.cancel()
            val clustered = ScreenTextSelectionPolicy.clusterParagraphBlocks(presentation.blocks)
            translationJob = opWork.launch(
                "翻译",
                op,
                produce = { ScreenTranslateEngineImpl.translateBlocks(context, clustered, curCfg) }
            ) { res ->
                presentation.translating = false
                res.onSuccess {
                    presentation.translatedBlocks = it
                    presentation.translateStatusText = "${curCfg.translateEngine.label} 完成 · 按住看原文"
                    ensureInpaint()
                }.onFailure { err ->
                    val m = err.localizedMessage?.trim()?.take(38).orEmpty()
                    presentation.translateStatusText = if (m.isNotBlank()) "翻译未完成（$m）" else "翻译暂不可用，可切换引擎"
                }
                refreshAll()
            }
        }

        fun handleBack(): Boolean {
            if (!isCurrent()) return false
            when {
                hud.dismissMoreMenu() -> {
                    refreshAll()
                    return true
                }
                presentation.showPlainTextLayer -> presentation.showPlainTextLayer = false
                presentation.showTokenTuner -> presentation.showTokenTuner = false
                presentation.previewingActiveSubImage -> presentation.previewingActiveSubImage = false
                presentation.adjustingSubImageCrop -> presentation.adjustingSubImageCrop = false
                presentation.focusState == FocusState.TEXT_SELECTED ||
                    presentation.focusState == FocusState.IMAGE_SELECTED -> presentation.clearFocus()
                presentation.showSubImageGallery -> presentation.showSubImageGallery = false
                presentation.focusState == FocusState.TRANSLATE && !isCroppedRegion ->
                    presentation.switchMode(ScreenAssistantMode.TEXT_SELECT)
                else -> {
                    dismiss()
                    return true
                }
            }
            refreshAll()
            return true
        }

        hud = ScreenAssistantHudController(
            context = context,
            bitmap = bitmap,
            isCroppedRegion = isCroppedRegion,
            canCropRegion = onSwitchToRegionCrop != null,
            presentation = presentation,
            imageActions = imageActions,
            isCurrent = ::isCurrent,
            effectiveConfig = ::cfg,
            extractedSubImagesProvider = { extractedSubImages },
            onSwitchMode = {
                presentation.switchMode(it)
                refreshAll()
            },
            onRunTranslate = { runTranslation(false) },
            onRetranslate = { runTranslation(true) },
            onCycleTranslateEngine = {
                val all = ScreenTranslateEngine.values()
                all[(all.indexOf(cfg().translateEngine) + 1) % all.size].also {
                    configOverride = cfg().copy(translateEngine = it)
                }
            },
            onSaveOfflineLexicon = {
                val pairs = presentation.translateModeBlocks().mapNotNull { b ->
                    b.translatedText?.takeIf { it.isNotBlank() && it != b.text }?.let { b.text to it }
                }
                session.launch("保存词库") {
                    val saved = ScreenAssistantAttachmentManager.cacheTranslationPairsOffline(context, pairs)
                    if (!isCurrent()) return@launch
                    if (saved != null) {
                        repository.updateAdvancedConfig {
                            it.copy(
                                screenAssistantConfig = it.screenAssistantConfig.copy(
                                    activeLexiconId = saved.id,
                                    activeLexiconName = saved.name
                                )
                            )
                        }
                        hud.showBanner("✓ 已缓存 ${pairs.size} 条到离线词库")
                    } else {
                        hud.showBanner("暂无新译文可缓存")
                    }
                }
            },
            onRequestCrop = {
                val copy = copyForTransfer(bitmap) ?: return@ScreenAssistantHudController
                val opener = onSwitchToRegionCrop ?: run {
                    copy.recycle()
                    return@ScreenAssistantHudController
                }
                val frameSnap = ScreenAssistantFrameSnapshot(initialSnapshot, snapshotDeferred, snapshotCrop)
                activeSnapshotDeferred = null
                dismiss()
                try {
                    opener(copy, frameSnap)
                } catch (f: Throwable) {
                    frameSnap.enriched?.cancel()
                    copy.recycle()
                    Log.w(TAG, "Could not open crop tool", f)
                    Toast.makeText(context, "无法打开框选，请重试", Toast.LENGTH_SHORT).show()
                }
            },
            onRerunOcr = { triggerOcr?.invoke() },
            onRerunSubImages = {
                triggerSubImages?.invoke(true)
            },
            onOpenOcrSettings = {
                val view = rootContainer
                if (AppAndIntentLauncher.launchMainSettings(context, "SCREEN_ASSISTANT")) {
                    if (view != null) {
                        view.post { if (isCurrent()) dismiss() }
                    } else if (isCurrent()) {
                        dismiss()
                    }
                }
            },
            onCopyText = { text -> toastClipboardResult(context, text, hud::showBanner) },
            onShareText = { text -> shareTextOut(context, text, hud::showBanner, ::dismiss) },
            onSearchQuery = { q ->
                val url = repository.state.value.appearanceConfig.searchEngineUrl
                dismiss()
                AppAndIntentLauncher.launchSearch(context, url, q)
            },
            onRecognizeSubImage = { subBmp ->
                val copy = copyForTransfer(subBmp)
                if (copy != null && !show(copy, AccessibilityScreenSnapshot(emptyList(), emptyList()), ScreenAssistantMode.TEXT_SELECT, true)) {
                    if (!copy.isRecycled) copy.recycle()
                    Toast.makeText(context, "未能打开识屏窗口，请重试", Toast.LENGTH_SHORT).show()
                }
            },
            onPauseToBrowse = if (!isCroppedRegion && onPauseToBrowseUnderlying != null) {
                { onPauseToBrowseUnderlying.invoke(presentation.mode) }
            } else null,
            onBackAction = { handleBack() },
            onRefreshAll = ::refreshAll,
            onInvalidateCanvas = { canvasView.invalidate() },
            onFabPositionSaved = { fx, fy ->
                configOverride = cfg().copy(fabXFraction = fx, fabYFraction = fy)
                session.launch("保存悬浮球位置") {
                    repository.updateAdvancedConfig {
                        it.copy(
                            screenAssistantConfig = it.screenAssistantConfig.copy(
                                fabXFraction = fx,
                                fabYFraction = fy
                            )
                        )
                    }
                }
            },
            onDismiss = ::dismiss
        )

        canvasView = ScreenAssistantCanvasView(
            context = context,
            bitmap = bitmap,
            isCroppedRegion = isCroppedRegion,
            presentation = presentation,
            geometry = geometry,
            isCurrent = ::isCurrent,
            inpaintedBitmapProvider = { inpaintedBitmap },
            blurredBitmapProvider = { blurredBackdropBitmap },
            extractedSubImagesProvider = { extractedSubImages },
            safeInsetsProvider = { Rect(safeInsets) },
            topBarBottomProvider = { safeInsets.top + dp(52f) },
            onHideFloatingBar = {},
            onStateUpdated = {
                hud.rebuildTokenChips()
                refreshAll()
            },
            onSubImageAdjusted = { idx, nl, nt, nr, nb ->
                if (isCurrent() && idx in extractedSubImages.indices && !bitmap.isRecycled) {
                    val cl = nl.coerceIn(0, bitmap.width - 2)
                    val ct = nt.coerceIn(0, bitmap.height - 2)
                    val cr = nr.coerceIn(cl + 2, bitmap.width)
                    val cb = nb.coerceIn(ct + 2, bitmap.height)
                    runCatching { Bitmap.createBitmap(bitmap, cl, ct, cr - cl, cb - ct) }.getOrNull()?.let { crop ->
                        val updated = extractedSubImages[idx].copy(
                            candidate = extractedSubImages[idx].candidate.copy(
                                leftPx = cl,
                                topPx = ct,
                                rightPx = cr,
                                bottomPx = cb,
                                sourceTag = "画面提图 · 已按框裁切"
                            ),
                            bitmap = crop,
                            rawEncodedBytes = null,
                            rawExtension = "png",
                            rawMimeType = "image/png",
                            isUnderlyingOriginal = false
                        )
                        extractedSubImages = extractedSubImages.toMutableList().apply { set(idx, updated) }
                        presentation.selectSubImage(idx)
                        presentation.adjustingSubImageCrop = true
                        refreshAll()
                        hud.showBanner("✓ 已按选框重新裁切 (${cr - cl}×${cb - ct})")
                    }
                }
            },
            onScrollUnderlying = if (!isCroppedRegion && onScrollUnderlying != null) {
                { sx, sy, ex, ey -> onScrollUnderlying.invoke(presentation.mode, sx, sy, ex, ey) }
            } else null,
            onCanvasTouchActiveChanged = { active -> hud.setCanvasDragging(active) },
            onSnapSubImageAtRegion = { points ->
                if (isCurrent() && !bitmap.isRecycled && points.isNotEmpty() && bitmap.width >= 16 && bitmap.height >= 16) {
                    session.launch("圈选吸附") {
                        val bw = bitmap.width
                        val bh = bitmap.height
                        if (cachedSnapMidPx == null) {
                            val mw = min(360, bw)
                            val mh = ((bh.toFloat() / bw) * mw).toInt().coerceIn(36, 960)
                            val px = withContext(Dispatchers.Default) {
                                if (bitmap.isRecycled) return@withContext null
                                val scaled = if (bw == mw && bh == mh) bitmap else Bitmap.createScaledBitmap(bitmap, mw, mh, true)
                                try {
                                    IntArray(mw * mh).also {
                                        if (!scaled.isRecycled) scaled.getPixels(it, 0, mw, 0, 0, mw, mh)
                                    }
                                } finally {
                                    if (scaled !== bitmap && !scaled.isRecycled) scaled.recycle()
                                }
                            }
                            if (px != null) {
                                cachedSnapMidPx = px
                                cachedSnapMidW = mw
                                cachedSnapMidH = mh
                            }
                        }
                        if (!isCurrent() || bitmap.isRecycled) return@launch
                        val snapped = withContext(Dispatchers.Default) {
                            ScreenImageExtractor.snapObject(
                                w = bw,
                                h = bh,
                                points = points,
                                mediaNodes = latestScreenSnapshot.mediaNodes,
                                textNodes = presentation.blocks.ifEmpty { latestScreenSnapshot.textNodes },
                                screenMid = cachedSnapMidPx,
                                midW = cachedSnapMidW,
                                midH = cachedSnapMidH
                            )
                        }
                        if (!isCurrent() || bitmap.isRecycled) return@launch
                        if (snapped == null) {
                            if (points.size >= 3) hud.showBanner("未检测到可吸附目标")
                            refreshAll()
                        } else {
                            val hitIdx = extractedSubImages.indexOfFirst {
                                it.candidate.leftPx >= 0 && ScreenAssistantPolicy.computeIoU(it.candidate, snapped) >= 0.72f
                            }
                            if (hitIdx >= 0) {
                                presentation.selectSubImage(hitIdx)
                                refreshAll()
                            } else {
                                val cl = snapped.leftPx.coerceIn(0, bw - 2)
                                val ct = snapped.topPx.coerceIn(0, bh - 2)
                                val cr = snapped.rightPx.coerceIn(cl + 2, bw)
                                val cb = snapped.bottomPx.coerceIn(ct + 2, bh)
                                val cbmp = runCatching { Bitmap.createBitmap(bitmap, cl, ct, cr - cl, cb - ct) }.getOrNull()
                                if (cbmp != null) {
                                    val updated = extractedSubImages + SubImageResult(
                                        snapped.copy(leftPx = cl, topPx = ct, rightPx = cr, bottomPx = cb),
                                        cbmp
                                    )
                                    extractedSubImages = updated
                                    presentation.selectSubImage(updated.lastIndex)
                                    refreshAll()
                                    hud.showBanner("✓ 已识别图片 (${cr - cl}×${cb - ct})")
                                }
                            }
                        }
                    }
                }
            }
        )

        if (Build.VERSION.SDK_INT >= 30) {
            runCatching { applySafeWindowInsets(windowManager.currentWindowMetrics.windowInsets) }
        }

        val container = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (!isCurrent()) return false
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) handleBack()
                    return true
                }
                return dispatchWithCallFallbackGuard(Build.VERSION.SDK_INT, event.keyCode, event.action) {
                    super.dispatchKeyEvent(event)
                }
            }
        }.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            val match = FrameLayout.LayoutParams.MATCH_PARENT
            val wrap = FrameLayout.LayoutParams.WRAP_CONTENT
            addView(canvasView, FrameLayout.LayoutParams(match, match))
            addView(hud.plainTextCard, FrameLayout.LayoutParams(match, match))
            addView(hud.statusPill, FrameLayout.LayoutParams(wrap, wrap))
            addView(hud.feedbackBanner, FrameLayout.LayoutParams(wrap, wrap))
            addView(hud.contextualCapsuleHost, FrameLayout.LayoutParams(wrap, wrap))
            addView(hud.bottomDockHost, FrameLayout.LayoutParams(match, wrap))
            addView(hud.fabMenuCard, FrameLayout.LayoutParams(wrap, wrap))
            addView(hud.fabButton, FrameLayout.LayoutParams(dp(48f), dp(48f)))
            setOnApplyWindowInsetsListener { _, insets ->
                if (!isCurrent()) return@setOnApplyWindowInsetsListener insets
                applySafeWindowInsets(insets)
                hud.applySafeInsets(safeInsets)
                refreshAll()
                insets
            }
        }
        hud.applySafeInsets(safeInsets)
        uiReady = true

        val dm = context.resources.displayMetrics
        val winW = if (Build.VERSION.SDK_INT >= 30) windowManager.currentWindowMetrics.bounds.width() else dm.widthPixels
        val winH = if (Build.VERSION.SDK_INT >= 30) windowManager.currentWindowMetrics.bounds.height() else dm.heightPixels
        val params = WindowManager.LayoutParams(
            winW,
            winH,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                fitInsetsTypes = 0
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        reportFailure = { label, failure ->
            if (isCurrent()) {
                Log.w(TAG, "$label failed", failure)
                val detail = failure.message?.trim()?.take(48)?.ifBlank { null }
                when (label) {
                    "识别" -> {
                        presentation.ocrDone = true
                        presentation.ocrStatusText = detail?.let { "识别未完成：$it" } ?: "识别未完成，可点设置检查引擎"
                        if (!presentation.subImagesDone && extractionJob == null) {
                            triggerSubImages?.invoke(false)
                        }
                    }
                    "翻译" -> {
                        presentation.translating = false
                        presentation.translateStatusText = detail?.let { "翻译未完成：$it" } ?: "翻译暂不可用，可切换引擎"
                    }
                    "提图" -> {
                        presentation.subImagesDone = true
                        presentation.subImageFailure = "提图未完成"
                        if (!presentation.ocrDone && recognitionJob == null) {
                            triggerOcr?.invoke()
                        }
                    }
                    else -> hud.showBanner(detail?.let { "${label}失败：$it" } ?: "${label}失败，请重试")
                }
                refreshAll()
            }
        }
        if (!isCurrent()) {
            dismiss()
            return
        }
        windowManager.addView(container, params)
        rootContainer = container
        container.requestApplyInsets()
        container.requestFocus()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                val cb = OnBackInvokedCallback { handleBack() }
                container.findOnBackInvokedDispatcher()?.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb)
                backCallback = cb
            }
        }
        refreshAll()

        var latestSnapshot = snapshot
        suspend fun awaitEnrichedSnapshot(maxWaitMs: Long): AccessibilityScreenSnapshot {
            val d = snapshotDeferred ?: return latestSnapshot
            val awaited = try {
                withTimeoutOrNull(maxWaitMs) { d.await() }
            } catch (_: CancellationException) {
                currentCoroutineContext().ensureActive()
                null
            }
            if (awaited != null) {
                latestSnapshot = ScreenCaptureHub.projectSnapshotToCrop(awaited, snapshotCrop)
                latestScreenSnapshot = latestSnapshot
            }
            return latestSnapshot
        }

        var pendingRerunBanner = false
        triggerSubImages = extract@{ retry ->
            if (!isCurrent()) return@extract
            if (!cfg().extractSubImagesEnabled) {
                presentation.subImagesDone = true
                refreshAll()
                return@extract
            }
            if (!retry && (extractionJob?.isActive == true || presentation.subImagesDone)) return@extract
            if (retry) {
                extractionJob?.cancel()
                extractionJob = null
                pendingRerunBanner = true
                hud.showBanner("正在重新识别与提图…", 1800L)
            }
            val op = presentation.begin(Work.EXTRACTION)
            val allowLsposed = cfg().lsposedViewCaptureEnabled
            refreshAll()
            extractionJob = opWork.launch(
                "提图",
                op,
                produce = {
                    ScreenImageExtractor.extractSubImages(
                        bitmap = bitmap,
                        snapshot = snapshot,
                        context = context,
                        allowLsposedViewCapture = allowLsposed,
                        scope = if (isCroppedRegion) ScreenImageScope.SELECTED_REGION else ScreenImageScope.FULL_SCREEN,
                        snapshotDeferred = snapshotDeferred,
                        snapshotCrop = snapshotCrop
                    ) { prelim ->
                        withContext(Dispatchers.Main) {
                            if (isCurrent() &&
                                presentation.accepts(op) &&
                                !presentation.subImagesDone &&
                                extractedSubImages.isEmpty() &&
                                prelim.isNotEmpty()
                            ) {
                                extractedSubImages = prelim
                                refreshAll()
                            }
                        }
                    }
                }
            ) { result ->
                val selId = extractedSubImages.getOrNull(presentation.activeSubImageIndex)?.candidate?.id
                val subs = result.images
                val origCount = subs.count { it.isUnderlyingOriginal }
                extractedSubImages = subs
                presentation.originalSourceNote = result.originalSourceNote
                presentation.subImagesDone = true
                val hitIdx = subs.indexOfFirst { it.candidate.id == selId }
                val nextIdx = when {
                    hitIdx >= 0 -> hitIdx
                    isCroppedRegion && initialMode == ScreenAssistantMode.SUB_IMAGES && subs.size == 1 -> 0
                    else -> presentation.activeSubImageIndex
                }
                if (nextIdx >= 0 && presentation.selectionSpans.isEmpty()) presentation.selectSubImage(nextIdx)
                refreshAll()
                if (pendingRerunBanner) {
                    pendingRerunBanner = false
                    hud.showBanner("提图完成：${subs.size}张（${result.originalSourceNote}）", 3200L)
                } else if (subs.isNotEmpty() || initialMode == ScreenAssistantMode.SUB_IMAGES) {
                    val summary = if (origCount > 0) {
                        "提图完成：单层无角标 ${origCount} 张 / 共 ${subs.size} 张"
                    } else {
                        "已提图 ${subs.size} 张（${result.originalSourceNote}）"
                    }
                    hud.showBanner(summary, 2600L)
                }
                if (!presentation.ocrDone && recognitionJob == null) {
                    triggerOcr?.invoke()
                }
            }
        }

        val startOcr: () -> Unit = ocr@{
            if (!isCurrent()) return@ocr
            val op = presentation.begin(Work.RECOGNITION)
            recognitionJob?.cancel()
            translationJob?.cancel()
            inpaintJob?.cancel()
            inpaintedBitmap = null
            if (latestSnapshot.textNodes.isNotEmpty() && presentation.blocks.isEmpty()) {
                presentation.acceptBlocks(ScreenTextSelectionPolicy.inReadingOrder(latestSnapshot.textNodes))
                presentation.ocrStatusText = "正在补全画面文字与图片…"
                hud.rebuildTokenChips()
                canvasView.invalidate()
            }
            refreshAll()
            val curCfg = cfg()
            val allowRoot = repository.state.value.advancedConfig.rootModeEnabled || curCfg.usePrivilegedCapture
            recognitionJob = opWork.launch(
                "识别",
                op,
                produce = {
                    val snap = awaitEnrichedSnapshot(if (curCfg.ocrEngine == ScreenOcrEngine.ACCESSIBILITY_TREE) 1200L else 320L)
                    if (snap.textNodes.isNotEmpty() && curCfg.ocrEngine != ScreenOcrEngine.ACCESSIBILITY_TREE) {
                        val quick = ScreenTextSelectionPolicy.inReadingOrder(snap.textNodes)
                        withContext(Dispatchers.Main) {
                            if (isCurrent() && presentation.accepts(op) && !presentation.ocrDone) {
                                presentation.acceptBlocks(quick)
                                hud.rebuildTokenChips()
                                canvasView.invalidate()
                                refreshAll()
                            }
                        }
                    }
                    ScreenOcrCoordinator.recognizeTextBlocks(context, bitmap, snap, curCfg, allowRoot) {
                        if (snapshotDeferred?.isCompleted == true) awaitEnrichedSnapshot(150L) else latestSnapshot
                    }
                }
            ) { res ->
                presentation.acceptBlocks(res.blocks)
                presentation.lastOcrNote = res.note
                presentation.lastOcrEngineUsed = res.engineUsed
                presentation.ocrDone = true
                presentation.ocrStatusText = res.note
                hud.rebuildTokenChips()
                refreshAll()
                if (presentation.mode == ScreenAssistantMode.TRANSLATE_INPLACE) runTranslation(false)
                if (!presentation.subImagesDone && extractionJob == null) {
                    triggerSubImages?.invoke(false)
                }
            }
        }
        triggerOcr = startOcr

        ensureBlur()
        if (initialMode == ScreenAssistantMode.SUB_IMAGES) {
            triggerSubImages(false)
        } else {
            startOcr()
            triggerSubImages(false)
        }
    }

    private fun copyForTransfer(bitmap: Bitmap): Bitmap? = try {
        bitmap.copy(Bitmap.Config.ARGB_8888, false).also {
            if (it == null) Toast.makeText(context, "内存不足，无法处理图片", Toast.LENGTH_SHORT).show()
        }
    } catch (f: Throwable) {
        Log.w(TAG, "Could not copy screenshot", f)
        Toast.makeText(context, "无法处理图片，请重新截取", Toast.LENGTH_SHORT).show()
        null
    }

    fun dismiss() {
        activeSnapshotDeferred?.cancel()
        activeSnapshotDeferred = null
        activeSession?.close()
        activeSession = null
        val view = rootContainer
        rootContainer = null
        if (view != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                backCallback?.let { runCatching { view.findOnBackInvokedDispatcher()?.unregisterOnBackInvokedCallback(it) } }
            }
            backCallback = null
            runCatching { windowManager.removeView(view) }
        }
        extractedSubImages = emptyList()
        blurredBackdropBitmap = null
        inpaintedBitmap = null
        sourceBitmap = null
        latestScreenSnapshot = AccessibilityScreenSnapshot(emptyList(), emptyList())
    }

    companion object {
        private const val TAG = "ScreenAssistantOverlay"

        private fun toastClipboardResult(context: Context, text: String, onFeedback: (String) -> Unit) {
            runCatching {
                val cm = checkNotNull(context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager) {
                    "剪贴板服务不可用"
                }
                cm.setPrimaryClip(ClipData.newPlainText("ScreenAssistantText", text))
                "✓ 已复制 ${text.length} 字".also {
                    onFeedback(it)
                    Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                }
            }.onFailure { err ->
                "复制失败：${err.localizedMessage ?: "无权限"}".also {
                    onFeedback(it)
                    Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                }
            }
        }

        private fun shareTextOut(context: Context, text: String, onFeedback: (String) -> Unit, onDone: () -> Unit) {
            if (text.isBlank()) {
                onFeedback("暂无可分享文字")
                return
            }
            runCatching {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                context.startActivity(
                    Intent.createChooser(sendIntent, "分享识别文字").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onSuccess {
                onDone()
            }.onFailure { err ->
                "无法打开系统分享：${err.localizedMessage ?: "未知错误"}".also {
                    onFeedback(it)
                    Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
