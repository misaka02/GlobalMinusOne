package com.example.globalminusone.regression.v2438

import com.example.globalminusone.MemoryPreferences
import com.example.globalminusone.data.*
import com.example.globalminusone.data.backup.NativeBackupCodec
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class PhotoCubeRefreshAndCreationTest {
    private val uris = listOf("content://test/one", "content://test/two")
    private fun repository(): MinusOneRepository {
        val state = GlobalMinusOneState(pages = listOf(PageConfig(id = "p", name = "P", layoutType = PageLayoutType.LINEAR,
            components = listOf(ComponentItem(id = "cube", type = ComponentType.PHOTO_CUBE, photoCubeUris = uris),
                ComponentItem(id = "stat", type = ComponentType.STAT_BATTERY_BAR)))), profiles = emptyList())
        return MinusOneRepository(MemoryPreferences(mapOf("state_json_v5" to NativeBackupCodec.json.encodeToString(state))))
    }

    @Test fun sameCubeUrisPublishOnlyTheirOwnRevisionsEvenWhenConfigurationIsUnchanged() {
        val repo = repository()
        val before = repo.state.value
        assertTrue(repo.savePhotoCube("p", "cube", uris))
        assertEquals(before.pages, repo.state.value.pages)
        val afterFirst = repo.state.value
        uris.forEach { assertEquals(1L, repo.imageAccessRevisions.value[it] ?: 0L) }
        assertNull(repo.imageAccessRevisions.value["content://test/unrelated"])
        assertTrue(repo.savePhotoCube("p", "cube", uris))
        assertEquals(afterFirst, repo.state.value)
        uris.forEach { assertEquals(2L, repo.imageAccessRevisions.value[it] ?: 0L) }
    }

    @Test fun missingCubeOrInvalidUrisCannotPublishRefreshOrFallBackToTheTopLevel() {
        val repo = repository()
        val before = repo.state.value
        assertFalse(repo.savePhotoCube("p", "missing", uris))
        assertFalse(repo.savePhotoCube("p", "cube", listOf("file:///unowned")))
        assertFalse(repo.savePhotoCube("p", null, uris, containerId = "gone"))
        assertEquals(before, repo.state.value)
        assertTrue(repo.imageAccessRevisions.value.isEmpty())
    }

    @Test fun preparingNewStatisticsNeverResizesExistingOrPersistedCards() {
        val repo = repository()
        val old = repo.state.value.pages[0].components.last()
        val prepared = repo.prepareNewComponent(0, ComponentItem(type = ComponentType.STAT_BATTERY_BAR))
        assertEquals(32, prepared.w)
        assertTrue(prepared.h > 6)
        assertEquals(old, repo.state.value.pages[0].components.last())
        assertEquals(10, old.w)
        assertEquals(6, old.h)
    }
}
