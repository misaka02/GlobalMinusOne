package com.example.globalminusone.ui.panel.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import com.example.globalminusone.ui.AppIcons as Icons
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.ClipboardRecord
import kotlinx.coroutines.flow.StateFlow
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.policy.TEXT_LABEL_FONT_SIZE_SP
import com.example.globalminusone.data.policy.TEXT_LABEL_LINE_HEIGHT_SP
import com.example.globalminusone.data.policy.DefaultPresets
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.system.SystemController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import com.example.globalminusone.ui.panel.editor.ClipboardClearConfirmationContent
import com.example.globalminusone.ui.panel.gesture.PanelGestureAxis
import com.example.globalminusone.ui.panel.gesture.ownPanelScrollGestures
import com.example.globalminusone.ui.panel.gesture.rememberPanelFlingBehavior

@Composable
fun HitokotoCardComponent(
    state: GlobalMinusOneState,
    repository: MinusOneRepository
) {
    HitokotoCardContent(state.appearanceConfig.customMotto, { repository.isPanelExpanded }) { next ->
        repository.updateAppearanceConfig { it.copy(customMotto = next) }
    }
}

@Composable
internal fun HitokotoCardContent(customMotto: String, expandedState: () -> StateFlow<Boolean>, onMotto: (String) -> Unit) {
    val cardColors = LocalCardColors.current
    var calendar by remember { mutableStateOf(Calendar.getInstance()) }
    val panelExpanded by expandedState().collectAsState()

    LaunchedEffect(panelExpanded) {
        if (!panelExpanded) return@LaunchedEffect
        while (true) {
            calendar = Calendar.getInstance()
            // The UI displays HH:mm: tick at minute boundaries, and stop when hidden.
            delay(60_000L - System.currentTimeMillis().mod(60_000L))
        }
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("M月d日 EEEE", Locale.CHINESE) }
    val timeStr = remember(calendar) { timeFormat.format(calendar.time) }
    val dateStr = remember(calendar) { dateFormat.format(calendar.time) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = timeStr,
                color = cardColors.textPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.SansSerif,
                lineHeight = 30.sp
            )
            Text(
                text = dateStr,
                color = cardColors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    val pool = DefaultPresets.builtInMottos
                    val currentMotto = customMotto
                    val next = pool.filter { it != currentMotto }.randomOrNull() ?: pool.random()
                    onMotto(next)
                }
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.FormatQuote,
                contentDescription = null,
                tint = cardColors.accent,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = customMotto,
                color = cardColors.textSecondary,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
fun NoteCardComponent(
    item: ComponentItem,
    pageIndex: Int,
    repository: MinusOneRepository
) {
    NoteCardContent(item) { repository.updateComponentText(pageIndex, item.id, it) }
}

@Composable
internal fun NoteCardContent(item: ComponentItem, onTextChanged: (String) -> Unit) {
    val cardColors = LocalCardColors.current
    val isEditMode = LocalIsEditMode.current
    var noteText by remember(item.id, item.textContent) { mutableStateOf(item.textContent) }
    val noteScroll = rememberScrollState()
    val canScrollNote = !isEditMode && (noteScroll.canScrollForward || noteScroll.canScrollBackward)

    Box(
        modifier = Modifier.fillMaxSize().padding(8.dp)
            .ownPanelScrollGestures(
                enabled = canScrollNote,
                canScrollVertically = { dy -> if (dy < 0f) noteScroll.canScrollForward else noteScroll.canScrollBackward },
                axis = PanelGestureAxis.Vertical
            )
            .verticalScroll(noteScroll, enabled = canScrollNote, flingBehavior = rememberPanelFlingBehavior())
    ) {
        BasicTextField(
            value = noteText,
            onValueChange = {
                noteText = it
                onTextChanged(it)
            },
            enabled = !isEditMode,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(
                color = cardColors.textPrimary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            ),
            cursorBrush = SolidColor(cardColors.accent),
            decorationBox = { inner ->
                if (noteText.isEmpty()) {
                    Text(
                        text = "点击可输入便签...",
                        color = cardColors.textPlaceholder,
                        fontSize = 12.sp
                    )
                }
                inner()
            }
        )
    }
}

@Composable
fun TextLabelComponent(
    item: ComponentItem,
    pageIndex: Int,
    repository: MinusOneRepository
) {
    TextLabelCardContent(item) { repository.updateComponentText(pageIndex, item.id, it) }
}

@Composable
internal fun TextLabelCardContent(item: ComponentItem, onTextChanged: (String) -> Unit) {
    val cardColors = LocalCardColors.current
    val isEditMode = LocalIsEditMode.current
    var textContent by remember(item.id, item.textContent) { mutableStateOf(item.textContent) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = textContent,
            onValueChange = {
                textContent = it
                onTextChanged(it)
            },
            enabled = !isEditMode,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(
                color = cardColors.textPrimary,
                fontSize = TEXT_LABEL_FONT_SIZE_SP.sp,
                lineHeight = TEXT_LABEL_LINE_HEIGHT_SP.sp,
                fontWeight = FontWeight.Medium
            ),
            cursorBrush = SolidColor(cardColors.accent),
            decorationBox = { inner ->
                if (textContent.isEmpty()) {
                    Text(
                        text = "点击编辑文字...",
                        color = cardColors.textPlaceholder,
                        fontSize = TEXT_LABEL_FONT_SIZE_SP.sp,
                        lineHeight = TEXT_LABEL_LINE_HEIGHT_SP.sp
                    )
                }
                inner()
            }
        )
    }
}

@Composable
fun ClipboardCardComponent(
    state: GlobalMinusOneState,
    repository: MinusOneRepository,
    systemController: SystemController
) {
    ClipboardCardContent(state.clipboardHistory, rememberClipboardHistoryActions(repository), rememberClipboardTextAccess(systemController))
}

@Composable
internal fun ClipboardCardContent(records: List<ClipboardRecord>, history: ClipboardHistoryActions, clipboard: ClipboardTextAccess) {
    var clearRequest by remember { mutableStateOf<Set<String>?>(null) }
    clearRequest?.let { expected -> ClipboardClearConfirmationContent(expected, history) { clearRequest = null } }
    val cardColors = LocalCardColors.current
    val isEditMode = LocalIsEditMode.current
    val listState = rememberLazyListState()
    val canScrollList = !isEditMode && (listState.canScrollForward || listState.canScrollBackward)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "剪贴板",
                color = cardColors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Row {
                FilledTonalButton(
                    onClick = {
                        recordCurrentClipboard(history, clipboard)
                    },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Text(text = "抓取", fontSize = 10.sp)
                }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(
                    onClick = { clearRequest = history.currentRecords.filterNot { it.pinned }.map { it.id }.toSet() },
                    enabled = records.any { !it.pinned },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Text(text = "清空", color = cardColors.textSecondary, fontSize = 10.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (records.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = "暂无剪贴板历史",
                    color = cardColors.textPlaceholder,
                    fontSize = 11.sp
                )
            }
        } else {
            LazyColumn(
                state = listState,
                userScrollEnabled = canScrollList,
                flingBehavior = rememberPanelFlingBehavior(),
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .ownPanelScrollGestures(
                        enabled = canScrollList,
                        canScrollVertically = { dy -> if (dy < 0f) listState.canScrollForward else listState.canScrollBackward },
                        axis = PanelGestureAxis.Vertical
                    ),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(records.take(5), key = { it.id }) { record ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { clipboard.copyToClipboard("剪贴板", record.text) },
                        shape = RoundedCornerShape(6.dp),
                        color = if (record.pinned) cardColors.accent.copy(alpha = 0.15f) else cardColors.iconBackground
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = record.text,
                                color = cardColors.textPrimary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { history.togglePinClipboard(record.id) },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PushPin,
                                    contentDescription = "置顶",
                                    tint = if (record.pinned) cardColors.accent else cardColors.textSecondary,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            IconButton(
                                onClick = { history.deleteClipboardRecord(record.id) },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "删除此条记录",
                                    tint = cardColors.textSecondary.copy(alpha = 0.75f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun NotificationCountContent(context: android.content.Context, expandedState: () -> StateFlow<Boolean>) {
    val statistics = remember(context) { com.example.globalminusone.data.ActivityStatisticsStore.get(context) }
    val counts by statistics.counts.collectAsState()
    val statsVisible by expandedState().collectAsState()
    LaunchedEffect(statistics, statsVisible) {
        if (!statsVisible) return@LaunchedEffect
        while (true) {
            statistics.refresh()
            kotlinx.coroutines.delay(30_000L)
        }
    }
    StatCountComponent("${counts.notifications}", "今日通知·已选应用", Icons.Rounded.Notifications)
}

@Composable
internal fun UnlockCountContent(context: android.content.Context, expandedState: () -> StateFlow<Boolean>) {
    val statistics = remember(context) { com.example.globalminusone.data.ActivityStatisticsStore.get(context) }
    val counts by statistics.counts.collectAsState()
    val statsVisible by expandedState().collectAsState()
    LaunchedEffect(statistics, statsVisible) {
        if (!statsVisible) return@LaunchedEffect
        while (true) {
            statistics.refresh()
            kotlinx.coroutines.delay(30_000L)
        }
    }
    StatCountComponent("${counts.unlocks}", "今日解锁·服务期间", Icons.Rounded.Lock)
}
