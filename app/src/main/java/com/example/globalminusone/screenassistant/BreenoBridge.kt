package com.example.globalminusone.screenassistant

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import com.example.globalminusone.system.RootCommandResult
import com.example.globalminusone.system.RootStatus
import com.example.globalminusone.system.SystemAccessibilityActions
import com.example.globalminusone.system.UserRootSession
import com.example.globalminusone.system.findSystemAccessibilityActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/** Protocol documented from real SmartSideBar/ColorDirectService, not a guessed DirectService. */
internal object BreenoSystemProtocol {
    const val action = "oplus.intent.action.DIRECT_SIDEBAR_SERVICE"
    const val servicePackage = "com.coloros.colordirectservice"
    const val component = "com.coloros.colordirectservice/com.coloros.colordirectservice.InvokeEntranceService"
    const val sidebarPackage = "com.coloros.smartsidebar"
    const val featureUri = "oppo.sidebar.feature:小布识屏"

    fun command(target: String): String {
        require(target == component)
        return "am start-foreground-service --user current -n '$target' -a '$action' --es extra_entrance_function 'full_screen_ocr' --es triggered_app '$sidebarPackage'"
    }

    fun accepted(exit: Int?, stdout: String, stderr: String): Boolean {
        val out = stdout.trim()
        return exit == 0 && stderr.isBlank() && out.startsWith("Starting service: Intent {") &&
            (out.contains("cmp=$component") || out.contains("cmp=$servicePackage/.InvokeEntranceService")) &&
            !Regex("(?i)error|exception|denial|denied|requires permission|not found|not exported|unable").containsMatchIn(out)
    }
}

internal object BreenoTilePolicy {
    val packages = listOf("com.coloros.directui", "com.oplus.directui")
    const val sentMarker = "GMO_BREENO_TILE_REQUEST_SENT"

    fun isRecognitionLabel(label: String) = label.trim() in setOf("小布识屏", "Breeno识屏", "Breeno 识屏", "Breeno Touch", "Screen recognition")
    fun validComponent(component: String): Boolean = component.split('/').let { p -> p.size == 2 && p[0] in packages && p[1].matches(Regex("(?:\\.|[A-Za-z_])[A-Za-z0-9_.$]*")) }

    fun selectPreferredTile(candidates: List<String>, activeSysuiTiles: String? = null): String? {
        val valid = candidates.asSequence().map { it.trim() }.filter(::validComponent).distinct().toList()
        if (valid.isEmpty()) return null
        if (!activeSysuiTiles.isNullOrBlank()) {
            val active = activeSysuiTiles.split(',').map { it.trim() }.toSet()
            valid.firstOrNull { comp ->
                val pkg = comp.substringBefore('/'); val cls = comp.substringAfter('/')
                val full = if (cls.startsWith('.')) "$pkg/$pkg$cls" else comp
                val short = if (cls.startsWith("$pkg.")) "$pkg/${cls.removePrefix(pkg)}" else comp
                "custom($full)" in active || "custom($short)" in active || "custom($comp)" in active
            }?.let { return it }
        }
        return valid.minWithOrNull(compareBy(
            { packages.indexOf(it.substringBefore('/')).let { i -> if (i < 0) Int.MAX_VALUE else i } },
            { if (it.contains("recogni", true) || it.contains("direct", true)) 0 else 1 },
            { it.length }, { it }
        ))
    }

    fun command(component: String): String {
        require(validComponent(component)) { "未匹配到小布识屏磁贴" }
        val quoted = "'" + component.replace("'", "'\"'\"'") + "'"
        val pkg = component.substringBefore('/'); val cls = component.substringAfter('/')
        val full = if (cls.startsWith('.')) "$pkg/$pkg$cls" else component
        val short = if (cls.startsWith("$pkg.")) "$pkg/${cls.removePrefix(pkg)}" else component
        return "h=\$(cmd statusbar help 2>&1); case \"\$h\" in *click-tile*) ;; *) echo '系统不支持click-tile'; exit 69;; esac; " +
            "t=\$(settings --user current get secure sysui_qs_tiles) || exit 70; " +
            "case ,\$t, in *,'custom($full)',*|*,'custom($short)',*) ;; *) echo '请先将小布识屏磁贴添加到系统快捷设置'; exit 71;; esac; " +
            "r=\$(cmd statusbar click-tile $quoted 2>&1); c=\$?; [ \$c -eq 0 ] || { printf '%s\\n' \"\$r\"; exit \$c; }; " +
            "[ -z \"\$r\" ] || { printf '%s\\n' \"\$r\"; exit 72; }; printf '$sentMarker\\n'"
    }

    fun requestWasSent(exitCode: Int?, output: String, diagnostic: String = "") = exitCode == 0 && diagnostic.isBlank() && output.trim() == sentMarker
}

internal enum class BreenoPrivilegeChannel(val label: String) { ROOT("Root"), SHIZUKU("Shizuku") }

internal object BreenoPrivilegePolicy {
    fun planExecutionOrder(rootGranted: Boolean, shizukuAuthorized: Boolean, shizukuAvailable: Boolean, allowPrivilegedShell: Boolean, rootDeniedPreviously: Boolean = false): List<BreenoPrivilegeChannel> {
        val canRoot = rootGranted || (allowPrivilegedShell && !rootDeniedPreviously)
        val canShizuku = shizukuAuthorized || (allowPrivilegedShell && shizukuAvailable)
        if (!canRoot && !canShizuku) return emptyList()
        return buildList {
            if (rootGranted) add(BreenoPrivilegeChannel.ROOT)
            if (shizukuAuthorized && BreenoPrivilegeChannel.SHIZUKU !in this) add(BreenoPrivilegeChannel.SHIZUKU)
            if (canShizuku && BreenoPrivilegeChannel.SHIZUKU !in this) add(BreenoPrivilegeChannel.SHIZUKU)
            if (canRoot && BreenoPrivilegeChannel.ROOT !in this) add(BreenoPrivilegeChannel.ROOT)
        }
    }
}

internal object BreenoQuickSettingsUiPolicy {
    const val systemUi = "com.android.systemui"
    private val containers = setOf("qs_pager", "quick_settings_panel", "qs_panel", "tile_layout")
    private val labels = setOf("小布识屏", "Breeno识屏", "Breeno 识屏", "屏幕识别", "Breeno Touch")
    fun isQsContainer(rootPkg: String?, nodePkg: String?, id: String?, visible: Boolean) =
        rootPkg == systemUi && nodePkg == systemUi && visible && id != null && id.substringBefore(":id/") == systemUi && id.substringAfter(":id/") in containers
    fun isRecognitionLabel(text: String?): Boolean {
        val v = text?.trim() ?: return false
        if (v in labels) return true
        val parts = v.split(',', '，').map { it.trim() }
        return parts.size == 2 && parts[0] in labels && parts[1] in setOf("开启", "关闭", "已开启", "已关闭", "开", "关", "不可用", "按钮")
    }
    fun canClick(rootPkg: String?, inQs: Boolean, ancestorDistance: Int, isContainer: Boolean, visible: Boolean, enabled: Boolean, clickable: Boolean) =
        rootPkg == systemUi && inQs && ancestorDistance in 0..4 && !isContainer && visible && enabled && clickable
    fun canPage(id: String?, cls: String?, scrollable: Boolean) =
        scrollable && id == "$systemUi:id/qs_pager" && (cls?.contains("ViewPager") == true || cls?.endsWith("PagedTileLayout") == true || cls == "android.view.ViewGroup")
}

internal data class BreenoQuickSettingsResult(val sent: Boolean, val message: String)

internal object BreenoQuickSettingsUi {
    private val gate = Mutex()
    private data class Entry(val node: AccessibilityNodeInfo, val parent: Int, val depth: Int)
    private class Tree(val entries: List<Entry>, val rootPackage: String?) : AutoCloseable {
        val containers = entries.indices.filter { entries[it].node.let { n -> BreenoQuickSettingsUiPolicy.isQsContainer(rootPackage, n.packageName?.toString(), n.viewIdResourceName, n.isVisibleToUser) } }
        fun within(i: Int, c: Int): Boolean { var at = i; repeat(26) { if (at == c) return true; if (at < 0) return false; at = entries[at].parent }; return false }
        fun clickRecognition(): Boolean? {
            for (i in entries.indices) {
                val n = entries[i].node
                if (n.packageName?.toString() != BreenoQuickSettingsUiPolicy.systemUi || !n.isVisibleToUser ||
                    !(BreenoQuickSettingsUiPolicy.isRecognitionLabel(n.text?.toString()) || BreenoQuickSettingsUiPolicy.isRecognitionLabel(n.contentDescription?.toString()))) continue
                val c = containers.firstOrNull { within(i, it) } ?: continue
                var at = i
                repeat(5) { d ->
                    if (at < 0 || !within(at, c)) return@repeat
                    val cand = entries[at].node
                    if (BreenoQuickSettingsUiPolicy.canClick(rootPackage, true, d, at in containers, cand.isVisibleToUser, cand.isEnabled, cand.isClickable))
                        return cand.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    at = entries[at].parent
                }
            }
            return null
        }
        fun scrollOnce() = entries.indices.singleOrNull { i -> entries[i].node.let { n -> containers.any { within(i, it) } && BreenoQuickSettingsUiPolicy.canPage(n.viewIdResourceName, n.className?.toString(), n.isScrollable) && n.isVisibleToUser } }
            ?.let { entries[it].node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) } ?: false
        @Suppress("DEPRECATION") override fun close() { entries.forEach { runCatching { it.node.recycle() } } }
    }

    private fun snapshot(service: SystemAccessibilityActions): Tree? {
        val root = service.activeWindowRoot() ?: return null
        val entries = ArrayList<Entry>().apply { add(Entry(root, -1, 0)) }; var i = 0
        try { while (i < entries.size && entries.size < 450) {
            val e = entries[i]; if (e.depth < 24) for (c in 0 until e.node.childCount.coerceAtMost(64)) { if (entries.size >= 450) break; e.node.getChild(c)?.let { entries += Entry(it, i, e.depth + 1) } }; i++
        } } catch (f: Exception) { @Suppress("DEPRECATION") entries.forEach { runCatching { it.node.recycle() } }; throw f }
        return Tree(entries, root.packageName?.toString())
    }

    suspend fun launch(context: Context): BreenoQuickSettingsResult = withContext(Dispatchers.Main.immediate) {
        if (!gate.tryLock()) return@withContext BreenoQuickSettingsResult(false, "已有系统识屏请求正在处理")
        var opened = false; var sent = false; val service = context.findSystemAccessibilityActions()
        try {
            if (service == null) return@withContext BreenoQuickSettingsResult(false, "请先连接已有无障碍服务以点击系统小布识屏磁贴")
            if (snapshot(service)?.use { it.containers.isNotEmpty() } != true) {
                opened = service.performQuickSettings()
                if (!opened) return@withContext BreenoQuickSettingsResult(false, "系统未接受打开快捷设置请求")
            }
            repeat(6) {
                delay(180); snapshot(service)?.use { t -> if (t.containers.isNotEmpty()) t.clickRecognition()?.let { c -> sent = c; return@withContext BreenoQuickSettingsResult(c, if (c) "已点击系统小布识屏磁贴，等待系统识屏响应" else "系统小布识屏磁贴未接受点击") } }
            }
            if (snapshot(service)?.use { it.containers.isNotEmpty() && it.scrollOnce() } == true) repeat(3) {
                delay(180); snapshot(service)?.use { t -> t.clickRecognition()?.let { c -> sent = c; return@withContext BreenoQuickSettingsResult(c, if (c) "已点击系统小布识屏磁贴，等待系统识屏响应" else "系统小布识屏磁贴未接受点击") } }
            }
            BreenoQuickSettingsResult(false, "未找到系统小布识屏磁贴；请先将小布识屏添加到系统快捷设置")
        } catch (c: kotlinx.coroutines.CancellationException) { throw c }
        catch (_: Exception) { BreenoQuickSettingsResult(false, "系统快捷设置节点不可用；请确认无障碍服务已连接及小布识屏磁贴已添加") }
        finally {
            if (opened && !sent && service != null && context.findSystemAccessibilityActions() === service) {
                runCatching { if (snapshot(service)?.use { it.containers.isNotEmpty() } == true) service.performBack() }
            }
            gate.unlock()
        }
    }
}

data class BreenoProbeSnapshot(
    val installedPackages: List<String>, val launchedExportedTarget: ComponentName?, val isDefaultAssistantBreeno: Boolean,
    val recognitionTile: ComponentName? = null, val hasDirectService: Boolean = false, val shizukuAvailable: Boolean = false, val shizukuAuthorized: Boolean = false
)
data class BreenoLaunchOutcome(val launched: Boolean, val fellBackToBuiltin: Boolean, val message: String, val installedPackages: List<String>)

internal object BreenoTouchBridge {
    private fun resolveDirectService(pm: PackageManager): ServiceInfo? {
        val byAction = @Suppress("DEPRECATION") runCatching { pm.queryIntentServices(Intent(BreenoSystemProtocol.action).setPackage(BreenoSystemProtocol.servicePackage), 0).mapNotNull { it.serviceInfo } }.getOrDefault(emptyList())
        val cands = if (byAction.isNotEmpty()) byAction else listOfNotNull(ComponentName.unflattenFromString(BreenoSystemProtocol.component)?.let { @Suppress("DEPRECATION") runCatching { pm.getServiceInfo(it, 0) }.getOrNull() })
        return cands.firstOrNull { it.enabled && it.applicationInfo.enabled && (it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) && ComponentName(it.packageName, it.name).flattenToString() == BreenoSystemProtocol.component }
    }

    fun probe(context: Context): BreenoProbeSnapshot {
        val pm = context.packageManager
        val installed = (BreenoTilePolicy.packages + listOf(BreenoSystemProtocol.servicePackage, BreenoSystemProtocol.sidebarPackage)).filter { pkg ->
            runCatching { @Suppress("DEPRECATION") pm.getApplicationInfo(pkg, 0).let { it.enabled && it.flags and ApplicationInfo.FLAG_SYSTEM != 0 } }.getOrDefault(false)
        }
        val tiles = installed.flatMap { pkg -> @Suppress("DEPRECATION") runCatching { pm.queryIntentServices(Intent("android.service.quicksettings.action.QS_TILE").setPackage(pkg), PackageManager.GET_META_DATA) }.getOrDefault(emptyList()) }
            .mapNotNull { res -> val s = res.serviceInfo ?: return@mapNotNull null; ComponentName(s.packageName, s.name).takeIf { s.enabled && s.exported && s.applicationInfo.enabled && s.permission == "android.permission.BIND_QUICK_SETTINGS_TILE" && BreenoTilePolicy.isRecognitionLabel(s.loadLabel(pm).toString()) && BreenoTilePolicy.validComponent(it.flattenToString()) }?.flattenToString() }.distinct()
        val selectedTile = BreenoTilePolicy.selectPreferredTile(tiles, runCatching { Settings.Secure.getString(context.contentResolver, "sysui_qs_tiles") }.getOrNull())?.let { ComponentName.unflattenFromString(it) }
        return BreenoProbeSnapshot(installed, null, false, selectedTile, resolveDirectService(pm) != null, ShizukuBreenoSession.serviceAvailable(), ShizukuBreenoSession.authorized())
    }

    private suspend fun executePrivilegedChannels(context: Context, channels: List<BreenoPrivilegeChannel>, installed: List<String>, tileComp: String? = null, svcComp: String? = null): Pair<BreenoPrivilegeChannel?, List<String>> {
        val fails = mutableListOf<String>()
        for (ch in channels) when (ch) {
            BreenoPrivilegeChannel.ROOT -> {
                val auth = UserRootSession.requestAuthorization(); if (!auth.succeeded) { fails += "Root: ${auth.reason}"; continue }
                val res = UserRootSession.runBreenoDirectCommand(installed, tileComp, svcComp); if (res.succeeded) return ch to emptyList(); fails += "Root: ${res.reason}"
            }
            BreenoPrivilegeChannel.SHIZUKU -> {
                if (!ShizukuBreenoSession.authorized() && !ShizukuBreenoSession.requestAuthorization()) {
                    fails += if (ShizukuBreenoSession.serviceAvailable()) "Shizuku: 未获得授权" else "Shizuku: 服务未运行"; continue
                }
                val res = ShizukuBreenoSession.runBreenoDirectCommand(context, installed, tileComp, svcComp); if (res.succeeded) return ch to emptyList(); fails += "Shizuku: ${res.reason}"
            }
        }
        return null to fails
    }

    suspend fun launch(context: Context, allowPrivilegedShell: Boolean): BreenoLaunchOutcome {
        val snap = probe(context); val rootStatus = UserRootSession.status.value
        val channels = BreenoPrivilegePolicy.planExecutionOrder(rootStatus == RootStatus.Granted, snap.shizukuAuthorized, snap.shizukuAvailable, allowPrivilegedShell, rootStatus == RootStatus.Denied || rootStatus == RootStatus.Unavailable)
        val pm = context.packageManager; val service = resolveDirectService(pm); var directErr: String? = null
        if (channels.isNotEmpty() && service != null) {
            val (ch, fails) = executePrivilegedChannels(context, channels, listOf(service.packageName), svcComp = BreenoSystemProtocol.component)
            if (ch != null) return BreenoLaunchOutcome(true, false, "已通过 ${ch.label} 向系统小布识屏发送全屏识别请求，等待系统界面响应", listOf(service.packageName))
            directErr = fails.joinToString(" / ").takeIf { it.isNotBlank() }
        }
        val feature = Intent(Intent.ACTION_VIEW, Uri.parse(BreenoSystemProtocol.featureUri)).setPackage(BreenoSystemProtocol.sidebarPackage).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        @Suppress("DEPRECATION") val activity = runCatching { pm.resolveActivity(feature, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo }.getOrNull()
        if (activity != null && activity.exported && activity.enabled && activity.applicationInfo.enabled && activity.packageName == BreenoSystemProtocol.sidebarPackage &&
            (activity.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) && (activity.permission.isNullOrBlank() || context.checkSelfPermission(activity.permission) == PackageManager.PERMISSION_GRANTED)) {
            if (runCatching { context.startActivity(feature.setComponent(ComponentName(activity.packageName, activity.name))); true }.getOrDefault(false))
                return BreenoLaunchOutcome(true, false, "已发送系统侧边栏小布识屏功能请求，等待系统界面响应", listOf(activity.packageName))
        }
        val qs = BreenoQuickSettingsUi.launch(context)
        if (qs.sent) return BreenoLaunchOutcome(true, false, qs.message, emptyList())
        fun out(sent: Boolean, msg: String) = BreenoLaunchOutcome(sent, false, msg, snap.installedPackages)
        if (snap.installedPackages.isEmpty()) return out(false, listOfNotNull(directErr, qs.message, if (service != null && channels.isEmpty()) "此固件存在系统识屏服务，可启用小布特权调用后使用 Root / Shizuku 直达" else null).joinToString("；"))
        val tile = snap.recognitionTile ?: return out(false, listOfNotNull(directErr, qs.message, "未发现可供特权调用的小布识屏磁贴服务", if (service != null && channels.isEmpty()) "可启用小布特权调用后使用系统 Root / Shizuku 识屏入口" else null).joinToString("；"))
        if (channels.isEmpty()) return out(false, "调用系统识屏磁贴需要先启用小布特权调用（支持 Root 或 Shizuku）")
        val (tileCh, tileFails) = executePrivilegedChannels(context, channels, snap.installedPackages, tileComp = tile.flattenToString())
        return if (tileCh != null) out(true, "已通过 ${tileCh.label} 发送系统小布识屏磁贴请求；系统是否显示识屏界面仍需核对")
        else out(false, (listOfNotNull(directErr) + tileFails).joinToString("；").ifBlank { "小布识屏请求失败" })
    }

    fun defaultAssistConfiguredHint(context: Context): String = runCatching { Settings.Secure.getString(context.contentResolver, "assistant") }.getOrNull().orEmpty().ifBlank { "未指定系统默认语音助手" }
}
