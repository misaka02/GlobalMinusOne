package com.example.globalminusone.browser

/** Native task lifetime follows ownership/grants, never a transient RPC error or heartbeat delay. */
internal object EmbeddedConnectionPolicy {
    fun mustClose(grantValid: Boolean, binderAlive: Boolean, confirmedClosed: Boolean = false) =
        !grantValid || !binderAlive || confirmedClosed
}
