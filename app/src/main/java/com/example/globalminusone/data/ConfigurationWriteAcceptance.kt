package com.example.globalminusone.data

import android.content.SharedPreferences

/** An apply() return acknowledges synchronous acceptance, never disk durability.
 * Serialization and protection must finish before touching the active value.
 * Android may change its in-memory map before a listener throws from apply();
 * best-effort asynchronous restoration prevents a rejected update leaking there.
 */
internal class ConfigurationWriteAcceptance(private val prefs: SharedPreferences, private val key: String) {
    fun accept(serialize: () -> String, protectOriginal: () -> Unit): Result<Unit> = runCatching {
        val encoded = serialize()
        protectOriginal()
        val previous = prefs.getString(key, null)
        try {
            prefs.edit().putString(key, encoded).apply()
        } catch (failure: Exception) {
            runCatching {
                if (prefs.getString(key, null) != previous) {
                    val rollback = prefs.edit()
                    if (previous == null) rollback.remove(key) else rollback.putString(key, previous)
                    rollback.apply()
                }
            }.exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        }
    }
}
