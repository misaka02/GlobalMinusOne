package com.example.globalminusone.ui.panel.editor

import android.graphics.Rect
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.SuspensionBallConfig
import com.example.globalminusone.data.TriggerConfig
import com.example.globalminusone.data.TriggerPosition
import com.example.globalminusone.data.TriggerWay
import kotlin.math.roundToInt
import com.example.globalminusone.ui.panel.gesture.LocalPanelGestureOwnership
import com.example.globalminusone.ui.panel.LocalPanelPalette

internal data class TriggerZonePreviewGeometry(
    val position: TriggerPosition,
    val xPx: Float,
    val yPx: Float,
    val widthPx: Float,
    val heightPx: Float,
    val movableSpanPx: Float,
    val isHorizontalEdge: Boolean
)

/** Matches OverlayService.getTriggerLayoutParams vertical/horizontal placement math. */
internal fun computeTriggerZonePreviewGeometry(
    position: TriggerPosition,
    screenWidthPx: Float,
    screenHeightPx: Float,
    density: Float,
    config: TriggerConfig
): TriggerZonePreviewGeometry {
    val safeDensity = density.coerceAtLeast(1f)
    val safeW = screenWidthPx.coerceAtLeast(1f)
    val safeH = screenHeightPx.coerceAtLeast(1f)
    return if (position == TriggerPosition.TOP || position == TriggerPosition.BOTTOM) {
        val barW = (safeW * (config.touchHeightPercent.coerceIn(5, 100) / 100f))
            .coerceAtLeast(36f * safeDensity)
            .coerceAtMost(safeW)
        val barH = (safeH * (config.touchWidthPercent.coerceIn(1, 20) / 100f))
            .coerceAtLeast(10f * safeDensity)
            .coerceAtMost(safeH)
        val span = (safeW - barW).coerceAtLeast(0f)
        val xPos = span * (config.touchOffsetPercent.coerceIn(0, 100) / 100f)
        val yPos = if (position == TriggerPosition.TOP) 0f else (safeH - barH).coerceAtLeast(0f)
        TriggerZonePreviewGeometry(
            position = position,
            xPx = xPos,
            yPx = yPos,
            widthPx = barW,
            heightPx = barH,
            movableSpanPx = span,
            isHorizontalEdge = true
        )
    } else {
        val barW = (safeW * (config.touchWidthPercent.coerceIn(1, 20) / 100f))
            .coerceAtLeast(10f * safeDensity)
            .coerceAtMost(safeW)
        val barH = (safeH * (config.touchHeightPercent.coerceIn(5, 100) / 100f))
            .coerceAtLeast(36f * safeDensity)
            .coerceAtMost(safeH)
        val span = (safeH - barH).coerceAtLeast(0f)
        val yPos = span * (config.touchOffsetPercent.coerceIn(0, 100) / 100f)
        val xPos = if (position == TriggerPosition.LEFT) 0f else (safeW - barW).coerceAtLeast(0f)
        TriggerZonePreviewGeometry(
            position = position,
            xPx = xPos,
            yPx = yPos,
            widthPx = barW,
            heightPx = barH,
            movableSpanPx = span,
            isHorizontalEdge = false
        )
    }
}

internal fun draggedTriggerOffsetPercent(
    startOffsetPercent: Int,
    deltaPx: Float,
    movableSpanPx: Float
): Int {
    if (movableSpanPx <= 1f) return startOffsetPercent.coerceIn(0, 100)
    val startPx = movableSpanPx * (startOffsetPercent.coerceIn(0, 100) / 100f)
    val nextPx = (startPx + deltaPx).coerceIn(0f, movableSpanPx)
    return ((nextPx / movableSpanPx) * 100f).roundToInt().coerceIn(0, 100)
}

internal fun draggedTriggerHeightPercent(
    startHeightPercent: Int,
    deltaHeightPx: Float,
    totalAxisPx: Float
): Int {
    if (totalAxisPx <= 1f) return startHeightPercent.coerceIn(5, 100)
    val startPx = totalAxisPx * (startHeightPercent.coerceIn(5, 100) / 100f)
    val nextPx = (startPx + deltaHeightPx).coerceIn(totalAxisPx * 0.05f, totalAxisPx)
    return ((nextPx / totalAxisPx) * 100f).roundToInt().coerceIn(5, 100)
}

/**
 * Full-screen interactive overlay in Edit Mode for dragging the trigger zone vertically (or horizontally
 * for TOP/BOTTOM, or 2D for Suspension Ball) without leaving the panel editor.
 */
@Composable
internal fun TriggerZoneDragOverlay(
    triggerConfig: TriggerConfig,
    suspensionBallConfig: SuspensionBallConfig,
    screenWidthPx: Float,
    screenHeightPx: Float,
    activeRightSide: Boolean,
    onUpdateTrigger: ((TriggerConfig) -> TriggerConfig) -> Unit,
    onUpdateSuspensionBall: ((SuspensionBallConfig) -> SuspensionBallConfig) -> Unit,
    onDone: () -> Unit
) {
    val density = LocalDensity.current
    val palette = LocalPanelPalette.current
    val hostView = LocalView.current
    val isBallMode = triggerConfig.triggerWay == TriggerWay.SUSPENSION_BALL

    val activePositions = remember(triggerConfig.triggerPosition) {
        when (triggerConfig.triggerPosition) {
            TriggerPosition.BOTH -> listOf(TriggerPosition.LEFT, TriggerPosition.RIGHT)
            else -> listOf(triggerConfig.triggerPosition)
        }
    }
    val geometries = remember(activePositions, screenWidthPx, screenHeightPx, density.density, triggerConfig) {
        activePositions.map { pos ->
            computeTriggerZonePreviewGeometry(pos, screenWidthPx, screenHeightPx, density.density, triggerConfig)
        }
    }

    // Exclude active edge trigger regions from Android 10+ system back gestures during drag adjustment.
    DisposableEffect(hostView, geometries, isBallMode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !isBallMode) {
            val maxExclusionHeight = (200f * density.density).roundToInt().coerceAtLeast(1)
            val rects = geometries.filter { !it.isHorizontalEdge }.map { g ->
                val centerY = (g.yPx + g.heightPx / 2f).roundToInt()
                val halfH = (g.heightPx.roundToInt().coerceAtLeast((64f * density.density).roundToInt()) / 2)
                    .coerceAtMost(maxExclusionHeight / 2)
                val left = if (g.position == TriggerPosition.LEFT) 0 else (screenWidthPx.roundToInt() - (48f * density.density).roundToInt()).coerceAtLeast(0)
                val right = if (g.position == TriggerPosition.LEFT) (48f * density.density).roundToInt() else screenWidthPx.roundToInt()
                Rect(left, (centerY - halfH).coerceAtLeast(0), right, (centerY + halfH).coerceAtMost(screenHeightPx.roundToInt()))
            }
            runCatching { hostView.systemGestureExclusionRects = rects }
        }
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching { hostView.systemGestureExclusionRects = emptyList() }
            }
        }
    }

    val hudBg = if (palette.isDark) Color(0xFF1E222B).copy(alpha = 0.96f) else Color.White.copy(alpha = 0.96f)
    val hudTextPrimary = if (palette.isDark) Color(0xFFF5F7FA) else Color(0xFF1F2329)
    val hudTextSecondary = if (palette.isDark) Color(0xFFA8B0BE) else Color(0xFF606975)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.22f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* Absorb stray background taps while adjusting trigger zone */ }
    ) {
        if (!isBallMode) {
            val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f) }
            Canvas(Modifier.fillMaxSize()) {
                geometries.forEach { g ->
                    if (!g.isHorizontalEdge) {
                        val topY = g.yPx
                        val bottomY = g.yPx + g.heightPx
                        drawLine(
                            color = palette.accent.copy(alpha = 0.45f),
                            start = Offset(0f, topY),
                            end = Offset(size.width, topY),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = dashEffect
                        )
                        drawLine(
                            color = palette.accent.copy(alpha = 0.45f),
                            start = Offset(0f, bottomY),
                            end = Offset(size.width, bottomY),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = dashEffect
                        )
                    }
                    drawRoundRect(
                        color = palette.accent.copy(alpha = 0.48f),
                        topLeft = Offset(g.xPx, g.yPx),
                        size = Size(g.widthPx, g.heightPx),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                    drawRoundRect(
                        color = palette.accent,
                        topLeft = Offset(g.xPx + 1.dp.toPx(), g.yPx + 1.dp.toPx()),
                        size = Size((g.widthPx - 2.dp.toPx()).coerceAtLeast(2f), (g.heightPx - 2.dp.toPx()).coerceAtLeast(2f)),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                }
            }

            // Primary handle side: show handle on every active edge bar (for BOTH, show on preferred active side + subtle twin on other side)
            val primaryHandleSide = when {
                triggerConfig.triggerPosition == TriggerPosition.BOTH -> if (activeRightSide) TriggerPosition.RIGHT else TriggerPosition.LEFT
                else -> triggerConfig.triggerPosition
            }

            geometries.forEach { g ->
                // 1) Direct bar touch strip along the edge itself
                val stripWidthPx = g.widthPx.coerceAtLeast(with(density) { 36.dp.toPx() })
                val stripHeightPx = g.heightPx.coerceAtLeast(with(density) { 48.dp.toPx() })
                val stripX = if (g.position == TriggerPosition.RIGHT) {
                    (screenWidthPx - stripWidthPx).coerceAtLeast(0f)
                } else {
                    g.xPx
                }
                val stripY = (g.yPx + (g.heightPx - stripHeightPx) / 2f).coerceIn(0f, (screenHeightPx - stripHeightPx).coerceAtLeast(0f))
                Box(
                    modifier = Modifier
                        .offset { IntOffset(stripX.roundToInt(), stripY.roundToInt()) }
                        .size(with(density) { stripWidthPx.toDp() }, with(density) { stripHeightPx.toDp() })
                        .triggerAxisDragGesture(
                            config = triggerConfig,
                            movableSpanPx = g.movableSpanPx,
                            horizontal = g.isHorizontalEdge,
                            onUpdateTrigger = onUpdateTrigger
                        )
                        .semantics { contentDescription = "触发条边缘拖动区" }
                )

                // 2) Prominent inward-offset handle pill (immune to system edge-back gesture)
                if (g.position == primaryHandleSide) {
                    var pillSize by remember { mutableStateOf(IntSize.Zero) }
                    val pillW = pillSize.width.takeIf { it > 0 }?.toFloat() ?: with(density) { 148.dp.toPx() }
                    val pillH = pillSize.height.takeIf { it > 0 }?.toFloat() ?: with(density) { 48.dp.toPx() }
                    val edgeClearancePx = maxOf(g.widthPx + with(density) { 10.dp.toPx() }, with(density) { 24.dp.toPx() })
                    val pillX = when (g.position) {
                        TriggerPosition.LEFT -> edgeClearancePx.coerceAtMost((screenWidthPx - pillW - 8f).coerceAtLeast(0f))
                        TriggerPosition.RIGHT -> (screenWidthPx - pillW - edgeClearancePx).coerceAtLeast(8f)
                        else -> (g.xPx + (g.widthPx - pillW) / 2f).coerceIn(8f, (screenWidthPx - pillW - 8f).coerceAtLeast(8f))
                    }
                    val topSafePx = with(density) { 64.dp.toPx() }
                    val bottomSafePx = with(density) { 210.dp.toPx() }
                    val pillY = when (g.position) {
                        TriggerPosition.TOP -> (g.heightPx + with(density) { 12.dp.toPx() }).coerceAtMost((screenHeightPx - pillH).coerceAtLeast(0f))
                        TriggerPosition.BOTTOM -> (screenHeightPx - g.heightPx - pillH - with(density) { 12.dp.toPx() }).coerceAtLeast(0f)
                        else -> (g.yPx + (g.heightPx - pillH) / 2f).coerceIn(
                            topSafePx.coerceAtMost((screenHeightPx - pillH).coerceAtLeast(0f)),
                            (screenHeightPx - bottomSafePx - pillH).coerceAtLeast(topSafePx)
                        )
                    }
                    Surface(
                        modifier = Modifier
                            .offset { IntOffset(pillX.roundToInt(), pillY.roundToInt()) }
                            .heightIn(min = 48.dp)
                            .onSizeChanged { pillSize = it }
                            .triggerAxisDragGesture(
                                config = triggerConfig,
                                movableSpanPx = g.movableSpanPx,
                                horizontal = g.isHorizontalEdge,
                                onUpdateTrigger = onUpdateTrigger
                            )
                            .semantics { contentDescription = "上下拖动触发区域手柄" },
                        shape = RoundedCornerShape(24.dp),
                        color = palette.accent,
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (g.isHorizontalEdge) "↔ 左右拖动" else "↕ 上下拖动",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White.copy(alpha = 0.22f)
                            ) {
                                Text(
                                    text = "${triggerConfig.touchOffsetPercent}%",
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Suspension Ball 2D/vertical drag preview
            val ballDiameterDp = (suspensionBallConfig.radiusDp.coerceIn(14, 48) * 2).dp
            val ballPx = with(density) { ballDiameterDp.toPx() }.coerceAtLeast(48f)
            val spanX = (screenWidthPx - ballPx).coerceAtLeast(1f)
            val spanY = (screenHeightPx - ballPx).coerceAtLeast(1f)
            val ballX = spanX * suspensionBallConfig.posXPercent.coerceIn(0f, 1f)
            val ballY = spanY * suspensionBallConfig.posYPercent.coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .offset { IntOffset(ballX.roundToInt(), ballY.roundToInt()) }
                    .size(ballDiameterDp.coerceAtLeast(48.dp))
                    .suspensionBallDragGesture(
                        ballConfig = suspensionBallConfig,
                        spanX = spanX,
                        spanY = spanY,
                        onUpdateBall = onUpdateSuspensionBall
                    )
                    .semantics { contentDescription = "拖动悬浮球位置" },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    color = palette.accent,
                    border = BorderStroke(2.dp, Color.White),
                    shadowElevation = 6.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${(suspensionBallConfig.posYPercent * 100).roundToInt()}%",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Bottom HUD Card: exact % display, quick presets, side switcher, height steppers, and Done
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .widthIn(max = 400.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = hudBg,
            border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.28f)),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (isBallMode) "拖动调整悬浮球上下位置" else "拖动调整触发区域上下位置",
                            color = hudTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isBallMode) "按住蓝色悬浮球任意拖动位置"
                            else if (triggerConfig.touchHeightPercent >= 100) "当前长度为 100% 占满全屏，请先减小区域长度再上下拖动"
                            else "按住侧边「↕ 上下拖动」胶囊或蓝色边缘条直接上下移动",
                            color = if (!isBallMode && triggerConfig.touchHeightPercent >= 100) Color(0xFFF59E0B) else hudTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = palette.accent.copy(alpha = 0.15f)
                    ) {
                        val pctText = if (isBallMode) {
                            "Y ${(suspensionBallConfig.posYPercent * 100).roundToInt()}%"
                        } else {
                            "位置 ${triggerConfig.touchOffsetPercent}%"
                        }
                        Text(
                            text = pctText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = palette.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (!isBallMode) {
                    // Side quick switcher + vertical preset jumps
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("侧边", color = hudTextSecondary, fontSize = 11.sp)
                        listOf(
                            TriggerPosition.LEFT to "左侧",
                            TriggerPosition.RIGHT to "右侧",
                            TriggerPosition.BOTH to "双侧"
                        ).forEach { (pos, label) ->
                            val selected = triggerConfig.triggerPosition == pos
                            Surface(
                                onClick = { onUpdateTrigger { it.withPosition(pos) } },
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) palette.accent else palette.accent.copy(alpha = 0.08f)
                            ) {
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) Color.White else hudTextPrimary
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        listOf(25 to "偏上", 50 to "居中", 75 to "偏下").forEach { (preset, label) ->
                            val activePreset = triggerConfig.touchOffsetPercent in (preset - 3)..(preset + 3)
                            Surface(
                                onClick = { onUpdateTrigger { it.withTouchGeometry(offsetPercent = preset) } },
                                shape = RoundedCornerShape(8.dp),
                                color = if (activePreset) palette.accent.copy(alpha = 0.18f) else palette.accent.copy(alpha = 0.06f)
                            ) {
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    fontSize = 11.sp,
                                    fontWeight = if (activePreset) FontWeight.Bold else FontWeight.Normal,
                                    color = if (activePreset) palette.accent else hudTextPrimary
                                )
                            }
                        }
                    }

                    // Trigger zone length stepper + Done button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("区域长度 ${triggerConfig.touchHeightPercent}%", color = hudTextSecondary, fontSize = 12.sp)
                        OutlinedButton(
                            onClick = {
                                onUpdateTrigger { it.withTouchGeometry(heightPercent = (it.touchHeightPercent - 5).coerceIn(5, 100)) }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(34.dp)
                        ) { Text("-5%", fontSize = 11.sp) }
                        OutlinedButton(
                            onClick = {
                                onUpdateTrigger { it.withTouchGeometry(heightPercent = (it.touchHeightPercent + 5).coerceIn(5, 100)) }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(34.dp)
                        ) { Text("+5%", fontSize = 11.sp) }
                        Spacer(Modifier.weight(1f))
                        FilledTonalButton(
                            onClick = onDone,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = palette.accent,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("完成触发区调整", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = onDone,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = palette.accent,
                                contentColor = Color.White
                            )
                        ) {
                            Text("完成位置调整", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Modifier.triggerAxisDragGesture(
    config: TriggerConfig,
    movableSpanPx: Float,
    horizontal: Boolean,
    onUpdateTrigger: ((TriggerConfig) -> TriggerConfig) -> Unit
): Modifier {
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val latestConfig by rememberUpdatedState(config)
    val latestSpan by rememberUpdatedState(movableSpanPx)
    val latestUpdate by rememberUpdatedState(onUpdateTrigger)
    val ownership = LocalPanelGestureOwnership.current
    val owner = remember { Any() }
    DisposableEffect(ownership, owner) { onDispose { ownership.release(owner) } }
    return onGloballyPositioned { coordinates = it }.pointerInput(horizontal, ownership) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true)
            val originRoot = coordinates?.takeIf { it.isAttached }?.localToRoot(down.position) ?: return@awaitEachGesture
            val startOffset = latestConfig.touchOffsetPercent
            val span = latestSpan
            down.consume()
            ownership.acquire(owner)
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val point = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!point.pressed) {
                        point.consume()
                        break
                    }
                    val currentRoot = coordinates?.takeIf { it.isAttached }?.localToRoot(point.position) ?: break
                    val delta = if (horizontal) currentRoot.x - originRoot.x else currentRoot.y - originRoot.y
                    point.consume()
                    val nextPct = draggedTriggerOffsetPercent(startOffset, delta, span)
                    if (nextPct != latestConfig.touchOffsetPercent) {
                        latestUpdate { it.withTouchGeometry(offsetPercent = nextPct) }
                    }
                }
            } finally {
                ownership.release(owner)
            }
        }
    }
}

@Composable
private fun Modifier.suspensionBallDragGesture(
    ballConfig: SuspensionBallConfig,
    spanX: Float,
    spanY: Float,
    onUpdateBall: ((SuspensionBallConfig) -> SuspensionBallConfig) -> Unit
): Modifier {
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val latestBall by rememberUpdatedState(ballConfig)
    val latestSpanX by rememberUpdatedState(spanX)
    val latestSpanY by rememberUpdatedState(spanY)
    val latestUpdate by rememberUpdatedState(onUpdateBall)
    val ownership = LocalPanelGestureOwnership.current
    val owner = remember { Any() }
    DisposableEffect(ownership, owner) { onDispose { ownership.release(owner) } }
    return onGloballyPositioned { coordinates = it }.pointerInput(ownership) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true)
            val originRoot = coordinates?.takeIf { it.isAttached }?.localToRoot(down.position) ?: return@awaitEachGesture
            val startX = latestBall.posXPercent
            val startY = latestBall.posYPercent
            down.consume()
            ownership.acquire(owner)
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val point = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!point.pressed) {
                        point.consume()
                        break
                    }
                    val currentRoot = coordinates?.takeIf { it.isAttached }?.localToRoot(point.position) ?: break
                    val delta = currentRoot - originRoot
                    point.consume()
                    val nextX = (startX + delta.x / latestSpanX.coerceAtLeast(1f)).coerceIn(0f, 1f)
                    val nextY = (startY + delta.y / latestSpanY.coerceAtLeast(1f)).coerceIn(0f, 1f)
                    latestUpdate { it.copy(posXPercent = nextX, posYPercent = nextY) }
                }
            } finally {
                ownership.release(owner)
            }
        }
    }
}
