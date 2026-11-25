package com.example.globalminusone.screenassistant

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.GZIPInputStream

@Serializable
data class DownloadedLexiconAttachment(
    val id: String,
    val name: String,
    val fileName: String,
    val sourceUrl: String = "",
    val sizeBytes: Long = 0L,
    val entryCount: Int = 0,
    val sha256: String = "",
    val addedAtMs: Long = System.currentTimeMillis()
)

/**
 * Manages optional offline lexicons in filesDir/screen_assistant/attachments.
 */
internal object ScreenAssistantAttachmentManager {
    private const val TAG = "ScreenAssistantAttach"
    private const val DIR_NAME = "screen_assistant/attachments"
    private const val INDEX_FILE = "lexicons_index.json"
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private fun attachmentDir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    private fun indexFile(context: Context): File =
        File(attachmentDir(context), INDEX_FILE)

    fun listLexicons(context: Context): List<DownloadedLexiconAttachment> {
        val f = indexFile(context)
        if (!f.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<DownloadedLexiconAttachment>>(f.readText(Charsets.UTF_8))
                .filter { File(attachmentDir(context), it.fileName).exists() }
        }.getOrDefault(emptyList())
    }

    private fun saveIndex(context: Context, list: List<DownloadedLexiconAttachment>) {
        val target = indexFile(context)
        val tmp = File(target.parentFile, "$INDEX_FILE.tmp")
        tmp.writeText(json.encodeToString(list), Charsets.UTF_8)
        if (!tmp.renameTo(target)) tmp.copyTo(target, overwrite = true).also { tmp.delete() }
    }

    /** Downloads a user-specified `.tsv`/`.txt`/`.json`/`.gz` lexicon URL into app-private attachments directory. */
    suspend fun downloadLexiconFromUrl(
        context: Context,
        rawUrl: String,
        displayName: String,
        expectedSha256: String = "",
        onProgressPercent: (Int) -> Unit = {}
    ): Result<DownloadedLexiconAttachment> = withContext(Dispatchers.IO) {
        val cleanUrl = rawUrl.trim()
        if (!cleanUrl.startsWith("https://", ignoreCase = true) && !cleanUrl.startsWith("http://", ignoreCase = true)) {
            return@withContext Result.failure(IllegalArgumentException("请输入以 http:// 或 https:// 开头的词库地址"))
        }
        val dir = attachmentDir(context)
        val tmpFile = File(dir, "dl_${UUID.randomUUID()}.tmp")
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(cleanUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 25_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "GlobalMinusOne/2.4 ScreenAssistant")
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                return@withContext Result.failure(IllegalStateException("下载失败 HTTP $code"))
            }
            val total = conn.contentLengthLong.coerceAtLeast(0L)
            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            conn.inputStream.use { input ->
                FileOutputStream(tmpFile).use { out ->
                    val buf = ByteArray(16_384)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        if (downloaded + n > 64L * 1024L * 1024L) {
                            throw IllegalStateException("附件超过 64MB 安全上限")
                        }
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        downloaded += n
                        if (total > 0L) onProgressPercent(((downloaded * 100L) / total).toInt().coerceIn(0, 99))
                    }
                }
            }
            val shaHex = digest.digest().joinToString("") { "%02x".format(it) }
            if (expectedSha256.isNotBlank() && !shaHex.equals(expectedSha256.trim(), ignoreCase = true)) {
                return@withContext Result.failure(SecurityException("SHA-256 校验失败: $shaHex"))
            }
            val textContent = readMaybeGzipText(tmpFile, cleanUrl)
            val entries = ScreenAssistantPolicy.parseLexiconContent(textContent)
            if (entries.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("未解析到有效词条（支持 JSON / TSV 制表符 / src|||tgt 格式）"))
            }
            val id = UUID.randomUUID().toString()
            val finalFileName = "lex_$id.tsv"
            val dest = File(dir, finalFileName)
            saveCanonicalLexiconTsv(dest, entries)
            val attachment = DownloadedLexiconAttachment(
                id = id,
                name = displayName.trim().ifBlank { cleanUrl.substringAfterLast('/').ifBlank { "在线离线词库" } },
                fileName = finalFileName,
                sourceUrl = cleanUrl,
                sizeBytes = dest.length(),
                entryCount = entries.size,
                sha256 = shaHex
            )
            saveIndex(context, listLexicons(context) + attachment)
            onProgressPercent(100)
            Result.success(attachment)
        } catch (t: Throwable) {
            Log.w(TAG, "downloadLexiconFromUrl failed", t)
            Result.failure(t)
        } finally {
            conn?.disconnect()
            if (tmpFile.exists()) tmpFile.delete()
        }
    }

    /** Imports a user-selected local `.tsv`/`.txt`/`.json`/`.gz` lexicon via SAF Uri. */
    suspend fun importLexiconFromUri(context: Context, uri: Uri): Result<DownloadedLexiconAttachment> = withContext(Dispatchers.IO) {
        val dir = attachmentDir(context)
        val tmpFile = File(dir, "saf_${UUID.randomUUID()}.tmp")
        try {
            val displayName = resolveUriDisplayName(context, uri) ?: "本地导入词库"
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tmpFile).use { out ->
                    val buf = ByteArray(16_384)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        if (copied + n > 64L * 1024L * 1024L) throw IllegalStateException("文件超过 64MB 上限")
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        copied += n
                    }
                }
            } ?: return@withContext Result.failure(IllegalStateException("无法读取所选文件"))

            val shaHex = digest.digest().joinToString("") { "%02x".format(it) }
            val entries = ScreenAssistantPolicy.parseLexiconContent(readMaybeGzipText(tmpFile, displayName))
            if (entries.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("文件中没有有效词条（支持 JSON / TSV / ||| 分隔）"))
            }
            val id = UUID.randomUUID().toString()
            val finalFileName = "lex_$id.tsv"
            val dest = File(dir, finalFileName)
            saveCanonicalLexiconTsv(dest, entries)
            val attachment = DownloadedLexiconAttachment(
                id = id,
                name = displayName,
                fileName = finalFileName,
                sourceUrl = "local://imported",
                sizeBytes = dest.length(),
                entryCount = entries.size,
                sha256 = shaHex
            )
            saveIndex(context, listLexicons(context) + attachment)
            Result.success(attachment)
        } catch (t: Throwable) {
            Log.w(TAG, "importLexiconFromUri failed", t)
            Result.failure(t)
        } finally {
            if (tmpFile.exists()) tmpFile.delete()
        }
    }

    /** Generates or appends entries to a local phrase cache pack so translations remain available offline. */
    suspend fun cacheTranslationPairsOffline(
        context: Context,
        pairs: List<Pair<String, String>>,
        packName: String = "本地翻译记忆词库"
    ): DownloadedLexiconAttachment? = withContext(Dispatchers.IO) {
        val valid = pairs.filter { it.first.isNotBlank() && it.second.isNotBlank() && it.first.trim() != it.second.trim() }
        if (valid.isEmpty()) return@withContext null
        val dir = attachmentDir(context)
        val existingList = listLexicons(context).toMutableList()
        val cacheItem = existingList.firstOrNull { it.sourceUrl == "local://memory-pack" }
        val existingMap = linkedMapOf<String, String>()
        if (cacheItem != null) {
            val file = File(dir, cacheItem.fileName)
            if (file.exists()) {
                ScreenAssistantPolicy.parseLexiconContent(file.readText(Charsets.UTF_8)).forEach {
                    existingMap[it.source] = it.target
                }
            }
        }
        valid.forEach { (s, t) -> existingMap[s.trim()] = t.trim() }
        val entries = existingMap.entries.map { LexiconEntry(it.key, it.value) }
        val id = cacheItem?.id ?: UUID.randomUUID().toString()
        val fileName = cacheItem?.fileName ?: "lex_$id.tsv"
        val dest = File(dir, fileName)
        saveCanonicalLexiconTsv(dest, entries)
        val updated = DownloadedLexiconAttachment(
            id = id,
            name = cacheItem?.name ?: packName,
            fileName = fileName,
            sourceUrl = "local://memory-pack",
            sizeBytes = dest.length(),
            entryCount = entries.size,
            sha256 = ""
        )
        existingList.removeAll { it.id == id }
        existingList.add(0, updated)
        saveIndex(context, existingList)
        updated
    }

    fun loadActiveLexiconMap(context: Context, lexiconId: String): Map<String, String> {
        val all = listLexicons(context)
        val chosen = all.firstOrNull { it.id == lexiconId } ?: all.firstOrNull() ?: return emptyMap()
        val file = File(attachmentDir(context), chosen.fileName)
        if (!file.exists()) return emptyMap()
        val entries = ScreenAssistantPolicy.parseLexiconContent(file.readText(Charsets.UTF_8))
        return entries.associate { it.source to it.target }
    }

    fun deleteLexicon(context: Context, lexiconId: String) {
        val remaining = listLexicons(context).filter { item ->
            if (item.id == lexiconId) {
                runCatching { File(attachmentDir(context), item.fileName).delete() }
                false
            } else true
        }
        saveIndex(context, remaining)
    }

    private fun readMaybeGzipText(file: File, nameHint: String): String {
        val isGz = nameHint.endsWith(".gz", ignoreCase = true) || runCatching {
            file.inputStream().use { ins -> ins.read() == 0x1f && ins.read() == 0x8b }
        }.getOrDefault(false)
        return if (isGz) {
            GZIPInputStream(file.inputStream()).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } else {
            file.readText(Charsets.UTF_8)
        }
    }

    private fun saveCanonicalLexiconTsv(file: File, entries: List<LexiconEntry>) {
        file.bufferedWriter(Charsets.UTF_8).use { writer ->
            entries.forEach { e ->
                writer.write(e.source.replace('\t', ' ').replace('\n', ' '))
                writer.write('\t'.code)
                writer.write(e.target.replace('\t', ' ').replace('\n', ' '))
                writer.newLine()
            }
        }
    }

    private fun resolveUriDisplayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
}
