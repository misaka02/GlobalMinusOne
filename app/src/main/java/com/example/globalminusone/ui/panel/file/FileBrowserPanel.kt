package com.example.globalminusone.ui.panel.file

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.operations.findComponent
import com.example.globalminusone.data.FileBrowserConfig
import com.example.globalminusone.data.FileBrowserRoot
import com.example.globalminusone.data.FileBrowserSortOrder
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.system.file.FileCategory
import com.example.globalminusone.system.file.FileManagerIo
import com.example.globalminusone.system.file.FileSearchCursor
import com.example.globalminusone.system.privilege.UserRootSession
import com.example.globalminusone.system.privilege.RootStatus
import com.example.globalminusone.system.privilege.RootWritePolicy
import com.example.globalminusone.system.file.RootFileIo
import com.example.globalminusone.system.file.entry
import com.example.globalminusone.system.file.bookmark
import com.example.globalminusone.system.file.location
import com.example.globalminusone.system.file.BrowserEntry
import com.example.globalminusone.system.file.BrowserLocation
import com.example.globalminusone.system.file.BrowserPreview
import com.example.globalminusone.system.file.FileBrowserIo
import com.example.globalminusone.system.file.PanelFileBrowser
import com.example.globalminusone.system.file.browserEntryCountLabel
import com.example.globalminusone.system.file.activeFileSearchResult
import com.example.globalminusone.system.file.observeBrowserDirectory
import com.example.globalminusone.system.file.DirectoryChangeSignal
import com.example.globalminusone.system.file.matchesQuickFilter
import com.example.globalminusone.system.file.supportsTextEditing
import com.example.globalminusone.system.file.sortBrowserEntries
import com.example.globalminusone.system.file.filterBrowserEntries
import com.example.globalminusone.theme.SettingsTheme
import com.example.globalminusone.ui.AppIcons as Icons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import com.example.globalminusone.ui.panel.container.LocalFolderReturnState
import com.example.globalminusone.ui.panel.LocalPanelPalette
import com.example.globalminusone.ui.panel.PanelAlertDialog
import com.example.globalminusone.ui.panel.PanelPalette
import com.example.globalminusone.ui.panel.gesture.ownPanelScrollGestures

/** File tools deliberately use an opaque reading surface independent of panel/card alpha. */
internal fun opaqueFilePalette(base: PanelPalette): PanelPalette = base.copy(
    cardBackground = if (base.isDark) Color(0xFF171B22) else Color(0xFFFFFFFF),
    cardBorder = if (base.isDark) Color(0xFF323A47) else Color(0xFFD5DCE6),
    textPrimary = if (base.isDark) Color(0xFFF3F6FA) else Color(0xFF0F172A),
    textSecondary = if (base.isDark) Color(0xFF9EA8B8) else Color(0xFF475569),
    accent = if (base.isDark) Color(0xFF8BB8FF) else Color(0xFF1D4ED8)
)

/** A content page constrained by the existing panel container and its window insets. */
@Composable
internal fun FileBrowserPane(
    repository: MinusOneRepository,
    initialFile: ComponentItem? = null,
    compact: Boolean = false,
    showCloseControl: Boolean = true,
    onCloseHandlerChanged: ((() -> Unit)?) -> Unit = {},
    onWindowCloseHandler: (FileWindowCloseRequest?) -> Unit = {},
    paneId: Int,
    paneActive: Boolean,
    paneVisible: Boolean,
    workspaceBusy: Boolean,
    sharedClipboard: MutableState<TransferClipboard?>,
    workspaceRefresh: Int,
    receipt: FileWorkspaceTransferReceipt?,
    requestedStack: List<BrowserLocation>?,
    requestVersion: Int,
    onStackChanged: (List<BrowserLocation>) -> Unit,
    onEditingChanged: (Boolean) -> Unit,
    onPreviewChanged: (Boolean) -> Unit,
    onTransferToOther: (TransferClipboard) -> Unit,
    onChanged: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val fileFolderReturn = LocalFolderReturnState.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val io = remember(context) { FileBrowserIo(context) }
    val fileManager = remember(context, io) { FileManagerIo(context, io) }
    val config = observeFileBrowserConfig(repository)
    val panelExpanded by repository.isPanelExpanded.collectAsState()
    val rootSessionStatus by UserRootSession.status.collectAsState()
    val event by PanelFileBrowser.events.collectAsState()
    val scope = rememberCoroutineScope()
    val navigationState = remember { FileBrowserNavigationState() }
    var stack by navigationState.stack
    val directoryState = remember { FileBrowserDirectoryState() }
    var entries by directoryState.entries
    var visibleEntries by directoryState.visibleEntries
    var entriesLocationKey by directoryState.locationKey
    val loading by directoryState.loading
    var error by directoryState.error
    var message by remember { mutableStateOf<String?>(null) }
    var navigation by navigationState.section
    val searchFocusRequester = remember { FocusRequester() }
    val filterFocusRequester = remember { FocusRequester() }
    var searchOpen by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var searchSubdirectories by remember { mutableStateOf(false) }
    var searchRegex by remember { mutableStateOf(false) }
    var searchOptionsMenu by remember { mutableStateOf(false) }
    val searchController = remember(scope) { FileBrowserSearchController(scope) }
    val advancedResults by searchController.results
    val finding by searchController.finding
    val findError by searchController.error
    val findProgress by searchController.progress
    var rootWorkspaceTarget by remember { mutableStateOf<String?>(null) }
    var rootScopeTarget by remember { mutableStateOf<BrowserLocation?>(null) }
    var rootActionBusy by remember { mutableStateOf(false) }
    var actionSheet by remember { mutableStateOf<String?>(null) }
    var contextEntry by remember { mutableStateOf<BrowserEntry?>(null) }
    var pathInputOpen by remember { mutableStateOf(false) }
    var pathInput by remember { mutableStateOf("") }
    var pathInputError by remember { mutableStateOf<String?>(null) }
    var pathInputBusy by remember { mutableStateOf(false) }
    var pathInputVersion by remember { mutableIntStateOf(0) }
    var pathInputJob by remember { mutableStateOf<Job?>(null) }
    var searchFocused by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var managerPage by remember { mutableStateOf<String?>(null) }
    var managerEntry by remember { mutableStateOf<BrowserEntry?>(null) }
    var managerEntries by remember { mutableStateOf<List<BrowserEntry>>(emptyList()) }
    var managerAction by remember { mutableStateOf<String?>(null) }
    var analyzerSavedSection by remember { mutableStateOf("overview") }
    var analyzerSavedCategory by remember { mutableStateOf<FileCategory?>(null) }
    var returnToManagerPage by remember { mutableStateOf<String?>(null) }
    val transferClipboard = sharedClipboard
    var transferBusy by remember { mutableStateOf(false) }
    var transferJob by remember { mutableStateOf<Job?>(null) }
    var editingText by remember { mutableStateOf(false) }
    var editorBack by remember { mutableStateOf<(() -> Unit)?>(null) }
    var managerBack by remember { mutableStateOf<(() -> Unit)?>(null) }
    var managerWindowClose by remember { mutableStateOf<FileWindowCloseRequest?>(null) }
    var managerEditing by remember { mutableStateOf(false) }
    var editorWindowClose by remember { mutableStateOf<FileWindowCloseRequest?>(null) }
    val requestWindowClose by rememberUpdatedState<FileWindowCloseRequest> { allowed, cancelled ->
        if (!workspaceBusy && !transferBusy && !rootActionBusy && !pathInputBusy) {
            when {
                managerPage != null -> managerWindowClose?.invoke(allowed, cancelled) ?: cancelled()
                editingText -> editorWindowClose?.invoke(allowed, cancelled) ?: cancelled()
                else -> allowed()
            }
        } else cancelled()
    }
    val closeReadingWindow by androidx.compose.runtime.rememberUpdatedState<() -> Unit>({
        if (editorBack != null) editorBack?.invoke() else managerBack?.invoke() ?: onDismiss()
    })
    DisposableEffect(Unit) {
        onWindowCloseHandler { allowed, cancelled -> requestWindowClose(allowed, cancelled) }
        onDispose { onWindowCloseHandler(null) }
    }
    DisposableEffect(Unit) {
        onCloseHandlerChanged { closeReadingWindow() }
        onDispose { onCloseHandlerChanged(null) }
    }
    var categoryFilter by remember { mutableStateOf(FileCategory.ALL) }
    var quickFilterOpen by remember { mutableStateOf(false) }
    var quickFilter by remember { mutableStateOf("") }
    var selectingFiles by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf(FileWorkspaceSelection()) }
    val selectedFileKeys = selection.keys
    val selectedRecords = selection.records
    var pendingDeletion by remember { mutableStateOf<FileDeletionPlan?>(null) }
    var selected by remember { mutableStateOf<BrowserEntry?>(null) }
    var previewActionsOpen by remember { mutableStateOf(false) }
    val previewMode = selected != null && managerPage == null && !editingText
    LaunchedEffect(previewMode) { onPreviewChanged(previewMode) }
    DisposableEffect(paneId) { onDispose { onPreviewChanged(false) } }
    LaunchedEffect(selected?.location?.key, editingText) { previewActionsOpen = false }
    var preview by remember { mutableStateOf<BrowserPreview?>(null) }
    var previewError by remember { mutableStateOf<String?>(null) }
    var previewLoading by remember { mutableStateOf(false) }
    var externalBusy by remember { mutableStateOf(false) }
    fun openSelectedExternally(file: BrowserEntry, share: Boolean) {
        if (externalBusy) return
        scope.launch {
            externalBusy = true
            try { io.launchExternal(file, share)?.let { message = it } }
            finally { externalBusy = false }
        }
    }
    var readableRoots by remember { mutableStateOf<Set<String>>(emptySet()) }
    var checkingRoots by remember { mutableStateOf(true) }
    val previewRequests = remember { AtomicLong() }
    val location = stack.lastOrNull()
    val currentWritable by produceState(false, location?.key, refresh, panelExpanded, rootSessionStatus) {
        value = panelExpanded && withContext(Dispatchers.IO) { location?.let { fileManager.canWrite(it) } == true }
    }
    val listStates = remember { mutableMapOf<String, LazyListState>() }
    val fileListState = remember(location?.key) { listStates.getOrPut(location?.key ?: "roots") { LazyListState() } }
    val rowDensity = LocalDensity.current
    var measuredRowHeight by remember(compact, rowDensity.fontScale, selectingFiles) { mutableFloatStateOf(if (compact) 44f else 48f) }

    fun clearFileSearch(closeEditor: Boolean = true) {
        searchController.clear()
        search = ""
        if (closeEditor) {
            searchOpen = false; searchFocused = false
            keyboard?.hide(); focusManager.clearFocus()
        }
    }
    fun updateSearch(value: String) {
        if (value.isBlank()) clearFileSearch(closeEditor = false)
        search = value
    }
    fun navigateTo(value: List<BrowserLocation>) {
        // Navigation is an explicit exit from the previous query, even for the same root.
        clearFileSearch(); error = null; message = null
        val changed = navigationState.navigate(value)
        if (changed) {
            directoryState.invalidate()
            selection = FileWorkspaceSelection(); selectingFiles = false
            pendingDeletion = null; search = ""; quickFilter = ""; selected = null
        }
    }
    fun openPathInput() {
        pathInput = location?.let(::fileWorkspacePath) ?: fileWorkspacePath(io.sharedLocation())
        pathInputError = null; pathInputOpen = true
    }
    fun closePathInput() {
        pathInputVersion++; pathInputJob?.cancel(); pathInputBusy = false; pathInputOpen = false
    }
    fun submitPathInput() {
        if (pathInputBusy) return
        val plan = try { FileWorkspacePathPolicy.plan(pathInput, stack,
            listOf(io.sharedLocation()) + config.roots.mapNotNull { runCatching { io.rootLocation(it) }.getOrNull() } +
                if (rootSessionStatus == RootStatus.Granted) listOf(BrowserLocation("/", path = "/", rootAccess = true)) else emptyList()) }
            catch (e: IllegalArgumentException) { pathInputError = e.message; return }
        val request = ++pathInputVersion
        pathInputError = null; pathInputBusy = true
        pathInputJob = scope.launch {
            try {
                val resolved = resolveFileWorkspacePath(plan, io::list)
                if (request == pathInputVersion) { navigateTo(resolved); pathInputOpen = false; keyboard?.hide(); focusManager.clearFocus() }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (request == pathInputVersion) pathInputError = e.message ?: "路径不可访问" }
            finally { if (request == pathInputVersion) pathInputBusy = false }
        }
    }
    SideEffect { onStackChanged(stack); onEditingChanged(editingText || managerEditing || transferBusy || rootActionBusy || pendingDeletion != null) }
    LaunchedEffect(paneActive, paneVisible, panelExpanded) {
        if (!paneActive || !paneVisible || !panelExpanded) {
            actionSheet = null
            pendingDeletion = null
            if (pathInputOpen) closePathInput()
        }
    }
    LaunchedEffect(requestedStack, requestVersion) { requestedStack?.let { navigateTo(it); selected = null } }
    LaunchedEffect(receipt?.id) {
        receipt?.takeIf { it.sourcePane == paneId }?.let {
            selection = selection.without(it.completed)
            if (selection.keys.isEmpty()) selectingFiles = false
        }
    }
    LaunchedEffect(message) {
        val notice = message
        if (notice?.startsWith("已收藏") == true) {
            delay(3500)
            if (message == notice) message = null
        }
    }
    LaunchedEffect(searchOpen, quickFilterOpen, navigation, paneActive, paneVisible, panelExpanded) {
        if (navigation == "目录" && paneActive && paneVisible && panelExpanded && !selectingFiles) {
            // Wait for the editor node to be attached before requesting focus/IME.
            androidx.compose.runtime.withFrameNanos { }
            if (quickFilterOpen) filterFocusRequester.requestFocus()
            else if (searchOpen) searchFocusRequester.requestFocus()
            if (quickFilterOpen || searchOpen) keyboard?.show()
        }
    }
    LaunchedEffect(workspaceRefresh) { refresh++ }
    LaunchedEffect(panelExpanded) { if (!panelExpanded) transferJob?.cancel() }
    LaunchedEffect(rootSessionStatus) {
        if (rootSessionStatus != RootStatus.Granted) { rootWorkspaceTarget = null; rootScopeTarget = null }
        if ((location?.rootAccess == true || selected?.location?.rootAccess == true) && rootSessionStatus != RootStatus.Granted) {
            directoryState.invalidate(); previewRequests.incrementAndGet()
            selected = null; preview = null; previewError = null
            managerPage = null; editingText = false; editorBack = null
            searchController.invalidate()
            entries = emptyList(); visibleEntries = emptyList(); io.invalidateAllDirectories(); error = "Root会话已结束，请返回root入口手动授权"
        }
    }

    fun findFiles(cursor: FileSearchCursor? = null, preserveResults: Boolean = false) {
        val directory = location ?: return
        if (!paneVisible || !panelExpanded) return
        if (search.isBlank()) { clearFileSearch(closeEditor = false); return }
        searchController.start(cursor, preserveResults, search = { report ->
            fileManager.search(directory, search.trim(), categoryFilter, cursor, searchSubdirectories, searchRegex) {
                report("${it.visited}项 · ${it.current}")
            }
        }, onFirstPage = { fileListState.scrollToItem(0) })
    }

    LaunchedEffect(search, searchSubdirectories, searchRegex, categoryFilter, location?.key, panelExpanded, paneVisible, searchOpen, rootSessionStatus) {
        val generation = searchController.invalidate()
        if (searchOpen && searchSubdirectories && search.isNotBlank() && location != null && paneVisible && panelExpanded) {
            delay(400)
            if (searchController.isCurrent(generation)) findFiles()
        }
    }

    // Receipts refresh the existing query without discarding its published page.
    LaunchedEffect(refresh) {
        searchController.refreshWhenSettled(
            eligible = { searchOpen && search.isNotBlank() && paneVisible && panelExpanded },
            restart = { findFiles(preserveResults = true) })
    }

    fun submitFileSearch() {
        if (finding) return
        keyboard?.hide()
        focusManager.clearFocus()
        searchFocused = false
        findFiles()
    }

    val goBack: () -> Unit = {
        when {
            workspaceBusy -> { message = "正在传输，可点上方取消" }
            editorBack != null -> editorBack?.invoke()
            managerBack != null -> managerBack?.invoke()
            pathInputOpen -> closePathInput()
            actionSheet != null -> actionSheet = null
            pendingDeletion != null -> pendingDeletion = null
            selectingFiles -> { selectingFiles = false; selection = FileWorkspaceSelection() }
            managerPage != null -> { managerPage = null; returnToManagerPage = null }
            selected != null -> {
                selected = null
                if (returnToManagerPage != null) {
                    managerPage = returnToManagerPage
                    returnToManagerPage = null
                }
            }
            quickFilterOpen -> { quickFilterOpen = false; quickFilter = ""; keyboard?.hide(); focusManager.clearFocus() }
            searchOpen -> { searchOpen = false; search = ""; keyboard?.hide(); focusManager.clearFocus() }
            navigation !in setOf("目录", "常用") -> navigation = if (location != null) "目录" else "常用"
            navigation == "常用" && location != null -> navigation = "目录"
            stack.isNotEmpty() -> { navigateTo(navigationState.parentStack()); search = "" }
            else -> onDismiss()
        }
    }
    val latestBack by rememberUpdatedState<() -> Unit> {
        if (actionSheet != null) goBack()
        else if (searchFocused) {
            keyboard?.hide()
            focusManager.clearFocus()
        } else goBack()
    }
    DisposableEffect(paneActive) {
        val uninstall = if (paneActive) PanelFileBrowser.installBackHandler { latestBack() } else null
        onDispose { uninstall?.invoke(); if (paneActive) { keyboard?.hide(); focusManager.clearFocus() } }
    }
    LaunchedEffect(initialFile?.id, initialFile?.intentUri) {
        initialFile?.let { item ->
            selected = try { withContext(Dispatchers.IO) { io.singleDocumentEntry(item.intentUri, item.title) } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                BrowserEntry(BrowserLocation(item.title, singleDocumentUri = item.intentUri), item.title, false, "application/octet-stream", null, null)
            }
        }
    }
    LaunchedEffect(location?.key, selected?.location?.key) {
        val entry = selected ?: location?.let { BrowserEntry(it, it.title, true, "vnd.android.document/directory", null, null) }
        entry?.let { opened ->
            val saved = opened.location.bookmark(opened.isDirectory, opened.mimeType)
            repository.updateFileBrowserConfig { it.copy(recent = (listOf(saved) + it.recent.filterNot { b -> b.location().key == opened.location.key }).take(30)) }
        }
    }
    LaunchedEffect(Unit) { PanelFileBrowser.consumeReturnToBrowser() }
    LaunchedEffect(event?.id) {
        event?.let { message = it.message; refresh++ }
    }
    LaunchedEffect(config.roots, refresh, panelExpanded) {
        checkingRoots = true
        readableRoots = withContext(Dispatchers.IO) {
            config.roots.filter { io.hasReadGrant(it.uri) }.map { it.uri }.toSet()
        }
        checkingRoots = false
    }
    val returnRefresh = remember { FileBrowserReturnRefreshState(panelExpanded && paneVisible && selected == null) }
    LaunchedEffect(panelExpanded, paneVisible, selected != null) {
        // Preview pauses directory observation without sending a lifecycle resume event.
        // Share the visibility transition so closing a hidden preview cannot schedule two scans.
        if (returnRefresh.update(panelExpanded && paneVisible && selected == null)) {
            location?.let { io.expireDirectory(it) }
            refresh++
        }
    }
    LaunchedEffect(location?.key, refresh, panelExpanded, paneVisible) {
        withContext(Dispatchers.IO) { directoryState.load(location.takeIf { panelExpanded && paneVisible },
            cached = io::cachedDirectory, fresh = io::isDirectoryCacheFresh,
            list = io::list, invalidateCache = io::invalidateDirectory,
            filterCached = { cached ->
                sortBrowserEntries(filterBrowserEntries(cached, if (searchOpen) search else "", searchRegex,
                    config.showHidden, quickFilter, categoryFilter), config.sortOrder, config.sortDescending)
            }) }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var lifecycleStarted by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var directoryObservation by remember(location?.key) { mutableStateOf(DirectoryChangeSignal.UNAVAILABLE) }
    DisposableEffect(lifecycle, location?.key) {
        val observer = LifecycleEventObserver { _, event ->
            lifecycleStarted = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (event == Lifecycle.Event.ON_RESUME) {
                location?.let { io.expireDirectory(it) }; refresh++
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(location?.key, panelExpanded, paneVisible, lifecycleStarted, selected != null, managerPage, navigation) {
        val current = location ?: return@LaunchedEffect
        directoryObservation = DirectoryChangeSignal.UNAVAILABLE
        if (!panelExpanded || !paneVisible || !lifecycleStarted || selected != null || managerPage != null || navigation != "目录") return@LaunchedEffect
        observeBrowserDirectory(context, current, io::checkDirectoryAccess).collect { signal ->
            if (signal == DirectoryChangeSignal.CHANGED || signal == DirectoryChangeSignal.INVALIDATED) {
                if (signal == DirectoryChangeSignal.INVALIDATED) directoryObservation = DirectoryChangeSignal.UNAVAILABLE
                // Conflated notifications bound bursts without polling or restarting the query identity.
                delay(350)
                io.expireDirectory(current); refresh++
            } else directoryObservation = signal
        }
    }
    LaunchedEffect(entries, search, searchRegex, searchSubdirectories, quickFilter, config.sortOrder, config.sortDescending, config.showHidden, categoryFilter, searchOpen) {
        val snapshot = entries
        val query = if (searchOpen) search.trim() else ""
        val instantFilter = quickFilter
        visibleEntries = withContext(Dispatchers.Default) {
            sortBrowserEntries(filterBrowserEntries(snapshot, query, searchRegex, config.showHidden,
                instantFilter, categoryFilter), config.sortOrder, config.sortDescending)
        }
    }
    LaunchedEffect(selected?.location?.key, panelExpanded, refresh, paneActive, paneVisible) {
        val request = previewRequests.incrementAndGet()
        preview = null
        previewError = null
        val entry = selected.takeIf { panelExpanded && paneActive && paneVisible }
        previewLoading = entry != null
        if (entry != null) {
            var pdfCache: java.io.File? = null
            try {
                val loaded = io.preview(entry) { pdfCache = it }
                if (previewRequests.get() == request) preview = loaded
                if (loaded is BrowserPreview.Pdf) {
                    previewLoading = false
                    kotlinx.coroutines.awaitCancellation()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (previewRequests.get() == request) previewError = e.message ?: "文件预览失败，请使用其他应用打开"
            } finally {
                pdfCache?.delete()
                if (currentCoroutineContext().isActive && previewRequests.get() == request) previewLoading = false
            }
        }
    }

    fun requestDeletion(entriesToDelete: Collection<BrowserEntry>) {
        if (transferBusy || pendingDeletion != null) return
        val parent = location ?: entriesToDelete.firstNotNullOfOrNull { it.parentLocation }
        if (parent == null) { message = "无法确认文件所在目录"; return }
        runCatching { FileDeletionPlan.create(entriesToDelete, parent) }
            .onSuccess { pendingDeletion = it }
            .onFailure { message = it.message ?: "无法删除所选项目" }
    }
    fun performEntryAction(entry: BrowserEntry, action: String) {
        if (entry.blockedReason != null) { message = entry.blockedReason; return }
        when (action) {
            "menu" -> { contextEntry = entry; actionSheet = "context" }
            "delete" -> requestDeletion(listOf(entry))
            "edit" -> {
                entry.parentLocation?.takeIf { it.key != location?.key }?.let { navigateTo(listOf(it)) }
                selected = entry; editingText = true
            }
            "select" -> { selectingFiles = true; selection = selection.select(entry) }
            "favorite" -> {
                val saved = entry.location.bookmark(entry.isDirectory, entry.mimeType)
                repository.updateFileBrowserConfig { cfg -> cfg.copy(favorites = listOf(saved) + cfg.favorites.filterNot { it.location().key == entry.location.key }) }
                message = "已收藏 ${entry.name}"
            }
            "copy", "move" -> {
                val parent = entry.parentLocation ?: location
                if (parent == null) message = "请先选择源目录"
                else if (action == "move" && !fileManager.canWrite(parent)) message = "此位置只读，不能移动；可复制"
                else onTransferToOther(TransferClipboard(listOf(entry), parent, action == "move"))
            }
            else -> {
                entry.parentLocation?.takeIf { it.key != location?.key }?.let { navigateTo(listOf(it)) }
                managerEntries = emptyList(); managerEntry = entry; managerAction = action; managerPage = "管理"
            }
        }
    }

    val activeResults = activeFileSearchResult(advancedResults, searchOpen, search)
    val shownEntries = activeResults?.entries?.let { found -> sortBrowserEntries(found.filter {
        (config.showHidden || !it.name.startsWith('.')) && matchesQuickFilter(it.name, quickFilter)
    }, config.sortOrder, config.sortDescending) }
        ?: if (entriesLocationKey == location?.key) visibleEntries else emptyList()

    fun changeNavigation(destination: String) {
        keyboard?.hide(); focusManager.clearFocus(); searchFocused = false
        navigation = if (destination == "目录" && location == null) "常用" else destination
        clearFileSearch(); error = null; message = null
    }
    fun toggleSearch() {
        if (location == null) {
            val target = if (io.hasAllFilesAccess()) io.sharedLocation()
                else config.roots.firstOrNull { io.hasReadGrant(it.uri) }?.let { runCatching { io.rootLocation(it) }.getOrNull() }
            if (target == null) { message = "先添加或授权要查找的目录"; navigation = "常用"; return }
            navigateTo(listOf(target))
        }
        navigation = "目录"; quickFilterOpen = false; searchOpen = !searchOpen
        if (!searchOpen) { search = ""; keyboard?.hide(); focusManager.clearFocus() }
    }
    fun openStorageAnalyzer() {
        val targetStack = when {
            stack.isNotEmpty() -> stack
            io.hasAllFilesAccess() -> listOf(io.sharedLocation())
            else -> {
                val firstReadable = config.roots.firstOrNull { it.uri in readableRoots }
                    ?: config.roots.firstOrNull { io.hasReadGrant(it.uri) }
                if (firstReadable != null) {
                    runCatching { listOf(io.rootLocation(firstReadable)) }.getOrNull()
                } else null
            }
        }
        if (targetStack == null) {
            message = "请先授权「手机存储」或添加一个常用目录后再进行存储分析"
            navigation = "常用"
            return
        }
        if (stack.isEmpty()) stack = targetStack
        managerEntry = null
        managerEntries = emptyList()
        managerAction = null
        analyzerSavedSection = "overview"
        analyzerSavedCategory = null
        returnToManagerPage = null
        managerPage = "分析查找"
    }

    fun openQuickCommonFolder(folderName: String, label: String, initialWildcard: String = "") {
        if (io.hasAllFilesAccess()) {
            val shared = io.sharedLocation()
            val sub = io.sharedSubdirectoryLocation(folderName, "$label ($folderName)")
            if (sub != null) {
                navigateTo(listOf(shared, sub))
                if (initialWildcard.isNotBlank()) {
                    quickFilter = initialWildcard
                    quickFilterOpen = true
                }
            } else {
                navigateTo(listOf(shared))
                if (initialWildcard.isNotBlank()) {
                    quickFilter = initialWildcard
                    quickFilterOpen = true
                } else {
                    message = "未找到 $folderName 目录，已进入手机共享存储根目录"
                }
            }
            return
        }
        val matchedRoot = config.roots.firstOrNull { root ->
            (root.uri in readableRoots || io.hasReadGrant(root.uri)) &&
                (root.title.equals(folderName, ignoreCase = true) ||
                    root.title.contains(folderName, ignoreCase = true) ||
                    root.uri.contains(folderName, ignoreCase = true))
        }
        if (matchedRoot != null) {
            runCatching {
                navigateTo(listOf(io.rootLocation(matchedRoot)))
                if (initialWildcard.isNotBlank()) {
                    quickFilter = initialWildcard
                    quickFilterOpen = true
                }
            }.onFailure { message = it.message ?: "目录书签已失效，请重新授权" }
        } else {
            val fallbackRoot = if (initialWildcard.isNotBlank()) {
                config.roots.firstOrNull { it.uri in readableRoots || io.hasReadGrant(it.uri) }
            } else null
            if (fallbackRoot != null) {
                runCatching {
                    navigateTo(listOf(io.rootLocation(fallbackRoot)))
                    quickFilter = initialWildcard
                    quickFilterOpen = true
                }
            } else {
                message = "请先授权「手机存储」，或点「添加目录」授权 $folderName 文件夹"
            }
        }
    }
    fun openManager(action: String?) { managerEntry = null; managerEntries = emptyList(); managerAction = action; managerPage = "管理" }
    fun bookmarkEntries(work: List<BrowserEntry>) {
        val saved = work.map { it.location.bookmark(it.isDirectory, it.mimeType) }
        val keys = saved.map { it.location().key }.toSet()
        repository.updateFileBrowserConfig { cfg -> cfg.copy(favorites = saved + cfg.favorites.filterNot { it.location().key in keys }) }
        message = "已收藏 ${work.size} 项"
    }
    fun sheetAction(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean = true,
        destructive: Boolean = false, run: () -> Unit) =
        FileWorkspaceAction(title, icon, enabled, destructive) { actionSheet = null; run() }
    val selectionOperable = selectedFileKeys.isNotEmpty() && selectedRecords.values.none { it.location.rootAccess && !RootWritePolicy.canManage(RootFileIo.path(it.location)) }
    val otherSide = if (paneId == 0) "B" else "A"
    val sheetTitle = when (actionSheet) {
        "places" -> "位置与书签"
        "new" -> "新建"
        "sort" -> "排序"
        "type" -> "文件类型"
        "selection" -> "所选 ${selectedFileKeys.size} 项"
        "context" -> contextEntry?.name ?: "文件操作"
        else -> "文件工具"
    }
    val sheetActions: List<FileWorkspaceAction> = when (actionSheet) {
        "places" -> listOf(
            sheetAction("当前目录", Icons.Folder, location != null) { changeNavigation("目录") },
            sheetAction("常用位置", Icons.Storage) { changeNavigation("常用") },
            sheetAction("最近访问", Icons.History) { changeNavigation("最近") },
            sheetAction("收藏", Icons.Checklist) { changeNavigation("收藏") },
            sheetAction("输入路径", Icons.Edit) { openPathInput() },
            sheetAction("收藏当前目录", Icons.Add, location != null) { location?.let { bookmarkEntries(listOf(BrowserEntry(it, it.title, true, "vnd.android.document/directory", null, null))) } })
        "new" -> listOf(
            sheetAction("新建文件夹", Icons.Folder, currentWritable) { openManager("new") },
            sheetAction("新建文本", Icons.EditNote, currentWritable) { openManager("newtext") })
        "sort" -> FileBrowserSortOrder.entries.map { order -> sheetAction((if (config.sortOrder == order) "✓ " else "") + order.label, Icons.Checklist) { repository.updateFileBrowserConfig { it.copy(sortOrder = order) } } }
        "type" -> FileCategory.entries.map { category -> sheetAction((if (categoryFilter == category) "✓ " else "") + category.label, Icons.GridView) { categoryFilter = category; if (location != null) navigation = "目录" } }
        "selection" -> listOf(
            sheetAction("按同后缀全选", Icons.FilterList, selectedRecords.values.any { !it.isDirectory && '.' in it.name }) {
                val exts = selectedRecords.values.filter { !it.isDirectory && '.' in it.name }
                    .map { it.name.substringAfterLast('.').lowercase(Locale.ROOT) }.toSet()
                if (exts.isNotEmpty()) {
                    val matched = shownEntries.filter {
                        it.blockedReason == null && !it.isDirectory &&
                            it.name.substringAfterLast('.', "").lowercase(Locale.ROOT) in exts
                    }
                    selection = FileWorkspaceSelection((selectedRecords + matched.associateBy { it.location.key }))
                }
            },
            sheetAction("收藏所选", Icons.Checklist, selectedFileKeys.isNotEmpty()) { bookmarkEntries(selectedRecords.values.toList()) },
            sheetAction("属性", Icons.Settings, selectedFileKeys.size == 1) { selectedRecords.values.singleOrNull()?.let { performEntryAction(it, "properties") } },
            sheetAction("退出多选", Icons.Close) { selectingFiles = false; selection = FileWorkspaceSelection() })
        "context" -> contextEntry?.let { entry ->
            val writable = currentWritable && entry.blockedReason == null
            val copyable = entry.blockedReason == null && (!entry.location.rootAccess || (!entry.isDirectory && RootWritePolicy.canManage(RootFileIo.path(entry.location))))
            listOf(sheetAction(if (entry.isDirectory) "打开文件夹" else "预览", Icons.Folder) {
                    if (entry.isDirectory) navigateTo(stack + entry.location) else { selected = entry; keyboard?.hide(); focusManager.clearFocus() }
                }) + (if (supportsTextEditing(entry)) listOf(sheetAction("编辑文本", Icons.EditNote) { performEntryAction(entry, "edit") }) else emptyList()) + listOf(
                sheetAction("选择", Icons.Checklist) { performEntryAction(entry, "select") },
                sheetAction("复制到$otherSide", Icons.ContentCopy, copyable) { performEntryAction(entry, "copy") },
                sheetAction("移动到$otherSide", Icons.SwapHoriz, writable) { performEntryAction(entry, "move") },
                sheetAction("重命名", Icons.Edit, writable) { performEntryAction(entry, "rename") },
                sheetAction("删除", Icons.Delete, writable, destructive = true) { performEntryAction(entry, "delete") },
                sheetAction("收藏", Icons.Checklist) { performEntryAction(entry, "favorite") }) +
                (if (!entry.isDirectory) listOf(sheetAction("校验和 (MD5/SHA256)", Icons.Fingerprint) { performEntryAction(entry, "checksum") }) else emptyList()) +
                listOf(sheetAction("属性", Icons.Settings) { performEntryAction(entry, "properties") })
        } ?: emptyList()
        else -> listOf(
            sheetAction("即时过滤 (*.ext)", Icons.FilterList, location != null) { navigation = "目录"; clearFileSearch(); quickFilterOpen = true },
            sheetAction("多选", Icons.Checklist, location != null) { navigation = "目录"; selectingFiles = true },
            sheetAction("排序", Icons.Checklist) { actionSheet = "sort" },
            sheetAction("类型筛选", Icons.GridView, location != null) { actionSheet = "type" },
            sheetAction(if (config.showHidden) "隐藏文件：开" else "隐藏文件：关", Icons.Tune) { repository.updateFileBrowserConfig { it.copy(showHidden = !it.showHidden) } },
            sheetAction("刷新", Icons.Refresh, location != null) { location?.let { io.expireDirectory(it) }; refresh++ },
            sheetAction("存储分析与清理", Icons.Storage, location != null || io.hasAllFilesAccess() || config.roots.isNotEmpty()) { openStorageAnalyzer() }) +
            if (rootSessionStatus == RootStatus.Granted) listOf(sheetAction("Root工作目录", Icons.Folder) { rootWorkspaceTarget = "/data/local/GMO-Root-" + java.util.UUID.randomUUID().toString().replace("-", "") }) else emptyList()
    } + if (actionSheet == "tools" && location?.rootAccess == true && !currentWritable && runCatching { RootWritePolicy.requireSafeDirectory(RootFileIo.path(location)) }.isSuccess)
        listOf(sheetAction("管理当前目录", Icons.Settings) { rootScopeTarget = location }) else emptyList()

    val palette = opaqueFilePalette(LocalPanelPalette.current)
    val containerLow = if (palette.isDark) Color(0xFF262B36) else Color(0xFFFFFFFF)
    val containerMid = if (palette.isDark) Color(0xFF2B313E) else Color(0xFFF1F4F9)
    val containerHigh = if (palette.isDark) Color(0xFF323948) else Color(0xFFE8ECF4)
    val baseBg = if (palette.isDark) Color(0xFF1B1F27) else Color(0xFFF7F8FC)
    val browserNotices: @Composable () -> Unit = {
        message?.takeUnless { notice -> (managerPage != null || editorBack != null) &&
            (notice.startsWith("已添加") || notice.startsWith("已保存") || notice == "已授权手机共享存储")
        }?.let { notice ->
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(notice, Modifier.weight(1f), maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                IconButton(onClick = { message = null }, modifier = Modifier.size(48.dp)) { Icon(Icons.Close, "关闭提示", Modifier.size(18.dp)) }
            }
        }
        error?.let { notice ->
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(notice, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                IconButton(onClick = {
                    if (location?.rootAccess == true) navigateTo(emptyList())
                    else if (location?.rootUri == null) PanelFileBrowser.requestAllFiles(context)
                    else location?.rootUri?.let { PanelFileBrowser.requestDirectory(context, it) }
                }, modifier = Modifier.size(40.dp)) {
                    Icon(if (location?.rootAccess == true) Icons.ArrowBack else Icons.Storage,
                        if (location?.rootAccess == true) "返回Root入口" else "重新授权目录", Modifier.size(18.dp))
                }
                IconButton(onClick = { error = null }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Close, "关闭目录错误", Modifier.size(18.dp))
                }
            }
        }
    }
    SettingsTheme(darkTheme = palette.isDark) {
        MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(
            primary = palette.accent,
            primaryContainer = palette.accent.copy(alpha = if (palette.isDark) 0.24f else 0.14f),
            onPrimaryContainer = palette.textPrimary,
            background = baseBg,
            surface = baseBg,
            surfaceContainerLowest = palette.cardBackground,
            surfaceContainerLow = containerLow,
            surfaceContainer = containerMid,
            surfaceContainerHigh = containerHigh,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
            onSurfaceVariant = palette.textSecondary,
            secondaryContainer = palette.accent.copy(alpha = if (palette.isDark) 0.18f else 0.11f),
            onSecondaryContainer = palette.textPrimary,
            outlineVariant = palette.cardBorder
        )) {
        Box(Modifier.fillMaxSize()) {
        Surface(Modifier.fillMaxSize().clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}
        ), color = baseBg, contentColor = palette.textPrimary) {
            Column(Modifier.fillMaxSize()) {
                when {
                    // Management owns its path, selection and operation toolbar. A second
                    // wrapper toolbar consumes the file rows in the normal floating height.
                    managerPage != null -> Unit
                    selectingFiles && selected == null && managerPage == null -> {
                        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).background(palette.accent.copy(alpha = .09f)), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { selectingFiles = false; selection = FileWorkspaceSelection() }) { Icon(Icons.Close, "退出多选", Modifier.size(20.dp)) }
                            Text("已选 ${selectedFileKeys.size} 项", Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            if (selectedRecords.values.any { !it.isDirectory && '.' in it.name }) {
                                TextButton(onClick = {
                                    val exts = selectedRecords.values.filter { !it.isDirectory && '.' in it.name }
                                        .map { it.name.substringAfterLast('.').lowercase(Locale.ROOT) }.toSet()
                                    val matched = shownEntries.filter {
                                        it.blockedReason == null && !it.isDirectory &&
                                            it.name.substringAfterLast('.', "").lowercase(Locale.ROOT) in exts
                                    }
                                    selection = FileWorkspaceSelection(selectedRecords + matched.associateBy { it.location.key })
                                }, modifier = Modifier.heightIn(min = 40.dp), contentPadding = PaddingValues(horizontal = 6.dp)) { Text("同后缀", fontSize = 11.sp) }
                            }
                            TextButton(onClick = { selection = FileWorkspaceSelection(shownEntries.filter { it.blockedReason == null }.associateBy { it.location.key }) }, modifier = Modifier.width(44.dp).heightIn(min = 40.dp), contentPadding = PaddingValues(0.dp)) { Text("全选", fontSize = 12.sp) }
                            TextButton(onClick = { selection = FileWorkspaceSelection(shownEntries.filter { it.blockedReason == null && it.location.key !in selectedFileKeys }.associateBy { it.location.key }) }, modifier = Modifier.width(44.dp).heightIn(min = 40.dp), contentPadding = PaddingValues(0.dp)) { Text("反选", fontSize = 12.sp) }
                        }
                    }
                    selected != null -> {
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = goBack) { Icon(Icons.ArrowBack, "返回文件列表", Modifier.size(20.dp)) }
                            Text(selected?.name ?: managerPage.orEmpty(), Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            selected?.takeUnless { editingText || managerPage != null }?.let { file ->
                                Box {
                                    IconButton(onClick = { previewActionsOpen = true }, modifier = Modifier.size(48.dp)) {
                                        Icon(Icons.MoreVert, "预览更多操作", Modifier.size(20.dp))
                                    }
                                    DropdownMenu(expanded = previewActionsOpen, onDismissRequest = { previewActionsOpen = false }) {
                                        com.example.globalminusone.ui.Android12WindowKeyGuard()

                                        if (supportsTextEditing(file)) DropdownMenuItem(
                                            text = { Text("编辑文本") }, onClick = { previewActionsOpen = false; editingText = true })
                                        DropdownMenuItem(text = { Text("其他应用") }, enabled = !externalBusy,
                                            onClick = { previewActionsOpen = false; openSelectedExternally(file, false) })
                                        DropdownMenuItem(text = { Text("分享") }, enabled = !externalBusy,
                                            onClick = { previewActionsOpen = false; openSelectedExternally(file, true) })
                                    }
                                }
                            }
                            if (showCloseControl) IconButton(onClick = { requestWindowClose(onDismiss, {}) }) { Icon(Icons.Close, "关闭文件浏览") }
                        }
                    }
                    // The search field already identifies its scope. Repeating the
                    // path/status toolbar here can leave no space for result rows
                    // at the normal floating-window height.
                    else -> if (!searchOpen || navigation != "目录" || location == null) FileWorkspacePathBar(
                        stack = if (navigation == "目录") stack else emptyList(), title = navigation,
                        status = if (navigation == "目录" && location != null) "${browserEntryCountLabel(shownEntries, search, quickFilter, categoryFilter, activeResults != null)} · ${if (currentWritable) "可读写" else "只读"}${if (location.rootAccess) " · Root" else ""}${if (quickFilter.isNotBlank()) " · 过滤:$quickFilter" else ""}" else "",
                        onBack = goBack, onSegment = { navigateTo(stack.take(it + 1)) }, onInput = { openPathInput() },
                        quickFilterActive = quickFilterOpen || quickFilter.isNotBlank(),
                        onQuickFilter = { quickFilterOpen = !quickFilterOpen; if (quickFilterOpen) clearFileSearch() else quickFilter = "" })
                }
                val inlineNotices = selected == null && managerPage == null && navigation == "目录" && location != null
                if (!inlineNotices) browserNotices()
                HorizontalDivider()
                if (rootWorkspaceTarget != null || rootScopeTarget != null) {
                    Column(Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState()).padding(8.dp)) {
                        Text(if (rootWorkspaceTarget != null) "创建并启用 Root 工作目录：${rootWorkspaceTarget}。授权仅在本次会话有效。"
                            else "允许本次 Root 会话管理 ${rootScopeTarget?.path} 及其子目录？", fontSize = 11.sp)
                        Row {
                            TextButton(onClick = { rootWorkspaceTarget = null; rootScopeTarget = null }, enabled = !rootActionBusy) { Text("取消") }
                            TextButton(onClick = {
                                val target = rootWorkspaceTarget; val existing = rootScopeTarget; rootActionBusy = true
                                scope.launch {
                                    try {
                                        if (target != null) navigateTo(listOf(RootFileIo.createWorkspace(target)))
                                        else RootFileIo.allowDirectory(requireNotNull(existing))
                                        refresh++; rootWorkspaceTarget = null; rootScopeTarget = null
                                        message = "Root工作目录已启用；不支持危险递归操作"
                                    } catch (e: CancellationException) { throw e }
                                    catch (e: Exception) { message = e.message ?: "Root目录操作失败" }
                                    finally { rootActionBusy = false }
                                }
                            }, enabled = !rootActionBusy) { Text("确认此范围") }
                        }
                    }
                }
                val file = selected
                if (managerPage != null) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        FileManagementPanel(repository, io, location, initialStack = stack, initialTab = managerPage!!,
                            initialEntry = managerEntry, initialAction = managerAction,
                            initialAnalyzerSection = analyzerSavedSection,
                            initialDrillCategory = analyzerSavedCategory,
                            onAnalyzerStateChanged = { sec, cat -> analyzerSavedSection = sec; analyzerSavedCategory = cat },
                            onBackHandler = { managerBack = it },
                            onWindowCloseHandler = { managerWindowClose = it }, onEditingChanged = { managerEditing = it },
                            initialEntries = managerEntries,
                            clipboardState = transferClipboard,
                            onClose = { updated -> navigateTo(updated); managerPage = null; returnToManagerPage = null; managerEntry = null; managerEntries = emptyList(); managerAction = null },
                            onOpen = { entry, updated ->
                                val previousManagerPage = managerPage
                                navigateTo(if (entry.isDirectory) updated + entry.location else updated)
                                managerPage = null; managerEntry = null; managerAction = null
                                if (entry.isDirectory) {
                                    returnToManagerPage = null
                                } else {
                                    returnToManagerPage = previousManagerPage?.takeIf { it == "分析查找" }
                                    selected = entry
                                }
                            },
                            onChanged = { refresh++; onChanged() })
                    }
                } else if (file != null) {
                    if (initialFile != null && file.location.singleDocumentUri != null) {
                        TextButton(modifier = if (compact) Modifier.height(32.dp) else Modifier, onClick = {
                            val latest = repository.state.value
                            val page = latest.pages.firstOrNull { page -> page.components.findComponent(initialFile.id) != null }
                            if (latest.advancedConfig.layoutLocked) message = "布局已锁定，请先解锁后重新选择"
                            else if (page == null) message = "原文件卡片已删除"
                            else { fileFolderReturn?.clear(); PanelFileBrowser.requestFile(context, page.id, initialFile.id) }
                        }) { Text("重选文件/请求读写权限") }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (editingText) FileTextEditor(fileManager, file, location,
                        onClose = { editingText = false }, onSaved = { editingText = false; refresh++; message = "已保存 UTF-8 文本" },
                        installBack = { editorBack = it }, installWindowClose = { editorWindowClose = it })
                    else
                    FilePreviewContent(
                        file, preview, previewLoading, previewError, io, panelExpanded && paneActive && paneVisible,
                        onCancel = {
                            selected = null
                            if (returnToManagerPage != null) {
                                managerPage = returnToManagerPage
                                returnToManagerPage = null
                            }
                        },
                        compact = compact
                    )
                    }
                } else if (navigation == "最近" || navigation == "收藏") {
                    val bookmarks = if (navigation == "最近") config.recent else config.favorites
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(8.dp)) {
                        if (bookmarks.isEmpty()) item { Text(if (navigation == "最近") "暂无最近访问" else "暂无收藏；可从文件菜单收藏文件或目录", Modifier.fillMaxWidth().padding(24.dp), color = palette.textSecondary) }
                        items(bookmarks.distinctBy { it.location().key }, key = { it.location().key }) { saved ->
                            val entry = saved.entry()
                            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable {
                                search = ""
                                if (entry.isDirectory) { navigateTo(listOf(entry.location)); navigation = "目录" }
                                else selected = entry
                            }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (entry.isDirectory) Icons.Folder else Icons.Storage, null, tint = palette.accent)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(saved.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                    Text(entry.location.path ?: entry.location.title, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = palette.textSecondary)
                                }
                                if (navigation == "收藏") IconButton(onClick = { repository.updateFileBrowserConfig { cfg -> cfg.copy(favorites = cfg.favorites.filterNot { it == saved }) } }) { Icon(Icons.Close, "移除收藏") }
                            }
                            HorizontalDivider(color = palette.cardBorder.copy(alpha = .3f))
                        }
                    }
                } else if (navigation == "常用" || location == null) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                    FileBrowserRoots(
                        config = config,
                        io = io,
                        readableRoots = readableRoots,
                        checkingRoots = checkingRoots,
                        onOpen = { root ->
                            try { navigateTo(listOf(io.rootLocation(root))); navigation = "目录"; search = "" }
                            catch (e: Exception) { message = e.message ?: "目录书签无效，请重新授权" }
                        },
                        onOpenShared = {
                            if (io.hasAllFilesAccess()) { navigateTo(listOf(io.sharedLocation())); navigation = "目录" }
                            else message = "点击「去授权」开启共享存储访问，或添加下方常用目录"
                        },
                        onOpenRoot = { root -> navigateTo(listOf(root)); navigation = "目录"; search = "" },
                        onOpenQuickFolder = { folder, label, wildcard -> openQuickCommonFolder(folder, label, wildcard) },
                        onOpenStorageAnalyzer = { openStorageAnalyzer() },
                        onOpenFavorites = { changeNavigation("收藏") },
                        onOpenRecent = { changeNavigation("最近") },
                        onAuthorizeShared = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) PanelFileBrowser.requestAllFiles(context)
                            else PanelFileBrowser.requestDirectory(context)
                        },
                        onAdd = { PanelFileBrowser.requestDirectory(context) },
                        onReauthorize = { PanelFileBrowser.requestDirectory(context, it.uri) },
                        onRemove = { root -> repository.updateFileBrowserConfig { it.copy(roots = it.roots.filterNot { bookmark -> bookmark.uri == root.uri }) } }
                    )
                    }
                } else {
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    var editorHeightDp by remember { mutableFloatStateOf(0f) }
                    val selectionColumns = fileBrowserToolbarColumns(maxWidth.value, rowDensity.fontScale, 4)
                    var selectionToolbarHeightDp by remember(selectionColumns, rowDensity.fontScale) {
                        mutableFloatStateOf(((4 + selectionColumns - 1) / selectionColumns) * 56f * rowDensity.fontScale.coerceAtLeast(1f))
                    }
                    val controlsHeight = fileBrowserControlsHeightDp(maxHeight.value, selectingFiles, measuredRowHeight,
                        editorHeightDp, selectionToolbarHeightDp).dp
                    Column(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxWidth().onSizeChanged { editorHeightDp = with(rowDensity) { it.height.toDp().value } }) {
                    if ((quickFilterOpen || quickFilter.isNotBlank()) && !selectingFiles) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = quickFilter,
                                    onValueChange = { quickFilter = it },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).heightIn(min = 44.dp).focusRequester(filterFocusRequester),
                                    textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    placeholder = { Text("当前目录过滤：名称或 *.apk", fontSize = 11.sp, maxLines = 1) },
                                    leadingIcon = { Icon(Icons.FilterList, null, Modifier.size(16.dp)) },
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            quickFilter = ""; quickFilterOpen = false
                                            keyboard?.hide(); focusManager.clearFocus()
                                        }, modifier = Modifier.size(40.dp)) {
                                            Icon(Icons.Close, "取消即时过滤", Modifier.size(18.dp))
                                        }
                                    }
                                )
                            }
                            LazyRow(
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val presets = listOf(
                                    "全部" to "",
                                    "安装包" to "*.apk,*.aab",
                                    "压缩包" to "*.zip,*.7z,*.rar,*.tar,*.gz",
                                    "图片" to "*.jpg,*.jpeg,*.png,*.webp,*.gif",
                                    "音视频" to "*.mp4,*.mkv,*.mp3,*.flac,*.wav",
                                    "文本/代码" to "*.txt,*.md,*.json,*.xml,*.log"
                                )
                                items(presets) { (label, expr) ->
                                    val activeChip = quickFilter.trim() == expr
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (activeChip) palette.accent.copy(alpha = 0.16f) else palette.cardBorder.copy(alpha = 0.2f),
                                        modifier = Modifier.clickable { quickFilter = expr }
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 10.sp,
                                            color = if (activeChip) palette.accent else palette.textSecondary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (searchOpen && !selectingFiles) {
                    OutlinedTextField(
                        value = search, onValueChange = { updateSearch(it) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester).then(if (compact) Modifier.heightIn(min = 56.dp) else Modifier).padding(horizontal = 8.dp).onFocusChanged { searchFocused = it.isFocused },
                        keyboardOptions = KeyboardOptions(autoCorrect = false, imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submitFileSearch() }),
                        label = { Text("${if (searchSubdirectories) "含子目录·输入后自动查找" else "当前目录"} · ${if (searchRegex) "正则RE2" else "普通"} · ${location.title}", fontSize = 10.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = if (compact) 12.sp else 16.sp),
                        placeholder = { Text("搜索名称", maxLines = 1, fontSize = if (compact) 12.sp else 16.sp) },
                        leadingIcon = { IconButton(onClick = { submitFileSearch() }, enabled = !finding) { Icon(Icons.Search, "开始文件查找") } },
                        trailingIcon = { Box {
                            TextButton(onClick = { searchOptionsMenu = true }) { Text("选项", fontSize = if (compact) 12.sp else 14.sp) }
                            DropdownMenu(searchOptionsMenu, { searchOptionsMenu = false }) {
                                com.example.globalminusone.ui.Android12WindowKeyGuard()

                                DropdownMenuItem(text = { Text("${if (searchSubdirectories) "✓ " else ""}包含子目录") }, onClick = { searchSubdirectories = !searchSubdirectories })
                                DropdownMenuItem(text = { Text("${if (searchRegex) "✓ " else ""}正则模式 RE2") }, onClick = { searchRegex = !searchRegex })
                                DropdownMenuItem(text = { Text("清空关键词") }, onClick = { updateSearch(""); searchOptionsMenu = false })
                            }
                        } }
                    )
                    }
                    }
                    val controlsScroll = rememberScrollState()
                    Column(Modifier.fillMaxWidth().heightIn(max = controlsHeight)
                        .ownPanelScrollGestures(true, canScrollVertically = { dy -> fileListCanScroll(dy, controlsScroll.canScrollBackward, controlsScroll.canScrollForward) })
                        .verticalScroll(controlsScroll)) {
                    if (!selectingFiles) {
                        Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (directoryObservation == DirectoryChangeSignal.LOCAL_READY) "正在监听当前目录变更"
                                else if (directoryObservation == DirectoryChangeSignal.PROVIDER_READY) "已监听目录通知；未同步时请刷新"
                                else "此位置无法自动同步，请手动刷新", Modifier.weight(1f), fontSize = 10.sp,
                                color = palette.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = { location.let { io.expireDirectory(it) }; refresh++ }) { Text("刷新", fontSize = 11.sp) }
                        }
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            item(key = "sort-chip") {
                                Surface(
                                    onClick = {
                                        val orders = FileBrowserSortOrder.entries
                                        val nextOrder = orders[(orders.indexOf(config.sortOrder) + 1) % orders.size]
                                        repository.updateFileBrowserConfig { it.copy(sortOrder = nextOrder) }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    border = BorderStroke(0.8.dp, palette.accent.copy(alpha = 0.35f))
                                ) {
                                    Text(
                                        text = "⇅ ${config.sortOrder.label}",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = palette.accent,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            item(key = "sort-dir-chip") {
                                val dirLabel = when (config.sortOrder) {
                                    FileBrowserSortOrder.NAME -> if (config.sortDescending) "↓ Z→A" else "↑ A→Z"
                                    FileBrowserSortOrder.MODIFIED -> if (config.sortDescending) "↑ 旧→新" else "↓ 新→旧"
                                    FileBrowserSortOrder.SIZE -> if (config.sortDescending) "↑ 小→大" else "↓ 大→小"
                                }
                                Surface(
                                    onClick = {
                                        repository.updateFileBrowserConfig { it.copy(sortDescending = !it.sortDescending) }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (config.sortDescending) palette.accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainer,
                                    border = BorderStroke(0.8.dp, palette.accent.copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        text = dirLabel,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = palette.accent,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            val quickCategories = listOf(
                                "全部" to FileCategory.ALL,
                                "图片" to FileCategory.IMAGE,
                                "视频" to FileCategory.VIDEO,
                                "音频" to FileCategory.AUDIO,
                                "文档" to FileCategory.DOCUMENT,
                                "压缩包" to FileCategory.ARCHIVE
                            )
                            items(quickCategories, key = { "cat-${it.second.name}" }) { (label, cat) ->
                                val selectedCat = categoryFilter == cat && quickFilter.isBlank()
                                Surface(
                                    onClick = {
                                        categoryFilter = cat
                                        if (cat != FileCategory.ALL) quickFilter = ""
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selectedCat) palette.accent.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainer,
                                    border = if (selectedCat) BorderStroke(0.8.dp, palette.accent) else null
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 10.5.sp,
                                        fontWeight = if (selectedCat) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (selectedCat) palette.accent else palette.textSecondary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            item(key = "apk-filter-chip") {
                                val apkActive = quickFilter.trim() == "*.apk,*.aab"
                                Surface(
                                    onClick = {
                                        categoryFilter = FileCategory.ALL
                                        quickFilter = if (apkActive) "" else "*.apk,*.aab"
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (apkActive) Color(0xFF34A853).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainer,
                                    border = if (apkActive) BorderStroke(0.8.dp, Color(0xFF34A853)) else null
                                ) {
                                    Text(
                                        text = "安装包",
                                        fontSize = 10.5.sp,
                                        fontWeight = if (apkActive) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (apkActive) Color(0xFF34A853) else palette.textSecondary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            item(key = "wildcard-toggle-chip") {
                                val wildcardActive = quickFilterOpen || (quickFilter.isNotBlank() && quickFilter.trim() != "*.apk,*.aab")
                                Surface(
                                    onClick = { quickFilterOpen = !quickFilterOpen; if (quickFilterOpen) clearFileSearch() else quickFilter = "" },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (wildcardActive) palette.accent.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainer
                                ) {
                                    Text(
                                        text = "*.ext过滤",
                                        fontSize = 10.5.sp,
                                        color = if (wildcardActive) palette.accent else palette.textSecondary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            item(key = "analyze-dir-chip") {
                                Surface(
                                    onClick = { openStorageAnalyzer() },
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    Text(
                                        text = "存储分析",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = palette.accent,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                    if (finding) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(findProgress.ifBlank { "查找中…" }, Modifier.weight(1f).padding(horizontal = 8.dp), fontSize = 10.sp, maxLines = 1)
                        TextButton(onClick = { searchController.cancelByUser() }) { Text("取消查找") }
                    }
                    findError?.let { Text(it, Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.error, fontSize = 11.sp, maxLines = 3) }
                    activeResults?.takeUnless { compact && selectingFiles }?.let { result ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (compact) "${result.entries.size}项 · ${if (result.limited) "还有更多" else "遍历结束"} · 不可读${result.skippedDirectories}"
                                else "本页${result.entries.size}项 · ${if (result.limited) "还有更多" else "遍历结束"} · 不可访问目录${result.skippedDirectories}",
                                Modifier.weight(1f).padding(start = 8.dp), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            result.continuation?.let { cursor -> TextButton(onClick = { findFiles(cursor) }, enabled = !finding) { Text("继续查找", fontSize = 11.sp) } }
                            if (compact) IconButton(onClick = { clearFileSearch() }) { Icon(Icons.Close, "返回目录", Modifier.size(18.dp)) }
                            else TextButton(onClick = { clearFileSearch() }) { Text("返回目录") }
                        }
                    }
                    activeResults?.limitReason?.let { Text("$it；扫描未完成，请继续查找", Modifier.padding(horizontal = 8.dp), fontSize = 10.sp) }
                    if (transferBusy) TextButton(onClick = { transferJob?.cancel() }) { Text("取消文件操作") }
                    }
                    LazyColumn(Modifier.weight(1f).fillMaxWidth()
                        .ownPanelScrollGestures(true, canScrollVertically = { dy -> fileListCanScroll(dy, fileListState.canScrollBackward, fileListState.canScrollForward) }),
                        state = fileListState, contentPadding = PaddingValues(bottom = 16.dp)) {
                        if (message != null || error != null) item(key = "directory-notices") { browserNotices() }
                        when {
                            loading -> item(key = "loading") { Box(Modifier.fillMaxWidth().heightIn(min = 140.dp)) { BrowserMessage("正在读取目录…", true, "取消加载", goBack) } }
                            searchOpen && searchSubdirectories && search.isNotBlank() && activeResults == null ->
                                item(key = "search-status") { Box(Modifier.fillMaxWidth().heightIn(min = 140.dp)) { BrowserMessage(if (finding) "正在查找子目录…" else "子目录查找尚未完成，请提交或重试", finding) } }
                            shownEntries.isEmpty() -> item(key = "empty") {
                                Text(if (activeResults?.limited == true) "扫描尚未完成，请点「继续查找」；当前批次没有匹配结果"
                                    else if (error != null) "无法读取此目录，可返回上级或刷新重试"
                                    else fileBrowserEmptyMessage(entries, config.showHidden, search, quickFilter, categoryFilter, activeResults != null),
                                    Modifier.fillMaxWidth().padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            else -> {
                                if (!compact) item(key = "entry-count") { Text(browserEntryCountLabel(shownEntries, search, quickFilter, categoryFilter, activeResults != null), Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                items(shownEntries, key = { it.location.key }, contentType = { if (it.isDirectory) "dir" else "file" }) { entry ->
                                    FileEntryRow(entry, compact = compact,
                                        onMeasuredHeight = { px -> measuredRowHeight = maxOf(measuredRowHeight, with(rowDensity) { px.toDp().value }) },
                                        canModify = currentWritable && entry.blockedReason == null, selectionMode = selectingFiles, isSelected = entry.location.key in selectedFileKeys,
                                        onToggle = {
                                            if (entry.blockedReason != null) message = entry.blockedReason
                                            else selection = selection.toggle(entry)
                                        }, onAction = { action ->
                                        performEntryAction(entry, action)
                                    }) {
                                        if (entry.blockedReason != null) message = entry.blockedReason
                                        else if (entry.isDirectory) { navigateTo(stack + entry.location); search = "" }
                                        else {
                                            keyboard?.hide(); focusManager.clearFocus(); searchFocused = false
                                            selected = entry
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (selectingFiles) Box(Modifier.fillMaxWidth().onSizeChanged {
                        selectionToolbarHeightDp = with(rowDensity) { it.height.toDp().value }
                    }) { FileWorkspaceBottomBar(listOf(
                        FileWorkspaceAction("复制到$otherSide", Icons.ContentCopy, selectionOperable) { selection.transfer(location, false)?.let(onTransferToOther) },
                        FileWorkspaceAction("移动到$otherSide", Icons.SwapHoriz, selectionOperable && currentWritable) { selection.transfer(location, true)?.let(onTransferToOther) },
                        FileWorkspaceAction("删除", Icons.Delete, selectedFileKeys.isNotEmpty() && currentWritable, destructive = true) { requestDeletion(selectedRecords.values) },
                        FileWorkspaceAction("更多", Icons.MoreVert) { actionSheet = "selection" })) }
                    }
                    }
                }
                if (selected == null && managerPage == null && !selectingFiles) FileWorkspaceBottomBar(listOf(
                    FileWorkspaceAction("位置", Icons.Folder) { actionSheet = "places" },
                    FileWorkspaceAction("新建", Icons.Add, currentWritable) { actionSheet = "new" },
                    FileWorkspaceAction(if (searchOpen) "收起查找" else "查找", Icons.Search) { toggleSearch() },
                    FileWorkspaceAction("更多", Icons.MoreVert) { actionSheet = "tools" }))
            }
        }
        if (actionSheet != null && paneActive) FileWorkspaceActionSheet(sheetTitle, sheetActions) { actionSheet = null }
        }
        }
    }
    pendingDeletion?.let { plan ->
        PanelAlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text("删除 ${plan.targets.size} 项？") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("此操作不可撤销。确认期间已锁定以下项目：")
                    plan.targets.forEach { Text("${fileWorkspacePath(it.parent).trimEnd('/')}/${it.entry.name}") }
                }
            },
            dismissButton = { TextButton(onClick = { pendingDeletion = null }) { Text("取消") } },
            confirmButton = { TextButton(enabled = !transferBusy, onClick = {
                if (transferBusy || pendingDeletion !== plan) return@TextButton
                pendingDeletion = null
                transferBusy = true
                transferJob = scope.launch {
                    var completed = 0
                    val errors = mutableListOf<String>()
                    try {
                        plan.targets.forEach { target ->
                            try { fileManager.delete(target.entry, target.parent); completed++ }
                            catch (e: CancellationException) { throw e }
                            catch (e: Exception) { errors += "${target.entry.name}：${e.message}" }
                        }
                        message = "已删除$completed/${plan.targets.size}项" + errors.takeIf { it.isNotEmpty() }?.joinToString("\n", prefix = "\n").orEmpty()
                    } catch (e: CancellationException) {
                        message = "已取消；已删除${completed}项不会回滚"
                        throw e
                    } finally {
                        transferBusy = false; refresh++; onChanged()
                        selectingFiles = false; selection = FileWorkspaceSelection()
                    }
                }
            }) { Text("确认删除", color = MaterialTheme.colorScheme.error) } }
        )
    }
    if (pathInputOpen) PanelAlertDialog(
        onDismissRequest = { closePathInput() },
        title = { Text("输入路径") },
        text = {
            Column {
                OutlinedTextField(value = pathInput, onValueChange = { pathInput = it; pathInputError = null },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !pathInputBusy,
                    label = { Text("目录路径") }, isError = pathInputError != null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { submitPathInput() }))
                pathInputError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = { TextButton(onClick = { submitPathInput() }, enabled = !pathInputBusy) { Text(if (pathInputBusy) "读取中…" else "前往") } },
        dismissButton = { TextButton(onClick = { closePathInput() }) { Text("取消") } }
    )

}
