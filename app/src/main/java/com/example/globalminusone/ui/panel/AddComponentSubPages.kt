package com.example.globalminusone.ui.panel

import android.graphics.drawable.BitmapDrawable
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.globalminusone.ActionPickerKind
import com.example.globalminusone.data.AppLaunchMode
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.DefaultPresets
import com.example.globalminusone.data.SHELL_COMMAND_TEMPLATES
import com.example.globalminusone.data.ShellCommandSpec
import com.example.globalminusone.data.ShellExecutionMode
import com.example.globalminusone.system.AppAndIntentLauncher
import com.example.globalminusone.system.DeviceShortcutsSnapshot
import com.example.globalminusone.system.InstalledAppInfo
import com.example.globalminusone.system.ShortcutDiscoveryPolicy
import com.example.globalminusone.ui.AppIcons as Icons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AddComponentShellSubPage(
    accent: Color,
    bodyText: Color,
    secondaryText: Color,
    onAdd: (ComponentItem, String) -> Unit
) {
    var shellTitle by remember { mutableStateOf("Shell 命令") }
    var shellCommand by remember { mutableStateOf("") }
    var shellMode by remember { mutableStateOf(ShellExecutionMode.NORMAL) }
    var shellRequireConfirm by remember { mutableStateOf(false) }
    var shellCompactTile by remember { mutableStateOf(false) }
    var shellTimeoutSec by remember { mutableIntStateOf(10) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val highRisk = ShellCommandSpec.isHighRiskCommand(shellCommand)
        Text("快捷模板（点选填充）", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SHELL_COMMAND_TEMPLATES.forEach { tpl ->
                FilterChip(
                    selected = shellCommand.trim() == tpl.command.trim() && shellMode == tpl.mode,
                    onClick = {
                        shellTitle = tpl.title
                        shellCommand = tpl.command
                        shellMode = tpl.mode
                        shellRequireConfirm = tpl.requireConfirm
                        shellCompactTile = tpl.preferCompactTile
                    },
                    label = { Text(tpl.title, fontSize = 11.sp) }
                )
            }
        }

        Text("执行权限模式", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ShellExecutionMode.entries.forEach { m ->
                FilterChip(
                    selected = shellMode == m,
                    onClick = { shellMode = m },
                    label = { Text(m.badge, fontSize = 11.sp) }
                )
            }
        }
        Text(
            text = when (shellMode) {
                ShellExecutionMode.NORMAL -> "普通 UID (/system/bin/sh)：无需 Root，安全读写标准命令与网络检查。"
                ShellExecutionMode.SHIZUKU -> "Shizuku (ADB Shell UID)：可查看系统状态、模拟按键与电源/窗口控制，仅用户点击时请求授权。"
                ShellExecutionMode.ROOT -> "Root (su uid 0)：最高权限，仅绑定当前会话授权，绝不在配置或后台静默执行。"
            },
            color = secondaryText,
            fontSize = 11.sp
        )

        OutlinedTextField(
            value = shellTitle,
            onValueChange = { shellTitle = it.take(100) },
            label = { Text("卡片名称") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = shellCommand,
            onValueChange = { shellCommand = it.take(4096) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Shell 命令（/system/bin/sh -c）") },
            minLines = 3
        )

        if (highRisk) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFD32F2F).copy(alpha = 0.14f)
            ) {
                Text(
                    text = "检测到潜在高危关键字，执行前将强制弹出二次确认窗。",
                    color = Color(0xFFD32F2F),
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("紧凑图标样式 (1×1)", color = bodyText, fontSize = 12.sp)
            Switch(checked = shellCompactTile, onCheckedChange = { shellCompactTile = it })
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("每次点击前弹出确认", color = bodyText, fontSize = 12.sp)
            Switch(checked = shellRequireConfirm || highRisk, enabled = !highRisk, onCheckedChange = { shellRequireConfirm = it })
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("超时上限:", color = secondaryText, fontSize = 11.sp)
            listOf(5, 10, 20, 45).forEach { sec ->
                FilterChip(
                    selected = shellTimeoutSec == sec,
                    onClick = { shellTimeoutSec = sec },
                    label = { Text("${sec}s", fontSize = 11.sp) }
                )
            }
        }

        FilledTonalButton(
            enabled = shellTitle.isNotBlank() && shellCommand.isNotBlank(),
            onClick = {
                val spec = ShellCommandSpec(
                    command = shellCommand.trim(),
                    mode = shellMode,
                    requireConfirm = shellRequireConfirm || highRisk,
                    timeoutSec = shellTimeoutSec,
                    showOutputDialog = true,
                    preferCompactTile = shellCompactTile
                )
                onAdd(
                    ComponentItem(
                        type = ComponentType.SHELL_COMMAND,
                        title = shellTitle.trim(),
                        subtitle = shellMode.label,
                        iconName = "bolt",
                        textContent = spec.command,
                        intentUri = spec.encodeToIntentUri(),
                        w = if (shellCompactTile) 8 else 32,
                        h = if (shellCompactTile) 7 else 9
                    ),
                    "已添加 Shell 命令卡「${shellTitle.trim()}」"
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存命令卡片")
        }
    }
}

@Composable
internal fun AddComponentAppSubPage(
    installedApps: List<InstalledAppInfo>,
    accent: Color,
    primaryText: Color,
    secondaryText: Color,
    itemBg: Color,
    itemBorderColor: Color,
    onAdd: (ComponentItem, String) -> Unit
) {
    val context = LocalContext.current
    val addScope = rememberCoroutineScope()
    var applicationMode by rememberSaveable { mutableStateOf(AppLaunchMode.NORMAL) }
    var checkingApplication by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = applicationMode == AppLaunchMode.NORMAL,
                onClick = { applicationMode = AppLaunchMode.NORMAL },
                enabled = checkingApplication == null,
                label = { Text("普通打开") }
            )
            FilterChip(
                selected = applicationMode == AppLaunchMode.FLOATING,
                onClick = { applicationMode = AppLaunchMode.FLOATING },
                enabled = checkingApplication == null,
                label = { Text("小窗打开") }
            )
        }
        if (checkingApplication != null) Text("正在检查小窗支持…", fontSize = 12.sp, color = secondaryText)
        IndexedSelectionList(
            entries = installedApps,
            key = { it.packageName },
            label = { it.label },
            searchText = { "${it.label} ${it.packageName}" },
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) { app ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = checkingApplication == null) {
                        val mode = applicationMode
                        if (mode == AppLaunchMode.NORMAL) {
                            onAdd(applicationCard(app.packageName, app.label, mode), "已添加应用「${app.label}」")
                        } else {
                            checkingApplication = app.packageName
                            addScope.launch {
                                try {
                                    if (resolveFloatingApplication(context, app.packageName, app.label) == null) {
                                        Toast.makeText(context, "此应用当前不支持独立小窗，未添加普通打开入口", Toast.LENGTH_LONG).show()
                                    } else {
                                        onAdd(applicationCard(app.packageName, app.label, mode), "已添加小窗入口「${app.label}」")
                                    }
                                } finally { checkingApplication = null }
                            }
                        }
                    },
                color = itemBg,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CachedAppRowIcon(
                        packageName = app.packageName,
                        label = app.label,
                        accent = accent,
                        borderColor = itemBorderColor
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = app.label,
                            color = primaryText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = app.packageName,
                            color = secondaryText,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AddComponentShortcutSubPage(
    shortcutSnapshot: DeviceShortcutsSnapshot?,
    loadingShortcuts: Boolean,
    accent: Color,
    primaryText: Color,
    secondaryText: Color,
    itemBg: Color,
    itemBorderColor: Color,
    onSelectAction: (ActionPickerKind, String?) -> Unit,
    onOpenShell: () -> Unit,
    onAdd: (ComponentItem, String) -> Unit
) {
    var shortcutQuery by remember { mutableStateOf("") }
    val snap = shortcutSnapshot
    val q = shortcutQuery.trim()
    val filteredProviders = remember(snap, q) {
        (snap?.createProviders ?: emptyList()).filter {
            q.isBlank() || it.appLabel.contains(q, true) || it.shortcutLabel.contains(q, true) || it.packageName.contains(q, true)
        }
    }
    val filteredHostShortcuts = remember(snap, q) {
        (snap?.hostPinnedShortcuts ?: emptyList()).filter {
            q.isBlank() || it.title.contains(q, true) || it.subtitle.contains(q, true) || it.packageName.contains(q, true)
        }
    }
    val installedPresets = remember(snap, q) {
        val base = if (snap != null) {
            ShortcutDiscoveryPolicy.filterBuiltInPresets(
                DefaultPresets.builtInShortcuts,
                snap.installedPackageNames,
                includeUninstalled = false
            ).first
        } else {
            DefaultPresets.builtInShortcuts
        }
        base.filter {
            q.isBlank() || it.title.contains(q, true) || it.subtitle.contains(q, true)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = shortcutQuery,
            onValueChange = { shortcutQuery = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("搜索本机快捷入口、应用或工具动作", fontSize = 12.sp) }
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item(key = "create_shortcut_picker") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelectAction(ActionPickerKind.CREATE_SHORTCUT, null) },
                    color = accent.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "浏览本机应用快捷方式生成器",
                                color = primaryText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (snap == null) "正在扫描本机 ACTION_CREATE_SHORTCUT 提供方…"
                                else "已发现 ${snap.createProviders.size} 个真实生成器 · 支持微信/支付宝/系统设置页等",
                                color = secondaryText,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            if (filteredHostShortcuts.isNotEmpty()) {
                item(key = "hdr_host_shortcuts") {
                    Text(
                        text = "本机动态/固定快捷方式 (${filteredHostShortcuts.size})",
                        color = accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(filteredHostShortcuts, key = { it.id }) { sc ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onAdd(
                                    ComponentItem(
                                        type = ComponentType.CUSTOM_SHORTCUT,
                                        title = sc.title,
                                        subtitle = sc.subtitle,
                                        iconName = sc.iconName,
                                        intentUri = sc.intentUri,
                                        packageName = sc.packageName
                                    ),
                                    "已添加本机快捷方式「${sc.title}」"
                                )
                            },
                        color = itemBg,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CachedAppRowIcon(
                                packageName = sc.packageName,
                                label = sc.title,
                                accent = accent,
                                borderColor = itemBorderColor
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(text = sc.title, color = primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(text = sc.subtitle, color = secondaryText, fontSize = 10.sp)
                            }
                        }
                    }
                }
            } else if (snap != null && !snap.hasShortcutHostPermission) {
                item(key = "host_hint") {
                    Text(
                        text = "系统限制：仅默认桌面可直接读取 App 桌面二级长按菜单，上方生成器与下方预设均真实可用。",
                        color = secondaryText,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }

            if (filteredProviders.isNotEmpty()) {
                item(key = "hdr_create_providers") {
                    Text(
                        text = "本机应用快捷生成器 (${filteredProviders.size})",
                        color = accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(filteredProviders, key = { "prov_${it.packageName}/${it.activityName}" }) { provider ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onSelectAction(
                                    ActionPickerKind.CREATE_SHORTCUT,
                                    "${provider.packageName}/${provider.activityName}"
                                )
                            },
                        color = itemBg,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CachedAppRowIcon(
                                packageName = provider.packageName,
                                label = provider.appLabel,
                                accent = accent,
                                borderColor = itemBorderColor
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "${provider.appLabel} · ${provider.shortcutLabel}",
                                    color = primaryText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = provider.packageName,
                                    color = secondaryText,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            } else if (loadingShortcuts) {
                item(key = "loading_sc") {
                    Text(
                        text = "正在扫描本机可添加快捷方式…",
                        color = secondaryText,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            item(key = "hdr_shell_shortcut_entry") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenShell() },
                    color = accent.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Bolt,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("新建 Shell 命令卡", color = primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("支持普通 UID / Shizuku / Root 脚本、模板与回显弹窗", color = secondaryText, fontSize = 10.sp)
                        }
                    }
                }
            }

            item(key = "hdr_installed_presets") {
                Text(
                    text = "内置工具与已安装预设动作 (${installedPresets.size})",
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(installedPresets, key = { it.id }) { sc ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onAdd(
                                ComponentItem(
                                    type = ComponentType.CUSTOM_SHORTCUT,
                                    title = sc.title,
                                    subtitle = sc.subtitle,
                                    iconName = sc.iconName,
                                    intentUri = sc.intentUri,
                                    packageName = sc.packageName
                                ),
                                "已添加快捷方式「${sc.title}」"
                            )
                        },
                    color = itemBg,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2196F3).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = getQuickActionIcon(sc.iconName),
                                contentDescription = null,
                                tint = Color(0xFF2196F3),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(text = sc.title, color = primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(text = sc.subtitle, color = secondaryText, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CachedAppRowIcon(
    packageName: String,
    label: String,
    accent: Color,
    borderColor: Color
) {
    val context = LocalContext.current
    val initialBitmap = remember(packageName) {
        AppAndIntentLauncher.peekAppIconBitmap(packageName)
    }
    val loadedBitmap by produceState<ImageBitmap?>(initialValue = initialBitmap, packageName) {
        if (value == null) {
            val info = AppAndIntentLauncher.getAppInfoCached(context, packageName, notify = false)
            value = AppAndIntentLauncher.peekAppIconBitmap(packageName)
                ?: info?.icon?.let { drawable ->
                    withContext(Dispatchers.Default) {
                        (drawable as? BitmapDrawable)?.bitmap?.asImageBitmap()
                            ?: runCatching { drawable.toBitmap().asImageBitmap() }.getOrNull()
                    }
                }
        }
    }
    val bmp = loadedBitmap
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = label,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(accent.copy(alpha = 0.14f))
                .border(1.dp, borderColor, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label.trim().take(1).uppercase().ifEmpty { "?" },
                color = accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
