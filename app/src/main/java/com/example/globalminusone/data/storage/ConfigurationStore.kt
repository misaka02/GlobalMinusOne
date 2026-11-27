package com.example.globalminusone.data.storage

import android.content.SharedPreferences
import java.util.UUID
import com.example.globalminusone.data.CorruptStateBackup
import com.example.globalminusone.data.GlobalMinusOneState

internal data class LoadedConfiguration(val state: GlobalMinusOneState, val corruptRecovered: Boolean)

/** Existing v5 preferences adapter. The repository owns serialization of calls with its
 * transaction lock; this store owns no edit session, state publication or callbacks to it.
 */
internal class ConfigurationStore(
    private val prefs: SharedPreferences,
    private val sectionEncoder: ConfigurationSectionEncoder = ConfigurationSectionEncoder(),
) {
    private var pendingCorruptRawBackup: String? = null
    private var verifiedCorruptBackup: CorruptStateBackup? = null
    private val configurationWrites = ConfigurationWriteAcceptance(prefs, KEY_STATE_JSON)

    fun readRaw(): String? = prefs.getString(KEY_STATE_JSON, null)

    fun loadInitialState(): LoadedConfiguration {
        sectionEncoder.clear()
        // Reading failures must propagate, rather than masquerading as malformed JSON.
        val raw = readRaw()
        return if (raw == null) {
            LoadedConfiguration(GlobalMinusOneState(), corruptRecovered = false)
        } else {
            val state = try {
                ConfigurationCodec.decodeValidatedState(raw)
            } catch (_: Exception) {
                pendingCorruptRawBackup = raw
                return LoadedConfiguration(GlobalMinusOneState(), corruptRecovered = true)
            }
            LoadedConfiguration(state, corruptRecovered = false)
        }
    }

    fun ensureCorruptStateBackup(): CorruptStateBackup? {
        val raw = pendingCorruptRawBackup ?: return verifiedCorruptBackup
        val key = "${KEY_STATE_JSON}_corrupt_${System.currentTimeMillis()}_${UUID.randomUUID()}"
        check(prefs.edit().putString(key, raw).commit()) { "损坏配置原文备份写入失败，未覆盖原配置" }
        check(prefs.getString(key, null) == raw) { "损坏配置原文备份回读不一致，未覆盖原配置" }
        return CorruptStateBackup(key, raw).also {
            verifiedCorruptBackup = it
            pendingCorruptRawBackup = null
        }
    }

    fun accept(state: GlobalMinusOneState): Result<Unit> = runCatching {
        val prepared = sectionEncoder.prepare(state)
        configurationWrites.accept(
            serialize = { prepared.encoded },
            protectOriginal = { ensureCorruptStateBackup() }
        ).getOrThrow()
        // Cache only successfully accepted sections. The repository still owns the transaction
        // and state publication; this changes neither the v5 value nor apply() durability.
        sectionEncoder.accept(prepared)
    }

    // Preserve edit-before-encode evaluation order and let the owning transaction classify failures.
    fun commitState(state: GlobalMinusOneState): Boolean {
        sectionEncoder.clear()
        return prefs.edit().putString(KEY_STATE_JSON, ConfigurationCodec.encode(state)).commit()
    }

    fun restoreRaw(raw: String?): Boolean {
        sectionEncoder.clear()
        return if (raw == null) prefs.edit().remove(KEY_STATE_JSON).commit()
        else prefs.edit().putString(KEY_STATE_JSON, raw).commit()
    }

    fun clearVerifiedCorruptBackup() {
        verifiedCorruptBackup = null
    }

    private companion object {
        const val KEY_STATE_JSON = "state_json_v5"
    }
}
