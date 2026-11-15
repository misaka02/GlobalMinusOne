package com.example.globalminusone.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BrowserAddressField(query, onQueryChange, requestHostInput, onSearch, Modifier.weight(1f), onEditingChanged,
                    placeholder = "使用 ${selectedEngine.label} 搜索或输入网址")
                IconButton(onClick = onSearch, modifier = Modifier.size(44.dp)) { Icon(AppIcons.Search, "搜索", Modifier.size(22.dp)) }
            }
            Spacer(Modifier.height(6.dp))
            if (onToggleNightMode != null || onToggleIncognito != null || onTogglePrivate != null) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onTogglePrivate != null) {
                        Surface(
                            onClick = onTogglePrivate,
                            enabled = privateAvailable,
                            shape = RoundedCornerShape(14.dp),
                            color = if (privateMode) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                if (privateMode) "退出无痕" else if (privateAvailable) "独立无痕" else "无痕不可用",
                                Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 11.sp
                            )
                        }
                    }
                    if (onToggleIncognito != null) {
                        Surface(
                            onClick = onToggleIncognito,
                            shape = RoundedCornerShape(14.dp),
                            color = if (incognitoMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = if (incognitoMode) "历史:暂停" else "暂停历史",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                fontWeight = if (incognitoMode) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (incognitoMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (onToggleNightMode != null) {
                        Surface(
                            onClick = onToggleNightMode,
                            shape = RoundedCornerShape(14.dp),
                            color = if (nightMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(
                                Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = AppIcons.DarkMode,
                                    contentDescription = "切换夜间模式",
                                    modifier = Modifier.size(13.dp),
                                    tint = if (nightMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (nightMode) "夜间:开" else "夜间",
                                    fontSize = 11.sp,
                                    fontWeight = if (nightMode) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (nightMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(BrowserSearchEngine.entries, key = { it.name }) { engine ->
                    val selected = engine == selectedEngine
                    Surface(
                        onClick = {
                            onSelectEngine(engine)
                            if (query.isNotBlank()) onSearch()
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Text(
                            text = engine.label,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
