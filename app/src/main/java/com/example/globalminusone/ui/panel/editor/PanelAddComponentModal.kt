package com.example.globalminusone.ui.panel.editor

import com.example.globalminusone.ActionPickerKind
import com.example.globalminusone.ActionPickerRequest
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.BorderStroke
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import com.example.globalminusone.ui.AppIcons as Icons
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.policy.AppQuickSearchMatcher
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentOrientation
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.ContainerLayoutType
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.system.AppAndIntentLauncher
import com.example.globalminusone.system.DeviceShortcutDiscovery
import com.example.globalminusone.system.DeviceShortcutsSnapshot
import com.example.globalminusone.system.InstalledAppInfo
import com.example.globalminusone.system.file.PanelFileBrowser
import com.example.globalminusone.ui.PlatformPinyinProvider
import com.example.globalminusone.ui.motion.overlayScrim
import com.example.globalminusone.ui.motion.overlaySheetMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.globalminusone.ui.panel.container.LocalFolderReturnState
import com.example.globalminusone.ui.panel.LocalPanelPalette

data class AddCategoryItem(
    val id: Int,
    val name: String,
    val icon: ImageVector,
    val desc: String = "",
    val group: String = "常用直达"
)

@Composable
fun AddComponentModal(
    currentPageIndex: Int,
    repository: MinusOneRepository,
    onRequestBindWidget: (pageId: String) -> Unit,
    onDismiss: () -> Unit,
    targetContainerId: String? = null,
    beforeComponentId: String? = null,
    targetGridCell: Pair<Int, Int>? = null
) {
    val context = LocalContext.current
    val folderReturn = LocalFolderReturnState.current
    // Keep the destination for this modal's lifetime even if the pager moves or pages change.
    val targetPageId = remember(repository) { repository.state.value.pages.getOrNull(currentPageIndex)?.id }
    var activeSubPage by remember { mutableStateOf<String?>(null) }
    var categoryQuery by remember { mutableStateOf("") }
    var shortcutSnapshot by remember { mutableStateOf<DeviceShortcutsSnapshot?>(null) }
    var loadingShortcuts by remember { mutableStateOf(false) }
    var selectedGroup by rememberSaveable { mutableStateOf("全部") }
    var installedApps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    LaunchedEffect(activeSubPage) {
        if (activeSubPage == "SHORTCUT" && shortcutSnapshot == null) {
            loadingShortcuts = true
            shortcutSnapshot = withContext(Dispatchers.IO) { DeviceShortcutDiscovery.scan(context) }
            loadingShortcuts = false
        }
    }
    var websiteTitle by remember { mutableStateOf("") }
    var websiteUrl by remember { mutableStateOf("https://") }

    LaunchedEffect(activeSubPage) {
        if (activeSubPage == "APP" && installedApps.isEmpty()) {
            installedApps = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                AppAndIntentLauncher.getInstalledApps(context)
            }
        }
    }

    val categories = remember {
        listOf(
            // 常用直达
            AddCategoryItem(1, "应用", Icons.Rounded.Apps, "已安装的应用与小窗入口", "常用直达"),
            AddCategoryItem(23, "文件夹", Icons.Rounded.Folder, "缩略图标预览，点击展开子卡片", "常用直达"),
            AddCategoryItem(3, "快捷方式", Icons.Rounded.FlashlightOn, "读取本机快捷入口与实用动作", "常用直达"),
            AddCategoryItem(7, "Shell 命令", Icons.Rounded.Bolt, "一键执行脚本（普通/Shizuku/Root）", "常用直达"),
            AddCategoryItem(28, "识屏与系统动作", Icons.Rounded.CropFree, "区域截屏/全局识屏/小布识屏/锁屏/截图等", "常用直达"),
            AddCategoryItem(9, "桌面小部件", Icons.Rounded.Widgets, "嵌入系统桌面 AppWidget", "常用直达"),
            AddCategoryItem(4, "网站书签", Icons.Rounded.Language, "网址快捷入口与独立浏览器", "常用直达"),
            AddCategoryItem(6, "文件与目录", Icons.Rounded.Folder, "面板内文件浏览或文件快捷卡", "常用直达"),
            // 容器与排版
            AddCategoryItem(24, "网格容器", Icons.Rounded.GridView, "页面内多列网格自动排布子项", "容器与排版"),
            AddCategoryItem(10, "自由容器", Icons.Rounded.Dashboard, "页面内自由坐标与缩放摆放子项", "容器与排版"),
            AddCategoryItem(33, "子面板入口", Icons.Rounded.Layers, "点击切换至独立/继承尺寸子面板，支持嵌套", "容器与排版"),
            AddCategoryItem(16, "应用抽屉", Icons.Rounded.Apps, "带首字母索引的全部应用网格", "容器与排版"),
            AddCategoryItem(30, "键盘快启", Icons.Rounded.Keyboard, "免系统输入法，按首字母/拼写/数字/九宫格快速定位启动应用", "容器与排版"),
            AddCategoryItem(20, "最近运行", Icons.Rounded.History, "快速切换近期使用的应用", "容器与排版"),
            AddCategoryItem(11, "底部操作栏", Icons.Rounded.Dashboard, "快捷加号与面板菜单栏", "容器与排版"),
            // 系统控制与状态
            AddCategoryItem(21, "控制中心开关", Icons.Rounded.Tune, "Wi-Fi/蓝牙/手电筒/响铃等", "系统控制与状态"),
            AddCategoryItem(18, "音量控制", Icons.Rounded.VolumeUp, "支持横条 / 竖条 / 单图标弹出", "系统控制与状态"),
            AddCategoryItem(19, "亮度控制", Icons.Rounded.BrightnessMedium, "支持横条 / 竖条 / 单图标弹出", "系统控制与状态"),
            AddCategoryItem(17, "电池电量条", Icons.Rounded.BatteryChargingFull, "实时电量百分比与状态条", "系统控制与状态"),
            AddCategoryItem(25, "电量详情", Icons.Rounded.BatteryChargingFull, "电池电量、充电状态与温度详情", "系统控制与状态"),
            AddCategoryItem(22, "硬件与使用统计", Icons.Rounded.Storage, "内存/存储/使用时长/通知/解锁统计", "系统控制与状态"),
            // 内容与高级工具
            AddCategoryItem(14, "随手便签", Icons.Rounded.EditNote, "随时记录与编辑文本笔记", "内容与高级工具"),
            AddCategoryItem(29, "剪贴板卡片", Icons.Rounded.ContentPaste, "最近剪贴历史快速查看与复制", "内容与高级工具"),
            AddCategoryItem(8, "单张图片", Icons.Rounded.Image, "展示自定义相册图片", "内容与高级工具"),
            AddCategoryItem(15, "图片魔方", Icons.Rounded.ViewAgenda, "多图自动轮播或点击翻页", "内容与高级工具"),
            AddCategoryItem(12, "文字标签", Icons.Rounded.TextFields, "自定义标题或分组说明文字", "内容与高级工具"),
            AddCategoryItem(13, "每日一言", Icons.Rounded.FormatQuote, "随机展示精选句子", "内容与高级工具"),
            AddCategoryItem(5, "联系人直拨", Icons.Rounded.Person, "一键查看或呼叫指定联系人", "内容与高级工具"),
            AddCategoryItem(2, "应用活动 (Activity)", Icons.Rounded.Explore, "直达应用内公开页面", "内容与高级工具")
        )
    }

    fun addAndDismiss(item: ComponentItem, toastMsg: String): Boolean {
        val pageId = targetPageId
        val pageIndex = repository.state.value.pages.indexOfFirst { it.id == pageId }
        val prepared = if (pageIndex >= 0) repository.prepareNewComponent(pageIndex, item, targetContainerId) else item
        if (pageId == null || !repository.placeMenuComponent(pageId, targetContainerId, prepared, beforeComponentId, targetGridCell)) {
            Toast.makeText(context, "原页面、容器或插入位置已失效，未添加卡片", Toast.LENGTH_SHORT).show()
            return false
        }
        repository.selectComponent(prepared.id)
        Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
        onDismiss()
        return true
    }
    fun selectAction(kind: ActionPickerKind, shortcutComponent: String? = null) {
        val latest = repository.state.value
        val pageId = targetPageId?.takeIf { id -> latest.pages.any { it.id == id } } ?: return
        if (latest.advancedConfig.layoutLocked) return
        folderReturn?.clear()
        onDismiss()
        com.example.globalminusone.service.OverlayService.start(context, com.example.globalminusone.service.OverlayService.ACTION_CLOSE_PANEL)
        context.startActivity(
            ActionPickerRequest(kind, pageId, targetContainerId, beforeComponentId, shortcutComponent,
                targetGridCell?.first ?: -1, targetGridCell?.second ?: -1).createIntent(context)
        )
    }

    val palette = LocalPanelPalette.current
    val tokens = com.example.globalminusone.ui.panel.panelOverlayTokens(palette)
    val sheetBg = tokens.sheetBg
    val itemBg = tokens.cardBg
    val inputBg = tokens.inputBg
    val itemBorderColor = tokens.border
    val primaryText = tokens.textPrimary
    val secondaryText = tokens.textSecondary
    val bodyText = tokens.textPrimary

    com.example.globalminusone.theme.SettingsTheme(darkTheme = palette.isDark) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .overlayScrim(Color.Black, 0.55f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.80f)
                .overlaySheetMotion()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {},
            color = sheetBg,
            contentColor = primaryText,
            shadowElevation = 8.dp,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // 顶部标题与返回/关闭
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (activeSubPage != null) {
                            IconButton(
                                onClick = { activeSubPage = null },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = bodyText, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = when (activeSubPage) {
                                "APP" -> "选择应用"
                                "SHORTCUT" -> "选择或读取本机快捷方式"
                                "SYSTEM_ACTION" -> "识屏与系统动作"
                                "SWITCH" -> "通知栏开关"
                                "STAT" -> "系统与硬件统计"
                                "WEBSITE" -> "添加网址"
                                "FILE" -> "选择文件组件"
                                "SHELL" -> "添加 Shell 命令卡"
                                "SUB_PANEL_ENTRY" -> "添加子面板入口"
                                "VOLUME_STYLE" -> "选择音量样式"
                                "BRIGHTNESS_STYLE" -> "选择亮度样式"
                                else -> if (targetContainerId != null) "向容器添加项目"
                                else if (targetGridCell != null) "添加至第${targetGridCell.second + 1}行 · 第${targetGridCell.first + 1}列"
                                else "添加组件"
                            },
                            color = primaryText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Rounded.Close, "关闭", tint = secondaryText, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (activeSubPage) {
                        "SHELL" -> AddComponentShellSubPage(
                            accent = palette.accent,
                            bodyText = bodyText,
                            secondaryText = secondaryText,
                            onAdd = ::addAndDismiss
                        )
                        "SUB_PANEL_ENTRY" -> SubPanelAddEntrySubPage(
                            repository = repository,
                            palette = palette,
                            primaryText = primaryText,
                            secondaryText = secondaryText,
                            itemBg = itemBg,
                            itemBorderColor = itemBorderColor,
                            onAddEntry = ::addAndDismiss
                        )
                        "VOLUME_STYLE" -> AddComponentSliderStyleSubPage(
                            isVolume = true,
                            accent = palette.accent,
                            primaryText = primaryText,
                            secondaryText = secondaryText,
                            itemBg = itemBg,
                            itemBorderColor = itemBorderColor,
                            onAdd = ::addAndDismiss
                        )
                        "BRIGHTNESS_STYLE" -> AddComponentSliderStyleSubPage(
                            isVolume = false,
                            accent = palette.accent,
                            primaryText = primaryText,
                            secondaryText = secondaryText,
                            itemBg = itemBg,
                            itemBorderColor = itemBorderColor,
                            onAdd = ::addAndDismiss
                        )
                        "FILE" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("文件在当前负一屏面板内浏览和预览。", color = bodyText, fontSize = 13.sp)
                                OutlinedButton(onClick = {
                                    addAndDismiss(ComponentItem(type = ComponentType.FILE_BROWSER, title = "文件浏览", iconName = "folder", w = 32, h = 8), "已添加文件浏览入口")
                                }, modifier = Modifier.fillMaxWidth()) { Text("文件浏览器 · 浏览已授权目录") }
                                FilledTonalButton(onClick = {
                                    val latest = repository.state.value
                                    if (latest.advancedConfig.layoutLocked) {
                                        Toast.makeText(context, "布局已锁定，请先解锁", Toast.LENGTH_SHORT).show()
                                    } else latest.pages.firstOrNull { it.id == targetPageId }?.let { page ->
                                        folderReturn?.clear()
                                        onDismiss()
                                        PanelFileBrowser.requestFile(context, page.id, targetContainerId = targetContainerId, beforeId = beforeComponentId, targetGridCell = targetGridCell)
                                    }
                                }, modifier = Modifier.fillMaxWidth()) { Text("选择具体文件 · 创建文件快捷卡") }
                                Text("系统选择器仅用于选取文件，取消不会创建卡片。", color = secondaryText, fontSize = 12.sp)
                            }
                        }
                        "APP" -> AddComponentAppSubPage(
                            installedApps = installedApps,
                            accent = palette.accent,
                            primaryText = primaryText,
                            secondaryText = secondaryText,
                            itemBg = itemBg,
                            itemBorderColor = itemBorderColor,
                            onAdd = ::addAndDismiss
                        )
                        "SHORTCUT" -> AddComponentShortcutSubPage(
                            shortcutSnapshot = shortcutSnapshot,
                            loadingShortcuts = loadingShortcuts,
                            accent = palette.accent,
                            primaryText = primaryText,
                            secondaryText = secondaryText,
                            itemBg = itemBg,
                            itemBorderColor = itemBorderColor,
                            onSelectAction = { kind, shortcutComponent -> selectAction(kind, shortcutComponent) },
                            onOpenShell = { activeSubPage = "SHELL" },
                            onAdd = ::addAndDismiss
                        )
                        "SYSTEM_ACTION" -> {
                            val systemActions = listOf(
                                ComponentType.ACTION_REGION_SCREENSHOT to "自由框选区域、标注、保存、识字、原位翻译或提图",
                                ComponentType.ACTION_SCREEN_ASSISTANT to "仿 ColorOS 小布识屏：屏幕选字、局部翻译、多图提取",
                                ComponentType.ACTION_BREENO_TOUCH to "探测并调用系统小布识屏（无障碍 / Shizuku / Root，与内置独立）",
                                ComponentType.ACTION_SCREENSHOT to "触发系统整屏截屏",
                                ComponentType.ACTION_LOCK to "一键锁屏",
                                ComponentType.ACTION_HOME to "返回系统桌面",
                                ComponentType.ACTION_BACK to "收起面板或执行返回",
                                ComponentType.ACTION_RECENTS to "打开最近任务多任务界面",
                                ComponentType.ACTION_NOTIFICATIONS to "下拉展开系统通知栏",
                                ComponentType.ACTION_VOICE_ASSIST to "唤起系统默认语音助手",
                                ComponentType.ACTION_WECHAT_SCAN to "直达微信扫一扫界面"
                            )
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(systemActions, key = { it.first.cnum }) { (actionType, desc) ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                addAndDismiss(
                                                    ComponentItem(
                                                        type = actionType,
                                                        title = actionType.label,
                                                        w = actionType.defaultW,
                                                        h = actionType.defaultH
                                                    ),
                                                    "已添加「${actionType.label}」"
                                                )
                                            },
                                        color = itemBg,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(text = actionType.label, color = primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                                Text(text = desc, color = secondaryText, fontSize = 10.sp)
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                Icons.Rounded.Add,
                                                contentDescription = null,
                                                tint = palette.accent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        "SWITCH", "STAT" -> {
                            val items = if (activeSubPage == "SWITCH") {
                                listOf(
                                    ComponentType.SWITCH_WIFI, ComponentType.SWITCH_BLUETOOTH, ComponentType.SWITCH_TORCH,
                                    ComponentType.SWITCH_RINGER, ComponentType.SWITCH_MUTE, ComponentType.SWITCH_VIBRATE,
                                    ComponentType.SWITCH_LOCATION, ComponentType.SWITCH_HOTSPOT, ComponentType.SWITCH_NFC,
                                    ComponentType.SWITCH_AIRPLANE, ComponentType.SWITCH_AUTO_BRIGHTNESS, ComponentType.SWITCH_AUTO_ROTATE,
                                    ComponentType.SWITCH_KEEP_AWAKE, ComponentType.SWITCH_EYE_CARE
                                )
                            } else {
                                listOf(
                                    ComponentType.STAT_BATTERY_BAR, ComponentType.STAT_BATTERY_DETAIL, ComponentType.STAT_RAM,
                                    ComponentType.STAT_ROM, ComponentType.STAT_UPTIME, ComponentType.STAT_SCREEN_TIME,
                                    ComponentType.STAT_DATA_TRAFFIC, ComponentType.STAT_APP_COUNT, ComponentType.STAT_NOTIF_COUNT,
                                    ComponentType.STAT_UNLOCK_COUNT
                                )
                            }
                            val prefix = if (activeSubPage == "SWITCH") "已添加开关" else "已添加统计卡"
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(items, key = { it.cnum }) { compType ->
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                                            addAndDismiss(
                                                ComponentItem(
                                                    type = compType,
                                                    title = compType.label,
                                                    w = compType.defaultW,
                                                    h = compType.defaultH
                                                ),
                                                "$prefix「${compType.label}」"
                                            )
                                        },
                                        color = itemBg,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                compType.label,
                                                color = primaryText,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Icon(
                                                Icons.Rounded.Add,
                                                contentDescription = null,
                                                tint = palette.accent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        "WEBSITE" -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(text = "网站标题", fontSize = 12.sp, color = bodyText)
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    color = inputBg
                                ) {
                                    Row(modifier = Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        BasicTextField(
                                            value = websiteTitle,
                                            onValueChange = { websiteTitle = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            textStyle = TextStyle(color = primaryText, fontSize = 13.sp)
                                        )
                                    }
                                }
                                Text(text = "网址 URL", fontSize = 12.sp, color = bodyText)
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    color = inputBg
                                ) {
                                    Row(modifier = Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        BasicTextField(
                                            value = websiteUrl,
                                            onValueChange = { websiteUrl = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            textStyle = TextStyle(color = primaryText, fontSize = 13.sp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                FilledTonalButton(
                                    onClick = {
                                        val url = websiteUrl.trim()
                                        if (url.removePrefix("https://").removePrefix("http://").isBlank()) {
                                            Toast.makeText(context, "请输入有效的网址", Toast.LENGTH_SHORT).show()
                                            return@FilledTonalButton
                                        }
                                        val title = if (websiteTitle.isNotBlank()) websiteTitle else "自定义网址"
                                        val item = ComponentItem(
                                            type = ComponentType.WEBSITE_LINK, title = title, intentUri = url, w = 32, h = 6
                                        )
                                        addAndDismiss(item, "已添加网站「$title」")
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("确认添加")
                                }
                            }
                        }
                        else -> {
                            val trimmedQuery = categoryQuery.trim()
                            val groupOptions = remember(categories) { listOf("全部") + categories.map { it.group }.distinct() }
                            val categoryKeys = remember(categories) {
                                categories.associate { item ->
                                    item.id to PlatformPinyinProvider.instance.buildTextSearchKeys(
                                        "${item.name} ${item.desc} ${item.group}"
                                    )
                                }
                            }
                            val filteredCategories = remember(trimmedQuery, selectedGroup, categories, categoryKeys) {
                                when {
                                    trimmedQuery.isNotEmpty() -> {
                                        val clean = AppQuickSearchMatcher.cleanQuery(trimmedQuery)
                                        val allNumeric = clean.isNotEmpty() && clean.all { it in '0'..'9' }
                                        categories.mapNotNull { cat ->
                                            val literalMatch = cat.name.contains(trimmedQuery, ignoreCase = true) ||
                                                cat.desc.contains(trimmedQuery, ignoreCase = true) ||
                                                cat.group.contains(trimmedQuery, ignoreCase = true)
                                            val pinyinScore = categoryKeys[cat.id]?.let { keys ->
                                                AppQuickSearchMatcher.scoreTextMatch(keys, trimmedQuery, t9Mode = allNumeric)
                                            } ?: 0
                                            val score = maxOf(if (literalMatch) 900 else 0, pinyinScore)
                                            if (score > 0) cat to score else null
                                        }.sortedByDescending { it.second }.map { it.first }
                                    }
                                    selectedGroup != "全部" -> categories.filter { it.group == selectedGroup }
                                    else -> categories
                                }
                            }
                            val groupedCategories = remember(filteredCategories, trimmedQuery) {
                                if (trimmedQuery.isNotEmpty()) listOf("" to filteredCategories)
                                else filteredCategories.groupBy { it.group }.toList()
                            }
                            Column(modifier = Modifier.fillMaxSize()) {
                                OutlinedTextField(
                                    value = categoryQuery,
                                    onValueChange = { categoryQuery = it.take(40) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                    placeholder = { Text("搜索组件、文件夹、开关或统计…", fontSize = 12.sp) },
                                    trailingIcon = if (categoryQuery.isNotEmpty()) {
                                        {
                                            IconButton(onClick = { categoryQuery = "" }, modifier = Modifier.size(28.dp)) {
                                                Icon(Icons.Rounded.Close, "清空搜索", modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    } else null
                                )
                                if (trimmedQuery.isEmpty() && groupOptions.size > 2) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .padding(top = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        groupOptions.forEach { groupName ->
                                            FilterChip(
                                                selected = selectedGroup == groupName,
                                                onClick = { selectedGroup = groupName },
                                                label = { Text(groupName, fontSize = 11.sp, maxLines = 1) }
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                if (filteredCategories.isEmpty()) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("未找到匹配的组件类型", color = secondaryText, fontSize = 13.sp)
                                    }
                                } else {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(2),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f).fillMaxWidth()
                                    ) {
                                        groupedCategories.forEach { (groupTitle, groupItems) ->
                                            if (groupTitle.isNotEmpty()) {
                                                item(key = "header_$groupTitle", span = { GridItemSpan(2) }) {
                                                    Text(
                                                        text = groupTitle,
                                                        color = palette.accent,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                                                    )
                                                }
                                            }
                                            items(groupItems, key = { it.id }) { cat ->
                                                Surface(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .clickable {
                                                            when (cat.id) {
                                                                1 -> activeSubPage = "APP"
                                                                2 -> selectAction(ActionPickerKind.ACTIVITY)
                                                                3 -> activeSubPage = "SHORTCUT"
                                                                4 -> activeSubPage = "WEBSITE"
                                                                5 -> selectAction(ActionPickerKind.CONTACT)
                                                                6 -> activeSubPage = "FILE"
                                                                7 -> activeSubPage = "SHELL"
                                                                8 -> {
                                                                    val latest = repository.state.value
                                                                    if (latest.advancedConfig.layoutLocked) Toast.makeText(context, "布局已锁定，请先解锁", Toast.LENGTH_SHORT).show()
                                                                    else latest.pages.firstOrNull { it.id == targetPageId }?.let { page ->
                                                                        folderReturn?.clear()
                                                                        onDismiss()
                                                                        PanelFileBrowser.requestImage(context, page.id, targetContainerId = targetContainerId, beforeId = beforeComponentId, targetGridCell = targetGridCell)
                                                                    }
                                                                }
                                                                9 -> {
                                                                    folderReturn?.clear()
                                                                    targetPageId?.let(onRequestBindWidget)
                                                                    onDismiss()
                                                                }
                                                                24 -> addAndDismiss(
                                                                    ComponentItem(
                                                                        type = ComponentType.CONTAINER_BOX,
                                                                        title = "网格容器",
                                                                        containerColumns = 4,
                                                                        containerLayout = ContainerLayoutType.GRID,
                                                                        showTitle = true,
                                                                        w = 32,
                                                                        h = 12,
                                                                        autoHeight = true
                                                                    ),
                                                                    "已添加网格容器（默认4列）"
                                                                )
                                                                10 -> addAndDismiss(
                                                                    ComponentItem(
                                                                        type = ComponentType.CONTAINER_BOX,
                                                                        title = "自由容器",
                                                                        containerColumns = 4,
                                                                        containerLayout = ContainerLayoutType.FREE,
                                                                        showTitle = true,
                                                                        w = 32,
                                                                        h = 12,
                                                                        autoHeight = true
                                                                    ),
                                                                    "已添加自由容器"
                                                                )
                                                                11 -> addAndDismiss(
                                                                    ComponentItem(type = ComponentType.OPERATION_BAR, title = "操作栏", showTitle = false, w = 32, h = 4),
                                                                    "已添加操作栏"
                                                                )
                                                                23 -> addAndDismiss(
                                                                    ComponentItem(type = ComponentType.FOLDER, title = "文件夹", containerColumns = 4,
                                                                        containerLayout = ContainerLayoutType.GRID, showTitle = false, w = 8, h = 8, autoHeight = true),
                                                                    "已添加文件夹"
                                                                )
                                                                12 -> addAndDismiss(
                                                                    ComponentItem(type = ComponentType.TEXT_LABEL, title = "文字", textContent = "自定义文字", showTitle = true, w = 32, h = 5, autoHeight = true),
                                                                    "已添加文字卡"
                                                                )
                                                                13 -> addAndDismiss(
                                                                    ComponentItem(type = ComponentType.HITOKOTO_CARD, title = "一言", showTitle = true, w = 32, h = 7),
                                                                    "已添加一言卡片"
                                                                )
                                                                14 -> addAndDismiss(
                                                                    ComponentItem(type = ComponentType.NOTE_CARD, title = "便签", textContent = "随手记便签", showTitle = true, w = 32, h = 12),
                                                                    "已添加便签卡片"
                                                                )
                                                                15 -> {
                                                                    val latest = repository.state.value
                                                                    if (latest.advancedConfig.layoutLocked) Toast.makeText(context, "布局已锁定，请先解锁", Toast.LENGTH_SHORT).show()
                                                                    else latest.pages.firstOrNull { it.id == targetPageId }?.let { page ->
                                                                        folderReturn?.clear()
                                                                        onDismiss()
                                                                        com.example.globalminusone.PhotoCubePickerActivity.start(context, page.id,
                                                                            containerId = targetContainerId, beforeId = beforeComponentId, targetGridCell = targetGridCell)
                                                                    }
                                                                }
                                                                16 -> addAndDismiss(
                                                                    ComponentItem(
                                                                        type = ComponentType.APP_DRAWER_CARD,
                                                                        title = "应用抽屉",
                                                                        showTitle = false,
                                                                        w = 8,
                                                                        h = 8,
                                                                        gridSpanX = 1,
                                                                        gridSpanY = 1,
                                                                        intentUri = com.example.globalminusone.data.policy.AppDrawerCardSpec(
                                                                            com.example.globalminusone.data.policy.LauncherCardPresentationMode.POPUP_ICON
                                                                        ).encodeToIntentUri()
                                                                    ),
                                                                    "已添加应用抽屉图标"
                                                                )
                                                                30 -> addAndDismiss(
                                                                    ComponentItem(
                                                                        type = ComponentType.APP_KEYBOARD_LAUNCHER,
                                                                        title = "键盘快启",
                                                                        showTitle = false,
                                                                        w = 8,
                                                                        h = 8,
                                                                        gridSpanX = 1,
                                                                        gridSpanY = 1,
                                                                        intentUri = com.example.globalminusone.data.policy.AppKeyboardLauncherSpec(
                                                                            presentationMode = com.example.globalminusone.data.policy.LauncherCardPresentationMode.POPUP_ICON
                                                                        ).encodeToIntentUri()
                                                                    ),
                                                                    "已添加键盘快启图标"
                                                                )
                                                                17 -> addAndDismiss(
                                                                    ComponentItem(type = ComponentType.STAT_BATTERY_BAR, title = "剩余电量", showTitle = true, w = 10, h = 6),
                                                                    "已添加电量条"
                                                                )
                                                                18 -> activeSubPage = "VOLUME_STYLE"
                                                                19 -> activeSubPage = "BRIGHTNESS_STYLE"
                                                                20 -> addAndDismiss(
                                                                    ComponentItem(type = ComponentType.RECENT_APPS_CARD, title = "最近运行", showTitle = true, w = 32, h = 8),
                                                                    "已添加最近运行卡片"
                                                                )
                                                                21 -> activeSubPage = "SWITCH"
                                                                22 -> activeSubPage = "STAT"
                                                                25 -> addAndDismiss(
                                                                    ComponentItem(type = ComponentType.STAT_BATTERY_DETAIL,
                                                                        title = ComponentType.STAT_BATTERY_DETAIL.label,
                                                                        w = ComponentType.STAT_BATTERY_DETAIL.defaultW,
                                                                        h = ComponentType.STAT_BATTERY_DETAIL.defaultH),
                                                                    "已添加电量详情"
                                                                )
                                                                28 -> activeSubPage = "SYSTEM_ACTION"
                                                                29 -> addAndDismiss(
                                                                    ComponentItem(
                                                                        type = ComponentType.CLIPBOARD_CARD,
                                                                        title = "剪贴板",
                                                                        showTitle = true,
                                                                        w = ComponentType.CLIPBOARD_CARD.defaultW,
                                                                        h = ComponentType.CLIPBOARD_CARD.defaultH
                                                                    ),
                                                                    "已添加剪贴板卡片"
                                                                )
                                                                33 -> {
                                                                    val cur = repository.state.value
                                                                    if (cur.subPanels.isNotEmpty() || cur.subPanelLibrary.isNotEmpty()) {
                                                                        activeSubPage = "SUB_PANEL_ENTRY"
                                                                    } else {
                                                                        val createdSubPanel = repository.createSubPanel("子面板")
                                                                        if (createdSubPanel != null) {
                                                                            val item = ComponentItem(
                                                                                type = ComponentType.SUB_PANEL_ENTRY,
                                                                                title = createdSubPanel.name,
                                                                                subPanelId = createdSubPanel.id,
                                                                                w = ComponentType.SUB_PANEL_ENTRY.defaultW,
                                                                                h = ComponentType.SUB_PANEL_ENTRY.defaultH
                                                                            )
                                                                            if (!addAndDismiss(item, "已添加子面板「${createdSubPanel.name}」")) {
                                                                                repository.deleteSubPanel(createdSubPanel.id)
                                                                            }
                                                                        } else {
                                                                            Toast.makeText(context, "编辑未解锁或已达子面板上限", Toast.LENGTH_SHORT).show()
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        },
                                                    shape = RoundedCornerShape(
                                                        com.example.globalminusone.ui.panel.PanelOverlayRadii.Control
                                                    ),
                                                    color = itemBg,
                                                    border = androidx.compose.foundation.BorderStroke(0.8.dp, itemBorderColor)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 10.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(36.dp)
                                                                .clip(RoundedCornerShape(10.dp))
                                                                .background(palette.accent.copy(alpha = 0.12f)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(cat.icon, cat.name, tint = palette.accent, modifier = Modifier.size(19.dp))
                                                        }
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                cat.name,
                                                                color = primaryText,
                                                                fontSize = 12.5.sp,
                                                                fontWeight = FontWeight.SemiBold,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Text(
                                                                cat.desc,
                                                                color = secondaryText,
                                                                fontSize = 10.5.sp,
                                                                lineHeight = 13.sp,
                                                                maxLines = 2,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}
