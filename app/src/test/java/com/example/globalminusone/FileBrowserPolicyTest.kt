package com.example.globalminusone

import com.example.globalminusone.data.FileBrowserConfig
import com.example.globalminusone.data.FileBrowserPolicy
import com.example.globalminusone.data.FileBrowserSortOrder
import com.example.globalminusone.system.BrowserEntry
import com.example.globalminusone.system.BrowserLocation
import com.example.globalminusone.system.sortBrowserEntries
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FileBrowserPolicyTest {
    private val root = File(System.getProperty("java.io.tmpdir"), "gmo-browser-boundary/shared")

    @Test
    fun canonicalBoundaryAllowsRootAndChildrenButRejectsTraversalAndSiblingPrefix() {
        assertTrue(FileBrowserPolicy.allowsSharedPath(root, root))
        assertTrue(FileBrowserPolicy.allowsSharedPath(root, File(root, "Documents/a.txt")))
        assertFalse(FileBrowserPolicy.allowsSharedPath(root, File(root, "../outside/secret.txt")))
        assertFalse(FileBrowserPolicy.allowsSharedPath(root, File(root.parentFile, "shared-backup/a.txt")))
    }

    @Test
    fun protectedAndroidTreesAreRefusedWithoutBlockingOtherAndroidDirectories() {
        listOf("Android/data", "Android/data/app/files/a", "Android/obb/game/a", "android/DATA/file").forEach {
            assertFalse(it, FileBrowserPolicy.allowsSharedPath(root, File(root, it)))
        }
        assertTrue(FileBrowserPolicy.allowsSharedPath(root, File(root, "Android/media/app/photo.jpg")))
        assertTrue(FileBrowserPolicy.allowsSharedPath(root, File(root, "Android/database/info")))
        assertTrue(FileBrowserPolicy.allowsSharedPath(root, File(root, "Documents/Android/data/example.txt")))
    }

    @Test
    fun samplingKeepsVeryWideTallAndLargeImagesWithinPreviewBudget() {
        listOf(100 to 100, 4097 to 100, 80_000 to 100, 100 to 80_000, 16_000 to 12_000, Int.MAX_VALUE to Int.MAX_VALUE).forEach { (width, height) ->
            val sample = FileBrowserPolicy.imageSampleSize(width, height)
            assertTrue(sample > 0 && (sample and (sample - 1)) == 0)
            val sampledWidth = (width.toLong() + sample - 1) / sample
            val sampledHeight = (height.toLong() + sample - 1) / sample
            assertTrue(sampledWidth <= 2048)
            assertTrue(sampledHeight <= 2048)
            assertTrue(sampledWidth * sampledHeight <= 2_097_152L)
        }
        assertEquals(1, FileBrowserPolicy.imageSampleSize(1024, 1024))
    }

    @Test
    fun everySortKeepsDirectoriesFirstAndUsesDeterministicNameTies() {
        val list = listOf(
            entry("b.txt", size = 40, modified = 20), entry("A.txt", size = 10, modified = 40),
            entry("z-dir", directory = true), entry("a-dir", directory = true), entry("c.txt", size = 40, modified = 20)
        )
        assertEquals(listOf("a-dir", "z-dir", "A.txt", "b.txt", "c.txt"), sortBrowserEntries(list, FileBrowserSortOrder.NAME).map { it.name })
        assertEquals(listOf("a-dir", "z-dir", "b.txt", "c.txt", "A.txt"), sortBrowserEntries(list, FileBrowserSortOrder.SIZE).map { it.name })
        assertEquals(listOf("a-dir", "z-dir", "A.txt", "b.txt", "c.txt"), sortBrowserEntries(list, FileBrowserSortOrder.MODIFIED).map { it.name })
    }

    @Test
    fun oldConfigurationCanOmitFileBrowserPreferences() {
        assertEquals(FileBrowserConfig(), Json.decodeFromString<FileBrowserConfig>("{}"))
    }

    private fun entry(name: String, directory: Boolean = false, size: Long? = null, modified: Long? = null) = BrowserEntry(
        BrowserLocation(name, path = "/shared/$name"), name, directory, if (directory) "directory" else "text/plain", size, modified
    )
}
