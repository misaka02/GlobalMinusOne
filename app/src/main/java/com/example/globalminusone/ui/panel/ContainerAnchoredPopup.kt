package com.example.globalminusone.ui.panel

import android.graphics.Rect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ContainerLayoutType
import com.example.globalminusone.data.containerPopupPosition
import com.example.globalminusone.data.containerPopupWidthDp
import com.example.globalminusone.data.containerPopupBounds
import com.example.globalminusone.data.containerPopupHeightDp
import kotlin.math.roundToInt

internal val LocalContainerPopupId = staticCompositionLocalOf<String?> { null }

private class FolderPopupPlacement {
    var position: IntOffset? = null
    var translationToAnchorX: Float = 0f
    var translationToAnchorY: Float = 0f
}

/** One fixed-size input window. The anchored folder, property editor and confirmations
 * are siblings inside it, so opening a property never creates another Android window. */
@Composable
internal fun ContainerAnchoredPopup(
    item: ComponentItem,
    anchorBounds: IntRect,
    onDismiss: () -> Unit,
    expanded: Boolean = true,
    forceFullColumns: Boolean = false,
    onDismissAnimationEnd: () -> Unit = onDismiss,
    overlay: @Composable () -> Unit = {},
    header: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val animMs = LocalPanelPalette.current.themeConfig.animationDurationMs.coerceIn(0, 800)
    val panelBounds = LocalContainerPresentation.current?.panelBounds
    // Read IME insets as state, so an already-open popup is measured again when the keyboard moves.
    WindowInsets.ime.getBottom(density)
    val visible = Rect().also { view.getWindowVisibleDisplayFrame(it) }
    val screen = IntArray(2).also { view.getLocationOnScreen(it) }
    val window = IntArray(2).also { view.getLocationInWindow(it) }
    visible.offset(window[0] - screen[0], window[1] - screen[1])
    val gap = with(density) { 8.dp.roundToPx() }
    val metrics = view.resources.displayMetrics
    val hostWidth = minOf(view.rootView.width.takeIf { it > 0 } ?: metrics.widthPixels, metrics.widthPixels).coerceAtLeast(1)
    val hostHeight = minOf(view.rootView.height.takeIf { it > 0 } ?: metrics.heightPixels, metrics.heightPixels).coerceAtLeast(1)
    val windowLeft = visible.left.coerceIn(0, hostWidth - 1)
    val windowTop = visible.top.coerceIn(0, hostHeight - 1)
    val windowRight = visible.right.coerceIn(windowLeft + 1, hostWidth)
    val windowBottom = visible.bottom.coerceIn(windowTop + 1, hostHeight)
    val bounds = containerPopupBounds(visible.left, visible.top, visible.right, visible.bottom,
        view.rootView.width.takeIf { it > 0 } ?: metrics.widthPixels,
        view.rootView.height.takeIf { it > 0 } ?: metrics.heightPixels,
        metrics.widthPixels, metrics.heightPixels, gap)
    val left = bounds.left
    val top = bounds.top
    val right = bounds.right
    val bottom = bounds.bottom
    // Panel bounds guide placement; the visible screen is the width limit for a growing grid.
    val preferredWidthPx = right - left
    val forceCols = if (forceFullColumns && item.containerLayout == ContainerLayoutType.GRID) {
        item.containerColumns.coerceIn(1, 8)
    } else null
    val width = containerPopupWidthDp(
        item,
        with(density) { preferredWidthPx.toDp().value },
        forceColumns = forceCols
    ).dp
    val preferredHeightPx = panelBounds?.let { minOf(bottom, it.bottom) - maxOf(top, it.top) }
        ?.takeIf { it > 0 } ?: (bottom - top)
    val maxHeight = containerPopupHeightDp(item, with(density) { preferredHeightPx.toDp().value }).dp
    val position = remember(windowLeft, windowTop) { object : PopupPositionProvider {
        override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
            return IntOffset(windowLeft, windowTop)
        }
    } }
    Popup(popupPositionProvider = position,
        onDismissRequest = { if (!PanelModalBackHandler.handleBack()) onDismiss() },
        properties = PopupProperties(focusable = true, clippingEnabled = false)) {
        com.example.globalminusone.service.Android12WindowKeyGuard()

        // This is a separate input window. Its holds must not reconfigure the
        // underlying panel/pager's pointer chain while a finger is still down.
        val popupOwnership = remember(item.id) { PanelGestureOwnership() }
        val flingCalibration = rememberPanelFlingCalibration()
        val scroll = rememberScrollState()
        val route = rememberContainerScrollRoute(scroll, parent = null)
        val placement = remember(item.id) { FolderPopupPlacement() }
        val progress = remember(item.id) { Animatable(if (animMs <= 0) 1f else 0f) }
        val latestOnDismissAnimationEnd by rememberUpdatedState(onDismissAnimationEnd)
        LaunchedEffect(expanded, animMs) {
            if (expanded) {
                if (animMs <= 0) {
                    progress.snapTo(1f)
                } else {
                    withFrameNanos { }
                    progress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = animMs.coerceIn(130, 240),
                            easing = CubicBezierEasing(0.2f, 0.9f, 0.2f, 1f)
                        )
                    )
                }
            } else {
                if (animMs <= 0) {
                    progress.snapTo(0f)
                } else {
                    progress.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = (animMs * 0.72f).roundToInt().coerceIn(95, 170),
                            easing = CubicBezierEasing(0.4f, 0f, 0.6f, 1f)
                        )
                    )
                }
                latestOnDismissAnimationEnd()
            }
        }
        val heightModifier = if (item.containerPopupHeightDp > 0) Modifier.height(maxHeight) else Modifier.heightIn(max = maxHeight)
        Box(Modifier.requiredSize(with(density) { (windowRight - windowLeft).toDp() },
            with(density) { (windowBottom - windowTop).toDp() })) {
        FolderModalScope(onDismiss) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val alpha = 0.14f * progress.value.coerceIn(0f, 1f)
                    if (alpha > 0f) drawRect(Color.Black.copy(alpha = alpha))
                }
                .then(if (expanded) Modifier.strictPanelBackgroundTap(onDismiss) else Modifier)
        )
        Layout(modifier = Modifier.fillMaxSize(), content = {
        Surface(Modifier.width(width).then(heightModifier)
            .graphicsLayer {
                val p = progress.value.coerceIn(0f, 1f)
                val s = 0.28f + 0.72f * p
                scaleX = s
                scaleY = s
                translationX = placement.translationToAnchorX * (1f - p)
                translationY = placement.translationToAnchorY * (1f - p)
                alpha = (p * 1.45f).coerceIn(0f, 1f)
                transformOrigin = TransformOrigin.Center
            }
            .observePanelReleaseVelocity(flingCalibration)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = expanded) {},
            shape = RoundedCornerShape(18.dp),
            color = LocalPanelPalette.current.cardBackground.copy(alpha = 1f),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp) {
            CompositionLocalProvider(LocalContainerPopupId provides item.id,
                LocalPanelGestureOwnership provides popupOwnership,
                LocalPanelFlingCalibration provides flingCalibration,
                LocalPanelScrollTarget provides route.target) {
                Column(Modifier.fillMaxWidth().padding(12.dp).blockInteractionsWhenClosing(!expanded)) {
                    header()
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.fillMaxWidth().weight(1f, fill = false)
                        .containerPanelScroll(route, true).ownPanelScrollGestures(true),
                        verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
                }
            }
        }
        }) { measurables, constraints ->
            val folder = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
            val preferred = containerPopupPosition(anchorBounds.left, anchorBounds.top, anchorBounds.right, anchorBounds.bottom,
                folder.width, folder.height, left, top, right, bottom, gap, panelBounds)
            if (!popupOwnership.active || placement.position == null) placement.position = IntOffset(preferred.x, preferred.y)
            val stable = placement.position!!
            if (folder.width > 0 && folder.height > 0 && anchorBounds != IntRect.Zero) {
                val anchorCenterX = (anchorBounds.left + anchorBounds.right) * 0.5f
                val anchorCenterY = (anchorBounds.top + anchorBounds.bottom) * 0.5f
                placement.translationToAnchorX = anchorCenterX - (stable.x + folder.width * 0.5f)
                placement.translationToAnchorY = anchorCenterY - (stable.y + folder.height * 0.5f)
            } else {
                placement.translationToAnchorX = 0f
                placement.translationToAnchorY = 0f
            }
            // A card reflow may change surface height mid-drag. The native window
            // and the surface origin both remain fixed until the claim is released.
            val x = stable.x - windowLeft
            val y = stable.y - windowTop
            layout(constraints.maxWidth, constraints.maxHeight) { folder.place(x, y) }
        }
        Box(Modifier.blockInteractionsWhenClosing(!expanded)) {
            overlay()
        }
        }
        }
    }
}

private fun Modifier.blockInteractionsWhenClosing(closing: Boolean): Modifier =
    if (!closing) this else pointerInput(closing) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            }
        }
    }
