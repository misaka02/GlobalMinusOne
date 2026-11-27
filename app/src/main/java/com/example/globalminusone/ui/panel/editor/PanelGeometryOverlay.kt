package com.example.globalminusone.ui.panel.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.policy.PanelBounds
import com.example.globalminusone.data.ThemeConfig
import kotlin.math.roundToInt
import com.example.globalminusone.ui.panel.gesture.LocalPanelGestureOwnership
import com.example.globalminusone.ui.panel.LocalPanelPalette

/** Only the five explicit handles participate in hit testing; the border never covers card input. */
@Composable
internal fun PanelGeometryOverlay(theme: ThemeConfig, bounds: PanelBounds, screenWidth: Float, screenHeight: Float,
    right: Boolean, onPreview: (ThemeConfig) -> Unit, onCommit: (ThemeConfig) -> Unit, onCancel: () -> Unit) {
    val density = LocalDensity.current
    val palette = LocalPanelPalette.current
    val hit = with(density) { 48.dp.toPx() }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(palette.accent, Offset(bounds.x + 1.dp.toPx(), bounds.y + 1.dp.toPx()),
                androidx.compose.ui.geometry.Size((bounds.width - 2.dp.toPx()).coerceAtLeast(0f),
                    (bounds.height - 2.dp.toPx()).coerceAtLeast(0f)), style = Stroke(2.dp.toPx()))
        }
        listOf(PanelGeometryHandle.TOP_LEFT, PanelGeometryHandle.TOP_RIGHT,
            PanelGeometryHandle.BOTTOM_LEFT, PanelGeometryHandle.BOTTOM_RIGHT, PanelGeometryHandle.MOVE).forEach { handle ->
            val left = handle == PanelGeometryHandle.TOP_LEFT || handle == PanelGeometryHandle.BOTTOM_LEFT
            val top = handle == PanelGeometryHandle.TOP_LEFT || handle == PanelGeometryHandle.TOP_RIGHT
            val moving = handle == PanelGeometryHandle.MOVE
            val x = if (moving || left) bounds.x else bounds.x + bounds.width - hit
            val y = if (moving) bounds.y + (bounds.height - hit) / 2 else if (top) bounds.y else bounds.y + bounds.height - hit
            val label = when (handle) {
                PanelGeometryHandle.MOVE -> "移动面板"
                PanelGeometryHandle.TOP_LEFT -> "缩放面板左上角"
                PanelGeometryHandle.TOP_RIGHT -> "缩放面板右上角"
                PanelGeometryHandle.BOTTOM_LEFT -> "缩放面板左下角"
                PanelGeometryHandle.BOTTOM_RIGHT -> "缩放面板右下角"
            }
            Box(Modifier.offset { IntOffset(x.coerceIn(0f, (screenWidth - hit).coerceAtLeast(0f)).roundToInt(),
                y.coerceIn(0f, (screenHeight - hit).coerceAtLeast(0f)).roundToInt()) }
                .size(48.dp).geometryDragHandle(handle, theme, screenWidth, screenHeight, right, onPreview, onCommit, onCancel)
                .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
                Box(Modifier.size(if (moving) 38.dp else 24.dp).background(palette.accent, RoundedCornerShape(if (moving) 10.dp else 7.dp)),
                    contentAlignment = Alignment.Center) {
                    Text(if (moving) "移动" else when (handle) {
                        PanelGeometryHandle.TOP_LEFT -> "↖"; PanelGeometryHandle.TOP_RIGHT -> "↗"
                        PanelGeometryHandle.BOTTOM_LEFT -> "↙"; else -> "↘"
                    }, color = androidx.compose.ui.graphics.Color.White, fontSize = if (moving) 11.sp else 16.sp)
                }
            }
        }
    }
}

@Composable
private fun Modifier.geometryDragHandle(handle: PanelGeometryHandle, theme: ThemeConfig, width: Float, height: Float,
    right: Boolean, onPreview: (ThemeConfig) -> Unit, onCommit: (ThemeConfig) -> Unit, onCancel: () -> Unit): Modifier {
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val current by rememberUpdatedState(theme)
    val preview by rememberUpdatedState(onPreview)
    val commit by rememberUpdatedState(onCommit)
    val cancel by rememberUpdatedState(onCancel)
    val ownership = LocalPanelGestureOwnership.current
    val owner = remember { Any() }
    DisposableEffect(ownership, owner) { onDispose { ownership.release(owner) } }
    return onGloballyPositioned { coordinates = it }.pointerInput(handle, width, height, right, ownership) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true)
            val origin = coordinates?.takeIf { it.isAttached }?.localToRoot(down.position) ?: return@awaitEachGesture
            val gesture = PanelGeometryGesture(current, width, height, right, handle)
            var moved = false
            var ended = false
            down.consume()
            ownership.acquire(owner)
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val point = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (event.changes.count { it.pressed } > 1 || point.isConsumed) break
                    val position = coordinates?.takeIf { it.isAttached }?.localToRoot(point.position) ?: break
                    val delta = position - origin
                    if (!point.pressed) {
                        point.consume()
                        val result = gesture.finish(cancelled = false)
                        ended = true
                        if (result?.persist == true) commit(result.theme) else cancel()
                        break
                    }
                    point.consume()
                    if (delta.getDistance() > viewConfiguration.touchSlop) moved = true
                    if (moved) gesture.update(delta.x, delta.y)?.let { preview(it) }
                }
            } finally {
                if (!ended) { gesture.finish(cancelled = true); cancel() }
                ownership.release(owner)
            }
        }
    }
}
