package com.example.globalminusone.ui.panel.file

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.FileBrowserBookmark
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.system.*
import com.example.globalminusone.ui.AppIcons as Icons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import com.example.globalminusone.system.file.AnalyzerSortOrder
import com.example.globalminusone.system.file.BrowserEntry
import com.example.globalminusone.system.file.BrowserLocation
import com.example.globalminusone.system.file.DuplicateFileGroup
import com.example.globalminusone.system.file.FileBrowserIo
import com.example.globalminusone.system.file.FileCategory
import com.example.globalminusone.system.file.FileCategoryUsage
import com.example.globalminusone.system.file.FileManagerIo
import com.example.globalminusone.system.file.FileScanResult
import com.example.globalminusone.system.file.bookmark
import com.example.globalminusone.system.file.entry
import com.example.globalminusone.system.file.location
import com.example.globalminusone.system.file.relativeAnalyzerParentLabel
import com.example.globalminusone.system.file.withRenamed
import com.example.globalminusone.system.file.withoutDeleted
import com.example.globalminusone.system.file.withoutDeletedKeys
import com.example.globalminusone.ui.panel.LocalPanelPalette
import com.example.globalminusone.ui.panel.PanelAlertDialog
import com.example.globalminusone.ui.panel.PanelPalette

@Composable
internal fun FileManagementAnalyzerInspectorOverlay(
    state: FileManagementAnalyzerUiState,
    current: BrowserLocation,
    stack: List<BrowserLocation>,
    reload: Int,
    expanded: Boolean,
    busy: Boolean,
    scope: CoroutineScope,
    browser: FileBrowserIo,
    manager: FileManagerIo,
    repository: MinusOneRepository,
    onFinishInput: () -> Unit,
    onSetNotice: (String?) -> Unit,
    onRunTask: (mutates: Boolean, block: suspend FileManagementTaskSession.Task.() -> Unit) -> Unit,
    onOpenEntry: (BrowserEntry) -> Unit,
    onNavigateToDirectory: (List<BrowserLocation>) -> Unit,
    onFavorite: (List<BrowserEntry>) -> Unit
) {
    val analyzer = state.analyzer
    val inspector = state.inspector
    val activeInspector = inspector.entry.value ?: return
    val entryWritable = remember(activeInspector.location.key, reload, expanded) {
        activeInspector.blockedReason == null && manager.canWrite(activeInspector.location)
    }
    AnalyzerInspectorSheet(
        entry = activeInspector,
        root = current,
        properties = inspector.properties.value,
        checksums = inspector.checksums.value,
        hashCompareInput = inspector.hashCompare.value,
        onHashCompareChange = { inspector.hashCompare.value = it },
        writable = entryWritable,
        busy = busy,
        externalBusy = state.externalLaunching,
        renameMode = inspector.renameMode.value,
        renameInput = inspector.renameInput.value,
        onRenameInputChange = { inspector.renameInput.value = it },
        onToggleRename = {
            onFinishInput()
            inspector.toggleRename(activeInspector.name)
        },
        onConfirmRename = {
            val targetName = inspector.renameInput.value.trim()
            if (!busy && targetName.isBlank()) onSetNotice("名称不能为空")
            if (!busy && targetName.isNotBlank() && targetName != activeInspector.name) {
                onFinishInput()
                inspector.renameMode.value = false
                onRunTask(true) {
                    val updated = currentTaskIO { manager.renameKeepingAnalyzerCache(activeInspector, current, targetName) }
                    analyzer.statistics.value = analyzer.statistics.value?.withRenamed(activeInspector, updated)
                    analyzer.duplicateGroups.value = analyzer.duplicateGroups.value?.withRenamed(activeInspector.location.key, updated)
                    analyzer.results.value = analyzer.results.value?.let { r ->
                        r.copy(entries = r.entries.map { if (it.location.key == activeInspector.location.key) updated else it })
                    }
                    repository.updateFileBrowserConfig { previous ->
                        fun replace(items: List<FileBrowserBookmark>) = items.map { saved ->
                            if (saved.location().key == activeInspector.location.key) updated.location.bookmark(updated.isDirectory, updated.mimeType) else saved
                        }
                        previous.copy(favorites = replace(previous.favorites), recent = replace(previous.recent))
                    }
                    inspector.entry.value = updated
                    inspector.properties.value = currentTaskIO { runCatching { manager.properties(updated) }.getOrNull() }
                    onSetNotice("已重命名为 ${updated.name}")
                }
            }
        },
        deleteConfirmMode = inspector.deleteConfirm.value,
        onToggleDeleteConfirm = {
            onFinishInput()
            inspector.toggleDelete()
        },
        onConfirmDelete = {
            if (!busy) {
                onFinishInput()
                val toDelete = activeInspector
                inspector.deleteConfirm.value = false
                onRunTask(true) {
                    currentTaskProgress("删除 ${toDelete.name}")
                    currentTaskIO { manager.deleteKeepingAnalyzerCache(toDelete) }
                    val keySet = setOf(toDelete.location.key)
                    repository.updateFileBrowserConfig { saved ->
                        saved.copy(
                            favorites = saved.favorites.filterNot { it.location().key in keySet },
                            recent = saved.recent.filterNot { it.location().key in keySet }
                        )
                    }
                    analyzer.statistics.value = analyzer.statistics.value?.withoutDeleted(listOf(toDelete))
                    analyzer.duplicateGroups.value = analyzer.duplicateGroups.value?.withoutDeletedKeys(keySet)
                    analyzer.results.value = analyzer.results.value?.let { r -> r.copy(entries = r.entries.filterNot { it.location.key in keySet }) }
                    analyzer.selected.value = analyzer.selected.value - keySet
                    if (analyzer.selected.value.isEmpty()) analyzer.selecting.value = false
                    inspector.entry.value = null; inspector.properties.value = null; inspector.checksums.value = null
                    onSetNotice("已删除 ${toDelete.name}（释放 ${fileBytes(toDelete.size)}）")
                }
            }
        },
        onPreview = {
            val target = activeInspector
            inspector.entry.value = null; inspector.properties.value = null; inspector.checksums.value = null
            onOpenEntry(target)
        },
        onOpenParentDirectory = {
            onFinishInput()
            val parentLoc = manager.resolveEntryParent(activeInspector, current)
            val parentStack = manager.resolveParentStack(stack, parentLoc)
            inspector.entry.value = null; inspector.properties.value = null; inspector.checksums.value = null
            onNavigateToDirectory(parentStack)
        },
        onOpenExternal = { share ->
            if (!state.externalLaunching) {
                scope.launch {
                    state.externalLaunching = true
                    try { browser.launchExternal(activeInspector, share)?.let(onSetNotice) }
                    finally { state.externalLaunching = false }
                }
            }
        },
        onFavorite = { onFavorite(listOf(activeInspector)) },
        onSelectForBatch = {
            val target = activeInspector
            inspector.entry.value = null; inspector.properties.value = null; inspector.checksums.value = null
            analyzer.selecting.value = true
            analyzer.selected.value = analyzer.selected.value + target.location.key
        },
        onComputeChecksums = {
            if (!busy && !activeInspector.isDirectory) {
                onRunTask(false) {
                    inspector.checksums.value = currentTaskIO {
                        manager.calculateChecksums(activeInspector) { bytes ->
                            currentTaskProgress("计算哈希 ${fileBytes(bytes)}")
                        }
                    }
                }
            }
        },
        onCopyNotice = { msg -> onSetNotice(msg) },
        onDismiss = {
            if (!busy) {
                onFinishInput()
                inspector.entry.value = null; inspector.properties.value = null; inspector.checksums.value = null
                inspector.renameMode.value = false; inspector.deleteConfirm.value = false
            }
        }
    )
}

@Composable
internal fun FileManagementAnalyzerConfirmDialogs(
    state: FileManagementAnalyzerUiState,
    busy: Boolean,
    manager: FileManagerIo,
    onSetNotice: (String?) -> Unit,
    onRunTask: (mutates: Boolean, block: suspend FileManagementTaskSession.Task.() -> Unit) -> Unit
) {
    val analyzer = state.analyzer
    val statistics by analyzer.statistics
    val duplicateGroups by analyzer.duplicateGroups
    val results by analyzer.results
    val analyzerSelected by analyzer.selected

    if (state.batchDeleteConfirm) {
        val batchTargets = remember(analyzerSelected, statistics, duplicateGroups, results) {
            val map = LinkedHashMap<String, BrowserEntry>()
            statistics?.categoryFiles?.values?.forEach { list -> list.forEach { map[it.location.key] = it } }
            statistics?.largeFiles?.forEach { map[it.location.key] = it }
            statistics?.apkFiles?.forEach { map[it.location.key] = it }
            duplicateGroups?.forEach { g -> g.entries.forEach { map[it.location.key] = it } }
            results?.entries?.forEach { map[it.location.key] = it }
            analyzerSelected.mapNotNull { map[it] }
        }
        val reclaimBytes = batchTargets.sumOf { it.size ?: 0L }
        val wipedDuplicateGroups = remember(analyzerSelected, duplicateGroups) {
            duplicateGroups.orEmpty().count { g ->
                g.entries.size >= 2 && g.entries.all { it.location.key in analyzerSelected }
            }
        }
        PanelAlertDialog(
            onDismissRequest = { state.batchDeleteConfirm = false },
            title = { Text("确认删除所选 ${batchTargets.size} 个文件？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "预计释放空间：${fileBytes(reclaimBytes)}\n" +
                            "目标示例：${batchTargets.take(5).joinToString("、") { it.name }}${if (batchTargets.size > 5) " 等 ${batchTargets.size} 项" else ""}\n" +
                            "删除后不可恢复，统计结果将即时更新无需重新扫描。",
                        fontSize = 12.sp
                    )
                    if (wipedDuplicateGroups > 0) {
                        Text(
                            "⚠ 注意：其中有 $wipedDuplicateGroups 组重复文件的全部副本均已被勾选，删除后将不再保留原件！",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.batchDeleteConfirm = false
                        val work = batchTargets.toList()
                        onRunTask(true) {
                            val deleted = ArrayList<BrowserEntry>()
                            val failures = ArrayList<String>()
                            for (item in work) {
                                try {
                                    currentTaskProgress("删除 ${item.name}")
                                    currentTaskIO { manager.deleteKeepingAnalyzerCache(item) }
                                    deleted.add(item)
                                } catch (e: CancellationException) {
                                    checkCurrent()
                                    if (deleted.isNotEmpty()) {
                                        val delKeys = deleted.map { it.location.key }.toSet()
                                        analyzer.statistics.value = analyzer.statistics.value?.withoutDeleted(deleted)
                                        analyzer.duplicateGroups.value = analyzer.duplicateGroups.value?.withoutDeletedKeys(delKeys)
                                        analyzer.results.value = analyzer.results.value?.let { r -> r.copy(entries = r.entries.filterNot { it.location.key in delKeys }) }
                                        analyzer.selected.value = analyzer.selected.value - delKeys
                                    }
                                    throw e
                                } catch (e: Exception) {
                                    failures += "${item.name}：${e.message}"
                                }
                            }
                            val delKeys = deleted.map { it.location.key }.toSet()
                            analyzer.statistics.value = analyzer.statistics.value?.withoutDeleted(deleted)
                            analyzer.duplicateGroups.value = analyzer.duplicateGroups.value?.withoutDeletedKeys(delKeys)
                            analyzer.results.value = analyzer.results.value?.let { r -> r.copy(entries = r.entries.filterNot { it.location.key in delKeys }) }
                            analyzer.selected.value = analyzer.selected.value - delKeys
                            if (analyzer.selected.value.isEmpty()) analyzer.selecting.value = false
                            val freed = deleted.sumOf { it.size ?: 0L }
                            onSetNotice(
                                "已删除 ${deleted.size}/${work.size} 项（释放 ${fileBytes(freed)}）" +
                                    if (failures.isEmpty()) "" else "\n" + failures.take(3).joinToString("\n")
                            )
                        }
                    },
                    enabled = !busy && batchTargets.isNotEmpty()
                ) { Text("确认删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { state.batchDeleteConfirm = false }) { Text("取消") } }
        )
    }

    if (state.cleanAllEmptyConfirm) {
        val emptyDirs = statistics?.emptyDirectories.orEmpty()
        PanelAlertDialog(
            onDismissRequest = { state.cleanAllEmptyConfirm = false },
            title = { Text("一键清理 ${emptyDirs.size} 个空文件夹？") },
            text = { Text("删除前将逐个复核目录确实为空；若目录内已有新文件会自动跳过保护。", fontSize = 12.sp) },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.cleanAllEmptyConfirm = false
                        val targets = emptyDirs.toList()
                        onRunTask(true) {
                            val removed = ArrayList<BrowserLocation>()
                            val failures = ArrayList<String>()
                            for (dirLoc in targets) {
                                try {
                                    currentTaskProgress("清理空目录 ${dirLoc.title}")
                                    currentTaskIO { manager.deleteEmptyDirectoryKeepingAnalyzerCache(dirLoc) }
                                    removed.add(dirLoc)
                                } catch (e: CancellationException) {
                                    checkCurrent()
                                    if (removed.isNotEmpty()) {
                                        analyzer.statistics.value = analyzer.statistics.value?.withoutDeleted(emptyList(), removed)
                                    }
                                    throw e
                                } catch (e: Exception) {
                                    failures += "${dirLoc.title}：${e.message}"
                                }
                            }
                            analyzer.statistics.value = analyzer.statistics.value?.withoutDeleted(emptyList(), removed)
                            onSetNotice(
                                "已清理 ${removed.size}/${targets.size} 个空文件夹" +
                                    if (failures.isEmpty()) "" else "\n" + failures.take(3).joinToString("\n")
                            )
                        }
                    },
                    enabled = !busy && emptyDirs.isNotEmpty()
                ) { Text("确认清理", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { state.cleanAllEmptyConfirm = false }) { Text("取消") } }
        )
    }
}

@Composable
internal fun AnalyzerHeaderSummaryCard(
    current: BrowserLocation,
    statistics: FileScanResult?,
    busy: Boolean,
    palette: PanelPalette,
    categoryColors: Map<FileCategory, Color>,
    onRescan: () -> Unit,
    onDrillCategory: (FileCategory) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.8.dp, palette.cardBorder.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("分析范围：${current.title}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    val summaryText = statistics?.let { stats ->
                        val scanTime = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(stats.completedAt))
                        "共 ${stats.files} 文件 · ${stats.directories} 目录 · ${fileBytes(stats.knownBytes)}（缓存 $scanTime）"
                    } ?: if (busy) "正在深度统计目录占用…" else "按需统计当前目录与子目录"
                    Text(summaryText, fontSize = 11.sp, color = palette.textSecondary)
                }
                FilledTonalButton(
                    onClick = onRescan,
                    enabled = !busy,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Refresh, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("重新扫描", fontSize = 11.5.sp)
                }
            }

            statistics?.let { stats ->
                val orderedTypes = listOf(
                    FileCategory.VIDEO, FileCategory.IMAGE, FileCategory.ARCHIVE,
                    FileCategory.AUDIO, FileCategory.DOCUMENT, FileCategory.OTHER
                )
                if (stats.knownBytes > 0L) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(palette.cardBorder.copy(alpha = 0.25f))
                    ) {
                        orderedTypes.forEach { cat ->
                            val bytes = stats.groups[cat]?.knownBytes ?: 0L
                            val weight = (bytes.toFloat() / stats.knownBytes.toFloat()).coerceAtLeast(0f)
                            if (weight > 0.005f) {
                                Box(
                                    Modifier
                                        .weight(weight)
                                        .fillMaxHeight()
                                        .background(categoryColors[cat] ?: Color.Gray)
                                        .clickable(enabled = !busy) { onDrillCategory(cat) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AnalyzerSubTabsRow(
    state: FileManagementAnalyzerUiState,
    statistics: FileScanResult?,
    duplicateGroups: List<DuplicateFileGroup>?,
    busy: Boolean,
    palette: PanelPalette,
    onSelectTab: (String) -> Unit
) {
    val largeCount = statistics?.largeFiles?.size ?: 0
    val apkCount = statistics?.apkFiles?.size ?: 0
    val emptyDirCount = statistics?.emptyDirectories?.size ?: 0
    val dupCount = duplicateGroups?.size
    val subTabs = listOf(
        "overview" to "分类占比",
        "large" to "大文件($largeCount)",
        "duplicates" to if (dupCount != null) "重复($dupCount)" else "重复查重",
        "apk" to "安装包($apkCount)",
        "empty" to "空目录($emptyDirCount)",
        "search" to "递归搜索"
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(subTabs, key = { it.first }) { (id, title) ->
            val activeSub = state.section == id
            Surface(
                onClick = { onSelectTab(id) },
                shape = RoundedCornerShape(10.dp),
                color = if (activeSub) palette.accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(0.8.dp, if (activeSub) palette.accent else palette.cardBorder.copy(alpha = 0.45f))
            ) {
                Text(
                    text = title,
                    fontSize = 11.5.sp,
                    fontWeight = if (activeSub) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (activeSub) palette.accent else palette.textPrimary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
internal fun AnalyzerCategoryTile(
    type: FileCategory,
    usage: FileCategoryUsage,
    ratio: Double?,
    dotColor: Color,
    palette: PanelPalette,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.8.dp, palette.cardBorder.copy(alpha = 0.45f)),
        modifier = modifier
    ) {
        Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).background(dotColor, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(type.label, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text(
                    ratio?.let { String.format(Locale.ROOT, "%.1f%%", it * 100) } ?: "0%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = dotColor
                )
                Icon(Icons.KeyboardArrowRight, contentDescription = "查看${type.label}文件", modifier = Modifier.size(15.dp), tint = palette.textSecondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${fileBytes(usage.knownBytes)} · ${usage.files} 项",
                    fontSize = 10.5.sp,
                    color = palette.textSecondary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (usage.files > 0) {
                    Text("查看", fontSize = 10.sp, color = palette.accent, fontWeight = FontWeight.Medium)
                }
            }
            LinearProgressIndicator(
                progress = { ratio?.toFloat() ?: 0f },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = dotColor,
                trackColor = palette.cardBorder.copy(alpha = 0.25f)
            )
        }
    }
}

@Composable
internal fun AnalyzerCategoryDrillHeader(
    activeCategory: FileCategory,
    usage: FileCategoryUsage,
    dotColor: Color,
    palette: PanelPalette,
    rawCategoryListEmpty: Boolean,
    hasFilteredEntries: Boolean,
    isPartial: Boolean,
    rawCategorySize: Int,
    filterQuery: String,
    onFilterQueryChange: (String) -> Unit,
    currentSort: AnalyzerSortOrder,
    onSortChange: (AnalyzerSortOrder) -> Unit,
    selecting: Boolean,
    onToggleSelect: () -> Unit,
    onBackToAll: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.8.dp, palette.cardBorder.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = onBackToAll,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(Icons.ArrowBack, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("全部分类", fontSize = 11.5.sp)
                }
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(8.dp).background(dotColor, CircleShape))
                Spacer(Modifier.width(5.dp))
                Text(
                    "${activeCategory.label} · ${usage.files} 项 (${fileBytes(usage.knownBytes)})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (hasFilteredEntries) {
                    TextButton(
                        onClick = onToggleSelect,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(if (selecting) "退出多选" else "多选", fontSize = 11.5.sp)
                    }
                }
            }
            if (!rawCategoryListEmpty) {
                OutlinedTextField(
                    value = filterQuery,
                    onValueChange = onFilterQueryChange,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    placeholder = { Text("过滤${activeCategory.label}文件名、子目录或 *.后缀", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.FilterList, null, Modifier.size(16.dp)) },
                    trailingIcon = {
                        if (filterQuery.isNotEmpty()) {
                            IconButton(onClick = { onFilterQueryChange("") }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Close, "清空过滤", Modifier.size(15.dp))
                            }
                        }
                    }
                )
                AnalyzerSortAndHintBar(
                    currentSort = currentSort,
                    onSortChange = onSortChange,
                    trailingHint = if (isPartial) "按体积展示前 $rawCategorySize 项" else "点击查看详情与操作 · 长按多选"
                )
            }
        }
    }
}

@Composable
internal fun AnalyzerFileListToolbar(
    title: String,
    trailingHint: String,
    selecting: Boolean,
    currentSort: AnalyzerSortOrder,
    palette: PanelPalette,
    onToggleSelect: () -> Unit,
    onSortChange: (AnalyzerSortOrder) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.8.dp, palette.cardBorder.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                TextButton(
                    onClick = onToggleSelect,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(if (selecting) "退出多选" else "多选", fontSize = 11.5.sp)
                }
            }
            AnalyzerSortAndHintBar(currentSort = currentSort, onSortChange = onSortChange, trailingHint = trailingHint)
        }
    }
}

@Composable
internal fun AnalyzerSortAndHintBar(
    currentSort: AnalyzerSortOrder,
    onSortChange: (AnalyzerSortOrder) -> Unit,
    trailingHint: String
) {
    val palette = opaqueFilePalette(LocalPanelPalette.current)
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        AnalyzerSortOrder.entries.forEach { mode ->
            val selected = currentSort == mode
            Surface(
                onClick = { onSortChange(mode) },
                shape = RoundedCornerShape(8.dp),
                color = if (selected) palette.accent.copy(alpha = 0.16f) else palette.cardBorder.copy(alpha = 0.16f),
                border = if (selected) BorderStroke(0.8.dp, palette.accent) else null
            ) {
                Text(
                    text = mode.label,
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) palette.accent else palette.textSecondary,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            trailingHint,
            fontSize = 9.5.sp,
            color = palette.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun AnalyzerEntryRow(
    entry: BrowserEntry,
    root: BrowserLocation,
    enabled: Boolean = true,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    badge: String? = null,
    embeddedInCard: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onInspect: () -> Unit
) {
    val palette = opaqueFilePalette(LocalPanelPalette.current)
    val relParent = remember(root.key, entry.location.key, entry.parentLocation?.key) {
        relativeAnalyzerParentLabel(root, entry)
    }
    val subtitle = remember(entry.isDirectory, entry.size, entry.modified, entry.blockedReason, relParent) {
        entry.blockedReason ?: listOfNotNull(
            if (entry.isDirectory) "文件夹" else fileBytes(entry.size),
            relParent,
            entry.modified?.let { DateFormat.getDateInstance(DateFormat.SHORT).format(Date(it)) }
        ).joinToString(" · ")
    }
    val rowContent: @Composable () -> Unit = {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .background(if (selectionMode && selected) palette.accent.copy(alpha = 0.11f) else Color.Transparent)
                .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)
                .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                if (selectionMode && !entry.isDirectory) {
                    Checkbox(checked = selected, onCheckedChange = { onClick() }, enabled = enabled)
                } else {
                    FileWorkspaceEntryGlyph(entry, compact = true)
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.name,
                        fontSize = 12.8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = palette.textPrimary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (badge != null) {
                        Spacer(Modifier.width(5.dp))
                        val keepBadge = badge.startsWith("保留")
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (keepBadge) palette.accent.copy(alpha = 0.15f) else Color(0xFFD28445).copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = badge,
                                fontSize = 9.sp,
                                color = if (keepBadge) palette.accent else Color(0xFFD28445),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = palette.textSecondary
                )
            }
            if (!selectionMode) {
                IconButton(onClick = onInspect, enabled = enabled, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (entry.isDirectory) Icons.KeyboardArrowRight else Icons.MoreVert,
                        contentDescription = "${entry.name} 详情与操作",
                        modifier = Modifier.size(18.dp),
                        tint = palette.textSecondary
                    )
                }
            }
        }
    }
    if (embeddedInCard) {
        Column {
            rowContent()
            HorizontalDivider(Modifier.padding(start = 50.dp), color = palette.cardBorder.copy(alpha = 0.22f))
        }
    } else {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(0.7.dp, if (selectionMode && selected) palette.accent else palette.cardBorder.copy(alpha = 0.38f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            rowContent()
        }
    }
}
