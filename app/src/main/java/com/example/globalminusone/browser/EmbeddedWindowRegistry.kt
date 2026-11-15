package com.example.globalminusone.browser

/** Live windows are retired only by their owner; opening another target never evicts one. */
internal class EmbeddedWindowRegistry<K, V>(private val limit: Int = 6) {
    private val windows = linkedMapOf<K, V>()
    private val slots = mutableMapOf<K, Int>()
    operator fun get(key: K): V? = windows[key]
    fun values(): List<V> = ArrayList(windows.values)
    fun add(key: K, create: (slot: Int) -> V): V? {
        windows[key]?.let { return it }
        if (windows.size >= limit) return null
        val slot = (0 until limit).first { it !in slots.values }
        val next = create(slot)
        windows[key] = next
        slots[key] = slot
        return next
    }
    fun remove(key: K, expected: V): Boolean {
        if (windows[key] !== expected) return false
        windows.remove(key); slots.remove(key); return true
    }
    fun clear(): List<V> = values().also { windows.clear(); slots.clear() }
}
