package com.example.globalminusone.data

import kotlin.math.roundToInt

/**
 * Per-card customization for single-icon floating pop-out pods (`1×1` launchers & sliders),
 * persisted compactly inside [ComponentItem.intentUri] query parameters so `state_json_v5`
 * schema and existing backups remain 100% compatible.
 */
data class FloatingPodStyleSpec(
    val widthDp: Int = 0,
    val heightDp: Int = 0,
    val bgColorHex: Long? = null,
    val bgAlphaPercent: Int = -1,
    val textColorHex: Long? = null,
    val cornerRadiusDp: Int = -1
) {
    val isDefault: Boolean
        get() = widthDp <= 0 &&
            heightDp <= 0 &&
            bgColorHex == null &&
            bgAlphaPercent !in 10..100 &&
            textColorHex == null &&
            cornerRadiusDp !in 0..MAX_POD_CORNER_DP

    companion object {
        const val MAX_POD_CORNER_DP = 36
    }
}

data class FloatingPodSizeBounds(
    val minWidthDp: Int,
    val maxWidthDp: Int,
    val defaultWidthDp: Int,
    val minHeightDp: Int,
    val maxHeightDp: Int,
    val defaultHeightDp: Int,
    val widthPresets: List<Int>,
    val heightPresets: List<Int>
)

private val POD_STYLE_KEYS = setOf("pw", "ph", "pbg", "pa", "ptx", "pr")

fun floatingPodSizeBoundsFor(type: ComponentType): FloatingPodSizeBounds = when (type) {
    ComponentType.BRIGHTNESS_BAR, ComponentType.VOLUME_BAR -> FloatingPodSizeBounds(
        minWidthDp = 52,
        maxWidthDp = 160,
        defaultWidthDp = 76,
        minHeightDp = 120,
        maxHeightDp = 380,
        defaultHeightDp = 228,
        widthPresets = listOf(64, 76, 92, 112),
        heightPresets = listOf(180, 228, 280, 340)
    )
    ComponentType.APP_KEYBOARD_LAUNCHER -> FloatingPodSizeBounds(
        minWidthDp = 220,
        maxWidthDp = 420,
        defaultWidthDp = 296,
        minHeightDp = 200,
        maxHeightDp = 480,
        defaultHeightDp = 290,
        widthPresets = listOf(260, 296, 336, 380),
        heightPresets = listOf(240, 280, 320, 380)
    )
    ComponentType.APP_DRAWER_CARD -> FloatingPodSizeBounds(
        minWidthDp = 220,
        maxWidthDp = 420,
        defaultWidthDp = 320,
        minHeightDp = 220,
        maxHeightDp = 620,
        defaultHeightDp = 420,
        widthPresets = listOf(260, 300, 340, 380),
        heightPresets = listOf(300, 380, 460, 540)
    )
    ComponentType.NOTE_CARD -> FloatingPodSizeBounds(
        minWidthDp = 200,
        maxWidthDp = 420,
        defaultWidthDp = 312,
        minHeightDp = 180,
        maxHeightDp = 560,
        defaultHeightDp = 260,
        widthPresets = listOf(256, 312, 356, 400),
        heightPresets = listOf(220, 260, 340, 440)
    )
    ComponentType.RECENT_APPS_CARD -> FloatingPodSizeBounds(
        minWidthDp = 200,
        maxWidthDp = 420,
        defaultWidthDp = 312,
        minHeightDp = 180,
        maxHeightDp = 560,
        defaultHeightDp = 300,
        widthPresets = listOf(256, 312, 356, 400),
        heightPresets = listOf(220, 300, 360, 460)
    )
    ComponentType.APP_WIDGET -> FloatingPodSizeBounds(
        minWidthDp = 200,
        maxWidthDp = 420,
        defaultWidthDp = 312,
        minHeightDp = 140,
        maxHeightDp = 560,
        defaultHeightDp = 220,
        widthPresets = listOf(240, 312, 356, 400),
        heightPresets = listOf(160, 220, 300, 420)
    )
    ComponentType.SINGLE_IMAGE, ComponentType.PHOTO_CUBE -> FloatingPodSizeBounds(
        minWidthDp = 160,
        maxWidthDp = 400,
        defaultWidthDp = 240,
        minHeightDp = 160,
        maxHeightDp = 420,
        defaultHeightDp = 240,
        widthPresets = listOf(180, 220, 260, 320),
        heightPresets = listOf(180, 220, 260, 320)
    )
    ComponentType.STAT_BATTERY_DETAIL -> FloatingPodSizeBounds(
        minWidthDp = 200,
        maxWidthDp = 420,
        defaultWidthDp = 300,
        minHeightDp = 140,
        maxHeightDp = 400,
        defaultHeightDp = 200,
        widthPresets = listOf(240, 280, 320, 360),
        heightPresets = listOf(160, 200, 240, 300)
    )
    ComponentType.HITOKOTO_CARD -> FloatingPodSizeBounds(
        minWidthDp = 200,
        maxWidthDp = 420,
        defaultWidthDp = 300,
        minHeightDp = 120,
        maxHeightDp = 360,
        defaultHeightDp = 180,
        widthPresets = listOf(240, 280, 320, 360),
        heightPresets = listOf(140, 180, 220, 280)
    )
    else -> FloatingPodSizeBounds(
        minWidthDp = 200,
        maxWidthDp = 420,
        defaultWidthDp = 312,
        minHeightDp = 180,
        maxHeightDp = 560,
        defaultHeightDp = 290,
        widthPresets = listOf(256, 312, 356, 400),
        heightPresets = listOf(220, 290, 360, 460)
    )
}

fun parseRgbHexColor(raw: String): Long? {
    val clean = raw.trim().removePrefix("#").uppercase()
    if (clean.length != 6 || clean.any { it !in '0'..'9' && it !in 'A'..'F' }) return null
    val rgb = clean.toLongOrNull(16) ?: return null
    return (0xFF000000L or rgb)
}

fun formatRgbHexColor(argb: Long): String {
    val rgb = argb and 0x00FFFFFFL
    return "#" + rgb.toString(16).uppercase().padStart(6, '0')
}

private fun formatSixCharHex(argb: Long): String =
    (argb and 0x00FFFFFFL).toString(16).uppercase().padStart(6, '0')

private fun splitIntentPrefixAndParams(rawUri: String): Pair<String, MutableList<Pair<String, String>>> {
    val trimmed = rawUri.trim()
    val (prefix, body) = when {
        trimmed.startsWith(AppKeyboardLauncherSpec.URI_PREFIX) ->
            AppKeyboardLauncherSpec.URI_PREFIX to trimmed.removePrefix(AppKeyboardLauncherSpec.URI_PREFIX)
        trimmed.startsWith(AppDrawerCardSpec.URI_PREFIX) ->
            AppDrawerCardSpec.URI_PREFIX to trimmed.removePrefix(AppDrawerCardSpec.URI_PREFIX)
        trimmed.startsWith(GENERIC_CARD_POD_URI_PREFIX) ->
            GENERIC_CARD_POD_URI_PREFIX to trimmed.removePrefix(GENERIC_CARD_POD_URI_PREFIX)
        '?' in trimmed ->
            (trimmed.substringBefore('?') + "?") to trimmed.substringAfter('?')
        ':' in trimmed && !trimmed.contains("://") ->
            (trimmed.substringBefore(':') + ":") to trimmed.substringAfter(':')
        else -> "" to trimmed
    }
    val entries = ArrayList<Pair<String, String>>(8)
    if (body.isNotEmpty()) {
        for (pair in body.split('&')) {
            val eq = pair.indexOf('=')
            if (eq > 0 && eq < pair.length - 1) {
                entries += pair.substring(0, eq) to pair.substring(eq + 1)
            }
        }
    }
    return prefix to entries
}

private fun joinIntentPrefixAndParams(prefix: String, entries: List<Pair<String, String>>): String {
    if (entries.isEmpty()) return ""
    return prefix + entries.joinToString("&") { (k, v) -> "$k=$v" }
}

private fun styleParamPairs(type: ComponentType, style: FloatingPodStyleSpec): List<Pair<String, String>> {
    val bounds = floatingPodSizeBoundsFor(type)
    val pairs = ArrayList<Pair<String, String>>(6)
    if (style.widthDp > 0) {
        pairs += "pw" to style.widthDp.coerceIn(bounds.minWidthDp, bounds.maxWidthDp).toString()
    }
    if (style.heightDp > 0) {
        pairs += "ph" to style.heightDp.coerceIn(bounds.minHeightDp, bounds.maxHeightDp).toString()
    }
    style.bgColorHex?.let { pairs += "pbg" to formatSixCharHex(it) }
    if (style.bgAlphaPercent in 10..100) {
        pairs += "pa" to style.bgAlphaPercent.toString()
    }
    style.textColorHex?.let { pairs += "ptx" to formatSixCharHex(it) }
    if (style.cornerRadiusDp in 0..FloatingPodStyleSpec.MAX_POD_CORNER_DP) {
        pairs += "pr" to style.cornerRadiusDp.toString()
    }
    return pairs
}

fun parseFloatingPodStyle(rawUri: String): FloatingPodStyleSpec {
    if (rawUri.isBlank()) return FloatingPodStyleSpec()
    val (_, entries) = splitIntentPrefixAndParams(rawUri)
    if (entries.isEmpty()) return FloatingPodStyleSpec()
    val map = entries.toMap()
    val pw = map["pw"]?.toIntOrNull()?.takeIf { it > 0 } ?: 0
    val ph = map["ph"]?.toIntOrNull()?.takeIf { it > 0 } ?: 0
    val pbg = map["pbg"]?.let(::parseRgbHexColor)
    val pa = map["pa"]?.toIntOrNull()?.takeIf { it in 10..100 } ?: -1
    val ptx = map["ptx"]?.let(::parseRgbHexColor)
    val pr = map["pr"]?.toIntOrNull()?.takeIf { it in 0..FloatingPodStyleSpec.MAX_POD_CORNER_DP } ?: -1
    return FloatingPodStyleSpec(
        widthDp = pw,
        heightDp = ph,
        bgColorHex = pbg,
        bgAlphaPercent = pa,
        textColorHex = ptx,
        cornerRadiusDp = pr
    )
}

fun withFloatingPodStyle(item: ComponentItem, style: FloatingPodStyleSpec): ComponentItem {
    if (item.type !in POPOUT_CAPABLE_CARD_TYPES) return item
    val (parsedPrefix, parsedEntries) = splitIntentPrefixAndParams(item.intentUri)
    val prefix = when {
        parsedPrefix.isNotEmpty() -> parsedPrefix
        item.type == ComponentType.APP_KEYBOARD_LAUNCHER -> AppKeyboardLauncherSpec.URI_PREFIX
        item.type == ComponentType.APP_DRAWER_CARD -> AppDrawerCardSpec.URI_PREFIX
        else -> GENERIC_CARD_POD_URI_PREFIX
    }
    val baseEntries = if (item.intentUri.isBlank()) {
        when (item.type) {
            ComponentType.APP_KEYBOARD_LAUNCHER -> {
                val baseMode = parseCardPresentationMode(item) ?: LauncherCardPresentationMode.INLINE_CARD
                splitIntentPrefixAndParams(
                    AppKeyboardLauncherSpec.fromComponent(item)
                        .copy(presentationMode = baseMode)
                        .encodeToIntentUri()
                ).second
            }
            ComponentType.APP_DRAWER_CARD -> {
                val baseMode = parseCardPresentationMode(item) ?: LauncherCardPresentationMode.INLINE_CARD
                mutableListOf("mode" to baseMode.name)
            }
            else -> mutableListOf()
        }
    } else {
        parsedEntries.filterNotTo(ArrayList()) { it.first in POD_STYLE_KEYS }
    }
    val stylePairs = styleParamPairs(item.type, style)
    baseEntries.addAll(stylePairs)
    return item.copy(intentUri = joinIntentPrefixAndParams(prefix, baseEntries))
}

fun mergeFloatingPodStyleParams(encodedBaseUri: String, existingRawUri: String): String {
    if (existingRawUri.isBlank()) return encodedBaseUri
    val style = parseFloatingPodStyle(existingRawUri)
    if (style.isDefault) return encodedBaseUri
    val (prefix, baseEntries) = splitIntentPrefixAndParams(encodedBaseUri)
    if (prefix.isEmpty()) return encodedBaseUri
    val filtered = baseEntries.filterNotTo(ArrayList()) { it.first in POD_STYLE_KEYS }
    val (_, existingEntries) = splitIntentPrefixAndParams(existingRawUri)
    existingEntries.filterTo(filtered) { it.first in POD_STYLE_KEYS }
    return joinIntentPrefixAndParams(prefix, filtered)
}

fun resolveEffectivePodSizeDp(
    type: ComponentType,
    style: FloatingPodStyleSpec,
    defaultWidthDp: Float,
    defaultHeightDp: Float,
    hostWidthDp: Float,
    hostHeightDp: Float
): Pair<Float, Float> {
    val safeHostW = hostWidthDp.coerceAtLeast(160f)
    val safeHostH = hostHeightDp.coerceAtLeast(180f)
    val bounds = floatingPodSizeBoundsFor(type)

    val minWFloor = minOf(64f, bounds.minWidthDp.toFloat())
    val maxHostW = (safeHostW - 16f).coerceAtLeast(minWFloor)
    val rawW = if (style.widthDp > 0) {
        style.widthDp.coerceIn(bounds.minWidthDp, bounds.maxWidthDp).toFloat()
    } else if (defaultWidthDp > 0f) {
        defaultWidthDp
    } else {
        (safeHostW * 0.88f).coerceIn(280f, 340f)
    }
    val resolvedW = rawW.coerceIn(minOf(minWFloor, maxHostW), maxHostW)

    val minHFloor = minOf(140f, bounds.minHeightDp.toFloat())
    val maxHostH = (safeHostH - 24f).coerceAtLeast(minHFloor)
    val rawH = if (style.heightDp > 0) {
        style.heightDp.coerceIn(bounds.minHeightDp, bounds.maxHeightDp).toFloat()
    } else if (defaultHeightDp > 0f) {
        defaultHeightDp
    } else {
        (safeHostH * 0.68f).coerceIn(300f, 480f)
    }
    val resolvedH = rawH.coerceIn(minOf(minHFloor, maxHostH), maxHostH)
    return resolvedW to resolvedH
}

fun resolveFloatingPodSurfaceArgb(style: FloatingPodStyleSpec, fallbackSurfaceArgb: Int): Int {
    val alphaPct = if (style.bgAlphaPercent in 10..100) style.bgAlphaPercent else 96
    val alphaByte = ((alphaPct / 100f) * 255f).roundToInt().coerceIn(25, 255)
    val baseRgb = (style.bgColorHex ?: fallbackSurfaceArgb.toLong()) and 0x00FFFFFFL
    return ((alphaByte.toLong() shl 24) or baseRgb).toInt()
}

fun isFloatingPodDarkSurface(
    style: FloatingPodStyleSpec,
    surfaceArgb: Int,
    fallbackDark: Boolean
): Boolean {
    if (style.bgColorHex == null) return fallbackDark
    val alphaByte = (surfaceArgb ushr 24) and 0xFF
    if (alphaByte < 64) return fallbackDark
    return relativeLuminance(surfaceArgb) < 0.48f
}

fun resolveFloatingPodTextArgb(
    style: FloatingPodStyleSpec,
    surfaceArgb: Int,
    fallbackTextArgb: Int,
    fallbackDark: Boolean
): Int {
    style.textColorHex?.let { return ((0xFF000000L or (it and 0x00FFFFFFL))).toInt() }
    if (style.bgColorHex != null) {
        val dark = isFloatingPodDarkSurface(style, surfaceArgb, fallbackDark)
        return if (dark) 0xFFF0F3F8.toInt() else 0xFF1F2329.toInt()
    }
    return fallbackTextArgb
}
