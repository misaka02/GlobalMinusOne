package com.example.globalminusone.system.privilege

import com.example.globalminusone.system.file.MAX_EDIT_TEXT_BYTES
import com.example.globalminusone.system.file.decodeEditableText
import com.example.globalminusone.system.file.textFingerprint

internal data class RootFileSnapshot(val path: String, val metadata: String, val hash: String, val epoch: Long) {
    val fields get() = metadata.split('|')
    val preservedMetadata get() = fields.let { it.take(6) + it.last() }.joinToString("|")
    val size get() = fields[6].toLong()
    val description get() = "uid:${fields[2]} gid:${fields[3]} mode:${fields[4]} · ${fields.last()}"
}
internal data class RootChangeApproval(val snapshot: RootFileSnapshot, val newHash: String)

/** Authorization of root is not authorization to write. Scopes and individual content approvals are session-only. */
internal object RootWritePolicy {
    private val files = mutableMapOf<String, RootFileSnapshot>()
    private val scopes = mutableSetOf<String>()
    private val changes = mutableMapOf<String, RootChangeApproval>()
    private val issued = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<RootChangeApproval, Boolean>())
    private val hardTrees = listOf("/proc", "/sys", "/dev", "/apex", "/metadata", "/boot", "/recovery",
        "/data/misc", "/data/misc_ce", "/data/misc_de", "/data/system_ce", "/data/system_de", "/data/vendor",
        "/data/adb", "/persist", "/mnt", "/storage", "/sdcard")
    fun refusal(path: String): String? {
        val p = runCatching { normalizeRootPath(path) }.getOrElse { return it.message ?: "路径无效" }
        if (p == "/" || hardTrees.any { p == it || p.startsWith("$it/") }) return "该设备/密钥/启动或受保护树不允许Root写入"
        val segments = p.lowercase(java.util.Locale.ROOT).split('/')
        if (segments.any { it.startsWith(".gmo-") || it in setOf("keystore", "keychain", "gatekeeper", "credentials", "accounts", "databases", "shared_prefs", "cacerts", "cacerts-added", "security", "selinux", "permissions", "keys", "tokens") }) return "凭据、备份及活跃数据库/偏好目录受保护"
        val name = segments.last()
        if (listOf("locksettings", "password", "credential", "keystore", "gatekeeper", "accounts", "fstab", "vbmeta", "bootloader", "partition", "sepolicy", "property_contexts", "service_contexts", "ssaid", "android_id", "packages").any { name.contains(it) }) return "启动、分区、密钥凭据配置受保护"
        if (name in setOf("su", "magisk", "init", "adbd", "packages.xml", "packages.list", "platform.xml", "sepolicy")) return "可执行/系统安全策略文件受保护"
        if (name.substringAfterLast('.', "") in setOf("db", "sqlite", "sqlite3", "wal", "shm", "key", "pem", "p12", "pfx", "der", "img", "bin", "so", "apk", "dex", "jar", "oat", "art", "vdex", "odex", "sh", "rc")) return "二进制、可执行、数据库或密钥文件不能在此编辑"
        if (name.endsWith("-wal") || name.endsWith("-shm") || name.endsWith("-journal")) return "活跃数据库附属文件受保护"
        return null
    }
    fun requireFile(path: String): String = normalizeRootPath(path).also { check(refusal(it) == null) { refusal(it)!! } }
    fun requireSafeDirectory(path: String): String {
        val p = requireFile(path)
        check(p.startsWith("/data/local/") && p != "/data/local/tmp" && !p.startsWith("/data/local/tmp/")) {
            "目录操作仅限显式启用的/data/local下非挂载普通目录；系统/app配置请逐文件解锁"
        }
        return p
    }
    @Synchronized fun clear() { files.clear(); scopes.clear(); changes.clear(); issued.clear() }
    @Synchronized fun unlock(snapshot: RootFileSnapshot) {
        requireFile(snapshot.path); check((UserRootSession.currentEpoch() == snapshot.epoch && UserRootSession.status.value == RootStatus.Granted)) { "Root会话已变化" }
        val fields = snapshot.fields
        check(fields.size == 9 && snapshot.hash.matches(Regex("[a-f0-9]{64}")) && fields[5] == "1" &&
            fields[4].toIntOrNull(8)?.let { (it and 73) == 0 } == true && fields[6].toLongOrNull()?.let { it in 0..MAX_EDIT_TEXT_BYTES.toLong() } == true &&
            fields[8].matches(Regex("[A-Za-z0-9_:.,-]+")) && ':' in fields[8]) { "Root快照元数据不完整或文件带执行位" }
        files[snapshot.path] = snapshot
    }
    @Synchronized fun allowDirectory(path: String) { check(UserRootSession.status.value == RootStatus.Granted); scopes += requireSafeDirectory(path) }
    @Synchronized fun canWrite(path: String): Boolean {
        val p = runCatching { normalizeRootPath(path) }.getOrNull() ?: return false
        if (refusal(p) != null || UserRootSession.status.value != RootStatus.Granted) return false
        return files[p]?.let { (UserRootSession.currentEpoch() == it.epoch && UserRootSession.status.value == RootStatus.Granted) } == true || scopes.any { p == it || p.startsWith("$it/") }
    }
    @Synchronized fun canManage(path: String): Boolean {
        val p = runCatching { requireSafeDirectory(path) }.getOrNull() ?: return false
        return UserRootSession.status.value == RootStatus.Granted && scopes.any { p == it || p.startsWith("$it/") }
    }
    @Synchronized fun requireDirectory(path: String): String = requireSafeDirectory(path).also { p ->
        check(UserRootSession.status.value == RootStatus.Granted && scopes.any { p == it || p.startsWith("$it/") }) { "此目录尚未明确授权文件操作" }
    }
    @Synchronized fun approve(path: String, original: String, bytes: ByteArray) {
        decodeEditableText(bytes)
        val target = requireNotNull(files[requireFile(path)]) { "请先逐文件解锁并核对元数据" }
        check(target.hash == original && (UserRootSession.currentEpoch() == target.epoch && UserRootSession.status.value == RootStatus.Granted)) { "原文件/会话变化，请重新打开并解锁" }
        changes[path] = RootChangeApproval(target, textFingerprint(bytes))
    }
    @Synchronized fun consume(path: String, bytes: ByteArray): RootChangeApproval {
        val change = requireNotNull(changes.remove(requireFile(path))) { "缺少本次内容的差异确认，未写入" }
        check(change.newHash == textFingerprint(bytes) && (UserRootSession.currentEpoch() == change.snapshot.epoch && UserRootSession.status.value == RootStatus.Granted)) { "确认内容或Root会话已变化" }
        files.remove(path)
        issued += change
        return change
    }
    @Synchronized fun consumeIssued(change: RootChangeApproval, bytes: ByteArray) {
        check(issued.remove(change) && change.newHash == textFingerprint(bytes) && UserRootSession.currentEpoch() == change.snapshot.epoch &&
            UserRootSession.status.value == RootStatus.Granted) { "不是本次会话签发的单次保存许可，未执行" }
    }
}

/** Bounded line-oriented before/after review; this is a review aid, never a patch applied to the file. */
internal fun rootTextDifference(before: String, after: String, limit: Int = 120): String {
    val a = before.lineSequence().iterator(); val b = after.lineSequence().iterator()
    val out = StringBuilder(); var line = 1; var shown = 0; var changed = 0
    while (a.hasNext() || b.hasNext()) {
        val old = if (a.hasNext()) a.next() else null; val new = if (b.hasNext()) b.next() else null
        if (old != new) {
            changed++
            if (shown < limit) { out.append("L$line\n- ${old?.take(300) ?: "（无）"}\n+ ${new?.take(300) ?: "（无）"}\n"); shown++ }
        }
        line++
    }
    return "共$changed 行位置变化；仅展示前$shown 处，每行最多300字。\n$out"
}
