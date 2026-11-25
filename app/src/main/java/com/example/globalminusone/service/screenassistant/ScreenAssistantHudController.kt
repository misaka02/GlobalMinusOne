package com.example.globalminusone.service.screenassistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.example.globalminusone.data.ScreenAssistantConfig
import com.example.globalminusone.data.ScreenTranslateEngine
import com.example.globalminusone.screenassistant.ScreenAssistantOverlayUiPolicy
import com.example.globalminusone.screenassistant.ScreenTextSelectionPolicy
import com.example.globalminusone.screenassistant.SubImageResult
import com.example.globalminusone.service.ScreenAssistantMode
import com.example.globalminusone.service.screenassistant.ScreenAssistantImageActions.Action
import com.example.globalminusone.service.screenassistant.ScreenAssistantPresentation.FocusState
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Clean floating HUD controller for GlobalMinusOne Screen Assistant:
 * - Compact centered floating glass capsule dock in IDLE / TRANSLATE;
 * - Contextual near-selection floating action capsule in TEXT_SELECTED / IMAGE_SELECTED;
 * - Compact 38dp top-right circular Close / Back button;
 * - Pure anti-aliased vector Canvas icons (zero raw Unicode glyph clutter);
 * - Bottom-anchored 55%-height plain-text sheet preserving upper screen visibility.
 */
internal class ScreenAssistantHudController(
    private val context: Context,
    private val bitmap: Bitmap,
    private val isCroppedRegion: Boolean,
    private val canCropRegion: Boolean,
    private val presentation: ScreenAssistantPresentation,
    private val imageActions: ScreenAssistantImageActions<ScreenAssistantImage, Uri>,
    private val isCurrent: () -> Boolean,
    private val effectiveConfig: () -> ScreenAssistantConfig,
    private val extractedSubImagesProvider: () -> List<SubImageResult>,
    private val onSwitchMode: (ScreenAssistantMode) -> Unit,
    private val onRunTranslate: () -> Unit,
    private val onRetranslate: () -> Unit,
    private val onCycleTranslateEngine: () -> ScreenTranslateEngine,
    private val onSaveOfflineLexicon: () -> Unit,
    private val onRequestCrop: () -> Unit,
    private val onRerunOcr: () -> Unit,
    private val onRerunSubImages: () -> Unit,
    private val onOpenOcrSettings: () -> Unit,
    private val onCopyText: (String) -> Unit,
    private val onShareText: (String) -> Unit,
    private val onSearchQuery: (String) -> Unit,
    private val onRecognizeSubImage: (Bitmap) -> Unit,
    private val onPauseToBrowse: (() -> Unit)?,
    private val onBackAction: () -> Unit,
    private val onRefreshAll: () -> Unit,
    private val onInvalidateCanvas: () -> Unit,
    private val onFabPositionSaved: (Float, Float) -> Unit = { _, _ -> },
    private val onDismiss: () -> Unit
) {
    private enum class HudIcon {
        CLOSE,
        BACK,
        IMAGE,
        TEXT,
        TRANSLATE,
        CROP,
        MORE,
        COPY,
        SELECT_ALL,
        TOKENS,
        SHARE,
        SEARCH,
        SAVE,
        TUNE,
        EYE,
        SWAP,
        BOOKMARK,
        REFRESH
    }

    /** Crisp 24x24 viewport vector icons drawn anti-aliased directly on Canvas. */
    private class HudVectorDrawable(
        private val icon: HudIcon,
        private val color: Int,
        private val sizePx: Int,
        private val strokeWidthPx: Float
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = this@HudVectorDrawable.color
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = this@HudVectorDrawable.color
            style = Paint.Style.FILL
        }
        private val path = Path()
        private val rect = RectF()

        override fun getIntrinsicWidth(): Int = sizePx
        override fun getIntrinsicHeight(): Int = sizePx

        override fun draw(canvas: Canvas) {
            val b = bounds
            val side = minOf(b.width(), b.height()).toFloat().coerceAtLeast(1f)
            val s = side / 24f
            val tx = b.left + (b.width() - side) * 0.5f
            val ty = b.top + (b.height() - side) * 0.5f
            paint.strokeWidth = strokeWidthPx / s

            canvas.save()
            canvas.translate(tx, ty)
            canvas.scale(s, s)
            path.reset()

            when (icon) {
                HudIcon.CLOSE -> {
                    canvas.drawLine(6.5f, 6.5f, 17.5f, 17.5f, paint)
                    canvas.drawLine(17.5f, 6.5f, 6.5f, 17.5f, paint)
                }
                HudIcon.BACK -> {
                    path.moveTo(15f, 5.5f)
                    path.lineTo(8.5f, 12f)
                    path.lineTo(15f, 18.5f)
                    canvas.drawPath(path, paint)
                }
                HudIcon.IMAGE -> {
                    rect.set(3.5f, 4.5f, 20.5f, 19.5f)
                    canvas.drawRoundRect(rect, 3f, 3f, paint)
                    canvas.drawCircle(8.7f, 9.2f, 1.4f, fillPaint)
                    path.moveTo(4.2f, 17.5f)
                    path.lineTo(9.5f, 12.6f)
                    path.lineTo(13.2f, 15.9f)
                    path.lineTo(16.2f, 13.2f)
                    path.lineTo(20f, 17.2f)
                    canvas.drawPath(path, paint)
                }
                HudIcon.TEXT -> {
                    canvas.drawLine(4.5f, 6.5f, 19.5f, 6.5f, paint)
                    canvas.drawLine(4.5f, 12f, 19.5f, 12f, paint)
                    canvas.drawLine(4.5f, 17.5f, 14f, 17.5f, paint)
                }
                HudIcon.TRANSLATE -> {
                    canvas.drawLine(4f, 6.5f, 13.5f, 6.5f, paint)
                    canvas.drawLine(8.7f, 4.2f, 8.7f, 6.5f, paint)
                    path.moveTo(11.8f, 6.5f)
                    path.quadTo(10.5f, 12.2f, 5.2f, 15.2f)
                    path.moveTo(6.2f, 9.5f)
                    path.quadTo(8.5f, 12.8f, 11.8f, 14.8f)
                    canvas.drawPath(path, paint)
                    path.reset()
                    path.moveTo(13.5f, 19.5f)
                    path.lineTo(16.8f, 11f)
                    path.lineTo(20.1f, 19.5f)
                    canvas.drawPath(path, paint)
                    canvas.drawLine(14.7f, 16.8f, 18.9f, 16.8f, paint)
                }
                HudIcon.CROP -> {
                    path.moveTo(7f, 3.5f)
                    path.lineTo(7f, 17f)
                    path.lineTo(20.5f, 17f)
                    path.moveTo(3.5f, 7f)
                    path.lineTo(17f, 7f)
                    path.lineTo(17f, 20.5f)
                    canvas.drawPath(path, paint)
                }
                HudIcon.MORE -> {
                    canvas.drawCircle(6f, 12f, 1.55f, fillPaint)
                    canvas.drawCircle(12f, 12f, 1.55f, fillPaint)
                    canvas.drawCircle(18f, 12f, 1.55f, fillPaint)
                }
                HudIcon.COPY -> {
                    rect.set(8.5f, 8.5f, 19f, 19.5f)
                    canvas.drawRoundRect(rect, 2.2f, 2.2f, paint)
                    path.moveTo(6.5f, 15.5f)
                    path.lineTo(5.5f, 15.5f)
                    path.quadTo(4.5f, 15.5f, 4.5f, 14.5f)
                    path.lineTo(4.5f, 5.5f)
                    path.quadTo(4.5f, 4.5f, 5.5f, 4.5f)
                    path.lineTo(14.5f, 4.5f)
                    path.quadTo(15.5f, 4.5f, 15.5f, 5.5f)
                    path.lineTo(15.5f, 6.5f)
                    canvas.drawPath(path, paint)
                }
                HudIcon.SELECT_ALL -> {
                    path.moveTo(4f, 8f); path.lineTo(4f, 4f); path.lineTo(8f, 4f)
                    path.moveTo(16f, 4f); path.lineTo(20f, 4f); path.lineTo(20f, 8f)
                    path.moveTo(20f, 16f); path.lineTo(20f, 20f); path.lineTo(16f, 20f)
                    path.moveTo(8f, 20f); path.lineTo(4f, 20f); path.lineTo(4f, 16f)
                    canvas.drawPath(path, paint)
                    rect.set(8f, 8f, 16f, 16f)
                    canvas.drawRoundRect(rect, 1.4f, 1.4f, paint)
                }
                HudIcon.TOKENS -> {
                    rect.set(4f, 5f, 10.5f, 10.5f)
                    canvas.drawRoundRect(rect, 2f, 2f, paint)
                    rect.set(13.5f, 5f, 20f, 10.5f)
                    canvas.drawRoundRect(rect, 2f, 2f, paint)
                    rect.set(4f, 13.5f, 13f, 19f)
                    canvas.drawRoundRect(rect, 2f, 2f, paint)
                    rect.set(15.5f, 13.5f, 20f, 19f)
                    canvas.drawRoundRect(rect, 2f, 2f, paint)
                }
                HudIcon.SHARE -> {
                    path.moveTo(12f, 15.5f)
                    path.lineTo(12f, 4.5f)
                    path.moveTo(8f, 8.5f)
                    path.lineTo(12f, 4.2f)
                    path.lineTo(16f, 8.5f)
                    path.moveTo(5.5f, 13.5f)
                    path.lineTo(5.5f, 18.5f)
                    path.quadTo(5.5f, 19.5f, 6.5f, 19.5f)
                    path.lineTo(17.5f, 19.5f)
                    path.quadTo(18.5f, 19.5f, 18.5f, 18.5f)
                    path.lineTo(18.5f, 13.5f)
                    canvas.drawPath(path, paint)
                }
                HudIcon.SEARCH -> {
                    canvas.drawCircle(10.5f, 10.5f, 5.8f, paint)
                    canvas.drawLine(15f, 15f, 19.5f, 19.5f, paint)
                }
                HudIcon.SAVE -> {
                    path.moveTo(12f, 4f)
                    path.lineTo(12f, 15f)
                    path.moveTo(8f, 11.2f)
                    path.lineTo(12f, 15.5f)
                    path.lineTo(16f, 11.2f)
                    path.moveTo(5f, 17f)
                    path.lineTo(5f, 19.2f)
                    path.lineTo(19f, 19.2f)
                    path.lineTo(19f, 17f)
                    canvas.drawPath(path, paint)
                }
                HudIcon.TUNE -> {
                    canvas.drawLine(4f, 7.5f, 20f, 7.5f, paint)
                    canvas.drawLine(4f, 12f, 20f, 12f, paint)
                    canvas.drawLine(4f, 16.5f, 20f, 16.5f, paint)
                    canvas.drawCircle(8.5f, 7.5f, 1.9f, fillPaint)
                    canvas.drawCircle(15.5f, 12f, 1.9f, fillPaint)
                    canvas.drawCircle(10f, 16.5f, 1.9f, fillPaint)
                }
                HudIcon.EYE -> {
                    path.moveTo(3f, 12f)
                    path.quadTo(12f, 4.5f, 21f, 12f)
                    path.quadTo(12f, 19.5f, 3f, 12f)
                    canvas.drawPath(path, paint)
                    canvas.drawCircle(12f, 12f, 2.4f, paint)
                }
                HudIcon.SWAP -> {
                    path.moveTo(5f, 8.5f); path.lineTo(19f, 8.5f)
                    path.moveTo(15.5f, 5.2f); path.lineTo(19.2f, 8.5f); path.lineTo(15.5f, 11.8f)
                    path.moveTo(19f, 15.5f); path.lineTo(5f, 15.5f)
                    path.moveTo(8.5f, 12.2f); path.lineTo(4.8f, 15.5f); path.lineTo(8.5f, 18.8f)
                    canvas.drawPath(path, paint)
                }
                HudIcon.BOOKMARK -> {
                    path.moveTo(6.5f, 4.5f)
                    path.lineTo(17.5f, 4.5f)
                    path.lineTo(17.5f, 19.5f)
                    path.lineTo(12f, 15.5f)
                    path.lineTo(6.5f, 19.5f)
                    path.close()
                    canvas.drawPath(path, paint)
                }
                HudIcon.REFRESH -> {
                    rect.set(5f, 5f, 19f, 19f)
                    path.arcTo(rect, -60f, 290f)
                    canvas.drawPath(path, paint)
                    path.reset()
                    path.moveTo(18.8f, 4.5f)
                    path.lineTo(19.2f, 8.8f)
                    path.lineTo(14.8f, 8.4f)
                    canvas.drawPath(path, paint)
                }
            }
            canvas.restore()
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
            fillPaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
            fillPaint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private fun dp(v: Float) = (v * context.resources.displayMetrics.density).roundToInt()

    private fun dpF(v: Float) = v * context.resources.displayMetrics.density

    private fun roundedBg(
        radiusDp: Float,
        fillColor: Int,
        strokeColor: Int? = null,
        strokeDp: Float = 1f,
        oval: Boolean = false
    ): GradientDrawable = GradientDrawable().apply {
        if (oval) shape = GradientDrawable.OVAL else cornerRadius = dpF(radiusDp)
        setColor(fillColor)
        if (strokeColor != null) setStroke(max(1, dp(strokeDp)), strokeColor)
    }

    private fun labelView(
        text: CharSequence = "",
        sp: Float = 12f,
        color: Int = Color.WHITE,
        bold: Boolean = false,
        singleLine: Boolean = true
    ): TextView = TextView(context).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        if (singleLine) {
            maxLines = 1
            isSingleLine = true
        }
    }

    private val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleSmall).apply {
        isIndeterminate = true
    }
    private val statusLabel = labelView(sp = 11.5f, color = Color.parseColor("#E1EAF7"), singleLine = false).apply {
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        setPadding(dp(4f), 0, dp(4f), 0)
    }
    private val settingsHintBtn = labelView("设置 >", 11.5f, Color.parseColor("#7AB8FF"), bold = true).apply {
        setPadding(dp(6f), dp(2f), dp(2f), dp(2f))
        visibility = View.GONE
        setOnClickListener { if (isCurrent()) onOpenOcrSettings() }
    }
    val statusPill: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = View.GONE
        setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
        background = roundedBg(18f, Color.argb(226, 22, 26, 35), Color.argb(56, 255, 255, 255))
        elevation = dpF(10f)
        addView(progressBar, LinearLayout.LayoutParams(dp(14f), dp(14f)).apply { marginEnd = dp(5f) })
        addView(statusLabel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(settingsHintBtn)
        setOnClickListener {
            if (isCurrent() && settingsHintBtn.visibility == View.VISIBLE) {
                onOpenOcrSettings()
            }
        }
    }

    val feedbackBanner: TextView = labelView(sp = 12f, bold = true, singleLine = false).apply {
        visibility = View.GONE
        gravity = Gravity.CENTER
        setPadding(dp(14f), dp(7f), dp(14f), dp(7f))
        background = roundedBg(18f, Color.argb(238, 22, 34, 52), Color.parseColor("#7AB8FF"))
        elevation = dpF(16f)
    }

    private val tokenSelectedPreview = labelView(sp = 12.5f).apply { ellipsize = TextUtils.TruncateAt.END }
    private val tokenCountBadge = labelView(sp = 11f, color = Color.parseColor("#7AB8FF"), bold = true).apply {
        setPadding(dp(8f), 0, dp(8f), 0)
    }
    private val tokenSearchQuickBtn = labelView("搜索", 11.5f, Color.parseColor("#7AB8FF"), bold = true).apply {
        setPadding(dp(8f), dp(3f), dp(8f), dp(3f))
        background = roundedBg(10f, Color.parseColor("#1D3354"))
        setOnClickListener {
            if (!isCurrent()) return@setOnClickListener
            val q = presentation.tokenSelectionOutput().ifBlank { presentation.selectedText() }
            if (q.isNotBlank()) onSearchQuery(q)
        }
    }
    private val tokenChipsHost = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    val tokenTunerPanel: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        visibility = View.GONE
        setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
        background = roundedBg(20f, Color.argb(242, 22, 26, 34), Color.argb(58, 255, 255, 255))
        elevation = dpF(14f)
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(6f))
            addView(tokenSelectedPreview, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(tokenCountBadge)
            addView(tokenSearchQuickBtn)
        })
        addView(
            ScrollView(context).apply { addView(tokenChipsHost) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(84f))
        )
    }

    private val dockSlotsRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val dockScroll = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        addView(dockSlotsRow, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }
    private val idleCapsuleBar = FrameLayout(context).apply {
        setPadding(dp(5f), dp(3f), dp(5f), dp(3f))
        background = roundedBg(18f, Color.argb(204, 24, 28, 36), Color.argb(54, 255, 255, 255))
        elevation = dpF(10f)
        addView(dockScroll, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, dp(30f)))
    }

    val bottomDockHost: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        addView(tokenTunerPanel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(8f)
        })
        addView(
            idleCapsuleBar,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        )
    }

    private val contextCapsuleRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val contextCapsuleScroll = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        addView(contextCapsuleRow, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }
    val contextualCapsuleHost: FrameLayout = FrameLayout(context).apply {
        visibility = View.GONE
        setPadding(dp(5f), dp(3f), dp(5f), dp(3f))
        background = roundedBg(19f, Color.argb(228, 24, 28, 38), Color.argb(60, 255, 255, 255))
        elevation = dpF(14f)
        addView(contextCapsuleScroll, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, dp(32f)))
    }

    val plainTextEditor: EditText = EditText(context).apply {
        keyListener = null
        setTextIsSelectable(true)
        background = null
        setTextColor(Color.parseColor("#EDF2F9"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setLineSpacing(dpF(4f), 1.38f)
        setPadding(dp(16f), dp(10f), dp(16f), dp(14f))
        gravity = Gravity.TOP or Gravity.START
    }

    private fun plainActionBtn(label: String, accent: Boolean = false, onClick: () -> Unit): TextView =
        labelView(label, 12.5f, bold = true).apply {
            setPadding(dp(11f), dp(6f), dp(11f), dp(6f))
            if (accent) background = roundedBg(14f, Color.parseColor("#2B7DE9"))
            setOnClickListener {
                if (isCurrent()) {
                    dismissMoreMenu()
                    onClick()
                }
            }
        }

    val plainTextCard: FrameLayout = FrameLayout(context).apply {
        visibility = View.GONE
        background = roundedBg(22f, Color.argb(242, 20, 24, 32), Color.argb(55, 255, 255, 255))
        elevation = dpF(12f)
        val toolbar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14f), dp(10f), dp(10f), dp(8f))
            addView(labelView("全部文字", 13.5f, bold = true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(plainActionBtn("复制", true) { selectedPlainText().takeIf { it.isNotBlank() }?.let(onCopyText) })
            addView(plainActionBtn("搜索") { selectedPlainText().takeIf { it.isNotBlank() }?.let(onSearchQuery) })
            addView(plainActionBtn("分享") { selectedPlainText().takeIf { it.isNotBlank() }?.let(onShareText) })
            addView(plainActionBtn("收起") {
                presentation.showPlainTextLayer = false
                onRefreshAll()
            })
        }
        val divider = View(context).apply {
            setBackgroundColor(Color.argb(32, 255, 255, 255))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
        }
        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(toolbar)
                addView(divider)
                addView(
                    ScrollView(context).apply {
                        isVerticalScrollBarEnabled = true
                        addView(plainTextEditor, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
                    },
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
                )
            },
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )
    }

    private var currentSafeInsets = Rect()
    private var hideBannerRunnable: Runnable? = null
    private var lastDockKey = ""
    private var lastContextKey = ""
    private var lastFabMenuKey = ""
    private var fabPosInitialized = false
    private var fabXRatio = 0.88f
    private var fabYRatio = 0.62f
    private var fabMenuExpanded = false

    private val fabIconView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER
        setImageDrawable(HudVectorDrawable(HudIcon.MORE, Color.WHITE, dp(20f), dpF(2f)))
    }

    val fabButton: FrameLayout = FrameLayout(context).apply {
        contentDescription = "识屏快捷菜单"
        elevation = dpF(14f)
        addView(fabIconView, FrameLayout.LayoutParams(dp(22f), dp(22f), Gravity.CENTER))
    }

    val fabMenuCard: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        visibility = View.GONE
        setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
        background = roundedBg(18f, Color.argb(238, 22, 26, 34), Color.argb(72, 255, 255, 255))
        elevation = dpF(16f)
    }

    init {
        val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var startLeft = 0
        var startTop = 0
        var dragging = false
        fabButton.setOnTouchListener { v, ev ->
            if (!isCurrent()) return@setOnTouchListener false
            val dm = context.resources.displayMetrics
            val viewW = dm.widthPixels.coerceAtLeast(1)
            val viewH = dm.heightPixels.coerceAtLeast(1)
            val (minX, maxX, minY, maxY) = fabBounds(viewW, viewH)
            val lp = fabButton.layoutParams as? FrameLayout.LayoutParams
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = ev.rawX
                    downRawY = ev.rawY
                    startLeft = lp?.leftMargin ?: minX
                    startTop = lp?.topMargin ?: minY
                    dragging = false
                    v.isPressed = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downRawX
                    val dy = ev.rawY - downRawY
                    if (!dragging && kotlin.math.hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                        dragging = true
                        if (fabMenuExpanded) {
                            fabMenuExpanded = false
                            refreshFabUi()
                        }
                    }
                    if (dragging && lp != null) {
                        lp.gravity = Gravity.TOP or Gravity.START
                        lp.leftMargin = (startLeft + dx.roundToInt()).coerceIn(minX, maxX)
                        lp.topMargin = (startTop + dy.roundToInt()).coerceIn(minY, maxY)
                        fabButton.layoutParams = lp
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    if (dragging && ev.actionMasked == MotionEvent.ACTION_UP) {
                        val curL = lp?.leftMargin ?: startLeft
                        val curT = lp?.topMargin ?: startTop
                        fabXRatio = ((curL - minX).toFloat() / (maxX - minX).coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                        fabYRatio = ((curT - minY).toFloat() / (maxY - minY).coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                        positionFabAndMenu()
                        onFabPositionSaved(fabXRatio, fabYRatio)
                    } else if (!dragging && ev.actionMasked == MotionEvent.ACTION_UP) {
                        v.performClick()
                        fabMenuExpanded = !fabMenuExpanded
                        refreshFabUi()
                    } else if (dragging) {
                        positionFabAndMenu()
                    }
                    dragging = false
                    true
                }
                else -> false
            }
        }
    }

    private fun selectedPlainText(): String {
        val s = plainTextEditor.selectionStart.coerceAtLeast(0)
        val e = plainTextEditor.selectionEnd.coerceAtLeast(0)
        return if (e > s) plainTextEditor.text.substring(s, e) else plainTextEditor.text.toString()
    }

    fun dismissMoreMenu(): Boolean {
        if (fabMenuExpanded) {
            fabMenuExpanded = false
            refreshFabUi()
            return true
        }
        return false
    }

    private fun fabBounds(viewW: Int, viewH: Int): IntArray {
        val fabPx = dp(48f)
        val minX = currentSafeInsets.left + dp(20f)
        val maxX = (viewW - currentSafeInsets.right - dp(20f) - fabPx).coerceAtLeast(minX)
        val minY = currentSafeInsets.top + dp(56f)
        val maxY = (viewH - currentSafeInsets.bottom - dp(20f) - fabPx).coerceAtLeast(minY)
        return intArrayOf(minX, maxX, minY, maxY)
    }

    private fun updateFabButtonAppearance() {
        fabButton.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (fabMenuExpanded) Color.argb(244, 37, 99, 235) else Color.argb(228, 22, 26, 34))
            setStroke(max(1, dp(1f)), Color.argb(if (fabMenuExpanded) 128 else 80, 255, 255, 255))
        }
        fabIconView.setImageDrawable(
            HudVectorDrawable(
                if (fabMenuExpanded) HudIcon.CLOSE else HudIcon.MORE,
                Color.WHITE,
                dp(20f),
                dpF(2f)
            )
        )
    }

    private fun buildFabMenuRow(
        icon: HudIcon,
        label: String,
        tintColor: Int = Color.argb(245, 255, 255, 255),
        onClick: () -> Unit
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(42f)
            minimumWidth = dp(152f)
            setPadding(dp(12f), dp(8f), dp(14f), dp(8f))
            background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(56, 255, 255, 255)),
                roundedBg(12f, Color.argb(20, 255, 255, 255)),
                roundedBg(12f, Color.WHITE)
            )
            isClickable = true
            isFocusable = true
            addView(ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setImageDrawable(HudVectorDrawable(icon, tintColor, dp(18f), dpF(1.8f)))
            }, LinearLayout.LayoutParams(dp(20f), dp(20f)).apply { marginEnd = dp(12f) })
            addView(TextView(context).apply {
                text = label
                textSize = 14f
                setTextColor(tintColor)
                setSingleLine(true)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            setOnClickListener {
                if (!isCurrent()) return@setOnClickListener
                fabMenuExpanded = false
                refreshFabUi()
                onClick()
            }
        }
    }

    private fun rebuildFabMenuRows() {
        val hasSelection = presentation.focusState != FocusState.IDLE
        val key = "${!isCroppedRegion && canCropRegion}|$hasSelection"
        if (key == lastFabMenuKey) return
        lastFabMenuKey = key
        val defaultTint = Color.argb(245, 255, 255, 255)
        val rows = buildList {
            if (hasSelection) {
                add(Triple(HudIcon.BACK to defaultTint, "取消选中") { onBackAction() })
            }
            add(Triple(HudIcon.TRANSLATE to defaultTint, "全屏翻译") { onRunTranslate() })
            add(Triple(HudIcon.TEXT to defaultTint, "查看全文") {
                if (presentation.blocks.isEmpty() && !presentation.ocrDone) {
                    showBanner("正在识别文字，请稍候…")
                } else {
                    presentation.showPlainTextLayer = !presentation.showPlainTextLayer
                    presentation.showTokenTuner = false
                    onRefreshAll()
                }
            })
            if (!isCroppedRegion && canCropRegion) {
                add(Triple(HudIcon.CROP to defaultTint, "区域框选") { onRequestCrop() })
            }
            add(Triple(HudIcon.REFRESH to defaultTint, "重新识别与提图") {
                onRerunOcr()
                onRerunSubImages()
            })
            add(Triple(HudIcon.TUNE to defaultTint, "识屏设置") { onOpenOcrSettings() })
            add(Triple(HudIcon.CLOSE to Color.parseColor("#FF9B95"), "退出识屏") { onDismiss() })
        }
        fabMenuCard.removeAllViews()
        rows.forEachIndexed { idx, (iconTint, label, action) ->
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42f))
            if (idx > 0) lp.topMargin = dp(2f)
            fabMenuCard.addView(buildFabMenuRow(iconTint.first, label, iconTint.second, action), lp)
        }
    }

    private fun positionFabAndMenu() {
        if (!fabPosInitialized) {
            val cfg = effectiveConfig()
            fabXRatio = cfg.fabXFraction.coerceIn(0f, 1f)
            fabYRatio = cfg.fabYFraction.coerceIn(0f, 1f)
            fabPosInitialized = true
        }
        val dm = context.resources.displayMetrics
        val viewW = dm.widthPixels.coerceAtLeast(1)
        val viewH = dm.heightPixels.coerceAtLeast(1)
        val fabPx = dp(48f)
        val (minX, maxX, minY, maxY) = fabBounds(viewW, viewH)
        val fabLeft = minX + ((maxX - minX) * fabXRatio).roundToInt()
        val fabTop = minY + ((maxY - minY) * fabYRatio).roundToInt()
        ((fabButton.layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(fabPx, fabPx)).also { lp ->
            lp.gravity = Gravity.TOP or Gravity.START
            lp.width = fabPx
            lp.height = fabPx
            lp.leftMargin = fabLeft
            lp.topMargin = fabTop
            fabButton.layoutParams = lp
        }
        if (!fabMenuExpanded) return
        val maxMenuW = (viewW - currentSafeInsets.left - currentSafeInsets.right - dp(24f)).coerceAtLeast(dp(160f))
        val maxMenuH = (viewH - currentSafeInsets.top - currentSafeInsets.bottom - dp(96f)).coerceAtLeast(dp(180f))
        fabMenuCard.measure(
            View.MeasureSpec.makeMeasureSpec(maxMenuW, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(maxMenuH, View.MeasureSpec.AT_MOST)
        )
        val menuW = fabMenuCard.measuredWidth.coerceIn(dp(164f), maxMenuW)
        val menuH = fabMenuCard.measuredHeight.coerceIn(dp(120f), maxMenuH)
        val safeMinMenuX = currentSafeInsets.left + dp(12f)
        val safeMaxMenuX = (viewW - currentSafeInsets.right - dp(12f) - menuW).coerceAtLeast(safeMinMenuX)
        val preferAlignRight = fabLeft + fabPx * 0.5f >= viewW * 0.5f
        val menuLeft = (if (preferAlignRight) fabLeft + fabPx - menuW else fabLeft).coerceIn(safeMinMenuX, safeMaxMenuX)
        val safeMinMenuY = currentSafeInsets.top + dp(48f)
        val safeMaxMenuY = (viewH - currentSafeInsets.bottom - dp(16f) - menuH).coerceAtLeast(safeMinMenuY)
        val gap = dp(10f)
        val canOpenAbove = fabTop - gap - menuH >= safeMinMenuY
        val preferAbove = canOpenAbove && (fabTop + fabPx * 0.5f > viewH * 0.5f || fabTop + fabPx + gap + menuH > viewH - currentSafeInsets.bottom - dp(16f))
        val menuTop = (if (preferAbove) fabTop - gap - menuH else fabTop + fabPx + gap).coerceIn(safeMinMenuY, safeMaxMenuY)
        ((fabMenuCard.layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)).also { lp ->
            lp.gravity = Gravity.TOP or Gravity.START
            lp.leftMargin = menuLeft
            lp.topMargin = menuTop
            fabMenuCard.layoutParams = lp
        }
        fabMenuCard.bringToFront()
        fabButton.bringToFront()
    }

    private fun refreshFabUi() {
        updateFabButtonAppearance()
        if (fabMenuExpanded) {
            rebuildFabMenuRows()
            fabMenuCard.visibility = View.VISIBLE
        } else {
            fabMenuCard.visibility = View.GONE
        }
        positionFabAndMenu()
        fabButton.bringToFront()
    }

    fun applySafeInsets(safeInsets: Rect) {
        currentSafeInsets = Rect(safeInsets)
        fun updateLp(v: View, block: FrameLayout.LayoutParams.() -> Unit) {
            (v.layoutParams as? FrameLayout.LayoutParams)?.apply {
                block()
                v.layoutParams = this
            }
        }
        updateLp(statusPill) {
            gravity = Gravity.TOP or Gravity.START
            topMargin = safeInsets.top + dp(10f)
            leftMargin = safeInsets.left + dp(14f)
            rightMargin = safeInsets.right + dp(14f)
        }
        updateLp(feedbackBanner) {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = safeInsets.top + dp(54f)
        }
        updateLp(bottomDockHost) {
            width = FrameLayout.LayoutParams.MATCH_PARENT
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            leftMargin = safeInsets.left + dp(12f)
            rightMargin = safeInsets.right + dp(12f)
            bottomMargin = safeInsets.bottom + dp(8f)
        }
        val displayH = context.resources.displayMetrics.heightPixels
        val sheetMaxH = ((displayH - safeInsets.top - safeInsets.bottom) * 0.55f).roundToInt().coerceAtLeast(dp(200f))
        updateLp(plainTextCard) {
            width = FrameLayout.LayoutParams.MATCH_PARENT
            height = sheetMaxH
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            leftMargin = safeInsets.left + dp(10f)
            rightMargin = safeInsets.right + dp(10f)
            bottomMargin = safeInsets.bottom + dp(50f)
        }
        refreshFabUi()
    }

    fun setCanvasDragging(active: Boolean) {
        if (!isCurrent()) return
        val targets = arrayOf<View>(bottomDockHost, contextualCapsuleHost, fabButton)
        targets.forEach { it.animate().cancel() }
        if (active) {
            dismissMoreMenu()
            targets.forEach { it.animate().alpha(0.22f).setStartDelay(70L).setDuration(110L).start() }
        } else {
            targets.forEach { it.animate().alpha(1f).setStartDelay(0L).setDuration(100L).start() }
        }
    }

    fun refresh(anchorRect: RectF? = null) {
        if (!isCurrent()) return
        val subs = extractedSubImagesProvider()
        val origCount = subs.count { it.isUnderlyingOriginal }
        val vis = presentation.visibility()

        plainTextCard.visibility = if (vis.plainText) View.VISIBLE else View.GONE
        if (vis.plainText) plainTextEditor.setText(presentation.editorDisplayText())
        tokenTunerPanel.visibility = if (vis.tokens) View.VISIBLE else View.GONE

        val focused = presentation.focusState == FocusState.TEXT_SELECTED ||
            presentation.focusState == FocusState.IMAGE_SELECTED
        val showContextCapsule = focused && !vis.plainText
        val showTranslateDock = presentation.focusState == FocusState.TRANSLATE && !vis.plainText && !showContextCapsule
        idleCapsuleBar.visibility = if (showTranslateDock) View.VISIBLE else View.GONE
        bottomDockHost.visibility = if (vis.tokens || showTranslateDock) View.VISIBLE else View.GONE

        val showFab = !vis.plainText && !showTranslateDock
        if (!showFab && fabMenuExpanded) fabMenuExpanded = false
        fabButton.visibility = if (showFab) View.VISIBLE else View.GONE
        if (showFab) {
            refreshFabUi()
        } else {
            fabMenuCard.visibility = View.GONE
        }

        if (showContextCapsule) {
            rebuildContextualCapsule(subs)
            positionContextualCapsule(anchorRect, vis.tokens)
        } else {
            contextualCapsuleHost.visibility = View.GONE
            if (showTranslateDock) rebuildIdleOrTranslateDock()
        }

        val statusMsg = presentation.statusText(subs.size, origCount)
        val showSettingsHint = ScreenAssistantOverlayUiPolicy.shouldShowSettingsHint(
            presentation.panelCollapsed,
            presentation.ocrDone,
            presentation.blocks.size,
            presentation.lastOcrNote,
            presentation.lastOcrEngineUsed,
            effectiveConfig().ocrEngine,
            isSubImageMode = false
        )
        progressBar.visibility = if (vis.progress) View.VISIBLE else View.GONE
        statusLabel.text = statusMsg
        settingsHintBtn.text = ScreenAssistantOverlayUiPolicy.settingsHintActionLabel(
            presentation.lastOcrNote,
            presentation.lastOcrEngineUsed,
            effectiveConfig().ocrEngine
        )
        settingsHintBtn.setTextColor(
            if (presentation.lastOcrNote.contains("⚠") || presentation.lastOcrNote.contains("签名")) {
                Color.parseColor("#FFD666")
            } else {
                Color.parseColor("#7AB8FF")
            }
        )
        settingsHintBtn.visibility = if (showSettingsHint && presentation.ocrDone) View.VISIBLE else View.GONE
        val showTopPill = vis.progress ||
            presentation.focusState == FocusState.IMAGE_SELECTED ||
            (showSettingsHint && presentation.ocrDone) ||
            presentation.focusState == FocusState.TRANSLATE && presentation.translating
        statusPill.visibility = if (showTopPill && statusMsg.isNotBlank()) View.VISIBLE else View.GONE
    }

    private data class DockSlot(
        val icon: HudIcon,
        val label: String,
        val activeHex: String? = null,
        val contentDescription: String = label,
        val onClick: (() -> Unit)? = null,
        val onTouch: View.OnTouchListener? = null
    )

    private fun rebuildIdleOrTranslateDock() {
        val focus = presentation.focusState
        val inCrop = isCroppedRegion
        val engShort = effectiveConfig().translateEngine.label.take(3)
        val key = listOf(
            focus.name,
            presentation.showPlainTextLayer.toString(),
            presentation.holdOriginalPreview.toString(),
            engShort,
            inCrop.toString(),
            canCropRegion.toString()
        ).joinToString("|")
        if (key == lastDockKey) return
        lastDockKey = key

        val slots: List<DockSlot> = if (focus == FocusState.TRANSLATE) {
            listOf(
                DockSlot(
                    HudIcon.EYE,
                    "按住原图",
                    if (presentation.holdOriginalPreview) "#2B7DE9" else null,
                    onTouch = View.OnTouchListener { v, ev ->
                        if (!isCurrent()) return@OnTouchListener false
                        when (ev.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                presentation.holdOriginalPreview = true
                                onInvalidateCanvas()
                                v.isPressed = true
                                true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                presentation.holdOriginalPreview = false
                                onInvalidateCanvas()
                                v.isPressed = false
                                if (ev.actionMasked == MotionEvent.ACTION_UP && abs(ev.x) < v.width * 1.5f) {
                                    v.performClick()
                                }
                                true
                            }
                            else -> false
                        }
                    }
                ),
                DockSlot(
                    HudIcon.SWAP,
                    "双语",
                    if (presentation.showPlainTextLayer) "#2B7DE9" else null,
                    onClick = {
                        presentation.showPlainTextLayer = !presentation.showPlainTextLayer
                        onRefreshAll()
                    }
                ),
                DockSlot(HudIcon.TUNE, engShort, contentDescription = "切换翻译引擎", onClick = {
                    onCycleTranslateEngine()
                    onRetranslate()
                }),
                DockSlot(HudIcon.BOOKMARK, "存词库", onClick = { onSaveOfflineLexicon() }),
                DockSlot(HudIcon.CLOSE, "退出", contentDescription = "退出原位翻译", onClick = { onSwitchMode(ScreenAssistantMode.TEXT_SELECT) })
            )
        } else {
            emptyList()
        }

        dockSlotsRow.removeAllViews()
        slots.forEachIndexed { idx, slot ->
            val v = buildCapsuleItem(slot, compact = true)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT)
            if (idx > 0) lp.marginStart = dp(2f)
            dockSlotsRow.addView(v, lp)
        }
    }

    private fun rebuildContextualCapsule(subs: List<SubImageResult>) {
        val focus = presentation.focusState
        val singleSel = presentation.selectionSpans.entries.singleOrNull()
        val singleBlock = singleSel?.let { e -> presentation.blocks.firstOrNull { it.id == e.key } }
        val isSubSpan = singleBlock != null &&
            (singleSel.value.startIndex > 0 || singleSel.value.endIndex < singleBlock.text.length)
        val activeSub = subs.getOrNull(presentation.activeSubImageIndex)
        val hasBox = activeSub != null && activeSub.candidate.leftPx >= 0
        val key = listOf(
            focus.name,
            isSubSpan.toString(),
            presentation.showTokenTuner.toString(),
            activeSub?.isUnderlyingOriginal.toString(),
            hasBox.toString(),
            presentation.adjustingSubImageCrop.toString()
        ).joinToString("|")
        if (key != lastContextKey) {
            lastContextKey = key
            val slots: List<DockSlot> = if (focus == FocusState.TEXT_SELECTED) {
                listOf(
                    DockSlot(HudIcon.COPY, "复制", "#2B7DE9", onClick = {
                        presentation.tokenSelectionOutput().takeIf { it.isNotBlank() }?.let(onCopyText)
                    }),
                    DockSlot(
                        HudIcon.SELECT_ALL,
                        if (isSubSpan) "整行" else "全选",
                        onClick = {
                            if (isSubSpan && singleBlock != null) {
                                presentation.selectTextSpans(mapOf(singleBlock.id to ScreenTextSelectionPolicy.fullSpan(singleBlock)))
                            } else {
                                presentation.selectTextSpans(presentation.blocks.associate { it.id to ScreenTextSelectionPolicy.fullSpan(it) })
                            }
                            rebuildTokenChips()
                            onRefreshAll()
                        }
                    ),
                    DockSlot(
                        HudIcon.TOKENS,
                        "拆词",
                        if (presentation.showTokenTuner) "#2B7DE9" else null,
                        onClick = {
                            presentation.showTokenTuner = !presentation.showTokenTuner
                            onRefreshAll()
                        }
                    ),
                    DockSlot(HudIcon.TRANSLATE, "翻译", onClick = { onRunTranslate() }),
                    DockSlot(HudIcon.SEARCH, "搜索", onClick = {
                        val q = presentation.tokenSelectionOutput().ifBlank { presentation.selectedText() }
                        if (q.isNotBlank()) onSearchQuery(q)
                    }),
                    DockSlot(HudIcon.SHARE, "分享", onClick = {
                        presentation.tokenSelectionOutput().ifBlank { presentation.selectedText() }.takeIf { it.isNotBlank() }?.let(onShareText)
                    })
                )
            } else {
                val saveHex = if (activeSub?.isUnderlyingOriginal == true) "#1E8449" else "#2B7DE9"
                listOf(
                    DockSlot(
                        HudIcon.SAVE,
                        if (activeSub?.isUnderlyingOriginal == true) "存单层图" else "保存",
                        saveHex,
                        onClick = {
                            if (activeSub != null) imageActions.dispatch(Action.SAVE, ScreenAssistantImage.Extracted(activeSub))
                        }
                    ),
                    DockSlot(HudIcon.COPY, "复制", onClick = {
                        if (activeSub != null) imageActions.dispatch(Action.COPY, ScreenAssistantImage.Extracted(activeSub))
                    }),
                    DockSlot(
                        if (hasBox) HudIcon.CROP else HudIcon.EYE,
                        if (hasBox) (if (presentation.adjustingSubImageCrop) "完成" else "微调") else "预览",
                        if (presentation.adjustingSubImageCrop) "#B7791F" else null,
                        onClick = {
                            if (hasBox) {
                                presentation.adjustingSubImageCrop = !presentation.adjustingSubImageCrop
                                presentation.previewingActiveSubImage = false
                            } else {
                                presentation.previewingActiveSubImage = !presentation.previewingActiveSubImage
                            }
                            onInvalidateCanvas()
                            onRefreshAll()
                        }
                    ),
                    DockSlot(HudIcon.TEXT, "识字", onClick = {
                        if (activeSub != null) onRecognizeSubImage(activeSub.bitmap)
                    }),
                    DockSlot(HudIcon.SHARE, "分享", onClick = {
                        if (activeSub != null) imageActions.dispatch(Action.SHARE, ScreenAssistantImage.Extracted(activeSub))
                    })
                )
            }
            contextCapsuleRow.removeAllViews()
            slots.forEachIndexed { idx, slot ->
                val v = buildCapsuleItem(slot, compact = true)
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT)
                if (idx > 0) lp.marginStart = dp(2f)
                contextCapsuleRow.addView(v, lp)
            }
        }
    }

    private fun positionContextualCapsule(anchorRect: RectF?, tokenTunerVisible: Boolean) {
        val dm = context.resources.displayMetrics
        val viewW = dm.widthPixels.coerceAtLeast(1)
        val viewH = dm.heightPixels.coerceAtLeast(1)
        val sidePad = dp(12f)
        val maxAvailW = (viewW - currentSafeInsets.left - currentSafeInsets.right - sidePad * 2).coerceAtLeast(dp(180f))
        contextualCapsuleHost.measure(
            View.MeasureSpec.makeMeasureSpec(maxAvailW, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(dp(44f), View.MeasureSpec.AT_MOST)
        )
        val capW = contextualCapsuleHost.measuredWidth.coerceIn(dp(120f), maxAvailW)
        val capH = contextualCapsuleHost.measuredHeight.coerceAtLeast(dp(42f))
        val minTop = currentSafeInsets.top + dp(54f)
        val bottomReserved = if (tokenTunerVisible) dp(148f) else dp(18f)
        val maxTop = (viewH - currentSafeInsets.bottom - bottomReserved - capH).coerceAtLeast(minTop)
        val gap = dp(16f).toFloat()

        val targetTop: Int
        val targetLeft: Int
        if (anchorRect != null && anchorRect.width() > 0f && anchorRect.height() > 0f) {
            val aboveTop = (anchorRect.top - gap - capH).roundToInt()
            val belowTop = (anchorRect.bottom + gap + dp(18f)).roundToInt()
            targetTop = when {
                aboveTop >= minTop -> minOf(aboveTop, maxTop)
                belowTop <= maxTop -> belowTop
                else -> maxTop
            }
            val cx = ((anchorRect.left + anchorRect.right) * 0.5f).roundToInt()
            val minLeft = currentSafeInsets.left + sidePad
            val maxLeft = (viewW - currentSafeInsets.right - sidePad - capW).coerceAtLeast(minLeft)
            targetLeft = (cx - capW / 2).coerceIn(minLeft, maxLeft)
        } else {
            targetTop = maxTop
            val safeCenterX = currentSafeInsets.left + (viewW - currentSafeInsets.left - currentSafeInsets.right) / 2
            targetLeft = (safeCenterX - capW / 2).coerceIn(currentSafeInsets.left + sidePad, maxOf(currentSafeInsets.left + sidePad, viewW - currentSafeInsets.right - sidePad - capW))
        }

        val lp = (contextualCapsuleHost.layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        lp.gravity = Gravity.TOP or Gravity.START
        lp.leftMargin = targetLeft
        lp.topMargin = targetTop
        contextualCapsuleHost.layoutParams = lp
        contextualCapsuleHost.visibility = View.VISIBLE
        contextualCapsuleHost.bringToFront()
    }

    private fun buildCapsuleItem(slot: DockSlot, compact: Boolean): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        val hPad = when {
            slot.label.isEmpty() -> dp(8f)
            compact -> dp(9f)
            else -> dp(10f)
        }
        setPadding(hPad, dp(3f), hPad, dp(3f))
        if (slot.activeHex != null) {
            background = roundedBg(15f, Color.parseColor(slot.activeHex))
        } else {
            background = roundedBg(15f, Color.TRANSPARENT)
        }
        contentDescription = slot.contentDescription

        val iconSize = dp(14f)
        val iconIv = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER
            setImageDrawable(HudVectorDrawable(slot.icon, Color.WHITE, iconSize, dpF(1.7f)))
        }
        addView(iconIv, LinearLayout.LayoutParams(iconSize, iconSize))

        if (slot.label.isNotEmpty()) {
            val titleTv = labelView(slot.label, 11.5f, Color.parseColor("#F1F5FB"), bold = slot.activeHex != null).apply {
                setPadding(dp(4f), 0, 0, 0)
            }
            addView(titleTv)
        }

        if (slot.onTouch != null) {
            setOnTouchListener(slot.onTouch)
        } else {
            setOnClickListener {
                if (isCurrent()) {
                    dismissMoreMenu()
                    slot.onClick?.invoke()
                }
            }
        }
    }

    fun rebuildTokenChips() {
        if (!isCurrent()) return
        tokenChipsHost.removeAllViews()
        presentation.rebuildTokens()
        val merged = presentation.selectedText()
        if (presentation.activeTokens.isEmpty()) {
            tokenSelectedPreview.text = ""
            tokenCountBadge.text = ""
            return
        }
        tokenSelectedPreview.text = merged
        tokenCountBadge.text = "已选 ${merged.length} 字"
        val maxRowPx = (context.resources.displayMetrics.widthPixels * 0.78f).toInt().coerceAtLeast(dp(200f))
        var curRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        var curW = 0
        val pm = TextPaint().apply {
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, context.resources.displayMetrics)
        }
        presentation.activeTokens.forEachIndexed { idx, tok ->
            val cw = pm.measureText(tok).toInt() + dp(18f)
            if (curW > 0 && curW + cw > maxRowPx) {
                tokenChipsHost.addView(curRow)
                curRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                curW = 0
            }
            curRow.addView(
                labelView(tok, 12f, singleLine = false).apply {
                    setPadding(dp(8f), dp(4f), dp(8f), dp(4f))
                    background = roundedBg(9f, Color.parseColor("#2A3242"))
                    setOnClickListener {
                        if (!isCurrent()) return@setOnClickListener
                        if (!presentation.tokenSelectedIndices.remove(idx)) presentation.tokenSelectedIndices.add(idx)
                        val sel = idx in presentation.tokenSelectedIndices
                        background = roundedBg(9f, Color.parseColor(if (sel) "#2B7DE9" else "#2A3242"))
                        val out = presentation.tokenSelectionOutput()
                        tokenSelectedPreview.text = out
                        tokenCountBadge.text = "已选 ${out.length} 字"
                    }
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = dp(5f)
                    bottomMargin = dp(5f)
                }
            )
            curW += cw + dp(5f)
        }
        if (curRow.childCount > 0) tokenChipsHost.addView(curRow)
    }

    fun showBanner(message: String, durationMs: Long = 2200L) {
        if (message.isBlank()) return
        hideBannerRunnable?.let { feedbackBanner.removeCallbacks(it) }
        feedbackBanner.text = message
        feedbackBanner.visibility = View.VISIBLE
        val task = Runnable { feedbackBanner.visibility = View.GONE }
        hideBannerRunnable = task
        feedbackBanner.postDelayed(task, durationMs.coerceIn(1000L, 6000L))
    }
}
