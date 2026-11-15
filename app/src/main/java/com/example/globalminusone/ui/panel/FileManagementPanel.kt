package com.example.globalminusone.ui.panel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.system.*
import com.example.globalminusone.ui.AppIcons as Icons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
    val state by repository.state.collectAsState()
    val expanded by repository.isPanelExpanded.collectAsState()
    val config = state.fileBrowserConfig
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(initialTab) }
    var stack by remember { mutableStateOf(initialStack ?: initialLocation?.let { listOf(it) } ?: emptyList()) }
    val current = stack.lastOrNull()
    var rows by remember { mutableStateOf<List<BrowserEntry>>(emptyList()) }
    var rowsLoading by remember { mutableStateOf(false) }
    var rowsLocationKey by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selecting by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var task by remember { mutableStateOf<Job?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var noticeExpanded by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var actionsMenuOpen by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<String?>(null) }
    var form by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var properties by remember { mutableStateOf<FileProperties?>(null) }
    var checksums by remember { mutableStateOf<FileChecksums?>(null) }
    var hashCompareInput by remember { mutableStateOf("") }
    var clipboard by clipboardState
    var statistics by remember {
        mutableStateOf<com.example.globalminusone.system.FileScanResult?>(null)
    }
    var duplicateGroups by remember {
        mutableStateOf<List<com.example.globalminusone.system.DuplicateFileGroup>?>(null)
    }
    var trackedAnalyzerRootKey by remember { mutableStateOf(current?.key) }
    var analyzerSection by remember { mutableStateOf(initialAnalyzerSection) }
    var drillCategory by remember { mutableStateOf(initialDrillCategory) }
    var analyzerSort by remember { mutableStateOf(AnalyzerSortOrder.SIZE_DESC) }
    var categoryFilterQuery by remember { mutableStateOf("") }
    var analyzerSelecting by remember { mutableStateOf(false) }
    var analyzerSelected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var analyzerBatchDeleteConfirm by remember { mutableStateOf(false) }
    var cleanAllEmptyConfirm by remember { mutableStateOf(false) }
    val inspector = remember { FileInspectorState() }
    var inspectorEntry by inspector.entry
    var inspectorProps by inspector.properties
    var inspectorChecksums by inspector.checksums
    var inspectorHashCompare by inspector.hashCompare
    var inspectorRenameMode by inspector.renameMode
    var inspectorRenameInput by inspector.renameInput
    var inspectorDeleteConfirm by inspector.deleteConfirm
    var externalLaunching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<FileSearchResult?>(null) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(FileCategory.ALL) }
    var categoryMenu by remember { mutableStateOf(false) }
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
        onAnalyzerStateChanged(analyzerSection, drillCategory)
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
        when {
            busy -> Unit
            editorBack != null -> editorBack?.invoke()
            clearRecentRequested -> clearRecentRequested = false
            pathInputOpen -> { finishInput(); pathInputOpen = false }
            actionsMenuOpen -> actionsMenuOpen = false
            navigationMenu -> navigationMenu = false
            categoryMenu -> categoryMenu = false
            analyzerBatchDeleteConfirm -> analyzerBatchDeleteConfirm = false
            cleanAllEmptyConfirm -> cleanAllEmptyConfirm = false
            inspectorRenameMode || inspectorDeleteConfirm -> {
                finishInput(); inspectorRenameMode = false; inspectorDeleteConfirm = false
            }
            inspectorEntry != null -> {
                finishInput(); inspectorEntry = null; inspectorProps = null; inspectorChecksums = null
            }
            form != null || properties != null || checksums != null -> { finishInput(); form = null; properties = null; checksums = null }
            tab == "分析查找" && analyzerSelecting -> { analyzerSelecting = false; analyzerSelected = emptySet() }
            tab == "分析查找" && analyzerSection == "overview" && drillCategory != null -> {
                finishInput(); drillCategory = null; categoryFilterQuery = ""
            }
            selecting -> { selecting = false; selected = emptySet() }
            tab != "管理" -> {
                finishInput(); task?.cancel()
                if (initialTab == "分析查找" && tab == "分析查找") onClose(stack) else tab = "管理"
            }
            else -> { finishInput(); task?.cancel(); onClose(stack) }
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

    fun runTask(mutates: Boolean = false, block: suspend () -> Unit) {
        if (busy) return
        busy = true; notice = null; progress = null
        task = scope.launch {
            try { block() }
            catch (e: CancellationException) { notice = "已取消。已完成的文件操作不会回滚；未完成的统计/查找结果已废弃"; throw e }
            catch (e: Exception) { notice = e.message ?: "操作失败，请检查授权" }
            finally { busy = false; progress = null; if (mutates) { reload++; onChanged() } }
        }
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
            }
            else { onOpen(entry, stack) }
        } catch (e: Exception) { notice = e.message ?: "访问失败，请重新授权" }
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
            stack = resolveFileWorkspacePath(plan) { browser.list(it) }
            selected = emptySet(); selecting = false; tab = "管理"
        }
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
                properties = manager.properties(target)
                checksums = manager.calculateChecksums(target) { bytes -> progress = "计算哈希 ${fileBytes(bytes)}" }
            }
        },
        sheetAction("属性", Icons.Settings, chosen.size == 1) { runTask { checksums = null; properties = manager.properties(chosen.single()) } },
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
    LaunchedEffect(current?.key) {
        if (trackedAnalyzerRootKey != current?.key) {
            trackedAnalyzerRootKey = current?.key
            statistics = current?.let { withContext(Dispatchers.IO) { runCatching { manager.cachedScan(it) }.getOrNull() } }
            duplicateGroups = current?.let { withContext(Dispatchers.IO) { runCatching { manager.cachedDuplicates(it) }.getOrNull() } }
            results = null
            drillCategory = null
            categoryFilterQuery = ""
            analyzerSelecting = false
            analyzerSelected = emptySet()
            inspectorEntry = null
            inspectorProps = null
            inspectorChecksums = null
        }
    }
    LaunchedEffect(current?.key, reload, expanded) {
        val directoryChanged = rowsLocationKey != current?.key
        if (directoryChanged) {
            properties = null; checksums = null; form = null
            rows = emptyList(); selected = emptySet(); selecting = false
            rowsLocationKey = current?.key
        }
        rowsLoading = expanded && current != null
        if (expanded && current != null) {
            try {
                val cached = withContext(Dispatchers.IO) { browser.cachedDirectory(current) }
                if (cached != null) rows = cached
                if (cached == null || !browser.isDirectoryCacheFresh(current)) rows = browser.list(current)
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
                        "properties" -> if (target != null) properties = manager.properties(target)
                        "checksum" -> if (target != null && !target.isDirectory) {
                            properties = manager.properties(target)
                            checksums = manager.calculateChecksums(target)
                        }
                        "edit" -> if (target != null) textEntry = target
                        "copy", "move" -> if (target != null && (initialAction == "copy" || writable)) {
                            clipboard = TransferClipboard(listOf(target), current, initialAction == "move")
                            selected = emptySet(); selecting = false
                        }
                    }
                    if (!writable && initialAction in setOf("new", "newtext", "rename", "delete", "move")) notice = "此位置仅有只读权限；请返回存储入口添加可管理目录，原文件未修改"
                }
            }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { rows = emptyList(); notice = e.message ?: "无法读取目录" }
            finally { rowsLoading = false }
        }
    }
    LaunchedEffect(Unit) { volumes = manager.volumes() }
    LaunchedEffect(expanded) { if (!expanded) task?.cancel() }
    DisposableEffect(Unit) { onDispose { task?.cancel(); finishInput() } }

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
                            drillCategory?.takeIf { analyzerSection == "overview" }?.let { "存储分析 · ${it.label} ▾" }
                                ?: "存储分析与清理 ▾"
                        } else "最近与收藏 ▾"
                        TextButton(onClick = { navigationMenu = true }, enabled = !busy) { Text(headerLabel, fontWeight = FontWeight.SemiBold) }
                        DropdownMenu(navigationMenu, { navigationMenu = false }) {
                            com.example.globalminusone.service.Android12WindowKeyGuard()

                            listOf("分析查找", "最近收藏").forEach { destination ->
                                DropdownMenuItem(text = { Text(if (destination == "分析查找") "存储分析与清理" else "最近与收藏") },
                                    onClick = { navigationMenu = false; changeTab(destination) })
                            }
                        }
                    }
                    TextButton(onClick = { task?.cancel(); onClose(stack) }) { Text("返回目录") }
                }
            }
            HorizontalDivider(color = palette.cardBorder.copy(alpha = .35f))
        }
        if (busy) Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
            Text(progress ?: "处理中…", Modifier.weight(1f), fontSize = 11.sp, maxLines = 2)
            FileToolButton(onClick = { task?.cancel() }) { Text("取消") }
        }
        if (tab != "管理" || current == null || form != null || properties != null) notice?.let { Text(it, Modifier.fillMaxWidth().clickable { noticeExpanded = !noticeExpanded }.padding(horizontal = 8.dp), maxLines = if (noticeExpanded) 4 else 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary) }
        if (newText || textEntry != null) {
            Box(Modifier.weight(1f)) {
                FileTextEditor(manager, textEntry, current,
                    onClose = { newText = false; textEntry = null },
                    onSaved = { newText = false; textEntry = null; notice = "已保存 UTF-8 文本"; reload++; onChanged() },
                    installBack = { editorBack = it }, installWindowClose = { editorWindowClose = it })
            }
        } else if (form != null || properties != null) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                properties?.let { p ->
                    val clipManager = remember(context) { context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager }
                    fun copyHash(label: String, value: String) {
                        clipManager?.setPrimaryClip(android.content.ClipData.newPlainText(label, value))
                        notice = "已复制 $label"
                    }
                    Text(p.title, fontWeight = FontWeight.Bold)
                    Text("类型：${p.type}\n大小：${fileBytes(p.size)}${if (p.type == "vnd.android.document/directory" || p.type == "目录") "（目录大小请使用分析）" else ""}\n修改时间：${p.modified?.let { DateFormat.getDateTimeInstance().format(Date(it)) } ?: "未知"}\n权限：${if (p.writable) "已授写入（具体操作由提供方决定）" else "只读"}\n位置：${p.location}", fontSize = 12.sp)
                    val targetEntry = chosen.singleOrNull() ?: initialEntry
                    if (targetEntry != null && !targetEntry.isDirectory && checksums == null) {
                        FileToolButton(onClick = {
                            runTask {
                                checksums = manager.calculateChecksums(targetEntry) { bytes -> progress = "计算哈希 ${fileBytes(bytes)}" }
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
                                    Text(hex, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                }
                                FileToolButton(onClick = { copyHash(algo, hex) }, height = 28.dp) { Text("复制", fontSize = 11.sp) }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = hashCompareInput,
                                onValueChange = { hashCompareInput = it },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                label = { Text("粘贴哈希值进行比对", fontSize = 11.sp) },
                                trailingIcon = {
                                    TextButton(onClick = {
                                        val clipText = clipManager?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
                                        if (clipText.isNotEmpty()) hashCompareInput = clipText
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
                    FileToolButton(onClick = { properties = null; checksums = null }) { Text("关闭属性") }
                }
                form?.let { mode ->
                    Text(when(mode) { "new" -> "新建文件夹"; "rename" -> "重命名 ${chosen.firstOrNull()?.name.orEmpty()}"; else -> "删除 ${chosen.size} 项？" }, fontWeight = FontWeight.Bold)
                    if (mode == "delete") {
                        Text("位置：${current?.title}\n目标（${chosen.size}项）：${chosen.joinToString("、") { it.name }}", fontSize = 12.sp)
                        Text("包含目录内的所有内容，删除不可撤销。只处理以上已选项目。", fontSize = 12.sp)
                    }
                    val submitForm: () -> Unit = submit@{
                            if (busy || (mode != "delete" && input.isBlank())) return@submit
                            val directory = current ?: return@submit
                            val work = chosen.toList(); val value = input
                            finishInput(); form = null
                            runTask(mutates = true) {
                                if (mode == "new") { manager.createDirectory(directory, value); notice = "已创建文件夹" }
                                else if (mode == "rename") {
                                    val old = work.single()
                                    val renamed = manager.rename(old, directory, value)
                                    repository.updateFileBrowserConfig { previous ->
                                        fun replace(items: List<com.example.globalminusone.data.FileBrowserBookmark>) = items.map { saved ->
                                            if (saved.location().key == old.location.key) renamed.bookmark(old.isDirectory, old.mimeType) else saved
                                        }
                                        previous.copy(favorites = replace(previous.favorites), recent = replace(previous.recent))
                                    }
                                    notice = "已重命名"
                                }
                                else {
                                    var complete = 0; val failures = mutableListOf<String>()
                                    for (entry in work) {
                                        try { progress = "删除 ${entry.name}"; manager.delete(entry, directory); complete++ }
                                        catch (e: CancellationException) { notice = "已取消；已删除 $complete 项"; throw e }
                                        catch (e: Exception) { failures += "${entry.name}：${e.message}" }
                                    }
                                    notice = "已删除 $complete/${work.size} 项" + if (failures.isEmpty()) "" else "\n" + failures.joinToString("\n")
                                }
                            }
                    }
                    Row {
                        FileToolButton(onClick = { finishInput(); form = null }, enabled = !busy) { Text("取消") }
                        FileToolButton(onClick = submitForm, enabled = !busy && (mode == "delete" || input.isNotBlank())) { Text(if (mode == "delete") "确认删除" else "确认") }
                    }
                    if (mode != "delete") OutlinedTextField(input, { input = it }, singleLine = true, label = { Text("名称") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submitForm() }))
                }
            }
        } else if (tab == "最近收藏") {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(8.dp)) {
                item { Text("收藏", fontWeight = FontWeight.Bold) }
                if (config.favorites.isEmpty()) item { Text("在管理中选择文件或目录后可收藏", fontSize = 12.sp) }
                items(config.favorites, key = { "fav:${it.location().key}" }) { b ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { ManagementEntryRow(b.entry(), enabled = !busy,
                            onClick = { openEntry(b.entry()); tab = "管理" }) }
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
        } else {
            if (current == null) {
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
                // Load cached scan/duplicates in 0ms; only scan once when no cached snapshot exists for current directory
                LaunchedEffect(current.key, expanded) {
                    if (expanded) {
                        val cached = withContext(Dispatchers.IO) { runCatching { manager.cachedScan(current) }.getOrNull() }
                        if (cached != null) {
                            statistics = cached
                        } else if (statistics == null && !busy) {
                            runTask {
                                statistics = manager.scan(current, force = false) {
                                    progress = "正在扫描 ${it.visited} 项 / ${it.directories} 目录：${it.current}"
                                }
                            }
                        }
                        if (duplicateGroups == null) {
                            duplicateGroups = runCatching { manager.cachedDuplicates(current) }.getOrNull()
                        }
                    }
                }

                LaunchedEffect(inspectorEntry?.location?.key) {
                    val target = inspectorEntry ?: return@LaunchedEffect
                    inspectorProps = runCatching { manager.properties(target) }.getOrNull()
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
                    finishInput()
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

                val rawCategoryList = remember(statistics, drillCategory) {
                    drillCategory?.let { statistics?.categoryFiles?.get(it) }.orEmpty()
                }
                val filteredCategoryEntries = remember(rawCategoryList, categoryFilterQuery, analyzerSort, current.key) {
                    val q = categoryFilterQuery.trim()
                    val filtered = if (q.isEmpty()) rawCategoryList else rawCategoryList.filter { entry ->
                        matchesQuickFilter(entry.name, q) ||
                            relativeAnalyzerParentLabel(current, entry).contains(q, ignoreCase = true)
                    }
                    sortAnalyzerEntries(filtered, analyzerSort)
                }
                val sortedLargeEntries = remember(statistics?.largeFiles, analyzerSort) {
                    sortAnalyzerEntries(statistics?.largeFiles.orEmpty(), analyzerSort)
                }
                val sortedApkEntries = remember(statistics?.apkFiles, analyzerSort) {
                    sortAnalyzerEntries(statistics?.apkFiles.orEmpty(), analyzerSort)
                }
                val analyzerVisibleEntries = remember(
                    analyzerSection, drillCategory, filteredCategoryEntries,
                    sortedLargeEntries, duplicateGroups, sortedApkEntries, results
                ) {
                    when {
                        analyzerSection == "overview" && drillCategory != null -> filteredCategoryEntries
                        analyzerSection == "large" -> sortedLargeEntries
                        analyzerSection == "duplicates" -> duplicateGroups.orEmpty().flatMap { it.entries }
                        analyzerSection == "apk" -> sortedApkEntries
                        analyzerSection == "search" -> results?.entries.orEmpty()
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
                        // Header Action Card
                        item(key = "analyzer-header") {
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
                                            onClick = {
                                                finishInput()
                                                analyzerSelecting = false; analyzerSelected = emptySet()
                                                inspectorEntry = null; duplicateGroups = null; results = null
                                                runTask {
                                                    statistics = manager.scan(current, force = true) {
                                                        progress = "扫描 ${it.visited} 项 / ${it.directories} 目录：${it.current}"
                                                    }
                                                }
                                            },
                                            enabled = !busy,
                                            modifier = Modifier.height(32.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                        ) {
                                            Icon(Icons.Refresh, null, Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("重新扫描", fontSize = 11.5.sp)
                                        }
                                    }

                                    // Interactive Multi-Color Stacked Storage Bar (tapping any segment drills into that category)
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
                                                                .clickable(enabled = !busy) {
                                                                    analyzerSection = "overview"
                                                                    drillCategory = cat
                                                                    categoryFilterQuery = ""
                                                                    analyzerSelecting = false
                                                                    analyzerSelected = emptySet()
                                                                }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Segmented Cleaner Sub-Tabs
                        item(key = "analyzer-subtabs") {
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
                                    val activeSub = analyzerSection == id
                                    Surface(
                                        onClick = {
                                            finishInput()
                                            if (analyzerSection != id) {
                                                analyzerSection = id
                                                analyzerSelecting = false
                                                analyzerSelected = emptySet()
                                            } else if (id == "overview") {
                                                drillCategory = null
                                            }
                                            if (id == "duplicates" && duplicateGroups == null && !busy) {
                                                val cachedDups = runCatching { manager.cachedDuplicates(current) }.getOrNull()
                                                if (cachedDups != null) {
                                                    duplicateGroups = cachedDups
                                                } else {
                                                    runTask {
                                                        duplicateGroups = manager.findDuplicateFiles(current, force = false) {
                                                            progress = "SHA-256 查重 ${it.visited} 项：${it.current}"
                                                        }
                                                        if (duplicateGroups?.isEmpty() == true) notice = "未发现内容完全相同的重复文件"
                                                    }
                                                }
                                            }
                                        },
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

                        when (analyzerSection) {
                            "overview" -> {
                                val stats = statistics
                                val activeCategory = drillCategory
                                if (stats != null && activeCategory == null) {
                                    val types = FileCategory.entries.filter { it !in setOf(FileCategory.ALL, FileCategory.DIRECTORY) }
                                    items(types.chunked(2), key = { pair -> "cat-pair:${pair.joinToString { it.name }}" }) { pair ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            pair.forEach { type ->
                                                val usage = stats.groups[type] ?: FileCategoryUsage()
                                                val ratio = stats.knownRatio(type)
                                                val dotColor = categoryColors[type] ?: palette.accent
                                                Surface(
                                                    onClick = {
                                                        drillCategory = type
                                                        categoryFilterQuery = ""
                                                        analyzerSelecting = false
                                                        analyzerSelected = emptySet()
                                                    },
                                                    enabled = !busy,
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                                    border = BorderStroke(0.8.dp, palette.cardBorder.copy(alpha = 0.45f)),
                                                    modifier = Modifier.weight(1f)
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
                                                            Icon(
                                                                Icons.KeyboardArrowRight,
                                                                contentDescription = "查看${type.label}文件",
                                                                modifier = Modifier.size(15.dp),
                                                                tint = palette.textSecondary
                                                            )
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
                                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                                        }
                                    }
                                    if (stats.inaccessibleDirectories > 0) {
                                        item(key = "inaccessible-hint") {
                                            Text(
                                                "提示：有 ${stats.inaccessibleDirectories} 个受系统保护的子目录已跳过统计。点击任意分类卡片可查看并管理对应文件。",
                                                fontSize = 10.5.sp,
                                                color = palette.textSecondary
                                            )
                                        }
                                    } else {
                                        item(key = "category-click-hint") {
                                            Text(
                                                "提示：点击上方任意分类卡片或色条，即可查看该分类文件详情、定位目录或批量清理。",
                                                fontSize = 10.5.sp,
                                                color = palette.textSecondary
                                            )
                                        }
                                    }
                                } else if (stats != null && activeCategory != null) {
                                    val usage = stats.groups[activeCategory] ?: FileCategoryUsage()
                                    val dotColor = categoryColors[activeCategory] ?: palette.accent
                                    item(key = "category-drill-header") {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                                            border = BorderStroke(0.8.dp, palette.cardBorder.copy(alpha = 0.45f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    TextButton(
                                                        onClick = {
                                                            finishInput()
                                                            drillCategory = null
                                                            categoryFilterQuery = ""
                                                            analyzerSelecting = false
                                                            analyzerSelected = emptySet()
                                                        },
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
                                                    if (filteredCategoryEntries.isNotEmpty()) {
                                                        TextButton(
                                                            onClick = {
                                                                analyzerSelecting = !analyzerSelecting
                                                                if (!analyzerSelecting) analyzerSelected = emptySet()
                                                            },
                                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                                            modifier = Modifier.height(30.dp)
                                                        ) {
                                                            Text(if (analyzerSelecting) "退出多选" else "多选", fontSize = 11.5.sp)
                                                        }
                                                    }
                                                }
                                                if (rawCategoryList.isNotEmpty()) {
                                                    OutlinedTextField(
                                                        value = categoryFilterQuery,
                                                        onValueChange = { categoryFilterQuery = it },
                                                        singleLine = true,
                                                        modifier = Modifier.fillMaxWidth(),
                                                        textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                                        placeholder = { Text("过滤${activeCategory.label}文件名、子目录或 *.后缀", fontSize = 11.sp) },
                                                        leadingIcon = { Icon(Icons.FilterList, null, Modifier.size(16.dp)) },
                                                        trailingIcon = {
                                                            if (categoryFilterQuery.isNotEmpty()) {
                                                                IconButton(onClick = { categoryFilterQuery = "" }, modifier = Modifier.size(28.dp)) {
                                                                    Icon(Icons.Close, "清空过滤", Modifier.size(15.dp))
                                                                }
                                                            }
                                                        }
                                                    )
                                                    AnalyzerSortAndHintBar(
                                                        currentSort = analyzerSort,
                                                        onSortChange = { analyzerSort = it },
                                                        trailingHint = if (usage.files > rawCategoryList.size) {
                                                            "按体积展示前 ${rawCategoryList.size} 项"
                                                        } else "点击查看详情与操作 · 长按多选"
                                                    )
                                                }
                                            }
                                        }
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
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                                            border = BorderStroke(0.8.dp, palette.cardBorder.copy(alpha = 0.45f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        "大文件 (>10 MB · ${sortedLargeEntries.size} 项 · ${fileBytes(sortedLargeEntries.sumOf { it.size ?: 0L })})",
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 12.sp,
                                                        modifier = Modifier.weight(1f),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
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
                                                AnalyzerSortAndHintBar(
                                                    currentSort = analyzerSort,
                                                    onSortChange = { analyzerSort = it },
                                                    trailingHint = "点击查看路径/属性/操作 · 长按多选"
                                                )
                                            }
                                        }
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
                                        Text(
                                            "大小 + SHA-256 精确查重",
                                            fontSize = 11.5.sp,
                                            color = palette.textSecondary,
                                            modifier = Modifier.weight(1f)
                                        )
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
                                                runTask {
                                                    duplicateGroups = manager.findDuplicateFiles(current, force = true) {
                                                        progress = "查重 ${it.visited} 项：${it.current}"
                                                    }
                                                    if (duplicateGroups?.isEmpty() == true) notice = "未发现完全相同的重复文件"
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
                                            Row(
                                                Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
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
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                                            border = BorderStroke(0.8.dp, palette.cardBorder.copy(alpha = 0.45f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        "安装包 (${sortedApkEntries.size} 项 · 占用 ${fileBytes(sortedApkEntries.sumOf { it.size ?: 0L })})",
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 12.sp,
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
                                                AnalyzerSortAndHintBar(
                                                    currentSort = analyzerSort,
                                                    onSortChange = { analyzerSort = it },
                                                    trailingHint = "点击可外部安装、查看哈希或删除"
                                                )
                                            }
                                        }
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
                                            Row(
                                                Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "空文件夹 (${emptyDirs.size} 个)",
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (writable) {
                                                    FilledTonalButton(
                                                        onClick = { cleanAllEmptyConfirm = true },
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
                                                        val targetStack = manager.resolveParentStack(stack, dirLoc)
                                                        if (initialTab == "分析查找") onClose(targetStack)
                                                        else { stack = targetStack; tab = "管理" }
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
                                                    onClick = {
                                                        val targetStack = manager.resolveParentStack(stack, dirLoc)
                                                        if (initialTab == "分析查找") onClose(targetStack)
                                                        else { stack = targetStack; tab = "管理" }
                                                    },
                                                    enabled = !busy,
                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                                    modifier = Modifier.height(28.dp)
                                                ) { Text("进入", fontSize = 11.sp) }
                                                if (writable) {
                                                    IconButton(
                                                        onClick = {
                                                            runTask(mutates = true) {
                                                                manager.deleteEmptyDirectoryKeepingAnalyzerCache(dirLoc)
                                                                statistics = statistics?.withoutDeleted(emptyList(), listOf(dirLoc))
                                                                notice = "已清理空目录：${dirLoc.title}"
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
                                            onValueChange = { query = it },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            label = { Text("名称关键词（可留空仅按类型查找）", fontSize = 11.5.sp) },
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                            keyboardActions = KeyboardActions(onSearch = {
                                                if (!busy) {
                                                    finishInput()
                                                    results = null
                                                    runTask {
                                                        results = manager.search(current, query.trim(), category) {
                                                            progress = "查找 ${it.visited} 项：${it.current}"
                                                        }
                                                    }
                                                }
                                            })
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Box {
                                                OutlinedButton(onClick = { categoryMenu = true }, modifier = Modifier.height(34.dp)) {
                                                    Text("类型：${category.label}", fontSize = 11.5.sp)
                                                }
                                                DropdownMenu(categoryMenu, { categoryMenu = false }) {
                                                    com.example.globalminusone.service.Android12WindowKeyGuard()

                                                    FileCategory.entries.forEach { type ->
                                                        DropdownMenuItem({ Text(type.label) }, { category = type; categoryMenu = false })
                                                    }
                                                }
                                            }
                                            FilledTonalButton(
                                                onClick = {
                                                    finishInput()
                                                    analyzerSelecting = false; analyzerSelected = emptySet()
                                                    results = null
                                                    runTask {
                                                        results = manager.search(current, query.trim(), category) {
                                                            progress = "查找 ${it.visited} 项：${it.current}"
                                                        }
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
                                                    runTask {
                                                        results = manager.search(current, query.trim(), category, cursor) {
                                                            progress = "继续查找 ${it.visited} 项：${it.current}"
                                                        }
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
                                                else if (entry.isDirectory) {
                                                    val targetStack = manager.resolveParentStack(stack, entry.location)
                                                    if (initialTab == "分析查找") onClose(targetStack)
                                                    else { stack = targetStack; tab = "管理" }
                                                } else openInspector(entry)
                                            },
                                            onLongClick = { if (!entry.isDirectory) startAnalyzerSelect(entry) },
                                            onInspect = {
                                                if (entry.isDirectory) {
                                                    val targetStack = manager.resolveParentStack(stack, entry.location)
                                                    if (initialTab == "分析查找") onClose(targetStack)
                                                    else { stack = targetStack; tab = "管理" }
                                                } else openInspector(entry)
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
                                    if (analyzerSection == "duplicates" && !duplicateGroups.isNullOrEmpty()) {
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
                                        favorite(chosenAnalyzerEntries)
                                    },
                                    FileWorkspaceAction(
                                        title = "批量删除",
                                        icon = Icons.Delete,
                                        destructive = true,
                                        enabled = !busy && writable && chosenAnalyzerEntries.isNotEmpty()
                                    ) {
                                        analyzerBatchDeleteConfirm = true
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
            } else {
                clipboard?.let { clip ->
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${if (clip.move) "移动" else "复制"} ${clip.entries.size} 项 → ${current.title}", Modifier.weight(1f),
                            fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = {
                            runTask(mutates = true) {
                                var complete = 0; val failures = mutableListOf<String>()
                                for (entry in clip.entries) {
                                    try { manager.transfer(entry, clip.parent, current, clip.move) { progress = "${if (clip.move) "移动" else "复制"}：$it" }; complete++ }
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
                    notice?.let { message -> item {
                        Text(message, Modifier.fillMaxWidth().clickable { noticeExpanded = !noticeExpanded }.padding(8.dp),
                            maxLines = if (noticeExpanded) 6 else 2, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, color = palette.accent)
                    } }
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
    }
    if (actionsMenuOpen) FileWorkspaceActionSheet(
        if (selecting) "所选 ${chosen.size} 项" else "目录工具", toolActions.filter { it.enabled },
        onDismiss = { actionsMenuOpen = false })

    val activeInspector = inspectorEntry
    if (activeInspector != null && current != null) {
        val entryWritable = remember(activeInspector.location.key, reload, expanded) {
            activeInspector.blockedReason == null && manager.canWrite(activeInspector.location)
        }
        AnalyzerInspectorSheet(
            entry = activeInspector,
            root = current,
            properties = inspectorProps,
            checksums = inspectorChecksums,
            hashCompareInput = inspectorHashCompare,
            onHashCompareChange = { inspectorHashCompare = it },
            writable = entryWritable,
            busy = busy,
            externalBusy = externalLaunching,
            renameMode = inspectorRenameMode,
            renameInput = inspectorRenameInput,
            onRenameInputChange = { inspectorRenameInput = it },
            onToggleRename = {
                finishInput()
                inspector.toggleRename(activeInspector.name)
            },
            onConfirmRename = {
                val targetName = inspectorRenameInput.trim()
                if (!busy && targetName.isBlank()) notice = "名称不能为空"
                if (!busy && targetName.isNotBlank() && targetName != activeInspector.name) {
                    finishInput()
                    inspectorRenameMode = false
                    runTask(mutates = true) {
                        val updated = manager.renameKeepingAnalyzerCache(activeInspector, current, targetName)
                        statistics = statistics?.withRenamed(activeInspector, updated)
                        duplicateGroups = duplicateGroups?.withRenamed(activeInspector.location.key, updated)
                        results = results?.let { r ->
                            r.copy(entries = r.entries.map { if (it.location.key == activeInspector.location.key) updated else it })
                        }
                        repository.updateFileBrowserConfig { previous ->
                            fun replace(items: List<com.example.globalminusone.data.FileBrowserBookmark>) = items.map { saved ->
                                if (saved.location().key == activeInspector.location.key) updated.location.bookmark(updated.isDirectory, updated.mimeType) else saved
                            }
                            previous.copy(favorites = replace(previous.favorites), recent = replace(previous.recent))
                        }
                        inspectorEntry = updated
                        inspectorProps = runCatching { manager.properties(updated) }.getOrNull()
                        notice = "已重命名为 ${updated.name}"
                    }
                }
            },
            deleteConfirmMode = inspectorDeleteConfirm,
            onToggleDeleteConfirm = {
                finishInput()
                inspector.toggleDelete()
            },
            onConfirmDelete = {
                if (!busy) {
                    finishInput()
                    val toDelete = activeInspector
                    inspectorDeleteConfirm = false
                    runTask(mutates = true) {
                        progress = "删除 ${toDelete.name}"
                        manager.deleteKeepingAnalyzerCache(toDelete)
                        val keySet = setOf(toDelete.location.key)
                        repository.updateFileBrowserConfig { saved -> saved.copy(
                            favorites = saved.favorites.filterNot { it.location().key in keySet },
                            recent = saved.recent.filterNot { it.location().key in keySet }
                        ) }
                        statistics = statistics?.withoutDeleted(listOf(toDelete))
                        duplicateGroups = duplicateGroups?.withoutDeletedKeys(keySet)
                        results = results?.let { r -> r.copy(entries = r.entries.filterNot { it.location.key in keySet }) }
                        analyzerSelected = analyzerSelected - keySet
                        if (analyzerSelected.isEmpty()) analyzerSelecting = false
                        inspectorEntry = null; inspectorProps = null; inspectorChecksums = null
                        notice = "已删除 ${toDelete.name}（释放 ${fileBytes(toDelete.size)}）"
                    }
                }
            },
            onPreview = {
                val target = activeInspector
                inspectorEntry = null; inspectorProps = null; inspectorChecksums = null
                openEntry(target)
            },
            onOpenParentDirectory = {
                finishInput()
                val parentLoc = manager.resolveEntryParent(activeInspector, current)
                val parentStack = manager.resolveParentStack(stack, parentLoc)
                inspectorEntry = null; inspectorProps = null; inspectorChecksums = null
                if (initialTab == "分析查找") onClose(parentStack)
                else { stack = parentStack; tab = "管理" }
            },
            onOpenExternal = { share ->
                if (!externalLaunching) {
                    scope.launch {
                        externalLaunching = true
                        try { browser.launchExternal(activeInspector, share)?.let { notice = it } }
                        finally { externalLaunching = false }
                    }
                }
            },
            onFavorite = {
                favorite(listOf(activeInspector))
            },
            onSelectForBatch = {
                val target = activeInspector
                inspectorEntry = null; inspectorProps = null; inspectorChecksums = null
                analyzerSelecting = true
                analyzerSelected = analyzerSelected + target.location.key
            },
            onComputeChecksums = {
                if (!busy && !activeInspector.isDirectory) {
                    runTask {
                        inspectorChecksums = manager.calculateChecksums(activeInspector) { bytes ->
                            progress = "计算哈希 ${fileBytes(bytes)}"
                        }
                    }
                }
            },
            onCopyNotice = { msg -> notice = msg },
            onDismiss = {
                if (!busy) {
                    finishInput()
                    inspectorEntry = null; inspectorProps = null; inspectorChecksums = null
                    inspectorRenameMode = false; inspectorDeleteConfirm = false
                }
            }
        )
    }
    }
    if (analyzerBatchDeleteConfirm && current != null) {
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
            onDismissRequest = { analyzerBatchDeleteConfirm = false },
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
                        analyzerBatchDeleteConfirm = false
                        val work = batchTargets.toList()
                        runTask(mutates = true) {
                            val deleted = ArrayList<BrowserEntry>()
                            val failures = ArrayList<String>()
                            for (item in work) {
                                try {
                                    progress = "删除 ${item.name}"
                                    manager.deleteKeepingAnalyzerCache(item)
                                    deleted.add(item)
                                } catch (e: CancellationException) {
                                    if (deleted.isNotEmpty()) {
                                        val delKeys = deleted.map { it.location.key }.toSet()
                                        statistics = statistics?.withoutDeleted(deleted)
                                        duplicateGroups = duplicateGroups?.withoutDeletedKeys(delKeys)
                                        results = results?.let { r -> r.copy(entries = r.entries.filterNot { it.location.key in delKeys }) }
                                        analyzerSelected = analyzerSelected - delKeys
                                    }
                                    throw e
                                } catch (e: Exception) {
                                    failures += "${item.name}：${e.message}"
                                }
                            }
                            val delKeys = deleted.map { it.location.key }.toSet()
                            statistics = statistics?.withoutDeleted(deleted)
                            duplicateGroups = duplicateGroups?.withoutDeletedKeys(delKeys)
                            results = results?.let { r -> r.copy(entries = r.entries.filterNot { it.location.key in delKeys }) }
                            analyzerSelected = analyzerSelected - delKeys
                            if (analyzerSelected.isEmpty()) analyzerSelecting = false
                            val freed = deleted.sumOf { it.size ?: 0L }
                            notice = "已删除 ${deleted.size}/${work.size} 项（释放 ${fileBytes(freed)}）" +
                                if (failures.isEmpty()) "" else "\n" + failures.take(3).joinToString("\n")
                        }
                    },
                    enabled = !busy && batchTargets.isNotEmpty()
                ) { Text("确认删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { analyzerBatchDeleteConfirm = false }) { Text("取消") } }
        )
    }
    if (cleanAllEmptyConfirm && current != null) {
        val emptyDirs = statistics?.emptyDirectories.orEmpty()
        PanelAlertDialog(
            onDismissRequest = { cleanAllEmptyConfirm = false },
            title = { Text("一键清理 ${emptyDirs.size} 个空文件夹？") },
            text = { Text("删除前将逐个复核目录确实为空；若目录内已有新文件会自动跳过保护。", fontSize = 12.sp) },
            confirmButton = {
                TextButton(
                    onClick = {
                        cleanAllEmptyConfirm = false
                        val targets = emptyDirs.toList()
                        runTask(mutates = true) {
                            val removed = ArrayList<BrowserLocation>()
                            val failures = ArrayList<String>()
                            for (dirLoc in targets) {
                                try {
                                    progress = "清理空目录 ${dirLoc.title}"
                                    manager.deleteEmptyDirectoryKeepingAnalyzerCache(dirLoc)
                                    removed.add(dirLoc)
                                } catch (e: CancellationException) {
                                    if (removed.isNotEmpty()) {
                                        statistics = statistics?.withoutDeleted(emptyList(), removed)
                                    }
                                    throw e
                                } catch (e: Exception) {
                                    failures += "${dirLoc.title}：${e.message}"
                                }
                            }
                            statistics = statistics?.withoutDeleted(emptyList(), removed)
                            notice = "已清理 ${removed.size}/${targets.size} 个空文件夹" +
                                if (failures.isEmpty()) "" else "\n" + failures.take(3).joinToString("\n")
                        }
                    },
                    enabled = !busy && emptyDirs.isNotEmpty()
                ) { Text("确认清理", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { cleanAllEmptyConfirm = false }) { Text("取消") } }
        )
    }
    if (clearRecentRequested) PanelAlertDialog(
        onDismissRequest = { clearRecentRequested = false },
        title = { Text("清空最近访问记录？") },
        text = { Text("将删除文件浏览器的 ${config.recent.size} 条最近访问记录，无法撤销。") },
        confirmButton = { TextButton(onClick = {
            repository.updateFileBrowserConfig { it.copy(recent = emptyList()) }
            clearRecentRequested = false
        }, enabled = !busy && config.recent.isNotEmpty()) { Text("确认清空", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { clearRecentRequested = false }) { Text("取消") } }
    )
    if (pathInputOpen) PanelAlertDialog(
        onDismissRequest = { finishInput(); pathInputOpen = false },
        title = { Text("输入路径") },
        text = { OutlinedTextField(pathInput, { pathInput = it; pathInputError = null }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("目录路径") }, isError = pathInputError != null,
            supportingText = { pathInputError?.let { Text(it) } }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { submitPath() })) },
        confirmButton = { TextButton(onClick = { submitPath() }, enabled = !busy) { Text("前往") } },
        dismissButton = { TextButton(onClick = { finishInput(); pathInputOpen = false }, enabled = !busy) { Text("取消") } }
    )
}

@Composable
private fun AnalyzerSortAndHintBar(
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
private fun AnalyzerEntryRow(
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

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ManagementEntryRow(entry: BrowserEntry, enabled: Boolean = true, selectionMode: Boolean = false,
    selected: Boolean = false, onClick: () -> Unit, onLongClick: () -> Unit = {}, onMenu: (() -> Unit)? = null) {
    val palette = opaqueFilePalette(LocalPanelPalette.current)
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp)
        .background(if (selectionMode && selected) palette.accent.copy(alpha = .1f) else Color.Transparent)
        .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)
        .padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            if (selectionMode) Checkbox(selected, { onClick() }, enabled = enabled)
            else FileWorkspaceEntryGlyph(entry)
        }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(entry.name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = palette.textPrimary, fontWeight = if (entry.isDirectory) FontWeight.Medium else FontWeight.Normal)
            Text(entry.blockedReason ?: listOfNotNull(if (entry.isDirectory) "文件夹" else fileBytes(entry.size),
                entry.modified?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) }).joinToString(" · "),
                fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = palette.textSecondary)
        }
        if (!selectionMode && onMenu != null) IconButton(onClick = onMenu, enabled = enabled, modifier = Modifier.size(40.dp)) {
            Icon(Icons.MoreVert, "${entry.name} 的操作", Modifier.size(18.dp), tint = palette.textSecondary)
        }
    }
    HorizontalDivider(Modifier.padding(start = 48.dp), color = palette.cardBorder.copy(alpha = .22f))
}

/** Compact controls leave room for directory rows in the minimum floating window. */
