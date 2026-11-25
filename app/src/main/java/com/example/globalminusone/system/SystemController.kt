package com.example.globalminusone.system

import android.annotation.SuppressLint
import android.Manifest
import android.database.ContentObserver
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.globalminusone.MainActivity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.example.globalminusone.data.GestureAction
import com.example.globalminusone.data.TriggerSide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

internal fun computeMediaVolumeIndex(percent: Int, minVol: Int, maxVol: Int): Int {
    val safeMax = maxOf(minVol, maxVol)
    val span = safeMax - minVol
    if (span <= 0) return minVol
    return (minVol + ((percent.coerceIn(0, 100) / 100f) * span).roundToInt()).coerceIn(minVol, safeMax)
}

internal fun computeMediaVolumePercent(curVol: Int, minVol: Int, maxVol: Int): Int {
    val safeMax = maxOf(minVol, maxVol)
    val span = safeMax - minVol
    if (span <= 0) return 0
    return (((curVol.coerceIn(minVol, safeMax) - minVol).toFloat() / span.toFloat()) * 100f)
        .roundToInt()
        .coerceIn(0, 100)
}

class SystemController(private val context: Context) {
    /**
     * Routes system/trigger Back gestures to the currently focused embedded app floating window (`小窗`)
     * before falling back to global AccessibilityService Back on the outside foreground app.
     */
    @Volatile
    var activeEmbeddedWindowBackHandler: (() -> Boolean)? = null

    val radios = RadioController(context)
    private val rootControlScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var authorizationJob: Job? = null
    private var shizukuAuthorizationJob: Job? = null
    private val externalAuthorizationMutable = MutableStateFlow(false)
    val externalAuthorizationPending = externalAuthorizationMutable.asStateFlow()
    private var authorizationRequests = 0
    private fun beginExternalAuthorization() { authorizationRequests++; externalAuthorizationMutable.value = true }
    private fun endExternalAuthorization() { authorizationRequests = (authorizationRequests - 1).coerceAtLeast(0); externalAuthorizationMutable.value = authorizationRequests > 0 }
    fun requestRootAuthorization(onComplete: (RootCommandResult) -> Unit): Job {
        authorizationJob?.takeIf { it.isActive }?.let { return it }
        return rootControlScope.launch {
            val needsPrompt = UserRootSession.authorizedEpoch() == null
            if (needsPrompt) beginExternalAuthorization()
            val result = try { if (needsPrompt) yield(); UserRootSession.requestAuthorization() }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { RootCommandResult(RootStatus.Error, null, "", "", "Root授权请求失败") }
                finally { if (needsPrompt) endExternalAuthorization() }
            onComplete(result)
        }.also { authorizationJob = it }
    }
    /** Service scope survives temporarily hiding the Compose panel for the prompt. */
    fun requestShizukuAuthorization(onComplete: (Boolean) -> Unit): Job {
        shizukuAuthorizationJob?.takeIf { it.isActive }?.let { return it }
        return rootControlScope.launch {
            val needsPrompt = !radios.hasShizukuAuthorization()
            if (needsPrompt) beginExternalAuthorization()
            val granted = try { if (needsPrompt) yield(); radios.requestShizukuAuthorization() }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { false }
                finally { if (needsPrompt) endExternalAuthorization() }
            onComplete(granted)
        }.also { shizukuAuthorizationJob = it }
    }
    fun cancelShizukuAuthorization() { shizukuAuthorizationJob?.cancel(); shizukuAuthorizationJob = null }
    fun cancelRootAuthorization() {
        authorizationJob?.cancel(); authorizationJob = null
        UserRootSession.revokeAuthorization()
    }
    private val keepAwakeMutable = MutableStateFlow(false)
    val keepAwake = keepAwakeMutable.asStateFlow()
    fun toggleKeepAwake() { keepAwakeMutable.value = !keepAwakeMutable.value }


    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val ringerMutable = MutableStateFlow(getRingerModeLabel())
    val ringerModeLabel = ringerMutable.asStateFlow()
    private val ringerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { ringerMutable.value = getRingerModeLabel() }
    }

    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val brightnessScaleGuard = PublicBrightnessScaleGuard()
    private val brightnessMutable = MutableStateFlow(readBrightnessState())
    val brightness = brightnessMutable.asStateFlow()
    private val brightnessObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) { refreshBrightness() }
    }

    private fun targetTorchCameraId(): String? = runCatching {
        val cameras = cameraManager.cameraIdList.map { it to cameraManager.getCameraCharacteristics(it) }
            .filter { it.second.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        cameras.firstOrNull { it.second.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK }
            ?.first ?: cameras.firstOrNull()?.first
    }.getOrNull()
    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == targetTorchCameraId()) _isTorchOn.value = enabled
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            if (cameraId == targetTorchCameraId()) _isTorchOn.value = false
        }
    }

    init {
        runCatching {
            listOf(Settings.System.SCREEN_BRIGHTNESS, Settings.System.SCREEN_BRIGHTNESS_MODE).forEach {
                context.contentResolver.registerContentObserver(Settings.System.getUriFor(it), false, brightnessObserver)
            }
        }
        runCatching { ContextCompat.registerReceiver(context, ringerReceiver,
            IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION), ContextCompat.RECEIVER_EXPORTED) }
        try {
            cameraManager.registerTorchCallback(torchCallback, mainHandler)
        } catch (e: Exception) {
            Log.w("SystemController", "Failed to register torch callback", e)
        }
    }

    fun release() {
        context.contentResolver.unregisterContentObserver(brightnessObserver)
        rootControlScope.cancel()
        radios.release()
        runCatching { context.unregisterReceiver(ringerReceiver) }
        try {
            cameraManager.unregisterTorchCallback(torchCallback)
        } catch (e: Exception) {
            Log.w("SystemController", "Failed to unregister torch callback", e)
        }
    }

    /**
     * 统一手势动作执行器
     */
    fun executeGestureAction(action: GestureAction, onOpenPanelSide: (TriggerSide) -> Unit): Boolean {
        return when (action) {
            GestureAction.NONE -> false
            GestureAction.LEFT_OPEN_NOS -> {
                onOpenPanelSide(TriggerSide.LEFT)
                true
            }
            GestureAction.RIGHT_OPEN_NOS -> {
                onOpenPanelSide(TriggerSide.RIGHT)
                true
            }
            GestureAction.BACK -> performBack()
            GestureAction.HOME -> {
                val service = context.currentSystemAccessibilityActions()
                if (service != null && service.performHome()) {
                    true
                } else {
                    goHome()
                    true
                }
            }
            GestureAction.RECENT -> openRecentApps()
            GestureAction.LAST_APP -> performLastApp()
            GestureAction.NOTIFICATION -> expandNotifications()
            GestureAction.QUICK_SETTINGS -> expandQuickSettings()
            GestureAction.LOCK -> lockScreen()
            GestureAction.POWER -> performPowerDialog()
            GestureAction.SCREENSHOT -> takeScreenshot()
            GestureAction.REGION_SCREENSHOT -> takeRegionScreenshot()
            GestureAction.SCREEN_ASSISTANT -> launchScreenAssistant()
            GestureAction.BREENO_TOUCH -> launchBreenoTouch()
        }
    }

    /**
     * 手电筒开关控制
     */
    fun canControlTorch(): Boolean = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    private fun closePanelForSystemUi() {
        context.panelVisibilityRequests().close(context)
    }
    fun requestTorchPermission() {
        closePanelForSystemUi()
        context.startActivity(Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_REQUEST_TORCH_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    fun toggleTorch(): Boolean {
        if (!canControlTorch()) return false
        return try {
            val targetCameraId = targetTorchCameraId()

            if (targetCameraId != null) {
                val nextState = !_isTorchOn.value
                cameraManager.setTorchMode(targetCameraId, nextState)
                // The torch callback, not an accepted request, supplies the displayed state.
                true
            } else {
                Toast.makeText(context, "未找到可用闪光灯", Toast.LENGTH_SHORT).show()
                false
            }
        } catch (e: Exception) {
            Log.e("SystemController", "Failed to toggle torch", e)
            Toast.makeText(context, "手电筒开关失败: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * 音量控制：媒体音量 (0..100)
     */
    private fun mediaVolumeBounds(): Pair<Int, Int> {
        val minVol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(0)
        } else {
            0
        }
        val maxVol = runCatching { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
            .getOrDefault(15)
            .coerceAtLeast(minVol)
        return minVol to maxVol
    }

    fun isMediaVolumeFixed(): Boolean = runCatching { audioManager.isVolumeFixed }.getOrDefault(false)

    fun getMediaVolumePercent(): Int {
        return try {
            val (minVol, maxVol) = mediaVolumeBounds()
            val curVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            computeMediaVolumePercent(curVolume, minVol, maxVol)
        } catch (_: Exception) {
            0
        }
    }

    fun setMediaVolumePercent(percent: Int): Boolean {
        return try {
            if (isMediaVolumeFixed()) return false
            val (minVol, maxVol) = mediaVolumeBounds()
            cachedVolumeBounds = minVol to maxVol
            val targetIndex = computeMediaVolumeIndex(percent, minVol, maxVol)
            applyMediaVolumeIndexDirect(targetIndex, minVol, maxVol)
        } catch (e: Exception) {
            Log.e("SystemController", "Failed to set media volume", e)
            false
        }
    }

    private val pendingVolumePercent = AtomicInteger(-1)
    private val volumeWriterActive = AtomicBoolean(false)
    @Volatile
    private var cachedVolumeBounds: Pair<Int, Int>? = null
    @Volatile
    private var lastWrittenVolumeIndex: Int = Int.MIN_VALUE

    private fun applyMediaVolumeIndexDirect(targetIndex: Int, minVol: Int, maxVol: Int): Boolean {
        return runCatching {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetIndex, 0)
            true
        }.getOrElse { err ->
            if (err is SecurityException && targetIndex == 0 && maxVol > minVol) {
                val safeMin = minOf(maxVol, maxOf(1, minVol))
                runCatching {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, safeMin, 0)
                    true
                }.getOrDefault(false)
            } else {
                false
            }
        }
    }

    /**
     * Non-blocking volume dispatch for continuous slider dragging: coalesces rapid UI changes and
     * performs quantized AudioManager Binder IPC exclusively on Dispatchers.IO.
     */
    fun setMediaVolumePercentAsync(percent: Int) {
        pendingVolumePercent.set(percent.coerceIn(0, 100))
        if (!volumeWriterActive.compareAndSet(false, true)) return
        rootControlScope.launch(Dispatchers.IO) {
            try {
                if (isMediaVolumeFixed()) {
                    pendingVolumePercent.set(-1)
                    return@launch
                }
                while (true) {
                    val nextPercent = pendingVolumePercent.getAndSet(-1)
                    if (nextPercent < 0) break
                    val (minVol, maxVol) = cachedVolumeBounds ?: mediaVolumeBounds().also { cachedVolumeBounds = it }
                    val targetIndex = computeMediaVolumeIndex(nextPercent, minVol, maxVol)
                    if (targetIndex != lastWrittenVolumeIndex) {
                        if (applyMediaVolumeIndexDirect(targetIndex, minVol, maxVol)) {
                            lastWrittenVolumeIndex = targetIndex
                        }
                    }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                Log.w("SystemController", "Async media volume update failed", e)
            } finally {
                volumeWriterActive.set(false)
                if (pendingVolumePercent.get() >= 0 && volumeWriterActive.compareAndSet(false, true)) {
                    val remaining = pendingVolumePercent.getAndSet(-1)
                    if (remaining >= 0) setMediaVolumePercentAsync(remaining) else volumeWriterActive.set(false)
                }
            }
        }
    }

    /**
     * Final release commit on Dispatchers.IO with verified readback and optional Root fallback.
     */
    fun commitMediaVolumePercentAsync(percent: Int, onFallbackNeeded: () -> Unit) {
        pendingVolumePercent.set(-1)
        rootControlScope.launch(Dispatchers.IO) {
            val fixed = isMediaVolumeFixed()
            val (minVol, maxVol) = mediaVolumeBounds().also { cachedVolumeBounds = it }
            val targetIndex = computeMediaVolumeIndex(percent.coerceIn(0, 100), minVol, maxVol)
            val directOk = !fixed && applyMediaVolumeIndexDirect(targetIndex, minVol, maxVol)
            if (directOk) {
                lastWrittenVolumeIndex = targetIndex
                delay(70L)
            }
            val readback = runCatching { audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(targetIndex)
            var adjusted = !fixed && directOk && (readback == targetIndex || (targetIndex == 0 && readback <= 1))
            if (!adjusted && !fixed && UserRootSession.authorizedEpoch() != null) {
                val res = UserRootSession.runBoundedCommand(
                    "/system/bin/cmd media_session volume --stream 3 --set $targetIndex",
                    maxBytes = 4096,
                    timeoutMs = 2_500L,
                    preserveSessionOnTimeout = true
                )
                val afterRoot = runCatching { audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(readback)
                adjusted = res.exitCode == 0 || afterRoot == targetIndex
            }
            if (!adjusted || fixed) {
                withContext(Dispatchers.Main) { onFallbackNeeded() }
            }
        }
    }

    fun openSystemVolumePanel() {
        runCatching {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_SAME,
                AudioManager.FLAG_SHOW_UI
            )
        }
    }

    /**
     * 音量控制：铃声音量 (0..100)
     */
    fun getRingVolumePercent(): Int {
        return try {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
            val curVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)
            if (maxVolume > 0) {
                ((curVolume.toFloat() / maxVolume.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
            } else 0
        } catch (e: Exception) {
            0
        }
    }

    fun setRingVolumePercent(percent: Int) {
        try {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
            val targetVolume = ((percent.coerceIn(0, 100) / 100f) * maxVolume).roundToInt().coerceIn(0, maxVolume)
            audioManager.setStreamVolume(AudioManager.STREAM_RING, targetVolume, 0)
        } catch (e: Exception) {
            Log.e("SystemController", "Failed to set ring volume", e)
        }
    }

    /**
     * 亮度与设置修改权限控制
     */
    fun canWriteSettings(): Boolean = Settings.System.canWrite(context)

    fun requestWriteSettingsPermission() {
        try {
            context.panelVisibilityRequests().close(context)
            val intent = Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Toast.makeText(context, "请先授予修改系统设置权限", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("SystemController", "Failed to open write settings permission screen", e)
        }
    }

    fun getBrightnessPercent(): Int? = readBrightnessState().percent

    private fun readBrightnessRaw(): Int? = runCatching {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    }.getOrNull().also { brightnessScaleGuard.observeRaw(it) }
    private fun readBrightnessResourceBound(name: String): Int? = runCatching {
        val id = context.resources.getIdentifier(name, "integer", "android")
        if (id == 0) null else context.resources.getInteger(id)
    }.getOrNull()
    private fun readBrightnessMode(): BrightnessMode = brightnessMode(runCatching {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
    }.getOrNull())
    private fun readBrightnessState() = BrightnessState(
        brightnessScaleGuard.percent(readBrightnessRaw(),
            readBrightnessResourceBound("config_screenBrightnessSettingMinimum"),
            readBrightnessResourceBound("config_screenBrightnessSettingMaximum")),
        readBrightnessMode(), canWriteSettings())
    fun refreshBrightness() { brightnessMutable.value = readBrightnessState() }
    fun useManualBrightness(): Boolean {
        // Changing the mode is explicit. Never write a guessed old level on entry.
        val current = readBrightnessState()
        brightnessMutable.value = current
        if (!current.permitted || current.percent == null || !current.modeKnown) return false
        if (current.mode == BrightnessMode.MANUAL) return true
        val changed = runCatching { Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) }.getOrDefault(false)
        refreshBrightness(); return changed && brightnessMutable.value.mode == BrightnessMode.MANUAL
    }

    fun setBrightnessPercent(percent: Int): Boolean {
        val current = readBrightnessState()
        brightnessMutable.value = current
        if (!current.canWrite) return false
        return try {
            val brightnessValue = publicBrightnessValue(percent)
            val written = Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                brightnessValue
            )
            refreshBrightness()
            written && readBrightnessRaw() == brightnessValue
        } catch (e: Exception) {
            Log.e("SystemController", "Failed to set brightness", e)
            false
        }
    }

    // Compatibility query for the existing auto-mode tile, never a manual-write gate.
    fun isAutoBrightnessEnabled(): Boolean = readBrightnessMode() == BrightnessMode.AUTOMATIC

    fun toggleAutoBrightness(): Boolean {
        if (!canWriteSettings()) {
            requestWriteSettingsPermission()
            return false
        }
        return try {
            val current = readBrightnessState()
            brightnessMutable.value = current
            val nextMode = when (current.mode) {
                BrightnessMode.AUTOMATIC -> Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                BrightnessMode.MANUAL -> Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                BrightnessMode.UNKNOWN -> return false
            }
            val changed = Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, nextMode)
            refreshBrightness()
            changed && brightnessMutable.value.mode == brightnessMode(nextMode)
        } catch (e: Exception) {
            Log.e("SystemController", "Failed to toggle auto brightness", e)
            false
        }
    }

    fun isAutoRotateEnabled(): Boolean {
        return try {
            val mode = Settings.System.getInt(
                context.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION
            )
            mode == 1
        } catch (e: Exception) {
            false
        }
    }

    fun toggleAutoRotate(): Boolean {
        if (!canWriteSettings()) {
            requestWriteSettingsPermission()
            return false
        }
        return try {
            val nextMode = if (isAutoRotateEnabled()) 0 else 1
            Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, nextMode) &&
                isAutoRotateEnabled() == (nextMode == 1)
        } catch (e: Exception) {
            Log.e("SystemController", "Failed to toggle auto rotate", e)
            false
        }
    }

    /**
     * 响铃模式
     */
    fun getRingerModeLabel(): String {
        return when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> "响铃"
            AudioManager.RINGER_MODE_VIBRATE -> "震动"
            AudioManager.RINGER_MODE_SILENT -> "静音"
            else -> "响铃"
        }
    }

    fun cycleRingerMode(): String {
        val next = when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
            AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        setRingerModeConfirmed(next)
        return getRingerModeLabel()
    }

    fun toggleMute() { setRingerModeConfirmed(if (audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT)
        AudioManager.RINGER_MODE_NORMAL else AudioManager.RINGER_MODE_SILENT) }
    fun toggleVibrate() { setRingerModeConfirmed(if (audioManager.ringerMode == AudioManager.RINGER_MODE_VIBRATE)
        AudioManager.RINGER_MODE_NORMAL else AudioManager.RINGER_MODE_VIBRATE) }
    private fun setRingerModeConfirmed(mode: Int): Boolean = try {
        audioManager.ringerMode = mode
        ringerMutable.value = getRingerModeLabel()
        (audioManager.ringerMode == mode).also { if (!it) Toast.makeText(context, "声音模式未变更", Toast.LENGTH_SHORT).show() }
    } catch (_: SecurityException) {
        Toast.makeText(context, "系统未允许修改此声音模式，未进行切换", Toast.LENGTH_SHORT).show(); false
    } catch (_: Exception) {
        Toast.makeText(context, "声音模式切换失败", Toast.LENGTH_SHORT).show(); false
    }

    fun promptAccessibilityPermission() {
        val autoJump = com.example.globalminusone.data.MinusOneRepository.getInstance(context)
            .state.value.advancedConfig.autoJumpAccessibility
        Toast.makeText(context,
            if (autoJump) "请先开启辅助服务，即将打开设置" else "请先在系统设置中开启辅助服务",
            Toast.LENGTH_SHORT).show()
        if (!autoJump) return
        closePanelForSystemUi()
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("SystemController", "Failed to open accessibility settings", e)
        }
    }

    fun performBack(): Boolean {
        if (activeEmbeddedWindowBackHandler?.invoke() == true) {
            return true
        }
        val service = context.currentSystemAccessibilityActions()
        return if (service != null) {
            service.performBack()
        } else {
            promptAccessibilityPermission()
            false
        }
    }

    fun performSplitScreen(): Boolean {
        val service = context.currentSystemAccessibilityActions()
        return if (service != null) {
            service.performSplitScreen()
        } else {
            promptAccessibilityPermission()
            false
        }
    }

    fun performPowerDialog(): Boolean {
        val service = context.currentSystemAccessibilityActions()
        return if (service != null) {
            service.performPowerDialog()
        } else {
            promptAccessibilityPermission()
            false
        }
    }

    fun performLastApp(): Boolean {
        val service = context.currentSystemAccessibilityActions()
        return if (service != null) {
            service.performLastApp()
        } else {
            promptAccessibilityPermission()
            false
        }
    }

    fun lockScreen(): Boolean {
        val service = context.currentSystemAccessibilityActions()
        return if (service != null) {
            service.performLockScreen()
        } else {
            promptAccessibilityPermission()
            false
        }
    }

    fun takeScreenshot(): Boolean {
        val service = context.currentSystemAccessibilityActions()
        return if (service != null) {
            service.performScreenshot()
        } else {
            promptAccessibilityPermission()
            false
        }
    }

    /**
     * Triggers GlobalMinusOne's interactive Region Screenshot (`区域截屏`) overlay via OverlayService
     * instead of calling the OS full-screen screenshot action.
     */
    fun takeRegionScreenshot(): Boolean {
        val hasPrivilege = context.screenToolRequests().supportsPrivilegedScreenCapture(context)
        if (context.currentSystemAccessibilityActions() == null && !hasPrivilege) {
            closePanelForSystemUi()
            promptAccessibilityPermission()
            return false
        }
        if (!Settings.canDrawOverlays(context)) {
            Toast.makeText(context, "区域截屏需要悬浮窗权限", Toast.LENGTH_SHORT).show()
            return false
        }
        return context.screenToolRequests().regionScreenshot(context)
    }

    /** Triggers GlobalMinusOne's built-in Screen Assistant (识字 / 原位翻译 / 提图). */
    fun launchScreenAssistant(): Boolean {
        val hasPrivilege = context.screenToolRequests().supportsPrivilegedScreenCapture(context)
        if (context.currentSystemAccessibilityActions() == null && !hasPrivilege) {
            closePanelForSystemUi()
            promptAccessibilityPermission()
            return false
        }
        if (!Settings.canDrawOverlays(context)) {
            Toast.makeText(context, "屏幕助手需要悬浮窗权限", Toast.LENGTH_SHORT).show()
            return false
        }
        return context.screenToolRequests().screenAssistant(context)
    }

    /** Requests the actual ColorOS screen-recognition entry; never substitutes the built-in assistant. */
    fun launchBreenoTouch(): Boolean {
        context.screenToolRequests().breenoTouch(context)
        return true
    }

    fun expandNotifications(): Boolean {
        val service = context.currentSystemAccessibilityActions()
        if (service != null && service.performNotifications()) {
            return true
        }
        return try {
            @SuppressLint("WrongConstant")
            val statusBarService = context.getSystemService("statusbar")
            val statusBarManager = Class.forName("android.app.StatusBarManager")
            val expandMethod = statusBarManager.getMethod("expandNotificationsPanel")
            expandMethod.invoke(statusBarService)
            true
        } catch (e: Exception) {
            Log.w("SystemController", "Failed to expand notifications panel via reflection", e)
            if (service == null) {
                promptAccessibilityPermission()
            }
            false
        }
    }

    fun expandQuickSettings(): Boolean {
        val service = context.currentSystemAccessibilityActions()
        if (service != null && service.performQuickSettings()) {
            return true
        }
        try {
            @SuppressLint("WrongConstant")
            val statusBarService = context.getSystemService("statusbar")
            val statusBarManager = Class.forName("android.app.StatusBarManager")
            val expandMethod = statusBarManager.getMethod("expandSettingsPanel")
            expandMethod.invoke(statusBarService)
            return true
        } catch (_: Exception) {}

        if (service == null) {
            promptAccessibilityPermission()
        }
        return false
    }

    fun goHome() {
        try {
            val intent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("SystemController", "Failed to go home", e)
        }
    }

    fun openRecentApps(): Boolean {
        val service = context.currentSystemAccessibilityActions()
        return if (service != null) {
            service.performRecents()
        } else {
            promptAccessibilityPermission()
            false
        }
    }

    fun openWifiSettings() {
        closePanelForSystemUi()
        try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("SystemController", "Failed to open wifi settings", e)
        }
    }

    fun openBluetoothSettings() {
        closePanelForSystemUi()
        try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("SystemController", "Failed to open bluetooth settings", e)
        }
    }

    fun openLocationSettings() {
        closePanelForSystemUi()
        try {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("SystemController", "Failed to open location settings", e)
        }
    }

    fun openHotspotSettings() {
        closePanelForSystemUi()
        try {
            val intent = Intent("android.settings.WIFI_AP_SETTINGS")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e("SystemController", "Failed to open hotspot settings", e)
            }
        }
    }

    fun openNfcSettings() {
        closePanelForSystemUi()
        try {
            val intent = Intent(Settings.ACTION_NFC_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("SystemController", "Failed to open NFC settings", e)
        }
    }

    fun openAirplaneModeSettings() {
        closePanelForSystemUi()
        try {
            val intent = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("SystemController", "Failed to open airplane mode settings", e)
        }
    }

    fun launchVoiceAssistant() {
        val candidates = listOf(
            Intent(Intent.ACTION_VOICE_COMMAND),
            Intent(Intent.ACTION_ASSIST),
            Intent("android.intent.action.VOICE_ASSIST")
        )
        for (candidate in candidates) {
            try {
                context.startActivity(candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) {
            }
        }
        Log.w("SystemController", "No voice assistant activity available")
        Toast.makeText(context, "未找到可用的语音助手应用", Toast.LENGTH_SHORT).show()
    }

    /**
     * 剪贴板与触觉反馈
     */
    fun readCurrentClipboardText(): String? {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val item = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
            item?.coerceToText(context)?.toString()
        } catch (e: Exception) {
            Log.e("SystemController", "Failed to read clipboard text", e)
            null
        }
    }

    fun copyToClipboard(label: String, text: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboard?.setPrimaryClip(clip)
            Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("SystemController", "Failed to copy text to clipboard", e)
            Toast.makeText(context, "复制失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("DEPRECATION")
    fun performTickHaptic(useSystemVibrate: Boolean = false) {
        try {
            val effect = if (useSystemVibrate && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            } else {
                VibrationEffect.createOneShot(if (useSystemVibrate) 28L else 18L, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val fallbackVibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                val vibrator = vibratorManager?.defaultVibrator ?: fallbackVibrator
                vibrator?.vibrate(effect)
            } else {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(effect)
            }
        } catch (e: Exception) {
            Log.w("SystemController", "Haptic vibration failed", e)
        }
    }
}
