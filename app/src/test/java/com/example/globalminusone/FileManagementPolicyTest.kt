package com.example.globalminusone

import com.example.globalminusone.system.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FileManagementPolicyTest {
    private val root = BrowserLocation("root", path = "/synthetic")
    private fun file(name: String, size: Long? = 1, mime: String = "application/octet-stream") =
        BrowserEntry(BrowserLocation(name, path = "/synthetic/$name"), name, false, mime, size, null)

    @Test fun unknownSizesDoNotBecomeZeroByteFilesOrPolluteRatios() {
        val audio = FileCategoryUsage().including(100).including(null).including(-1).including(0)
        assertEquals(4L, audio.files); assertEquals(100L, audio.knownBytes); assertEquals(2L, audio.unknownSizes)
        val result = FileScanResult(mapOf(FileCategory.AUDIO to audio, FileCategory.IMAGE to FileCategoryUsage().including(300)), 1, 0, 2)
        assertEquals(0.25, result.knownRatio(FileCategory.AUDIO)!!, 0.00001)
        assertEquals(2L, result.inaccessibleDirectories)
        assertNull(FileScanResult(mapOf(FileCategory.OTHER to FileCategoryUsage().including(null)), 1, 0).knownRatio(FileCategory.OTHER))
    }
    @Test fun namesRejectTraversalSeparatorsNulAndEmptyButKeepUnicode() {
        listOf("", " ", ".", "..", "a/b", "a\\b", "a\u0000b", "x".repeat(256)).forEach { bad ->
            try { validatedFileName(bad); fail("Accepted unsafe name") } catch (_: IllegalArgumentException) { }
        }
        assertEquals("照片 2026.png", validatedFileName("  照片 2026.png  "))
        assertEquals(".hidden", validatedFileName(".hidden"))
    }
    @Test fun fileTypesUseMimeOrKnownExtensionsCaseInsensitively() {
        assertEquals(FileCategory.AUDIO, FileCategory.of(file("voice.MP3")))
        assertEquals(FileCategory.VIDEO, FileCategory.of(file("opaque", mime = "video/webm")))
        assertEquals(FileCategory.DOCUMENT, FileCategory.of(file("plan.PDF")))
        assertEquals(FileCategory.ARCHIVE, FileCategory.of(file("backup.7z")))
        assertEquals(FileCategory.IMAGE, FileCategory.of(file("photo.HEIC")))
        assertEquals(FileCategory.OTHER, FileCategory.of(file("opaque")))
    }
    @Test fun pagingContinuesPast1000WithoutDuplicateOrRepeatedProviderRead() = runBlocking {
        val entries = (1..1005).map { file("$it.txt") }; var reads = 0
        val first = searchFileTree(root, "", FileCategory.DOCUMENT, null, { "read" }, {}, { reads++; entries }, {})
        assertEquals(1000, first.entries.size); assertNotNull(first.continuation)
        val next = searchFileTree(root, "", FileCategory.DOCUMENT, first.continuation, { "read" }, {}, { reads++; entries }, {})
        assertEquals(5, next.entries.size); assertNull(next.continuation)
        assertEquals(1, reads)
        assertEquals(1005, (first.entries + next.entries).map { it.location.key }.distinct().size)
    }
    @Test fun canceledNextPageDoesNotAdvancePreviouslyDeliveredResumeCursor() = runBlocking {
        val sub = BrowserEntry(BrowserLocation("sub", path = "/synthetic/sub"), "sub", true, "vnd.android.document/directory", null, null)
        val entries = (1..1000).map { file("$it.txt") } + sub
        val first = searchFileTree(root, "", FileCategory.DOCUMENT, null, { "read" }, {}, { entries }, {})
        val cursor = first.continuation!!
        try {
            searchFileTree(root, "", FileCategory.DOCUMENT, cursor, { "read" }, {}, { if (it.key == sub.location.key) listOf(file("last.txt")) else entries }, { throw CancellationException("cancel") })
            fail("Canceled traversal published results")
        } catch (_: CancellationException) { }
        assertEquals(1000, cursor.index)
        val resumed = searchFileTree(root, "", FileCategory.DOCUMENT, cursor, { "read" }, {}, { listOf(file("last.txt")) }, {})
        assertEquals(listOf("last.txt"), resumed.entries.map { it.name })
    }
    @Test fun resumeRejectsChangedGrantOrScopeAndReportsInaccessibleDirectories() = runBlocking {
        val entries = (1..1001).map { file("$it.txt") }
        val cursor = searchFileTree(root, "", FileCategory.ALL, null, { "read" }, {}, { entries }, {}).continuation
        try {
            searchFileTree(root, "", FileCategory.ALL, cursor, { "revoked" }, {}, { entries }, {})
            fail("Reused a cursor after grant changed")
        } catch (_: IllegalStateException) { }
        try {
            searchFileTree(BrowserLocation("other", path = "/other"), "", FileCategory.ALL, cursor, { "read" }, {}, { entries }, {})
            fail("Reused a cursor for another scope")
        } catch (_: IllegalStateException) { }
        val blocked = file("private").copy(isDirectory = true, blockedReason = "protected")
        val result = searchFileTree(root, "", FileCategory.ALL, null, { "read" }, {}, { listOf(blocked, file("safe.txt")) }, {})
        assertEquals(1, result.skippedDirectories); assertEquals(listOf("safe.txt"), result.entries.map { it.name })
    }
    @Test fun quickFilterAndChecksumsMatchExpectedDigests() {
        assertTrue(matchesQuickFilter("via_browser.apk", "*.apk, *.zip"))
        assertTrue(matchesQuickFilter("IMG_20261101.PNG", "img_*.png"))
        assertFalse(matchesQuickFilter("notes.txt", "*.apk, *.zip"))
        val checksums = computeStreamChecksums("hello".byteInputStream())
        assertEquals("5d41402abc4b2a76b9719d911017c592", checksums.md5)
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", checksums.sha256)
        assertEquals("SHA-256", checksums.matchedAlgorithm("  2CF24DBA5FB0A30E26E83B2AC5B9E29E1B161E5C1FA7425E73043362938B9824 "))
        assertEquals("CRC32", checksums.matchedAlgorithm("0x3610a686"))
        assertNull(checksums.matchedAlgorithm("deadbeef"))
    }

    @Test fun analyzerInPlaceDeleteAndRenameUpdateScanAndDuplicatesWithoutRescan() {
        val moviesDir = BrowserLocation("movies", path = "/synthetic/movies")
        val videoBig = BrowserEntry(
            BrowserLocation("clip.mp4", path = "/synthetic/movies/clip.mp4"),
            "clip.mp4", false, "video/mp4", 20L * 1024 * 1024, 2000L, parentLocation = moviesDir
        )
        val appPkg = BrowserEntry(
            BrowserLocation("app.apk", path = "/synthetic/app.apk"),
            "app.apk", false, "application/vnd.android.package-archive", 15L * 1024 * 1024, 1000L, parentLocation = root
        )
        val draftDoc = BrowserEntry(
            BrowserLocation("draft.txt", path = "/synthetic/movies/draft.txt"),
            "draft.txt", false, "text/plain", 4096L, 3000L, parentLocation = moviesDir
        )
        val emptySub = BrowserLocation("empty_sub", path = "/synthetic/empty_sub")

        val initial = FileScanResult(
            groups = mapOf(
                FileCategory.VIDEO to FileCategoryUsage().including(videoBig.size),
                FileCategory.OTHER to FileCategoryUsage().including(appPkg.size),
                FileCategory.DOCUMENT to FileCategoryUsage().including(draftDoc.size)
            ),
            directories = 3L,
            completedAt = 123456L,
            largeFiles = listOf(videoBig, appPkg),
            apkFiles = listOf(appPkg),
            emptyDirectories = listOf(emptySub),
            categoryFiles = mapOf(
                FileCategory.VIDEO to listOf(videoBig),
                FileCategory.OTHER to listOf(appPkg),
                FileCategory.DOCUMENT to listOf(draftDoc)
            )
        )

        assertEquals("movies", relativeAnalyzerParentLabel(root, videoBig))
        assertEquals("当前目录", relativeAnalyzerParentLabel(root, appPkg))
        assertEquals(listOf("clip.mp4", "app.apk", "draft.txt"),
            sortAnalyzerEntries(listOf(draftDoc, appPkg, videoBig), AnalyzerSortOrder.SIZE_DESC).map { it.name })
        assertEquals(listOf("draft.txt", "clip.mp4", "app.apk"),
            sortAnalyzerEntries(listOf(draftDoc, appPkg, videoBig), AnalyzerSortOrder.TIME_DESC).map { it.name })

        // Rename draft.txt -> archive.zip (DOCUMENT -> ARCHIVE transition in-place)
        val renamedZip = draftDoc.copy(
            location = BrowserLocation("archive.zip", path = "/synthetic/movies/archive.zip"),
            name = "archive.zip",
            mimeType = "application/zip"
        )
        val afterRename = initial.withRenamed(draftDoc, renamedZip)
        assertNull(afterRename.groups[FileCategory.DOCUMENT])
        assertEquals(1L, afterRename.groups[FileCategory.ARCHIVE]?.files)
        assertEquals(4096L, afterRename.groups[FileCategory.ARCHIVE]?.knownBytes)
        assertEquals(listOf("archive.zip"), afterRename.categoryFiles[FileCategory.ARCHIVE]?.map { it.name })
        assertNull(afterRename.categoryFiles[FileCategory.DOCUMENT])

        // Delete videoBig and emptySub in-place without re-scan
        val afterDelete = afterRename.withoutDeleted(listOf(videoBig), listOf(emptySub))
        assertNull(afterDelete.groups[FileCategory.VIDEO])
        assertNull(afterDelete.categoryFiles[FileCategory.VIDEO])
        assertEquals(listOf("app.apk"), afterDelete.largeFiles.map { it.name })
        assertTrue(afterDelete.emptyDirectories.isEmpty())
        assertEquals(2L, afterDelete.directories)

        // Duplicate groups smart redundant selection & in-place deletion
        val dupA1 = file("copy1.jpg", 5000L, "image/jpeg")
        val dupA2 = BrowserEntry(BrowserLocation("copy2.jpg", path = "/synthetic/sub/copy2.jpg"), "copy2.jpg", false, "image/jpeg", 5000L, null, parentLocation = moviesDir)
        val dupA3 = BrowserEntry(BrowserLocation("copy3.jpg", path = "/synthetic/sub2/copy3.jpg"), "copy3.jpg", false, "image/jpeg", 5000L, null, parentLocation = moviesDir)
        val dupGroups = listOf(DuplicateFileGroup(5000L, "abcd1234", listOf(dupA1, dupA2, dupA3)))
        val redundant = selectRedundantDuplicateKeys(dupGroups)
        assertEquals(setOf(dupA2.location.key, dupA3.location.key), redundant)
        val afterPartialDupDelete = dupGroups.withoutDeletedKeys(setOf(dupA3.location.key))
        assertEquals(1, afterPartialDupDelete.size)
        assertEquals(2, afterPartialDupDelete.single().entries.size)
        assertTrue(dupGroups.withoutDeletedKeys(redundant).isEmpty())
    }
}
