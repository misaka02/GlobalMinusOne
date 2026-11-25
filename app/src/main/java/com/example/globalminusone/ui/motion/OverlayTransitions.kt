package com.example.globalminusone.ui.motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Draw/graphicsLayer-only progress provider for zero-recomposition overlay transitions. */
internal val LocalOverlayMotionProgress = staticCompositionLocalOf<(() -> Float)?> { null }

internal fun Modifier.blockClosingTouches(closing: Boolean): Modifier =
    if (!closing) this else pointerInput(closing) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            }
        }
    }

@Composable
internal fun AnimatedOverlayPresence(
    visible: Boolean,
    animMs: Int,
    content: @Composable () -> Unit
) {
    var mounted by remember { mutableStateOf(visible) }
    if (visible && !mounted) mounted = true
    if (!mounted && !visible) return

    val progress = remember { Animatable(if (visible && animMs <= 0) 1f else 0f) }
    val provider = remember(progress) { { progress.value } }

    LaunchedEffect(visible, animMs) {
        if (visible) {
            mounted = true
            if (animMs <= 0) progress.snapTo(1f)
            else progress.animateTo(1f, MotionTokens.overlayEnterSpring(animMs))
        } else if (mounted) {
            if (animMs <= 0) progress.snapTo(0f)
            else progress.animateTo(0f, MotionTokens.overlayExitSpring(animMs))
            mounted = false
        }
    }

    CompositionLocalProvider(LocalOverlayMotionProgress provides provider) {
        Box(Modifier.fillMaxSize().blockClosingTouches(!visible)) {
            content()
        }
    }
}

@Composable
internal fun <T : Any> AnimatedOverlayTarget(
    targetState: T?,
    animMs: Int,
    keyOf: (T) -> Any = { it },
    content: @Composable (T) -> Unit
) {
    var displayed by remember { mutableStateOf(targetState) }
    var lastKey by remember { mutableStateOf<Any?>(targetState?.let(keyOf)) }
    val targetKey = targetState?.let(keyOf)
    if (targetState != null) {
        displayed = targetState
    }
    val active = displayed ?: return

    val progress = remember { Animatable(if (targetState != null && animMs <= 0) 1f else 0f) }
    val provider = remember(progress) { { progress.value } }

    LaunchedEffect(targetKey, targetState != null, animMs) {
        if (targetState != null) {
            if (lastKey != null && lastKey != targetKey && animMs > 0) {
                progress.snapTo(0f)
            }
            lastKey = targetKey
            if (animMs <= 0) progress.snapTo(1f)
            else progress.animateTo(1f, MotionTokens.overlayEnterSpring(animMs))
        } else {
            if (animMs <= 0) progress.snapTo(0f)
            else progress.animateTo(0f, MotionTokens.overlayExitSpring(animMs))
            displayed = null
            lastKey = null
        }
    }

    CompositionLocalProvider(LocalOverlayMotionProgress provides provider) {
        Box(Modifier.fillMaxSize().blockClosingTouches(targetState == null)) {
            content(active)
        }
    }
}

@Composable
internal fun Modifier.overlayScrim(color: Color, maxAlpha: Float): Modifier {
    val provider = LocalOverlayMotionProgress.current
    return drawBehind {
        val p = provider?.invoke()?.coerceIn(0f, 1f) ?: 1f
        val a = (maxAlpha * p).coerceIn(0f, 1f)
        if (a > 0f) drawRect(color.copy(alpha = a))
    }
}

@Composable
internal fun Modifier.overlaySheetMotion(slideDp: Dp = 44.dp): Modifier {
    val provider = LocalOverlayMotionProgress.current
    return graphicsLayer {
        val p = provider?.invoke()?.coerceIn(0f, 1f) ?: 1f
        val scale = 0.97f + 0.03f * p
        alpha = p
        translationY = (1f - p) * slideDp.toPx()
        scaleX = scale
        scaleY = scale
        transformOrigin = TransformOrigin(0.5f, 1f)
    }
}

@Composable
internal fun Modifier.overlayDialogMotion(minScale: Float = 0.90f): Modifier {
    val provider = LocalOverlayMotionProgress.current
    return graphicsLayer {
        val p = provider?.invoke()?.coerceIn(0f, 1f) ?: 1f
        val scale = minScale + (1f - minScale) * p
        alpha = p
        scaleX = scale
        scaleY = scale
        transformOrigin = TransformOrigin.Center
    }
}

internal fun settingsScreenDepth(screen: String): Int = when (screen) {
    "MAIN" -> 0
    "SB_SETTINGS" -> 2
    else -> 1
}

@Composable
internal fun SettingsScreenTransition(
    targetScreen: String,
    animMs: Int,
    content: @Composable (String) -> Unit
) {
    AnimatedContent(
        targetState = targetScreen,
        transitionSpec = {
            if (animMs <= 0) {
                (EnterTransition.None togetherWith ExitTransition.None)
            } else {
                val forward = settingsScreenDepth(targetState) >= settingsScreenDepth(initialState)
                val slideInSpec = MotionTokens.screenSlideEnterSpring(animMs)
                val slideOutSpec = MotionTokens.screenSlideExitSpring(animMs)
                val fadeInSpec = MotionTokens.overlayEnterSpring(animMs)
                val fadeOutSpec = MotionTokens.overlayExitSpring(animMs)
                val enter = slideInHorizontally(slideInSpec) { w -> if (forward) (w * 0.22f).toInt() else -(w * 0.16f).toInt() } +
                    fadeIn(fadeInSpec)
                val exit = slideOutHorizontally(slideOutSpec) { w -> if (forward) -(w * 0.10f).toInt() else (w * 0.22f).toInt() } +
                    fadeOut(fadeOutSpec)
                (enter togetherWith exit).using(SizeTransform(clip = false))
            }
        },
        label = "settings-nav"
    ) { screen ->
        content(screen)
    }
}
