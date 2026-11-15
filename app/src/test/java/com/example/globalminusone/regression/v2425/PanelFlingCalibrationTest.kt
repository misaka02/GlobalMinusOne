package com.example.globalminusone.ui.panel

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PanelFlingCalibrationTest {
    private fun swipe(clock: () -> Long, offset: Float = 0f): PanelFlingCalibration = PanelFlingCalibration(clock).also {
        it.begin(0, offset + 500f)
        for (time in 20L..80L step 20) it.sample(time, offset + 500f - time)
        it.end(100, offset + 400f)
    }

    @Test fun releaseBudgetDependsOnScreenMovementInsteadOfLongCardOrigin() {
        val short = swipe({ 100L })
        val long = swipe({ 100L }, 10000f)
        assertEquals(1000f, short.limit(6000f), 3f)
        assertEquals(short.limit(6000f), long.limit(6000f), 3f)
        assertEquals(400f, short.limit(400f), 0f)
        assertEquals(-1000f, short.limit(-6000f), 3f)
    }

    @Test fun stoppedFingerCancellationAndNewTouchDoNotReuseAnOldFling() {
        var now = 100L
        val sample = swipe({ now })
        sample.sample(140, 400f); sample.end(160, 400f)
        assertEquals(0f, sample.limit(6000f), 0f)
        sample.cancel()
        assertEquals(0f, sample.limit(-6000f), 0f)
        sample.begin(200, 500f)
        assertEquals(500f, sample.limit(500f), 0f)
        now = 500
        assertEquals(500f, sample.limit(500f), 0f)
    }

    @Test fun staleBudgetDoesNotChangeLaterKeyboardOrProgrammaticScrolling() {
        var now = 100L
        val sample = swipe({ now })
        now = 351
        assertEquals(6000f, sample.limit(6000f), 0f)
        assertEquals(0f, sample.limit(Float.NaN), 0f)
    }

    @Test fun nativeFlingReceivesOnlyPhysicalVelocityAndDoesNotForwardRejectedExcess() = runBlocking {
        var received = 0f
        val native = object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                received = initialVelocity
                scrollBy(initialVelocity / 10)
                return initialVelocity / 4
            }
        }
        val behavior = CalibratedPanelFling(native, swipe({ 100L }))
        var distance = 0f
        val scope = object : ScrollScope { override fun scrollBy(pixels: Float) = pixels.also { distance += it } }
        val remaining = with(behavior) { scope.performFling(6000f) }
        assertEquals(1000f, received, 3f)
        assertEquals(100f, distance, .3f)
        assertEquals(250f, remaining, 1f) // not 5250: discarded excess must never enter the ancestor
    }

    @Test fun impossibleNativeResidualCannotCreateASecondHighVelocityFling() = runBlocking {
        val native = object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float) = initialVelocity * 10
        }
        val behavior = CalibratedPanelFling(native, swipe({ 100L }))
        val scope = object : ScrollScope { override fun scrollBy(pixels: Float) = pixels }
        assertEquals(-1000f, with(behavior) { scope.performFling(-5000f) }, 3f)
    }
}
