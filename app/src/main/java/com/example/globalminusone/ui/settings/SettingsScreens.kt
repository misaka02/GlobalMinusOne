package com.example.globalminusone.ui.settings

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable

import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.globalminusone.ui.AppIcons as Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.globalminusone.data.ConfigProfile
import com.example.globalminusone.data.GestureAction
import com.example.globalminusone.data.IconClickBehavior
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.PageTransition
import com.example.globalminusone.data.ScenarioModeItem
import com.example.globalminusone.data.ShowSideMode
import com.example.globalminusone.data.TriggerPosition
import com.example.globalminusone.data.TriggerWay
import com.example.globalminusone.service.GlobalAccessibilityService
import com.example.globalminusone.service.OverlayService

// ======================================================================================
// 矢量图标定义
// ======================================================================================

internal fun customIcon(name: String, pathData: String): ImageVector {
    return ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = PathParser().parsePathString(pathData).toNodes(),
        fill = SolidColor(Color.Black)
    ).build()
}

internal val IconAccessibility: ImageVector by lazy {
    customIcon(
        "IconAccessibility",
        "M12 2c1.1 0 2 .9 2 2s-.9 2-2 2-2-.9-2-2 .9-2 2-2zm9 7h-6v13h-2v-6h-2v6H9V9H3V7h18v2z"
    )
}

internal val IconOverlayWindow: ImageVector by lazy {
    customIcon(
        "IconOverlayWindow",
        "M19 4H5c-1.11 0-2 .9-2 2v12c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 14H5V9h14v9zm0-11H5V6h14v1z"
    )
}

internal val IconFloppy: ImageVector by lazy {
    customIcon(
        "IconFloppy",
        "M17 3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V7l-4-4zm-5 16c-1.66 0-3-1.34-3-3s1.34-3 3-3 3 1.34 3 3-1.34 3-3 3zm3-10H5V5h10v4z"
    )
}

internal val IconDoNotDisturb: ImageVector by lazy {
    customIcon(
        "IconDoNotDisturb",
        "M12 22c1.1 0 2-.9 2-2h-4c0 1.1.9 2 2 2zm6-6v-5c0-3.07-1.63-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.64 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z"
    )
}

internal val IconBarChart: ImageVector by lazy {
    customIcon(
        "IconBarChart",
        "M4 9h4v11H4zm6-5h4v16h-4zm6 8h4v8h-4z"
    )
}

internal val IconPhoneAndroid: ImageVector by lazy {
    customIcon(
        "IconPhoneAndroid",
        "M17 1.01L7 1c-1.1 0-2 .9-2 2v18c0 1.1.9 2 2 2h10c1.1 0 2-.9 2-2V3c0-1.1-.9-1.99-2-1.99zM17 19H7V5h10v14z"
    )
}

internal val IconProhibited: ImageVector by lazy {
    customIcon(
        "IconProhibited",
        "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.42 0-8-3.58-8-8 0-1.85.63-3.55 1.69-4.9L16.9 18.31C15.55 19.37 13.85 20 12 20zm6.31-3.1L7.1 5.69C8.45 4.63 10.15 4 12 4c4.42 0 8 3.58 8 8 0 1.85-.63 3.55-1.69 4.9z"
    )
}

internal val IconAndroid: ImageVector by lazy {
    customIcon(
        "IconAndroid",
        "M6 18c0 .55.45 1 1 1h1v3.5c0 .83.67 1.5 1.5 1.5s1.5-.67 1.5-1.5V19h4v3.5c0 .83.67 1.5 1.5 1.5s1.5-.67 1.5-1.5V19h1c.55 0 1-.45 1-1V8H6v10zM3.5 8C2.67 8 2 8.67 2 9.5v7c0 .83.67 1.5 1.5 1.5S5 17.33 5 16.5v-7C5 8.67 4.33 8 3.5 8zm17 0c-.83 0-1.5.67-1.5 1.5v7c0 .83.67 1.5 1.5 1.5s1.5-.67 1.5-1.5v-7c0-.83-.67-1.5-1.5-1.5zm-4.97-4.84l1.3-1.3c.2-.2.2-.51 0-.71-.2-.2-.51-.2-.71 0l-1.48 1.48C13.85 2.23 12.95 2 12 2c-.96 0-1.86.23-2.66.63L7.85 1.15c-.2-.2-.51-.2-.71 0-.2.2-.2.51 0 .71l1.31 1.31C6.97 4.26 6 6.01 6 8h12c0-1.99-.97-3.75-2.47-4.84zM10 5H9V4h1v1zm5 0h-1V4h1v1z"
    )
}

internal val IconPuzzle: ImageVector by lazy {
    customIcon(
        "IconPuzzle",
        "M20.5 11H19V7c0-1.1-.9-2-2-2h-4V3.5C13 2.12 11.88 1 10.5 1S8 2.12 8 3.5V5H4c-1.1 0-1.99.9-1.99 2v3.8H3.5c1.49 0 2.7 1.21 2.7 2.7s-1.21 2.7-2.7 2.7H2V20c0 1.1.9 2 2 2h3.8v-1.5c0-1.49 1.21-2.7 2.7-2.7 1.49 0 2.7 1.21 2.7 2.7V22H17c1.1 0 2-.9 2-2v-4h1.5c1.38 0 2.5-1.12 2.5-2.5s-1.12-2.5-2.5-2.5z"
    )
}

internal val IconHelp: ImageVector by lazy {
    customIcon(
        "IconHelp",
        "M11 18h2v-2h-2v2zm1-16C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8zm0-14c-2.21 0-4 1.79-4 4h2c0-1.1.9-2 2-2s2 .9 2 2c0 2-3 1.75-3 5h2c0-2.25 3-2.5 3-5 0-2.21-1.79-4-4-4z"
    )
}

internal val IconInfo: ImageVector by lazy {
    customIcon(
        "IconInfo",
        "M11 17h2v-6h-2v6zm1-15C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8zm-1-13h2v2h-2v-2z"
    )
}

// ======================================================================================
// 根导航架构：独立设置主题 + 单页主界面 + 二级子页面
// ======================================================================================

@Composable
fun GlobalMinusOneMainApp(repository: MinusOneRepository, onReturnToPanel: () -> Unit = {}) {
    val context = LocalContext.current
    var currentScreen by rememberSaveable { mutableStateOf("MAIN") }
    val state by repository.state.collectAsState()
    // Activity settings write immediately. Resolve the overlay's draft before any
    // settings screen is composed, including re-entry while that Activity survives.
    if (state.editMode) {
        BackHandler { onReturnToPanel() }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("先结束面板编辑", style = MaterialTheme.typography.titleLarge)
                Text("设置会立即保存。请先保存或放弃当前面板草稿，再修改设置。")
                Button(onClick = { repository.finishPanelEdit(save = true) }) { Text("保存草稿并进入设置") }
                OutlinedButton(onClick = { repository.finishPanelEdit(save = false) }) { Text("放弃草稿并进入设置") }
                TextButton(onClick = onReturnToPanel) { Text("返回面板继续编辑") }
            }
        }
        return
    }

    // 系统物理返回键与手势返回拦截
    BackHandler(enabled = currentScreen != "MAIN") {
        currentScreen = if (currentScreen == "SB_SETTINGS") "OPERATION" else "MAIN"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
    ) {
        when (currentScreen) {
            "MAIN" -> MainHomeScreen(
                repository = repository,
                onNavigate = { currentScreen = it }
            )
            "OPERATION" -> OperationSettingsScreen(
                repository = repository,
                onBack = { currentScreen = "MAIN" },
                onNavigateToSBSettings = { currentScreen = "SB_SETTINGS" }
            )
            "SB_SETTINGS" -> SuspensionBallSettingsScreen(
                repository = repository,
                onBack = { currentScreen = "OPERATION" }
            )
            "THEME" -> ThemeSettingsScreen(
                repository = repository,
                onBack = { currentScreen = "MAIN" }
            )
            "ADVANCE" -> AdvanceSettingsScreen(
                repository = repository,
                onBack = { currentScreen = "MAIN" }
            )
            "EXTEND" -> ExtendSettingsScreen(
                repository = repository,
                onBack = { currentScreen = "MAIN" }
            )
            "CONFIG" -> ConfigSettingsScreen(
                repository = repository,
                onBack = { currentScreen = "MAIN" }
            )
            "ABOUT" -> AboutSettingsScreen(
                repository = repository,
                onBack = { currentScreen = "MAIN" }
            )
        }
    }
}

// ======================================================================================
// 1. 主界面：运行状态、权限检查、日常设置与管理帮助
// ======================================================================================

@Composable
private fun MainHomeScreen(
    repository: MinusOneRepository,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val state by repository.state.collectAsState()
    val triggerConfig = state.triggerConfig

    // 权限状态实时感应 (BUG-004: 同时监听辅助服务连接状态与系统设置开启状态)
    val a11yConnected by GlobalAccessibilityService.connectedState.collectAsState()
    var canDrawOverlays by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isAccessibilityEnabled by remember { mutableStateOf(GlobalAccessibilityService.instance != null) }
    var isAccessibilityEnabledInSettings by remember { mutableStateOf(GlobalAccessibilityService.isEnabledInSystemSettings(context)) }
    LaunchedEffect(a11yConnected) {
        isAccessibilityEnabled = a11yConnected || GlobalAccessibilityService.instance != null
        isAccessibilityEnabledInSettings = GlobalAccessibilityService.isEnabledInSystemSettings(context)
    }
    var canWriteSettings by remember { mutableStateOf(Settings.System.canWrite(context)) }
    var isNotificationPolicyGranted by remember {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        mutableStateOf(nm?.isNotificationPolicyAccessGranted == true)
    }
    var isBatteryOptIgnored by remember {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        mutableStateOf(pm?.isIgnoringBatteryOptimizations(context.packageName) == true)
    }
    var isUsageAccessGranted by remember {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        val mode = if (appOps != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            }
        } else AppOpsManager.MODE_ERRORED
        mutableStateOf(mode == AppOpsManager.MODE_ALLOWED)
    }
    var isStorageGranted by remember {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        mutableStateOf(granted)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canDrawOverlays = Settings.canDrawOverlays(context)
                isAccessibilityEnabled = GlobalAccessibilityService.instance != null
                isAccessibilityEnabledInSettings = GlobalAccessibilityService.isEnabledInSystemSettings(context)
                canWriteSettings = Settings.System.canWrite(context)
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                isNotificationPolicyGranted = nm?.isNotificationPolicyAccessGranted == true
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                isBatteryOptIgnored = pm?.isIgnoringBatteryOptimizations(context.packageName) == true
                val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                val mode = if (appOps != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
                    } else {
                        @Suppress("DEPRECATION")
                        appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
                    }
                } else AppOpsManager.MODE_ERRORED
                isUsageAccessGranted = mode == AppOpsManager.MODE_ALLOWED
                isStorageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Environment.isExternalStorageManager()
                } else {
                    context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val serviceReady by repository.isOverlayServiceRunning.collectAsState()
    val isServiceRunning = triggerConfig.serviceEnabled && canDrawOverlays && serviceReady
    val serviceContentColor = if (isServiceRunning) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }
    var permissionsExpanded by rememberSaveable { mutableStateOf(false) }
    var showTutorialDialog by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 36.dp)
    ) {
        // 顶部标题：全局负一屏
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "全局负一屏",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // 状态大卡片 (layout/y.xml)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .heightIn(min = 112.dp)
                    .clickable {
                        if (!isServiceRunning) {
                            if (!canDrawOverlays) {
                                Toast.makeText(context, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            } else {
                                repository.updateTriggerConfig { it.copy(serviceEnabled = true) }
                                OverlayService.start(context)
                                Toast.makeText(context, "正在启动全局负一屏", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            repository.updateTriggerConfig { it.copy(serviceEnabled = false) }
                            OverlayService.stop(context)
                            Toast.makeText(context, "已关闭全局负一屏", Toast.LENGTH_SHORT).show()
                        }
                    },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isServiceRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(
                            text = when {
                                isServiceRunning -> "服务运行中"
                                !canDrawOverlays && triggerConfig.serviceEnabled -> "缺少悬浮窗权限"
                                triggerConfig.serviceEnabled -> "服务未就绪 · 点击重试"
                                else -> "服务已关闭"
                            },
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = serviceContentColor
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isServiceRunning) "点击卡片关闭 · 右侧按钮打开面板" else "点击卡片开启全局负一屏",
                            fontSize = 14.sp,
                            color = serviceContentColor
                        )
                        if (triggerConfig.serviceEnabled && !isServiceRunning) TextButton(onClick = {
                            repository.updateTriggerConfig { it.copy(serviceEnabled = false) }
                            OverlayService.stop(context)
                        }) { Text("关闭主开关", color = serviceContentColor) }
                    }

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(serviceContentColor.copy(alpha = 0.12f))
                            .clickable {
                                if (isServiceRunning) {
                                    OverlayService.start(context, OverlayService.ACTION_TOGGLE_PANEL)
                                } else {
                                    if (!canDrawOverlays) {
                                        Toast.makeText(context, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                        context.startActivity(intent)
                                    } else {
                                        repository.updateTriggerConfig { it.copy(serviceEnabled = true) }
                                        OverlayService.start(context)
                                        Toast.makeText(context, "正在启动全局负一屏", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.KeyboardArrowRight,
                            contentDescription = "呼出预览",
                            tint = serviceContentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        item {
            PreferenceItem(
                title = "权限与运行检查",
                summary = "悬浮窗${if (canDrawOverlays) "已授权" else "未授权"} · 辅助服务${if (isAccessibilityEnabled) "已连接" else "未连接"} · ${if (permissionsExpanded) "点击收起" else "按所用功能查看权限"}",
                onClick = { permissionsExpanded = !permissionsExpanded }
            )
        }

        // 按需展开权限状态列表，所有入口保持原系统导航。
        item {
            AnimatedVisibility(
                visible = permissionsExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 6.dp)
                ) {
                    // 第 1 行：辅助服务 | 悬浮窗 | 手机存储
                    Column(modifier = Modifier.fillMaxWidth()) {
                        PermissionCircleItem(
                            title = if (!isAccessibilityEnabled && isAccessibilityEnabledInSettings) "辅助(需重开)»" else "辅助服务»",
                            icon = IconAccessibility,
                            granted = isAccessibilityEnabled,
                            onClick = {
                                if (!isAccessibilityEnabled && isAccessibilityEnabledInSettings) {
                                    Toast.makeText(context, "系统已开启但服务被后台断开，请关闭后再重新开启一次", Toast.LENGTH_LONG).show()
                                }
                                try {
                                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                } catch (_: Exception) {
                                    Toast.makeText(context, "无法打开辅助服务设置", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        PermissionCircleItem(
                            title = "悬浮窗»",
                            icon = IconOverlayWindow,
                            granted = canDrawOverlays,
                            onClick = {
                                try {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    Toast.makeText(context, "无法打开悬浮窗设置", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        PermissionCircleItem(
                            title = "手机存储»",
                            icon = IconFloppy,
                            granted = isStorageGranted,
                            onClick = {
                                try {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                        context.startActivity(intent)
                                    } else {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                                        context.startActivity(intent)
                                    }
                                } catch (_: Exception) {
                                    val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                                    context.startActivity(fallback)
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 第 2 行：修改设置 | 勿扰 | 使用数据
                    Column(modifier = Modifier.fillMaxWidth()) {
                        PermissionCircleItem(
                            title = "修改设置»",
                            icon = Icons.Settings,
                            granted = canWriteSettings,
                            onClick = {
                                try {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_WRITE_SETTINGS,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    Toast.makeText(context, "无法打开修改系统设置页", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        PermissionCircleItem(
                            title = "勿扰»",
                            icon = IconDoNotDisturb,
                            granted = isNotificationPolicyGranted,
                            onClick = {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                                } catch (_: Exception) {
                                    Toast.makeText(context, "无法打开勿扰权限设置", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        PermissionCircleItem(
                            title = "使用数据»",
                            icon = IconBarChart,
                            granted = isUsageAccessGranted,
                            onClick = {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                } catch (_: Exception) {
                                    Toast.makeText(context, "无法打开使用数据访问设置", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 第 3 行：设备管理 | 后台弹出界面 | 电池优化
                    Column(modifier = Modifier.fillMaxWidth()) {
                        PermissionCircleItem(
                            title = "设备管理»",
                            icon = IconPhoneAndroid,
                            granted = false,
                            onClick = {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                                } catch (_: Exception) {
                                    Toast.makeText(context, "无法打开设备管理设置", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        PermissionCircleItem(
                            title = "后台弹出界面»",
                            icon = IconProhibited,
                            granted = canDrawOverlays,
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    Toast.makeText(context, "无法打开应用详情设置", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        PermissionCircleItem(
                            title = "电池优化»",
                            icon = Icons.BatteryStd,
                            granted = isBatteryOptIgnored,
                            onClick = {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                } catch (_: Exception) {
                                    Toast.makeText(context, "无法打开电池优化设置", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    TextButton(
                        onClick = { permissionsExpanded = false },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text("收起权限检查")
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(2.dp)) }

        item { PreferenceCategoryTitle(title = "日常设置") }
        item {
            MainMenuItemRow(
                icon = Icons.TouchApp,
                title = "触发设置",
                subtitle = "触发位置、区域、悬浮球等",
                onClick = { onNavigate("OPERATION") }
            )
        }

        item {
            MainMenuItemRow(
                icon = Icons.Palette,
                title = "主题外观",
                subtitle = "设置页深浅色、面板、卡片与动画",
                onClick = { onNavigate("THEME") }
            )
        }

        item { PreferenceCategoryTitle(title = "更多功能") }

        item {
            MainMenuItemRow(
                icon = IconAndroid,
                title = "高级设置",
                subtitle = "Root模式、黑名单、启动行为等",
                onClick = { onNavigate("ADVANCE") }
            )
        }

        item {
            MainMenuItemRow(
                icon = IconPuzzle,
                title = "扩展功能",
                subtitle = "验证码、护眼滤色与情景规则",
                onClick = { onNavigate("EXTEND") }
            )
        }

        item { PreferenceCategoryTitle(title = "配置与帮助") }

        item {
            MainMenuItemRow(
                icon = IconFloppy,
                title = "配置管理",
                subtitle = "备份、导入、删除配置文件等",
                onClick = { onNavigate("CONFIG") }
            )
        }

        item {
            MainMenuItemRow(
                icon = IconHelp,
                title = "使用教程",
                subtitle = "一些常见的使用问题",
                onClick = { showTutorialDialog = true }
            )
        }

        item {
            MainMenuItemRow(
                icon = IconInfo,
                title = "关于",
                subtitle = "开发者、开源仓库、AI 开发说明与隐私政策",
                onClick = { onNavigate("ABOUT") }
            )
        }
    }

    if (showTutorialDialog) {
        TutorialDialog(onDismiss = { showTutorialDialog = false })
    }
}

/** 权限入口展示系统可读状态；设备管理等无可靠检测项不伪报授权。 */
@Composable
private fun PermissionCircleItem(
    title: String,
    icon: ImageVector,
    granted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp))
        Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(title.removeSuffix("»"), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface)
            Text(
                when {
                    title.startsWith("设备管理") || title.startsWith("后台弹出") -> "打开系统设置查看"
                    granted -> "已就绪 · 点击管理"
                    else -> "未就绪 · 按功能需要设置"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(Icons.KeyboardArrowRight, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(4.dp))
}

/**
 * 主菜单单行列表项（左侧灰色图标 + 标题与副标题 + 右侧 >）
 */
@Composable
private fun MainMenuItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .heightIn(min = 80.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(22.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            imageVector = Icons.KeyboardArrowRight,
            contentDescription = "进入",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
    }
}

// ======================================================================================
// 2. 触发设置页面 ("OPERATION")
// ======================================================================================



// ======================================================================================
// 3. 悬浮球设置页面 ("SB_SETTINGS")
// ======================================================================================



// ======================================================================================
// 4. 主题外观页面 ("THEME")
// ======================================================================================



// ======================================================================================
// 5. 高级设置页面 ("ADVANCE")
// ======================================================================================



// ======================================================================================
// 6. 扩展功能页面 ("EXTEND")
// ======================================================================================



// ======================================================================================
// 7. 配置管理页面 ("CONFIG")
// ======================================================================================



/**
 * 固定布局示意，不代表方案实际内容；使用中标记来自当前方案ID。
 */




// ======================================================================================
// 8. 关于页面 ("ABOUT")
// ======================================================================================

private const val PROJECT_DEVELOPER = "misaka02"
private const val PROJECT_DEVELOPER_URL = "https://github.com/misaka02"
private const val PROJECT_REPO_URL = "https://github.com/misaka02/GlobalMinusOne"
private const val PROJECT_ISSUES_URL = "https://github.com/misaka02/GlobalMinusOne/issues"

private fun openProjectWebUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }.onFailure {
        Toast.makeText(context, "未找到可用浏览器，请手动访问：$url", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun AboutSettingsScreen(
    repository: MinusOneRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val versionLabel = remember(context) {
        runCatching {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            "v${info.versionName ?: "2.4.43"}"
        }.getOrDefault("v2.4.43")
    }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SubPageTopBar(title = "关于", onBack = onBack)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 36.dp)
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "全局负一屏 · GlobalMinusOne $versionLabel",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "AI 开发声明：本项目主要由 AI 辅助与主导完成架构设计、代码实现及测试（AI-Developed / AI-Assisted），使用 Kotlin + Jetpack Compose 构建，遵循 Apache License 2.0 协议开源。",
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "开发者：$PROJECT_DEVELOPER · 仓库：$PROJECT_REPO_URL",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            item { PreferenceCategoryTitle(title = "项目与开发者信息") }

            item {
                PreferenceItem(
                    title = "开发者",
                    summary = "$PROJECT_DEVELOPER（$PROJECT_DEVELOPER_URL）",
                    onClick = { openProjectWebUrl(context, PROJECT_DEVELOPER_URL) }
                )
            }

            item {
                PreferenceItem(
                    title = "开源项目地址",
                    summary = PROJECT_REPO_URL,
                    onClick = { openProjectWebUrl(context, PROJECT_REPO_URL) }
                )
            }

            item {
                PreferenceItem(
                    title = "问题反馈与交流",
                    summary = "$PROJECT_ISSUES_URL（点击前往 GitHub Issues 提交反馈）",
                    onClick = { openProjectWebUrl(context, PROJECT_ISSUES_URL) }
                )
            }

            item { PreferenceDivider() }

            item { PreferenceCategoryTitle(title = "合规与维护") }

            item {
                PreferenceItem(
                    title = "开源许可与第三方声明",
                    summary = "Apache-2.0 · 包含 AI 开发说明及 RE2/J、Shizuku API、libsu 归属",
                    onClick = { showLicensesDialog = true }
                )
            }

            item {
                PreferenceItem(
                    title = "隐私政策",
                    summary = "点击查看软件隐私合规与数据安全声明",
                    onClick = { showPrivacyDialog = true }
                )
            }

            item {
                PreferenceItem(
                    title = "恢复初始默认布局",
                    summary = "重置负一屏所有卡片与排版为初始状态",
                    onClick = {
                        if (repository.state.value.advancedConfig.layoutLocked) {
                            Toast.makeText(context, "请先解锁布局，再恢复默认", Toast.LENGTH_SHORT).show()
                        } else {
                            showResetConfirmDialog = true
                        }
                    }
                )
            }

            item {
                PreferenceItem(
                    title = "高级版服务",
                    summary = "账户、购买与激活尚未实现",
                    enabled = false,
                    onClick = {}
                )
            }

        }
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = {
                com.example.globalminusone.service.Android12WindowKeyGuard()
                Text("恢复初始默认布局", fontWeight = FontWeight.Bold) },
            text = { Text("此操作将清除所有自定义卡片与排版，恢复为默认的线性卡片流（应用、快捷方式、支付、网站与操作栏），是否确认重置？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (repository.state.value.advancedConfig.layoutLocked) {
                            Toast.makeText(context, "请先解锁布局，再恢复默认", Toast.LENGTH_SHORT).show()
                        } else {
                            repository.resetToDefaults()
                            Toast.makeText(context, "已恢复初始默认布局", Toast.LENGTH_SHORT).show()
                        }
                        showResetConfirmDialog = false
                    }
                ) {
                    Text("确认重置", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = {
                com.example.globalminusone.service.Android12WindowKeyGuard()
                Text("隐私政策与合规声明", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "《全局负一屏》严格保护您的隐私数据：\n\n" +
                            "1. 本软件为纯本地离线效率工具，无需任何网络服务器，不收集、不出售任何个人隐私或设备信息；\n" +
                            "2. 悬浮窗与无障碍服务权限仅用于在屏幕边缘识别滑动或触摸以呼出负一屏面板，不监听任何按键或文本输入；\n" +
                            "3. 存储权限仅用于读取与备份用户自定义的配置文件；\n" +
                            "4. 所有应用列表、快捷方式与配置数据仅保存在您手机本地的私有存储空间中。"
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text("同意并关闭", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }

    if (showLicensesDialog) {
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            title = {
                com.example.globalminusone.service.Android12WindowKeyGuard()
                Text("开源许可与第三方声明", fontWeight = FontWeight.Bold)
            },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Text(
                            text = "本应用以 Apache License 2.0 协议开源（主要由 AI 辅助与主导开发，由 $PROJECT_DEVELOPER 维护发布，项目地址：$PROJECT_REPO_URL），感谢并声明所使用第三方开源组件：",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    item {
                        Text(
                            text = "• AndroidX / Jetpack Compose / Kotlin / Kotlinx Serialization (Apache-2.0)\n" +
                                "• Google RE2/J 1.8 (Go / BSD-3-Clause, Copyright 2009 The Go Authors)\n" +
                                "• RikkaApps Shizuku API 13.1.5 (MIT License, Copyright 2021 RikkaW)\n" +
                                "• topjohnwu libsu 6.0.0 (Apache-2.0，包含本地 root-session-retry 补丁；完整许可原文打包于应用 assets/licenses 及 assets/third_party/)",
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicensesDialog = false }) {
                    Text("关闭", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }
}

// ======================================================================================
// 通用偏好项：自适应高度、统一说明与可访问控件
// ======================================================================================













// ======================================================================================
// 通用弹窗组件（单选、12动作、教程、应用多选、配置导入与详情）
// ======================================================================================





@Composable
private fun TutorialDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            com.example.globalminusone.service.Android12WindowKeyGuard()
            Text("全局负一屏 · 使用教程", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text(
                        text = "1. 侧边滑出：\n在任意界面从屏幕预设边缘（默认左侧70%高度处）向内滑动，即可呼出沉浸式负一屏面板。",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                item {
                    Text(
                        text = "2. 底部呼出 9 键菜单：\n在负一屏面板非卡片区域，从屏幕底部向上轻滑超过50px，或点击卡片底部的【+】操作栏卡片，即可呼出底部的 9 键管理浮层工具栏！",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                item {
                    Text(
                        text = "3. 编辑与组件添加：\n在 9 键工具栏中点击【编辑模式】，卡片右上角会出现红色序号，长按卡片可修改尺寸、坐标与外观；点击【添加】可自由选用 22 类系统功能组件。",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                item {
                    Text(
                        text = "4. 自由布局网格：\n支持新建自由布局分页，支持在 32 等分网格坐标系中拖动移动、拉伸 4 个边缘缩放，随心所欲定制您的专属桌面！",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("我知道了", color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}

data class AppItemInfo(val packageName: String, val label: String)












