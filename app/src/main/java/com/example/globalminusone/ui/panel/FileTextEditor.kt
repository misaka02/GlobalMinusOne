package com.example.globalminusone.ui.panel

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.globalminusone.system.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** A bounded UTF-8 editor; a preview's possibly truncated text is never used as the source. */
@Composable
internal fun FileTextEditor(
    manager: FileManagerIo, entry: BrowserEntry?, parent: BrowserLocation?,
    onClose: () -> Unit, onSaved: () -> Unit,
    installBack: ((() -> Unit)?) -> Unit = {},
    installWindowClose: (FileWindowCloseRequest?) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var original by remember(entry?.location?.key) { mutableStateOf<EditableFileText?>(null) }
    var text by remember(entry?.location?.key) { mutableStateOf("") }
    var name by remember { mutableStateOf("新建文本.txt") }
    var busy by remember(entry?.location?.key) { mutableStateOf(entry != null) }
    var error by remember(entry?.location?.key) { mutableStateOf<String?>(null) }
    var confirmSave by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var pendingWindowClose by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingWindowCloseCancelled by remember { mutableStateOf<(() -> Unit)?>(null) }
    val rootTarget = entry?.location?.rootAccess == true
    var rootSnapshot by remember(entry?.location?.key) { mutableStateOf<RootFileSnapshot?>(null) }
    var unlockReview by remember { mutableStateOf(false) }
    var rootUnlocked by remember { mutableStateOf(false) }
    var reviewedRootChange by remember { mutableStateOf(false) }
    var rootReceipt by remember(entry?.location?.key) { mutableStateOf(RootFileIo.recoveryRecords.value.lastOrNull { it.target == entry?.location?.path }) }
    var rollbackSnapshot by remember { mutableStateOf<RootFileSnapshot?>(null) }
    var safRecovery by remember(entry?.location?.key) { mutableStateOf<SafTextRecovery?>(null) }
    var safReview by remember(entry?.location?.key) { mutableStateOf<SafTextRestoreReview?>(null) }
    var safRestored by remember(entry?.location?.key) { mutableStateOf(false) }
    var backupMenu by remember(entry?.location?.key) { mutableStateOf(false) }
    var errorDetails by remember(entry?.location?.key) { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val dirty = text != (original?.text ?: "") || (entry == null && text.isNotEmpty())
    val normalWritable = remember(entry?.location?.key, parent?.key) {
        (entry?.location ?: parent)?.let { manager.canWrite(it) } == true
    }
    val writable = if (rootTarget) rootUnlocked && RootWritePolicy.canWrite(requireNotNull(entry?.location?.path)) else normalWritable
    fun finishClose(includeRootReceipt: Boolean) {
        if ((!rootTarget && safRestored) || (includeRootReceipt && rootReceipt?.completed == true)) onSaved() else onClose()
    }
    val requestClose by rememberUpdatedState<() -> Unit> {
        if (!busy) {
            val cancelled = pendingWindowCloseCancelled
            pendingWindowClose = null; pendingWindowCloseCancelled = null
            cancelled?.invoke()
            if (confirmExit) confirmExit = false
            else if (dirty) confirmExit = true else finishClose(includeRootReceipt = true)
        }
    }
    DisposableEffect(Unit) {
        installBack { requestClose() }
        onDispose { installBack(null) }
    }
    val requestWindowClose by rememberUpdatedState<FileWindowCloseRequest> { allowed, cancelled ->
        if (!busy) {
            if (dirty) {
                // Reviews have not started writing; the window-close decision takes priority.
                safReview = null; unlockReview = false; rollbackSnapshot = null; errorDetails = false
                pendingWindowClose = allowed; pendingWindowCloseCancelled = cancelled; confirmSave = false; confirmExit = true
            }
            else allowed()
        } else cancelled()
    }
    DisposableEffect(Unit) {
        installWindowClose { allowed, cancelled -> requestWindowClose(allowed, cancelled) }
        onDispose { installWindowClose(null); pendingWindowCloseCancelled?.invoke() }
    }
    LaunchedEffect(entry?.location?.key) {
        busy = entry != null
        error = null
        if (entry != null) try {
            safRecovery = manager.pendingTextRecovery(entry)
            original = manager.readText(entry)
            text = original!!.text
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "无法读取文本" }
        finally { busy = false }
    }
    if (errorDetails && error != null) PanelAlertDialog(onDismissRequest = { errorDetails = false },
        title = { Text("错误详情") },
        text = { Text(requireNotNull(error), Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { errorDetails = false }) { Text("关闭") } })
    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (entry == null) "新建文本" else "编辑 ${entry.name}", Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            if (!confirmSave && !confirmExit) {
                TextButton(onClick = { requestClose() }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text("取消") }
                TextButton(onClick = { keyboard?.hide(); focus.clearFocus(); reviewedRootChange = false; confirmSave = true }, enabled = !busy && writable && safRecovery == null && (entry == null || original != null),
                    modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text("保存") }
            }
        }
        Text(if (rootTarget) "Root UTF-8 ≤1MiB · ${if (writable) "本文件已解锁，保存须核对差异" else "默认只读；需逐文件探测真实RW能力"}"
            else if (parent?.rootAccess == true) "Root工作目录 · 新建文件root所有，仅此范围操作"
            else "仅 UTF-8 · 最大 1 MiB${if (!writable) " · 只读授权，不能保存" else ""}", style = MaterialTheme.typography.labelSmall)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (safReview != null) {
            val review = requireNotNull(safReview)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text("确认用备份原文覆盖当前文件？本页未保存编辑也将被替换。")
                Text("目标：${review.recovery.targetName}\n${review.recovery.targetKey}\n备份时间：${java.util.Date(review.recovery.createdAt)}")
                Text("备份SHA256：${review.recovery.originalHash}\n当前SHA256：${review.currentHash}\n当前内容（前400字）：\n${review.currentPreview}", style = MaterialTheme.typography.bodySmall)
                Text("确认前会再次核对当前指纹；提供方写入不保证原子性。失败时原文备份继续保留。")
                Row {
                    TextButton(onClick = { safReview = null }, enabled = !busy) { Text("取消") }
                    TextButton(onClick = {
                        busy = true; safReview = null
                        scope.launch {
                            try {
                                manager.restoreTextRecovery(requireNotNull(entry), review, parent)
                                safRestored = true
                                safRecovery = manager.pendingTextRecovery(entry)
                                original = manager.readText(entry); text = requireNotNull(original).text
                                error = "原文已恢复并读回核对一致"
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) { error = e.message ?: "恢复未确认；原文备份保留" }
                            finally { busy = false }
                        }
                    }, enabled = !busy) { Text("确认恢复原文") }
                }
            }
        } else if (unlockReview || rollbackSnapshot != null) {
            val snapshot = rollbackSnapshot ?: rootSnapshot
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text(if (rollbackSnapshot != null) "恢复本次原始备份？" else "只解锁此配置文件，不授权整个目录。")
                Text("${snapshot?.path}\n${snapshot?.description}\nSHA256:${snapshot?.hash}", style = MaterialTheme.typography.bodySmall)
                Text("不会remount或改变owner/mode/SELinux。系统服务可能同时改写配置；错误内容可能导致功能异常。数据库、密钥、启动和可执行文件受保护。")
                if (rollbackSnapshot != null) Text("备份：${rootReceipt?.backupPath}\n备份指纹：${rootReceipt?.originalHash}", style = MaterialTheme.typography.bodySmall)
                Row {
                    TextButton(onClick = { unlockReview = false; rollbackSnapshot = null }) { Text("取消") }
                    TextButton(onClick = {
                        if (rollbackSnapshot != null) {
                            val current = requireNotNull(rollbackSnapshot); rollbackSnapshot = null; busy = true
                            scope.launch {
                                try {
                                    RootFileIo.rollback(requireNotNull(rootReceipt), current.hash)
                                    original = manager.readText(requireNotNull(entry)); text = original!!.text
                                    rootReceipt = RootFileIo.recoveryRecords.value.lastOrNull { it.target == entry.location.path }
                                    rootUnlocked = false; error = null
                                } catch (e: CancellationException) { throw e }
                                catch (e: Exception) { error = e.message ?: "回滚失败；请保留备份" }
                                finally { busy = false }
                            }
                        } else {
                            try { RootFileIo.unlock(requireNotNull(rootSnapshot)); rootUnlocked = true; unlockReview = false }
                            catch (e: Exception) { unlockReview = false; error = e.message ?: "解锁会话已失效" }
                        }
                    }) { Text(if (rollbackSnapshot != null) "确认回滚" else "确认逐文件解锁") }
                }
            }
        } else if (confirmSave || confirmExit) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text(if (confirmExit) "放弃尚未保存的文本更改？" else if (entry == null) "创建 $name？同名不会覆盖。" else "以 UTF-8 覆盖 ${entry.name} 的全部内容？文件在外部变动时会拒绝保存。")
                if (rootTarget && !confirmExit) {
                    Text("${rootSnapshot?.path}\n${rootSnapshot?.description}\n原SHA256:${original?.fingerprint}\n将保留原inode属性并创建Root私有原始备份；写后核对内容与元数据。超时/断电不能保证自动回滚，需核对备份。", style = MaterialTheme.typography.bodySmall)
                    Text(rootTextDifference(original?.text.orEmpty(), text), style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(reviewedRootChange, { reviewedRootChange = it })
                        Text("我已核对目标与差异，确认本次写入", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row {
                    TextButton(onClick = {
                        val cancelled = pendingWindowCloseCancelled
                        pendingWindowClose = null; pendingWindowCloseCancelled = null; confirmSave = false; confirmExit = false
                        cancelled?.invoke()
                    }, enabled = !busy) { Text("继续编辑") }
                    TextButton(onClick = {
                        if (confirmExit) {
                            val closeWindow = pendingWindowClose
                            pendingWindowClose = null; pendingWindowCloseCancelled = null; confirmExit = false
                            if (closeWindow != null) closeWindow() else finishClose(includeRootReceipt = false)
                        }
                        else {
                            confirmSave = false; busy = true; error = null
                            scope.launch {
                                try {
                                    if (entry == null) manager.createText(requireNotNull(parent), name, text)
                                    else {
                                        if (rootTarget) RootFileIo.approveText(entry.location, requireNotNull(original).fingerprint, text)
                                        manager.saveText(entry, requireNotNull(original).fingerprint, text, parent)
                                    }
                                    if (rootTarget) {
                                        original = EditableFileText(text, textFingerprint(editableTextBytes(text)))
                                        rootReceipt = RootFileIo.recoveryRecords.value.lastOrNull { it.target == entry?.location?.path }
                                        rootUnlocked = false
                                    }
                                    onSaved()
                                } catch (e: CancellationException) { throw e }
                                catch (e: Exception) {
                                    if (rootTarget) rootReceipt = RootFileIo.recoveryRecords.value.lastOrNull { it.target == entry?.location?.path }
                                    error = e.message ?: "保存失败"
                                    if (!rootTarget && entry != null) try { safRecovery = manager.pendingTextRecovery(entry) }
                                    catch (cancel: CancellationException) { throw cancel }
                                    catch (lookup: Exception) { error += "；备份记录读取失败：${lookup.message}" }
                                }
                                finally { busy = false }
                            }
                        }
                    }, enabled = !busy && (!rootTarget || confirmExit || reviewedRootChange), modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(if (confirmExit) "放弃更改" else "确认保存")
                    }
                }
            }
        } else {
            if (entry == null) OutlinedTextField(name, { name = it }, label = { Text("文件名") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().height(56.dp).semantics { contentDescription = "文件名" }, enabled = !busy)
            error?.let {
                Text(it, Modifier.fillMaxWidth().clickable { errorDetails = true }
                    .semantics { contentDescription = "查看完整错误详情" }, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            safRecovery?.let { backup ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("有未完成原文备份", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Box {
                        TextButton(onClick = { backupMenu = true }, enabled = !busy,
                            modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text("备份 ▾") }
                        DropdownMenu(expanded = backupMenu, onDismissRequest = { backupMenu = false }) {
                            com.example.globalminusone.service.Android12WindowKeyGuard()

                            DropdownMenuItem(text = { Text("分享原文备份") }, onClick = {
                                backupMenu = false
                                busy = true; keyboard?.hide(); focus.clearFocus()
                                scope.launch {
                                    try { manager.shareTextRecovery(requireNotNull(entry), backup) }
                                    catch (e: CancellationException) { throw e }
                                    catch (e: Exception) { error = e.message ?: "无法分享备份" }
                                    finally { busy = false }
                                }
                            }, enabled = !busy)
                            DropdownMenuItem(text = { Text("核对并恢复原文") }, onClick = {
                                backupMenu = false
                                busy = true; keyboard?.hide(); focus.clearFocus()
                                scope.launch {
                                    try { safReview = manager.reviewTextRecovery(requireNotNull(entry), backup) }
                                    catch (e: CancellationException) { throw e }
                                    catch (e: Exception) { error = e.message ?: "无法核对当前文件；备份仍保留" }
                                    finally { busy = false }
                                }
                            }, enabled = !busy && writable)
                        }
                    }
                }
            }
            if (rootTarget && !writable && original != null && !busy) TextButton(onClick = {
                busy = true; error = null
                scope.launch {
                    try {
                        rootSnapshot = RootFileIo.inspectForEditing(requireNotNull(entry).location)
                        check(rootSnapshot!!.hash == original!!.fingerprint) { "文件已变化，请退出并重新打开" }
                        unlockReview = true
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = e.message ?: "不能解锁此文件" }
                    finally { busy = false }
                }
            }) { Text("探测并解锁此配置文件") }
            rootReceipt?.let { receipt ->
                Text("${receipt.status}\n原始备份：${receipt.backupPath}", style = MaterialTheme.typography.labelSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Row {
                    TextButton(onClick = { if (receipt.completed) onSaved() else onClose() }, enabled = !busy) { Text("完成") }
                    TextButton(onClick = {
                        busy = true
                        scope.launch {
                            try { rollbackSnapshot = RootFileIo.inspectForEditing(requireNotNull(entry).location) }
                            catch (e: CancellationException) { throw e }
                            catch (e: Exception) { error = e.message ?: "不能准备回滚" }
                            finally { busy = false }
                        }
                    }, enabled = !busy) { Text("核对并回滚") }
                }
            }
            OutlinedTextField(text, { text = it }, modifier = Modifier.weight(1f).fillMaxWidth()
                .ownPanelScrollGestures(true).semantics { contentDescription = "UTF-8 正文" }, minLines = 2,
                enabled = !busy && (entry == null || original != null), readOnly = !writable,
                label = { Text("UTF-8 正文") })
        }
    }
}
