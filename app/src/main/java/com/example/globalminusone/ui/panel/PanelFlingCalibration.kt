package com.example.globalminusone.ui.panel

import android.os.SystemClock
import androidx.compose.foundation.gestures.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import kotlin.math.abs

/** One screen-space release sample per input window. Card height/translation is never
 * an input. Only the launch velocity is bounded; drag deltas and native decay stay intact. */
internal class PanelFlingCalibration(private val clock: () -> Long) {
    private val tracker = VelocityTracker()
    private var firstTime = Long.MIN_VALUE
    private var firstY = 0f
    private var lastTime = Long.MIN_VALUE
    private var lastY = 0f
    private var lastMovement = Long.MIN_VALUE
    private var samples = 0
    private var releaseSpeed: Float? = null
    private var releasedAt = Long.MIN_VALUE

    fun begin(time: Long, screenY: Float) {
        tracker.resetTracking()
        firstTime = time; firstY = screenY
        lastTime = Long.MIN_VALUE; samples = 0; lastMovement = time
        releaseSpeed = null
        sample(time, screenY)
    }
    fun sample(time: Long, screenY: Float) {
        if (!screenY.isFinite() || time <= lastTime) return
        val moved = samples == 0 || abs(screenY - lastY) > .01f
        if (samples > 0 && moved) lastMovement = time
        // Compose VelocityTracker resets velocity on >=40ms gaps at identical coordinates;
        // skip stationary points past 35ms and let end()'s 55ms window decide whether fling stays alive.
        if (moved || time - lastTime < 35L) {
            tracker.addPosition(time, Offset(0f, screenY))
        }
        lastTime = time; lastY = screenY; samples++
    }
    fun end(time: Long, screenY: Float) {
        sample(time, screenY)
        val rawVelocity = if (samples >= 2 && time - lastMovement <= 55) {
            val tracked = tracker.calculateVelocity().y
            if (tracked == 0f && samples == 2 && lastTime > firstTime) {
                (lastY - firstY) / ((lastTime - firstTime) / 1000f)
            } else tracked
        } else 0f
        releaseSpeed = abs(rawVelocity).takeIf { it.isFinite() } ?: 0f
        releasedAt = clock()
    }
    fun cancel() { releaseSpeed = 0f; releasedAt = clock() }
    fun limit(initial: Float): Float {
        if (!initial.isFinite()) return 0f
        val speed = releaseSpeed ?: return initial
        if (clock() - releasedAt !in 0..250) return initial // no stale budget for keyboard/programmatic scroll
        return initial.coerceIn(-speed, speed)
    }
}

internal val LocalPanelFlingCalibration = staticCompositionLocalOf<PanelFlingCalibration?> { null }

@Composable
internal fun rememberPanelFlingCalibration() = remember { PanelFlingCalibration { SystemClock.uptimeMillis() } }

internal class CalibratedPanelFling(private val native: FlingBehavior,
    private val calibration: PanelFlingCalibration?) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val accepted = calibration?.limit(initialVelocity) ?: initialVelocity.takeIf { it.isFinite() } ?: 0f
        val scrollScope = this
        val remaining = with(native) { scrollScope.performFling(accepted) }
        // Discard excess estimated velocity; forwarding it re-injects the same phantom fling
        // into the ancestor. Only actual, bounded native residual may cross a boundary.
        return if (remaining.isFinite()) remaining.coerceIn(minOf(0f, accepted), maxOf(0f, accepted)) else 0f
    }
}

@Composable
internal fun rememberPanelFlingBehavior(): FlingBehavior {
    val native = ScrollableDefaults.flingBehavior()
    val calibration = LocalPanelFlingCalibration.current
    return remember(native, calibration) { CalibratedPanelFling(native, calibration) }
}

/** Observe at the stable window ancestor before descendants process UP. Never consume
 * a pointer or take ownership, and include historical samples from batched MotionEvents. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun Modifier.observePanelReleaseVelocity(calibration: PanelFlingCalibration): Modifier {
    val view = LocalView.current
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    return onGloballyPositioned { coordinates[0] = it }.pointerInput(view, calibration) {
        val screen = IntArray(2); val window = IntArray(2)
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            view.getLocationOnScreen(screen); view.getLocationInWindow(window)
            val windowOffsetY = (screen[1] - window[1]).toFloat()
            fun screenY(point: Offset): Float =
                (coordinates[0]?.takeIf { it.isAttached }?.localToWindow(point)?.y ?: point.y) + windowOffsetY
            calibration.begin(down.uptimeMillis, screenY(down.position))
            var valid = true
            var completed = false
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val point = event.changes.firstOrNull { it.id == down.id }
                    if (point == null || event.changes.count { it.pressed } > 1) { valid = false; calibration.cancel() }
                    if (point != null && valid) {
                        point.historical.forEach { calibration.sample(it.uptimeMillis, screenY(it.position)) }
                        if (point.pressed) calibration.sample(point.uptimeMillis, screenY(point.position))
                        else {
                            if (point.isConsumed) calibration.cancel() else calibration.end(point.uptimeMillis, screenY(point.position))
                            completed = true
                        }
                    }
                } while (event.changes.any { it.pressed })
            } finally { if (!completed) calibration.cancel() }
        }
    }
}
