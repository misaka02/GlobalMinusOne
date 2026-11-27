package com.example.globalminusone.ui.panel.layout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.globalminusone.data.CardReorderMotionStyle
import com.example.globalminusone.ui.motion.MotionTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * Owns one velocity-preserving offset [Animatable] and one pickup-lift [Animatable] per card slot,
 * eliminating dual-offset overshoot when transitioning between finger drag and spring settle.
 */
@Stable
internal class CardSlotMotionController(
    initialOffsetPx: Offset,
    private val scope: CoroutineScope
) {
    val offsetAnim: Animatable<Offset, AnimationVector2D> = Animatable(initialOffsetPx, Offset.VectorConverter)
    val liftAnim: Animatable<Float, AnimationVector1D> = Animatable(0f)

    fun seedDragOffset(pixelOffset: Offset) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            offsetAnim.snapTo(pixelOffset)
        }
    }

    fun setDraggingLift(dragging: Boolean, animMs: Int, style: CardReorderMotionStyle) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val target = if (dragging) 1f else 0f
            if (animMs <= 0 || style == CardReorderMotionStyle.NONE) {
                liftAnim.snapTo(target)
            } else {
                liftAnim.animateTo(
                    targetValue = target,
                    animationSpec = spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMedium)
                )
            }
        }
    }

    suspend fun settleTo(
        targetOffsetPx: Offset,
        animateMoves: Boolean,
        animMs: Int,
        style: CardReorderMotionStyle
    ) {
        if (!animateMoves || animMs <= 0 || style == CardReorderMotionStyle.NONE) {
            offsetAnim.snapTo(targetOffsetPx)
        } else if ((offsetAnim.value - targetOffsetPx).getDistanceSquared() > 0.25f) {
            offsetAnim.animateTo(
                targetValue = targetOffsetPx,
                animationSpec = MotionTokens.cardOffsetSpring(animMs, style)
            )
        } else {
            offsetAnim.snapTo(targetOffsetPx)
        }
    }

    val currentOffsetPx: Offset get() = offsetAnim.value
    val liftProgress: Float get() = liftAnim.value.coerceIn(0f, 1f)
    val isLiftActive: Boolean get() = liftAnim.isRunning || liftAnim.value > 0.001f
}

private val CardPickupShape = RoundedCornerShape(10.dp)

@Composable
internal fun rememberCardSlotMotionController(
    key: Any,
    initialOffsetPx: Offset,
    targetOffsetPx: Offset,
    isDragging: Boolean,
    isResizing: Boolean,
    animateMoves: Boolean,
    animMs: Int,
    style: CardReorderMotionStyle
): CardSlotMotionController {
    val scope = rememberCoroutineScope()
    val controller = remember(key) { CardSlotMotionController(initialOffsetPx, scope) }

    LaunchedEffect(isDragging, animMs, style) {
        controller.setDraggingLift(isDragging, animMs, style)
    }

    LaunchedEffect(targetOffsetPx, isDragging, isResizing, animateMoves, animMs, style) {
        if (isDragging) return@LaunchedEffect
        val shouldAnimate = animateMoves && !isResizing
        controller.settleTo(targetOffsetPx, shouldAnimate, animMs, style)
    }

    return controller
}

internal fun Modifier.cardPickupVisuals(
    controller: CardSlotMotionController,
    rotationDeg: Float,
    isDragging: Boolean = false,
    maxLiftScale: Float = 0.032f,
    maxShadowDp: Float = 11f
): Modifier = if (!isDragging && rotationDeg == 0f && !controller.isLiftActive) {
    this
} else {
    graphicsLayer {
        val lift = controller.liftProgress
        val scale = 1f + maxLiftScale * lift
        scaleX = scale
        scaleY = scale
        alpha = 1f - 0.05f * lift
        rotationZ = rotationDeg
        shadowElevation = if (lift > 0.01f) maxShadowDp.dp.toPx() * lift else 0f
        shape = CardPickupShape
    }
}
