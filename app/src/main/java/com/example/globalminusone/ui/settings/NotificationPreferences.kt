package com.example.globalminusone.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.system.AppAndIntentLauncher
import com.example.globalminusone.system.InstalledAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun NotificationPreferences(repository: MinusOneRepository) {
    val state by repository.state.collectAsState()
    val prefs = state.advancedConfig.notificationPreferences
    var packages by remember(prefs.allowedPackages) { mutableStateOf(prefs.allowedPackages.joinToString("\n")) }
    var showPicker by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("需开启本应用无障碍服务。仅处理已选应用的新通知；空白名单不处理任何通知，轻提示不保存通知正文。", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodyMedium)
        PreferenceSwitchItem(title = "轻提示", summary = "仅对白名单内应用生效", checked = prefs.reminderEnabled, onCheckedChange = { value -> repository.updateAdvancedConfig { it.copy(notificationPreferences = it.notificationPreferences.copy(reminderEnabled = value)) } })
        PreferenceSwitchItem(title = "提醒时震动", summary = "轻提示开启时生效", checked = prefs.vibrate, onCheckedChange = { value -> repository.updateAdvancedConfig { it.copy(notificationPreferences = it.notificationPreferences.copy(vibrate = value)) } })
        PreferenceSwitchItem(title = "自动跳转白名单通知", summary = "默认关闭；仅在解锁亮屏、面板收起且未编辑时打开通知页面", checked = prefs.autoJumpEnabled, onCheckedChange = { value -> repository.updateAdvancedConfig { it.copy(notificationPreferences = it.notificationPreferences.copy(autoJumpEnabled = value)) } })
        OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.padding(horizontal = 20.dp)) { Text("选择通知应用 · 已选 ${prefs.allowedPackages.size} 个") }
        TextButton(onClick = { advanced = !advanced }, modifier = Modifier.padding(horizontal = 20.dp)) { Text(if (advanced) "收起高级输入" else "高级输入") }
        if (advanced) {
            OutlinedTextField(packages, { packages = it }, label = { Text("应用包名（每行一个）") }, supportingText = { Text("用于没有桌面图标的应用；保存会替换整个白名单。留空保存即清空。") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
            TextButton(onClick = {
                val allowed = packages.split(Regex("[\\s,，;；]+" )).map { it.trim() }.filter { it.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")) }.take(100).toSet()
                repository.updateAdvancedConfig { it.copy(notificationPreferences = it.notificationPreferences.copy(allowedPackages = allowed)) }
            }, modifier = Modifier.padding(horizontal = 20.dp)) { Text("保存高级输入") }
        }
    }
    if (showPicker) NotificationApplicationPicker(prefs.allowedPackages, onDismiss = { showPicker = false }) { selected ->
        repository.updateAdvancedConfig { it.copy(notificationPreferences = it.notificationPreferences.copy(allowedPackages = selected)) }
        showPicker = false
    }
}

@Composable
private fun NotificationApplicationPicker(initial: Set<String>, onDismiss: () -> Unit, onSave: (Set<String>) -> Unit) {
    val context = LocalContext.current.applicationContext
    var apps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(initial) }
    LaunchedEffect(context) {
        apps = withContext(Dispatchers.IO) { AppAndIntentLauncher.getInstalledApps(context) }.distinctBy { it.packageName }
        loading = false
    }
    val visible = remember(apps, query) { apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) } }
    AlertDialog(onDismissRequest = onDismiss, title = {
        com.example.globalminusone.service.Android12WindowKeyGuard()
         Text("选择通知应用") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("已选 ${selected.size} 个；保存后生效。未列出的既有选择会保留，可在高级输入中移除。")
            OutlinedTextField(query, { query = it }, label = { Text("搜索应用名称或包名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (loading) CircularProgressIndicator()
            else if (visible.isEmpty()) Text("没有匹配的应用")
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                items(visible, key = { it.packageName }) { app ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).toggleable(
                        value = app.packageName in selected, role = Role.Checkbox,
                        onValueChange = { checked -> selected = if (checked) selected + app.packageName else selected - app.packageName }
                    ).padding(vertical = 12.dp)) {
                        Checkbox(checked = app.packageName in selected, onCheckedChange = null)
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(app.label, style = MaterialTheme.typography.bodyLarge)
                            Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            TextButton(onClick = { selected = emptySet() }) { Text("清空选择") }
        }
    }, confirmButton = { TextButton(onClick = { onSave(selected) }, enabled = !loading) { Text("保存已选应用") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
