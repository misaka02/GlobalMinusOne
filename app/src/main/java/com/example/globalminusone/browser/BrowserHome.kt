package com.example.globalminusone.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.ui.AppIcons

internal val browserDefaultSites = listOf(
    BrowserLink("百度", "https://www.baidu.com"), BrowserLink("必应", "https://www.bing.com"),
    BrowserLink("GitHub", "https://github.com"), BrowserLink("哔哩哔哩", "https://www.bilibili.com"),
    BrowserLink("知乎", "https://www.zhihu.com"), BrowserLink("V2EX", "https://www.v2ex.com"),
    BrowserLink("维基百科", "https://zh.wikipedia.org"), BrowserLink("DuckDuckGo", "https://duckduckgo.com"))

/** All homepage assets are Compose text/shapes. No WebView, favicon or image request is needed. */
@Composable
internal fun BrowserHome(query: String, onQueryChange: (String) -> Unit, requestHostInput: () -> Unit,
    onSearch: () -> Unit, onEditingChanged: (Boolean) -> Unit, bookmarks: List<BrowserLink>,
    onOpen: (String) -> Unit, onBookmarks: () -> Unit, modifier: Modifier = Modifier,
    selectedEngine: BrowserSearchEngine = BrowserSearchEngine.BAIDU,
    onSelectEngine: (BrowserSearchEngine) -> Unit = {},
    nightMode: Boolean = false,
    onToggleNightMode: (() -> Unit)? = null,
    incognitoMode: Boolean = false,
    onToggleIncognito: (() -> Unit)? = null,
    privateMode: Boolean = false,
    privateAvailable: Boolean = false,
    onTogglePrivate: (() -> Unit)? = null) {
    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        val columns = if (maxWidth >= 220.dp) 4 else 2
        val showHomeLogo = maxHeight >= 380.dp
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
            var engineMenuExpanded by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BrowserAddressField(
                    query,
                    onQueryChange,
                    requestHostInput,
                    onSearch,
                    Modifier.weight(1f),
                    onEditingChanged,
                    placeholder = "搜索或输入网址",
                    leadingContent = {
                        Box {
                            Surface(
                                onClick = { engineMenuExpanded = true },
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.height(28.dp)
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        selectedEngine.label,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                    Icon(
                                        AppIcons.KeyboardArrowDown,
                                        contentDescription = "切换搜索引擎",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = engineMenuExpanded,
                                onDismissRequest = { engineMenuExpanded = false }
                            ) {
                                BrowserSearchEngine.entries.forEach { engine ->
                                    val active = engine == selectedEngine
                                    val textColor = if (active) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                engine.label,
                                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                                color = textColor
                                            )
                                        },
                                        onClick = {
                                            engineMenuExpanded = false
                                            onSelectEngine(engine)
                                            if (query.isNotBlank()) onSearch()
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
                IconButton(onClick = onSearch, modifier = Modifier.size(44.dp)) { Icon(AppIcons.Search, "搜索", Modifier.size(22.dp)) }
            }
            if (onToggleNightMode != null || onToggleIncognito != null || onTogglePrivate != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.Start),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onTogglePrivate != null) {
                        val privateBg = if (privateMode) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                        val privateFg = if (privateMode) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Surface(
                            onClick = onTogglePrivate,
                            enabled = privateAvailable,
                            shape = RoundedCornerShape(14.dp),
                            color = privateBg,
                            modifier = Modifier.heightIn(min = 32.dp)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(AppIcons.Shield, null, Modifier.size(13.dp), tint = privateFg)
                                Text(
                                    text = if (privateMode) "退出无痕" else if (privateAvailable) "独立无痕" else "无痕不可用",
                                    fontSize = 11.5.sp,
                                    fontWeight = if (privateMode) FontWeight.SemiBold else FontWeight.Normal,
                                    color = privateFg
                                )
                            }
                        }
                    }
                    if (onToggleIncognito != null) {
                        val pauseFg = if (incognitoMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        Surface(
                            onClick = onToggleIncognito,
                            shape = RoundedCornerShape(14.dp),
                            color = if (incognitoMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.heightIn(min = 32.dp)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(AppIcons.History, null, Modifier.size(13.dp), tint = pauseFg)
                                Text(
                                    text = if (incognitoMode) "历史:暂停" else "暂停历史",
                                    fontSize = 11.5.sp,
                                    fontWeight = if (incognitoMode) FontWeight.SemiBold else FontWeight.Normal,
                                    color = pauseFg
                                )
                            }
                        }
                    }
                    if (onToggleNightMode != null) {
                        val nightFg = if (nightMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        Surface(
                            onClick = onToggleNightMode,
                            shape = RoundedCornerShape(14.dp),
                            color = if (nightMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.heightIn(min = 32.dp)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = AppIcons.DarkMode,
                                    contentDescription = "切换夜间模式",
                                    modifier = Modifier.size(13.dp),
                                    tint = nightFg
                                )
                                Text(
                                    text = if (nightMode) "夜间:开" else "夜间",
                                    fontSize = 11.5.sp,
                                    fontWeight = if (nightMode) FontWeight.SemiBold else FontWeight.Normal,
                                    color = nightFg
                                )
                            }
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
            if (showHomeLogo) {
                Box(Modifier.align(Alignment.CenterHorizontally).size(48.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .5f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(AppIcons.Explore, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.height(8.dp))
            }
            HomeLinkGrid(browserDefaultSites, columns, onOpen)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("收藏夹", Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onBookmarks, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("管理全部", fontSize = 12.sp) }
            }
            val links = browserHomeBookmarks(bookmarks)
            if (links.isEmpty()) Text("收藏的网页会以极简磁贴显示在这里", Modifier.padding(vertical = 6.dp), fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            else HomeLinkGrid(links, columns, onOpen)
            }
        }
    }
}

@Composable
private fun HomeLinkGrid(links: List<BrowserLink>, columns: Int, onOpen: (String) -> Unit) {
    val colors = listOf(Color(0xFF4C81C5), Color(0xFF4C9E88), Color(0xFFC38B55), Color(0xFF8E73B8))
    links.chunked(columns).forEachIndexed { rowIndex, row ->
        Row(Modifier.fillMaxWidth()) {
            row.forEachIndexed { column, link ->
                val color = colors[(rowIndex * columns + column) % colors.size]
                TextButton(onClick = { onOpen(link.url) }, modifier = Modifier.weight(1f).heightIn(min = 72.dp), contentPadding = PaddingValues(4.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(34.dp).background(color.copy(alpha = .13f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                            Text(browserHomeInitial(link.title), color = color, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(link.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
            }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

internal fun browserHomeBookmarks(records: List<BrowserLink>): List<BrowserLink> = records.asSequence()
    .filter { it.url != "about:blank" && resolveBrowserDestination(it.url) == it.url }
    .distinctBy { it.url }.take(8).map { it.copy(title = it.title.ifBlank { it.url }.take(80)) }.toList()

internal fun browserHomeInitial(title: String): String = title.trim().takeIf { it.isNotEmpty() }
    ?.let { String(Character.toChars(it.codePointAt(0))) } ?: "网"
