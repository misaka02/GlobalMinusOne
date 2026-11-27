package com.example.globalminusone.ui.settings

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
import com.example.globalminusone.ui.motion.SettingsScreenTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.globalminusone.ui.AppIcons as Icons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.resolvedModalMenuMs
import com.example.globalminusone.service.GlobalAccessibilityService
import com.example.globalminusone.service.OverlayService

// ======================================================================================
// 根导航架构：独立设置主题 + 单页主界面 + 二级子页面
// ======================================================================================

@Composable
fun GlobalMinusOneMainApp(
    repository: MinusOneRepository,
    initialScreen: String? = null,
    navigationRequest: SettingsNavigationRequest? = null,
    onReturnToPanel: () -> Unit = {}
) {
    val context = LocalContext.current
    val requestedStart = navigationRequest?.screen ?: initialScreen ?: "MAIN"
    val startScreen = if (requestedStart == "CONFIG_IMPORT_PAGE") "CONFIG" else requestedStart
    var currentScreen by rememberSaveable { mutableStateOf(startScreen) }
    var lastConsumedNonce by rememberSaveable { mutableLongStateOf(navigationRequest?.nonce ?: 0L) }
    var forceExpandOcrToken by rememberSaveable { mutableLongStateOf(if (startScreen == "SCREEN_ASSISTANT") 1L else 0L) }
    var autoLaunchPageImportToken by rememberSaveable { mutableLongStateOf(if (requestedStart == "CONFIG_IMPORT_PAGE") 1L else 0L) }
    var pendingTargetGroup by rememberSaveable { mutableStateOf("") }
    var pendingJumpNonce by rememberSaveable { mutableLongStateOf(0L) }
    LaunchedEffect(navigationRequest) {
        if (SettingsNavigationPolicy.shouldApply(navigationRequest, lastConsumedNonce)) {
            val request = navigationRequest!!
            val target = if (request.screen == "CONFIG_IMPORT_PAGE") "CONFIG" else request.screen
            currentScreen = target
            pendingTargetGroup = if (target == "SCREEN_ASSISTANT") "OCR" else ""
            pendingJumpNonce += 1L
            lastConsumedNonce = request.nonce
            if (target == "SCREEN_ASSISTANT") {
                forceExpandOcrToken += 1L
            } else if (request.screen == "CONFIG_IMPORT_PAGE") {
                autoLaunchPageImportToken += 1L
            }
        }
    }
    LaunchedEffect(initialScreen) {
        if (navigationRequest == null && !initialScreen.isNullOrBlank()) {
            val target = if (initialScreen == "CONFIG_IMPORT_PAGE") "CONFIG" else initialScreen
            currentScreen = target
            if (target == "SCREEN_ASSISTANT") {
                pendingTargetGroup = "OCR"
                pendingJumpNonce += 1L
                forceExpandOcrToken += 1L
            } else if (initialScreen == "CONFIG_IMPORT_PAGE") {
                autoLaunchPageImportToken += 1L
            }
        }
    }
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

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        SettingsScreenTransition(
            targetScreen = currentScreen,
            animMs = state.themeConfig.resolvedModalMenuMs()
        ) { screen ->
            when (screen) {
                "MAIN" -> MainHomeScreen(
                    repository = repository,
                    onNavigate = {
                        pendingTargetGroup = ""
                        currentScreen = it
                    },
                    onJumpSearchEntry = { entry ->
                        pendingTargetGroup = entry.targetGroup
                        pendingJumpNonce += 1L
                        if (entry.expandOcrSection) {
                            forceExpandOcrToken += 1L
                        }
                        if (entry.targetScreen != "PERMISSIONS" && entry.targetScreen != "TUTORIAL") {
                            currentScreen = entry.targetScreen
                        }
                    }
                )
                "OPERATION" -> OperationSettingsScreen(
                    repository = repository,
                    initialExpandedGroup = pendingTargetGroup,
                    jumpNonce = pendingJumpNonce,
                    onBack = { currentScreen = "MAIN" },
                    onNavigateToSBSettings = {
                        pendingTargetGroup = ""
                        currentScreen = "SB_SETTINGS"
                    }
                )
                "SB_SETTINGS" -> SuspensionBallSettingsScreen(
                    repository = repository,
                    initialExpandedGroup = pendingTargetGroup,
                    jumpNonce = pendingJumpNonce,
                    onBack = { currentScreen = "OPERATION" }
                )
                "THEME" -> ThemeSettingsScreen(
                    repository = repository,
                    initialExpandedGroup = pendingTargetGroup,
                    jumpNonce = pendingJumpNonce,
                    onBack = { currentScreen = "MAIN" }
                )
                "ADVANCE" -> AdvanceSettingsScreen(
                    repository = repository,
                    initialExpandedGroup = pendingTargetGroup,
                    jumpNonce = pendingJumpNonce,
                    onBack = { currentScreen = "MAIN" }
                )
                "EXTEND" -> ExtendSettingsScreen(
                    repository = repository,
                    initialExpandedGroup = pendingTargetGroup,
                    jumpNonce = pendingJumpNonce,
                    onBack = { currentScreen = "MAIN" }
                )
                "SCREEN_ASSISTANT" -> ScreenAssistantSettingsScreen(
                    repository = repository,
                    forceExpandOcrToken = forceExpandOcrToken,
                    initialExpandedGroup = pendingTargetGroup,
                    jumpNonce = pendingJumpNonce,
                    onBack = { currentScreen = "MAIN" }
                )
                "CONFIG" -> ConfigSettingsScreen(
                    repository = repository,
                    autoLaunchPageImportToken = autoLaunchPageImportToken,
                    onBack = { currentScreen = "MAIN" }
                )
                "ABOUT" -> AboutSettingsScreen(
                    repository = repository,
                    onBack = { currentScreen = "MAIN" }
                )
            }
        }
    }
}

// ======================================================================================
// 1. 主界面：运行状态、权限检查、日常设置与管理帮助
// ======================================================================================

@Composable
private fun MainHomeScreen(
    repository: MinusOneRepository,
    onNavigate: (String) -> Unit,
    onJumpSearchEntry: (SettingsSearchEntry) -> Unit
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
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val searchResults = remember(searchQuery) {
        SettingsSearchIndex.search(searchQuery, com.example.globalminusone.ui.PlatformPinyinProvider.instance)
    }

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

        // 设置搜索栏
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("搜索任意设置（支持拼音/首字母，如：高斯模糊、xfq、护眼、备份…）", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Search, contentDescription = "搜索") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Close, contentDescription = "清除搜索")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
        }

        if (searchQuery.isNotBlank()) {
            item {
                PreferenceCategoryTitle(title = "搜索结果 (${searchResults.size})")
            }
            if (searchResults.isEmpty()) {
                item {
                    Text(
                        text = "未找到匹配的设置项，请尝试其它关键词或拼音缩写（如：主题、鼠标速度、xfq、词库、护眼）",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(searchResults, key = { it.id }) { entry ->
                    PreferenceItem(
                        title = "${entry.title}  ·  ${entry.category}",
                        summary = entry.subtitle,
                        onClick = {
                            searchQuery = ""
                            when (entry.targetScreen) {
                                "PERMISSIONS" -> permissionsExpanded = true
                                "TUTORIAL" -> showTutorialDialog = true
                                else -> onJumpSearchEntry(entry)
                            }
                        }
                    )
                }
            }
        } else {

        // 状态大卡片：开关独立控制，点击次按钮呼出面板，杜绝误触关闭服务
        item {
            ServiceStatusHeroCard(
                isServiceRunning = isServiceRunning,
                serviceEnabled = triggerConfig.serviceEnabled,
                canDrawOverlays = canDrawOverlays,
                serviceContentColor = serviceContentColor,
                onToggleService = { enabled ->
                    if (enabled && !canDrawOverlays) {
                        Toast.makeText(context, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    } else if (enabled) {
                        repository.updateTriggerConfig { it.copy(serviceEnabled = true) }
                        OverlayService.start(context)
                        Toast.makeText(context, "正在启动全局负一屏", Toast.LENGTH_SHORT).show()
                    } else {
                        repository.updateTriggerConfig { it.copy(serviceEnabled = false) }
                        OverlayService.stop(context)
                        Toast.makeText(context, "已关闭全局负一屏", Toast.LENGTH_SHORT).show()
                    }
                },
                onPrimaryAction = {
                    if (isServiceRunning) {
                        OverlayService.start(context, OverlayService.ACTION_TOGGLE_PANEL)
                    } else if (!canDrawOverlays) {
                        Toast.makeText(context, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                        )
                    } else {
                        repository.updateTriggerConfig { it.copy(serviceEnabled = true) }
                        OverlayService.start(context)
                        Toast.makeText(context, "正在启动全局负一屏", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        item {
            PermissionsHeaderCard(
                canDrawOverlays = canDrawOverlays,
                isAccessibilityEnabled = isAccessibilityEnabled,
                isAccessibilityEnabledInSettings = isAccessibilityEnabledInSettings,
                isStorageGranted = isStorageGranted,
                canWriteSettings = canWriteSettings,
                isNotificationPolicyGranted = isNotificationPolicyGranted,
                isUsageAccessGranted = isUsageAccessGranted,
                isBatteryOptIgnored = isBatteryOptIgnored,
                permissionsExpanded = permissionsExpanded,
                onToggleExpand = { permissionsExpanded = !permissionsExpanded }
            )
        }

        // 按需展开权限状态列表，所有入口保持原系统导航。
        item {
            AnimatedVisibility(
                visible = permissionsExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                PermissionsChecklistContent(
                    canDrawOverlays = canDrawOverlays,
                    isAccessibilityEnabled = isAccessibilityEnabled,
                    isAccessibilityEnabledInSettings = isAccessibilityEnabledInSettings,
                    isStorageGranted = isStorageGranted,
                    canWriteSettings = canWriteSettings,
                    isNotificationPolicyGranted = isNotificationPolicyGranted,
                    isUsageAccessGranted = isUsageAccessGranted,
                    isBatteryOptIgnored = isBatteryOptIgnored,
                    onCollapse = { permissionsExpanded = false }
                )
            }
        }

        item { PreferenceCategoryTitle(title = "日常设置") }
        item {
            MainMenuGroupCard {
                MainMenuItemRow(
                    icon = Icons.TouchApp,
                    title = "触发设置",
                    subtitle = "触发位置、区域、悬浮球等",
                    onClick = { onNavigate("OPERATION") }
                )
                MainMenuGroupDivider()
                MainMenuItemRow(
                    icon = Icons.Palette,
                    title = "主题外观",
                    subtitle = "设置页深浅色、面板、卡片与动画",
                    onClick = { onNavigate("THEME") }
                )
            }
        }

        item { PreferenceCategoryTitle(title = "更多功能") }
        item {
            MainMenuGroupCard {
                MainMenuItemRow(
                    icon = IconAndroid,
                    title = "高级设置",
                    subtitle = "Root模式、黑名单、启动行为等",
                    onClick = { onNavigate("ADVANCE") }
                )
                MainMenuGroupDivider()
                MainMenuItemRow(
                    icon = IconPuzzle,
                    title = "扩展功能",
                    subtitle = "验证码、护眼滤色与情景规则",
                    onClick = { onNavigate("EXTEND") }
                )
                MainMenuGroupDivider()
                MainMenuItemRow(
                    icon = Icons.Rounded.Translate,
                    title = "全局识屏与翻译",
                    subtitle = "屏幕选字、原位翻译、提取小图、按需模型与小布联动",
                    onClick = { onNavigate("SCREEN_ASSISTANT") }
                )
            }
        }

        item { PreferenceCategoryTitle(title = "配置与帮助") }
        item {
            MainMenuGroupCard {
                MainMenuItemRow(
                    icon = IconFloppy,
                    title = "配置管理",
                    subtitle = "备份、导入、删除配置文件等",
                    onClick = { onNavigate("CONFIG") }
                )
                MainMenuGroupDivider()
                MainMenuItemRow(
                    icon = IconHelp,
                    title = "使用教程",
                    subtitle = "一些常见的使用问题",
                    onClick = { showTutorialDialog = true }
                )
                MainMenuGroupDivider()
                MainMenuItemRow(
                    icon = IconInfo,
                    title = "关于",
                    subtitle = "项目来源、AI 开发说明、开发者与开源仓库",
                    onClick = { onNavigate("ABOUT") }
                )
            }
        }
        }
    }

    if (showTutorialDialog) {
        TutorialDialog(onDismiss = { showTutorialDialog = false })
    }
}



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




data class AppItemInfo(val packageName: String, val label: String)











