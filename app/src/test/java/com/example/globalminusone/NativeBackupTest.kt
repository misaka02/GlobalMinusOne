package com.example.globalminusone

import android.content.SharedPreferences
import com.example.globalminusone.data.*
import com.example.globalminusone.data.backup.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import com.example.globalminusone.data.BoundWidgetInfo
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.ConfigProfile
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.PageConfig
import com.example.globalminusone.data.QuickActionItem
import com.example.globalminusone.data.operations.flattenComponents

class NativeBackupTest {
    private val json = NativeBackupCodec.json
    private fun state(): GlobalMinusOneState {
        val everyType = ComponentType.entries.mapIndexed { index, type ->
            ComponentItem(id = "card-$index", type = type, packageName = "missing.external.app", textContent = "保留文本与命令，不执行", iconUri = "file:///owned/icon.png")
        }
        val nested = ComponentItem(id = "folder", type = ComponentType.FOLDER,
            containerActions = listOf(QuickActionItem(id = "legacy", title = "旧快捷方式", intentUri = "custom://unchanged")),
            containerComponents = listOf(ComponentItem(id = "inner", type = ComponentType.CONTAINER_BOX, containerComponents = everyType)))
        val base = GlobalMinusOneState(pages = listOf(PageConfig(id = "page", name = "完整页", components = listOf(nested))),
            profiles = emptyList(), noteText = "便签数据", favoriteApps = listOf("missing.external.app"))
        return base.copy(profiles = listOf(ConfigProfile(id = "profile", name = "独立快照", stateJson = json.encodeToString(base))))
    }

    private fun archive(value: DecodedNativeBackup): ByteArray = ByteArrayOutputStream().also { NativeBackupCodec.write(it, value) }.toByteArray()
    private fun expectFailure(block: () -> Unit) { assertTrue(runCatching(block).isFailure) }

    @Test fun roundTripPreservesAllNestedCardTypesLegacyActionsProfilesAndBrowserPreferenceTypes() {
        val bytes = byteArrayOf(1, 3, 5, 7)
        val name = "assets/${NativeBackupCodec.sha256(bytes)}.bin"
        val library = mapOf("bookmarks" to BackupPreference("string", "[{\"title\":\"合成书签\",\"url\":\"https://example.test/\"}]"),
            "history" to BackupPreference("string", "[]"))
        val settings = mapOf("night_mode" to BackupPreference("boolean", "true"), "reader_font_sp" to BackupPreference("int", "18"),
            "window_width_fraction" to BackupPreference("float", "0.75"), "future_long" to BackupPreference("long", "1234"),
            "future_set" to BackupPreference("set", values = listOf("a", "b")))
        val payload = NativeBackupPayload(state(), library, settings, mapOf("file:///owned/icon.png" to name))
        val restored = NativeBackupCodec.read(ByteArrayInputStream(archive(DecodedNativeBackup(payload, mapOf(name to bytes)))))
        assertEquals(payload, restored.payload)
        assertArrayEquals(bytes, restored.assets[name])
        assertEquals(ComponentType.entries.toSet(), restored.payload.state.pages[0].components.flattenComponents().map { it.type }.toSet())
        val rewritten = NativeBackupCodec.mapImages(restored.payload.state) { if (it == "file:///owned/icon.png") "file:///new/icon.bin" else it }
        assertEquals(setOf("file:///new/icon.bin"), NativeBackupCodec.collectImageReferences(rewritten))
        assertEquals("custom://unchanged", rewritten.pages[0].components[0].containerActions[0].intentUri)
        assertEquals("file:///new/icon.bin", json.decodeFromString<GlobalMinusOneState>(rewritten.profiles[0].stateJson)
            .pages[0].components.flattenComponents().first { it.id == "card-0" }.iconUri)
    }

    @Test fun corruptHashUnknownPathMissingEntriesAndExpansionAreRejectedBeforeMutation() {
        val good = archive(DecodedNativeBackup(NativeBackupPayload(state()), emptyMap()))
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(good)).use { zip -> while (true) {
            val entry = zip.nextEntry ?: break; entries[entry.name] = zip.readBytes()
        } }
        fun zip(values: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { output -> ZipOutputStream(output).use { z ->
            values.forEach { (key, value) -> z.putNextEntry(ZipEntry(key)); z.write(value); z.closeEntry() }
        } }.toByteArray()
        expectFailure { NativeBackupCodec.read(ByteArrayInputStream(zip(entries + ("configuration.json" to "{}".toByteArray())))) }
        expectFailure { NativeBackupCodec.read(ByteArrayInputStream(zip(entries + ("../escape" to byteArrayOf(1))))) }
        expectFailure { NativeBackupCodec.read(ByteArrayInputStream(zip(entries - "configuration.json"))) }
        expectFailure { NativeBackupCodec.read(ByteArrayInputStream(zip(mapOf("configuration.json" to ByteArray(8_000_001))))) }
        expectFailure { NativeBackupCodec.readBounded(ByteArrayInputStream(ByteArray(32)), 31) }
    }

    @Test fun invalidStateRecursiveProfileAndMalformedBrowserSettingsAreRejected() {
        expectFailure { NativeBackupCodec.validateState(state().copy(pages = emptyList())) }
        expectFailure { NativeBackupCodec.validateState(state().copy(currentPageIndex = 12)) }
        val recursiveLibrary = json.encodeToString(state())
        expectFailure { NativeBackupCodec.validateState(state().copy(profiles = listOf(ConfigProfile(name = "嵌套", stateJson = recursiveLibrary)))) }
        expectFailure { NativeBackupCodec.validatePayload(NativeBackupPayload(state(), browserSettings = mapOf("night_mode" to BackupPreference("int", "1")))) }
        expectFailure { NativeBackupCodec.validatePayload(NativeBackupPayload(state(), browserLibrary = mapOf("history" to BackupPreference("string", "{}")))) }
        expectFailure { NativeBackupCodec.validatePayload(NativeBackupPayload(state(), browserSettings = mapOf("window_x_fraction" to BackupPreference("float", "NaN")))) }
    }

    @Test fun legacyJsonPreviewPreservesCurrentBrowserMapsAndRejectsInvalidUtf8OrOversizeInput() {
        val library = mapOf("bookmarks" to BackupPreference("string", "[{\"title\":\"保留\",\"url\":\"https://example.test\"}]"))
        val settings = mapOf("night_mode" to BackupPreference("boolean", "true"))
        val raw = (" \n\t" + json.encodeToString(state())).toByteArray(Charsets.UTF_8)
        val decoded = NativeBackupCodec.readCompatible(raw.inputStream(), library, settings)
        assertEquals(state().pages, decoded.payload.state.pages)
        assertEquals(library, decoded.payload.browserLibrary)
        assertEquals(settings, decoded.payload.browserSettings)
        assertTrue(decoded.payload.configurationOnly)
        assertTrue(decoded.assets.isEmpty())
        assertTrue(decoded.payload.warnings.any { it.contains("旧格式仅配置") })
        val malformedUtf8 = "{\"noteText\":\"".toByteArray() + byteArrayOf(0xc3.toByte(), 0x28) + "\"}".toByteArray()
        expectFailure { NativeBackupCodec.readCompatible(malformedUtf8.inputStream(), library, settings) }
        expectFailure { NativeBackupCodec.readCompatible(("{" + " ".repeat(8_000_000)).byteInputStream(), library, settings) }
        expectFailure { NativeBackupCodec.readCompatible(json.encodeToString(state().copy(pages = emptyList())).byteInputStream(), library, settings) }
        val zipped = archive(DecodedNativeBackup(NativeBackupPayload(state()), emptyMap()))
        assertFalse(NativeBackupCodec.readCompatible(zipped.inputStream(), library, settings).payload.configurationOnly)
    }

    @Test fun legacyCodeImportAcknowledgesCommitAndDoesNotPublishWhenDiskWriteFailsOrEditing() {
        val disk = MemoryPreferences()
        var failNextCommit = false
        val prefs = object : SharedPreferences by disk {
            override fun edit(): SharedPreferences.Editor {
                val delegate = disk.edit()
                return object : SharedPreferences.Editor by delegate {
                    override fun putString(key: String?, value: String?): SharedPreferences.Editor { delegate.putString(key, value); return this }
                    override fun commit(): Boolean { delegate.commit(); return if (failNextCommit) { failNextCommit = false; false } else true }
                }
            }
        }
        val repository = MinusOneRepository(prefs)
        repository.updateNoteText("原文")
        val original = repository.state.value
        val raw = disk.getString("state_json_v5", null)
        val incoming = json.encodeToString(original.copy(noteText = "新文"))
        failNextCommit = true
        val failed = repository.importConfigCodeResult(incoming)
        assertTrue(failed.isFailure)
        assertTrue(failed.exceptionOrNull()!!.message!!.contains("写入失败"))
        assertEquals(original, repository.state.value)
        assertEquals(raw, disk.getString("state_json_v5", null))
        repository.setEditMode(true)
        assertFalse(repository.importConfigCode(incoming))
        assertTrue(repository.importConfigCodeResult(incoming).exceptionOrNull()!!.message!!.contains("编辑"))
        repository.finishPanelEdit(save = false)
        assertTrue(repository.importConfigCode(incoming))
        assertEquals("新文", MinusOneRepository(prefs).state.value.noteText)
    }

    @Test fun verifiedWidgetBindingsKeepOnlyValidLocalProviderAndPreserveCardsAndProfiles() {
        val local = BoundWidgetInfo(41, "local.provider", "local.provider.Widget", "本机")
        val foreign = BoundWidgetInfo(42, "foreign.provider", "foreign.provider.Widget", "外机")
        val base = GlobalMinusOneState(pages = listOf(PageConfig(id = "widgets", name = "小部件", components = listOf(
            ComponentItem(id = "valid", type = ComponentType.APP_WIDGET, widgetId = 41),
            ComponentItem(id = "foreign", type = ComponentType.APP_WIDGET, widgetId = 42)))),
            profiles = emptyList(), boundWidgets = listOf(local, foreign))
        val withProfile = base.copy(profiles = listOf(ConfigProfile(name = "快照", stateJson = json.encodeToString(base))))
        val result = NativeBackupCodec.verifiedWidgetBindings(withProfile) { it == local }
        assertEquals(listOf(local), result.boundWidgets)
        assertEquals(listOf(41, -1), result.pages[0].components.map { it.widgetId })
        assertEquals(result.pages, json.decodeFromString<GlobalMinusOneState>(result.profiles[0].stateJson).pages)
    }

    @Test fun transactionalRestoreCommitsOnlyAfterCompanionsAndRollsBackFailedDiskCommit() {
        val disk = MemoryPreferences()
        var failCommits = 0
        val prefs = object : SharedPreferences by disk {
            override fun edit(): SharedPreferences.Editor {
                val delegate = disk.edit()
                return object : SharedPreferences.Editor by delegate {
                    override fun putString(key: String?, value: String?): SharedPreferences.Editor { delegate.putString(key, value); return this }
                    override fun commit(): Boolean { delegate.commit(); if (failCommits > 0) { failCommits--; return false }; return true }
                }
            }
        }
        val repository = MinusOneRepository(prefs)
        repository.updateNoteText("原状态")
        val old = repository.state.value
        val oldRaw = disk.getString("state_json_v5", null)
        var companion = "old"
        failCommits = 1
        expectFailure { repository.restoreNativeBackup(json.encodeToString(old.copy(noteText = "新状态")), { companion = "new" }, { companion = "old" }) }
        assertEquals(old, repository.state.value)
        assertEquals(oldRaw, disk.getString("state_json_v5", null))
        assertEquals("old", companion)
        assertEquals(0L, repository.configurationRestoreGeneration.value)
        repository.restoreNativeBackup(json.encodeToString(old.copy(noteText = "成功")), { companion = "new" }, { companion = "old" })
        assertEquals("成功", MinusOneRepository(prefs).state.value.noteText)
        assertEquals("new", companion)
        assertEquals(1L, repository.configurationRestoreGeneration.value)
        // Equal state still needs an event: browser preferences may be the only change.
        repository.restoreNativeBackup(json.encodeToString(repository.state.value), {}, {})
        assertEquals(2L, repository.configurationRestoreGeneration.value)
        repository.setLayoutLocked(true)
        var called = false
        expectFailure { repository.restoreNativeBackup(json.encodeToString(old), { called = true }, {}) }
        assertFalse(called)
    }

    @Test fun appendImportsKeepExistingPagesAndRejectLockedEmptyDuplicateAndMissingTargets() {
        val repository = MinusOneRepository(MemoryPreferences())
        val before = repository.state.value
        assertFalse(repository.appendImportedPages(emptyList()))
        assertFalse(repository.appendImportedPages(listOf(before.pages[0])))
        val page = PageConfig(id = "imported", name = "导入页")
        assertTrue(repository.appendImportedPages(listOf(page)))
        assertEquals(before.pages + page, repository.state.value.pages)
        val folder = ComponentItem(id = "imported-folder", type = ComponentType.FOLDER,
            containerComponents = listOf(ComponentItem(id = "photo", type = ComponentType.SINGLE_IMAGE)))
        val unchanged = repository.state.value
        assertFalse(repository.appendImportedFolder(folder, "missing"))
        assertFalse(repository.appendImportedFolder(folder, page.id, "deleted-container"))
        assertEquals(unchanged, repository.state.value)
        assertTrue(repository.appendImportedFolder(folder, page.id))
        assertFalse(repository.appendImportedFolder(folder, page.id))
        repository.setLayoutLocked(true)
        assertFalse(repository.appendImportedPages(listOf(PageConfig(id = "locked", name = "锁定"))))
        assertFalse(repository.appendImportedFolder(folder.copy(id = "another"), page.id))
    }
}
