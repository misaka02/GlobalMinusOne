package com.example.globalminusone.regression.v2430

import com.example.globalminusone.MemoryPreferences
import com.example.globalminusone.data.policy.ContainerActionGeometry
import com.example.globalminusone.data.FileBrowserSortOrder
import com.example.globalminusone.data.policy.LEGACY_ACTION_VIEWPORT_MAX_DP
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.QuickActionItem
import com.example.globalminusone.data.policy.commitContainerActionGeometry
import com.example.globalminusone.data.policy.containerActionGeometry
import com.example.globalminusone.data.panels.PanelsBackupImporter
import com.example.globalminusone.data.panels.SafeZipReader
import com.example.globalminusone.system.file.BrowserEntry
import com.example.globalminusone.system.file.BrowserLocation
import com.example.globalminusone.system.file.DuplicateFileGroup
import com.example.globalminusone.system.file.orderDuplicateEntriesKeepingOriginal
import com.example.globalminusone.system.file.selectRedundantDuplicateKeys
import com.example.globalminusone.system.file.sortBrowserEntries
import com.example.globalminusone.ui.panel.container.FolderReturnState
import com.example.globalminusone.ui.panel.PanelModalBackHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class V2430AuditAndUserFixesTest {

    @Test
    fun safeZipReaderAndPanelsImporter_acceptLeadingSlashStoredAndDeflatedEntriesWithoutZipPathValidator() {
        val genuineBytes = requireNotNull(javaClass.getResourceAsStream("/panels/synthetic-panels-sample.bkp")).use {
            it.readBytes()
        }
        assertTrue(SafeZipReader.looksLikePanelsArchive(genuineBytes))
        val genuineArchive = PanelsBackupImporter.parseBytes(genuineBytes)
        assertEquals(5, genuineArchive.pages.size)
        assertEquals(21, genuineArchive.iconAssets.size)

        val validPng = minimalPngHeaderBytes(64, 64)
        val archiveBytes = buildMixedZip(
            storedEntries = mapOf("/original/custom_icon.png" to validPng),
            deflatedEntries = mapOf(
                "items_table.json" to """[{"id":1,"type":2,"panelId":1,"label":"Demo","iconName":"custom_icon"}]"""
                    .toByteArray(Charsets.UTF_8),
                "panels_table.json" to """[{"id":1,"setId":1,"type":2,"counterSpanCount":4}]"""
                    .toByteArray(Charsets.UTF_8),
                "sets_table.json" to """[{"id":1,"screenId":1}]""".toByteArray(Charsets.UTF_8),
                "screens_table.json" to """[{"id":1}]""".toByteArray(Charsets.UTF_8)
            )
        )
        assertTrue(SafeZipReader.looksLikePanelsArchive(archiveBytes))
        val parsed = PanelsBackupImporter.parseBytes(archiveBytes)
        val converted = parsed.convertPage(1, iconUriFor = { "asset://$it" }).components.single()
        assertEquals("asset:///original/custom_icon.png", converted.iconUri)

        // Normalized duplicate paths ("/original/dup.png" + "original/dup.png") must be rejected
        val dupArchive = buildMixedZip(
            storedEntries = mapOf(
                "/original/dup.png" to validPng,
                "original/dup.png" to validPng
            ),
            deflatedEntries = mapOf(
                "items_table.json" to "[]".toByteArray(),
                "panels_table.json" to """[{"id":1,"setId":1,"type":2,"counterSpanCount":4}]""".toByteArray(),
                "sets_table.json" to """[{"id":1,"screenId":1}]""".toByteArray(),
                "screens_table.json" to """[{"id":1}]""".toByteArray()
            )
        )
        try {
            PanelsBackupImporter.parseBytes(dupArchive)
            fail("Expected duplicate normalized path rejection")
        } catch (_: Exception) {
            // expected
        }
    }

    @Test
    fun panelModalBackHandler_priorityAndLifoStackUnwindsFolderEditingAndPropertyModalsWithoutClosingFolder() {
        PanelModalBackHandler.clearForTest()
        try {
            var folderClosed = false
            var editingInside = true
            var showQuickLayoutBar = true
            var propertyModalOpen = true
            var innerDialogOpen = true

            val baseOwner = Any()
            val innerOwner = Any()
            val propertyOwner = Any()
            val dialogOwner = Any()

            PanelModalBackHandler.register(baseOwner, PanelModalBackHandler.PRIORITY_FOLDER_BASE) {
                folderClosed = true
                true
            }
            PanelModalBackHandler.register(innerOwner, PanelModalBackHandler.PRIORITY_FOLDER_INNER) {
                when {
                    showQuickLayoutBar -> {
                        showQuickLayoutBar = false
                        true
                    }
                    editingInside -> {
                        editingInside = false
                        true
                    }
                    else -> false
                }
            }
            PanelModalBackHandler.register(propertyOwner, PanelModalBackHandler.PRIORITY_PROPERTY_MODAL) {
                if (propertyModalOpen) {
                    propertyModalOpen = false
                    true
                } else false
            }
            PanelModalBackHandler.register(dialogOwner, PanelModalBackHandler.PRIORITY_DIALOG) {
                if (innerDialogOpen) {
                    innerDialogOpen = false
                    true
                } else false
            }

            // Simulate dismissing an inner confirmation dialog; lower-priority handlers must stay intact!
            innerDialogOpen = false
            PanelModalBackHandler.unregister(dialogOwner)

            // 1st Back: closes Property Modal, folder remains open and editingInside remains true
            assertTrue(PanelModalBackHandler.handleBack())
            assertFalse(propertyModalOpen)
            assertTrue(editingInside)
            assertFalse(folderClosed)
            PanelModalBackHandler.unregister(propertyOwner)

            // 2nd Back: collapses quick layout strip inside folder, folder remains open
            assertTrue(PanelModalBackHandler.handleBack())
            assertFalse(showQuickLayoutBar)
            assertTrue(editingInside)
            assertFalse(folderClosed)

            // 3rd Back: exits editingInside, folder STILL remains open!
            assertTrue(PanelModalBackHandler.handleBack())
            assertFalse(editingInside)
            assertFalse(folderClosed)
            PanelModalBackHandler.unregister(innerOwner)

            // 4th Back: finally closes the folder itself
            assertTrue(PanelModalBackHandler.handleBack())
            assertTrue(folderClosed)
        } finally {
            PanelModalBackHandler.clearForTest()
        }

        val returnState = FolderReturnState()
        returnState.markPending("folder-42")
        assertNull(returnState.reopenFolderId)
        returnState.triggerPendingReopen()
        assertEquals("folder-42", returnState.reopenFolderId)
        assertEquals(1, returnState.triggerToken)
        assertFalse(returnState.consume("other-folder"))
        assertTrue(returnState.consume("folder-42"))
        assertNull(returnState.reopenFolderId)
    }

    @Test
    fun freeContainerActionGeometry_clampsOutOfBoundsYAndHeightWithinLegacyViewport() {
        val action = QuickActionItem(id = "act-1", title = "测试动作", intentUri = "action:test")
        val clamped = commitContainerActionGeometry(action, 20, 620, 120, 96)
        val maxTop = (LEGACY_ACTION_VIEWPORT_MAX_DP - 96f).toInt()
        assertEquals(maxTop, clamped.yDp)
        assertEquals(96, clamped.hDp)

        val oversized = QuickActionItem(
            id = "act-2",
            title = "超高动作",
            intentUri = "action:test",
            xDp = 10,
            yDp = 500,
            wDp = 120,
            hDp = 500
        )
        val geom = containerActionGeometry(oversized, 0, 4, 360f)
        assertEquals(ContainerActionGeometry(10f, 0f, 120f, LEGACY_ACTION_VIEWPORT_MAX_DP), geom)
    }

    @Test
    fun corruptPersistedState_notifiesUserWithoutStartupWriteAndArchivesBoundedBackupsOnNextPersist() {
        val prefs = MemoryPreferences(mapOf("state_json_v5" to "{broken_json"))
        val repository = MinusOneRepository(prefs)

        // Startup invariant: never overwrite SharedPreferences during construction
        assertEquals(0, prefs.writeCount)
        assertTrue(repository.corruptStateRecoveredNotice.value)

        repository.acknowledgeCorruptStateNotice()
        assertFalse(repository.corruptStateRecoveredNotice.value)

        // First user mutation persists clean state AND archives the corrupt raw payload
        repository.updateNoteText("已恢复正常")
        assertTrue(prefs.writeCount >= 1)
        val corruptBackupKeys = prefs.all.keys.filter { it.startsWith("state_json_v5_corrupt_") }
        assertEquals(1, corruptBackupKeys.size)
        assertEquals("{broken_json", prefs.getString(corruptBackupKeys.single(), null))
    }

    @Test
    fun duplicateSelectionAndReverseSorting_keepOriginalFileAndPreserveDirectoriesFirst() {
        val dupCopy = BrowserEntry(
            BrowserLocation("report_duplicate.pdf", path = "/storage/Download/report_duplicate.pdf"),
            "report_duplicate.pdf",
            false,
            "application/pdf",
            4096L,
            1000L
        )
        val numberedCopy = BrowserEntry(
            BrowserLocation("report (1).pdf", path = "/storage/Download/report (1).pdf"),
            "report (1).pdf",
            false,
            "application/pdf",
            4096L,
            900L
        )
        val original = BrowserEntry(
            BrowserLocation("report.pdf", path = "/storage/Documents/Sub/report.pdf"),
            "report.pdf",
            false,
            "application/pdf",
            4096L,
            2000L
        )
        val cnCopy = BrowserEntry(
            BrowserLocation("report副本.pdf", path = "/storage/report副本.pdf"),
            "report副本.pdf",
            false,
            "application/pdf",
            4096L,
            800L
        )

        val ordered = orderDuplicateEntriesKeepingOriginal(listOf(dupCopy, numberedCopy, original, cnCopy))
        assertEquals("report.pdf", ordered.first().name)

        val redundant = selectRedundantDuplicateKeys(
            listOf(DuplicateFileGroup(4096L, "sha256", listOf(dupCopy, numberedCopy, original, cnCopy)))
        )
        assertFalse(original.location.key in redundant)
        assertEquals(
            setOf(dupCopy.location.key, numberedCopy.location.key, cnCopy.location.key),
            redundant
        )

        // Reverse sort keeps directories ahead of files while reversing primary key order
        val entries = listOf(
            BrowserEntry(BrowserLocation("a-dir", path = "/s/a-dir"), "a-dir", true, "directory", null, null),
            BrowserEntry(BrowserLocation("z-dir", path = "/s/z-dir"), "z-dir", true, "directory", null, null),
            BrowserEntry(BrowserLocation("alpha.txt", path = "/s/alpha.txt"), "alpha.txt", false, "text/plain", 10L, 100L),
            BrowserEntry(BrowserLocation("omega.txt", path = "/s/omega.txt"), "omega.txt", false, "text/plain", 90L, 500L)
        )
        assertEquals(
            listOf("z-dir", "a-dir", "omega.txt", "alpha.txt"),
            sortBrowserEntries(entries, FileBrowserSortOrder.NAME, reverse = true).map { it.name }
        )
        assertEquals(
            listOf("a-dir", "z-dir", "alpha.txt", "omega.txt"),
            sortBrowserEntries(entries, FileBrowserSortOrder.SIZE, reverse = true).map { it.name }
        )
        assertEquals(
            listOf("a-dir", "z-dir", "alpha.txt", "omega.txt"),
            sortBrowserEntries(entries, FileBrowserSortOrder.MODIFIED, reverse = true).map { it.name }
        )
    }

    private fun minimalPngHeaderBytes(width: Int, height: Int): ByteArray {
        val buf = ByteBuffer.allocate(32)
        buf.put(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10))
        buf.putInt(13)
        buf.put("IHDR".toByteArray(Charsets.US_ASCII))
        buf.putInt(width)
        buf.putInt(height)
        buf.put(byteArrayOf(8, 6, 0, 0, 0, 0, 0, 0))
        return buf.array()
    }

    private fun buildMixedZip(
        storedEntries: Map<String, ByteArray>,
        deflatedEntries: Map<String, ByteArray>
    ): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            storedEntries.forEach { (name, payload) ->
                val crc = CRC32().apply { update(payload) }.value
                val entry = ZipEntry(name).apply {
                    method = ZipEntry.STORED
                    size = payload.size.toLong()
                    compressedSize = payload.size.toLong()
                    this.crc = crc
                }
                zos.putNextEntry(entry)
                zos.write(payload)
                zos.closeEntry()
            }
            deflatedEntries.forEach { (name, payload) ->
                val entry = ZipEntry(name).apply {
                    method = ZipEntry.DEFLATED
                }
                zos.putNextEntry(entry)
                zos.write(payload)
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
