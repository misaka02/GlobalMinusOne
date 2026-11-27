package com.example.globalminusone.ui.panel.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalView
import com.example.globalminusone.data.ComponentType

internal enum class PanelGestureAxis { Horizontal, Vertical, Both }

internal fun panelScrollAxisForComponent(
    item: com.example.globalminusone.data.ComponentItem,
    widthDp: Float = 0f,
    heightDp: Float = 0f
): PanelGestureAxis = if (com.example.globalminusone.data.policy.isSliderBarType(item.type)) {
    if (com.example.globalminusone.data.policy.resolveComponentVertical(item, widthDp, heightDp)) {
        PanelGestureAxis.Vertical
    } else {
        PanelGestureAxis.Horizontal
    }
} else {
    PanelGestureAxis.Both
}

/** Inner content only blocks outer scrolling on the axis it actually handles. */
internal class PanelGestureOwnership {
    private val cardBounds = mutableMapOf<Any, Rect>()
    private val cardCoordinates = mutableMapOf<Any, LayoutCoordinates>()
    fun recordCard(owner: Any, bounds: Rect) { cardBounds[owner] = bounds }
    fun recordCardCoordinates(owner: Any, coords: LayoutCoordinates) { cardCoordinates[owner] = coords }
    fun removeCard(owner: Any) {
        cardBounds.remove(owner)
        cardCoordinates.remove(owner)
    }
    fun hitsCard(point: Offset): Boolean =
        cardBounds.values.any { it.contains(point) } ||
            cardCoordinates.values.any { it.isAttached && it.boundsInRoot().contains(point) }
    private data class Claim(val axis: PanelGestureAxis, val blocksDescendants: Boolean)
    private var owners by mutableStateOf<Map<Any, Claim>>(emptyMap())
    val active: Boolean get() = owners.isNotEmpty()
    val horizontalActive: Boolean get() = owners.values.any { it.axis != PanelGestureAxis.Vertical }
    val verticalActive: Boolean get() = owners.values.any { it.axis != PanelGestureAxis.Horizontal }
    // Compose scrollables already pass unconsumed distance through nested scroll.
    // Only an exclusive control/drag may disable an ancestor recognizer mid-gesture.
    val exclusiveHorizontalActive: Boolean get() = owners.values.any {
        it.blocksDescendants && it.axis != PanelGestureAxis.Vertical
    }
    val exclusiveVerticalActive: Boolean get() = owners.values.any {
        it.blocksDescendants && it.axis != PanelGestureAxis.Horizontal
    }
    // A viewport's own scroll claim protects it from ancestors, not from itself.
    val descendantVerticalBlocked: Boolean get() = exclusiveVerticalActive
    private var holdEpoch = Long.MIN_VALUE
    private var holdOwner: Any? = null
    // Main pass visits the deepest child first. A background observer cannot claim its hold.
    fun claimHold(epoch: Long, owner: Any, enabled: Boolean = true): Boolean {
        if (!enabled) return false
        if (holdEpoch != epoch) { holdEpoch = epoch; holdOwner = owner }
        return holdOwner === owner
    }

    // Ordinary card editing must yield to a control already handling this touch.
    // Existing edit-mode drag holds may coexist with their nested viewport claim.
    fun claimEditHold(epoch: Long, owner: Any, enabled: Boolean = true): Boolean =
        !active && claimHold(epoch, owner, enabled)

    fun releaseHold(owner: Any) { if (holdOwner === owner) holdOwner = null }
    fun acquire(owner: Any, axis: PanelGestureAxis = PanelGestureAxis.Both, blocksDescendants: Boolean = true) {
        val claim = Claim(axis, blocksDescendants)
        if (owners[owner] != claim) owners = owners + (owner to claim)
    }
    fun release(owner: Any) { if (owner in owners) owners = owners - owner }
}

internal val LocalPanelGestureOwnership = staticCompositionLocalOf { PanelGestureOwnership() }
internal val LocalAddAboveComponent = staticCompositionLocalOf<((String) -> Unit)?> { null }

/** A drag on the scrim must never become a close, including an out-and-back drag or an UP-only jump. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun Modifier.strictPanelBackgroundTap(onTap: () -> Unit): Modifier {
    val action by rememberUpdatedState(onTap)
    val view = LocalView.current
    val coordinates = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    return onGloballyPositioned { coordinates[0] = it }
        .semantics { onClick { action(); true } }
        .pointerInput(view) {
            val screen = IntArray(2)
            val window = IntArray(2)
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Main)
                view.getLocationOnScreen(screen); view.getLocationInWindow(window)
                val windowToScreen = Offset((screen[0] - window[0]).toFloat(), (screen[1] - window[1]).toFloat())
                fun screenPoint(position: Offset): Offset {
                    val local = coordinates[0]?.takeIf { it.isAttached }?.localToWindow(position) ?: position
                    return local + windowToScreen
                }
                val origin = screenPoint(down.position)
                val policy = BackgroundTapPolicy(origin.x, origin.y, down.uptimeMillis,
                    viewConfiguration.touchSlop, viewConfiguration.longPressTimeoutMillis)
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        val point = event.changes.firstOrNull { it.id == down.id } ?: break
                        fun observe(position: Offset, uptimeMillis: Long) {
                            val stable = screenPoint(position)
                            policy.observe(stable.x, stable.y, uptimeMillis, event.changes.size,
                                consumed = point.isConsumed,
                                inBounds = position.x >= 0f && position.y >= 0f &&
                                    position.x < size.width && position.y < size.height)
                        }
                        // Android can batch MOVE samples; an out-and-back path within
                        // one delivered event must not disappear into its final position.
                        point.historical.forEach { observe(it.position, it.uptimeMillis) }
                        observe(point.position, point.uptimeMillis)
                        if (!point.pressed) {
                            if (event.type == PointerEventType.Release && !policy.cancelled) {
                                point.consume()
                                action()
                            }
                            break
                        }
                        if (policy.cancelled) break
                    }
                } finally { policy.cancel() }
            }
        }
}

internal object PanelEditorBackHandler {
    var handler: (() -> Boolean)? = null
    fun handleBack() = handler?.invoke() == true
}

@Composable
internal fun Modifier.panelCardBounds(id: String): Modifier {
    val ownership = LocalPanelGestureOwnership.current
    val token = remember(id) { Any() }
    DisposableEffect(ownership, token) { onDispose { ownership.removeCard(token) } }
    return onGloballyPositioned { ownership.recordCardCoordinates(token, it) }
}

private val scrollOwners = setOf(
    // Drawers and containers register their actual ScrollState at their viewport.
    ComponentType.RECENT_APPS_CARD,
    ComponentType.NOTE_CARD, ComponentType.CLIPBOARD_CARD, ComponentType.APP_WIDGET,
    ComponentType.VOLUME_BAR, ComponentType.BRIGHTNESS_BAR
)
internal fun ComponentType.ownsPanelScrolling(): Boolean = this in scrollOwners

@Composable
internal fun Modifier.ownPanelScrollGestures(enabled: Boolean, lockFromDown: Boolean = false,
    canScrollVertically: ((Float) -> Boolean)? = null,
    axis: PanelGestureAxis = PanelGestureAxis.Both): Modifier {
    val ownership = LocalPanelGestureOwnership.current
    val owner = remember { Any() }
    val canScroll by rememberUpdatedState(canScrollVertically)
    DisposableEffect(ownership, owner, enabled) { onDispose { ownership.release(owner) } }
    // Keep the node's enabled transition explicit. A keyed restart cancels
    // the old detector and releases its claim even when Compose reuses nodes.
    return pointerInput(ownership, owner, enabled, lockFromDown, axis) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (lockFromDown) ownership.acquire(owner, axis)
            var accumulated = Offset.Zero
            try {
                do {
                    // Main visits children before this wrapper and outer page scrollables
                    // afterwards, so only child consumption can establish a fallback claim.
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val point = event.changes.firstOrNull { it.id == down.id }
                    if (point != null && !lockFromDown) {
                        val delta = point.position - point.previousPosition
                        accumulated += delta
                        val scrollCheck = canScroll
                        if (scrollCheck != null) {
                            val internal = ownsVerticalScroll(accumulated.x, accumulated.y,
                                viewConfiguration.touchSlop, scrollCheck(delta.y))
                            if (internal && point.pressed) ownership.acquire(owner, PanelGestureAxis.Vertical, blocksDescendants = false)
                            else ownership.release(owner)
                        } else if (point.pressed && point.isConsumed && delta != Offset.Zero) {
                            val claimedAxis = if (kotlin.math.abs(accumulated.x) > kotlin.math.abs(accumulated.y))
                                PanelGestureAxis.Horizontal else PanelGestureAxis.Vertical
                            ownership.acquire(owner, claimedAxis, blocksDescendants = false)
                        }
                    }
                } while (event.changes.any { it.pressed })
            } finally { ownership.release(owner) }
        }
    }
}

internal fun ownsVerticalScroll(dx: Float, dy: Float, slop: Float, canScroll: Boolean): Boolean =
    canScroll && kotlin.math.abs(dy) > slop && kotlin.math.abs(dy) > kotlin.math.abs(dx)
