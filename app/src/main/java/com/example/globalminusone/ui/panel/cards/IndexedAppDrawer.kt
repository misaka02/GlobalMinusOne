package com.example.globalminusone.ui.panel.cards

import android.icu.text.AlphabeticIndex
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.example.globalminusone.ui.AppIcons as Icons
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.policy.AppQuickSearchMatcher
import com.example.globalminusone.system.AppAndIntentLauncher
import com.example.globalminusone.system.InstalledAppInfo
import com.example.globalminusone.ui.PlatformPinyinProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import com.example.globalminusone.ui.panel.gesture.PanelGestureAxis
import com.example.globalminusone.ui.panel.gesture.ownPanelScrollGestures
import com.example.globalminusone.ui.panel.gesture.rememberPanelFlingBehavior

private object IndexedDrawerMemoryCache {
    @Volatile
    var generation: Int = -1
    @Volatile
    var baseGroups: List<AppLetterGroup<InstalledAppInfo>> = emptyList()
}

@Composable
internal fun IndexedAppDrawer(isEditMode: Boolean, onRequestClose: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalCardColors.current
    val listGeneration by AppAndIntentLauncher.appListGeneration.collectAsState()
    var indexedApps by remember {
        mutableStateOf(
            if (IndexedDrawerMemoryCache.generation == listGeneration) IndexedDrawerMemoryCache.baseGroups
            else emptyList()
        )
    }
    val environment = LocalAppMenuEnvironment.current
    val appearances = rememberAppAppearanceOverrides(environment?.actions)
    var displayIndex by remember(indexedApps, appearances) {
        mutableStateOf(
            if (indexedApps.isEmpty() || appearances.values.none { it.displayName.isNotBlank() }) indexedApps
            else {
                val index = AlphabeticIndex<Any>(Locale.SIMPLIFIED_CHINESE).addLabels(Locale.ENGLISH).buildImmutableIndex()
                val renamed = indexedApps.flatMap { it.apps }.map { app ->
                    appearances[app.packageName]?.displayName?.takeIf { it.isNotBlank() }?.let { app.copy(label = it) } ?: app
                }
                groupAppsByLetter(renamed, { it.label }, { index.getBucket(index.getBucketIndex(it))?.label.orEmpty() })
            }
        )
    }
    var filter by remember { mutableStateOf("") }
    val indexedSearchApps = remember(displayIndex) {
        AppQuickSearchMatcher.indexApps(
            apps = displayIndex.flatMap { it.apps },
            label = { it.label },
            packageName = { it.packageName },
            provider = PlatformPinyinProvider.instance
        )
    }
    val matchedPackages = remember(indexedSearchApps, filter) {
        val q = filter.trim()
        if (q.isEmpty()) null
        else {
            val clean = AppQuickSearchMatcher.cleanQuery(q)
            val allNumeric = clean.isNotEmpty() && clean.all { ch -> ch in '0'..'9' }
            val hits = AppQuickSearchMatcher.matchApps(
                indexed = indexedSearchApps,
                rawQuery = q,
                t9Mode = allNumeric,
                maxResults = indexedSearchApps.size.coerceAtLeast(1)
            )
            hits.mapTo(HashSet(hits.size * 2)) { it.packageName }
        }
    }
    val groups = remember(displayIndex, matchedPackages, filter) {
        if (matchedPackages == null) displayIndex
        else filterAppGroups(displayIndex) {
            it.packageName in matchedPackages ||
                it.label.contains(filter, true) ||
                it.packageName.contains(filter, true)
        }
    }
    var loading by remember {
        mutableStateOf(
            IndexedDrawerMemoryCache.generation != listGeneration || IndexedDrawerMemoryCache.baseGroups.isEmpty()
        )
    }
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var draggedLetter by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(context, listGeneration) {
        if (IndexedDrawerMemoryCache.generation == listGeneration && IndexedDrawerMemoryCache.baseGroups.isNotEmpty()) {
            indexedApps = IndexedDrawerMemoryCache.baseGroups
        } else {
            val apps = AppAndIntentLauncher.getInstalledApps(context)
            val computed = withContext(Dispatchers.Default) {
                // AlphabeticIndex is available since API 24; prepare once, never per keystroke.
                val index = AlphabeticIndex<Any>(Locale.SIMPLIFIED_CHINESE)
                    .addLabels(Locale.ENGLISH).buildImmutableIndex()
                groupAppsByLetter(apps, { it.label }, { index.getBucket(index.getBucketIndex(it))?.label.orEmpty() })
            }
            IndexedDrawerMemoryCache.generation = listGeneration
            IndexedDrawerMemoryCache.baseGroups = computed
            indexedApps = computed
        }
        loading = false
    }
    LaunchedEffect(filter) {
        if (filter.isNotEmpty() || grid.firstVisibleItemIndex > 0) {
            grid.scrollToItem(0)
        }
    }
    val offsets = remember(groups) { appGroupOffsets(groups.map { it.apps.size }) }
    val visibleLetter by remember(groups, offsets, grid) { derivedStateOf {
        val layout = grid.layoutInfo
        val group = visibleAppGroupIndex(offsets, layout.visibleItemsInfo,
            layout.viewportStartOffset, layout.viewportEndOffset, grid.firstVisibleItemIndex,
            { it.index }, { it.offset.y }, { it.size.height })
        group?.let { groups.getOrNull(it)?.letter }
    } }
    val currentLetter = draggedLetter ?: visibleLetter
    val railTickHeight = with(LocalDensity.current) { 18.sp.toPx() + 2.dp.toPx() }
    val canScrollDrawer = !isEditMode && (grid.canScrollForward || grid.canScrollBackward)
    Column(Modifier.heightIn(max = LocalConfiguration.current.screenHeightDp.dp).fillMaxSize().ownPanelScrollGestures(canScrollDrawer, canScrollVertically = { dy -> if (dy < 0f) grid.canScrollForward else grid.canScrollBackward }, axis = PanelGestureAxis.Vertical).padding(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.iconBackground, RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
            BasicTextField(
                value = filter, onValueChange = { filter = it }, enabled = !isEditMode, singleLine = true,
                textStyle = TextStyle(color = colors.textPrimary, fontSize = 12.sp),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.weight(1f),
                decorationBox = { inner -> Box { if (filter.isEmpty()) Text("搜索应用…", color = colors.textPlaceholder, fontSize = 12.sp); inner() } }
            )
            if (filter.isNotEmpty() && !isEditMode) {
                IconButton(onClick = { filter = "" }, modifier = Modifier.size(22.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "清空搜索", tint = colors.textSecondary, modifier = Modifier.size(14.dp))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        // Keep the selected group readable even when its tick is omitted on a short rail.
        Text("当前：${currentLetter ?: "—"}", color = colors.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        if (groups.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(if (loading) "正在加载应用…" else "没有匹配应用", color = colors.textSecondary, fontSize = 12.sp)
            }
        } else Box(Modifier.weight(1f)) {
            Row(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                flingBehavior = rememberPanelFlingBehavior(),
                columns = GridCells.Adaptive(48.dp), state = grid, userScrollEnabled = canScrollDrawer,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                groups.forEach { group ->
                    item(key = "heading:${group.letter}", span = { GridItemSpan(maxLineSpan) }) {
                        Text(group.letter, color = colors.titleColor, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth().background(colors.iconBackground).padding(4.dp))
                    }
                    items(group.apps, key = { it.packageName }) { app ->
                        AppIconComponent(packageName = app.packageName, title = app.label, enabled = !isEditMode,
                            drawerAppearanceOverrides = appearances,
                            onClick = { if (!isEditMode) { AppAndIntentLauncher.launchApp(context, app.packageName); onRequestClose() } })
                    }
                }
            }
            BoxWithConstraints(
                modifier = Modifier.width(30.dp).fillMaxHeight().background(colors.iconBackground, RoundedCornerShape(8.dp))
                    .pointerInput(groups, isEditMode, railTickHeight) {
                        if (!isEditMode) awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            down.consume()
                            // Prepare once per gesture; MOVE only interpolates these landmarks.
                            val ticks = appIndexRailTicks(groups.size, (size.height / railTickHeight).toInt())
                            fun select(y: Float) {
                                appIndexAtRailPosition(y, size.height.toFloat(), groups.size, ticks)?.let { index ->
                                    val letter = groups[index].letter
                                    if (draggedLetter != letter) {
                                        draggedLetter = letter
                                        scope.launch { grid.scrollToItem(offsets[index]) }
                                    }
                                }
                            }
                            select(down.position.y)
                            try {
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    event.changes.forEach { it.consume() }
                                    event.changes.firstOrNull { it.id == down.id }?.let { select(it.position.y) }
                                } while (event.changes.any { it.pressed })
                            } finally { draggedLetter = null }
                        }
                    }
            ) {
                // Respect font scaling, rather than shrinking 26 letters into the card.
                val capacity = (constraints.maxHeight / railTickHeight).toInt()
                if (capacity < 2 && groups.size > 1) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("${groups.first().letter}–${groups.last().letter}", color = colors.textSecondary,
                            fontSize = 10.sp, maxLines = 1)
                    }
                } else Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    appIndexRailTicks(groups.size, capacity.coerceAtLeast(1)).forEach { index ->
                        val letter = groups[index].letter
                        Box(Modifier.weight(1f).fillMaxWidth().semantics {
                            if (isEditMode) disabled() else onClick(label = "定位${letter}组") {
                                scope.launch { grid.scrollToItem(offsets[index]) }
                                true
                            }
                        }, contentAlignment = Alignment.Center) {
                            Text(letter, color = if (currentLetter == letter) colors.accent else colors.textSecondary,
                                fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
        }
            val activeDrag = draggedLetter
            if (activeDrag != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(56.dp),
                    shape = CircleShape,
                    color = colors.accent.copy(alpha = 0.92f),
                    shadowElevation = 6.dp
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = activeDrag,
                            color = androidx.compose.ui.graphics.Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
