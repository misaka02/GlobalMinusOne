package com.example.globalminusone.ui.panel.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.globalminusone.data.policy.KeyboardCardColorTokens
import com.example.globalminusone.data.TriggerSide
import com.example.globalminusone.data.policy.resolveEffectiveKeyTap
import kotlin.math.roundToInt

internal data class SlidePickerKeySpec(
    val id: String,
    val displayLabel: String,
    val emitChar: Char,
    val rowKeys: List<Char>? = null,
    val useReachableSnapOnTap: Boolean = false
)

internal data class SlidePickerKeyBounds(
    val spec: SlidePickerKeySpec,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val centerX: Float get() = (left + right) * 0.5f

    fun contains(x: Float, y: Float): Boolean =
        x >= left && x <= right && y >= top && y <= bottom

    fun distanceSqTo(x: Float, y: Float): Float {
        val clampedX = x.coerceIn(left, right)
        val clampedY = y.coerceIn(top, bottom)
        val dx = x - clampedX
        val dy = y - clampedY
        return dx * dx + dy * dy
    }
}

internal fun resolveSlidePickerHit(
    x: Float,
    y: Float,
    keys: List<SlidePickerKeyBounds>,
    gapSnapPx: Float
): SlidePickerKeyBounds? {
    if (keys.isEmpty()) return null
    keys.firstOrNull { it.contains(x, y) }?.let { return it }
    val maxDistSq = gapSnapPx.coerceAtLeast(0f).let { it * it }
    var best: SlidePickerKeyBounds? = null
    var bestDistSq = Float.MAX_VALUE
    for (candidate in keys) {
        val distSq = candidate.distanceSqTo(x, y)
        if (distSq <= maxDistSq && distSq < bestDistSq) {
            bestDistSq = distSq
            best = candidate
        }
    }
    return best
}

internal fun resolveSlidePickerCommitChar(
    hovered: SlidePickerKeySpec,
    didSlideAcrossKeys: Boolean,
    reachableKeys: Set<Char>?,
    activeSide: TriggerSide
): Char {
    if (!didSlideAcrossKeys &&
        hovered.useReachableSnapOnTap &&
        hovered.rowKeys != null &&
        reachableKeys != null
    ) {
        val resolved = resolveEffectiveKeyTap(hovered.rowKeys, hovered.emitChar, reachableKeys, activeSide)
        return if (hovered.emitChar.isLowerCase()) resolved.lowercaseChar() else resolved
    }
    return hovered.emitChar
}

internal class KeyboardSlidePickerState {
    private var containerCoords: LayoutCoordinates? = null
    private val registeredCoords = mutableMapOf<String, Pair<SlidePickerKeySpec, LayoutCoordinates>>()
    private val keyBounds = mutableMapOf<String, SlidePickerKeyBounds>()

    var containerWidthPx by mutableIntStateOf(0)
        private set

    var hoveredKey by mutableStateOf<SlidePickerKeyBounds?>(null)

    fun updateContainer(coords: LayoutCoordinates) {
        containerCoords = coords
        containerWidthPx = coords.size.width
        recomputeAllBounds()
    }

    fun registerKey(spec: SlidePickerKeySpec, coords: LayoutCoordinates) {
        registeredCoords[spec.id] = spec to coords
        updateSingleBound(spec, coords)
    }

    fun unregisterKey(id: String) {
        registeredCoords.remove(id)
        keyBounds.remove(id)
        if (hoveredKey?.spec?.id == id) {
            hoveredKey = null
        }
    }

    fun hitTest(x: Float, y: Float, gapSnapPx: Float): SlidePickerKeyBounds? =
        resolveSlidePickerHit(x, y, keyBounds.values.toList(), gapSnapPx)

    private fun recomputeAllBounds() {
        val container = containerCoords?.takeIf { it.isAttached } ?: return
        val iterator = registeredCoords.entries.iterator()
        while (iterator.hasNext()) {
            val (id, entry) = iterator.next()
            val (spec, coords) = entry
            if (!coords.isAttached) {
                keyBounds.remove(id)
                continue
            }
            val box = container.localBoundingBoxOf(coords, clipBounds = false)
            keyBounds[id] = SlidePickerKeyBounds(spec, box.left, box.top, box.right, box.bottom)
        }
    }

    private fun updateSingleBound(spec: SlidePickerKeySpec, coords: LayoutCoordinates) {
        val container = containerCoords?.takeIf { it.isAttached } ?: return
        if (!coords.isAttached) return
        val box = container.localBoundingBoxOf(coords, clipBounds = false)
        keyBounds[spec.id] = SlidePickerKeyBounds(spec, box.left, box.top, box.right, box.bottom)
    }
}

@Composable
internal fun Modifier.keyboardSlideKeyTarget(
    state: KeyboardSlidePickerState,
    spec: SlidePickerKeySpec
): Modifier {
    val latestSpec by rememberUpdatedState(spec)
    DisposableEffect(state, spec.id) {
        onDispose { state.unregisterKey(spec.id) }
    }
    return onGloballyPositioned { coords ->
        state.registerKey(latestSpec, coords)
    }
}

@Composable
internal fun Modifier.keyboardSlidePickerGestures(
    state: KeyboardSlidePickerState,
    interactive: Boolean,
    reachableKeys: Set<Char>?,
    activeSide: TriggerSide,
    onCommitChar: (Char) -> Unit
): Modifier {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val latestReachable by rememberUpdatedState(reachableKeys)
    val latestSide by rememberUpdatedState(activeSide)
    val latestOnCommit by rememberUpdatedState(onCommitChar)
    val gapSnapPx = with(density) { 4.dp.toPx() }

    return pointerInput(state, interactive, gapSnapPx) {
        if (!interactive) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val initialHit = state.hitTest(down.position.x, down.position.y, gapSnapPx)
                ?: return@awaitEachGesture
            down.consume()
            state.hoveredKey = initialHit
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

            var didCrossKey = false
            var currentHit: SlidePickerKeyBounds? = initialHit
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val pointer = event.changes.firstOrNull { it.id == down.id }
                        ?: event.changes.firstOrNull()
                        ?: break
                    if (!pointer.pressed) {
                        if (event.type == PointerEventType.Release && !pointer.isConsumed) {
                            pointer.consume()
                            currentHit?.let { chosen ->
                                val emitted = resolveSlidePickerCommitChar(
                                    hovered = chosen.spec,
                                    didSlideAcrossKeys = didCrossKey,
                                    reachableKeys = latestReachable,
                                    activeSide = latestSide
                                )
                                latestOnCommit(emitted)
                            }
                        }
                        break
                    }
                    pointer.consume()
                    val nextHit = state.hitTest(pointer.position.x, pointer.position.y, gapSnapPx)
                    if (nextHit?.spec?.id != currentHit?.spec?.id) {
                        if (nextHit != null && nextHit.spec.id != initialHit.spec.id) {
                            didCrossKey = true
                        }
                        if (nextHit != null) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        currentHit = nextHit
                        state.hoveredKey = nextHit
                    }
                }
            } finally {
                state.hoveredKey = null
            }
        }
    }
}

@Composable
internal fun KeyboardSlidePreviewBubble(
    hovered: SlidePickerKeyBounds?,
    containerWidthPx: Int,
    kbdColors: KeyboardCardColorTokens,
    accentColor: Color
) {
    if (hovered == null) return
    val density = LocalDensity.current
    var bubbleWidthPx by rememberIntStateZero()
    var bubbleHeightPx by rememberIntStateZero()
    val liftGapPx = with(density) { 6.dp.toPx() }
    val estW = if (bubbleWidthPx > 0) bubbleWidthPx else with(density) { 36.dp.roundToPx() }
    val estH = if (bubbleHeightPx > 0) bubbleHeightPx else with(density) { 36.dp.roundToPx() }
    val maxLeft = (containerWidthPx - estW).coerceAtLeast(0)
    val leftPx = (hovered.centerX - estW * 0.5f).roundToInt().coerceIn(0, maxLeft)
    val topPx = (hovered.top - estH - liftGapPx).roundToInt()

    Box(
        modifier = Modifier
            .zIndex(12f)
            .offset { IntOffset(leftPx, topPx) }
            .onSizeChanged {
                bubbleWidthPx = it.width
                bubbleHeightPx = it.height
            }
            .widthIn(min = 36.dp)
            .height(36.dp)
            .shadow(6.dp, RoundedCornerShape(10.dp), clip = false)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(kbdColors.keyBg))
            .border(1.2.dp, accentColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = hovered.spec.displayLabel,
            color = accentColor,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun rememberIntStateZero() = androidx.compose.runtime.remember { mutableIntStateOf(0) }
