package com.example.globalminusone.data

import android.content.SharedPreferences
import com.example.globalminusone.MemoryPreferences
import org.junit.Assert.*
import org.junit.Test
import com.example.globalminusone.data.storage.ConfigurationCodec

/** Exercise the real repository/store with a warmed section cache and interrupted writes. */
class ConfigurationSectionPersistenceTest {
    private val key = "state_json_v5"

    private fun fixture(label: String) = GlobalMinusOneState(
        pages = listOf(PageConfig(id = "page", name = label,
            components = listOf(ComponentItem(id = "note", type = ComponentType.NOTE_CARD, y = 4)))),
        profiles = emptyList(), noteText = label,
        todos = listOf(TodoItem(id = "todo", content = label, createdAt = 1L)),
        clipboardHistory = listOf(ClipboardRecord(id = "clip", text = label, timestamp = 1L))
    )

    private class FaultPreferences(val disk: MemoryPreferences) : SharedPreferences by disk {
        var rejectApply = false
        var rejectCommit = false
        override fun edit(): SharedPreferences.Editor {
            val editor = disk.edit()
            return object : SharedPreferences.Editor by editor {
                override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                    editor.putString(key, value)
                    return this
                }
                override fun apply() {
                    editor.apply()
                    if (rejectApply) {
                        rejectApply = false
                        error("synthetic listener failure after memory update")
                    }
                }
                override fun commit(): Boolean {
                    val result = editor.commit()
                    if (rejectCommit) {
                        rejectCommit = false
                        return false
                    }
                    return result
                }
            }
        }
    }

    private fun preferences() = FaultPreferences(MemoryPreferences(mapOf(key to ConfigurationCodec.encode(fixture("old")))))

    private fun assertSaved(repository: MinusOneRepository, prefs: SharedPreferences) {
        val expected = repository.state.value.copy(editMode = false, selectedComponentId = null)
        assertEquals(ConfigurationCodec.encode(expected), prefs.getString(key, null))
        assertEquals(expected, MinusOneRepository(prefs).state.value)
        assertNull(repository.persistenceError.value)
    }

    @Test fun warmCacheKeepsCompleteV5AcrossAlternatingContentAndLayoutChanges() {
        val prefs = preferences()
        val repository = MinusOneRepository(prefs)
        repository.updateNoteText("warm")
        repository.toggleTodo("todo")
        repository.recordClipboardText("a quote \" and newline\n中文")
        assertTrue(repository.setEditMode(true))
        assertTrue(repository.updateComponent(0, "note") { it.copy(y = 17) })
        assertTrue(repository.finishPanelEdit(save = true))
        repository.updateNoteText("after layout")
        assertSaved(repository, prefs)
        assertEquals(17, repository.state.value.pages.single().components.single().y)
        assertTrue(repository.state.value.todos.single().done)
    }

    @Test fun rejectedContentCannotLeakIntoLaterDifferentDomainOrRestart() {
        val prefs = preferences()
        val repository = MinusOneRepository(prefs)
        repository.updateNoteText("accepted")
        val oldState = repository.state.value
        val oldRaw = prefs.getString(key, null)
        prefs.rejectApply = true
        repository.updateNoteText("rejected")
        assertEquals(oldState, repository.state.value)
        assertEquals(oldRaw, prefs.getString(key, null))
        assertNotNull(repository.persistenceError.value)
        repository.toggleTodo("todo")
        assertEquals("accepted", repository.state.value.noteText)
        assertSaved(repository, prefs)
    }

    @Test fun failedImportRollsBackWarmCacheAndNextContentSaveUsesOriginalLayout() {
        val prefs = preferences()
        val repository = MinusOneRepository(prefs)
        repository.updateNoteText("warm")
        val original = repository.state.value
        prefs.rejectCommit = true
        assertFalse(repository.importConfigCode(ConfigurationCodec.encode(fixture("imported"))))
        assertEquals(original, repository.state.value)
        assertEquals(ConfigurationCodec.encode(original), prefs.getString(key, null))
        repository.recordClipboardText("after rollback")
        assertEquals(original.pages, repository.state.value.pages)
        assertEquals(original.todos, repository.state.value.todos)
        assertSaved(repository, prefs)
    }

    @Test fun successfulImportAndNativeRestoreReplaceEveryPreviouslyCachedDomain() {
        val prefs = preferences()
        val repository = MinusOneRepository(prefs)
        repository.updateNoteText("warm")
        assertTrue(repository.importConfigCode(ConfigurationCodec.encode(fixture("imported"))))
        repository.toggleTodo("todo")
        assertEquals("imported", repository.state.value.pages.single().name)
        assertEquals("imported", repository.state.value.noteText)
        assertSaved(repository, prefs)
        repository.restoreNativeBackup(ConfigurationCodec.encode(fixture("restored")), {}, { fail("unexpected rollback") })
        repository.updateNoteText("after restore")
        assertEquals("restored", repository.state.value.clipboardHistory.single().text)
        assertEquals("restored", repository.state.value.todos.single().content)
        assertEquals("restored", repository.state.value.pages.single().name)
        assertSaved(repository, prefs)
    }

    @Test fun discardLayoutKeepsLiveContentWithoutPersistingDraftGeometry() {
        val prefs = preferences()
        val repository = MinusOneRepository(prefs)
        repository.updateNoteText("warm")
        val savedPages = repository.state.value.pages
        assertTrue(repository.setEditMode(true))
        assertTrue(repository.updateComponent(0, "note") { it.copy(y = 99) })
        repository.toggleTodo("todo")
        repository.updateNoteText("live note")
        val duringEdit = MinusOneRepository(prefs).state.value
        assertEquals(savedPages, duringEdit.pages)
        assertEquals("live note", duringEdit.noteText)
        assertTrue(duringEdit.todos.single().done)
        assertTrue(repository.finishPanelEdit(save = false))
        assertEquals(savedPages, repository.state.value.pages)
        assertSaved(repository, prefs)
    }
}
