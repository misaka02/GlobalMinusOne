package com.example.globalminusone

import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import com.example.globalminusone.data.*
import com.example.globalminusone.service.OverlayService
import com.example.globalminusone.system.DeviceShortcutDiscovery
import com.example.globalminusone.system.DeviceShortcutProviderInfo
import com.example.globalminusone.system.ShortcutDiscoveryPolicy
import com.example.globalminusone.theme.SettingsTheme
import com.example.globalminusone.ui.panel.cards.IndexedSelectionList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.MinusOneRepository

private data class ActivityChoice(val component: ComponentName, val label: String, val app: String)

/** Single contact/explicit exported activity selection. No contacts database permission or Root. */
class ActionPickerActivity : ComponentActivity() {
    private val request by lazy { ActionPickerRequest.read(intent) }
    private val repository by lazy { MinusOneRepository.getInstance(this) }
    private val contacts = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == RESULT_OK && uri != null && uri.authority == ContactsContract.AUTHORITY) {
            val name = runCatching {
                contentResolver.query(uri, arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY), null, null, null)
                    ?.use { if (it.moveToFirst()) it.getString(0) else null }
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: "联系人"
            save(ComponentItem(type = ComponentType.CUSTOM_SHORTCUT, title = name, subtitle = "查看联系人",
                intentUri = Intent(Intent.ACTION_VIEW, uri).toUri(Intent.URI_INTENT_SCHEME), iconName = "person"))
        } else finishPicker()
    }
    private var pendingShortcutProviderPkg: String? = null
    private val createShortcutLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            val saved = handleCreateShortcutResult(data)
            if (!saved) {
                Toast.makeText(this, "无法解析该应用返回的快捷方式", Toast.LENGTH_LONG).show()
                finishPicker()
            }
        } else {
            finishPicker()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingShortcutProviderPkg = savedInstanceState?.getString(STATE_PENDING_SHORTCUT_PKG)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finishPicker() }
        })
        if (request?.kind == ActionPickerKind.CONTACT) {
            if (savedInstanceState == null) runCatching {
                contacts.launch(Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI))
            }.onFailure { Toast.makeText(this, "没有可用的联系人选择器", Toast.LENGTH_SHORT).show(); finishPicker() }
            return
        }
        val directShortcutComp = request?.takeIf { it.kind == ActionPickerKind.CREATE_SHORTCUT }
            ?.shortcutComponent?.let { ComponentName.unflattenFromString(it) }
        if (directShortcutComp != null) {
            if (savedInstanceState == null) launchCreateShortcutProvider(directShortcutComp)
            return
        }
        if (request?.kind == ActionPickerKind.CREATE_SHORTCUT) {
            setContent {
                val state by repository.state.collectAsState()
                val darkTheme = if (state.advancedConfig.settingsFollowSystemTheme) {
                    isSystemInDarkTheme()
                } else {
                    state.advancedConfig.settingsDarkMode
                }
                SettingsTheme(darkTheme) {
                    var providers by remember { mutableStateOf<List<DeviceShortcutProviderInfo>>(emptyList()) }
                    var loading by remember { mutableStateOf(true) }
                    LaunchedEffect(Unit) {
                        providers = withContext(Dispatchers.IO) {
                            DeviceShortcutDiscovery.queryCreateShortcutProviders(packageManager)
                        }
                        loading = false
                    }
                    Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize().padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("创建本机应用快捷方式", style = MaterialTheme.typography.titleLarge)
                                TextButton(onClick = { finishPicker() }) { Text("取消") }
                            }
                            Text("扫描本机声明了 ACTION_CREATE_SHORTCUT 的真实快捷方式生成器。")
                            if (loading) CircularProgressIndicator()
                            else IndexedSelectionList(
                                providers,
                                { "${it.packageName}/${it.activityName}" },
                                { it.appLabel },
                                searchText = { "${it.appLabel} ${it.shortcutLabel} ${it.packageName}" },
                                modifier = Modifier.weight(1f)
                            ) { provider ->
                                Surface(
                                    Modifier.fillMaxWidth().clickable {
                                        launchCreateShortcutProvider(ComponentName(provider.packageName, provider.activityName))
                                    },
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text("${provider.appLabel} · ${provider.shortcutLabel}")
                                        Text(provider.packageName, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return
        }
        setContent {
            val state by repository.state.collectAsState()
            val darkTheme = if (state.advancedConfig.settingsFollowSystemTheme) {
                isSystemInDarkTheme()
            } else {
                state.advancedConfig.settingsDarkMode
            }
            SettingsTheme(darkTheme) {
                var choices by remember { mutableStateOf<List<ActivityChoice>>(emptyList()) }
                var loading by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    choices = withContext(Dispatchers.IO) {
                        @Suppress("DEPRECATION")
                        packageManager.getInstalledPackages(0).flatMap { pkg ->
                            val activities = runCatching { packageManager.getPackageInfo(pkg.packageName, PackageManager.GET_ACTIVITIES).activities.orEmpty() }
                                .getOrDefault(emptyArray())
                            activities.filter { info ->
                                info.enabled && info.applicationInfo.enabled && info.exported &&
                                    (info.permission.isNullOrBlank() || checkSelfPermission(info.permission) == PackageManager.PERMISSION_GRANTED)
                            }.map { info -> ActivityChoice(ComponentName(info.packageName, info.name),
                                info.loadLabel(packageManager).toString(), info.applicationInfo.loadLabel(packageManager).toString()) }
                        }.distinctBy { it.component }
                    }
                    loading = false
                }
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("选择可启动活动", style = MaterialTheme.typography.titleLarge)
                            TextButton(onClick = { finishPicker() }) { Text("取消") }
                        }
                        Text("仅列出已启用、公开且当前应用有权限的活动；对方仍可能要求登录或拒绝启动。")
                        if (loading) CircularProgressIndicator()
                        else IndexedSelectionList(choices, { it.component.flattenToString() }, { it.app },
                            searchText = { "${it.app} ${it.label} ${it.component.flattenToString()}" }, modifier = Modifier.weight(1f)) { choice ->
                            Surface(Modifier.fillMaxWidth().clickable {
                                @Suppress("DEPRECATION")
                                val stillAvailable = runCatching {
                                    packageManager.getActivityInfo(choice.component, 0).let { info ->
                                        info.exported && info.enabled && info.applicationInfo.enabled &&
                                            (info.permission.isNullOrBlank() || checkSelfPermission(info.permission) == PackageManager.PERMISSION_GRANTED)
                                    }
                                }.getOrDefault(false)
                                if (!stillAvailable) {
                                    Toast.makeText(this@ActionPickerActivity, "活动已失效或无启动权限，未添加", Toast.LENGTH_SHORT).show()
                                    return@clickable
                                }
                                val launch = Intent().setComponent(choice.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                save(ComponentItem(type = ComponentType.CUSTOM_SHORTCUT, title = choice.label,
                                    subtitle = "活动 · ${choice.app}", intentUri = launch.toUri(Intent.URI_INTENT_SCHEME)))
                            }, color = MaterialTheme.colorScheme.surfaceVariant) {
                                Column(Modifier.padding(12.dp)) { Text(choice.label); Text(choice.component.flattenToShortString()) }
                            }
                        }
                    }
                }
            }
        }
    }
    private fun launchCreateShortcutProvider(component: ComponentName) {
        @Suppress("DEPRECATION")
        val usable = runCatching {
            val info = packageManager.getActivityInfo(component, 0)
            info.exported && info.enabled && info.applicationInfo.enabled &&
                (info.permission.isNullOrBlank() || checkSelfPermission(info.permission) == PackageManager.PERMISSION_GRANTED)
        }.getOrDefault(false)
        if (!usable) {
            Toast.makeText(this, "该快捷方式生成器不可用或无权限", Toast.LENGTH_SHORT).show()
            finishPicker()
            return
        }
        pendingShortcutProviderPkg = component.packageName
        runCatching {
            createShortcutLauncher.launch(Intent(Intent.ACTION_CREATE_SHORTCUT).setComponent(component))
        }.onFailure {
            Toast.makeText(this, "无法调起快捷方式配置界面: ${it.message}", Toast.LENGTH_SHORT).show()
            finishPicker()
        }
    }

    private inline fun <reified T : android.os.Parcelable> Intent.getParcelableCompat(key: String): T? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(key, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(key) as? T
        }

    private fun handleCreateShortcutResult(data: Intent): Boolean {
        val fallbackPkg = pendingShortcutProviderPkg.orEmpty()
        val pinReq = data.getParcelableCompat<LauncherApps.PinItemRequest>(LauncherApps.EXTRA_PIN_ITEM_REQUEST)
        if (pinReq != null && pinReq.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) {
            val info = pinReq.shortcutInfo
            if (info != null) {
                val launcherApps = getSystemService(LAUNCHER_APPS_SERVICE) as? LauncherApps
                val appPkg = info.`package`.ifBlank { fallbackPkg }
                val appLabel = runCatching {
                    @Suppress("DEPRECATION")
                    packageManager.getApplicationLabel(packageManager.getApplicationInfo(appPkg, 0)).toString()
                }.getOrDefault(appPkg)
                val title = (info.shortLabel ?: info.longLabel ?: appLabel).toString().trim().ifBlank { "快捷方式" }
                val iconUri = runCatching {
                    launcherApps?.getShortcutIconDrawable(info, resources.displayMetrics.densityDpi)
                }.getOrNull()?.let { DeviceShortcutDiscovery.drawableToBitmap(it) }
                    ?.let { DeviceShortcutDiscovery.saveShortcutIconBitmap(this, it) }

                val accepted = if (runCatching { launcherApps?.hasShortcutHostPermission() == true }.getOrDefault(false)) {
                    runCatching { pinReq.accept() }.getOrDefault(false)
                } else false

                val directIntent = info.intent ?: info.intents?.lastOrNull()
                val resolvedUri = when {
                    accepted -> ShortcutDiscoveryPolicy.buildAppShortcutUri(appPkg, info.id)
                    directIntent != null -> directIntent.toUri(Intent.URI_INTENT_SCHEME)
                    else -> null
                }
                if (resolvedUri != null) {
                    save(
                        ComponentItem(
                            type = ComponentType.CUSTOM_SHORTCUT,
                            title = title,
                            subtitle = "$appLabel · 快捷方式",
                            packageName = appPkg,
                            intentUri = resolvedUri,
                            iconUri = iconUri.orEmpty(),
                            iconName = "bolt"
                        )
                    )
                    return true
                }
                Toast.makeText(this, "该应用返回了系统级固定快捷方式，非默认桌面无权接管；可改用“指定活动”", Toast.LENGTH_LONG).show()
                finishPicker()
                return true
            }
        }

        @Suppress("DEPRECATION")
        val shortcutIntent = data.getParcelableCompat<Intent>(Intent.EXTRA_SHORTCUT_INTENT) ?: return false
        @Suppress("DEPRECATION")
        val rawName = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME)?.trim().orEmpty()
        val resolvedPkg = shortcutIntent.component?.packageName
            ?: shortcutIntent.`package`
            ?: fallbackPkg
        val appLabel = if (resolvedPkg.isNotBlank()) {
            runCatching {
                @Suppress("DEPRECATION")
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(resolvedPkg, 0)).toString()
            }.getOrDefault(resolvedPkg)
        } else {
            "快捷方式"
        }
        val title = rawName.ifBlank { appLabel }

        @Suppress("DEPRECATION")
        val directBmp = data.getParcelableCompat<Bitmap>(Intent.EXTRA_SHORTCUT_ICON)
        val iconBmp = directBmp ?: run {
            @Suppress("DEPRECATION")
            val iconRes = data.getParcelableCompat<Intent.ShortcutIconResource>(Intent.EXTRA_SHORTCUT_ICON_RESOURCE)
            if (iconRes != null) {
                runCatching {
                    val targetRes = packageManager.getResourcesForApplication(iconRes.packageName)
                    val resId = targetRes.getIdentifier(iconRes.resourceName, null, null)
                    if (resId != 0) {
                        ResourcesCompat.getDrawable(targetRes, resId, null)?.let {
                            DeviceShortcutDiscovery.drawableToBitmap(it)
                        }
                    } else null
                }.getOrNull()
            } else null
        }
        val iconUri = iconBmp?.let { DeviceShortcutDiscovery.saveShortcutIconBitmap(this, it) }

        save(
            ComponentItem(
                type = ComponentType.CUSTOM_SHORTCUT,
                title = title,
                subtitle = "$appLabel · 快捷方式",
                packageName = resolvedPkg,
                intentUri = shortcutIntent.toUri(Intent.URI_INTENT_SCHEME),
                iconUri = iconUri.orEmpty(),
                iconName = "bolt"
            )
        )
        return true
    }

    private fun save(item: ComponentItem) {
        val target = request
        val saved = target != null && repository.placeMenuComponent(
            target.pageId, target.containerId, item, target.beforeComponentId, target.targetGridCell
        )
        Toast.makeText(this, if (saved) "已添加${item.title}" else repository.persistenceError.value
            ?: "布局已锁定或原目标已失效，未添加", Toast.LENGTH_SHORT).show()
        if (!saved) repository.acknowledgePersistenceError()
        finishPicker()
    }
    private fun finishPicker() {
        if (request != null && android.provider.Settings.canDrawOverlays(this) && repository.state.value.triggerConfig.serviceEnabled) {
            OverlayService.start(this, OverlayService.ACTION_OPEN_PANEL)
        }
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingShortcutProviderPkg?.let { outState.putString(STATE_PENDING_SHORTCUT_PKG, it) }
    }

    private companion object {
        const val STATE_PENDING_SHORTCUT_PKG = "pending_shortcut_provider_pkg"
    }
}
