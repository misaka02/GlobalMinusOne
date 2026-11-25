package com.example.globalminusone.ui.settings

/** Identifies an explicit settings route request with a monotonic nonce so singleTop re-entry always applies. */
data class SettingsNavigationRequest(
    val screen: String,
    val nonce: Long
)

internal object SettingsNavigationPolicy {
    fun buildRequest(
        openScreen: String?,
        nextNonce: () -> Long
    ): SettingsNavigationRequest? {
        val target = openScreen?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return SettingsNavigationRequest(screen = target, nonce = nextNonce())
    }

    fun shouldApply(
        request: SettingsNavigationRequest?,
        lastConsumedNonce: Long
    ): Boolean {
        return request != null && request.screen.isNotBlank() && request.nonce > lastConsumedNonce
    }
}
