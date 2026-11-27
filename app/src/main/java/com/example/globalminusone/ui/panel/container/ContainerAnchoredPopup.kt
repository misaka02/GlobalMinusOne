package com.example.globalminusone.ui.panel.container

import android.graphics.Rect
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ContainerLayoutType
import com.example.globalminusone.data.resolvedContainerPopupMs
import com.example.globalminusone.data.policy.containerPopupBounds
import com.example.globalminusone.data.policy.containerPopupHeightDp
import com.example.globalminusone.data.policy.containerPopupPosition
import com.example.globalminusone.data.policy.containerPopupWidthDp
import com.example.globalminusone.ui.motion.MotionTokens
import com.example.globalminusone.ui.motion.blockClosingTouches
import kotlin.math.roundToInt
import com.example.globalminusone.ui.panel.keyboard.KeyboardCandidateOverlayHostBox
import com.example.globalminusone.ui.panel.cards.LocalAppMenuEnvironment
import com.example.globalminusone.ui.panel.cards.LocalCardColors
import com.example.globalminusone.ui.panel.cards.LocalCloseContainerPopup
import com.example.globalminusone.ui.panel.editor.LocalEditDragViewport
import com.example.globalminusone.ui.panel.cards.LocalIsEditMode
import com.example.globalminusone.ui.panel.layout.LocalPageViewportActive
import com.example.globalminusone.ui.panel.gesture.LocalPanelFlingCalibration
import com.example.globalminusone.ui.panel.gesture.LocalPanelGestureOwnership
import com.example.globalminusone.ui.panel.LocalPanelOverlayPortal
import com.example.globalminusone.ui.panel.LocalPanelPalette
import com.example.globalminusone.ui.panel.gesture.LocalPanelScrollTarget
import com.example.globalminusone.ui.panel.gesture.PanelGestureOwnership
import com.example.globalminusone.ui.panel.PanelModalBackHandler
import com.example.globalminusone.ui.panel.gesture.containerPanelScroll
import com.example.globalminusone.ui.panel.gesture.observePanelReleaseVelocity
import com.example.globalminusone.ui.panel.gesture.ownPanelScrollGestures
import com.example.globalminusone.ui.panel.gesture.rememberContainerScrollRoute
import com.example.globalminusone.ui.panel.gesture.rememberPanelFlingCalibration
import com.example.globalminusone.ui.panel.layout.resolveTransitionCompositingStrategy
import com.example.globalminusone.ui.panel.gesture.strictPanelBackgroundTap

internal val LocalContainerPopupId = staticCompositionLocalOf<String?> { null }

private class FolderPopupPlacement {
    var position: IntOffset? = null
    var translationToAnchorX: Float = 0f
    var translationToAnchorY: Float = 0f
}

/**
 * Anchored folder host. Uses the in-window [PanelOverlayPortalState] when available so opening a
 * folder never spawns a cross-process Android Popup window; falls back to [Popup] for standalone UI tests.
 */
@Composable
internal fun ContainerAnchoredPopup(
    item: ComponentItem,
    anchorBounds: IntRect,
    onDismiss: () -> Unit,
    expanded: Boolean = true,
    forceFullColumns: Boolean = false,
    gridColumns: Int = 32,
    onDismissAnimationEnd: () -> Unit = onDismiss,
    overlay: @Composable () -> Unit = {},
    header: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val portal = LocalPanelOverlayPortal.current
    if (portal != null) {
        val portalKey = remember(item.id) { Any() }
        val cardColors = LocalCardColors.current
        val isEditMode = LocalIsEditMode.current
        val appMenuEnv = LocalAppMenuEnvironment.current
        val inContainerChild = LocalInContainerChild.current
        val closeContainerPopup = LocalCloseContainerPopup.current
        val folderEditAction = LocalFolderEditAction.current
        val folderOpenProperties = LocalFolderOpenProperties.current
        val folderGridSpacing = LocalFolderGridSpacing.current
        val folderIconSizeDp = LocalFolderIconSizeDp.current
        val pageViewportActive = LocalPageViewportActive.current
        val editDragViewport = LocalEditDragViewport.current
        val latestLayer by rememberUpdatedState<@Composable () -> Unit>({
            CompositionLocalProvider(
                LocalCardColors provides cardColors,
                LocalIsEditMode provides isEditMode,
                LocalAppMenuEnvironment provides appMenuEnv,
                LocalInContainerChild provides inContainerChild,
                LocalCloseContainerPopup provides closeContainerPopup,
                LocalFolderEditAction provides folderEditAction,
                LocalFolderOpenProperties provides folderOpenProperties,
                LocalFolderGridSpacing provides folderGridSpacing,
                LocalFolderIconSizeDp provides folderIconSizeDp,
                LocalPageViewportActive provides pageViewportActive,
                LocalEditDragViewport provides editDragViewport
            ) {
                ContainerAnchoredPopupBody(
                    item = item,
                    anchorBounds = anchorBounds,
                    onDismiss = onDismiss,
                    expanded = expanded,
                    forceFullColumns = forceFullColumns,
                    gridColumns = gridColumns,
                    onDismissAnimationEnd = onDismissAnimationEnd,
                    usePortalWindow = true,
                    overlay = overlay,
                    header = header,
                    content = content
                )
            }
        })
        DisposableEffect(portal, portalKey) {
            portal.upsert(portalKey) { latestLayer() }
            onDispose { portal.remove(portalKey) }
        }
        val mountedClearGeneration = remember(portal, portalKey) { portal.clearGeneration }
        val latestOnPortalCleared by rememberUpdatedState(onDismissAnimationEnd)
        LaunchedEffect(portal, portal.clearGeneration) {
            if (portal.clearGeneration != mountedClearGeneration) {
                latestOnPortalCleared()
            }
        }
        return
    }

    val view = LocalView.current
    val density = LocalDensity.current
    WindowInsets.ime.getBottom(density)
    val visible = visibleFrameInWindow(view)
    val metrics = view.resources.displayMetrics
    val hostWidth = minOf(view.rootView.width.takeIf { it > 0 } ?: metrics.widthPixels, metrics.widthPixels).coerceAtLeast(1)
    val hostHeight = minOf(view.rootView.height.takeIf { it > 0 } ?: metrics.heightPixels, metrics.heightPixels).coerceAtLeast(1)
    val windowLeft = visible.left.coerceIn(0, hostWidth - 1)
    val windowTop = visible.top.coerceIn(0, hostHeight - 1)
    val windowRight = visible.right.coerceIn(windowLeft + 1, hostWidth)
    val windowBottom = visible.bottom.coerceIn(windowTop + 1, hostHeight)
    val position = remember(windowLeft, windowTop) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset = IntOffset(windowLeft, windowTop)
        }
    }
    Popup(
        popupPositionProvider = position,
        onDismissRequest = { if (!PanelModalBackHandler.handleBack()) onDismiss() },
        properties = PopupProperties(focusable = true, clippingEnabled = false)
    ) {
        com.example.globalminusone.ui.Android12WindowKeyGuard()
        Box(
            Modifier.requiredSize(
                with(density) { (windowRight - windowLeft).toDp() },
                with(density) { (windowBottom - windowTop).toDp() }
            )
        ) {
            ContainerAnchoredPopupBody(
                item = item,
                anchorBounds = anchorBounds,
                onDismiss = onDismiss,
                expanded = expanded,
                forceFullColumns = forceFullColumns,
                gridColumns = gridColumns,
                onDismissAnimationEnd = onDismissAnimationEnd,
                usePortalWindow = false,
                fallbackOrigin = IntOffset(windowLeft, windowTop),
                overlay = overlay,
                header = header,
                content = content
            )
        }
    }
}

private fun visibleFrameInWindow(view: android.view.View): Rect {
    val visible = Rect().also { view.getWindowVisibleDisplayFrame(it) }
    val screen = IntArray(2).also { view.getLocationOnScreen(it) }
    val window = IntArray(2).also { view.getLocationInWindow(it) }
    visible.offset(window[0] - screen[0], window[1] - screen[1])
    return visible
}

@Composable
private fun ContainerAnchoredPopupBody(
    item: ComponentItem,
    anchorBounds: IntRect,
    onDismiss: () -> Unit,
    expanded: Boolean,
    forceFullColumns: Boolean,
    gridColumns: Int,
    onDismissAnimationEnd: () -> Unit,
    usePortalWindow: Boolean,
    fallbackOrigin: IntOffset = IntOffset.Zero,
    overlay: @Composable () -> Unit,
    header: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val animMs = LocalPanelPalette.current.themeConfig.resolvedContainerPopupMs()
    val panelBounds = LocalContainerPresentation.current?.panelBounds
    WindowInsets.ime.getBottom(density)
    val visible = visibleFrameInWindow(view)
    val gap = with(density) { 8.dp.roundToPx() }
    val metrics = view.resources.displayMetrics
    val bounds = containerPopupBounds(
        visible.left, visible.top, visible.right, visible.bottom,
        view.rootView.width.takeIf { it > 0 } ?: metrics.widthPixels,
        view.rootView.height.takeIf { it > 0 } ?: metrics.heightPixels,
        metrics.widthPixels, metrics.heightPixels, gap
    )
    val preferredWidthPx = bounds.right - bounds.left
    val forceCols = if (forceFullColumns && item.containerLayout == ContainerLayoutType.GRID) {
        item.containerColumns.coerceIn(1, 8)
    } else null
    val width = containerPopupWidthDp(
        item,
        with(density) { preferredWidthPx.toDp().value },
        forceColumns = forceCols,
        gridColumns = gridColumns
    ).dp
    val preferredHeightPx = panelBounds?.let { minOf(bounds.bottom, it.bottom) - maxOf(bounds.top, it.top) }
        ?.takeIf { it > 0 } ?: (bounds.bottom - bounds.top)
    val maxHeight = containerPopupHeightDp(item, with(density) { preferredHeightPx.toDp().value }).dp
    val heightModifier = if (item.containerPopupHeightDp > 0) Modifier.height(maxHeight) else Modifier.heightIn(max = maxHeight)

    val popupOwnership = remember(item.id) { PanelGestureOwnership() }
    val flingCalibration = rememberPanelFlingCalibration()
    val scroll = rememberScrollState()
    val route = rememberContainerScrollRoute(scroll, parent = null)
    val placement = remember(item.id) { FolderPopupPlacement() }
    val portalOriginState = remember(item.id) { mutableStateOf(fallbackOrigin) }
    val progress = remember(item.id) { Animatable(if (animMs <= 0) 1f else 0.24f) }
    val latestOnDismissAnimationEnd by rememberUpdatedState(onDismissAnimationEnd)

    LaunchedEffect(expanded, animMs) {
        if (expanded) {
            if (animMs <= 0) {
                progress.snapTo(1f)
            } else {
                if (progress.value < 0.24f) progress.snapTo(0.24f)
                progress.animateTo(1f, MotionTokens.overlayEnterSpring(animMs))
            }
        } else {
            if (animMs <= 0) {
                progress.snapTo(0f)
            } else {
                progress.animateTo(0f, MotionTokens.overlayExitSpring(animMs))
            }
            latestOnDismissAnimationEnd()
        }
    }

    val folderShape = remember { RoundedCornerShape(18.dp) }
    val rootModifier = if (usePortalWindow) {
        Modifier.fillMaxSize().imePadding().onGloballyPositioned { coords ->
            val r = coords.boundsInWindow()
            portalOriginState.value = IntOffset(r.left.roundToInt(), r.top.roundToInt())
        }
    } else {
        Modifier.fillMaxSize()
    }

    KeyboardCandidateOverlayHostBox(rootModifier) {
        FolderModalScope(onDismiss) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val a = 0.14f * progress.value.coerceIn(0f, 1f)
                        if (a > 0f) drawRect(Color.Black.copy(alpha = a))
                    }
                    .then(if (expanded) Modifier.strictPanelBackgroundTap(onDismiss) else Modifier)
            )
            Layout(modifier = Modifier.fillMaxSize(), content = {
                Surface(
                    Modifier
                        .width(width)
                        .then(heightModifier)
                        .graphicsLayer {
                            val p = progress.value.coerceIn(0f, 1f)
                            val s = 0.28f + 0.72f * p
                            val popupAlpha = (p * 1.45f).coerceIn(0f, 1f)
                            scaleX = s
                            scaleY = s
                            translationX = placement.translationToAnchorX * (1f - p)
                            translationY = placement.translationToAnchorY * (1f - p)
                            alpha = popupAlpha
                            transformOrigin = TransformOrigin.Center
                            shape = folderShape
                            shadowElevation = p * 8.dp.toPx()
                            compositingStrategy = resolveTransitionCompositingStrategy(popupAlpha)
                        }
                        .observePanelReleaseVelocity(flingCalibration)
                        // Hit-test barrier blocks scrim taps behind blank card space without consuming UP for child detectors.
                        .pointerInput(expanded) {
                            if (!expanded) return@pointerInput
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent(PointerEventPass.Final)
                                }
                            }
                        },
                    shape = folderShape,
                    color = LocalPanelPalette.current.cardBackground.copy(alpha = 1f),
                    tonalElevation = 6.dp,
                    shadowElevation = 0.dp
                ) {
                    CompositionLocalProvider(
                        LocalContainerPopupId provides item.id,
                        LocalPanelGestureOwnership provides popupOwnership,
                        LocalPanelFlingCalibration provides flingCalibration,
                        LocalPanelScrollTarget provides route.target
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp).blockClosingTouches(!expanded)) {
                            header()
                            Spacer(Modifier.height(8.dp))
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false)
                                    .containerPanelScroll(route, true)
                                    .ownPanelScrollGestures(true),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                content = content
                            )
                        }
                    }
                }
            }) { measurables, constraints ->
                val folder = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
                val preferred = containerPopupPosition(
                    anchorBounds.left, anchorBounds.top, anchorBounds.right, anchorBounds.bottom,
                    folder.width, folder.height,
                    bounds.left, bounds.top, bounds.right, bounds.bottom,
                    gap, panelBounds
                )
                if (!popupOwnership.active || placement.position == null) {
                    placement.position = IntOffset(preferred.x, preferred.y)
                }
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
                val origin = if (usePortalWindow) portalOriginState.value else fallbackOrigin
                layout(constraints.maxWidth, constraints.maxHeight) {
                    folder.place(stable.x - origin.x, stable.y - origin.y)
                }
            }
            Box(Modifier.blockClosingTouches(!expanded)) {
                overlay()
            }
        }
    }
}
