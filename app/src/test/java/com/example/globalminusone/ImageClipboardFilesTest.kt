package com.example.globalminusone

import com.example.globalminusone.system.ImageClipboardFiles
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ImageClipboardFilesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun stagedImageDoesNotRemovePreviouslyPublishedImage() {
        val directory = temporary.newFolder()
        val files = ImageClipboardFiles(directory)
        val first = files.stage { it.writeBytes(byteArrayOf(1)) }
        files.retainPublished(first)
        val second = files.stage { it.writeBytes(byteArrayOf(2)) }
        assertTrue(first.isFile)
        assertTrue(second.isFile)
        assertEquals(first.name, File(directory, "current").readText())
    }

    @Test fun publishingNextImageReplacesMarkerAndRemovesOnlyOldImages() {
        val directory = temporary.newFolder()
        val files = ImageClipboardFiles(directory)
        val first = files.stage { it.writeBytes(byteArrayOf(1)) }
        files.retainPublished(first)
        val unrelated = File(directory, "unrelated.txt").apply { writeText("keep") }
        val second = files.stage { it.writeBytes(byteArrayOf(2)) }
        files.retainPublished(second)
        assertFalse(first.exists())
        assertTrue(second.isFile)
        assertTrue(unrelated.isFile)
        assertEquals(second.name, File(directory, "current").readText())
    }

    @Test fun failedEncodingDeletesPartialImageAndKeepsPublishedImage() {
        val directory = temporary.newFolder()
        val files = ImageClipboardFiles(directory)
        val first = files.stage { it.writeBytes(byteArrayOf(1)) }
        files.retainPublished(first)
        assertThrows(IllegalStateException::class.java) {
            files.stage { it.writeBytes(byteArrayOf(2)); error("encode failed") }
        }
        assertTrue(first.isFile)
        assertEquals(setOf(first.name, "current"), directory.list()!!.toSet())
    }

    @Test fun newStoreInstanceKeepsThePinnedImageWithoutPreviewLifetime() {
        val directory = temporary.newFolder()
        val original = ImageClipboardFiles(directory)
        val first = original.stage { it.writeBytes(byteArrayOf(1)) }
        original.retainPublished(first)
        val reopened = ImageClipboardFiles(directory)
        val second = reopened.stage { it.writeBytes(byteArrayOf(2)) }
        second.delete() // cancelled publication
        assertTrue(first.isFile)
        assertEquals(first.name, File(directory, "current").readText())
    }

    @Test fun emptyEncodingIsRejectedAndCleanedUp() {
        val directory = temporary.newFolder()
        assertThrows(IllegalStateException::class.java) { ImageClipboardFiles(directory).stage { it.createNewFile() } }
        assertTrue(directory.list()!!.isEmpty())
    }
    @Test fun unsupportedAtomicMoveFallsBackAndPinsNewPublishedImage() {
        val directory = temporary.newFolder()
        val files = ImageClipboardFiles(directory) { source, target ->
            throw java.nio.file.AtomicMoveNotSupportedException(source.path, target.path, "test filesystem")
        }
        val first = files.stage { it.writeBytes(byteArrayOf(1)) }
        files.retainPublished(first)
        val second = files.stage { it.writeBytes(byteArrayOf(2)) }
        files.retainPublished(second)
        assertEquals(second.name, File(directory, "current").readText())
        assertFalse(first.exists())
        assertTrue(second.isFile)
        assertFalse(File(directory, "current.pending").exists())
    }

    @Test fun otherMoveFailureRetainsPublishedImagesAndOldMarker() {
        val directory = temporary.newFolder()
        val files = ImageClipboardFiles(directory)
        val first = files.stage { it.writeBytes(byteArrayOf(1)) }
        files.retainPublished(first)
        val second = files.stage { it.writeBytes(byteArrayOf(2)) }
        val failing = ImageClipboardFiles(directory) { _, _ -> throw java.io.IOException("disk failure") }
        assertThrows(java.io.IOException::class.java) { failing.retainPublished(second) }
        assertEquals(first.name, File(directory, "current").readText())
        assertTrue(first.isFile)
        assertTrue(second.isFile)
        assertFalse(File(directory, "current.pending").exists())
    }
}
