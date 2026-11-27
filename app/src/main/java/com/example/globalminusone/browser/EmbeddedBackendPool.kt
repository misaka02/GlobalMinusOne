package com.example.globalminusone.browser

import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.example.globalminusone.system.privilege.UserRootSession
import com.topjohnwu.superuser.ipc.RootService
import rikka.shizuku.Shizuku
import java.io.Closeable
import java.util.concurrent.Executors

/** No grant is persisted here. Only an already bound transport with a live authorization is reused. */
internal object EmbeddedBackendPool {
    private val main = Handler(Looper.getMainLooper())
    // Fixed, lazy workers: one backend's synchronous CLOSE cannot strand the other.
    private val cleanup = EmbeddedBackendCleanup(
        Executors.newSingleThreadExecutor { Thread(it, "GmoRootBackendClose").apply { isDaemon = true } },
        Executors.newSingleThreadExecutor { Thread(it, "GmoShizukuBackendClose").apply { isDaemon = true } }
    )
    private val entries = mutableMapOf<ChromeSurfaceView.Backend, Entry>()
    private val retiring = mutableSetOf<IBinder>()
    fun rememberSelection(context: android.content.Context, mode: ChromeSurfaceView.Backend) {
        if (mode != ChromeSurfaceView.Backend.PUBLIC) context.applicationContext
            .getSharedPreferences("embedded_app_backend", android.content.Context.MODE_PRIVATE).edit().putString("backend", mode.name).apply()
    }
    fun authorizedSelection(context: android.content.Context): ChromeSurfaceView.Backend? {
        val name = context.applicationContext.getSharedPreferences("embedded_app_backend", android.content.Context.MODE_PRIVATE).getString("backend", null)
        val mode = ChromeSurfaceView.Backend.entries.firstOrNull { it.name == name } ?: return null
        return mode.takeIf(::isAuthorized)
    }
    fun isAuthorized(mode: ChromeSurfaceView.Backend): Boolean = when (mode) {
            ChromeSurfaceView.Backend.ROOT -> UserRootSession.authorizedEpoch() != null
            ChromeSurfaceView.Backend.SHIZUKU -> runCatching { Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
            else -> false
    }
    class Entry internal constructor(val mode: ChromeSurfaceView.Backend, val factory: ChromeBridge.Client,
        val connection: ServiceConnection, val args: Shizuku.UserServiceArgs?, val epoch: Long?) {
        // A binding is not a grant. Keep this controlled, actually-authorized
        // transport until explicit end/revocation/death; an idle timer must not
        // turn the next window open into another authorization transaction.
        internal val usage = EmbeddedLeaseUsage(idleMs = null)
        internal var expiry: Runnable? = null
        internal var revoke: Closeable? = null
        internal var death: IBinder.DeathRecipient? = null
        internal var shizukuDeath: Shizuku.OnBinderDeadListener? = null
        internal var closing: EmbeddedBackendCleanup.Ticket? = null
    }
    private fun authorized(entry: Entry): Boolean = entry.factory.binder().isBinderAlive && when (entry.mode) {
        ChromeSurfaceView.Backend.ROOT -> entry.epoch?.let(UserRootSession::isAuthorizationCurrent) == true
        ChromeSurfaceView.Backend.SHIZUKU -> runCatching { Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
        else -> false
    }
    private fun requireMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Backend lease state belongs to the main thread" }
    }
    private fun cleanupBackend(mode: ChromeSurfaceView.Backend) = when (mode) {
        ChromeSurfaceView.Backend.ROOT -> EmbeddedBackendCleanup.Backend.ROOT
        ChromeSurfaceView.Backend.SHIZUKU -> EmbeddedBackendCleanup.Backend.SHIZUKU
        ChromeSurfaceView.Backend.PUBLIC -> error("Public sessions do not own a pooled backend")
    }
    private fun recordCloseResult(result: Result<String>) {
        result.exceptionOrNull()?.let { Log.w("GmoBackendCleanup", "Backend cleanup returned an error", it) }
        result.getOrNull()?.takeIf { it.contains("cleanup_unconfirmed") || it.startsWith("closed:") }
            ?.let { Log.w("GmoBackendCleanup", it) }
    }
    internal fun closeSession(owner: Entry, session: ChromeBridge.Client) {
        cleanup.submit(cleanupBackend(owner.mode), { session.close() }, ::recordCloseResult)
    }
    fun acquire(mode: ChromeSurfaceView.Backend): Entry? {
        requireMainThread()
        val entry = entries[mode] ?: return null
        if (!authorized(entry) || !entry.usage.acquire(SystemClock.elapsedRealtime())) { remove(entry); return null }
        entry.expiry?.let(main::removeCallbacks); entry.expiry = null
        return entry
    }
    fun adopt(mode: ChromeSurfaceView.Backend, binder: IBinder, connection: ServiceConnection,
        args: Shizuku.UserServiceArgs?, epoch: Long?): Entry {
        requireMainThread()
        if (binder in retiring) {
            Log.w("GmoBackendCleanup", "Retiring backend: ${cleanup.snapshot(cleanupBackend(mode))}")
            error("上一后端正在结束，请稍后明确重连")
        }
        acquire(mode)?.let { reused ->
            unbind(mode, connection, args, remove = reused.factory.binder() != binder)
            return reused
        }
        val entry = Entry(mode, ChromeBridge.Client(binder), connection, args, epoch)
        entries[mode] = entry; check(entry.usage.acquire(SystemClock.elapsedRealtime()))
        try {
            entry.death = IBinder.DeathRecipient { main.post { remove(entry) } }.also { binder.linkToDeath(it, 0) }
            if (mode == ChromeSurfaceView.Backend.ROOT)
                entry.revoke = UserRootSession.registerRevocationListener { main.post { remove(entry) } }
            if (mode == ChromeSurfaceView.Backend.SHIZUKU)
                entry.shizukuDeath = Shizuku.OnBinderDeadListener { main.post { remove(entry) } }.also(Shizuku::addBinderDeadListener)
            check(authorized(entry)) { "后端授权已失效；未打开窗口" }
        } catch (failure: Exception) { remove(entry); throw failure }
        return entry
    }
    fun release(entry: Entry) {
        requireMainThread()
        if (entries[entry.mode] !== entry) return
        if (!authorized(entry)) { remove(entry); return }
        entry.usage.release(SystemClock.elapsedRealtime())
    }
    /** Explicit service-binding termination; never revokes another feature's global grant. */
    fun end(mode: ChromeSurfaceView.Backend) { requireMainThread(); entries[mode]?.let(::remove) }
    private fun remove(entry: Entry) {
        requireMainThread()
        if (entries[entry.mode] !== entry) return
        entries.remove(entry.mode); entry.expiry?.let(main::removeCallbacks)
        entry.usage.retire()
        retiring += entry.factory.binder()
        entry.revoke?.close(); entry.revoke = null
        entry.shizukuDeath?.let(Shizuku::removeBinderDeadListener); entry.shizukuDeath = null
        entry.death?.let { runCatching { entry.factory.binder().unlinkToDeath(it, 0) } }; entry.death = null
        // Base CLOSE recursively retires all owned child sessions before the binding is dropped.
        entry.closing = cleanup.submit(cleanupBackend(entry.mode), { entry.factory.close() }) { result ->
            // This callback runs only after CLOSE really returns (or cannot be scheduled).
            // A long-running ticket remains retiring; no timeout pretends to cancel Binder.
            try { recordCloseResult(result) } finally {
                main.post {
                    unbind(entry.mode, entry.connection, entry.args); retiring -= entry.factory.binder()
                }
            }
        }
    }
    fun unbind(mode: ChromeSurfaceView.Backend?, connection: ServiceConnection?, args: Shizuku.UserServiceArgs?, remove: Boolean = true) {
        if (connection == null) return
        runCatching { when (mode) {
            ChromeSurfaceView.Backend.ROOT -> RootService.unbind(connection)
            ChromeSurfaceView.Backend.SHIZUKU -> if (args != null) Shizuku.unbindUserService(args, connection, remove)
            else -> Unit
        } }
    }
}
