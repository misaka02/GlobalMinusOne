package com.example.globalminusone.ui.panel.keyboard

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.TriggerSide
import com.example.globalminusone.data.policy.FloatingPodHorizontalAnchor
import com.example.globalminusone.data.policy.computeCandidateGridColumns
import com.example.globalminusone.data.policy.computeDetachedCandidatePlacement
import com.example.globalminusone.data.policy.computeFloatingPodPlacement
import com.example.globalminusone.data.policy.findDirectQueryHighlightRange
import com.example.globalminusone.data.policy.resolveSideFlushPodCorners
import com.example.globalminusone.ui.AppIcons as Icons
import com.example.globalminusone.ui.panel.gesture.PanelGestureAxis
import com.example.globalminusone.ui.panel.gesture.ownPanelScrollGestures
import com.example.globalminusone.ui.panel.gesture.rememberPanelFlingBehavior

/**
 * Spacious floating multi-row candidate grid rendered outside the keyboard pod
 * so search results use the full overlay width instead of a cramped 1-row strip.
 */
@Composable
internal fun KeyboardCandidateGridPanel(
    context: Context,
    overlay: KeyboardCandidateOverlayState,
    panelWidthDp: Float,
    maxHeightDp: Float,
    showCloseButton: Boolean,
    modifier: Modifier = Modifier,
    placedAbove: Boolean = true
) {
    val columns = computeCandidateGridColumns(panelWidthDp, overlay.showAppLabels)
    val safeMaxHeightDp = maxHeightDp.coerceAtLeast(0f)
    val flushCorners = resolveSideFlushPodCorners(overlay.activeSide, overlay.cornerRadiusDp)
    val flushShape = RoundedCornerShape(
        topStart = flushCorners.leftDp.dp, bottomStart = flushCorners.leftDp.dp,
        topEnd = flushCorners.rightDp.dp, bottomEnd = flushCorners.rightDp.dp
    )
    val startPad = if (overlay.activeSide == TriggerSide.LEFT) 6.dp else 8.dp
    val endPad = if (overlay.activeSide == TriggerSide.RIGHT) 6.dp else 8.dp

    Surface(
        shape = flushShape,
        color = overlay.surfaceColor,
        border = BorderStroke(1.dp, overlay.accentColor.copy(alpha = 0.42f)),
        shadowElevation = 12.dp,
        modifier = modifier
            .width(panelWidthDp.dp)
            .heightIn(max = safeMaxHeightDp.dp)
            // Hit-test barrier blocks scrim taps behind blank card space without stealing pointer UP.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) { awaitPointerEvent(PointerEventPass.Final) }
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = startPad, end = endPad, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (placedAbove) CandidateGridHeaderRow(overlay = overlay, showCloseButton = showCloseButton)
            if (overlay.candidates.isEmpty()) {
                CandidateGridEmptyBanner(overlay = overlay)
            } else {
                CandidateGridLazyContent(
                    context = context,
                    overlay = overlay,
                    columns = columns,
                    reverseVertical = placedAbove,
                    maxGridHeightDp = (safeMaxHeightDp - 50f).coerceAtLeast(1f)
                )
            }
            if (!placedAbove) CandidateGridHeaderRow(overlay = overlay, showCloseButton = showCloseButton)
        }
    }
}

@Composable
private fun CandidateGridLazyContent(
    context: Context,
    overlay: KeyboardCandidateOverlayState,
    columns: Int,
    reverseVertical: Boolean,
    maxGridHeightDp: Float
) {
    val gridLayoutDir = if (overlay.activeSide == TriggerSide.RIGHT) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides gridLayoutDir) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            reverseLayout = reverseVertical,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxGridHeightDp.dp)
                .ownPanelScrollGestures(enabled = true, axis = PanelGestureAxis.Vertical),
            flingBehavior = rememberPanelFlingBehavior(),
            contentPadding = PaddingValues(2.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(
                items = overlay.candidates,
                key = { _, entry -> entry.packageName }
            ) { index, candidate ->
                KeyboardCandidateGridTile(
                    context = context,
                    candidate = candidate,
                    overlay = overlay,
                    highlighted = overlay.query.isNotEmpty() && index == 0,
                    onClick = { overlay.onLaunchApp(candidate) }
                )
            }
        }
    }
}

@Composable
private fun CandidateMatchCountText(overlay: KeyboardCandidateOverlayState) {
    val label = if (overlay.query.isNotEmpty()) {
        "${overlay.candidates.size} 个匹配"
    } else if (overlay.showAllCandidates || overlay.candidates.size >= overlay.totalMatchCount) {
        "共 ${overlay.candidates.size} 个"
    } else {
        "${overlay.candidates.size}/${overlay.totalMatchCount}"
    }
    Text(
        text = label,
        color = overlay.accentColor,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1
    )
}

@Composable
private fun CandidateGridHeaderRow(
    overlay: KeyboardCandidateOverlayState,
    showCloseButton: Boolean
) {
    val hasQuery = overlay.query.isNotEmpty()
    val headerLabel = if (hasQuery) overlay.query.uppercase() else if (overlay.showAllCandidates) "全部应用" else "高频 / 最近"
    val headerColor = if (hasQuery) Color(overlay.kbdColors.keyText) else Color(overlay.kbdColors.subText)
    val controlsOnLeft = overlay.ergonomics.headerControlsOnLeft
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(overlay.kbdColors.queryBarBg))
            .border(0.6.dp, Color(overlay.kbdColors.queryBarBorder), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (controlsOnLeft) {
            CandidateHeaderActionControls(overlay = overlay, hasQuery = hasQuery, showCloseButton = showCloseButton)
            CandidateMatchCountText(overlay = overlay)
        }
        Text(
            text = headerLabel,
            color = headerColor,
            fontSize = 12.sp,
            fontWeight = if (hasQuery) FontWeight.Bold else FontWeight.Medium,
            fontFamily = if (hasQuery) FontFamily.Monospace else FontFamily.Default,
            textAlign = if (controlsOnLeft) TextAlign.End else TextAlign.Start,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (!controlsOnLeft) {
            CandidateMatchCountText(overlay = overlay)
            CandidateHeaderActionControls(overlay = overlay, hasQuery = hasQuery, showCloseButton = showCloseButton)
        }
    }
}

@Composable
private fun CandidateHeaderActionControls(
    overlay: KeyboardCandidateOverlayState,
    hasQuery: Boolean,
    showCloseButton: Boolean
) {
    val canToggleAll = !hasQuery && (overlay.totalMatchCount > overlay.candidates.size || overlay.showAllCandidates)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (canToggleAll) {
            ShowAllCandidatesChip(overlay)
        }
        if (hasQuery) {
            HeaderPillChip(
                text = "清空",
                bg = Color(overlay.kbdColors.actionKeyBg),
                border = Color(overlay.kbdColors.actionKeyBorder),
                textColor = Color(overlay.kbdColors.keyText),
                onClick = overlay.onClearQuery
            )
        }
        if (showCloseButton) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(overlay.kbdColors.actionKeyBg))
                    .clickable { if (hasQuery) overlay.onClearQuery() else overlay.onDismissOverlay() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Close, "收起候选", tint = Color(overlay.kbdColors.subText), modifier = Modifier.size(13.dp))
            }
        }
    }
}

@Composable
private fun ShowAllCandidatesChip(overlay: KeyboardCandidateOverlayState) {
    val active = overlay.showAllCandidates
    val bg = if (active) overlay.accentColor.copy(alpha = 0.22f) else Color(overlay.kbdColors.actionKeyBg)
    val border = if (active) overlay.accentColor.copy(alpha = 0.55f) else Color(overlay.kbdColors.actionKeyBorder)
    val textColor = if (active) overlay.accentColor else Color(overlay.kbdColors.keyText)
    HeaderPillChip(
        text = if (active) "常用" else "全部",
        bg = bg,
        border = border,
        textColor = textColor,
        onClick = overlay.onToggleShowAll
    )
}

@Composable
private fun HeaderPillChip(text: String, bg: Color, border: Color, textColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(0.6.dp, border, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = textColor, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CandidateGridEmptyBanner(overlay: KeyboardCandidateOverlayState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(overlay.kbdColors.emptyStripBg))
            .border(0.6.dp, Color(overlay.kbdColors.chipBorder), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (overlay.isLoadingApps) "正在加载应用..." else "无匹配应用，可退格或清空调整",
            color = Color(overlay.kbdColors.subText),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun KeyboardCandidateGridTile(
    context: Context,
    candidate: KeyboardCandidateEntry,
    overlay: KeyboardCandidateOverlayState,
    highlighted: Boolean,
    onClick: () -> Unit
) {
    val displayBitmap = rememberCandidateAppBitmap(context, candidate)
    val tileBg = if (highlighted) overlay.accentColor.copy(alpha = 0.22f) else Color(overlay.kbdColors.chipBg)
    val tileBorder = if (highlighted) overlay.accentColor else Color(overlay.kbdColors.chipBorder)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(11.dp))
                .background(tileBg)
                .border(if (highlighted) 1.3.dp else 0.6.dp, tileBorder, RoundedCornerShape(11.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            CandidateAppIconBox(
                bitmap = displayBitmap,
                label = candidate.label,
                accentColor = overlay.accentColor,
                sizeDp = 36.dp,
                cornerDp = 9.dp,
                letterSp = 13.sp
            )
            if (overlay.showAppLabels) {
                CandidateLabelText(
                    label = candidate.label,
                    query = overlay.query,
                    highlighted = highlighted,
                    textColor = Color(overlay.kbdColors.keyText),
                    accentColor = overlay.accentColor
                )
            }
        }
    }
}

@Composable
private fun CandidateLabelText(
    label: String,
    query: String,
    highlighted: Boolean,
    textColor: Color,
    accentColor: Color
) {
    val range = findDirectQueryHighlightRange(label, query)
    val styled: AnnotatedString = if (range != null) {
        buildAnnotatedString {
            append(label.substring(0, range.first))
            withStyle(SpanStyle(color = accentColor, fontWeight = FontWeight.Bold)) {
                append(label.substring(range.first, range.last + 1))
            }
            append(label.substring(range.last + 1))
        }
    } else {
        AnnotatedString(label)
    }
    Text(
        text = styled,
        color = textColor,
        fontSize = 10.5.sp,
        lineHeight = 12.sp,
        fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Medium,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
internal fun PlacedKeyboardWithCandidatesLayout(
    hostW: Int,
    hostH: Int,
    anchor: IntRect,
    activeSide: TriggerSide,
    maxCandWidthPx: Int,
    safeTopPx: Int,
    safeBottomPx: Int,
    gapPx: Int,
    maxCandHeightPx: Int,
    minPrefCandHeightPx: Int,
    content: @Composable () -> Unit
) {
    Layout(modifier = Modifier.fillMaxSize(), content = content) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val kbdPlaceable = measurables[0].measure(loose)
        val kbdPos = computeFloatingPodPlacement(
            hostWidthPx = hostW, hostHeightPx = hostH,
            podWidthPx = kbdPlaceable.width, podHeightPx = kbdPlaceable.height,
            anchorLeftPx = anchor.left, anchorTopPx = anchor.top,
            anchorRightPx = anchor.right, anchorBottomPx = anchor.bottom,
            activeSide = activeSide, horizontalAnchor = FloatingPodHorizontalAnchor.DOCK_ACTIVE_SIDE,
            edgeMarginPx = 0, safeTopPx = safeTopPx, safeBottomPx = safeBottomPx,
            gapPx = gapPx, inlineStripMode = false
        )
        val preCand = computeDetachedCandidatePlacement(
            hostWidthPx = hostW, hostHeightPx = hostH,
            anchorTopPx = kbdPos.y, anchorBottomPx = kbdPos.y + kbdPlaceable.height,
            safeTopPx = safeTopPx, safeBottomPx = safeBottomPx,
            horizontalMarginPx = 0, gapPx = gapPx,
            maxPanelHeightPx = maxCandHeightPx, minPreferredHeightPx = minPrefCandHeightPx,
            activeSide = activeSide, maxPanelWidthPx = maxCandWidthPx
        )
        val candPlaceable = measurables[1].measure(
            loose.copy(maxWidth = preCand.widthPx, maxHeight = preCand.maxHeightPx.coerceAtLeast(0))
        )
        val candPos = computeDetachedCandidatePlacement(
            hostWidthPx = hostW, hostHeightPx = hostH,
            anchorTopPx = kbdPos.y, anchorBottomPx = kbdPos.y + kbdPlaceable.height,
            safeTopPx = safeTopPx, safeBottomPx = safeBottomPx,
            horizontalMarginPx = 0, gapPx = gapPx,
            maxPanelHeightPx = maxCandHeightPx, minPreferredHeightPx = minPrefCandHeightPx,
            measuredPanelHeightPx = candPlaceable.height,
            activeSide = activeSide, maxPanelWidthPx = maxCandWidthPx,
            measuredPanelWidthPx = candPlaceable.width
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            candPlaceable.placeRelative(candPos.x, candPos.y)
            kbdPlaceable.placeRelative(kbdPos.x, kbdPos.y)
        }
    }
}

@Composable
internal fun PlacedDetachedCandidateLayout(
    hostW: Int,
    hostH: Int,
    anchorTopPx: Int,
    anchorBottomPx: Int,
    activeSide: TriggerSide,
    maxPanelWidthPx: Int,
    safeTopPx: Int,
    safeBottomPx: Int,
    gapPx: Int,
    maxPanelHeightPx: Int,
    minPreferredHeightPx: Int,
    content: @Composable () -> Unit
) {
    Layout(modifier = Modifier.fillMaxSize(), content = content) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val pre = computeDetachedCandidatePlacement(
            hostWidthPx = hostW, hostHeightPx = hostH,
            anchorTopPx = anchorTopPx, anchorBottomPx = anchorBottomPx,
            safeTopPx = safeTopPx, safeBottomPx = safeBottomPx,
            horizontalMarginPx = 0, gapPx = gapPx,
            maxPanelHeightPx = maxPanelHeightPx, minPreferredHeightPx = minPreferredHeightPx,
            activeSide = activeSide, maxPanelWidthPx = maxPanelWidthPx
        )
        val candPlaceable = measurables.single().measure(
            loose.copy(maxWidth = pre.widthPx, maxHeight = pre.maxHeightPx.coerceAtLeast(0))
        )
        val pos = computeDetachedCandidatePlacement(
            hostWidthPx = hostW, hostHeightPx = hostH,
            anchorTopPx = anchorTopPx, anchorBottomPx = anchorBottomPx,
            safeTopPx = safeTopPx, safeBottomPx = safeBottomPx,
            horizontalMarginPx = 0, gapPx = gapPx,
            maxPanelHeightPx = maxPanelHeightPx, minPreferredHeightPx = minPreferredHeightPx,
            measuredPanelHeightPx = candPlaceable.height,
            activeSide = activeSide, maxPanelWidthPx = maxPanelWidthPx,
            measuredPanelWidthPx = candPlaceable.width
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            if (pos.maxHeightPx > 0) {
                candPlaceable.placeRelative(pos.x, pos.y)
            }
        }
    }
}
