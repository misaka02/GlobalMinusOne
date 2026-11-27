package com.example.globalminusone.service.overlay

/** A delivered foreground start must be acknowledged even when it only requests a stop. */
internal fun handleOverlayStart(
    startId: Int,
    shouldRun: Boolean,
    assertForeground: () -> Unit,
    prepareWindows: () -> Boolean,
    publishRunning: (Boolean) -> Unit,
    stopById: (Int) -> Boolean,
    onFailure: (Exception) -> Unit
): Boolean {
    try {
        assertForeground()
        if (shouldRun && prepareWindows()) {
            publishRunning(true)
            return true
        }
    } catch (failure: Exception) {
        onFailure(failure)
    }
    publishRunning(false)
    // A newer queued request must not be stopped by an older stop/failure.
    stopById(startId)
    return false
}
