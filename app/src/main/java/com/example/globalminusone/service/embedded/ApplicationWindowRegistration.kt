package com.example.globalminusone.service.embedded

/** WindowManager registration starts at addView, before View.isAttachedToWindow.
 * Never use the latter to decide whether an owned window still needs removal. */
internal class ApplicationWindowRegistration {
    var registered = false
        private set

    fun show(add: () -> Unit, update: () -> Unit) {
        if (registered) update()
        else {
            add()
            registered = true
        }
    }

    fun hide(remove: () -> Unit) {
        if (!registered) return
        remove()
        // A rejected removal retains ownership so the next hide can retry.
        registered = false
    }

    fun updateIfRegistered(update: () -> Unit) { if (registered) update() }
}
