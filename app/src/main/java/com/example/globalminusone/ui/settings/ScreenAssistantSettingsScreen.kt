package com.example.globalminusone.ui.settings

import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.ScreenAssistantConfig
import com.example.globalminusone.data.ScreenOcrEngine
import com.example.globalminusone.data.ScreenTranslateEngine
import com.example.globalminusone.data.TraditionalTranslateProvider
import com.example.globalminusone.screenassistant.BreenoProbeSnapshot
import com.example.globalminusone.screenassistant.BreenoTouchBridge
import com.example.globalminusone.screenassistant.DownloadedLexiconAttachment
import com.example.globalminusone.screenassistant.ScreenAssistantAttachmentManager
import com.example.globalminusone.screenassistant.ScreenAssistantSecretStore
import com.example.globalminusone.screenassistant.SystemViewCaptureBridge
import com.example.globalminusone.service.OverlayService
import kotlinx.coroutines.launch

@Composable
internal fun ScreenAssistantSettingsScreen(
    repository: MinusOneRepository,
    forceExpandOcrToken: Long = 0L,
    initialExpandedGroup: String = "",
    jumpNonce: Long = 0L,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by repository.state.collectAsState()
    val config = state.advancedConfig.screenAssistantConfig
    val app = context.applicationContext

    var expandedGroup by rememberSaveable { mutableStateOf(initialExpandedGroup.ifBlank { "OCR" }) }
    val listState = rememberLazyListState()
    var breenoProbeTrigger by remember { mutableIntStateOf(0) }
    LaunchedEffect(forceExpandOcrToken) {
        if (forceExpandOcrToken > 0L) {
            expandedGroup = "OCR"
        }
    }
    LaunchedEffect(initialExpandedGroup, jumpNonce) {
        if (initialExpandedGroup.isNotBlank()) {
            expandedGroup = initialExpandedGroup
            val idx = when (initialExpandedGroup) {
                "OCR" -> 2
                "TRANSLATE" -> 3
                "ATTACHMENTS" -> 4
                "BREENO" -> 5
                else -> null
            }
            if (idx != null) listState.animateScrollToItem(idx)
        }
    }

    var showOcrEngineDialog by remember { mutableStateOf(false) }
    var showTranslateEngineDialog by remember { mutableStateOf(false) }
    var showTraditionalProviderDialog by remember { mutableStateOf(false) }
    var showTargetLangDialog by remember { mutableStateOf(false) }

    var showLlmConfigDialog by remember { mutableStateOf(false) }
    var showVisionOcrDialog by remember { mutableStateOf(false) }
    var showCustomOcrDialog by remember { mutableStateOf(false) }
    var showCustomLibreDialog by remember { mutableStateOf(false) }
    var showDownloadLexiconDialog by remember { mutableStateOf(false) }
    var showLsposedWarningDialog by remember { mutableStateOf(false) }


    var lexicons by remember { mutableStateOf<List<DownloadedLexiconAttachment>>(emptyList()) }
    var breenoProbe by remember { mutableStateOf<BreenoProbeSnapshot?>(null) }
    var lsposedPingStatus by remember { mutableStateOf<SystemViewCaptureBridge.PingStatus?>(null) }

    fun updateSaConfig(transform: (ScreenAssistantConfig) -> ScreenAssistantConfig) {
        repository.updateAdvancedConfig { adv ->
            adv.copy(screenAssistantConfig = transform(adv.screenAssistantConfig))
        }
    }

    fun refreshAttachments() {
        lexicons = ScreenAssistantAttachmentManager.listLexicons(context)
    }

    LaunchedEffect(Unit) {
        refreshAttachments()
    }
    LaunchedEffect(breenoProbeTrigger) {
        breenoProbe = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            BreenoTouchBridge.probe(context)
        }
        lsposedPingStatus = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            SystemViewCaptureBridge.ping()
        }
    }

    val importLexiconLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val res = ScreenAssistantAttachmentManager.importLexiconFromUri(context, uri)
                res.onSuccess { added ->
                    refreshAttachments()
                    updateSaConfig {
                        it.copy(
                            activeLexiconId = added.id,
                            activeLexiconName = added.name
                        )
                    }
                    Toast.makeText(context, "已导入词库「${added.name}」(${added.entryCount} 条)", Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    Toast.makeText(context, "导入失败: ${err.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SubPageTopBar(title = "全局识屏与翻译", onBack = onBack)

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 36.dp)
        ) {
            item {
                SettingsHint(
                    "全部能力可选：内置仿小布识屏（选字/原位翻译/提取缩略图）与系统小布桥接并存；OCR 扩展与离线词库均由你手动下载或导入；主安装包不包含模型和识别引擎。"
                )
            }

            settingsGroup(
                title = "文字识别 (OCR) 与原图提取",
                summary = "当前引擎：${config.ocrEngine.label}",
                expanded = expandedGroup == "OCR",
                onToggle = { expandedGroup = if (expandedGroup == "OCR") "" else "OCR" }
            ) {
                item {
                    PreferenceItem(
                        title = "OCR 识别引擎",
                        summary = "${config.ocrEngine.label}\n${config.ocrEngine.description}",
                        onClick = { showOcrEngineDialog = true }
                    )
                }

                item(key = "ocr-engine-settings") {
                    OcrEngineSettingsCard(
                        config = config,
                        allowRootPrivilege = state.advancedConfig.rootModeEnabled || config.usePrivilegedCapture,
                        probeNonce = breenoProbeTrigger,
                        onSelectPack = { id ->
                            com.example.globalminusone.screenassistant.NativeOcrRunner.shutdown()
                            updateSaConfig {
                                it.copy(
                                    ocrEngine = ScreenOcrEngine.PPOCR_WASM,
                                    wasmOcrPackId = id,
                                    ppOcrLangPack = id
                                )
                            }
                        }
                    )
                }

                item {
                    val keyMask = ScreenAssistantSecretStore.maskKey(ScreenAssistantSecretStore.getVisionLlmApiKey(context))
                    PreferenceItem(
                        title = "视觉大模型 OCR 接口 (Vision LLM)",
                        summary = "模型：${config.visionLlmModel} · 密钥：$keyMask（仅保存在本机隐私区）",
                        onClick = { showVisionOcrDialog = true }
                    )
                }

                item {
                    val keyMask = ScreenAssistantSecretStore.maskKey(ScreenAssistantSecretStore.getCustomOcrApiKey(context))
                    PreferenceItem(
                        title = "自定义 HTTP OCR 接口",
                        summary = "${config.customOcrUrl.ifBlank { "未配置地址" }} · 密钥：$keyMask",
                        onClick = { showCustomOcrDialog = true }
                    )
                }

                item {
                    PreferenceSwitchItem(
                        title = "同时识别界面缩略图与小图",
                        summary = "在全局识屏与区域截屏结果中自动提取封面/卡片小图，支持保存或单图再次识字",
                        checked = config.extractSubImagesEnabled,
                        onCheckedChange = { enabled ->
                            updateSaConfig { it.copy(extractSubImagesEnabled = enabled) }
                        }
                    )
                }

                item {
                    PreferenceSwitchItem(
                        title = "LSPosed 系统框架单层提图（实验性 · 未经测试）",
                        summary = buildString {
                            append("默认关闭；仅需在 LSPosed 勾选「系统框架」，不注入任何第三方应用。")
                            lsposedPingStatus?.let { ping ->
                                append("\n状态：")
                                append(ping.note)
                            }
                        },
                        checked = config.lsposedViewCaptureEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                showLsposedWarningDialog = true
                            } else {
                                updateSaConfig { it.copy(lsposedViewCaptureEnabled = false) }
                            }
                        }
                    )
                }

                item {
                    val privStatus = remember(breenoProbeTrigger, config.usePrivilegedCapture) {
                        val rootOk = com.example.globalminusone.system.UserRootSession.authorizedEpoch() != null
                        val shizukuOk = com.example.globalminusone.screenassistant.ScreenCaptureHub.shizukuAuthorized()
                        val shizukuPresent = com.example.globalminusone.screenassistant.ScreenCaptureHub.shizukuAvailable()
                        when {
                            rootOk && shizukuOk -> "已就绪：Root + Shizuku（原画质截屏 / 原生 OCR）"
                            rootOk -> "已就绪：Root（原画质截屏 / 原生 OCR）"
                            shizukuOk -> "已就绪：Shizuku（免 Root 原画质截屏 / 原生 OCR）"
                            shizukuPresent -> "检测到 Shizuku 服务，开启后将请求授权"
                            else -> "未检测到已授权的 Root 或 Shizuku，不可用时回退无障碍截屏"
                        }
                    }
                    PreferenceSwitchItem(
                        title = "主动请求 Shizuku / Root 原画质与深层节点",
                        summary = "开启后将自动连接已授权的 Root 或主动弹出 Shizuku 授权\n$privStatus",
                        checked = config.usePrivilegedCapture,
                        onCheckedChange = { enabled ->
                            updateSaConfig { it.copy(usePrivilegedCapture = enabled) }
                            if (enabled) {
                                com.example.globalminusone.screenassistant.ScreenCaptureHub.clearShizukuCooldown()
                                if (com.example.globalminusone.screenassistant.ScreenCaptureHub.shizukuAvailable() &&
                                    !com.example.globalminusone.screenassistant.ScreenCaptureHub.shizukuAuthorized()
                                ) {
                                    scope.launch {
                                        com.example.globalminusone.screenassistant.ScreenCaptureHub.requestShizukuAuthorization()
                                        breenoProbeTrigger++
                                    }
                                }
                            }
                            breenoProbeTrigger++
                        }
                    )
                }
            }

            settingsGroup(
                title = "原位翻译引擎与 API",
                summary = "当前引擎：${config.translateEngine.label} -> ${config.targetLanguage}",
                expanded = expandedGroup == "TRANSLATE",
                onToggle = { expandedGroup = if (expandedGroup == "TRANSLATE") "" else "TRANSLATE" }
            ) {
                item {
                    PreferenceItem(
                        title = "翻译引擎方案",
                        summary = "${config.translateEngine.label}\n${config.translateEngine.description}",
                        onClick = { showTranslateEngineDialog = true }
                    )
                }

                item {
                    PreferenceItem(
                        title = "目标语言",
                        summary = targetLanguageLabel(config.targetLanguage),
                        onClick = { showTargetLangDialog = true }
                    )
                }

                item {
                    PreferenceItem(
                        title = "传统免费翻译源",
                        summary = config.traditionalProvider.label,
                        onClick = { showTraditionalProviderDialog = true }
                    )
                }

                if (config.traditionalProvider == TraditionalTranslateProvider.CUSTOM_LIBRE) {
                    item {
                        PreferenceItem(
                            title = "自定义 LibreTranslate 地址",
                            summary = config.traditionalCustomUrl.ifBlank { "例如 https://libretranslate.example.com/translate" },
                            onClick = { showCustomLibreDialog = true }
                        )
                    }
                }

                if (config.translateEngine == ScreenTranslateEngine.BILIBILI_INDEX) {
                    item {
                        PreferenceItem(
                            title = "B站免费 Index-Translate 状态",
                            summary = "免密直连：https://index-translate.bilibili.com/v1\n模型：Index-Translate-35B-A3B · 官方免费公共接口（高并发时段若限流可切传统免费引擎）",
                            onClick = { showTranslateEngineDialog = true }
                        )
                    }
                }

                item {
                    val keyMask = ScreenAssistantSecretStore.maskKey(ScreenAssistantSecretStore.getLlmApiKey(context))
                    PreferenceItem(
                        title = "大语言模型 API 翻译设置（DeepSeek / OpenAI 等）",
                        summary = "模型：${config.llmModel} · 接口：${config.llmBaseUrl}\nAPI 密钥：$keyMask · 注：DeepSeek 仅网页免费，API 需自备付费 Key",
                        onClick = { showLlmConfigDialog = true }
                    )
                }
            }

            settingsGroup(
                title = "离线词库附件管理 (${lexicons.size})",
                summary = "存放于应用私有目录，仅按需下载或导入，不占 APK 体积",
                expanded = expandedGroup == "ATTACHMENTS",
                onToggle = { expandedGroup = if (expandedGroup == "ATTACHMENTS") "" else "ATTACHMENTS" }
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { showDownloadLexiconDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("URL 下载词库")
                        }
                        OutlinedButton(
                            onClick = { importLexiconLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("导入本地词库")
                        }
                    }
                }

                if (lexicons.isEmpty()) {
                    item {
                        Text(
                            text = "尚未下载或导入离线词库（支持 .tsv / .txt / .json / .gz）。在线翻译过的词句也会自动增量缓存到本地供离线检索。",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                } else {
                    items(lexicons, key = { it.id }) { lex ->
                        val active = config.activeLexiconId == lex.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (active) "✓ ${lex.name}（当前启用）" else lex.name,
                                    fontSize = 14.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${lex.entryCount} 条词句 · ${Formatter.formatShortFileSize(context, lex.sizeBytes)}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!active) {
                                TextButton(onClick = {
                                    updateSaConfig { it.copy(activeLexiconId = lex.id, activeLexiconName = lex.name) }
                                }) {
                                    Text("启用")
                                }
                            }
                            TextButton(onClick = {
                                ScreenAssistantAttachmentManager.deleteLexicon(context, lex.id)
                                if (config.activeLexiconId == lex.id) {
                                    updateSaConfig { it.copy(activeLexiconId = "", activeLexiconName = "") }
                                }
                                refreshAttachments()
                            }) {
                                Text("删除")
                            }
                        }
                    }
                }
            }

            settingsGroup(
                title = "ColorOS 小布识屏联动",
                summary = if (breenoProbe?.installedPackages?.isNotEmpty() == true) {
                    "已检测到系统包：${breenoProbe!!.installedPackages.joinToString()}"
                } else {
                    "未检测到系统小布组件或尚在探测中"
                },
                expanded = expandedGroup == "BREENO",
                onToggle = { expandedGroup = if (expandedGroup == "BREENO") "" else "BREENO" }
            ) {
                item {
                    val snap = breenoProbe
                    val probeText = buildString {
                        append("无障碍识屏磁贴通道：")
                        append(if (com.example.globalminusone.service.GlobalAccessibilityService.instance != null) "已连接" else "未启用主服务无障碍")
                        append("\nShizuku 特权通道：")
                        append(
                            when {
                                snap == null -> "探测中"
                                snap.shizukuAuthorized -> "已连接并已授权（免 Root 可直达）"
                                snap.shizukuAvailable -> "服务运行中，开启下方开关并触发时将请求授权"
                                else -> "未检测到 Shizuku 运行"
                            }
                        )
                        if (snap != null) {
                            append("\n系统识屏包：${snap.installedPackages.joinToString().ifBlank { "未发现已知包" }}")
                            append("\nDirectService 全屏直达服务：${if (snap.hasDirectService) "已就绪" else "未发现"}")
                            append("\n特权可调用的自定义识屏磁贴：${snap.recognitionTile?.flattenToShortString() ?: "未发现"}")
                        }
                        append("\n已授权 Shizuku/Root 时优先直达系统全屏识屏服务，其次系统侧边栏或识屏磁贴；绝不静默替换成语音助手或内置识屏。")
                    }

                    PreferenceItem(
                        title = "系统小布组件实时探测状态",
                        summary = probeText,
                        onClick = { breenoProbeTrigger++ }
                    )
                }

                item {
                    PreferenceSwitchItem(
                        title = "允许小布特权通道（Shizuku / Root）",
                        summary = "无 Root 时优先复用或请求 Shizuku 权限调用系统全屏识屏服务或磁贴，已有 Root 会话亦可直接复用；始终与内置识屏保持独立",
                        checked = config.breenoUsePrivilegedShell,
                        onCheckedChange = { checked ->
                            updateSaConfig { it.copy(breenoUsePrivilegedShell = checked) }
                        }
                    )
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val outcome = BreenoTouchBridge.launch(
                                        context = context,
                                        allowPrivilegedShell = state.advancedConfig.rootModeEnabled || config.breenoUsePrivilegedShell
                                    )
                                    Toast.makeText(context, outcome.message, Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("唤起系统小布")
                        }
                        OutlinedButton(
                            onClick = {
                                OverlayService.start(context, OverlayService.ACTION_SCREEN_ASSISTANT)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("启动内置识屏")
                        }
                    }
                }
            }
        }
    }

    if (showOcrEngineDialog) {
        val options = ScreenOcrEngine.selectableEntries
        val currentIdx = options.indexOf(config.ocrEngine).let { if (it >= 0) it else options.indexOf(ScreenOcrEngine.PPOCR_WASM) }
        SingleChoiceDialog(
            title = "选择 OCR 识别引擎",
            options = options.map { "${it.label}\n${it.description}" },
            selectedIndex = currentIdx,
            onSelect = { idx ->
                updateSaConfig { it.copy(ocrEngine = options[idx]) }
                showOcrEngineDialog = false
            },
            onDismiss = { showOcrEngineDialog = false }
        )
    }

    if (showTranslateEngineDialog) {
        val options = ScreenTranslateEngine.entries
        SingleChoiceDialog(
            title = "选择翻译引擎",
            options = options.map { "${it.label}\n${it.description}" },
            selectedIndex = options.indexOf(config.translateEngine),
            onSelect = { idx ->
                updateSaConfig { it.copy(translateEngine = options[idx]) }
                showTranslateEngineDialog = false
            },
            onDismiss = { showTranslateEngineDialog = false }
        )
    }

    if (showTraditionalProviderDialog) {
        val options = TraditionalTranslateProvider.entries
        SingleChoiceDialog(
            title = "选择传统免费翻译源",
            options = options.map { it.label },
            selectedIndex = options.indexOf(config.traditionalProvider),
            onSelect = { idx ->
                updateSaConfig { it.copy(traditionalProvider = options[idx]) }
                showTraditionalProviderDialog = false
            },
            onDismiss = { showTraditionalProviderDialog = false }
        )
    }

    if (showTargetLangDialog) {
        val langs = listOf("zh-CN", "zh-TW", "en", "ja", "ko")
        SingleChoiceDialog(
            title = "选择目标翻译语言",
            options = langs.map { targetLanguageLabel(it) },
            selectedIndex = langs.indexOf(config.targetLanguage).coerceAtLeast(0),
            onSelect = { idx ->
                updateSaConfig { it.copy(targetLanguage = langs[idx]) }
                showTargetLangDialog = false
            },
            onDismiss = { showTargetLangDialog = false }
        )
    }

    if (showLlmConfigDialog) {
        EndpointAndSecretDialog(
            title = "大模型 API 翻译配置",
            initialUrl = config.llmBaseUrl,
            urlLabel = "OpenAI 兼容接口地址 (Base URL)",
            initialModel = config.llmModel,
            modelLabel = "模型名称 (如 deepseek-chat / gpt-4o-mini)",
            initialKey = ScreenAssistantSecretStore.getLlmApiKey(context),
            presets = listOf(
                Triple("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
                Triple("B站免Key", "https://index-translate.bilibili.com/v1", "Index-Translate-35B-A3B"),
                Triple("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini")
            ),
            helperNote = "提示：B站 Index-Translate 为免费公共接口（无需密钥，可留空或填 index）；DeepSeek 官网/App 免费，但开放平台 API 为按量付费，需自备 Key。",
            onConfirm = { url, model, key ->
                updateSaConfig {
                    it.copy(
                        llmBaseUrl = url.ifBlank { "https://api.openai.com/v1" },
                        llmModel = model.ifBlank { "gpt-4o-mini" }
                    )
                }
                ScreenAssistantSecretStore.setLlmApiKey(context, key)
                showLlmConfigDialog = false
                Toast.makeText(context, "已保存大模型翻译配置与本机密钥", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showLlmConfigDialog = false }
        )
    }

    if (showVisionOcrDialog) {
        EndpointAndSecretDialog(
            title = "视觉大模型 OCR 配置",
            initialUrl = config.visionLlmBaseUrl,
            urlLabel = "OpenAI 兼容多模态地址 (Base URL)",
            initialModel = config.visionLlmModel,
            modelLabel = "视觉模型名称 (如 gpt-4o-mini / qwen-vl-plus)",
            initialKey = ScreenAssistantSecretStore.getVisionLlmApiKey(context),
            onConfirm = { url, model, key ->
                updateSaConfig {
                    it.copy(
                        visionLlmBaseUrl = url.ifBlank { "https://api.openai.com/v1" },
                        visionLlmModel = model.ifBlank { "gpt-4o-mini" }
                    )
                }
                ScreenAssistantSecretStore.setVisionLlmApiKey(context, key)
                showVisionOcrDialog = false
                Toast.makeText(context, "已保存视觉 OCR 配置与本机密钥", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showVisionOcrDialog = false }
        )
    }

    if (showCustomOcrDialog) {
        EndpointAndSecretDialog(
            title = "自定义 HTTP OCR 配置",
            initialUrl = config.customOcrUrl,
            urlLabel = "HTTP OCR 接口 URL（POST JSON）",
            initialModel = null,
            modelLabel = "",
            initialKey = ScreenAssistantSecretStore.getCustomOcrApiKey(context),
            onConfirm = { url, _, key ->
                updateSaConfig { it.copy(customOcrUrl = url) }
                ScreenAssistantSecretStore.setCustomOcrApiKey(context, key)
                showCustomOcrDialog = false
                Toast.makeText(context, "已保存自定义 HTTP OCR 地址", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showCustomOcrDialog = false }
        )
    }

    if (showCustomLibreDialog) {
        EndpointAndSecretDialog(
            title = "自定义 LibreTranslate 地址",
            initialUrl = config.traditionalCustomUrl,
            urlLabel = "接口地址 (例如 https://xxx/translate)",
            initialModel = null,
            modelLabel = "",
            initialKey = null,
            onConfirm = { url, _, _ ->
                updateSaConfig { it.copy(traditionalCustomUrl = url) }
                showCustomLibreDialog = false
            },
            onDismiss = { showCustomLibreDialog = false }
        )
    }

    if (showDownloadLexiconDialog) {
        DownloadLexiconDialog(
            onDownload = { url, name, sha256, onDone ->
                scope.launch {
                    val res = ScreenAssistantAttachmentManager.downloadLexiconFromUrl(
                        context = context,
                        rawUrl = url,
                        displayName = name,
                        expectedSha256 = sha256
                    )
                    res.onSuccess { added ->
                        refreshAttachments()
                        updateSaConfig { it.copy(activeLexiconId = added.id, activeLexiconName = added.name) }
                        Toast.makeText(context, "词库下载成功：${added.entryCount} 条", Toast.LENGTH_SHORT).show()
                        showDownloadLexiconDialog = false
                    }.onFailure { err ->
                        Toast.makeText(context, "下载失败: ${err.message}", Toast.LENGTH_LONG).show()
                    }
                    onDone()
                }
            },
            onDismiss = { showDownloadLexiconDialog = false }
        )
    }

    if (showLsposedWarningDialog) {
        AlertDialog(
            onDismissRequest = { showLsposedWarningDialog = false },
            title = {
                com.example.globalminusone.ui.Android12WindowKeyGuard()
                Text(text = "实验性功能与系统框架风险提示", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "• 未经真机充分测试：此方案为实验性实现，不同机型/ROM（如 Android 16 / ColorOS 16）可能拦截系统 ViewDebug 抓取或返回空白图。",
                        fontSize = 13.sp
                    )
                    Text(
                        text = "• 使用要求：需在 LSPosed 中启用本模块并「仅勾选系统框架 (android)」，然后重启手机。本模块绝不注入任何第三方应用。",
                        fontSize = 13.sp
                    )
                    Text(
                        text = "• 风险与已知限制：会在 system_server 注册受 UID 校验的私有 Binder 事务；抓图时可能造成目标应用短暂掉帧；分辨率以屏幕控件尺寸为上限，且不支持 WebView / Flutter / Compose 自绘图片。",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        updateSaConfig { it.copy(lsposedViewCaptureEnabled = true) }
                        breenoProbeTrigger++
                        showLsposedWarningDialog = false
                    }
                ) {
                    Text("确认开启")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLsposedWarningDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

private fun targetLanguageLabel(code: String): String = when (code) {
    "zh-CN" -> "简体中文 (zh-CN)"
    "zh-TW" -> "繁体中文 (zh-TW)"
    "en" -> "English (en)"
    "ja" -> "日本語 (ja)"
    "ko" -> "한국어 (ko)"
    else -> code
}

@Composable
private fun EndpointAndSecretDialog(
    title: String,
    initialUrl: String,
    urlLabel: String,
    initialModel: String?,
    modelLabel: String,
    initialKey: String?,
    presets: List<Triple<String, String, String>> = emptyList(),
    helperNote: String? = null,
    onConfirm: (url: String, model: String, key: String) -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf(initialUrl) }
    var model by remember { mutableStateOf(initialModel.orEmpty()) }
    var apiKey by remember { mutableStateOf(initialKey.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            com.example.globalminusone.ui.Android12WindowKeyGuard()
            Text(text = title, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (presets.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presets.forEach { (label, presetUrl, presetModel) ->
                            OutlinedButton(
                                onClick = {
                                    url = presetUrl
                                    if (initialModel != null) model = presetModel
                                    if (initialKey != null &&
                                        presetUrl.contains("index-translate.bilibili.com", ignoreCase = true) &&
                                        apiKey.isBlank()
                                    ) {
                                        apiKey = "index"
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(label, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(urlLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (initialModel != null) {
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text(modelLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (initialKey != null) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key（仅存本机，不随配置分享）") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (!helperNote.isNullOrBlank()) {
                    Text(
                        text = helperNote,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(url.trim(), model.trim(), apiKey.trim()) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun DownloadLexiconDialog(
    onDownload: (url: String, name: String, sha256: String, onDone: () -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("自定义离线词库") }
    var sha256 by remember { mutableStateOf("") }
    var downloading by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        title = {
            com.example.globalminusone.ui.Android12WindowKeyGuard()
            Text(text = "下载离线词库附件", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "支持 TSV / 纯文本 (source<TAB>target 或 ||| 分隔) / JSON / .gz 压缩格式。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("词库名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("下载地址 (https://...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sha256,
                    onValueChange = { sha256 = it },
                    label = { Text("可选 SHA-256 校验值") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
        },
        confirmButton = {
            Button(
                enabled = !downloading && url.trim().isNotEmpty(),
                onClick = {
                    downloading = true
                    onDownload(url.trim(), name.trim().ifBlank { "离线词库" }, sha256.trim()) {
                        downloading = false
                    }
                }
            ) {
                Text(if (downloading) "下载中…" else "开始下载")
            }
        },
        dismissButton = {
            TextButton(enabled = !downloading, onClick = onDismiss) { Text("取消") }
        }
    )
}
