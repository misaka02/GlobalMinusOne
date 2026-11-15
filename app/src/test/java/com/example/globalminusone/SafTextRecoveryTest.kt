package com.example.globalminusone

import com.example.globalminusone.system.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class SafTextRecoveryTest {
    private val original = "原始文件\noriginal".toByteArray()
    private val replacement = "replacement".toByteArray()
    private val target = "content://synthetic/document/exact-one"
    private fun withStore(block: (File, SafTextRecoveryStore) -> Unit) {
        val directory = Files.createTempDirectory("gmo-text-recovery").toFile()
        try { block(directory, SafTextRecoveryStore(directory)) } finally { directory.deleteRecursively() }
    }
    @Test fun partialProviderWriteRetainsVerifiedOriginalAndTargetAcrossStoreRecreation() = withStore { directory, store ->
        var current = original.copyOf()
        val failure = runCatching { store.save(target, "note.txt", original, replacement, { current }) {
            current = it.copyOf(3); throw IOException("injected full disk")
        } }.exceptionOrNull()
        assertNotNull(failure)
        assertFalse(failure!!.message.orEmpty().contains("已恢复"))
        assertArrayEquals(replacement.copyOf(3), current)
        val reopened = SafTextRecoveryStore(directory)
        val record = requireNotNull(reopened.pending(target))
        assertEquals("note.txt", record.targetName)
        assertTrue(record.createdAt > 0)
        assertNull(reopened.pending("content://synthetic/document/other"))
        assertArrayEquals(original, reopened.original(record).readBytes())
    }
    @Test fun explicitRestoreRetainsBackupOnWriteFailureOrReadbackMismatchAndRejectsExternalChanges() = withStore { _, store ->
        var current = original.copyOf()
        runCatching { store.save(target, "note.txt", original, replacement, { current }) {
            current = it.copyOf(2); throw IOException("partial save")
        } }
        val record = requireNotNull(store.pending(target))
        assertTrue(runCatching { store.restore(record, textFingerprint(current), { current }) {
            current = it.copyOf(1); throw IOException("partial restore")
        } }.isFailure)
        assertArrayEquals(original, store.original(record).readBytes())
        assertTrue(runCatching { store.restore(record, textFingerprint(current), { current }) {
            current = "silently wrong".toByteArray()
        } }.isFailure)
        assertNotNull(store.pending(target))
        val reviewedHash = textFingerprint(current)
        current = "external edit after review".toByteArray()
        var writes = 0
        assertTrue(runCatching { store.restore(record, reviewedHash, { current }) { writes++ } }.isFailure)
        assertEquals(0, writes)
        store.restore(record, textFingerprint(current), { current }) { current = it.copyOf() }
        assertArrayEquals(original, current)
        assertNull(store.pending(target))
    }
    @Test fun saveRequiresMatchingReadbackAndQuotaRefusalOccursBeforeProviderWrite() = withStore { directory, store ->
        var current = original.copyOf()
        store.save(target, "note.txt", original, replacement, { current }) { current = it.copyOf() }
        assertArrayEquals(replacement, current)
        assertNull(store.pending(target))
        current = original.copyOf()
        assertTrue(runCatching { store.save(target, "note.txt", original, replacement, { current }) {
            current = "wrong but no stream exception".toByteArray()
        } }.isFailure)
        assertArrayEquals(original, store.original(requireNotNull(store.pending(target))).readBytes())
        val full = SafTextRecoveryStore(directory, maxRecords = 1)
        var writes = 0
        assertTrue(runCatching { full.save("content://synthetic/document/two", "two.txt", original,
            replacement, { original }) { writes++ } }.isFailure)
        assertEquals(0, writes)
        assertNotNull(full.pending(target))
    }
}
