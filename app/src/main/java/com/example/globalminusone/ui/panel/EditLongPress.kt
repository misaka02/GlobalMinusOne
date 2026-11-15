package com.example.globalminusone.ui.panel

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp

/** Observe consumed scrolling too, using both positions from the same layout event. */
internal fun PointerInputChange.movementForHold(): Offset = position - previousPosition

/** Observe child taps, but a hold must remain stationary even when a child consumes scrolling.
 * Set [requireIdleOwnership] to false when an interactive card (e.g. a slider) acquires axis lock on DOWN. */
@Composable
internal fun Modifier.cardLongPressToEdit(
    key: Any,
    enabled: Boolean,
    ignoreOwnedGesture: Boolean = false,
    requireIdleOwnership: Boolean = true,
    onEdit: () -> Unit
): Modifier {
    val active by rememberUpdatedState(enabled)
    val edit by rememberUpdatedState(onEdit)
    val ownership = LocalPanelGestureOwnership.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val coordinates = remember(key) { arrayOfNulls<LayoutCoordinates>(1) }
    // A disabled descendant must not register a competing observer or reserve this DOWN.
    if (!enabled) return this
    return onGloballyPositioned { coordinates[0] = it }.pointerInput(key, ownership, enabled, ignoreOwnedGesture, requireIdleOwnership) {
        val owner = Any()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
            val rootPoint = coordinates[0]?.takeIf { it.isAttached }?.localToRoot(down.position)
            val blockedByOwnership = ignoreOwnedGesture && (ownership.active || rootPoint?.let(ownership::hitsCard) == true)
            val claimed = if (requireIdleOwnership) {
                ownership.claimEditHold(down.uptimeMillis, owner, active)
            } else {
                ownership.claimHold(down.uptimeMillis, owner, active)
            }
            if (!active || blockedByOwnership || !claimed) return@awaitEachGesture
            try {
            // Permit small finger/sensor jitter; an intentional swipe or consumed
            // scroll still cancels immediately, even if it later returns here.
            val policy = StationaryHoldPolicy(3.dp.toPx(), 12.dp.toPx())
            val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val point = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                    val delta = point.movementForHold()
                    if (!policy.observe(delta.x, delta.y, event.changes.count { it.pressed }, point.pressed,
                            point.isConsumed && delta != Offset.Zero)) return@withTimeoutOrNull true
                }
            }
            if (ended == null && !policy.cancelled && active) {
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                edit()
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
            }
            } finally { ownership.releaseHold(owner) }
        }
    }
}

/** Reserve only the hold while waiting; scroll exclusion starts at explicit confirmation.
 * The same finally covers early returns, cancellation and callback exceptions. */
internal inline fun <T> withStationaryDragHold(
    ownership: PanelGestureOwnership,
    epoch: Long,
    block: (confirmHold: () -> Unit) -> T
): T? {
    val owner = Any()
    if (!ownership.claimHold(epoch, owner)) return null
    try {
        return block { ownership.acquire(owner) }
    } finally {
        ownership.release(owner)
        ownership.releaseHold(owner)
    }
}

/** Editing starts dragging only after the same stationary hold as entering edit. */
internal suspend fun PointerInputScope.detectStationaryDragGestures(
    ownership: PanelGestureOwnership,
    onTap: () -> Unit = {},
    onDragStart: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onStationaryLongPress: (() -> Unit)? = null,
    onLongPressPreview: (() -> Unit)? = null,
    onLongPressCancel: (() -> Unit)? = null,
    positionInStableSpace: ((Offset) -> Offset)? = null,
    onDrag: (PointerInputChange, Offset) -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        // The same detector owns tap/hold/drag. A sibling tap detector must not consume
        // the DOWN or release while a parent background observer is waiting for a hold.
        withStationaryDragHold(ownership, down.uptimeMillis) { confirmHold ->
        val policy = StationaryHoldPolicy(3.dp.toPx(), 12.dp.toPx())
        var previous = down.position
        val motion = positionInStableSpace?.let { StableDragMotion(it(down.position)) }
        val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val point = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                val delta = if (motion != null && positionInStableSpace != null)
                    motion.sample(positionInStableSpace(point.position)) else point.movementForHold()
                previous = point.position
                if (!point.pressed) {
                    if (policy.observeRelease(delta.x, delta.y, event.changes.size, point.isConsumed)) onTap()
                    return@withTimeoutOrNull true
                }
                if (!policy.observe(delta.x, delta.y, event.changes.count { it.pressed }, point.pressed,
                        point.isConsumed && delta != Offset.Zero)) return@withTimeoutOrNull true
            }
        }
        if (ended != null || policy.cancelled) return@awaitEachGesture
        confirmHold()
        var completed = false
        var started = onStationaryLongPress == null
        var previewShown = false
        val heldMovement = HeldDragIntent(viewConfiguration.touchSlop)
        try {
            if (!started && onLongPressPreview != null) {
                previewShown = true
                onLongPressPreview()
            }
            if (started) onDragStart(previous)
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val point = event.changes.firstOrNull { it.id == down.id }
                if (point == null || event.changes.count { it.pressed } > 1) break
                val delta = if (motion != null && positionInStableSpace != null)
                    motion.sample(positionInStableSpace(point.position)) else point.positionChange()
                if (!point.pressed) {
                    // Compose represents ACTION_CANCEL as an already-consumed release.
                    if (isSuccessfulDragRelease(point.previousPressed, point.pressed, point.isConsumed)) {
                        if (started) {
                            if (delta != Offset.Zero) onDrag(point, delta)
                            onDragEnd()
                        } else onStationaryLongPress?.invoke()
                        completed = true
                    }
                    break
                }
                // The input node itself moves while reordering and edge-scrolling. Map the
                // current point before comparing it with the previous physical sample.
                point.consume()
                if (!started) {
                    if (heldMovement.move(delta.x, delta.y)) {
                        if (previewShown) { onLongPressCancel?.invoke(); previewShown = false }
                        started = true
                        onDragStart(point.position - Offset(heldMovement.x, heldMovement.y))
                        onDrag(point, Offset(heldMovement.x, heldMovement.y))
                    }
                } else onDrag(point, delta)
            }
        } finally {
            if (!completed && previewShown) onLongPressCancel?.invoke()
            if (!completed && started) onDragCancel()
        }
        }
    }
}

/** Movement intent after a stationary hold, independent of a card's screen position/height. */
internal class HeldDragIntent(private val slop: Float) {
    var x = 0f; private set
    var y = 0f; private set
    fun move(dx: Float, dy: Float): Boolean {
        if (dx.isFinite() && dy.isFinite()) { x += dx; y += dy }
        return x * x + y * y > slop.coerceAtLeast(0f) * slop.coerceAtLeast(0f)
    }
}
