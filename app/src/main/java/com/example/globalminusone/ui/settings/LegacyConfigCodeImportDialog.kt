package com.example.globalminusone.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.backup.DecodedNativeBackup
import com.example.globalminusone.data.backup.NativeBackupStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Legacy codes remain supported, with the same validation/recovery transaction as file restores. */
@Composable
internal fun LegacyConfigCodeImportDialog(repository: MinusOneRepository, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember(repository) { NativeBackupStore(context, repository) }
    val state by repository.state.collectAsState()
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<DecodedNativeBackup?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var complete by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = {
            com.example.globalminusone.service.Android12WindowKeyGuard()
            Text(if (complete) "配置码已恢复" else if (pending == null) "导入配置码" else "确认替换当前配置")
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (complete) {
                    Text("配置已保存。恢复前的配置和图片已留存，可在本页“另存恢复前备份”导出。浏览器书签、历史及偏好保留。")
                } else if (pending == null) {
                    Text("粘贴旧版 JSON 配置码，先检查内容，再确认替换。")
                    OutlinedTextField(code, { code = it; error = null }, enabled = !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 260.dp),
                        label = { Text("JSON 配置码") })
                } else {
                    val backup = pending!!
                    Text("将替换当前 ${state.pages.size} 页为 ${backup.payload.state.pages.size} 页，并恢复 ${backup.payload.state.profiles.size} 个独立方案。此操作会替换现有布局；恢复前先创建本机恢复备份。")
                    backup.payload.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                if (state.advancedConfig.layoutLocked || state.editMode) Text("请先解锁布局，并保存或取消面板编辑后再导入。", color = MaterialTheme.colorScheme.error)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && (complete || (!state.advancedConfig.layoutLocked && !state.editMode && (pending != null || code.isNotBlank()))), onClick = {
                if (complete) onDismiss() else scope.launch {
                    busy = true; error = null
                    try {
                        if (pending == null) pending = withContext(Dispatchers.IO) {
                            code.trim().byteInputStream(Charsets.UTF_8).use(store::preview)
                        } else {
                            withContext(Dispatchers.IO) { store.restore(pending!!) }
                            complete = true
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { error = failure.message ?: "恢复未完成，请检查恢复前备份" }
                    finally { busy = false }
                }
            }) { Text(if (busy) "正在处理…" else if (complete) "完成" else if (pending == null) "检查配置" else "确认替换") }
        },
        dismissButton = {
            if (!complete) TextButton(enabled = !busy, onClick = { if (pending != null) pending = null else onDismiss() }) {
                Text(if (pending == null) "取消" else "返回修改")
            }
        })
}
