package com.example.globalminusone.ui.panel

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.globalminusone.data.*
import kotlinx.coroutines.flow.collect
import kotlin.math.roundToInt

/** Editing mounts every card once. A drag cannot disappear when a lazy row leaves the viewport. */
@Composable
internal fun EditableLinearPage(page: PageConfig, pageIndex: Int, state: GlobalMinusOneState,
    repository: MinusOneRepository, onProperties: (ComponentItem) -> Unit, viewportAnchor: LinearViewportAnchor?,
    onViewportAnchorChanged: (LinearViewportAnchor) -> Unit, content: @Composable (ComponentItem) -> Unit) {
    val palette = LocalPanelPalette.current
    val density = LocalDensity.current
    val ownership = LocalPanelGestureOwnership.current
    val expansion = compactCurtainFractions()
    val memory = LocalPageViewport.current ?: remember(page.id) { PageViewportMemory() }
    val recordingRevision = remember(page.id) { memory.restoreRevision }
    val pageActive = LocalPageViewportActive.current
    val scroll = remember(page.id) { androidx.compose.foundation.ScrollState(memory.editScrollPx) }
    val scrollTarget = rememberPanelScrollTarget(scroll)
    val entryAnchor = remember(page.id) { viewportAnchor }
    var viewportHeightPx by remember(page.id) { mutableIntStateOf(0) }
    var restoredAnchor by remember(page.id) { mutableStateOf(false) }
    val viewport = rememberEditDragViewport(scrollBy = { scroll.scrollBy(it) })
    var moving by remember(page.id) { mutableStateOf<String?>(null) }
    var previewOrder by remember(page.id) { mutableStateOf<List<String>?>(null) }
    var reorderSession by remember(page.id) { mutableStateOf<LinearReorderSession?>(null) }
    var origin by remember { mutableFloatStateOf(0f) }
    var pointerOrigin by remember { mutableFloatStateOf(0f) }
    var scrollOrigin by remember { mutableFloatStateOf(0f) }
    val selected = page.components.firstOrNull { it.id == state.selectedComponentId }
    var sizePreview by remember(selected?.id) { mutableStateOf<Pair<Int, Int>?>(null) }
    var measuredSelectedRows by remember(selected?.id) { mutableIntStateOf(selected?.h ?: 1) }
    var numberField by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var deleting by remember(page.id) { mutableStateOf<ComponentItem?>(null) }
    fun saveSize(w: Int, h: Int, heightChanged: Boolean = true) {
        selected?.let { item -> repository.updateComponent(pageIndex, item.id) {
            commitCardGeometry(it, it.x, it.y, w, h, resizeWidth = true, resizeHeight = heightChanged)
        } }
        sizePreview = null
    }
    val columns = state.advancedConfig.gridColumns.coerceIn(8, 64)
    if (selected != null && numberField != null) {
        val width = numberField == "width"
        ExactNumberInput(if (width) "卡片宽度" else "卡片高度", if (width) selected.w else if (selected.autoHeight) measuredSelectedRows else selected.h,
            1, if (width) columns else 120, { value ->
                saveSize(if (width) value else selected.w, if (width) selected.h else value, !width)
            }) { numberField = null }
    }
    deleting?.let { item -> PanelAlertDialog(onDismissRequest = { deleting = null },
        title = { Text("删除卡片？") }, text = { Text("将删除“${item.title}”${if (item.type.isContainerType()) "及其中的全部子卡片" else ""}。此操作无法恢复。") },
        confirmButton = { TextButton(onClick = {
            val current = repository.state.value
            val targetPage = current.pages.indexOfFirst { it.id == page.id }
            if (!current.advancedConfig.layoutLocked && targetPage >= 0 && current.pages[targetPage].components.findComponent(item.id) != null)
                repository.removeComponent(targetPage, item.id)
            deleting = null
        }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }) }
    DisposableEffect(page.id) { onDispose { viewport.end() } }
    LaunchedEffect(pageActive) {
        if (!pageActive) {
            moving = null; previewOrder = null; reorderSession = null; viewport.end()
        }
    }
    Box(Modifier.fillMaxSize()) {
        if (selected != null) Row(
            Modifier
                .align(Alignment.BottomCenter)
                .zIndex(10f)
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .fillMaxWidth()
                .background(palette.cardBackground.copy(alpha = 0.96f), RoundedCornerShape(12.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { numberField = "width" }, enabled = selected != null, modifier = Modifier.weight(1f).heightIn(min = 36.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text("宽 ${sizePreview?.first ?: selected.w}", maxLines = 1, fontSize = 11.sp)
            }
            TextButton(onClick = { numberField = "height" }, enabled = selected != null, modifier = Modifier.weight(1f).heightIn(min = 36.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text("高 ${sizePreview?.second ?: if (selected.autoHeight) measuredSelectedRows else selected.h}", maxLines = 1, fontSize = 11.sp)
            }
            TextButton(onClick = { onProperties(selected) }, modifier = Modifier.heightIn(min = 36.dp), contentPadding = PaddingValues(horizontal = 6.dp)) {
                Text("属性", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            TextButton(
                onClick = { repository.moveComponentOrderInLinearPage(pageIndex, selected.id, true) },
                modifier = Modifier.heightIn(min = 36.dp),
                contentPadding = PaddingValues(horizontal = 5.dp)
            ) {
                Text("↑", fontSize = 12.sp, maxLines = 1)
            }
            TextButton(
                onClick = { repository.moveComponentOrderInLinearPage(pageIndex, selected.id, false) },
                modifier = Modifier.heightIn(min = 36.dp),
                contentPadding = PaddingValues(horizontal = 5.dp)
            ) {
                Text("↓", fontSize = 12.sp, maxLines = 1)
            }
            Box {
                TextButton(onClick = { menu = true }, enabled = selected != null, modifier = Modifier.heightIn(min = 36.dp), contentPadding = PaddingValues(horizontal = 5.dp)) {
                    Text("操作 ▾", fontSize = 11.sp, maxLines = 1)
                }
                DropdownMenu(menu, { menu = false }) {
                    com.example.globalminusone.service.Android12WindowKeyGuard()
                     selected?.let { item ->
                    DropdownMenuItem({ Text("卡片属性") }, { menu = false; onProperties(item) })
                    DropdownMenuItem({ Text("上移") }, { menu = false; repository.moveComponentOrderInLinearPage(pageIndex, item.id, true) })
                    DropdownMenuItem({ Text("下移") }, { menu = false; repository.moveComponentOrderInLinearPage(pageIndex, item.id, false) })
                    DropdownMenuItem({ Text("删除…", color = MaterialTheme.colorScheme.error) }, { menu = false; deleting = item })
                } }
            }
            TextButton(
                onClick = { repository.selectComponent(null) },
                modifier = Modifier.heightIn(min = 36.dp),
                contentPadding = PaddingValues(horizontal = 5.dp)
            ) {
                Text("✕", fontSize = 11.sp, maxLines = 1)
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val padding = with(density) { 12.dp.toPx() }
            val gap = with(density) {
                state.themeConfig.cardSpacingDp.coerceIn(
                    com.example.globalminusone.data.MIN_CARD_SPACING_DP,
                    com.example.globalminusone.data.MAX_CARD_SPACING_DP
                ).dp.toPx()
            }
            val inset = gap.coerceAtLeast(0f)
            val availableWidth = (constraints.maxWidth - padding * 2).coerceAtLeast(1f)
            val sx = availableWidth / columns
            val sy = if (state.advancedConfig.heightUnitDp > 0) with(density) { state.advancedConfig.heightUnitDp.dp.toPx() } else sx
            val order = previewOrder?.takeIf { it.toSet() == page.components.map { c -> c.id }.toSet() } ?: page.components.map { it.id }
            val heights = page.components.associate { item ->
                val resized = if (selected?.id == item.id) sizePreview?.let { item.copy(w = it.first, h = it.second, autoHeight = false) } ?: item else item
                item.id to with(density) { componentDisplayHeightDp(resized, (resized.w * sx).toDp().value, columns,
                    state.advancedConfig.heightUnitDp, density.fontScale, compactExpansion = expansion).dp.toPx() }
            }
            SideEffect { selected?.let { measuredSelectedRows = kotlin.math.ceil(heights.getValue(it.id) / sy).toInt().coerceIn(1, 120) } }
            val starts = mutableMapOf<String, Float>()
            var end = inset
            order.forEach { id -> starts[id] = end; end += ((heights[id] ?: sy) + gap).coerceAtLeast(16f) }
            val anchorSpans = order.mapNotNull { id ->
                // Normalize away the leading content inset: LazyList offsets are measured from
                // after contentPadding, while this canvas starts its first card after `inset`.
                starts[id]?.let { start -> LinearCardSpan(id, (start - inset).roundToInt(), (heights[id] ?: sy).roundToInt().coerceAtLeast(1)) }
            }
            SideEffect {
                if (!restoredAnchor) {
                    entryAnchor?.let { linearScrollForAnchor(it, anchorSpans)?.let { target -> scroll.dispatchRawDelta((target - scroll.value).toFloat()) } }
                    restoredAnchor = true
                }
            }
            LaunchedEffect(page.id, anchorSpans, pageActive, restoredAnchor) {
                if (!pageActive || !restoredAnchor) return@LaunchedEffect
                snapshotFlow { scroll.value }.collect { scrollPx ->
                    val anchor = anchorAtLinearScroll(anchorSpans, scrollPx)
                    if (memory.recordEditingViewport(scrollPx, anchor, recordingRevision)) anchor?.let(onViewportAnchorChanged)
                }
            }
            val latestStarts by rememberUpdatedState(starts.toMap())
            val latestHeights by rememberUpdatedState(heights)
            val latestOrder by rememberUpdatedState(order)
            LaunchedEffect(moving) {
                val id = moving ?: return@LaunchedEffect
                snapshotFlow { viewport.pointer to viewport.consumedScroll }.collect { (point, _) ->
                    point ?: return@collect
                    val y = point.y - viewport.bounds.top + scroll.value
                    val target = latestOrder.filter { it != id }.firstOrNull { other ->
                        val top = latestStarts[other] ?: 0f
                        y in top..(top + (latestHeights[other] ?: sy))
                    } ?: return@collect
                    val from = latestOrder.indexOf(id); val to = latestOrder.indexOf(target)
                    val middle = latestStarts.getValue(target) + latestHeights.getValue(target) / 2
                    if ((to > from && y > middle) || (to < from && y < middle)) previewOrder = moveLinearPreview(latestOrder, id, target)
                }
            }
            val dragY = origin + ((viewport.pointer?.y ?: pointerOrigin) - pointerOrigin) + viewport.consumedScroll - scrollOrigin
            val toolsInset = if (selected != null) with(density) { 72.dp.toPx() } else 0f
            val extent = maxOf(end + toolsInset, if (moving != null) dragY + (heights[moving] ?: sy) + gap + toolsInset else 0f,
                scroll.value + constraints.maxHeight.toFloat())
            Box(Modifier.fillMaxSize().onGloballyPositioned {
                viewport.bounds = it.boundsInRoot()
                viewportHeightPx = it.size.height
            }
                .verticalScroll(scroll, enabled = !ownership.exclusiveVerticalActive, flingBehavior = rememberPanelFlingBehavior())) {
                Box(Modifier.fillMaxWidth().height(with(density) { extent.toDp() })) {
                    page.components.forEach { item -> key(item.id) {
                        var dragTargetCoordinates by remember(item.id) { mutableStateOf<LayoutCoordinates?>(null) }
                        val dragging = moving == item.id
                        val targetY = if (dragging) dragY.coerceAtLeast(0f) else starts.getValue(item.id)
                        val animMs = state.themeConfig.animationDurationMs
                        val position by animateFloatAsState(
                            targetY,
                            if (dragging || animMs <= 0) snap()
                            else if (animMs != 240) tween(animMs.coerceIn(60, 800))
                            else spring(dampingRatio = .95f, stiffness = 1100f),
                            label = "linear-position"
                        )
                        val w = (if (selected?.id == item.id) sizePreview?.first ?: item.w else item.w).coerceIn(1, columns)
                        val h = heights.getValue(item.id)
                        if (dragging) Box(Modifier.offset { IntOffset(padding.roundToInt(), starts.getValue(item.id).roundToInt()) }
                            .size(with(density) { (w * sx).toDp() }, with(density) { h.toDp() })
                            .border(1.dp, palette.accent.copy(alpha = .6f), RoundedCornerShape(10.dp)))
                        Box(Modifier.offset { IntOffset(padding.roundToInt(), (if (dragging) targetY else position).roundToInt()) }
                            .size(with(density) { (w * sx).toDp() }, with(density) { h.toDp() })
                            .zIndex(if (dragging) 2f else 0f)
                            .then(if (state.selectedComponentId == item.id) Modifier.border(2.dp, palette.accent, RoundedCornerShape(10.dp)) else Modifier)) {
                            CompositionLocalProvider(LocalPanelScrollTarget provides scrollTarget) { content(item) }
                            Box(cardEditDragTarget(item)
                                .onGloballyPositioned { dragTargetCoordinates = it }
                                .pointerInput(page.id, item.id) {
                                    detectStationaryDragGestures(ownership, onTap = { repository.selectComponent(item.id) }, onDragStart = start@{ point ->
                                        reorderSession = repository.beginLinearReorder(page.id)
                                        if (reorderSession == null) return@start
                                        origin = latestStarts.getValue(item.id)
                                        pointerOrigin = dragTargetCoordinates?.takeIf { it.isAttached }?.localToRoot(point)?.y
                                            ?: (viewport.bounds.top + origin - scroll.value + point.y)
                                        scrollOrigin = viewport.consumedScroll; previewOrder = latestOrder
                                        repository.selectComponent(item.id); moving = item.id; viewport.begin(Offset(0f, pointerOrigin))
                                    }, onDrag = { change, amount ->
                                        if (moving == item.id) { change.consume(); viewport.moveBy(amount) }
                                    }, onDragEnd = {
                                        reorderSession?.let { repository.commitLinearReorder(it, previewOrder) }
                                        moving = null; previewOrder = null; reorderSession = null; viewport.end()
                                    }, onDragCancel = {
                                        moving = null; previewOrder = null; reorderSession = null; viewport.end()
                                    }, positionInStableSpace = { point ->
                                        dragTargetCoordinates?.takeIf { it.isAttached }?.localToRoot(point) ?: point
                                    })
                                }) { ContainerDragHint(item) }
                            if (selected?.id == item.id && !dragging) LinearCardResizeGrip(item.copy(h = kotlin.math.ceil(h / sy).toInt().coerceIn(1, 120)), sx, sy, columns,
                                Modifier.align(Alignment.BottomEnd), onPreview = { sizePreview = it },
                                onCommit = { size, heightChanged -> saveSize(size.first, size.second, heightChanged) })
                        }
                    } }
                }
            }
        }
    }
}

@Composable
private fun LinearCardResizeGrip(item: ComponentItem, sx: Float, sy: Float, columns: Int, modifier: Modifier,
    onPreview: (Pair<Int, Int>?) -> Unit, onCommit: (Pair<Int, Int>, Boolean) -> Unit) {
    val current by rememberUpdatedState(item)
    val preview by rememberUpdatedState(onPreview)
    val commit by rememberUpdatedState(onCommit)
    val palette = LocalPanelPalette.current
    Box(modifier.size(48.dp)
        .semantics { contentDescription = "拖动调整卡片宽高" }
        .ownPanelScrollGestures(true, lockFromDown = true).pointerInput(item.id, sx, sy, columns) {
            var initial = 1 to 1; var delta = Offset.Zero; var final = initial
            detectDragGestures(onDragStart = { initial = current.w to current.h; final = initial; delta = Offset.Zero },
                onDragEnd = { commit(final, final.second != initial.second); preview(null) }, onDragCancel = { preview(null) }) { change, amount ->
                change.consume(); delta += amount
                final = resizedCardSize(initial.first, initial.second, delta.x, delta.y, sx, sy, columns)
                preview(final)
            }
        }, contentAlignment = Alignment.BottomEnd) {
        CardResizeGripMark(palette.accent, Modifier.padding(8.dp))
    }
}
