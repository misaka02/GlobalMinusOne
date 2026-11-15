package com.example.globalminusone.browser

/** The pool's actual reference/idle policy, separated from Android Binder for deterministic tests. */
internal class EmbeddedLeaseUsage(private val idleMs: Long? = 120_000) {
    var users: Int = 0
        private set
    private var idleDeadline: Long? = null
    private var retired = false
    fun acquire(now: Long): Boolean {
        if (retired || expired(now)) return false
        users++; idleDeadline = null; return true
    }
    fun release(now: Long) {
        if (retired || users == 0) return
        users--
        if (users == 0) idleDeadline = idleMs?.let { now + it }
    }
    fun expired(now: Long): Boolean = retired || (users == 0 && idleDeadline?.let { now >= it } == true)
    fun retire() { retired = true; users = 0; idleDeadline = null }
}
