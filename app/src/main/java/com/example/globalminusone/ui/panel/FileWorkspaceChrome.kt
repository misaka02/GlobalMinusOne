package com.example.globalminusone.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.system.BrowserEntry
import com.example.globalminusone.system.BrowserLocation
import com.example.globalminusone.system.FileCategory
import com.example.globalminusone.ui.AppIcons as Icons

internal data class FileWorkspaceAction(val title: String, val icon: ImageVector,
    val enabled: Boolean = true, val destructive: Boolean = false, val onClick: () -> Unit)

@Composable
internal fun FileWorkspaceActionSheet(title: String, actions: List<FileWorkspaceAction>, onDismiss: () -> Unit) {
    val palette = opaqueFilePalette(LocalPanelPalette.current)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .35f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss))
        val columns = if (maxWidth >= 260.dp) 2 else 1
        Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = maxHeight * .72f)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp), color = palette.cardBackground,
            contentColor = palette.textPrimary, shadowElevation = 8.dp) {
            Column {
                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) { Icon(Icons.Close, "关闭菜单", Modifier.size(20.dp), tint = palette.textSecondary) }
                }
                HorizontalDivider(color = palette.cardBorder.copy(alpha = .45f))
                val sheetScrollState = rememberScrollState()
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .ownPanelScrollGestures(true)
                        .verticalScroll(sheetScrollState)
                        .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 20.dp)
                ) {
                    actions.chunked(columns).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { action ->
                                TextButton(onClick = action.onClick, enabled = action.enabled,
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(action.icon, null, Modifier.size(20.dp), tint = if (!action.enabled) palette.textSecondary.copy(alpha = .4f) else if (action.destructive) MaterialTheme.colorScheme.error else palette.textPrimary)
                                        Spacer(Modifier.width(10.dp))
                                        Text(action.title, Modifier.weight(1f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                            color = if (!action.enabled) palette.textSecondary.copy(alpha = .4f) else if (action.destructive) MaterialTheme.colorScheme.error else palette.textPrimary)
                                    }
                                }
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                if (sheetScrollState.canScrollForward) {
                    HorizontalDivider(color = palette.cardBorder.copy(alpha = .35f))
                    Text(
                        text = "↑ 上滑查看更多操作",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        fontSize = 10.sp,
                        color = palette.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
internal fun FileWorkspacePathBar(stack: List<BrowserLocation>, title: String, status: String,
    onBack: () -> Unit, onSegment: (Int) -> Unit, onInput: () -> Unit,
    quickFilterActive: Boolean = false, onQuickFilter: (() -> Unit)? = null) {
    val palette = opaqueFilePalette(LocalPanelPalette.current)
    val pathListState = rememberLazyListState()
    LaunchedEffect(stack.map { it.key }) { if (stack.isNotEmpty()) pathListState.scrollToItem(stack.lastIndex) }
    val pathSegments: @Composable () -> Unit = {
        if (stack.isEmpty()) Text(title, Modifier.fillMaxWidth().padding(horizontal = 8.dp), fontSize = 13.sp,
            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = palette.textPrimary)
        else LazyRow(Modifier.fillMaxWidth().heightIn(min = 32.dp).ownPanelScrollGestures(true), state = pathListState,
            verticalAlignment = Alignment.CenterVertically) {
            itemsIndexed(stack, key = { index, location -> "$index:${location.key}" }) { index, segment ->
                if (index > 0) Text("/", fontSize = 11.sp, color = palette.textSecondary)
                TextButton(onClick = { onSegment(index) }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.heightIn(min = 32.dp).widthIn(max = 200.dp)) {
                    Text(segment.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp,
                        color = if (index == stack.lastIndex) palette.accent else palette.textSecondary)
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val narrow = fileBrowserNarrowChrome(maxWidth.value, LocalDensity.current.fontScale)
        Column {
            if (narrow) pathSegments()
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.ArrowBack, "返回上一级", tint = palette.textPrimary, modifier = Modifier.size(19.dp))
                }
                Column(Modifier.weight(1f)) {
                    if (!narrow) pathSegments()
                    if (status.isNotEmpty()) Text(status, Modifier.padding(start = 4.dp), fontSize = 10.sp,
                        color = palette.textSecondary, maxLines = if (narrow) 3 else 2, overflow = TextOverflow.Ellipsis)
                }
                if (onQuickFilter != null && stack.isNotEmpty()) {
                    IconButton(onClick = onQuickFilter, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.FilterList, "即时过滤", Modifier.size(18.dp), tint = if (quickFilterActive) palette.accent else palette.textSecondary)
                    }
                }
                IconButton(onClick = onInput, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Edit, "输入路径", Modifier.size(18.dp), tint = palette.textSecondary)
                }
            }
        }
    }
}

@Composable
internal fun FileWorkspaceEntryGlyph(entry: BrowserEntry, compact: Boolean = false) {
    val ext = if (!entry.isDirectory && '.' in entry.name) {
        entry.name.substringAfterLast('.').lowercase(java.util.Locale.ROOT)
    } else ""
    val isApk = ext == "apk" || ext == "aab" || ext == "apks"
    val category = FileCategory.of(entry)
    val color = when {
        isApk -> Color(0xFF34A853)
        category == FileCategory.DIRECTORY -> Color(0xFFE0A82E)
        category == FileCategory.IMAGE -> Color(0xFF9B67CA)
        category == FileCategory.AUDIO -> Color(0xFF38A88D)
        category == FileCategory.VIDEO -> Color(0xFF4C8BF5)
        category == FileCategory.ARCHIVE -> Color(0xFFD28445)
        category == FileCategory.DOCUMENT -> Color(0xFF5C7CFA)
        else -> Color(0xFF7E8A9C)
    }
    val icon = when {
        isApk -> Icons.Apps
        category == FileCategory.DIRECTORY -> Icons.Folder
        category == FileCategory.IMAGE -> Icons.Image
        category == FileCategory.AUDIO -> Icons.VolumeUp
        category == FileCategory.VIDEO -> Icons.PlayArrow
        category == FileCategory.ARCHIVE -> Icons.Apps
        category == FileCategory.DOCUMENT -> Icons.EditNote
        else -> Icons.Storage
    }
    val boxSize = if (compact) 30.dp else 34.dp
    val iconSize = if (compact) 18.dp else 20.dp
    Box(
        Modifier
            .size(boxSize)
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, if (entry.isDirectory) "文件夹" else null, Modifier.size(iconSize), tint = color)
    }
}

@Composable
internal fun FileWorkspaceBottomBar(actions: List<FileWorkspaceAction>) {
    val palette = opaqueFilePalette(LocalPanelPalette.current)
    Surface(color = palette.cardBackground, tonalElevation = 2.dp) {
        Column {
            HorizontalDivider(color = palette.cardBorder.copy(alpha = .45f))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = fileBrowserToolbarColumns(maxWidth.value, LocalDensity.current.fontScale, actions.size)
                Column {
                    actions.chunked(columns).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { action ->
                                TextButton(onClick = action.onClick, enabled = action.enabled,
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = action.title },
                                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(action.icon, null, Modifier.size(20.dp), tint = if (!action.enabled) palette.textSecondary.copy(alpha = .4f) else if (action.destructive) MaterialTheme.colorScheme.error else palette.accent)
                                        Spacer(Modifier.height(3.dp))
                                        Text(action.title, fontSize = 10.sp, textAlign = TextAlign.Center,
                                            color = if (!action.enabled) palette.textSecondary.copy(alpha = .4f) else if (action.destructive) MaterialTheme.colorScheme.error else palette.textPrimary)
                                    }
                                }
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}
