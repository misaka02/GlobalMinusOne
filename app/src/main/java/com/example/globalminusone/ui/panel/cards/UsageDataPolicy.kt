package com.example.globalminusone.ui.panel.cards

internal data class AppUsageTransition(
    val packageName: String?,
    val time: Long,
    val foreground: Boolean,
    val className: String? = null
)

internal data class UsageSummary(val recentPackages: List<String>, val foregroundMillis: Long)

// Track per-component sessions so switching activities inside the same app neither splits nor double-counts time.
internal fun summarizeAppUsage(events: List<AppUsageTransition>, start: Long, end: Long): UsageSummary {
    val activeClasses = mutableMapOf<String, MutableSet<String>>()
    val activeStartedAt = mutableMapOf<String, Long>()
    val lastUsed = mutableMapOf<String, Long>()
    var total = 0L
    fun closePackage(packageName: String, atTime: Long) {
        activeClasses.remove(packageName)
        activeStartedAt.remove(packageName)?.let { resumed ->
            total += (atTime - maxOf(resumed, start)).coerceAtLeast(0L)
        }
    }
    events.sortedBy { it.time }.filter { it.time <= end }.forEach { event ->
        val packageName = event.packageName
        if (packageName == null) {
            activeStartedAt.values.forEach { resumed -> total += (event.time - maxOf(resumed, start)).coerceAtLeast(0L) }
            activeClasses.clear()
            activeStartedAt.clear()
        } else if (event.foreground) {
            activeClasses.getOrPut(packageName) { mutableSetOf() }.add(event.className.orEmpty())
            activeStartedAt.putIfAbsent(packageName, event.time)
            lastUsed[packageName] = event.time
        } else if (event.className == null) {
            closePackage(packageName, event.time)
        } else {
            val classes = activeClasses[packageName]
            if (classes != null) {
                classes.remove(event.className)
                if (classes.isEmpty()) closePackage(packageName, event.time)
            }
        }
    }
    activeStartedAt.values.forEach { total += (end - maxOf(it, start)).coerceAtLeast(0L) }
    return UsageSummary(lastUsed.entries.sortedByDescending { it.value }.map { it.key }, total)
}
