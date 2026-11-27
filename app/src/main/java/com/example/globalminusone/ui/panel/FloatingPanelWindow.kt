package com.example.globalminusone.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import com.example.globalminusone.ui.AppIcons as Icons
import com.example.globalminusone.ui.motion.overlayDialogMotion
import kotlin.math.roundToInt
import com.example.globalminusone.ui.panel.file.FLOATING_WINDOW_MIN_HEIGHT_DP
import com.example.globalminusone.ui.panel.file.fileBrowserNarrowChrome
import com.example.globalminusone.ui.panel.file.floatingWindowPresentationBounds
import com.example.globalminusone.ui.panel.file.opaqueFilePalette
import com.example.globalminusone.ui.panel.file.visibleFileWindowHeight
import com.example.globalminusone.ui.panel.file.workspaceWindowBounds

/** Shared by Compose file/browser windows and the native embedded-app window frame. */
internal const val FLOATING_WINDOW_CORNER_DP = 14
internal const val FLOATING_WINDOW_BORDER_DP = 1

internal val LocalFloatingWindowContentVisible = compositionLocalOf { true }

/** Shared screen workspace window; the caller provides full-overlay constraints and persistence. */
@Composable
internal fun FloatingPanelWindow(
    windowId: String,
    title: String,
    initialWidthFraction: Float = 0.9f,
    initialHeightFraction: Float = 0.55f,
    initialXFraction: Float = 0.05f,
    initialYFraction: Float = 0.18f,
    minHeightDp: Int = FLOATING_WINDOW_MIN_HEIGHT_DP,
    onGeometryCommitted: (Float, Float, Float, Float) -> Unit = { _, _, _, _ -> },
    onClose: (() -> Unit)? = null,
    allowCollapse: Boolean = false,
    onContentVisibilityChanged: (Boolean) -> Unit = {},
    content: @Composable () -> Unit
) {
    val palette = opaqueFilePalette(LocalPanelPalette.current)
    fun finite(value: Float, fallback: Float) = value.takeIf { it.isFinite() } ?: fallback
    var widthFraction by rememberSaveable(windowId) { mutableFloatStateOf(finite(initialWidthFraction, 0.9f)) }
    var heightFraction by rememberSaveable(windowId) { mutableFloatStateOf(finite(initialHeightFraction, 0.68f)) }
    var xFraction by rememberSaveable(windowId) { mutableFloatStateOf(finite(initialXFraction, 0.05f)) }
    var yFraction by rememberSaveable(windowId) { mutableFloatStateOf(finite(initialYFraction, 0.14f)) }
    var collapsed by rememberSaveable(windowId) { mutableStateOf(false) }
    val contentVisible = !allowCollapse || !collapsed
    val visibilityChanged by rememberUpdatedState(onContentVisibilityChanged)
    DisposableEffect(contentVisible) {
        visibilityChanged(contentVisible)
        onDispose { visibilityChanged(false) }
    }
    var activelyManipulating by remember { mutableStateOf(false) }
    var dragTranslation by remember(windowId) { mutableStateOf(Offset.Zero) }
    val commitGeometry by rememberUpdatedState(onGeometryCommitted)
    LaunchedEffect(initialWidthFraction, initialHeightFraction, initialXFraction, initialYFraction) {
        if (!activelyManipulating) {
            widthFraction = finite(initialWidthFraction, 0.9f)
            heightFraction = finite(initialHeightFraction, 0.68f)
            xFraction = finite(initialXFraction, 0.05f)
            yFraction = finite(initialYFraction, 0.14f)
        }
    }
    val density = LocalDensity.current
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    DisposableEffect(windowId, focusManager, keyboard) {
        onDispose {
            // A removed workspace must not leave its editor owning the shared
            // overlay's focus/IME when ordinary panel cards become visible.
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }
    LaunchedEffect(contentVisible) {
        if (!contentVisible) { focusManager.clearFocus(force = true); keyboard?.hide() }
    }
    val configuration = LocalConfiguration.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    var containerTop by remember { mutableFloatStateOf(0f) }
    @Suppress("DEPRECATION")
    val screenHeight = remember(view, configuration.orientation, configuration.screenHeightDp) {
        val metrics = android.util.DisplayMetrics()
        view.display?.getRealMetrics(metrics)
        metrics.heightPixels.takeIf { it > 0 }?.toFloat() ?: view.resources.displayMetrics.heightPixels.toFloat()
    }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { coordinates ->
        val origin = IntArray(2)
        view.rootView.getLocationOnScreen(origin)
        containerTop = coordinates.positionInWindow().y + origin[1]
    }) {
        val availableWidth = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val availableHeight = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val visibleHeight = visibleFileWindowHeight(availableHeight, containerTop, screenHeight, imeBottom)
        val minWidth = with(density) { 220.dp.toPx() }.coerceAtMost(availableWidth)
        val minHeight = with(density) { minHeightDp.dp.toPx() }.coerceAtMost(availableHeight)
        val window = workspaceWindowBounds(availableWidth, availableHeight, visibleHeight,
            minWidth, minHeight, widthFraction, heightFraction, xFraction, yFraction)
        val titleHeight = with(density) { 48.dp.toPx() }
        val shownWindow = floatingWindowPresentationBounds(window, availableHeight, visibleHeight,
            titleHeight, yFraction, contentVisible)
        val width = shownWindow.width; val height = shownWindow.height; val x = shownWindow.x; val y = shownWindow.y
        fun saveGeometry() {
            // Keyboard avoidance is temporary and must never replace the user's saved geometry.
            if (imeBottom > 0) return
            val savedWidth = widthFraction.coerceIn(minWidth / availableWidth, 1f)
            val savedHeight = heightFraction.coerceIn(minHeight / availableHeight, 1f)
            val savedVisibleHeight = if (contentVisible) savedHeight else titleHeight.coerceAtMost(availableHeight) / availableHeight
            commitGeometry(savedWidth, savedHeight,
                xFraction.coerceIn(0f, (1f - savedWidth).coerceAtLeast(0f)),
                yFraction.coerceIn(0f, (1f - savedVisibleHeight).coerceAtLeast(0f)))
        }
        Column(
            Modifier.offset { IntOffset((x + dragTranslation.x).roundToInt(), (y + dragTranslation.y).roundToInt()) }
                .width(with(density) { width.toDp() }).height(with(density) { height.toDp() })
                .overlayDialogMotion(minScale = 0.94f)
                .clip(RoundedCornerShape(FLOATING_WINDOW_CORNER_DP.dp))
                .background(palette.cardBackground)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .border(FLOATING_WINDOW_BORDER_DP.dp, palette.accent.copy(alpha = 0.6f), RoundedCornerShape(FLOATING_WINDOW_CORNER_DP.dp))
        ) {
            Row(
                Modifier.fillMaxWidth().height(48.dp)
                    .background(palette.accent.copy(alpha = 0.08f)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(Modifier.weight(1f).height(48.dp)
                    .semantics { contentDescription = "拖动${title}窗口" }
                    .pointerInput(availableWidth, availableHeight, imeBottom, contentVisible) {
                        if (imeBottom > 0) return@pointerInput
                        var original: FloatArray? = null
                        fun restoreGeometry() {
                            dragTranslation = Offset.Zero
                            original?.let {
                                widthFraction = it[0]; heightFraction = it[1]
                                xFraction = it[2]; yFraction = it[3]
                            }
                            original = null
                        }
                        try {
                        detectDragGestures(
                            onDragStart = {
                                activelyManipulating = true
                                dragTranslation = Offset.Zero
                                original = floatArrayOf(widthFraction, heightFraction, xFraction, yFraction)
                            },
                            onDragEnd = {
                                val currentWidth = (widthFraction * availableWidth).coerceIn(minWidth, availableWidth)
                                val currentHeight = if (contentVisible) (heightFraction * availableHeight).coerceIn(minHeight, availableHeight) else titleHeight.coerceAtMost(availableHeight)
                                xFraction = ((xFraction * availableWidth + dragTranslation.x).coerceIn(0f, availableWidth - currentWidth)) / availableWidth
                                yFraction = ((yFraction * availableHeight + dragTranslation.y).coerceIn(0f, availableHeight - currentHeight)) / availableHeight
                                dragTranslation = Offset.Zero
                                activelyManipulating = false
                                saveGeometry()
                                original = null
                            },
                            onDragCancel = {
                                activelyManipulating = false
                                restoreGeometry()
                            }
                        ) { change, amount ->
                            change.consume()
                            val currentWidth = (widthFraction * availableWidth).coerceIn(minWidth, availableWidth)
                            val currentHeight = if (contentVisible) (heightFraction * availableHeight).coerceIn(minHeight, availableHeight) else titleHeight.coerceAtMost(availableHeight)
                            val baseX = (xFraction * availableWidth).coerceIn(0f, availableWidth - currentWidth)
                            val baseY = (yFraction * availableHeight).coerceIn(0f, availableHeight - currentHeight)
                            val nextX = (baseX + dragTranslation.x + amount.x).coerceIn(0f, availableWidth - currentWidth)
                            val nextY = (baseY + dragTranslation.y + amount.y).coerceIn(0f, availableHeight - currentHeight)
                            dragTranslation = Offset(nextX - baseX, nextY - baseY)
                        }
                        } finally {
                            // A pointerInput restart (including IME changes) can cancel
                            // the coroutine before detectDragGestures calls onDragCancel.
                            activelyManipulating = false
                            restoreGeometry()
                        }
                    }.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!fileBrowserNarrowChrome(window.width / density.density, density.fontScale)) {
                    Icon(Icons.DragHandle, null, Modifier.size(18.dp), tint = palette.textSecondary)
                    Spacer(Modifier.width(6.dp))
                }
                Text(title, fontSize = 14.sp, color = palette.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (allowCollapse && fileBrowserNarrowChrome(window.width / density.density, density.fontScale)) {
                    IconButton(onClick = { collapsed = !collapsed }, modifier = Modifier.size(40.dp)) {
                        Icon(if (contentVisible) Icons.KeyboardArrowUp else Icons.KeyboardArrowDown,
                            if (contentVisible) "收起到标题栏" else "展开文件窗口", tint = palette.textPrimary)
                    }
                } else if (allowCollapse) TextButton(onClick = { collapsed = !collapsed }, modifier = Modifier.height(48.dp)) {
                    Text(if (contentVisible) "收起" else "展开", color = palette.textPrimary,
                        modifier = Modifier.semantics { contentDescription = if (contentVisible) "收起到标题栏" else "展开文件窗口" })
                }
                if (onClose != null) IconButton(onClick = onClose, modifier = Modifier.size(if (fileBrowserNarrowChrome(window.width / density.density, density.fontScale)) 40.dp else 48.dp)) {
                    Icon(Icons.Close, "关闭$title", tint = palette.textPrimary)
                }
            }
            // Keep composition and media alive; an unplaced body owns no hit-test region.
            // Its expanded measurement is stable, so collapsing cannot reset nested pane state.
            val bodyModifier = if (contentVisible) Modifier.weight(1f) else Modifier.height(0.dp)
            Box(bodyModifier.fillMaxWidth().focusProperties { canFocus = contentVisible }
                .then(if (contentVisible) Modifier else Modifier.clearAndSetSemantics {})
                .layout { measurable, bodyConstraints ->
                    val bodyHeight = (window.height - titleHeight - with(density) { 24.dp.toPx() }).roundToInt().coerceAtLeast(0)
                    val placeable = measurable.measure(Constraints.fixed(bodyConstraints.maxWidth, bodyHeight))
                    layout(bodyConstraints.maxWidth, if (contentVisible) bodyConstraints.maxHeight else 0) {
                        if (contentVisible) placeable.placeRelative(0, 0)
                    }
                }) {
                CompositionLocalProvider(LocalFloatingWindowContentVisible provides contentVisible) { content() }
            }
            if (contentVisible) Box(
                Modifier.fillMaxWidth().height(24.dp).background(palette.accent.copy(alpha = 0.05f))
                    .semantics { contentDescription = "调整${title}窗口大小" }
                    .pointerInput(availableWidth, availableHeight, imeBottom) {
                        if (imeBottom > 0) return@pointerInput
                        var original: FloatArray? = null
                        fun restoreGeometry() {
                            original?.let {
                                widthFraction = it[0]; heightFraction = it[1]
                                xFraction = it[2]; yFraction = it[3]
                            }
                            original = null
                        }
                        try {
                        detectDragGestures(
                            onDragStart = {
                                activelyManipulating = true
                                original = floatArrayOf(widthFraction, heightFraction, xFraction, yFraction)
                            },
                            onDragEnd = {
                                activelyManipulating = false
                                saveGeometry()
                                original = null
                            },
                            onDragCancel = {
                                activelyManipulating = false
                                restoreGeometry()
                            }
                        ) { change, amount ->
                            change.consume()
                            val currentX = (xFraction * availableWidth).coerceIn(0f, availableWidth - minWidth)
                            val currentY = (yFraction * availableHeight).coerceIn(0f, availableHeight - minHeight)
                            widthFraction = ((widthFraction * availableWidth).coerceIn(minWidth, availableWidth) + amount.x)
                                .coerceIn(minWidth, availableWidth - currentX) / availableWidth
                            heightFraction = ((heightFraction * availableHeight).coerceIn(minHeight, availableHeight) + amount.y)
                                .coerceIn(minHeight, availableHeight - currentY) / availableHeight
                            xFraction = currentX / availableWidth
                            yFraction = currentY / availableHeight
                        }
                        } finally {
                            activelyManipulating = false
                            restoreGeometry()
                        }
                    },
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.DragHandle, if (imeBottom > 0) "收起键盘后调整大小" else "拖动调整窗口大小",
                    Modifier.padding(horizontal = 10.dp).size(18.dp).graphicsLayer { rotationZ = -45f }, tint = palette.textSecondary)
            }
        }
    }
}
