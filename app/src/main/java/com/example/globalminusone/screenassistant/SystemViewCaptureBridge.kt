package com.example.globalminusone.screenassistant

import android.annotation.SuppressLint
import android.graphics.Rect
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * Client-side Binder bridge talking ONLY to `WindowManagerService.onTransact` (`window` service)
 * injected in `system_server` when the user enables the experimental LSPosed module.
 */
internal object SystemViewCaptureBridge {
    const val TRANSACTION_CODE = 0x474D4F56 // 'GMOV'
    const val DESCRIPTOR = "com.example.globalminusone.xposed.ISystemViewCapture"
    const val PROTOCOL_VERSION = 1

    const val OP_PING = 1
    const val OP_DUMP_TOP_WINDOW = 2
    const val OP_CAPTURE_VIEWS = 3

    const val STATUS_OK = 0
    const val STATUS_BUSY = 1
    const val STATUS_NO_WINDOW = 2
    const val STATUS_FAILED = 3
    const val STATUS_INTERNAL_ERROR = 4

    private const val DUMP_CLIENT_TIMEOUT_MS = 2_500L
    private const val CAPTURE_CLIENT_TIMEOUT_MS = 4_500L
    private const val MAX_DUMP_STREAM_BYTES = 2 * 1024 * 1024 + 4096
    private const val MAX_CAPTURE_STREAM_BYTES = 13 * 1024 * 1024

    private val pipePool = Executors.newCachedThreadPool { r ->
        Thread(r, "gmo-lsposed-pipe").apply { isDaemon = true }
    }

    data class PingStatus(
        val active: Boolean,
        val protocolVersion: Int = 0,
        val systemSdkInt: Int = 0,
        val note: String
    )

    data class DumpResult(
        val status: Int,
        val diagnostic: String,
        val packageName: String,
        val windowFrame: Rect,
        val dumpText: String
    ) {
        val ok: Boolean get() = status == STATUS_OK && dumpText.isNotEmpty()
    }

    data class CapturedViewItem(
        val descriptor: String,
        val errCode: Int,
        val errMsg: String,
        val pngBytes: ByteArray
    )

    data class CaptureBatchResult(
        val status: Int,
        val diagnostic: String,
        val items: List<CapturedViewItem>
    ) {
        val ok: Boolean get() = status == STATUS_OK
    }

    @SuppressLint("PrivateApi")
    private fun getWindowManagerBinder(): IBinder? = runCatching {
        val smClass = Class.forName("android.os.ServiceManager")
        val getService = smClass.getMethod("getService", String::class.java)
        getService.invoke(null, "window") as? IBinder
    }.getOrNull()

    suspend fun ping(): PingStatus = withContext(Dispatchers.IO) {
        val binder = getWindowManagerBinder()
            ?: return@withContext PingStatus(false, note = "无法获取系统 window 服务")
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeInt(OP_PING)
            val sent = binder.transact(TRANSACTION_CODE, data, reply, 0)
            if (!sent) {
                return@withContext PingStatus(false, note = "系统框架未响应扩展事务（模块未启用或未重启）")
            }
            reply.readException()
            val proto = reply.readInt()
            val sdk = reply.readInt()
            PingStatus(
                active = proto == PROTOCOL_VERSION,
                protocolVersion = proto,
                systemSdkInt = sdk,
                note = if (proto == PROTOCOL_VERSION) {
                    "已就绪：系统框架 Hook 在线 (API $sdk, v$proto)"
                } else {
                    "协议版本不匹配 (server=$proto, client=$PROTOCOL_VERSION)"
                }
            )
        } catch (err: Exception) {
            val detail = err.message?.trim().orEmpty()
            val note = if (err is SecurityException || detail.contains("Unauthorized", ignoreCase = true)) {
                "系统框架拒绝鉴权：$detail"
            } else {
                "LSPosed 系统框架未激活（请在 LSPosed 仅勾选「系统框架」并重启）"
            }
            PingStatus(false, note = note)
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    suspend fun dumpTopWindow(): DumpResult = withContext(Dispatchers.IO) {
        val raw = transactStream(
            timeoutMs = DUMP_CLIENT_TIMEOUT_MS,
            maxBytes = MAX_DUMP_STREAM_BYTES
        ) { binder, writePfd, reply ->
            val data = Parcel.obtain()
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeInt(OP_DUMP_TOP_WINDOW)
                writePfd.writeToParcel(data, 0)
                if (!binder.transact(TRANSACTION_CODE, data, reply, 0)) {
                    throw IllegalStateException("window 服务未受理 DUMP 请求")
                }
            } finally {
                data.recycle()
            }
        }
        raw.fold(
            onSuccess = { parseDumpStream(it) },
            onFailure = { DumpResult(STATUS_FAILED, it.message ?: "DUMP 通信失败", "", Rect(), "") }
        )
    }

    suspend fun captureViews(targetPkg: String, descriptors: List<String>): CaptureBatchResult = withContext(Dispatchers.IO) {
        val clean = descriptors.filter { ViewDebugDumpParser.isValidViewDescriptor(it) }.distinct().take(12)
        if (targetPkg.isBlank() || clean.isEmpty()) {
            return@withContext CaptureBatchResult(STATUS_FAILED, "无有效图片节点", emptyList())
        }
        val raw = transactStream(
            timeoutMs = CAPTURE_CLIENT_TIMEOUT_MS,
            maxBytes = MAX_CAPTURE_STREAM_BYTES
        ) { binder, writePfd, reply ->
            val data = Parcel.obtain()
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeInt(OP_CAPTURE_VIEWS)
                data.writeString(targetPkg)
                data.writeStringArray(clean.toTypedArray())
                writePfd.writeToParcel(data, 0)
                if (!binder.transact(TRANSACTION_CODE, data, reply, 0)) {
                    throw IllegalStateException("window 服务未受理 CAPTURE 请求")
                }
            } finally {
                data.recycle()
            }
        }
        raw.fold(
            onSuccess = { parseCaptureStream(it) },
            onFailure = { CaptureBatchResult(STATUS_FAILED, it.message ?: "CAPTURE 通信失败", emptyList()) }
        )
    }

    private suspend fun transactStream(
        timeoutMs: Long,
        maxBytes: Int,
        send: (IBinder, ParcelFileDescriptor, Parcel) -> Unit
    ): Result<ByteArray> {
        val binder = getWindowManagerBinder()
            ?: return Result.failure(IllegalStateException("无法连接系统 window 服务"))
        val pipe = runCatching { ParcelFileDescriptor.createPipe() }.getOrNull()
            ?: return Result.failure(IllegalStateException("无法创建系统提图管道"))
        val readPfd = pipe[0]
        val writePfd = pipe[1]
        val readerFut = CompletableFuture.supplyAsync({
            readPfd.use { pfd ->
                readBoundedStream(ParcelFileDescriptor.AutoCloseInputStream(pfd), maxBytes)
            }
        }, pipePool)
        val reply = Parcel.obtain()
        try {
            send(binder, writePfd, reply)
            runCatching { writePfd.close() }
            reply.readException()
            val ack = reply.readInt()
            if (ack == STATUS_BUSY) {
                // Server already wrote diagnostic frame into writePfd; read it cleanly.
            }
            val bytes = withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine<ByteArray> { cont ->
                    readerFut.whenComplete { payload, err ->
                        if (!cont.isActive) return@whenComplete
                        if (err != null) cont.resume(ByteArray(0)) else cont.resume(payload ?: ByteArray(0))
                    }
                }
            } ?: run {
                readerFut.cancel(true)
                runCatching { readPfd.close() }
                return Result.failure(IllegalStateException("系统提图等待超时 (${timeoutMs}ms)"))
            }
            return Result.success(bytes)
        } catch (c: CancellationException) {
            readerFut.cancel(true)
            runCatching { readPfd.close() }
            throw c
        } catch (err: Exception) {
            readerFut.cancel(true)
            runCatching { readPfd.close() }
            return Result.failure(err)
        } finally {
            reply.recycle()
            runCatching { writePfd.close() }
        }
    }

    internal fun parseDumpStream(raw: ByteArray): DumpResult {
        if (raw.size < 28 ||
            raw[0] != 'G'.code.toByte() ||
            raw[1] != 'M'.code.toByte() ||
            raw[2] != 'O'.code.toByte() ||
            raw[3] != 'X'.code.toByte()
        ) {
            return DumpResult(STATUS_FAILED, "视图流头无效(${raw.size}B)", "", Rect(), "")
        }
        val bb = ByteBuffer.wrap(raw, 4, raw.size - 4).order(ByteOrder.BIG_ENDIAN)
        if (bb.remaining() < 4) return DumpResult(STATUS_FAILED, "视图流截断", "", Rect(), "")
        val status = bb.getInt()
        val diag = readUtf8(bb) ?: return DumpResult(STATUS_FAILED, "视图诊断字段损坏", "", Rect(), "")
        val pkg = readUtf8(bb) ?: return DumpResult(STATUS_FAILED, "包名字段损坏", "", Rect(), "")
        if (bb.remaining() < 20) return DumpResult(STATUS_FAILED, "窗口坐标字段截断", "", Rect(), "")
        val frame = Rect(bb.getInt(), bb.getInt(), bb.getInt(), bb.getInt())
        val dumpLen = bb.getInt()
        if (dumpLen < 0 || dumpLen > MAX_DUMP_STREAM_BYTES || bb.remaining() < dumpLen) {
            return DumpResult(STATUS_FAILED, "视图树正文截断", pkg, frame, "")
        }
        val dumpBytes = ByteArray(dumpLen)
        if (dumpLen > 0) bb.get(dumpBytes)
        return DumpResult(
            status = status,
            diagnostic = diag,
            packageName = pkg,
            windowFrame = frame,
            dumpText = String(dumpBytes, Charsets.UTF_8)
        )
    }

    internal fun parseCaptureStream(raw: ByteArray): CaptureBatchResult {
        if (raw.size < 16 ||
            raw[0] != 'G'.code.toByte() ||
            raw[1] != 'M'.code.toByte() ||
            raw[2] != 'O'.code.toByte() ||
            raw[3] != 'X'.code.toByte()
        ) {
            return CaptureBatchResult(STATUS_FAILED, "抓图流头无效(${raw.size}B)", emptyList())
        }
        val bb = ByteBuffer.wrap(raw, 4, raw.size - 4).order(ByteOrder.BIG_ENDIAN)
        if (bb.remaining() < 4) return CaptureBatchResult(STATUS_FAILED, "抓图流截断", emptyList())
        val status = bb.getInt()
        val diag = readUtf8(bb) ?: return CaptureBatchResult(STATUS_FAILED, "抓图诊断字段损坏", emptyList())
        if (bb.remaining() < 4) return CaptureBatchResult(STATUS_FAILED, "抓图计数截断", emptyList())
        val count = bb.getInt().coerceIn(0, 64)
        val items = ArrayList<CapturedViewItem>(count)
        for (i in 0 until count) {
            val desc = readUtf8(bb) ?: break
            if (bb.remaining() < 4) break
            val errCode = bb.getInt()
            val errMsg = readUtf8(bb) ?: break
            if (bb.remaining() < 4) break
            val pngLen = bb.getInt()
            if (pngLen < 0 || pngLen > MAX_CAPTURE_STREAM_BYTES || bb.remaining() < pngLen) break
            val png = ByteArray(pngLen)
            if (pngLen > 0) bb.get(png)
            items += CapturedViewItem(
                descriptor = desc,
                errCode = errCode,
                errMsg = if (errCode == 0 && !looksLikePng(png)) "非有效 PNG 字节" else errMsg,
                pngBytes = if (errCode == 0 && looksLikePng(png)) png else ByteArray(0)
            )
        }
        return CaptureBatchResult(status, diag, items)
    }

    internal fun looksLikePng(b: ByteArray): Boolean =
        b.size >= 24 &&
            b[0] == 0x89.toByte() && b[1] == 0x50.toByte() &&
            b[2] == 0x4E.toByte() && b[3] == 0x47.toByte() &&
            b[4] == 0x0D.toByte() && b[5] == 0x0A.toByte() &&
            b[6] == 0x1A.toByte() && b[7] == 0x0A.toByte()

    private fun readUtf8(bb: ByteBuffer): String? {
        if (bb.remaining() < 4) return null
        val len = bb.getInt()
        if (len < 0 || len > 1_048_576 || bb.remaining() < len) return null
        if (len == 0) return ""
        val bytes = ByteArray(len)
        bb.get(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun readBoundedStream(input: InputStream, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream(8192)
        val buf = ByteArray(16384)
        var total = 0
        input.use { stream ->
            while (true) {
                val n = stream.read(buf)
                if (n < 0) break
                if (n == 0) continue
                total += n
                if (total > maxBytes) {
                    val allowed = maxBytes - (total - n)
                    if (allowed > 0) out.write(buf, 0, allowed)
                    break
                }
                out.write(buf, 0, n)
            }
        }
        return out.toByteArray()
    }
}
