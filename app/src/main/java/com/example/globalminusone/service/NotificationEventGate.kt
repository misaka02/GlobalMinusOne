package com.example.globalminusone.service

/** Bounded, process-local notification deduplication; never retains notification text. */
class NotificationEventGate {
    private val recent = LinkedHashMap<Int, Long>()
    fun accept(key: Int, now: Long): Boolean {
        recent.entries.removeAll { now - it.value >= 30_000L || now < it.value }
        if (recent.containsKey(key)) return false
        if (recent.size >= 128) recent.remove(recent.keys.first())
        recent[key] = now
        return true
    }
    fun clear() = recent.clear()
}
