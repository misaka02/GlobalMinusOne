package com.example.globalminusone.split

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.globalminusone.system.AppSplitLauncher
import com.example.globalminusone.theme.SettingsTheme
import kotlinx.coroutines.*

private data class SplitApp(val component: ComponentName, val label: String)

/** Selection UI only. The first split pane is always the captured external task, never this Activity. */
class SplitLaunchActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var transaction: Job? = null
    private var generation = 0L
    private var origin by mutableStateOf<SplitOrigin?>(null)
    private var status by mutableStateOf("选择第二个应用，保留面板前的原应用")
    private var busy by mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readRequest(intent, autoLaunch = savedInstanceState == null)
        setContent { SettingsTheme(darkTheme = isSystemInDarkTheme()) { Surface(Modifier.fillMaxSize()) { AppList() } } }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent); readRequest(intent, autoLaunch = true)
    }
    private fun readRequest(value: Intent, autoLaunch: Boolean) {
        val requestGeneration = ++generation
        val previous = transaction
        val pkg = value.getStringExtra(AppSplitLauncher.ORIGIN_PACKAGE).orEmpty()
        val task = value.getIntExtra(AppSplitLauncher.ORIGIN_TASK, -1)
        val user = value.getIntExtra(AppSplitLauncher.ORIGIN_USER, -1)
        val capture = value.getLongExtra(AppSplitLauncher.ORIGIN_CAPTURE, -1)
        origin = if (validSplitPackage(pkg) && pkg != packageName && task > 0 && user >= 0 && capture > 0)
            SplitOrigin(pkg, task, user, capture) else null
        status = if (origin == null) "原应用信息已失效，请关闭后重新打开面板" else "选择第二个应用，保留面板前的原应用"
        transaction = scope.launch {
            previous?.cancelAndJoin()
            if (generation != requestGeneration) return@launch
            busy = false
            val component = if (autoLaunch) value.getStringExtra(AppSplitLauncher.TARGET)?.let(ComponentName::unflattenFromString) else null
            if (component != null) performPair(component, requestGeneration)
        }
    }
    private fun select(component: ComponentName) {
        if (busy) return
        val requestGeneration = ++generation
        transaction = scope.launch { performPair(component, requestGeneration) }
    }
    private suspend fun performPair(component: ComponentName, requestGeneration: Long) {
        val source = origin ?: return
        val target = runCatching { packageManager.getActivityInfo(component, 0) }.getOrNull()
        if (target == null || !target.enabled || !target.applicationInfo.enabled || !target.exported ||
            component.packageName == packageName || component.packageName == source.packageName) {
            status = "请选择与原应用不同、可正常启动的第二个应用"; return
        }
        val executor = AppSplitLauncher.authorizedExecutor(applicationContext)
        if (executor == null) { status = "Root或Shizuku会话已失效，分屏未执行"; return }
        busy = true; status = "正在配对原应用与所选应用…"
        try {
            val result = SplitPairController(executor).pair(source, component.flattenToString(), component.packageName)
            if (generation != requestGeneration) return
            status = result.message
            Toast.makeText(applicationContext, result.message, Toast.LENGTH_LONG).show()
            if (result.confirmed) finish()
        } finally { if (generation == requestGeneration) busy = false }
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    @Composable private fun AppList() {
        var query by remember { mutableStateOf("") }
        var loadFailed by remember { mutableStateOf(false) }
        val apps by produceState<List<SplitApp>?>(null) {
            val result = withContext(Dispatchers.IO) { runCatching {
                packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                    .filter { it.activityInfo.enabled && it.activityInfo.applicationInfo.enabled && it.activityInfo.exported && it.activityInfo.packageName != packageName }
                    .map { SplitApp(ComponentName(it.activityInfo.packageName, it.activityInfo.name), it.loadLabel(packageManager).toString()) }
                    .distinctBy { it.component }.sortedBy { it.label.lowercase(java.util.Locale.ROOT) }
            } }
            loadFailed = result.isFailure; value = result.getOrDefault(emptyList())
        }
        val filtered = remember(apps, query, origin) { apps.orEmpty().filter {
            it.component.packageName != origin?.packageName &&
                (query.isBlank() || it.label.contains(query, true) || it.component.packageName.contains(query, true))
        } }
        val sourceLabel = apps?.firstOrNull { it.component.packageName == origin?.packageName }?.label ?: origin?.packageName.orEmpty()
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 12.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("应用分屏", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { finish() }) { Text("关闭") }
            }
            Text("原应用：$sourceLabel", Modifier.padding(vertical = 4.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(status, Modifier.padding(vertical = 6.dp), style = MaterialTheme.typography.bodySmall,
                color = if (busy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedTextField(query, { query = it.take(200) }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("查找第二个应用") })
            when {
                apps == null -> Text("正在读取应用…", Modifier.padding(12.dp))
                loadFailed -> Text("无法读取应用列表，请关闭后重试", Modifier.padding(12.dp))
                filtered.isEmpty() -> Text("没有匹配的应用", Modifier.padding(12.dp))
                else -> LazyColumn(Modifier.weight(1f)) {
                    items(filtered, key = { it.component.flattenToString() }) { app ->
                        TextButton(onClick = { select(app.component) }, enabled = !busy && origin != null,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(app.component.packageName, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}
