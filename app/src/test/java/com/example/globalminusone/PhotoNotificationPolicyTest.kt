package com.example.globalminusone

import com.example.globalminusone.service.overlay.NotificationEventGate
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.AdvancedConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Test

class PhotoNotificationPolicyTest {
    @Test fun notificationDedupExpiresAndBoundsMemory() {
        val gate = NotificationEventGate()
        assertTrue(gate.accept(1, 0))
        assertFalse(gate.accept(1, 100))
        assertTrue(gate.accept(1, 30_000))
        repeat(200) { gate.accept(it + 2, 30_001) }
        assertTrue(gate.accept(1, 30_002))
        gate.clear()
        assertTrue(gate.accept(1, 30_003))
    }
    @Test fun oldConfigurationKeepsNewFeaturesDisabled() {
        val config = Json.decodeFromString<AdvancedConfig>("{}")
        assertFalse(config.notificationPreferences.reminderEnabled)
        assertFalse(config.notificationPreferences.autoJumpEnabled)
        assertTrue(config.notificationPreferences.allowedPackages.isEmpty())
        val card = Json.decodeFromString<ComponentItem>("""{"type":"PHOTO_CUBE"}""")
        assertTrue(card.photoCubeUris.isEmpty())
        assertEquals(5, card.photoCubeIntervalSeconds)
    }
}
