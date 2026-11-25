package com.example.globalminusone.ui.panel

import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import com.example.globalminusone.data.PageTransition
import kotlin.math.abs

internal data class PageTransitionSpec(
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val alpha: Float = 1f,
    val rotationX: Float = 0f,
    val rotationY: Float = 0f,
    val rotationZ: Float = 0f,
    val pivotFractionX: Float = 0.5f,
    val pivotFractionY: Float = 0.5f,
    val cameraDistance: Float
)

internal fun computePageTransitionSpec(
    transition: PageTransition,
    pageOffset: Float,
    width: Float,
    height: Float,
    density: Float
): PageTransitionSpec {
    val defaultCamera = 8f * density.coerceAtLeast(1f)
    val perspectiveCamera = 16f * density.coerceAtLeast(1f)
    val absOffset = abs(pageOffset)
    val clampedOffset = pageOffset.coerceIn(-1f, 1f)
    return when (transition) {
        PageTransition.TRANSLATION -> PageTransitionSpec(
            alpha = 1f,
            cameraDistance = defaultCamera
        )
        PageTransition.STACK -> PageTransitionSpec(
            translationX = if (pageOffset > 0f) pageOffset * width else 0f,
            alpha = if (pageOffset > 0f) (1f - pageOffset).coerceIn(0f, 1f) else 1f,
            cameraDistance = defaultCamera
        )
        PageTransition.STACK_SCALE -> {
            val scale = if (pageOffset > 0f) (1f - 0.16f * pageOffset).coerceIn(0.84f, 1f) else 1f
            PageTransitionSpec(
                translationX = if (pageOffset > 0f) pageOffset * width else 0f,
                scaleX = scale,
                scaleY = scale,
                alpha = if (pageOffset > 0f) (1f - pageOffset).coerceIn(0f, 1f) else 1f,
                cameraDistance = defaultCamera
            )
        }
        PageTransition.SCALE -> {
            val scale = (1f - 0.22f * absOffset).coerceIn(0.78f, 1f)
            PageTransitionSpec(
                scaleX = scale,
                scaleY = scale,
                alpha = if (absOffset >= 1f) 0f else (1f - 0.35f * absOffset).coerceIn(0.45f, 1f),
                cameraDistance = defaultCamera
            )
        }
        PageTransition.PAGE -> if (pageOffset > 0f) {
            PageTransitionSpec(
                translationX = pageOffset * width,
                pivotFractionX = 0f,
                pivotFractionY = 0.5f,
                rotationY = (-65f * pageOffset).coerceIn(-65f, 0f),
                alpha = (1f - pageOffset).coerceIn(0f, 1f),
                cameraDistance = perspectiveCamera
            )
        } else {
            PageTransitionSpec(cameraDistance = perspectiveCamera)
        }
        PageTransition.TURN_TABLE -> PageTransitionSpec(
            rotationZ = -22f * clampedOffset,
            pivotFractionX = 0.5f,
            pivotFractionY = 1.6f,
            alpha = if (absOffset >= 1f) 0f else (1f - 0.35f * absOffset).coerceIn(0.45f, 1f),
            cameraDistance = defaultCamera
        )
        PageTransition.ROTATION -> PageTransitionSpec(
            rotationY = -45f * clampedOffset,
            alpha = if (absOffset >= 1f) 0f else (1f - 0.3f * absOffset).coerceIn(0.5f, 1f),
            cameraDistance = perspectiveCamera
        )
        PageTransition.COMPRESS -> PageTransitionSpec(
            scaleX = (1f - 0.4f * absOffset).coerceIn(0.6f, 1f),
            alpha = if (absOffset >= 1f) 0f else (1f - 0.3f * absOffset).coerceIn(0.5f, 1f),
            cameraDistance = defaultCamera
        )
        PageTransition.UP -> PageTransitionSpec(
            translationY = -pageOffset * height * 0.45f,
            alpha = (1f - absOffset).coerceIn(0f, 1f),
            cameraDistance = defaultCamera
        )
        PageTransition.DOWN -> PageTransitionSpec(
            translationY = pageOffset * height * 0.45f,
            alpha = (1f - absOffset).coerceIn(0f, 1f),
            cameraDistance = defaultCamera
        )
        PageTransition.OUT_CUBE -> PageTransitionSpec(
            pivotFractionX = if (pageOffset > 0f) 1f else 0f,
            pivotFractionY = 0.5f,
            rotationY = -90f * clampedOffset,
            alpha = if (absOffset >= 1f) 0f else (1f - 0.35f * absOffset).coerceIn(0f, 1f),
            cameraDistance = perspectiveCamera
        )
        PageTransition.IN_CUBE -> PageTransitionSpec(
            pivotFractionX = if (pageOffset > 0f) 1f else 0f,
            pivotFractionY = 0.5f,
            rotationY = 90f * clampedOffset,
            alpha = if (absOffset >= 1f) 0f else (1f - 0.35f * absOffset).coerceIn(0f, 1f),
            cameraDistance = perspectiveCamera
        )
    }
}

internal fun resolveTransitionCompositingStrategy(alpha: Float): CompositingStrategy =
    if (alpha < 0.999f) CompositingStrategy.ModulateAlpha else CompositingStrategy.Auto

fun androidx.compose.ui.graphics.GraphicsLayerScope.applyPageTransitionAnimation(
    transition: PageTransition,
    pageOffset: Float,
    width: Float,
    height: Float,
    density: Float
) {
    val spec = computePageTransitionSpec(transition, pageOffset, width, height, density)
    translationX = spec.translationX
    translationY = spec.translationY
    scaleX = spec.scaleX
    scaleY = spec.scaleY
    alpha = spec.alpha
    rotationX = spec.rotationX
    rotationY = spec.rotationY
    rotationZ = spec.rotationZ
    cameraDistance = spec.cameraDistance
    transformOrigin = TransformOrigin(spec.pivotFractionX, spec.pivotFractionY)
    clip = true
    compositingStrategy = resolveTransitionCompositingStrategy(spec.alpha)
}
