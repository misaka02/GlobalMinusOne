package com.example.globalminusone.ui.panel.keyboard

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import com.example.globalminusone.data.policy.computeFrecencyRankOrder

/**
 * Lightweight persisted MRU + launch-frequency store backed by private SharedPreferences so
 * frequently opened apps remain pinned in the closest thumb slots across service restarts.
 */
internal object KeyboardLauncherRecencyStore {
    private const val PREFS_NAME = "gmo_kbd_launcher_recency"
    private const val KEY_RECENTS = "recents_csv"
    private const val KEY_COUNTS = "counts_csv"
    private const val MAX_ITEMS = 36

    private val lock = Any()
    private val recents = ArrayDeque<String>(MAX_ITEMS + 2)
    private val counts = HashMap<String, Int>(MAX_ITEMS + 2)
    private val revision = mutableIntStateOf(0)

    @Volatile
    private var loaded = false

    fun ensureLoaded(context: Context) {
        if (loaded) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawRecents = prefs.getString(KEY_RECENTS, "").orEmpty()
        val rawCounts = prefs.getString(KEY_COUNTS, "").orEmpty()
        synchronized(lock) {
            if (loaded) return
            recents.clear()
            counts.clear()
            rawRecents.split('\n').forEach { line ->
                val pkg = line.trim()
                if (pkg.isNotEmpty() && pkg !in recents && recents.size < MAX_ITEMS) {
                    recents.addLast(pkg)
                }
            }
            rawCounts.split('\n').forEach { entry ->
                val sep = entry.indexOf('=')
                if (sep > 0 && sep < entry.length - 1) {
                    val pkg = entry.substring(0, sep).trim()
                    val count = entry.substring(sep + 1).toIntOrNull() ?: 0
                    if (pkg.isNotEmpty() && count > 0) {
                        counts[pkg] = count.coerceAtMost(9999)
                    }
                }
            }
            loaded = true
            revision.intValue += 1
        }
    }

    fun snapshot(): Pair<Int, List<String>> = synchronized(lock) {
        revision.intValue to computeFrecencyRankOrder(counts, recents.toList(), MAX_ITEMS)
    }

    fun recordLaunch(context: Context, packageName: String) {
        ensureLoaded(context)
        val pkg = packageName.trim()
        if (pkg.isEmpty()) return
        val serializedRecents: String
        val serializedCounts: String
        synchronized(lock) {
            recents.remove(pkg)
            recents.addFirst(pkg)
            while (recents.size > MAX_ITEMS) recents.removeLast()
            counts[pkg] = ((counts[pkg] ?: 0) + 1).coerceAtMost(9999)
            revision.intValue += 1
            serializedRecents = recents.joinToString("\n")
            serializedCounts = counts.entries.joinToString("\n") { (k, v) -> "$k=$v" }
        }
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RECENTS, serializedRecents)
            .putString(KEY_COUNTS, serializedCounts)
            .apply()
    }
}
