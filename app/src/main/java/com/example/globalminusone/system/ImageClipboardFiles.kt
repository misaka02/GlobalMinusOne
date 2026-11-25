package com.example.globalminusone.system

import java.io.File
import java.util.UUID
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** A clipboard image is retained until a later image has actually been published. */
internal class ImageClipboardFiles(
    private val directory: File,
    private val atomicMarkerMove: (File, File) -> Unit = { pending, marker ->
        Files.move(pending.toPath(), marker.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        Unit
    }
) {
    fun stage(write: (File) -> Unit): File {
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create image clipboard directory" }
        val file = File(directory, "image_${UUID.randomUUID()}.png")
        try {
            write(file)
            check(file.length() > 0) { "Empty clipboard image" }
            return file
        } catch (failure: Throwable) {
            file.delete()
            throw failure
        }
    }

    /** Call only after ClipboardManager accepted the new URI. Never called by preview cleanup. */
    fun retainPublished(file: File) {
        require(file.parentFile == directory && file.isFile)
        val pending = File(directory, "current.pending")
        val marker = File(directory, "current")
        try {
            pending.outputStream().use { stream ->
                stream.write(file.name.toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            try {
                atomicMarkerMove(pending, marker)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(pending.toPath(), marker.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            pending.delete()
        }
        directory.listFiles()?.filter { it != file && it.name.startsWith("image_") && it.extension == "png" }
            ?.forEach { it.delete() }
    }
}
