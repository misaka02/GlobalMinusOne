package com.example.globalminusone.system.file

import java.util.Base64
import com.example.globalminusone.system.privilege.RootChangeApproval
import com.example.globalminusone.system.privilege.RootWritePolicy
import com.example.globalminusone.system.privilege.normalizeRootPath
import com.example.globalminusone.system.privilege.rootShellPath
import com.example.globalminusone.system.privilege.rootShellQuote

internal sealed interface RootMutationRequest {
    data class InspectFile(val path: String) : RootMutationRequest
    data class SaveText(val approval: RootChangeApproval, val bytes: ByteArray, val recoveryPath: String) : RootMutationRequest
    data class Workspace(val path: String) : RootMutationRequest
    data class AuthorizeDirectory(val path: String) : RootMutationRequest
    data class Create(val parent: String, val name: String, val text: ByteArray?) : RootMutationRequest
    data class Rename(val path: String, val destination: String) : RootMutationRequest
    data class Delete(val path: String) : RootMutationRequest
    data class Copy(val path: String, val destination: String, val move: Boolean) : RootMutationRequest
}

/** Pin an existing regular inode before opening it for writing; never write through a path symlink. */
private fun pinnedRootFile(path: String): String {
    RootWritePolicy.requireFile(path)
    return "p=${rootShellPath(path)}; [ \"\$(readlink -f -- \"\$p\")\" = \"\$p\" ] && [ ! -L \"\$p\" ] && [ -f \"\$p\" ] || { echo '目标/祖先为链接、非普通文件或路径变化' >&2; exit 72; }; " +
        "exec 3<\"\$p\" || exit 73; f=/proc/\$\$/fd/3; [ \"\$(readlink -- \"\$f\")\" = \"\$p\" ] || exit 72; " +
        "meta() { v=\$(stat -L -c '%d|%i|%u|%g|%a|%h|%s|%Y' -- \"\$f\") || return 1; z=\$(ls -LZd -- \"\$f\") || return 1; z=\${z%% *}; " +
        "[ \"\$z\" != '?' ] && [ -n \"\$z\" ] || return 1; printf '%s|%s' \"\$v\" \"\$z\"; }; " +
        "n=\$(stat -L -c '%h' -- \"\$f\"); mode=\$(stat -L -c '%a' -- \"\$f\"); " +
        "[ \"\$n\" = 1 ] && [ \$((0\$mode & 0111)) = 0 ] || { echo '拒绝硬链接或带执行位文件' >&2; exit 72; }; " +
        "[ \"\$(stat -L -c '%s' -- \"\$f\")\" -le 1048576 ] || { echo '文件超过1MiB文本上限，未修改' >&2; exit 72; }; " +
        "exec 4<>\"\$f\" || { echo '实际O_RDWR打开失败：文件系统只读、SELinux或文件权限拒绝；未修改' >&2; exit 73; }; "
}

private fun noRootMounts(path: String): String {
    val pieces = normalizeRootPath(path).split('/').filter { it.isNotEmpty() }
    return "[ -r /proc/self/mountinfo ] || exit 72; " + (2..pieces.size).joinToString("") { count ->
        val prefix = "/" + pieces.take(count).joinToString("/")
        val escaped = prefix.replace("\\", "\\134").replace(" ", "\\040")
        "grep -Fq -- ${rootShellQuote(" $escaped ")} /proc/self/mountinfo; c=\$?; [ \"\$c\" = 1 ] || { echo '挂载点/挂载子树或无法确认挂载信息，拒绝目录操作' >&2; exit 72; }; "
    }
}

/** A root-owned leaf is insufficient when shell/app can replace one of its ancestors. */
private fun trustedRootDirectoryChain(path: String): String {
    val pieces = normalizeRootPath(path).split('/').filter { it.isNotEmpty() }
    val ancestors = listOf("/") + pieces.indices.map { "/" + pieces.take(it + 1).joinToString("/") }
    return ancestors.joinToString("") { ancestor ->
        "a=${rootShellPath(ancestor)}; [ ! -L \"\$a\" ] && [ -d \"\$a\" ] || exit 72; " +
            "u=\$(stat -c '%u' -- \"\$a\") || exit 72; g=\$(stat -c '%g' -- \"\$a\") || exit 72; m=\$(stat -c '%a' -- \"\$a\") || exit 72; " +
            "{ [ \"\$u\" = 0 ] || [ \"\$u\" = 1000 ]; } && [ \$((0\$m & 0002)) = 0 ] && " +
            "{ [ \$((0\$m & 0020)) = 0 ] || [ \"\$g\" = 0 ] || [ \"\$g\" = 1000 ]; } || { echo '祖先目录可由shell/app改写或身份不可信，拒绝Root目录操作' >&2; exit 72; }; "
    }
}

private fun rootSafeParent(path: String, variable: String): String {
    RootWritePolicy.requireSafeDirectory(path)
    return noRootMounts(path) + trustedRootDirectoryChain(path) + "$variable=${rootShellPath(path)}; [ \"\$(readlink -f -- \"\$$variable\")\" = \"\$$variable\" ] && [ ! -L \"\$$variable\" ] && [ -d \"\$$variable\" ] && [ \"\$(stat -c '%u' -- \"\$$variable\")\" = 0 ] && [ \$((0\$(stat -c '%a' -- \"\$$variable\") & 0022)) = 0 ] || { echo 'Root工作目录身份/链接变化或可被其它UID写入' >&2; exit 72; }; "
}

internal fun rootMutationCommand(request: RootMutationRequest): String = when (request) {
    is RootMutationRequest.InspectFile -> pinnedRootFile(request.path) +
        "m=\$(meta) || { echo '无法读取owner/mode/SELinux；不开放写入' >&2; exit 73; }; h=\$(sha256sum -- \"\$f\"); h=\${h%% *}; " +
        "printf '%s\\000%s\\000%s\\000' \"\$p\" \"\$m\" \"\$h\""
    is RootMutationRequest.SaveText -> {
        val before = request.approval.snapshot
        require(request.bytes.size <= MAX_EDIT_TEXT_BYTES)
        check(request.approval.newHash == textFingerprint(request.bytes))
        check(request.recoveryPath.matches(Regex("^/data/local/\\.gmo-recovery-[a-f0-9]{32}$")))
        val encoded = Base64.getEncoder().encodeToString(request.bytes)
        pinnedRootFile(before.path) +
            "m=\$(meta) || exit 73; [ \"\$m\" = ${rootShellQuote(before.metadata)} ] || { echo '原文件身份/元数据变化，未写入' >&2; exit 75; }; " +
            "h=\$(sha256sum -- \"\$f\"); h=\${h%% *}; [ \"\$h\" = ${rootShellQuote(before.hash)} ] || { echo '指纹冲突，未覆盖' >&2; exit 75; }; " +
            "r=${rootShellQuote(request.recoveryPath)}; (umask 077; mkdir -- \"\$r\") || exit 73; " +
            "(umask 077; cat \"\$f\" >\"\$r/original\"; base64 -d >\"\$r/new\" <<'GMO_CONTENT'\n$encoded\nGMO_CONTENT\n printf '%s' \"\$m\" >\"\$r/metadata\"; printf '%s' \"\$p\" >\"\$r/target\") || exit 74; " +
            "h=\$(sha256sum -- \"\$r/original\"); [ \"\${h%% *}\" = ${rootShellQuote(before.hash)} ] || exit 74; " +
            "h=\$(sha256sum -- \"\$r/new\"); [ \"\${h%% *}\" = ${rootShellQuote(request.approval.newHash)} ] || exit 74; " +
            "sync \"\$r/original\" \"\$r/metadata\" \"\$r/target\" \"\$r\" || { echo '无法同步原始备份，未写入目标' >&2; exit 74; }; " +
            "started=0; ok=0; trap 'if [ \"\$started\" = 1 ] && [ \"\$ok\" != 1 ]; then restored=0; " +
            "if cat \"\$r/original\" >\"\$f\" && truncate -s ${before.size} \"\$f\" && sync \"\$f\"; then rh=\$(sha256sum -- \"\$f\"); [ \"\${rh%% *}\" = ${rootShellQuote(before.hash)} ] && restored=1; fi; " +
            "if [ \"\$restored\" = 1 ]; then echo \"保存失败，原内容恢复并回读一致；请核对路径/元数据，备份：\$r/original\" >&2; else echo \"保存失败且无法确认原内容恢复，保留备份：\$r/original\" >&2; fi; fi' EXIT; trap 'exit 74' HUP TERM; " +
            "[ \"\$(readlink -f -- \"\$p\")\" = \"\$p\" ] && [ \"\$(stat -c '%d:%i' -- \"\$p\")\" = ${rootShellQuote(before.fields.take(2).joinToString(":"))} ] || exit 75; " +
            "[ \"\$(meta)\" = ${rootShellQuote(before.metadata)} ] || { echo '备份后元数据变化，未写入' >&2; exit 75; }; " +
            "h=\$(sha256sum -- \"\$f\"); [ \"\${h%% *}\" = ${rootShellQuote(before.hash)} ] || { echo '备份后内容变化，未写入' >&2; exit 75; }; " +
            "started=1; cat \"\$r/new\" >&4 && truncate -s ${request.bytes.size} \"\$f\" || exit 74; " +
            "h=\$(sha256sum -- \"\$f\"); [ \"\${h%% *}\" = ${rootShellQuote(request.approval.newHash)} ] || exit 74; " +
            "after=\$(meta) || exit 74; old=\$m; " +
            "[ \"\$(stat -L -c '%d|%i|%u|%g|%a|%h' -- \"\$f\")\" = ${rootShellQuote(before.fields.take(6).joinToString("|"))} ] || exit 74; " +
            "z=\$(ls -LZd -- \"\$f\"); [ \"\${z%% *}\" = ${rootShellQuote(before.fields.last())} ] || exit 74; " +
            "sync \"\$f\" || exit 74; [ \"\$(readlink -f -- \"\$p\")\" = \"\$p\" ] && [ \"\$(stat -c '%d:%i' -- \"\$p\")\" = ${rootShellQuote(before.fields.take(2).joinToString(":"))} ] || { echo '保存期间路径被替换；未宣称成功' >&2; exit 75; }; " +
            "ok=1; printf '%s\\000%s\\000' \"\$r/original\" \"\${h%% *}\""
    }
    is RootMutationRequest.Workspace -> {
        val p = RootWritePolicy.requireSafeDirectory(request.path)
        check(p.matches(Regex("^/data/local/GMO-Root-[A-Za-z0-9_-]+$")))
        noRootMounts(p.substringBeforeLast('/')) + trustedRootDirectoryChain(p.substringBeforeLast('/')) +
            "p=${rootShellPath(p)}; [ ! -e \"\$p\" ] && [ ! -L \"\$p\" ] || { echo '工作目录已存在，请勿覆盖' >&2; exit 75; }; (umask 077; mkdir -- \"\$p\")"
    }
    is RootMutationRequest.AuthorizeDirectory -> rootSafeParent(request.path, "d") + "[ -w \"\$d\" ] || { echo '目录实际只读' >&2; exit 73; }; printf 'ROOTDIR'"
    is RootMutationRequest.Create -> {
        val name = validatedFileName(request.name)
        RootWritePolicy.requireFile(request.parent + "/" + name)
        rootSafeParent(request.parent, "d") + "p=\"\$d\"/${rootShellQuote(name)}; [ ! -e \"\$p\" ] && [ ! -L \"\$p\" ] || exit 75; " +
            if (request.text == null) "(umask 077; mkdir -- \"\$p\")" else {
                require(request.text.size <= MAX_EDIT_TEXT_BYTES)
                "(umask 077; set -C; base64 -d >\"\$p\" <<'GMO_CONTENT'\n${Base64.getEncoder().encodeToString(request.text)}\nGMO_CONTENT\n)"
            }
    }
    is RootMutationRequest.Rename -> rootSafeMutation(request.path, request.destination, "rename")
    is RootMutationRequest.Delete -> rootSafeMutation(request.path, null, "delete")
    is RootMutationRequest.Copy -> rootSafeMutation(request.path, request.destination, if (request.move) "move" else "copy")
}

private fun rootSafeMutation(source: String, destination: String?, operation: String): String {
    val sourcePath = RootWritePolicy.requireSafeDirectory(source)
    val parent = sourcePath.substringBeforeLast('/')
    RootWritePolicy.requireDirectory(parent)
    val destinationPath = destination?.let { RootWritePolicy.requireSafeDirectory(it) }
    val destinationParent = destinationPath?.substringBeforeLast('/')
    if (destinationParent != null) RootWritePolicy.requireDirectory(destinationParent)
    val guard = noRootMounts(sourcePath) + rootSafeParent(parent, "s") +
        (destinationParent?.let { rootSafeParent(it, "d") } ?: "") +
        "p=${rootShellPath(sourcePath)}; [ ! -L \"\$p\" ] && [ \"\$(readlink -f -- \"\$p\")\" = \"\$p\" ] || exit 72; " +
        "[ \"\$(stat -c '%u' -- \"\$p\")\" = 0 ] || exit 72; " +
        "if [ -d \"\$p\" ]; then [ -z \"\$(find \"\$p\" -mindepth 1 -maxdepth 1 -print -quit)\" ] || { echo 'Root不递归操作非空目录' >&2; exit 72; }; " +
        "elif [ -f \"\$p\" ]; then [ \"\$(stat -c '%h' -- \"\$p\")\" = 1 ] && [ \$((0\$(stat -c '%a' -- \"\$p\") & 0111)) = 0 ] || exit 72; else exit 72; fi; "
    if (operation == "delete") return guard + "if [ -d \"\$p\" ]; then rmdir -- \"\$p\"; else rm -- \"\$p\"; fi"
    val q = requireNotNull(destinationPath)
    val targetGuard = "q=${rootShellPath(q)}; [ ! -e \"\$q\" ] && [ ! -L \"\$q\" ] || { echo '目标已存在，未覆盖' >&2; exit 75; }; "
    if (operation in setOf("rename", "move")) return guard + targetGuard +
        "[ \"\$(stat -c '%d' -- \"\$p\")\" = \"\$(stat -c '%d' -- \"\$d\")\" ] || { echo '不允许Root跨文件系统移动' >&2; exit 72; }; mv -n -- \"\$p\" \"\$q\"; [ ! -e \"\$p\" ] && [ -e \"\$q\" ]"
    return guard + targetGuard + "[ -f \"\$p\" ] && [ \"\$(stat -c '%s' -- \"\$p\")\" -le 8388608 ] || { echo 'Root复制仅普通文件且最多8MiB' >&2; exit 72; }; " +
        "(umask 077; set -C; cat \"\$p\" >\"\$q\") || exit 74; a=\$(sha256sum -- \"\$p\"); b=\$(sha256sum -- \"\$q\"); [ \"\${a%% *}\" = \"\${b%% *}\" ]"
}
