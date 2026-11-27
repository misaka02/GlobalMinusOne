package com.example.globalminusone.ui.panel.layout

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
    if (transition == PageTransition.TRANSLATION || abs(pageOffset) < 0.0005f) {
        translationX = 0f
        translationY = 0f
        scaleX = 1f
        scaleY = 1f
        alpha = 1f
        rotationX = 0f
        rotationY = 0f
        rotationZ = 0f
        transformOrigin = TransformOrigin.Center
        clip = false
        compositingStrategy = CompositingStrategy.Auto
        return
    }
    val safeDensity = density.coerceAtLeast(1f)
    val defaultCamera = 8f * safeDensity
    val perspectiveCamera = 16f * safeDensity
    val absOffset = abs(pageOffset)
    val clampedOffset = pageOffset.coerceIn(-1f, 1f)
    var tx = 0f
    var ty = 0f
    var sx = 1f
    var sy = 1f
    var a = 1f
    var ry = 0f
    var rz = 0f
    var px = 0.5f
    var py = 0.5f
    var cam = defaultCamera
    when (transition) {
        PageTransition.TRANSLATION -> Unit
        PageTransition.STACK -> if (pageOffset > 0f) {
            tx = pageOffset * width
            a = (1f - pageOffset).coerceIn(0f, 1f)
        }
        PageTransition.STACK_SCALE -> if (pageOffset > 0f) {
            val scale = (1f - 0.16f * pageOffset).coerceIn(0.84f, 1f)
            tx = pageOffset * width
            sx = scale
            sy = scale
            a = (1f - pageOffset).coerceIn(0f, 1f)
        }
        PageTransition.SCALE -> {
            val scale = (1f - 0.22f * absOffset).coerceIn(0.78f, 1f)
            sx = scale
            sy = scale
            a = if (absOffset >= 1f) 0f else (1f - 0.35f * absOffset).coerceIn(0.45f, 1f)
        }
        PageTransition.PAGE -> {
            cam = perspectiveCamera
            if (pageOffset > 0f) {
                tx = pageOffset * width
                px = 0f
                ry = (-65f * pageOffset).coerceIn(-65f, 0f)
                a = (1f - pageOffset).coerceIn(0f, 1f)
            }
        }
        PageTransition.TURN_TABLE -> {
            rz = -22f * clampedOffset
            py = 1.6f
            a = if (absOffset >= 1f) 0f else (1f - 0.35f * absOffset).coerceIn(0.45f, 1f)
        }
        PageTransition.ROTATION -> {
            ry = -45f * clampedOffset
            a = if (absOffset >= 1f) 0f else (1f - 0.3f * absOffset).coerceIn(0.5f, 1f)
            cam = perspectiveCamera
        }
        PageTransition.COMPRESS -> {
            sx = (1f - 0.4f * absOffset).coerceIn(0.6f, 1f)
            a = if (absOffset >= 1f) 0f else (1f - 0.3f * absOffset).coerceIn(0.5f, 1f)
        }
        PageTransition.UP -> {
            ty = -pageOffset * height * 0.45f
            a = (1f - absOffset).coerceIn(0f, 1f)
        }
        PageTransition.DOWN -> {
            ty = pageOffset * height * 0.45f
            a = (1f - absOffset).coerceIn(0f, 1f)
        }
        PageTransition.OUT_CUBE -> {
            px = if (pageOffset > 0f) 1f else 0f
            ry = -90f * clampedOffset
            a = if (absOffset >= 1f) 0f else (1f - 0.35f * absOffset).coerceIn(0f, 1f)
            cam = perspectiveCamera
        }
        PageTransition.IN_CUBE -> {
            px = if (pageOffset > 0f) 1f else 0f
            ry = 90f * clampedOffset
            a = if (absOffset >= 1f) 0f else (1f - 0.35f * absOffset).coerceIn(0f, 1f)
            cam = perspectiveCamera
        }
    }
    translationX = tx
    translationY = ty
    scaleX = sx
    scaleY = sy
    alpha = a
    rotationX = 0f
    rotationY = ry
    rotationZ = rz
    cameraDistance = cam
    transformOrigin = TransformOrigin(px, py)
    clip = true
    compositingStrategy = resolveTransitionCompositingStrategy(a)
}
