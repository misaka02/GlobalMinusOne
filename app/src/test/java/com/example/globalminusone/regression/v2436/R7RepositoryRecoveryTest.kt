package com.example.globalminusone.regression.v2436

import android.content.SharedPreferences
import com.example.globalminusone.MemoryPreferences
import com.example.globalminusone.data.*
import com.example.globalminusone.data.backup.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import com.example.globalminusone.data.BoundWidgetInfo
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.ConfigProfile
import com.example.globalminusone.data.CorruptStateBackup
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.PageConfig
import com.example.globalminusone.data.operations.savedWidgetIds

class R7RepositoryRecoveryTest {
    private fun fixture(): GlobalMinusOneState {
        val widget = ComponentItem(id = "nested-widget", type = ComponentType.APP_WIDGET, widgetId = 901)
        val inner = ComponentItem(id = "nested-folder", type = ComponentType.FOLDER, containerComponents = listOf(widget))
        val container = ComponentItem(id = "page1-container", type = ComponentType.CONTAINER_BOX, containerComponents = listOf(inner))
        val note = ComponentItem(id = "page2-note", type = ComponentType.NOTE_CARD, textContent = "original")
        return GlobalMinusOneState(pages = listOf(PageConfig(id = "p1", name = "One", components = listOf(container)),
            PageConfig(id = "p2", name = "Two", components = listOf(note))), profiles = emptyList(),
            boundWidgets = listOf(BoundWidgetInfo(901, "synthetic.provider", "Widget", "Widget")))
    }

    private fun preferences(state: GlobalMinusOneState) = MemoryPreferences(mapOf(
        "state_json_v5" to NativeBackupCodec.json.encodeToString(state)))

    @Test fun discardRestoresBothPagesAndArbitrarySubtreesAcrossRepeatedEnterAndPageChanges() {
        val original = fixture()
        val prefs = preferences(original)
        val repository = MinusOneRepository(prefs)
        repository.setEditMode(true)
        repository.removeComponent(0, "page1-container")
        repository.setCurrentPageIndex(1)
        repository.setEditMode(true) // a second edit affordance must not replace the original baseline.
        repository.removeComponent(1, "page2-note")
        assertTrue(repository.hasPanelEditChanges())
        assertEquals(original.pages, MinusOneRepository(prefs).state.value.pages)
        repository.finishPanelEdit(save = false)
        assertEquals(original.pages, repository.state.value.pages)
        assertEquals(original.boundWidgets, repository.state.value.boundWidgets)
        assertEquals(1, repository.state.value.currentPageIndex)
        assertEquals(original.pages, MinusOneRepository(prefs).state.value.pages)
    }

    @Test fun widgetHostRetentionDefersSubtreeUnbindingUntilSaveAndNeverUnbindsOnDiscard() {
        val repository = MinusOneRepository(preferences(fixture()))
        repository.setEditMode(true)
        repository.removeComponent(0, "nested-folder")
        assertFalse(901 in repository.state.value.savedWidgetIds())
        assertTrue(901 in repository.retainedWidgetIdsForHost())
        repository.finishPanelEdit(save = false)
        assertTrue(901 in repository.retainedWidgetIdsForHost())
        repository.setEditMode(true)
        repository.deletePage(0)
        assertTrue(901 in repository.retainedWidgetIdsForHost())
        repository.finishPanelEdit(save = true)
        assertFalse(901 in repository.retainedWidgetIdsForHost())
    }

    @Test fun lockingOrApplyingProfileCannotSilentlyCommitAndReplaceAnActiveBaseline() {
        val original = fixture()
        val withProfile = original.copy(profiles = listOf(ConfigProfile(id = "replacement", name = "Replacement",
            stateJson = NativeBackupCodec.json.encodeToString(original.copy(pages = original.pages.reversed())))))
        val repository = MinusOneRepository(preferences(withProfile))
        repository.setEditMode(true)
        repository.removeComponent(0, "page1-container")
        repository.setLayoutLocked(true)
        assertFalse(repository.state.value.advancedConfig.layoutLocked)
        assertTrue(repository.state.value.editMode)
        assertFalse(repository.applyConfigProfile("replacement"))
        assertTrue(repository.hasPanelEditChanges())
        repository.finishPanelEdit(save = false)
        assertEquals(original.pages, repository.state.value.pages)
        repository.setLayoutLocked(true)
        assertTrue(repository.state.value.advancedConfig.layoutLocked)
    }

    @Test fun sameImageUriReauthorizationPublishesRetrySignalEvenWhenStateDoesNotChange() {
        val original = fixture().copy(pages = listOf(PageConfig(id = "image-page", name = "Image", components = listOf(
            ComponentItem(id = "image", type = ComponentType.SINGLE_IMAGE, title = "Image", imageUri = "content://synthetic/image")))))
        val repository = MinusOneRepository(preferences(original))
        assertTrue(repository.saveImageCard("image-page", "image", "Image", "content://synthetic/image"))
        val afterFirst = repository.state.value
        val generation = repository.imageAccessGeneration.value
        assertTrue(repository.saveImageCard("image-page", "image", "Image", "content://synthetic/image"))
        assertEquals(afterFirst, repository.state.value)
        assertEquals(generation + 1, repository.imageAccessGeneration.value)
        repository.removeComponent(0, "image")
        assertFalse(repository.saveImageCard("image-page", "image", "Image", "content://synthetic/image"))
        assertEquals(generation + 1, repository.imageAccessGeneration.value)
    }

    @Test fun corruptRawIsVerifiedBeforeRestoreAndNeverReplacedByTheDefaultFallback() {
        val raw = "{broken state: 原始字节\n"
        val prefs = MemoryPreferences(mapOf("state_json_v5" to raw))
        val repository = MinusOneRepository(prefs)
        val preserved = requireNotNull(repository.ensureCorruptStateBackup())
        assertEquals(raw, prefs.getString(preserved.preferenceKey, null))
        assertEquals(raw, prefs.getString("state_json_v5", null))
        val target = fixture()
        repository.restoreNativeBackup(NativeBackupCodec.json.encodeToString(target), {}, {})
        assertEquals(target.pages, repository.state.value.pages)
        assertEquals(raw, prefs.getString(preserved.preferenceKey, null))
        assertNull(repository.ensureCorruptStateBackup()) // successful restore no longer exports a fallback warning.
    }

    @Test fun failedCorruptBackupCommitBlocksRestoreBeforeAnyCompanionWriteAndCanRetry() {
        val raw = "unreadable original"
        val disk = MemoryPreferences(mapOf("state_json_v5" to raw))
        var failBackup = true
        val prefs = object : SharedPreferences by disk {
            override fun edit(): SharedPreferences.Editor {
                val delegate = disk.edit()
                return object : SharedPreferences.Editor by delegate {
                    override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                        delegate.putString(key, value); return this
                    }
                    override fun commit(): Boolean {
                        val result = delegate.commit() // Android can update memory even when a disk commit reports failure.
                        return !failBackup && result
                    }
                }
            }
        }
        val repository = MinusOneRepository(prefs)
        var companionWrites = 0
        assertTrue(runCatching { repository.restoreNativeBackup(NativeBackupCodec.json.encodeToString(fixture()),
            { companionWrites++ }, {}) }.isFailure)
        assertEquals(0, companionWrites)
        assertEquals(raw, disk.getString("state_json_v5", null))
        failBackup = false
        val preserved = requireNotNull(repository.ensureCorruptStateBackup())
        assertEquals(raw, disk.getString(preserved.preferenceKey, null))
    }

    @Test fun directConfigImportAlsoPreservesPendingCorruptRawBeforeReplacement() {
        val raw = "not-json"
        val prefs = MemoryPreferences(mapOf("state_json_v5" to raw))
        val repository = MinusOneRepository(prefs)
        assertTrue(repository.importConfigCode(NativeBackupCodec.json.encodeToString(fixture())))
        assertEquals(listOf(raw), prefs.all.filterKeys { it.startsWith("state_json_v5_corrupt_") }.values.toList())
        assertEquals(fixture().pages.map { it.id }, repository.state.value.pages.map { it.id })
    }

    @Test fun corruptRawRecoveryFileIsByteExactAndAnExistingWrongFileIsRejected() {
        val directory = Files.createTempDirectory("r7-corrupt-raw").toFile()
        try {
            val raw = "坏原文\n{not valid JSON}\u0000"
            val backup = CorruptStateBackup("synthetic-corrupt", raw)
            val file = archiveCorruptStateRaw(directory, backup)
            assertArrayEquals(raw.toByteArray(Charsets.UTF_8), file.readBytes())
            assertEquals(file, archiveCorruptStateRaw(directory, backup))
            file.writeText("wrong data")
            assertTrue(runCatching { archiveCorruptStateRaw(directory, backup) }.isFailure)
            assertEquals("wrong data", file.readText()) // do not turn a mismatched existing file into a claimed success.
        } finally { directory.deleteRecursively() }
    }
}
