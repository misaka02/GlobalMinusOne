package com.example.globalminusone.system

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

@Immutable
data class HardwareStatus(
    val ramUsedGb: Float,
    val ramTotalGb: Float,
    val ramPercent: Int,
    val romUsedGb: Float,
    val romTotalGb: Float,
    val romPercent: Int,
    val batteryLevel: Int,
    val isCharging: Boolean,
    val batteryTempCelsius: Float,
    val batteryVoltageMv: Int
) {
    companion object {
        val Empty = HardwareStatus(0f, 0f, 0, 0f, 0f, 0, 0, false, 0f, 0)
    }
}

object SystemMonitor {

    private const val SAMPLE_INTERVAL_MS = 3_000L
    private const val ROM_CACHE_TTL_MS = 30_000L

    private val samplingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var sharedStatus: StateFlow<HardwareStatus>? = null
    @Volatile private var cachedRomAtMs = -1L
    @Volatile private var cachedRomStats = Triple(0f, 0f, 0)

    /** One IO sampler for all visible cards; no system reads while there are no collectors. */
    fun observe(context: Context): StateFlow<HardwareStatus> {
        return sharedStatus ?: synchronized(this) {
            sharedStatus ?: run {
                val appContext = context.applicationContext
                flow {
                    while (currentCoroutineContext().isActive) {
                        emit(snapshot(appContext))
                        delay(SAMPLE_INTERVAL_MS)
                    }
                }.stateIn(
                    scope = samplingScope,
                    started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0),
                    initialValue = HardwareStatus.Empty
                ).also { sharedStatus = it }
            }
        }
    }

    fun snapshot(context: Context): HardwareStatus {
        // 1. 运行内存 (RAM) 监控
        var ramUsedGb = 0f
        var ramTotalGb = 0f
        var ramPercent = 0
        try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (activityManager != null) {
                val memInfo = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memInfo)
                val totalBytes = memInfo.totalMem
                val availBytes = memInfo.availMem
                val usedBytes = (totalBytes - availBytes).coerceAtLeast(0L)

                val bytesInGb = 1024.0 * 1024.0 * 1024.0
                ramTotalGb = ((totalBytes.toDouble() / bytesInGb * 10.0).roundToInt() / 10.0).toFloat()
                ramUsedGb = ((usedBytes.toDouble() / bytesInGb * 10.0).roundToInt() / 10.0).toFloat()
                ramPercent = if (totalBytes > 0L) {
                    ((usedBytes.toDouble() / totalBytes.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
                } else 0
            }
        } catch (_: Exception) {}

        // 2. 存储空间 (ROM) 监控（30 秒节流缓存，避免每帧重复 StatFs）
        val nowMs = SystemClock.elapsedRealtime()
        val (romUsedGb, romTotalGb, romPercent) = if (cachedRomAtMs >= 0L && nowMs - cachedRomAtMs < ROM_CACHE_TTL_MS) {
            cachedRomStats
        } else {
            var nextUsed = 0f
            var nextTotal = 0f
            var nextPct = 0
            try {
                val statFs = StatFs(Environment.getDataDirectory().path)
                val totalRomBytes = statFs.totalBytes
                val freeRomBytes = statFs.availableBytes
                val usedRomBytes = (totalRomBytes - freeRomBytes).coerceAtLeast(0L)
                val bytesInGb = 1024.0 * 1024.0 * 1024.0
                nextTotal = ((totalRomBytes.toDouble() / bytesInGb * 10.0).roundToInt() / 10.0).toFloat()
                nextUsed = ((usedRomBytes.toDouble() / bytesInGb * 10.0).roundToInt() / 10.0).toFloat()
                nextPct = if (totalRomBytes > 0L) {
                    ((usedRomBytes.toDouble() / totalRomBytes.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
                } else 0
            } catch (_: Exception) {}
            Triple(nextUsed, nextTotal, nextPct).also {
                cachedRomStats = it
                cachedRomAtMs = nowMs
            }
        }

        // 3. 电池与温度状态监控 (通过系统粘性广播实时读取)
        var batteryLevel = 0
        var isCharging = false
        var batteryTempCelsius = 0f
        var batteryVoltageMv = 0
        try {
            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (batteryIntent != null) {
                val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                batteryLevel = if (level >= 0 && scale > 0) {
                    ((level.toFloat() / scale.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
                } else 0

                val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL

                val rawTemp = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                // 系统温度单位为 0.1 摄氏度
                batteryTempCelsius = rawTemp / 10.0f

                batteryVoltageMv = batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
            }
        } catch (_: Exception) {}

        return HardwareStatus(
            ramUsedGb = ramUsedGb,
            ramTotalGb = ramTotalGb,
            ramPercent = ramPercent,
            romUsedGb = romUsedGb,
            romTotalGb = romTotalGb,
            romPercent = romPercent,
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            batteryTempCelsius = batteryTempCelsius,
            batteryVoltageMv = batteryVoltageMv
        )
    }
}
