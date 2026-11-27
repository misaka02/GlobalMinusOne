package com.example.globalminusone.service.overlay

/** Main-thread coordination only: the service still owns windows, animations and resources.
 * Authorization sources may overlap; only the aggregate edge hides/restores the host.
 * A teardown callback may update state, but must never recreate auxiliary windows.
 */
internal class OverlayWindowCoordination {
    enum class AuthorizationSource { APPLICATION_WINDOWS, SYSTEM_CONTROLS }

    private val authorizationSources = mutableSetOf<AuthorizationSource>()
    var panelClosing = false
        private set
    var remoteOwnsInput = false
        private set
    var authorizationPending = false
        private set
    var authorizationAlpha = 1f
        private set
    var destroying = false
        private set

    fun requestAuthorization(source: AuthorizationSource, pending: Boolean): Boolean {
        if (pending) authorizationSources.add(source) else authorizationSources.remove(source)
        return authorizationSources.isNotEmpty()
    }

    /** Returns true only on an aggregate edge. Never overwrite the saved alpha
     * when a second permission source joins an already hidden host. */
    fun transitionAuthorization(pending: Boolean, currentAlpha: Float): Boolean {
        if (authorizationPending == pending) return false
        authorizationPending = pending
        if (pending && !destroying) authorizationAlpha = currentAlpha
        return true
    }

    fun setRemoteInputOwner(ownsInput: Boolean) { remoteOwnsInput = ownsInput }
    fun beginOpening() { panelClosing = false }
    fun beginClosing() { panelClosing = true }
    fun beginDestroying() { destroying = true }

    fun canRestoreHostFocus(visible: Boolean, progress: Float): Boolean =
        !panelClosing && visible && progress > 0f
}

/** Replace only adjustment bits; preserve state and all other platform bits. */
internal fun replaceWindowInputAdjustment(current: Int, adjustmentMask: Int, adjustment: Int): Int =
    (current and adjustmentMask.inv()) or adjustment
