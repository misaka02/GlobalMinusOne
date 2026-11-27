package com.example.globalminusone.system

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.pm.PackageInfoCompat
import com.example.globalminusone.data.QuickActionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Collator
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class InstalledAppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?
)

/** Thread-safe pure-Kotlin LRU cache (safe in both Android runtime and JVM unit tests). */
internal class BoundedMemoryLruCache<K, V>(private val maxEntries: Int) {
    private val map = object : LinkedHashMap<K, V>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxEntries
        }
    }

    @Synchronized
    fun get(key: K): V? = map[key]

    @Synchronized
    fun put(key: K, value: V): V? = map.put(key, value)

    @Synchronized
    fun remove(key: K): V? = map.remove(key)

    @Synchronized
    fun evictAll() {
        map.clear()
    }

    @Synchronized
    fun size(): Int = map.size
}

object AppAndIntentLauncher {

    @Volatile
    private var cachedApps: List<InstalledAppInfo>? = null
    private val appListLock = Any()
    private val iconLoadStripes = Array(16) { Mutex() }
    private val iconLoadSemaphore = Semaphore(12)
    private val iconCache = BoundedMemoryLruCache<String, InstalledAppInfo>(1024)
    private val iconBitmapCache = BoundedMemoryLruCache<String, ImageBitmap>(1024)
    private val pinnedPackages = ConcurrentHashMap.newKeySet<String>()
    private val pinnedPanelApps = ConcurrentHashMap<String, InstalledAppInfo>()
    private val pinnedPanelBitmaps = ConcurrentHashMap<String, ImageBitmap>()
    @Volatile
    private var diskCacheDir: File? = null
    private val missingPackageCache = BoundedMemoryLruCache<String, Boolean>(512)
    private val _cacheGeneration = MutableStateFlow(0)
    val cacheGeneration: StateFlow<Int> = _cacheGeneration.asStateFlow()
    private val _appListGeneration = MutableStateFlow(0)
    val appListGeneration: StateFlow<Int> = _appListGeneration.asStateFlow()
    private val INTENT_COMPONENT_REGEX = Regex("(?:^|[;#])component=([^/;\\s]+)/")
    private val INTENT_PACKAGE_REGEX = Regex("(?:^|[;#])package=([^;\\s]+)")

    fun notifyCacheUpdated() {
        _cacheGeneration.update { it + 1 }
    }

    fun invalidateInstalledAppList() {
        synchronized(appListLock) {
            cachedApps = null
        }
        _appListGeneration.update { it + 1 }
    }

    fun onPackageChanged(packageName: String) {
        val key = packageName.trim()
        if (key.isNotEmpty()) {
            evictAppCache(key)
        }
        invalidateInstalledAppList()
    }

    /** Replace the active panel/folder pin set so drawer scrolling never evicts panel or folder icons. */
    fun syncPinnedPackages(packageNames: Collection<String>) {
        val nextKeys = packageNames.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        pinnedPackages.retainAll(nextKeys)
        pinnedPackages.addAll(nextKeys)
        pinnedPanelApps.keys.retainAll(nextKeys)
        pinnedPanelBitmaps.keys.retainAll(nextKeys)
        nextKeys.forEach { key ->
            iconCache.get(key)?.let { pinnedPanelApps.putIfAbsent(key, it) }
            iconBitmapCache.get(key)?.let { pinnedPanelBitmaps.putIfAbsent(key, it) }
        }
    }

    /** Pin additional packages (e.g. opened folder contents) in memory without evicting existing pins. */
    fun pinPackages(packageNames: Collection<String>) {
        packageNames.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { key ->
                pinnedPackages.add(key)
                iconCache.get(key)?.let { pinnedPanelApps.putIfAbsent(key, it) }
                iconBitmapCache.get(key)?.let { pinnedPanelBitmaps.putIfAbsent(key, it) }
            }
    }

    private fun storeCachedApp(key: String, app: InstalledAppInfo, bitmap: ImageBitmap?) {
        iconCache.put(key, app)
        if (bitmap != null) {
            iconBitmapCache.put(key, bitmap)
        }
        if (key in pinnedPackages) {
            pinnedPanelApps[key] = app
            if (bitmap != null) {
                pinnedPanelBitmaps[key] = bitmap
            }
        }
    }

    /** Resolve package name with intent URI component/package fallback for imported shortcuts. */
    fun resolveEffectivePackageName(packageName: String, intentUri: String = ""): String {
        val direct = packageName.trim()
        if (direct.isNotEmpty()) return direct
        val rawUri = intentUri.trim()
        if (rawUri.isEmpty()) return ""
        return INTENT_COMPONENT_REGEX.find(rawUri)?.groupValues?.getOrNull(1)?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: INTENT_PACKAGE_REGEX.find(rawUri)?.groupValues?.getOrNull(1)?.trim().orEmpty()
    }

    /** Synchronous O(1) cache lookup to seed Compose state without 1-frame placeholder flash. */
    fun peekAppInfoCached(packageName: String): InstalledAppInfo? {
        val key = packageName.trim()
        if (key.isEmpty()) return null
        return pinnedPanelApps[key] ?: iconCache.get(key)
    }

    /** Synchronous O(1) pre-wrapped ImageBitmap lookup so reopened folders never re-allocate bitmaps. */
    fun peekAppIconBitmap(packageName: String): ImageBitmap? {
        val key = packageName.trim()
        if (key.isEmpty()) return null
        pinnedPanelBitmaps[key]?.let { return it }
        iconBitmapCache.get(key)?.let { return it }
        val bmp = (peekAppInfoCached(key)?.icon as? BitmapDrawable)?.bitmap ?: return null
        return runCatching { bmp.asImageBitmap() }.getOrNull()?.also { img ->
            iconBitmapCache.put(key, img)
            if (key in pinnedPackages) {
                pinnedPanelBitmaps[key] = img
            }
        }
    }

    fun isKnownMissingPackage(packageName: String): Boolean {
        val key = packageName.trim()
        return key.isNotEmpty() && missingPackageCache.get(key) == true
    }

    fun evictAppCache(packageName: String) {
        val key = packageName.trim()
        if (key.isNotEmpty()) {
            iconCache.remove(key)
            iconBitmapCache.remove(key)
            pinnedPanelApps.remove(key)
            pinnedPanelBitmaps.remove(key)
            missingPackageCache.remove(key)
            deleteStaleDiskIcons(diskCacheDir, sanitizePackageKey(key), keepBaseName = null)
            notifyCacheUpdated()
        }
    }

    fun trimMemoryCaches(severe: Boolean) {
        iconBitmapCache.evictAll()
        missingPackageCache.evictAll()
        if (severe) {
            iconCache.evictAll()
            pinnedPanelApps.clear()
            pinnedPanelBitmaps.clear()
            synchronized(appListLock) {
                cachedApps = null
            }
            notifyCacheUpdated()
        }
    }

    private fun sanitizePackageKey(pkg: String): String {
        val safe = pkg.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
        val hash = (pkg.hashCode().toLong() and 0xffffffffL).toString(16)
        return "${safe}_$hash"
    }

    private fun resolveDiskCacheDir(context: Context): File? {
        return runCatching {
            val existing = diskCacheDir
            if (existing != null) {
                if (!existing.exists()) existing.mkdirs()
                return@runCatching existing
            }
            val base = (context.applicationContext ?: context).filesDir ?: return@runCatching null
            File(base, "app-icon-cache").also { dir ->
                if (!dir.exists()) dir.mkdirs()
                diskCacheDir = dir
            }
        }.getOrNull()
    }

    private fun deleteStaleDiskIcons(dir: File?, safeKey: String, keepBaseName: String?) {
        if (dir == null || !dir.isDirectory) return
        val prefix = "${safeKey}_"
        runCatching {
            dir.listFiles()?.forEach { file ->
                val name = file.name
                val matches = name.startsWith(prefix) &&
                    (name.endsWith(".png") || name.endsWith(".label") || name.endsWith(".tmp"))
                val keep = keepBaseName != null &&
                    (name == "$keepBaseName.png" || name == "$keepBaseName.label")
                if (matches && !keep) {
                    file.delete()
                }
            }
        }
    }

    private fun rasterizeAppIconBitmap(context: Context, drawable: Drawable): Bitmap? {
        return try {
            val pixels = (64 * context.resources.displayMetrics.density).toInt().coerceIn(64, 256)
            val bitmap = Bitmap.createBitmap(pixels, pixels, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, pixels, pixels)
            drawable.draw(Canvas(bitmap))
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    private fun loadFromDiskOrPackageManager(context: Context, key: String): Pair<InstalledAppInfo, ImageBitmap?>? {
        val appContext = context.applicationContext ?: context
        val pm = appContext.packageManager
        val safeKey = sanitizePackageKey(key)
        val cacheDir = resolveDiskCacheDir(appContext)
        val pkgInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(key, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(key, 0)
            }
        } catch (_: Exception) {
            null
        }
        if (pkgInfo == null) {
            deleteStaleDiskIcons(cacheDir, safeKey, keepBaseName = null)
            return null
        }

        val versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo)
        val baseName = "${safeKey}_${versionCode}"
        val pngFile = cacheDir?.let { File(it, "$baseName.png") }
        val labelFile = cacheDir?.let { File(it, "$baseName.label") }

        if (pngFile != null && pngFile.isFile && pngFile.length() > 0L) {
            val diskBitmap = runCatching { BitmapFactory.decodeFile(pngFile.absolutePath) }.getOrNull()
            if (diskBitmap != null) {
                val cachedLabel = runCatching {
                    if (labelFile != null && labelFile.isFile) {
                        labelFile.readText(Charsets.UTF_8).trim().takeIf { it.isNotEmpty() }
                    } else null
                }.getOrNull() ?: runCatching {
                    pkgInfo.applicationInfo?.let { pm.getApplicationLabel(it).toString() }
                }.getOrNull().orEmpty().ifBlank { key }.also { resolvedLabel ->
                    runCatching {
                        if (labelFile != null) {
                            labelFile.writeText(resolvedLabel, Charsets.UTF_8)
                        }
                    }
                }
                deleteStaleDiskIcons(cacheDir, safeKey, keepBaseName = baseName)
                val img = runCatching { diskBitmap.asImageBitmap() }.getOrNull()
                val app = InstalledAppInfo(
                    packageName = key,
                    label = cachedLabel,
                    icon = BitmapDrawable(appContext.resources, diskBitmap)
                )
                return app to img
            }
        }

        val raw = loadAppInfoRaw(appContext, key) ?: return null
        val rasterized = raw.icon?.let { rasterizeAppIconBitmap(appContext, it) }
        return if (rasterized != null) {
            if (pngFile != null && cacheDir != null) {
                runCatching {
                    deleteStaleDiskIcons(cacheDir, safeKey, keepBaseName = baseName)
                    val tmpFile = File(cacheDir, "$baseName.png.tmp")
                    tmpFile.outputStream().use { out ->
                        rasterized.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    if (!tmpFile.renameTo(pngFile)) {
                        pngFile.outputStream().use { out ->
                            rasterized.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                        tmpFile.delete()
                    }
                    labelFile?.writeText(raw.label, Charsets.UTF_8)
                }
            }
            val img = runCatching { rasterized.asImageBitmap() }.getOrNull()
            raw.copy(icon = BitmapDrawable(appContext.resources, rasterized)) to img
        } else {
            raw to null
        }
    }

    suspend fun getAppInfoCached(
        context: Context,
        packageName: String,
        notify: Boolean = true
    ): InstalledAppInfo? {
        val key = packageName.trim()
        if (key.isEmpty()) return null
        peekAppInfoCached(key)?.let { existing ->
            if (key in pinnedPackages) {
                pinnedPanelApps.putIfAbsent(key, existing)
                peekAppIconBitmap(key)?.let { pinnedPanelBitmaps.putIfAbsent(key, it) }
            }
            return existing
        }
        if (missingPackageCache.get(key) == true) return null
        return withContext(Dispatchers.IO) {
            peekAppInfoCached(key)?.let { return@withContext it }
            if (missingPackageCache.get(key) == true) return@withContext null
            val stripe = iconLoadStripes[(key.hashCode() and 0x7fffffff) % iconLoadStripes.size]
            stripe.withLock {
                peekAppInfoCached(key)?.let { return@withLock it }
                if (missingPackageCache.get(key) == true) return@withLock null
                iconLoadSemaphore.withPermit {
                    val loaded = loadFromDiskOrPackageManager(context, key)
                    if (loaded == null) {
                        missingPackageCache.put(key, true)
                        null
                    } else {
                        missingPackageCache.remove(key)
                        val (app, img) = loaded
                        storeCachedApp(key, app, img)
                        if (notify) notifyCacheUpdated()
                        app
                    }
                }
            }
        }
    }

    /** Pre-warm app icons across all pages and closed folders in the background. */
    suspend fun prewarmPackages(context: Context, packageNames: Collection<String>) {
        val appContext = context.applicationContext ?: context
        resolveDiskCacheDir(appContext)
        val pending = packageNames
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && peekAppInfoCached(it) == null && missingPackageCache.get(it) != true }
            .distinct()
            .toList()
        if (pending.isEmpty()) return
        val loadedCount = AtomicInteger(0)
        withContext(Dispatchers.IO) {
            coroutineScope {
                pending.forEach { pkg ->
                    launch {
                        val loaded = getAppInfoCached(appContext, pkg, notify = false)
                        if (loaded != null && loadedCount.incrementAndGet() % 8 == 0) {
                            notifyCacheUpdated()
                        }
                    }
                }
            }
        }
        if (loadedCount.get() > 0) {
            notifyCacheUpdated()
        }
    }

    /**
     * 同步获取已安装的应用列表
     */
    fun getInstalledAppsSync(context: Context, forceRefresh: Boolean = false): List<InstalledAppInfo> {
        if (!forceRefresh) {
            cachedApps?.let { return it }
        }
        return synchronized(appListLock) {
            if (!forceRefresh) cachedApps?.let { return@synchronized it }
            if (forceRefresh) {
                // Only clear negative cache; preserve pinned and warm icon bitmaps so folders never reload.
                missingPackageCache.evictAll()
            }
            queryInstalledApps(context).also {
                cachedApps = it
                if (forceRefresh) {
                    _appListGeneration.update { gen -> gen + 1 }
                }
            }
        }
    }

    /**
     * 异步获取已安装的应用列表（运行在 Dispatchers.IO）
     */
    suspend fun getInstalledApps(context: Context, forceRefresh: Boolean = false): List<InstalledAppInfo> {
        if (!forceRefresh) {
            cachedApps?.let { return it }
        }
        return withContext(Dispatchers.IO) {
            getInstalledAppsSync(context, forceRefresh)
        }
    }

    private fun queryInstalledApps(context: Context): List<InstalledAppInfo> {
        return try {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val resolveInfos: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    mainIntent,
                    PackageManager.ResolveInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(mainIntent, 0)
            }

            val collator = Collator.getInstance(Locale.getDefault()).apply {
                strength = Collator.PRIMARY
            }

            resolveInfos
                .mapNotNull { resolveInfo ->
                    val pkg = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                    val label = resolveInfo.loadLabel(pm).toString().ifBlank { pkg }
                    // Keep the list lightweight; visible tiles load icons on IO into a bounded cache.
                    InstalledAppInfo(packageName = pkg, label = label, icon = null)
                }
                .distinctBy { it.packageName }
                .sortedWith { a, b -> collator.compare(a.label, b.label) }
        } catch (e: Exception) {
            Log.e("AppAndIntentLauncher", "Failed to query installed apps", e)
            emptyList()
        }
    }

    private fun loadAppInfoRaw(context: Context, packageName: String): InstalledAppInfo? {
        return try {
            val pm = context.packageManager
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            val label = pm.getApplicationLabel(appInfo).toString()
            val icon = pm.getApplicationIcon(appInfo)
            InstalledAppInfo(packageName = packageName, label = label, icon = icon)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 获取指定包名的应用详情并写入图标缓存
     */
    fun getAppInfo(context: Context, packageName: String): InstalledAppInfo? {
        val key = packageName.trim()
        if (key.isEmpty()) return null
        peekAppInfoCached(key)?.let { return it }
        if (missingPackageCache.get(key) == true) return null
        val loaded = loadFromDiskOrPackageManager(context, key)
        return if (loaded == null) {
            missingPackageCache.put(key, true)
            null
        } else {
            missingPackageCache.remove(key)
            val (app, img) = loaded
            storeCachedApp(key, app, img)
            app
        }
    }

    /**
     * 适配 Android 12~16 后台启动限制 (BAL - Background Activity Launch)
     */
    /** Launches [com.example.globalminusone.MainActivity] with PendingIntent + BAL options first, falling back to direct startActivity. */
    fun launchMainSettings(context: Context, openScreen: String? = null): Boolean {
        val intent = Intent(context, com.example.globalminusone.MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("force_settings", true)
            if (!openScreen.isNullOrBlank()) {
                putExtra("open_screen", openScreen)
            }
        }
        val options = buildLaunchOptionsBundle()
        val requestCode = when (openScreen) {
            "SCREEN_ASSISTANT" -> 0x474D02
            null, "" -> 0x474D00
            else -> 0x474D01 or (openScreen.hashCode() and 0xFF)
        }
        val launched = runCatching {
            val pending = PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            pending.send(context, 0, null, null, null, null, options)
            true
        }.getOrElse { piError ->
            Log.w("AppAndIntentLauncher", "PendingIntent launchMainSettings failed; falling back to startActivity", piError)
            runCatching {
                context.startActivity(intent, options)
                true
            }.getOrElse { actError ->
                Log.w("AppAndIntentLauncher", "Direct startActivity launchMainSettings failed", actError)
                false
            }
        }
        if (!launched) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(
                    context,
                    "无法从后台拉起设置，请从桌面长按应用图标选择“应用设置”或“OCR 与识屏”",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        return launched
    }

    private fun buildLaunchOptionsBundle(): Bundle? {
        return if (Build.VERSION.SDK_INT >= 34) {
            @Suppress("DEPRECATION")
            val mode = if (Build.VERSION.SDK_INT >= 36) {
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
            } else {
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            }
            ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(mode)
                .toBundle()
        } else {
            null
        }
    }

    /**
     * 启动应用：通过 PendingIntent 与直接 startActivity 双重保障
     */
    fun launchApp(context: Context, packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent == null) {
                Log.w("AppAndIntentLauncher", "No launch intent found for $packageName")
                Toast.makeText(context, "应用未安装、已停用或没有可启动入口", Toast.LENGTH_SHORT).show()
                return false
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            val options = buildLaunchOptionsBundle()

            var launched = false
            try {
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    packageName.hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                pendingIntent.send(context, 0, null, null, null, null, options)
                launched = true
            } catch (e: Exception) {
                Log.w("AppAndIntentLauncher", "PendingIntent launch failed for $packageName", e)
            }

            if (!launched) {
                try {
                    context.startActivity(intent, options)
                    launched = true
                } catch (e: Exception) {
                    Log.e("AppAndIntentLauncher", "Direct startActivity failed for $packageName", e)
                }
            }
            launched
        } catch (e: Exception) {
            Log.e("AppAndIntentLauncher", "Failed to launch app: $packageName", e)
            false
        }
    }

    /**
     * 启动快捷动作项 (QuickActionItem)
     * 支持 action:、category:、intent: 与标准 URL Scheme
     */
    fun launchQuickAction(context: Context, item: QuickActionItem): Boolean {
        val uri = item.intentUri.trim()
        when (uri) {
            "action:screenshot_region" -> {
                if (!android.provider.Settings.canDrawOverlays(context)) {
                    Toast.makeText(context, "区域截屏需要悬浮窗权限", Toast.LENGTH_SHORT).show()
                    return false
                }
                val hasPrivilege = context.screenToolRequests().supportsPrivilegedScreenCapture(context)
                if (context.currentSystemAccessibilityActions() == null && !hasPrivilege) {
                    Toast.makeText(context, "请先开启辅助服务以使用区域截屏", Toast.LENGTH_SHORT).show()
                    context.panelVisibilityRequests().close(context)
                    runCatching {
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                    return false
                }
                return context.screenToolRequests().regionScreenshot(context)
            }
            "action:screen_assistant" -> {
                if (!android.provider.Settings.canDrawOverlays(context)) {
                    Toast.makeText(context, "全局识屏需要悬浮窗权限", Toast.LENGTH_SHORT).show()
                    return false
                }
                val hasPrivilege = context.screenToolRequests().supportsPrivilegedScreenCapture(context)
                if (context.currentSystemAccessibilityActions() == null && !hasPrivilege) {
                    Toast.makeText(context, "请先开启辅助服务以使用全局识屏", Toast.LENGTH_SHORT).show()
                    context.panelVisibilityRequests().close(context)
                    runCatching {
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                    return false
                }
                return context.screenToolRequests().screenAssistant(context)
            }
            "action:breeno_touch" -> {
                context.screenToolRequests().breenoTouch(context)
                return true
            }
            "action:com.example.globalminusone.MAIN" -> {
                return launchMainSettings(context)
            }
            "action:eye_protection" -> {
                val repo = com.example.globalminusone.data.MinusOneRepository.getInstance(context)
                repo.updateAdvancedConfig { it.copy(eyeProtectionEnabled = !it.eyeProtectionEnabled) }
                Toast.makeText(context, "已切换护眼模式", Toast.LENGTH_SHORT).show()
                return true
            }
            "action:media_play_pause" -> {
                return runCatching {
                    val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                    val wasPlaying = am?.isMusicActive == true
                    am?.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                    am?.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                    if (!wasPlaying) {
                        Toast.makeText(context, "已发送播放/暂停指令（当前无活跃媒体）", Toast.LENGTH_SHORT).show()
                    }
                    true
                }.getOrDefault(false)
            }
        }
        // A saved application card uses MAIN plus packageName. An unscoped MAIN
        // intent can resolve to Home/Settings when the requested app is absent.
        if (uri == "action:android.intent.action.MAIN") {
            if (item.packageName.isBlank()) {
                Toast.makeText(context, "请先为此卡片选择应用", Toast.LENGTH_SHORT).show()
                return false
            }
            return launchApp(context, item.packageName)
        }
        val appShortcutRef = ShortcutDiscoveryPolicy.parseAppShortcutUri(uri)
        if (appShortcutRef != null) {
            if (!isAppShortcutPackageAllowed(item.packageName, appShortcutRef.first)) {
                Toast.makeText(context, "快捷方式目标与应用不一致", Toast.LENGTH_SHORT).show(); return false
            }
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? android.content.pm.LauncherApps
            if (launcherApps != null) {
                val ok = runCatching {
                    launcherApps.startShortcut(appShortcutRef.first, appShortcutRef.second, null,
                        buildLaunchOptionsBundle(), android.os.Process.myUserHandle()); true
                }.getOrDefault(false)
                if (ok) return true
            }
            Toast.makeText(context, "无法直接启动该系统快捷方式（需设为默认桌面或授权快捷访问）", Toast.LENGTH_SHORT).show()
            return false
        }
        val options = buildLaunchOptionsBundle()
        try {
            val intent = when {
                uri.startsWith("action:") -> {
                    Intent(uri.removePrefix("action:"))
                }
                uri.startsWith("category:") -> {
                    Intent(Intent.ACTION_MAIN).addCategory(uri.removePrefix("category:"))
                }
                uri.startsWith("intent:") -> {
                    Intent.parseUri(uri, Intent.URI_INTENT_SCHEME).apply {
                        val declaredPackage = item.packageName.takeIf { it.isNotBlank() }
                        require(declaredPackage == null || `package` == null || `package` == declaredPackage) { "快捷方式目标与应用不一致" }
                        require(declaredPackage == null || component == null || component?.packageName == declaredPackage) { "快捷方式目标与应用不一致" }
                        selector = null
                        clipData = null
                        flags = 0
                        // Only enforce BROWSABLE on completely unscoped implicit intent URIs;
                        // explicit components or package-scoped launcher shortcuts rarely declare BROWSABLE.
                        if (component == null && `package` == null && categories.isNullOrEmpty()) {
                            addCategory(Intent.CATEGORY_BROWSABLE)
                        }
                        component?.let { target ->
                            @Suppress("DEPRECATION")
                            val info = context.packageManager.getActivityInfo(target, 0)
                            require(info.exported && info.enabled && info.applicationInfo.enabled &&
                                (info.permission.isNullOrBlank() || context.checkSelfPermission(info.permission) == PackageManager.PERMISSION_GRANTED)) {
                                "快捷方式目标未公开或无启动权限"
                            }
                        }
                    }
                }
                else -> {
                    Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                }
            }
            if (item.packageName.isNotBlank()) {
                val componentPackage = intent.component?.packageName
                require(componentPackage == null || componentPackage == item.packageName) { "快捷方式目标与应用不一致" }
                intent.setPackage(item.packageName)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent, options)
            return true
        } catch (e: Exception) {
            Log.w("AppAndIntentLauncher", "Failed to launch quick action with URI: $uri", e)
            Toast.makeText(context, if (e is IllegalArgumentException) e.message ?: "快捷方式目标无效" else "无法打开快捷方式: ${item.title}", Toast.LENGTH_SHORT).show()
            return false
        }
    }

    internal fun isAppShortcutPackageAllowed(declaredPackage: String, shortcutPackage: String): Boolean =
        declaredPackage.isBlank() || declaredPackage == shortcutPackage

    /** 聚合搜索跳转：将 %s 替换为编码后的关键词，并在浏览器中打开 */
    fun launchSearch(context: Context, searchEngineUrl: String, query: String) {
        if (query.isBlank()) return
        val encodedQuery = Uri.encode(query.trim())
        val targetUrl = if (searchEngineUrl.contains("%s")) {
            searchEngineUrl.replace("%s", encodedQuery)
        } else {
            "$searchEngineUrl$encodedQuery"
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent, buildLaunchOptionsBundle())
        } catch (e: Exception) {
            Log.e("AppAndIntentLauncher", "Failed to launch search url: $targetUrl", e)
            Toast.makeText(context, "无法打开浏览器", Toast.LENGTH_SHORT).show()
        }
    }
}
