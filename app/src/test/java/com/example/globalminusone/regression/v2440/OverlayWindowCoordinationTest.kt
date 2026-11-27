package com.example.globalminusone.regression.v2440

import com.example.globalminusone.service.overlay.OverlayWindowCoordination
import com.example.globalminusone.service.overlay.OverlayWindowCoordination.AuthorizationSource.APPLICATION_WINDOWS
import com.example.globalminusone.service.overlay.OverlayWindowCoordination.AuthorizationSource.SYSTEM_CONTROLS
import com.example.globalminusone.service.overlay.replaceWindowInputAdjustment
import org.junit.Assert.*
import org.junit.Test

class OverlayWindowCoordinationTest {
    @Test fun overlappingPermissionRequestsRestoreTheOriginalAlphaOnlyAfterBothFinish() {
        val state = OverlayWindowCoordination()
        assertTrue(state.transitionAuthorization(state.requestAuthorization(APPLICATION_WINDOWS, true), 0.65f))
        assertFalse(state.transitionAuthorization(state.requestAuthorization(SYSTEM_CONTROLS, true), 0f))
        assertFalse(state.transitionAuthorization(state.requestAuthorization(APPLICATION_WINDOWS, false), 0f))
        assertTrue(state.authorizationPending)
        assertEquals(0.65f, state.authorizationAlpha, 0f)
        assertTrue(state.transitionAuthorization(state.requestAuthorization(SYSTEM_CONTROLS, false), 0f))
        assertFalse(state.authorizationPending)
        assertEquals(0.65f, state.authorizationAlpha, 0f)
        // A later independent request captures the newly configured host alpha.
        assertTrue(state.transitionAuthorization(state.requestAuthorization(APPLICATION_WINDOWS, true), 0.8f))
        assertEquals(0.8f, state.authorizationAlpha, 0f)
    }

    @Test fun repeatedSourceNotificationsAreIdempotentAndDoNotNeedMatchingCounts() {
        val state = OverlayWindowCoordination()
        state.transitionAuthorization(state.requestAuthorization(APPLICATION_WINDOWS, true), 0.4f)
        assertFalse(state.transitionAuthorization(state.requestAuthorization(APPLICATION_WINDOWS, true), 0f))
        assertTrue(state.transitionAuthorization(state.requestAuthorization(APPLICATION_WINDOWS, false), 0f))
        assertFalse(state.transitionAuthorization(state.requestAuthorization(APPLICATION_WINDOWS, false), 0f))
        assertEquals(0.4f, state.authorizationAlpha, 0f)
    }

    @Test fun inputReleaseDuringCloseCannotReopenTheHostButDragCanInterruptClose() {
        val state = OverlayWindowCoordination()
        state.setRemoteInputOwner(true)
        state.beginClosing()
        state.setRemoteInputOwner(false)
        assertFalse(state.remoteOwnsInput)
        assertFalse(state.canRestoreHostFocus(visible = true, progress = 0.5f))
        state.beginOpening()
        assertTrue(state.canRestoreHostFocus(visible = true, progress = 0.5f))
        assertFalse(state.canRestoreHostFocus(visible = false, progress = 0.5f))
        assertFalse(state.canRestoreHostFocus(visible = true, progress = 0f))
    }

    @Test fun teardownCallbacksRemainObservableWithoutReplacingTheSavedAlpha() {
        val state = OverlayWindowCoordination()
        state.beginDestroying()
        assertTrue(state.transitionAuthorization(state.requestAuthorization(APPLICATION_WINDOWS, true), 0f))
        assertTrue(state.destroying)
        assertTrue(state.authorizationPending)
        assertEquals(1f, state.authorizationAlpha, 0f)
        state.transitionAuthorization(state.requestAuthorization(APPLICATION_WINDOWS, false), 0f)
        assertFalse(state.authorizationPending)
        assertTrue(state.destroying)
    }

    @Test fun workspaceImePolicyReplacesAdjustmentWithoutLosingOtherFlags() {
        // Android's input state/mask values passed as data: no android.jar calls.
        val priorMode = 0x100 or 0x5 or 0x20
        val nothing = replaceWindowInputAdjustment(priorMode, 0xf0, 0x30)
        assertEquals(0x135, nothing)
        assertEquals(0x115, replaceWindowInputAdjustment(nothing, 0xf0, 0x10))
        assertEquals(nothing, replaceWindowInputAdjustment(nothing, 0xf0, 0x30))
    }
}
