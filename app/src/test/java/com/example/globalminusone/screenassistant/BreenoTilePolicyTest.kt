package com.example.globalminusone.screenassistant

import com.example.globalminusone.system.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class BreenoTilePolicyTest {
    private val target = "com.coloros.directui/com.coloros.directui.ExampleRecognitionTile"
    @After fun reset() = UserRootSession.resetForTests()

    @Test fun rejectsVoiceAssistantScannerAndShellSyntax() {
        listOf("小布助手", "扫一扫", "识屏设置", "Breeno").forEach { assertFalse(BreenoTilePolicy.isRecognitionLabel(it)) }
        listOf("com.heytap.speechassist/.Tile", "com.coloros.directui/.Tile;id", "com.coloros.directui/.Tile\n", "com.coloros.directui/.Tile/other").forEach {
            assertFalse(BreenoTilePolicy.validComponent(it))
            assertTrue(runCatching { BreenoTilePolicy.command(it) }.isFailure)
        }
    }

    @Test fun commandChecksCapabilityAndAddedTileWithoutChangingSettings() {
        val cmd = BreenoTilePolicy.command(target)
        assertTrue(cmd.indexOf("sysui_qs_tiles") < cmd.indexOf("cmd statusbar click-tile"))
        assertTrue(cmd.contains("custom(com.coloros.directui/.ExampleRecognitionTile)"))
        assertTrue(cmd.contains("statusbar help"))
        listOf("monkey", "start-service", "android.intent.action.ASSIST", "add-tile", "settings put").forEach { assertFalse(cmd.contains(it)) }
    }

    @Test fun exitZeroAndPreloadAreNotRecognitionDispatchAcknowledgements() {
        assertFalse(BreenoTilePolicy.requestWasSent(0, ""))
        assertFalse(BreenoTilePolicy.requestWasSent(0, "Starting service: Intent"))
        assertFalse(BreenoTilePolicy.requestWasSent(0, BreenoTilePolicy.sentMarker, "Error"))
        assertFalse(BreenoTilePolicy.requestWasSent(71, BreenoTilePolicy.sentMarker))
        assertTrue(BreenoTilePolicy.requestWasSent(0, BreenoTilePolicy.sentMarker + "\n"))
    }

    @Test fun rootRejectsUnknownTargetBeforeAuthorizationAndReusesLease() = runBlocking {
        var authorizations = 0
        var output = ""
        UserRootSession.resetForTests(RootExecutor { r ->
            if (r.entryMarker == null) { authorizations++; RootProcessOutcome(0, "0".toByteArray()) }
            else RootProcessOutcome(0, output.toByteArray(), "${r.entryMarker}:0\n")
        }, { "/synthetic/su" })
        assertFalse(UserRootSession.runBreenoDirectCommand(listOf("com.coloros.directui")).succeeded)
        assertEquals(0, authorizations)
        UserRootSession.requestAuthorization()
        assertFalse(UserRootSession.runBreenoDirectCommand(listOf("com.coloros.directui"), target).succeeded)
        output = BreenoTilePolicy.sentMarker
        assertTrue(UserRootSession.runBreenoDirectCommand(listOf("com.coloros.directui"), target).succeeded)
        UserRootSession.requestAuthorization()
        assertEquals(1, authorizations)
    }
}
