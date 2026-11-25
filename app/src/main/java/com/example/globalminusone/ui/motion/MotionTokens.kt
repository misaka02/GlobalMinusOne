package com.example.globalminusone.ui.motion

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import com.example.globalminusone.data.resolveEffectiveAnimationMs

/** Shared Compose physics tokens scaled consistently from user `animationDurationMs`. */
internal object MotionTokens {
    fun pagerSnapSpring(animMs: Int): AnimationSpec<Float> =
        if (animMs <= 0) snap()
        else spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = scaledStiffness(animMs, baseStiffness = 650f, baseMs = 240f, scale = 1f, maxMs = 600, minStiffness = 140f)
        )

    fun cardSettleSpring(animMs: Int): AnimationSpec<Float> =
        if (animMs <= 0) snap()
        else spring(dampingRatio = 0.94f, stiffness = cardSettleStiffness(animMs))

    fun cardOffsetSpring(animMs: Int): AnimationSpec<Offset> =
        if (animMs <= 0) snap()
        else spring(dampingRatio = 0.94f, stiffness = cardSettleStiffness(animMs))

    fun overlayEnterSpring(animMs: Int): FiniteAnimationSpec<Float> =
        if (animMs <= 0) snap()
        else spring(
            dampingRatio = 0.92f,
            stiffness = scaledStiffness(animMs, baseStiffness = 780f, baseMs = 240f, scale = 1f, maxMs = 420, minStiffness = 220f)
        )

    fun overlayExitSpring(animMs: Int): FiniteAnimationSpec<Float> =
        if (animMs <= 0) snap()
        else spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = scaledStiffness(animMs, baseStiffness = 980f, baseMs = 200f, scale = 0.78f, maxMs = 340, minStiffness = 300f)
        )

    fun screenSlideEnterSpring(animMs: Int): FiniteAnimationSpec<IntOffset> =
        if (animMs <= 0) snap()
        else spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = scaledStiffness(animMs, baseStiffness = 680f, baseMs = 240f, scale = 1f, maxMs = 420, minStiffness = 200f)
        )

    fun screenSlideExitSpring(animMs: Int): FiniteAnimationSpec<IntOffset> =
        if (animMs <= 0) snap()
        else spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = scaledStiffness(animMs, baseStiffness = 820f, baseMs = 210f, scale = 0.82f, maxMs = 360, minStiffness = 260f)
        )

    fun chromeFadeSpec(animMs: Int): AnimationSpec<Float> =
        if (animMs <= 0) snap()
        else spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = scaledStiffness(animMs, baseStiffness = 850f, baseMs = 240f, scale = 1f, maxMs = 500, minStiffness = 220f)
        )

    private fun cardSettleStiffness(animMs: Int): Float =
        if (animMs <= 0) Spring.StiffnessHigh
        else scaledStiffness(animMs, baseStiffness = 950f, baseMs = 240f, scale = 1f, maxMs = 600, minStiffness = 180f)

    private fun scaledStiffness(
        animMs: Int,
        baseStiffness: Float,
        baseMs: Float,
        scale: Float,
        maxMs: Int,
        minStiffness: Float,
        maxStiffness: Float = 12000f
    ): Float {
        val effectiveMs = resolveEffectiveAnimationMs(animMs, scale, maxMs)
        if (effectiveMs <= 0) return maxStiffness
        val ratio = baseMs / effectiveMs.toFloat()
        return (baseStiffness * ratio * ratio).coerceIn(minStiffness, maxStiffness)
    }
}
