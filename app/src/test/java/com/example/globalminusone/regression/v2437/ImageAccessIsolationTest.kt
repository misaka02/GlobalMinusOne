package com.example.globalminusone.regression.v2437

import com.example.globalminusone.MemoryPreferences
import com.example.globalminusone.data.*
import com.example.globalminusone.data.backup.NativeBackupCodec
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class ImageAccessIsolationTest {
    private val firstUri = "content://synthetic/image-one"
    private val secondUri = "content://synthetic/image-two"
    private fun repository(): MinusOneRepository {
        val state = GlobalMinusOneState(pages = listOf(PageConfig(id = "page", name = "Images", components = listOf(
            ComponentItem(id = "one", title = "One", type = ComponentType.SINGLE_IMAGE, imageUri = firstUri),
            ComponentItem(id = "two", title = "Two", type = ComponentType.SINGLE_IMAGE, imageUri = secondUri)
        ))), profiles = emptyList())
        return MinusOneRepository(MemoryPreferences(mapOf("state_json_v5" to NativeBackupCodec.json.encodeToString(state))))
    }

    @Test fun savingOneImageDoesNotInvalidateAnotherImageOrAnUnrelatedIcon() {
        val repo = repository()
        assertTrue(repo.saveImageCard("page", "one", "One", firstUri))
        assertEquals(1L, repo.imageAccessRevisions.value[firstUri] ?: 0L)
        assertNull(repo.imageAccessRevisions.value[secondUri])
        assertTrue(repo.saveComponentIcon("page", "two", "content://synthetic/icon"))
        assertEquals(1L, repo.imageAccessRevisions.value[firstUri] ?: 0L)
        assertNull(repo.imageAccessRevisions.value[secondUri])
        assertEquals(1L, repo.imageAccessRevisions.value["content://synthetic/icon"] ?: 0L)
    }

    @Test fun reauthorizingTheSameResourceRetriesItWithoutPersistingRuntimeRevisions() {
        val repo = repository()
        assertTrue(repo.saveImageCard("page", "one", "One", firstUri))
        val state = repo.state.value
        assertTrue(repo.saveImageCard("page", "one", "One", firstUri))
        assertEquals(2L, repo.imageAccessRevisions.value[firstUri] ?: 0L)
        assertEquals(state, repo.state.value)
        assertFalse(NativeBackupCodec.json.encodeToString(repo.state.value).contains("imageAccessRevisions"))
    }

    @Test fun aDeletedTargetOrInvalidIconDoesNotInvalidateAnyResource() {
        val repo = repository()
        repo.removeComponent(0, "one")
        assertFalse(repo.saveImageCard("page", "one", "One", firstUri))
        assertFalse(repo.saveComponentIcon("page", "two", "file:///unowned/icon"))
        assertTrue(repo.imageAccessRevisions.value.isEmpty())
    }
}
