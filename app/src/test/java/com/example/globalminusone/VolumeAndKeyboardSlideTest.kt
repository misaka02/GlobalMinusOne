package com.example.globalminusone

import android.media.AudioManager
import com.example.globalminusone.data.TriggerSide
import com.example.globalminusone.system.media.MediaVolumeBackend
import com.example.globalminusone.system.media.MediaVolumeCommitResult
import com.example.globalminusone.system.media.MediaVolumeController
import com.example.globalminusone.system.media.isVolumeReadbackAcceptable
import com.example.globalminusone.ui.panel.keyboard.KeyboardSlidePickerState
import com.example.globalminusone.ui.panel.keyboard.SlidePickerKeyBounds
import com.example.globalminusone.ui.panel.keyboard.SlidePickerKeySpec
import com.example.globalminusone.ui.panel.keyboard.resolveSlidePickerCommitChar
import com.example.globalminusone.ui.panel.keyboard.resolveSlidePickerHit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeMediaVolumeBackend(
    initialIndex: Int = 5,
    var minVol: Int = 0,
    var maxVol: Int = 15,
    var volumeFixed: Boolean = false,
    var allowSetStreamVolume: Boolean = true,
    var allowStepAdjust: Boolean = true,
    var stepSize: Int = 1,
    var rootTargetOverride: Int? = null,
    var quantizeStep: Int = 1
) : MediaVolumeBackend {
    var index: Int = initialIndex
    var openPanelCount: Int = 0
        private set
    val recordedWrites = mutableListOf<Int>()
    var onSetStreamHook: ((Int) -> Unit)? = null

    override fun isVolumeFixed(): Boolean = volumeFixed

    override fun bounds(): Pair<Int, Int> = minVol to maxVol

    override fun currentIndex(): Int = index

    override fun setStreamVolume(index: Int): Boolean {
        recordedWrites += index
        onSetStreamHook?.invoke(index)
        if (!allowSetStreamVolume) return false
        val clamped = index.coerceIn(minVol, maxVol)
        this.index = if (quantizeStep > 1 && clamped > minVol && clamped < maxVol) {
            minVol + ((clamped - minVol) / quantizeStep) * quantizeStep
        } else {
            clamped
        }
        return true
    }

    override fun adjustStreamVolumeStep(direction: Int): Boolean {
        if (!allowStepAdjust) return false
        val delta = when (direction) {
            AudioManager.ADJUST_RAISE -> stepSize
            AudioManager.ADJUST_LOWER -> -stepSize
            else -> 0
        }
        if (delta == 0) return false
        index = (index + delta).coerceIn(minVol, maxVol)
        return true
    }

    override suspend fun setViaAuthorizedRoot(index: Int): Boolean {
        val rootIdx = rootTargetOverride ?: return false
        this.index = rootIdx.coerceIn(minVol, maxVol)
        return true
    }

    override fun openSystemVolumePanel() {
        openPanelCount++
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class VolumeAndKeyboardSlideTest {

    @Test
    fun commitWaitsForInflightDragWriteAndNeverAutoOpensSystemPanel() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val backend = FakeMediaVolumeBackend(initialIndex = 5, minVol = 0, maxVol = 15)
        val controllerScope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + dispatcher)
        val controller = MediaVolumeController(
            backend = backend,
            scope = controllerScope,
            ioDispatcher = dispatcher,
            mainDispatcher = dispatcher
        )

        var commitResult: MediaVolumeCommitResult? = null
        // Drag starts at 20% (index 3), then final release commits 80% (index 12).
        controller.enqueueDragPercent(20)
        controller.commitPercentAsync(80) { result ->
            commitResult = result
        }

        advanceUntilIdle()

        assertNotNull(commitResult)
        assertTrue(commitResult!!.succeeded)
        assertEquals(12, backend.currentIndex())
        assertEquals(80, commitResult!!.actualPercent)
        assertEquals(0, backend.openPanelCount)
    }

    @Test
    fun directSetNoOpFallsBackToSilentSteppedAdjustWithoutPopup() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val backend = FakeMediaVolumeBackend(
            initialIndex = 3,
            minVol = 0,
            maxVol = 15,
            allowSetStreamVolume = false,
            allowStepAdjust = true,
            stepSize = 1
        )
        val controllerScope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + dispatcher)
        val controller = MediaVolumeController(
            backend = backend,
            scope = controllerScope,
            ioDispatcher = dispatcher,
            mainDispatcher = dispatcher
        )

        var commitResult: MediaVolumeCommitResult? = null
        controller.commitPercentAsync(60) { commitResult = it }
        advanceUntilIdle()

        assertNotNull(commitResult)
        assertTrue(commitResult!!.succeeded)
        assertEquals(9, backend.currentIndex())
        assertEquals(60, commitResult!!.actualPercent)
        assertEquals(0, backend.openPanelCount)
    }

    @Test
    fun quantizedFineStepReadbackAndDynamicRouteBoundsAreAccepted() = runTest {
        assertTrue(
            isVolumeReadbackAcceptable(
                before = 30,
                target = 73,
                after = 70,
                minVol = 0,
                maxVol = 150
            )
        )
        assertFalse(
            isVolumeReadbackAcceptable(
                before = 30,
                target = 73,
                after = 30,
                minVol = 0,
                maxVol = 150
            )
        )

        val dispatcher = StandardTestDispatcher(testScheduler)
        val backend = FakeMediaVolumeBackend(
            initialIndex = 5,
            minVol = 0,
            maxVol = 15,
            quantizeStep = 10
        )
        val controllerScope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + dispatcher)
        val controller = MediaVolumeController(
            backend = backend,
            scope = controllerScope,
            ioDispatcher = dispatcher,
            mainDispatcher = dispatcher
        )

        // Route switches dynamically to 150-step Bluetooth/ColorOS output after first read.
        assertEquals(33, controller.getPercent())
        backend.maxVol = 150
        backend.index = 30

        var commitResult: MediaVolumeCommitResult? = null
        controller.commitPercentAsync(50) { commitResult = it }
        advanceUntilIdle()

        assertNotNull(commitResult)
        assertTrue(commitResult!!.succeeded)
        assertEquals(70, backend.currentIndex())
        assertEquals(47, commitResult!!.actualPercent)
        assertEquals(0, backend.openPanelCount)
    }

    @Test
    fun fixedOrBlockedVolumeFailsExplicitlyWithoutOpeningSystemVolumePanel() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixedBackend = FakeMediaVolumeBackend(initialIndex = 7, volumeFixed = true)
        val controllerScope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + dispatcher)
        val fixedController = MediaVolumeController(
            backend = fixedBackend,
            scope = controllerScope,
            ioDispatcher = dispatcher,
            mainDispatcher = dispatcher
        )
        var fixedResult: MediaVolumeCommitResult? = null
        fixedController.commitPercentAsync(90) { fixedResult = it }
        advanceUntilIdle()

        assertNotNull(fixedResult)
        assertFalse(fixedResult!!.succeeded)
        assertEquals(47, fixedResult!!.actualPercent)
        assertTrue(fixedResult!!.reason.contains("固定音量"))
        assertEquals(0, fixedBackend.openPanelCount)

        val blockedBackend = FakeMediaVolumeBackend(
            initialIndex = 4,
            allowSetStreamVolume = false,
            allowStepAdjust = false
        )
        val blockedController = MediaVolumeController(
            backend = blockedBackend,
            scope = controllerScope,
            ioDispatcher = dispatcher,
            mainDispatcher = dispatcher
        )
        var blockedResult: MediaVolumeCommitResult? = null
        blockedController.commitPercentAsync(80) { blockedResult = it }
        advanceUntilIdle()

        assertNotNull(blockedResult)
        assertFalse(blockedResult!!.succeeded)
        assertEquals(27, blockedResult!!.actualPercent)
        assertTrue(blockedResult!!.reason.contains("音量图标"))
        assertEquals(0, blockedBackend.openPanelCount)
    }

    @Test
    fun keyboardSlidePickerResolvesNeighborSlideAndSupportsAbortAndStationarySnap() {
        val row2 = listOf('A', 'S', 'D', 'F')
        val bounds = listOf(
            SlidePickerKeyBounds(SlidePickerKeySpec("q_A", "A", 'a', row2, true), 0f, 0f, 40f, 40f),
            SlidePickerKeyBounds(SlidePickerKeySpec("q_S", "S", 's', row2, true), 44f, 0f, 84f, 40f),
            SlidePickerKeyBounds(SlidePickerKeySpec("q_D", "D", 'd', row2, true), 88f, 0f, 128f, 40f),
            SlidePickerKeyBounds(SlidePickerKeySpec("q_F", "F", 'f', row2, true), 132f, 0f, 172f, 40f)
        )

        // Direct hit & tight 4dp gap bridge without falling through between adjacent keys.
        assertEquals("q_A", resolveSlidePickerHit(20f, 20f, bounds, gapSnapPx = 8f)?.spec?.id)
        assertEquals("q_S", resolveSlidePickerHit(42.5f, 20f, bounds, gapSnapPx = 8f)?.spec?.id)

        // Sliding far off the keypad (or onto Backspace/margin) returns null to abort cleanly.
        assertNull(resolveSlidePickerHit(20f, -20f, bounds, gapSnapPx = 8f))
        assertNull(resolveSlidePickerHit(195f, 20f, bounds, gapSnapPx = 8f))

        // Explicit slide onto 's' commits exact 's' even when only 'd' is reachable.
        val specS = bounds[1].spec
        assertEquals(
            's',
            resolveSlidePickerCommitChar(
                hovered = specS,
                didSlideAcrossKeys = true,
                reachableKeys = setOf('d'),
                activeSide = TriggerSide.RIGHT
            )
        )

        // Stationary single-key tap on 's' still assists toward reachable 'd'.
        assertEquals(
            'd',
            resolveSlidePickerCommitChar(
                hovered = specS,
                didSlideAcrossKeys = false,
                reachableKeys = setOf('d'),
                activeSide = TriggerSide.RIGHT
            )
        )

        // T9 key slide commits exact digit on release.
        val t9Spec = SlidePickerKeySpec("t9_5", "5", '5')
        assertEquals(
            '5',
            resolveSlidePickerCommitChar(
                hovered = t9Spec,
                didSlideAcrossKeys = true,
                reachableKeys = setOf('2', '5'),
                activeSide = TriggerSide.LEFT
            )
        )
        assertNull(KeyboardSlidePickerState().hoveredKey)
    }
}
