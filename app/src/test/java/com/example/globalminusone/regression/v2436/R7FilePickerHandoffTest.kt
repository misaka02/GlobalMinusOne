package com.example.globalminusone.regression.v2436

import com.example.globalminusone.data.*
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class R7FilePickerHandoffTest {
    @Test fun openingPanelFalseFlagDoesNotCountAsAcknowledgementOfTheNewCloseRequest() = runTest {
        val expanded = MutableStateFlow(false)
        val generation = MutableStateFlow(3L)
        val waiting = async { awaitFilePickerPanelRelease(expanded, generation, 3L) }
        runCurrent()
        advanceTimeBy(1_201)
        assertFalse(waiting.isCompleted) // the old 1200ms budget must not launch a covered picker.
        generation.value = 4L
        runCurrent()
        assertTrue(waiting.await())
    }

    @Test fun aNewCloseAcknowledgementStillRequiresThePanelToBeActuallyClosed() = runTest {
        val expanded = MutableStateFlow(true)
        val generation = MutableStateFlow(1L)
        val waiting = async { awaitFilePickerPanelRelease(expanded, generation, 1L) }
        runCurrent()
        generation.value = 2L
        runCurrent()
        assertFalse(waiting.isCompleted)
        expanded.value = false
        runCurrent()
        assertTrue(waiting.await())
    }

    @Test fun timeoutReturnsFailureAndCancellationCannotReturnAnInstructionToLaunch() = runTest {
        val expanded = MutableStateFlow(true)
        val generation = MutableStateFlow(0L)
        val timed = async { awaitFilePickerPanelRelease(expanded, generation, 0L, timeoutMillis = 10) }
        advanceTimeBy(11)
        assertFalse(timed.await())
        val cancelled = async { awaitFilePickerPanelRelease(expanded, generation, 0L) }
        runCurrent()
        cancelled.cancel()
        expanded.value = false; generation.value = 1L
        runCurrent()
        assertTrue(cancelled.isCancelled)
    }

    @Test fun repeatedFalsePublicationProducesFreshClosureAcknowledgements() {
        val repository = MinusOneRepository(com.example.globalminusone.MemoryPreferences())
        val before = repository.panelCloseGeneration.value
        repository.setPanelExpanded(false)
        repository.setPanelExpanded(false)
        assertEquals(before + 2, repository.panelCloseGeneration.value)
        assertFalse(repository.isPanelExpanded.value)
    }
}
