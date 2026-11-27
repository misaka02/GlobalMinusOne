package com.example.globalminusone.ui.panel.file

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.FileBrowserConfig
import com.example.globalminusone.data.FileBrowserRoot
import com.example.globalminusone.system.file.BrowserEntry
import com.example.globalminusone.system.file.BrowserLocation
import com.example.globalminusone.system.file.BrowserPreview
import com.example.globalminusone.system.file.FileBrowserIo
import com.example.globalminusone.system.file.FileManagerIo
import com.example.globalminusone.system.file.PanelFileBrowser
import com.example.globalminusone.system.file.RootFileIo
import com.example.globalminusone.system.privilege.RootStatus
import com.example.globalminusone.system.privilege.UserRootSession
import com.example.globalminusone.ui.AppIcons as Icons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import com.example.globalminusone.ui.panel.gesture.ownPanelScrollGestures

@Composable
internal fun FileBrowserRoots(
    config: FileBrowserConfig,
    io: FileBrowserIo,
    readableRoots: Set<String>,
    checkingRoots: Boolean,
    onOpen: (FileBrowserRoot) -> Unit,
    onOpenShared: () -> Unit,
    onOpenRoot: (BrowserLocation) -> Unit,
    onOpenQuickFolder: (folderName: String, label: String, wildcard: String) -> Unit,
    onOpenStorageAnalyzer: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenRecent: () -> Unit,
    onAuthorizeShared: () -> Unit,
    onAdd: () -> Unit,
    onReauthorize: (FileBrowserRoot) -> Unit,
    onRemove: (FileBrowserRoot) -> Unit
) {
    val context = LocalContext.current
    val manager = remember(context, io) { FileManagerIo(context, io) }
    val rootState by UserRootSession.status.collectAsState()
    val rootScope = rememberCoroutineScope()
    var requestingRoot by remember { mutableStateOf(false) }
    var rootReason by remember { mutableStateOf<String?>(null) }
    var rootSectionExpanded by remember { mutableStateOf(false) }
    var addLocationMenuExpanded by remember { mutableStateOf(false) }
    var accessInfoExpanded by remember { mutableStateOf(false) }
    var volumes by remember { mutableStateOf<List<com.example.globalminusone.system.file.StorageVolumeInfo>>(emptyList()) }
    LaunchedEffect(Unit) { volumes = manager.volumes() }
    val primaryVolume = volumes.firstOrNull { !it.removable }
    val hasSharedAccess = io.hasAllFilesAccess()

    val usedFraction = remember(primaryVolume) {
        val total = primaryVolume?.total ?: 0L
        val avail = primaryVolume?.available ?: 0L
        if (total > 0L) ((total - avail).coerceAtLeast(0L).toFloat() / total.toFloat()).coerceIn(0f, 1f) else null
    }

    val rootsScroll = rememberLazyListState()
    LazyColumn(
        state = rootsScroll,
        modifier = Modifier.fillMaxSize().ownPanelScrollGestures(true,
            canScrollVertically = { dy -> fileListCanScroll(dy, rootsScroll.canScrollBackward, rootsScroll.canScrollForward) }),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Visual Storage Capacity & Cleaner Dashboard Card
        item(key = "storage-dashboard-card") {
            Surface(
                onClick = { if (hasSharedAccess) onOpenShared() else onAuthorizeShared() },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(38.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Storage, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("手机内部存储", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (hasSharedAccess) Color(0xFF34A853).copy(alpha = 0.16f) else MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = if (hasSharedAccess) "已授权" else "待授权",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (hasSharedAccess) Color(0xFF2E944B) else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = primaryVolume?.let { storageCapacityText(it) }
                                    ?: if (hasSharedAccess) "可读写共享存储文件与目录" else "授权后可一键直达下载、相册与存储清理",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (usedFraction != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            LinearProgressIndicator(
                                progress = { usedFraction },
                                modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
                                color = if (usedFraction > 0.88f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                            val totalBytes = primaryVolume?.total ?: 0L
                            val availBytes = primaryVolume?.available ?: 0L
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    "已用 ${(usedFraction * 100).toInt()}% (${formatFileSize((totalBytes - availBytes).coerceAtLeast(0L))})",
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "可用 ${formatFileSize(availBytes)}",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { if (hasSharedAccess) onOpenShared() else onAuthorizeShared() },
                            modifier = Modifier.weight(1f).height(36.dp),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Folder, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(if (hasSharedAccess) "浏览全部文件" else "授权手机存储", fontSize = 12.sp, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = onOpenStorageAnalyzer,
                            modifier = Modifier.weight(1f).height(36.dp),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Search, null, Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("存储分析清理", fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            }
        }

        // 2. Quick Bookmarks / Recent / Add Folder Strip
        item(key = "quick-nav-strip") {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    onClick = onOpenFavorites,
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Checklist, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                        Text("收藏 (${config.favorites.size})", fontSize = 11.5.sp, maxLines = 1)
                    }
                }
                Surface(
                    onClick = onOpenRecent,
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.History, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                        Text("最近 (${config.recent.size})", fontSize = 11.5.sp, maxLines = 1)
                    }
                }
                Box(Modifier.weight(1f)) {
                    Surface(
                        onClick = { addLocationMenuExpanded = true },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth().height(38.dp)
                    ) {
                        Row(
                            Modifier.fillMaxSize().padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Add, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(3.dp))
                            Text("添加目录", fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                        }
                    }
                    DropdownMenu(addLocationMenuExpanded, { addLocationMenuExpanded = false }) {
                        com.example.globalminusone.ui.Android12WindowKeyGuard()

                        DropdownMenuItem(
                            text = { Text("添加可读写目录 (推荐)") },
                            onClick = { addLocationMenuExpanded = false; PanelFileBrowser.requestWritableDirectory(context) }
                        )
                        DropdownMenuItem(
                            text = { Text("添加只读目录") },
                            onClick = { addLocationMenuExpanded = false; onAdd() }
                        )
                        DropdownMenuItem(
                            text = { Text("访问权限说明") },
                            onClick = { addLocationMenuExpanded = false; accessInfoExpanded = !accessInfoExpanded }
                        )
                    }
                }
            }
            if (accessInfoExpanded) {
                Text(
                    "SAF 目录授权可持续使用；仅访问您指定的目录及子目录，系统受保护的 Android/data 不会越权读取。",
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 3. 6-Tile Common Folders & Category Quick Grid
        item(key = "common-folders-grid") {
            data class QuickDirTile(
                val title: String,
                val sub: String,
                val folder: String,
                val icon: androidx.compose.ui.graphics.vector.ImageVector,
                val tint: Color,
                val wildcard: String = ""
            )
            val tiles = listOf(
                QuickDirTile("下载目录", "Download", "Download", Icons.Folder, Color(0xFF3B82F6)),
                QuickDirTile("相机相册", "DCIM", "DCIM", Icons.Image, Color(0xFF9B67CA)),
                QuickDirTile("图片截图", "Pictures", "Pictures", Icons.Image, Color(0xFFEC4899)),
                QuickDirTile("文档资料", "Documents", "Documents", Icons.EditNote, Color(0xFF5C7CFA)),
                QuickDirTile("影音媒体", "Movies", "Movies", Icons.PlayArrow, Color(0xFF0EA5E9)),
                QuickDirTile("安装包", "*.apk", "Download", Icons.Apps, Color(0xFF34A853), "*.apk,*.aab")
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "快捷目录直达",
                    modifier = Modifier.padding(horizontal = 4.dp),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                tiles.chunked(3).forEach { rowTiles ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowTiles.forEach { tile ->
                            Surface(
                                onClick = { onOpenQuickFolder(tile.folder, tile.title, tile.wildcard) },
                                shape = RoundedCornerShape(11.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier
                                            .size(28.dp)
                                            .background(tile.tint.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(tile.icon, null, Modifier.size(16.dp), tint = tile.tint)
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(tile.title, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(tile.sub, fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Removable SD / USB volumes if present
        items(volumes.filter { it.removable }, key = { "vol-${it.title}" }) { volume ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Storage, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("${volume.title} · 外置存储", fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                        Text(storageCapacityText(volume), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // 4. Authorized SAF Directory Bookmarks
        item(key = "bookmarks-header") {
            Text(
                "已授权目录书签 (${config.roots.size})",
                Modifier.padding(start = 4.dp, top = 2.dp),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (config.roots.isEmpty()) {
            item(key = "bookmarks-empty") {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "尚未添加单独的 SAF 书签目录；可点上方「添加目录」固定常用工作文件夹。",
                        Modifier.padding(12.dp),
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        items(config.roots.distinctBy { it.uri }, key = { it.uri }) { root ->
            var rootMenuExpanded by remember(root.uri) { mutableStateOf(false) }
            val isReadable = root.uri in readableRoots
            val isWritable by produceState(false, root.uri, isReadable, checkingRoots) {
                value = isReadable && withContext(Dispatchers.IO) { runCatching { manager.canWrite(io.rootLocation(root)) }.getOrDefault(false) }
            }
            Surface(
                shape = RoundedCornerShape(11.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onOpen(root) }.padding(start = 10.dp, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .background(Color(0xFFE0A82E).copy(alpha = 0.16f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Folder, null, tint = Color(0xFFE0A82E), modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(root.title, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        Text(
                            when {
                                checkingRoots -> "正在校验访问权限…"
                                isReadable -> if (isWritable) "可读写 · 已持久化授权" else "只读 · 已持久化授权"
                                else -> "授权已失效，点击菜单重新授权"
                            },
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (checkingRoots || isReadable) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                        )
                    }
                    Box {
                        IconButton(onClick = { rootMenuExpanded = true }, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.MoreVert, "${root.title}的位置操作", Modifier.size(18.dp))
                        }
                        DropdownMenu(rootMenuExpanded, { rootMenuExpanded = false }) {
                            com.example.globalminusone.ui.Android12WindowKeyGuard()

                            DropdownMenuItem(
                                text = { Text("升级为可读写授权") },
                                onClick = { rootMenuExpanded = false; PanelFileBrowser.requestWritableDirectory(context, root.uri) }
                            )
                            DropdownMenuItem(
                                text = { Text("重新授权只读") },
                                onClick = { rootMenuExpanded = false; onReauthorize(root) }
                            )
                            DropdownMenuItem(
                                text = { Text("移除目录书签", color = MaterialTheme.colorScheme.error) },
                                onClick = { rootMenuExpanded = false; onRemove(root) }
                            )
                        }
                    }
                }
            }
        }

        // 5. Collapsible Root Filesystem Card
        item(key = "root-fs-card") {
            Surface(
                shape = RoundedCornerShape(11.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 38.dp).clickable { rootSectionExpanded = !rootSectionExpanded },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Tune, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text("Root 文件系统 (/)", Modifier.weight(1f), fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                        Text(
                            if (rootState == RootStatus.Granted) "已连接" else if (rootSectionExpanded) "收起" else "展开",
                            fontSize = 11.sp,
                            color = if (rootState == RootStatus.Granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (rootSectionExpanded) {
                        Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                when (rootState) {
                                    RootStatus.NotRequested -> "状态：尚未请求授权"
                                    RootStatus.Granted -> "状态：已获授权 · 本次会话有效"
                                    RootStatus.Unavailable -> "状态：当前设备未提供 Root 权限"
                                    RootStatus.Denied -> "状态：未获授权，可手动重试"
                                    RootStatus.TimedOut -> "状态：授权超时，可手动重试"
                                    RootStatus.Error -> "状态：连接失败，可手动重试"
                                },
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text("仅只读浏览系统分区；不会自动挂载只读系统分区为可写。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedButton(
                                onClick = {
                                    if (rootState == RootStatus.Granted) onOpenRoot(RootFileIo.location("/"))
                                    else {
                                        requestingRoot = true
                                        rootScope.launch {
                                            try {
                                                val result = UserRootSession.requestAuthorization()
                                                rootReason = result.reason
                                                if (result.succeeded) onOpenRoot(RootFileIo.location("/"))
                                            } finally {
                                                requestingRoot = false
                                            }
                                        }
                                    }
                                },
                                enabled = !requestingRoot,
                                modifier = Modifier.height(34.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    if (requestingRoot) "等待 Root 授权…" else if (rootState == RootStatus.Granted) "进入根目录 /" else "请求授权并打开 /",
                                    fontSize = 12.sp
                                )
                            }
                            rootReason?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun FileEntryRow(
    entry: BrowserEntry,
    compact: Boolean = false,
    canModify: Boolean,
    selectionMode: Boolean,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onAction: (String) -> Unit,
    onMeasuredHeight: ((Int) -> Unit)? = null,
    onClick: () -> Unit
) {
    val extBadge = if (!entry.isDirectory && '.' in entry.name) {
        entry.name.substringAfterLast('.').uppercase(Locale.ROOT).take(4).takeIf { it.isNotEmpty() }
    } else null
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 44.dp else 48.dp)
            .onSizeChanged { onMeasuredHeight?.invoke(it.height) }
            .background(if (selectionMode && isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .combinedClickable(onClick = { if (selectionMode) onToggle() else onClick() }, onLongClick = { onAction("select") })
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(if (compact) 32.dp else 36.dp), contentAlignment = Alignment.Center) {
            if (selectionMode) Checkbox(isSelected, { onToggle() }, enabled = entry.blockedReason == null)
            else FileWorkspaceEntryGlyph(entry, compact = compact)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    entry.name,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = if (compact) 12.5.sp else 13.5.sp,
                    fontWeight = if (entry.isDirectory) FontWeight.SemiBold else FontWeight.Normal
                )
                if (extBadge != null) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = extBadge,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            val permBadge = if (entry.blockedReason != null) null else if (canModify) "读写" else "只读"
            Text(
                entry.blockedReason ?: listOfNotNull(
                    if (entry.isDirectory) "文件夹" else formatFileSize(entry.size),
                    formatFileDate(entry.modified),
                    permBadge
                ).joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!selectionMode) {
            IconButton(onClick = { onAction("menu") }, modifier = Modifier.size(if (compact) 36.dp else 40.dp)) {
                Icon(Icons.MoreVert, "${entry.name} 的操作", Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    HorizontalDivider(
        Modifier.padding(start = if (compact) 48.dp else 52.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .24f)
    )
}

@Composable
internal fun BrowserMessage(text: String, loading: Boolean = false, action: String? = null, onAction: () -> Unit = {}) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (loading) CircularProgressIndicator(Modifier.size(32.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null) TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
internal fun FilePreviewContent(
    entry: BrowserEntry, preview: BrowserPreview?, loading: Boolean, error: String?,
    io: FileBrowserIo, active: Boolean, onCancel: () -> Unit, compact: Boolean = false
) {
    Column(Modifier.fillMaxSize()) {
        if (!compact) {
            Text(listOfNotNull(formatFileSize(entry.size), formatFileDate(entry.modified)).joinToString(" · "),
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
        }
        when {
            !active -> Unit
            loading -> BrowserMessage("正在读取预览…", true, "取消预览", onCancel)
            preview is BrowserPreview.Pdf -> PdfFilePreview(preview, io)
            preview is BrowserPreview.Media -> MediaFilePreview(preview)
            preview is BrowserPreview.Summary -> {
                SelectionContainer(Modifier.weight(1f).fillMaxWidth().ownPanelScrollGestures(true).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Text(preview.content, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                }
            }
            error != null -> BrowserMessage(error)
            preview is BrowserPreview.Image -> ZoomableFileImage(preview, entry.name, entry.name.endsWith(".gif", true) || entry.mimeType == "image/gif")
            preview is BrowserPreview.Text -> {
                Text(if (preview.truncated) "${preview.encoding} · 仅预览前 256 KB，内容已截断；可打开原文件查看全文" else "${preview.encoding} 文本预览",
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(10.dp), style = MaterialTheme.typography.labelSmall)
                SelectionContainer(Modifier.weight(1f).fillMaxWidth().ownPanelScrollGestures(true).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Text(preview.content.ifEmpty { "（空文本文件）" }, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                }
            }
            else -> BrowserMessage("此类型请使用其他应用打开，也可分享原文件")
        }
    }
}

internal fun formatFileSize(size: Long?): String {
    if (size == null) return "大小未知"
    if (size < 1024) return "$size B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = size.toDouble() / 1024
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) { value /= 1024; unit++ }
    return String.format(Locale.getDefault(), "%.1f %s", value, units[unit])
}

internal fun formatFileDate(time: Long?): String? = time?.let {
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
}
