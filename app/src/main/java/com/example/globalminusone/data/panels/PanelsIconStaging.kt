package com.example.globalminusone.data.panels

import java.io.Closeable
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Header and dimension guard only; Android's decoder validates pixels when displayed. */
internal fun validatePanelsPng(bytes: ByteArray) {
    require(bytes.size in 24..4_194_304 && bytes.take(8).toByteArray().contentEquals(
        byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)) &&
        String(bytes, 12, 4, Charsets.US_ASCII) == "IHDR") { "Panels 图标 PNG 头无效" }
    fun sizeAt(offset: Int): Long = (0..3).fold(0L) { value, i -> (value shl 8) or (bytes[offset + i].toLong() and 255) }
    val width = sizeAt(16)
    val height = sizeAt(20)
    require(width in 1..2048 && height in 1..2048 && width * height <= 4_194_304L) { "Panels 图标尺寸无效" }
}

/** Owns only newly created flat files. Failed imports never leave dangling asset references
 * or delete an existing asset; archive names are never used as filesystem paths. */
internal class PanelsIconStaging(private val filesRoot: File, private val assets: Map<String, ByteArray>) : Closeable {
    private val directory = File(File(filesRoot, "panels-import"), UUID.randomUUID().toString())
    private val written = linkedMapOf<String, File>()
    private var committed = false

    fun uriFor(key: String): String {
        val bytes = assets[key] ?: return ""
        written[key]?.let { return it.toURI().toString() }
        validatePanelsPng(bytes)
        require(directory.canonicalFile.toPath().startsWith(filesRoot.canonicalFile.toPath())) { "图标目录无效" }
        check(directory.isDirectory || directory.mkdirs()) { "无法创建图标目录" }
        val name = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) } + ".png"
        val file = File(directory, name)
        check(!file.exists()) { "图标文件冲突" }
        written[key] = file
        file.outputStream().use { it.write(bytes) }
        return file.toURI().toString()
    }

    fun commit() { committed = true }
    override fun close() {
        if (!committed) {
            written.values.forEach { file -> if (file.canonicalFile.parentFile == directory.canonicalFile) file.delete() }
            directory.delete() // deliberately non-recursive
        }
    }
}
