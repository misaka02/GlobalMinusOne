package com.example.globalminusone.system

/** A user-selected launch may trigger the system action once, only for its own foreground app. */
internal class PendingAppSplit(val packageName: String, private val deadline: Long) {
    var pending = true
        private set
    fun acceptWindow(actualPackage: String, now: Long): Boolean {
        if (!pending) return false
        if (now >= deadline) { pending = false; return false }
        if (actualPackage != packageName) return false
        pending = false
        return true
    }
    fun cancel() { pending = false }
}
