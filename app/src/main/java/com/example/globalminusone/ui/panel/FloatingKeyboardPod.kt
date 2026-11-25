package com.example.globalminusone.ui.panel

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.KeyboardCardColorTokens
import com.example.globalminusone.data.KeyboardCardLayout
import com.example.globalminusone.data.KeyboardSideErgonomicsSpec
import com.example.globalminusone.data.TriggerSide
import com.example.globalminusone.data.resolveEffectiveKeyTap
import com.example.globalminusone.system.AppAndIntentLauncher
import com.example.globalminusone.ui.AppIcons as Icons

internal val QWERTY_NUMBER_ROW = listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '0')
internal val QWERTY_ROW_1 = listOf('Q', 'W', 'E', 'R', 'T', 'Y', 'U', 'I', 'O', 'P')
internal val QWERTY_ROW_2 = listOf('A', 'S', 'D', 'F', 'G', 'H', 'J', 'K', 'L')
internal val QWERTY_ROW_3 = listOf('Z', 'X', 'C', 'V', 'B', 'N', 'M')
internal val QWERTY_SEARCH_CHARS: List<Char> = ('a'..'z').toList() + ('0'..'9').toList()
internal val T9_KEYPAD_ROWS: List<List<Pair<Char, String>>> = listOf(
    listOf('1' to "1", '2' to "ABC", '3' to "DEF"),
    listOf('4' to "GHI", '5' to "JKL", '6' to "MNO"),
    listOf('7' to "PQRS", '8' to "TUV", '9' to "WXYZ")
)
internal val T9_SEARCH_CHARS: List<Char> = ('0'..'9').toList()

@Composable
internal fun SingleHandFloatingKeyboardPod(
    context: Context,
    overlay: KeyboardCandidateOverlayState,
    podWidthDp: Float,
    podMaxHeightDp: Float,
    keyHeightDp: Float,
    fixedHeightDp: Float? = null
) {
    val isT9 = overlay.activeLayout == KeyboardCardLayout.T9
    val ergonomics = overlay.ergonomics
    val stripHeightDp = if (overlay.showAppLabels) 46f else 36f
    val (effectiveKeyHeightDp, maxKeyHeightCapDp) = if (fixedHeightDp != null) {
        val keypadAvail = (fixedHeightDp - (56f + stripHeightDp)).coerceAtLeast(96f)
        val rows = if (isT9) 4f else if (overlay.showNumberRow) 3.85f else 3f
        val gaps = if (isT9 || overlay.showNumberRow) 9f else 6f
        val computed = ((keypadAvail - gaps) / rows).coerceIn(22f, 64f)
        computed to 64f
    } else {
        keyHeightDp to 42f
    }

    Surface(
        shape = RoundedCornerShape(overlay.cornerRadiusDp.coerceIn(0, 36).dp),
        color = overlay.surfaceColor,
        border = BorderStroke(1.dp, overlay.accentColor.copy(alpha = 0.42f)),
        shadowElevation = 14.dp,
        modifier = Modifier
            .width(podWidthDp.dp)
            .then(
                if (fixedHeightDp != null) Modifier.height(fixedHeightDp.dp)
                else Modifier.heightIn(min = 200.dp, max = podMaxHeightDp.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fixedHeightDp != null) Modifier.fillMaxHeight() else Modifier)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PodKeyboardHeaderRow(overlay = overlay, isT9 = isT9, ergonomics = ergonomics)
            KeyboardCandidateStrip(
                context = context,
                candidates = overlay.candidates,
                query = overlay.query,
                showAppLabels = overlay.showAppLabels,
                isLoadingApps = overlay.isLoadingApps,
                kbdColors = overlay.kbdColors,
                accentColor = overlay.accentColor,
                activeSide = overlay.activeSide,
                enabled = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(stripHeightDp.dp),
                onLaunchApp = overlay.onLaunchApp
            )
            if (fixedHeightDp != null) {
                Spacer(modifier = Modifier.weight(1f))
            }
            KeyboardLauncherKeypadGrid(
                isT9 = isT9,
                showNumberRow = overlay.showNumberRow,
                keyHeightDp = effectiveKeyHeightDp,
                maxKeyHeightDp = maxKeyHeightCapDp,
                compactKeys = effectiveKeyHeightDp < 30f,
                interactive = true,
                queryEmpty = overlay.query.isEmpty(),
                hasClearableState = overlay.query.isNotEmpty(),
                reachableKeys = overlay.reachableKeys,
                activeSide = overlay.activeSide,
                ergonomics = ergonomics,
                kbdColors = overlay.kbdColors,
                onAppendKey = overlay.onAppendKey,
                onBackspace = overlay.onBackspace,
                onClearQuery = overlay.onClearQuery
            )
        }
    }
}

@Composable
private fun PodKeyboardHeaderRow(
    overlay: KeyboardCandidateOverlayState,
    isT9: Boolean,
    ergonomics: KeyboardSideErgonomicsSpec
) {
    @Composable
    fun PodLayoutChip() {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(overlay.accentColor.copy(alpha = 0.16f))
                .border(0.6.dp, overlay.accentColor.copy(alpha = 0.38f), RoundedCornerShape(6.dp))
                .clickable { overlay.onToggleLayout() }
                .padding(horizontal = 7.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isT9) "全键" else "9键",
                color = overlay.accentColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    @Composable
    fun PodCloseChip() {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(overlay.kbdColors.actionKeyBg))
                .border(0.6.dp, Color(overlay.kbdColors.actionKeyBorder), RoundedCornerShape(6.dp))
                .clickable { overlay.onDismissOverlay() }
                .padding(horizontal = 7.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "收起",
                color = Color(overlay.kbdColors.subText),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(overlay.kbdColors.queryBarBg))
            .border(0.6.dp, Color(overlay.kbdColors.queryBarBorder), RoundedCornerShape(8.dp))
            .padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (ergonomics.headerControlsOnLeft) {
            PodCloseChip()
            PodLayoutChip()
        } else {
            Icon(
                imageVector = Icons.Rounded.Keyboard,
                contentDescription = null,
                tint = overlay.accentColor,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = if (overlay.query.isEmpty()) {
                "单手快启 · ${overlay.candidates.size}个"
            } else {
                "${overlay.query.uppercase()} (${overlay.candidates.size})"
            },
            color = if (overlay.query.isEmpty()) Color(overlay.kbdColors.subText) else Color(overlay.kbdColors.keyText),
            fontSize = if (overlay.query.isEmpty()) 11.sp else 12.sp,
            fontWeight = if (overlay.query.isEmpty()) FontWeight.Medium else FontWeight.Bold,
            fontFamily = if (overlay.query.isEmpty()) FontFamily.Default else FontFamily.Monospace,
            textAlign = if (ergonomics.headerControlsOnLeft) TextAlign.End else TextAlign.Start,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (overlay.query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(overlay.kbdColors.actionKeyBg))
                    .border(0.6.dp, Color(overlay.kbdColors.actionKeyBorder), RoundedCornerShape(6.dp))
                    .clickable { overlay.onClearQuery() }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "清空",
                    color = Color(overlay.kbdColors.keyText),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        if (!ergonomics.headerControlsOnLeft) {
            PodLayoutChip()
            PodCloseChip()
        }
    }
}

@Composable
internal fun KeyboardCandidateStrip(
    context: Context,
    candidates: List<KeyboardCandidateEntry>,
    query: String,
    showAppLabels: Boolean,
    isLoadingApps: Boolean,
    kbdColors: KeyboardCardColorTokens,
    accentColor: Color,
    activeSide: TriggerSide,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onLaunchApp: (KeyboardCandidateEntry) -> Unit
) {
    if (candidates.isEmpty()) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(kbdColors.emptyStripBg))
                .border(0.6.dp, Color(kbdColors.chipBorder), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isLoadingApps) "正在加载应用..." else "无匹配应用，可退格调整",
                color = Color(kbdColors.subText),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        return
    }

    val thumbFirstRight = activeSide == TriggerSide.RIGHT
    LazyRow(
        modifier = modifier.ownPanelScrollGestures(enabled, axis = PanelGestureAxis.Horizontal),
        reverseLayout = thumbFirstRight,
        flingBehavior = rememberPanelFlingBehavior(),
        contentPadding = PaddingValues(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(items = candidates, key = { it.packageName }) { candidate ->
            KeyboardCandidateChip(
                context = context,
                candidate = candidate,
                kbdColors = kbdColors,
                showLabel = showAppLabels,
                enabled = enabled,
                highlighted = query.isNotEmpty() && candidates.firstOrNull()?.packageName == candidate.packageName,
                accentColor = accentColor,
                onClick = { onLaunchApp(candidate) }
            )
        }
    }
}

@Composable
internal fun KeyboardLauncherKeypadGrid(
    isT9: Boolean,
    showNumberRow: Boolean,
    keyHeightDp: Float,
    compactKeys: Boolean,
    interactive: Boolean,
    queryEmpty: Boolean,
    hasClearableState: Boolean,
    reachableKeys: Set<Char>?,
    activeSide: TriggerSide,
    ergonomics: KeyboardSideErgonomicsSpec,
    kbdColors: KeyboardCardColorTokens,
    onAppendKey: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClearQuery: () -> Unit,
    modifier: Modifier = Modifier,
    maxKeyHeightDp: Float = 42f
) {
    val rowGap = if (compactKeys) 2.dp else 3.dp
    val keyGap = 2.dp
    val safeKeyHeight = keyHeightDp.coerceIn(22f, maxKeyHeightDp.coerceAtLeast(26f)).dp
    val numberKeyHeight = (keyHeightDp * 0.85f).coerceIn(20f, (maxKeyHeightDp * 0.85f).coerceAtLeast(24f)).dp

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(rowGap)
    ) {
        if (isT9) {
            T9_KEYPAD_ROWS.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth().height(safeKeyHeight), horizontalArrangement = Arrangement.spacedBy(keyGap)) {
                    row.forEach { (digit, subLabel) ->
                        VirtualT9KeyCap(
                            digit = digit.toString(),
                            subText = subLabel,
                            kbdColors = kbdColors,
                            enabledNext = reachableKeys == null || digit in reachableKeys,
                            compact = compactKeys,
                            interactive = interactive,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onClick = { onAppendKey(digit) }
                        )
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().height(safeKeyHeight), horizontalArrangement = Arrangement.spacedBy(keyGap)) {
                if (ergonomics.backspaceOnLeft) {
                    VirtualBackspaceKeyCap(kbdColors, interactive && !queryEmpty, Modifier.weight(1f).fillMaxHeight(), onBackspace, onClearQuery)
                } else {
                    VirtualActionKeyCap("清空", kbdColors, interactive && hasClearableState, false, Modifier.weight(1f).fillMaxHeight(), onClearQuery)
                }
                VirtualT9KeyCap(
                    digit = "0",
                    subText = "数字0",
                    kbdColors = kbdColors,
                    enabledNext = reachableKeys == null || '0' in reachableKeys,
                    compact = compactKeys,
                    interactive = interactive,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = { onAppendKey('0') }
                )
                if (ergonomics.backspaceOnLeft) {
                    VirtualActionKeyCap("清空", kbdColors, interactive && hasClearableState, false, Modifier.weight(1f).fillMaxHeight(), onClearQuery)
                } else {
                    VirtualBackspaceKeyCap(kbdColors, interactive && !queryEmpty, Modifier.weight(1f).fillMaxHeight(), onBackspace, onClearQuery)
                }
            }
        } else {
            if (showNumberRow) {
                QwertyLetterRow(QWERTY_NUMBER_ROW, numberKeyHeight, keyGap, true, interactive, false, reachableKeys, activeSide, kbdColors, onAppendKey)
            }
            QwertyLetterRow(QWERTY_ROW_1, safeKeyHeight, keyGap, compactKeys, interactive, true, reachableKeys, activeSide, kbdColors, onAppendKey)
            Row(modifier = Modifier.fillMaxWidth().height(safeKeyHeight), horizontalArrangement = Arrangement.spacedBy(keyGap)) {
                if (ergonomics.row2LeadingSpacerWeight > 0f) Spacer(Modifier.weight(ergonomics.row2LeadingSpacerWeight))
                QwertyRowKeys(QWERTY_ROW_2, compactKeys, interactive, true, reachableKeys, activeSide, kbdColors, onAppendKey)
                if (ergonomics.row2TrailingSpacerWeight > 0f) Spacer(Modifier.weight(ergonomics.row2TrailingSpacerWeight))
            }
            Row(modifier = Modifier.fillMaxWidth().height(safeKeyHeight), horizontalArrangement = Arrangement.spacedBy(keyGap)) {
                if (ergonomics.backspaceOnLeft) {
                    VirtualBackspaceKeyCap(kbdColors, interactive && !queryEmpty, Modifier.weight(1.35f).fillMaxHeight(), onBackspace, onClearQuery)
                }
                if (ergonomics.row3LeadingSpacerWeight > 0f) Spacer(Modifier.weight(ergonomics.row3LeadingSpacerWeight))
                QwertyRowKeys(QWERTY_ROW_3, compactKeys, interactive, true, reachableKeys, activeSide, kbdColors, onAppendKey)
                if (ergonomics.row3TrailingSpacerWeight > 0f) Spacer(Modifier.weight(ergonomics.row3TrailingSpacerWeight))
                if (!ergonomics.backspaceOnLeft) {
                    VirtualBackspaceKeyCap(kbdColors, interactive && !queryEmpty, Modifier.weight(1.35f).fillMaxHeight(), onBackspace, onClearQuery)
                }
            }
        }
    }
}

@Composable
private fun QwertyLetterRow(
    keys: List<Char>,
    heightDp: androidx.compose.ui.unit.Dp,
    keyGap: androidx.compose.ui.unit.Dp,
    compact: Boolean,
    interactive: Boolean,
    lowercaseOutput: Boolean,
    reachableKeys: Set<Char>?,
    activeSide: TriggerSide,
    kbdColors: KeyboardCardColorTokens,
    onAppendKey: (Char) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth().height(heightDp), horizontalArrangement = Arrangement.spacedBy(keyGap)) {
        QwertyRowKeys(keys, compact, interactive, lowercaseOutput, reachableKeys, activeSide, kbdColors, onAppendKey)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.QwertyRowKeys(
    keys: List<Char>,
    compact: Boolean,
    interactive: Boolean,
    lowercaseOutput: Boolean,
    reachableKeys: Set<Char>?,
    activeSide: TriggerSide,
    kbdColors: KeyboardCardColorTokens,
    onAppendKey: (Char) -> Unit
) {
    keys.forEach { ch ->
        val emit = if (lowercaseOutput) ch.lowercaseChar() else ch
        VirtualLetterKeyCap(
            label = ch.toString(),
            kbdColors = kbdColors,
            enabledNext = reachableKeys == null || emit in reachableKeys,
            compact = compact,
            interactive = interactive,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            onClick = {
                val effective = if (reachableKeys != null) {
                    resolveEffectiveKeyTap(keys, emit, reachableKeys, activeSide)
                } else emit
                onAppendKey(effective)
            }
        )
    }
}

@Composable
internal fun KeyboardCandidateChip(
    context: Context,
    candidate: KeyboardCandidateEntry,
    kbdColors: KeyboardCardColorTokens,
    showLabel: Boolean,
    enabled: Boolean,
    highlighted: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val customBitmap = rememberUserIconBitmap(context, candidate.iconUri)
    val cacheGen by AppAndIntentLauncher.cacheGeneration.collectAsState()
    val cachedAppBitmap = remember(candidate.packageName, cacheGen) {
        AppAndIntentLauncher.peekAppIconBitmap(candidate.packageName)
    }
    var loadedInfo by remember(candidate.packageName) {
        mutableStateOf(AppAndIntentLauncher.peekAppInfoCached(candidate.packageName))
    }
    LaunchedEffect(candidate.packageName, customBitmap == null && cachedAppBitmap == null) {
        if (customBitmap == null && cachedAppBitmap == null && !AppAndIntentLauncher.isKnownMissingPackage(candidate.packageName)) {
            loadedInfo = AppAndIntentLauncher.getAppInfoCached(context, candidate.packageName)
        }
    }
    val displayBitmap = customBitmap ?: cachedAppBitmap ?: remember(loadedInfo) {
        (loadedInfo?.icon as? BitmapDrawable)?.bitmap?.let { runCatching { it.asImageBitmap() }.getOrNull() }
    }

    Row(
        modifier = Modifier
            .fillMaxHeight()
            .then(if (showLabel) Modifier.widthIn(min = 64.dp, max = 128.dp) else Modifier.width(38.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlighted) accentColor.copy(alpha = 0.20f) else Color(kbdColors.chipBg))
            .border(
                if (highlighted) 1.dp else 0.6.dp,
                if (highlighted) accentColor.copy(alpha = 0.65f) else Color(kbdColors.chipBorder),
                RoundedCornerShape(10.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (showLabel) 7.dp else 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (showLabel) Arrangement.spacedBy(5.dp) else Arrangement.Center
    ) {
        if (displayBitmap != null) {
            Image(
                bitmap = displayBitmap,
                contentDescription = candidate.label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp))
            )
        } else {
            Box(
                modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(accentColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(candidate.label.take(1).uppercase(), color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (showLabel) {
            Text(
                text = candidate.label,
                color = Color(kbdColors.keyText),
                fontSize = 11.sp,
                fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun VirtualLetterKeyCap(
    label: String,
    kbdColors: KeyboardCardColorTokens,
    enabledNext: Boolean,
    compact: Boolean,
    interactive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .alpha(if (enabledNext) 1f else 0.38f)
            .clip(RoundedCornerShape(7.dp))
            .background(Color(kbdColors.keyBg))
            .border(0.6.dp, Color(kbdColors.keyBorder), RoundedCornerShape(7.dp))
            .clickable(enabled = interactive, onClick = onClick)
            .semantics { contentDescription = "键盘按键 $label" },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color(kbdColors.keyText), fontSize = if (compact) 11.sp else 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun VirtualT9KeyCap(
    digit: String,
    subText: String,
    kbdColors: KeyboardCardColorTokens,
    enabledNext: Boolean,
    compact: Boolean,
    interactive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .alpha(if (enabledNext) 1f else 0.38f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(kbdColors.keyBg))
            .border(0.6.dp, Color(kbdColors.keyBorder), RoundedCornerShape(8.dp))
            .clickable(enabled = interactive, onClick = onClick)
            .semantics { contentDescription = "九宫格按键 $digit" },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(digit, color = Color(kbdColors.keyText), fontSize = if (compact) 13.sp else 15.sp, fontWeight = FontWeight.Bold, lineHeight = if (compact) 14.sp else 16.sp)
        Text(subText, color = Color(kbdColors.subText), fontSize = if (compact) 8.sp else 9.sp, lineHeight = 10.sp)
    }
}

@Composable
internal fun VirtualActionKeyCap(
    label: String,
    kbdColors: KeyboardCardColorTokens,
    interactive: Boolean,
    accent: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = LocalCardColors.current
    Box(
        modifier = modifier
            .alpha(if (interactive) 1f else 0.55f)
            .clip(RoundedCornerShape(7.dp))
            .background(if (accent) palette.accent else Color(kbdColors.actionKeyBg))
            .then(if (!accent) Modifier.border(0.6.dp, Color(kbdColors.actionKeyBorder), RoundedCornerShape(7.dp)) else Modifier)
            .clickable(enabled = interactive, onClick = onClick)
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (accent) Color(kbdColors.accentOnText) else Color(kbdColors.subText), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun VirtualBackspaceKeyCap(
    kbdColors: KeyboardCardColorTokens,
    interactive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = modifier
            .alpha(if (interactive) 1f else 0.55f)
            .clip(RoundedCornerShape(7.dp))
            .background(Color(kbdColors.actionKeyBg))
            .border(0.6.dp, Color(kbdColors.actionKeyBorder), RoundedCornerShape(7.dp))
            .combinedClickable(enabled = interactive, onClick = onClick, onLongClick = onLongClick)
            .semantics { contentDescription = "退格清空" },
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = Icons.Rounded.Backspace, contentDescription = "退格", tint = Color(kbdColors.keyText), modifier = Modifier.size(16.dp))
    }
}
