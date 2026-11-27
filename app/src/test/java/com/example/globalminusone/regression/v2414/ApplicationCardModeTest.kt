package com.example.globalminusone.regression.v2414

import com.example.globalminusone.MemoryPreferences
import com.example.globalminusone.data.*
import com.example.globalminusone.ui.panel.cards.applicationCard
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import com.example.globalminusone.data.AppLaunchMode
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.operations.findComponent

class ApplicationCardModeTest {
    @Test fun olderAppCardsRemainNormalWithoutMigration() {
        val item = Json.decodeFromString<ComponentItem>("""{"id":"old","type":"APP_ICON","packageName":"test.old"}""")
        assertEquals(AppLaunchMode.NORMAL, item.appLaunchMode)
        assertEquals("test.old", item.packageName)
    }
    @Test fun floatingOnlyCardSurvivesNestedPersistenceAndProfileApplication() {
        val prefs = MemoryPreferences()
        val repository = MinusOneRepository(prefs)
        val pageId = repository.state.value.pages.first().id
        val folder = ComponentItem(id = "folder", type = ComponentType.FOLDER)
        assertTrue(repository.placeMenuComponent(pageId, null, folder))
        val item = applicationCard("test.app", "测试", AppLaunchMode.FLOATING)
        assertTrue(repository.placeMenuComponent(pageId, folder.id, item))
        val profile = repository.createConfigProfile("小窗应用")
        val reloaded = MinusOneRepository(prefs)
        assertEquals(AppLaunchMode.FLOATING, reloaded.state.value.pages.first().components.findComponent(item.id)!!.appLaunchMode)
        reloaded.removeComponent(0, folder.id)
        reloaded.applyConfigProfile(profile.id)
        assertEquals(AppLaunchMode.FLOATING, reloaded.state.value.pages.first().components.findComponent(item.id)!!.appLaunchMode)
    }
    @Test fun deletedAsyncTargetCannotTurnFloatingCardIntoATopLevelNormalCard() {
        val repository = MinusOneRepository(MemoryPreferences())
        val pageId = repository.state.value.pages.first().id
        val item = applicationCard("test.app", "测试", AppLaunchMode.FLOATING)
        val before = repository.state.value
        assertFalse(repository.placeMenuComponent(pageId, "removed-container", item))
        assertEquals(before, repository.state.value)
    }
}
