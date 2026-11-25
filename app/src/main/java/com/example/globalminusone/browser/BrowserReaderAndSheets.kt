package com.example.globalminusone.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.ui.AppIcons

@Composable
internal fun BrowserAddressAndEngineBar(
    address: String,
    onAddressChange: (String) -> Unit,
    editingAddress: Boolean,
    onEditingChanged: (Boolean) -> Unit,
    url: String,
    privateMode: Boolean,
    incognitoMode: Boolean,
    nightMode: Boolean,
    progress: Int,
    selectedEngine: BrowserSearchEngine,
    onRequestHostInput: () -> Unit,
    onNavigate: (String, BrowserSearchEngine) -> Unit,
    onToggleNightMode: () -> Unit,
    onStopLoading: () -> Unit,
    onReload: () -> Unit,
    onSelectEngine: (BrowserSearchEngine) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 6.dp, end = 2.dp).height(46.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isHttps = url.startsWith("https://", ignoreCase = true)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = when {
                    privateMode || incognitoMode -> MaterialTheme.colorScheme.tertiaryContainer
                    isHttps -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                },
                modifier = Modifier.padding(end = 6.dp)
            ) {
                Text(
                    text = when {
                        privateMode -> "独立无痕"
                        incognitoMode -> "暂停历史"
                        isHttps -> "🔒"
                        else -> "⚠"
                    },
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            BrowserAddressField(
                address,
                onAddressChange,
                onRequestHostInput,
                { onNavigate(address, selectedEngine) },
                Modifier.weight(1f),
                onEditingChanged,
                placeholder = "使用 ${selectedEngine.label} 搜索或输入网址"
            )
            if (!editingAddress) {
                IconButton(
                    onClick = onToggleNightMode,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        AppIcons.DarkMode,
                        contentDescription = if (nightMode) "关闭夜间模式" else "开启夜间模式",
                        modifier = Modifier.size(18.dp),
                        tint = if (nightMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(
                onClick = {
                    if (editingAddress) onNavigate(address, selectedEngine)
                    else if (progress < 100) onStopLoading() else onReload()
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    if (editingAddress) AppIcons.ArrowForward else if (progress < 100) AppIcons.Close else AppIcons.Refresh,
                    contentDescription = if (editingAddress) "前往或搜索" else if (progress < 100) "停止加载" else "刷新",
                    modifier = Modifier.size(19.dp)
                )
            }
        }
        if (editingAddress) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("引擎:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BrowserSearchEngine.entries.forEach { engine ->
                    val selected = selectedEngine == engine
                    Surface(
                        onClick = { onSelectEngine(engine) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Text(
                            text = engine.label,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun BrowserZoomSheet(
    pagePercent: Int,
    textZoom: Int,
    forcePageZoom: Boolean,
    onDismiss: () -> Unit,
    onTextZoomChange: (Int) -> Unit,
    onPageZoomRequest: (Int) -> Unit,
    onForcePageZoomChange: (Boolean) -> Unit
) {
    CompactBrowserPanel("字号与页面缩放 ($pagePercent%)", onDismiss) {
        Column(
            Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("网页文字大小 ${textZoom}%", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                FilledTonalButton(
                    onClick = { onTextZoomChange((textZoom - 10).coerceAtLeast(50)) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) { Text("A−") }
                Spacer(Modifier.width(6.dp))
                TextButton(
                    onClick = { onTextZoomChange(100) },
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) { Text("100%") }
                Spacer(Modifier.width(6.dp))
                FilledTonalButton(
                    onClick = { onTextZoomChange((textZoom + 10).coerceAtMost(200)) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) { Text("A＋") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            Text("整体画面比例（也可双指捏合缩放）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onPageZoomRequest((pagePercent - 25).coerceAtLeast(25)) }, modifier = Modifier.weight(1f).heightIn(min = 44.dp)) { Text("−25%") }
                TextButton(onClick = { onPageZoomRequest(100) }, modifier = Modifier.weight(1f).heightIn(min = 44.dp)) { Text("重置 100%") }
                TextButton(onClick = { onPageZoomRequest((pagePercent + 25).coerceAtMost(500)) }, modifier = Modifier.weight(1f).heightIn(min = 44.dp)) { Text("＋25%") }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("强制突破网页禁止缩放限制", Modifier.weight(1f), fontSize = 13.sp)
                Switch(checked = forcePageZoom, onCheckedChange = onForcePageZoomChange)
            }
        }
    }
}

@Composable
internal fun BrowserTabsSheet(
    session: IndependentBrowserSession,
    onDismiss: () -> Unit
) {
    CompactBrowserPanel("标签页 ${session.tabs.size}/8", onDismiss, footer = {
        if (session.tabs.size > 1) {
            TextButton(onClick = { session.closeAllTabs(); onDismiss() }) {
                Text("关闭全部", color = MaterialTheme.colorScheme.error)
            }
        }
        TextButton(onClick = { if (session.openTab()) onDismiss() }, enabled = session.tabs.size < 8) { Text("＋ 新标签页") }
    }) {
        LazyColumn(Modifier.heightIn(max = 340.dp).padding(horizontal = 8.dp)) {
            items(session.tabs, key = { it.id }) { entry ->
                val badges = buildList {
                    if (entry.desktop) add("桌面")
                    if (!entry.javascriptEnabled) add("禁JS")
                    if (entry.readerMode) add("阅读")
                    if (entry.blockedCount > 0) add("拦${entry.blockedCount}")
                }.joinToString(" · ")
                OutlinedCard(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = if (entry.id == session.activeId) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { session.selectTab(entry.id); onDismiss() },
                            modifier = Modifier.weight(1f).heightIn(min = 46.dp)
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(
                                    (if (entry.id == session.activeId) "● " else "") + entry.title + (if (badges.isNotEmpty()) " [$badges]" else ""),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(entry.url, maxLines = 1, style = MaterialTheme.typography.bodySmall, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        IconButton(onClick = { session.closeTab(entry.id) }, modifier = Modifier.size(44.dp)) {
                            Icon(AppIcons.Close, contentDescription = "关闭标签")
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun BrowserLibrarySheet(
    pane: String,
    entries: List<BrowserLink>,
    onDismiss: () -> Unit,
    onRequestHistoryClear: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onRemoveBookmark: (String) -> Unit
) {
    CompactBrowserPanel(
        pane,
        onDismiss,
        footer = if (pane == "历史" && entries.isNotEmpty()) ({
            TextButton(onClick = onRequestHistoryClear) { Text("清空历史", color = MaterialTheme.colorScheme.error) }
        }) else null
    ) {
        if (entries.isEmpty()) {
            Text(
                if (pane == "书签") "还没有书签，可在菜单中收藏当前网页" else "还没有浏览记录",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else LazyColumn(Modifier.heightIn(max = 340.dp)) {
            items(entries, key = { it.url }) { entry ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { onOpenUrl(entry.url) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(entry.url, maxLines = 1, style = MaterialTheme.typography.bodySmall, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (pane == "书签") {
                        TextButton(onClick = { onRemoveBookmark(entry.url) }) {
                            Text("移除", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun BrowserClearHistoryDialog(
    historySize: Int,
    onDismiss: () -> Unit,
    onConfirmClear: () -> Unit
) {
    CompactBrowserPanel("清空浏览历史？", onDismiss, footer = {
        TextButton(onClick = onDismiss) { Text("取消") }
        TextButton(
            onClick = onConfirmClear,
            enabled = historySize > 0
        ) {
            Text("确认清空", color = MaterialTheme.colorScheme.error)
        }
    }) {
        Text("将删除独立浏览器的 $historySize 条浏览历史，无法撤销。", modifier = Modifier.padding(16.dp))
    }
}

@Composable
internal fun BrowserClearDataDialog(
    onDismiss: () -> Unit,
    onConfirmClear: () -> Unit
) {
    CompactBrowserPanel("清理网页缓存与 Cookie？", onDismiss, footer = {
        TextButton(onClick = onDismiss) { Text("取消") }
        TextButton(onClick = onConfirmClear) {
            Text("确认清理", color = MaterialTheme.colorScheme.error)
        }
    }) {
        Text("将清除独立浏览器的网页缓存、表单数据及全部网站登录状态（Cookie），无法撤销。", modifier = Modifier.padding(16.dp))
    }
}

@Composable
internal fun ReaderModeOverlay(
    article: ReaderArticle?,
    loading: Boolean,
    javascriptEnabled: Boolean,
    paperTheme: ReaderPaperTheme,
    fontSp: Int,
    onSelectTheme: (ReaderPaperTheme) -> Unit,
    onDecreaseFont: () -> Unit,
    onIncreaseFont: () -> Unit,
    onEnableJsAndExtract: () -> Unit,
    onRefreshExtract: () -> Unit,
    onExit: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(paperTheme.bg)
    ) {
        Surface(
            color = paperTheme.text.copy(alpha = 0.06f),
            contentColor = paperTheme.text,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "阅读提纯",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = paperTheme.text,
                    modifier = Modifier.padding(end = 4.dp)
                )
                ReaderPaperTheme.entries.forEach { option ->
                    val selected = option == paperTheme
                    Surface(
                        onClick = { onSelectTheme(option) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) paperTheme.text.copy(alpha = 0.16f) else Color.Transparent,
                        border = BorderStroke(1.dp, paperTheme.muted.copy(alpha = 0.35f))
                    ) {
                        Text(
                            option.label,
                            fontSize = 11.sp,
                            color = paperTheme.text,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = onDecreaseFont,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(30.dp).widthIn(min = 34.dp)
                ) { Text("A−", fontSize = 12.sp, color = paperTheme.text) }
                TextButton(
                    onClick = onIncreaseFont,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(30.dp).widthIn(min = 34.dp)
                ) { Text("A＋", fontSize = 12.sp, color = paperTheme.text) }
                TextButton(
                    onClick = onExit,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(30.dp)
                ) { Text("退出", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = paperTheme.text) }
            }
        }
        HorizontalDivider(color = paperTheme.muted.copy(alpha = 0.2f))

        when {
            loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp, color = paperTheme.text)
                        Text("正在提炼网页正文…", fontSize = 13.sp, color = paperTheme.muted)
                    }
                }
            }
            !javascriptEnabled -> {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("当前标签已关闭 JS 脚本，无法读取网页正文", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = paperTheme.text)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onEnableJsAndExtract) { Text("开启 JS 并提取") }
                            OutlinedButton(onClick = onExit) { Text("返回原网页", color = paperTheme.text) }
                        }
                    }
                }
            }
            article == null || article.blocks.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("当前页面未检测到可提纯的长文段落", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = paperTheme.text)
                        Text("适用于新闻、博客、百科、技术文档等正文页面。", fontSize = 12.sp, color = paperTheme.muted)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onRefreshExtract) { Text("重新提取", color = paperTheme.text) }
                            Button(onClick = onExit) { Text("返回原网页") }
                        }
                    }
                }
            }
            else -> {
                SelectionContainer(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(key = "reader-header") {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = article.title,
                                    fontSize = (fontSp + 4).sp,
                                    lineHeight = (fontSp + 10).sp,
                                    fontWeight = FontWeight.Bold,
                                    color = paperTheme.text
                                )
                                if (article.site.isNotBlank()) {
                                    Text(
                                        text = "${article.site} · 共 ${article.blocks.size} 段",
                                        fontSize = 11.sp,
                                        color = paperTheme.muted
                                    )
                                }
                                HorizontalDivider(
                                    modifier = Modifier.padding(top = 6.dp),
                                    color = paperTheme.muted.copy(alpha = 0.25f)
                                )
                            }
                        }
                        items(article.blocks.size, key = { "rb-$it" }) { idx ->
                            val block = article.blocks[idx]
                            if (block.isHeading) {
                                Text(
                                    text = block.text,
                                    fontSize = (fontSp + 2).sp,
                                    lineHeight = (fontSp + 8).sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = paperTheme.text,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            } else {
                                Text(
                                    text = block.text,
                                    fontSize = fontSp.sp,
                                    lineHeight = (fontSp * 1.72f).sp,
                                    color = paperTheme.text
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal class BrowserTool(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val active: Boolean = false,
    val destructive: Boolean = false,
    val group: BrowserToolGroup = BrowserToolGroup.PAGE,
    val action: () -> Unit
)

@Composable
internal fun BrowserBarButton(label: String, icon: ImageVector, enabled: Boolean, modifier: Modifier, action: () -> Unit) {
    IconButton(onClick = action, enabled = enabled, modifier = modifier.height(48.dp)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp))
    }
}

@Composable
internal fun BrowserNavigationButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    modifier: Modifier,
    active: Boolean = false,
    destructive: Boolean = false,
    action: () -> Unit
) {
    val containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        destructive -> MaterialTheme.colorScheme.error
        active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        onClick = action,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        modifier = modifier.heightIn(min = 58.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(5.dp))
            Text(
                label,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
internal fun BrowserFindField(
    value: String,
    onValueChange: (String) -> Unit,
    requestHostInput: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    BrowserAddressField(
        value,
        onValueChange,
        requestHostInput,
        { keyboard?.hide(); focusManager.clearFocus() },
        modifier,
        placeholder = "查找网页正文",
        keyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
        imeAction = ImeAction.Search
    )
}

/** Genuine Desktop Chrome User-Agent without Android '; wv' or 'Mobile' tokens so sites serve full desktop layouts. */
internal fun desktopAgent(agent: String): String {
    val chromeMatch = Regex("Chrome/[0-9.]+").find(agent)?.value ?: "Chrome/130.0.0.0"
    return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) $chromeMatch Safari/537.36"
}
