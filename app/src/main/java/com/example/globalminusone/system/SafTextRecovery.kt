package com.example.globalminusone.system

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Properties
import java.util.UUID

data class SafTextRecovery(val id: String, val targetKey: String, val targetName: String,
    val createdAt: Long, val originalHash: String)
data class SafTextRestoreReview(val recovery: SafTextRecovery, val currentHash: String, val currentPreview: String)

/** Bounded, persistent originals. Provider writes are never claimed to be atomic. */
internal class SafTextRecoveryStore(private val directory: File,
    private val maxBytes: Long = 8L * 1024 * 1024, private val maxRecords: Int = 8) {
    companion object { private val lock = Any() }
    private fun part(id: String, suffix: String): File {
        require(id.matches(Regex("[a-f0-9-]{36}")))
        return File(directory, "$id.$suffix")
    }
    private fun records(): List<SafTextRecovery> = directory.listFiles().orEmpty()
        .filter { it.extension == "properties" }.map { file ->
            check(file.length() <= 16_384) { "原文备份记录损坏；未写入目标" }
            val p = Properties().apply { file.inputStream().use { load(it) } }
            SafTextRecovery(file.nameWithoutExtension, p.getProperty("target"), p.getProperty("name"),
                p.getProperty("created").toLong(), p.getProperty("sha256"))
        }
    fun pending(target: String): SafTextRecovery? = synchronized(lock) {
        records().filter { it.targetKey == target }.maxByOrNull { it.createdAt }
    }
    fun original(record: SafTextRecovery): File = synchronized(lock) {
        check(records().any { it == record }) { "原文备份记录已变化" }
        part(record.id, "txt").also {
            check(it.isFile && it.length() <= MAX_EDIT_TEXT_BYTES && textFingerprint(it.readBytes()) == record.originalHash) {
                "原文备份损坏；未覆盖目标"
            }
        }
    }
    private fun prepare(target: String, name: String, bytes: ByteArray): SafTextRecovery {
        check(bytes.size <= MAX_EDIT_TEXT_BYTES)
        check(target.length <= 4096 && name.length <= 512)
        check(directory.isDirectory || directory.mkdirs()) { "无法建立原文备份目录；未写入目标" }
        check(pending(target) == null) { "此文件有未完成原文备份，请先核对恢复；未写入目标" }
        val files = directory.listFiles() ?: error("无法检查备份容量；未写入目标")
        // Count orphan originals too. Never evict an unsuccessful save to make space.
        check(files.count { it.extension == "txt" } < maxRecords &&
            files.sumOf { it.length() } + bytes.size + 16_384 <= maxBytes) {
            "原文备份已满；未写入目标，请先处理未完成备份"
        }
        val record = SafTextRecovery(UUID.randomUUID().toString(), target, name, System.currentTimeMillis(), textFingerprint(bytes))
        val data = part(record.id, "txt")
        val metadata = part(record.id, "properties")
        try {
            FileOutputStream(data).use { it.write(bytes); it.fd.sync() }
            val p = Properties().apply {
                setProperty("target", target); setProperty("name", name)
                setProperty("created", record.createdAt.toString()); setProperty("sha256", record.originalHash)
            }
            FileOutputStream(metadata).use { p.store(it, "GMO original text recovery"); it.fd.sync() }
            original(record) // Verify persisted bytes before opening the provider for truncation.
        } catch (e: Exception) {
            metadata.delete(); data.delete() // The provider has not been touched.
            throw e
        }
        return record
    }
    private fun complete(record: SafTextRecovery) {
        // Metadata first: a crash leaves at worst a quota-counted orphan original, never a false recovery record.
        check(part(record.id, "properties").delete()) { "内容已核对，但备份记录清理失败" }
        part(record.id, "txt").delete()
    }
    fun save(target: String, name: String, original: ByteArray, replacement: ByteArray,
        read: () -> ByteArray, replace: (ByteArray) -> Unit) = synchronized(lock) {
        val record = prepare(target, name, original)
        try {
            requireUnchangedText(read(), record.originalHash)
            replace(replacement)
            check(textFingerprint(read()) == textFingerprint(replacement)) { "提供方写后读回内容不一致" }
            complete(record)
        } catch (e: Exception) {
            // A partial provider write cannot be distinguished from an external edit. Never
            // silently overwrite it with a rollback; retain the original for explicit review.
            throw IOException("保存未确认：${e.message}；原文备份已保留，请核对恢复或分享原文备份", e)
        }
    }
    fun restore(record: SafTextRecovery, expectedCurrentHash: String, read: () -> ByteArray,
        replace: (ByteArray) -> Unit) = synchronized(lock) {
        val bytes = original(record).readBytes()
        requireUnchangedText(read(), expectedCurrentHash)
        try {
            replace(bytes)
            check(textFingerprint(read()) == record.originalHash) { "恢复后读回内容不一致" }
            complete(record)
        } catch (e: Exception) {
            throw IOException("恢复未确认：${e.message}；原文备份仍保留，可分享取回", e)
        }
    }
}
