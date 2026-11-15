package com.example.globalminusone.browser

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import com.example.globalminusone.split.SplitSystemRequest
import com.example.globalminusone.split.SplitSystemResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import java.util.UUID

/** Typed factory operations only; never requests a grant or creates a display/window lease. */
object PrivilegedSplitBackend {
    fun available(context: Context): Boolean = EmbeddedBackendPool.isAuthorized(ChromeSurfaceView.Backend.SHIZUKU)

    suspend fun execute(context: Context, request: SplitSystemRequest): SplitSystemResult = withContext(Dispatchers.Main.immediate) {
        val entry = try { authorizedFactory(context.applicationContext) }
        catch (_: kotlinx.coroutines.TimeoutCancellationException) { return@withContext SplitSystemResult(null, "", "Shizuku 已授权服务连接超时；未请求新授权") }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { return@withContext SplitSystemResult(null, "", "Shizuku 已授权连接不可用；未请求新授权") }
        try {
            withContext(Dispatchers.IO) {
                check(available(context)) { "Shizuku 授权已失效" }
                entry.factory.split(request).also { check(available(context)) { "Shizuku 授权已失效，旧结果已丢弃" } }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { SplitSystemResult(null, "", "Shizuku 分屏连接已失效或系统不支持此操作") }
        finally { withContext(NonCancellable + Dispatchers.Main.immediate) { EmbeddedBackendPool.release(entry) } }
    }

    private suspend fun authorizedFactory(context: Context): EmbeddedBackendPool.Entry {
        check(available(context)) { "Shizuku 未授权" }
        EmbeddedBackendPool.acquire(ChromeSurfaceView.Backend.SHIZUKU)?.let { return it }
        val result = CompletableDeferred<EmbeddedBackendPool.Entry>()
        var adopted: EmbeddedBackendPool.Entry? = null
        var delivered = false
        var finished = false
        val args = Shizuku.UserServiceArgs(ComponentName(context, ShizukuChromeService::class.java))
            .daemon(false).tag("embedded-${UUID.randomUUID()}").processNameSuffix("chrome_surface").version(1)
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) { main.post {
                if (finished) { if (!delivered) EmbeddedBackendPool.unbind(ChromeSurfaceView.Backend.SHIZUKU, this, args); return@post }
                if (result.isCompleted) return@post
                runCatching {
                    check(available(context))
                    EmbeddedBackendPool.adopt(ChromeSurfaceView.Backend.SHIZUKU, binder, this, args, null)
                }.onSuccess { adopted = it; result.complete(it) }.onFailure { result.completeExceptionally(it) }
            } }
            override fun onServiceDisconnected(name: ComponentName) { result.completeExceptionally(IllegalStateException("Shizuku disconnected")) }
            override fun onNullBinding(name: ComponentName) { result.completeExceptionally(IllegalStateException("Shizuku null binding")) }
            override fun onBindingDied(name: ComponentName) { onServiceDisconnected(name) }
        }
        try {
            Shizuku.bindUserService(args, connection)
            return withTimeout(10_000) { result.await() }.also { delivered = true }
        } finally {
            finished = true
            if (!delivered) {
                adopted?.let(EmbeddedBackendPool::release)
                if (adopted == null) EmbeddedBackendPool.unbind(ChromeSurfaceView.Backend.SHIZUKU, connection, args)
            }
        }
    }
}
