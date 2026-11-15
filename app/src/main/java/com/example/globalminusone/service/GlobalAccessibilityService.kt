package com.example.globalminusone.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.ActivityStatisticsStore
import com.example.globalminusone.system.SystemController
import com.example.globalminusone.system.AppSplitLauncher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全局负一屏增强辅助服务：
 * 1. 实时感知当前前台运行的应用包名，用于全屏游戏/黑名单应用的边缘手势自动避让与情景模式；
 * 2. 监听通知栏自动提取验证码并复制到剪贴板；
 * 3. 提供全局快捷动作（返回键、桌面、多任务、切换上个应用、锁屏、截屏、通知栏、快捷设置、电源菜单、分屏等）。
 */
class GlobalAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val notificationGate = NotificationEventGate()
    private val statisticsNotificationGate = NotificationEventGate()
    private val verificationGate = NotificationEventGate()
    private val screenshotExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var systemController: SystemController? = null
    private var activeScenarioPackage: String? = null
    private var savedVolumeBeforeScenario: Int? = null
    private var savedBrightnessBeforeScenario: Int? = null
    private var screenReceiverRegistered = false
    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> restoreScenario()
                Intent.ACTION_USER_PRESENT -> ActivityStatisticsStore.get(this@GlobalAccessibilityService).recordUnlock()
            }
        }
    }
    private fun unregisterScreenReceiver() {
        if (screenReceiverRegistered) runCatching { unregisterReceiver(screenReceiver) }
        screenReceiverRegistered = false
    }
    /** The selected-app route uses a real Activity source, never launch-then-toggle guessing. */
    fun openApplicationInSplit(component: ComponentName): Boolean = AppSplitLauncher.open(this, component)

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _connectedState.value = true
        systemController = SystemController(this)
        if (!screenReceiverRegistered) {
            androidx.core.content.ContextCompat.registerReceiver(this, screenReceiver,
                android.content.IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_USER_PRESENT)
                }, androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
            screenReceiverRegistered = true
        }
        Log.d(TAG, "GlobalAccessibilityService connected")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        restoreScenario()
        unregisterScreenReceiver()
        notificationGate.clear()
        statisticsNotificationGate.clear()
        verificationGate.clear()
        instance = null
        _connectedState.value = false
        systemController?.release()
        systemController = null
        Log.d(TAG, "GlobalAccessibilityService unbound")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        restoreScenario()
        unregisterScreenReceiver()
        notificationGate.clear()
        statisticsNotificationGate.clear()
        verificationGate.clear()
        instance = null
        _connectedState.value = false
        systemController?.release()
        systemController = null
        screenshotExecutor.shutdown()
        Log.d(TAG, "GlobalAccessibilityService destroyed")
        super.onDestroy()
    }

    override fun onInterrupt() {
        Log.w(TAG, "GlobalAccessibilityService interrupted")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val repo = MinusOneRepository.getInstance(this)

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = event.packageName?.toString() ?: return
                val lowerPkg = pkg.lowercase()
                val locked = (getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager)?.isKeyguardLocked == true
                val interactive = (getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager)?.isInteractive != false
                if (locked || !interactive) restoreScenario()
                // 过滤自身、SystemUI 及常见输入法窗口
                if (pkg != "com.example.globalminusone" &&
                    pkg != "com.android.systemui" &&
                    !lowerPkg.contains("inputmethod") &&
                    !lowerPkg.contains("ime")
                ) {
                    try {
                        repo.updateForegroundPackage(pkg)

                        // 检查并应用情景模式（含退出应用后自动恢复先前音量与亮度）
                        val scenarioModes = repo.state.value.advancedConfig.scenarioModes
                        val scenario = scenarioModes.firstOrNull { it.enabled && it.packageName == pkg }
                        val sc = systemController
                        if (sc != null) {
                            if (scenario != null) {
                                if (activeScenarioPackage == null) {
                                    savedVolumeBeforeScenario = sc.getMediaVolumePercent()
                                    savedBrightnessBeforeScenario = sc.getBrightnessPercent()
                                }
                                activeScenarioPackage = pkg
                                if (scenario.volumePercent in 0..100) {
                                    sc.setMediaVolumePercent(scenario.volumePercent)
                                }
                                if (scenario.brightnessPercent in 0..100) {
                                    sc.setBrightnessPercent(scenario.brightnessPercent)
                                }
                            } else if (activeScenarioPackage != null && pkg != activeScenarioPackage) {
                                savedVolumeBeforeScenario?.takeIf { it in 0..100 }?.let { sc.setMediaVolumePercent(it) }
                                savedBrightnessBeforeScenario?.takeIf { it in 0..100 }?.let { sc.setBrightnessPercent(it) }
                                activeScenarioPackage = null
                                savedVolumeBeforeScenario = null
                                savedBrightnessBeforeScenario = null
                            }
                        }
                    } catch (e: Throwable) {
                        Log.e(TAG, "Error handling window state changed: $pkg", e)
                    }
                }
            }
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                try {
                    recordNotificationStatistics(event, repo)
                    handleNotificationPreferences(event, repo)
                    if (repo.state.value.advancedConfig.autoCopyVerificationCode) {
                        handleNotificationVerificationCode(event, repo)
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Error handling notification verification code", e)
                }
            }
        }
    }

    private fun recordNotificationStatistics(event: AccessibilityEvent, repo: MinusOneRepository) {
        val pkg = event.packageName?.toString() ?: return
        val allowed = repo.state.value.advancedConfig.notificationPreferences.allowedPackages
        if (pkg == packageName || pkg !in allowed) return
        val notification = event.parcelableData as? android.app.Notification ?: return
        if (notification.flags and (android.app.Notification.FLAG_ONGOING_EVENT or
                    android.app.Notification.FLAG_FOREGROUND_SERVICE) != 0) return
        val title = notification.extras?.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = notification.extras?.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && body.isBlank() && notification.tickerText.isNullOrBlank()) return
        val fingerprint = listOf(pkg, notification.`when`, title, body, notification.contentIntent).hashCode()
        if (statisticsNotificationGate.accept(fingerprint, android.os.SystemClock.elapsedRealtime())) {
            ActivityStatisticsStore.get(this).recordNotification()
        }
    }

    private fun handleNotificationPreferences(event: AccessibilityEvent, repo: MinusOneRepository) {
        val prefs = repo.state.value.advancedConfig.notificationPreferences
        if (!prefs.reminderEnabled && !prefs.autoJumpEnabled) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg !in prefs.allowedPackages) return
        val notification = event.parcelableData as? android.app.Notification ?: return
        if (notification.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0) return
        val key = listOf(pkg, notification.`when`, notification.contentIntent,
            notification.extras?.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()?.hashCode()).hashCode()
        if (!notificationGate.accept(key, android.os.SystemClock.elapsedRealtime())) return
        if (prefs.reminderEnabled) {
            val label = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
            Toast.makeText(this, "$label 有新通知", Toast.LENGTH_SHORT).show()
            if (prefs.vibrate) runCatching {
                (getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator)?.vibrate(android.os.VibrationEffect.createOneShot(80, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
        val pending = notification.contentIntent
        val interactive = (getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager)?.isInteractive == true
        val locked = (getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager)?.isKeyguardLocked != false
        if (prefs.autoJumpEnabled && pending != null && pending.creatorPackage == pkg &&
            (Build.VERSION.SDK_INT < 31 || pending.isActivity) && interactive && !locked &&
            !repo.state.value.editMode && !repo.isPanelExpanded.value) {
            runCatching {
                val options = if (Build.VERSION.SDK_INT >= 34) {
                    @Suppress("DEPRECATION")
                    val mode = if (Build.VERSION.SDK_INT >= 35) android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                        else android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    android.app.ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(mode).toBundle()
                } else null
                pending.send(this, 0, null, null, null, null, options)
            }.onFailure { Toast.makeText(this, "通知跳转已失效或被系统阻止", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun handleNotificationVerificationCode(event: AccessibilityEvent, repo: MinusOneRepository) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg !in repo.state.value.advancedConfig.notificationPreferences.allowedPackages) return
        val notification = event.parcelableData as? android.app.Notification ?: return
        if (notification.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0) return
        val texts = mutableListOf<CharSequence>()
        event.text?.forEach { texts.add(it) }
        val parcelable = event.parcelableData
        if (parcelable is android.app.Notification) {
            parcelable.tickerText?.let { texts.add(it) }
            parcelable.extras?.let { extras ->
                extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.let { texts.add(it) }
                extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.let { texts.add(it) }
                extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.let { texts.add(it) }
            }
        }
        val fullContent = texts.joinToString(" ")
        val code = extractNotificationVerificationCode(fullContent) ?: return
        if (!verificationGate.accept(listOf(pkg, code).hashCode(), android.os.SystemClock.elapsedRealtime())) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = ClipData.newPlainText("VerificationCode", code)
        if (Build.VERSION.SDK_INT >= 33) clip.description.extras = android.os.PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
        clipboard.setPrimaryClip(clip)
        // OTPs must not enter persistent clipboard history or appear in a Toast preview.
        Toast.makeText(applicationContext, "已自动复制验证码", Toast.LENGTH_SHORT).show()
    }

    private fun restoreScenario() {
        val sc = systemController
        savedVolumeBeforeScenario?.takeIf { it in 0..100 }?.let { sc?.setMediaVolumePercent(it) }
        savedBrightnessBeforeScenario?.takeIf { it in 0..100 }?.let { sc?.setBrightnessPercent(it) }
        activeScenarioPackage = null
        savedVolumeBeforeScenario = null
        savedBrightnessBeforeScenario = null
    }

    // --- 便捷系统级全局动作 ---

    fun performBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    fun performHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    fun performRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)

    fun performLockScreen(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        } else {
            false
        }
    }

    fun performScreenshot(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        } else {
            false
        }
    }

    /**
     * Silently captures the full display buffer into a mutable software ARGB_8888 Bitmap on Android 11+ (API 30+)
     * without triggering the system's full-screen screenshot sound/toast/thumbnail UI or MediaProjection dialogs,
     * so RegionScreenshotOverlay can freeze and crop any custom rectangle interactively.
     */
    fun captureSilentScreenBitmap(
        retryOnRateLimit: Boolean = true,
        onResult: (android.graphics.Bitmap?, Int) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onResult(null, -1)
            return
        }
        try {
            takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                screenshotExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        var hwBitmap: android.graphics.Bitmap? = null
                        val hwBuffer = screenshot.hardwareBuffer
                        try {
                            hwBitmap = android.graphics.Bitmap.wrapHardwareBuffer(hwBuffer, screenshot.colorSpace)
                            val swBitmap = hwBitmap?.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                            if (swBitmap != null) {
                                mainHandler.post { onResult(swBitmap, 0) }
                            } else {
                                mainHandler.post { onResult(null, ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR) }
                            }
                        } catch (t: Throwable) {
                            Log.e(TAG, "Failed converting HardwareBuffer to ARGB_8888 bitmap", t)
                            mainHandler.post { onResult(null, ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR) }
                        } finally {
                            runCatching { hwBuffer.close() }
                            runCatching { hwBitmap?.recycle() }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        if (retryOnRateLimit && errorCode == ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) {
                            mainHandler.postDelayed({
                                captureSilentScreenBitmap(retryOnRateLimit = false, onResult = onResult)
                            }, 450L)
                            return
                        }
                        Log.w(TAG, "takeScreenshot failed with errorCode=$errorCode")
                        mainHandler.post { onResult(null, errorCode) }
                    }
                }
            )
        } catch (t: Throwable) {
            Log.e(TAG, "takeScreenshot threw exception", t)
            onResult(null, -2)
        }
    }

    fun performNotifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    fun performQuickSettings(): Boolean = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)

    fun performPowerDialog(): Boolean = performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)

    fun performSplitScreen(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
        } else {
            false
        }
    }

    fun performLastApp(): Boolean {
        val success = performRecents()
        if (success) {
            mainHandler.postDelayed({
                performRecents()
            }, 120L)
        }
        return success
    }

    companion object {
        private const val TAG = "GlobalAccessibility"

        @Volatile
        var instance: GlobalAccessibilityService? = null

        private val _connectedState = MutableStateFlow(false)
        val connectedState: StateFlow<Boolean> = _connectedState.asStateFlow()

        /**
         * BUG-004 fix: Checks whether the accessibility service is enabled in system settings
         * so the UI can distinguish between "not enabled" vs "enabled in settings but disconnected/killed by OEM".
         */
        fun isEnabledInSystemSettings(context: Context): Boolean = runCatching {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val expectedFull = "${context.packageName}/${GlobalAccessibilityService::class.java.name}"
            val expectedShort = "${context.packageName}/.service.${GlobalAccessibilityService::class.java.simpleName}"
            enabledServices.split(':').any { entry ->
                entry.equals(expectedFull, ignoreCase = true) || entry.equals(expectedShort, ignoreCase = true)
            }
        }.getOrDefault(false)
    }
}
