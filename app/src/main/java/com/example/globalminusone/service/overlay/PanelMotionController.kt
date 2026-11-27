package com.example.globalminusone.service.overlay

import android.view.Choreographer
import com.example.globalminusone.ui.motion.SpringPhysics1D
import com.example.globalminusone.ui.motion.panelSpringAngularFrequency
import com.example.globalminusone.ui.motion.panelSpringMaxSettleSeconds
import kotlin.math.abs

/**
 * Owns overlay `openProgress` `[0f, 1f]` and settles drags/toggles using a VSYNC-driven
 * critically damped spring that preserves finger release velocity and mid-flight interruptions.
 */
internal class PanelMotionController(
    private val onProgressChanged: (Float) -> Unit
) {
    var currentProgress: Float = 0f
        private set
    var currentVelocity: Float = 0f
        private set
    var isAnimating: Boolean = false
        private set

    private var startPosition = 0f
    private var targetPosition = 0f
    private var startVelocity = 0f
    private var startNanos = 0L
    private var maxSettleSeconds = MAX_SETTLE_SECONDS
    private var spring = SpringPhysics1D(22f)
    private var pendingOnEnd: (() -> Unit)? = null

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isAnimating) return
            if (startNanos == 0L) {
                startNanos = frameTimeNanos - FIRST_FRAME_LEAD_NANOS
            }
            val elapsedSec = ((frameTimeNanos - startNanos).coerceAtLeast(FIRST_FRAME_LEAD_NANOS) / 1_000_000_000f)
                .coerceAtMost(maxSettleSeconds)
            val sample = spring.sample(startPosition, targetPosition, startVelocity, elapsedSec)
            val nextPos = sample.position.coerceIn(0f, 1f)
            val settled = sample.settled ||
                elapsedSec >= maxSettleSeconds ||
                (targetPosition >= 0.9999f && nextPos >= 0.998f && abs(sample.velocity) < 0.08f) ||
                (targetPosition <= 0.0001f && nextPos <= 0.002f && abs(sample.velocity) < 0.08f)
            if (settled) {
                val onEnd = pendingOnEnd
                isAnimating = false
                pendingOnEnd = null
                currentProgress = targetPosition
                currentVelocity = 0f
                onProgressChanged(targetPosition)
                onEnd?.invoke()
            } else {
                currentProgress = nextPos
                currentVelocity = sample.velocity
                onProgressChanged(nextPos)
                if (isAnimating) {
                    Choreographer.getInstance().postFrameCallback(this)
                }
            }
        }
    }

    fun setDragProgress(progress: Float) {
        snapTo(progress)
    }

    fun snapTo(progress: Float) {
        cancel(resetVelocity = true)
        currentProgress = progress.coerceIn(0f, 1f)
        onProgressChanged(currentProgress)
    }

    fun animateTo(
        target: Float,
        initialVelocity: Float = 0f,
        animationDurationMs: Int,
        closing: Boolean,
        onEnd: () -> Unit
    ) {
        val carryVelocity = if (initialVelocity != 0f) initialVelocity else currentVelocity
        cancel(resetVelocity = false)
        val clampedTarget = target.coerceIn(0f, 1f)
        val startPos = currentProgress
        val startVel = carryVelocity.takeIf { it.isFinite() }?.coerceIn(-MAX_VELOCITY, MAX_VELOCITY) ?: 0f
        val omega = panelSpringAngularFrequency(animationDurationMs, closing)
        val settleCap = panelSpringMaxSettleSeconds(animationDurationMs, closing)
        if (omega <= 0f || settleCap <= 0f || (abs(startPos - clampedTarget) <= 0.002f && abs(startVel) <= 0.04f)) {
            snapTo(clampedTarget)
            onEnd()
            return
        }
        startPosition = startPos
        targetPosition = clampedTarget
        startVelocity = startVel
        startNanos = 0L
        maxSettleSeconds = settleCap
        spring = SpringPhysics1D(omega)
        pendingOnEnd = onEnd
        isAnimating = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun cancel(resetVelocity: Boolean = false) {
        if (isAnimating) {
            runCatching { Choreographer.getInstance().removeFrameCallback(frameCallback) }
        }
        isAnimating = false
        pendingOnEnd = null
        if (resetVelocity) currentVelocity = 0f
    }

    private companion object {
        const val MAX_VELOCITY = 24f
        const val MAX_SETTLE_SECONDS = 2.5f
        const val FIRST_FRAME_LEAD_NANOS = 8_000_000L
    }
}
