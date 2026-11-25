package com.example.globalminusone.ui.panel

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.AppAppearanceOverride
import com.example.globalminusone.data.AppKeyboardLauncherSpec
import com.example.globalminusone.data.AppQuickSearchMatcher
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.IndexedSearchApp
import com.example.globalminusone.data.KeyboardCardLayout
import com.example.globalminusone.data.LauncherCardPresentationMode
import com.example.globalminusone.data.TriggerSide
import com.example.globalminusone.data.isSingleIconLauncherItem
import com.example.globalminusone.data.parseFloatingPodStyle
import com.example.globalminusone.data.resolveKeyboardCardColors
import com.example.globalminusone.data.resolveKeyboardPodDimensions
import com.example.globalminusone.data.resolveKeyboardSideErgonomics
import com.example.globalminusone.system.AppAndIntentLauncher
import com.example.globalminusone.system.InstalledAppInfo
import com.example.globalminusone.ui.AppIcons as Icons
import com.example.globalminusone.ui.PlatformPinyinProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Process-local MRU history for zero-permission empty-query & tie-breaker prioritization. */
private object KeyboardLauncherRecencyStore {
    private val lock = Any()
    private val recents = ArrayDeque<String>(24)
    private val revision = mutableIntStateOf(0)

    fun snapshot(): Pair<Int, List<String>> = synchronized(lock) {
        revision.intValue to recents.toList()
    }

    fun recordLaunch(packageName: String) {
        val pkg = packageName.trim()
        if (pkg.isEmpty()) return
        synchronized(lock) {
            recents.remove(pkg)
            recents.addFirst(pkg)
            while (recents.size > 20) recents.removeLast()
            revision.intValue += 1
        }
    }
}

/**
 * Built-in virtual keyboard card (no system IME / TextField) that narrows installed apps by
 * Pinyin initials, full spelling, CamelCase, numbers, digits, or T9 key presses.
 * Enforces ergonomic golden key proportions inline and pops out a thumb-sized floating pod
 * when inside cramped slots/folders or explicitly requested.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppKeyboardLauncherCard(
    item: ComponentItem,
    isEditMode: Boolean,
    onRequestClose: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val palette = LocalCardColors.current
    val panelPalette = LocalPanelPalette.current
    val activeSide = LocalPanelActiveSide.current
    val ergonomics = remember(activeSide) { resolveKeyboardSideErgonomics(activeSide) }
    val overlayHost = LocalKeyboardCandidateOverlayHost.current
    val insideContainer = LocalContainerPopupId.current != null
    val fallbackHostWidthDp = LocalConfiguration.current.screenWidthDp.toFloat()
    val kbdColors = remember(palette.textPrimary, palette.accent, panelPalette.isDark) {
        resolveKeyboardCardColors(
            textPrimaryArgb = palette.textPrimary.toArgb(),
            accentArgb = palette.accent.toArgb(),
            fallbackDark = panelPalette.isDark
        )
    }
    val spec = remember(item.intentUri) { AppKeyboardLauncherSpec.fromComponent(item) }
    val environment = LocalAppMenuEnvironment.current
    val appearances = rememberAppAppearanceOverrides(environment?.actions)
    val listGen by AppAndIntentLauncher.appListGeneration.collectAsState()

    var query by rememberSaveable(item.id) { mutableStateOf("") }
    var candidatePopupExpanded by rememberSaveable(item.id) { mutableStateOf(false) }
    var podExpanded by remember(item.id) { mutableStateOf(false) }
    var activeLayout by rememberSaveable(item.id, spec.layout) { mutableStateOf(spec.layout) }
    var installedApps by remember(listGen) { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    var cardBoundsInWindow by remember { mutableStateOf(IntRect.Zero) }
    val ownerToken = remember { Any() }
    val pageActive = LocalPageViewportActive.current

    LaunchedEffect(isEditMode, pageActive) {
        if (isEditMode || !pageActive) {
            podExpanded = false
            candidatePopupExpanded = false
            overlayHost?.unregister(ownerToken)
        }
    }

    LaunchedEffect(context, listGen) {
        installedApps = withContext(Dispatchers.IO) {
            AppAndIntentLauncher.getInstalledApps(context)
        }
    }

    val indexedApps by produceState<List<IndexedSearchApp<KeyboardCandidateEntry>>>(
        initialValue = emptyList(),
        key1 = installedApps,
        key2 = appearances
    ) {
        value = withContext(Dispatchers.Default) {
            val entries = installedApps.map { app ->
                val custom: AppAppearanceOverride? = appearances[app.packageName]
                KeyboardCandidateEntry(
                    packageName = app.packageName,
                    label = custom?.displayName?.takeIf { it.isNotBlank() } ?: app.label,
                    iconUri = custom?.iconUri.orEmpty()
                )
            }
            AppQuickSearchMatcher.indexApps(
                apps = entries,
                label = { it.label },
                packageName = { it.packageName },
                provider = PlatformPinyinProvider.instance
            )
        }
    }

    val isT9 = activeLayout == KeyboardCardLayout.T9
    val recencySnapshot = remember(KeyboardLauncherRecencyStore.snapshot().first, query) {
        KeyboardLauncherRecencyStore.snapshot().second
    }

    val candidates = remember(indexedApps, query, isT9, recencySnapshot, spec.maxCandidates) {
        AppQuickSearchMatcher.matchApps(
            indexed = indexedApps,
            rawQuery = query,
            t9Mode = isT9,
            recentPackages = recencySnapshot,
            maxResults = spec.maxCandidates
        )
    }

    val reachableKeys = remember(indexedApps, query, isT9) {
        if (query.isEmpty()) null
        else AppQuickSearchMatcher.nextReachableKeys(
            indexed = indexedApps,
            currentQuery = query,
            candidateKeys = if (isT9) T9_SEARCH_CHARS else QWERTY_SEARCH_CHARS,
            t9Mode = isT9
        )
    }

    fun launchCandidate(candidate: KeyboardCandidateEntry) {
        if (isEditMode) return
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        KeyboardLauncherRecencyStore.recordLaunch(candidate.packageName)
        if (AppAndIntentLauncher.launchApp(context, candidate.packageName)) {
            if (spec.clearAfterLaunch) query = ""
            candidatePopupExpanded = false
            podExpanded = false
            onRequestClose()
        }
    }

    fun backspace() {
        if (isEditMode || query.isEmpty()) return
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        query = query.dropLast(1)
    }

    fun clearQuery() {
        if (isEditMode) return
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        query = ""
        candidatePopupExpanded = false
    }

    fun toggleLayout() {
        if (isEditMode) return
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        activeLayout = if (isT9) KeyboardCardLayout.QWERTY else KeyboardCardLayout.T9
    }

    DisposableEffect(overlayHost, ownerToken) {
        onDispose {
            overlayHost?.unregister(ownerToken)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                val r = coords.boundsInWindow()
                cardBoundsInWindow = IntRect(
                    r.left.roundToInt(),
                    r.top.roundToInt(),
                    r.right.roundToInt(),
                    r.bottom.roundToInt()
                )
            }
            .padding(horizontal = 2.dp, vertical = 3.dp)
    ) {
        val effectiveHostWidthDp = overlayHost?.hostWidthDp ?: fallbackHostWidthDp
        val podDims = remember(
            activeLayout,
            effectiveHostWidthDp,
            maxWidth.value,
            maxHeight.value,
            insideContainer,
            spec.showNumberRow
        ) {
            resolveKeyboardPodDimensions(
                layout = activeLayout,
                hostWidthDp = effectiveHostWidthDp,
                cardWidthDp = maxWidth.value,
                cardHeightDp = maxHeight.value,
                insideContainer = insideContainer,
                showNumberRow = spec.showNumberRow
            )
        }

        fun appendKey(ch: Char) {
            if (isEditMode) return
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            candidatePopupExpanded = false
            if (overlayHost != null && podDims.shouldUsePopOutPod && !podExpanded) {
                podExpanded = true
            }
            val nextQuery = (query + ch.lowercaseChar()).take(24)
            query = nextQuery
            if (spec.autoLaunchSingleMatch) {
                val narrow = AppQuickSearchMatcher.matchApps(
                    indexed = indexedApps,
                    rawQuery = nextQuery,
                    t9Mode = isT9,
                    recentPackages = recencySnapshot,
                    maxResults = 2
                )
                if (narrow.size == 1) {
                    launchCandidate(narrow.first())
                }
            }
        }

        val singleIconMode = overlayHost != null && (
            isSingleIconLauncherItem(item) ||
                (insideContainer && spec.presentationMode != LauncherCardPresentationMode.INLINE_CARD) ||
                (!item.intentUri.contains("mode=INLINE_CARD") && maxWidth < 116.dp && maxHeight < 116.dp)
            )
        val shouldShowFloatingOverlay = pageActive && !isEditMode && (
            podExpanded ||
                (!singleIconMode && !podDims.canShowInlineCandidates && (query.isNotEmpty() || candidatePopupExpanded))
            )
        val currentBounds = cardBoundsInWindow
        val podStyle = remember(item.intentUri) { parseFloatingPodStyle(item.intentUri) }
        val podVisuals = remember(podStyle, panelPalette, palette) {
            resolvePodVisuals(podStyle, panelPalette, palette, defaultCornerDp = 16)
        }
        fun publishKeyboardOverlay(expandedPod: Boolean = podExpanded) {
            if (overlayHost == null) return
            overlayHost.publish(
                KeyboardCandidateOverlayState(
                    ownerCardId = item.id,
                    anchorBoundsInWindow = currentBounds,
                    candidates = candidates,
                    query = query,
                    showAppLabels = spec.showAppLabels,
                    isLoadingApps = installedApps.isEmpty(),
                    kbdColors = podVisuals.kbdColors,
                    accentColor = palette.accent,
                    activeSide = activeSide,
                    surfaceColor = podVisuals.surfaceColor,
                    podExpanded = expandedPod || singleIconMode,
                    activeLayout = activeLayout,
                    showNumberRow = spec.showNumberRow,
                    reachableKeys = reachableKeys,
                    ergonomics = ergonomics,
                    ownerToken = ownerToken,
                    customWidthDp = podStyle.widthDp,
                    customHeightDp = podStyle.heightDp,
                    cornerRadiusDp = podVisuals.cornerRadiusDp,
                    onAppendKey = ::appendKey,
                    onBackspace = ::backspace,
                    onClearQuery = ::clearQuery,
                    onToggleLayout = ::toggleLayout,
                    onLaunchApp = ::launchCandidate,
                    onDismissOverlay = {
                        podExpanded = false
                        candidatePopupExpanded = false
                        overlayHost.unregister(ownerToken)
                    }
                )
            )
        }

        LaunchedEffect(
            overlayHost,
            shouldShowFloatingOverlay,
            currentBounds,
            activeSide,
            query,
            activeLayout,
            candidates,
            reachableKeys,
            installedApps.isEmpty(),
            podExpanded,
            candidatePopupExpanded,
            isEditMode,
            pageActive,
            singleIconMode,
            spec.showAppLabels,
            spec.showNumberRow,
            podStyle,
            podVisuals
        ) {
            if (overlayHost != null) {
                if (shouldShowFloatingOverlay) {
                    publishKeyboardOverlay()
                } else {
                    overlayHost.unregister(ownerToken)
                }
            }
        }

        if (singleIconMode) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CustomShortcutComponent(
                    item = item.copy(title = item.title.ifBlank { "键盘快启" }),
                    enabled = !isEditMode,
                    fallbackIcon = Icons.Rounded.Keyboard,
                    isPopOutLauncherIcon = true,
                    onClick = {
                        if (!isEditMode) {
                            val next = !podExpanded
                            candidatePopupExpanded = false
                            podExpanded = next
                            if (next) {
                                publishKeyboardOverlay(expandedPod = true)
                            } else {
                                overlayHost?.unregister(ownerToken)
                            }
                        }
                    }
                )
            }
        } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            @Composable
            fun HeaderLayoutToggleChip() {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.accent.copy(alpha = 0.16f))
                        .border(0.6.dp, palette.accent.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                        .clickable(enabled = !isEditMode) { toggleLayout() }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isT9) "全键" else "9键",
                        color = palette.accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            @Composable
            fun HeaderPodToggleChip() {
                if (overlayHost == null) return
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (podExpanded) palette.accent.copy(alpha = 0.22f)
                            else Color(kbdColors.actionKeyBg)
                        )
                        .border(
                            0.6.dp,
                            if (podExpanded) palette.accent.copy(alpha = 0.55f)
                            else Color(kbdColors.actionKeyBorder),
                            RoundedCornerShape(6.dp)
                        )
                        .clickable(enabled = !isEditMode) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            candidatePopupExpanded = false
                            podExpanded = !podExpanded
                        }
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (podExpanded) "收起" else "单手",
                        color = if (podExpanded) palette.accent else Color(kbdColors.subText),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            @Composable
            fun HeaderActionOrRecentChip() {
                if (query.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "${candidates.size}个",
                            color = palette.accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(kbdColors.actionKeyBg))
                                .border(0.6.dp, Color(kbdColors.actionKeyBorder), RoundedCornerShape(6.dp))
                                .clickable(enabled = !isEditMode) { clearQuery() }
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "清空",
                                color = Color(kbdColors.keyText),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else if (!podDims.canShowInlineCandidates && overlayHost != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (candidatePopupExpanded) palette.accent.copy(alpha = 0.20f)
                                else Color(kbdColors.actionKeyBg)
                            )
                            .border(
                                0.6.dp,
                                if (candidatePopupExpanded) palette.accent.copy(alpha = 0.55f)
                                else Color(kbdColors.actionKeyBorder),
                                RoundedCornerShape(6.dp)
                            )
                            .clickable(enabled = !isEditMode) {
                                candidatePopupExpanded = !candidatePopupExpanded
                            }
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (candidatePopupExpanded) "收起" else "最近",
                            color = if (candidatePopupExpanded) palette.accent else Color(kbdColors.subText),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // 1. 顶部检索与单手布局控制条
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(kbdColors.queryBarBg))
                    .border(0.6.dp, Color(kbdColors.queryBarBorder), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (ergonomics.headerControlsOnLeft) {
                    HeaderLayoutToggleChip()
                    HeaderPodToggleChip()
                    HeaderActionOrRecentChip()
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Keyboard,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = if (query.isEmpty()) {
                        if (podDims.shouldUsePopOutPod && overlayHost != null) {
                            "点按键打开单手快启"
                        } else if (isT9) {
                            "点数字键搜应用"
                        } else {
                            "首字母/全拼搜应用"
                        }
                    } else {
                        query.uppercase()
                    },
                    color = if (query.isEmpty()) Color(kbdColors.subText) else Color(kbdColors.keyText),
                    fontSize = if (query.isEmpty()) 11.sp else 12.sp,
                    fontWeight = if (query.isEmpty()) FontWeight.Normal else FontWeight.Bold,
                    fontFamily = if (query.isEmpty()) FontFamily.Default else FontFamily.Monospace,
                    textAlign = if (ergonomics.headerControlsOnLeft) TextAlign.End else TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = !isEditMode && query.isEmpty()) {
                            if (overlayHost != null && podDims.shouldUsePopOutPod) {
                                podExpanded = !podExpanded
                            } else if (!podDims.canShowInlineCandidates) {
                                candidatePopupExpanded = !candidatePopupExpanded
                            }
                        }
                )
                if (ergonomics.headerControlsOnLeft) {
                    Icon(
                        imageVector = Icons.Rounded.Keyboard,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(14.dp)
                    )
                } else {
                    HeaderActionOrRecentChip()
                    HeaderPodToggleChip()
                    HeaderLayoutToggleChip()
                }
            }

            // 2. 候选与键盘主体：高卡片上方承接拇指优先候选（单手宽度封顶），底部固定黄金比例按键，避免 5:1 纵向拉伸
            val keyHeightDp = if (isT9) podDims.t9KeyHeightDp else podDims.qwertyKeyHeightDp
            val showInlineStrip = podDims.canShowInlineCandidates ||
                (overlayHost == null && (query.isNotEmpty() || candidatePopupExpanded))

            if (podExpanded && podDims.shouldUsePopOutPod) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(kbdColors.emptyStripBg))
                        .border(0.6.dp, palette.accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .clickable(enabled = !isEditMode) { podExpanded = false },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "单手快启舱已展开 · 点击空白或此处收起",
                        color = palette.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                if (showInlineStrip) {
                    val stripAlign = if (activeSide == TriggerSide.RIGHT) {
                        Alignment.BottomEnd
                    } else {
                        Alignment.BottomStart
                    }
                    val inlineBarHeight = if (spec.showAppLabels) 48.dp else 36.dp
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = stripAlign
                    ) {
                        KeyboardCandidateStrip(
                            context = context,
                            candidates = candidates,
                            query = query,
                            showAppLabels = spec.showAppLabels,
                            isLoadingApps = installedApps.isEmpty(),
                            kbdColors = kbdColors,
                            accentColor = palette.accent,
                            activeSide = activeSide,
                            enabled = !isEditMode,
                            modifier = Modifier
                                .widthIn(max = podDims.candidateMaxStripWidthDp.dp)
                                .fillMaxWidth()
                                .height(inlineBarHeight),
                            onLaunchApp = ::launchCandidate
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = podDims.keypadHeightDp.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    KeyboardLauncherKeypadGrid(
                        isT9 = isT9,
                        showNumberRow = spec.showNumberRow,
                        keyHeightDp = keyHeightDp,
                        compactKeys = keyHeightDp < 31f || this@BoxWithConstraints.maxWidth < 236.dp,
                        interactive = !isEditMode,
                        queryEmpty = query.isEmpty(),
                        hasClearableState = query.isNotEmpty() || candidatePopupExpanded,
                        reachableKeys = reachableKeys,
                        activeSide = activeSide,
                        ergonomics = ergonomics,
                        kbdColors = kbdColors,
                        onAppendKey = ::appendKey,
                        onBackspace = ::backspace,
                        onClearQuery = ::clearQuery
                    )
                }
            }
        }
        }
    }
}
