package com.example.globalminusone.data.backup

import com.example.globalminusone.data.CorruptStateBackup
import java.io.File
import java.util.UUID

/** Write and verify the original bytes, independently of the valid fallback configuration's ZIP. */
internal fun archiveCorruptStateRaw(directory: File, backup: CorruptStateBackup): File {
    check(directory.isDirectory || directory.mkdirs()) { "不能创建损坏配置原文恢复目录" }
    val bytes = backup.raw.toByteArray(Charsets.UTF_8)
    val hash = NativeBackupCodec.sha256(bytes)
    val target = File(directory, "corrupt-$hash.txt")
    if (!target.exists()) {
        val temporary = File(directory, ".corrupt-${UUID.randomUUID()}.tmp")
        try {
            temporary.outputStream().use { output -> output.write(bytes); output.flush() }
            check(temporary.length() == bytes.size.toLong() && NativeBackupCodec.sha256(temporary.readBytes()) == hash) { "损坏配置原文写入校验失败" }
            check(temporary.renameTo(target)) { "损坏配置原文备份提交失败" }
        } finally { temporary.delete() }
    }
    check(target.length() == bytes.size.toLong() && NativeBackupCodec.sha256(target.readBytes()) == hash) { "损坏配置原文备份回读失败" }
    return target
}
