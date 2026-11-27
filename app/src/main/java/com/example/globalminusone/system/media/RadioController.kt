package com.example.globalminusone.system.media

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.example.globalminusone.system.privilege.PrivilegeAction
import com.example.globalminusone.system.privilege.ShizukuRadioSession
import com.example.globalminusone.system.privilege.UserRootSession
import com.example.globalminusone.system.panelVisibilityRequests

class RadioController(context: Context) {
    enum class Backend { PUBLIC, SHIZUKU, ROOT }
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences("radio_control", Context.MODE_PRIVATE)
    private val selectedBackend = MutableStateFlow(runCatching {
        Backend.valueOf(preferences.getString("backend", Backend.PUBLIC.name) ?: Backend.PUBLIC.name)
    }.getOrDefault(Backend.PUBLIC))
    val backend = selectedBackend.asStateFlow()
    private val shizuku = ShizukuRadioSession(app)
    fun selectBackend(value: Backend) {
        cancelBluetoothObservationRefresh()
        selectedBackend.value = value
        preferences.edit().putString("backend", value.name).apply()
        if (value != Backend.SHIZUKU) shizuku.close()
    }
    fun hasShizukuAuthorization() = shizuku.authorized()
    suspend fun requestShizukuAuthorization() = shizuku.requestAuthorization()
    private val wifiManager = app.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val wifiMutable = MutableStateFlow(RadioState.Unknown)
    private val bluetoothMutable = MutableStateFlow(RadioState.Unknown)
    val wifi = wifiMutable.asStateFlow()
    val bluetooth = bluetoothMutable.asStateFlow()
    private val operation = Mutex()
    private var observers = 0
    private val observationScope = CoroutineScope(Dispatchers.Main.immediate)
    private var bluetoothRefreshJob: Job? = null
    private var bluetoothRefreshPending = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Never trust broadcast extras as hardware state.
            refreshWifi()
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) refreshBluetoothPublic()
        }
    }
    private fun supported(kind: RadioKind) = app.packageManager.hasSystemFeature(
        if (kind == RadioKind.WIFI) PackageManager.FEATURE_WIFI else PackageManager.FEATURE_BLUETOOTH)
    fun startObserving() {
        if (observers++ == 0) runCatching { ContextCompat.registerReceiver(app, receiver,
            IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION).apply { addAction(BluetoothAdapter.ACTION_STATE_CHANGED) },
            ContextCompat.RECEIVER_EXPORTED) }
        refreshWifi(); refreshBluetoothPublic()
    }
    fun stopObserving() {
        if (observers > 0 && --observers == 0) {
            cancelBluetoothObservationRefresh()
            runCatching { app.unregisterReceiver(receiver) }
        }
    }
    fun release() {
        cancelBluetoothObservationRefresh()
        if (observers > 0) runCatching { app.unregisterReceiver(receiver) }
        observers = 0; shizuku.close()
    }
    private fun refreshWifi(): RadioState {
        val state = if (!supported(RadioKind.WIFI)) RadioState.Unsupported else runCatching {
            when (wifiManager?.wifiState) {
                WifiManager.WIFI_STATE_ENABLED -> RadioState.On
                WifiManager.WIFI_STATE_DISABLED -> RadioState.Off
                WifiManager.WIFI_STATE_ENABLING -> RadioState.TurningOn
                WifiManager.WIFI_STATE_DISABLING -> RadioState.TurningOff
                else -> RadioState.Unknown
            }
        }.getOrDefault(RadioState.Unknown)
        wifiMutable.value = state
        return state
    }
    @Suppress("MissingPermission")
    private fun readBluetoothPublic(): RadioState {
        if (!supported(RadioKind.BLUETOOTH)) return RadioState.Unsupported
        if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(app, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return RadioState.Unknown
        return runCatching {
            when ((app.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.state) {
                BluetoothAdapter.STATE_ON -> RadioState.On
                BluetoothAdapter.STATE_OFF -> RadioState.Off
                BluetoothAdapter.STATE_TURNING_ON -> RadioState.TurningOn
                BluetoothAdapter.STATE_TURNING_OFF -> RadioState.TurningOff
                else -> RadioState.Unknown
            }
        }.getOrDefault(RadioState.Unknown)
    }
    private fun refreshBluetoothPublic() {
        val state = readBluetoothPublic()
        // Without CONNECT, a broadcast/public read supplies no new state. Keep the
        // privileged observation until refresh() can query the selected backend again.
        val lacksConnect = Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        if (state != RadioState.Unknown || !lacksConnect || backend.value == Backend.PUBLIC) bluetoothMutable.value = state
        else requestBluetoothObservationRefresh()
    }
    private fun canRefreshBluetoothObservation() = observers > 0 && when (backend.value) {
        Backend.SHIZUKU -> shizuku.authorized()
        Backend.ROOT -> UserRootSession.authorizedEpoch() != null
        Backend.PUBLIC -> false
    }
    private fun requestBluetoothObservationRefresh() {
        if (!canRefreshBluetoothObservation()) return
        bluetoothRefreshPending = true
        if (bluetoothRefreshJob?.isActive == true) return
        bluetoothRefreshJob = observationScope.launch {
            do {
                bluetoothRefreshPending = false
                refresh(RadioKind.BLUETOOTH)
            } while (bluetoothRefreshPending && canRefreshBluetoothObservation())
        }
    }
    private fun cancelBluetoothObservationRefresh() {
        bluetoothRefreshPending = false
        bluetoothRefreshJob?.cancel()
        bluetoothRefreshJob = null
    }
    suspend fun refresh(kind: RadioKind): RadioState {
        if (!supported(kind)) return RadioState.Unsupported.also { update(kind, it) }
        var state = if (kind == RadioKind.WIFI) refreshWifi() else readBluetoothPublic()
        if (state == RadioState.Unknown && (backend.value == Backend.SHIZUKU && shizuku.authorized() ||
                    backend.value == Backend.ROOT && UserRootSession.authorizedEpoch() != null)) {
            val result = command(if (kind == RadioKind.WIFI) PrivilegeAction.WIFI_STATE else PrivilegeAction.BLUETOOTH_STATE)
            if (result.first == 0) state = if (kind == RadioKind.WIFI) parseWifiShellState(result.second) else parseBluetoothManagerState(result.second)
        }
        update(kind, state)
        return state
    }
    private fun update(kind: RadioKind, state: RadioState) { if (kind == RadioKind.WIFI) wifiMutable.value = state else bluetoothMutable.value = state }
    private suspend fun command(action: PrivilegeAction, route: Backend = backend.value): Pair<Int, String> = try {
        when (route) {
            Backend.SHIZUKU -> shizuku.run(action)
            // A UI cancellation stops observation, not the shared authorized broker.
            // The fixed radio command has its own eight-second server watchdog.
            Backend.ROOT -> {
                currentCoroutineContext().ensureActive()
                withContext(NonCancellable) { UserRootSession.runPrivilegeAction(action) }
                    .let { (if (it.succeeded) 0 else -1) to if (it.succeeded) it.stdout else it.reason }
            }
            Backend.PUBLIC -> -1 to "系统控制不执行特权命令"
        }
    } catch (cancelled: CancellationException) { throw cancelled }
      catch (error: Exception) { -1 to (error.message ?: "控制服务不可用") }

    @Suppress("DEPRECATION", "MissingPermission")
    private fun publicCommand(kind: RadioKind, enabled: Boolean): Boolean = runCatching {
        if (!supportsPublicRadioToggle(kind, Build.VERSION.SDK_INT,
                Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(app, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)) return@runCatching false
        when {
            kind == RadioKind.WIFI -> wifiManager?.setWifiEnabled(enabled) == true
            kind == RadioKind.BLUETOOTH -> (app.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.let { if (enabled) it.enable() else it.disable() } == true
            else -> false
        }
    }.getOrDefault(false)

    suspend fun setEnabled(kind: RadioKind, enabled: Boolean): RadioControlResult = operation.withLock {
        val route = backend.value
        if (!supported(kind)) return@withLock RadioControlResult(false, "设备没有此控制能力")
        if (route == Backend.ROOT && UserRootSession.authorizedEpoch() == null)
            return@withLock RadioControlResult(false, "请先主动授权本次Root会话")
        if (route == Backend.SHIZUKU && !shizuku.authorized())
            return@withLock RadioControlResult(false, "请先明确授权 Shizuku；未请求 Root")
        if (route == Backend.PUBLIC && kind == RadioKind.BLUETOOTH && Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            app.panelVisibilityRequests().close(app)
            val requested = runCatching {
                app.startActivity(Intent(app, com.example.globalminusone.MainActivity::class.java)
                    .setAction(com.example.globalminusone.MainActivity.ACTION_REQUEST_BLUETOOTH_PERMISSION)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            return@withLock RadioControlResult(false, if (requested) "请先允许附近设备权限以读取蓝牙状态" else "请在应用设置授予附近设备权限")
        }
        if (route == Backend.PUBLIC && !publicCommand(kind, enabled)) {
            return@withLock RadioControlResult(
                false,
                if (openOfficialControl(kind, wantEnable = enabled)) {
                    if (kind == RadioKind.BLUETOOTH && enabled) "已弹出系统蓝牙开启确认框" else "已打开系统控制，请在系统界面确认开关状态"
                } else "系统未提供可用控制入口"
            )
        }
        val action = when (kind) {
            RadioKind.WIFI -> if (enabled) PrivilegeAction.WIFI_ON else PrivilegeAction.WIFI_OFF
            RadioKind.BLUETOOTH -> if (enabled) PrivilegeAction.BLUETOOTH_ON else PrivilegeAction.BLUETOOTH_OFF
        }
        var commandFailure = ""
        withTimeoutOrNull(16_000L) {
            val verified = verifyRadioChange(enabled, command = {
                if (route == Backend.PUBLIC) true else {
                    val result = command(action, route)
                    if (result.first != 0) commandFailure = result.second
                    result.first == 0
                }
            }, observe = { refresh(kind) })
            if (!verified.succeeded && commandFailure.isNotBlank()) verified.copy(message = commandFailure) else verified
        } ?: RadioControlResult(false, "控制等待超时，请刷新系统状态")
    }

    fun openOfficialControl(kind: RadioKind): Boolean = openOfficialControl(kind, wantEnable = null)

    @Suppress("MissingPermission")
    fun openOfficialControl(kind: RadioKind, wantEnable: Boolean?): Boolean = runCatching {
        app.panelVisibilityRequests().close(app)
        if (kind == RadioKind.BLUETOOTH && wantEnable == true) {
            val hasBtConnect = Build.VERSION.SDK_INT < 31 ||
                ContextCompat.checkSelfPermission(app, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            if (hasBtConnect) {
                val requestOk = runCatching {
                    app.startActivity(
                        Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }.isSuccess
                if (requestOk) return@runCatching true
            }
        }
        val action = if (kind == RadioKind.WIFI && Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_WIFI
            else if (kind == RadioKind.WIFI) Settings.ACTION_WIFI_SETTINGS else Settings.ACTION_BLUETOOTH_SETTINGS
        app.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    }.getOrDefault(false)
}
