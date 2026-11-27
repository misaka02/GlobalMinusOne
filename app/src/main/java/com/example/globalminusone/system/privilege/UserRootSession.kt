package com.example.globalminusone.system.privilege

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import com.example.globalminusone.system.file.RootMutationRequest
import com.example.globalminusone.system.file.decodeEditableText
import com.example.globalminusone.system.file.rootMutationCommand

enum class RootStatus { NotRequested, Unavailable, Denied, TimedOut, Granted, Error }
enum class PrivilegeAction { WIFI_ON, WIFI_OFF, BLUETOOTH_ON, BLUETOOTH_OFF, WIFI_STATE, BLUETOOTH_STATE }
data class RootCommandResult(val status: RootStatus, val exitCode: Int?, val stdout: String, val stderr: String, val reason: String) {
    val succeeded get() = status == RootStatus.Granted && exitCode == 0
}
internal data class BoundedRootOutput(
    val exitCode: Int?,
    val bytes: ByteArray,
    val diagnostic: String,
    val timedOut: Boolean,
    val leaseCurrent: Boolean
)
internal data class RootProcessRequest(val executable: String, val command: String, val input: ByteArray?, val maxBytes: Int, val timeoutMs: Long, val entryMarker: String? = null)
internal data class RootProcessOutcome(val exitCode: Int?, val bytes: ByteArray = byteArrayOf(), val stderr: String = "", val timedOut: Boolean = false)
internal fun interface RootExecutor { suspend fun execute(request: RootProcessRequest): RootProcessOutcome }
internal sealed interface RootReadRequest {
    data class ListDirectory(val path: String) : RootReadRequest
    data class ReadFile(val path: String, val maxBytes: Int) : RootReadRequest
}
internal fun rootReadCommand(request: RootReadRequest): String = when(request) {
    is RootReadRequest.ReadFile -> {
        require(request.maxBytes in 1..(32 * 1024 * 1024)) { "root读取限额无效" }
        "head -c ${request.maxBytes + 1} -- ${rootShellPath(request.path)}"
    }
    is RootReadRequest.ListDirectory -> {
        val dir = rootShellPath(request.path)
        "d=$dir; [ -d \"\$d\" ] && [ -r \"\$d\" ] || exit 73; " +
            "for p in \"\$d\"/* \"\$d\"/.[!.]* \"\$d\"/..?*; do " +
            "[ -e \"\$p\" ] || [ -L \"\$p\" ] || continue; " +
            "if [ -L \"\$p\" ]; then k=l; elif [ -d \"\$p\" ]; then k=d; elif [ -f \"\$p\" ]; then k=f; else k=o; fi; " +
            "m=\$(stat -c '%s %Y' -- \"\$p\") || exit 74; printf '%s\\000%s\\000%s\\000' \"\$k\" \"\$m\" \"\$p\"; done"
    }
}

/** Paths are data, never shell syntax. Control bytes are refused rather than interpreted. */
internal fun rootShellPath(path: String): String {
    return rootShellQuote(normalizeRootPath(path))
}
internal fun normalizeRootPath(path: String): String {
    require(path.startsWith('/') && path.none { it.code < 32 || it.code == 127 }) { "root路径必须是绝对路径，且不能含控制字符" }
    val parts = path.split('/')
    require(parts.none { it == "." || it == ".." }) { "root路径不能含.或..跳转段" }
    return "/" + parts.filter { it.isNotEmpty() }.joinToString("/")
}
internal fun rootShellQuote(value: String) = "'" + value.replace("'", "'\"'\"'") + "'"

/** Only an explicit user action calls requestAuthorization. No grant is stored in configuration. */
object UserRootSession {
    private val mutableStatus = MutableStateFlow(RootStatus.NotRequested)
    val status: StateFlow<RootStatus> = mutableStatus.asStateFlow()
    private val gate = Mutex()
    private var executable: String? = null
    private val generation = java.util.concurrent.atomic.AtomicLong()
    private val processLock = Any()
    @Volatile private var broker: LibsuRootTransport? = null
    internal var directSuProcessFactoryForTests: ((List<String>) -> Process)? = null
    private var captureWasGranted = false
    private var captureRecoveryUsed = false
    private var captureAuthorizationBlocked = false
    private val revocationListeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()
    private val callbackExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { task ->
        Thread(task, "GmoRootRevocation").apply { isDaemon = true }
    }
    fun registerRevocationListener(listener: () -> Unit): java.io.Closeable {
        revocationListeners += listener
        return java.io.Closeable { revocationListeners -= listener }
    }
    fun authorizedEpoch(): Long? = synchronized(processLock) {
        if (status.value == RootStatus.Granted && executorForTests == null && broker?.alive != true) {
            mutableStatus.value = RootStatus.Error; executable = null; broker?.close(); broker = null
            generation.incrementAndGet(); RootWritePolicy.clear(); notifyRevoked()
        }
        generation.get().takeIf { status.value == RootStatus.Granted }
    }
    fun isAuthorizationCurrent(epoch: Long): Boolean = authorizedEpoch() == epoch
    fun revokeAuthorization() {
        synchronized(processLock) {
            captureWasGranted = false
            captureRecoveryUsed = false
            captureAuthorizationBlocked = true
            generation.incrementAndGet(); mutableStatus.value = RootStatus.NotRequested; executable = null
            broker?.close(); broker = null
            RootWritePolicy.clear()
        }
        notifyRevoked()
    }
    private fun notifyRevoked() {
        val listeners = revocationListeners.toList()
        callbackExecutor.execute { listeners.forEach { runCatching { it() } } }
    }
    internal fun currentEpoch(): Long = generation.get()
    private var executorForTests: RootExecutor? = null
    private var probeForTests: (() -> String?)? = null
    internal fun resetForTests(executor: RootExecutor? = null, probe: (() -> String?)? = null) {
        revokeAuthorization(); executorForTests = executor; probeForTests = probe
        synchronized(processLock) { captureAuthorizationBlocked = false }
    }
    private data class Raw(val exit: Int?, val bytes: ByteArray, val diagnostic: String, val timedOut: Boolean = false)

    suspend fun requestAuthorization(): RootCommandResult = gate.withLock { authorizeLocked() }

    /** Only screen-read workflows may recover a lost grant, once, with the existing Root manager policy. */
    internal suspend fun connectForScreenRead(allowConfiguredConnect: Boolean): Boolean = gate.withLock {
        if (authorizedEpoch() != null) return@withLock true
        val expectedEpoch = synchronized(processLock) {
            if (captureAuthorizationBlocked) return@synchronized null
            when (status.value) {
                RootStatus.NotRequested -> if (allowConfiguredConnect) generation.get() else null
                RootStatus.Error, RootStatus.TimedOut -> if ((captureWasGranted || allowConfiguredConnect) && !captureRecoveryUsed) {
                    captureRecoveryUsed = true
                    generation.get()
                } else null
                else -> null
            }
        } ?: return@withLock false
        authorizeLocked(expectedEpoch, explicit = false).succeeded
    }

    private suspend fun authorizeLocked(expectedEpoch: Long? = null, explicit: Boolean = true): RootCommandResult {
        if (authorizedEpoch() != null) return RootCommandResult(RootStatus.Granted, 0, "0", "", "复用当前有效Root会话")
        val epoch = generation.get()
        val su = if (probeForTests != null) probeForTests?.invoke() else withContext(Dispatchers.IO) {
            listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su").firstOrNull { File(it).canExecute() }
                ?: runCatching { ProcessBuilder("sh", "-c", "command -v su").start().let { process ->
                    try { if (process.waitFor(1, TimeUnit.SECONDS) && process.exitValue() == 0)
                        process.inputStream.bufferedReader().readLine()?.takeIf { it.startsWith('/') && File(it).canExecute() } else null
                    } finally { process.destroy() }
                } }.getOrNull()
        }
        if (epoch != generation.get() || (expectedEpoch != null && expectedEpoch != epoch)) {
            return RootCommandResult(status.value, null, "", "", "Root会话已结束")
        }
        if (su == null) {
            synchronized(processLock) {
                if (epoch != generation.get()) return RootCommandResult(status.value, null, "", "", "Root会话已结束")
                mutableStatus.value = RootStatus.Unavailable; executable = null
            }
            return RootCommandResult(RootStatus.Unavailable, null, "", "", "未找到su；设备未提供root能力")
        }
        synchronized(processLock) { if (epoch == generation.get()) executable = su }
        val raw = perform(su, "id -u", null, 4096, 30_000, epoch = epoch)
        if (epoch != generation.get()) return RootCommandResult(status.value, null, "", raw.diagnostic, raw.diagnostic.ifBlank { "Root请求已取消或会话已结束" })
        val next = when {
            raw.timedOut -> RootStatus.TimedOut
            raw.exit == 0 && raw.bytes.toString(Charsets.UTF_8).trim() == "0" -> RootStatus.Granted
            raw.exit != null -> RootStatus.Denied
            else -> RootStatus.Error
        }
        synchronized(processLock) {
            if (epoch != generation.get()) return RootCommandResult(status.value, null, "", "", "Root会话已结束")
            mutableStatus.value = next
            if (next == RootStatus.Granted) {
                captureWasGranted = true
                captureAuthorizationBlocked = false
                if (explicit) captureRecoveryUsed = false
            }
            if (next != RootStatus.Granted) { executable = null; broker?.close(); broker = null; RootWritePolicy.clear(); generation.incrementAndGet(); notifyRevoked() }
        }
        return RootCommandResult(next, raw.exit, raw.bytes.toString(Charsets.UTF_8), raw.diagnostic,
            when(next) { RootStatus.Granted -> "已确认su返回uid0（仅当前会话）"; RootStatus.TimedOut -> "su授权超时，已终止请求"; RootStatus.Denied -> "su未授予root权限"; else -> raw.diagnostic.ifBlank { "无法启动su" } })
    }

    suspend fun runPrivilegeAction(action: PrivilegeAction): RootCommandResult {
        val command = when(action) {
            PrivilegeAction.WIFI_ON -> "svc wifi enable"
            PrivilegeAction.WIFI_OFF -> "svc wifi disable"
            PrivilegeAction.BLUETOOTH_ON -> "svc bluetooth enable"
            PrivilegeAction.BLUETOOTH_OFF -> "svc bluetooth disable"
            PrivilegeAction.WIFI_STATE -> "cmd wifi status"
            PrivilegeAction.BLUETOOTH_STATE -> "dumpsys bluetooth_manager"
        }
        val lease = authorizedEpoch() ?: return RootCommandResult(status.value, null, "", "", "尚无有效Root会话；请显式授权")
        return runAuthorized(command, expectedEpoch = lease, maxBytes = if (action == PrivilegeAction.BLUETOOTH_STATE) 128 * 1024 else 16_384, timeoutMs = 8_000).let { raw ->
            RootCommandResult(if (raw.exit == 0 && status.value == RootStatus.Granted) RootStatus.Granted else if (raw.timedOut) RootStatus.TimedOut else status.value.takeIf { it != RootStatus.Granted } ?: RootStatus.Error,
                raw.exit, raw.bytes.toString(Charsets.UTF_8), raw.diagnostic,
                if (raw.exit == 0 && status.value == RootStatus.Granted) "命令已完成，仍须核对系统实际状态" else raw.diagnostic.ifBlank { "特权命令失败；退出码${raw.exit}" })
        }
    }

    /** Real manifest-resolved recognition tile only; never a guessed service or launcher. */
    internal suspend fun runBreenoDirectCommand(installedPackages: List<String>, tileComponent: String? = null, serviceComponent: String? = null): RootCommandResult {
        val policy = com.example.globalminusone.screenassistant.BreenoTilePolicy
        val protocol = com.example.globalminusone.screenassistant.BreenoSystemProtocol
        val direct = serviceComponent == protocol.component && protocol.servicePackage in installedPackages && tileComponent == null
        if (!direct && (serviceComponent != null || tileComponent == null || !policy.validComponent(tileComponent) || tileComponent.substringBefore('/') !in installedPackages))
            return RootCommandResult(RootStatus.Error, null, "", "", "未找到已核对的小布识屏磁贴入口；未执行猜测命令")
        val lease = authorizedEpoch() ?: return RootCommandResult(status.value, null, "", "", "尚无有效Root会话")
        val command = if (direct) protocol.command(checkNotNull(serviceComponent)) else policy.command(checkNotNull(tileComponent))
        val raw = runAuthorized(command, expectedEpoch = lease, maxBytes = 8192, timeoutMs = 4_000)
        val text = raw.bytes.toString(Charsets.UTF_8)
        val sent = isAuthorizationCurrent(lease) && !raw.timedOut &&
            if (direct) protocol.accepted(raw.exit, text, raw.diagnostic) else policy.requestWasSent(raw.exit, text, raw.diagnostic)
        return RootCommandResult(
            if (sent) RootStatus.Granted else if (raw.timedOut) RootStatus.TimedOut else RootStatus.Error,
            if (sent) 0 else raw.exit?.takeUnless { it == 0 }, text, raw.diagnostic,
            if (sent) "已发送系统识屏请求，未确认系统界面" else
                raw.diagnostic.ifBlank { text.trim().take(300).ifBlank { "系统识屏磁贴请求失败，未回退" } }
        )
    }

    internal suspend fun runFileRequest(request: RootReadRequest): ByteArray {
        val lease = authorizedEpoch() ?: error("Root会话未授权或已失效")
        val command = rootReadCommand(request)
        val limit = if (request is RootReadRequest.ReadFile) request.maxBytes else 4 * 1024 * 1024
        val raw = runAuthorized(command, null, limit, 8_000, lease)
        check(status.value == RootStatus.Granted) { "root会话已结束或授权失效" }
        check(raw.exit == 0 && !raw.timedOut) { raw.diagnostic.ifBlank { raw.bytes.toString(Charsets.UTF_8).take(600).ifBlank { "root命令失败（${raw.exit}）" } } }
        return raw.bytes
    }

    /** Captures lossless native framebuffer PNG via fixed `screencap -p` using the active Root lease. */
    internal suspend fun captureScreenPng(): ByteArray? {
        val lease = authorizedEpoch() ?: return null
        val raw = runAuthorized("/system/bin/screencap -p", null, 24 * 1024 * 1024, 5_500, lease)
        if (!isAuthorizationCurrent(lease) || raw.timedOut || raw.exit != 0) return null
        val b = raw.bytes
        return if (b.size >= 8 && b[0] == 0x89.toByte() && b[1] == 0x50.toByte() && b[2] == 0x4E.toByte() && b[3] == 0x47.toByte()) b else null
    }

    /**
     * Captures cross-window UI hierarchy XML (`--compressed` + inner 2s bound) using active Root lease
     * without letting a slow `uiautomator dump` trip the session-wide watchdog.
     */
    internal suspend fun dumpUiHierarchyXml(): String? {
        val lease = authorizedEpoch() ?: return null
        val cmd = "f=/data/local/tmp/gmo_ui_dump_\$\$.xml; trap 'rm -f \"\$f\"' EXIT; " +
            "/system/bin/timeout -k 1s 2s /system/bin/uiautomator dump --compressed \"\$f\" >/dev/null 2>&1 && /system/bin/cat \"\$f\""
        val raw = runAuthorized(cmd, null, 4 * 1024 * 1024, 5_000, lease)
        if (!isAuthorizationCurrent(lease) || raw.timedOut || raw.exit != 0) return null
        val xml = raw.bytes.toString(Charsets.UTF_8)
        return xml.takeIf { it.contains("<hierarchy") || it.contains("<node") }
    }

    /** Injects a strictly bounded integer touchscreen swipe via Root when Accessibility gesture dispatch is unavailable. */
    internal suspend fun injectTouchSwipe(
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        durationMs: Int = 280
    ): Boolean {
        val lease = authorizedEpoch() ?: return false
        val sx0 = x0.coerceIn(1, 20_000)
        val sy0 = y0.coerceIn(1, 20_000)
        val sx1 = x1.coerceIn(1, 20_000)
        val sy1 = y1.coerceIn(1, 20_000)
        val dur = durationMs.coerceIn(120, 800)
        val cmd = "/system/bin/timeout -s KILL 3s /system/bin/input touchscreen swipe $sx0 $sy0 $sx1 $sy1 $dur"
        val raw = runAuthorized(cmd, null, 4096, 3_500L, lease, preserveSessionOnTimeout = true)
        return isAuthorizationCurrent(lease) && !raw.timedOut && raw.exit == 0
    }

    internal suspend fun runBoundedCommand(
        command: String,
        maxBytes: Int,
        timeoutMs: Long,
        preserveSessionOnTimeout: Boolean = false
    ): BoundedRootOutput {
        val lease = authorizedEpoch() ?: return BoundedRootOutput(
            exitCode = null,
            bytes = byteArrayOf(),
            diagnostic = "Root会话未授权",
            timedOut = false,
            leaseCurrent = false
        )
        val cappedBytes = maxBytes.coerceIn(4096, 16 * 1024 * 1024)
        val cappedTimeout = timeoutMs.coerceIn(1_000L, 30_000L)
        val raw = runAuthorized(
            command = command,
            input = null,
            maxBytes = cappedBytes,
            timeoutMs = cappedTimeout,
            expectedEpoch = lease,
            preserveSessionOnTimeout = preserveSessionOnTimeout
        )
        return BoundedRootOutput(
            exitCode = raw.exit,
            bytes = raw.bytes,
            diagnostic = raw.diagnostic,
            timedOut = raw.timedOut,
            leaseCurrent = isAuthorizationCurrent(lease)
        )
    }

    internal fun openDirectSuProcess(command: String, expectedEpoch: Long): Process? {
        if (!isAuthorizationCurrent(expectedEpoch)) return null
        val factory = directSuProcessFactoryForTests
        val args = if (factory != null) {
            listOf(synchronized(processLock) { executable } ?: "/system/bin/su", "-c", command)
        } else {
            val suBin = synchronized(processLock) { executable } ?: return null
            listOf(suBin, "-c", command)
        }
        return runCatching {
            factory?.invoke(args) ?: ProcessBuilder(args).start()
        }.getOrNull()
    }

    internal fun openInteractiveSuProcess(expectedEpoch: Long): Process? {
        if (!isAuthorizationCurrent(expectedEpoch)) return null
        val factory = directSuProcessFactoryForTests
        val args = if (factory != null) {
            listOf(synchronized(processLock) { executable } ?: "/system/bin/su")
        } else {
            val suBin = synchronized(processLock) { executable } ?: return null
            listOf(suBin)
        }
        return runCatching {
            factory?.invoke(args) ?: ProcessBuilder(args).start()
        }.getOrNull()
    }

    internal suspend fun runShellCardCommand(command: String, timeoutMs: Long = 10_000L): RootCommandResult {
        require(command.isNotBlank() && command.length <= 4096) { "命令需为1～4096字符" }
        var lease = authorizedEpoch()
        if (lease == null) {
            val authResult = requestAuthorization()
            if (!authResult.succeeded) return authResult
            lease = authorizedEpoch() ?: return RootCommandResult(status.value, null, "", "", authResult.reason.ifBlank { "Root会话尚未建立" })
        }
        val clampedMs = timeoutMs.coerceIn(1_000L, 60_000L)
        val timeoutSec = ((clampedMs + 999L) / 1000L).coerceIn(1L, 60L)
        val wrapped = "timeout -s KILL ${timeoutSec}s /system/bin/sh -c ${rootShellQuote(command)} 2>&1"
        val raw = runAuthorized(wrapped, null, 65_536, clampedMs, lease)
        if (!isAuthorizationCurrent(lease) && raw.exit == null) {
            return RootCommandResult(status.value, null, raw.bytes.toString(Charsets.UTF_8), raw.diagnostic, "Root会话已结束")
        }
        return RootCommandResult(
            status = status.value,
            exitCode = raw.exit,
            stdout = raw.bytes.toString(Charsets.UTF_8),
            stderr = raw.diagnostic,
            reason = if (raw.timedOut) "Root命令超时已终止" else if (raw.exit == 0) "Root执行完成" else "Root命令返回 ${raw.exit}"
        )
    }

    internal suspend fun runMutationRequest(request: RootMutationRequest): ByteArray {
        val lease = authorizedEpoch() ?: error("Root会话未授权或已失效")
        if (request is RootMutationRequest.SaveText) RootWritePolicy.consumeIssued(request.approval, request.bytes)
        if (request is RootMutationRequest.Create) RootWritePolicy.requireDirectory(request.parent)
        if (request is RootMutationRequest.Create && request.text != null) decodeEditableText(request.text)
        val raw = runAuthorized(rootMutationCommand(request), null, 16_384, 8_000, lease)
        check(status.value == RootStatus.Granted && raw.exit == 0 && !raw.timedOut) {
            raw.diagnostic.ifBlank { "Root事务失败；请检查原目标与备份，未宣称保存完成（${raw.exit}）" }
        }
        return raw.bytes
    }

    private suspend fun runAuthorized(
        command: String,
        input: ByteArray? = null,
        maxBytes: Int,
        timeoutMs: Long,
        expectedEpoch: Long,
        preserveSessionOnTimeout: Boolean = false
    ): Raw = gate.withLock {
        drainRootOperation {
            val epoch = generation.get()
            if (epoch != expectedEpoch || status.value != RootStatus.Granted || executable == null) return@drainRootOperation Raw(null, byteArrayOf(), "尚未手动授予本会话root权限")
            // A privileged daemon may outlive its su client. A server-side watchdog bounds the actual job too.
            if (executorForTests == null && !File("/system/bin/timeout").canExecute()) return@drainRootOperation Raw(null, byteArrayOf(), "设备缺少timeout，无法安全限制root命令；此操作未执行")
            val marker = "GMO_ROOT_" + java.util.UUID.randomUUID().toString().replace("-", "")
            val guarded = "[ \"\$(id -u)\" = 0 ] || exit 126; printf '$marker:0\\n' >&2; " + command
            val raw = perform(executable ?: return@drainRootOperation Raw(null, byteArrayOf(), "root会话已结束"), guarded, input, maxBytes, timeoutMs + 1000, marker, epoch)
            if (epoch != generation.get()) return@drainRootOperation Raw(-1, byteArrayOf(), "root会话已结束，旧响应已废弃")
            val entered = raw.diagnostic.contains("$marker:0\n")
            if (!entered) {
                synchronized(processLock) {
                    if (epoch != generation.get()) return@drainRootOperation Raw(-1, byteArrayOf(), "root会话已结束，旧响应已废弃")
                    mutableStatus.value = if (raw.timedOut) RootStatus.TimedOut else if (raw.exit != null && raw.exit != 0) RootStatus.Denied else RootStatus.Error
                    executable = null; broker?.close(); broker = null; RootWritePolicy.clear(); generation.incrementAndGet(); notifyRevoked()
                }
                Raw(raw.exit?.takeIf { it != 0 } ?: -1, byteArrayOf(), "未进入root会话：${raw.diagnostic.take(500)}", raw.timedOut)
            } else {
                if (raw.timedOut) {
                    if (preserveSessionOnTimeout) {
                        val suIfDead = synchronized(processLock) {
                            if (epoch == generation.get() && executorForTests == null && broker?.alive != true) executable else null
                        }
                        if (suIfDead != null) {
                            val reopened = runCatching { withContext(Dispatchers.IO) { LibsuRootTransport.open(suIfDead) } }.getOrNull()
                            synchronized(processLock) {
                                if (epoch == generation.get()) {
                                    if (reopened != null) broker = reopened
                                    else {
                                        mutableStatus.value = RootStatus.TimedOut; executable = null; broker?.close(); broker = null; RootWritePolicy.clear(); generation.incrementAndGet(); notifyRevoked()
                                    }
                                } else {
                                    reopened?.close()
                                }
                            }
                        }
                    } else synchronized(processLock) {
                        if (epoch == generation.get()) { mutableStatus.value = RootStatus.TimedOut; executable = null; broker?.close(); broker = null; RootWritePolicy.clear(); generation.incrementAndGet(); notifyRevoked() }
                    }
                }
                raw.copy(diagnostic = raw.diagnostic.replace("$marker:0\n", ""))
            }
        }
    }

    private suspend fun perform(su: String, command: String, input: ByteArray?, maxBytes: Int, timeoutMs: Long, marker: String? = null, epoch: Long): Raw {
        currentCoroutineContext().ensureActive()
        if (epoch != generation.get()) return Raw(null, byteArrayOf(), "root会话已结束")
        return try {
            val fake = executorForTests
            if (fake == null) execute(su, command, input, maxBytes, timeoutMs, epoch) else {
                val result = fake.execute(RootProcessRequest(su, command, input, maxBytes, timeoutMs, marker))
                if (result.bytes.size > maxBytes) Raw(null, byteArrayOf(), result.stderr + "root输出超过单次资源上限")
                else Raw(result.exitCode, result.bytes, result.stderr, result.timedOut)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Cancel this startup without treating it as a user revocation of an earlier grant.
            synchronized(processLock) {
                if (epoch == generation.get() && status.value != RootStatus.Granted) {
                    generation.incrementAndGet()
                    mutableStatus.value = if (captureWasGranted) RootStatus.Error else RootStatus.NotRequested
                    executable = null; broker?.close(); broker = null
                    RootWritePolicy.clear(); notifyRevoked()
                }
            }
            throw e
        }
        catch (e: Exception) {
            if (epoch != generation.get()) return Raw(null, byteArrayOf(), "Root会话已结束，旧管道错误已丢弃")
            val timeout = generateSequence<Throwable>(e) { it.cause }.any { it is java.util.concurrent.TimeoutException }
            val next = if (timeout) RootStatus.TimedOut else if (e is com.topjohnwu.superuser.NoShellException) RootStatus.Denied else RootStatus.Error
            synchronized(processLock) {
                if (epoch != generation.get()) return Raw(null, byteArrayOf(), "Root会话已结束，旧管道错误已丢弃")
                mutableStatus.value = next; executable = null; broker?.close(); broker = null
                RootWritePolicy.clear(); generation.incrementAndGet(); notifyRevoked()
            }
            Raw(null, byteArrayOf(), if (timeout) "Root授权/命令超时，已结束会话" else e.message ?: "Root进程失败", timeout)
        }
    }

    /** The sole su process survives ordinary overlay recreation; only explicit end/failure closes it. */
    private suspend fun execute(su: String, command: String, input: ByteArray?, maxBytes: Int, timeoutMs: Long, epoch: Long): Raw = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        if (epoch != generation.get()) return@withContext Raw(null, byteArrayOf(), "root会话已结束")
        check(input == null) { "Root协议不接受任意stdin；写入由类型化事务编码" }
        val connection = broker ?: LibsuRootTransport.open(su).also { started ->
            synchronized(processLock) {
                if (epoch != generation.get()) { started.close(); return@withContext Raw(null, byteArrayOf(), "root会话已结束") }
                broker = started
            }
        }
        val result = connection.execute(command, maxBytes, timeoutMs)
        Raw(result.exitCode, result.bytes, result.stderr, result.timedOut)
    }
}
