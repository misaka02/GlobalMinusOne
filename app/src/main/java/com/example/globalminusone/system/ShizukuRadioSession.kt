package com.example.globalminusone.system

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Parcel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/** Permission is checked, never requested as a side effect of a radio command. */
internal class ShizukuRadioSession(private val app: Context) {
    private val gate = Mutex()
    @Volatile private var binder: IBinder? = null
    private var connection: ServiceConnection? = null
    private val args = Shizuku.UserServiceArgs(ComponentName(app.packageName, ShizukuRadioService::class.java.name))
        .daemon(false).tag("radio-control").processNameSuffix("radios").version(1)

    fun authorized() = runCatching { Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)

    suspend fun requestAuthorization(): Boolean = withContext(Dispatchers.Main.immediate) {
        if (authorized()) return@withContext true
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) return@withContext false
        val code = codes.incrementAndGet()
        var listener: Shizuku.OnRequestPermissionResultListener? = null
        try {
            withTimeoutOrNull(30_000) {
                suspendCancellableCoroutine { continuation ->
                    listener = Shizuku.OnRequestPermissionResultListener { request, result ->
                        if (request == code && continuation.isActive) continuation.resume(result == PackageManager.PERMISSION_GRANTED)
                    }.also(Shizuku::addRequestPermissionResultListener)
                    runCatching { Shizuku.requestPermission(code) }.onFailure {
                        if (continuation.isActive) continuation.resume(false)
                    }
                }
            } == true
        } finally { listener?.let(Shizuku::removeRequestPermissionResultListener) }
    }

    suspend fun run(action: PrivilegeAction): Pair<Int, String> = gate.withLock {
        check(authorized()) { "请先明确授权 Shizuku；未请求 Root" }
        val remote = withContext(Dispatchers.Main.immediate) { connect() }
            ?: return@withLock (-1 to "Shizuku 控制服务连接失败")
        withContext(Dispatchers.IO) {
            check(authorized() && remote.isBinderAlive) { "Shizuku 会话已结束" }
            val input = Parcel.obtain(); val output = Parcel.obtain()
            try {
                input.writeInterfaceToken("com.example.globalminusone.Radio.v1")
                input.writeInt(action.ordinal)
                check(remote.transact(IBinder.FIRST_CALL_TRANSACTION, input, output, 0)) { "Shizuku radio protocol unavailable" }
                output.readException()
                output.readInt() to output.readString().orEmpty()
            } finally { input.recycle(); output.recycle() }
        }
    }

    private suspend fun connect(): IBinder? {
        binder?.takeIf { it.isBinderAlive }?.let { return it }
        close()
        val result = withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine<IBinder?> { continuation ->
                val conn = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, service: IBinder) {
                        if (continuation.isActive && connection === this) { binder = service; continuation.resume(service) }
                    }
                    override fun onServiceDisconnected(name: ComponentName) { binder = null; if (continuation.isActive) continuation.resume(null) }
                    override fun onNullBinding(name: ComponentName) { if (continuation.isActive) continuation.resume(null) }
                    override fun onBindingDied(name: ComponentName) { onServiceDisconnected(name) }
                }
                connection = conn
                continuation.invokeOnCancellation { close() }
                runCatching { Shizuku.bindUserService(args, conn) }.onFailure {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }
        if (result == null) close()
        return result
    }
    fun close() {
        binder = null
        val old = connection; connection = null
        if (old != null) runCatching { Shizuku.unbindUserService(args, old, true) }
    }
    companion object { private val codes = AtomicInteger(31000) }
}
