package com.example.globalminusone.system.file

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import com.example.globalminusone.system.privilege.RootWritePolicy

internal const val MAX_EDIT_TEXT_BYTES = 1024 * 1024
data class EditableFileText(val text: String, val fingerprint: String)

internal fun textFingerprint(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }

internal fun requireUnchangedText(bytes: ByteArray, expectedFingerprint: String) {
    check(textFingerprint(bytes) == expectedFingerprint) { "文件已在其它位置被修改；未覆盖，请取消后重新打开" }
}

internal fun decodeEditableText(bytes: ByteArray): EditableFileText {
    require(bytes.size <= MAX_EDIT_TEXT_BYTES) { "文本超过 1 MiB 编辑上限；未截断或修改原文件" }
    val text = try { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString() }
    catch (_: Exception) { error("编辑器仅支持 UTF-8；此文件编码不同，原文件未修改") }
    require('\u0000' !in text) { "检测到二进制内容，不能作为文本编辑" }
    return EditableFileText(text.removePrefix("\uFEFF"), textFingerprint(bytes))
}

internal fun editableTextBytes(text: String): ByteArray {
    require('\u0000' !in text) { "文本不能包含 NUL 字符" }
    return text.toByteArray(Charsets.UTF_8).also {
        require(it.size <= MAX_EDIT_TEXT_BYTES) { "UTF-8 编码后超过 1 MiB，未写入" }
    }
}

internal fun supportsTextEditing(entry: BrowserEntry): Boolean {
    if (entry.isDirectory) return false
    if (entry.location.rootAccess) return entry.location.path?.let { RootWritePolicy.refusal(it) == null } == true
    return entry.mimeType.startsWith("text/") || entry.mimeType in setOf("application/json", "application/xml", "application/javascript") ||
        entry.name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT) in
        setOf("txt", "md", "json", "csv", "xml", "html", "htm", "log", "ini", "conf", "yaml", "yml", "css", "js", "kt", "java", "sh", "sql")
}
