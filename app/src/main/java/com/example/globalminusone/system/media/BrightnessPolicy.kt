package com.example.globalminusone.system.media

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
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

internal const val WINDOW_BRIGHTNESS_OVERRIDE_NONE = -1f

internal fun brightnessPercentToWindowFloat(percent: Int): Float =
    (percent.coerceIn(0, 100) / 100f).coerceIn(0.01f, 1f)

/**
 * Tracks a panel-requested `LayoutParams.screenBrightness` override so video apps (like Bilibili)
 * that override window brightness underneath cannot block panel brightness control, while yielding
 * immediately when auto-brightness, another brightness controller, foreground app switch, or screen-off happens.
 */
internal class ForegroundBrightnessOverrideController {
    private var overrideFloat: Float = WINDOW_BRIGHTNESS_OVERRIDE_NONE
    private var ownerPackage: String = ""
    private var lastWrittenSystemRaw: Int? = null

    @Synchronized
    fun current(): Float = overrideFloat

    @Synchronized
    fun onSliderApplied(percent: Int, writtenSystemRaw: Int?, foregroundPackage: String): Float {
        lastWrittenSystemRaw = writtenSystemRaw
        ownerPackage = foregroundPackage
        overrideFloat = brightnessPercentToWindowFloat(percent)
        return overrideFloat
    }

    @Synchronized
    fun onSystemBrightnessObserved(raw: Int?, mode: BrightnessMode): Float {
        if (overrideFloat >= 0f &&
            (mode == BrightnessMode.AUTOMATIC || (raw != null && lastWrittenSystemRaw != null && raw != lastWrittenSystemRaw))
        ) {
            return clear()
        }
        return overrideFloat
    }

    @Synchronized
    fun onForegroundPackageChanged(newPackage: String): Float {
        if (overrideFloat >= 0f && ownerPackage.isNotBlank() && newPackage.isNotBlank() && newPackage != ownerPackage) {
            return clear()
        }
        return overrideFloat
    }

    @Synchronized
    fun clear(): Float {
        overrideFloat = WINDOW_BRIGHTNESS_OVERRIDE_NONE
        ownerPackage = ""
        lastWrittenSystemRaw = null
        return overrideFloat
    }
}

/** Coalesces slider drag brightness writes onto IO while keeping commit confirmation serialized. */
internal class AsyncBrightnessWriter(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val writePercent: (Int) -> Boolean
) {
    private val mutex = Mutex()
    private val pendingPercent = AtomicInteger(NO_PENDING)
    private var drainJob: Job? = null

    fun enqueue(percent: Int) {
        pendingPercent.set(percent.coerceIn(0, 100))
        ensureDrainRunning()
    }

    fun commit(percent: Int, onResult: (Boolean) -> Unit) {
        val target = percent.coerceIn(0, 100)
        pendingPercent.set(NO_PENDING)
        scope.launch(mainDispatcher) {
            val ok = withContext(ioDispatcher) {
                mutex.withLock {
                    pendingPercent.set(NO_PENDING)
                    writePercent(target)
                }
            }
            onResult(ok)
        }
    }

    @Synchronized
    private fun ensureDrainRunning() {
        if (drainJob?.isActive == true) return
        drainJob = scope.launch(ioDispatcher) {
            try {
                while (true) {
                    val remaining = mutex.withLock {
                        val next = pendingPercent.getAndSet(NO_PENDING)
                        if (next != NO_PENDING) writePercent(next)
                        pendingPercent.get()
                    }
                    if (remaining == NO_PENDING) break
                }
            } finally {
                onDrainFinished()
            }
        }
    }

    @Synchronized
    private fun onDrainFinished() {
        drainJob = null
        if (pendingPercent.get() != NO_PENDING) ensureDrainRunning()
    }

    private companion object {
        const val NO_PENDING = -1
    }
}
