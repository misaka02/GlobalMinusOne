package com.example.globalminusone.data

import android.content.SharedPreferences
import com.example.globalminusone.MemoryPreferences
import org.junit.Assert.*
import org.junit.Test
import com.example.globalminusone.data.storage.ConfigurationCodec
import com.example.globalminusone.data.storage.ConfigurationStore

class ConfigurationStoreTest {
    private val key = "state_json_v5"
    private val corruptRaw = "坏原文\n{invalid JSON}\u0000"

    private fun fixture(note: String = "original") = GlobalMinusOneState(
        pages = listOf(PageConfig(id = "stable-page", name = "Page")),
        profiles = emptyList(), noteText = note
    )

    private class FaultPreferences(val disk: MemoryPreferences = MemoryPreferences()) : SharedPreferences by disk {
        var backupFailure: String? = null
        var failNextStateCommit = false
        var failNextApply = false
        val failure = IllegalStateException("synthetic preferences failure")
        val events = mutableListOf<String>()

        override fun getString(key: String?, defValue: String?): String? {
            events += "read:$key"
            if (backupFailure == "mismatch" && key?.startsWith("state_json_v5_corrupt_") == true) return "wrong"
            return disk.getString(key, defValue)
        }

        override fun edit(): SharedPreferences.Editor {
            events += "edit"
            val delegate = disk.edit()
            var writtenKey: String? = null
            return object : SharedPreferences.Editor by delegate {
                override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                    writtenKey = key
                    delegate.putString(key, value)
                    return this
                }
                override fun remove(key: String?): SharedPreferences.Editor {
                    writtenKey = key
                    events += "remove:$key"
                    delegate.remove(key)
                    return this
                }
                override fun commit(): Boolean {
                    events += "commit:$writtenKey"
                    if (writtenKey?.startsWith("state_json_v5_corrupt_") == true) {
                        if (backupFailure == "throw") throw failure
                        val committed = delegate.commit()
                        return backupFailure != "false" && committed
                    }
                    val committed = delegate.commit() // Even a rejected disk commit may update memory.
                    if (failNextStateCommit) {
                        failNextStateCommit = false
                        return false
                    }
                    return committed
                }
                override fun apply() {
                    events += "apply:$writtenKey"
                    delegate.apply()
                    if (failNextApply) {
                        failNextApply = false
                        throw failure
                    }
                }
            }
        }
    }

    @Test fun validLoadInitializesRepositoryWithoutWritesOrRecoveryNotice() {
        val original = fixture()
        val prefs = FaultPreferences(MemoryPreferences(mapOf(key to ConfigurationCodec.encode(original))))
        val repository = MinusOneRepository(prefs)
        assertEquals(original.pages, repository.state.value.pages)
        assertEquals(original.noteText, repository.state.value.noteText)
        assertFalse(repository.corruptStateRecoveredNotice.value)
        assertEquals(listOf("read:$key"), prefs.events)
        assertNull(repository.ensureCorruptStateBackup())
    }

    @Test fun missingLoadHasNoRecoveryEvidenceAndDoesNotPersistDefaults() {
        val prefs = FaultPreferences()
        val loaded = ConfigurationStore(prefs).loadInitialState()
        assertFalse(loaded.corruptRecovered)
        assertTrue(loaded.state.pages.isNotEmpty())
        assertTrue(prefs.disk.all.isEmpty())
        assertEquals(listOf("read:$key"), prefs.events)
    }

    @Test fun corruptLoadInitializesNoticeAndKeepsExactRawUntilProtectionIsRequested() {
        val prefs = FaultPreferences(MemoryPreferences(mapOf(key to corruptRaw)))
        val repository = MinusOneRepository(prefs)
        assertTrue(repository.corruptStateRecoveredNotice.value)
        assertEquals(mapOf(key to corruptRaw), prefs.disk.all)
        repository.acknowledgeCorruptStateNotice()
        val backup = requireNotNull(repository.ensureCorruptStateBackup())
        assertEquals(corruptRaw, backup.raw)
        assertEquals(corruptRaw, prefs.disk.getString(backup.preferenceKey, null))
        assertTrue(backup.preferenceKey.matches(Regex("state_json_v5_corrupt_[0-9]+_[0-9a-f-]{36}")))
        assertSame(backup, repository.ensureCorruptStateBackup())
        assertFalse(repository.corruptStateRecoveredNotice.value)
        assertEquals(corruptRaw, prefs.disk.getString(key, null))
    }

    @Test fun readFailureEscapesRepositoryConstructionWithoutCreatingRecoveryWrites() {
        val disk = MemoryPreferences(mapOf(key to corruptRaw))
        val failure = IllegalStateException("cannot read preferences")
        val prefs = object : SharedPreferences by disk {
            override fun getString(key: String?, defValue: String?): String? = throw failure
        }
        assertSame(failure, runCatching { MinusOneRepository(prefs) }.exceptionOrNull())
        assertEquals(0, disk.writeCount)
        assertEquals(mapOf(key to corruptRaw), disk.all)
    }

    private fun assertProtectionCanRetry(mode: String) {
        val prefs = FaultPreferences(MemoryPreferences(mapOf(key to corruptRaw)))
        val store = ConfigurationStore(prefs)
        assertTrue(store.loadInitialState().corruptRecovered)
        prefs.backupFailure = mode
        val failed = store.accept(fixture("replacement"))
        assertTrue(failed.isFailure)
        if (mode == "throw") assertSame(prefs.failure, failed.exceptionOrNull())
        assertEquals(corruptRaw, prefs.disk.getString(key, null))
        assertTrue(prefs.events.none { it.startsWith("apply:") })
        prefs.backupFailure = null
        prefs.events.clear()
        assertTrue(store.accept(fixture("replacement")).isSuccess)
        val backup = requireNotNull(store.ensureCorruptStateBackup())
        assertEquals(corruptRaw, prefs.disk.getString(backup.preferenceKey, null))
        val commit = prefs.events.indexOf("commit:${backup.preferenceKey}")
        val verification = prefs.events.indexOf("read:${backup.preferenceKey}")
        val activeRead = prefs.events.indexOf("read:$key")
        val activeApply = prefs.events.indexOf("apply:$key")
        assertTrue(commit >= 0 && verification > commit && activeRead > verification && activeApply > activeRead)
        assertEquals("replacement", ConfigurationCodec.decodeValidatedState(requireNotNull(store.readRaw())).noteText)
    }

    @Test fun falseBackupCommitBlocksActiveWriteAndRetainsPendingRawForRetry() = assertProtectionCanRetry("false")

    @Test fun throwingBackupCommitRetainsOriginalExceptionAndPendingRawForRetry() = assertProtectionCanRetry("throw")

    @Test fun mismatchedBackupReadbackBlocksActiveWriteAndRetainsPendingRawForRetry() = assertProtectionCanRetry("mismatch")

    @Test fun applyFailureAfterProtectionRestoresActiveMirrorAndRetainsVerifiedEvidence() {
        val prefs = FaultPreferences(MemoryPreferences(mapOf(key to corruptRaw)))
        val store = ConfigurationStore(prefs)
        store.loadInitialState()
        prefs.failNextApply = true
        assertSame(prefs.failure, store.accept(fixture()).exceptionOrNull())
        assertEquals(corruptRaw, store.readRaw())
        val backup = requireNotNull(store.ensureCorruptStateBackup())
        assertEquals(corruptRaw, prefs.disk.getString(backup.preferenceKey, null))
        val commits = prefs.events.count { it.startsWith("commit:") }
        assertTrue(store.accept(fixture("retry")).isSuccess)
        assertEquals(commits, prefs.events.count { it.startsWith("commit:") })
        assertEquals(3, prefs.events.count { it == "apply:$key" })
    }

    @Test fun restoreRawUsesSynchronousCommitAndRemovesAbsentOriginalWithoutTouchingBackups() {
        val prefs = FaultPreferences(MemoryPreferences(mapOf("saved_backup" to corruptRaw)))
        val store = ConfigurationStore(prefs)
        assertTrue(store.restoreRaw("exact old raw\n"))
        assertEquals("exact old raw\n", store.readRaw())
        assertTrue(store.restoreRaw(null))
        assertFalse(prefs.disk.contains(key))
        assertEquals(corruptRaw, prefs.disk.getString("saved_backup", null))
        assertEquals(2, prefs.events.count { it == "commit:$key" })
        assertTrue("remove:$key" in prefs.events)
        assertTrue(prefs.events.none { it.startsWith("apply:") })
    }

    @Test fun failedImportRollsBackMissingActiveValueWithoutPublishingAndCanRetry() {
        val prefs = FaultPreferences()
        val repository = MinusOneRepository(prefs)
        val before = repository.state.value
        prefs.failNextStateCommit = true
        val target = ConfigurationCodec.encode(fixture("imported"))
        assertFalse(repository.importConfigCode(target))
        assertSame(before, repository.state.value)
        assertFalse(prefs.disk.contains(key))
        assertNotNull(repository.persistenceError.value)
        assertTrue(repository.importConfigCode(target))
        assertEquals("imported", repository.state.value.noteText)
        assertNull(repository.persistenceError.value)
    }

    @Test fun companionFailurePreservesCorruptEvidenceNoticeAndStateUntilRestoreRetrySucceeds() {
        val prefs = FaultPreferences(MemoryPreferences(mapOf(key to corruptRaw)))
        val repository = MinusOneRepository(prefs)
        val before = repository.state.value
        val failure = IllegalArgumentException("companion failed")
        val target = ConfigurationCodec.encode(fixture("restored"))
        var rolledBack = false
        assertSame(failure, runCatching {
            repository.restoreNativeBackup(target, { throw failure }, { rolledBack = true })
        }.exceptionOrNull())
        assertTrue(rolledBack)
        assertSame(before, repository.state.value)
        assertEquals(0L, repository.configurationRestoreGeneration.value)
        assertTrue(repository.corruptStateRecoveredNotice.value)
        assertEquals(corruptRaw, prefs.disk.getString(key, null))
        val backup = requireNotNull(repository.ensureCorruptStateBackup())
        repository.restoreNativeBackup(target, {}, { fail("successful restore must not roll back") })
        assertEquals("restored", repository.state.value.noteText)
        assertEquals(1L, repository.configurationRestoreGeneration.value)
        assertNull(repository.ensureCorruptStateBackup())
        assertFalse(repository.corruptStateRecoveredNotice.value)
        assertEquals(corruptRaw, prefs.disk.getString(backup.preferenceKey, null))
    }

    @Test fun failedStateCommitRollsBackBeforeCompanionAndKeepsOrdinaryPersistenceRetryable() {
        val original = fixture()
        val oldRaw = ConfigurationCodec.encode(original)
        val prefs = FaultPreferences(MemoryPreferences(mapOf(key to oldRaw)))
        val repository = MinusOneRepository(prefs)
        val before = repository.state.value
        prefs.failNextStateCommit = true
        val failure = runCatching {
            repository.restoreNativeBackup(ConfigurationCodec.encode(fixture("rejected")), {}, {
                assertEquals(oldRaw, prefs.disk.getString(key, null))
                assertSame(before, repository.state.value)
            })
        }.exceptionOrNull()
        assertEquals("配置写入失败", failure?.message)
        assertSame(before, repository.state.value)
        assertEquals(0L, repository.configurationRestoreGeneration.value)
        assertNotNull(repository.persistenceError.value)
        // Same state still retries persistence after failure; equality must not skip acceptance.
        val writes = prefs.disk.writeCount
        repository.updateNoteText(original.noteText)
        assertTrue(prefs.disk.writeCount > writes)
        assertNull(repository.persistenceError.value)
    }
}
