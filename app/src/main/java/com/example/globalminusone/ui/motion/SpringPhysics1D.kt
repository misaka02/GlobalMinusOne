package com.example.globalminusone.ui.motion

import com.example.globalminusone.data.resolveEffectiveAnimationMs
import kotlin.math.abs
import kotlin.math.exp

/** Sample produced by [SpringPhysics1D] at a point in time. */
data class SpringSample(
    val position: Float,
    val velocity: Float,
    val settled: Boolean
)

/**
 * Analytical 1D critically damped spring ($\zeta = 1$).
 * Preserves any hand-off [initialVelocity], never overshoots, and cancels cleanly mid-flight.
 */
class SpringPhysics1D(
    val angularFrequency: Float,
    val positionTolerance: Float = 0.0005f,
    val velocityTolerance: Float = 0.005f
) {
    fun sample(
        startPosition: Float,
        targetPosition: Float,
        initialVelocity: Float,
        elapsedSeconds: Float
    ): SpringSample {
        val d0 = startPosition - targetPosition
        if (!elapsedSeconds.isFinite() || elapsedSeconds <= 0f) {
            val atRest = abs(d0) <= positionTolerance && abs(initialVelocity) <= velocityTolerance
            return SpringSample(startPosition, initialVelocity, atRest)
        }
        val w = angularFrequency.coerceAtLeast(1f)
        val decay = exp((-w * elapsedSeconds).toDouble()).toFloat()
        val c2 = initialVelocity + w * d0
        val displacement = (d0 + c2 * elapsedSeconds) * decay
        val velocity = (initialVelocity - w * c2 * elapsedSeconds) * decay
        val settled = abs(displacement) <= positionTolerance && abs(velocity) <= velocityTolerance
        return if (settled) {
            SpringSample(targetPosition, 0f, true)
        } else {
            SpringSample(targetPosition + displacement, velocity, false)
        }
    }
}

/** Maps user `animationDurationMs` to natural angular frequency (rad/s) for panel open/close springs. */
fun panelSpringAngularFrequency(animMs: Int, closing: Boolean = false): Float {
    val effectiveMs = resolveEffectiveAnimationMs(animMs, if (closing) 0.82f else 1f, 800)
    if (effectiveMs <= 0) return 0f
    return (22f * (260f / effectiveMs.toFloat())).coerceIn(9f, 65f)
}
