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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.globalminusone.data.MinusOneRepository
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
fun NativeBackupEntry(repository: MinusOneRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by repository.state.collectAsState()
    val restoreAllowed = !state.editMode && !state.advancedConfig.layoutLocked
    val store = remember(repository) { NativeBackupStore(context, repository) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<DecodedNativeBackup?>(null) }
    var detectedPanelsArchive by remember { mutableStateOf<PanelsImportArchive?>(null) }
    var recovery by remember { mutableStateOf<File?>(null) }
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
                val payload = requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "不能写入所选文件" }.use(store::export)
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
        Text("完整备份与恢复", style = MaterialTheme.typography.titleMedium)
        Text("保存配置、独立方案、浏览器书签/历史/设置及可读取的图片。文件可能包含个人数据，请妥善保管。", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { export.launch("GlobalMinusOne-backup.zip") }, enabled = !busy) { Text("备份到文件") }
            OutlinedButton(onClick = { open.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, enabled = !busy && restoreAllowed) { Text("恢复备份") }
        }
        if (!restoreAllowed) Text("恢复前请先结束面板编辑并解锁布局。", style = MaterialTheme.typography.bodySmall)
        if (busy) Text("正在处理备份…", style = MaterialTheme.typography.bodySmall)
        if (recovery != null) TextButton(onClick = { saveRecovery.launch("GlobalMinusOne-before-restore.zip") }, enabled = !busy) { Text("另存恢复前备份") }
    }
    pending?.let { backup ->
        AlertDialog(onDismissRequest = { if (!busy) pending = null }, title = { Text("确认恢复备份") },
            text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (backup.payload.configurationOnly) "将替换配置及方案，保留当前浏览器书签/历史/设置：${backup.payload.state.pages.size} 页、${backup.payload.state.profiles.size} 个方案。旧格式不含图片文件。恢复前会先保存一份本机恢复备份。" else "将替换当前全部配置、方案及浏览器书签/历史/设置：${backup.payload.state.pages.size} 页、${backup.payload.state.profiles.size} 个方案、${backup.assets.size} 个图片文件。恢复前会先保存一份本机恢复备份。")
                Text("同一安装中经系统核验仍有效的小部件绑定会保留，其余小部件卡片需重新绑定。SAF 目录权限、登录状态和其它系统授权不能由备份迁移。浏览器书签和设置将在恢复后刷新，现有网页标签不清空。")
                backup.payload.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            } },
            confirmButton = { TextButton(enabled = !busy && restoreAllowed, onClick = {
                if (repository.state.value.editMode || repository.state.value.advancedConfig.layoutLocked) {
                    message = "恢复前请先结束面板编辑并解锁布局。"
                } else work {
                    runCatching { withContext(Dispatchers.IO) { store.restore(backup) } }
                        .onSuccess { recovery = it; pending = null; message = "恢复已写入，浏览器配置已通知刷新。请检查目录授权及小部件绑定。恢复前备份已保留，可点击“另存恢复前备份”保存到文件。" }
                        .onFailure {
                            if (it is CancellationException) throw it
                            recovery = withContext(Dispatchers.IO) { store.latestRecoveryFile() }
                            message = it.message ?: "恢复失败"
                        }
                        }
            }) { Text("确认替换") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { pending = null }) { Text("取消") } })
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

