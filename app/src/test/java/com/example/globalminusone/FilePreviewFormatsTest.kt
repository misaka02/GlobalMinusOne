package com.example.globalminusone

import com.example.globalminusone.system.FilePreviewFormats
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FilePreviewFormatsTest {
    private fun binaryArchive(name: String, bytes: ByteArray, block: (File) -> Unit) {
        val file = File.createTempFile("preview-binary", ".zip")
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
            }
            block(file)
        } finally { file.delete() }
    }

    private fun archive(vararg entries: Pair<String, String>, block: (File) -> Unit) {
        val file = File.createTempFile("preview-test", ".zip")
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                for ((name, text) in entries) {
                    zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry()
                }
            }
            block(file)
        } finally { file.delete() }
    }

    @Test fun utf8TruncationDoesNotFallBackToChineseEncoding() {
        val data = "中文末尾".toByteArray()
        val (text, encoding) = FilePreviewFormats.decodeText(data.copyOf(data.size - 1), true)
        assertEquals("UTF-8", encoding)
        assertEquals("中文末", text)
        val gb = "中文兼容文本".toByteArray(java.nio.charset.Charset.forName("GB18030"))
        assertEquals("中文兼容文本", FilePreviewFormats.decodeText(gb, false).first)
    }

    @Test fun mimeAndExtensionRecognizeDocumentsAndMedia() {
        assertEquals("pdf", FilePreviewFormats.kind("无后缀", "application/pdf"))
        assertEquals("docx", FilePreviewFormats.kind("报告.DOCX", "application/octet-stream"))
        assertEquals("video", FilePreviewFormats.kind("movie.mp4", "application/octet-stream"))
        assertEquals("svg", FilePreviewFormats.kind("drawing.svg", "image/svg+xml"))
    }

    @Test fun summariesReadWordSlidesAndSharedSpreadsheetValues() {
        archive("word/document.xml" to "<document><p><t>文档内容</t></p></document>") {
            assertTrue(FilePreviewFormats.summary(it, "docx").contains("文档内容"))
        }
        archive("ppt/slides/slide1.xml" to "<slide><p><t>幻灯文字</t></p></slide>") {
            assertTrue(FilePreviewFormats.summary(it, "pptx").contains("幻灯文字"))
        }
        archive(
            "xl/sharedStrings.xml" to "<sst><si><t>姓名</t></si></sst>",
            "xl/worksheets/sheet1.xml" to "<worksheet><row><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\"><v>42</v></c></row></worksheet>"
        ) {
            val result = FilePreviewFormats.summary(it, "xlsx")
            assertTrue(result.contains("A1: 姓名")); assertTrue(result.contains("B1: 42"))
        }
    }

    @Test fun zipListingDoesNotExtractPaths() {
        archive("../outside.txt" to "untrusted") {
            val out = FilePreviewFormats.summary(it, "zip")
            assertTrue(out.contains("../outside.txt"))
            assertTrue(out.contains("路径穿越"))
        }
        assertTrue(FilePreviewFormats.isUnsafeArchivePath("../outside.txt"))
        assertTrue(FilePreviewFormats.isUnsafeArchivePath("/etc/passwd"))
        assertFalse(FilePreviewFormats.isUnsafeArchivePath("META-INF/MANIFEST.MF"))
        assertEquals("zip", FilePreviewFormats.kind("app-debug.apk", "application/vnd.android.package-archive"))
    }

    @Test fun rejectsDtdAndInflatedXmlLimit() {
        archive("word/document.xml" to "<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///etc/passwd'>]><document><t>&e;</t></document>") {
            assertThrows(IllegalStateException::class.java) { FilePreviewFormats.summary(it, "docx") }
        }
        archive("word/document.xml" to ("<document>" + "a".repeat(2 * 1024 * 1024) + "</document>")) {
            assertThrows(IllegalStateException::class.java) { FilePreviewFormats.summary(it, "docx") }
        }
    }

    @Test fun officeSelectionSkipsLargeUnrelatedEntriesAndOrdersSheetsNumerically() {
        archive(
            "xl/worksheets/sheet10.xml" to "<worksheet><row><c r=\"A10\"><v>ten</v></c></row></worksheet>",
            "xl/worksheets/sheet2.xml" to "<worksheet><row><c r=\"A2\"><v>two</v></c></row></worksheet>",
            "xl/media/unrelated.bin" to "x".repeat(3 * 1024 * 1024)
        ) {
            val result = FilePreviewFormats.summary(it, "xlsx")
            assertTrue(result.indexOf("sheet2.xml") < result.indexOf("sheet10.xml"))
        }
    }

    @Test fun officeOutputTruncatesAndRejectsUnsafePaths() {
        archive("word/document.xml" to "<document><p><t>${"x".repeat(300_000)}</t></p></document>") {
            assertTrue(FilePreviewFormats.summary(it, "docx").endsWith("…内容已截断"))
        }
        archive("../escape" to "ignored", "word/document.xml" to "<document><t>safe</t></document>") {
            assertThrows(IllegalStateException::class.java) { FilePreviewFormats.summary(it, "docx") }
        }
    }

    @Test fun officeRejectsMalformedUtf8AndBrokenXml() {
        binaryArchive("word/document.xml", byteArrayOf(0x3c, 0x74, 0x3e, 0xc3.toByte(), 0x28, 0x3c, 0x2f, 0x74, 0x3e)) {
            assertThrows(java.nio.charset.MalformedInputException::class.java) { FilePreviewFormats.summary(it, "docx") }
        }
        archive("word/document.xml" to "<document><t>broken</document>") {
            assertThrows(org.xml.sax.SAXException::class.java) { FilePreviewFormats.summary(it, "docx") }
        }
    }
}
