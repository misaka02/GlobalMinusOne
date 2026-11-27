package com.example.globalminusone.data

import android.content.SharedPreferences
import com.example.globalminusone.MemoryPreferences
import org.junit.Assert.*
import org.junit.Test
import com.example.globalminusone.data.operations.ConfigProfileOperations
import com.example.globalminusone.data.storage.ConfigurationCodec

class ConfigProfileOperationsTest {
    @Test fun snapshotsExcludeProfileLibraryAndEditingStateAndKeepSuppliedIdentityAndMetadata() {
        val current = GlobalMinusOneState(noteText = "saved content", editMode = true, selectedComponentId = "selected")
        val profile = ConfigProfileOperations.createSnapshot(current, "snapshot", "  ", "author", "description", 123)
        assertEquals("snapshot", profile.id)
        assertEquals("配置方案", profile.name)
        assertEquals("author", profile.author)
        assertEquals("description", profile.description)
        assertEquals(123L, profile.updatedAt)
        val snapshot = ConfigProfileOperations.decodeSnapshot(profile)!!
        assertTrue(snapshot.profiles.isEmpty())
        assertEquals("default", snapshot.activeProfileId)
        assertFalse(snapshot.editMode)
        assertNull(snapshot.selectedComponentId)
        assertEquals("saved content", snapshot.noteText)
        val next = ConfigProfileOperations.createSnapshot(ConfigProfileOperations.add(current, profile), "second", "second", "author", "", 124)
        assertEquals(profile.stateJson, next.stateJson)
    }

    @Test fun applyingSnapshotKeepsCurrentLibraryAndBlankSnapshotOnlyChangesActiveProfile() {
        val saved = GlobalMinusOneState(noteText = "saved")
        val profile = ConfigProfileOperations.createSnapshot(saved, "saved", "saved", "author", "", 123)
        val current = ConfigProfileOperations.add(saved.copy(noteText = "live"), profile)
        val applied = ConfigProfileOperations.apply(current, profile.id, ConfigProfileOperations.decodeSnapshot(profile))
        assertEquals(current.profiles, applied.profiles)
        assertEquals("saved", applied.noteText)
        assertEquals(profile.id, applied.activeProfileId)
        val blank = profile.copy(id = "blank", stateJson = "  ")
        assertNull(ConfigProfileOperations.decodeSnapshot(blank))
        assertEquals(current.copy(activeProfileId = "blank"), ConfigProfileOperations.apply(current, blank.id, null))
    }

    @Test fun repositoryRetainsLockEditingAndActiveDeletionRulesIncludingMissingIdSuccess() {
        val repository = MinusOneRepository(MemoryPreferences())
        val profile = repository.createConfigProfile("saved")
        assertFalse(repository.deleteConfigProfile(profile.id))
        assertTrue(repository.deleteConfigProfile("not-present"))
        assertTrue(repository.setLayoutLocked(true))
        assertFalse(repository.applyConfigProfile(profile.id))
        assertTrue(repository.setLayoutLocked(false))
        assertTrue(repository.setEditMode(true))
        assertFalse(repository.applyConfigProfile(profile.id))
        try {
            repository.createConfigProfile("blocked")
            fail("Creation during panel editing must be rejected")
        } catch (expected: IllegalStateException) {
            assertEquals("请先保存或取消当前编辑，再创建方案", expected.message)
        }
        assertTrue(repository.finishPanelEdit(save = false))
        assertTrue(repository.applyConfigProfile("default"))
        assertTrue(repository.deleteConfigProfile(profile.id))
    }

    @Test fun exportingInactiveSnapshotDoesNotApplyItAndDefaultBlankUsesCurrentContent() {
        val repository = MinusOneRepository(MemoryPreferences())
        repository.updateNoteText("snapshot content")
        val saved = repository.createConfigProfile("saved")
        repository.updateNoteText("current content")
        val before = repository.state.value
        val exported = repository.exportConfigProfileCode(saved.id)!!
        assertEquals("snapshot content", ConfigurationCodec.decodeValidatedState(exported).noteText)
        assertEquals(before, repository.state.value)
        assertEquals(repository.exportConfigCode(), repository.exportConfigProfileCode("default"))
        assertNull(repository.exportConfigProfileCode("not-present"))
        val invalid = saved.copy(stateJson = "not json")
        val prefs = MemoryPreferences(mapOf("state_json_v5" to ConfigurationCodec.encode(before.copy(profiles = before.profiles + invalid.copy(id = "invalid")))))
        assertNull(MinusOneRepository(prefs).exportConfigProfileCode("invalid"))
    }

    @Test fun rejectedProfileWritesKeepPublishedStateAndPreserveExceptionOrBooleanContract() {
        val prefs = RejectingPreferences()
        val repository = MinusOneRepository(prefs)
        repository.updateNoteText("saved content")
        val saved = repository.createConfigProfile("saved")
        repository.updateNoteText("live content")
        val before = repository.state.value
        prefs.rejectWrites = true
        assertFalse(repository.applyConfigProfile(saved.id))
        assertEquals(before, repository.state.value)
        assertFalse(repository.deleteConfigProfile("not-present"))
        try {
            repository.createConfigProfile("not accepted")
            fail("Rejected creation must throw")
        } catch (expected: IllegalStateException) {
            assertEquals("配置方案未创建，请重试", expected.message)
            assertNotNull(expected.cause)
        }
        assertEquals(before, repository.state.value)
        assertNotNull(repository.persistenceError.value)
    }

    private class RejectingPreferences(private val backing: MemoryPreferences = MemoryPreferences()) : SharedPreferences by backing {
        var rejectWrites = false
        override fun edit(): SharedPreferences.Editor {
            if (rejectWrites) throw IllegalStateException("write rejected")
            return backing.edit()
        }
    }
}
