package com.example.globalminusone.screenassistant

import com.example.globalminusone.data.ScreenAssistantConfig
import com.example.globalminusone.data.ScreenOcrEngine
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ScreenAssistantCompatibilityTest {
    @Test fun existingDownloadedModelSelectionStillRestoresToLocalOcr() {
        val config = Json.decodeFromString<ScreenAssistantConfig>("""{"ocrEngine":"MLKIT_UNBUNDLED"}""")
        assertEquals(ScreenOcrEngine.MLKIT_UNBUNDLED, config.ocrEngine)
    }

    @Test fun oldExplicitFallbackSettingRestoresWithoutChangingBuiltinOrBreenoActions() {
        val config = Json.decodeFromString<ScreenAssistantConfig>("""{"breenoAutoFallbackToBuiltin":true,"breenoUsePrivilegedShell":true}""")
        assertEquals(true, config.breenoUsePrivilegedShell)
        assertFalse(ScreenAssistantConfig().breenoAutoFallbackToBuiltin)
    }
}
