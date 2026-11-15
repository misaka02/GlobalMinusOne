package com.example.globalminusone

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.globalminusone.data.*
import com.example.globalminusone.service.OverlayService
import com.example.globalminusone.theme.SettingsTheme
import com.example.globalminusone.ui.panel.IndexedSelectionList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finishPicker() }
        })
        if (request?.kind == ActionPickerKind.CONTACT) {
            if (savedInstanceState == null) runCatching {
                contacts.launch(Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI))
            }.onFailure { Toast.makeText(this, "没有可用的联系人选择器", Toast.LENGTH_SHORT).show(); finishPicker() }
            return
        }
        setContent {
            val state by repository.state.collectAsState()
            SettingsTheme(state.advancedConfig.settingsDarkMode) {
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
    private fun save(item: ComponentItem) {
        val target = request
        val saved = target != null && repository.placeMenuComponent(target.pageId, target.containerId, item, target.beforeComponentId)
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
}
