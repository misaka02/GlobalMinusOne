package com.example.globalminusone.data.panels

import java.io.File
import java.net.URI
import java.util.Base64
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PanelsIconStagingTest {
    @get:Rule val temp = TemporaryFolder()
    private val png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aT1kAAAAASUVORK5CYII=")

    @Test fun confirmedAssetsPersistAndRepeatedReferencesShareOneOwnedFile() {
        val root = temp.newFolder()
        var uri = ""
        PanelsIconStaging(root, mapOf("/original/icon.png" to png)).use { stage ->
            uri = stage.uriFor("/original/icon.png")
            assertEquals(uri, stage.uriFor("/original/icon.png"))
            stage.commit()
        }
        val file = File(URI(uri))
        assertArrayEquals(png, file.readBytes())
        assertTrue(file.canonicalFile.toPath().startsWith(root.canonicalFile.toPath()))
    }

    @Test fun rejectedImportCleansOnlyItsOwnStagingFilesAndNeverUsesSourcePaths() {
        val root = temp.newFolder()
        val sentinel = File(root, "keep.png").apply { writeText("user asset") }
        var staged: File? = null
        try {
            PanelsIconStaging(root, mapOf("../../keep.png" to png)).use { stage ->
                staged = File(URI(stage.uriFor("../../keep.png")))
                assertNotEquals(sentinel.canonicalPath, staged!!.canonicalPath)
                throw IllegalStateException("destination removed before append")
            }
        } catch (_: IllegalStateException) { }
        assertFalse(staged!!.exists())
        assertEquals("user asset", sentinel.readText())
    }

    @Test fun missingOrCorruptAssetDoesNotReplaceAnExistingResource() {
        val root = temp.newFolder()
        PanelsIconStaging(root, emptyMap()).use { assertEquals("", it.uriFor("missing.png")) }
        try {
            PanelsIconStaging(root, mapOf("broken.png" to byteArrayOf(1, 2))).use { it.uriFor("broken.png") }
            fail("Invalid image accepted")
        } catch (_: IllegalArgumentException) { }
        assertFalse(File(root, "panels-import").exists())
    }

    @Test fun stagingUsesSame2048PixelLimitAsImporter() {
        val oversizedHeader = png.copyOf().apply {
            this[18] = 8
            this[19] = 1 // 0x00000801 = 2049 pixels
        }
        val root = temp.newFolder()
        assertThrows(IllegalArgumentException::class.java) {
            PanelsIconStaging(root, mapOf("icon" to oversizedHeader)).use { it.uriFor("icon") }
        }
        assertFalse(File(root, "panels-import").exists())
    }
}
