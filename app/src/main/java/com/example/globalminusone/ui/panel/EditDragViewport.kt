package com.example.globalminusone.ui.panel

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive

/** A pointer stays in screen coordinates while its canvas scrolls underneath it. */
internal class EditDragViewport {
    var bounds by mutableStateOf(Rect.Zero)
    var pointer by mutableStateOf<Offset?>(null)
    var consumedScroll by mutableFloatStateOf(0f)
    fun begin(point: Offset) { pointer = point }
    fun moveBy(delta: Offset) { pointer = pointer?.plus(delta) }
    fun end() { pointer = null }
}

internal val LocalEditDragViewport = staticCompositionLocalOf<EditDragViewport?> { null }

/** Gradual acceleration at the edges, capped in density-independent pixels per second. */
internal fun dragEdgeVelocity(y: Float, height: Float, edge: Float, maximum: Float): Float {
    if (!y.isFinite() || height <= 0f || edge <= 0f) return 0f
    val zone = edge.coerceAtMost(height / 3f)
    val amount = when {
        y < zone -> -((zone - y) / zone).coerceIn(0f, 1f)
        y > height - zone -> ((y - height + zone) / zone).coerceIn(0f, 1f)
        else -> 0f
    }
    return amount * kotlin.math.abs(amount) * maximum
}

@Composable
internal fun rememberEditDragViewport(
    scrollBy: suspend (Float) -> Float,
    growDown: (Float) -> Unit = {}
): EditDragViewport {
    val viewport = remember { EditDragViewport() }
    val currentScroll by rememberUpdatedState(scrollBy)
    val currentGrow by rememberUpdatedState(growDown)
    val density = LocalDensity.current
    val edge = with(density) { 64.dp.toPx() }
    val speed = with(density) { 620.dp.toPx() }
    LaunchedEffect(viewport.pointer != null) {
        var previous = 0L
        while (isActive && viewport.pointer != null) {
            withFrameNanos { now ->
                // Timestamp only. Suspending scroll mutations happen outside the frame callback.
                if (previous == 0L) previous = now
                now
            }.let { now ->
                val seconds = ((now - previous) / 1_000_000_000f).coerceIn(0f, .032f)
                previous = now
                val pointer = viewport.pointer ?: return@let
                val velocity = dragEdgeVelocity(pointer.y - viewport.bounds.top, viewport.bounds.height, edge, speed)
                val delta = velocity * seconds
                if (delta > 0f) currentGrow(delta)
                if (delta != 0f) viewport.consumedScroll += currentScroll(delta)
            }
        }
    }
    DisposableEffect(viewport) { onDispose { viewport.end() } }
    return viewport
}
