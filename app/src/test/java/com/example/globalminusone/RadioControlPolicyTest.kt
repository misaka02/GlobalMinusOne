package com.example.globalminusone.system

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import com.example.globalminusone.system.media.RadioState
import com.example.globalminusone.system.media.parseBluetoothManagerState
import com.example.globalminusone.system.media.parseWifiShellState
import com.example.globalminusone.system.media.verifyRadioChange

class RadioControlPolicyTest {
    @Test fun currentBluetoothStateIsNotAnEnabledPreferenceOrPastLog() {
        assertEquals(RadioState.On, parseBluetoothManagerState("Bluetooth Status\n  enabled: true\n  state: ON\n  name: Synthetic\n\nEnable log: OFF"))
        assertEquals(RadioState.Off, parseBluetoothManagerState("Bluetooth Status\n  enabled: false\n  state: 10\n\nEnable log: ON"))
        assertEquals(RadioState.Unknown, parseBluetoothManagerState("enabled=true\nbluetooth_on=1\nstate: ON"))
        assertEquals(RadioState.Unknown, parseBluetoothManagerState("Bluetooth Status\n  enabled: true\n\nHistory\nstate: ON"))
        assertEquals(RadioState.Unknown, parseBluetoothManagerState("Bluetooth Status\n  state: BLE_ON"))
        assertEquals(RadioState.TurningOn, parseBluetoothManagerState("Bluetooth Status\n  state: TURNING_ON"))
    }
    @Test fun wifiStatusMustBeRecognizedAndUnambiguous() {
        assertEquals(RadioState.On, parseWifiShellState("Wifi is enabled\nWifi scanning is always available"))
        assertEquals(RadioState.Off, parseWifiShellState("Wifi is disabled"))
        assertEquals(RadioState.Unknown, parseWifiShellState("Wifi is enabled\nWifi is disabled"))
        assertEquals(RadioState.Unknown, parseWifiShellState("command succeeded"))
    }
    @Test fun successfulCommandCannotInventTheRequestedState() = runBlocking {
        val result = verifyRadioChange(true, { true }, { RadioState.Off }, attempts = 3, waitStep = {})
        assertFalse(result.succeeded)
    }
    @Test fun pendingCommandSucceedsOnlyAfterAnObservedTerminalState() = runBlocking {
        val states = ArrayDeque(listOf(RadioState.Off, RadioState.TurningOn, RadioState.On))
        val result = verifyRadioChange(true, { true }, { states.removeFirst() }, attempts = 3, waitStep = {})
        assertTrue(result.succeeded)
        assertTrue(states.isEmpty())
    }
    @Test fun abnormalCommandExitStillVerifiesTheActualAsynchronousState() = runBlocking {
        val states = ArrayDeque(listOf(RadioState.Unknown, RadioState.TurningOff, RadioState.Off))
        var commands = 0
        val result = verifyRadioChange(false, { commands++; false }, { states.removeFirst() }, attempts = 3, waitStep = {})
        assertTrue(result.succeeded)
        assertEquals("命令返回异常；系统状态已核对", result.message)
        assertEquals(1, commands)
        assertTrue(states.isEmpty())
        val restored = verifyRadioChange(true, { false }, { RadioState.On }, attempts = 1, waitStep = {})
        assertTrue(restored.succeeded)
        assertEquals("命令返回异常；系统状态已核对", restored.message)
    }
    @Test fun abnormalCommandExitCannotInventTheTargetState() = runBlocking {
        var reads = 0
        val result = verifyRadioChange(true, { false }, { reads++; RadioState.Off }, attempts = 3, waitStep = {})
        assertFalse(result.succeeded)
        assertEquals(3, reads)
        assertTrue(result.message.contains("未确认目标状态"))
        assertFalse(verifyRadioChange(false, { false }, { RadioState.Unknown }, attempts = 2, waitStep = {}).succeeded)
    }
    @Test fun missingHardwareNeverBecomesASuccessfulSwitch() = runBlocking {
        assertFalse(verifyRadioChange(true, { true }, { RadioState.Unsupported }, waitStep = {}).succeeded)
        assertFalse(verifyRadioChange(true, { false }, { RadioState.Unsupported }, waitStep = {}).succeeded)
    }
}
