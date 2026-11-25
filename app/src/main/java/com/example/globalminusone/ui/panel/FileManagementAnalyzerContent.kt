package com.example.globalminusone.ui.panel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.FileBrowserBookmark
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.system.*
import com.example.globalminusone.ui.AppIcons as Icons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

internal class FileManagementAnalyzerUiState(
    val tasks: FileManagementTaskSession,
    initialSection: String,
    initialDrillCategory: FileCategory?,
    initialRootKey: String?
) {
    val analyzer: FileManagementAnalyzerState = tasks.analyzer
    val inspector = FileInspectorState()
    var section by mutableStateOf(initialSection)
    var drillCategory by mutableStateOf(initialDrillCategory)
    var sort by mutableStateOf(AnalyzerSortOrder.SIZE_DESC)
    var categoryFilterQuery by mutableStateOf("")
    var categoryMenu by mutableStateOf(false)
    var batchDeleteConfirm by mutableStateOf(false)
    var cleanAllEmptyConfirm by mutableStateOf(false)
    var externalLaunching by mutableStateOf(false)
    var trackedRootKey by mutableStateOf(initialRootKey)
}

@Composable
internal fun ColumnScope.FileManagementAnalyzerPane(
    state: FileManagementAnalyzerUiState,
    current: BrowserLocation,
    stack: List<BrowserLocation>,
    expanded: Boolean,
    reload: Int,
    busy: Boolean,
    writable: Boolean,
    palette: PanelPalette,
    manager: FileManagerIo,
    onFinishInput: () -> Unit,
    onSetNotice: (String?) -> Unit,
    onRunTask: (mutates: Boolean, block: suspend FileManagementTaskSession.Task.() -> Unit) -> Unit,
    onFavorite: (List<BrowserEntry>) -> Unit,
    onOpenEntry: (BrowserEntry) -> Unit,
    onNavigateToDirectory: (List<BrowserLocation>) -> Unit
) {
    val analyzer = state.analyzer
    var statistics by analyzer.statistics
    var duplicateGroups by analyzer.duplicateGroups
    var results by analyzer.results
    var analyzerSelecting by analyzer.selecting
    var analyzerSelected by analyzer.selected
    val query by analyzer.query
    val category by analyzer.category
    val inspector = state.inspector

    // Load cached scan/duplicates in 0ms; only scan once when no cached snapshot exists for current directory
    LaunchedEffect(current.key, expanded, reload) {
        if (expanded) {
            val cached = withContext(Dispatchers.IO) { runCatching { manager.cachedScan(current) }.getOrNull() }
            if (cached != null) {
                statistics = cached
            } else if (statistics == null && !busy) {
                onRunTask(false) {
                    statistics = currentTaskIO {
                        manager.scan(current, force = false) {
                            currentTaskProgress("正在扫描 ${it.visited} 项 / ${it.directories} 目录：${it.current}")
                        }
                    }
                }
            }
            if (duplicateGroups == null) {
                duplicateGroups = runCatching { manager.cachedDuplicates(current) }.getOrNull()
            }
        }
    }

    LaunchedEffect(inspector.entry.value?.location?.key) {
        val target = inspector.entry.value ?: return@LaunchedEffect
        val loaded = runCatching { manager.properties(target) }.getOrNull()
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        if (inspector.entry.value?.location?.key == target.location.key) {
            inspector.properties.value = loaded
        }
    }

    val categoryColors = remember {
        mapOf(
            FileCategory.IMAGE to Color(0xFF9B67CA),
            FileCategory.VIDEO to Color(0xFF4C8BF5),
            FileCategory.AUDIO to Color(0xFF38A88D),
            FileCategory.ARCHIVE to Color(0xFFD28445),
            FileCategory.DOCUMENT to Color(0xFF5C7CFA),
            FileCategory.OTHER to Color(0xFF7E8A9C)
        )
    }

    fun openInspector(entry: BrowserEntry) {
        if (busy) return
        onFinishInput()
        inspector.open(entry)
    }

    fun toggleAnalyzerSelect(entry: BrowserEntry) {
        if (busy) return
        val k = entry.location.key
        analyzerSelected = if (k in analyzerSelected) analyzerSelected - k else analyzerSelected + k
    }

    fun startAnalyzerSelect(entry: BrowserEntry) {
        if (busy) return
        analyzerSelecting = true
        analyzerSelected = analyzerSelected + entry.location.key
    }

    val rawCategoryList = remember(statistics, state.drillCategory) {
        state.drillCategory?.let { statistics?.categoryFiles?.get(it) }.orEmpty()
    }
    val filteredCategoryEntries = remember(rawCategoryList, state.categoryFilterQuery, state.sort, current.key) {
        val q = state.categoryFilterQuery.trim()
        val filtered = if (q.isEmpty()) rawCategoryList else rawCategoryList.filter { entry ->
            matchesQuickFilter(entry.name, q) ||
                relativeAnalyzerParentLabel(current, entry).contains(q, ignoreCase = true)
        }
        sortAnalyzerEntries(filtered, state.sort)
    }
    val sortedLargeEntries = remember(statistics?.largeFiles, state.sort) {
        sortAnalyzerEntries(statistics?.largeFiles.orEmpty(), state.sort)
    }
    val sortedApkEntries = remember(statistics?.apkFiles, state.sort) {
        sortAnalyzerEntries(statistics?.apkFiles.orEmpty(), state.sort)
    }
    val analyzerVisibleEntries = remember(
        state.section, state.drillCategory, filteredCategoryEntries,
        sortedLargeEntries, duplicateGroups, sortedApkEntries, results
    ) {
        when {
            state.section == "overview" && state.drillCategory != null -> filteredCategoryEntries
            state.section == "large" -> sortedLargeEntries
            state.section == "duplicates" -> duplicateGroups.orEmpty().flatMap { it.entries }
            state.section == "apk" -> sortedApkEntries
            state.section == "search" -> results?.entries.orEmpty()
            else -> emptyList()
        }
    }
    val analyzerEntryLookup = remember(statistics, duplicateGroups, results, analyzerVisibleEntries) {
        val map = LinkedHashMap<String, BrowserEntry>()
        statistics?.categoryFiles?.values?.forEach { list -> list.forEach { map[it.location.key] = it } }
        statistics?.largeFiles?.forEach { map[it.location.key] = it }
        statistics?.apkFiles?.forEach { map[it.location.key] = it }
        duplicateGroups?.forEach { g -> g.entries.forEach { map[it.location.key] = it } }
        results?.entries?.forEach { map[it.location.key] = it }
        analyzerVisibleEntries.forEach { map[it.location.key] = it }
        map
    }
    val chosenAnalyzerEntries = remember(analyzerSelected, analyzerEntryLookup) {
        analyzerSelected.mapNotNull { analyzerEntryLookup[it] }
    }

    fun handleAnalyzerRowClick(entry: BrowserEntry) {
        if (analyzerSelecting) toggleAnalyzerSelect(entry) else openInspector(entry)
    }

    Column(Modifier.weight(1f).fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "analyzer-header") {
                AnalyzerHeaderSummaryCard(
                    current = current,
                    statistics = statistics,
                    busy = busy,
                    palette = palette,
                    categoryColors = categoryColors,
                    onRescan = {
                        onFinishInput()
                        analyzerSelecting = false
                        analyzerSelected = emptySet()
                        inspector.entry.value = null
                        duplicateGroups = null
                        results = null
                        onRunTask(false) {
                            statistics = currentTaskIO {
                                manager.scan(current, force = true) {
                                    currentTaskProgress("扫描 ${it.visited} 项 / ${it.directories} 目录：${it.current}")
                                }
                            }
                        }
                    },
                    onDrillCategory = { cat ->
                        state.section = "overview"
                        state.drillCategory = cat
                        state.categoryFilterQuery = ""
                        analyzerSelecting = false
                        analyzerSelected = emptySet()
                    }
                )
            }

            item(key = "analyzer-subtabs") {
                AnalyzerSubTabsRow(
                    state = state,
                    statistics = statistics,
                    duplicateGroups = duplicateGroups,
                    busy = busy,
                    palette = palette,
                    onSelectTab = { id ->
                        onFinishInput()
                        if (state.section != id) {
                            state.section = id
                            analyzerSelecting = false
                            analyzerSelected = emptySet()
                        } else if (id == "overview") {
                            state.drillCategory = null
                        }
                        if (id == "duplicates" && duplicateGroups == null && !busy) {
                            val cachedDups = runCatching { manager.cachedDuplicates(current) }.getOrNull()
                            if (cachedDups != null) {
                                duplicateGroups = cachedDups
                            } else {
                                onRunTask(false) {
                                    duplicateGroups = currentTaskIO {
                                        manager.findDuplicateFiles(current, force = false) {
                                            currentTaskProgress("SHA-256 查重 ${it.visited} 项：${it.current}")
                                        }
                                    }
                                    if (duplicateGroups?.isEmpty() == true) onSetNotice("未发现内容完全相同的重复文件")
                                }
                            }
                        }
                    }
                )
            }

            when (state.section) {
                "overview" -> {
                    val stats = statistics
                    val activeCategory = state.drillCategory
                    if (stats != null && activeCategory == null) {
                        val types = FileCategory.entries.filter { it !in setOf(FileCategory.ALL, FileCategory.DIRECTORY) }
                        items(types.chunked(2), key = { pair -> "cat-pair:${pair.joinToString { it.name }}" }) { pair ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                pair.forEach { type ->
                                    val usage = stats.groups[type] ?: FileCategoryUsage()
                                    val ratio = stats.knownRatio(type)
                                    val dotColor = categoryColors[type] ?: palette.accent
                                    AnalyzerCategoryTile(
                                        type = type,
                                        usage = usage,
                                        ratio = ratio,
                                        dotColor = dotColor,
                                        palette = palette,
                                        enabled = !busy,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            state.drillCategory = type
                                            state.categoryFilterQuery = ""
                                            analyzerSelecting = false
                                            analyzerSelected = emptySet()
                                        }
                                    )
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                        item(key = if (stats.inaccessibleDirectories > 0) "inaccessible-hint" else "category-click-hint") {
                            Text(
                                if (stats.inaccessibleDirectories > 0) {
                                    "提示：有 ${stats.inaccessibleDirectories} 个受系统保护的子目录已跳过统计。点击任意分类卡片可查看并管理对应文件。"
                                } else {
                                    "提示：点击上方任意分类卡片或色条，即可查看该分类文件详情、定位目录或批量清理。"
                                },
                                fontSize = 10.5.sp,
                                color = palette.textSecondary
                            )
                        }
                    } else if (stats != null && activeCategory != null) {
                        val usage = stats.groups[activeCategory] ?: FileCategoryUsage()
                        val dotColor = categoryColors[activeCategory] ?: palette.accent
                        item(key = "category-drill-header") {
                            AnalyzerCategoryDrillHeader(
                                activeCategory = activeCategory,
                                usage = usage,
                                dotColor = dotColor,
                                palette = palette,
                                rawCategoryListEmpty = rawCategoryList.isEmpty(),
                                hasFilteredEntries = filteredCategoryEntries.isNotEmpty(),
                                isPartial = usage.files > rawCategoryList.size,
                                rawCategorySize = rawCategoryList.size,
                                filterQuery = state.categoryFilterQuery,
                                onFilterQueryChange = { state.categoryFilterQuery = it },
                                currentSort = state.sort,
                                onSortChange = { state.sort = it },
                                selecting = analyzerSelecting,
                                onToggleSelect = {
                                    analyzerSelecting = !analyzerSelecting
                                    if (!analyzerSelecting) analyzerSelected = emptySet()
                                },
                                onBackToAll = {
                                    onFinishInput()
                                    state.drillCategory = null
                                    state.categoryFilterQuery = ""
                                    analyzerSelecting = false
                                    analyzerSelected = emptySet()
                                }
                            )
                        }
                        if (filteredCategoryEntries.isEmpty()) {
                            item(key = "category-drill-empty") {
                                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        if (rawCategoryList.isEmpty()) "当前目录下没有「${activeCategory.label}」类文件。" else "没有匹配过滤条件的文件。",
                                        Modifier.padding(14.dp),
                                        fontSize = 12.sp,
                                        color = palette.textSecondary
                                    )
                                }
                            }
                        } else {
                            items(filteredCategoryEntries, key = { "cat-row:${it.location.key}" }) { entry ->
                                AnalyzerEntryRow(
                                    entry = entry,
                                    root = current,
                                    enabled = !busy,
                                    selectionMode = analyzerSelecting,
                                    selected = entry.location.key in analyzerSelected,
                                    onClick = { handleAnalyzerRowClick(entry) },
                                    onLongClick = { startAnalyzerSelect(entry) },
                                    onInspect = { openInspector(entry) }
                                )
                            }
                        }
                    }
                }

                "large" -> {
                    if (sortedLargeEntries.isEmpty()) {
                        item(key = "large-empty") {
                            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                                Text("当前目录下没有大于 10 MB 的大文件。", Modifier.padding(14.dp), fontSize = 12.sp, color = palette.textSecondary)
                            }
                        }
                    } else {
                        item(key = "large-toolbar") {
                            AnalyzerFileListToolbar(
                                title = "大文件 (>10 MB · ${sortedLargeEntries.size} 项 · ${fileBytes(sortedLargeEntries.sumOf { it.size ?: 0L })})",
                                trailingHint = "点击查看路径/属性/操作 · 长按多选",
                                selecting = analyzerSelecting,
                                currentSort = state.sort,
                                palette = palette,
                                onToggleSelect = {
                                    analyzerSelecting = !analyzerSelecting
                                    if (!analyzerSelecting) analyzerSelected = emptySet()
                                },
                                onSortChange = { state.sort = it }
                            )
                        }
                        items(sortedLargeEntries, key = { "large-row:${it.location.key}" }) { entry ->
                            AnalyzerEntryRow(
                                entry = entry,
                                root = current,
                                enabled = !busy,
                                selectionMode = analyzerSelecting,
                                selected = entry.location.key in analyzerSelected,
                                onClick = { handleAnalyzerRowClick(entry) },
                                onLongClick = { startAnalyzerSelect(entry) },
                                onInspect = { openInspector(entry) }
                            )
                        }
                    }
                }

                "duplicates" -> {
                    val groups = duplicateGroups
                    item(key = "dup-header") {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("大小 + SHA-256 精确查重", fontSize = 11.5.sp, color = palette.textSecondary, modifier = Modifier.weight(1f))
                            if (!groups.isNullOrEmpty()) {
                                FilledTonalButton(
                                    onClick = {
                                        val redundant = selectRedundantDuplicateKeys(groups)
                                        analyzerSelecting = redundant.isNotEmpty()
                                        analyzerSelected = redundant
                                    },
                                    enabled = !busy,
                                    modifier = Modifier.height(30.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) { Text("一键选冗余", fontSize = 11.sp) }
                            }
                            OutlinedButton(
                                onClick = {
                                    analyzerSelecting = false; analyzerSelected = emptySet()
                                    duplicateGroups = null
                                    onRunTask(false) {
                                        duplicateGroups = currentTaskIO {
                                            manager.findDuplicateFiles(current, force = true) {
                                                currentTaskProgress("查重 ${it.visited} 项：${it.current}")
                                            }
                                        }
                                        if (duplicateGroups?.isEmpty() == true) onSetNotice("未发现完全相同的重复文件")
                                    }
                                },
                                enabled = !busy,
                                modifier = Modifier.height(30.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                            ) { Text("重新查重", fontSize = 11.sp) }
                        }
                    }
                    if (groups == null && !busy) {
                        item(key = "dup-idle") {
                            Text("点击「重新查重」开始检测重复文件。", fontSize = 12.sp, color = palette.textSecondary)
                        }
                    } else if (groups != null && groups.isEmpty()) {
                        item(key = "dup-clean") {
                            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                                Text("很干净！未发现内容相同的重复冗余文件。", Modifier.padding(14.dp), fontSize = 12.sp, color = palette.textSecondary)
                            }
                        }
                    } else if (groups != null) {
                        val totalWasted = groups.sumOf { it.wastedBytes }
                        item(key = "dup-summary") {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = palette.accent.copy(alpha = 0.12f),
                                border = BorderStroke(0.8.dp, palette.accent.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "发现 ${groups.size} 组重复 · 可释放 ${fileBytes(totalWasted)}",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        color = palette.accent,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        onClick = {
                                            analyzerSelecting = !analyzerSelecting
                                            if (!analyzerSelecting) analyzerSelected = emptySet()
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text(if (analyzerSelecting) "退出多选" else "多选", fontSize = 11.5.sp)
                                    }
                                }
                            }
                        }
                        items(groups.take(30), key = { "dup-group:${it.sha256}:${it.sizeBytes}" }) { group ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(0.8.dp, palette.cardBorder.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                    Text(
                                        "${group.entries.size} 份相同 · 单份 ${fileBytes(group.sizeBytes)} · SHA256 ${group.sha256.take(10)}…",
                                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = palette.textSecondary
                                    )
                                    HorizontalDivider(color = palette.cardBorder.copy(alpha = 0.3f))
                                    group.entries.forEachIndexed { idx, dup ->
                                        AnalyzerEntryRow(
                                            entry = dup,
                                            root = current,
                                            enabled = !busy,
                                            selectionMode = analyzerSelecting,
                                            selected = dup.location.key in analyzerSelected,
                                            badge = if (idx == 0) "保留首项" else "冗余副本",
                                            embeddedInCard = true,
                                            onClick = { handleAnalyzerRowClick(dup) },
                                            onLongClick = { startAnalyzerSelect(dup) },
                                            onInspect = { openInspector(dup) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                "apk" -> {
                    if (sortedApkEntries.isEmpty()) {
                        item(key = "apk-empty") {
                            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                                Text("当前目录下未发现 .apk 安装包文件。", Modifier.padding(14.dp), fontSize = 12.sp, color = palette.textSecondary)
                            }
                        }
                    } else {
                        item(key = "apk-toolbar") {
                            AnalyzerFileListToolbar(
                                title = "安装包 (${sortedApkEntries.size} 项 · 占用 ${fileBytes(sortedApkEntries.sumOf { it.size ?: 0L })})",
                                trailingHint = "点击可外部安装、查看哈希或删除",
                                selecting = analyzerSelecting,
                                currentSort = state.sort,
                                palette = palette,
                                onToggleSelect = {
                                    analyzerSelecting = !analyzerSelecting
                                    if (!analyzerSelecting) analyzerSelected = emptySet()
                                },
                                onSortChange = { state.sort = it }
                            )
                        }
                        items(sortedApkEntries, key = { "apk-row:${it.location.key}" }) { entry ->
                            AnalyzerEntryRow(
                                entry = entry,
                                root = current,
                                enabled = !busy,
                                selectionMode = analyzerSelecting,
                                selected = entry.location.key in analyzerSelected,
                                onClick = { handleAnalyzerRowClick(entry) },
                                onLongClick = { startAnalyzerSelect(entry) },
                                onInspect = { openInspector(entry) }
                            )
                        }
                    }
                }

                "empty" -> {
                    val emptyDirs = statistics?.emptyDirectories.orEmpty()
                    if (emptyDirs.isEmpty()) {
                        item(key = "empty-none") {
                            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                                Text("当前目录下没有空文件夹。", Modifier.padding(14.dp), fontSize = 12.sp, color = palette.textSecondary)
                            }
                        }
                    } else {
                        item(key = "empty-header") {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(0.8.dp, palette.cardBorder.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("空文件夹 (${emptyDirs.size} 个)", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                    if (writable) {
                                        FilledTonalButton(
                                            onClick = { state.cleanAllEmptyConfirm = true },
                                            enabled = !busy,
                                            modifier = Modifier.height(28.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                        ) {
                                            Icon(Icons.Delete, null, Modifier.size(13.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("一键清理全部", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                        items(emptyDirs, key = { "empty-dir:${it.key}" }) { dirLoc ->
                            val dirDisplayEntry = remember(dirLoc) {
                                BrowserEntry(dirLoc, dirLoc.title, true, "vnd.android.document/directory", null, null)
                            }
                            val relParent = remember(current.key, dirLoc.key) {
                                relativeAnalyzerParentLabel(current, dirDisplayEntry)
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(0.8.dp, palette.cardBorder.copy(alpha = 0.38f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !busy) {
                                            onNavigateToDirectory(manager.resolveParentStack(stack, dirLoc))
                                        }
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Folder, null, Modifier.size(18.dp), tint = Color(0xFFE0A82E))
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(dirLoc.title, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("位于：$relParent", fontSize = 10.sp, color = palette.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    TextButton(
                                        onClick = { onNavigateToDirectory(manager.resolveParentStack(stack, dirLoc)) },
                                        enabled = !busy,
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) { Text("进入", fontSize = 11.sp) }
                                    if (writable) {
                                        IconButton(
                                            onClick = {
                                                onRunTask(true) {
                                                    currentTaskIO { manager.deleteEmptyDirectoryKeepingAnalyzerCache(dirLoc) }
                                                    statistics = statistics?.withoutDeleted(emptyList(), listOf(dirLoc))
                                                    onSetNotice("已清理空目录：${dirLoc.title}")
                                                }
                                            },
                                            enabled = !busy,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Delete, "删除空目录 ${dirLoc.title}", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                "search" -> {
                    item(key = "search-controls") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { analyzer.updateQuery(it) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("名称关键词（可留空仅按类型查找）", fontSize = 11.5.sp) },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    if (!busy) {
                                        onFinishInput()
                                        results = null
                                        onRunTask(false) {
                                            val searchOwner = analyzer.searchOwner()
                                            val found = currentTaskIO {
                                                manager.search(current, searchOwner.query, searchOwner.category) {
                                                    currentTaskProgress("查找 ${it.visited} 项：${it.current}")
                                                }
                                            }
                                            analyzer.publishSearch(searchOwner, found)
                                        }
                                    }
                                })
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box {
                                    OutlinedButton(onClick = { state.categoryMenu = true }, modifier = Modifier.height(34.dp)) {
                                        Text("类型：${category.label}", fontSize = 11.5.sp)
                                    }
                                    DropdownMenu(state.categoryMenu, { state.categoryMenu = false }) {
                                        com.example.globalminusone.ui.Android12WindowKeyGuard()

                                        FileCategory.entries.forEach { type ->
                                            DropdownMenuItem({ Text(type.label) }, { analyzer.updateCategory(type); state.categoryMenu = false })
                                        }
                                    }
                                }
                                FilledTonalButton(
                                    onClick = {
                                        onFinishInput()
                                        analyzerSelecting = false; analyzerSelected = emptySet()
                                        results = null
                                        onRunTask(false) {
                                            val searchOwner = analyzer.searchOwner()
                                            val found = currentTaskIO {
                                                manager.search(current, searchOwner.query, searchOwner.category) {
                                                    currentTaskProgress("查找 ${it.visited} 项：${it.current}")
                                                }
                                            }
                                            analyzer.publishSearch(searchOwner, found)
                                        }
                                    },
                                    enabled = !busy,
                                    modifier = Modifier.height(34.dp)
                                ) { Text("开始递归搜索", fontSize = 11.5.sp) }
                                if (!results?.entries.isNullOrEmpty()) {
                                    Spacer(Modifier.weight(1f))
                                    TextButton(
                                        onClick = {
                                            analyzerSelecting = !analyzerSelecting
                                            if (!analyzerSelecting) analyzerSelected = emptySet()
                                        },
                                        modifier = Modifier.height(32.dp)
                                    ) { Text(if (analyzerSelecting) "退出多选" else "多选", fontSize = 11.5.sp) }
                                }
                            }
                        }
                    }
                    results?.let { found ->
                        item(key = "search-summary") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${found.entries.size} 项${if (found.limited) "（还有更多）" else "（遍历完成）"} · 跳过受限目录 ${found.skippedDirectories}",
                                    fontSize = 11.5.sp,
                                    color = palette.textSecondary,
                                    modifier = Modifier.weight(1f)
                                )
                                found.continuation?.let { cursor ->
                                    FileToolButton(onClick = {
                                        onRunTask(false) {
                                            val searchOwner = analyzer.searchOwner()
                                            val nextFound = currentTaskIO {
                                                manager.search(current, searchOwner.query, searchOwner.category, cursor) {
                                                    currentTaskProgress("继续查找 ${it.visited} 项：${it.current}")
                                                }
                                            }
                                            analyzer.publishSearch(searchOwner, nextFound)
                                        }
                                    }, enabled = !busy) { Text("加载下一页") }
                                }
                            }
                        }
                        items(found.entries, key = { "search-row:${it.location.key}" }) { entry ->
                            AnalyzerEntryRow(
                                entry = entry,
                                root = current,
                                enabled = !busy,
                                selectionMode = analyzerSelecting,
                                selected = entry.location.key in analyzerSelected,
                                onClick = {
                                    if (analyzerSelecting) toggleAnalyzerSelect(entry)
                                    else if (entry.isDirectory) onNavigateToDirectory(manager.resolveParentStack(stack, entry.location))
                                    else openInspector(entry)
                                },
                                onLongClick = { if (!entry.isDirectory) startAnalyzerSelect(entry) },
                                onInspect = {
                                    if (entry.isDirectory) onNavigateToDirectory(manager.resolveParentStack(stack, entry.location))
                                    else openInspector(entry)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Sticky Analyzer Multi-Select Batch Action Bar
        if (analyzerSelecting) {
            val visibleFileEntries = analyzerVisibleEntries.filter { !it.isDirectory }
            val allVisibleSelected = visibleFileEntries.isNotEmpty() &&
                visibleFileEntries.all { it.location.key in analyzerSelected }
            val selectedBytes = chosenAnalyzerEntries.sumOf { it.size ?: 0L }
            Surface(color = palette.cardBackground, tonalElevation = 3.dp) {
                Column {
                    HorizontalDivider(color = palette.cardBorder.copy(alpha = 0.45f))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(palette.accent.copy(alpha = 0.08f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "已选 ${chosenAnalyzerEntries.size} 项 · 共 ${fileBytes(selectedBytes)}",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.accent,
                            modifier = Modifier.weight(1f)
                        )
                        if (state.section == "duplicates" && !duplicateGroups.isNullOrEmpty()) {
                            TextButton(
                                onClick = { analyzerSelected = selectRedundantDuplicateKeys(duplicateGroups.orEmpty()) },
                                enabled = !busy,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.height(26.dp)
                            ) { Text("智能选冗余", fontSize = 11.sp) }
                        }
                    }
                    FileWorkspaceBottomBar(listOf(
                        FileWorkspaceAction(
                            title = if (allVisibleSelected) "取消全选" else "全选本页",
                            icon = Icons.Check,
                            enabled = !busy && visibleFileEntries.isNotEmpty()
                        ) {
                            val pageKeys = visibleFileEntries.map { it.location.key }.toSet()
                            analyzerSelected = if (allVisibleSelected) analyzerSelected - pageKeys else analyzerSelected + pageKeys
                        },
                        FileWorkspaceAction(
                            title = "收藏所选",
                            icon = Icons.Checklist,
                            enabled = !busy && chosenAnalyzerEntries.isNotEmpty()
                        ) {
                            onFavorite(chosenAnalyzerEntries)
                        },
                        FileWorkspaceAction(
                            title = "批量删除",
                            icon = Icons.Delete,
                            destructive = true,
                            enabled = !busy && writable && chosenAnalyzerEntries.isNotEmpty()
                        ) {
                            state.batchDeleteConfirm = true
                        },
                        FileWorkspaceAction(
                            title = "退出多选",
                            icon = Icons.Close,
                            enabled = !busy
                        ) {
                            analyzerSelecting = false
                            analyzerSelected = emptySet()
                        }
                    ))
                }
            }
        }
    }
}
