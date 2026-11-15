package com.example.globalminusone

import com.example.globalminusone.system.*
import org.junit.Assert.*
import org.junit.Test

class FileTextPolicyTest {
    @Test fun utf8RoundTripPreservesUnicodeAndEmptyTextAndRecognizesBom() {
        val content = "中文 😀\nsecond line\r\n"
        assertEquals(content, decodeEditableText(editableTextBytes(content)).text)
        assertEquals("", decodeEditableText(byteArrayOf()).text)
        assertEquals(content, decodeEditableText(byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()) + editableTextBytes(content)).text)
    }
    @Test fun rejectsMalformedUtf8BinaryAndOversizeWithoutTruncation() {
        for (bytes in listOf(byteArrayOf(0xc3.toByte(), 0x28), byteArrayOf(65, 0, 66), ByteArray(MAX_EDIT_TEXT_BYTES + 1) { 65 })) {
            assertTrue(runCatching { decodeEditableText(bytes) }.isFailure)
        }
        assertTrue(runCatching { editableTextBytes("中".repeat(MAX_EDIT_TEXT_BYTES / 3 + 1)) }.isFailure)
        assertEquals(MAX_EDIT_TEXT_BYTES, editableTextBytes("a".repeat(MAX_EDIT_TEXT_BYTES)).size)
    }
    @Test fun detectsExternalChangesBeforeReplacingContent() {
        val original = "original".toByteArray()
        val fingerprint = decodeEditableText(original).fingerprint
        requireUnchangedText(original.copyOf(), fingerprint)
        assertTrue(runCatching { requireUnchangedText("changed".toByteArray(), fingerprint) }.isFailure)
    }
    @Test fun officeAndBinaryDocumentsAreNotOfferedAsTextEditing() {
        fun entry(name: String, mime: String) = BrowserEntry(BrowserLocation(name, path = "/synthetic/$name"), name, false, mime, null, null)
        assertTrue(supportsTextEditing(entry("note.txt", "application/octet-stream")))
        assertTrue(supportsTextEditing(entry("data", "application/json")))
        assertFalse(supportsTextEditing(entry("report.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")))
        assertFalse(supportsTextEditing(entry("photo.png", "image/png")))
    }
}
