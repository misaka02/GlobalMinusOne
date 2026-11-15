package com.example.globalminusone.system

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.ArrayDeque

internal data class FileSearchBudget(val entries: Int = 10_000, val directories: Int = 256, val millis: Long = 5_000) {
    init { require(entries > 0 && directories > 0 && millis > 0) }
}

/** Immutable resume snapshots allow cancellation/retry without skipping already-undelivered files. */
internal suspend fun searchFileTree(
    location: BrowserLocation, name: String, category: FileCategory, previous: FileSearchCursor? = null,
    permissionsNow: () -> String, checkRead: (BrowserLocation) -> Unit,
    list: suspend (BrowserLocation) -> List<BrowserEntry>, onProgress: suspend (FileScanProgress) -> Unit,
    includeSubdirectories: Boolean = true, regex: Boolean = false,
    budget: FileSearchBudget = FileSearchBudget(), nowMillis: () -> Long = { System.nanoTime() / 1_000_000 }
): FileSearchResult {
        val matcher = FileSearchPattern(name, regex)
        val permissions = permissionsNow(); checkRead(location)
        if (previous != null) check(previous.rootKey == location.key && previous.permissions == permissions && previous.query == name && previous.category == category && previous.includeSubdirectories == includeSubdirectories && previous.regex == regex) { "查找范围、模式、关键词或权限已改变，请重新查找" }
        val queue = ArrayDeque<BrowserLocation>(); queue.addAll(previous?.pending ?: listOf(location))
        val seen = (previous?.seen ?: emptySet()).toMutableSet()
        var children = previous?.rows ?: emptyList(); var index = previous?.index ?: 0
        var visited = previous?.visited ?: 0; var directories = previous?.directories ?: 0; var skipped = previous?.skippedDirectories ?: 0
        val result = mutableListOf<BrowserEntry>()
        val startingVisited = visited; val startingDirectories = directories; val startedAt = nowMillis()
        fun page(reason: String): FileSearchResult {
            checkRead(location); check(permissionsNow() == permissions) { "权限变化，查找结果已废弃" }
            val cursor = if (index < children.size || queue.isNotEmpty()) FileSearchCursor(location.key, permissions, name, category,
                queue.toList(), seen.toSet(), children, index, visited, directories, skipped, includeSubdirectories, regex) else null
            return FileSearchResult(result.toList(), cursor, skipped, reason.takeIf { cursor != null })
        }
        while (index < children.size || queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            check(permissionsNow() == permissions) { "权限变化，查找结果已废弃" }
            if (visited - startingVisited >= budget.entries) return page("达到本批扫描项数上限")
            if (nowMillis() - startedAt >= budget.millis) return page("达到本批扫描时间上限")
            if (index >= children.size) {
                if (directories - startingDirectories >= budget.directories) return page("达到本批目录数上限")
                val folder = queue.removeFirst()
                if (!seen.add(folder.key)) continue
                onProgress(FileScanProgress(visited, ++directories, folder.title))
                children = try { list(folder).map { it.copy(parentLocation = folder) } }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    checkRead(location); check(permissionsNow() == permissions) { "权限变化，查找结果已废弃" }
                    if (folder.key == location.key) throw e
                    skipped++; emptyList()
                }
                index = 0
            }
            while (index < children.size) {
                currentCoroutineContext().ensureActive()
                if (visited - startingVisited >= budget.entries) return page("达到本批扫描项数上限")
                if (nowMillis() - startedAt >= budget.millis) return page("达到本批扫描时间上限")
                val entry = children[index++]; visited++
                if (visited % 256 == 0) onProgress(FileScanProgress(visited, directories, entry.name))
                if (entry.blockedReason != null) { skipped++; continue }
                if (entry.isDirectory && includeSubdirectories) queue.add(entry.location)
                if ((category == FileCategory.ALL || FileCategory.of(entry) == category) && matcher.matches(entry.name)) {
                    result += entry
                    if (result.size == 1000) return page("达到本批1000条结果上限")
                }
            }
        }
        checkRead(location); check(permissionsNow() == permissions) { "权限变化，查找结果已废弃" }
        return FileSearchResult(result, null, skipped)
}
