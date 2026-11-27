package com.example.globalminusone.screenassistant

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import com.example.globalminusone.data.ScreenAssistantConfig
import com.example.globalminusone.system.privilege.RootStatus
import com.example.globalminusone.system.privilege.UserRootSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

/** Wire codec for the ARM64 `gmo-ocr-native` JSON-Lines protocol and `GMOR` RGB24 image framing. */
internal object NativeOcrWireCodec {
    private const val MAX_REPLY_BYTES = 128 * 1024

    data class ParsedReply(
        val id: String,
        val ok: Boolean,
        val error: String,
        val elapsedMs: Long,
        val engine: String,
        val blocks: List<ScreenTextBlock>
    )

    fun encodeGmorRgb24(argbPixels: IntArray, width: Int, height: Int, out: OutputStream) {
        require(width in 1..8192 && height in 1..8192 && argbPixels.size >= width * height) {
            "Invalid GMOR dimensions"
        }
        val hdr = ByteBuffer.allocate(16)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put('G'.code.toByte())
            .put('M'.code.toByte())
            .put('O'.code.toByte())
            .put('R'.code.toByte())
            .putInt(width)
            .putInt(height)
            .putInt(2)
        out.write(hdr.array())
        val rowBuf = ByteArray(width * 3)
        for (y in 0 until height) {
            val rowOff = y * width
            var bi = 0
            for (x in 0 until width) {
                val c = argbPixels[rowOff + x]
                rowBuf[bi++] = ((c ushr 16) and 0xFF).toByte()
                rowBuf[bi++] = ((c ushr 8) and 0xFF).toByte()
                rowBuf[bi++] = (c and 0xFF).toByte()
            }
            out.write(rowBuf)
        }
        out.flush()
    }

    fun buildPrewarmJson(
        id: String,
        detPath: String,
        recPath: String,
        dictPath: String = "",
        threads: Int = 4
    ): String = buildJsonObject {
        put("id", singleLine(id))
        put("cmd", "prewarm")
        put("detPath", singleLine(detPath))
        put("recPath", singleLine(recPath))
        if (dictPath.isNotBlank()) put("dictPath", singleLine(dictPath))
        put("threads", threads.coerceIn(1, 6))
    }.toString()

    fun buildRecognizeJson(
        id: String,
        imagePath: String,
        detPath: String,
        recPath: String,
        dictPath: String = "",
        detMaxSide: Int = 1024,
        threads: Int = 4,
        skipBoxes: IntArray? = null
    ): String = buildJsonObject {
        put("id", singleLine(id))
        put("cmd", "recognize")
        put("imagePath", singleLine(imagePath))
        put("detPath", singleLine(detPath))
        put("recPath", singleLine(recPath))
        if (dictPath.isNotBlank()) put("dictPath", singleLine(dictPath))
        put("detMaxSide", detMaxSide.coerceIn(256, 1280))
        put("threads", threads.coerceIn(1, 6))
        if (skipBoxes != null && skipBoxes.size >= 4) {
            put("skipBoxes", buildJsonArray { skipBoxes.forEach { add(it) } })
        }
    }.toString()

    fun parseResponseJson(
        rawLine: String,
        frameWidth: Int,
        frameHeight: Int,
        originalWidth: Int,
        originalHeight: Int
    ): ParsedReply {
        require(rawLine.toByteArray(Charsets.UTF_8).size <= MAX_REPLY_BYTES) { "原生 OCR 回复过大" }
        val root = Json.parseToJsonElement(rawLine).jsonObject
        val id = root.str("id")
        val ok = (root["ok"] as? JsonPrimitive)?.booleanOrNull ?: false
        val error = root.str("error")
        val elapsedMs = ((root["elapsedMs"] as? JsonPrimitive)?.longOrNull ?: 0L).coerceAtLeast(0L)
        val engine = root.str("engine", "arm64-cpu")
        if (!ok) {
            return ParsedReply(id, false, error.ifBlank { "原生 OCR 执行失败" }, elapsedMs, engine, emptyList())
        }
        val rows = root["blocks"] as? JsonArray ?: return ParsedReply(id, true, "", elapsedMs, engine, emptyList())
        require(rows.size <= 512) { "原生 OCR 返回文字块过多" }
        val blocks = rows.mapIndexedNotNull { idx, elem ->
            val row = elem as? JsonObject ?: return@mapIndexedNotNull null
            val rawText = row.str("text")
            val leadTrim = rawText.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
            val text = rawText.trim().take(8192).takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
            val rect = OcrExtensionGeometry.rescaleRect(
                row.intVal("left"),
                row.intVal("top"),
                row.intVal("right"),
                row.intVal("bottom"),
                frameWidth,
                frameHeight,
                originalWidth,
                originalHeight
            ) ?: return@mapIndexedNotNull null
            val rawSpans = row["s"] as? JsonArray
            val parsedSpans = if (rawSpans != null && rawSpans.size >= 6) {
                buildList {
                    var i = 0
                    while (i + 5 < rawSpans.size) {
                        val sIdx = (rawSpans.intAt(i) - leadTrim).coerceIn(0, text.length)
                        val eIdx = (rawSpans.intAt(i + 1) - leadTrim).coerceIn(sIdx, text.length)
                        if (eIdx > sIdx) {
                            OcrExtensionGeometry.rescaleRect(
                                rawSpans.intAt(i + 2),
                                rawSpans.intAt(i + 3),
                                rawSpans.intAt(i + 4),
                                rawSpans.intAt(i + 5),
                                frameWidth,
                                frameHeight,
                                originalWidth,
                                originalHeight
                            )?.let { sr -> add(TextCharSpan(sIdx, eIdx, sr.left, sr.top, sr.right, sr.bottom)) }
                        }
                        i += 6
                    }
                }
            } else {
                emptyList()
            }
            ScreenTextBlock(
                id = "native_$idx",
                text = text,
                leftPx = rect.left,
                topPx = rect.top,
                rightPx = rect.right,
                bottomPx = rect.bottom,
                sourceTag = "原生加速 OCR",
                charSpans = parsedSpans
            )
        }
        return ParsedReply(id, true, "", elapsedMs, engine, blocks)
    }

    private fun JsonObject.str(key: String, default: String = ""): String =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: default

    private fun JsonObject.intVal(key: String): Int =
        (this[key] as? JsonPrimitive)?.intOrNull ?: 0

    private fun JsonArray.intAt(idx: Int): Int =
        (this[idx] as? JsonPrimitive)?.intOrNull ?: 0

    private fun singleLine(v: String): String {
        require('\n' !in v && '\r' !in v) { "Single-line wire argument violation" }
        return v
    }
}

/**
 * Coordinates the ARM64 `gmo-ocr-native` daemon over Root (`su -c` persistent daemon + `runBoundedCommand`
 * one-shot fallback) or Shizuku `ShizukuPrivilegedService`.
 */
internal object NativeOcrRunner {
    const val MAX_FRAME_SIDE_PX = 1440
    const val MAX_FRAME_PIXELS = 1_650_000L
    private const val ROOT_STAGE_DIR = "/data/local/tmp/gmo-ocr-root-v1"
    private val SHA256_RE = Regex("^[a-f0-9]{64}$")
    private val SAFE_PATH_RE = Regex("^[A-Za-z0-9_./:-]{1,256}$")

    private val gate = Mutex()
    private val prewarmScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ioPool = Executors.newFixedThreadPool(2) { r ->
        Thread(r, "gmo-native-ocr-io").apply { isDaemon = true }
    }
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    private var rootProcess: Process? = null
    private var rootWriter: BufferedWriter? = null
    private var rootReader: BufferedReader? = null
    private var rootEpoch: Long = -1L
    private var rootRevokeHandle: Closeable? = null
    private var rootDiagSnapshot: (() -> String)? = null
    private var stagedEpoch: Long = -1L
    private var stagedStamp: String = ""
    private var idleShutdownRunnable: Runnable? = null

    @Volatile
    var lastDiagnostic: String = ""
        private set

    data class NativeOcrRunResult(val blocks: List<ScreenTextBlock>, val engineUsed: String, val elapsedMs: Long)

    fun computeFrameDimensions(srcW: Int, srcH: Int): Pair<Int, Int> =
        OcrExtensionGeometry.computeScaledDimensions(srcW, srcH, MAX_FRAME_SIDE_PX, MAX_FRAME_PIXELS)

    fun encodeSkipBoxes(
        blocks: List<ScreenTextBlock>,
        srcWidth: Int,
        srcHeight: Int,
        frameWidth: Int,
        frameHeight: Int
    ): IntArray? {
        if (blocks.isEmpty() || srcWidth <= 0 || srcHeight <= 0 || frameWidth <= 0 || frameHeight <= 0) return null
        val sx = frameWidth.toFloat() / srcWidth
        val sy = frameHeight.toFloat() / srcHeight
        val coords = ArrayList<Int>(minOf(blocks.size, 96) * 4)
        for (b in blocks) {
            if (coords.size >= 384) break
            val l = (b.leftPx * sx).toInt().coerceIn(0, frameWidth - 2)
            val t = (b.topPx * sy).toInt().coerceIn(0, frameHeight - 2)
            coords.add(l)
            coords.add(t)
            coords.add((b.rightPx * sx).toInt().coerceIn(l + 2, frameWidth))
            coords.add((b.bottomPx * sy).toInt().coerceIn(t + 2, frameHeight))
        }
        return if (coords.isEmpty()) null else coords.toIntArray()
    }

    fun isAvailable(context: Context, allowRootAutoConnect: Boolean): Boolean {
        if (OcrModelStore(context.applicationContext).resolveVerifiedNativeBinaries() == null) return false
        val rootStatus = UserRootSession.status.value
        val rootPossible = rootStatus == RootStatus.Granted || (allowRootAutoConnect && rootStatus == RootStatus.NotRequested)
        return rootPossible || ScreenCaptureHub.shizukuAuthorized()
    }

    fun prewarmAsync(context: Context, config: ScreenAssistantConfig, allowRootAutoConnect: Boolean) {
        if (gate.isLocked) return
        val app = context.applicationContext
        prewarmScope.launch {
            runCatching {
                val store = OcrModelStore(app)
                val bins = store.resolveVerifiedNativeBinaries() ?: return@launch
                val packId = listOf(config.wasmOcrPackId, config.ppOcrLangPack)
                    .firstOrNull { it.isNotBlank() && store.isModelReady(it) }
                    ?: store.firstReadyModelId()
                    ?: return@launch
                val routes = store.resolveModelRoutes(packId) ?: return@launch
                if (!gate.tryLock()) return@launch
                try {
                    withTimeoutOrNull(8_000L) {
                        val priv = resolvePrivilege(allowRootAutoConnect) ?: return@withTimeoutOrNull
                        if (priv == PrivMode.ROOT) {
                            runRootRpc(
                                bins,
                                NativeOcrWireCodec.buildPrewarmJson(
                                    UUID.randomUUID().toString(),
                                    routes.detFile.absolutePath,
                                    routes.recFile.absolutePath,
                                    routes.dictFile?.absolutePath.orEmpty()
                                ),
                                6_000L
                            )
                        } else {
                            val synced = ensureShizukuAssets(app, bins, routes) ?: return@withTimeoutOrNull
                            ScreenCaptureHub.shizukuOcrRpc(
                                app,
                                null,
                                NativeOcrWireCodec.buildPrewarmJson(
                                    UUID.randomUUID().toString(),
                                    synced.first,
                                    synced.second,
                                    synced.third
                                )
                            )
                        }
                        scheduleIdleShutdown()
                    }
                } finally {
                    gate.unlock()
                }
            }
        }
    }

    suspend fun recognize(
        context: Context,
        bitmap: Bitmap,
        packId: String,
        skipNativeBlocks: List<ScreenTextBlock> = emptyList(),
        allowRootAutoConnect: Boolean = false
    ): NativeOcrRunResult? {
        lastDiagnostic = ""
        if (bitmap.isRecycled || !OcrModelStore.validModelId(packId)) {
            lastDiagnostic = "无效输入图像或模型 ID"
            return null
        }
        val app = context.applicationContext
        val (bins, routes) = withContext(Dispatchers.IO) {
            val store = OcrModelStore(app)
            store.resolveVerifiedNativeBinaries() to store.resolveModelRoutes(packId)
        }
        if (bins == null) {
            lastDiagnostic = "ARM64 原生加速包未安装或校验失败"
            return null
        }
        if (routes == null) {
            lastDiagnostic = "未检测到已就绪的模型 ($packId)"
            return null
        }

        return gate.withLock {
            var activePriv = resolvePrivilege(allowRootAutoConnect) ?: run {
                lastDiagnostic = "Root/Shizuku 均未就绪(Root=${UserRootSession.status.value})"
                return@withLock null
            }
            val (frameW, frameH) = computeFrameDimensions(bitmap.width, bitmap.height)
            val skipBoxes = encodeSkipBoxes(skipNativeBlocks, bitmap.width, bitmap.height, frameW, frameH)
            val reqFile = File(app.cacheDir, "gmo_native_req_${UUID.randomUUID()}.raw")
            try {
                withContext(Dispatchers.IO) {
                    val target = if (frameW == bitmap.width && frameH == bitmap.height) {
                        bitmap
                    } else {
                        Bitmap.createScaledBitmap(bitmap, frameW, frameH, true)
                    }
                    try {
                        val px = IntArray(frameW * frameH).also {
                            target.getPixels(it, 0, frameW, 0, 0, frameW, frameH)
                        }
                        reqFile.outputStream().buffered(65536).use {
                            NativeOcrWireCodec.encodeGmorRgb24(px, frameW, frameH, it)
                        }
                        reqFile.setReadable(true, false)
                    } finally {
                        if (target !== bitmap && !target.isRecycled) target.recycle()
                    }
                }
                val reqId = UUID.randomUUID().toString()
                var shizukuRoutes: Triple<String, String, String>? = null
                if (activePriv == PrivMode.SHIZUKU) {
                    shizukuRoutes = withTimeoutOrNull(45_000L) {
                        ensureShizukuAssets(app, bins, routes)
                    } ?: run {
                        if (lastDiagnostic.isBlank()) lastDiagnostic = "Shizuku 同步 OCR 模型超时或失败"
                        return@withLock null
                    }
                }
                val replyLine = withTimeoutOrNull(18_000L) {
                    if (activePriv == PrivMode.ROOT) {
                        val reqJson = NativeOcrWireCodec.buildRecognizeJson(
                            reqId,
                            reqFile.absolutePath,
                            routes.detFile.absolutePath,
                            routes.recFile.absolutePath,
                            routes.dictFile?.absolutePath.orEmpty(),
                            detMaxSide = 1024,
                            skipBoxes = skipBoxes
                        )
                        runRootRpc(bins, reqJson, 12_000L) ?: if (!lastDiagnostic.contains("超时") && ScreenCaptureHub.shizukuAuthorized()) {
                            val rootDiag = lastDiagnostic.ifBlank { "Root 原生守护进程无响应" }
                            val sp = withTimeoutOrNull(25_000L) { ensureShizukuAssets(app, bins, routes) }
                            if (sp != null) {
                                activePriv = PrivMode.SHIZUKU
                                val (sr, sd) = ScreenCaptureHub.shizukuOcrRpc(
                                    app,
                                    reqFile,
                                    NativeOcrWireCodec.buildRecognizeJson(
                                        reqId,
                                        ScreenCaptureHub.shizukuWorkReqPath(),
                                        sp.first,
                                        sp.second,
                                        sp.third,
                                        detMaxSide = 1024,
                                        skipBoxes = skipBoxes
                                    )
                                )
                                if (sr == null) lastDiagnostic = "$rootDiag；$sd"
                                sr
                            } else {
                                lastDiagnostic = "$rootDiag；Shizuku 资源同步失败"
                                null
                            }
                        } else {
                            null
                        }
                    } else {
                        val sp = shizukuRoutes ?: return@withTimeoutOrNull null
                        val (sr, sd) = ScreenCaptureHub.shizukuOcrRpc(
                            app,
                            reqFile,
                            NativeOcrWireCodec.buildRecognizeJson(
                                reqId,
                                ScreenCaptureHub.shizukuWorkReqPath(),
                                sp.first,
                                sp.second,
                                sp.third,
                                detMaxSide = 1024,
                                skipBoxes = skipBoxes
                            )
                        )
                        if (sr == null && sd.isNotBlank()) lastDiagnostic = sd
                        sr
                    }
                } ?: run {
                    if (lastDiagnostic.isBlank()) lastDiagnostic = "原生 OCR 识别超时（18s）"
                    return@withLock null
                }

                val parsed = withContext(Dispatchers.Default) {
                    NativeOcrWireCodec.parseResponseJson(replyLine, frameW, frameH, bitmap.width, bitmap.height)
                }
                if (parsed.id != reqId) {
                    if (activePriv == PrivMode.ROOT) destroyRootDaemon() else ScreenCaptureHub.shutdownShizuku()
                    lastDiagnostic = "原生 OCR 响应序号不匹配"
                    return@withLock null
                }
                if (!parsed.ok) {
                    lastDiagnostic = "原生引擎错误: ${parsed.error}"
                    return@withLock null
                }
                scheduleIdleShutdown()
                NativeOcrRunResult(
                    ScreenTextSelectionPolicy.inReadingOrder(parsed.blocks),
                    "原生加速 OCR (${parsed.engine}, ${parsed.elapsedMs}ms)",
                    parsed.elapsedMs
                )
            } finally {
                withContext(NonCancellable + Dispatchers.IO) { reqFile.delete() }
            }
        }
    }

    fun shutdown() {
        cancelIdleShutdown()
        destroyRootDaemon()
        ScreenCaptureHub.shutdownShizuku()
    }

    private enum class PrivMode { ROOT, SHIZUKU }

    private suspend fun resolvePrivilege(allowRootAutoConnect: Boolean): PrivMode? {
        if (UserRootSession.authorizedEpoch() != null) return PrivMode.ROOT
        if (allowRootAutoConnect && ScreenCaptureHub.ensureRootAutoConnectedIfEnabled(true)) return PrivMode.ROOT
        if (ScreenCaptureHub.shizukuAuthorized()) return PrivMode.SHIZUKU
        return null
    }

    internal fun handshakeRootDaemon(
        reader: BufferedReader,
        writer: BufferedWriter,
        diagSnapshot: () -> String
    ): String? {
        writer.write("{\"id\":\"boot\",\"cmd\":\"ping\"}\n")
        writer.flush()
        val pong = reader.readLine() ?: return diagSnapshot().take(180).let {
            if (it.isNotBlank()) "守护进程退出(EOF): $it" else "守护进程无响应(EOF，可能缺依赖或被SELinux拒绝)"
        }
        if (!pong.contains("\"pong\"") && !pong.contains("\"ok\":true") && !pong.contains("\"ok\": true")) {
            return "握手返回异常: ${pong.take(120)}"
        }
        return null
    }

    private suspend fun ensureRootStaged(
        bins: OcrModelStore.VerifiedNativeBinaries,
        epoch: Long,
        forceRestage: Boolean
    ): Boolean {
        val stamp = "v2:${bins.execSha256}:${bins.ortSha256}"
        require(SHA256_RE.matches(bins.execSha256) && SHA256_RE.matches(bins.ortSha256))
        require(SAFE_PATH_RE.matches(bins.execFile.absolutePath) && SAFE_PATH_RE.matches(bins.ortLibFile.absolutePath))
        if (!forceRestage && stagedEpoch == epoch && stagedStamp == stamp) return true
        val stageCmd = "mkdir -p $ROOT_STAGE_DIR && chmod 0755 $ROOT_STAGE_DIR && " +
            "s=\$(cat $ROOT_STAGE_DIR/.stamp 2>/dev/null || true); " +
            "if [ \"\$s\" != '$stamp' ] || [ ! -x $ROOT_STAGE_DIR/gmo-ocr-native ] || [ ! -x $ROOT_STAGE_DIR/libonnxruntime.so ]; then " +
            "cp -f '${bins.execFile.absolutePath}' $ROOT_STAGE_DIR/gmo-ocr-native && " +
            "cp -f '${bins.ortLibFile.absolutePath}' $ROOT_STAGE_DIR/libonnxruntime.so && " +
            "chmod 0755 $ROOT_STAGE_DIR/gmo-ocr-native $ROOT_STAGE_DIR/libonnxruntime.so && " +
            "(chcon u:object_r:shell_data_file:s0 $ROOT_STAGE_DIR $ROOT_STAGE_DIR/gmo-ocr-native $ROOT_STAGE_DIR/libonnxruntime.so 2>/dev/null || true) && " +
            "printf '%s' '$stamp' > $ROOT_STAGE_DIR/.stamp; fi"
        val res = UserRootSession.runBoundedCommand(stageCmd, 4096, 8_000L)
        if (!res.leaseCurrent || res.timedOut || res.exitCode != 0) {
            stagedEpoch = -1L
            stagedStamp = ""
            return false
        }
        stagedEpoch = epoch
        stagedStamp = stamp
        return true
    }

    private suspend fun ensureRootDaemonConnected(
        bins: OcrModelStore.VerifiedNativeBinaries,
        currentEpoch: Long
    ): Boolean {
        if (rootProcess != null && rootEpoch == currentEpoch && isAlive(rootProcess)) return true
        val errors = mutableListOf<String>()
        for (attemptIdx in 0..1) {
            val useInteractive = attemptIdx == 0
            destroyRootDaemon()
            if (!ensureRootStaged(bins, currentEpoch, forceRestage = !useInteractive)) {
                errors += "stage 失败"
                continue
            }
            val proc = if (useInteractive) {
                UserRootSession.openInteractiveSuProcess(currentEpoch)
            } else {
                val daemonCmd = "cd $ROOT_STAGE_DIR && export LD_LIBRARY_PATH=$ROOT_STAGE_DIR && exec $ROOT_STAGE_DIR/gmo-ocr-native || exit 79"
                UserRootSession.openDirectSuProcess(daemonCmd, currentEpoch)
            }
            if (proc == null) {
                errors += if (useInteractive) "su 启动返回空" else "su -c 启动返回空"
                continue
            }
            val stderrBuf = StringBuilder(256)
            val stderrReaderRef = AtomicReference<Thread?>(null)
            val t = Thread({
                runCatching {
                    BufferedReader(InputStreamReader(proc.errorStream, Charsets.UTF_8)).use { er ->
                        val buf = CharArray(256)
                        while (true) {
                            val n = er.read(buf)
                            if (n < 0) break
                            synchronized(stderrBuf) {
                                if (stderrBuf.length < 512) stderrBuf.append(buf, 0, minOf(n, 512 - stderrBuf.length))
                            }
                        }
                    }
                }
            }, "gmo-root-ocr-err").apply {
                isDaemon = true
                start()
            }
            stderrReaderRef.set(t)
            val diagSnap = {
                synchronized(stderrBuf) { stderrBuf.toString().replace('\n', ' ').trim() }
            }
            val writer = BufferedWriter(OutputStreamWriter(proc.outputStream, Charsets.UTF_8))
            val reader = BufferedReader(InputStreamReader(proc.inputStream, Charsets.UTF_8))
            val err = withContext(Dispatchers.IO) {
                val fut = ioPool.submit(Callable<String?> {
                    if (useInteractive) {
                        writer.write(
                            "cd $ROOT_STAGE_DIR\n" +
                                "export LD_LIBRARY_PATH=$ROOT_STAGE_DIR\n" +
                                "printf '__sh__\\n'\n" +
                                "exec $ROOT_STAGE_DIR/gmo-ocr-native\n"
                        )
                        writer.flush()
                        var ready = false
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.trim() == "__sh__") {
                                ready = true
                                break
                            }
                        }
                        if (!ready) {
                            val snap = diagSnap().take(180)
                            return@Callable if (snap.isNotBlank()) "交互shell退出: $snap" else "交互shell无响应(EOF)"
                        }
                    }
                    handshakeRootDaemon(reader, writer, diagSnap)
                })
                try {
                    fut.get(4_000L, TimeUnit.MILLISECONDS)
                } catch (c: CancellationException) {
                    runCatching { proc.destroyForcibly() }
                    fut.cancel(true)
                    throw c
                } catch (ex: Exception) {
                    runCatching { proc.destroyForcibly() }
                    fut.cancel(true)
                    val snap = diagSnap().take(160)
                    "握手异常(${ex.message ?: ex.javaClass.simpleName})${if (snap.isNotBlank()) ": $snap" else ""}"
                }
            }
            if (err == null && isAlive(proc)) {
                rootEpoch = currentEpoch
                rootProcess = proc
                rootWriter = writer
                rootReader = reader
                rootDiagSnapshot = diagSnap
                rootRevokeHandle?.let { runCatching { it.close() } }
                rootRevokeHandle = UserRootSession.registerRevocationListener {
                    stagedEpoch = -1L
                    stagedStamp = ""
                    destroyRootDaemon()
                }
                return true
            }
            val snap = diagSnap().take(160)
            val label = if (useInteractive) "su" else "su -c"
            errors += "$label: ${err ?: "进程已退出"}${if (snap.isNotBlank()) " [$snap]" else ""}"
            runCatching { proc.destroyForcibly() }
        }
        lastDiagnostic = "Root守护进程连接失败[${errors.joinToString(" | ")}]"
        return false
    }

    private suspend fun runRootRpc(
        bins: OcrModelStore.VerifiedNativeBinaries,
        jsonLine: String,
        timeoutMs: Long
    ): String? {
        var rpcTimedOut = false
        for (attempt in 0..1) {
            val currentEpoch = UserRootSession.authorizedEpoch() ?: run {
                destroyRootDaemon()
                lastDiagnostic = "Root会话未授权(${UserRootSession.status.value})"
                return null
            }
            val hadLive = rootProcess != null && rootEpoch == currentEpoch && isAlive(rootProcess)
            if (ensureRootDaemonConnected(bins, currentEpoch)) {
                val writer = rootWriter
                val reader = rootReader
                val diagSnap = rootDiagSnapshot
                if (writer != null && reader != null) {
                    val reply = withContext(Dispatchers.IO) {
                        val fut = ioPool.submit(Callable<String?> {
                            writer.write(jsonLine)
                            writer.write("\n")
                            writer.flush()
                            reader.readLine()
                        })
                        try {
                            fut.get(timeoutMs, TimeUnit.MILLISECONDS)
                        } catch (c: CancellationException) {
                            fut.cancel(true)
                            destroyRootDaemon()
                            throw c
                        } catch (_: TimeoutException) {
                            fut.cancel(true)
                            rpcTimedOut = true
                            val errTail = diagSnap?.invoke().orEmpty().take(120)
                            lastDiagnostic = "Root 原生引擎推理超时(${timeoutMs / 1000}s)${if (errTail.isNotBlank()) ": $errTail" else ""}"
                            destroyRootDaemon()
                            null
                        } catch (ex: Exception) {
                            fut.cancel(true)
                            lastDiagnostic = "Root RPC 异常(${ex.message}): ${diagSnap?.invoke().orEmpty().take(160)}"
                            destroyRootDaemon()
                            null
                        }
                    }
                    if (reply != null) return reply
                    if (rpcTimedOut) return null
                }
            }
            destroyRootDaemon()
            if (!hadLive || rpcTimedOut) break
        }
        val daemonDiag = lastDiagnostic
        val currentEpoch = UserRootSession.authorizedEpoch() ?: return null
        if (!ensureRootStaged(bins, currentEpoch, forceRestage = false)) return null
        val quotedReq = "'" + jsonLine.replace("'", "'\"'\"'") + "'"
        val cmd = "cd $ROOT_STAGE_DIR && export LD_LIBRARY_PATH=$ROOT_STAGE_DIR && " +
            "{ printf '%s\\n' $quotedReq; printf '{\"id\":\"bye\",\"cmd\":\"quit\"}\\n'; } | " +
            "$ROOT_STAGE_DIR/gmo-ocr-native 2>&1"
        val res = UserRootSession.runBoundedCommand(cmd, 256 * 1024, timeoutMs, preserveSessionOnTimeout = true)
        val outText = res.bytes.toString(Charsets.UTF_8)
        val jsonLineOut = outText.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("{") && it.endsWith("}") }
        if (jsonLineOut != null) return jsonLineOut
        val oneShotDiag = res.diagnostic.ifBlank { outText.trim().take(200) }
        lastDiagnostic = listOf(daemonDiag, oneShotDiag).filter { it.isNotBlank() }.distinct().joinToString(" | ")
        return null
    }

    private suspend fun ensureShizukuAssets(
        app: Context,
        bins: OcrModelStore.VerifiedNativeBinaries,
        routes: OcrModelStore.ModelRoutes
    ): Triple<String, String, String>? {
        if (!syncIfNeeded(app, "", "gmo-ocr-native", bins.execSha256, true, bins.execFile)) return null
        if (!syncIfNeeded(app, "", "libonnxruntime.so", bins.ortSha256, false, bins.ortLibFile)) return null
        val detName = "det_${routes.pack.detAsset.sha256.take(12)}.onnx"
        val recName = "rec_${routes.pack.recAsset.sha256.take(12)}.onnx"
        if (!syncIfNeeded(app, "models", detName, routes.pack.detAsset.sha256, false, routes.detFile)) return null
        if (!syncIfNeeded(app, "models", recName, routes.pack.recAsset.sha256, false, routes.recFile)) return null
        val modelsBase = ScreenCaptureHub.shizukuWorkModelsDir()
        val dictRemote = if (routes.dictFile != null && routes.pack.dictAsset != null) {
            val dictName = "dict_${routes.pack.dictAsset.sha256.take(12)}.txt"
            if (!syncIfNeeded(app, "models", dictName, routes.pack.dictAsset.sha256, false, routes.dictFile)) return null
            "$modelsBase/$dictName"
        } else {
            ""
        }
        return Triple("$modelsBase/$detName", "$modelsBase/$recName", dictRemote)
    }

    private suspend fun syncIfNeeded(
        app: Context,
        subDir: String,
        name: String,
        sha256: String,
        exec: Boolean,
        file: File
    ): Boolean = ScreenCaptureHub.shizukuOcrHasFile(app, subDir, name, sha256) ||
        ScreenCaptureHub.shizukuOcrSyncFile(app, subDir, name, sha256, exec, file)

    private fun destroyRootDaemon() {
        runCatching {
            rootWriter?.write("{\"id\":\"bye\",\"cmd\":\"quit\"}\n")
            rootWriter?.flush()
        }
        runCatching { rootProcess?.destroyForcibly() }
        rootProcess = null
        rootWriter = null
        rootReader = null
        rootEpoch = -1L
        rootDiagSnapshot = null
        rootRevokeHandle?.let { runCatching { it.close() } }
        rootRevokeHandle = null
    }

    private fun isAlive(p: Process?): Boolean = p != null && try {
        p.exitValue()
        false
    } catch (_: IllegalThreadStateException) {
        true
    }

    private fun cancelIdleShutdown() {
        idleShutdownRunnable?.let { mainHandler.removeCallbacks(it) }
        idleShutdownRunnable = null
    }

    private fun scheduleIdleShutdown() {
        mainHandler.post {
            cancelIdleShutdown()
            val t = Runnable { shutdown() }
            idleShutdownRunnable = t
            mainHandler.postDelayed(t, 180_000L)
        }
    }
}
