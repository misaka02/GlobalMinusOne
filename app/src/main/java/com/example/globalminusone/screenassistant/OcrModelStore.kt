package com.example.globalminusone.screenassistant

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipFile
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal const val MAX_OCR_METADATA_BYTES = 256 * 1024
private const val LOCAL_IMPORT_ORIGIN = "https://local-import.invalid/"

internal data class CatalogHttpResponse(val code: Int, val body: ByteArray)

internal class CatalogHttpException(val code: Int, message: String) : IOException(message)

internal fun interface CatalogHttp {
    fun get(url: String, accept: String?, maxBytes: Int): CatalogHttpResponse
}

internal object OcrCatalogNetwork : CatalogHttp {
    override fun get(url: String, accept: String?, maxBytes: Int): CatalogHttpResponse {
        var current = url
        repeat(5) {
            val uri = URI(current)
            require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank()) { "仅支持 HTTPS 资源请求" }
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 12_000
                readTimeout = 15_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "GlobalMinusOne-OCR/2.4")
                if (!accept.isNullOrBlank()) setRequestProperty("Accept", accept)
            }
            try {
                val code = conn.responseCode
                if (code in 301..308 && code != 304 && code != 305) {
                    val loc = conn.getHeaderField("Location").orEmpty().trim().ifEmpty {
                        throw CatalogHttpException(code, "Redirect missing Location")
                    }
                    current = uri.resolve(loc).toString()
                    return@repeat
                }
                if (code >= 400) throw CatalogHttpException(code, "HTTP $code")
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var total = 0
                (conn.inputStream ?: throw IOException("Empty HTTP body")).use { input ->
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        total += n
                        if (total > maxBytes) throw IOException("响应体超过最大限制 ($maxBytes bytes)")
                        out.write(buf, 0, n)
                    }
                }
                return CatalogHttpResponse(code, out.toByteArray())
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("HTTP 重定向次数过多")
    }
}

internal object OcrCatalogPolicy {
    private val atomEntryRegex = Regex("<entry\\b[^>]*>(.*?)</entry>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val atomUpdatedRegex = Regex("<updated\\b[^>]*>([^<]+)</updated>", RegexOption.IGNORE_CASE)
    private val atomHrefRegex = Regex(
        """href\s*=\s*["']([^"']*?/misaka02/GlobalMinusOne/releases/tag/([^"/?#<]+))["']""",
        RegexOption.IGNORE_CASE
    )

    fun parseAtomReleaseTags(atomXml: String): List<String> {
        require(atomXml.contains("<feed", ignoreCase = true)) { "在线更新源返回了非目录内容" }
        data class Entry(val updated: String, val order: Int, val tag: String)
        return atomEntryRegex.findAll(atomXml).mapIndexedNotNull { index, match ->
            val block = match.groupValues[1]
            val rawTag = atomHrefRegex.find(block)?.groupValues?.get(2) ?: return@mapIndexedNotNull null
            val tag = runCatching { URLDecoder.decode(rawTag, "UTF-8") }.getOrDefault(rawTag).trim()
            val invalid = !tag.startsWith("ocr-", true) || tag.isEmpty() || tag == "." || tag == ".." ||
                tag.any { it == '/' || it == '\\' || it.isISOControl() }
            if (invalid) null else Entry(atomUpdatedRegex.find(block)?.groupValues?.getOrNull(1)?.trim().orEmpty(), index, tag)
        }.sortedWith(compareByDescending<Entry> { it.updated }.thenBy { it.order }).map { it.tag }.distinct().toList()
    }

    fun normalizeMirrorPrefix(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return ""
        val uri = runCatching { URI(trimmed) }.getOrNull()
            ?: throw IllegalArgumentException("加速前缀格式无效，请输入完整 https:// 地址")
        require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
            "加速前缀必须为不含账号密码与查询参数的 HTTPS 地址"
        }
        return trimmed
    }

    fun applyMirrorPrefix(officialUrl: String, mirrorPrefix: String): String {
        val prefix = normalizeMirrorPrefix(mirrorPrefix)
        if (prefix.isEmpty()) return officialUrl
        val uri = runCatching { URI(officialUrl) }.getOrNull() ?: return officialUrl
        return if (uri.scheme.equals("https", true) && uri.host.equals("github.com", true)) "$prefix/$officialUrl" else officialUrl
    }
}

internal object OcrMirrorStore {
    private const val PREFS_NAME = "ocr_extension_private"
    private const val KEY_MIRROR_PREFIX = "github_mirror_prefix"

    fun getMirrorPrefix(context: Context): String = runCatching {
        OcrCatalogPolicy.normalizeMirrorPrefix(
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_MIRROR_PREFIX, "").orEmpty()
        )
    }.getOrDefault("")

    fun saveMirrorPrefix(context: Context, rawPrefix: String): String =
        OcrCatalogPolicy.normalizeMirrorPrefix(rawPrefix).also { norm ->
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_MIRROR_PREFIX, norm).apply()
        }
}

internal object OcrCancellableHttp {
    suspend fun <T> run(
        initialUrl: String,
        connectTimeoutMs: Int = 10_000,
        readTimeoutMs: Int = 20_000,
        maxRedirects: Int = 8,
        onResponse: (HttpURLConnection, AtomicBoolean) -> T
    ): T = suspendCancellableCoroutine { cont ->
        val activeConn = AtomicReference<HttpURLConnection?>(null)
        val cancelled = AtomicBoolean(false)
        val worker = CompletableFuture.runAsync({
            var curConn: HttpURLConnection? = null
            try {
                var target = URL(initialUrl.trim())
                var connected: HttpURLConnection? = null
                for (redirect in 0..maxRedirects) {
                    if (cancelled.get()) throw CancellationException("HTTP request cancelled")
                    require(target.protocol.equals("https", true) && target.userInfo == null && target.host.isNotBlank()) {
                        "网络请求必须使用不含账号密码的 HTTPS 地址"
                    }
                    val conn = (target.openConnection() as HttpURLConnection).apply {
                        connectTimeout = connectTimeoutMs
                        readTimeout = readTimeoutMs
                        instanceFollowRedirects = false
                    }
                    curConn = conn
                    activeConn.set(conn)
                    val status = conn.responseCode
                    if (status in listOf(301, 302, 303, 307, 308)) {
                        target = URL(target, conn.getHeaderField("Location") ?: error("重定向缺少目标地址"))
                        activeConn.set(null)
                        conn.disconnect()
                        curConn = null
                        continue
                    }
                    connected = conn
                    break
                }
                val finalConn = checkNotNull(connected) { "网络请求重定向次数过多" }
                if (cancelled.get()) throw CancellationException("HTTP request cancelled")
                val value = onResponse(finalConn, cancelled)
                if (cont.isActive) cont.resume(value)
            } catch (t: Throwable) {
                if (cont.isActive) cont.resumeWithException(t)
            } finally {
                activeConn.getAndSet(null)?.let { runCatching { it.disconnect() } }
                runCatching { curConn?.disconnect() }
            }
        }, Dispatchers.IO.asExecutor())
        cont.invokeOnCancellation {
            cancelled.set(true)
            Dispatchers.IO.asExecutor().execute { runCatching { activeConn.getAndSet(null)?.disconnect() } }
            worker.cancel(true)
        }
    }
}

@Serializable
internal data class OcrFileAsset(val name: String, val url: String, val sha256: String, val bytes: Long) {
    init {
        require(sha256.matches(Regex("[a-f0-9]{64}")) && bytes in 1L..(112L * 1024 * 1024))
        val uri = URI(url)
        require(uri.scheme == "https" && uri.host != null && uri.rawUserInfo == null && uri.rawFragment == null)
        require(name.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,119}")))
    }
}

@Serializable
internal data class OcrModelPack(
    val id: String,
    val title: String,
    val detAsset: OcrFileAsset,
    val recAsset: OcrFileAsset,
    val dictAsset: OcrFileAsset? = null,
    val isCustom: Boolean = false
) {
    val files: List<OcrFileAsset>
        get() = listOfNotNull(detAsset, recAsset, dictAsset)
}

internal class NativeOcrReleaseIndexUnavailable(cause: Exception) : IOException("无法读取原生 OCR 引擎发布目录", cause)

@Serializable
internal data class NativeOcrMember(
    val name: String,
    val executable: Boolean = false,
    val bytes: Long,
    val sha256: String
) {
    init {
        require(name in REQUIRED_MEMBERS) { "原生 OCR 包内包含未受支持的文件: $name" }
        require(bytes in 1L..(64L * 1024 * 1024)) { "原生 OCR 文件大小无效" }
        require(SHA256_RE.matches(sha256)) { "原生 OCR 文件 SHA-256 格式无效" }
    }

    companion object {
        val REQUIRED_MEMBERS = setOf("gmo-ocr-native", "libonnxruntime.so")
        private val SHA256_RE = Regex("[a-f0-9]{64}")
    }
}

internal class NativeOcrCatalog(jsonStr: String) {
    @Serializable
    private data class Manifest(
        val schemaVersion: Int,
        val revision: Long,
        val minAppVersionCode: Long,
        val abi: String,
        val version: String,
        val ortVersion: String,
        val revoked: Boolean = false,
        val archive: OcrFileAsset? = null,
        val files: List<NativeOcrMember> = emptyList(),
        val noticesUrl: String = "",
        val issuesUrl: String = ""
    )

    private val manifest = parser.decodeFromString<Manifest>(jsonStr)
    val revision: Long = manifest.revision
    val minAppVersionCode: Long = manifest.minAppVersionCode
    val abi: String = manifest.abi
    val version: String = manifest.version
    val ortVersion: String = manifest.ortVersion
    val revoked: Boolean = manifest.revoked
    val archive: OcrFileAsset? = manifest.archive
    val files: List<NativeOcrMember> = manifest.files

    init {
        require(manifest.schemaVersion == 1 && revision > 0 && minAppVersionCode > 0) { "原生 OCR 目录版本无效" }
        require(abi == "arm64-v8a") { "原生 OCR 仅支持 arm64-v8a" }
        require(VERSION_RE.matches(version)) { "原生 OCR 版本号无效" }
        if (revoked) {
            require(archive == null && files.isEmpty()) { "已撤回目录不可包含可执行文件" }
        } else {
            val arc = requireNotNull(archive) { "原生 OCR 目录缺少归档信息" }
            require(arc.name.endsWith(".zip")) { "原生 OCR 归档必须为 .zip" }
            require(files.map { it.name }.toSet() == NativeOcrMember.REQUIRED_MEMBERS && files.size == 2) {
                "原生 OCR 成员列表不完整"
            }
            require(files.single { it.name == "gmo-ocr-native" }.executable) { "缺少可执行标记" }
        }
    }

    companion object {
        private val VERSION_RE = Regex("[0-9]+\\.[0-9]+\\.[0-9]+")
        private val parser = Json { ignoreUnknownKeys = true }
    }
}

internal class NativeOcrPublishedCatalog(
    directory: File,
    private val appVersionCode: Long,
    private val http: CatalogHttp = OcrCatalogNetwork,
    private val mirrorPrefixProvider: () -> String = { "" }
) {
    private val accepted = File(directory, "published-native-catalog-v1.json")

    fun cached(): NativeOcrCatalog? =
        if (accepted.exists()) runCatching { parse(OcrModelStore.readMetadata(accepted)) }.getOrNull() else null

    private fun parse(raw: String) = NativeOcrCatalog(raw).also {
        check(it.minAppVersionCode <= appVersionCode) { "原生 OCR 引擎需要更新主程序" }
    }

    suspend fun refresh(): NativeOcrCatalog? = withContext(Dispatchers.IO) {
        refreshGate.withLock {
            val known = cached()
            val mirror = runCatching { OcrCatalogPolicy.normalizeMirrorPrefix(mirrorPrefixProvider()) }.getOrDefault("")
            val source = if (mirror.isNotEmpty()) {
                runCatching { fetchAtom(http, mirror) }.getOrNull() ?: fetchApi(http)
            } else {
                try {
                    fetchApi(http)
                } catch (err: NativeOcrReleaseIndexUnavailable) {
                    runCatching { fetchAtom(http, "") }.getOrNull() ?: throw err
                }
            }
            if (source == null) {
                check(known == null) { "原生 OCR 更新目录暂不可用，已保留现有校验状态" }
                return@withLock null
            }
            val urls = buildList {
                if (mirror.isNotEmpty()) add(OcrCatalogPolicy.applyMirrorPrefix(source.url, mirror))
                add(source.url)
            }.distinct()
            var response: CatalogHttpResponse? = null
            var lastErr: Exception? = null
            for (u in urls) {
                try {
                    val r = http.get(u, null, MAX_OCR_METADATA_BYTES)
                    if (r.code == 200) {
                        response = r
                        break
                    }
                    lastErr = CatalogHttpException(r.code, "HTTP ${r.code}")
                } catch (e: Exception) {
                    lastErr = e
                }
            }
            val resp = response ?: throw (lastErr ?: IOException("原生 OCR 目录下载失败"))
            source.size?.let { check(resp.body.size.toLong() == it) { "原生 OCR 目录下载不完整" } }
            source.sha256?.let {
                check(OcrModelStore.sha256Hex(resp.body) == it) { "原生 OCR 目录校验值不匹配" }
            }
            val raw = resp.body.decodeToString(throwOnInvalidSequence = true)
            val incoming = parse(raw)
            check(incoming.revision == source.revision) { "原生 OCR 目录修订号与发布标签不一致" }
            if (!incoming.revoked) {
                val arc = requireNotNull(incoming.archive)
                check(arc.url == "$OFFICIAL_RELEASES/download/${source.tag}/${arc.name}") {
                    "原生 OCR 下载地址不属于当前官方发布"
                }
            }
            if (known != null) {
                check(incoming.revision >= known.revision) { "已拒绝回退到旧版原生 OCR 目录" }
                if (incoming.revision == known.revision) {
                    check(raw == OcrModelStore.readMetadata(accepted)) { "同版本原生 OCR 目录内容发生篡改" }
                    return@withLock known
                }
            }
            OcrModelStore.writeMetadata(accepted, raw)
            incoming
        }
    }

    private data class ReleaseSource(val revision: Long, val tag: String, val url: String, val size: Long?, val sha256: String?)
    private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun fetchApi(http: CatalogHttp): ReleaseSource? {
        var latest: Pair<Long, JsonObject>? = null
        for (page in 1..4) {
            val resp = try {
                http.get("$GITHUB_API/releases?per_page=100&page=$page", null, 2 * 1024 * 1024)
            } catch (e: CatalogHttpException) {
                throw NativeOcrReleaseIndexUnavailable(e)
            } catch (e: IOException) {
                throw NativeOcrReleaseIndexUnavailable(e)
            }
            check(resp.code == 200) { "无法检查在线原生 OCR 发布" }
            val arr = Json.parseToJsonElement(resp.body.decodeToString(throwOnInvalidSequence = true)) as? JsonArray
                ?: error("无效的 GitHub 发布列表")
            for (item in arr) {
                val rel = item as? JsonObject ?: continue
                if ((rel["draft"] as? JsonPrimitive)?.booleanOrNull != false ||
                    (rel["prerelease"] as? JsonPrimitive)?.booleanOrNull != false
                ) continue
                val tag = rel.str("tag_name") ?: continue
                val rev = TAG.matchEntire(tag)?.groupValues?.get(1)?.toLongOrNull() ?: continue
                if (rev > (latest?.first ?: 0L)) latest = rev to rel
            }
            if (arr.size < 100) break
        }
        val (rev, rel) = latest ?: return null
        val tag = requireNotNull(rel.str("tag_name"))
        val assets = rel["assets"] as? JsonArray ?: error("原生 OCR 发布缺少附件")
        val cat = assets.filterIsInstance<JsonObject>().singleOrNull { it.str("name") == CATALOG_NAME }
            ?: error("原生 OCR 发布缺少目录文件")
        val size = (cat["size"] as? JsonPrimitive)?.longOrNull ?: 0L
        val sum = cat.str("digest")?.takeIf { DIGEST.matches(it) }?.removePrefix("sha256:")
            ?: error("原生 OCR 目录缺少 SHA-256")
        val url = "$OFFICIAL_RELEASES/download/$tag/$CATALOG_NAME"
        check(cat.str("state") == "uploaded" && cat.str("browser_download_url") == url && size in 1L..MAX_OCR_METADATA_BYTES.toLong()) {
            "原生 OCR 目录发布元数据无效"
        }
        return ReleaseSource(rev, tag, url, size, sum)
    }

    private fun fetchAtom(http: CatalogHttp, mirrorPrefix: String): ReleaseSource? {
        val resp = http.get(OcrCatalogPolicy.applyMirrorPrefix("$OFFICIAL_RELEASES.atom", mirrorPrefix), null, 512 * 1024)
        if (resp.code != 200) return null
        val (rev, tag) = OcrCatalogPolicy.parseAtomReleaseTags(resp.body.decodeToString(throwOnInvalidSequence = true))
            .mapNotNull { t -> TAG.matchEntire(t)?.groupValues?.get(1)?.toLongOrNull()?.let { it to t } }
            .maxByOrNull { it.first }
            ?: return null
        return ReleaseSource(rev, tag, "$OFFICIAL_RELEASES/download/$tag/$CATALOG_NAME", null, null)
    }

    companion object {
        private const val GITHUB_API = "https://api.github.com/repos/misaka02/GlobalMinusOne"
        private const val OFFICIAL_RELEASES = "https://github.com/misaka02/GlobalMinusOne/releases"
        private const val CATALOG_NAME = "native-ocr-catalog-v1.json"
        private val DIGEST = Regex("sha256:[0-9a-f]{64}")
        private val TAG = Regex("ocr-native-arm64-v([1-9][0-9]{0,17})")
        private val refreshGate = Mutex()
    }
}

/**
 * Single unified OCR asset store & ARM64 native ORT runtime manager.
 * Zero models and zero native binaries are packaged into the main APK.
 */
internal class OcrModelStore(private val context: Context) {

    private val modelDir = File(context.noBackupFilesDir, "ocr-models-v1")
    private val legacyModelDir = File(context.noBackupFilesDir, "ocr-wasm-v1")
    private val nativeDir = File(context.noBackupFilesDir, "ocr-native-arm64-v1")
    private val appVersionCode: Long = runCatching {
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
    }.getOrDefault(100L)
    private val nativeOnline by lazy {
        NativeOcrPublishedCatalog(nativeDir, appVersionCode, mirrorPrefixProvider = { OcrMirrorStore.getMirrorPrefix(context) })
    }
    private val mirrorPrefix: String
        get() = OcrMirrorStore.getMirrorPrefix(context)

    @Serializable
    internal data class NativeInstalledRecord(
        val version: String,
        val ortVersion: String,
        val revision: Long,
        val members: List<NativeOcrMember>
    )

    internal data class VerifiedNativeBinaries(
        val execFile: File,
        val ortLibFile: File,
        val record: NativeInstalledRecord,
        val execSha256: String,
        val ortSha256: String
    )

    internal data class NativeInventory(
        val arm64Supported: Boolean,
        val installed: Boolean,
        val installedVersion: String,
        val ortVersion: String,
        val revoked: Boolean,
        val remoteCatalog: NativeOcrCatalog?,
        val expectedArchiveBytes: Long
    )

    internal data class ModelRoutes(val pack: OcrModelPack, val detFile: File, val recFile: File, val dictFile: File?)

    fun allModels(): List<OcrModelPack> = (CURATED_PACKS + customModels()).distinctBy { it.id }

    fun modelById(id: String): OcrModelPack? = allModels().firstOrNull { it.id == id }

    private fun readCustomModelsFile(file: File): List<OcrModelPack> =
        if (!file.exists()) emptyList()
        else runCatching { json.decodeFromString<List<OcrModelPack>>(readMetadata(file)) }.getOrDefault(emptyList())

    fun customModels(): List<OcrModelPack> =
        (readCustomModelsFile(customModelsRecord) + readCustomModelsFile(legacyCustomModelsRecord))
            .distinctBy { it.id }

    private fun findVerifiedModelFile(asset: OcrFileAsset): File? =
        checkedAssetFile(modelDir, asset) ?: checkedAssetFile(legacyModelDir, asset)

    fun isModelReady(id: String): Boolean = resolveModelRoutes(id) != null

    fun firstReadyModelId(preferredIds: List<String> = listOf("v6_small", "ch_v5", "v6_tiny", "en_v5")): String? =
        preferredIds.firstOrNull(::isModelReady) ?: allModels().firstOrNull { isModelReady(it.id) }?.id

    fun resolveModelRoutes(id: String): ModelRoutes? {
        if (!validModelId(id)) return null
        val pack = modelById(id) ?: return null
        val det = findVerifiedModelFile(pack.detAsset) ?: return null
        val rec = findVerifiedModelFile(pack.recAsset) ?: return null
        val dict = pack.dictAsset?.let { d ->
            findVerifiedModelFile(d) ?: return null
        }
        return ModelRoutes(pack, det, rec, dict)
    }

    suspend fun downloadModel(id: String, onProgress: (String, Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        downloadGate.withLock {
            val pack = modelById(id) ?: error("未找到指定 OCR 模型: $id")
            val missing = pack.files.filter { findVerifiedModelFile(it) == null }
            if (missing.isNotEmpty()) {
                installFiles(modelDir, missing, onProgress, mirrorPrefix)
            }
        }
    }

    suspend fun importLocalPaddleModel(
        recUri: Uri,
        detUri: Uri?,
        dictUri: Uri?,
        title: String
    ): OcrModelPack = withContext(Dispatchers.IO) {
        downloadGate.withLock {
            val recName = resolveUriName(recUri) ?: "custom_rec.onnx"
            require(recName.endsWith(".onnx", true)) { "PP-OCR 识别模型需为 .onnx 文件" }
            val recAsset = requireNotNull(context.contentResolver.openInputStream(recUri)) { "无法读取识别模型文件" }
                .use { importLocalFile(modelDir, recName, it, 112L * 1024 * 1024) }
            val detAsset = if (detUri != null) {
                val detName = resolveUriName(detUri) ?: "custom_det.onnx"
                require(detName.endsWith(".onnx", true)) { "检测模型需为 .onnx 文件" }
                requireNotNull(context.contentResolver.openInputStream(detUri)) { "无法读取检测模型文件" }
                    .use { importLocalFile(modelDir, detName, it, 112L * 1024 * 1024) }
            } else {
                val baseDet = SHARED_DET_V5
                if (findVerifiedModelFile(baseDet) == null) {
                    installFiles(modelDir, listOf(baseDet), { _, _, _ -> }, mirrorPrefix)
                }
                baseDet
            }
            val dictAsset = dictUri?.let { u ->
                requireNotNull(context.contentResolver.openInputStream(u)) { "无法读取字典文件" }.use {
                    importLocalFile(modelDir, resolveUriName(u) ?: "dict.txt", it, 2L * 1024 * 1024)
                }
            }
            val cleanId = ("custom_" + recName.removeSuffix(".onnx").lowercase(Locale.US)
                .replace(Regex("[^a-z0-9_]"), "_").replace(Regex("_+"), "_").trim('_')).take(40) +
                "_" + recAsset.sha256.take(6)
            val pack = OcrModelPack(
                id = cleanId,
                title = title.trim().ifBlank { "自定义 ONNX (${recName.removeSuffix(".onnx")})" },
                detAsset = detAsset,
                recAsset = recAsset,
                dictAsset = dictAsset,
                isCustom = true
            )
            val updated = customModels().filterNot { it.id == pack.id } + pack
            writeMetadata(customModelsRecord, json.encodeToString(updated))
            pack
        }
    }

    suspend fun deleteModel(id: String) = withContext(Dispatchers.IO) {
        downloadGate.withLock {
            val pack = modelById(id) ?: return@withLock
            val remaining = allModels().filterNot { it.id == id }
            val stillNeededShas = remaining.flatMapTo(HashSet()) { it.files.map { f -> f.sha256 } }
            pack.files.forEach { f ->
                if (f.sha256 !in stillNeededShas) {
                    File(modelDir, f.sha256).delete()
                    File(legacyModelDir, f.sha256).delete()
                    verifiedStamps.remove(f.sha256)
                }
            }
            if (pack.isCustom) {
                val nextPrimary = readCustomModelsFile(customModelsRecord).filterNot { it.id == id }
                if (nextPrimary.isEmpty()) customModelsRecord.delete()
                else writeMetadata(customModelsRecord, json.encodeToString(nextPrimary))
                val nextLegacy = readCustomModelsFile(legacyCustomModelsRecord).filterNot { it.id == id }
                if (nextLegacy.isEmpty()) legacyCustomModelsRecord.delete()
                else writeMetadata(legacyCustomModelsRecord, json.encodeToString(nextLegacy))
            }
        }
    }

    fun isDeviceArm64(): Boolean = isAbiListArm64(Build.SUPPORTED_ABIS)

    fun cachedNativeCatalog(): NativeOcrCatalog? = nativeOnline.cached()

    suspend fun refreshNative(): NativeOcrCatalog? = nativeOnline.refresh()

    fun nativeInventory(): NativeInventory {
        val remote = cachedNativeCatalog()
        val revoked = remote?.revoked == true
        val verified = if (revoked) null else resolveVerifiedNativeBinaries()
        return NativeInventory(
            arm64Supported = isDeviceArm64(),
            installed = verified != null,
            installedVersion = verified?.record?.version.orEmpty(),
            ortVersion = verified?.record?.ortVersion ?: remote?.ortVersion ?: "1.22.0",
            revoked = revoked,
            remoteCatalog = remote,
            expectedArchiveBytes = remote?.archive?.bytes ?: 18_750_000L
        )
    }

    fun resolveVerifiedNativeBinaries(): VerifiedNativeBinaries? {
        if (!isDeviceArm64() || cachedNativeCatalog()?.revoked == true) return null
        val rec = readInstalledNativeRecord() ?: return null
        val ex = rec.members.firstOrNull { it.name == "gmo-ocr-native" && it.executable } ?: return null
        val ort = rec.members.firstOrNull { it.name == "libonnxruntime.so" } ?: return null
        val exFile = checkedAssetFile(nativeDir, OcrFileAsset(ex.name, LOCAL_IMPORT_ORIGIN + ex.name, ex.sha256, ex.bytes)) ?: return null
        val ortFile = checkedAssetFile(nativeDir, OcrFileAsset(ort.name, LOCAL_IMPORT_ORIGIN + ort.name, ort.sha256, ort.bytes)) ?: return null
        return if (hasValidElf64AArch64Header(exFile) && hasValidElf64AArch64Header(ortFile)) {
            VerifiedNativeBinaries(exFile, ortFile, rec, ex.sha256, ort.sha256)
        } else {
            null
        }
    }

    suspend fun downloadNativeRuntime(onProgress: (String, Long, Long) -> Unit): NativeInstalledRecord =
        withContext(Dispatchers.IO) {
            downloadGate.withLock {
                check(isDeviceArm64()) { "当前设备不是 arm64-v8a 架构" }
                val cat = nativeOnline.refresh() ?: error("尚未发现已发布的原生 ARM64 OCR 资源版本，可导入本地 ZIP 包")
                check(!cat.revoked) { "该原生 OCR 资源版本已被在线目录撤回" }
                val tmpZip = createTempFileIn(nativeDir)
                try {
                    downloadAsset(requireNotNull(cat.archive), tmpZip, onProgress, mirrorPrefix)
                    val asAssets = cat.files.map { OcrFileAsset(it.name, LOCAL_IMPORT_ORIGIN + it.name, it.sha256, it.bytes) }
                    installArchive(nativeDir, tmpZip, asAssets)
                    val exF = requireNotNull(checkedAssetFile(nativeDir, asAssets.first { it.name == "gmo-ocr-native" }))
                    val ortF = requireNotNull(checkedAssetFile(nativeDir, asAssets.first { it.name == "libonnxruntime.so" }))
                    check(hasValidElf64AArch64Header(exF) && hasValidElf64AArch64Header(ortF)) { "原生引擎二进制 ELF 头部无效" }
                    NativeInstalledRecord(cat.version, cat.ortVersion, cat.revision, cat.files).also {
                        writeMetadata(nativeRecordFile, json.encodeToString(it))
                    }
                } finally {
                    tmpZip.delete()
                }
            }
        }

    suspend fun importNativeRuntimeArchive(uri: Uri): NativeInstalledRecord = withContext(Dispatchers.IO) {
        downloadGate.withLock {
            check(isDeviceArm64()) { "当前设备不是 arm64-v8a 架构" }
            check(cachedNativeCatalog()?.revoked != true) { "在线目录已撤回当前原生 OCR 版本" }
            val keepHashes = readInstalledNativeRecord()?.members?.map { it.sha256 }?.toSet().orEmpty()
            val stagedHashes = mutableListOf<String>()
            val tmpZip = createTempFileIn(nativeDir)
            var committed = false
            try {
                copyUriBoundedToTemp(context, uri, tmpZip, 64L * 1024 * 1024, "无法读取所选 ZIP 包", "原生 OCR ZIP 包超出安全大小")
                val imported = mutableListOf<NativeOcrMember>()
                ZipFile(tmpZip).use { zf ->
                    val entries = zf.entries().asSequence().toList()
                    check(entries.size == 2 && entries.map { it.name }.toSet() == NativeOcrMember.REQUIRED_MEMBERS) {
                        "原生 OCR 包须仅包含 gmo-ocr-native 与 libonnxruntime.so"
                    }
                    for (entry in entries) {
                        currentCoroutineContext().ensureActive()
                        check(!entry.isDirectory && entry.size in 1L..(64L * 1024 * 1024)) { "包内文件大小无效: ${entry.name}" }
                        val peek = ByteArray(24)
                        var nPeek = 0
                        val asset = zf.getInputStream(entry).use { stream ->
                            importLocalFile(nativeDir, entry.name, PeekingInputStream(stream, peek) { nPeek = it }, 64L * 1024 * 1024)
                        }
                        stagedHashes += asset.sha256
                        check(nPeek >= 20 && isElf64AArch64Header(peek)) { "${entry.name} 不是有效的 ARM64 ELF 文件" }
                        imported += NativeOcrMember(entry.name, entry.name == "gmo-ocr-native", asset.bytes, asset.sha256)
                    }
                }
                NativeInstalledRecord("1.0.0", "1.22.0", 0L, imported).also { writeMetadata(nativeRecordFile, json.encodeToString(it)) }
                    .also { committed = true }
            } finally {
                if (!committed) stagedHashes.filter { it !in keepHashes }.forEach { h ->
                    File(nativeDir, h).delete(); verifiedStamps.remove(h)
                }
                tmpZip.delete()
            }
        }
    }

    suspend fun deleteNativeRuntime() = withContext(Dispatchers.IO) {
        downloadGate.withLock {
            readInstalledNativeRecord()?.members?.forEach { File(nativeDir, it.sha256).delete(); verifiedStamps.remove(it.sha256) }
            nativeRecordFile.delete()
        }
    }

    private fun readInstalledNativeRecord(): NativeInstalledRecord? =
        if (!nativeRecordFile.exists()) null
        else runCatching { json.decodeFromString<NativeInstalledRecord>(readMetadata(nativeRecordFile)) }.getOrNull()

    private fun hasValidElf64AArch64Header(file: File): Boolean = runCatching {
        FileInputStream(file).use { val b = ByteArray(24); it.read(b) >= 20 && isElf64AArch64Header(b) }
    }.getOrDefault(false)

    private fun resolveUriName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/')
    }.getOrNull()

    private val customModelsRecord get() = File(modelDir, "custom-models-v1.json")
    private val legacyCustomModelsRecord get() = File(legacyModelDir, "custom-models-v1.json")
    private val nativeRecordFile get() = File(nativeDir, "installed-native-ocr-v1.json")

    private class PeekingInputStream(
        private val delegate: InputStream,
        private val buf: ByteArray,
        private val onCount: (Int) -> Unit
    ) : InputStream() {
        private var cap = 0
        override fun read(): Int = delegate.read().also { ch ->
            if (ch >= 0 && cap < buf.size) {
                buf[cap++] = ch.toByte()
                onCount(cap)
            }
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, len).also { n ->
            if (n > 0 && cap < buf.size) {
                val c = minOf(n, buf.size - cap)
                System.arraycopy(b, off, buf, cap, c)
                cap += c
                onCount(cap)
            }
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val downloadGate = Mutex()
        private val modelIdPattern = Regex("[a-z0-9_]{2,48}")
        private data class VerifiedFileStamp(val absolutePath: String, val bytes: Long, val lastModified: Long)
        private val verifiedStamps = ConcurrentHashMap<String, VerifiedFileStamp>()

        val SHARED_DET_V5 = OcrFileAsset(
            "ch_PP-OCRv5_det_mobile.onnx",
            "https://www.modelscope.cn/models/RapidAI/RapidOCR/resolve/v3.10.0/onnx/PP-OCRv5/det/ch_PP-OCRv5_det_mobile.onnx",
            "4d97c44a20d30a81aad087d6a396b08f786c4635742afc391f6621f5c6ae78ae",
            4819576L
        )

        val CURATED_PACKS: List<OcrModelPack> = listOf(
            OcrModelPack(
                id = "v6_small",
                title = "PP-OCRv6 Small（50语统一新架构 · 推荐）",
                detAsset = OcrFileAsset(
                    "PP-OCRv6_det_small.onnx",
                    "https://www.modelscope.cn/models/RapidAI/RapidOCR/resolve/master/onnx/PP-OCRv6/det/PP-OCRv6_det_small.onnx",
                    "090f04abcd9d9a7498bc4ebf677e4cb9bdce1fe4197ddb7e529f1ef44e1ff94f",
                    9929594L
                ),
                recAsset = OcrFileAsset(
                    "PP-OCRv6_rec_small.onnx",
                    "https://www.modelscope.cn/models/RapidAI/RapidOCR/resolve/master/onnx/PP-OCRv6/rec/PP-OCRv6_rec_small.onnx",
                    "6f327246b50388f3c176ae304bd95767ea6dc0c9ae92153ef8cbe210b3c14884",
                    21234383L
                )
            ),
            OcrModelPack(
                id = "v6_tiny",
                title = "PP-OCRv6 Tiny（6.3 MB 极速轻巧版）",
                detAsset = OcrFileAsset(
                    "PP-OCRv6_det_tiny.onnx",
                    "https://www.modelscope.cn/models/RapidAI/RapidOCR/resolve/master/onnx/PP-OCRv6/det/PP-OCRv6_det_tiny.onnx",
                    "f42c0fbd294d95eac1a550e131b277dac97462c8025fa4b6c3cec1b7894bd3d5",
                    1829618L
                ),
                recAsset = OcrFileAsset(
                    "PP-OCRv6_rec_tiny.onnx",
                    "https://www.modelscope.cn/models/RapidAI/RapidOCR/resolve/master/onnx/PP-OCRv6/rec/PP-OCRv6_rec_tiny.onnx",
                    "e16e242de5937ad92609223f19bc2aff3727ee40b095f996907c24749bad251b",
                    4489813L
                )
            ),
            OcrModelPack(
                id = "ch_v5",
                title = "PP-OCRv5 中 / 英 / 日通用",
                detAsset = SHARED_DET_V5,
                recAsset = OcrFileAsset(
                    "ch_PP-OCRv5_rec_mobile.onnx",
                    "https://www.modelscope.cn/models/RapidAI/RapidOCR/resolve/v3.10.0/onnx/PP-OCRv5/rec/ch_PP-OCRv5_rec_mobile.onnx",
                    "5825fc7ebf84ae7a412be049820b4d86d77620f204a041697b0494669b1742c5",
                    16631306L
                )
            ),
            OcrModelPack(
                id = "en_v5",
                title = "PP-OCRv5 英文与数字",
                detAsset = SHARED_DET_V5,
                recAsset = OcrFileAsset(
                    "en_PP-OCRv5_rec_mobile.onnx",
                    "https://www.modelscope.cn/models/RapidAI/RapidOCR/resolve/v3.10.0/onnx/PP-OCRv5/rec/en_PP-OCRv5_rec_mobile.onnx",
                    "c3461add59bb4323ecba96a492ab75e06dda42467c9e3d0c18db5d1d21924be8",
                    7872351L
                )
            ),
            OcrModelPack(
                id = "latin_v5",
                title = "PP-OCRv5 欧洲拉丁文字",
                detAsset = SHARED_DET_V5,
                recAsset = OcrFileAsset(
                    "latin_PP-OCRv5_rec_mobile.onnx",
                    "https://www.modelscope.cn/models/RapidAI/RapidOCR/resolve/v3.10.0/onnx/PP-OCRv5/rec/latin_PP-OCRv5_rec_mobile.onnx",
                    "b20bd37c168a570f583afbc8cd7925603890efbcdc000a59e22c269d160b5f5a",
                    7904513L
                )
            ),
            OcrModelPack(
                id = "korean_v5",
                title = "PP-OCRv5 韩文与英文",
                detAsset = SHARED_DET_V5,
                recAsset = OcrFileAsset(
                    "korean_PP-OCRv5_rec_mobile.onnx",
                    "https://www.modelscope.cn/models/RapidAI/RapidOCR/resolve/v3.10.0/onnx/PP-OCRv5/rec/korean_PP-OCRv5_rec_mobile.onnx",
                    "cd6e2ea50f6943ca7271eb8c56a877a5a90720b7047fe9c41a2e541a25773c9b",
                    13488748L
                )
            )
        )

        fun validModelId(id: String): Boolean = modelIdPattern.matches(id)
        fun isAbiListArm64(abis: Array<String>): Boolean = abis.any { it.equals("arm64-v8a", ignoreCase = true) }

        fun isElf64AArch64Header(h: ByteArray): Boolean =
            h.size >= 20 &&
                h[0] == 0x7F.toByte() && h[1] == 'E'.code.toByte() && h[2] == 'L'.code.toByte() && h[3] == 'F'.code.toByte() &&
                h[4] == 2.toByte() && h[5] == 1.toByte() &&
                (((h[18].toInt() and 0xFF) or ((h[19].toInt() and 0xFF) shl 8)) == 183)

        internal fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }

        private fun createTempFileIn(dir: File): File {
            check(dir.isDirectory || dir.mkdirs()) { "无法建立 OCR 本地目录" }
            return File.createTempFile("ocr-", ".tmp", dir)
        }

        internal fun readMetadata(file: File): String = file.inputStream().use { input ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                check(out.size() + n <= MAX_OCR_METADATA_BYTES) { "OCR 元数据超出安全上限" }
                out.write(buf, 0, n)
            }
            out.toByteArray().decodeToString(throwOnInvalidSequence = true)
        }

        internal fun writeMetadata(file: File, value: String) {
            val bytes = value.encodeToByteArray()
            require(bytes.size in 1..MAX_OCR_METADATA_BYTES)
            val temp = createTempFileIn(requireNotNull(file.parentFile))
            try {
                temp.outputStream().use { s ->
                    s.write(bytes)
                    s.fd.sync()
                }
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally {
                temp.delete()
            }
        }

        private fun checkedAssetFile(dir: File, asset: OcrFileAsset): File? {
            val stored = File(dir, asset.sha256)
            if (!stored.isFile || stored.length() != asset.bytes) {
                verifiedStamps.remove(asset.sha256)
                return null
            }
            val mtime = stored.lastModified()
            val cached = verifiedStamps[asset.sha256]
            if (cached != null && cached.absolutePath == stored.absolutePath && cached.bytes == asset.bytes && cached.lastModified == mtime) {
                return stored
            }
            val valid = stored.inputStream().use { input ->
                val md = MessageDigest.getInstance("SHA-256")
                val buf = ByteArray(65536)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > asset.bytes) return@use false
                    md.update(buf, 0, n)
                }
                total == asset.bytes && md.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == asset.sha256
            }
            if (!valid) {
                verifiedStamps.remove(asset.sha256)
                return null
            }
            verifiedStamps[asset.sha256] = VerifiedFileStamp(stored.absolutePath, asset.bytes, mtime)
            return stored
        }

        private suspend fun copyUriBoundedToTemp(
            context: Context,
            uri: Uri,
            dest: File,
            maxBytes: Long,
            readErr: String,
            sizeErr: String
        ) {
            val input = requireNotNull(context.contentResolver.openInputStream(uri)) { readErr }
            input.use { stream ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = stream.read(buf)
                        if (n < 0) break
                        total += n
                        check(total <= maxBytes) { sizeErr }
                        out.write(buf, 0, n)
                    }
                }
            }
        }

        private suspend fun importLocalFile(dir: File, name: String, input: InputStream, maxBytes: Long): OcrFileAsset {
            val clean = name.replace(Regex("[^a-zA-Z0-9._-]"), "_").trimStart('.', '_', '-').take(110).ifBlank { "custom.bin" }
            val temp = createTempFileIn(dir)
            try {
                val md = MessageDigest.getInstance("SHA-256")
                val buf = ByteArray(64 * 1024)
                var total = 0L
                temp.outputStream().use { out ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        check(total <= maxBytes) { "$clean 超过大小限制" }
                        out.write(buf, 0, n)
                        md.update(buf, 0, n)
                    }
                    check(total > 0L) { "$clean 文件为空" }
                    out.fd.sync()
                }
                val asset = OcrFileAsset(clean, "$LOCAL_IMPORT_ORIGIN$clean", md.digest().joinToString("") { "%02x".format(it.toInt() and 255) }, total)
                val dest = File(dir, asset.sha256)
                Files.move(temp.toPath(), dest.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                verifiedStamps[asset.sha256] = VerifiedFileStamp(dest.absolutePath, asset.bytes, dest.lastModified())
                return asset
            } finally {
                temp.delete()
            }
        }

        private suspend fun installFiles(
            dir: File,
            assets: List<OcrFileAsset>,
            onProgress: (String, Long, Long) -> Unit,
            mirrorPrefix: String
        ) {
            val staged = mutableListOf<Pair<OcrFileAsset, File>>()
            try {
                for (asset in assets.distinctBy { it.sha256 }) {
                    currentCoroutineContext().ensureActive()
                    if (checkedAssetFile(dir, asset) != null) continue
                    val tmp = createTempFileIn(dir)
                    staged += asset to tmp
                    downloadAsset(asset, tmp, onProgress, mirrorPrefix)
                }
                publish(dir, staged)
            } finally {
                staged.forEach { (_, p) -> p.delete() }
            }
        }

        private suspend fun installArchive(dir: File, archive: File, assets: List<OcrFileAsset>) {
            val expected = assets.associateBy { it.name }
            check(expected.size == assets.size) { "OCR 扩展文件列表重复" }
            val staged = mutableListOf<Pair<OcrFileAsset, File>>()
            val ctx = currentCoroutineContext()
            try {
                ZipFile(archive).use { zip ->
                    val entries = zip.entries().asSequence().toList()
                    check(zip.size() == assets.size && entries.size == assets.size && entries.map { it.name }.toSet() == expected.keys) {
                        "OCR 扩展归档内容不匹配"
                    }
                    for (entry in entries) {
                        ctx.ensureActive()
                        val asset = expected.getValue(entry.name)
                        check(!entry.isDirectory && entry.size == asset.bytes) { "${asset.name} 大小不匹配" }
                        val tmp = createTempFileIn(dir)
                        staged += asset to tmp
                        zip.getInputStream(entry).use { copyVerified(it, tmp, asset) { ctx.ensureActive() } }
                    }
                }
                publish(dir, staged)
            } finally {
                staged.forEach { (_, p) -> p.delete() }
            }
        }

        private suspend fun downloadAsset(
            asset: OcrFileAsset,
            destination: File,
            onProgress: (String, Long, Long) -> Unit,
            mirrorPrefix: String
        ) {
            check(!asset.url.startsWith(LOCAL_IMPORT_ORIGIN)) { "本地导入模型请重新选择文件" }
            val urls = buildList {
                val trim = mirrorPrefix.trim()
                if (trim.isNotBlank() && asset.url.startsWith("https://github.com/")) {
                    add(OcrCatalogPolicy.applyMirrorPrefix(asset.url, trim))
                }
                add(asset.url)
            }.distinct()
            var lastErr: Throwable? = null
            for (u in urls) {
                currentCoroutineContext().ensureActive()
                try {
                    OcrCancellableHttp.run(u, 12_000, 28_000) { conn, cancel ->
                        check(conn.responseCode == 200) { "${asset.name} 下载失败（HTTP ${conn.responseCode}）" }
                        conn.inputStream.use {
                            copyVerified(it, destination, asset) { count ->
                                if (cancel.get()) throw CancellationException()
                                onProgress(asset.name, count, asset.bytes)
                            }
                        }
                    }
                    return
                } catch (c: CancellationException) {
                    throw c
                } catch (f: Throwable) {
                    lastErr = f
                    destination.delete()
                }
            }
            throw (lastErr ?: IllegalStateException("${asset.name} 下载失败"))
        }

        private suspend fun publish(dir: File, staged: List<Pair<OcrFileAsset, File>>) {
            currentCoroutineContext().ensureActive()
            for ((asset, tmp) in staged) {
                val dest = File(dir, asset.sha256)
                Files.move(tmp.toPath(), dest.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                verifiedStamps[asset.sha256] = VerifiedFileStamp(dest.absolutePath, asset.bytes, dest.lastModified())
            }
        }

        private fun copyVerified(input: InputStream, dest: File, asset: OcrFileAsset, onRead: (Long) -> Unit) {
            val md = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(64 * 1024)
            var total = 0L
            var lastTick = -1L
            dest.outputStream().use { out ->
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    check(total <= asset.bytes) { "${asset.name} 大于指定长度" }
                    out.write(buf, 0, n)
                    md.update(buf, 0, n)
                    if (lastTick != total / 262144L) {
                        lastTick = total / 262144L
                        onRead(total)
                    }
                }
                check(total == asset.bytes && md.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == asset.sha256) {
                    "${asset.name} 校验失败"
                }
                out.fd.sync()
            }
        }
    }
}
