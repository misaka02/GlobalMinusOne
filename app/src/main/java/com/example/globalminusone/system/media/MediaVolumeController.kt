package com.example.globalminusone.system.media

import android.media.AudioManager
import android.os.Build
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.roundToInt
import com.example.globalminusone.system.privilege.UserRootSession

internal fun computeMediaVolumeIndex(percent: Int, minVol: Int, maxVol: Int): Int {
    val safeMax = maxOf(minVol, maxVol)
    val span = safeMax - minVol
    if (span <= 0) return minVol
    return (minVol + ((percent.coerceIn(0, 100) / 100f) * span).roundToInt()).coerceIn(minVol, safeMax)
}

internal fun computeMediaVolumePercent(curVol: Int, minVol: Int, maxVol: Int): Int {
    val safeMax = maxOf(minVol, maxVol)
    val span = safeMax - minVol
    if (span <= 0) return 0
    return (((curVol.coerceIn(minVol, safeMax) - minVol).toFloat() / span.toFloat()) * 100f)
        .roundToInt()
        .coerceIn(0, 100)
}

internal class VolumeWriteCoalescer {
    private val pending = AtomicInteger(-1)
    private val active = AtomicBoolean(false)

    /** Queues the latest requested percent and returns true iff this caller must start draining. */
    fun enqueue(percent: Int): Boolean {
        pending.set(percent.coerceIn(0, 100))
        return active.compareAndSet(false, true)
    }

    fun pollNext(): Int = pending.getAndSet(-1)

    fun clearPending() {
        pending.set(-1)
    }

    /** Releases active ownership and re-claims only if another update arrived before release. */
    fun finishPassAndCheckRestart(): Boolean {
        active.set(false)
        return pending.get() >= 0 && active.compareAndSet(false, true)
    }
}

data class MediaVolumeCommitResult(
    val succeeded: Boolean,
    val actualPercent: Int,
    val reason: String = ""
)

internal interface MediaVolumeBackend {
    fun isVolumeFixed(): Boolean
    fun bounds(): Pair<Int, Int>
    fun currentIndex(): Int
    fun setStreamVolume(index: Int): Boolean
    fun adjustStreamVolumeStep(direction: Int): Boolean
    suspend fun setViaAuthorizedRoot(index: Int): Boolean
    fun openSystemVolumePanel()
}

internal class AndroidMediaVolumeBackend(
    private val audioManager: AudioManager
) : MediaVolumeBackend {

    override fun isVolumeFixed(): Boolean =
        runCatching { audioManager.isVolumeFixed }.getOrDefault(false)

    override fun bounds(): Pair<Int, Int> {
        val minVol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(0)
        } else {
            0
        }
        val maxVol = runCatching { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
            .getOrDefault(15)
            .coerceAtLeast(minVol)
        return minVol to maxVol
    }

    override fun currentIndex(): Int =
        runCatching { audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(0)

    override fun setStreamVolume(index: Int): Boolean {
        val (minVol, maxVol) = bounds()
        return runCatching {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
            true
        }.getOrElse { err ->
            if (err is SecurityException && index <= minVol && maxVol > minVol) {
                val safeMin = minOf(maxVol, maxOf(1, minVol))
                runCatching {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, safeMin, 0)
                    true
                }.getOrDefault(false)
            } else {
                false
            }
        }
    }

    override fun adjustStreamVolumeStep(direction: Int): Boolean =
        runCatching {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
            true
        }.getOrDefault(false)

    override suspend fun setViaAuthorizedRoot(index: Int): Boolean {
        if (UserRootSession.authorizedEpoch() == null) return false
        val res = UserRootSession.runBoundedCommand(
            "/system/bin/cmd media_session volume --stream 3 --set $index",
            maxBytes = 4096,
            timeoutMs = 2_500L,
            preserveSessionOnTimeout = true
        )
        return res.exitCode == 0
    }

    override fun openSystemVolumePanel() {
        runCatching {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_SAME,
                AudioManager.FLAG_SHOW_UI
            )
        }
    }
}

internal fun isVolumeReadbackAcceptable(
    before: Int,
    target: Int,
    after: Int,
    minVol: Int,
    maxVol: Int
): Boolean {
    val safeMax = maxOf(minVol, maxVol)
    if (after == target) return true
    if (target == minVol && after <= minOf(safeMax, minVol + 1)) return true
    val span = (safeMax - minVol).coerceAtLeast(0)
    val tolerance = if (span > 30) (span / 15).coerceAtLeast(2) else 1
    if (before == target) return abs(after - target) <= tolerance
    if (after == before) return false
    return abs(after - target) <= tolerance ||
        (target > before && after > before) ||
        (target < before && after < before)
}

/**
 * Single-writer controller for media volume reads, coalesced drag writes, silent fallbacks,
 * and final release verification. Never pops up the system volume panel automatically.
 */
internal class MediaVolumeController(
    private val backend: MediaVolumeBackend,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) {
    private val writeMutex = Mutex()
    private val coalescer = VolumeWriteCoalescer()

    fun isFixed(): Boolean = backend.isVolumeFixed()

    fun getPercent(): Int = runCatching {
        val (minVol, maxVol) = backend.bounds()
        computeMediaVolumePercent(backend.currentIndex(), minVol, maxVol)
    }.getOrDefault(0)

    fun setPercentSync(percent: Int): Boolean {
        if (backend.isVolumeFixed()) return false
        val (minVol, maxVol) = backend.bounds()
        val before = backend.currentIndex()
        val target = computeMediaVolumeIndex(percent, minVol, maxVol)
        if (before == target) return true
        applyTargetIndexSilentSync(before, target)
        val after = backend.currentIndex()
        return isVolumeReadbackAcceptable(before, target, after, minVol, maxVol)
    }

    fun enqueueDragPercent(percent: Int) {
        if (coalescer.enqueue(percent)) {
            launchDrainLoop()
        }
    }

    fun commitPercentAsync(percent: Int, onResult: (MediaVolumeCommitResult) -> Unit) {
        coalescer.clearPending()
        scope.launch(ioDispatcher) {
            val result = writeMutex.withLock {
                val (minVol, maxVol) = backend.bounds()
                val before = backend.currentIndex()
                if (backend.isVolumeFixed()) {
                    val actual = computeMediaVolumePercent(before, minVol, maxVol)
                    return@withLock MediaVolumeCommitResult(
                        succeeded = false,
                        actualPercent = actual,
                        reason = "设备当前为固定音量模式，无法直接调节"
                    )
                }
                val clamped = percent.coerceIn(0, 100)
                val target = computeMediaVolumeIndex(clamped, minVol, maxVol)
                if (before != target) {
                    applyTargetIndexSilentSync(before, target)
                    val afterDirect = backend.currentIndex()
                    if (!isVolumeReadbackAcceptable(before, target, afterDirect, minVol, maxVol)) {
                        backend.setViaAuthorizedRoot(target)
                    }
                }
                val (freshMin, freshMax) = backend.bounds()
                val after = backend.currentIndex()
                val actualPercent = computeMediaVolumePercent(after, freshMin, freshMax)
                val succeeded = isVolumeReadbackAcceptable(before, target, after, freshMin, freshMax)
                MediaVolumeCommitResult(
                    succeeded = succeeded,
                    actualPercent = actualPercent,
                    reason = if (succeeded) "" else "系统限制后台调节媒体音量，可点击音量图标呼出系统滑条"
                )
            }
            withContext(mainDispatcher) {
                onResult(result)
            }
        }
    }

    fun openSystemVolumePanel() {
        backend.openSystemVolumePanel()
    }

    private fun launchDrainLoop() {
        scope.launch(ioDispatcher) {
            var restart = false
            try {
                if (backend.isVolumeFixed()) {
                    coalescer.clearPending()
                    return@launch
                }
                while (true) {
                    val nextPercent = coalescer.pollNext()
                    if (nextPercent < 0) break
                    writeMutex.withLock {
                        val (minVol, maxVol) = backend.bounds()
                        val before = backend.currentIndex()
                        val target = computeMediaVolumeIndex(nextPercent, minVol, maxVol)
                        if (before != target) {
                            applyTargetIndexSilentSync(before, target)
                        }
                    }
                }
            } finally {
                restart = coalescer.finishPassAndCheckRestart()
            }
            if (restart) launchDrainLoop()
        }
    }

    private fun applyTargetIndexSilentSync(before: Int, target: Int) {
        val setCalled = backend.setStreamVolume(target)
        val afterSet = backend.currentIndex()
        if (setCalled && afterSet != before) return
        // Some OEM/ColorOS builds ignore setStreamVolume from overlay services; fall back to
        // silent flag=0 stepped adjust without ever passing FLAG_SHOW_UI.
        stepAdjustTowardTarget(before = afterSet, target = target)
    }

    private fun stepAdjustTowardTarget(before: Int, target: Int) {
        var current = before
        val direction = when {
            target > current -> AudioManager.ADJUST_RAISE
            target < current -> AudioManager.ADJUST_LOWER
            else -> return
        }
        val maxSteps = minOf(24, abs(target - current))
        repeat(maxSteps) {
            if (!backend.adjustStreamVolumeStep(direction)) return
            val next = backend.currentIndex()
            if (next == current) return
            current = next
            if (direction == AudioManager.ADJUST_RAISE && current >= target) return
            if (direction == AudioManager.ADJUST_LOWER && current <= target) return
        }
    }
}
