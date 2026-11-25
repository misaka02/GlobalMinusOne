package com.example.globalminusone.service

import org.junit.Assert.*
import org.junit.Test

class PanelSystemKeyTest {
    @Test fun visibleImeSequenceMatchesZeroDownTimeAndConsumesOnlyOneUp() {
        val state = PanelImeBackState()
        assertFalse(state.matches(0L))
        assertFalse(state.consumeUp(0L))
        state.recordDown(0) { 0L }
        assertTrue(state.matches(0L))
        assertTrue(state.matches(0L)) // Pre-IME reads must not consume the sequence.
        assertTrue(state.consumeUp(0L))
        assertFalse(state.matches(0L))
        assertFalse(state.consumeUp(0L))
    }

    @Test fun invisibleImeDownStoresNullAndDoesNotConsumeTheBackSequence() {
        val state = PanelImeBackState()
        state.recordDown(0) { 17L }
        state.recordDown(0) { null }
        assertFalse(state.matches(17L))
        assertFalse(state.matches(0L))
        assertFalse(state.consumeUp(17L))
    }

    @Test fun repeatDownDoesNotResampleImeOrReplaceTheInitialDownTime() {
        val state = PanelImeBackState()
        var samples = 0
        state.recordDown(1) { samples++; 9L }
        assertEquals(0, samples)
        assertFalse(state.matches(9L))
        state.recordDown(0) { samples++; 20L }
        state.recordDown(1) { samples++; null }
        state.recordDown(2) { samples++; 99L }
        assertEquals(1, samples)
        assertTrue(state.matches(20L))
        assertFalse(state.matches(99L))
        assertTrue(state.consumeUp(20L))
    }

    @Test fun mismatchedUpClearsThePriorSequenceBeforeReturningFalse() {
        val state = PanelImeBackState()
        state.recordDown(0) { 31L }
        assertFalse(state.consumeUp(32L))
        assertFalse(state.matches(31L))
        assertFalse(state.consumeUp(31L))
    }

    @Test fun inputOwnershipResetBlocksOldUpAndAllowsTheNextImeSequence() {
        val state = PanelImeBackState()
        state.recordDown(0) { 41L }
        state.reset()
        assertFalse(state.consumeUp(41L))
        state.reset()
        state.recordDown(0) { 42L }
        assertTrue(state.consumeUp(42L))
    }

    @Test fun nextInitialDownReplacesThePreviousTimeEvenAfterAnUnmatchedSequence() {
        val state = PanelImeBackState()
        state.recordDown(0) { 51L }
        state.recordDown(0) { 52L }
        assertFalse(state.matches(51L))
        assertTrue(state.matches(52L))
        assertTrue(state.consumeUp(52L))
        state.recordDown(0) { null }
        state.recordDown(0) { 53L }
        assertTrue(state.consumeUp(53L))
    }

    @Test fun failedImeSamplingPropagatesAndLeavesThePreviouslyRecordedTimeIntact() {
        val state = PanelImeBackState()
        val failure = IllegalStateException("synthetic inset read failure")
        state.recordDown(0) { 61L }
        assertSame(failure, runCatching { state.recordDown(0) { throw failure } }.exceptionOrNull())
        assertTrue(state.matches(61L))
        assertTrue(state.consumeUp(61L))
    }
}
