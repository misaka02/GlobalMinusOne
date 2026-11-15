package com.example.globalminusone.data.panels

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.zip.CRC32
import java.util.zip.Inflater
import java.util.zip.ZipException

/**
 * Pure-Kotlin bounded ZIP reader that parses the Central Directory and Local File Headers
 * directly without delegating entry traversal to [java.util.zip.ZipInputStream] or
 * [java.util.zip.ZipFile].
 *
 * On Android 14+ (API 34+, especially with targetSdk = 36), `dalvik.system.ZipPathValidator`
 * throws `ZipException("Invalid zip entry path: /original/...")` before application code can
 * inspect or normalize legitimate third-party `.bkp` archives whose icon entries start with `/`.
 * This reader enforces caller-supplied path rules, size caps, decompression ratios, and CRC32
 * integrity explicitly across all Android versions.
 */
object SafeZipReader {
    private const val SIG_EOCD = 0x06054b50L
    private const val SIG_CDFH = 0x02014b50L
    private const val SIG_LFH = 0x04034b50L
    private const val METHOD_STORED = 0
    private const val METHOD_DEFLATED = 8

    data class EntryMeta(
        val name: String,
        val isDirectory: Boolean,
        val compressedSize: Long,
        val uncompressedSize: Long,
        val crc32: Long,
        val method: Int,
        val localHeaderOffset: Long
    )

    data class ListingSummary(
        val totalEntries: Int,
        val entries: List<EntryMeta>
    )

    private interface RandomSource : AutoCloseable {
        val length: Long
        fun readFully(offset: Long, dest: ByteArray, destOffset: Int = 0, byteCount: Int = dest.size)
    }

    private class ByteArraySource(private val data: ByteArray) : RandomSource {
        override val length: Long get() = data.size.toLong()
        override fun readFully(offset: Long, dest: ByteArray, destOffset: Int, byteCount: Int) {
            if (offset < 0L || byteCount < 0 || offset + byteCount > data.size.toLong()) {
                throw ZipException("压缩包结构越界或已截断")
            }
            System.arraycopy(data, offset.toInt(), dest, destOffset, byteCount)
        }
        override fun close() = Unit
    }

    private class FileSource(file: File) : RandomSource {
        private val raf = RandomAccessFile(file, "r")
        override val length: Long = raf.length()
        override fun readFully(offset: Long, dest: ByteArray, destOffset: Int, byteCount: Int) {
            if (offset < 0L || byteCount < 0 || offset + byteCount > length) {
                throw ZipException("压缩包结构越界或已截断")
            }
            raf.seek(offset)
            raf.readFully(dest, destOffset, byteCount)
        }
        override fun close() = raf.close()
    }

    fun readBoundedBytes(input: InputStream, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            require(out.size().toLong() + count <= maxBytes) { "压缩包体积超过安全上限" }
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }

    fun listFileEntries(file: File, maxCollectedEntries: Int = 200): ListingSummary =
        FileSource(file).use { parseCentralDirectory(it, maxAllowedEntries = Int.MAX_VALUE, maxCollectedEntries = maxCollectedEntries) }

    fun listByteEntries(bytes: ByteArray, maxCollectedEntries: Int = 300): ListingSummary =
        ByteArraySource(bytes).use { parseCentralDirectory(it, maxAllowedEntries = Int.MAX_VALUE, maxCollectedEntries = maxCollectedEntries) }

    fun looksLikePanelsArchive(bytes: ByteArray): Boolean = runCatching {
        val names = listByteEntries(bytes, maxCollectedEntries = 300).entries.map { it.name.removePrefix("/") }.toSet()
        "items_table.json" in names && "panels_table.json" in names && "sets_table.json" in names
    }.getOrDefault(false)

    fun readEntries(
        input: InputStream,
        maxArchiveBytes: Int,
        maxEntries: Int,
        maxEntryBytes: Int,
        maxTotalUncompressedBytes: Int,
        validateEntry: (EntryMeta) -> Unit
    ): LinkedHashMap<String, ByteArray> {
        val archiveBytes = readBoundedBytes(input, maxArchiveBytes)
        return readEntriesFromBytes(
            archiveBytes = archiveBytes,
            maxEntries = maxEntries,
            maxEntryBytes = maxEntryBytes,
            maxTotalUncompressedBytes = maxTotalUncompressedBytes,
            validateEntry = validateEntry
        )
    }

    fun readEntriesFromBytes(
        archiveBytes: ByteArray,
        maxEntries: Int,
        maxEntryBytes: Int,
        maxTotalUncompressedBytes: Int,
        include: (EntryMeta) -> Boolean = { true },
        validateEntry: (EntryMeta) -> Unit
    ): LinkedHashMap<String, ByteArray> {
        val listing = ByteArraySource(archiveBytes).use {
            parseCentralDirectory(it, maxAllowedEntries = maxEntries, maxCollectedEntries = maxEntries)
        }
        val result = linkedMapOf<String, ByteArray>()
        var totalUncompressed = 0L
        val lfhBuf = ByteArray(30)
        val inflateBuf = ByteArray(8192)

        for (entry in listing.entries) {
            validateEntry(entry)
            if (entry.isDirectory || !include(entry)) continue
            require(entry.name !in result) { "重复的备份文件" }
            require(entry.uncompressedSize in 0..maxEntryBytes.toLong()) { "Panels 备份单文件过大" }
            require(totalUncompressed + entry.uncompressedSize <= maxTotalUncompressedBytes.toLong()) { "Panels 备份解压大小超限" }
            require(entry.compressedSize >= 0L) { "压缩包条目大小无效" }

            val lfhOffset = entry.localHeaderOffset
            if (lfhOffset < 0L || lfhOffset + 30L > archiveBytes.size.toLong()) {
                throw ZipException("本地文件头偏移无效")
            }
            System.arraycopy(archiveBytes, lfhOffset.toInt(), lfhBuf, 0, 30)
            if (u32(lfhBuf, 0) != SIG_LFH) {
                throw ZipException("本地文件头签名无效")
            }
            val lfhNameLen = u16(lfhBuf, 26)
            val lfhExtraLen = u16(lfhBuf, 28)
            val dataOffset = lfhOffset + 30L + lfhNameLen + lfhExtraLen
            if (dataOffset < 0L || dataOffset + entry.compressedSize > archiveBytes.size.toLong()) {
                throw ZipException("压缩数据越界或文件已损坏")
            }

            val payload = when (entry.method) {
                METHOD_STORED -> {
                    if (entry.compressedSize != entry.uncompressedSize) {
                        throw ZipException("未压缩条目长度不匹配")
                    }
                    totalUncompressed += entry.uncompressedSize
                    require(totalUncompressed <= maxTotalUncompressedBytes.toLong()) { "Panels 备份解压大小超限" }
                    val raw = archiveBytes.copyOfRange(dataOffset.toInt(), (dataOffset + entry.compressedSize).toInt())
                    val crc = CRC32().apply { update(raw) }.value
                    if (crc != entry.crc32) throw ZipException("备份文件 CRC32 校验失败：${entry.name}")
                    raw
                }
                METHOD_DEFLATED -> {
                    val out = ByteArrayOutputStream(entry.uncompressedSize.toInt().coerceAtMost(65536))
                    val crc = CRC32()
                    val inflater = Inflater(true)
                    try {
                        inflater.setInput(archiveBytes, dataOffset.toInt(), entry.compressedSize.toInt())
                        while (!inflater.finished()) {
                            val count = inflater.inflate(inflateBuf)
                            if (count == 0) {
                                if (inflater.finished()) break
                                if (inflater.needsDictionary() || inflater.needsInput()) {
                                    throw ZipException("压缩数据不完整或已损坏：${entry.name}")
                                }
                            } else {
                                totalUncompressed += count
                                require(out.size() + count <= maxEntryBytes && totalUncompressed <= maxTotalUncompressedBytes.toLong()) {
                                    "Panels 备份解压大小超限"
                                }
                                crc.update(inflateBuf, 0, count)
                                out.write(inflateBuf, 0, count)
                            }
                        }
                    } catch (e: java.util.zip.DataFormatException) {
                        throw ZipException("压缩数据格式错误：${e.message ?: entry.name}")
                    } finally {
                        inflater.end()
                    }
                    val inflated = out.toByteArray()
                    if (inflated.size.toLong() != entry.uncompressedSize) {
                        throw ZipException("解压后大小与目录记录不符：${entry.name}")
                    }
                    if (crc.value != entry.crc32) {
                        throw ZipException("备份文件 CRC32 校验失败：${entry.name}")
                    }
                    inflated
                }
                else -> throw ZipException("不支持的 ZIP 压缩方式：${entry.method}")
            }
            result[entry.name] = payload
        }
        return result
    }

    /** Extracts only selected file entries, without loading the complete archive into memory. */
    fun readSelectedFileEntries(
        file: File,
        maxEntries: Int,
        maxEntryBytes: Int,
        maxTotalUncompressedBytes: Int,
        include: (EntryMeta) -> Boolean,
        validateEntry: (EntryMeta) -> Unit
    ): LinkedHashMap<String, ByteArray> = FileSource(file).use { source ->
        val listing = parseCentralDirectory(source, maxEntries, maxEntries)
        val selected = linkedMapOf<String, ByteArray>()
        var total = 0L
        val header = ByteArray(30)
        val inflateBuffer = ByteArray(8192)
        for (entry in listing.entries) {
            validateEntry(entry)
            if (entry.isDirectory || !include(entry)) continue
            if (entry.name in selected) throw ZipException("重复的 ZIP 文件：${entry.name}")
            check(entry.uncompressedSize in 0..maxEntryBytes.toLong() &&
                total + entry.uncompressedSize <= maxTotalUncompressedBytes.toLong()) { "预览 XML 超过安全上限" }
            // DEFLATE overhead is small for a bounded XML entry; reject anomalous sizes before allocation.
            check(entry.compressedSize in 0..(maxEntryBytes.toLong() + 65536L)) { "预览压缩条目超过安全上限" }
            source.readFully(entry.localHeaderOffset, header)
            if (u32(header, 0) != SIG_LFH) throw ZipException("本地文件头签名无效")
            val nameLength = u16(header, 26)
            val extraLength = u16(header, 28)
            val localName = ByteArray(nameLength)
            source.readFully(entry.localHeaderOffset + 30L, localName)
            if (decodeEntryName(localName, (u16(header, 6) and 0x800) != 0) != entry.name || u16(header, 8) != entry.method) {
                throw ZipException("本地文件头与中央目录不一致")
            }
            val compressed = ByteArray(entry.compressedSize.toInt())
            source.readFully(entry.localHeaderOffset + 30L + nameLength + extraLength, compressed)
            val payload = when (entry.method) {
                METHOD_STORED -> {
                    if (entry.compressedSize != entry.uncompressedSize) throw ZipException("条目长度不匹配")
                    compressed
                }
                METHOD_DEFLATED -> {
                    val inflater = Inflater(true)
                    val output = ByteArrayOutputStream(minOf(entry.uncompressedSize.toInt(), 65536))
                    try {
                        inflater.setInput(compressed)
                        while (!inflater.finished()) {
                            val count = inflater.inflate(inflateBuffer)
                            if (count == 0) {
                                if (inflater.needsInput() || inflater.needsDictionary()) throw ZipException("压缩数据不完整")
                                throw ZipException("压缩数据无法继续解码")
                            }
                            if (output.size() + count > maxEntryBytes || output.size().toLong() + count > entry.uncompressedSize) {
                                throw ZipException("预览 XML 超过安全上限")
                            }
                            output.write(inflateBuffer, 0, count)
                        }
                    } catch (e: java.util.zip.DataFormatException) {
                        throw ZipException("压缩数据格式错误：${e.message ?: entry.name}")
                    } finally { inflater.end() }
                    output.toByteArray()
                }
                else -> throw ZipException("不支持的 ZIP 压缩方式：${entry.method}")
            }
            if (payload.size.toLong() != entry.uncompressedSize || CRC32().apply { update(payload) }.value != entry.crc32) {
                throw ZipException("ZIP 条目长度或 CRC32 不匹配：${entry.name}")
            }
            total += payload.size
            selected[entry.name] = payload
        }
        selected
    }

    private fun parseCentralDirectory(
        source: RandomSource,
        maxAllowedEntries: Int,
        maxCollectedEntries: Int
    ): ListingSummary {
        val len = source.length
        if (len < 22L) throw ZipException("不是有效的 ZIP 压缩包（文件过小）")

        val tailSize = minOf(len, 65557L).toInt()
        val tailOffset = len - tailSize
        val tail = ByteArray(tailSize)
        source.readFully(tailOffset, tail)

        var eocdInTail = -1
        for (i in (tailSize - 22) downTo 0) {
            if (tail[i] == 0x50.toByte() &&
                tail[i + 1] == 0x4b.toByte() &&
                tail[i + 2] == 0x05.toByte() &&
                tail[i + 3] == 0x06.toByte()
            ) {
                val commentLen = u16(tail, i + 20)
                if (i + 22 + commentLen == tailSize) {
                    eocdInTail = i
                    break
                }
            }
        }
        if (eocdInTail < 0) throw ZipException("不是有效的 ZIP 压缩包（缺少中央目录尾）")

        val diskNum = u16(tail, eocdInTail + 4)
        val cdStartDisk = u16(tail, eocdInTail + 6)
        if (diskNum != 0 || cdStartDisk != 0) throw ZipException("不支持分卷 ZIP 压缩包")

        val totalEntries = u16(tail, eocdInTail + 10)
        val cdSize = u32(tail, eocdInTail + 12)
        val cdOffset = u32(tail, eocdInTail + 16)
        if (totalEntries == 0xFFFF || cdSize == 0xFFFFFFFFL || cdOffset == 0xFFFFFFFFL) {
            throw ZipException("不支持 ZIP64 超大压缩包")
        }
        require(totalEntries <= maxAllowedEntries) { "Panels 备份文件数量超限" }

        val eocdFileOffset = tailOffset + eocdInTail
        if (cdOffset < 0L || cdSize < 0L || cdOffset + cdSize > eocdFileOffset) {
            throw ZipException("ZIP 中央目录偏移无效")
        }

        val collected = ArrayList<EntryMeta>(minOf(totalEntries, maxCollectedEntries, 256))
        var cursor = cdOffset
        val cdEnd = cdOffset + cdSize
        val fixedHeader = ByteArray(46)

        val toRead = minOf(totalEntries, maxCollectedEntries)
        for (idx in 0 until toRead) {
            if (cursor + 46L > cdEnd) throw ZipException("ZIP 中央目录记录截断")
            source.readFully(cursor, fixedHeader)
            if (u32(fixedHeader, 0) != SIG_CDFH) throw ZipException("ZIP 中央目录文件头签名无效")

            val flags = u16(fixedHeader, 8)
            if ((flags and 0x1) != 0) throw ZipException("不支持加密的 ZIP 压缩包")
            val method = u16(fixedHeader, 10)
            val crc32 = u32(fixedHeader, 16)
            val compSize = u32(fixedHeader, 20)
            val uncompSize = u32(fixedHeader, 24)
            val nameLen = u16(fixedHeader, 28)
            val extraLen = u16(fixedHeader, 30)
            val commentLen = u16(fixedHeader, 32)
            val localOffset = u32(fixedHeader, 42)

            val variableLen = nameLen.toLong() + extraLen.toLong() + commentLen.toLong()
            if (cursor + 46L + variableLen > cdEnd) throw ZipException("ZIP 中央目录条目名称越界")

            val nameBytes = ByteArray(nameLen)
            if (nameLen > 0) source.readFully(cursor + 46L, nameBytes)
            val name = decodeEntryName(nameBytes, utf8Flag = (flags and 0x800) != 0)
            if ('\u0000' in name) throw ZipException("压缩包路径包含非法空字符")

            collected.add(
                EntryMeta(
                    name = name,
                    isDirectory = name.endsWith("/"),
                    compressedSize = compSize,
                    uncompressedSize = uncompSize,
                    crc32 = crc32,
                    method = method,
                    localHeaderOffset = localOffset
                )
            )
            cursor += 46L + variableLen
        }
        return ListingSummary(totalEntries = totalEntries, entries = collected)
    }

    private fun decodeEntryName(bytes: ByteArray, utf8Flag: Boolean): String {
        val utf8Decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            utf8Decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: Exception) {
            if (utf8Flag) throw ZipException("ZIP 文件名 UTF-8 编码损坏")
            String(bytes, Charsets.ISO_8859_1)
        }
    }

    private fun u16(buf: ByteArray, offset: Int): Int =
        (buf[offset].toInt() and 0xFF) or ((buf[offset + 1].toInt() and 0xFF) shl 8)

    private fun u32(buf: ByteArray, offset: Int): Long =
        (buf[offset].toLong() and 0xFFL) or
            ((buf[offset + 1].toLong() and 0xFFL) shl 8) or
            ((buf[offset + 2].toLong() and 0xFFL) shl 16) or
            ((buf[offset + 3].toLong() and 0xFFL) shl 24)
}
