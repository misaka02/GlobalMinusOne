package com.example.globalminusone.screenassistant

import android.content.Context
import android.util.Log
import com.example.globalminusone.data.ScreenAssistantConfig
import com.example.globalminusone.data.ScreenTranslateEngine
import com.example.globalminusone.data.TraditionalTranslateProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException

/**
 * Multi-engine screen text translation (Offline Lexicon, Bilibili Index-Translate,
 * OpenAI-compatible LLMs, and Edge/Google/MyMemory/Libre web translators).
 */
internal object ScreenTranslateEngineImpl {
    private const val TAG = "ScreenTranslateEngine"
    private const val EDGE_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Edg/128.0.0.0"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Volatile
    private var cachedEdgeToken: String = ""

    @Volatile
    private var cachedEdgeTokenAtMs: Long = 0L

    @Volatile
    private var googleUnreachableUntilMs: Long = 0L

    suspend fun translateBlocks(
        context: Context,
        blocks: List<ScreenTextBlock>,
        config: ScreenAssistantConfig
    ): Result<List<ScreenTextBlock>> = withContext(Dispatchers.IO) {
        if (blocks.isEmpty()) return@withContext Result.success(emptyList())
        val targetLang = config.targetLanguage.ifBlank { "zh-CN" }
        val targetIndices = blocks.indices.filter { ScreenAssistantPolicy.needsTranslation(blocks[it].text, targetLang) }
        if (targetIndices.isEmpty()) return@withContext Result.success(blocks.map { it.copy(translatedText = it.text) })

        val subset = targetIndices.map { blocks[it] }
        val partial = when (config.translateEngine) {
            ScreenTranslateEngine.OFFLINE_LEXICON -> {
                val map = ScreenAssistantAttachmentManager.loadActiveLexiconMap(context, config.activeLexiconId)
                if (map.isEmpty()) {
                    Result.failure(IllegalStateException("尚未下载或导入离线词库，请到“全局识屏与翻译”设置中下载或导入词典包"))
                } else {
                    val out = ScreenAssistantPolicy.translateWithLexicon(subset.map { it.text }, map)
                    Result.success(subset.mapIndexed { i, b -> b.copy(translatedText = out.getOrElse(i) { b.text }) })
                }
            }
            ScreenTranslateEngine.BILIBILI_INDEX -> translateViaOpenAiChatEndpoint(
                subset, "${ScreenAssistantPolicy.BILIBILI_INDEX_BASE_URL}/chat/completions",
                ScreenAssistantPolicy.BILIBILI_INDEX_MODEL, "index", targetLang,
                temperature = 0.0, disableThinking = true, timeoutMs = 30_000,
                rateLimitHint = "B站公益翻译并发受限，请稍后重试或切换传统免费引擎"
            )
            ScreenTranslateEngine.LLM_API -> translateViaLlm(context, subset, config, targetLang)
            ScreenTranslateEngine.TRADITIONAL_FREE -> translateViaTraditional(subset, config, targetLang)
        }
        partial.mapCatching { translatedSubset ->
            val byIdx = HashMap<Int, String>(targetIndices.size)
            targetIndices.forEachIndexed { pos, origIdx ->
                byIdx[origIdx] = translatedSubset.getOrNull(pos)?.translatedText ?: blocks[origIdx].text
            }
            val merged = blocks.mapIndexed { idx, orig -> orig.copy(translatedText = byIdx[idx] ?: orig.text) }
            check(!ScreenAssistantPolicy.hasOnlyUntranslatedOutput(merged, targetLang)) {
                if (config.translateEngine == ScreenTranslateEngine.OFFLINE_LEXICON) "离线词库未匹配到译文" else "当前翻译引擎未返回有效译文"
            }
            merged
        }
    }

    private suspend fun translateViaLlm(
        context: Context,
        blocks: List<ScreenTextBlock>,
        config: ScreenAssistantConfig,
        targetLang: String
    ): Result<List<ScreenTextBlock>> {
        val baseUrl = config.llmBaseUrl.trim().trimEnd('/').ifBlank { "https://api.openai.com/v1" }
        val isBilibili = ScreenAssistantPolicy.isBilibiliIndexEndpoint(baseUrl)
        val rawKey = ScreenAssistantSecretStore.getLlmApiKey(context)
        val apiKey = if (isBilibili && rawKey.isBlank()) "index" else rawKey
        if (apiKey.isBlank()) return Result.failure(IllegalStateException("尚未配置翻译大模型 API Key"))
        val endpoint = if (baseUrl.endsWith("/chat/completions")) baseUrl else "$baseUrl/chat/completions"
        val model = config.llmModel.ifBlank { if (isBilibili) ScreenAssistantPolicy.BILIBILI_INDEX_MODEL else "gpt-4o-mini" }
        return translateViaOpenAiChatEndpoint(
            blocks, endpoint, model, apiKey, targetLang,
            temperature = if (isBilibili) 0.0 else 0.2, disableThinking = isBilibili,
            timeoutMs = if (isBilibili) 30_000 else 25_000,
            rateLimitHint = if (isBilibili) "B站公益翻译并发受限，请稍后重试或切换引擎" else "大模型 API 请求受限或额度不足，请稍后重试"
        )
    }

    private suspend fun translateViaOpenAiChatEndpoint(
        blocks: List<ScreenTextBlock>,
        endpoint: String,
        model: String,
        apiKey: String,
        targetLanguage: String,
        temperature: Double,
        disableThinking: Boolean,
        timeoutMs: Int,
        rateLimitHint: String
    ): Result<List<ScreenTextBlock>> = withContext(Dispatchers.IO) {
        val langDesc = when {
            targetLanguage.equals("zh-TW", true) || targetLanguage.contains("Hant", true) -> "Traditional Chinese"
            targetLanguage.startsWith("zh", true) -> "Simplified Chinese"
            else -> targetLanguage
        }
        val sysPrompt = "Translate each numbered text value naturally into $langDesc keeping concise UI tone. " +
            "Return ONLY a JSON object mapping string index keys to translated strings, e.g. {\"0\":\"...\",\"1\":\"...\"}."

        val translatedBlocks = ArrayList<ScreenTextBlock>(blocks.size)
        for (batch in blocks.chunked(24)) {
            currentCoroutineContext().ensureActive()
            val itemsPayload = buildJsonObject { batch.forEachIndexed { idx, b -> put(idx.toString(), b.text) } }
            val req = buildJsonObject {
                put("model", model); put("temperature", temperature)
                if (disableThinking) put("chat_template_kwargs", buildJsonObject { put("enable_thinking", false) })
                put("messages", buildJsonArray {
                    add(buildJsonObject { put("role", "system"); put("content", sysPrompt) })
                    add(buildJsonObject { put("role", "user"); put("content", itemsPayload.toString()) })
                })
            }
            val batchResult = ScreenOcrCoordinator.postJsonWithAuth(endpoint, apiKey, req.toString(), timeoutMs).mapCatching { body ->
                val stripped = ScreenAssistantPolicy.stripThinkingTags(ScreenOcrCoordinator.extractOpenAiContent(body))
                val parsed = ScreenAssistantPolicy.parseBatchTranslationJson(stripped, batch.size)
                if (parsed.isEmpty()) {
                    if (batch.size == 1 && stripped.isNotBlank() && !stripped.startsWith("{") && !stripped.startsWith("[")) {
                        return@mapCatching listOf(batch[0].copy(translatedText = stripped.removeSurrounding("```").trim()))
                    }
                    error("大模型返回格式异常，未能解析译文映射")
                }
                batch.mapIndexed { idx, b -> b.copy(translatedText = parsed[idx] ?: b.text) }
            }.recoverCatching { err ->
                val msg = err.message.orEmpty()
                if (msg.startsWith("HTTP 429") || msg.contains("rate limit", true)) throw IllegalStateException("$rateLimitHint ($msg)", err)
                throw err
            }
            if (batchResult.isFailure) return@withContext Result.failure(batchResult.exceptionOrNull() ?: IllegalStateException("大模型翻译失败"))
            translatedBlocks.addAll(batchResult.getOrThrow())
        }
        Result.success(translatedBlocks)
    }

    private suspend fun translateViaTraditional(
        blocks: List<ScreenTextBlock>,
        config: ScreenAssistantConfig,
        targetLang: String
    ): Result<List<ScreenTextBlock>> = withContext(Dispatchers.IO) {
        val texts = blocks.map { it.text }
        val order = if (config.traditionalProvider == TraditionalTranslateProvider.AUTO) {
            listOf(TraditionalTranslateProvider.BING_WEB, TraditionalTranslateProvider.GOOGLE_WEB, TraditionalTranslateProvider.MY_MEMORY)
        } else listOf(config.traditionalProvider)

        var lastError: Throwable? = null
        var bestFallback: List<String>? = null
        for (provider in order) {
            currentCoroutineContext().ensureActive()
            if (provider == TraditionalTranslateProvider.GOOGLE_WEB && System.currentTimeMillis() < googleUnreachableUntilMs) continue
            val attempt = runCatching {
                runProvider(provider, texts, targetLang, config.traditionalCustomUrl.trim())
            }.onFailure { if (it is CancellationException) throw it }

            if (attempt.isSuccess) {
                val outList = attempt.getOrThrow().toMutableList()
                fallbackUntranslated(provider, texts, outList, targetLang)
                val anyChanged = texts.indices.any { !texts[it].trim().equals(outList.getOrElse(it) { texts[it] }.trim(), true) }
                if (anyChanged || texts.none { ScreenAssistantPolicy.needsTranslation(it, targetLang) } || config.traditionalProvider != TraditionalTranslateProvider.AUTO) {
                    return@withContext Result.success(blocks.mapIndexed { i, b -> b.copy(translatedText = outList.getOrElse(i) { b.text }) })
                }
                bestFallback = outList
                lastError = IllegalStateException("$provider 返回原文未译，尝试下一引擎")
            } else {
                lastError = attempt.exceptionOrNull()
                Log.w(TAG, "Traditional provider $provider failed", lastError)
            }
        }
        if (bestFallback != null) {
            return@withContext Result.success(blocks.mapIndexed { i, b -> b.copy(translatedText = bestFallback.getOrElse(i) { b.text }) })
        }
        Result.failure(lastError ?: IllegalStateException("传统翻译接口暂不可用，请检查网络或切换大模型 API"))
    }

    private suspend fun runProvider(
        provider: TraditionalTranslateProvider,
        texts: List<String>,
        targetLang: String,
        customUrl: String
    ): List<String> = when (provider) {
        TraditionalTranslateProvider.BING_WEB -> batchTranslateViaEdgeArray(texts, resolveMicrosoftTargetLang(targetLang), targetLang)
        TraditionalTranslateProvider.GOOGLE_WEB -> batchTranslateViaDelimited(texts, targetLang) { q, sl -> callGoogleWebTranslate(q, targetLang, sl) }
        TraditionalTranslateProvider.MY_MEMORY -> batchTranslateViaDelimited(texts, targetLang, 8, 420) { q, _ -> callMyMemoryTranslate(q, targetLang) }
        TraditionalTranslateProvider.CUSTOM_LIBRE -> {
            require(customUrl.startsWith("https://", true) || customUrl.startsWith("http://", true)) {
                "自定义 LibreTranslate 地址必须以 http:// 或 https:// 开头"
            }
            batchTranslateViaDelimited(texts, targetLang, 8, 1200) { q, _ -> callLibreTranslate(customUrl, q, targetLang) }
        }
        TraditionalTranslateProvider.AUTO -> texts
    }

    private suspend fun fallbackUntranslated(
        primary: TraditionalTranslateProvider,
        texts: List<String>,
        outList: MutableList<String>,
        targetLang: String
    ) {
        val missingIdx = texts.indices.filter { ScreenAssistantPolicy.isLikelyForeignUntranslated(texts[it], outList.getOrElse(it) { texts[it] }, targetLang) }
        if (missingIdx.isEmpty()) return
        val missing = missingIdx.map { texts[it] }
        val googleOk = System.currentTimeMillis() >= googleUnreachableUntilMs
        val fallbackProvider = when {
            primary == TraditionalTranslateProvider.BING_WEB && googleOk -> TraditionalTranslateProvider.GOOGLE_WEB
            primary == TraditionalTranslateProvider.BING_WEB -> TraditionalTranslateProvider.MY_MEMORY
            primary == TraditionalTranslateProvider.GOOGLE_WEB -> TraditionalTranslateProvider.MY_MEMORY
            else -> TraditionalTranslateProvider.BING_WEB
        }
        val repaired = runCatching { runProvider(fallbackProvider, missing, targetLang, "") }
            .onFailure { if (it is CancellationException) throw it }.getOrNull() ?: return
        missingIdx.forEachIndexed { pos, origIdx ->
            val cand = repaired.getOrNull(pos)
            if (!cand.isNullOrBlank() && !cand.trim().equals(texts[origIdx].trim(), true)) outList[origIdx] = cand
        }
    }

    private fun detectBatchExplicitSourceLang(batch: List<String>, targetLang: String): String? {
        if (batch.isEmpty()) return null
        if (targetLang.startsWith("zh", true) && batch.any { t -> t.any { it.code in 0x4E00..0x9FFF } }) return null
        val langs = batch.map { ScreenAssistantPolicy.inferMyMemorySourceLang(it, targetLang) }.distinct()
        return langs.singleOrNull()?.takeUnless { it.equals(targetLang.substringBefore('-'), true) }
    }

    private suspend fun batchTranslateViaEdgeArray(texts: List<String>, bingTo: String, targetLang: String): List<String> {
        val out = ArrayList<String>(texts.size)
        for (batch in chunkByLimits(texts, maxCount = 24, maxChars = 2600, overheadPerItem = 0)) {
            currentCoroutineContext().ensureActive()
            val explicitFrom = detectBatchExplicitSourceLang(batch, targetLang).orEmpty()
            val resolved = callMicrosoftEdgeBatch(batch, bingTo, explicitFrom).toMutableList()
            if (explicitFrom.isEmpty()) {
                val missedByLang = LinkedHashMap<String, MutableList<Int>>()
                for (i in batch.indices) {
                    if (ScreenAssistantPolicy.isLikelyForeignUntranslated(batch[i], resolved[i], targetLang)) {
                        missedByLang.getOrPut(ScreenAssistantPolicy.inferMyMemorySourceLang(batch[i], targetLang)) { ArrayList() }.add(i)
                    }
                }
                for ((srcLang, idxs) in missedByLang) {
                    currentCoroutineContext().ensureActive()
                    val slice = idxs.take(24)
                    val retry = runCatching { callMicrosoftEdgeBatch(slice.map { batch[it] }, bingTo, srcLang) }
                        .onFailure { if (it is CancellationException) throw it }.getOrNull()
                    if (retry != null) slice.forEachIndexed { p, orig -> retry.getOrNull(p)?.takeIf { it.isNotBlank() }?.let { resolved[orig] = it } }
                }
            }
            out.addAll(resolved)
        }
        return out
    }

    internal suspend fun batchTranslateViaDelimited(
        texts: List<String>,
        targetLang: String,
        maxChunkSize: Int = 14,
        maxChunkChars: Int = 1500,
        singleCall: (text: String, sourceLang: String) -> String
    ): List<String> {
        val out = ArrayList<String>(texts.size)
        for (chunk in chunkByLimits(texts, maxChunkSize, maxChunkChars, overheadPerItem = 12)) {
            currentCoroutineContext().ensureActive()
            val explicitFrom = detectBatchExplicitSourceLang(chunk, targetLang) ?: "auto"
            val joined = chunk.mapIndexed { i, txt -> "||G$i|| ${txt.replace("\n", " ")}" }.joinToString("\n")
            val parsed = ScreenAssistantPolicy.splitDelimitedBatch(singleCall(joined, explicitFrom), chunk)
            if (parsed != null) {
                val fixed = parsed.toMutableList()
                for (idx in chunk.indices) {
                    val orig = chunk[idx]
                    if (ScreenAssistantPolicy.isLikelyForeignUntranslated(orig, fixed[idx], targetLang)) {
                        currentCoroutineContext().ensureActive()
                        val src = ScreenAssistantPolicy.inferMyMemorySourceLang(orig, targetLang)
                        runCatching { singleCall(orig, src) }.onFailure { if (it is CancellationException) throw it }.getOrNull()?.takeIf { it.isNotBlank() }?.let {
                            fixed[idx] = it
                        }
                    }
                }
                out.addAll(fixed)
            } else {
                chunk.forEach { single ->
                    currentCoroutineContext().ensureActive()
                    val src = ScreenAssistantPolicy.inferMyMemorySourceLang(single, targetLang)
                    out.add(runCatching { singleCall(single, src) }.onFailure { if (it is CancellationException) throw it }.getOrDefault(single))
                }
            }
        }
        return out
    }

    private fun chunkByLimits(texts: List<String>, maxCount: Int, maxChars: Int, overheadPerItem: Int): List<List<String>> {
        val chunks = ArrayList<List<String>>()
        var cur = ArrayList<String>()
        var chars = 0
        for (t in texts) {
            if (cur.isNotEmpty() && (cur.size >= maxCount || chars + t.length > maxChars)) {
                chunks += cur; cur = ArrayList(); chars = 0
            }
            cur += t; chars += t.length + overheadPerItem
        }
        if (cur.isNotEmpty()) chunks += cur
        return chunks
    }

    private fun callGoogleWebTranslate(text: String, targetLang: String, sourceLang: String = "auto"): String {
        if (System.currentTimeMillis() < googleUnreachableUntilMs) error("Google translate temporarily unreachable")
        val sl = if (sourceLang.isBlank() || sourceLang == "auto") "auto" else sourceLang
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=" +
            "${enc(sl)}&tl=${enc(targetLang)}&dt=t&q=${enc(text)}"
        val (_, body) = try {
            httpRequest(url, "GET", mapOf("User-Agent" to "Mozilla/5.0"), connectMs = 2500, readMs = 4000)
        } catch (err: Exception) {
            if (err is SocketTimeoutException || err is ConnectException || err is UnknownHostException) {
                googleUnreachableUntilMs = System.currentTimeMillis() + 180_000L
            }
            throw err
        }
        val sentences = json.parseToJsonElement(body).jsonArray[0].jsonArray
        return buildString {
            for (el in sentences) el.jsonArray.getOrNull(0)?.jsonPrimitive?.contentOrNull?.let(::append)
        }.ifBlank { text }
    }

    private fun resolveMicrosoftTargetLang(targetLang: String): String = when {
        targetLang.equals("zh-TW", true) || targetLang.contains("Hant", true) -> "zh-Hant"
        targetLang.startsWith("zh", true) -> "zh-Hans"
        else -> targetLang
    }

    private fun fetchEdgeTranslatorJwt(): String {
        val now = System.currentTimeMillis()
        cachedEdgeToken.takeIf { it.isNotBlank() && now - cachedEdgeTokenAtMs < 8 * 60_000L }?.let { return it }
        val (_, body) = httpRequest("https://edge.microsoft.com/translate/auth", "GET", mapOf("User-Agent" to EDGE_UA), connectMs = 6000, readMs = 7000)
        val jwt = body.trim().also { require(it.isNotBlank()) { "Edge auth empty token" } }
        cachedEdgeToken = jwt; cachedEdgeTokenAtMs = now
        return jwt
    }

    private fun callMicrosoftEdgeBatch(texts: List<String>, bingTo: String, fromLang: String = ""): List<String> {
        if (texts.isEmpty()) return emptyList()
        var jwt = fetchEdgeTranslatorJwt()
        repeat(2) { attempt ->
            val fromParam = if (fromLang.isBlank() || fromLang == "auto") "" else enc(fromLang)
            val url = "https://api-edge.cognitive.microsofttranslator.com/translate?from=$fromParam&to=${enc(bingTo)}&api-version=3.0&includeSentenceLength=true"
            val bodyJson = buildJsonArray { texts.forEach { t -> add(buildJsonObject { put("Text", t) }) } }.toString()
            val (code, resp) = httpRequest(
                url, "POST",
                mapOf("Authorization" to "Bearer $jwt", "Content-Type" to "application/json; charset=UTF-8", "User-Agent" to EDGE_UA),
                bodyJson, connectMs = 7000, readMs = 10000, acceptNon2xx = true
            )
            if (code == 401 && attempt == 0) { cachedEdgeToken = ""; jwt = fetchEdgeTranslatorJwt(); return@repeat }
            require(code in 200..299) { "Edge HTTP $code" }
            val arr = json.parseToJsonElement(resp).jsonArray
            return texts.mapIndexed { idx, orig ->
                arr.getOrNull(idx)?.jsonObject?.get("translations")?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull?.ifBlank { orig } ?: orig
            }
        }
        error("Edge translate retries exhausted")
    }

    private fun callMyMemoryTranslate(text: String, targetLang: String): String {
        val src = ScreenAssistantPolicy.inferMyMemorySourceLang(text, targetLang)
        val tgt = if (targetLang.equals("zh", true) || targetLang.equals("zh-CN", true)) "zh-CN" else targetLang
        if (src.equals(tgt, true)) return text
        val (_, resp) = httpRequest("https://api.mymemory.translated.net/get?q=${enc(text)}&langpair=$src|$tgt", "GET", emptyMap(), connectMs = 8000, readMs = 10000)
        return json.parseToJsonElement(resp).jsonObject["responseData"]?.jsonObject?.get("translatedText")
            ?.jsonPrimitive?.contentOrNull?.ifBlank { text } ?: text
    }

    private fun callLibreTranslate(endpoint: String, text: String, targetLang: String): String {
        val payload = buildJsonObject {
            put("q", text); put("source", "auto"); put("target", if (targetLang.startsWith("zh")) "zh" else targetLang); put("format", "text")
        }
        val body = ScreenOcrCoordinator.postJsonWithAuth(endpoint, "", payload.toString(), timeoutMs = 10_000).getOrThrow()
        val obj = json.parseToJsonElement(body).jsonObject
        return obj["translatedText"]?.jsonPrimitive?.contentOrNull ?: obj["translation"]?.jsonPrimitive?.contentOrNull ?: text
    }

    private fun httpRequest(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String? = null,
        connectMs: Int,
        readMs: Int,
        acceptNon2xx: Boolean = false
    ): Pair<Int, String> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = connectMs; readTimeout = readMs
            if (body != null) doOutput = true
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        return try {
            if (body != null) conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (!acceptNon2xx) require(code in 200..299) { "HTTP $code ($url)" }
            val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            code to stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
