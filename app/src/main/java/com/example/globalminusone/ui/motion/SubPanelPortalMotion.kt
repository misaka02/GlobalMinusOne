package com.example.globalminusone.ui.motion

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Rect
import com.example.globalminusone.data.SubPanelTransitionStyle
import com.example.globalminusone.data.policy.resolveEffectiveAnimationMs

internal enum class SubPanelTransitionDirection {
    NONE,
    ENTER_FROM_CARD,
    EXIT_TO_PARENT
}

internal data class SubPanelPortalTransform(
    val scale: Float = 1f,
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val alpha: Float = 1f,
    val portalRimAlpha: Float = 0f
)

internal fun subPanelPortalSpring(
    animMs: Int,
    direction: SubPanelTransitionDirection = SubPanelTransitionDirection.ENTER_FROM_CARD
): FiniteAnimationSpec<Float> {
    if (animMs <= 0) return snap()
    val effectiveMs = resolveEffectiveAnimationMs(animMs, scale = 1f, maxMs = 480)
    if (effectiveMs <= 0) return snap()
    val ratio = 260f / effectiveMs.toFloat()
    val stiffness = (580f * ratio * ratio).coerceIn(180f, 12000f)
    val damping = if (direction == SubPanelTransitionDirection.EXIT_TO_PARENT) {
        Spring.DampingRatioNoBouncy
    } else {
        0.90f
    }
    return spring(
        dampingRatio = damping,
        stiffness = stiffness
    )
}

internal fun computeSubPanelPortalTransform(
    progress: Float,
    direction: SubPanelTransitionDirection,
    anchorBounds: Rect?,
    panelBounds: Rect,
    style: SubPanelTransitionStyle = SubPanelTransitionStyle.PORTAL_EXPAND
): SubPanelPortalTransform {
    val p = progress.coerceIn(0f, 1f)
    if (direction == SubPanelTransitionDirection.NONE ||
        style == SubPanelTransitionStyle.NONE ||
        p >= 0.999f ||
        panelBounds.width <= 1f ||
        panelBounds.height <= 1f
    ) {
        return SubPanelPortalTransform()
    }
    return when (style) {
        SubPanelTransitionStyle.NONE -> SubPanelPortalTransform()
        SubPanelTransitionStyle.FADE_SCALE -> when (direction) {
            SubPanelTransitionDirection.ENTER_FROM_CARD -> SubPanelPortalTransform(
                scale = 0.90f + 0.10f * p,
                alpha = (p * 1.45f).coerceIn(0f, 1f)
            )
            SubPanelTransitionDirection.EXIT_TO_PARENT -> SubPanelPortalTransform(
                scale = 0.95f + 0.05f * p,
                alpha = (0.70f + 0.30f * p).coerceIn(0f, 1f)
            )
            SubPanelTransitionDirection.NONE -> SubPanelPortalTransform()
        }
        SubPanelTransitionStyle.SLIDE -> {
            val shift = panelBounds.width * 0.24f * (1f - p)
            when (direction) {
                SubPanelTransitionDirection.ENTER_FROM_CARD -> SubPanelPortalTransform(
                    translationX = shift,
                    alpha = (p * 1.4f).coerceIn(0f, 1f)
                )
                SubPanelTransitionDirection.EXIT_TO_PARENT -> SubPanelPortalTransform(
                    translationX = -shift,
                    alpha = (0.45f + 0.55f * p).coerceIn(0f, 1f)
                )
                SubPanelTransitionDirection.NONE -> SubPanelPortalTransform()
            }
        }
        SubPanelTransitionStyle.PORTAL_EXPAND -> when (direction) {
            SubPanelTransitionDirection.ENTER_FROM_CARD -> {
                val hasAnchor = anchorBounds != null && anchorBounds.width > 1f && anchorBounds.height > 1f
                val startScale = if (hasAnchor) {
                    maxOf(
                        anchorBounds!!.width / panelBounds.width,
                        anchorBounds.height / panelBounds.height
                    ).coerceIn(0.22f, 0.72f)
                } else {
                    0.86f
                }
                val startDx = if (hasAnchor) anchorBounds!!.center.x - panelBounds.center.x else 0f
                val startDy = if (hasAnchor) anchorBounds!!.center.y - panelBounds.center.y else 0f
                SubPanelPortalTransform(
                    scale = startScale + (1f - startScale) * p,
                    translationX = startDx * (1f - p),
                    translationY = startDy * (1f - p),
                    alpha = (p * 1.65f).coerceIn(0f, 1f),
                    portalRimAlpha = ((1f - p) * 0.55f).coerceIn(0f, 0.55f)
                )
            }
            SubPanelTransitionDirection.EXIT_TO_PARENT -> {
                SubPanelPortalTransform(
                    scale = 0.96f + 0.04f * p,
                    alpha = (0.82f + 0.18f * p).coerceIn(0f, 1f)
                )
            }
            SubPanelTransitionDirection.NONE -> SubPanelPortalTransform()
        }
    }
}
