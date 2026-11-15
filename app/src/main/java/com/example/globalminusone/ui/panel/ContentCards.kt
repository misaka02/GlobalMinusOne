package com.example.globalminusone.ui.panel

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
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.TEXT_LABEL_FONT_SIZE_SP
import com.example.globalminusone.data.TEXT_LABEL_LINE_HEIGHT_SP
import com.example.globalminusone.data.DefaultPresets
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.system.SystemController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun HitokotoCardComponent(
    state: GlobalMinusOneState,
    repository: MinusOneRepository
) {
    val cardColors = LocalCardColors.current
    var calendar by remember { mutableStateOf(Calendar.getInstance()) }
    val panelExpanded by repository.isPanelExpanded.collectAsState()

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
                    val currentMotto = state.appearanceConfig.customMotto
                    val next = pool.filter { it != currentMotto }.randomOrNull() ?: pool.random()
                    repository.updateAppearanceConfig { it.copy(customMotto = next) }
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
                text = state.appearanceConfig.customMotto,
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
    val cardColors = LocalCardColors.current
    val isEditMode = LocalIsEditMode.current
    var noteText by remember(item.id, item.textContent) { mutableStateOf(item.textContent) }

    Box(
        modifier = Modifier.fillMaxSize().padding(8.dp)
            .ownPanelScrollGestures(!isEditMode)
            .verticalScroll(rememberScrollState(), enabled = !isEditMode)
    ) {
        BasicTextField(
            value = noteText,
            onValueChange = {
                noteText = it
                repository.updateComponentText(pageIndex, item.id, it)
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
                repository.updateComponentText(pageIndex, item.id, it)
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
    var clearRequest by remember { mutableStateOf<Set<String>?>(null) }
    clearRequest?.let { expected -> ClipboardClearConfirmation(expected, repository) { clearRequest = null } }
    val cardColors = LocalCardColors.current

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
                        systemController.readCurrentClipboardText()?.let {
                            repository.recordClipboardText(it)
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Text(text = "抓取", fontSize = 10.sp)
                }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(
                    onClick = { clearRequest = repository.state.value.clipboardHistory.filterNot { it.pinned }.map { it.id }.toSet() },
                    enabled = state.clipboardHistory.any { !it.pinned },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Text(text = "清空", color = cardColors.textSecondary, fontSize = 10.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (state.clipboardHistory.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = "暂无剪贴板历史",
                    color = cardColors.textPlaceholder,
                    fontSize = 11.sp
                )
            }
        } else {
            LazyColumn(
                flingBehavior = rememberPanelFlingBehavior(),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(state.clipboardHistory.take(5), key = { it.id }) { record ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { systemController.copyToClipboard("剪贴板", record.text) },
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
                                onClick = { repository.togglePinClipboard(record.id) },
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
                                onClick = { repository.deleteClipboardRecord(record.id) },
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
