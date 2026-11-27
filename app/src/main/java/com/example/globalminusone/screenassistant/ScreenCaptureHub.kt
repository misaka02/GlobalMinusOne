package com.example.globalminusone.screenassistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityNodeInfo
import com.example.globalminusone.system.privilege.RootStatus
import com.example.globalminusone.system.privilege.ShizukuPermissions
import com.example.globalminusone.system.privilege.ShizukuUserServiceClient
import com.example.globalminusone.system.privilege.UserRootSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class AccessibilityScreenSnapshot(
    val textNodes: List<ScreenTextBlock>,
    val mediaNodes: List<ExtractedSubImageCandidate>,
    val overlayObstructions: List<ExtractedSubImageCandidate> = emptyList(),
    val foregroundPackage: String = ""
)

internal data class PrivilegedDisplayCapture(
    val bitmap: Bitmap,
    val snapshot: AccessibilityScreenSnapshot
)

internal suspend fun <T> awaitScreenCaptureResult(
    discard: (T) -> Unit,
    start: ((T) -> Unit) -> Unit
): T {
    val lock = Any()
    var cancelled = false
    var delivered = false
    var pending: T? = null
    var hasPending = false
    fun discardPending() {
        val (found, value) = synchronized(lock) {
            if (!hasPending) false to null
            else { val v = pending; pending = null; hasPending = false; true to v }
        }
        @Suppress("UNCHECKED_CAST")
        if (found) discard(value as T)
    }
    return try {
        suspendCancellableCoroutine<T> { continuation ->
            continuation.invokeOnCancellation {
                synchronized(lock) { cancelled = true }
                discardPending()
            }
            start { result ->
                val resumeNow = synchronized(lock) {
                    if (cancelled || delivered || !continuation.isActive) false
                    else { delivered = true; pending = result; hasPending = true; true }
                }
                if (resumeNow) continuation.resume(result) else discard(result)
            }
        }.also { synchronized(lock) { pending = null; hasPending = false } }
    } finally {
        discardPending()
    }
}

/**
 * Unified Shizuku + Root capture and true-bounds view-hierarchy snapshot hub:
 * - Concurrent lossless screencap + in-process UiAutomation true-bounds tree;
 * - Cross-window Accessibility snapshot fallback;
 * - Direct Shizuku UserService bridge for native ARM64 OCR daemon IPC.
 */
internal object ScreenCaptureHub {
    const val USER_SERVICE_VERSION = 10

    private const val SHIZUKU_WORK_DIR = "/data/local/tmp/gmo-ocr-v1"
    private val PACKAGE_REGEX = Regex("^[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)+$")
    private val shizukuGate = Mutex()
    private val shizukuOcrGate = Mutex()
    private val pipePool = Executors.newCachedThreadPool { r ->
        Thread(r, "gmo-shizuku-pipe").apply { isDaemon = true }
    }
    private val shizukuClient = ShizukuUserServiceClient(
        serviceClassName = ShizukuPrivilegedService::class.java.name,
        tag = "screen-assistant",
        processSuffix = "privileged",
        version = USER_SERVICE_VERSION,
        connectTimeoutMs = 4_500L,
        failureCooldownMs = 3_500L
    )

    fun isSafeInspectablePackage(pkg: String): Boolean {
        val clean = pkg.trim()
        return clean.length in 3..128 &&
            PACKAGE_REGEX.matches(clean) &&
            clean != "com.example.globalminusone" &&
            clean != "com.android.systemui"
    }

    fun shizukuAvailable(): Boolean = ShizukuPermissions.isServiceAvailable()

    fun shizukuAuthorized(): Boolean = ShizukuPermissions.isAuthorized()

    fun clearShizukuCooldown() {
        shizukuClient.clearCooldown()
    }

    suspend fun requestShizukuAuthorization(): Boolean = ShizukuPermissions.requestAuthorization(25_000L)

    suspend fun ensureRootAutoConnectedIfEnabled(allowRootAutoConnect: Boolean): Boolean {
        if (UserRootSession.authorizedEpoch() != null) return true
        if (!allowRootAutoConnect) return false
        val status = UserRootSession.status.value
        if (status == RootStatus.Denied || status == RootStatus.Unavailable) return false
        return UserRootSession.connectForScreenRead(allowConfiguredConnect = true)
    }

    suspend fun captureDisplay(
        context: Context,
        baseSnapshot: AccessibilityScreenSnapshot,
        extraObstructions: List<ExtractedSubImageCandidate>,
        allowRootPrompt: Boolean
    ): PrivilegedDisplayCapture? = coroutineScope {
        val (winW, winH) = displayDimensions(context)
        val useRoot = ensureRootAutoConnectedIfEnabled(allowRootPrompt)
        if (useRoot && UserRootSession.authorizedEpoch() != null) {
            val pngDef = async(Dispatchers.IO) { UserRootSession.captureScreenPng() }
            val uiDef = async(Dispatchers.IO) { captureUiTreeSnapshot(context, winW, winH) }
            val png = pngDef.await()
            if (png != null) decodeAndMergeCapture(png, uiDef, baseSnapshot, extraObstructions)?.let {
                return@coroutineScope it
            }
        }
        if (shizukuAuthorized()) {
            val pngDef = async(Dispatchers.IO) {
                transactPipe(context, ShizukuPrivilegedService.OP_CAPTURE_SCREEN, ShizukuPrivilegedService.MAX_CAPTURE_BYTES)
            }
            val uiDef = async(Dispatchers.IO) { captureUiTreeSnapshot(context, winW, winH) }
            val png = pngDef.await()
            if (png != null && png.size >= 8) {
                decodeAndMergeCapture(png, uiDef, baseSnapshot, extraObstructions)?.let {
                    return@coroutineScope it
                }
            }
        }
        null
    }

    private suspend fun decodeAndMergeCapture(
        png: ByteArray,
        uiDef: Deferred<AccessibilityScreenSnapshot?>,
        baseSnapshot: AccessibilityScreenSnapshot,
        extraObstructions: List<ExtractedSubImageCandidate>
    ): PrivilegedDisplayCapture? {
        val bmp = withContext(Dispatchers.Default) {
            BitmapFactory.decodeByteArray(
                png, 0, png.size, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            )
        } ?: return null
        var transferred = false
        return try {
            val uiSnap = withTimeoutOrNull(1_600L) { uiDef.await() }
            val merged = mergeSnapshots(
                primary = uiSnap ?: AccessibilityScreenSnapshot(emptyList(), emptyList()),
                uiDump = baseSnapshot,
                extraObstructions = extraObstructions
            )
            PrivilegedDisplayCapture(bmp, merged).also { transferred = true }
        } finally {
            if (!transferred && !bmp.isRecycled) bmp.recycle()
        }
    }

    suspend fun captureUiTreeSnapshot(
        context: Context,
        screenW: Int,
        screenH: Int,
        allowRootAutoConnect: Boolean = false
    ): AccessibilityScreenSnapshot? {
        if (shizukuAuthorized()) {
            val bytes = transactPipe(context, ShizukuPrivilegedService.OP_DUMP_UI_TREE, 4 * 1024 * 1024)
            val raw = bytes?.toString(Charsets.UTF_8)
            if (!raw.isNullOrBlank()) {
                val snap = parseUiTree(raw, screenW, screenH)
                if (snap.textNodes.isNotEmpty() || snap.mediaNodes.isNotEmpty() || isSafeInspectablePackage(snap.foregroundPackage)) {
                    return snap
                }
            }
        }
        val rootOk = UserRootSession.authorizedEpoch() != null ||
            (allowRootAutoConnect && ensureRootAutoConnectedIfEnabled(true))
        if (rootOk) {
            val xml = UserRootSession.dumpUiHierarchyXml()
            if (!xml.isNullOrBlank()) return parseXml(xml, screenW, screenH)
        }
        return null
    }

    suspend fun captureXmlSnapshot(
        context: Context,
        screenW: Int,
        screenH: Int
    ): AccessibilityScreenSnapshot? {
        if (UserRootSession.authorizedEpoch() != null) {
            val xml = UserRootSession.dumpUiHierarchyXml()
            if (!xml.isNullOrBlank()) return parseXml(xml, screenW, screenH)
        }
        if (shizukuAuthorized()) {
            val bytes = transactPipe(context, ShizukuPrivilegedService.OP_DUMP_XML, 4 * 1024 * 1024)
            val xml = bytes?.toString(Charsets.UTF_8)
            if (!xml.isNullOrBlank() && (xml.contains("<hierarchy") || xml.contains("<node"))) {
                return parseXml(xml, screenW, screenH)
            }
        }
        return null
    }

    suspend fun shizukuOcrHasFile(context: Context, subDir: String, name: String, sha256: String): Boolean {
        val remote = connectShizuku(context.applicationContext) ?: return false
        return withContext(Dispatchers.IO) {
            val d = Parcel.obtain()
            val r = Parcel.obtain()
            try {
                d.writeInterfaceToken(ShizukuPrivilegedService.DESCRIPTOR)
                d.writeInt(ShizukuPrivilegedService.OP_OCR_HAS_FILE)
                d.writeString(subDir)
                d.writeString(name)
                d.writeString(sha256)
                remote.transact(IBinder.FIRST_CALL_TRANSACTION, d, r, 0) && run {
                    r.readException()
                    r.readInt() == 1
                }
            } catch (_: Exception) {
                false
            } finally {
                d.recycle()
                r.recycle()
            }
        }
    }

    suspend fun shizukuOcrSyncFile(
        context: Context,
        subDir: String,
        name: String,
        sha256: String,
        executable: Boolean,
        localFile: File
    ): Boolean {
        if (sha256.length != 64 || !localFile.isFile) return false
        val remote = connectShizuku(context.applicationContext) ?: return false
        return withContext(Dispatchers.IO) {
            val pfd = runCatching { ParcelFileDescriptor.open(localFile, ParcelFileDescriptor.MODE_READ_ONLY) }.getOrNull()
                ?: return@withContext false
            val d = Parcel.obtain()
            val r = Parcel.obtain()
            try {
                d.writeInterfaceToken(ShizukuPrivilegedService.DESCRIPTOR)
                d.writeInt(ShizukuPrivilegedService.OP_OCR_SYNC_FILE)
                d.writeString(subDir)
                d.writeString(name)
                d.writeString(sha256)
                d.writeInt(if (executable) 1 else 0)
                pfd.writeToParcel(d, 0)
                check(remote.transact(IBinder.FIRST_CALL_TRANSACTION, d, r, 0))
                r.readException()
                true
            } catch (_: Exception) {
                false
            } finally {
                d.recycle()
                r.recycle()
                runCatching { pfd.close() }
            }
        }
    }

    suspend fun shizukuOcrRpc(context: Context, reqFile: File?, jsonLine: String): Pair<String?, String> {
        val remote = connectShizuku(context.applicationContext) ?: return null to "Shizuku OCR 服务连接失败"
        return shizukuOcrGate.withLock {
            withContext(Dispatchers.IO) {
                val pfd = if (reqFile != null) {
                    runCatching { ParcelFileDescriptor.open(reqFile, ParcelFileDescriptor.MODE_READ_ONLY) }.getOrNull()
                } else {
                    null
                }
                if (reqFile != null && pfd == null) return@withContext null to "无法打开 OCR 图像帧文件"
                val d = Parcel.obtain()
                val r = Parcel.obtain()
                try {
                    d.writeInterfaceToken(ShizukuPrivilegedService.DESCRIPTOR)
                    d.writeInt(ShizukuPrivilegedService.OP_OCR_RPC)
                    d.writeInt(if (pfd != null) 1 else 0)
                    if (pfd != null) pfd.writeToParcel(d, 0)
                    d.writeString(jsonLine)
                    check(remote.transact(IBinder.FIRST_CALL_TRANSACTION, d, r, 0))
                    r.readException()
                    r.readString() to ""
                } catch (ex: Exception) {
                    disconnectShizuku()
                    null to "Shizuku OCR RPC 失败(${ex.message ?: ex.javaClass.simpleName})"
                } finally {
                    d.recycle()
                    r.recycle()
                    runCatching { pfd?.close() }
                }
            }
        }
    }

    fun shizukuWorkModelsDir(): String = "$SHIZUKU_WORK_DIR/models"
    fun shizukuWorkReqPath(): String = "$SHIZUKU_WORK_DIR/req.raw"

    fun shutdownShizuku() {
        val remote = shizukuClient.peekLiveBinder()
        if (remote != null && remote.isBinderAlive) {
            runCatching {
                val d = Parcel.obtain()
                val r = Parcel.obtain()
                d.writeInterfaceToken(ShizukuPrivilegedService.DESCRIPTOR)
                d.writeInt(ShizukuPrivilegedService.OP_OCR_SHUTDOWN)
                remote.transact(IBinder.FIRST_CALL_TRANSACTION, d, r, IBinder.FLAG_ONEWAY)
                d.recycle()
                r.recycle()
            }
        }
        disconnectShizuku()
    }

    private suspend fun transactPipe(context: Context, op: Int, maxBytes: Int): ByteArray? {
        val remote = connectShizuku(context.applicationContext) ?: return null
        return shizukuGate.withLock {
            withContext(Dispatchers.IO) {
                val pipe = runCatching { ParcelFileDescriptor.createPipe() }.getOrNull() ?: return@withContext null
                val fut = CompletableFuture.supplyAsync({
                    pipe[0].use { readPfd ->
                        readBoundedStream(ParcelFileDescriptor.AutoCloseInputStream(readPfd), maxBytes)
                    }
                }, pipePool)
                val d = Parcel.obtain()
                val r = Parcel.obtain()
                try {
                    d.writeInterfaceToken(ShizukuPrivilegedService.DESCRIPTOR)
                    d.writeInt(op)
                    pipe[1].writeToParcel(d, 0)
                    runCatching { pipe[1].close() }
                    check(remote.transact(IBinder.FIRST_CALL_TRANSACTION, d, r, 0))
                    r.readException()
                    r.readInt()
                    withTimeoutOrNull(4_500L) {
                        suspendCancellableCoroutine<ByteArray?> { cont ->
                            fut.whenComplete { bytes, err ->
                                if (!cont.isActive) return@whenComplete
                                if (err != null) cont.resume(null) else cont.resume(bytes)
                            }
                        }
                    }
                } catch (_: Exception) {
                    fut.cancel(true)
                    disconnectShizuku()
                    null
                } finally {
                    d.recycle()
                    r.recycle()
                    runCatching { pipe[1].close() }
                }
            }
        }
    }

    private suspend fun connectShizuku(app: Context): IBinder? {
        if (!shizukuAuthorized()) return null
        return shizukuClient.connect(app)
    }

    private fun disconnectShizuku() = shizukuClient.close()

    private fun readBoundedStream(input: java.io.InputStream, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16384)
        var total = 0
        input.use { stream ->
            while (true) {
                val n = stream.read(buf)
                if (n < 0) break
                if (n == 0) continue
                total += n
                if (total > maxBytes) break
                out.write(buf, 0, n)
            }
        }
        return out.toByteArray()
    }

    private fun displayDimensions(context: Context): Pair<Int, Int> {
        val dm = context.resources.displayMetrics
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val b = (context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager)
                ?.currentWindowMetrics?.bounds
            if (b != null && b.width() > 0 && b.height() > 0) return b.width() to b.height()
        }
        return dm.widthPixels to dm.heightPixels
    }

    // =========================================================================
    // True-Bounds View Hierarchy Parsers (GMOU1 UiAutomation, XML, A11y)
    // =========================================================================

    internal data class RawUiNode(
        val depth: Int,
        val childCount: Int,
        val leftPx: Int,
        val topPx: Int,
        val rightPx: Int,
        val bottomPx: Int,
        val pkg: String,
        val cls: String,
        val viewId: String,
        val text: String,
        val contentDesc: String = "",
        val role: String = ""
    )

    internal fun buildSnapshotFromNodes(
        nodes: List<RawUiNode>,
        obstructions: List<ExtractedSubImageCandidate>,
        screenW: Int,
        screenH: Int
    ): AccessibilityScreenSnapshot {
        val textOut = ArrayList<ScreenTextBlock>()
        val tierA = ArrayList<ExtractedSubImageCandidate>()
        val tierB = ArrayList<ExtractedSubImageCandidate>()
        val tierCPool = ArrayList<ExtractedSubImageCandidate>()
        var bestPkg = ""
        val screenArea = if (screenW > 0 && screenH > 0) screenW.toLong() * screenH.toLong() else 0L

        for (node in nodes) {
            if (node.pkg == "com.example.globalminusone" || node.pkg == "com.android.systemui") continue
            if (bestPkg.isEmpty() && isSafeInspectablePackage(node.pkg)) bestPkg = node.pkg
            if (node.rightPx <= 0 || node.bottomPx <= 0) continue
            if (screenW > 0 && node.leftPx >= screenW) continue
            if (screenH > 0 && node.topPx >= screenH) continue

            val cl = node.leftPx.coerceAtLeast(0)
            val ct = node.topPx.coerceAtLeast(0)
            val cr = if (screenW > 0) node.rightPx.coerceAtMost(screenW) else node.rightPx
            val cb = if (screenH > 0) node.bottomPx.coerceAtMost(screenH) else node.bottomPx
            val w = cr - cl
            val h = cb - ct
            if (w <= 0 || h <= 0) continue

            val lowerCls = node.cls.lowercase()
            val cleanText = node.text.trim()
            if (cleanText.isNotEmpty() &&
                cleanText.length <= 240 &&
                w >= 8 &&
                h >= 8 &&
                (node.childCount == 0 || lowerCls.contains("textview"))
            ) {
                textOut += ScreenTextBlock(
                    id = "ui_t_${textOut.size}",
                    text = cleanText,
                    leftPx = cl,
                    topPx = ct,
                    rightPx = cr,
                    bottomPx = cb,
                    sourceTag = "界面文本节点",
                    isDirectRenderedText = true
                )
            }

            if (lowerCls.contains("textview") || lowerCls.contains("edittext")) continue
            val lowerId = node.viewId.lowercase()
            val explicitImgClass = lowerCls.contains("imageview") ||
                lowerCls.contains("photoview") ||
                lowerCls.contains("drawee") ||
                lowerCls.contains("asyncimage") ||
                lowerCls.contains("biliimage") ||
                lowerCls.contains("surfaceview") ||
                lowerCls.contains("textureview") ||
                lowerCls.contains("imagefilterview")
            val hasMediaId = lowerId.contains("cover") ||
                lowerId.contains("thumb") ||
                lowerId.contains("poster") ||
                lowerId.contains("image") ||
                lowerId.contains("img") ||
                lowerId.contains("pic") ||
                lowerId.contains("photo") ||
                lowerId.contains("banner") ||
                lowerId.contains("avatar") ||
                lowerId.contains("artwork") ||
                lowerId.contains("media")
            val isWrapperId = lowerId.contains("container") ||
                lowerId.contains("layout") ||
                lowerId.contains("wrapper") ||
                lowerId.contains("root") ||
                lowerId.contains("holder") ||
                lowerId.contains("item") ||
                lowerId.contains("list") ||
                lowerId.contains("recycler") ||
                lowerId.contains("bar") ||
                lowerId.contains("tab") ||
                lowerId.contains("nav")
            val isImageRole = node.role.equals("image", ignoreCase = true)
            val isTierAClassOrId = explicitImgClass || isImageRole || (hasMediaId && !isWrapperId)

            val fullScreenChrome = screenW > 0 && screenH > 0 && w >= screenW * 0.96f && h >= screenH * 0.94f
            if (isTierAClassOrId && w >= 44 && h >= 44 && (explicitImgClass || !fullScreenChrome)) {
                tierA += ExtractedSubImageCandidate(
                    id = "ui_a_${tierA.size}",
                    leftPx = cl,
                    topPx = ct,
                    rightPx = cr,
                    bottomPx = cb,
                    sourceTag = "界面图像节点",
                    confidence = 0.94f
                )
                continue
            }

            val areaRatio = if (screenArea > 0L) (w.toLong() * h.toLong()).toFloat() / screenArea.toFloat() else 0.1f
            val aspect = w.toFloat() / h.toFloat()
            val excludedControl = lowerCls.contains("checkbox") ||
                lowerCls.contains("switch") ||
                lowerCls.contains("radiobutton") ||
                lowerCls.contains("seekbar") ||
                lowerCls.contains("progressbar") ||
                lowerCls.contains("scrollview") ||
                lowerCls.contains("recyclerview") ||
                lowerCls.contains("listview") ||
                lowerCls.contains("webview") ||
                lowerCls.contains("viewpager") ||
                lowerCls.contains("button") ||
                lowerCls.contains("tab")

            if (node.childCount == 0 &&
                cleanText.isEmpty() &&
                !excludedControl &&
                w >= 56 &&
                h >= 56 &&
                aspect in 0.18f..5.5f &&
                (screenArea <= 0L || areaRatio < 0.88f)
            ) {
                tierB += ExtractedSubImageCandidate(
                    id = "ui_b_${tierB.size}",
                    leftPx = cl,
                    topPx = ct,
                    rightPx = cr,
                    bottomPx = cb,
                    sourceTag = "界面视觉节点",
                    confidence = 0.84f
                )
                continue
            }

            val isSmallContainer = lowerCls.contains("framelayout") ||
                lowerCls.contains("relativelayout") ||
                lowerCls.contains("constraintlayout") ||
                lowerCls.contains("cardview") ||
                lowerCls.contains("linearlayout") ||
                lowerCls.contains("viewgroup")
            if (isSmallContainer &&
                node.childCount in 1..6 &&
                w >= 72 &&
                h >= 72 &&
                aspect in 0.25f..4.2f &&
                (screenArea <= 0L || areaRatio <= 0.65f)
            ) {
                tierCPool += ExtractedSubImageCandidate(
                    id = "ui_c_${tierCPool.size}",
                    leftPx = cl,
                    topPx = ct,
                    rightPx = cr,
                    bottomPx = cb,
                    sourceTag = "界面媒体容器",
                    confidence = 0.80f
                )
            }
        }

        val abNodes = tierA + tierB
        val tierC = tierCPool.filter { container ->
            val cArea = container.areaPx.coerceAtLeast(1L)
            val alreadyCoveredByChild = abNodes.any { leaf ->
                if (ScreenAssistantPolicy.computeIoU(leaf, container) >= 0.72f) return@any true
                val il = max(leaf.leftPx, container.leftPx)
                val it = max(leaf.topPx, container.topPx)
                val ir = min(leaf.rightPx, container.rightPx)
                val ib = min(leaf.bottomPx, container.bottomPx)
                if (ir <= il || ib <= it) return@any false
                val inter = (ir - il).toLong() * (ib - it).toLong()
                inter * 100L >= cArea * 78L || inter * 100L >= leaf.areaPx.coerceAtLeast(1L) * 75L
            }
            if (alreadyCoveredByChild) return@filter false
            var tArea = 0L
            for (tb in textOut) {
                val il = max(tb.leftPx, container.leftPx)
                val it = max(tb.topPx, container.topPx)
                val ir = min(tb.rightPx, container.rightPx)
                val ib = min(tb.bottomPx, container.bottomPx)
                if (ir > il && ib > it) tArea += (ir - il).toLong() * (ib - it).toLong()
            }
            tArea * 100L < cArea * 22L
        }

        val mediaOut = ArrayList<ExtractedSubImageCandidate>(abNodes.size + tierC.size)
        for (cand in (abNodes + tierC).sortedWith(
            compareByDescending<ExtractedSubImageCandidate> { it.confidence }.thenBy { it.areaPx }
        )) {
            if (mediaOut.none { ScreenAssistantPolicy.computeIoU(it, cand) >= 0.72f }) {
                mediaOut += cand
            }
        }

        val clampedObs = obstructions.mapNotNull { ob ->
            val l = ob.leftPx.coerceAtLeast(0)
            val t = ob.topPx.coerceAtLeast(0)
            val r = if (screenW > 0) ob.rightPx.coerceAtMost(screenW) else ob.rightPx
            val b = if (screenH > 0) ob.bottomPx.coerceAtMost(screenH) else ob.bottomPx
            val w = r - l
            val h = b - t
            val areaOk = screenArea <= 0L || (w.toLong() * h.toLong()) * 100L <= screenArea * 30L
            val widthOk = screenW <= 0 || w * 100 <= screenW * 80
            if (w >= 24 && h >= 24 && areaOk && widthOk) ob.copy(leftPx = l, topPx = t, rightPx = r, bottomPx = b) else null
        }
        return AccessibilityScreenSnapshot(textOut, mediaOut, clampedObs, bestPkg)
    }

    fun parseUiTree(raw: String, screenW: Int, screenH: Int): AccessibilityScreenSnapshot {
        val trimmed = raw.trimStart()
        if (!trimmed.startsWith("GMOU1")) {
            return if (trimmed.contains("<hierarchy") || trimmed.contains("<node")) {
                parseXml(raw, screenW, screenH)
            } else {
                AccessibilityScreenSnapshot(emptyList(), emptyList())
            }
        }
        val nodes = ArrayList<RawUiNode>(128)
        val obstructions = ArrayList<ExtractedSubImageCandidate>(4)
        for (line in trimmed.lineSequence()) {
            if (line.isBlank() || line == "GMOU1") continue
            val parts = line.split('\t')
            when (parts.firstOrNull()) {
                "O" -> if (parts.size >= 5) {
                    val l = parts[1].toIntOrNull() ?: continue
                    val t = parts[2].toIntOrNull() ?: continue
                    val r = parts[3].toIntOrNull() ?: continue
                    val b = parts[4].toIntOrNull() ?: continue
                    obstructions += ExtractedSubImageCandidate(
                        id = "obs_${obstructions.size}",
                        leftPx = l,
                        topPx = t,
                        rightPx = r,
                        bottomPx = b,
                        sourceTag = "悬浮窗遮挡",
                        confidence = 1.0f
                    )
                }
                "N" -> if (parts.size >= 11) {
                    val depth = parts[1].toIntOrNull() ?: continue
                    val childCount = parts[2].toIntOrNull() ?: continue
                    val l = parts[3].toIntOrNull() ?: continue
                    val t = parts[4].toIntOrNull() ?: continue
                    val r = parts[5].toIntOrNull() ?: continue
                    val b = parts[6].toIntOrNull() ?: continue
                    nodes += RawUiNode(
                        depth = depth,
                        childCount = childCount,
                        leftPx = l,
                        topPx = t,
                        rightPx = r,
                        bottomPx = b,
                        pkg = parts[7],
                        cls = parts[8],
                        viewId = parts[9],
                        text = parts[10],
                        contentDesc = parts.getOrElse(11) { "" }
                    )
                }
            }
        }
        return buildSnapshotFromNodes(nodes, obstructions, screenW, screenH)
    }

    fun parseXml(xml: String, screenW: Int, screenH: Int): AccessibilityScreenSnapshot {
        val boundsRegex = Regex("""\[(-?\d+),(-?\d+)]\[(-?\d+),(-?\d+)]""")
        val tagRegex = Regex("""<(/?node)\b([^>]*)>""")
        val attrRegex = Regex("""([A-Za-z0-9_:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')""")
        fun decodeEntities(raw: String): String {
            if ('&' !in raw) return raw
            return raw.replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
        }

        val collected = ArrayList<RawUiNode>(64)
        val childCounts = ArrayList<Int>(64)
        val openStack = ArrayList<Int>(32)

        for (tagMatch in tagRegex.findAll(xml)) {
            val tagName = tagMatch.groupValues[1]
            if (tagName == "/node") {
                if (openStack.isNotEmpty()) openStack.removeAt(openStack.lastIndex)
                continue
            }
            val rawTail = tagMatch.groupValues[2]
            val selfClosing = rawTail.trimEnd().endsWith("/")
            val attrsRaw = if (selfClosing) rawTail.trimEnd().dropLast(1) else rawTail
            var pkg = ""
            var cls = ""
            var role = ""
            var txt = ""
            var desc = ""
            var resId = ""
            var boundsStr = ""
            for (am in attrRegex.findAll(attrsRaw)) {
                val name = am.groupValues[1]
                val valRaw = am.groupValues[2].ifEmpty { am.groupValues[3] }
                when (name) {
                    "package" -> pkg = decodeEntities(valRaw).trim()
                    "class" -> cls = decodeEntities(valRaw)
                    "role-description" -> role = decodeEntities(valRaw)
                    "text" -> txt = decodeEntities(valRaw).trim()
                    "content-desc" -> desc = decodeEntities(valRaw).trim()
                    "resource-id" -> resId = decodeEntities(valRaw).trim()
                    "bounds" -> boundsStr = decodeEntities(valRaw)
                }
            }
            openStack.lastOrNull()?.let { parentIdx ->
                if (parentIdx >= 0) childCounts[parentIdx] = childCounts[parentIdx] + 1
            }
            val bm = boundsRegex.matchEntire(boundsStr)
            if (bm == null) {
                if (!selfClosing) openStack.add(-1)
                continue
            }
            val idx = collected.size
            collected += RawUiNode(
                depth = openStack.size,
                childCount = 0,
                leftPx = bm.groupValues[1].toInt(),
                topPx = bm.groupValues[2].toInt(),
                rightPx = bm.groupValues[3].toInt(),
                bottomPx = bm.groupValues[4].toInt(),
                pkg = pkg,
                cls = cls,
                viewId = resId,
                text = txt,
                contentDesc = desc,
                role = role
            )
            childCounts += 0
            if (!selfClosing) openStack.add(idx)
        }
        val finalized = collected.mapIndexed { i, n -> n.copy(childCount = childCounts[i]) }
        return buildSnapshotFromNodes(finalized, emptyList(), screenW, screenH)
    }

    fun captureMultiWindowAccessibilitySnapshot(
        rootNodes: List<AccessibilityNodeInfo>,
        selfPackage: String,
        displayWidth: Int = 0,
        displayHeight: Int = 0
    ): AccessibilityScreenSnapshot {
        if (rootNodes.isEmpty()) return AccessibilityScreenSnapshot(emptyList(), emptyList())
        val collected = ArrayList<RawUiNode>(96)
        val rect = Rect()

        fun walk(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 48 || collected.size >= 1600) return
            val pkg = node.packageName?.toString().orEmpty()
            if (pkg == selfPackage || pkg == "com.android.systemui") return
            if (!node.isVisibleToUser) return
            node.getBoundsInScreen(rect)
            if (rect.width() > 0 && rect.height() > 0) {
                collected += RawUiNode(
                    depth = depth,
                    childCount = node.childCount,
                    leftPx = rect.left,
                    topPx = rect.top,
                    rightPx = rect.right,
                    bottomPx = rect.bottom,
                    pkg = pkg,
                    cls = node.className?.toString().orEmpty(),
                    viewId = node.viewIdResourceName.orEmpty(),
                    text = node.text?.toString()?.trim().orEmpty(),
                    contentDesc = node.contentDescription?.toString()?.trim().orEmpty()
                )
            }
            val count = node.childCount.coerceAtMost(96)
            for (i in 0 until count) {
                if (collected.size >= 1600) break
                val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
                try {
                    walk(child, depth + 1)
                } finally {
                    @Suppress("DEPRECATION")
                    runCatching { child.recycle() }
                }
            }
        }
        rootNodes.forEach { walk(it, 0) }
        return buildSnapshotFromNodes(collected, emptyList(), displayWidth, displayHeight)
    }

    fun mergeSnapshots(
        primary: AccessibilityScreenSnapshot,
        uiDump: AccessibilityScreenSnapshot,
        extraObstructions: List<ExtractedSubImageCandidate> = emptyList()
    ): AccessibilityScreenSnapshot {
        val mergedTexts = ArrayList<ScreenTextBlock>(primary.textNodes.size + uiDump.textNodes.size)
        mergedTexts.addAll(primary.textNodes)
        uiDump.textNodes.forEach { candidate ->
            val dup = mergedTexts.any { existing ->
                val il = max(existing.leftPx, candidate.leftPx)
                val it = max(existing.topPx, candidate.topPx)
                val ir = min(existing.rightPx, candidate.rightPx)
                val ib = min(existing.bottomPx, candidate.bottomPx)
                ir > il && ib > it && existing.text.equals(candidate.text, ignoreCase = true)
            }
            if (!dup) mergedTexts += candidate
        }
        val mergedMedia = ScreenAssistantPolicy.mergeAndDeduplicateSubImageCandidates(
            primary.mediaNodes + uiDump.mediaNodes,
            iouThreshold = 0.55f,
            minSizePx = 44,
            maxResults = 48
        )
        val mergedObs = (primary.overlayObstructions + uiDump.overlayObstructions + extraObstructions).distinctBy {
            "${it.leftPx}_${it.topPx}_${it.rightPx}_${it.bottomPx}"
        }
        val pkg = listOf(primary.foregroundPackage, uiDump.foregroundPackage)
            .firstOrNull { isSafeInspectablePackage(it) }
            ?: primary.foregroundPackage.ifBlank { uiDump.foregroundPackage }
        return AccessibilityScreenSnapshot(mergedTexts, mergedMedia, mergedObs, pkg)
    }

    fun projectSnapshotToCrop(snapshot: AccessibilityScreenSnapshot, crop: Rect?): AccessibilityScreenSnapshot {
        if (crop == null || crop.width() <= 0 || crop.height() <= 0) return snapshot
        fun intersect(l: Int, t: Int, r: Int, b: Int): IntArray? {
            val il = max(l, crop.left) - crop.left
            val it = max(t, crop.top) - crop.top
            val ir = min(r, crop.right) - crop.left
            val ib = min(b, crop.bottom) - crop.top
            return if (ir > il && ib > it) intArrayOf(il, it, ir, ib) else null
        }
        val texts = snapshot.textNodes.mapNotNull { tb ->
            val r = intersect(tb.leftPx, tb.topPx, tb.rightPx, tb.bottomPx) ?: return@mapNotNull null
            tb.copy(leftPx = r[0], topPx = r[1], rightPx = r[2], bottomPx = r[3])
        }
        val media = snapshot.mediaNodes.mapNotNull { mb ->
            val r = intersect(mb.leftPx, mb.topPx, mb.rightPx, mb.bottomPx) ?: return@mapNotNull null
            mb.copy(leftPx = r[0], topPx = r[1], rightPx = r[2], bottomPx = r[3])
        }
        val obs = snapshot.overlayObstructions.mapNotNull { ob ->
            val r = intersect(ob.leftPx, ob.topPx, ob.rightPx, ob.bottomPx) ?: return@mapNotNull null
            ob.copy(leftPx = r[0], topPx = r[1], rightPx = r[2], bottomPx = r[3])
        }
        return AccessibilityScreenSnapshot(texts, media, obs, snapshot.foregroundPackage)
    }
}
