package com.example.globalminusone

import android.os.Bundle
import android.Manifest
import android.content.Intent
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.globalminusone.data.IconClickBehavior
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.service.OverlayService
import com.example.globalminusone.theme.SettingsTheme
import com.example.globalminusone.ui.settings.GlobalMinusOneMainApp

class MainActivity : ComponentActivity() {

    // Activity receives these only after the focused view declines the key.
    // Use public callbacks rather than androidx.core's restricted dispatcher.
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean =
        com.example.globalminusone.service.isAndroid12CallFallback(
            android.os.Build.VERSION.SDK_INT, keyCode, event.action
        ) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent): Boolean =
        com.example.globalminusone.service.isAndroid12CallFallback(
            android.os.Build.VERSION.SDK_INT, keyCode, event.action
        ) || super.onKeyUp(keyCode, event)

    private lateinit var repository: MinusOneRepository
    private val torchPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Toast.makeText(this, if (granted) "已允许手电筒访问，请点按面板开关" else "相机权限未授予，手电筒未开启", Toast.LENGTH_SHORT).show()
        if (!granted) showPermissionSettings(android.Manifest.permission.CAMERA) else finishPermissionRequest()
    }
    private fun finishPermissionRequest() {
        if (Settings.canDrawOverlays(this) && repository.state.value.triggerConfig.serviceEnabled) {
            OverlayService.start(this, OverlayService.ACTION_OPEN_PANEL)
        }
        finish()
    }
    private val bluetoothPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Toast.makeText(this, if (granted) "已允许读取蓝牙状态，请再次点按开关" else "附近设备权限未授予，可使用系统蓝牙设置", Toast.LENGTH_SHORT).show()
        if (!granted) showPermissionSettings(android.Manifest.permission.BLUETOOTH_CONNECT) else finishPermissionRequest()
    }
    private fun requestBluetoothPermission() {
        OverlayService.start(this, OverlayService.ACTION_CLOSE_PANEL)
        if (android.os.Build.VERSION.SDK_INT >= 31) requestRuntimePermission(Manifest.permission.BLUETOOTH_CONNECT) {
            bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
        else finishPermissionRequest()
    }
    private fun requestTorchPermission() {
        OverlayService.start(this, OverlayService.ACTION_CLOSE_PANEL)
        requestRuntimePermission(Manifest.permission.CAMERA) { torchPermission.launch(Manifest.permission.CAMERA) }
    }
    private fun requestRuntimePermission(permission: String, launch: () -> Unit) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            finishPermissionRequest()
        } else if (shouldShowRequestPermissionRationale(permission)) {
            android.app.AlertDialog.Builder(this).setTitle("允许所需权限")
                .setMessage(if (permission == Manifest.permission.CAMERA) "手电筒开关需要相机权限。" else "读取蓝牙状态需要附近设备权限；Android 13 及以上仍需在系统界面切换。")
                .setPositiveButton("继续") { _, _ -> launch() }
                .setNegativeButton("取消") { _, _ -> finishPermissionRequest() }
                .setOnCancelListener { finishPermissionRequest() }.show()
        } else launch()
    }
    private fun showPermissionSettings(permission: String) {
        if (shouldShowRequestPermissionRationale(permission)) { finishPermissionRequest(); return }
        android.app.AlertDialog.Builder(this).setTitle("权限未授予")
            .setMessage("可在应用设置中手动允许权限，也可以取消。")
            .setPositiveButton("应用设置") { _, _ ->
                runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName"))) }
                finish()
            }.setNegativeButton("取消") { _, _ -> finishPermissionRequest() }
            .setOnCancelListener { finishPermissionRequest() }.show()
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_REQUEST_TORCH_PERMISSION) requestTorchPermission()
        if (intent.action == ACTION_REQUEST_BLUETOOTH_PERMISSION) requestBluetoothPermission()
    }
    companion object {
        const val ACTION_REQUEST_TORCH_PERMISSION = "com.example.globalminusone.action.REQUEST_TORCH_PERMISSION"
        const val ACTION_REQUEST_BLUETOOTH_PERMISSION = "com.example.globalminusone.action.REQUEST_BLUETOOTH_PERMISSION"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = MinusOneRepository.getInstance(this)
        val permissionRequest = intent?.action == ACTION_REQUEST_TORCH_PERMISSION || intent?.action == ACTION_REQUEST_BLUETOOTH_PERMISSION
        if (permissionRequest && savedInstanceState == null) {
            if (intent?.action == ACTION_REQUEST_TORCH_PERMISSION) requestTorchPermission() else requestBluetoothPermission()
        }

        val forceSettings = intent?.getBooleanExtra("force_settings", false) ?: false
        val state = repository.state.value
        val hasOverlayPermission = Settings.canDrawOverlays(this)

        // 支持点击桌面图标行为：直接呼出负一屏或进入设置
        if (!permissionRequest && !forceSettings &&
            state.advancedConfig.iconClickBehavior == IconClickBehavior.OPEN_NOS &&
            hasOverlayPermission && state.triggerConfig.serviceEnabled
        ) {
            OverlayService.start(this, OverlayService.ACTION_TOGGLE_PANEL)
            finish()
            return
        }

        // 若已获得悬浮窗权限且服务主开关已开启，自动启动常驻边缘手势服务
        if (!permissionRequest && hasOverlayPermission && state.triggerConfig.serviceEnabled) {
            OverlayService.start(this)
        }

        enableEdgeToEdge()
        setContent {
            com.example.globalminusone.ui.RepositoryPersistenceFeedback(repository)
            val currentState by repository.state.collectAsState()
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val darkMode = if (currentState.advancedConfig.settingsFollowSystemTheme) {
                systemDark
            } else {
                currentState.advancedConfig.settingsDarkMode
            }
            SideEffect {
                val barStyle = if (darkMode) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
            }
            SettingsTheme(darkTheme = darkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GlobalMinusOneMainApp(repository = repository, onReturnToPanel = {
                        if (Settings.canDrawOverlays(this) && repository.state.value.triggerConfig.serviceEnabled) {
                            OverlayService.start(this, OverlayService.ACTION_OPEN_PANEL)
                        }
                        finish()
                    })
                }
            }
        }
    }
}
