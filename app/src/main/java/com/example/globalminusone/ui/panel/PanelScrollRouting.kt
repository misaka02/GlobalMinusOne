package com.example.globalminusone.ui.panel

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal class PanelScrollTarget(val state: ScrollableState, val parent: PanelScrollTarget? = null) {
    suspend fun stop() {
        state.stopScroll(MutatePriority.UserInput)
        parent?.stop()
    }
}
internal val LocalPanelScrollTarget = staticCompositionLocalOf<PanelScrollTarget?> { null }

/** Cancelling a child touch/disposal must also cancel any unfinished ancestor stop. */
internal class AncestorScrollInterruption(scope: CoroutineScope, parent: PanelScrollTarget) {
    private val pending = scope.launch(start = CoroutineStart.UNDISPATCHED) { parent.stop() }
    fun finish() { pending.cancel() }
}

@Composable
internal fun rememberPanelScrollTarget(state: ScrollableState): PanelScrollTarget =
    remember(state) { PanelScrollTarget(state) }

internal class ContainerScrollRoute(val layout: ScrollState, val target: PanelScrollTarget)

internal fun nativeContainerScrollRoute(layout: ScrollState, parent: PanelScrollTarget?) =
    ContainerScrollRoute(layout, PanelScrollTarget(layout, parent))

@Composable
internal fun rememberContainerScrollRoute(layout: ScrollState,
    parent: PanelScrollTarget? = LocalPanelScrollTarget.current): ContainerScrollRoute {
    // Keep the actual local ScrollState in the native nested-scroll chain.
    // Manually moving ancestors from consumeScrollDelta bypasses their scrolling
    // session while Compose also forwards post-scroll/post-fling to those nodes.
    return remember(layout, parent) { nativeContainerScrollRoute(layout, parent) }
}

/** One native scroll node handles distance, velocity and boundary handoff together.
 * No second scrollable or ancestor raw-delta bridge is mounted on this viewport. */
@Composable
internal fun Modifier.containerPanelScroll(route: ContainerScrollRoute, enabled: Boolean): Modifier {
    val parent = route.target.parent
    val gestureOwnership = LocalPanelGestureOwnership.current
    val input = pointerInput(parent, route.layout, enabled) {
        if (!enabled || parent == null) return@pointerInput
        coroutineScope {
            val pointerScope = this
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                // A pending stop must belong to this touch, not the composable's
                // lifetime: a delayed job could otherwise stop a later gesture.
                val stop = AncestorScrollInterruption(pointerScope, parent)
                try {
                    do { val event = awaitPointerEvent() } while (event.changes.any { it.pressed })
                } finally { stop.finish() }
            }
        }
    }
    return input.verticalScroll(
        route.layout,
        enabled = enabled && !gestureOwnership.exclusiveVerticalActive,
        flingBehavior = rememberPanelFlingBehavior()
    )
}

/** Child horizontal scrollers stay disabled unless their content actually overflows,
 * otherwise Compose's nested-scroll connection steals drag and zeros Pager post-fling velocity. */
internal fun shouldEnableChildHorizontalScroll(
    allowed: Boolean,
    maxValue: Int,
    exclusiveBlocked: Boolean = false
): Boolean = allowed && maxValue > 0 && !exclusiveBlocked
