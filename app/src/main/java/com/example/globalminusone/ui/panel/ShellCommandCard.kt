package com.example.globalminusone.ui.panel

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.system.AppShellExecutor
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun ShellCommandCard(item: ComponentItem, enabled: Boolean) {
    var opened by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    CustomShortcutComponent(item, onClick = { if (enabled) opened = true }, enabled = enabled)
    if (opened) PanelAlertDialog(onDismissRequest = { pending?.cancel(); opened = false },
        title = { Text(item.title) }, text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("以本应用普通UID启动，不自动请求Root。执行前请检查命令本身的行为。5秒或64KiB输出上限；取消不能撤销已执行的操作。")
                Text(item.textContent.ifBlank { "尚未配置命令，请打开卡片属性" })
                if (output.isNotEmpty()) Text(output)
            }
        }, confirmButton = {
            TextButton(enabled = pending == null && item.textContent.isNotBlank(), onClick = {
                pending = scope.launch {
                    try {
                        output = "执行中…"
                        output = runCatching { AppShellExecutor.execute(context, item.textContent) }
                            .fold({ "${if (it.timedOut) "超时或输出达到上限，已终止当前进程" else "退出码：${it.exitCode}"}\n${it.output}" },
                                { "执行失败或已取消：${it.message.orEmpty()}" })
                    } finally { pending = null }
                }
            }) { Text("执行命令") }
        }, dismissButton = { TextButton(onClick = { pending?.cancel(); opened = false }) { Text(if (pending != null) "取消执行" else "关闭") } })
}
