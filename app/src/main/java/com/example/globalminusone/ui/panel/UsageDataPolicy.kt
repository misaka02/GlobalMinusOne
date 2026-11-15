package com.example.globalminusone.ui.panel

internal data class AppUsageTransition(val packageName: String?, val time: Long, val foreground: Boolean)
internal data class UsageSummary(val recentPackages: List<String>, val foregroundMillis: Long)

// Read from before midnight so an app already foreground at midnight is clipped correctly.
internal fun summarizeAppUsage(events: List<AppUsageTransition>, start: Long, end: Long): UsageSummary {
    val active = mutableMapOf<String, Long>()
    val lastUsed = mutableMapOf<String, Long>()
    var total = 0L
    events.sortedBy { it.time }.filter { it.time <= end }.forEach { event ->
        val packageName = event.packageName
        if (packageName == null) {
            active.values.forEach { resumed -> total += (event.time - maxOf(resumed, start)).coerceAtLeast(0L) }
            active.clear()
        } else if (event.foreground) {
            active.putIfAbsent(packageName, event.time)
            lastUsed[packageName] = event.time
        } else {
            active.remove(packageName)?.let { resumed ->
                total += (event.time - maxOf(resumed, start)).coerceAtLeast(0L)
            }
        }
    }
    active.values.forEach { total += (end - maxOf(it, start)).coerceAtLeast(0L) }
    return UsageSummary(lastUsed.entries.sortedByDescending { it.value }.map { it.key }, total)
}
