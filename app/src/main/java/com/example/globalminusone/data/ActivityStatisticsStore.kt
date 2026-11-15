package com.example.globalminusone.data

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Counts only events observed while the app's accessibility service is connected. */
class ActivityStatisticsStore internal constructor(
    private val prefs: SharedPreferences,
    private val today: () -> LocalDate
) {
    data class DailyCounts(val date: String, val notifications: Int, val unlocks: Int)

    private constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences("activity_statistics", Context.MODE_PRIVATE),
        LocalDate::now
    )
    private val mutable = MutableStateFlow(readToday())
    val counts: StateFlow<DailyCounts> = mutable

    @Synchronized fun refresh(): DailyCounts {
        val date = today().toString()
        if (mutable.value.date != date) mutable.value = readToday(date)
        return mutable.value
    }

    @Synchronized fun recordNotification() = update { it.copy(notifications = it.notifications + 1) }
    @Synchronized fun recordUnlock() = update { it.copy(unlocks = it.unlocks + 1) }

    private fun update(change: (DailyCounts) -> DailyCounts) {
        val next = change(refresh())
        // apply updates the in-process preferences immediately and schedules disk I/O.
        val accepted = runCatching {
            prefs.edit().putString("date", next.date)
                .putInt("notifications", next.notifications)
                .putInt("unlocks", next.unlocks).apply()
            true
        }.getOrDefault(false)
        if (accepted) mutable.value = next
    }

    private fun readToday(date: String = today().toString()): DailyCounts {
        return DailyCounts(date,
            if (prefs.getString("date", null) == date) prefs.getInt("notifications", 0).coerceAtLeast(0) else 0,
            if (prefs.getString("date", null) == date) prefs.getInt("unlocks", 0).coerceAtLeast(0) else 0)
    }

    companion object {
        @Volatile private var instance: ActivityStatisticsStore? = null
        fun get(context: Context): ActivityStatisticsStore = instance ?: synchronized(this) {
            instance ?: ActivityStatisticsStore(context).also { instance = it }
        }
    }
}
