package com.example.globalminusone.ui.panel

import android.icu.text.AlphabeticIndex
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** One background index per loaded list; searches only filter it, never rescan packages. */
@Composable
internal fun <T> IndexedSelectionList(
    entries: List<T>, key: (T) -> String, label: (T) -> String,
    searchText: (T) -> String = label, queryOverride: String? = null, modifier: Modifier = Modifier,
    row: @Composable (T) -> Unit
) {
    var inputQuery by remember { mutableStateOf("") }
    val query = queryOverride ?: inputQuery
    val groups by produceState<List<AppLetterGroup<T>>>(emptyList(), entries) {
        value = withContext(Dispatchers.Default) {
            val index = AlphabeticIndex<Any>(Locale.SIMPLIFIED_CHINESE).addLabels(Locale.ENGLISH).buildImmutableIndex()
            groupAppsByLetter(entries, label) { index.getBucket(index.getBucketIndex(it))?.label.orEmpty() }
        }
    }
    val visible = remember(groups, query) { filterAppGroups(groups) { searchText(it).contains(query.trim(), true) } }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(query) { list.scrollToItem(0) }
    Column(modifier) {
        if (queryOverride == null) {
            OutlinedTextField(query, { inputQuery = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("搜索名称或包名") })
            Spacer(Modifier.height(8.dp))
        }
        if (visible.isEmpty()) Text(if (entries.isEmpty()) "暂无可选项" else "没有匹配项")
        Row(Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(Modifier.weight(1f).fillMaxHeight(), state = list, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                visible.forEach { group ->
                    item("section-${group.letter}") { Text(group.letter, color = MaterialTheme.colorScheme.primary) }
                    items(group.apps, key = key) { row(it) }
                }
            }
            Column(Modifier.width(32.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                visible.forEachIndexed { groupIndex, group ->
                    Box(Modifier.fillMaxWidth().weight(1f).clickable {
                        val target = visible.take(groupIndex).sumOf { 1 + it.apps.size }
                        scope.launch { list.scrollToItem(target) }
                    }, contentAlignment = Alignment.Center) { Text(group.letter, fontSize = 10.sp) }
                }
            }
        }
    }
}
