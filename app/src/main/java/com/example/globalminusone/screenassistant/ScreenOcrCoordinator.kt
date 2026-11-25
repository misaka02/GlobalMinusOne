package com.example.globalminusone.screenassistant

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.globalminusone.data.ScreenAssistantConfig
import com.example.globalminusone.data.ScreenOcrEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.math.min

data class OcrRecognitionResult(
    val blocks: List<ScreenTextBlock>,
    val engineUsed: String,
    val note: String = ""
)

/**
 * Unified Shizuku + Root OCR & translation coordinator:
 * - Native ARM64 ORT (Root / Shizuku) skipping direct-rendered hierarchy text;
 * - Vision LLM / Custom HTTP OCR;
 * - Hierarchy text fusion + `ScreenTextInpainter` foreground/background style enrichment.
 */
internal object ScreenOcrCoordinator {
    private const val TAG = "ScreenOcrCoord"
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun buildSkipNativeBlocks(snapshot: AccessibilityScreenSnapshot): List<ScreenTextBlock> =
        snapshot.textNodes.filter { block ->
            block.isDirectRenderedText &&
                block.text.isNotBlank() &&
                block.widthPx >= 12 &&
                block.heightPx >= 12 &&
                snapshot.mediaNodes.none { m ->
                    val il = max(block.leftPx, m.leftPx)
                    val it = max(block.topPx, m.topPx)
                    val ir = min(block.rightPx, m.rightPx)
                    val ib = min(block.bottomPx, m.bottomPx)
                    ir > il &&
                        ib > it &&
                        ((ir - il).toLong() * (ib - it).toLong() * 2L >= block.widthPx.toLong() * block.heightPx.toLong())
                }
        }.take(96)

    fun mergeHierarchyAndVisualOcrBlocks(
        hierarchy: List<ScreenTextBlock>,
        visual: List<ScreenTextBlock>
    ): List<ScreenTextBlock> {
        if (hierarchy.isEmpty()) return ScreenTextSelectionPolicy.inReadingOrder(visual)
        if (visual.isEmpty()) return ScreenTextSelectionPolicy.inReadingOrder(hierarchy)
        val merged = ArrayList<ScreenTextBlock>(hierarchy.size + visual.size)
        merged.addAll(visual)
        for (h in hierarchy) {
            val hArea = (h.widthPx.toLong() * h.heightPx.toLong()).coerceAtLeast(1L)
            val dup = visual.any { v ->
                val il = max(h.leftPx, v.leftPx)
                val it = max(h.topPx, v.topPx)
                val ir = min(h.rightPx, v.rightPx)
                val ib = min(h.bottomPx, v.bottomPx)
                ir > il && ib > it && ((ir - il).toLong() * (ib - it).toLong() * 100L >= hArea * 45L)
            }
            if (!dup) merged += h
        }
        return ScreenTextSelectionPolicy.inReadingOrder(merged)
    }

    suspend fun recognizeTextBlocks(
        context: Context,
        bitmap: Bitmap,
        snapshot: AccessibilityScreenSnapshot,
        config: ScreenAssistantConfig,
        allowRootNativeOcr: Boolean = false,
        loadFallbackSnapshot: (suspend () -> AccessibilityScreenSnapshot)? = null
    ): OcrRecognitionResult {
        val rawResult = try {
            when {
                config.ocrEngine.isInstallFreeEngine() -> {
                    runPrivilegedNativeOcr(context, bitmap, snapshot, config, allowRootNativeOcr, loadFallbackSnapshot)
                }
                config.ocrEngine == ScreenOcrEngine.ACCESSIBILITY_TREE -> {
                    if (snapshot.textNodes.isNotEmpty()) {
                        OcrRecognitionResult(snapshot.textNodes, "原生界面树")
                    } else {
                        OcrRecognitionResult(emptyList(), "原生界面树", "未读取到原生界面文字，可启用 Root/Shizuku 原生 OCR")
                    }
                }
                config.ocrEngine == ScreenOcrEngine.VISION_LLM -> runVisionLlmOcr(context, bitmap, config)
                config.ocrEngine == ScreenOcrEngine.CUSTOM_HTTP_OCR -> runCustomHttpOcr(context, bitmap, config)
                else -> runAutoOcr(context, bitmap, snapshot, config, allowRootNativeOcr, loadFallbackSnapshot)
            }
        } catch (c: CancellationException) {
            throw c
        } catch (failure: Throwable) {
            Log.w(TAG, "OCR engine unavailable", failure)
            val reason = failure.message?.trim()?.take(120)?.ifBlank { null }
            OcrRecognitionResult(
                snapshot.textNodes,
                "原生节点树",
                reason ?: if (snapshot.textNodes.isEmpty()) "识别引擎暂不可用，请在设置中检查" else "⚠ 识别异常，当前仅屏幕原生文本"
            )
        }
        if (rawResult.blocks.isEmpty()) return rawResult
        val enriched = withContext(Dispatchers.Default) {
            ScreenTextInpainter.enrichBlocksFromBitmap(bitmap, rawResult.blocks)
        }
        return rawResult.copy(blocks = enriched)
    }

    private suspend fun resolveReadyPackId(context: Context, config: ScreenAssistantConfig): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val store = OcrModelStore(context)
                listOf(config.wasmOcrPackId, config.ppOcrLangPack)
                    .firstOrNull { it.isNotBlank() && store.isModelReady(it) }
                    ?: store.firstReadyModelId()
            }.getOrNull()
        }

    private suspend fun runPrivilegedNativeOcr(
        context: Context,
        bitmap: Bitmap,
        snapshot: AccessibilityScreenSnapshot,
        config: ScreenAssistantConfig,
        allowRootNativeOcr: Boolean,
        loadFallbackSnapshot: (suspend () -> AccessibilityScreenSnapshot)?
    ): OcrRecognitionResult {
        val packId = resolveReadyPackId(context, config)
            ?: return OcrRecognitionResult(emptyList(), "原生加速 OCR", "尚未下载 PP-OCR 模型，请在设置中下载或导入")
        if (!withContext(Dispatchers.IO) { NativeOcrRunner.isAvailable(context, allowRootNativeOcr) }) {
            return OcrRecognitionResult(emptyList(), "原生加速 OCR", "未检测到已授权的 Root / Shizuku 或 ARM64 原生引擎")
        }
        val skipNative = buildSkipNativeBlocks(snapshot)
        val nativeRes = NativeOcrRunner.recognize(context, bitmap, packId, skipNative, allowRootNativeOcr)
        if (nativeRes != null) {
            val hierarchy = snapshot.textNodes.ifEmpty { loadFallbackSnapshot?.invoke()?.textNodes.orEmpty() }
            return if (hierarchy.isNotEmpty()) {
                val fused = withContext(Dispatchers.Default) {
                    mergeHierarchyAndVisualOcrBlocks(hierarchy, nativeRes.blocks)
                }
                OcrRecognitionResult(fused, "${nativeRes.engineUsed} + 界面节点")
            } else {
                OcrRecognitionResult(nativeRes.blocks, nativeRes.engineUsed)
            }
        }
        val diag = NativeOcrRunner.lastDiagnostic.ifBlank { "原生 OCR 未返回结果" }
        return OcrRecognitionResult(emptyList(), "原生加速 OCR", diag)
    }

    private suspend fun runAutoOcr(
        context: Context,
        bitmap: Bitmap,
        snapshot: AccessibilityScreenSnapshot,
        config: ScreenAssistantConfig,
        allowRootNativeOcr: Boolean,
        loadFallbackSnapshot: (suspend () -> AccessibilityScreenSnapshot)?
    ): OcrRecognitionResult {
        val diagnostics = mutableListOf<String>()
        val nativeAttempt = runPrivilegedNativeOcr(context, bitmap, snapshot, config, allowRootNativeOcr, loadFallbackSnapshot)
        if (nativeAttempt.blocks.isNotEmpty() || nativeAttempt.note.isBlank()) return nativeAttempt
        diagnostics += nativeAttempt.note

        if (ScreenAssistantSecretStore.getVisionLlmApiKey(context).isNotBlank()) {
            val vRes = runVisionLlmOcr(context, bitmap, config)
            if (vRes.blocks.isNotEmpty()) {
                val hierarchy = snapshot.textNodes.ifEmpty { loadFallbackSnapshot?.invoke()?.textNodes.orEmpty() }
                return if (hierarchy.isNotEmpty()) {
                    vRes.copy(blocks = withContext(Dispatchers.Default) { mergeHierarchyAndVisualOcrBlocks(hierarchy, vRes.blocks) })
                } else {
                    vRes
                }
            }
            if (vRes.note.isNotBlank()) diagnostics += "视觉模型: ${vRes.note}"
        }
        if (config.customOcrUrl.isNotBlank()) {
            val hRes = runCustomHttpOcr(context, bitmap, config)
            if (hRes.blocks.isNotEmpty()) {
                val hierarchy = snapshot.textNodes.ifEmpty { loadFallbackSnapshot?.invoke()?.textNodes.orEmpty() }
                return if (hierarchy.isNotEmpty()) {
                    hRes.copy(blocks = withContext(Dispatchers.Default) { mergeHierarchyAndVisualOcrBlocks(hierarchy, hRes.blocks) })
                } else {
                    hRes
                }
            }
            if (hRes.note.isNotBlank()) diagnostics += "自定义OCR: ${hRes.note}"
        }
        val fallback = snapshot.textNodes.ifEmpty { loadFallbackSnapshot?.invoke()?.textNodes.orEmpty() }
        if (fallback.isNotEmpty()) {
            return OcrRecognitionResult(
                fallback,
                "无障碍节点树",
                "⚠ ${diagnostics.firstOrNull().orEmpty().take(120)}，当前仅屏幕原生文本"
            )
        }
        return nativeAttempt.copy(note = diagnostics.joinToString("；"))
    }

    private suspend fun runVisionLlmOcr(
        context: Context,
        bitmap: Bitmap,
        config: ScreenAssistantConfig
    ): OcrRecognitionResult = withContext(Dispatchers.IO) {
        val apiKey = ScreenAssistantSecretStore.getVisionLlmApiKey(context)
        if (apiKey.isBlank()) return@withContext OcrRecognitionResult(emptyList(), "视觉大模型 OCR", "尚未配置视觉大模型 API Key")
        val baseUrl = config.visionLlmBaseUrl.trim().trimEnd('/').ifBlank { "https://api.openai.com/v1" }
        val endpoint = if (baseUrl.endsWith("/chat/completions")) baseUrl else "$baseUrl/chat/completions"
        val b64 = encodeBitmapJpegBase64(bitmap, maxDim = 1440, quality = 85)
        val prompt = """
            Detect every visible readable text line/block in this image (${bitmap.width}x${bitmap.height}).
            Return STRICT JSON array ONLY with NO markdown:
            [{"text":"exact text","box_2d":[ymin,xmin,ymax,xmax]}]
            where box_2d uses normalized integers in 0..1000 relative to image height/width.
        """.trimIndent()
        val payload = buildJsonObject {
            put("model", config.visionLlmModel.ifBlank { "gpt-4o-mini" })
            put("temperature", 0.1)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", prompt)
                        })
                        add(buildJsonObject {
                            put("type", "image_url")
                            put("image_url", buildJsonObject { put("url", "data:image/jpeg;base64,$b64") })
                        })
                    })
                })
            })
        }
        postJsonWithAuth(endpoint, apiKey, payload.toString(), timeoutMs = 28_000).fold(
            onSuccess = { body ->
                val blocks = ScreenAssistantPolicy.parseVisionOcrJson(extractOpenAiContent(body), bitmap.width, bitmap.height)
                OcrRecognitionResult(blocks, "视觉大模型 (${config.visionLlmModel})", if (blocks.isEmpty()) "模型未返回有效文本坐标" else "")
            },
            onFailure = { err ->
                OcrRecognitionResult(emptyList(), "视觉大模型 OCR", "请求失败: ${err.localizedMessage ?: err.javaClass.simpleName}")
            }
        )
    }

    private suspend fun runCustomHttpOcr(
        context: Context,
        bitmap: Bitmap,
        config: ScreenAssistantConfig
    ): OcrRecognitionResult = withContext(Dispatchers.IO) {
        val endpoint = config.customOcrUrl.trim()
        if (endpoint.isBlank()) return@withContext OcrRecognitionResult(emptyList(), "自定义 HTTP OCR", "尚未填写自定义 OCR 服务 URL")
        val b64 = encodeBitmapJpegBase64(bitmap, maxDim = 1600, quality = 90)
        val key = ScreenAssistantSecretStore.getCustomOcrApiKey(context)
        val payload = buildJsonObject {
            put("image", b64)
            put("base64", b64)
            put("format", "json")
        }
        postJsonWithAuth(endpoint, key, payload.toString(), timeoutMs = 18_000).fold(
            onSuccess = { body ->
                val blocks = ScreenAssistantPolicy.parseCustomHttpOcrJson(body, bitmap.width, bitmap.height)
                OcrRecognitionResult(blocks, "自定义 HTTP OCR", if (blocks.isEmpty()) "接口未返回可识别文本框" else "")
            },
            onFailure = { err ->
                OcrRecognitionResult(emptyList(), "自定义 HTTP OCR", "接口请求失败: ${err.localizedMessage ?: err.javaClass.simpleName}")
            }
        )
    }

    private fun encodeBitmapJpegBase64(bitmap: Bitmap, maxDim: Int, quality: Int): String {
        val longest = max(bitmap.width, bitmap.height)
        val scaled = if (longest > maxDim) {
            val ratio = maxDim.toFloat() / longest.toFloat()
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt().coerceAtLeast(1), (bitmap.height * ratio).toInt().coerceAtLeast(1), true)
        } else {
            bitmap
        }
        try {
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } finally {
            if (scaled !== bitmap && !scaled.isRecycled) scaled.recycle()
        }
    }

    fun postJsonWithAuth(endpoint: String, bearerKey: String, jsonBody: String, timeoutMs: Int): Result<String> {
        var conn: HttpURLConnection? = null
        return try {
            val parsedUrl = URL(endpoint.trim())
            require(
                (parsedUrl.protocol.equals("https", true) || parsedUrl.protocol.equals("http", true)) &&
                    !parsedUrl.host.isNullOrBlank()
            ) { "接口地址必须为有效的 http/https URL" }
            conn = (parsedUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10_000
                readTimeout = timeoutMs
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                if (bearerKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $bearerKey")
            }
            conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                val maxBytes = 2 * 1024 * 1024
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    require(out.size() + n <= maxBytes) { "响应体积超过 2 MiB 安全限制" }
                    out.write(buf, 0, n)
                }
                out.toString("UTF-8")
            }.orEmpty()
            if (code !in 200..299) Result.failure(IllegalStateException("HTTP $code: ${text.take(240)}"))
            else Result.success(text)
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            conn?.disconnect()
        }
    }

    fun extractOpenAiContent(rawBody: String): String {
        val root = json.parseToJsonElement(rawBody).jsonObject
        val first = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: return ""
        val message = first["message"]?.jsonObject ?: return ""
        return when (val contentEl = message["content"]) {
            is JsonPrimitive -> contentEl.contentOrNull.orEmpty()
            is JsonArray -> contentEl.mapNotNull { (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull }.joinToString("\n")
            else -> ""
        }
    }
}
