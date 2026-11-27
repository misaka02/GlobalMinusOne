package com.example.globalminusone.ui.panel.cards

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.service.OverlayService
import com.example.globalminusone.system.*
import com.example.globalminusone.ui.AppIcons as Icons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import com.example.globalminusone.system.media.RadioController
import com.example.globalminusone.system.media.RadioKind
import com.example.globalminusone.system.media.RadioState
import com.example.globalminusone.system.privilege.RootStatus
import com.example.globalminusone.system.SystemController
import com.example.globalminusone.system.media.supportsPublicRadioToggle
import com.example.globalminusone.ui.panel.PanelAlertDialog

private fun radioLabel(state: RadioState) = when (state) {
    RadioState.On -> "已开启"; RadioState.Off -> "已关闭"
    RadioState.TurningOn -> "正在开启"; RadioState.TurningOff -> "正在关闭"
    RadioState.Unsupported -> "设备不支持"; RadioState.Unknown -> "状态未确认"
}
private fun rootLabel(status: RootStatus) = when (status) {
    RootStatus.NotRequested -> "尚未请求本次Root授权"
    RootStatus.Granted -> "本次Root已授权"
    RootStatus.Unavailable -> "未找到可用Root"
    RootStatus.Denied -> "Root未授权"
    RootStatus.TimedOut -> "Root授权超时"
    RootStatus.Error -> "Root操作失败"
}

@Composable
internal fun RadioControlTile(kind: RadioKind, system: SystemController, repository: MinusOneRepository,
    enabled: Boolean, onRequestClose: () -> Unit) {
    RadioControlTileContent(kind, rememberRadioCardControl(system), rememberRadioAuthorizationActions(system),
        { repository.isPanelExpanded }, enabled, onRequestClose)
}

@Composable
internal fun RadioControlTileContent(kind: RadioKind, control: RadioCardControl, authorization: RadioAuthorizationActions,
    expandedState: () -> StateFlow<Boolean>, enabled: Boolean, onRequestClose: () -> Unit) {
    val state by (if (kind == RadioKind.WIFI) control.wifi else control.bluetooth).collectAsState()
    val backend by control.backend.collectAsState()
    val root by authorization.rootStatus.collectAsState()
    val expanded by expandedState().collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var opened by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<Job?>(null) }
    val name = if (kind == RadioKind.WIFI) "Wi-Fi" else "蓝牙"
    val route = when (backend) { RadioController.Backend.PUBLIC -> "系统控制"; RadioController.Backend.SHIZUKU -> "Shizuku"; RadioController.Backend.ROOT -> "Root" }
    DisposableEffect(control, expanded) {
        if (expanded) control.startObserving()
        onDispose { if (expanded) control.stopObserving() }
    }
    LaunchedEffect(expanded, root, backend) { if (expanded) control.refresh(kind) }
    fun toggle() {
        if (busy) return
        if ((backend == RadioController.Backend.ROOT && root != RootStatus.Granted) ||
            (backend == RadioController.Backend.SHIZUKU && !control.hasShizukuAuthorization())) { opened = true; return }
        if (backend == RadioController.Backend.PUBLIC && !supportsPublicRadioToggle(kind, Build.VERSION.SDK_INT,
                androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED)) {
            if (control.openOfficialControl(kind)) onRequestClose() else { message = "系统控制界面不可用"; opened = true }
            return
        }
        busy = true
        pending = scope.launch {
            try {
                control.refresh(kind)
                val actual = (if (kind == RadioKind.WIFI) control.wifi else control.bluetooth).value
                if (actual !in setOf(RadioState.On, RadioState.Off)) { message = "系统状态尚未确认，请刷新后重试"; opened = true }
                else {
                    val result = control.setEnabled(kind, actual != RadioState.On)
                    message = result.message
                    if (!result.succeeded) { opened = true }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message = "操作未确认，请检查控制方式"; opened = true }
            finally { busy = false }
        }
    }
    SwitchTileComponent(title = name, icon = if (kind == RadioKind.WIFI) Icons.Rounded.Wifi else Icons.Rounded.Bluetooth,
        active = state == RadioState.On, enabled = enabled && !busy,
        subtitle = if (busy) "正在切换…" else "$route · 点按切换", onClick = ::toggle)
    if (opened) PanelAlertDialog(onDismissRequest = { if (!busy) opened = false }, title = { Text("$name · 控制方式") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${radioLabel(state)} · $route")
            Text("选定方式后，点按卡片直接操作；也可在卡片属性中更换控制方式。")
            if (message.isNotBlank()) Text(message)
            RadioControlSettingsContent(control, authorization)
            if (busy) { LinearProgressIndicator(); TextButton(onClick = { pending?.cancel(); pending = null; busy = false; message = "已取消本次等待" }) { Text("取消操作") } }
            TextButton(enabled = !busy, onClick = { pending = scope.launch { control.refresh(kind) } }) { Text("刷新状态") }
        } }, confirmButton = { TextButton(enabled = !busy, onClick = { opened = false }) { Text("完成") } })
}

/** Explicit permission choices, shared by card properties and the first-use prompt. */
@Composable
internal fun RadioControlSettings(system: SystemController) {
    RadioControlSettingsContent(rememberRadioCardControl(system), rememberRadioAuthorizationActions(system))
}

@Composable
internal fun RadioControlSettingsContent(control: RadioCardControl, authorization: RadioAuthorizationActions) {
    val backend by control.backend.collectAsState()
    val root by authorization.rootStatus.collectAsState()
    val authorizationPending by authorization.externalAuthorizationPending.collectAsState()
    var pending by remember { mutableStateOf<Job?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val waiting = busy || authorizationPending
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("控制方式对 Wi-Fi 和蓝牙共同生效。系统控制无需 Root；新系统的面板内直接切换可使用 Shizuku 或 Root。已有授权会复用。")
        TextButton(enabled = !waiting, onClick = {
            control.selectBackend(RadioController.Backend.PUBLIC)
            message = "已选择系统控制"
        }) { Text(if (backend == RadioController.Backend.PUBLIC) "✓ 系统控制 · 无需 Root" else "系统控制 · 无需 Root") }
        OutlinedButton(enabled = !waiting, onClick = {
            control.selectBackend(RadioController.Backend.SHIZUKU)
            if (control.hasShizukuAuthorization()) message = "已复用 Shizuku 授权"
            else {
                busy = true
                pending = authorization.requestShizukuAuthorization { granted ->
                    busy = false
                    message = if (granted) "Shizuku 已授权，下次点按直接切换" else "Shizuku 尚未授权"
                }
            }
        }) { Text(if (backend == RadioController.Backend.SHIZUKU) "✓ Shizuku · 无需 Root" else "Shizuku · 无需 Root") }
        OutlinedButton(enabled = !waiting, onClick = {
            control.selectBackend(RadioController.Backend.ROOT)
            if (root == RootStatus.Granted) message = "已复用 Root 会话"
            else {
                busy = true
                pending = authorization.requestRootAuthorization { result -> busy = false; message = result.reason }
            }
        }) { Text(if (backend == RadioController.Backend.ROOT) "✓ Root" else "Root") }
        if (waiting) {
            LinearProgressIndicator()
            TextButton(onClick = { pending?.cancel(); pending = null; busy = false; message = "已取消本次等待" }) { Text("取消等待") }
        }
        if (root == RootStatus.Granted) TextButton(enabled = !waiting, onClick = {
            authorization.cancelRootAuthorization()
            control.selectBackend(RadioController.Backend.PUBLIC)
            message = "Root 会话已结束"
        }) { Text("结束 Root 会话") }
        if (message.isNotBlank()) Text(message)
    }
}
