package com.example.globalminusone.system

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/** Shared Shizuku permission checks and explicit authorization flow. */
internal object ShizukuPermissions {
    private val requestCodes = AtomicInteger(31000)

    fun isServiceAvailable(): Boolean =
        runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun isAuthorized(): Boolean = runCatching {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    suspend fun requestAuthorization(timeoutMs: Long = 30_000L): Boolean = withContext(Dispatchers.Main.immediate) {
        if (isAuthorized()) return@withContext true
        if (!isServiceAvailable()) return@withContext false
        val code = requestCodes.incrementAndGet()
        var listener: Shizuku.OnRequestPermissionResultListener? = null
        try {
            withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine { continuation ->
                    listener = Shizuku.OnRequestPermissionResultListener { reqCode, result ->
                        if (reqCode == code && continuation.isActive) {
                            continuation.resume(result == PackageManager.PERMISSION_GRANTED)
                        }
                    }.also(Shizuku::addRequestPermissionResultListener)
                    runCatching { Shizuku.requestPermission(code) }.onFailure {
                        if (continuation.isActive) continuation.resume(false)
                    }
                }
            } == true
        } finally {
            listener?.let(Shizuku::removeRequestPermissionResultListener)
        }
    }
}

/** Shared Shizuku UserService binder lifecycle manager (connect, timeout, cooldown, unbind). */
internal class ShizukuUserServiceClient(
    private val serviceClassName: String,
    private val tag: String,
    private val processSuffix: String,
    private val version: Int,
    private val connectTimeoutMs: Long = 10_000L,
    private val failureCooldownMs: Long = 0L
) {
    @Volatile
    private var binder: IBinder? = null

    @Volatile
    private var connection: ServiceConnection? = null

    @Volatile
    private var boundPackage: String? = null

    @Volatile
    private var lastFailureMs: Long = 0L

    fun peekLiveBinder(): IBinder? = binder?.takeIf { it.isBinderAlive }

    fun clearCooldown() {
        lastFailureMs = 0L
    }

    suspend fun connect(context: Context): IBinder? {
        peekLiveBinder()?.let { return it }
        if (failureCooldownMs > 0L) {
            val prev = lastFailureMs
            if (prev > 0L && SystemClock.elapsedRealtime() - prev < failureCooldownMs) return null
        }
        val appPkg = (context.applicationContext ?: context).packageName
        return withContext(Dispatchers.Main.immediate) {
            peekLiveBinder()?.let { return@withContext it }
            close()
            val args = userServiceArgs(appPkg)
            boundPackage = appPkg
            val bound = withTimeoutOrNull(connectTimeoutMs) {
                suspendCancellableCoroutine<IBinder?> { continuation ->
                    val conn = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName, service: IBinder) {
                            if (continuation.isActive && connection === this) {
                                binder = service
                                lastFailureMs = 0L
                                continuation.resume(service)
                            }
                        }

                        override fun onServiceDisconnected(name: ComponentName) {
                            if (connection === this) {
                                binder = null
                                if (continuation.isActive) continuation.resume(null)
                            }
                        }

                        override fun onNullBinding(name: ComponentName) = onServiceDisconnected(name)
                        override fun onBindingDied(name: ComponentName) = onServiceDisconnected(name)
                    }
                    connection = conn
                    continuation.invokeOnCancellation { close() }
                    runCatching { Shizuku.bindUserService(args, conn) }.onFailure {
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            }
            if (bound == null) {
                if (failureCooldownMs > 0L) lastFailureMs = SystemClock.elapsedRealtime()
                close()
            }
            bound
        }
    }

    fun close() {
        binder = null
        val old = connection
        val pkg = boundPackage
        connection = null
        if (old != null && !pkg.isNullOrBlank()) {
            runCatching { Shizuku.unbindUserService(userServiceArgs(pkg), old, true) }
        }
    }

    suspend fun <T> withOneShotBinder(context: Context, block: suspend (IBinder?) -> T): T {
        val remote = connect(context)
        return try {
            block(remote)
        } finally {
            close()
        }
    }

    private fun userServiceArgs(pkg: String): Shizuku.UserServiceArgs =
        Shizuku.UserServiceArgs(ComponentName(pkg, serviceClassName))
            .daemon(false)
            .tag(tag)
            .processNameSuffix(processSuffix)
            .version(version)
}
