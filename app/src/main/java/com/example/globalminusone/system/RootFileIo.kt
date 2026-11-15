package com.example.globalminusone.system

import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import java.io.File

internal data class RootRecoveryRecord(val target: String, val backupPath: String, val originalHash: String, val newHash: String, val status: String, val completed: Boolean = false)

/** Root mutations require an explicit scope or a one-use per-file reviewed content approval. */
internal object RootFileIo {
    const val READ_ONLY_REASON = "此Root目标尚未解锁；配置文本需逐文件核对/差异确认，批量操作只在专用工作目录"
    private val recovery = kotlinx.coroutines.flow.MutableStateFlow<List<RootRecoveryRecord>>(emptyList())
    val recoveryRecords: kotlinx.coroutines.flow.StateFlow<List<RootRecoveryRecord>> = recovery
    fun checkSession() { check(UserRootSession.authorizedEpoch() != null) { "Root会话无效；请显式授权，操作不会自行重试su" } }
    fun path(location: BrowserLocation): String = normalizeRootPath(requireNotNull(location.path))
    fun location(path: String): BrowserLocation {
        val normalized = normalizeRootPath(path)
        return BrowserLocation(File(normalized).name.ifEmpty { "/" }, path = normalized, rootAccess = true)
    }
    suspend fun list(location: BrowserLocation): List<BrowserEntry> {
        checkSession()
        return decodeRootListing(UserRootSession.runFileRequest(RootReadRequest.ListDirectory(path(location))))
    }
    suspend fun read(location: BrowserLocation, maxBytes: Int): ByteArray = UserRootSession.runFileRequest(RootReadRequest.ReadFile(path(location), maxBytes))
    suspend fun inspectForEditing(location: BrowserLocation): RootFileSnapshot {
        val target = RootWritePolicy.requireFile(path(location)); val epoch = UserRootSession.authorizedEpoch() ?: error("请先授权Root")
        val data = UserRootSession.runMutationRequest(RootMutationRequest.InspectFile(target)).toString(Charsets.UTF_8).split('\u0000')
        check(data.size == 4 && data.last().isEmpty() && data[0] == target) { "Root文件快照协议错误" }
        val fields = data[1].split('|')
        check(fields.size == 9 && fields.take(8).all { it.toLongOrNull() != null } && fields[5] == "1" && fields[6].toLong() in 0..MAX_EDIT_TEXT_BYTES.toLong()
            && fields[8].matches(Regex("[A-Za-z0-9_:.,-]+")) && fields[8].contains(":") && data[2].matches(Regex("[a-f0-9]{64}"))) { "无法完整验证Root文件身份/SELinux/指纹" }
        check(UserRootSession.isAuthorizationCurrent(epoch)) { "会话变化，快照已废弃" }
        return RootFileSnapshot(target, data[1], data[2], epoch)
    }
    fun unlock(snapshot: RootFileSnapshot) = RootWritePolicy.unlock(snapshot)
    fun approveText(location: BrowserLocation, original: String, text: String) = RootWritePolicy.approve(path(location), original, editableTextBytes(text))
    suspend fun createWorkspace(target: String): BrowserLocation {
        UserRootSession.runMutationRequest(RootMutationRequest.Workspace(target))
        allowDirectory(location(target))
        return location(target)
    }
    suspend fun allowDirectory(parent: BrowserLocation) {
        val target = RootWritePolicy.requireSafeDirectory(path(parent))
        val result = UserRootSession.runMutationRequest(RootMutationRequest.AuthorizeDirectory(target))
        check(result.toString(Charsets.UTF_8) == "ROOTDIR") { "Root目录能力未确认" }
        RootWritePolicy.allowDirectory(target)
    }
    suspend fun createDirectory(parent: BrowserLocation, name: String): BrowserLocation {
        val target = RootWritePolicy.requireDirectory(path(parent)); val valid = validatedFileName(name)
        UserRootSession.runMutationRequest(RootMutationRequest.Create(target, valid, null))
        return location("$target/$valid")
    }
    suspend fun createText(parent: BrowserLocation, name: String, bytes: ByteArray): BrowserLocation {
        val target = RootWritePolicy.requireDirectory(path(parent)); val valid = validatedFileName(name)
        UserRootSession.runMutationRequest(RootMutationRequest.Create(target, valid, bytes))
        return location("$target/$valid")
    }
    suspend fun write(location: BrowserLocation, bytes: ByteArray) {
        val change = RootWritePolicy.consume(path(location), bytes)
        val directory = "/data/local/.gmo-recovery-" + java.util.UUID.randomUUID().toString().replace("-", "")
        val record = RootRecoveryRecord(path(location), "$directory/original", change.snapshot.hash, change.newHash, "事务待完成；超时也保留此恢复位置")
        recovery.value = (recovery.value + record).takeLast(8)
        try {
            val response = UserRootSession.runMutationRequest(RootMutationRequest.SaveText(change, bytes, directory)).toString(Charsets.UTF_8).split('\u0000')
            check(response.size == 3 && response[0] == record.backupPath && response[1] == change.newHash) { "保存回读协议无效，未宣称成功；请检查备份" }
            recovery.value = recovery.value.map { if (it == record) it.copy(status = "保存并读回一致；owner/mode/SELinux保持", completed = true) else it }
        } catch (e: Exception) {
            recovery.value = recovery.value.map { if (it == record) it.copy(status = "事务失败/中断；备份可能已生成，请重新授权后核对并回滚") else it }
            throw e
        }
    }
    suspend fun rollback(record: RootRecoveryRecord, currentFingerprint: String) {
        val target = location(record.target)
        val snapshot = inspectForEditing(target)
        check(snapshot.hash == currentFingerprint) { "回滚确认后目标再次变化，未覆盖" }
        val backup = read(location(record.backupPath), MAX_EDIT_TEXT_BYTES)
        check(textFingerprint(backup) == record.originalHash) { "备份未生成或指纹不一致，拒绝回滚" }
        decodeEditableText(backup)
        unlock(snapshot); RootWritePolicy.approve(record.target, snapshot.hash, backup)
        write(target, backup)
    }
    suspend fun rename(entry: BrowserEntry, parent: BrowserLocation, name: String): BrowserLocation {
        val source = RootWritePolicy.requireDirectory(path(entry.location)); val targetParent = RootWritePolicy.requireDirectory(path(parent))
        val target = "$targetParent/${validatedFileName(name)}"
        UserRootSession.runMutationRequest(RootMutationRequest.Rename(source, target)); return location(target)
    }
    suspend fun delete(entry: BrowserEntry) {
        RootWritePolicy.requireDirectory(path(entry.location))
        UserRootSession.runMutationRequest(RootMutationRequest.Delete(path(entry.location)))
    }
    suspend fun copy(entry: BrowserEntry, destination: BrowserLocation, move: Boolean) {
        RootWritePolicy.requireDirectory(path(entry.location)); val target = RootWritePolicy.requireDirectory(path(destination)) + "/" + validatedFileName(entry.name)
        UserRootSession.runMutationRequest(RootMutationRequest.Copy(path(entry.location), target, move))
    }
}
internal fun decodeRootListing(bytes: ByteArray, mimeForName: (String) -> String = {
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(it.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"
}): List<BrowserEntry> {
    val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
    val text = decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
    if (text.isEmpty()) return emptyList()
    val fields = text.split('\u0000')
    check(fields.last().isEmpty() && (fields.size - 1) % 3 == 0) { "root目录返回格式不完整，未显示部分结果" }
    return (0 until fields.lastIndex step 3).map { i ->
        val kind = fields[i]; val path = fields[i + 2]
        val metadata = fields[i + 1].split(' ')
        check(metadata.size == 2) { "root元数据无效" }
        val name = File(path).name
        val blocked = when {
            path.any { it.code < 32 || it.code == 127 } -> "名称含控制字节，当前root操作不支持"
            kind == "l" -> "符号链接：为避免跟随到意外目标，暂不直接操作"
            kind !in setOf("d", "f") -> "设备/套接字等特殊节点不支持预览或写入"
            else -> null
        }
        BrowserEntry(BrowserLocation(name, path = if (blocked == null) normalizeRootPath(path) else path, rootAccess = true), name, kind == "d",
            if (kind == "d") DocumentsContract.Document.MIME_TYPE_DIR else mimeForName(name),
            if (kind == "d") null else metadata[0].toLongOrNull()?.takeIf { it >= 0 }, metadata[1].toLongOrNull()?.takeIf { it > 0 }?.times(1000), blocked)
    }
}

