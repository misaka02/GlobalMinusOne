package com.example.globalminusone.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.system.BrowserLocation
import com.example.globalminusone.system.FileBrowserIo
import com.example.globalminusone.system.FileManagerIo
import com.example.globalminusone.system.UserRootSession
import com.example.globalminusone.ui.AppIcons as Icons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal typealias FileWindowCloseRequest = (allowed: () -> Unit, cancelled: () -> Unit) -> Unit

/** Both panes stay composed across width changes, keeping real paths, selection and list positions. */
@Composable
fun FileBrowserPanel(
    repository: MinusOneRepository,
    initialFile: ComponentItem? = null,
    compact: Boolean = false,
    showCloseControl: Boolean = true,
    onCloseHandlerChanged: ((() -> Unit)?) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val palette = opaqueFilePalette(LocalPanelPalette.current)
    val io = remember(context) { FileBrowserIo(context) }
    val manager = remember(context, io) { FileManagerIo(context, io) }
    val scope = rememberCoroutineScope()
    val expanded by repository.isPanelExpanded.collectAsState()
    val rootStatus by UserRootSession.status.collectAsState()
    val clipboard = remember { mutableStateOf<TransferClipboard?>(null) }
    val stacks = remember { List(2) { mutableStateOf<List<BrowserLocation>>(emptyList()) } }
    val requestedStacks = remember { List(2) { mutableStateOf<List<BrowserLocation>?>(null) } }
    val requestVersions = remember { List(2) { mutableIntStateOf(0) } }
    val editing = remember { List(2) { mutableStateOf(false) } }
    val previewing = remember { List(2) { mutableStateOf(false) } }
    val closeHandlers = remember { arrayOfNulls<() -> Unit>(2) }
    val windowCloseHandlers = remember { arrayOfNulls<FileWindowCloseRequest>(2) }
    var active by remember { mutableIntStateOf(0) }
    var closeCheckingPane by remember { mutableStateOf<Int?>(null) }
    var sourcePane by remember { mutableIntStateOf(0) }
    var ownedClipboard by remember { mutableStateOf<TransferClipboard?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var confirmation by remember { mutableStateOf<FileWorkspaceTransferPlan?>(null) }
    var receipt by remember { mutableStateOf<FileWorkspaceTransferReceipt?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var transferJob by remember { mutableStateOf<Job?>(null) }
    fun activate(pane: Int) {
        if (busy || closeCheckingPane != null || pane == active) return
        if (editing[active].value) {
            notice = "请先保存或关闭当前编辑页"
            closeHandlers[active]?.invoke()
        } else { active = pane; notice = null }
    }
    fun requestTransfer(from: Int, clip: TransferClipboard) {
        if (busy || closeCheckingPane != null) return
        val locked = editing.indexOfFirst { it.value }
        if (locked >= 0) {
            active = locked; notice = "请先保存或关闭当前编辑页"
            closeHandlers[locked]?.invoke()
            return
        }
        sourcePane = from
        ownedClipboard = clip
        clipboard.value = clip
        val target = stacks[1 - from].value.lastOrNull()
        if (target == null) {
            notice = "在${if (from == 0) "B" else "A"}选择目标目录，再点上方传输按钮"
            activate(1 - from)
            // Activation normally clears notices; target guidance must remain visible.
            notice = "请选择目标目录，源文件仍保留在${if (from == 0) "A" else "B"}"
        } else {
            runCatching {
                check(manager.canWrite(target)) { "目标目录只读，请先授权写入或选择其他目录" }
                FileWorkspaceTransferPlan.create(from, clip, target)
            }
                .onSuccess { confirmation = it; notice = null }
                .onFailure { notice = it.message ?: "无法使用这个目标目录" }
        }
    }
    val closeWorkspace by rememberUpdatedState<() -> Unit>({
        if (!busy && closeCheckingPane == null) {
            // Lock other panes until the current dirty decision is accepted or cancelled.
            fun closeAfterPane(pane: Int) {
                if (busy) { closeCheckingPane = null; return }
                if (pane == windowCloseHandlers.size) { closeCheckingPane = null; onDismiss() }
                else {
                    closeCheckingPane = pane
                    active = pane
                    val handler = windowCloseHandlers[pane]
                    if (handler == null) closeAfterPane(pane + 1)
                    else handler({ closeAfterPane(pane + 1) }, { closeCheckingPane = null })
                }
            }
            closeAfterPane(0)
        }
    })
    DisposableEffect(Unit) {
        onCloseHandlerChanged { closeWorkspace() }
        onDispose { onCloseHandlerChanged(null) }
    }
    LaunchedEffect(expanded) { if (!expanded) { confirmation = null; transferJob?.cancel() } }
    // Copy/cut from the existing file-management page participates in the same workspace clipboard.
    LaunchedEffect(clipboard.value) {
        clipboard.value?.let { clip ->
            if (ownedClipboard !== clip) {
                // A management-page copy originates in the active pane, even if both paths match.
                sourcePane = active
                ownedClipboard = clip
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(palette.cardBackground)) {
        val wide = maxWidth >= 640.dp
        val shortPaneLabels = fileBrowserNarrowChrome(maxWidth.value, LocalDensity.current.fontScale)
        val activateOnDown by rememberUpdatedState<(Int) -> Unit>({ pane ->
            if (wide && !busy && active != pane && !editing[active].value) activate(pane)
        })
        Column(Modifier.fillMaxSize()) {
            if (!(compact && !wide && !showCloseControl && previewing[active].value)) Surface(
                color = palette.cardBackground,
                tonalElevation = 1.dp
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp).heightIn(min = 38.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            Modifier
                                .weight(1f)
                                .background(palette.cardBorder.copy(alpha = if (palette.isDark) 0.22f else 0.32f), RoundedCornerShape(10.dp))
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(2) { pane ->
                                val isPaneActive = active == pane
                                val paneTitle = stacks[pane].value.lastOrNull()?.title ?: "常用主页"
                                Surface(
                                    onClick = { activate(pane) },
                                    enabled = !busy && closeCheckingPane == null,
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isPaneActive) {
                                        if (palette.isDark) palette.accent.copy(alpha = 0.22f) else palette.cardBackground
                                    } else androidx.compose.ui.graphics.Color.Transparent,
                                    shadowElevation = if (isPaneActive && !palette.isDark) 1.dp else 0.dp,
                                    modifier = Modifier.weight(1f).heightIn(min = 40.dp).semantics {
                                        contentDescription = "${if (pane == 0) "A" else "B"}窗格：$paneTitle"
                                    }
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = if (shortPaneLabels) 2.dp else 7.dp, vertical = 6.dp),
                                        horizontalArrangement = if (shortPaneLabels) Arrangement.Center else Arrangement.Start,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            Modifier
                                                .sizeIn(minWidth = 20.dp, minHeight = 20.dp)
                                                .background(
                                                    if (isPaneActive) palette.accent else palette.cardBorder.copy(alpha = 0.55f),
                                                    RoundedCornerShape(5.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (pane == 0) "A" else "B",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isPaneActive) {
                                                    if (palette.isDark) androidx.compose.ui.graphics.Color(0xFF101824) else androidx.compose.ui.graphics.Color.White
                                                } else palette.textSecondary
                                            )
                                        }
                                        if (!shortPaneLabels) {
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = paneTitle,
                                                Modifier.weight(1f),
                                                fontSize = 11.5.sp,
                                                fontWeight = if (isPaneActive) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (isPaneActive) palette.textPrimary else palette.textSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        val canSyncOther = !busy && closeCheckingPane == null && stacks[active].value.isNotEmpty() && !editing[1 - active].value
                        val canSwapBoth = !busy && closeCheckingPane == null && !editing[0].value && !editing[1].value &&
                            (stacks[0].value.isNotEmpty() || stacks[1].value.isNotEmpty())
                        IconButton(
                            onClick = {
                                if (canSyncOther) {
                                    requestedStacks[1 - active].value = stacks[active].value.toList()
                                    requestVersions[1 - active].intValue++
                                    notice = "已将${if (active == 0) "A" else "B"}当前目录同步到${if (active == 0) "B" else "A"}"
                                }
                            },
                            enabled = canSyncOther,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.ContentCopy,
                                "同步当前目录到另一栏",
                                Modifier.size(16.dp),
                                tint = if (canSyncOther) palette.accent else palette.textSecondary.copy(alpha = 0.38f)
                            )
                        }
                        IconButton(
                            onClick = {
                                if (canSwapBoth) {
                                    val left = stacks[0].value.toList()
                                    val right = stacks[1].value.toList()
                                    requestedStacks[0].value = right
                                    requestVersions[0].intValue++
                                    requestedStacks[1].value = left
                                    requestVersions[1].intValue++
                                    notice = "已对调 A / B 两侧目录"
                                }
                            },
                            enabled = canSwapBoth,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.SwapHoriz,
                                "对调A/B目录",
                                Modifier.size(18.dp),
                                tint = if (canSwapBoth) palette.accent else palette.textSecondary.copy(alpha = 0.38f)
                            )
                        }
                        if (showCloseControl) {
                            IconButton(onClick = { closeWorkspace() }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Close, "关闭文件管理", Modifier.size(18.dp))
                            }
                        }
                    }
                    HorizontalDivider(color = palette.cardBorder.copy(alpha = 0.35f))
                }
            }
            clipboard.value?.let { clip ->
                val target = stacks[1 - sourcePane].value.lastOrNull()
                val targetWritable by produceState(false, target?.key, refresh, expanded, rootStatus) {
                    value = expanded && kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { target?.let { manager.canWrite(it) } == true }
                }
                Column(Modifier.fillMaxWidth().background(palette.accent.copy(alpha = .06f)).padding(start = 12.dp, end = 4.dp)) {
                    Text(fileWorkspaceClipboardTitle(sourcePane, clip.entries.size, clip.move), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = palette.textPrimary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(target?.let { fileWorkspacePath(it) + if (targetWritable) "" else " · 只读" } ?: "另一侧尚未选择目标目录", Modifier.weight(1f), fontSize = 11.sp, color = palette.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { requestTransfer(sourcePane, clip) }, enabled = !busy && (target == null || targetWritable)) { Text(if (target == null) "选择目标" else if (clip.move) "移动到${if (sourcePane == 0) "B" else "A"}" else "复制到${if (sourcePane == 0) "B" else "A"}", fontSize = 12.sp) }
                        IconButton(onClick = { clipboard.value = null }, enabled = !busy) { Icon(Icons.Close, "取消传输选择", Modifier.size(18.dp)) }
                    }
                }
            }
            if (busy) Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(progress.ifBlank { "正在传输…" }, Modifier.weight(1f).padding(horizontal = 8.dp), color = palette.textPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { transferJob?.cancel() }) { Text("取消") }
            }
            notice?.let { message ->
                Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(message, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, color = palette.textPrimary, fontSize = 12.sp)
                    IconButton(onClick = { notice = null }) { Icon(Icons.Close, "关闭操作提示", Modifier.size(18.dp)) }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                // The Row/Box structure does not change at the breakpoint, so neither pane loses state.
                Row(Modifier.fillMaxSize()) {
                    repeat(2) { pane ->
                        val visible = wide || active == pane
                        val widthModifier = if (wide) Modifier.weight(1f) else if (active == pane) Modifier.weight(1f) else Modifier.width(0.dp)
                        Box(widthModifier.fillMaxHeight().then(if (visible) Modifier else Modifier.alpha(0f).clearAndSetSemantics {})
                            .pointerInput(pane, wide) {
                                awaitEachGesture {
                                    // Observe before the row handles DOWN; never consume its click or long press.
                                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                    activateOnDown(pane)
                                }
                            }) {
                            FileBrowserPane(repository, if (pane == 0) initialFile else null, compact = compact || wide, showCloseControl = false,
                                paneId = pane, paneActive = active == pane, paneVisible = visible, workspaceBusy = busy,
                                sharedClipboard = clipboard, workspaceRefresh = refresh, receipt = receipt,
                                requestedStack = requestedStacks[pane].value, requestVersion = requestVersions[pane].intValue,
                                onStackChanged = { stacks[pane].value = it }, onEditingChanged = { editing[pane].value = it },
                                onPreviewChanged = { previewing[pane].value = it },
                                onTransferToOther = { requestTransfer(pane, it) }, onChanged = { refresh++ },
                                onCloseHandlerChanged = { closeHandlers[pane] = it },
                                onWindowCloseHandler = { windowCloseHandlers[pane] = it }, onDismiss = onDismiss)
                            if ((closeCheckingPane != null && closeCheckingPane != pane) ||
                                (wide && active != pane && editing[active].value)) {
                                Box(Modifier.matchParentSize().background(palette.cardBackground.copy(alpha = .08f))
                                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { activate(pane) })
                            }
                            if (wide) Box(Modifier.matchParentSize().border(if (active == pane) 2.dp else 1.dp,
                                if (active == pane) palette.accent else palette.cardBorder, RoundedCornerShape(2.dp)))
                        }
                    }
                }
                if (busy) Box(Modifier.matchParentSize().background(palette.cardBackground.copy(alpha = .25f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {})
            }
        }
    }
    confirmation?.let { plan ->
        PanelAlertDialog(onDismissRequest = { confirmation = null },
            title = { Text("${if (plan.clipboard.move) "移动" else "复制"} ${plan.clipboard.entries.size} 项") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("源 ${if (plan.sourcePane == 0) "A" else "B"}：${fileWorkspacePath(plan.clipboard.parent)}")
                    Text("目标 ${if (plan.targetPane == 0) "A" else "B"}：${fileWorkspacePath(plan.destination)}", fontWeight = FontWeight.Medium)
                    Text("同名文件不会覆盖。${if (plan.clipboard.move) "成功复制后才删除源文件。" else ""}", fontSize = 12.sp)
                }
            }, dismissButton = { TextButton(onClick = { confirmation = null }) { Text("取消") } },
            confirmButton = { TextButton(onClick = {
                if (busy) return@TextButton
                confirmation = null; busy = true
                transferJob = scope.launch {
                    val completed = linkedSetOf<String>()
                    val errors = mutableListOf<String>()
                    var cancelled = false
                    try {
                        for (entry in plan.clipboard.entries) {
                            try {
                                manager.transfer(entry, entry.parentLocation ?: plan.clipboard.parent, plan.destination, plan.clipboard.move) { progress = it }
                                completed += entry.location.key
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) { errors += "${entry.name}：${e.message ?: "操作失败"}" }
                        }
                    } catch (e: CancellationException) { cancelled = true; throw e }
                    finally {
                        val remaining = plan.clipboard.entries.filterNot { it.location.key in completed }
                        val unfinished = remaining.takeIf { it.isNotEmpty() }?.let { plan.clipboard.copy(entries = it) }
                        ownedClipboard = unfinished; clipboard.value = unfinished
                        receipt = FileWorkspaceTransferReceipt(System.nanoTime(), plan.sourcePane, completed)
                        notice = "${if (cancelled) "已取消；" else ""}本次完成 ${completed.size}/${plan.clipboard.entries.size} 项" + errors.take(2).joinToString("；", prefix = if (errors.isEmpty()) "" else "；")
                        busy = false; progress = ""; refresh++
                    }
                }
            }) { Text("确认${if (plan.clipboard.move) "移动" else "复制"}") } })
    }
}
