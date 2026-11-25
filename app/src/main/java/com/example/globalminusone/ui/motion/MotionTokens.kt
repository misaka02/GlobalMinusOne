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
    fun pagerSnapSpring(animMs: Int): AnimationSpec<Float> {
        if (animMs <= 0) return snap()
        val effectiveMs = resolveEffectiveAnimationMs(animMs, 1f, 600)
        val ratio = 240f / effectiveMs.toFloat()
        val stiffness = (650f * ratio * ratio).coerceIn(140f, 3200f)
        return spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = stiffness
        )
    }

    fun cardSettleSpring(animMs: Int): AnimationSpec<Float> =
        if (animMs <= 0) snap()
        else spring(dampingRatio = 0.94f, stiffness = cardSettleStiffness(animMs))

    fun cardOffsetSpring(animMs: Int): AnimationSpec<Offset> =
        if (animMs <= 0) snap()
        else spring(dampingRatio = 0.94f, stiffness = cardSettleStiffness(animMs))

    fun overlayEnterSpring(animMs: Int): FiniteAnimationSpec<Float> {
        if (animMs <= 0) return snap()
        val effectiveMs = resolveEffectiveAnimationMs(animMs, 1f, 420)
        val ratio = 240f / effectiveMs.toFloat()
        return spring(
            dampingRatio = 0.92f,
            stiffness = (780f * ratio * ratio).coerceIn(220f, 3200f)
        )
    }

    fun overlayExitSpring(animMs: Int): FiniteAnimationSpec<Float> {
        if (animMs <= 0) return snap()
        val effectiveMs = resolveEffectiveAnimationMs(animMs, 0.78f, 340)
        val ratio = 200f / effectiveMs.toFloat()
        return spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = (980f * ratio * ratio).coerceIn(300f, 4000f)
        )
    }

    fun screenSlideEnterSpring(animMs: Int): FiniteAnimationSpec<IntOffset> {
        if (animMs <= 0) return snap()
        val effectiveMs = resolveEffectiveAnimationMs(animMs, 1f, 420)
        val ratio = 240f / effectiveMs.toFloat()
        return spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = (680f * ratio * ratio).coerceIn(200f, 3000f)
        )
    }

    fun screenSlideExitSpring(animMs: Int): FiniteAnimationSpec<IntOffset> {
        if (animMs <= 0) return snap()
        val effectiveMs = resolveEffectiveAnimationMs(animMs, 0.82f, 360)
        val ratio = 210f / effectiveMs.toFloat()
        return spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = (820f * ratio * ratio).coerceIn(260f, 3600f)
        )
    }

    fun chromeFadeSpec(animMs: Int): AnimationSpec<Float> {
        if (animMs <= 0) return snap()
        val effectiveMs = resolveEffectiveAnimationMs(animMs, 1f, 500)
        val ratio = 240f / effectiveMs.toFloat()
        return spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = (850f * ratio * ratio).coerceIn(220f, 3600f)
        )
    }

    private fun cardSettleStiffness(animMs: Int): Float {
        if (animMs <= 0) return Spring.StiffnessHigh
        val effectiveMs = resolveEffectiveAnimationMs(animMs, 1f, 600)
        val ratio = 240f / effectiveMs.toFloat()
        return (950f * ratio * ratio).coerceIn(180f, 3600f)
    }
}
