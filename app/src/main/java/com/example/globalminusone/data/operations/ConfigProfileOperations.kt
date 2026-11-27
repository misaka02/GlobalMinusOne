package com.example.globalminusone.data.operations

import com.example.globalminusone.data.ConfigProfile
import com.example.globalminusone.data.storage.ConfigurationCodec
import com.example.globalminusone.data.GlobalMinusOneState

/** Snapshot content only; lock checks and write acceptance remain repository responsibilities. */
internal object ConfigProfileOperations {
    fun createSnapshot(
        current: GlobalMinusOneState, id: String, name: String, author: String, description: String, updatedAt: Long,
    ): ConfigProfile = ConfigProfile(
        id = id,
        name = name.ifBlank { "配置方案" },
        author = author,
        description = description,
        updatedAt = updatedAt,
        stateJson = ConfigurationCodec.encode(current.copy(
            profiles = emptyList(), activeProfileId = "default", editMode = false, selectedComponentId = null,
        )),
    )

    fun add(current: GlobalMinusOneState, profile: ConfigProfile): GlobalMinusOneState = current.copy(
        profiles = current.profiles + profile, activeProfileId = profile.id,
    )

    fun decodeSnapshot(profile: ConfigProfile): GlobalMinusOneState? =
        profile.stateJson.takeIf { it.isNotBlank() }?.let(ConfigurationCodec::decodeValidatedState)

    /** Called inside the repository's write transform, after rechecking the current lock/edit state. */
    fun apply(current: GlobalMinusOneState, profileId: String, snapshot: GlobalMinusOneState?): GlobalMinusOneState =
        snapshot?.copy(profiles = current.profiles, activeProfileId = profileId)
            ?: current.copy(activeProfileId = profileId)

    fun delete(profiles: List<ConfigProfile>, profileId: String): List<ConfigProfile> = profiles.filterNot { it.id == profileId }

    fun exportSnapshot(profile: ConfigProfile): String =
        ConfigurationCodec.encode(ConfigurationCodec.decodeValidatedState(profile.stateJson), pretty = true)
}
