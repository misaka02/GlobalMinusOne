package com.example.globalminusone.ui.panel.keyboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.policy.FloatingPodHorizontalAnchor
import com.example.globalminusone.data.policy.FloatingPodStyleSpec
import com.example.globalminusone.data.policy.KeyboardCardColorTokens
import com.example.globalminusone.data.TriggerSide
import com.example.globalminusone.data.policy.isFloatingPodDarkSurface
import com.example.globalminusone.data.policy.isSingleIconLauncherItem
import com.example.globalminusone.data.policy.parseFloatingPodStyle
import com.example.globalminusone.data.policy.resolveFloatingPodSurfaceArgb
import com.example.globalminusone.data.policy.resolveFloatingPodTextArgb
import com.example.globalminusone.data.policy.resolveKeyboardCardColors
import kotlin.math.roundToInt
import com.example.globalminusone.ui.panel.cards.CardColorPalette
import com.example.globalminusone.ui.panel.cards.CustomShortcutComponent
import com.example.globalminusone.ui.panel.cards.LocalAppMenuEnvironment
import com.example.globalminusone.ui.panel.cards.LocalCardColors
import com.example.globalminusone.ui.panel.container.LocalContainerPopupId
import com.example.globalminusone.ui.panel.cards.LocalIsEditMode
import com.example.globalminusone.ui.panel.layout.LocalPageViewportActive
import com.example.globalminusone.ui.panel.LocalPanelPalette
import com.example.globalminusone.ui.panel.PanelPalette

internal data class ResolvedPodVisuals(
    val surfaceColor: Color,
    val cardColors: CardColorPalette,
    val kbdColors: KeyboardCardColorTokens,
    val cornerRadiusDp: Int
)

internal fun resolvePodVisuals(
    style: FloatingPodStyleSpec,
    panelPalette: PanelPalette,
    baseCardColors: CardColorPalette,
    defaultCornerDp: Int
): ResolvedPodVisuals {
    val surfaceArgb = resolveFloatingPodSurfaceArgb(style, panelPalette.cardBackground.toArgb())
    val darkSurface = isFloatingPodDarkSurface(style, surfaceArgb, panelPalette.isDark)
    val hasCustomVisuals = style.bgColorHex != null || style.textColorHex != null
    val textArgb = resolveFloatingPodTextArgb(
        style = style,
        surfaceArgb = surfaceArgb,
        fallbackTextArgb = baseCardColors.textPrimary.toArgb(),
        fallbackDark = panelPalette.isDark
    )
    val podCardColors = if (hasCustomVisuals) {
        val textRgb = Color(textArgb)
        baseCardColors.copy(
            textPrimary = textRgb,
            textSecondary = textRgb.copy(alpha = 0.78f),
            titleColor = textRgb,
            textPlaceholder = textRgb.copy(alpha = 0.56f),
            iconBackground = if (darkSurface) Color.White.copy(alpha = 0.14f) else Color(0xFFF2F3F5),
            trackColor = if (darkSurface) Color.White.copy(alpha = 0.22f) else Color(0xFFE5E6EB)
        )
    } else {
        baseCardColors
    }
    val kbdColors = resolveKeyboardCardColors(
        textPrimaryArgb = textArgb,
        accentArgb = baseCardColors.accent.toArgb(),
        fallbackDark = darkSurface
    )
    val cornerRadiusDp = if (style.cornerRadiusDp in 0..FloatingPodStyleSpec.MAX_POD_CORNER_DP) {
        style.cornerRadiusDp
    } else {
        defaultCornerDp
    }
    return ResolvedPodVisuals(
        surfaceColor = Color(surfaceArgb),
        cardColors = podCardColors,
        kbdColors = kbdColors,
        cornerRadiusDp = cornerRadiusDp
    )
}

@Composable
internal fun FloatingFunctionalPodCard(
    pod: FunctionalPodOverlayState,
    widthDp: Float,
    heightDp: Float
) {
    val controlsOnLeft = pod.activeSide == TriggerSide.LEFT
    val cornerDp = if (pod.cornerRadiusDp >= 0) pod.cornerRadiusDp else if (pod.showHeader) 16 else 20
    val shape = RoundedCornerShape(cornerDp.dp)
    Surface(
        shape = shape,
        color = pod.surfaceColor,
        border = BorderStroke(1.dp, pod.accentColor.copy(alpha = 0.42f)),
        shadowElevation = 14.dp,
        modifier = Modifier
            .width(widthDp.dp)
            .height(heightDp.dp)
            // Non-consuming hit-test barrier so child sliders/buttons never lose pointer UP.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Final)
                    }
                }
            }
    ) {
        CompositionLocalProvider(
            LocalCardColors provides pod.cardColors,
            LocalAppMenuEnvironment provides pod.appMenuEnv,
            LocalIsEditMode provides false
        ) {
            if (!pod.showHeader) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    pod.content()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (controlsOnLeft) {
                            PodCollapseChip(pod = pod)
                        } else {
                            Icon(
                                imageVector = pod.icon,
                                contentDescription = null,
                                tint = pod.accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = pod.title,
                            color = Color(pod.kbdColors.keyText),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = if (controlsOnLeft) TextAlign.End else TextAlign.Start,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (controlsOnLeft) {
                            Icon(
                                imageVector = pod.icon,
                                contentDescription = null,
                                tint = pod.accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            PodCollapseChip(pod = pod)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        pod.content()
                    }
                }
            }
        }
    }
}

@Composable
private fun PodCollapseChip(pod: FunctionalPodOverlayState) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(pod.kbdColors.actionKeyBg))
            .border(0.6.dp, Color(pod.kbdColors.actionKeyBorder), RoundedCornerShape(6.dp))
            .clickable { pod.onDismiss() }
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "收起",
            color = Color(pod.kbdColors.subText),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Unified single-icon pop-out host for functional cards (App Drawer, Brightness, Volume,
 * Recent Apps, Clipboard, Note). Reads [podOpen] & [anchorBoundsInWindow] during composition
 * so taps immediately recompose and register the floating pod with [KeyboardCandidateOverlayHost].
 */
@Composable
internal fun FunctionalCardPopOutWrapper(
    item: ComponentItem,
    podKind: FloatingPodKind,
    defaultTitle: String,
    icon: ImageVector,
    isEditMode: Boolean,
    horizontalAnchor: FloatingPodHorizontalAnchor,
    podWidthDp: Float,
    podHeightDp: Float,
    showHeader: Boolean = true,
    compactWidthThresholdDp: Float = 120f,
    compactHeightThresholdDp: Float = 120f,
    autoPopInsideContainer: Boolean = false,
    inlineContent: @Composable () -> Unit,
    podContent: @Composable (dismiss: () -> Unit) -> Unit
) {
    val overlayHost = LocalKeyboardCandidateOverlayHost.current
    if (overlayHost == null) {
        inlineContent()
        return
    }
    val insideContainer = LocalContainerPopupId.current != null
    var podOpen by remember(item.id) { mutableStateOf(false) }
    val pageActive = if (podOpen) LocalPageViewportActive.current else true
    val panelPalette = LocalPanelPalette.current
    val cardColors = LocalCardColors.current
    val appMenuEnv = LocalAppMenuEnvironment.current
    val activeSide = LocalPanelActiveSide.current
    val ownerToken = remember { Any() }
    val anchorCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }

    val podStyle = remember(item.intentUri) { parseFloatingPodStyle(item.intentUri) }
    val defaultCornerDp = if (showHeader) 16 else 20
    val podVisuals = remember(podStyle, panelPalette, cardColors, defaultCornerDp) {
        resolvePodVisuals(podStyle, panelPalette, cardColors, defaultCornerDp)
    }

    LaunchedEffect(isEditMode, pageActive) {
        if (isEditMode || !pageActive) {
            podOpen = false
            overlayHost.unregisterFunctionalPod(ownerToken)
        }
    }
    DisposableEffect(overlayHost, ownerToken) {
        onDispose { overlayHost.unregisterFunctionalPod(ownerToken) }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords -> anchorCoords[0] = coords }
    ) {
        val explicitInline = item.intentUri.contains("mode=INLINE_CARD")
        val compactBySize = !explicitInline &&
            maxWidth.value < compactWidthThresholdDp &&
            maxHeight.value < compactHeightThresholdDp
        val popOutMode = isSingleIconLauncherItem(item) ||
            (autoPopInsideContainer && insideContainer && !explicitInline) ||
            compactBySize
        fun readAnchorBounds(): IntRect = anchorCoords[0]?.takeIf { it.isAttached }?.boundsInWindow()?.let { r ->
            IntRect(r.left.roundToInt(), r.top.roundToInt(), r.right.roundToInt(), r.bottom.roundToInt())
        } ?: IntRect.Zero
        val shouldPublish = popOutMode && pageActive && !isEditMode && podOpen

        fun publishCurrent() {
            overlayHost.publishFunctionalPod(
                FunctionalPodOverlayState(
                    ownerToken = ownerToken,
                    ownerCardId = item.id,
                    componentType = item.type,
                    kind = podKind,
                    title = item.title.ifBlank { defaultTitle },
                    icon = icon,
                    anchorBoundsInWindow = readAnchorBounds(),
                    activeSide = activeSide,
                    horizontalAnchor = horizontalAnchor,
                    surfaceColor = podVisuals.surfaceColor,
                    accentColor = podVisuals.cardColors.accent,
                    kbdColors = podVisuals.kbdColors,
                    cardColors = podVisuals.cardColors,
                    appMenuEnv = appMenuEnv,
                    widthDp = podWidthDp,
                    heightDp = podHeightDp,
                    style = podStyle,
                    showHeader = showHeader,
                    cornerRadiusDp = podVisuals.cornerRadiusDp,
                    onDismiss = {
                        podOpen = false
                        overlayHost.unregisterFunctionalPod(ownerToken)
                    },
                    content = {
                        podContent {
                            podOpen = false
                            overlayHost.unregisterFunctionalPod(ownerToken)
                        }
                    }
                )
            )
        }

        LaunchedEffect(
            shouldPublish,
            activeSide,
            item.title,
            podStyle,
            podVisuals,
            appMenuEnv,
            popOutMode,
            isEditMode,
            pageActive
        ) {
            if (shouldPublish) {
                publishCurrent()
            } else {
                overlayHost.unregisterFunctionalPod(ownerToken)
            }
        }

        if (popOutMode) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CustomShortcutComponent(
                    item = item.copy(title = item.title.ifBlank { defaultTitle }),
                    enabled = !isEditMode,
                    fallbackIcon = icon,
                    isPopOutLauncherIcon = true,
                    onClick = {
                        if (!isEditMode) {
                            val next = !podOpen
                            podOpen = next
                            if (next) {
                                publishCurrent()
                            } else {
                                overlayHost.unregisterFunctionalPod(ownerToken)
                            }
                        }
                    }
                )
            }
        } else {
            inlineContent()
        }
    }
}
