package com.example.globalminusone.ui.panel

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

internal fun fileBytes(bytes: Long?): String {
    if (bytes == null) return "未知"
    var value = bytes.toDouble(); val units = arrayOf("B", "KB", "MB", "GB", "TB"); var index = 0
    while (value >= 1024 && index < units.lastIndex) { value /= 1024; index++ }
    return String.format(Locale.ROOT, "%.1f %s", value, units[index])
}

internal fun storageCapacityText(volume: StorageVolumeInfo): String {
    val total = volume.total
    val free = volume.available
    val used = if (total != null && free != null && free in 0..total) total - free else null
    val ratio = if (total != null && total > 0 && used != null) String.format(Locale.ROOT, "%.1f%%", used.toDouble() * 100 / total) else "未知"
    return "总容量 ${fileBytes(total)} · 可用 ${fileBytes(free)}\n已用/预留 ${fileBytes(used)}（$ratio，含系统及不可访问空间）"
}

internal data class TransferClipboard(val entries: List<BrowserEntry>, val parent: BrowserLocation, val move: Boolean)

/** All tools stay in the existing opaque file window, including confirmations and destination picking. */
@Composable
internal fun FileManagementPanel(
    repository: MinusOneRepository,
    browser: FileBrowserIo,
    initialLocation: BrowserLocation?,
    initialStack: List<BrowserLocation>? = null,
    initialTab: String = "管理",
    initialEntry: BrowserEntry? = null,
    initialEntries: List<BrowserEntry> = emptyList(),
    initialAction: String? = null,
    initialAnalyzerSection: String = "overview",
    initialDrillCategory: FileCategory? = null,
    onAnalyzerStateChanged: (String, FileCategory?) -> Unit = { _, _ -> },
    clipboardState: MutableState<TransferClipboard?>,
    onBackHandler: ((() -> Unit)?) -> Unit = {},
    onWindowCloseHandler: (FileWindowCloseRequest?) -> Unit = {},
    onEditingChanged: (Boolean) -> Unit = {},
    onClose: (List<BrowserLocation>) -> Unit,
    onOpen: (BrowserEntry, List<BrowserLocation>) -> Unit,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    fun finishInput() {
        focusManager.clearFocus(force = true)
        keyboard?.hide()
    }
    val manager = remember(context, browser) { FileManagerIo(context, browser) }
    val expanded by repository.isPanelExpanded.collectAsState()
    val config = observeFileBrowserConfig(repository)
    val scope = rememberCoroutineScope()
    val latestChanged by rememberUpdatedState(onChanged)
    val tasks = remember(scope, manager) { FileManagementTaskSession(scope) { latestChanged() } }
    val analyzer = tasks.analyzer
    var tab by remember { mutableStateOf(initialTab) }
    var stack by remember { mutableStateOf(initialStack ?: initialLocation?.let { listOf(it) } ?: emptyList()) }
    val current = stack.lastOrNull()
    val directory = remember(browser) { FileBrowserDirectoryState() }
    var rows by directory.entries
    var rowsLoading by directory.loading
    var rowsLocationKey by directory.locationKey
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selecting by remember { mutableStateOf(false) }
    var reload by tasks.reload
    val busy by tasks.busy
    var notice by tasks.notice
    var noticeExpanded by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var actionsMenuOpen by remember { mutableStateOf(false) }
    val progress by tasks.progress
    var form by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var properties by remember { mutableStateOf<FileProperties?>(null) }
    var checksums by remember { mutableStateOf<FileChecksums?>(null) }
    var hashCompareInput by remember { mutableStateOf("") }
    var clipboard by clipboardState
    val analyzerUi = remember(tasks, initialAnalyzerSection, initialDrillCategory) {
        FileManagementAnalyzerUiState(tasks, initialAnalyzerSection, initialDrillCategory, current?.key)
    }
    var volumes by remember { mutableStateOf<List<StorageVolumeInfo>>(emptyList()) }
    var pendingInitial by remember { mutableStateOf(initialAction != null) }
    var textEntry by remember { mutableStateOf<BrowserEntry?>(null) }
    var newText by remember { mutableStateOf(false) }
    var pathInputOpen by remember { mutableStateOf(false) }
    var pathInput by remember { mutableStateOf("") }
    var pathInputError by remember { mutableStateOf<String?>(null) }
    var navigationMenu by remember { mutableStateOf(false) }
    var clearRecentRequested by remember { mutableStateOf(false) }
    var editorBack by remember { mutableStateOf<(() -> Unit)?>(null) }
    var editorWindowClose by remember { mutableStateOf<FileWindowCloseRequest?>(null) }

    SideEffect {
        onEditingChanged(newText || textEntry != null || busy)
        onAnalyzerStateChanged(analyzerUi.section, analyzerUi.drillCategory)
    }
    val requestWindowClose by rememberUpdatedState<FileWindowCloseRequest> { allowed, cancelled ->
        if (!busy) {
            val editor = editorWindowClose
            if (newText || textEntry != null) editor?.invoke(allowed, cancelled) ?: cancelled() else { finishInput(); allowed() }
        } else cancelled()
    }
    DisposableEffect(Unit) {
        onWindowCloseHandler { allowed, cancelled -> requestWindowClose(allowed, cancelled) }
        onDispose { onWindowCloseHandler(null); onEditingChanged(false) }
    }
    val requestBack by rememberUpdatedState<() -> Unit> {
        val inspector = analyzerUi.inspector
        when {
            busy -> Unit
            editorBack != null -> editorBack?.invoke()
            clearRecentRequested -> clearRecentRequested = false
            pathInputOpen -> { finishInput(); pathInputOpen = false }
            actionsMenuOpen -> actionsMenuOpen = false
            navigationMenu -> navigationMenu = false
            analyzerUi.categoryMenu -> analyzerUi.categoryMenu = false
            analyzerUi.batchDeleteConfirm -> analyzerUi.batchDeleteConfirm = false
            analyzerUi.cleanAllEmptyConfirm -> analyzerUi.cleanAllEmptyConfirm = false
            inspector.renameMode.value || inspector.deleteConfirm.value -> {
                finishInput(); inspector.renameMode.value = false; inspector.deleteConfirm.value = false
            }
            inspector.entry.value != null -> {
                finishInput(); inspector.entry.value = null; inspector.properties.value = null; inspector.checksums.value = null
            }
            form != null || properties != null || checksums != null -> { finishInput(); form = null; properties = null; checksums = null }
            tab == "分析查找" && analyzer.selecting.value -> { analyzer.selecting.value = false; analyzer.selected.value = emptySet() }
            tab == "分析查找" && analyzerUi.section == "overview" && analyzerUi.drillCategory != null -> {
                finishInput(); analyzerUi.drillCategory = null; analyzerUi.categoryFilterQuery = ""
            }
            selecting -> { selecting = false; selected = emptySet() }
            tab != "管理" -> {
                finishInput(); tasks.cancel()
                if (initialTab == "分析查找" && tab == "分析查找") onClose(stack) else tab = "管理"
            }
            else -> { finishInput(); tasks.cancel(); onClose(stack) }
        }
    }
    DisposableEffect(Unit) {
        onBackHandler { requestBack() }
        onDispose { onBackHandler(null) }
    }

    val chosen = rows.filter { it.location.key in selected }
    // UI hint is sampled once per navigation/refresh; every mutation rechecks actual grants.
    val writable by produceState(false, current?.key, reload, expanded) {
        value = current != null && expanded && withContext(Dispatchers.IO) { manager.canWrite(current) }
    }

    fun runTask(mutates: Boolean = false, block: suspend FileManagementTaskSession.Task.() -> Unit) {
        tasks.run(mutates, expectedRootKey = current?.key, block = block)
    }
    fun rememberVisit(entry: BrowserEntry) {
        val saved = entry.location.bookmark(entry.isDirectory, entry.mimeType)
        repository.updateFileBrowserConfig { it.copy(recent = (listOf(saved) + it.recent.filterNot { b -> b.location().key == entry.location.key }).take(30)) }
    }
    fun openEntry(entry: BrowserEntry) {
        if (busy) return
        try {
            manager.checkRead(entry.location)
            rememberVisit(entry)
            if (entry.isDirectory) {
                stack = if (tab == "管理") stack + entry.location else listOf(entry.location)
                selected = emptySet()
            } else {
                onOpen(entry, stack)
            }
        } catch (e: Exception) {
            notice = e.message ?: "访问失败，请重新授权"
        }
    }
    fun favorite(entries: List<BrowserEntry>) {
        repository.updateFileBrowserConfig { previous ->
            val existing = previous.favorites.associateBy { it.location().key }.toMutableMap()
            entries.forEach { entry -> existing[entry.location.key] = entry.location.bookmark(entry.isDirectory, entry.mimeType) }
            previous.copy(favorites = existing.values.toList())
        }
        notice = "已收藏 ${entries.size} 项"
    }
    fun changeTab(value: String) {
        if (busy) return
        finishInput()
        tab = value; form = null; properties = null; selected = emptySet(); selecting = false
    }
    fun toggleEntry(entry: BrowserEntry) {
        if (busy) return
        selected = if (entry.location.key in selected) selected - entry.location.key else selected + entry.location.key
    }
    fun selectEntry(entry: BrowserEntry) {
        if (busy) return
        selecting = true; selected = selected + entry.location.key
    }
    fun submitPath() {
        if (busy) return
        val plan = try { FileWorkspacePathPolicy.plan(pathInput, stack) }
        catch (e: Exception) { pathInputError = e.message ?: "路径无效"; return }
        finishInput(); pathInputOpen = false
        runTask {
            stack = currentTaskIO { resolveFileWorkspacePath(plan) { browser.list(it) } }
            selected = emptySet(); selecting = false; tab = "管理"
        }
    }
    fun navigateFromAnalyzer(targetStack: List<BrowserLocation>) {
        if (initialTab == "分析查找") onClose(targetStack)
        else { stack = targetStack; tab = "管理" }
    }
    fun sheetAction(title: String, icon: ImageVector, enabled: Boolean = true, destructive: Boolean = false, action: () -> Unit) =
        FileWorkspaceAction(title, icon, enabled && !busy, destructive) { actionsMenuOpen = false; action() }
    val toolActions = listOf(
        sheetAction("新建文件夹", Icons.Folder, writable) { input = ""; form = "new" },
        sheetAction("新建文本", Icons.EditNote, writable) { newText = true },
        sheetAction(if (selecting) "退出多选" else "多选", Icons.Checklist, current != null) {
            selecting = !selecting; selected = emptySet()
        },
        sheetAction(if (rows.isNotEmpty() && selected.size == rows.size) "取消全选" else "全选", Icons.Check, rows.isNotEmpty()) {
            selecting = true; selected = if (selected.size == rows.size) emptySet() else rows.map { it.location.key }.toSet()
        },
        sheetAction("重命名", Icons.Edit, chosen.size == 1 && writable) { input = chosen.single().name; form = "rename" },
        sheetAction("编辑文本", Icons.EditNote, chosen.size == 1 && supportsTextEditing(chosen.single())) { textEntry = chosen.single() },
        sheetAction("删除所选", Icons.Delete, chosen.isNotEmpty() && writable, destructive = true) { form = "delete" },
        sheetAction("校验和 (MD5/SHA256)", Icons.Fingerprint, chosen.size == 1 && !chosen.single().isDirectory) {
            val target = chosen.single()
            runTask {
                properties = currentTaskIO { manager.properties(target) }
                checksums = currentTaskIO { manager.calculateChecksums(target) { bytes -> currentTaskProgress("计算哈希 ${fileBytes(bytes)}") } }
            }
        },
        sheetAction("属性", Icons.Settings, chosen.size == 1) { runTask { checksums = null; properties = currentTaskIO { manager.properties(chosen.single()) } } },
        sheetAction("收藏所选", Icons.Checklist, chosen.isNotEmpty()) { favorite(chosen) },
        sheetAction("收藏当前目录", Icons.Add, current != null) {
            current?.let { favorite(listOf(BrowserEntry(it, it.title, true, "vnd.android.document/directory", null, null))) }
        },
        sheetAction("分析与查找", Icons.Search, current != null) { changeTab("分析查找") },
        sheetAction("最近与收藏", Icons.History) { changeTab("最近收藏") },
        sheetAction("刷新", Icons.Refresh, current != null) { reload++ },
        sheetAction("存储位置", Icons.Storage) { stack = emptyList(); selected = emptySet(); selecting = false; tab = "管理" },
        sheetAction("操作说明", Icons.Settings) { showHelp = !showHelp }
    )
    DisposableEffect(tasks, current?.key, expanded) {
        tasks.bindRoot(current?.key, expanded)
        onDispose { directory.invalidate(); tasks.bindRoot(null, false) }
    }
    LaunchedEffect(current?.key) {
        if (analyzerUi.trackedRootKey != current?.key) {
            analyzerUi.trackedRootKey = current?.key
            analyzer.statistics.value = current?.let { withContext(Dispatchers.IO) { runCatching { manager.cachedScan(it) }.getOrNull() } }
            analyzer.duplicateGroups.value = current?.let { withContext(Dispatchers.IO) { runCatching { manager.cachedDuplicates(it) }.getOrNull() } }
            analyzer.results.value = null
            analyzerUi.drillCategory = null
            analyzerUi.categoryFilterQuery = ""
            analyzer.selecting.value = false
            analyzer.selected.value = emptySet()
            analyzerUi.inspector.entry.value = null
            analyzerUi.inspector.properties.value = null
            analyzerUi.inspector.checksums.value = null
        }
    }
    LaunchedEffect(current?.key, reload, expanded) {
        suspend fun <T> readDirectory(block: suspend () -> T): T {
            val result = block()
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            return result
        }
        val directoryChanged = rowsLocationKey != current?.key
        if (directoryChanged) {
            properties = null; checksums = null; form = null
            rows = emptyList(); selected = emptySet(); selecting = false
            rowsLocationKey = current?.key
        }
        rowsLoading = expanded && current != null
        if (expanded && current != null) {
            try {
                withContext(Dispatchers.IO) {
                    directory.load(current, cached = browser::cachedDirectory, fresh = browser::isDirectoryCacheFresh,
                        list = browser::list, invalidateCache = browser::invalidateDirectory, filterCached = { it })
                }
                directory.error.value?.let { notice = it; return@LaunchedEffect }
                if (pendingInitial) {
                    pendingInitial = false
                    val targetKeys = (initialEntries + listOfNotNull(initialEntry)).map { it.location.key }.toSet()
                    val targets = rows.filter { it.location.key in targetKeys }
                    val target = targets.singleOrNull()
                    selected = targets.map { it.location.key }.toSet()
                    selecting = selected.isNotEmpty()
                    when (initialAction) {
                        "new" -> { input = ""; form = "new" }
                        "newtext" -> newText = true
                        "rename" -> if (target != null && writable) { input = target.name; form = "rename" }
                        "delete" -> if (targets.isNotEmpty() && writable) form = "delete"
                        "properties" -> if (target != null) properties = readDirectory { manager.properties(target) }
                        "checksum" -> if (target != null && !target.isDirectory) {
                            properties = readDirectory { manager.properties(target) }
                            checksums = readDirectory { manager.calculateChecksums(target) }
                        }
                        "edit" -> if (target != null) textEntry = target
                        "copy", "move" -> if (target != null && (initialAction == "copy" || writable)) {
                            clipboard = TransferClipboard(listOf(target), current, initialAction == "move")
                            selected = emptySet(); selecting = false
                        }
                    }
                    if (!writable && initialAction in setOf("new", "newtext", "rename", "delete", "move")) notice = "此位置仅有只读权限；请返回存储入口添加可管理目录，原文件未修改"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                rows = emptyList(); notice = e.message ?: "无法读取目录"
            }
        }
    }
    LaunchedEffect(Unit) { volumes = manager.volumes() }

    DisposableEffect(Unit) { onDispose { tasks.cancel(); finishInput() } }

    val palette = opaqueFilePalette(LocalPanelPalette.current)
    Box(Modifier.fillMaxSize().background(palette.cardBackground)) {
        Column(Modifier.fillMaxSize()) {
            if (!newText && textEntry == null) {
                when {
                    form != null || properties != null -> Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { requestBack() }, enabled = !busy) { Icon(Icons.ArrowBack, "返回文件列表") }
                        Text(if (properties != null) "文件属性" else "文件操作", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    }
                    tab == "管理" && selecting -> Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).background(palette.accent.copy(alpha = .09f)), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selecting = false; selected = emptySet() }, enabled = !busy) { Icon(Icons.Close, "退出多选") }
                        Text("已选 ${selected.size} 项", Modifier.weight(1f), fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        TextButton(onClick = { selected = if (selected.size == rows.size) emptySet() else rows.map { it.location.key }.toSet() }, enabled = !busy) {
                            Text(if (rows.isNotEmpty() && selected.size == rows.size) "取消全选" else "全选")
                        }
                    }
                    tab == "管理" -> FileWorkspacePathBar(stack, "存储位置",
                        if (current == null) "选择目录" else "${rows.size} 项 · ${if (writable) "可管理" else "只读"}",
                        onBack = { if (!busy) { if (stack.isEmpty()) onClose(stack) else stack = stack.dropLast(1) } },
                        onSegment = { if (!busy) stack = stack.take(it + 1) },
                        onInput = { if (!busy && current != null) { pathInput = fileWorkspacePath(current); pathInputError = null; pathInputOpen = true } })
                    else -> Row(Modifier.fillMaxWidth().heightIn(min = 46.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { requestBack() }) { Icon(Icons.ArrowBack, "返回") }
                        Box(Modifier.weight(1f)) {
                            val headerLabel = if (tab == "分析查找") {
                                analyzerUi.drillCategory?.takeIf { analyzerUi.section == "overview" }?.let { "存储分析 · ${it.label} ▾" }
                                    ?: "存储分析与清理 ▾"
                            } else "最近与收藏 ▾"
                            TextButton(onClick = { navigationMenu = true }, enabled = !busy) { Text(headerLabel, fontWeight = FontWeight.SemiBold) }
                            DropdownMenu(navigationMenu, { navigationMenu = false }) {
                                com.example.globalminusone.ui.Android12WindowKeyGuard()

                                listOf("分析查找", "最近收藏").forEach { destination ->
                                    DropdownMenuItem(text = { Text(if (destination == "分析查找") "存储分析与清理" else "最近与收藏") },
                                        onClick = { navigationMenu = false; changeTab(destination) })
                                }
                            }
                        }
                        TextButton(onClick = { tasks.cancel(); onClose(stack) }) { Text("返回目录") }
                    }
                }
                HorizontalDivider(color = palette.cardBorder.copy(alpha = .35f))
            }
            FileManagementTaskStatus(busy, progress, notice,
                showNotice = tab != "管理" || current == null || form != null || properties != null,
                noticeExpanded = noticeExpanded, onCancel = { tasks.cancel() },
                onToggleNotice = { noticeExpanded = !noticeExpanded })
            if (newText || textEntry != null) {
                Box(Modifier.weight(1f)) {
                    FileTextEditor(manager, textEntry, current,
                        onClose = { newText = false; textEntry = null },
                        onSaved = { newText = false; textEntry = null; notice = "已保存 UTF-8 文本"; reload++; onChanged() },
                        installBack = { editorBack = it }, installWindowClose = { editorWindowClose = it })
                }
            } else if (form != null || properties != null) {
                FileManagementFormAndPropertiesPane(
                    properties = properties,
                    checksums = checksums,
                    hashCompareInput = hashCompareInput,
                    onHashCompareChange = { hashCompareInput = it },
                    chosen = chosen,
                    initialEntry = initialEntry,
                    form = form,
                    input = input,
                    onInputChange = { input = it },
                    current = current,
                    busy = busy,
                    palette = palette,
                    manager = manager,
                    repository = repository,
                    onFinishInput = ::finishInput,
                    onClearForm = { form = null },
                    onClearProperties = { properties = null; checksums = null },
                    onSetChecksums = { checksums = it },
                    onSetNotice = { notice = it },
                    onRunTask = ::runTask
                )
            } else if (tab == "最近收藏") {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(8.dp)) {
                    item { Text("收藏", fontWeight = FontWeight.Bold) }
                    if (config.favorites.isEmpty()) item { Text("在管理中选择文件或目录后可收藏", fontSize = 12.sp) }
                    items(config.favorites, key = { "fav:${it.location().key}" }) { b ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                ManagementEntryRow(b.entry(), enabled = !busy, onClick = { openEntry(b.entry()); tab = "管理" })
                            }
                            IconButton(onClick = { repository.updateFileBrowserConfig { it.copy(favorites = it.favorites.filterNot { saved -> saved == b }) } }, enabled = !busy) {
                                Icon(Icons.Close, "移除收藏 ${b.title}", Modifier.size(18.dp))
                            }
                        }
                    }
                    item { Text("最近访问（最多30项）", fontWeight = FontWeight.Bold) }
                    items(config.recent, key = { "recent:${it.location().key}" }) { b ->
                        ManagementEntryRow(b.entry(), enabled = !busy, onClick = { openEntry(b.entry()); tab = "管理" })
                    }
                    item { FileToolButton(onClick = { clearRecentRequested = true }, enabled = !busy && config.recent.isNotEmpty()) { Text("清空最近访问") } }
                }
            } else if (current == null) {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(8.dp)) {
                    items(volumes) { volume -> Text("${volume.title}${if (volume.removable) "（SD/USB可移除存储）" else ""}\n${storageCapacityText(volume)}", Modifier.padding(6.dp), fontSize = 12.sp) }
                    item {
                        FileToolButton(onClick = {
                            if (browser.hasAllFilesAccess()) stack = listOf(browser.sharedLocation())
                            else notice = "共享存储未授权。返回浏览页点明确授权入口；也可只授权下方目录"
                        }) { Text("手机共享存储") }
                    }
                    items(config.roots, key = { it.uri }) { root ->
                        Text(root.title, Modifier.fillMaxWidth().clickable { if (!busy) stack = listOf(browser.rootLocation(root)) }.padding(12.dp))
                    }
                    item { Text("SD卡/USB请通过系统目录选择器添加根或所需子目录。系统禁止选择的根无法越权访问。", fontSize = 12.sp) }
                }
            } else if (tab == "分析查找") {
                FileManagementAnalyzerPane(
                    state = analyzerUi,
                    current = current,
                    stack = stack,
                    expanded = expanded,
                    reload = reload,
                    busy = busy,
                    writable = writable,
                    palette = palette,
                    manager = manager,
                    onFinishInput = ::finishInput,
                    onSetNotice = { notice = it },
                    onRunTask = ::runTask,
                    onFavorite = ::favorite,
                    onOpenEntry = ::openEntry,
                    onNavigateToDirectory = ::navigateFromAnalyzer
                )
            } else {
                clipboard?.let { clip ->
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${if (clip.move) "移动" else "复制"} ${clip.entries.size} 项 → ${current.title}", Modifier.weight(1f),
                            fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = {
                            runTask(mutates = true) {
                                var complete = 0; val failures = mutableListOf<String>()
                                for (entry in clip.entries) {
                                    try { currentTaskIO { manager.transfer(entry, clip.parent, current, clip.move) { currentTaskProgress("${if (clip.move) "移动" else "复制"}：$it") } }; complete++ }
                                    catch (e: CancellationException) { throw e }
                                    catch (e: Exception) { failures += "${entry.name}：${e.message}" }
                                }
                                notice = "完成 $complete/${clip.entries.size} 项" + if (failures.isEmpty()) "" else "\n" + failures.joinToString("\n")
                                clipboard = null
                            }
                        }, enabled = !busy && writable) { Text("粘贴") }
                        IconButton(onClick = { clipboard = null }, enabled = !busy) { Icon(Icons.Close, "取消待粘贴") }
                    }
                    HorizontalDivider(color = palette.cardBorder.copy(alpha = .35f))
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 2.dp)) {
                    notice?.let { message ->
                        item {
                            Text(message, Modifier.fillMaxWidth().clickable { noticeExpanded = !noticeExpanded }.padding(8.dp),
                                maxLines = if (noticeExpanded) 6 else 2, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, color = palette.accent)
                        }
                    }
                    if (showHelp) item { Text("点击打开，长按多选。复制或移动后进入目标目录粘贴；同名不覆盖。只读目录不可修改。", Modifier.padding(12.dp), fontSize = 12.sp) }
                    if (rows.isEmpty()) item { Text(if (rowsLoading) "正在读取目录…" else "目录为空", Modifier.padding(16.dp), fontSize = 12.sp, color = palette.textSecondary) }
                    items(rows, key = { it.location.key }) { entry ->
                        ManagementEntryRow(entry, enabled = !busy, selectionMode = selecting,
                            selected = entry.location.key in selected,
                            onClick = { if (selecting) toggleEntry(entry) else openEntry(entry) },
                            onLongClick = { selectEntry(entry) },
                            onMenu = { selected = setOf(entry.location.key); selecting = true; actionsMenuOpen = true })
                    }
                }
                if (selecting) FileWorkspaceBottomBar(listOf(
                    FileWorkspaceAction("复制", Icons.ContentCopy, !busy && chosen.isNotEmpty()) {
                        clipboard = TransferClipboard(chosen.toList(), current, false); selected = emptySet(); selecting = false
                    },
                    FileWorkspaceAction("移动", Icons.SwapHoriz, !busy && chosen.isNotEmpty() && writable) {
                        clipboard = TransferClipboard(chosen.toList(), current, true); selected = emptySet(); selecting = false
                    },
                    FileWorkspaceAction("删除", Icons.Delete, !busy && chosen.isNotEmpty() && writable, destructive = true) { form = "delete" },
                    FileWorkspaceAction("更多", Icons.MoreVert, !busy) { actionsMenuOpen = true }
                )) else FileWorkspaceBottomBar(listOf(
                    FileWorkspaceAction("新建目录", Icons.Add, !busy && writable) { input = ""; form = "new" },
                    FileWorkspaceAction("多选", Icons.Checklist, !busy && rows.isNotEmpty()) { selecting = true },
                    FileWorkspaceAction("工具", Icons.MoreVert, !busy) { actionsMenuOpen = true },
                    FileWorkspaceAction("返回浏览", Icons.ArrowBack, !busy) { onClose(stack) }
                ))
            }
        }
        if (actionsMenuOpen) FileWorkspaceActionSheet(
            if (selecting) "所选 ${chosen.size} 项" else "目录工具", toolActions.filter { it.enabled },
            onDismiss = { actionsMenuOpen = false })

        if (current != null) {
            FileManagementAnalyzerInspectorOverlay(
                state = analyzerUi,
                current = current,
                stack = stack,
                reload = reload,
                expanded = expanded,
                busy = busy,
                scope = scope,
                browser = browser,
                manager = manager,
                repository = repository,
                onFinishInput = ::finishInput,
                onSetNotice = { notice = it },
                onRunTask = ::runTask,
                onOpenEntry = ::openEntry,
                onNavigateToDirectory = ::navigateFromAnalyzer,
                onFavorite = ::favorite
            )
        }
    }

    if (current != null) {
        FileManagementAnalyzerConfirmDialogs(
            state = analyzerUi,
            busy = busy,
            manager = manager,
            onSetNotice = { notice = it },
            onRunTask = ::runTask
        )
    }
    if (clearRecentRequested) PanelAlertDialog(
        onDismissRequest = { clearRecentRequested = false },
        title = { Text("清空最近访问记录？") },
        text = { Text("将删除文件浏览器的 ${config.recent.size} 条最近访问记录，无法撤销。") },
        confirmButton = {
            TextButton(onClick = {
                repository.updateFileBrowserConfig { it.copy(recent = emptyList()) }
                clearRecentRequested = false
            }, enabled = !busy && config.recent.isNotEmpty()) { Text("确认清空", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { clearRecentRequested = false }) { Text("取消") } }
    )
    if (pathInputOpen) PanelAlertDialog(
        onDismissRequest = { finishInput(); pathInputOpen = false },
        title = { Text("输入路径") },
        text = {
            OutlinedTextField(
                pathInput, { pathInput = it; pathInputError = null }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("目录路径") }, isError = pathInputError != null,
                supportingText = { pathInputError?.let { Text(it) } }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submitPath() })
            )
        },
        confirmButton = { TextButton(onClick = { submitPath() }, enabled = !busy) { Text("前往") } },
        dismissButton = { TextButton(onClick = { finishInput(); pathInputOpen = false }, enabled = !busy) { Text("取消") } }
    )
}

@Composable
private fun ColumnScope.FileManagementFormAndPropertiesPane(
    properties: FileProperties?,
    checksums: FileChecksums?,
    hashCompareInput: String,
    onHashCompareChange: (String) -> Unit,
    chosen: List<BrowserEntry>,
    initialEntry: BrowserEntry?,
    form: String?,
    input: String,
    onInputChange: (String) -> Unit,
    current: BrowserLocation?,
    busy: Boolean,
    palette: PanelPalette,
    manager: FileManagerIo,
    repository: MinusOneRepository,
    onFinishInput: () -> Unit,
    onClearForm: () -> Unit,
    onClearProperties: () -> Unit,
    onSetChecksums: (FileChecksums?) -> Unit,
    onSetNotice: (String?) -> Unit,
    onRunTask: (mutates: Boolean, block: suspend FileManagementTaskSession.Task.() -> Unit) -> Unit
) {
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        properties?.let { p ->
            val context = LocalContext.current
            val clipManager = remember(context) { context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager }
            fun copyHash(label: String, value: String) {
                clipManager?.setPrimaryClip(android.content.ClipData.newPlainText(label, value))
                onSetNotice("已复制 $label")
            }
            Text(p.title, fontWeight = FontWeight.Bold)
            Text("类型：${p.type}\n大小：${fileBytes(p.size)}${if (p.type == "vnd.android.document/directory" || p.type == "目录") "（目录大小请使用分析）" else ""}\n修改时间：${p.modified?.let { DateFormat.getDateTimeInstance().format(Date(it)) } ?: "未知"}\n权限：${if (p.writable) "已授写入（具体操作由提供方决定）" else "只读"}\n位置：${p.location}", fontSize = 12.sp)
            val targetEntry = chosen.singleOrNull() ?: initialEntry
            if (targetEntry != null && !targetEntry.isDirectory && checksums == null) {
                FileToolButton(onClick = {
                    onRunTask(false) {
                        onSetChecksums(currentTaskIO { manager.calculateChecksums(targetEntry) { bytes -> currentTaskProgress("计算哈希 ${fileBytes(bytes)}") } })
                    }
                }, enabled = !busy) { Text("计算校验和 (MD5 / SHA-1 / SHA-256 / CRC32)") }
            }
            checksums?.let { c ->
                HorizontalDivider(color = palette.cardBorder.copy(alpha = .35f))
                Text("文件校验和（已扫描 ${fileBytes(c.byteCount)}）", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                listOf("MD5" to c.md5, "SHA-1" to c.sha1, "SHA-256" to c.sha256, "CRC32" to c.crc32).forEach { (algo, hex) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(algo, fontSize = 10.sp, color = palette.textSecondary, fontWeight = FontWeight.Medium)
                            Text(hex, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                        FileToolButton(onClick = { copyHash(algo, hex) }, height = 28.dp) { Text("复制", fontSize = 11.sp) }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = hashCompareInput,
                        onValueChange = onHashCompareChange,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        label = { Text("粘贴哈希值进行比对", fontSize = 11.sp) },
                        trailingIcon = {
                            TextButton(onClick = {
                                val clipText = clipManager?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
                                if (clipText.isNotEmpty()) onHashCompareChange(clipText)
                            }) { Text("粘贴", fontSize = 11.sp) }
                        }
                    )
                }
                if (hashCompareInput.isNotBlank()) {
                    val matched = c.matchedAlgorithm(hashCompareInput)
                    Text(
                        text = if (matched != null) "✓ 校验一致（匹配 $matched）" else "✗ 与当前文件哈希不匹配",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (matched != null) palette.accent else MaterialTheme.colorScheme.error
                    )
                }
            }
            FileToolButton(onClick = onClearProperties) { Text("关闭属性") }
        }
        form?.let { mode ->
            Text(when (mode) { "new" -> "新建文件夹"; "rename" -> "重命名 ${chosen.firstOrNull()?.name.orEmpty()}"; else -> "删除 ${chosen.size} 项？" }, fontWeight = FontWeight.Bold)
            if (mode == "delete") {
                Text("位置：${current?.title}\n目标（${chosen.size}项）：${chosen.joinToString("、") { it.name }}", fontSize = 12.sp)
                Text("包含目录内的所有内容，删除不可撤销。只处理以上已选项目。", fontSize = 12.sp)
            }
            val submitForm: () -> Unit = submit@{
                if (busy || (mode != "delete" && input.isBlank())) return@submit
                val directory = current ?: return@submit
                val work = chosen.toList(); val value = input
                onFinishInput(); onClearForm()
                onRunTask(true) {
                    if (mode == "new") {
                        currentTaskIO { manager.createDirectory(directory, value) }; onSetNotice("已创建文件夹")
                    } else if (mode == "rename") {
                        val old = work.single()
                        val renamed = currentTaskIO { manager.rename(old, directory, value) }
                        repository.updateFileBrowserConfig { previous ->
                            fun replace(items: List<FileBrowserBookmark>) = items.map { saved ->
                                if (saved.location().key == old.location.key) renamed.bookmark(old.isDirectory, old.mimeType) else saved
                            }
                            previous.copy(favorites = replace(previous.favorites), recent = replace(previous.recent))
                        }
                        onSetNotice("已重命名")
                    } else {
                        var complete = 0; val failures = mutableListOf<String>()
                        for (entry in work) {
                            try { currentTaskProgress("删除 ${entry.name}"); currentTaskIO { manager.delete(entry, directory) }; complete++ }
                            catch (e: CancellationException) {
                                checkCurrent(); onSetNotice("已取消；已删除 $complete 项"); throw e
                            } catch (e: Exception) { failures += "${entry.name}：${e.message}" }
                        }
                        onSetNotice("已删除 $complete/${work.size} 项" + if (failures.isEmpty()) "" else "\n" + failures.joinToString("\n"))
                    }
                }
            }
            Row {
                FileToolButton(onClick = { onFinishInput(); onClearForm() }, enabled = !busy) { Text("取消") }
                FileToolButton(onClick = submitForm, enabled = !busy && (mode == "delete" || input.isNotBlank())) { Text(if (mode == "delete") "确认删除" else "确认") }
            }
            if (mode != "delete") OutlinedTextField(
                input, onInputChange, singleLine = true, label = { Text("名称") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submitForm() })
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ManagementEntryRow(
    entry: BrowserEntry,
    enabled: Boolean = true,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onMenu: (() -> Unit)? = null
) {
    val palette = opaqueFilePalette(LocalPanelPalette.current)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp)
            .background(if (selectionMode && selected) palette.accent.copy(alpha = .1f) else Color.Transparent)
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            if (selectionMode) Checkbox(selected, { onClick() }, enabled = enabled)
            else FileWorkspaceEntryGlyph(entry)
        }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(entry.name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = palette.textPrimary, fontWeight = if (entry.isDirectory) FontWeight.Medium else FontWeight.Normal)
            Text(
                entry.blockedReason ?: listOfNotNull(
                    if (entry.isDirectory) "文件夹" else fileBytes(entry.size),
                    entry.modified?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) }
                ).joinToString(" · "),
                fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = palette.textSecondary
            )
        }
        if (!selectionMode && onMenu != null) {
            IconButton(onClick = onMenu, enabled = enabled, modifier = Modifier.size(40.dp)) {
                Icon(Icons.MoreVert, "${entry.name} 的操作", Modifier.size(18.dp), tint = palette.textSecondary)
            }
        }
    }
    HorizontalDivider(Modifier.padding(start = 48.dp), color = palette.cardBorder.copy(alpha = .22f))
}

/** Compact controls leave room for directory rows in the minimum floating window. */
