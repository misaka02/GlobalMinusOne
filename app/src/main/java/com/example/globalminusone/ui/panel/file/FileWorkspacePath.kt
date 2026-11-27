package com.example.globalminusone.ui.panel.file

import com.example.globalminusone.system.file.BrowserEntry
import com.example.globalminusone.system.file.BrowserLocation

internal data class FileWorkspacePathPlan(val base: List<BrowserLocation>, val segments: List<String>)

/** Resolve human-readable paths inside the selected root without manufacturing document IDs or grants. */
internal object FileWorkspacePathPolicy {
    fun plan(input: String, currentStack: List<BrowserLocation>, availableRoots: List<BrowserLocation> = emptyList()): FileWorkspacePathPlan {
        val path = input.trim()
        require(path.isNotEmpty() && path.none { it == '\u0000' || it == '\n' || it == '\r' }) { "请输入有效的目录路径" }
        val root = if (path.startsWith('/')) {
            (currentStack.take(1) + availableRoots).distinctBy { it.key }.filter {
                val prefix = fileWorkspacePath(it).trimEnd('/').ifEmpty { "/" }
                prefix == "/" || path == prefix || path.startsWith("$prefix/")
            }.maxByOrNull { fileWorkspacePath(it).trimEnd('/').length }
                ?: throw IllegalArgumentException("路径不在可用存储位置中，请输入共享存储或已授权目录路径")
        } else currentStack.firstOrNull() ?: throw IllegalArgumentException("请从存储首页输入完整目录路径")
        val rootPath = fileWorkspacePath(root).trimEnd('/').ifEmpty { "/" }
        var base: List<BrowserLocation>
        val relative: String
        if (path.startsWith('/')) {
            require(rootPath == "/" || path == rootPath || path.startsWith("$rootPath/")) { "路径超出当前存储位置，请先从位置菜单选择对应目录" }
            base = listOf(root)
            relative = if (rootPath == "/") path.removePrefix("/") else path.removePrefix(rootPath).trimStart('/')
        } else { base = currentStack.toList(); relative = path }
        val segments = mutableListOf<String>()
        relative.split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex)
                    else { require(base.size > 1) { "不能越过当前存储位置" }; base = base.dropLast(1) }
                else -> segments += segment
            }
        }
        return FileWorkspacePathPlan(base.toList(), segments.toList())
    }
}

/** Each real listing revalidates SAF/shared/Root access through the existing browser IO. */
internal suspend fun resolveFileWorkspacePath(plan: FileWorkspacePathPlan,
    listDirectory: suspend (BrowserLocation) -> List<BrowserEntry>): List<BrowserLocation> {
    val resolved = plan.base.toMutableList()
    require(resolved.isNotEmpty())
    var children = listDirectory(resolved.last())
    plan.segments.forEach { name ->
        val entry = children.firstOrNull { it.name == name } ?: error("找不到目录：$name")
        check(entry.blockedReason == null) { entry.blockedReason ?: "此目录不可访问" }
        check(entry.isDirectory) { "$name 不是目录" }
        resolved += entry.location
        children = listDirectory(entry.location)
    }
    return resolved.toList()
}
