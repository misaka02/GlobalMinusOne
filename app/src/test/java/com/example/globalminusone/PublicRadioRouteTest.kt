package com.example.globalminusone

import com.example.globalminusone.system.media.RadioKind
import com.example.globalminusone.system.media.supportsPublicRadioToggle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicRadioRouteTest {
    @Test fun wifiUsesSystemControlStartingAtQ() {
        assertTrue(supportsPublicRadioToggle(RadioKind.WIFI, 28, false))
        assertFalse(supportsPublicRadioToggle(RadioKind.WIFI, 29, true))
        assertFalse(supportsPublicRadioToggle(RadioKind.WIFI, 36, true))
    }
    @Test fun bluetoothRequiresConnectOnSAndNeverUsesToggleOnT() {
        assertTrue(supportsPublicRadioToggle(RadioKind.BLUETOOTH, 30, false))
        assertFalse(supportsPublicRadioToggle(RadioKind.BLUETOOTH, 31, false))
        assertTrue(supportsPublicRadioToggle(RadioKind.BLUETOOTH, 32, true))
        assertFalse(supportsPublicRadioToggle(RadioKind.BLUETOOTH, 33, true))
        assertFalse(supportsPublicRadioToggle(RadioKind.BLUETOOTH, 36, true))
    }
}
