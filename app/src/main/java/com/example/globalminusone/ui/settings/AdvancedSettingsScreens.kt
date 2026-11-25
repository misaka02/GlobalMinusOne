package com.example.globalminusone.ui.settings

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.globalminusone.ui.AppIcons as Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import kotlinx.coroutines.launch

@Composable
internal fun AdvanceSettingsScreen(
    repository: MinusOneRepository,
    initialExpandedGroup: String = "",
    jumpNonce: Long = 0L,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val state by repository.state.collectAsState()
    val advancedConfig = state.advancedConfig
    val authorizationScope = androidx.compose.runtime.rememberCoroutineScope()

    var showIconBehaviorDialog by remember { mutableStateOf(false) }
    var showBlacklistDialog by remember { mutableStateOf(false) }

    var expandedGroup by rememberSaveable { mutableStateOf(initialExpandedGroup) }
    val listState = rememberLazyListState()
    LaunchedEffect(initialExpandedGroup, jumpNonce) {
        if (initialExpandedGroup.isNotBlank()) {
            expandedGroup = initialExpandedGroup
            val idx = when (initialExpandedGroup) {
                "负一屏" -> 1
                "启动与自动收起" -> 2
                "显示层级与网格" -> 3
                "Root" -> 4
                else -> null
            }
            if (idx != null) listState.animateScrollToItem(idx)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SubPageTopBar(title = "高级设置", onBack = onBack)

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 36.dp)
        ) {
            item { SettingsHint("选择一个分类展开设置，其他分类会自动收起。") }

            settingsGroup(
                title = "布局保护", summary = if (advancedConfig.layoutLocked) "布局已锁定 · 查看编辑保护" else "布局未锁定 · 查看编辑保护",
                expanded = expandedGroup == "负一屏",
                onToggle = { expandedGroup = if (expandedGroup == "负一屏") "" else "负一屏" }
            ) {

                item {
                    PreferenceSwitchItem(
                        title = "锁定布局",
                        summary = "锁定后禁止增删、拖动与修改组件样式；便签输入、翻页等日常操作仍可使用。导入、切换方案或恢复默认前需先解锁。",
                        checked = advancedConfig.layoutLocked,
                        onCheckedChange = repository::setLayoutLocked
                    )
                }



            }

            settingsGroup(
                title = "启动与自动收起", summary = "图标行为、横屏、黑名单与小部件",
                expanded = expandedGroup == "启动与自动收起",
                onToggle = { expandedGroup = if (expandedGroup == "启动与自动收起") "" else "启动与自动收起" }
            ) {

                item {
                    PreferenceSwitchItem(
                        title = "自动跳转辅助服务",
                        summary = "需要辅助服务而尚未开启时，自动打开系统无障碍设置；关闭后仅提示",
                        checked = advancedConfig.autoJumpAccessibility,
                        onCheckedChange = { enabled ->
                            repository.updateAdvancedConfig { it.copy(autoJumpAccessibility = enabled) }
                        }
                    )
                }

                item {
                    PreferenceItem(
                        title = "点击应用图标行为",
                        summary = "${advancedConfig.iconClickBehavior.label}\n需点击返回键退出应用后生效",
                        onClick = { showIconBehaviorDialog = true }
                    )
                }

                item {
                    PreferenceSwitchItem(
                        title = "横屏自动关闭",
                        checked = advancedConfig.autoCloseInLandscape,
                        onCheckedChange = { checked ->
                            repository.updateAdvancedConfig { it.copy(autoCloseInLandscape = checked) }
                        }
                    )
                }

                item {
                    PreferenceItem(
                        title = "黑名单",
                        summary = "在进入黑名单中的应用时自动关闭负一屏 (已添加 ${advancedConfig.blacklistPackages.size} 项)",
                        onClick = { showBlacklistDialog = true }
                    )
                }

                item {
                    PreferenceSwitchItem(
                        title = "点击小部件自动收起",
                        summary = "开启后每次点击小部件都会抢占下层焦点(视频、游戏等暂停)",
                        checked = advancedConfig.autoCloseOnWidgetClick,
                        onCheckedChange = { checked ->
                            repository.updateAdvancedConfig { it.copy(autoCloseOnWidgetClick = checked) }
                        }
                    )
                }


            }

            settingsGroup(
                title = "显示层级与网格", summary = "悬浮窗覆盖层级与布局等分",
                expanded = expandedGroup == "显示层级与网格",
                onToggle = { expandedGroup = if (expandedGroup == "显示层级与网格") "" else "显示层级与网格" }
            ) {

                item {
                    PreferenceSwitchItem(
                        title = "无障碍悬浮层级",
                        summary = "辅助服务运行时尝试将面板置于无障碍悬浮层；受系统窗口保护限制，失败自动回到普通悬浮层",
                        checked = advancedConfig.highestOverlayLevel,
                        onCheckedChange = { enabled ->
                            repository.updateAdvancedConfig { it.copy(highestOverlayLevel = enabled) }
                        }
                    )
                }

                item {
                    PreferenceSliderItem(
                        title = "自由布局宽度等分",
                        value = advancedConfig.gridColumns.toFloat(),
                        valueRange = 8f..64f,
                        valueText = "${advancedConfig.gridColumns}",
                        onValueChange = { cols ->
                            repository.updateAdvancedConfig { it.copy(gridColumns = cols.toInt()) }
                        }
                    )
                }
            }

            settingsGroup(
                title = "Root", summary = "已获取 Root 的设备可按需开启",
                expanded = expandedGroup == "Root",
                onToggle = { expandedGroup = if (expandedGroup == "Root") "" else "Root" }
            ) {

                item {
                    PreferenceSwitchItem(
                        title = "Root模式",
                        summary = "开启时请求并预热 Root 授权；仅在授权成功后保存偏好。具体 Root 文件和操作仍需各自确认，关闭不会撤销既有会话",
                        checked = advancedConfig.rootModeEnabled,
                        onCheckedChange = { enabled ->
                            if (!enabled) {
                                repository.updateAdvancedConfig { it.copy(rootModeEnabled = false) }
                            } else {
                                authorizationScope.launch {
                                    val granted = try {
                                        com.example.globalminusone.system.UserRootSession.requestAuthorization().succeeded
                                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        false
                                    }
                                    if (granted) repository.updateAdvancedConfig { it.copy(rootModeEnabled = true) }
                                    Toast.makeText(context,
                                        if (granted) "Root授权已就绪" else "Root授权未成功，设置未开启",
                                        Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }

            }


        }
    }

    if (showIconBehaviorDialog) {
        val options = IconClickBehavior.values()
        SingleChoiceDialog(
            title = "点击应用图标行为",
            options = options.map { it.label },
            selectedIndex = options.indexOf(advancedConfig.iconClickBehavior),
            onSelect = { idx ->
                repository.updateAdvancedConfig { it.copy(iconClickBehavior = options[idx]) }
                showIconBehaviorDialog = false
            },
            onDismiss = { showIconBehaviorDialog = false }
        )
    }

    if (showBlacklistDialog) {
        AppPickerDialog(
            title = "选择黑名单应用",
            initialSelected = advancedConfig.blacklistPackages,
            onConfirm = { selected ->
                repository.updateAdvancedConfig { it.copy(blacklistPackages = selected) }
                showBlacklistDialog = false
            },
            onDismiss = { showBlacklistDialog = false }
        )
    }
}

@Composable
internal fun ExtendSettingsScreen(
    repository: MinusOneRepository,
    initialExpandedGroup: String = "",
    jumpNonce: Long = 0L,
    onBack: () -> Unit
) {
    val state by repository.state.collectAsState()
    val advancedConfig = state.advancedConfig

    var showScenarioDialog by remember { mutableStateOf(false) }
    var showEyeColorDialog by remember { mutableStateOf(false) }

    var expandedGroup by rememberSaveable { mutableStateOf(initialExpandedGroup) }
    val listState = rememberLazyListState()
    LaunchedEffect(initialExpandedGroup, jumpNonce) {
        if (initialExpandedGroup.isNotBlank()) {
            expandedGroup = initialExpandedGroup
            val idx = when (initialExpandedGroup) {
                "通知" -> 1
                "其它" -> 2
                "护眼与滤色" -> 3
                else -> null
            }
            if (idx != null) listState.animateScrollToItem(idx)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SubPageTopBar(title = "扩展功能", onBack = onBack)

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 36.dp)
        ) {
            item { SettingsHint("选择一个分类展开设置，其他分类会自动收起。") }

            settingsGroup(
                title = "通知提醒", summary = "轻提示、震动、白名单与通知跳转",
                expanded = expandedGroup == "通知",
                onToggle = { expandedGroup = if (expandedGroup == "通知") "" else "通知" }
            ) {
                item { NotificationPreferences(repository) }
            }

            settingsGroup(
                title = "验证码与情景规则", summary = "短信验证码复制与按应用调节亮度、音量",
                expanded = expandedGroup == "其它",
                onToggle = { expandedGroup = if (expandedGroup == "其它") "" else "其它" }
            ) {

                item {
                    PreferenceSwitchItem(
                        title = "验证码自动复制",
                        summary = "接收到短信验证码时自动复制到剪贴板",
                        checked = advancedConfig.autoCopyVerificationCode,
                        onCheckedChange = { checked ->
                            repository.updateAdvancedConfig { it.copy(autoCopyVerificationCode = checked) }
                        }
                    )
                }

                item {
                    PreferenceItem(
                        title = "应用情景模式(实验性)",
                        summary = "新增、查看或删除应用规则（${advancedConfig.scenarioModes.size} 项），设置进入应用时的亮度与媒体音量",
                        onClick = { showScenarioDialog = true }
                    )
                }


            }

            settingsGroup(
                title = "护眼与滤色", summary = "全局护眼开关与暖色滤镜",
                expanded = expandedGroup == "护眼与滤色",
                onToggle = { expandedGroup = if (expandedGroup == "护眼与滤色") "" else "护眼与滤色" }
            ) {

                item {
                    PreferenceSwitchItem(
                        title = "全局护眼",
                        summary = "开启屏幕暖色护眼遮罩滤镜",
                        checked = advancedConfig.eyeProtectionEnabled,
                        onCheckedChange = { checked ->
                            repository.updateAdvancedConfig { it.copy(eyeProtectionEnabled = checked) }
                        }
                    )
                }

                item {
                    PreferenceItem(
                        title = "护眼色",
                        summary = "点击调整暖黄/琥珀护眼滤色",
                        onClick = { showEyeColorDialog = true }
                    )
                }
            }


        }
    }

    if (showScenarioDialog) {
        ScenarioModeManageDialog(
            repository = repository,
            onDismiss = { showScenarioDialog = false }
        )
    }

    if (showEyeColorDialog) {
        EyeProtectionColorDialog(
            currentColor = advancedConfig.eyeProtectionColorHex,
            onSelect = { color ->
                repository.updateAdvancedConfig { it.copy(eyeProtectionColorHex = color, eyeProtectionEnabled = true) }
                showEyeColorDialog = false
            },
            onDismiss = { showEyeColorDialog = false }
        )
    }
}

@Composable
internal fun AppPickerDialog(
    title: String,
    initialSelected: Set<String>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedSet by remember { mutableStateOf(initialSelected) }
    var searchQuery by remember { mutableStateOf("") }
    var appList by remember { mutableStateOf<List<AppItemInfo>>(emptyList()) }
    var loadingApps by remember { mutableStateOf(true) }

    LaunchedEffect(context) {
        loadingApps = true
        appList = com.example.globalminusone.system.AppAndIntentLauncher.getInstalledApps(context)
            .map { AppItemInfo(packageName = it.packageName, label = it.label) }
            .distinctBy { it.packageName }
            .sortedBy { it.label }
        loadingApps = false
    }

    val filtered = remember(searchQuery, appList) {
        if (searchQuery.isBlank()) appList
        else appList.filter {
            it.label.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            com.example.globalminusone.ui.Android12WindowKeyGuard()
            Text(text = title, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.height(360.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("搜索应用名称或包名...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Rounded.Close, contentDescription = "清空搜索", modifier = Modifier.size(16.dp))
                            }
                        }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (loadingApps) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("正在加载应用列表…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (filtered.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("未找到匹配应用", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filtered, key = { it.packageName }) { item ->
                        val isChecked = item.packageName in selectedSet
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedSet = if (isChecked) selectedSet - item.packageName
                                    else selectedSet + item.packageName
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { chk ->
                                    selectedSet = if (chk) selectedSet + item.packageName
                                    else selectedSet - item.packageName
                                }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(text = item.label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text(text = item.packageName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedSet) }) {
                Text("保存 (${selectedSet.size})", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
internal fun ScenarioModeManageDialog(
    repository: MinusOneRepository,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val state by repository.state.collectAsState()
    val list = state.advancedConfig.scenarioModes
    var addingRule by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf<List<com.example.globalminusone.system.InstalledAppInfo>>(emptyList()) }
    var loadingApps by remember { mutableStateOf(false) }
    var appQuery by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf<com.example.globalminusone.system.InstalledAppInfo?>(null) }
    var targetVolume by remember { mutableFloatStateOf(50f) }
    var targetBrightness by remember { mutableFloatStateOf(60f) }

    LaunchedEffect(addingRule) {
        if (addingRule && apps.isEmpty()) {
            loadingApps = true
            apps = com.example.globalminusone.system.AppAndIntentLauncher.getInstalledApps(context)
            loadingApps = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            com.example.globalminusone.ui.Android12WindowKeyGuard()
            Text(if (addingRule) "新增情景规则" else "应用情景模式", fontWeight = FontWeight.Bold) },
        text = {
            if (addingRule) {
                val filteredApps = remember(appQuery, apps) {
                    val q = appQuery.trim()
                    if (q.isEmpty()) apps
                    else apps.filter { it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
                }
                Column(modifier = Modifier.height(360.dp)) {
                    Text(
                        text = if (selectedApp != null) "已选应用：${selectedApp!!.label}" else "请先点选下方应用：",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("媒体音量: ${targetVolume.toInt()}%", fontSize = 12.sp)
                    Slider(value = targetVolume, onValueChange = { targetVolume = it }, valueRange = 0f..100f)
                    Text("屏幕亮度: ${targetBrightness.toInt()}%", fontSize = 12.sp)
                    Slider(value = targetBrightness, onValueChange = { targetBrightness = it }, valueRange = 0f..100f)
                    OutlinedTextField(
                        value = appQuery,
                        onValueChange = { appQuery = it },
                        placeholder = { Text("搜索应用名称或包名…") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = if (appQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { appQuery = "" }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Rounded.Close, contentDescription = "清空搜索", modifier = Modifier.size(16.dp))
                                }
                            }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    if (loadingApps) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("正在加载应用列表…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            val isSelected = selectedApp?.packageName == app.packageName
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedApp = app }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = isSelected, onClick = { selectedApp = app })
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(app.label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text(app.packageName, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    }
                }
            } else {
                Column(modifier = Modifier.height(260.dp)) {
                    Text(
                        text = "进入指定应用时，自动将系统亮度与媒体音量调节至预设值。\n退出应用后自动恢复。",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    if (list.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("暂未配置任何情景应用，点击下方“新增规则”添加", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(list) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(item.appLabel, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                        Text("音量:${item.volumePercent}% 亮度:${item.brightnessPercent}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(
                                        onClick = {
                                            repository.updateAdvancedConfig { cfg ->
                                                cfg.copy(scenarioModes = cfg.scenarioModes.filterNot { it.packageName == item.packageName })
                                            }
                                        }
                                    ) {
                                        Icon(Icons.DeleteOutline, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            if (addingRule) {
                TextButton(onClick = { addingRule = false }) {
                    Text("返回列表", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                TextButton(onClick = { addingRule = true }) {
                    Text("新增规则", color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            if (addingRule) {
                TextButton(
                    enabled = selectedApp != null,
                    onClick = {
                        val app = selectedApp ?: return@TextButton
                        val newRule = com.example.globalminusone.data.ScenarioModeItem(
                            packageName = app.packageName,
                            appLabel = app.label,
                            volumePercent = targetVolume.toInt().coerceIn(0, 100),
                            brightnessPercent = targetBrightness.toInt().coerceIn(0, 100),
                            enabled = true
                        )
                        repository.updateAdvancedConfig { cfg ->
                            val filtered = cfg.scenarioModes.filterNot { it.packageName == app.packageName }
                            cfg.copy(scenarioModes = filtered + newRule)
                        }
                        addingRule = false
                        selectedApp = null
                    }
                ) {
                    Text("保存规则", color = MaterialTheme.colorScheme.primary)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("完成", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    )
}

@Composable
internal fun EyeProtectionColorDialog(
    currentColor: Long,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val presetColors = listOf(
        Pair("经典暖黄", 0x26FFB100L),
        Pair("琥珀蜜色", 0x26FFA000L),
        Pair("晚霞暖橙", 0x26FF8000L),
        Pair("护眼淡绿", 0x2680FF80L),
        Pair("深色浓黄", 0x33FFB100L)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            com.example.globalminusone.ui.Android12WindowKeyGuard()
            Text("选择全局护眼色", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn {
                items(presetColors) { (name, hex) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(hex) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(hex or 0xFF000000L))
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = name,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (currentColor == hex) {
                            Icon(Icons.Check, contentDescription = "已选", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
