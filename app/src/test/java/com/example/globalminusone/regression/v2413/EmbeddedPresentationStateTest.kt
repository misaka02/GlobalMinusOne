package com.example.globalminusone.regression.v2413

import com.example.globalminusone.browser.EmbeddedPresentationState
import org.junit.Assert.*
import org.junit.Test

class EmbeddedPresentationStateTest {
    @Test fun stalePanelResumeCannotOverrideHiddenHost() {
        val state = EmbeddedPresentationState()
        state.hostVisible = false
        state.change(state.requestedSuspension)
        val hiddenRevision = state.revision
        state.panelSuspended = false
        assertTrue(state.requestedSuspension)
        assertFalse(state.confirmAttached(hiddenRevision))
        state.hostVisible = true
        assertFalse(state.requestedSuspension)
        state.panelSuspended = true
        assertTrue(state.requestedSuspension)
    }

    @Test fun minimizedPresentationStopsPollingButKeepsAuthorizedSession() {
        val state = EmbeddedPresentationState()
        state.initialOutput()
        assertTrue(state.attached)
        state.change(true)
        assertFalse(state.attached)
        assertTrue(state.keepOnHostLoss(false))
        assertFalse(state.shouldHeartbeat(opened = true, shown = false))
        assertFalse(state.shouldHeartbeat(opened = false, shown = false))
    }

    @Test fun lateRestoreAcknowledgementCannotUndoANewerMinimize() {
        val state = EmbeddedPresentationState()
        state.change(true); state.change(false)
        val oldRestore = state.revision
        state.change(true)
        assertFalse(state.confirmAttached(oldRestore))
        assertFalse(state.attached)
        state.change(false)
        assertTrue(state.confirmAttached(state.revision))
        assertTrue(state.attached)
    }

    @Test fun destroyedOutputAndFinalCloseInvalidatePendingAttachCallbacks() {
        val state = EmbeddedPresentationState()
        val oldSurface = state.revision
        state.outputLost()
        assertFalse(state.confirmAttached(oldSurface))
        val replacement = state.revision
        state.closed()
        assertFalse(state.confirmAttached(replacement))
        assertFalse(state.attached)
    }

    @Test fun authorizationHideDoesNotBecomeANormalHostClose() {
        val state = EmbeddedPresentationState()
        assertTrue(state.keepOnHostLoss(awaitingAuthorization = true))
        assertFalse(state.keepOnHostLoss(awaitingAuthorization = false))
        state.change(true); state.initialOutput()
        assertFalse(state.attached)
        state.change(false)
        assertFalse(state.shouldHeartbeat(opened = true, shown = false))
        assertTrue(state.shouldHeartbeat(opened = true, shown = true))
    }

    @Test fun finalDetachClearsMinimizedStateAndCannotBeKeptAliveOrReattachedByOldAck() {
        val state = EmbeddedPresentationState()
        state.change(true)
        val pending = state.revision
        state.disposed()
        assertFalse(state.suspended)
        assertFalse(state.attached)
        assertFalse(state.keepOnHostLoss(awaitingAuthorization = false))
        assertFalse(state.shouldHeartbeat(opened = false, shown = false))
        assertFalse(state.confirmAttached(pending))
    }
}
