package com.example.globalminusone.system.media

import kotlinx.coroutines.delay

enum class RadioKind { WIFI, BLUETOOTH }
enum class RadioState { Unsupported, Unknown, Off, TurningOn, On, TurningOff }
data class RadioControlResult(val succeeded: Boolean, val message: String)

/** Modern target SDK: Wi-Fi Q+, Bluetooth T+ require system/privileged control. */
internal fun supportsPublicRadioToggle(kind: RadioKind, sdk: Int, bluetoothConnectGranted: Boolean): Boolean =
    if (kind == RadioKind.WIFI) sdk < 29 else sdk < 33 && (sdk < 31 || bluetoothConnectGranted)

/** Parse only the manager's current-state header, never historical logs, preferences or device names. */
fun parseBluetoothManagerState(output: String): RadioState {
    val header = output.substringAfter("Bluetooth Status", "").lineSequence().dropWhile { it.isBlank() }.take(10)
        .takeWhile { it.isNotBlank() }.toList()
    val state = header.firstNotNullOfOrNull { Regex("^\\s*state:\\s*(\\S+)\\s*$").matchEntire(it)?.groupValues?.get(1) }
    return when (state) {
        "ON", "12" -> RadioState.On
        "OFF", "10" -> RadioState.Off
        "TURNING_ON", "11" -> RadioState.TurningOn
        "TURNING_OFF", "13" -> RadioState.TurningOff
        else -> RadioState.Unknown
    }
}

fun parseWifiShellState(output: String): RadioState {
    val states = output.lineSequence().map { it.trim() }
        .filter { it == "Wifi is enabled" || it == "Wifi is disabled" }.distinct().toList()
    return when (states.singleOrNull()) {
        "Wifi is enabled" -> RadioState.On
        "Wifi is disabled" -> RadioState.Off
        else -> RadioState.Unknown
    }
}

/** A command's exit status cannot confirm or disprove the final hardware state. */
internal suspend fun verifyRadioChange(targetOn: Boolean, command: suspend () -> Boolean,
    observe: suspend () -> RadioState, attempts: Int = 16, waitStep: suspend () -> Unit = { delay(400) }): RadioControlResult {
    val accepted = command()
    repeat(attempts) { index ->
        val state = observe()
        if (state == if (targetOn) RadioState.On else RadioState.Off)
            return RadioControlResult(true, if (accepted) "已由系统状态确认" else "命令返回异常；系统状态已核对")
        if (state == RadioState.Unsupported) return RadioControlResult(false, "设备没有此控制能力")
        if (index < attempts - 1) waitStep()
    }
    return RadioControlResult(false, if (accepted) "未在时限内确认系统状态，请刷新检查" else "系统命令返回异常，且未确认目标状态，请刷新检查")
}
