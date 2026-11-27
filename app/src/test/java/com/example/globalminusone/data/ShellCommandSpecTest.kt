package com.example.globalminusone.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.globalminusone.data.policy.ShellCommandSpec
import com.example.globalminusone.data.policy.ShellExecutionMode

class ShellCommandSpecTest {

    @Test
    fun shellCommandSpec_roundTripsThroughIntentUriAndFallsBackToLegacyTextContent() {
        val original = ShellCommandSpec(
            command = "dumpsys window | head -n 5",
            mode = ShellExecutionMode.SHIZUKU,
            requireConfirm = true,
            timeoutSec = 25,
            showOutputDialog = true,
            preferCompactTile = true
        )
        val encoded = original.encodeToIntentUri()
        assertTrue(encoded.startsWith(ShellCommandSpec.INTENT_PREFIX))

        val item = ComponentItem(
            id = "shell_1",
            type = ComponentType.SHELL_COMMAND,
            title = "窗口焦点",
            textContent = original.command,
            intentUri = encoded
        )
        val decoded = ShellCommandSpec.fromComponent(item)
        assertEquals(original, decoded)

        // Legacy item containing only textContent and empty intentUri defaults safely to NORMAL mode.
        val legacyItem = ComponentItem(
            id = "shell_legacy",
            type = ComponentType.SHELL_COMMAND,
            title = "Legacy",
            textContent = "printf synthetic"
        )
        val legacyDecoded = ShellCommandSpec.fromComponent(legacyItem)
        assertEquals("printf synthetic", legacyDecoded.command)
        assertEquals(ShellExecutionMode.NORMAL, legacyDecoded.mode)
        assertFalse(legacyDecoded.preferCompactTile)
    }

    @Test
    fun isHighRiskCommand_flagsDestructivePatternsWithoutFalsePositives() {
        assertTrue(ShellCommandSpec.isHighRiskCommand("rm -rf /sdcard/Test"))
        assertTrue(ShellCommandSpec.isHighRiskCommand("reboot bootloader"))
        assertTrue(ShellCommandSpec.isHighRiskCommand("dd if=/dev/zero of=/dev/block/sda"))
        assertTrue(ShellCommandSpec.isHighRiskCommand("pm uninstall com.android.systemui"))

        assertFalse(ShellCommandSpec.isHighRiskCommand("cat /proc/meminfo | head -n 10"))
        assertFalse(ShellCommandSpec.isHighRiskCommand("dumpsys battery"))
        assertFalse(ShellCommandSpec.isHighRiskCommand("ping -c 2 223.5.5.5"))
        assertFalse(ShellCommandSpec.isHighRiskCommand("   "))
    }
}
