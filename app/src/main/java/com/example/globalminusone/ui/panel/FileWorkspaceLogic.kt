package com.example.globalminusone.ui.panel

import com.example.globalminusone.system.BrowserEntry
import com.example.globalminusone.system.BrowserLocation
import com.example.globalminusone.system.FileCategory

/** A visibility/preview gap needs one fresh listing when the directory becomes visible again. */
internal class FileBrowserReturnRefreshState(private var active: Boolean) {
    fun update(nowActive: Boolean): Boolean {
        val refresh = nowActive && !active
        active = nowActive
        return refresh
    }
}

internal fun fileWorkspaceClipboardTitle(sourcePane: Int, count: Int, move: Boolean): String =
    "${if (sourcePane == 0) "A → B" else "B → A"} · 待处理 $count 项 · ${if (move) "移动" else "复制"}"

internal fun fileBrowserEmptyMessage(entries: List<BrowserEntry>, showHidden: Boolean,
    search: String, quickFilter: String, category: FileCategory, filteringSearchResults: Boolean): String = when {
    quickFilter.isNotBlank() -> if (filteringSearchResults) "当前查找结果中没有匹配的文件或文件夹"
        else "当前目录没有匹配的文件或文件夹。查找子目录请使用「查找」并开启「包含子目录」。"
    search.isNotBlank() -> "当前查找范围没有匹配结果"
    category != FileCategory.ALL -> "当前目录没有符合所选类型的项目"
    entries.isNotEmpty() && !showHidden && entries.all { it.name.startsWith('.') } -> "目录中只有隐藏项目，可打开「隐藏文件」"
    entries.isNotEmpty() -> "当前筛选没有匹配结果"
    else -> "此目录为空"
}

/** Selection records are the source of truth; keys cannot get ahead of the first selected record. */
internal data class FileWorkspaceSelection(val records: Map<String, BrowserEntry> = emptyMap()) {
    val keys: Set<String> get() = records.keys
    fun toggle(entry: BrowserEntry): FileWorkspaceSelection {
        if (entry.blockedReason != null) return this
        return copy(records = if (entry.location.key in records) records - entry.location.key else records + (entry.location.key to entry))
    }
    fun select(entry: BrowserEntry): FileWorkspaceSelection =
        if (entry.blockedReason != null) this else copy(records = records + (entry.location.key to entry))
    fun without(completed: Set<String>) = copy(records = records.filterKeys { it !in completed })
    fun transfer(parent: BrowserLocation, move: Boolean): TransferClipboard? =
        records.values.takeIf { it.isNotEmpty() }?.let { TransferClipboard(it.toList(), parent, move) }
}

/** A confirmed transfer never follows a subsequent pane switch or directory navigation. */
internal data class FileWorkspaceTransferPlan(
    val sourcePane: Int,
    val targetPane: Int,
    val clipboard: TransferClipboard,
    val destination: BrowserLocation
) {
    companion object {
        fun create(sourcePane: Int, clipboard: TransferClipboard, destination: BrowserLocation?): FileWorkspaceTransferPlan {
            require(sourcePane in 0..1) { "无效的源位置" }
            require(clipboard.entries.isNotEmpty()) { "请先选择文件" }
            val target = requireNotNull(destination) { "请先在另一侧选择目标目录" }
            require(target.singleDocumentUri == null) { "目标必须是目录" }
            require(clipboard.entries.none { (it.parentLocation ?: clipboard.parent).key == target.key }) { "包含已在目标目录中的项目，请在另一侧选择不同目录" }
            require(clipboard.entries.none { it.blockedReason != null }) { "包含无法操作的项目" }
            require(clipboard.entries.all { it.location.rootAccess == target.rootAccess }) { "Root文件不能跨模式传输" }
            require(clipboard.entries.none { it.location.key == target.key }) { "不能复制到自身" }
            return FileWorkspaceTransferPlan(sourcePane, 1 - sourcePane, clipboard.copy(entries = clipboard.entries.toList()), target)
        }
    }
}

internal data class FileWorkspaceTransferReceipt(val id: Long, val sourcePane: Int, val completed: Set<String>)

/** Confirmation owns an immutable batch and each result's original parent directory. */
internal data class FileDeletionTarget(val entry: BrowserEntry, val parent: BrowserLocation)
internal class FileDeletionPlan private constructor(val targets: List<FileDeletionTarget>) {
    companion object {
        fun create(entries: Collection<BrowserEntry>, currentDirectory: BrowserLocation): FileDeletionPlan {
            require(entries.isNotEmpty()) { "请先选择文件" }
            require(entries.none { it.blockedReason != null }) { "包含无法操作的项目" }
            return FileDeletionPlan(entries.distinctBy { it.location.key }.map {
                FileDeletionTarget(it, it.parentLocation ?: currentDirectory)
            })
        }
    }
}

internal fun fileWorkspacePath(location: BrowserLocation): String = location.path
    ?: location.documentId?.substringAfter(':')?.let { "/$it" }
    ?: location.title
