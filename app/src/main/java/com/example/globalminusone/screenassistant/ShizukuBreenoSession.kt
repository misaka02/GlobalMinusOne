package com.example.globalminusone.screenassistant

import android.content.Context
import android.os.IBinder
import android.os.Parcel
import com.example.globalminusone.system.privilege.RootCommandResult
import com.example.globalminusone.system.privilege.RootStatus
import com.example.globalminusone.system.privilege.ShizukuPermissions
import com.example.globalminusone.system.privilege.ShizukuUserServiceClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Manages the optional Shizuku UserService connection used to invoke verified OEM Breeno
 * recognition entry points without Root. Shares ShizukuUserServiceClient lifecycle guarantees.
 */
internal object ShizukuBreenoSession {
    private val gate = Mutex()
    private val client = ShizukuUserServiceClient(
        serviceClassName = ShizukuBreenoService::class.java.name,
        tag = "breeno-touch",
        processSuffix = "breeno",
        version = 1,
        connectTimeoutMs = 10_000L
    )

    fun serviceAvailable(): Boolean = ShizukuPermissions.isServiceAvailable()

    fun authorized(): Boolean = ShizukuPermissions.isAuthorized()

    suspend fun requestAuthorization(): Boolean = ShizukuPermissions.requestAuthorization(30_000L)

    suspend fun runBreenoDirectCommand(
        context: Context,
        installedPackages: List<String>,
        tileComponent: String? = null,
        serviceComponent: String? = null
    ): RootCommandResult {
        val app = context.applicationContext

        val direct = serviceComponent == BreenoSystemProtocol.component &&
            BreenoSystemProtocol.servicePackage in installedPackages &&
            tileComponent == null
        val validTile = !direct &&
            serviceComponent == null &&
            tileComponent != null &&
            BreenoTilePolicy.validComponent(tileComponent) &&
            tileComponent.substringBefore('/') in installedPackages

        if (!direct && !validTile) {
            return RootCommandResult(
                RootStatus.Error,
                null,
                "",
                "",
                "未找到已核对的小布识屏磁贴或系统服务入口；未执行猜测命令"
            )
        }

        if (!authorized()) {
            val detail = if (serviceAvailable()) {
                "Shizuku 尚未授予当前应用权限"
            } else {
                "Shizuku 服务未运行或未连接"
            }
            return RootCommandResult(RootStatus.Denied, null, "", detail, detail)
        }

        return gate.withLock {
            if (!authorized()) {
                return@withLock RootCommandResult(RootStatus.Denied, null, "", "", "Shizuku 权限已失效")
            }
            val remote = client.connect(app)
                ?: return@withLock RootCommandResult(RootStatus.Error, -1, "", "", "Shizuku 小布识屏服务连接失败")

            runCatching {
                withContext(Dispatchers.IO) {
                    check(authorized() && remote.isBinderAlive) { "Shizuku 会话已结束" }
                    val op = if (direct) ShizukuBreenoService.OP_DIRECT_SERVICE else ShizukuBreenoService.OP_QS_TILE
                    val target = checkNotNull(if (direct) serviceComponent else tileComponent)
                    val input = Parcel.obtain()
                    val output = Parcel.obtain()
                    try {
                        input.writeInterfaceToken(ShizukuBreenoService.DESCRIPTOR)
                        input.writeInt(op)
                        input.writeString(target)
                        check(remote.transact(IBinder.FIRST_CALL_TRANSACTION, input, output, 0)) {
                            "Shizuku 小布识屏通信不可用"
                        }
                        output.readException()
                        val exit = output.readInt()
                        val stdout = output.readString().orEmpty()
                        val stderr = output.readString().orEmpty()
                        val sent = if (direct) {
                            BreenoSystemProtocol.accepted(exit, stdout, stderr)
                        } else {
                            BreenoTilePolicy.requestWasSent(exit, stdout, stderr)
                        }
                        RootCommandResult(
                            status = if (sent) RootStatus.Granted else RootStatus.Error,
                            exitCode = if (sent) 0 else exit.takeUnless { it == 0 },
                            stdout = stdout,
                            stderr = stderr,
                            reason = if (sent) {
                                "已通过 Shizuku 发送系统小布识屏请求，等待系统界面响应"
                            } else {
                                stderr.trim().ifBlank { stdout.trim() }.take(300)
                                    .ifBlank { "Shizuku 调用系统小布识屏失败（退出码 $exit）" }
                            }
                        )
                    } finally {
                        input.recycle()
                        output.recycle()
                    }
                }
            }.getOrElse { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                withContext(Dispatchers.Main.immediate) { close() }
                RootCommandResult(
                    RootStatus.Error,
                    null,
                    "",
                    error.message.orEmpty(),
                    error.message?.take(240)?.ifBlank { null } ?: "Shizuku 调用异常"
                )
            }
        }
    }

    fun close() = client.close()
}
