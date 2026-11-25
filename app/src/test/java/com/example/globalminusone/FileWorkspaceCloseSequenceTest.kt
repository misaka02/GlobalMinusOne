package com.example.globalminusone

import com.example.globalminusone.ui.panel.FileWorkspaceCloseSequence
import org.junit.Assert.*
import org.junit.Test

class FileWorkspaceCloseSequenceTest {
    @Test fun dirtyDecisionsAdvanceInPaneOrderOnce() {
        val sequence = FileWorkspaceCloseSequence()
        val token = sequence.begin()!!
        assertNull(sequence.begin())
        assertFalse(sequence.accept(token, 1))
        assertTrue(sequence.accept(token, 0))
        assertFalse(sequence.accept(token, 0))
        assertTrue(sequence.accept(token, 1))
        assertTrue(sequence.isPending(token, 2))
        sequence.cancel(token) // final completion consumes the attempt before onDismiss
        assertFalse(sequence.isPending(token, 2))
        assertFalse(sequence.accept(token, 1))
    }

    @Test fun cancelledDecisionCannotAdvanceOrCancelNewAttempt() {
        val sequence = FileWorkspaceCloseSequence()
        val old = sequence.begin()!!
        sequence.cancel(old)
        assertFalse(sequence.accept(old, 0)) // ready after cancel
        val current = sequence.begin()!!
        assertFalse(sequence.accept(old, 0))
        sequence.cancel(old)
        assertTrue(sequence.accept(current, 0))
    }

    @Test fun collapseInvalidatesPendingDecisionButAllowsLaterClose() {
        val sequence = FileWorkspaceCloseSequence()
        val old = sequence.begin()!!
        sequence.invalidate()
        assertFalse(sequence.accept(old, 0))
        assertNotNull(sequence.begin())
    }

    @Test fun disposalRejectsRetainedRegistrationAndPendingDecision() {
        val sequence = FileWorkspaceCloseSequence()
        val old = sequence.begin()!!
        sequence.dispose()
        assertFalse(sequence.accept(old, 0))
        assertNull(sequence.begin())
    }

    @Test fun replacingPendingHandlerInvalidatesItsAlreadyIssuedReady() {
        val sequence = FileWorkspaceCloseSequence()
        val old = sequence.begin()!!
        assertTrue(sequence.accept(old, 0))
        sequence.invalidate() // pending pane's handler is removed/replaced
        assertFalse(sequence.accept(old, 1))
        val current = sequence.begin()!!
        assertTrue(sequence.isPending(current, 0))
    }
}
