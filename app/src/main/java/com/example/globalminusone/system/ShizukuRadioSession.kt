package com.example.globalminusone.system

import android.content.Context
import android.os.IBinder
import android.os.Parcel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Permission is checked, never requested as a side effect of a radio command. */
internal class ShizukuRadioSession(private val app: Context) {
    private val gate = Mutex()
    private val client = ShizukuUserServiceClient(
        serviceClassName = ShizukuRadioService::class.java.name,
        tag = "radio-control",
        processSuffix = "radios",
        version = 1,
        connectTimeoutMs = 10_000L
    )

    fun authorized(): Boolean = ShizukuPermissions.isAuthorized()

    suspend fun requestAuthorization(): Boolean = ShizukuPermissions.requestAuthorization(30_000L)

    suspend fun run(action: PrivilegeAction): Pair<Int, String> = gate.withLock {
        check(authorized()) { "请先明确授权 Shizuku；未请求 Root" }
        val remote = client.connect(app) ?: return@withLock (-1 to "Shizuku 控制服务连接失败")
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

    fun close() = client.close()
}
