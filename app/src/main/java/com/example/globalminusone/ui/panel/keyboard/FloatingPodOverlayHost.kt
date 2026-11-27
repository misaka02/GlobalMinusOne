package com.example.globalminusone.ui.panel.keyboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.policy.FloatingPodHorizontalAnchor
import com.example.globalminusone.data.policy.FloatingPodStyleSpec
import com.example.globalminusone.data.policy.KeyboardCardColorTokens
import com.example.globalminusone.data.policy.KeyboardCardLayout
import com.example.globalminusone.data.policy.KeyboardSideErgonomicsSpec
import com.example.globalminusone.data.TriggerSide
import com.example.globalminusone.data.resolvedContainerPopupMs
import com.example.globalminusone.data.policy.computeDetachedCandidatePlacement
import com.example.globalminusone.data.policy.computeFloatingPodPlacement
import com.example.globalminusone.data.policy.resolveDetachedCandidateWidthDp
import com.example.globalminusone.data.policy.resolveEffectivePodSizeDp
import com.example.globalminusone.data.policy.resolveKeyboardPodDimensions
import com.example.globalminusone.data.policy.resolveKeyboardSideErgonomics
import com.example.globalminusone.ui.AppIcons as Icons
import com.example.globalminusone.ui.motion.AnimatedOverlayTarget
import com.example.globalminusone.ui.motion.overlayDialogMotion
import kotlin.math.roundToInt
import com.example.globalminusone.ui.panel.cards.AppMenuEnvironment
import com.example.globalminusone.ui.panel.cards.CardColorPalette
import com.example.globalminusone.ui.panel.LocalPanelPalette
import com.example.globalminusone.ui.panel.PanelModalBackHandler

internal val LocalPanelActiveSide = compositionLocalOf { TriggerSide.RIGHT }

internal data class KeyboardCandidateEntry(
    val packageName: String,
    val label: String,
    val iconUri: String
)

internal enum class FloatingPodKind {
    APP_DRAWER,
    BRIGHTNESS,
    VOLUME,
    RECENT_APPS,
    CLIPBOARD,
    NOTE,
    APP_WIDGET,
    BATTERY_DETAIL,
    SINGLE_IMAGE,
    PHOTO_CUBE,
    HITOKOTO
}

internal data class KeyboardCandidateOverlayState(
    val ownerCardId: String,
    val anchorBoundsInWindow: IntRect,
    val candidates: List<KeyboardCandidateEntry>,
    val query: String,
    val showAppLabels: Boolean,
    val isLoadingApps: Boolean,
    val kbdColors: KeyboardCardColorTokens,
    val accentColor: Color,
    val activeSide: TriggerSide,
    val surfaceColor: Color,
    val podExpanded: Boolean = false,
    val activeLayout: KeyboardCardLayout = KeyboardCardLayout.QWERTY,
    val showNumberRow: Boolean = true,
    val reachableKeys: Set<Char>? = null,
    val ergonomics: KeyboardSideErgonomicsSpec = resolveKeyboardSideErgonomics(activeSide),
    val ownerToken: Any = ownerCardId,
    val customWidthDp: Int = 0,
    val customHeightDp: Int = 0,
    val cornerRadiusDp: Int = 16,
    val onAppendKey: (Char) -> Unit = {},
    val onBackspace: () -> Unit = {},
    val onClearQuery: () -> Unit = {},
    val onToggleLayout: () -> Unit = {},
    val onLaunchApp: (KeyboardCandidateEntry) -> Unit,
    val onDismissOverlay: () -> Unit,
    val totalMatchCount: Int = candidates.size,
    val showAllCandidates: Boolean = false,
    val onToggleShowAll: () -> Unit = {}
)

internal data class FunctionalPodOverlayState(
    val ownerToken: Any,
    val ownerCardId: String,
    val componentType: ComponentType = ComponentType.APP_DRAWER_CARD,
    val kind: FloatingPodKind,
    val title: String,
    val icon: ImageVector,
    val anchorBoundsInWindow: IntRect,
    val activeSide: TriggerSide,
    val horizontalAnchor: FloatingPodHorizontalAnchor,
    val surfaceColor: Color,
    val accentColor: Color,
    val kbdColors: KeyboardCardColorTokens,
    val cardColors: CardColorPalette,
    val appMenuEnv: AppMenuEnvironment?,
    val widthDp: Float,
    val heightDp: Float,
    val style: FloatingPodStyleSpec = FloatingPodStyleSpec(),
    val showHeader: Boolean = true,
    val cornerRadiusDp: Int = if (showHeader) 16 else 20,
    val onDismiss: () -> Unit,
    val content: @Composable () -> Unit
)

internal class KeyboardCandidateOverlayHost {
    var activeState by mutableStateOf<KeyboardCandidateOverlayState?>(null)
        private set

    var activeFunctionalPod by mutableStateOf<FunctionalPodOverlayState?>(null)
        private set

    var hostWidthDp by mutableStateOf<Float?>(null)
        internal set

    var hostSafeVerticalWindowRangePx by mutableStateOf<Pair<Int, Int>?>(null)
        internal set

    fun publish(state: KeyboardCandidateOverlayState) {
        val prevPod = activeFunctionalPod
        val prevKbd = activeState?.takeIf { it.ownerToken !== state.ownerToken }
        activeFunctionalPod = null
        activeState = state
        prevPod?.onDismiss?.invoke()
        prevKbd?.onDismissOverlay?.invoke()
    }

    fun unregister(ownerToken: Any) {
        val cur = activeState ?: return
        if (cur.ownerToken === ownerToken || cur.ownerCardId == ownerToken) {
            activeState = null
        }
    }

    fun publishFunctionalPod(state: FunctionalPodOverlayState) {
        val prevKbd = activeState
        val prevPod = activeFunctionalPod?.takeIf { it.ownerToken !== state.ownerToken }
        activeState = null
        activeFunctionalPod = state
        prevKbd?.onDismissOverlay?.invoke()
        prevPod?.onDismiss?.invoke()
    }

    fun unregisterFunctionalPod(ownerToken: Any) {
        val cur = activeFunctionalPod ?: return
        if (cur.ownerToken === ownerToken || cur.ownerCardId == ownerToken) {
            activeFunctionalPod = null
        }
    }

    fun dismiss() {
        val prevKbd = activeState
        val prevPod = activeFunctionalPod
        activeState = null
        activeFunctionalPod = null
        prevKbd?.onDismissOverlay?.invoke()
        prevPod?.onDismiss?.invoke()
    }
}

internal val LocalKeyboardCandidateOverlayHost = compositionLocalOf<KeyboardCandidateOverlayHost?> { null }

@Composable
internal fun KeyboardCandidateOverlayHostBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val host = remember { KeyboardCandidateOverlayHost() }
    CompositionLocalProvider(LocalKeyboardCandidateOverlayHost provides host) {
        Box(modifier = modifier) {
            content()
            KeyboardCandidateOverlayLayer(
                host = host,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun resolveAnchorInHost(
    anchorBoundsInWindow: IntRect,
    hostBoundsInWindow: IntRect,
    hostW: Int,
    hostH: Int
): IntRect = if (anchorBoundsInWindow == IntRect.Zero || hostBoundsInWindow == IntRect.Zero) {
    IntRect(hostW / 2 - 24, hostH / 2 - 24, hostW / 2 + 24, hostH / 2 + 24)
} else {
    IntRect(
        left = anchorBoundsInWindow.left - hostBoundsInWindow.left,
        top = anchorBoundsInWindow.top - hostBoundsInWindow.top,
        right = anchorBoundsInWindow.right - hostBoundsInWindow.left,
        bottom = anchorBoundsInWindow.bottom - hostBoundsInWindow.top
    )
}

@Composable
internal fun KeyboardCandidateOverlayLayer(
    host: KeyboardCandidateOverlayHost,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var hostBoundsInWindow by remember { mutableStateOf(IntRect.Zero) }
    val overlay = host.activeState
    val functionalPod = host.activeFunctionalPod

    val latestOverlay by rememberUpdatedState(overlay)
    val latestFunctionalPod by rememberUpdatedState(functionalPod)
    val backOwner = remember(host) { Any() }
    DisposableEffect(host) {
        PanelModalBackHandler.register(backOwner, PanelModalBackHandler.PRIORITY_DIALOG + 5) {
            val pod = latestFunctionalPod
            val kbd = latestOverlay
            when {
                pod != null -> {
                    pod.onDismiss()
                    true
                }
                kbd != null -> {
                    kbd.onDismissOverlay()
                    true
                }
                else -> false
            }
        }
        onDispose { PanelModalBackHandler.unregister(backOwner) }
    }

    val statusBarTopPx = WindowInsets.statusBars.getTop(density)
    val navBarBottomPx = WindowInsets.navigationBars.getBottom(density)
    val edgeMarginPx = with(density) { 8.dp.roundToPx() }
    val safeTopPx = statusBarTopPx + with(density) { 4.dp.roundToPx() }
    val safeBottomPx = navBarBottomPx + with(density) { 4.dp.roundToPx() }
    val gapPx = with(density) { 6.dp.roundToPx() }

    BoxWithConstraints(
        modifier = modifier.onGloballyPositioned { coords ->
            val r = coords.boundsInWindow()
            val rect = IntRect(r.left.roundToInt(), r.top.roundToInt(), r.right.roundToInt(), r.bottom.roundToInt())
            hostBoundsInWindow = rect
            val wPx = (rect.right - rect.left).coerceAtLeast(0)
            val hPx = (rect.bottom - rect.top).coerceAtLeast(0)
            if (wPx > 0) host.hostWidthDp = with(density) { wPx.toDp().value }
            if (hPx > 0) host.hostSafeVerticalWindowRangePx = (rect.top + safeTopPx) to (rect.bottom - safeBottomPx)
        }
    ) {
        val measuredHostW = hostBoundsInWindow.right - hostBoundsInWindow.left
        val measuredHostH = hostBoundsInWindow.bottom - hostBoundsInWindow.top
        val hostW = if (measuredHostW > 0) measuredHostW else constraints.maxWidth
        val hostH = if (measuredHostH > 0) measuredHostH else constraints.maxHeight
        if (hostW <= 0 || hostH <= 0) return@BoxWithConstraints

        val animMs = LocalPanelPalette.current.themeConfig.resolvedContainerPopupMs()
        val hostWidthDp = with(density) { hostW.toDp().value }
        val hostHeightDp = with(density) { hostH.toDp().value }

        AnimatedOverlayTarget(
            targetState = functionalPod,
            animMs = animMs,
            keyOf = { it.ownerToken }
        ) { activePod ->
            val (resolvedWidthDp, resolvedHeightDp) = resolveEffectivePodSizeDp(
                type = activePod.componentType,
                style = activePod.style,
                defaultWidthDp = activePod.widthDp,
                defaultHeightDp = activePod.heightDp,
                hostWidthDp = hostWidthDp,
                hostHeightDp = hostHeightDp
            )
            val anchor = resolveAnchorInHost(activePod.anchorBoundsInWindow, hostBoundsInWindow, hostW, hostH)
            PodBackdropScrim(onDismiss = activePod.onDismiss)
            PlacedPodLayout(
                hostW = hostW,
                hostH = hostH,
                anchor = anchor,
                activeSide = activePod.activeSide,
                horizontalAnchor = activePod.horizontalAnchor,
                edgeMarginPx = edgeMarginPx,
                safeTopPx = safeTopPx,
                safeBottomPx = safeBottomPx,
                gapPx = 0,
                inlineStripMode = false
            ) {
                Box(Modifier.overlayDialogMotion(minScale = 0.90f)) {
                    FloatingFunctionalPodCard(
                        pod = activePod,
                        widthDp = resolvedWidthDp,
                        heightDp = resolvedHeightDp
                    )
                }
            }
        }

        val expandedKbd = if (functionalPod == null) overlay?.takeIf { it.podExpanded } else null
        AnimatedOverlayTarget(
            targetState = expandedKbd,
            animMs = animMs,
            keyOf = { it.ownerToken }
        ) { activeKbd ->
            val podDims = remember(
                activeKbd.activeLayout,
                hostWidthDp,
                hostHeightDp,
                activeKbd.showNumberRow,
                activeKbd.customWidthDp
            ) {
                resolveKeyboardPodDimensions(
                    layout = activeKbd.activeLayout,
                    hostWidthDp = hostWidthDp,
                    cardWidthDp = hostWidthDp,
                    cardHeightDp = hostHeightDp,
                    insideContainer = false,
                    showNumberRow = activeKbd.showNumberRow,
                    customPodWidthDp = activeKbd.customWidthDp
                )
            }
            val safeHostAvailDp = with(density) { (hostH - safeTopPx - safeBottomPx).coerceAtLeast(120).toDp().value }
            val fixedKbdHeightDp = if (activeKbd.customHeightDp > 0) {
                activeKbd.customHeightDp.toFloat().coerceIn(minOf(180f, safeHostAvailDp), safeHostAvailDp)
            } else {
                null
            }
            val anchor = resolveAnchorInHost(activeKbd.anchorBoundsInWindow, hostBoundsInWindow, hostW, hostH)

            PodBackdropScrim(onDismiss = activeKbd.onDismissOverlay)
            val estPodHeightDp = fixedKbdHeightDp ?: podDims.podHeightDp
            val safeTopDp = with(density) { safeTopPx.toDp().value }
            val safeBottomDp = with(density) { (hostH - safeBottomPx).coerceAtLeast(safeTopPx + 1).toDp().value }
            val anchorCenterDp = with(density) { ((anchor.top + anchor.bottom) / 2).toDp().value }
            val kbdTopDp = (anchorCenterDp - estPodHeightDp / 2f).coerceIn(
                safeTopDp,
                (safeBottomDp - estPodHeightDp).coerceAtLeast(safeTopDp)
            )
            val maxSideSpareDp = maxOf(kbdTopDp - safeTopDp - 8f, safeBottomDp - (kbdTopDp + estPodHeightDp) - 8f)
            val useDetachedCandidates = maxSideSpareDp >= 124f
            val podKeyHeight = if (activeKbd.activeLayout == KeyboardCardLayout.T9) {
                podDims.t9KeyHeightDp
            } else {
                podDims.qwertyKeyHeightDp
            }
            if (useDetachedCandidates) {
                val candGapPx = with(density) { 8.dp.roundToPx() }
                val candMaxHeightPx = with(density) { 304.dp.roundToPx() }
                val candMinPrefPx = with(density) { 148.dp.roundToPx() }
                val candMaxWidthDp = resolveDetachedCandidateWidthDp(hostWidthDp)
                val candMaxWidthPx = with(density) { candMaxWidthDp.dp.roundToPx() }
                val candMaxHeightDp = with(density) { candMaxHeightPx.toDp().value }
                val candPlacedAbove = computeDetachedCandidatePlacement(
                    hostW, hostH, with(density) { kbdTopDp.dp.roundToPx() },
                    with(density) { (kbdTopDp + estPodHeightDp).dp.roundToPx() },
                    safeTopPx, safeBottomPx, 0, candGapPx, candMaxHeightPx, candMinPrefPx
                ).placedAbove
                PlacedKeyboardWithCandidatesLayout(
                    hostW = hostW, hostH = hostH, anchor = anchor, activeSide = activeKbd.activeSide,
                    maxCandWidthPx = candMaxWidthPx, safeTopPx = safeTopPx, safeBottomPx = safeBottomPx,
                    gapPx = candGapPx, maxCandHeightPx = candMaxHeightPx, minPrefCandHeightPx = candMinPrefPx
                ) {
                    Box(Modifier.overlayDialogMotion(minScale = 0.90f)) {
                        SingleHandFloatingKeyboardPod(
                            context = context, overlay = activeKbd, podWidthDp = podDims.podWidthDp,
                            podMaxHeightDp = podDims.podHeightDp, fixedHeightDp = fixedKbdHeightDp,
                            keyHeightDp = podKeyHeight, showInlineCandidates = false
                        )
                    }
                    Box(Modifier.overlayDialogMotion(minScale = 0.92f)) {
                        KeyboardCandidateGridPanel(
                            context = context, overlay = activeKbd, panelWidthDp = candMaxWidthDp,
                            maxHeightDp = candMaxHeightDp, showCloseButton = false, placedAbove = candPlacedAbove
                        )
                    }
                }
            } else {
                PlacedPodLayout(
                    hostW = hostW,
                    hostH = hostH,
                    anchor = anchor,
                    activeSide = activeKbd.activeSide,
                    horizontalAnchor = FloatingPodHorizontalAnchor.DOCK_ACTIVE_SIDE,
                    edgeMarginPx = 0,
                    safeTopPx = safeTopPx,
                    safeBottomPx = safeBottomPx,
                    gapPx = gapPx,
                    inlineStripMode = false
                ) {
                    Box(Modifier.overlayDialogMotion(minScale = 0.90f)) {
                        SingleHandFloatingKeyboardPod(
                            context = context,
                            overlay = activeKbd,
                            podWidthDp = podDims.podWidthDp,
                            podMaxHeightDp = (podDims.podHeightDp + 44f).coerceAtMost(safeHostAvailDp),
                            fixedHeightDp = fixedKbdHeightDp,
                            keyHeightDp = podKeyHeight,
                            showInlineCandidates = true
                        )
                    }
                }
            }
        }

        val inlineKbd = if (functionalPod == null) overlay?.takeIf { !it.podExpanded } else null
        if (inlineKbd != null) {
            val anchor = resolveAnchorInHost(inlineKbd.anchorBoundsInWindow, hostBoundsInWindow, hostW, hostH)
            val candGapPx = with(density) { 8.dp.roundToPx() }
            val candMaxHeightPx = with(density) { 304.dp.roundToPx() }
            val candMinPrefPx = with(density) { 148.dp.roundToPx() }
            val candWidthDp = resolveDetachedCandidateWidthDp(hostWidthDp)
            val candMaxWidthPx = with(density) { candWidthDp.dp.roundToPx() }
            val candMaxHeightDp = with(density) { candMaxHeightPx.toDp().value }
            val candPlacedAbove = computeDetachedCandidatePlacement(
                hostW, hostH, anchor.top, anchor.bottom, safeTopPx, safeBottomPx,
                0, candGapPx, candMaxHeightPx, candMinPrefPx
            ).placedAbove
            PlacedDetachedCandidateLayout(
                hostW = hostW, hostH = hostH, anchorTopPx = anchor.top, anchorBottomPx = anchor.bottom,
                activeSide = inlineKbd.activeSide, maxPanelWidthPx = candMaxWidthPx,
                safeTopPx = safeTopPx, safeBottomPx = safeBottomPx, gapPx = candGapPx,
                maxPanelHeightPx = candMaxHeightPx, minPreferredHeightPx = candMinPrefPx
            ) {
                KeyboardCandidateGridPanel(
                    context = context, overlay = inlineKbd, panelWidthDp = candWidthDp,
                    maxHeightDp = candMaxHeightDp, showCloseButton = true, placedAbove = candPlacedAbove
                )
            }
        }
    }
}

@Composable
private fun PodBackdropScrim(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    )
}

@Composable
private fun PlacedPodLayout(
    hostW: Int,
    hostH: Int,
    anchor: IntRect,
    activeSide: TriggerSide,
    horizontalAnchor: FloatingPodHorizontalAnchor,
    edgeMarginPx: Int,
    safeTopPx: Int,
    safeBottomPx: Int,
    gapPx: Int,
    inlineStripMode: Boolean,
    content: @Composable () -> Unit
) {
    Layout(modifier = Modifier.fillMaxSize(), content = content) { measurables, constraints ->
        val placeable = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val pos = computeFloatingPodPlacement(
            hostWidthPx = hostW,
            hostHeightPx = hostH,
            podWidthPx = placeable.width,
            podHeightPx = placeable.height,
            anchorLeftPx = anchor.left,
            anchorTopPx = anchor.top,
            anchorRightPx = anchor.right,
            anchorBottomPx = anchor.bottom,
            activeSide = activeSide,
            horizontalAnchor = horizontalAnchor,
            edgeMarginPx = edgeMarginPx,
            safeTopPx = safeTopPx,
            safeBottomPx = safeBottomPx,
            gapPx = gapPx,
            inlineStripMode = inlineStripMode
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.placeRelative(pos.x, pos.y)
        }
    }
}
