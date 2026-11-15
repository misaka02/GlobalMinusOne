package com.example.globalminusone.regression.v2414

import com.example.globalminusone.split.*
import org.junit.Assert.*
import org.junit.Test

class SplitSystemProtocolTest {
    @Test fun typedWireRejectsShellPayloadsAndUnexpectedFields() {
        for (component in listOf("app.target/.Main;id", "app.target/.Main\nreboot", "app.target/.Main 'x'", "--user/0"))
            assertThrows(IllegalArgumentException::class.java) { SplitSystemProtocol.decode(3, 0, component, 0) }
        assertThrows(IllegalArgumentException::class.java) { SplitSystemProtocol.decode(0, 11, null, 0) }
        assertThrows(IllegalArgumentException::class.java) { SplitSystemProtocol.decode(4, 0, null, 0) }
        assertThrows(IllegalArgumentException::class.java) { SplitSystemProtocol.decode(5, 11, "app.target/.Main", 0) }
        assertThrows(IllegalArgumentException::class.java) { SplitSystemProtocol.decode(6, 11, null, 0) }
    }
    @Test fun validCommandsNeverContainToggleSettingsRemountOrTaskKill() {
        val operations = listOf(SplitSystemRequest.Help, SplitSystemRequest.Snapshot, SplitSystemRequest.Focus(11),
            SplitSystemRequest.StartTarget("app.target/.Main", 0), SplitSystemRequest.MoveToSide(22), SplitSystemRequest.RemoveFromSide(22))
        for (request in operations) {
            assertEquals(request, SplitSystemProtocol.decode(SplitSystemProtocol.op(request), SplitSystemProtocol.taskId(request), SplitSystemProtocol.component(request), SplitSystemProtocol.userId(request)))
            val command = SplitSystemProtocol.command(request)
            assertFalse(command.contains("settings put")); assertFalse(command.contains("remount"))
            assertFalse(command.contains("kill")); assertFalse(command.contains("toggle")); assertFalse(command.contains("startTasks"))
        }
    }
    @Test fun systemRejectionWithExitZeroIsStillAFailure() {
        assertFalse(SplitSystemProtocol.sanitize(SplitSystemRequest.MoveToSide(22), SplitSystemResult(0, "Invalid command: moveToSideStage", "")).succeeded)
        assertFalse(SplitSystemProtocol.sanitize(SplitSystemRequest.StartTarget("app.target/.Main", 0), SplitSystemResult(0, "Error: permission denied", "")).succeeded)
        assertFalse(SplitSystemProtocol.sanitize(SplitSystemRequest.Snapshot, SplitSystemResult(0, "not a task dump", "")).succeeded)
    }
}
