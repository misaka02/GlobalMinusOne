package com.example.globalminusone.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.backup.BackupExportMode
import com.example.globalminusone.data.backup.DecodedNativeBackup
import com.example.globalminusone.data.backup.NativeBackupCodec
import com.example.globalminusone.data.backup.NativeBackupStore
import com.example.globalminusone.data.panels.PanelsBackupImporter
import com.example.globalminusone.data.panels.PanelsImportArchive
import com.example.globalminusone.data.panels.SafeZipReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File

@Composable
fun NativeBackupEntry(
    repository: MinusOneRepository,
    autoLaunchPageImportToken: Long = 0L
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by repository.state.collectAsState()
    val restoreAllowed = !state.editMode && !state.advancedConfig.layoutLocked
    val store = remember(repository) { NativeBackupStore(context, repository) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<DecodedNativeBackup?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportMode by remember { mutableStateOf(BackupExportMode.FULL) }
    var exportSelectedPageIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var detectedPanelsArchive by remember { mutableStateOf<PanelsImportArchive?>(null) }
    var recovery by remember { mutableStateOf<File?>(null) }
    var lastAutoLaunchToken by rememberSaveable { mutableLongStateOf(0L) }
    var preferPageAppendMode by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(store) {
        recovery = withContext(Dispatchers.IO) { store.latestRecoveryFile() }
    }
    fun work(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { action() }
            finally { busy = false }
        }
    }
    val saveRecovery = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) work {
            message = runCatching { withContext(Dispatchers.IO) {
                val file = requireNotNull(recovery) { "没有恢复前备份" }
                requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "不能写入所选文件" }.use { output -> file.inputStream().use { it.copyTo(output) } }
                "恢复前备份已保存。"
            } }.getOrElse { if (it is CancellationException) throw it; "保存失败：${it.message}" }
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) work {
            message = runCatching { withContext(Dispatchers.IO) {
                val payload = requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "不能写入所选文件" }
                    .use { out -> store.exportScoped(out, exportMode, exportSelectedPageIds) }
                "备份已写入：${payload.state.pages.size} 页、${payload.state.profiles.size} 个方案、${payload.assets.size} 个图片引用。\n" + payload.warnings.joinToString("\n")
            } }.getOrElse { if (it is CancellationException) throw it; "备份失败：${it.message}" }
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) work {
            runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = requireNotNull(context.contentResolver.openInputStream(uri)) { "不能读取所选文件" }
                        .use { NativeBackupCodec.readBounded(it, NativeBackupCodec.MAX_ARCHIVE_BYTES) }
                    if (SafeZipReader.looksLikePanelsArchive(bytes)) {
                        PanelsBackupImporter.parseBytes(bytes) to null
                    } else {
                        null to store.preview(ByteArrayInputStream(bytes))
                    }
                }
            }.onSuccess { (panels, nativeBackup) ->
                if (panels != null) {
                    detectedPanelsArchive = panels
                } else {
                    pending = nativeBackup
                }
            }.onFailure { if (it is CancellationException) throw it; message = "备份检查失败：${it.message}" }
        }
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text("分层备份与恢复", style = MaterialTheme.typography.titleMedium)
        Text("支持完整备份、仅导出选中页面或仅设置导出；导入支持完整替换或按模块/单页选择，并在面板尺寸/列数不适配时自动提示。", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showExportDialog = true }, enabled = !busy) { Text("导出备份…") }
            OutlinedButton(
                onClick = {
                    preferPageAppendMode = false
                    open.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                },
                enabled = !busy && restoreAllowed
            ) { Text("导入 / 恢复…") }
        }
        LaunchedEffect(autoLaunchPageImportToken, restoreAllowed, busy) {
            if (autoLaunchPageImportToken > lastAutoLaunchToken && restoreAllowed && !busy) {
                lastAutoLaunchToken = autoLaunchPageImportToken
                preferPageAppendMode = true
                open.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
            }
        }
        if (!restoreAllowed) Text("恢复前请先结束面板编辑并解锁布局。", style = MaterialTheme.typography.bodySmall)
        if (busy) Text("正在处理备份…", style = MaterialTheme.typography.bodySmall)
        if (recovery != null) TextButton(onClick = { saveRecovery.launch("GlobalMinusOne-before-restore.zip") }, enabled = !busy) { Text("另存恢复前备份") }
    }
    if (showExportDialog) {
        BackupExportOptionsDialog(
            state = state,
            onDismiss = { showExportDialog = false },
            onConfirm = { mode, pageIds ->
                exportMode = mode
                exportSelectedPageIds = pageIds
                showExportDialog = false
                val fileName = when (mode) {
                    BackupExportMode.FULL -> "GlobalMinusOne-full-backup.zip"
                    BackupExportMode.SELECTED_PAGES -> "GlobalMinusOne-pages-backup.zip"
                    BackupExportMode.SETTINGS_ONLY -> "GlobalMinusOne-settings-backup.zip"
                }
                export.launch(fileName)
            }
        )
    }
    pending?.let { backup ->
        BackupRestoreOptionsDialog(
            backup = backup,
            currentState = state,
            busy = busy,
            restoreAllowed = restoreAllowed,
            defaultPageAppendOnly = preferPageAppendMode,
            onDismiss = { if (!busy) pending = null },
            onConfirm = { selection ->
                if (repository.state.value.editMode || repository.state.value.advancedConfig.layoutLocked) {
                    message = "恢复前请先结束面板编辑并解锁布局。"
                } else work {
                    runCatching { withContext(Dispatchers.IO) { store.restoreSelective(backup, selection) } }
                        .onSuccess {
                            recovery = it
                            pending = null
                            message = "导入已完成。恢复前备份已保留，可点击“另存恢复前备份”保存。若包含小部件或 SAF 目录卡片，请检查对应系统授权。"
                        }
                        .onFailure {
                            if (it is CancellationException) throw it
                            recovery = withContext(Dispatchers.IO) { store.latestRecoveryFile() }
                            message = it.message ?: "恢复失败"
                        }
                }
            }
        )
    }
    detectedPanelsArchive?.let { panelsArchive ->
        PanelsImportHostDialog(
            archive = panelsArchive,
            repository = repository,
            bannerHint = "已识别为 Panels (.bkp) 配置文件，已自动切换为追加迁移模式",
            onDismiss = { detectedPanelsArchive = null },
            onImported = { resultMessage ->
                detectedPanelsArchive = null
                message = resultMessage
            }
        )
    }
    message?.let { text -> AlertDialog(onDismissRequest = { message = null }, title = { Text("备份与恢复") },
        text = { Text(text, Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { message = null }) { Text("知道了") } }) }
}
