package com.example.globalminusone.system

import kotlin.math.roundToInt

internal fun publicBrightnessPercent(raw: Int?): Int? = raw?.takeIf { it in 1..255 }
    ?.let { ((it - 1) * 100f / 254f).roundToInt() }
internal fun publicBrightnessValue(percent: Int): Int = (1 + percent.coerceIn(0, 100) * 254f / 100f).roundToInt()
enum class BrightnessMode { MANUAL, AUTOMATIC, UNKNOWN }

internal fun brightnessMode(raw: Int?): BrightnessMode = when (raw) {
    0 -> BrightnessMode.MANUAL
    1 -> BrightnessMode.AUTOMATIC
    else -> BrightnessMode.UNKNOWN
}

/** Framework bounds are negative evidence only, never a vendor raw-scale adapter. */
internal class PublicBrightnessScaleGuard {
    private var contradicted = false
    private var rejectedBounds: Pair<Int?, Int?>? = null

    @Synchronized
    fun observeRaw(raw: Int?) {
        if (raw != null && raw > 255) { contradicted = true; rejectedBounds = null }
    }

    @Synchronized
    fun percent(raw: Int?, resourceMin: Int? = null, resourceMax: Int? = null): Int? {
        val bounds = resourceMin to resourceMax
        if ((raw != null && raw > 255) || (resourceMin != null && resourceMin !in 0..255) ||
            (resourceMax != null && resourceMax !in 1..255) ||
            (resourceMin != null && resourceMax != null && resourceMin > resourceMax)) {
            // OEM bounds are not evidence for a safe 1..255 Settings write.
            contradicted = true
            rejectedBounds = bounds
        } else if (contradicted && rejectedBounds != null && rejectedBounds != bounds &&
            raw != null && raw in 1..255 && resourceMin != null && resourceMin in 0..255 && resourceMax == 255) {
            // Recovery requires changed explicit framework scale evidence. A smaller
            // raw value on the same OEM scale must never re-enable a 1..255 write.
            contradicted = false
            rejectedBounds = null
        }
        return if (contradicted) null else publicBrightnessPercent(raw)
    }
}

data class BrightnessState(val percent: Int?, val mode: BrightnessMode, val permitted: Boolean) {
    val automatic: Boolean get() = mode == BrightnessMode.AUTOMATIC
    val modeKnown: Boolean get() = mode != BrightnessMode.UNKNOWN
    val canWrite: Boolean get() = percent != null && percent in 0..100 && mode == BrightnessMode.MANUAL && permitted
}
