package com.example.globalminusone.ui.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.globalminusone.data.ScreenAssistantConfig
import com.example.globalminusone.screenassistant.NativeOcrRunner
import com.example.globalminusone.screenassistant.OcrMirrorStore
import com.example.globalminusone.screenassistant.OcrModelPack
import com.example.globalminusone.screenassistant.OcrModelStore
import com.example.globalminusone.screenassistant.ScreenCaptureHub
import com.example.globalminusone.system.UserRootSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OcrEngineSettingsCard(
    config: ScreenAssistantConfig,
    allowRootPrivilege: Boolean,
    probeNonce: Int,
    onSelectPack: (String) -> Unit
) {
    val app = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var store by remember { mutableStateOf<OcrModelStore?>(null) }
    var nativeInv by remember { mutableStateOf<OcrModelStore.NativeInventory?>(null) }
    var models by remember { mutableStateOf<List<OcrModelPack>>(emptyList()) }
    var readyMap by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var busyJob by remember { mutableStateOf<Job?>(null) }
    var progressText by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("") }

    var showMirrorDialog by remember { mutableStateOf(false) }
    var mirrorInput by remember { mutableStateOf(OcrMirrorStore.getMirrorPrefix(app)) }
    var showImportOnnxDialog by remember { mutableStateOf(false) }
    var importRecUri by remember { mutableStateOf<Uri?>(null) }
    var importDetUri by remember { mutableStateOf<Uri?>(null) }
    var importDictUri by remember { mutableStateOf<Uri?>(null) }
    var importTitle by remember { mutableStateOf("") }

    suspend fun reloadAll() {
        withContext(Dispatchers.IO) {
            val s = store ?: OcrModelStore(app).also { store = it }
            val n = s.nativeInventory()
            val all = s.allModels()
            val rMap = all.associate { it.id to s.isModelReady(it.id) }
            nativeInv = n
            models = all
            readyMap = rMap
        }
    }

    LaunchedEffect(app, probeNonce) {
        reloadAll()
    }

    fun launchTask(block: suspend () -> String) {
        if (busyJob?.isActive == true) return
        statusText = ""
        progressText = ""
        busyJob = scope.launch {
            try {
                statusText = block()
            } catch (c: CancellationException) {
                statusText = "操作已取消"
                throw c
            } catch (err: Throwable) {
                statusText = err.message ?: "OCR 操作失败"
            } finally {
                progressText = ""
                busyJob = null
            }
        }
    }

    val importNativeZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            launchTask {
                progressText = "正在校验并导入 ARM64 原生引擎…"
                val rec = (store ?: OcrModelStore(app)).importNativeRuntimeArchive(uri)
                reloadAll()
                "已导入原生引擎 v${rec.version}（ORT ${rec.ortVersion}）"
            }
        }
    }
    val pickRec = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importRecUri = uri
    }
    val pickDet = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importDetUri = uri
    }
    val pickDict = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importDictUri = uri
    }

    val rootReady = UserRootSession.authorizedEpoch() != null
    val shizukuReady = ScreenCaptureHub.shizukuAuthorized()
    val privHint = when {
        rootReady -> "Root 已就绪（直读模型与缓存）"
        shizukuReady -> "Shizuku 已授权（UserService 守护进程）"
        allowRootPrivilege -> "未连接 Root · 识屏时自动申请授权"
        else -> "未授权 Root / Shizuku（点击下方可授权 Shizuku）"
    }
    val busy = busyJob?.isActive == true
    val inv = nativeInv

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Shizuku / Root 原生 ARM64 OCR 引擎", style = MaterialTheme.typography.titleSmall)
            val nativeSummary = when {
                inv == null -> "正在检测设备 ABI 与本地原生引擎…"
                !inv.arm64Supported -> "当前设备非 arm64-v8a，无法运行 ARM64 原生引擎"
                inv.revoked -> "当前线上版本已由维护者撤回"
                inv.installed -> "已安装 v${inv.installedVersion} · ORT ${inv.ortVersion} · 权限：$privHint"
                else -> "可选外置下载（约 ${String.format(Locale.US, "%.1f", inv.expectedArchiveBytes / 1_000_000.0)} MB）· 不进入主 APK · 权限：$privHint"
            }
            Text(nativeSummary, style = MaterialTheme.typography.bodySmall)

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(enabled = inv?.arm64Supported == true && !busy, onClick = {
                    launchTask {
                        progressText = "正在检查原生 OCR 更新目录…"
                        val cat = (store ?: OcrModelStore(app)).refreshNative()
                        reloadAll()
                        if (cat == null) "暂未发现新版本原生引擎，可导入本地 ZIP" else "已同步目录 rev ${cat.revision} (v${cat.version})"
                    }
                }) { Text("检查引擎") }

                TextButton(enabled = inv?.arm64Supported == true && inv.revoked != true && !busy, onClick = {
                    launchTask {
                        val rec = (store ?: OcrModelStore(app)).downloadNativeRuntime { name, count, total ->
                            val pct = if (total > 0L) (count * 100 / total).coerceIn(0, 100) else 0L
                            progressText = "下载 $name · $pct%"
                        }
                        reloadAll()
                        "已安装并校验原生引擎 v${rec.version}"
                    }
                }) { Text(if (inv?.installed == true) "重装引擎" else "下载引擎") }

                TextButton(enabled = inv?.arm64Supported == true && inv.revoked != true && !busy, onClick = {
                    importNativeZip.launch(arrayOf("application/zip", "application/octet-stream"))
                }) { Text("导入ZIP") }

                if (inv?.installed == true) {
                    TextButton(enabled = !busy, onClick = {
                        launchTask {
                            NativeOcrRunner.shutdown()
                            (store ?: OcrModelStore(app)).deleteNativeRuntime()
                            reloadAll()
                            "已删除原生 ARM64 引擎"
                        }
                    }) { Text("删引擎") }

                    TextButton(enabled = !busy && (rootReady || shizukuReady || allowRootPrivilege), onClick = {
                        launchTask {
                            val activePack = config.wasmOcrPackId.ifBlank { "ch_v5" }
                            progressText = "正在使用 $activePack 执行真图自检…"
                            runSelfTest(app, activePack, allowRootPrivilege)
                        }
                    }) { Text("真图自检") }
                }

                if (!shizukuReady && ScreenCaptureHub.shizukuAvailable()) {
                    TextButton(enabled = !busy, onClick = {
                        launchTask {
                            val ok = ScreenCaptureHub.requestShizukuAuthorization()
                            reloadAll()
                            if (ok) "Shizuku 已授权" else "Shizuku 未授予权限"
                        }
                    }) { Text("授权 Shizuku") }
                }
            }

            if (inv?.installed == true && inv.installedVersion == "1.0.0") {
                Text(
                    "检测到旧版原生引擎 v1.0.0（XNNPACK 可能导致 v6_small / ch_v5 异常），请点上方「重装引擎」升级至 v1.0.1",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (progressText.isNotBlank()) Text(progressText, style = MaterialTheme.typography.bodySmall)
            if (statusText.isNotBlank()) {
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text("PP-OCR 识别模型（不进入主包 · 本地 SHA-256 校验）", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(enabled = !busy, onClick = {
                    mirrorInput = OcrMirrorStore.getMirrorPrefix(app)
                    showMirrorDialog = true
                }) {
                    val configured = OcrMirrorStore.getMirrorPrefix(app).isNotBlank()
                    Text(if (configured) "GitHub 镜像（已配）" else "下载镜像")
                }
                TextButton(enabled = !busy, onClick = {
                    importRecUri = null
                    importDetUri = null
                    importDictUri = null
                    importTitle = ""
                    showImportOnnxDialog = true
                }) { Text("导入本地 ONNX") }
            }

            for (pack in models) {
                val ready = readyMap[pack.id] == true
                val active = config.wasmOcrPackId == pack.id
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                        Text(pack.title, style = MaterialTheme.typography.bodyMedium)
                        val mb = pack.files.sumOf { it.bytes } / 1_000_000.0
                        val sub = if (ready) "已就绪 · ${String.format(Locale.US, "%.1f MB", mb)}" else "需下载 · ${String.format(Locale.US, "%.1f MB", mb)}"
                        Text(sub, style = MaterialTheme.typography.bodySmall)
                    }
                    Row {
                        if (ready && !active) {
                            TextButton(enabled = !busy, onClick = {
                                launchTask {
                                    (store ?: OcrModelStore(app)).deleteModel(pack.id)
                                    reloadAll()
                                    "已删除模型「${pack.title}」"
                                }
                            }) { Text("删除") }
                        }
                        TextButton(
                            enabled = !busy && !(ready && active),
                            onClick = {
                                if (ready) {
                                    onSelectPack(pack.id)
                                    statusText = "已切换为 ${pack.title}"
                                } else {
                                    launchTask {
                                        (store ?: OcrModelStore(app)).downloadModel(pack.id) { name, count, total ->
                                            val pct = if (total > 0L) (count * 100 / total).coerceIn(0, 100) else 0L
                                            progressText = "下载 $name · $pct%"
                                        }
                                        reloadAll()
                                        onSelectPack(pack.id)
                                        "已下载并启用 ${pack.title}"
                                    }
                                }
                            }
                        ) {
                            Text(
                                when {
                                    ready && active -> "使用中"
                                    ready -> "使用"
                                    else -> "下载"
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showMirrorDialog) {
        AlertDialog(
            onDismissRequest = { showMirrorDialog = false },
            title = { Text("GitHub 加速镜像前缀") },
            text = {
                OutlinedTextField(
                    value = mirrorInput,
                    onValueChange = { mirrorInput = it },
                    singleLine = true,
                    label = { Text("https://... 前缀（留空直连）") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { OcrMirrorStore.saveMirrorPrefix(app, mirrorInput) }
                        .onSuccess {
                            statusText = if (it.isBlank()) "已恢复直连" else "已设置镜像：$it"
                            showMirrorDialog = false
                        }
                        .onFailure { statusText = it.message ?: "镜像格式无效" }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showMirrorDialog = false }) { Text("取消") } }
        )
    }

    if (showImportOnnxDialog) {
        AlertDialog(
            onDismissRequest = { showImportOnnxDialog = false },
            title = { Text("导入本地 ONNX 模型") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = importTitle,
                        onValueChange = { importTitle = it },
                        singleLine = true,
                        label = { Text("模型名称（可选）") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(onClick = { pickRec.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (importRecUri == null) "1. 选择识别模型 (*rec*.onnx)" else "已选识别模型 ✓")
                    }
                    OutlinedButton(onClick = { pickDet.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (importDetUri == null) "2. 选择检测模型 (可选)" else "已选检测模型 ✓")
                    }
                    OutlinedButton(onClick = { pickDict.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (importDictUri == null) "3. 选择字典 dict.txt (可选)" else "已选字典 ✓")
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = importRecUri != null && !busy, onClick = {
                    val rec = importRecUri ?: return@TextButton
                    showImportOnnxDialog = false
                    launchTask {
                        progressText = "正在校验并导入 ONNX 模型…"
                        val pack = (store ?: OcrModelStore(app)).importLocalPaddleModel(rec, importDetUri, importDictUri, importTitle)
                        reloadAll()
                        onSelectPack(pack.id)
                        "已导入并启用「${pack.title}」"
                    }
                }) { Text("导入") }
            },
            dismissButton = { TextButton(onClick = { showImportOnnxDialog = false }) { Text("取消") } }
        )
    }
}

private suspend fun runSelfTest(context: Context, preferredPackId: String, allowRoot: Boolean): String {
    val store = withContext(Dispatchers.IO) { OcrModelStore(context) }
    val packId = if (preferredPackId.isNotBlank() && store.isModelReady(preferredPackId)) {
        preferredPackId
    } else {
        store.firstReadyModelId() ?: return "尚未下载任何 PP-OCR 模型，请先下载"
    }
    val bmp = Bitmap.createBitmap(680, 180, Bitmap.Config.ARGB_8888)
    try {
        Canvas(bmp).apply {
            drawColor(Color.WHITE)
            drawText("HELLO 12345", 42f, 116f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 85f
            })
        }
        val t0 = SystemClock.elapsedRealtime()
        val res = NativeOcrRunner.recognize(context, bmp, packId, allowRootAutoConnect = allowRoot)
            ?: return "原生引擎自检失败 [$packId]：${NativeOcrRunner.lastDiagnostic.ifBlank { "请确认 Root/Shizuku 授权" }}"
        val ms = (SystemClock.elapsedRealtime() - t0).coerceAtLeast(0L)
        val txt = res.blocks.joinToString(" ") { it.text }.take(100)
        return if (txt.contains("HELLO", true) || txt.contains("12345")) {
            "原生引擎真图自检通过 [$packId]（${ms}ms · ${res.engineUsed}）· $txt"
        } else {
            "原生引擎返回异常结果 [$packId]（${ms}ms）· ${txt.ifBlank { "<空>" }}"
        }
    } finally {
        if (!bmp.isRecycled) bmp.recycle()
    }
}
