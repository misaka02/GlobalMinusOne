package com.example.globalminusone.ui.panel

import com.example.globalminusone.ActionPickerKind
import com.example.globalminusone.ActionPickerRequest

import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

import androidx.compose.foundation.BorderStroke
import android.graphics.drawable.BitmapDrawable
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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.core.graphics.drawable.toBitmap
import com.example.globalminusone.data.AppLaunchMode
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentOrientation
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.ContainerLayoutType
import com.example.globalminusone.data.DefaultPresets
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.system.AppAndIntentLauncher
import com.example.globalminusone.system.InstalledAppInfo
import com.example.globalminusone.system.PanelFileBrowser
import kotlinx.coroutines.launch

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
    beforeComponentId: String? = null
) {
    val context = LocalContext.current
    val addScope = rememberCoroutineScope()
    val folderReturn = LocalFolderReturnState.current
    // Keep the destination for this modal's lifetime even if the pager moves or pages change.
    val targetPageId = remember(repository) { repository.state.value.pages.getOrNull(currentPageIndex)?.id }
    var applicationMode by rememberSaveable { mutableStateOf(AppLaunchMode.NORMAL) }
    var checkingApplication by remember { mutableStateOf<String?>(null) }
    var activeSubPage by remember { mutableStateOf<String?>(null) }
    var categoryQuery by remember { mutableStateOf("") }
    var selectedGroup by rememberSaveable { mutableStateOf("全部") }
    var installedApps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    var websiteTitle by remember { mutableStateOf("") }
    var websiteUrl by remember { mutableStateOf("https://") }
    var shellTitle by remember { mutableStateOf("Shell命令") }
    var shellCommand by remember { mutableStateOf("") }

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
            AddCategoryItem(3, "快捷方式", Icons.Rounded.FlashlightOn, "扫一扫/付款码/截屏等系统动作", "常用直达"),
            AddCategoryItem(9, "桌面小部件", Icons.Rounded.Widgets, "嵌入系统桌面 AppWidget", "常用直达"),
            AddCategoryItem(4, "网站书签", Icons.Rounded.Language, "网址快捷入口与独立浏览器", "常用直达"),
            AddCategoryItem(6, "文件与目录", Icons.Rounded.Folder, "面板内文件浏览或文件快捷卡", "常用直达"),
            // 容器与排版
            AddCategoryItem(24, "网格容器", Icons.Rounded.GridView, "页面内多列网格自动排布子项", "容器与排版"),
            AddCategoryItem(10, "自由容器", Icons.Rounded.Dashboard, "页面内自由坐标与缩放摆放子项", "容器与排版"),
            AddCategoryItem(16, "应用抽屉", Icons.Rounded.Apps, "带首字母索引的全部应用网格", "容器与排版"),
            AddCategoryItem(20, "最近运行", Icons.Rounded.History, "快速切换近期使用的应用", "容器与排版"),
            AddCategoryItem(11, "底部操作栏", Icons.Rounded.Dashboard, "快捷加号与面板菜单栏", "容器与排版"),
            // 系统控制与状态
            AddCategoryItem(21, "控制中心开关", Icons.Rounded.Tune, "Wi-Fi/蓝牙/手电筒/响铃等", "系统控制与状态"),
            AddCategoryItem(18, "音量调节条", Icons.Rounded.VolumeUp, "横向滑动实时调节媒体音量", "系统控制与状态"),
            AddCategoryItem(26, "竖排音量条", Icons.Rounded.VolumeUp, "竖直上下滑动调节媒体音量", "系统控制与状态"),
            AddCategoryItem(19, "亮度调节条", Icons.Rounded.BrightnessMedium, "横向滑动实时调节屏幕亮度", "系统控制与状态"),
            AddCategoryItem(27, "竖排亮度条", Icons.Rounded.BrightnessMedium, "竖直上下滑动调节屏幕亮度", "系统控制与状态"),
            AddCategoryItem(17, "电池电量条", Icons.Rounded.BatteryChargingFull, "实时电量百分比与状态条", "系统控制与状态"),
            AddCategoryItem(25, "电量详情", Icons.Rounded.BatteryChargingFull, "电池电量、充电状态与温度详情", "系统控制与状态"),
            AddCategoryItem(22, "硬件与使用统计", Icons.Rounded.Storage, "内存/存储/剪贴板/开机统计", "系统控制与状态"),
            // 内容与高级工具
            AddCategoryItem(14, "随手便签", Icons.Rounded.EditNote, "随时记录与编辑文本笔记", "内容与高级工具"),
            AddCategoryItem(8, "单张图片", Icons.Rounded.Image, "展示自定义相册图片", "内容与高级工具"),
            AddCategoryItem(15, "图片魔方", Icons.Rounded.ViewAgenda, "多图自动轮播或点击翻页", "内容与高级工具"),
            AddCategoryItem(12, "文字标签", Icons.Rounded.TextFields, "自定义标题或分组说明文字", "内容与高级工具"),
            AddCategoryItem(13, "每日一言", Icons.Rounded.FormatQuote, "随机展示精选句子", "内容与高级工具"),
            AddCategoryItem(5, "联系人直拨", Icons.Rounded.Person, "一键查看或呼叫指定联系人", "内容与高级工具"),
            AddCategoryItem(2, "应用活动 (Activity)", Icons.Rounded.Explore, "直达应用内公开页面", "内容与高级工具"),
            AddCategoryItem(7, "Shell 命令", Icons.Rounded.Bolt, "普通 UID 命令执行与回显", "内容与高级工具")
        )
    }

    fun addAndDismiss(item: ComponentItem, toastMsg: String) {
        val pageId = targetPageId
        val pageIndex = repository.state.value.pages.indexOfFirst { it.id == pageId }
        val prepared = if (pageIndex >= 0) repository.prepareNewComponent(pageIndex, item, targetContainerId) else item
        if (pageId == null || !repository.placeMenuComponent(pageId, targetContainerId, prepared, beforeComponentId)) {
            Toast.makeText(context, "原页面、容器或插入位置已失效，未添加卡片", Toast.LENGTH_SHORT).show()
            return
        }
        repository.selectComponent(prepared.id)
        Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
        onDismiss()
    }
    fun selectAction(kind: ActionPickerKind) {
        val latest = repository.state.value
        val pageId = targetPageId?.takeIf { id -> latest.pages.any { it.id == id } } ?: return
        if (latest.advancedConfig.layoutLocked) return
        folderReturn?.clear()
        onDismiss()
        com.example.globalminusone.service.OverlayService.start(context, com.example.globalminusone.service.OverlayService.ACTION_CLOSE_PANEL)
        context.startActivity(ActionPickerRequest(kind, pageId, targetContainerId, beforeComponentId).createIntent(context))
    }

    val palette = LocalPanelPalette.current
    val sheetBg = if (palette.isDark) Color(0xFF1C2026) else Color.White
    val itemBg = if (palette.isDark) Color(0xFF252A34) else Color(0xFFF9F9F9)
    val inputBg = if (palette.isDark) Color(0xFF282D37) else Color(0xFFF5F5F5)
    val itemBorderColor = if (palette.isDark) Color(0xFF343B48) else Color(0xFFEEEEEE)
    val primaryText = if (palette.isDark) Color(0xFFF0F2F5) else Color(0xFF212121)
    val secondaryText = if (palette.isDark) Color(0xFF9EA6B5) else Color(0xFF757575)
    val bodyText = if (palette.isDark) Color(0xFFD0D5DD) else Color(0xFF424242)

    com.example.globalminusone.theme.SettingsTheme(darkTheme = palette.isDark) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
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
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {},
            color = sheetBg,
            contentColor = primaryText,
            shadowElevation = 8.dp,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
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
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = bodyText, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = when (activeSubPage) {
                                "APP" -> "选择应用"
                                "SHORTCUT" -> "选择快捷方式"
                                "SWITCH" -> "通知栏开关"
                                "STAT" -> "系统与硬件统计"
                                "WEBSITE" -> "添加网址"
                                "FILE" -> "选择文件组件"
                                "SHELL" -> "添加普通UID命令"
                                else -> if (targetContainerId == null) "添加组件" else "向容器添加项目"
                            },
                            color = primaryText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.Close, "关闭", tint = secondaryText, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (activeSubPage) {
                        "SHELL" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("以本应用UID执行，不自动请求Root。命令需要再次手动确认执行，5秒超时并回显结果。", color = bodyText)
                            androidx.compose.material3.OutlinedTextField(shellTitle, { shellTitle = it.take(100) }, label = { Text("名称") }, singleLine = true)
                            androidx.compose.material3.OutlinedTextField(shellCommand, { shellCommand = it.take(4096) },
                                modifier = Modifier.fillMaxWidth(), label = { Text("Shell命令") }, minLines = 3)
                            FilledTonalButton(enabled = shellTitle.isNotBlank() && shellCommand.isNotBlank(), onClick = {
                                addAndDismiss(ComponentItem(type = ComponentType.SHELL_COMMAND, title = shellTitle,
                                    textContent = shellCommand, subtitle = "普通UID执行", w = 32, h = 7), "已添加命令，尚未执行")
                            }) { Text("保存命令卡片") }
                        }
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
                                        PanelFileBrowser.requestFile(context, page.id, targetContainerId = targetContainerId, beforeId = beforeComponentId)
                                    }
                                }, modifier = Modifier.fillMaxWidth()) { Text("选择具体文件 · 创建文件快捷卡") }
                                Text("系统选择器仅用于选取文件，取消不会创建卡片。", color = secondaryText, fontSize = 12.sp)
                            }
                        }
                        "APP" -> Column(Modifier.fillMaxSize()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = applicationMode == AppLaunchMode.NORMAL, onClick = { applicationMode = AppLaunchMode.NORMAL },
                                    enabled = checkingApplication == null, label = { Text("普通打开") })
                                FilterChip(selected = applicationMode == AppLaunchMode.FLOATING, onClick = { applicationMode = AppLaunchMode.FLOATING },
                                    enabled = checkingApplication == null, label = { Text("小窗打开") })
                            }
                            if (checkingApplication != null) Text("正在检查小窗支持…", fontSize = 12.sp, color = secondaryText)
                            IndexedSelectionList(entries = installedApps, key = { it.packageName }, label = { it.label },
                                searchText = { "${it.label} ${it.packageName}" }, modifier = Modifier.weight(1f).fillMaxWidth()) { app ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable(enabled = checkingApplication == null) {
                                                val mode = applicationMode
                                                if (mode == AppLaunchMode.NORMAL) {
                                                    addAndDismiss(applicationCard(app.packageName, app.label, mode), "已添加应用「${app.label}」")
                                                } else {
                                                    checkingApplication = app.packageName
                                                    addScope.launch {
                                                        try {
                                                            if (resolveFloatingApplication(context, app.packageName, app.label) == null) {
                                                                Toast.makeText(context, "此应用当前不支持独立小窗，未添加普通打开入口", Toast.LENGTH_LONG).show()
                                                            } else addAndDismiss(applicationCard(app.packageName, app.label, mode), "已添加小窗入口「${app.label}」")
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
                                                accent = palette.accent,
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
                        "SHORTCUT" -> {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(DefaultPresets.builtInShortcuts, key = { it.id }) { sc ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                addAndDismiss(
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
                                                    imageVector = Icons.Rounded.FlashlightOn,
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
                        "SWITCH" -> {
                            val switches = listOf(
                                ComponentType.SWITCH_WIFI,
                                ComponentType.SWITCH_BLUETOOTH,
                                ComponentType.SWITCH_TORCH,
                                ComponentType.SWITCH_RINGER,
                                ComponentType.SWITCH_MUTE,
                                ComponentType.SWITCH_VIBRATE,
                                ComponentType.SWITCH_AUTO_BRIGHTNESS,
                                ComponentType.SWITCH_AUTO_ROTATE,
                                ComponentType.SWITCH_KEEP_AWAKE,
                                ComponentType.SWITCH_EYE_CARE
                            )
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(switches) { sw ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                addAndDismiss(
                                                    ComponentItem(
                                                        type = sw,
                                                        title = sw.label,
                                                        w = sw.defaultW,
                                                        h = sw.defaultH
                                                    ),
                                                    "已添加开关「${sw.label}」"
                                                )
                                            },
                                        color = itemBg,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = sw.label, color = primaryText, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                            Text(text = "默认: ${sw.defaultW}×${sw.defaultH}", color = secondaryText, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                        "STAT" -> {
                            val stats = listOf(
                                ComponentType.STAT_BATTERY_BAR,
                                ComponentType.STAT_BATTERY_DETAIL,
                                ComponentType.STAT_RAM,
                                ComponentType.STAT_ROM,
                                ComponentType.STAT_UPTIME,
                                ComponentType.STAT_SCREEN_TIME,
                                ComponentType.STAT_DATA_TRAFFIC,
                                ComponentType.STAT_APP_COUNT
                            )
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(stats) { st ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                addAndDismiss(
                                                    ComponentItem(
                                                        type = st,
                                                        title = st.label,
                                                        w = st.defaultW,
                                                        h = st.defaultH
                                                    ),
                                                    "已添加统计卡「${st.label}」"
                                                )
                                            },
                                        color = itemBg,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = st.label, color = primaryText, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                            Text(text = "默认: ${st.defaultW}×${st.defaultH}", color = secondaryText, fontSize = 11.sp)
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
                                        val title = if (websiteTitle.isNotBlank()) websiteTitle else "自定义网址"
                                        addAndDismiss(
                                            ComponentItem(
                                                type = ComponentType.WEBSITE_LINK,
                                                title = title,
                                                intentUri = websiteUrl,
                                                w = 32,
                                                h = 6
                                            ),
                                            "已添加网站「$title」"
                                        )
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
                            val filteredCategories = remember(trimmedQuery, selectedGroup, categories) {
                                when {
                                    trimmedQuery.isNotEmpty() -> categories.filter {
                                        it.name.contains(trimmedQuery, ignoreCase = true) ||
                                            it.desc.contains(trimmedQuery, ignoreCase = true) ||
                                            it.group.contains(trimmedQuery, ignoreCase = true)
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
                                                                        PanelFileBrowser.requestImage(context, page.id, targetContainerId = targetContainerId, beforeId = beforeComponentId)
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
                                                                            containerId = targetContainerId, beforeId = beforeComponentId)
                                                                    }
                                                                }
                                                                16 -> addAndDismiss(
                                                                    ComponentItem(type = ComponentType.APP_DRAWER_CARD, title = "应用抽屉", showTitle = true, w = 32, h = ComponentType.APP_DRAWER_CARD.defaultH),
                                                                    "已添加应用抽屉"
                                                                )
                                                                17 -> addAndDismiss(
                                                                    ComponentItem(type = ComponentType.STAT_BATTERY_BAR, title = "剩余电量", showTitle = true, w = 10, h = 6),
                                                                    "已添加电量条"
                                                                )
                                                                18 -> addAndDismiss(
                                                                    ComponentItem(type = ComponentType.VOLUME_BAR, title = "音量条", showTitle = false, orientation = ComponentOrientation.HORIZONTAL, w = 32, h = 5),
                                                                    "已添加音量条"
                                                                )
                                                                26 -> addAndDismiss(
                                                                    ComponentItem(type = ComponentType.VOLUME_BAR, title = "竖排音量条", showTitle = false, orientation = ComponentOrientation.VERTICAL, w = 6, h = 16, gridSpanX = 1, gridSpanY = 3),
                                                                    "已添加竖排音量条"
                                                                )
                                                                19 -> addAndDismiss(
                                                                    ComponentItem(type = ComponentType.BRIGHTNESS_BAR, title = "亮度条", showTitle = false, orientation = ComponentOrientation.HORIZONTAL, w = 32, h = 5),
                                                                    "已添加亮度条"
                                                                )
                                                                27 -> addAndDismiss(
                                                                    ComponentItem(type = ComponentType.BRIGHTNESS_BAR, title = "竖排亮度条", showTitle = false, orientation = ComponentOrientation.VERTICAL, w = 6, h = 16, gridSpanX = 1, gridSpanY = 3),
                                                                    "已添加竖排亮度条"
                                                                )
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
                                                            }
                                                        },
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = itemBg,
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, itemBorderColor)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(34.dp)
                                                                .clip(CircleShape)
                                                                .background(palette.accent.copy(alpha = 0.12f)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                imageVector = cat.icon,
                                                                contentDescription = cat.name,
                                                                tint = palette.accent,
                                                                modifier = Modifier.size(19.dp)
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = cat.name,
                                                                color = primaryText,
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.SemiBold,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Text(
                                                                text = cat.desc,
                                                                color = secondaryText,
                                                                fontSize = 10.sp,
                                                                maxLines = 1,
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

// -----------------------------------------------------------------------------------------
// 组件属性编辑弹窗 (ComponentEditModal)
// -----------------------------------------------------------------------------------------

@Composable
private fun CachedAppRowIcon(
    packageName: String,
    label: String,
    accent: Color,
    borderColor: Color
) {
    val context = LocalContext.current
    val initialBitmap = remember(packageName) {
        AppAndIntentLauncher.peekAppInfoCached(packageName)?.icon?.let { drawable ->
            (drawable as? BitmapDrawable)?.bitmap?.asImageBitmap()
                ?: runCatching { drawable.toBitmap().asImageBitmap() }.getOrNull()
        }
    }
    val loadedBitmap by produceState<ImageBitmap?>(initialValue = initialBitmap, packageName) {
        if (value == null) {
            val info = AppAndIntentLauncher.getAppInfoCached(context, packageName)
            value = info?.icon?.let { drawable ->
                (drawable as? BitmapDrawable)?.bitmap?.asImageBitmap()
                    ?: runCatching { drawable.toBitmap().asImageBitmap() }.getOrNull()
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
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
