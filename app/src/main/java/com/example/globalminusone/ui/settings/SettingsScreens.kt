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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.globalminusone.ui.AppIcons as Icons
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
    val startScreen = navigationRequest?.screen ?: initialScreen ?: "MAIN"
    var currentScreen by rememberSaveable { mutableStateOf(startScreen) }
    var lastConsumedNonce by rememberSaveable { mutableLongStateOf(navigationRequest?.nonce ?: 0L) }
    var forceExpandOcrToken by rememberSaveable { mutableLongStateOf(if (startScreen == "SCREEN_ASSISTANT") 1L else 0L) }
    var pendingTargetGroup by rememberSaveable { mutableStateOf("") }
    var pendingJumpNonce by rememberSaveable { mutableLongStateOf(0L) }
    LaunchedEffect(navigationRequest) {
        if (SettingsNavigationPolicy.shouldApply(navigationRequest, lastConsumedNonce)) {
            val request = navigationRequest!!
            currentScreen = request.screen
            pendingTargetGroup = if (request.screen == "SCREEN_ASSISTANT") "OCR" else ""
            pendingJumpNonce += 1L
            lastConsumedNonce = request.nonce
            if (request.screen == "SCREEN_ASSISTANT") {
                forceExpandOcrToken += 1L
            }
        }
    }
    LaunchedEffect(initialScreen) {
        if (navigationRequest == null && !initialScreen.isNullOrBlank()) {
            currentScreen = initialScreen
            if (initialScreen == "SCREEN_ASSISTANT") {
                pendingTargetGroup = "OCR"
                pendingJumpNonce += 1L
                forceExpandOcrToken += 1L
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
    ) {
        SettingsScreenTransition(
            targetScreen = currentScreen,
            animMs = state.themeConfig.animationDurationMs.coerceIn(0, 800)
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

        item {
            MainMenuItemRow(
                icon = Icons.Rounded.Translate,
                title = "全局识屏与翻译",
                subtitle = "屏幕选字、原位翻译、提取小图、按需模型与小布联动",
                onClick = { onNavigate("SCREEN_ASSISTANT") }
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
                subtitle = "项目来源、AI 开发说明、开发者与开源仓库",
                onClick = { onNavigate("ABOUT") }
            )
        }
        }
    }

    if (showTutorialDialog) {
        TutorialDialog(onDismiss = { showTutorialDialog = false })
    }
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




data class AppItemInfo(val packageName: String, val label: String)












