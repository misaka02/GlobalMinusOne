package com.example.globalminusone.service.overlay

/** WM registration is established by successful add/update, not asynchronous View attachment. */
internal fun <T : Any> ensureOverlayWindowRegistered(
    existing: T?,
    create: () -> T,
    update: (T) -> Unit,
    remove: (T) -> Unit,
    add: (T) -> Unit,
    onFailure: (Exception) -> Unit
): T? {
    if (existing != null) {
        try {
            update(existing)
            return existing
        } catch (failure: Exception) {
            onFailure(failure)
            // The view may still be registered but unusable; remove before replacing it.
            try {
                remove(existing)
            } catch (failure: Exception) {
                onFailure(failure)
                // IllegalArgumentException is WM's already-unregistered result.
                // Other removal failures cannot safely create a second root.
                if (failure !is IllegalArgumentException) return null
            }
        }
    }
    val replacement = create()
    return try {
        add(replacement)
        replacement
    } catch (failure: Exception) {
        onFailure(failure)
        // addView may fail after partial registration. Never retain that stale handle.
        try { remove(replacement) } catch (_: Exception) { }
        null
    }
}
