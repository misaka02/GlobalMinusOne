package com.example.globalminusone.system

internal interface RootMainShellApi<S : Any> {
    fun configure(command: String, timeoutSeconds: Long)
    fun main(): S
    fun cached(): S?
    fun aliveRoot(shell: S): Boolean
    fun close(shell: S)
}

/** Both RootService and files use this main-shell builder. Cache loss must fail until an explicit request. */
internal class RootShellBootstrap<S : Any>(private val api: RootMainShellApi<S>) {
    fun explicitOpen(su: String): S {
        normalizeRootPath(su)
        api.configure(su, 30)
        return try {
            api.main().also { if (!api.aliveRoot(it)) { api.close(it); error("su未建立有效uid0会话") } }
        } finally { api.configure("/system/bin/false", 1) }
    }
    fun isCurrent(shell: S): Boolean = api.aliveRoot(shell) && api.cached() === shell
}
