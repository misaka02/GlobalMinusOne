package com.example.globalminusone.ui.panel.cards

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.TrafficStats
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.globalminusone.system.AppAndIntentLauncher
import com.example.globalminusone.system.InstalledAppInfo
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.ui.AppIcons as Icons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import java.util.Locale
import com.example.globalminusone.ui.panel.gesture.PanelGestureAxis
import com.example.globalminusone.ui.panel.gesture.ownPanelScrollGestures
import com.example.globalminusone.ui.panel.gesture.shouldEnableChildHorizontalScroll

private data class UsageData(val allowed: Boolean = false, val loaded: Boolean = false,
    val failed: Boolean = false, val recent: List<InstalledAppInfo> = emptyList(), val todayMillis: Long = 0)

private val usageDataMutex = Mutex()
private var cachedUsageData: UsageData? = null
private var cachedUsageAt = 0L
private var cachedUsagePermission = false

private suspend fun sharedUsageData(context: Context): UsageData = withContext(Dispatchers.IO) {
    usageDataMutex.withLock {
        val permission = usageAccessAllowed(context)
        val now = SystemClock.elapsedRealtime()
        val cached = cachedUsageData
        if (cached != null && permission == cachedUsagePermission && now - cachedUsageAt < 30_000L) cached
        else readUsageData(context, permission).also {
            cachedUsageData = it
            cachedUsagePermission = permission
            cachedUsageAt = SystemClock.elapsedRealtime()
        }
    }
}

private fun usageAccessAllowed(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps?.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    } else {
        @Suppress("DEPRECATION")
        appOps?.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun readUsageData(context: Context, allowed: Boolean): UsageData {
    if (!allowed) return UsageData(loaded = true)
    return try {
        val now = System.currentTimeMillis()
        val midnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        // Query today + 24h lookback to cover continuous sessions starting the previous day.
        val query = manager.queryEvents(midnight - 24L * 3600_000L, now)
        val event = UsageEvents.Event()
        val transitions = mutableListOf<AppUsageTransition>()
        while (query.hasNextEvent()) {
            query.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED ->
                    transitions += AppUsageTransition(event.packageName, event.timeStamp, true, event.className)
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED ->
                    transitions += AppUsageTransition(event.packageName, event.timeStamp, false, event.className)
                16 /* SCREEN_NON_INTERACTIVE */, 17 /* KEYGUARD_SHOWN */, 26 /* DEVICE_SHUTDOWN */ ->
                    transitions += AppUsageTransition(null, event.timeStamp, false)
            }
        }
        val summary = summarizeAppUsage(transitions, midnight, now)
        val apps = AppAndIntentLauncher.getInstalledAppsSync(context).associateBy { it.packageName }
        val recentList = summary.recentPackages.mapNotNull { apps[it] }.take(8).toMutableList()
        if (recentList.size < 8) {
            val seen = recentList.mapTo(HashSet()) { it.packageName }
            val fallbackStats = runCatching {
                manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 7L * 24 * 60 * 60 * 1000, now)
            }.getOrNull().orEmpty().sortedByDescending { it.lastTimeUsed }
            for (stat in fallbackStats) {
                if (recentList.size >= 8) break
                val pkg = stat.packageName ?: continue
                if (pkg !in seen) {
                    apps[pkg]?.let { recentList.add(it); seen.add(pkg) }
                }
            }
        }
        UsageData(allowed = true, loaded = true, recent = recentList, todayMillis = summary.foregroundMillis)
    } catch (_: Exception) { UsageData(allowed = true, loaded = true, failed = true) }
}

@Composable
private fun rememberUsageData(): UsageData {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val expanded by remember(context) { MinusOneRepository.getInstance(context).isPanelExpanded }.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    var data by remember { mutableStateOf(UsageData()) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(context, refresh, expanded) {
        if (expanded || refresh == 0) data = sharedUsageData(context.applicationContext)
    }
    return data
}

@Composable
private fun UsagePermissionPrompt(isEditMode: Boolean, label: String = "需授权查看最近使用") {
    val context = LocalContext.current
    TextButton(enabled = !isEditMode, onClick = {
        try { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: Exception) { Toast.makeText(context, "无法打开使用情况授权页面", Toast.LENGTH_SHORT).show() }
    }) { Text(label, color = LocalCardColors.current.accent, fontSize = 11.sp) }
}

@Composable
internal fun RealRecentApps(isEditMode: Boolean, onRequestClose: () -> Unit) {
    val data = rememberUsageData()
    val context = LocalContext.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            !data.loaded -> Text("读取中…", color = LocalCardColors.current.textSecondary, fontSize = 11.sp)
            !data.allowed -> UsagePermissionPrompt(isEditMode)
            data.failed -> Text("使用记录暂不可用", color = LocalCardColors.current.textSecondary, fontSize = 11.sp)
            data.recent.isEmpty() -> Text("暂无最近使用记录", color = LocalCardColors.current.textSecondary, fontSize = 11.sp)
            else -> {
                val recentScroll = rememberScrollState()
                val recentScrollEnabled = shouldEnableChildHorizontalScroll(!isEditMode, recentScroll.maxValue)
                Row(Modifier.fillMaxSize()
                    .ownPanelScrollGestures(recentScrollEnabled, axis = PanelGestureAxis.Horizontal)
                    .horizontalScroll(recentScroll, enabled = recentScrollEnabled)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                data.recent.forEach { app ->
                    Box(Modifier.width(52.dp)) {
                        AppIconComponent(packageName = app.packageName, title = app.label, enabled = !isEditMode,
                            onClick = { if (!isEditMode) { AppAndIntentLauncher.launchApp(context, app.packageName); onRequestClose() } })
                    }
                }
                }
            }
        }
    }
}

@Composable
internal fun RealScreenTime(isEditMode: Boolean = LocalIsEditMode.current) {
    val data = rememberUsageData()
    Column(Modifier.fillMaxSize().padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (data.loaded && !data.allowed) {
            UsagePermissionPrompt(isEditMode, label = "授权使用情况")
        } else {
            Text(when { !data.loaded -> "读取中…"; data.failed -> "记录暂不可用"
                else -> "${data.todayMillis / 3600000}小时${data.todayMillis % 3600000 / 60000}分" },
                color = LocalCardColors.current.textPrimary, fontSize = 13.sp)
        }
        Text("今日应用前台合计", color = LocalCardColors.current.textSecondary, fontSize = 10.sp)
    }
}

@Composable
internal fun RealAppCount() {
    val context = LocalContext.current
    var count by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(context) { count = AppAndIntentLauncher.getInstalledApps(context).size }
    StatCountComponent(count?.toString() ?: "读取中", "可启动应用", Icons.Rounded.Apps)
}

@Composable
internal fun RealTrafficCount() {
    val context = LocalContext.current
    val expanded by remember(context) { MinusOneRepository.getInstance(context).isPanelExpanded }.collectAsState()
    var total by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(expanded) {
        total = withContext(Dispatchers.IO) {
            val rx = TrafficStats.getTotalRxBytes()
            val tx = TrafficStats.getTotalTxBytes()
            if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) null else rx + tx
        }
    }
    val text = total?.let { if (it >= 1024L * 1024 * 1024) String.format(Locale.ROOT, "%.2f GB", it / (1024.0 * 1024 * 1024))
        else String.format(Locale.ROOT, "%.1f MB", it / (1024.0 * 1024)) } ?: "暂不可用"
    StatCountComponent(text, "本次开机流量", Icons.Rounded.SwapHoriz)
}
