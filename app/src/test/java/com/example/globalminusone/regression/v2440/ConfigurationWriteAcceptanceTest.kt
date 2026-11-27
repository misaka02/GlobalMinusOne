package com.example.globalminusone.regression.v2440

import android.content.SharedPreferences
import com.example.globalminusone.MemoryPreferences
import com.example.globalminusone.data.*
import org.junit.Assert.*
import org.junit.Test
import com.example.globalminusone.data.BoundWidgetInfo
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.storage.ConfigurationWriteAcceptance
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.PageLayoutType
import com.example.globalminusone.data.operations.findComponent

class ConfigurationWriteAcceptanceTest {
    private val key = "state_json_v5"

    private class FailingPreferences(val disk: MemoryPreferences = MemoryPreferences()) : SharedPreferences by disk {
        var failNextApply = false
        var failBackup = false
        var applies = 0
        var commits = 0
        val applyFailure = IllegalStateException("synthetic listener failure")
        override fun edit(): SharedPreferences.Editor {
            val delegate = disk.edit()
            return object : SharedPreferences.Editor by delegate {
                override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                    delegate.putString(key, value)
                    return this
                }
                override fun remove(key: String?): SharedPreferences.Editor {
                    delegate.remove(key)
                    return this
                }
                override fun apply() {
                    applies++
                    delegate.apply() // Model a listener throwing after Android changes its map.
                    if (failNextApply) {
                        failNextApply = false
                        throw applyFailure
                    }
                }
                override fun commit(): Boolean {
                    commits++
                    if (failBackup) throw IllegalStateException("synthetic backup failure")
                    return delegate.commit()
                }
            }
        }
    }

    @Test fun serializationAndProtectionFailureCannotReplaceTheActiveValue() {
        val prefs = FailingPreferences(MemoryPreferences(mapOf(key to "original")))
        val writer = ConfigurationWriteAcceptance(prefs, key)
        var protectionCalls = 0
        assertTrue(writer.accept({ error("encode failed") }, { protectionCalls++ }).isFailure)
        assertEquals(0, protectionCalls)
        assertTrue(writer.accept({ "replacement" }, { protectionCalls++; error("backup failed") }).isFailure)
        assertEquals(1, protectionCalls)
        assertEquals("original", prefs.getString(key, null))
        assertEquals(0, prefs.applies)
        assertEquals(0, prefs.commits)
    }

    @Test fun rejectedApplyRestoresThePreferenceMirrorAndSameChangeCanBeRetried() {
        val prefs = FailingPreferences()
        val repository = MinusOneRepository(prefs)
        repository.updateNoteText("accepted")
        val before = repository.state.value
        val raw = prefs.getString(key, null)
        prefs.failNextApply = true
        repository.updateNoteText("rejected")
        assertEquals(before, repository.state.value)
        assertEquals(raw, prefs.getString(key, null))
        assertNotNull(repository.persistenceError.value)
        assertSame(prefs.applyFailure, repository.lastPersistenceFailure)
        repository.acknowledgePersistenceError()
        assertNull(repository.persistenceError.value)
        assertSame(prefs.applyFailure, repository.lastPersistenceFailure)
        repository.updateNoteText("rejected")
        assertEquals("rejected", repository.state.value.noteText)
        assertEquals("rejected", MinusOneRepository(prefs).state.value.noteText)
        assertNull(repository.persistenceError.value)
        assertNull(repository.lastPersistenceFailure)
        assertEquals(0, prefs.commits)
    }

    @Test fun failedProtectionPreservesCorruptRawAndDoesNotPublishTheFallbackMutation() {
        val raw = "unreadable original"
        val prefs = FailingPreferences(MemoryPreferences(mapOf(key to raw))).apply { failBackup = true }
        val repository = MinusOneRepository(prefs)
        val before = repository.state.value
        repository.updateNoteText("must not publish")
        assertEquals(before, repository.state.value)
        assertEquals(raw, prefs.getString(key, null))
        assertEquals(0, prefs.applies)
        assertNotNull(repository.persistenceError.value)
        prefs.failBackup = false
        repository.updateNoteText("retry")
        assertEquals("retry", repository.state.value.noteText)
        assertEquals(listOf(raw), prefs.all.filterKeys { it.startsWith("${key}_corrupt_") }.values.toList())
        assertNull(repository.persistenceError.value)
    }

    @Test fun failedEditSaveRetainsDraftBaselineAndWidgetRetentionUntilRetrySucceeds() {
        val prefs = FailingPreferences()
        val repository = MinusOneRepository(prefs)
        assertTrue(repository.addBoundWidget(BoundWidgetInfo(942, "synthetic.provider", "Widget", "Widget")))
        val original = repository.state.value
        assertTrue(repository.setEditMode(true))
        repository.removeComponent(0, "widget_942")
        val pageId = repository.state.value.pages.first().id
        assertTrue(repository.placeMenuComponent(pageId, null, ComponentItem(id = "draft", type = ComponentType.NOTE_CARD)))
        assertTrue(repository.hasPanelEditChanges())
        assertTrue(942 in repository.retainedWidgetIdsForHost())
        val draft = repository.state.value
        prefs.failNextApply = true
        assertFalse(repository.finishPanelEdit(save = true))
        assertEquals(draft, repository.state.value)
        assertTrue(repository.state.value.editMode)
        assertTrue(repository.hasPanelEditChanges())
        assertTrue(942 in repository.retainedWidgetIdsForHost())
        assertEquals(original.pages, MinusOneRepository(prefs).state.value.pages)
        assertFalse(repository.setLayoutLocked(true))
        assertTrue(repository.finishPanelEdit(save = true))
        assertFalse(repository.state.value.editMode)
        assertFalse(repository.hasPanelEditChanges())
        assertFalse(942 in repository.retainedWidgetIdsForHost())
        assertNotNull(MinusOneRepository(prefs).state.value.pages.first().components.findComponent("draft"))
        assertNull(repository.persistenceError.value)
    }

    @Test fun failedEditEntryLeavesNoBaselineAndFailedSaveCanStillBeDiscarded() {
        val prefs = FailingPreferences().apply { failNextApply = true }
        val repository = MinusOneRepository(prefs)
        assertFalse(repository.setEditMode(true))
        assertFalse(repository.state.value.editMode)
        assertFalse(repository.hasPanelEditChanges())
        assertTrue(repository.addPage(PageLayoutType.FREE) >= 0)
        assertFalse(repository.hasPanelEditChanges())
        assertEquals(repository.state.value.pages, MinusOneRepository(prefs).state.value.pages)
        assertTrue(repository.setEditMode(true))
        val before = repository.state.value.pages
        repository.addPage(PageLayoutType.FREE)
        prefs.failNextApply = true
        assertFalse(repository.finishPanelEdit(save = true))
        assertTrue(repository.finishPanelEdit(save = false))
        assertEquals(before, repository.state.value.pages)
        assertFalse(repository.state.value.editMode)
    }

    @Test fun returnValuesAndImageSignalsCannotReportAnUnacceptedChange() {
        val prefs = FailingPreferences()
        val repository = MinusOneRepository(prefs)
        val before = repository.state.value
        prefs.failNextApply = true
        assertEquals(-1, repository.addPage(PageLayoutType.FREE))
        assertEquals(before, repository.state.value)
        prefs.failNextApply = true
        assertFalse(repository.placeMenuComponent(before.pages.first().id, null, ComponentItem(id = "failed", type = ComponentType.NOTE_CARD)))
        prefs.failNextApply = true
        val generation = repository.imageAccessGeneration.value
        assertFalse(repository.saveAppIcon("synthetic.app", "content://synthetic/icon"))
        assertEquals(generation, repository.imageAccessGeneration.value)
        prefs.failNextApply = true
        assertTrue(runCatching { repository.createConfigProfile("failed profile") }.isFailure)
        assertEquals(before, repository.state.value)
        assertTrue(repository.setLayoutLocked(true))
        assertNull(repository.persistenceError.value)
    }

    @Test fun successfulSynchronousImportClearsAnEarlierApplyFailure() {
        val prefs = FailingPreferences().apply { failNextApply = true }
        val repository = MinusOneRepository(prefs)
        repository.updateNoteText("failed")
        assertNotNull(repository.persistenceError.value)
        val originalCode = repository.exportConfigCode()
        assertTrue(repository.importConfigCode(originalCode))
        assertNull(repository.persistenceError.value)
        assertNull(repository.lastPersistenceFailure)
        assertTrue(prefs.commits > 0)
    }
}
