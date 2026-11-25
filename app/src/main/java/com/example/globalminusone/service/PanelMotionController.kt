package com.example.globalminusone.service

import android.view.Choreographer
import com.example.globalminusone.ui.motion.SpringPhysics1D
import com.example.globalminusone.ui.motion.panelSpringAngularFrequency
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
    private var spring = SpringPhysics1D(22f)
    private var pendingOnEnd: (() -> Unit)? = null

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isAnimating) return
            val elapsedSec = ((frameTimeNanos - startNanos).coerceAtLeast(1_000_000L) / 1_000_000_000f)
                .coerceAtMost(MAX_SETTLE_SECONDS)
            val sample = spring.sample(startPosition, targetPosition, startVelocity, elapsedSec)
            val nextPos = sample.position.coerceIn(0f, 1f)
            val settled = sample.settled ||
                elapsedSec >= MAX_SETTLE_SECONDS ||
                (targetPosition >= 0.9999f && nextPos >= 0.9995f && abs(sample.velocity) < 0.05f) ||
                (targetPosition <= 0.0001f && nextPos <= 0.0005f && abs(sample.velocity) < 0.05f)
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
        if (omega <= 0f || (abs(startPos - clampedTarget) <= 0.0005f && abs(startVel) <= 0.005f)) {
            snapTo(clampedTarget)
            onEnd()
            return
        }
        startPosition = startPos
        targetPosition = clampedTarget
        startVelocity = startVel
        startNanos = System.nanoTime()
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
        const val MAX_VELOCITY = 12f
        const val MAX_SETTLE_SECONDS = 2.5f
    }
}
