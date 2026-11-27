package com.example.globalminusone.data.policy

import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.TriggerSide

enum class KeyboardCardLayout(val label: String) {
    QWERTY("全键盘"),
    T9("九宫格")
}

enum class LauncherCardPresentationMode(val label: String) {
    POPUP_ICON("单图标弹出"),
    INLINE_CARD("内嵌大卡片")
}

/**
 * Pure-Kotlin spec for [ComponentType.APP_KEYBOARD_LAUNCHER], persisted inside
 * ComponentItem.intentUri with a backward-compatible `gmo-kbd-v1:` prefix.
 */
data class AppKeyboardLauncherSpec(
    val layout: KeyboardCardLayout = KeyboardCardLayout.QWERTY,
    val showNumberRow: Boolean = true,
    val clearAfterLaunch: Boolean = true,
    val autoLaunchSingleMatch: Boolean = false,
    val maxCandidates: Int = DEFAULT_MAX_CANDIDATES,
    val showAppLabels: Boolean = true,
    val presentationMode: LauncherCardPresentationMode = LauncherCardPresentationMode.INLINE_CARD
) {
    fun encodeToIntentUri(): String {
        val safeMax = maxCandidates.coerceIn(4, 48)
        return buildString {
            append(URI_PREFIX)
            append("layout=").append(layout.name)
            append("&nums=").append(if (showNumberRow) 1 else 0)
            append("&clear=").append(if (clearAfterLaunch) 1 else 0)
            append("&auto=").append(if (autoLaunchSingleMatch) 1 else 0)
            append("&max=").append(safeMax).append("&mv=2")
            append("&labels=").append(if (showAppLabels) 1 else 0)
            if (presentationMode == LauncherCardPresentationMode.POPUP_ICON) {
                append("&mode=").append(LauncherCardPresentationMode.POPUP_ICON.name)
            }
        }
    }

    companion object {
        const val URI_PREFIX = "gmo-kbd-v1:"
        const val DEFAULT_MAX_CANDIDATES = 24

        fun fromComponent(item: ComponentItem): AppKeyboardLauncherSpec = fromIntentUri(item.intentUri)

        fun fromIntentUri(rawUri: String): AppKeyboardLauncherSpec {
            if (!rawUri.startsWith(URI_PREFIX)) return AppKeyboardLauncherSpec()
            val body = rawUri.removePrefix(URI_PREFIX)
            val params = HashMap<String, String>(8)
            for (pair in body.split('&')) {
                val eq = pair.indexOf('=')
                if (eq > 0 && eq < pair.length - 1) params[pair.substring(0, eq)] = pair.substring(eq + 1)
            }
            val layout = params["layout"]?.let { n -> KeyboardCardLayout.entries.firstOrNull { it.name == n } }
                ?: KeyboardCardLayout.QWERTY
            val rawMax = params["max"]?.toIntOrNull()
            val maxCand = if (params["mv"] == null && rawMax == 8) {
                DEFAULT_MAX_CANDIDATES
            } else {
                rawMax?.coerceIn(4, 48) ?: DEFAULT_MAX_CANDIDATES
            }
            val mode = if (params["mode"] == LauncherCardPresentationMode.POPUP_ICON.name) {
                LauncherCardPresentationMode.POPUP_ICON
            } else {
                LauncherCardPresentationMode.INLINE_CARD
            }
            return AppKeyboardLauncherSpec(
                layout = layout,
                showNumberRow = params["nums"] != "0",
                clearAfterLaunch = params["clear"] != "0",
                autoLaunchSingleMatch = params["auto"] == "1",
                maxCandidates = maxCand,
                showAppLabels = params["labels"] != "0",
                presentationMode = mode
            )
        }
    }
}

/**
 * Pure-Kotlin spec for [ComponentType.APP_DRAWER_CARD], persisted inside
 * ComponentItem.intentUri with a `gmo-drawer-v1:` prefix.
 */
data class AppDrawerCardSpec(
    val presentationMode: LauncherCardPresentationMode = LauncherCardPresentationMode.POPUP_ICON
) {
    fun encodeToIntentUri(): String = "${URI_PREFIX}mode=${presentationMode.name}"

    companion object {
        const val URI_PREFIX = "gmo-drawer-v1:"

        fun fromComponent(item: ComponentItem): AppDrawerCardSpec =
            if (item.intentUri.startsWith(URI_PREFIX)) {
                fromIntentUri(item.intentUri)
            } else {
                AppDrawerCardSpec(presentationMode = LauncherCardPresentationMode.INLINE_CARD)
            }

        fun fromIntentUri(rawUri: String): AppDrawerCardSpec {
            if (!rawUri.startsWith(URI_PREFIX)) return AppDrawerCardSpec()
            val body = rawUri.removePrefix(URI_PREFIX)
            val modeVal = body.split('&')
                .mapNotNull { pair ->
                    val eq = pair.indexOf('=')
                    if (eq > 0 && eq < pair.length - 1) pair.substring(0, eq) to pair.substring(eq + 1) else null
                }
                .toMap()["mode"]
            val parsed = LauncherCardPresentationMode.entries.firstOrNull { it.name == modeVal }
                ?: LauncherCardPresentationMode.POPUP_ICON
            return AppDrawerCardSpec(presentationMode = parsed)
        }
    }
}

fun isSingleIconLauncherItem(item: ComponentItem): Boolean {
    if (item.type !in POPOUT_CAPABLE_CARD_TYPES) return false
    if (item.intentUri.contains("mode=POPUP_ICON")) return true
    if (item.intentUri.contains("mode=INLINE_CARD")) return false
    // Legacy APP_DRAWER_CARD compacted into a 1x1 cell (h <= 14) auto-promotes to pop-out mode,
    // while preserving V2480 shrunk wide drawers that retain default h = 40 inside folders.
    return item.type == ComponentType.APP_DRAWER_CARD &&
        item.gridSpanX == 1 &&
        item.gridSpanY == 1 &&
        item.h <= 14
}

val POPOUT_CAPABLE_CARD_TYPES: Set<ComponentType> = setOf(
    ComponentType.APP_KEYBOARD_LAUNCHER,
    ComponentType.APP_DRAWER_CARD,
    ComponentType.BRIGHTNESS_BAR,
    ComponentType.VOLUME_BAR,
    ComponentType.RECENT_APPS_CARD,
    ComponentType.CLIPBOARD_CARD,
    ComponentType.NOTE_CARD,
    ComponentType.APP_WIDGET,
    ComponentType.STAT_BATTERY_DETAIL,
    ComponentType.SINGLE_IMAGE,
    ComponentType.PHOTO_CUBE,
    ComponentType.HITOKOTO_CARD
)

const val GENERIC_CARD_POD_URI_PREFIX = "gmo://card_pod?"

fun parseCardPresentationMode(item: ComponentItem): LauncherCardPresentationMode? {
    if (item.type !in POPOUT_CAPABLE_CARD_TYPES) return null
    return when (item.type) {
        ComponentType.APP_KEYBOARD_LAUNCHER -> {
            val base = AppKeyboardLauncherSpec.fromComponent(item)
            if (!item.intentUri.contains("mode=") && isSingleIconLauncherItem(item)) {
                LauncherCardPresentationMode.POPUP_ICON
            } else {
                base.presentationMode
            }
        }
        ComponentType.APP_DRAWER_CARD -> {
            val base = AppDrawerCardSpec.fromComponent(item)
            if (!item.intentUri.contains("mode=") && isSingleIconLauncherItem(item)) {
                LauncherCardPresentationMode.POPUP_ICON
            } else {
                base.presentationMode
            }
        }
        else -> when {
            item.intentUri.contains("mode=POPUP_ICON") -> LauncherCardPresentationMode.POPUP_ICON
            item.intentUri.contains("mode=INLINE_CARD") -> LauncherCardPresentationMode.INLINE_CARD
            else -> LauncherCardPresentationMode.INLINE_CARD
        }
    }
}

fun encodeCardPresentationIntentUri(
    item: ComponentItem,
    mode: LauncherCardPresentationMode
): String {
    if (item.type !in POPOUT_CAPABLE_CARD_TYPES) return item.intentUri
    val encoded = when (item.type) {
        ComponentType.APP_KEYBOARD_LAUNCHER ->
            AppKeyboardLauncherSpec.fromComponent(item).copy(presentationMode = mode).encodeToIntentUri()
        ComponentType.APP_DRAWER_CARD ->
            AppDrawerCardSpec(presentationMode = mode).encodeToIntentUri()
        else -> "${GENERIC_CARD_POD_URI_PREFIX}mode=${mode.name}"
    }
    return mergeFloatingPodStyleParams(encoded, item.intentUri)
}

fun withCardPresentationMode(
    item: ComponentItem,
    mode: LauncherCardPresentationMode
): ComponentItem {
    if (item.type !in POPOUT_CAPABLE_CARD_TYPES) return item
    val nextUri = encodeCardPresentationIntentUri(item, mode)
    return if (mode == LauncherCardPresentationMode.POPUP_ICON) {
        item.copy(
            w = 8,
            h = 8,
            gridSpanX = 1,
            gridSpanY = 1,
            autoHeight = false,
            showTitle = false,
            intentUri = nextUri
        )
    } else {
        val (inlineW, inlineH, spanX, spanY) = when (item.type) {
            ComponentType.BRIGHTNESS_BAR, ComponentType.VOLUME_BAR ->
                listOf(32, 6, GRID_SPAN_FULL_ROW, 1)
            ComponentType.APP_KEYBOARD_LAUNCHER ->
                listOf(32, 16, 4, 4)
            ComponentType.APP_DRAWER_CARD ->
                listOf(32, 18, 4, 4)
            ComponentType.APP_WIDGET ->
                listOf(32, 16, 4, 2)
            ComponentType.SINGLE_IMAGE, ComponentType.PHOTO_CUBE ->
                listOf(16, 16, 2, 2)
            ComponentType.HITOKOTO_CARD ->
                listOf(32, 10, GRID_SPAN_FULL_ROW, 1)
            else ->
                listOf(32, 12, 4, 2)
        }
        val showInlineTitle = item.type in setOf(
            ComponentType.APP_DRAWER_CARD,
            ComponentType.RECENT_APPS_CARD,
            ComponentType.CLIPBOARD_CARD,
            ComponentType.NOTE_CARD
        )
        item.copy(
            w = inlineW,
            h = inlineH,
            gridSpanX = spanX,
            gridSpanY = spanY,
            autoHeight = false,
            showTitle = showInlineTitle,
            intentUri = nextUri
        )
    }
}

enum class FloatingPodHorizontalAnchor {
    ALIGN_TRIGGER_X,
    DOCK_ACTIVE_SIDE,
    INLINE_STRIP_ANCHOR
}

data class FloatingPodPlacement(val x: Int, val y: Int)

/**
 * Pure geometric placement for pop-out floating pods:
 * - [FloatingPodHorizontalAnchor.ALIGN_TRIGGER_X]: centers horizontally on the trigger card icon
 *   without snapping to the screen edge (used by Brightness / Volume slider pods).
 * - [FloatingPodHorizontalAnchor.DOCK_ACTIVE_SIDE]: docks flush to the active grip side
 *   ([TriggerSide.LEFT] or [TriggerSide.RIGHT]) within margin.
 * - Vertical coordinate adapts directly to the trigger's vertical center so top-screen triggers
 *   pop out near the top rather than jumping to the bottom of the screen.
 */
fun computeFloatingPodPlacement(
    hostWidthPx: Int,
    hostHeightPx: Int,
    podWidthPx: Int,
    podHeightPx: Int,
    anchorLeftPx: Int,
    anchorTopPx: Int,
    anchorRightPx: Int,
    anchorBottomPx: Int,
    activeSide: TriggerSide,
    horizontalAnchor: FloatingPodHorizontalAnchor,
    edgeMarginPx: Int,
    safeTopPx: Int = edgeMarginPx,
    safeBottomPx: Int = edgeMarginPx,
    gapPx: Int = 0,
    inlineStripMode: Boolean = false
): FloatingPodPlacement {
    val minX = edgeMarginPx
    val maxX = (hostWidthPx - podWidthPx - edgeMarginPx).coerceAtLeast(minX)
    val minY = maxOf(edgeMarginPx, safeTopPx)
    val maxY = (hostHeightPx - podHeightPx - maxOf(edgeMarginPx, safeBottomPx)).coerceAtLeast(minY)
    val anchorCenterX = (anchorLeftPx + anchorRightPx) / 2
    val anchorCenterY = (anchorTopPx + anchorBottomPx) / 2

    val preferredX = when (horizontalAnchor) {
        FloatingPodHorizontalAnchor.ALIGN_TRIGGER_X -> anchorCenterX - podWidthPx / 2
        FloatingPodHorizontalAnchor.DOCK_ACTIVE_SIDE -> if (activeSide == TriggerSide.LEFT) minX else maxX
        FloatingPodHorizontalAnchor.INLINE_STRIP_ANCHOR ->
            if (activeSide == TriggerSide.LEFT) anchorLeftPx else anchorRightPx - podWidthPx
    }
    val x = preferredX.coerceIn(minX, maxX)

    val y = if (inlineStripMode) {
        val aboveY = anchorTopPx - gapPx - podHeightPx
        val belowY = anchorBottomPx + gapPx
        when {
            aboveY >= minY -> aboveY
            belowY <= maxY -> belowY
            else -> aboveY.coerceIn(minY, maxY)
        }
    } else {
        (anchorCenterY - podHeightPx / 2).coerceIn(minY, maxY)
    }
    return FloatingPodPlacement(x = x, y = y)
}

data class KeyboardCardColorTokens(
    val isDark: Boolean,
    val queryBarBg: Int,
    val queryBarBorder: Int,
    val keyBg: Int,
    val keyBorder: Int,
    val actionKeyBg: Int,
    val actionKeyBorder: Int,
    val keyText: Int,
    val subText: Int,
    val chipBg: Int,
    val chipBorder: Int,
    val emptyStripBg: Int,
    val accentOnText: Int
)

/**
 * Ergonomic layout parameters adapting the quick-launch keyboard to the active panel side
 * (single-hand thumb reach on LEFT vs RIGHT, removing the redundant "启动" action key).
 */
data class KeyboardSideErgonomicsSpec(
    val side: TriggerSide,
    val backspaceOnLeft: Boolean,
    val row2LeadingSpacerWeight: Float,
    val row2TrailingSpacerWeight: Float,
    val row3LeadingSpacerWeight: Float,
    val row3TrailingSpacerWeight: Float,
    val headerControlsOnLeft: Boolean,
    val hasLaunchButton: Boolean = false
)

fun resolveKeyboardSideErgonomics(side: TriggerSide): KeyboardSideErgonomicsSpec =
    if (side == TriggerSide.LEFT) {
        KeyboardSideErgonomicsSpec(
            side = side,
            backspaceOnLeft = true,
            row2LeadingSpacerWeight = 0.06f,
            row2TrailingSpacerWeight = 0.18f,
            row3LeadingSpacerWeight = 0f,
            row3TrailingSpacerWeight = 0.08f,
            headerControlsOnLeft = true,
            hasLaunchButton = false
        )
    } else {
        KeyboardSideErgonomicsSpec(
            side = side,
            backspaceOnLeft = false,
            row2LeadingSpacerWeight = 0.18f,
            row2TrailingSpacerWeight = 0.06f,
            row3LeadingSpacerWeight = 0.08f,
            row3TrailingSpacerWeight = 0f,
            headerControlsOnLeft = false,
            hasLaunchButton = false
        )
    }

/**
 * Smart fat-finger snap: if the tapped letter/digit is dimmed (not in [reachableKeys]) while an
 * immediate horizontal neighbor in the same [rowKeys] IS reachable, snap to that reachable neighbor
 * (breaking ties toward the active thumb edge). Preserves case when matched inside [rowKeys].
 */
fun resolveEffectiveKeyTap(
    rowKeys: List<Char>,
    tappedKey: Char,
    reachableKeys: Set<Char>,
    side: TriggerSide
): Char {
    if (reachableKeys.isEmpty()) return tappedKey
    val lowerReachable = reachableKeys.mapTo(HashSet(reachableKeys.size)) { it.lowercaseChar() }
    if (tappedKey.lowercaseChar() in lowerReachable) return tappedKey
    val idx = rowKeys.indexOfFirst { it.lowercaseChar() == tappedKey.lowercaseChar() }
    if (idx < 0) return tappedKey
    val leftNeighbor = rowKeys.getOrNull(idx - 1)?.takeIf { it.lowercaseChar() in lowerReachable }
    val rightNeighbor = rowKeys.getOrNull(idx + 1)?.takeIf { it.lowercaseChar() in lowerReachable }
    return when {
        side == TriggerSide.LEFT -> leftNeighbor ?: rightNeighbor ?: tappedKey
        else -> rightNeighbor ?: leftNeighbor ?: tappedKey
    }
}

fun relativeLuminance(argb: Int): Float {
    val r = ((argb ushr 16) and 0xFF) / 255f
    val g = ((argb ushr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    return 0.299f * r + 0.587f * g + 0.114f * b
}

fun contrastRatio(fgArgb: Int, bgArgb: Int): Float {
    val l1 = relativeLuminance(fgArgb)
    val l2 = relativeLuminance(bgArgb)
    val lighter = maxOf(l1, l2)
    val darker = minOf(l1, l2)
    return (lighter + 0.05f) / (darker + 0.05f)
}

fun resolveKeyboardCardColors(
    textPrimaryArgb: Int,
    accentArgb: Int,
    fallbackDark: Boolean
): KeyboardCardColorTokens {
    val textLum = relativeLuminance(textPrimaryArgb)
    val darkTokens = textLum >= 0.52f || (textLum in 0.42f..0.52f && fallbackDark)
    val accentOnText = if (relativeLuminance(accentArgb) > 0.62f) {
        0xFF121620.toInt()
    } else {
        0xFFFFFFFF.toInt()
    }
    return if (darkTokens) {
        KeyboardCardColorTokens(
            isDark = true,
            queryBarBg = 0x9E141822.toInt(),
            queryBarBorder = 0x1AFFFFFF.toInt(),
            keyBg = 0xD1252B37.toInt(),
            keyBorder = 0x17FFFFFF.toInt(),
            actionKeyBg = 0xE0323A4A.toInt(),
            actionKeyBorder = 0x1CFFFFFF.toInt(),
            keyText = 0xFFF0F3F8.toInt(),
            subText = 0xFF98A3B6.toInt(),
            chipBg = 0xC71E2430.toInt(),
            chipBorder = 0x14FFFFFF.toInt(),
            emptyStripBg = 0x66141822.toInt(),
            accentOnText = accentOnText
        )
    } else {
        KeyboardCardColorTokens(
            isDark = false,
            queryBarBg = 0xFFF0F3F7.toInt(),
            queryBarBorder = 0xFFDCE2EC.toInt(),
            keyBg = 0xFFFFFFFF.toInt(),
            keyBorder = 0xFFD7DEE9.toInt(),
            actionKeyBg = 0xFFE5EAF2.toInt(),
            actionKeyBorder = 0xFFCFD7E4.toInt(),
            keyText = 0xFF1D2432.toInt(),
            subText = 0xFF6B778C.toInt(),
            chipBg = 0xFFF4F6FA.toInt(),
            chipBorder = 0xFFDFE5EF.toInt(),
            emptyStripBg = 0x99EEF1F6.toInt(),
            accentOnText = accentOnText
        )
    }
}

/**
 * 单手舒适操作几何规格：
 * - 固定单手浮点舱与悬浮候选条最大舒适宽度（不再横跨整块屏幕）；
 * - 限定全键/九键单键高宽比与键盘整体高度，避免高格位卡片将按键纵向拉成 5:1 细长条；
 * - 为高卡片预留内置候选头部空间，为窄卡片/文件夹容器自动开启单手浮点键盘舱。
 */
data class KeyboardPodDimensionsSpec(
    val podWidthDp: Float,
    val candidateMaxStripWidthDp: Float,
    val qwertyKeyHeightDp: Float,
    val t9KeyHeightDp: Float,
    val keypadHeightDp: Float,
    val inlineCandidateHeightDp: Float,
    val canShowInlineCandidates: Boolean,
    val shouldUsePopOutPod: Boolean,
    val podHeightDp: Float
)

fun resolveKeyboardPodDimensions(
    layout: KeyboardCardLayout,
    hostWidthDp: Float,
    cardWidthDp: Float,
    cardHeightDp: Float,
    insideContainer: Boolean,
    showNumberRow: Boolean = false,
    customPodWidthDp: Int = 0
): KeyboardPodDimensionsSpec {
    val safeHostW = hostWidthDp.coerceAtLeast(220f)
    val maxAllowedPodW = (safeHostW - 4f).coerceAtLeast(200f)
    val podWidthDp = if (customPodWidthDp > 0) {
        customPodWidthDp.toFloat().coerceIn(minOf(220f, maxAllowedPodW), maxAllowedPodW)
    } else if (layout == KeyboardCardLayout.T9) {
        (safeHostW * 0.78f).coerceIn(268f, 304f).coerceAtMost(maxAllowedPodW)
    } else {
        (safeHostW * 0.90f).coerceIn(308f, 368f).coerceAtMost(maxAllowedPodW)
    }
    val candidateMaxStripWidthDp = resolveDetachedCandidateWidthDp(safeHostW)

    val effectiveKeyboardW = if (insideContainer || cardWidthDp < 248f) podWidthDp else cardWidthDp.coerceIn(190f, podWidthDp)
    val qwertyKeyW = ((effectiveKeyboardW - 10f - 9f * 2.5f) / 10.2f).coerceIn(18f, 36f)
    val qwertyKeyHeightDp = (qwertyKeyW * 1.42f).coerceIn(34f, 48f)

    val t9CellW = ((effectiveKeyboardW - 14f - 6f) / 3f).coerceIn(48f, 94f)
    val t9KeyHeightDp = (t9CellW * 0.48f).coerceIn(30f, 38f)

    val qwertyRowUnits = if (showNumberRow) 3.85f else 3f
    val qwertyGapTotal = if (showNumberRow) 9f else 6f
    val keypadHeightDp = if (layout == KeyboardCardLayout.T9) {
        (t9KeyHeightDp * 4f + 9f).coerceIn(126f, 162f)
    } else {
        (qwertyKeyHeightDp * qwertyRowUnits + qwertyGapTotal).coerceIn(108f, 206f)
    }

    val headerAndPaddingDp = 34f
    val inlineHeadroomDp = (cardHeightDp - keypadHeightDp - headerAndPaddingDp).coerceAtLeast(0f)
    val canShowInlineCandidates = !insideContainer &&
        cardWidthDp >= 248f &&
        inlineHeadroomDp >= 52f
    val inlineCandidateHeightDp = if (canShowInlineCandidates) {
        inlineHeadroomDp.coerceIn(52f, 128f)
    } else {
        0f
    }
    val shouldUsePopOutPod = insideContainer ||
        cardWidthDp < 248f ||
        cardHeightDp < (keypadHeightDp + 44f)

    val podKeyW = ((podWidthDp - 10f - 9f * 2.5f) / 10.2f).coerceIn(20f, 36f)
    val podQwertyKeyH = (podKeyW * 1.42f).coerceIn(36f, 48f)
    val podT9KeyH = (((podWidthDp - 14f - 6f) / 3f) * 0.46f).coerceIn(32f, 40f)
    val podKeypadH = if (layout == KeyboardCardLayout.T9) {
        (podT9KeyH * 4f + 9f).coerceIn(136f, 170f)
    } else {
        (podQwertyKeyH * qwertyRowUnits + qwertyGapTotal).coerceIn(116f, 208f)
    }
    val podHeightDp = (32f + podKeypadH + 18f).coerceIn(176f, 268f)

    return KeyboardPodDimensionsSpec(
        podWidthDp = podWidthDp,
        candidateMaxStripWidthDp = candidateMaxStripWidthDp,
        qwertyKeyHeightDp = qwertyKeyHeightDp,
        t9KeyHeightDp = t9KeyHeightDp,
        keypadHeightDp = keypadHeightDp,
        inlineCandidateHeightDp = inlineCandidateHeightDp,
        canShowInlineCandidates = canShowInlineCandidates,
        shouldUsePopOutPod = shouldUsePopOutPod,
        podHeightDp = podHeightDp
    )
}
