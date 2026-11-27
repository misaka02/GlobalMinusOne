package com.example.globalminusone.system.file

import com.example.globalminusone.data.panels.SafeZipReader
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

/** Bounded, read-only summaries. No embedded objects, scripts or remote resources are loaded. */
object FilePreviewFormats {
    const val DOCUMENT_BYTES = 64L * 1024 * 1024
    const val ZIP_ENTRIES = 2000
    private const val XML_BYTES = 2 * 1024 * 1024
    private const val XML_TOTAL = 8 * 1024 * 1024
    private const val OUTPUT_CHARS = 256 * 1024

    fun kind(name: String, mime: String): String {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext in setOf("apk", "jar", "aab", "bkp")) return "zip"
        if (ext in setOf("pdf", "zip", "docx", "xlsx", "pptx", "svg")) return ext
        return when {
            mime == "application/pdf" -> "pdf"
            mime == "application/zip" || mime == "application/x-zip-compressed" || mime == "application/vnd.android.package-archive" -> "zip"
            mime.contains("wordprocessingml") -> "docx"
            mime.contains("spreadsheetml") -> "xlsx"
            mime.contains("presentationml") -> "pptx"
            mime == "image/svg+xml" -> "svg"
            mime.startsWith("audio/") || ext in setOf("mp3", "wav", "m4a", "aac", "ogg", "flac") -> "audio"
            mime.startsWith("video/") || ext in setOf("mp4", "mkv", "webm", "3gp", "mov", "avi") -> "video"
            else -> ext
        }
    }

    /** Detects Zip-Slip path traversal or invalid archive entry names without extracting. */
    fun isUnsafeArchivePath(entryName: String): Boolean {
        if (entryName.isEmpty() || entryName.contains('\u0000')) return true
        if (entryName.startsWith("/") || entryName.startsWith("\\") || entryName.contains(":\\")) return true
        val segments = entryName.split('/', '\\')
        return segments.any { it == ".." }
    }

    fun decodeText(bytes: ByteArray, truncated: Boolean): Pair<String, String> {
        val le = bytes.size >= 2 && bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte()
        val be = bytes.size >= 2 && bytes[0] == 0xfe.toByte() && bytes[1] == 0xff.toByte()
        if (le || be) {
            val charset = if (le) Charsets.UTF_16LE else Charsets.UTF_16BE
            return String(bytes, 0, if (truncated) bytes.size / 2 * 2 else bytes.size, charset).removePrefix("\uFEFF") to charset.name()
        }
        check(!bytes.contains(0.toByte())) { "此文件可能是二进制格式，请使用其他应用打开" }
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        // With endOfInput=false the decoder leaves an incomplete last code point unread.
        val output = java.nio.CharBuffer.allocate(bytes.size + 1)
        val result = decoder.decode(ByteBuffer.wrap(bytes), output, !truncated)
        if (!result.isError) { output.flip(); return output.toString().removePrefix("\uFEFF") to "UTF-8" }
        val gb = java.nio.charset.Charset.forName("GB18030")
        val gbOutput = java.nio.CharBuffer.allocate(bytes.size + 1)
        val gbResult = gb.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes), gbOutput, !truncated)
        check(!gbResult.isError) { "无法识别文本编码，请使用其他应用打开" }
        gbOutput.flip()
        return gbOutput.toString() to "GB18030 / GBK"
    }

    fun summary(file: File, kind: String): String {
        if (kind == "zip") {
            val listing = SafeZipReader.listFileEntries(file, maxCollectedEntries = ZIP_ENTRIES)
            check(listing.totalEntries < ZIP_ENTRIES) { "压缩包超过 2000 项预览上限，请使用其他应用打开" }
            val entries = listing.entries
            val dirCount = entries.count { it.isDirectory }
            val fileCount = entries.size - dirCount
            val totalUncompressed = entries.sumOf { it.uncompressedSize.coerceAtLeast(0L) }
            val totalCompressed = entries.sumOf { it.compressedSize.coerceAtLeast(0L) }
            val unsafeCount = entries.count { isUnsafeArchivePath(it.name) }
            val hasManifest = entries.any { it.name == "AndroidManifest.xml" }
            val dexCount = entries.count { it.name.matches(Regex("classes[0-9]*\\.dex")) }
            val soAbis = entries.mapNotNull {
                if (it.name.startsWith("lib/") && it.name.endsWith(".so")) it.name.removePrefix("lib/").substringBefore('/', "") else null
            }.filter { it.isNotEmpty() }.distinct().sorted()
            val out = StringBuilder("ZIP / APK 归档清单（只读安全检查，不解压）\n")
            out.append("共 ${entries.size} 项（文件 $fileCount · 目录 $dirCount） · 原始 ${totalUncompressed} B · 压缩后 ${totalCompressed} B\n")
            if (hasManifest || dexCount > 0) {
                out.append("APK 结构：Manifest=${if (hasManifest) "有" else "无"} · DEX=$dexCount 个")
                if (soAbis.isNotEmpty()) out.append(" · Native ABI=${soAbis.joinToString("/")}")
                out.append('\n')
            }
            if (unsafeCount > 0) {
                out.append("⚠ 安全警告：检测到 $unsafeCount 项含路径穿越或前导斜杠特征，切勿直接解压到系统目录！\n")
            }
            out.append('\n')
            for (entry in entries) {
                if (out.length > OUTPUT_CHARS) { out.append("\n…清单已截断"); break }
                val unsafeTag = if (isUnsafeArchivePath(entry.name)) "  [⚠ 路径穿越风险]" else ""
                out.append(entry.name.take(1024))
                    .append(if (entry.isDirectory) "/" else "  (${entry.uncompressedSize.takeIf { it >= 0 } ?: "?"} B)")
                    .append(unsafeTag)
                    .append('\n')
            }
            return out.toString()
        }
        val slide = Regex("ppt/slides/slide[0-9]+\\.xml")
        val sheet = Regex("xl/worksheets/sheet[0-9]+\\.xml")
        val xmlFiles = SafeZipReader.readSelectedFileEntries(file, ZIP_ENTRIES, XML_BYTES, XML_TOTAL,
            include = { entry -> when (kind) {
                "docx" -> entry.name == "word/document.xml"
                "pptx" -> slide.matches(entry.name)
                "xlsx" -> entry.name == "xl/sharedStrings.xml" || sheet.matches(entry.name)
                else -> false
            } },
            validateEntry = { entry -> check(!isUnsafeArchivePath(entry.name)) { "Office 压缩包包含不安全路径" } })
        fun xml(name: String, handler: DefaultHandler) {
            val bytes = xmlFiles[name] ?: return
            // Reject declarations before parsing; some Android SAX implementations lack
            // the Apache feature, so also reject every entity resolution request.
            val xmlText = when {
                bytes.size >= 2 && bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte() -> strictDecode(bytes, Charsets.UTF_16LE)
                bytes.size >= 2 && bytes[0] == 0xfe.toByte() && bytes[1] == 0xff.toByte() -> strictDecode(bytes, Charsets.UTF_16BE)
                else -> strictDecode(bytes, Charsets.UTF_8)
            }.removePrefix("\uFEFF")
            check(!xmlText.contains('\u0000') && !Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(xmlText)) {
                "不支持含 DTD 或实体声明的 Office 文档"
            }
            val factory = SAXParserFactory.newInstance().apply {
                isNamespaceAware = true
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            }
            factory.newSAXParser().xmlReader.apply {
                contentHandler = handler
                entityResolver = org.xml.sax.EntityResolver { _, _ -> throw org.xml.sax.SAXException("Office XML 不允许外部实体") }
            }.parse(org.xml.sax.InputSource(java.io.StringReader(xmlText)))
        }
        val out = StringBuilder("${kind.uppercase(Locale.ROOT)} 内容摘要（不保留排版、图片和嵌入对象）\n\n")
        var truncated = false
        fun add(text: String) {
            val remaining = OUTPUT_CHARS - out.length
            if (remaining <= 0) { truncated = true; return }
            if (text.length > remaining) truncated = true
            out.append(text, 0, minOf(text.length, remaining))
        }
        fun textHandler() = object : DefaultHandler() {
            var text = false
            override fun startElement(uri: String?, local: String, q: String?, a: Attributes?) { text = local == "t" }
            override fun characters(ch: CharArray, start: Int, length: Int) { if (text) add(String(ch, start, length)) }
            override fun endElement(uri: String?, local: String, q: String?) {
                if (local == "t") text = false
                if (local == "p") add("\n")
                if (local == "tr") add("\n")
                if (local == "tc") add("\t")
            }
        }
        when (kind) {
            "docx" -> xml("word/document.xml", textHandler())
            "pptx" -> xmlFiles.keys.filter { slide.matches(it) }
                .sortedBy { it.substringAfterLast("slide").substringBefore('.').toIntOrNull() ?: 0 }
                .forEach { name -> add("\n—— ${name.substringAfterLast('/')} ——\n"); xml(name, textHandler()) }
            "xlsx" -> {
                val shared = mutableListOf<String>()
                var item = StringBuilder()
                xml("xl/sharedStrings.xml", object : DefaultHandler() {
                    var text = false
                    override fun startElement(uri: String?, local: String, q: String?, a: Attributes?) {
                        if (local == "si") item = StringBuilder()
                        text = local == "t"
                    }
                    override fun characters(ch: CharArray, start: Int, length: Int) { if (text) item.append(ch, start, length) }
                    override fun endElement(uri: String?, local: String, q: String?) { text = false; if (local == "si") shared += item.toString() }
                })
                xmlFiles.keys.filter { sheet.matches(it) }
                    .sortedBy { it.substringAfterLast("sheet").substringBefore('.').toIntOrNull() ?: 0 }.forEach { name ->
                    add("\n—— ${name.substringAfterLast('/')}（单元格原始值）——\n")
                    xml(name, object : DefaultHandler() {
                        var type = ""; var capture = false; var value = StringBuilder()
                        override fun startElement(uri: String?, local: String, q: String?, a: Attributes) {
                            if (local == "c") { type = a.getValue("t") ?: ""; value = StringBuilder(); add("${a.getValue("r") ?: "?"}: ") }
                            capture = local == "v" || local == "t"
                        }
                        override fun characters(ch: CharArray, start: Int, length: Int) { if (capture) value.append(ch, start, length) }
                        override fun endElement(uri: String?, local: String, q: String?) {
                            capture = false
                            if (local == "c") { val raw = value.toString(); add((if (type == "s") shared.getOrNull(raw.toIntOrNull() ?: -1) ?: raw else raw) + "\t") }
                            if (local == "row") add("\n")
                        }
                    })
                }
            }
        }
        check(out.toString().substringAfter("\n\n").isNotBlank()) { "未找到可预览的 Office 文字，请使用其他应用打开" }
        if (truncated) out.append("\n…内容已截断")
        return out.toString()
    }

    private fun strictDecode(bytes: ByteArray, charset: java.nio.charset.Charset): String = charset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString()

    fun InputStream.readBounded(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer, 0, minOf(buffer.size, limit + 1 - out.size()))
            if (count < 0) break
            out.write(buffer, 0, count)
            check(out.size() <= limit) { "文件内容超过预览读取上限，请使用其他应用打开" }
        }
        return out.toByteArray()
    }
}
