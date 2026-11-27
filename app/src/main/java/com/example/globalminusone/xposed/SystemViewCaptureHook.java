package com.example.globalminusone.xposed;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Binder;
import android.os.Build;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.WindowManager;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Experimental system_server hook (scope MUST be limited to "android" only).
 * Never injects into third-party app processes. Exposes a strictly UID-authenticated
 * Binder transaction on WindowManagerService to invoke AOSP IWindow.executeCommand
 * ("DUMP" / "CAPTURE") outside mGlobalLock with strict timeouts and byte caps.
 */
public final class SystemViewCaptureHook implements IXposedHookLoadPackage {
    public static final int TRANSACTION_CODE = 0x474D4F56; // 'GMOV'
    public static final String DESCRIPTOR = "com.example.globalminusone.xposed.ISystemViewCapture";
    public static final int PROTOCOL_VERSION = 1;

    public static final int OP_PING = 1;
    public static final int OP_DUMP_TOP_WINDOW = 2;
    public static final int OP_CAPTURE_VIEWS = 3;

    public static final int STATUS_OK = 0;
    public static final int STATUS_BUSY = 1;
    public static final int STATUS_NO_WINDOW = 2;
    public static final int STATUS_FAILED = 3;
    public static final int STATUS_INTERNAL_ERROR = 4;

    private static final String ANDROID_PKG = "android";
    private static final String SELF_PKG = "com.example.globalminusone";
    private static final String SYSTEMUI_PKG = "com.android.systemui";
    private static final byte[] STREAM_MAGIC = new byte[]{'G', 'M', 'O', 'X'};

    private static final long DUMP_TIMEOUT_MS = 1_200L;
    private static final long CAPTURE_PER_VIEW_TIMEOUT_MS = 800L;
    private static final long CAPTURE_TOTAL_BUDGET_MS = 3_200L;
    private static final int MAX_DUMP_BYTES = 2 * 1024 * 1024;
    private static final int MAX_PNG_BYTES = 4 * 1024 * 1024;
    private static final int MAX_BATCH_BYTES = 12 * 1024 * 1024;
    private static final int MAX_CAPTURE_VIEWS = 12;
    private static final int MAX_VISIBLE_WINDOWS_TO_TRY = 2;

    private static final Pattern SAFE_VIEW_DESCRIPTOR =
            Pattern.compile("^[A-Za-z0-9_.$]{1,180}@[0-9a-fA-F]{1,8}$");

    private static final AtomicBoolean IN_FLIGHT = new AtomicBoolean(false);

    private static final ExecutorService WORKER_POOL = new ThreadPoolExecutor(
            0, 2, 15L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(8),
            new NamedDaemonThreadFactory("gmo-view-worker"), new ThreadPoolExecutor.AbortPolicy());
    private static final ExecutorService PIPE_READER_POOL = new ThreadPoolExecutor(
            0, 3, 20L, TimeUnit.SECONDS, new SynchronousQueue<>(),
            new NamedDaemonThreadFactory("gmo-view-pipe"), new ThreadPoolExecutor.AbortPolicy());
    private static final ExecutorService COMMAND_INVOKE_POOL = new ThreadPoolExecutor(
            0, 4, 20L, TimeUnit.SECONDS, new SynchronousQueue<>(),
            new NamedDaemonThreadFactory("gmo-view-exec"), new ThreadPoolExecutor.AbortPolicy());

    private volatile int cachedAppUid = -1;
    private volatile long cachedAppUidAtMs = 0L;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            if (lpparam == null
                    || !ANDROID_PKG.equals(lpparam.packageName)
                    || !ANDROID_PKG.equals(lpparam.processName)) {
                return;
            }
            XposedHelpers.findAndHookMethod(
                    "com.android.server.wm.WindowManagerService",
                    lpparam.classLoader,
                    "onTransact",
                    int.class,
                    Parcel.class,
                    Parcel.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            handleWmsOnTransact(param);
                        }
                    }
            );
            XposedBridge.log("GMO: hooked WindowManagerService.onTransact in system_server");
        } catch (Throwable err) {
            XposedBridge.log(err);
        }
    }

    private void handleWmsOnTransact(XC_MethodHook.MethodHookParam param) {
        // Ultra-fast path: never allocate or touch parcels unless transaction code matches 'GMOV'.
        if (param == null || param.args == null || param.args.length < 3) {
            return;
        }
        Object arg0 = param.args[0];
        if (!(arg0 instanceof Integer) || ((Integer) arg0) != TRANSACTION_CODE) {
            return;
        }
        Object arg1 = param.args[1];
        if (!(arg1 instanceof Parcel)) {
            return;
        }
        Parcel data = (Parcel) arg1;
        Parcel reply = param.args[2] instanceof Parcel ? (Parcel) param.args[2] : null;
        final int startPos = data.dataPosition();
        try {
            data.enforceInterface(DESCRIPTOR);
        } catch (SecurityException mismatch) {
            data.setDataPosition(startPos);
            return;
        }

        // From here on this transaction strictly belongs to our bridge.
        param.setResult(Boolean.TRUE);
        ParcelFileDescriptor outPfd = null;
        try {
            int callingUid = Binder.getCallingUid();
            int selfUid = resolveSelfAppUid(param.thisObject);
            if (callingUid < 10000 || selfUid < 10000 || callingUid != selfUid) {
                writeReplyException(reply, new SecurityException("Unauthorized GMO caller uid=" + callingUid));
                return;
            }

            int op = data.readInt();
            switch (op) {
                case OP_PING: {
                    if (reply != null) {
                        reply.writeNoException();
                        reply.writeInt(PROTOCOL_VERSION);
                        reply.writeInt(Build.VERSION.SDK_INT);
                    }
                    return;
                }
                case OP_DUMP_TOP_WINDOW: {
                    outPfd = ParcelFileDescriptor.CREATOR.createFromParcel(data);
                    if (!IN_FLIGHT.compareAndSet(false, true)) {
                        writeDumpErrorAndClose(outPfd, STATUS_BUSY, "已有正在进行的视图采集");
                        outPfd = null;
                        writeReplyOk(reply, STATUS_BUSY);
                        return;
                    }
                    final ParcelFileDescriptor targetPfd = outPfd;
                    final Object wms = param.thisObject;
                    try {
                        WORKER_POOL.execute(() -> executeDumpTopWindow(wms, targetPfd));
                        outPfd = null; // Owned and closed by worker.
                        writeReplyOk(reply, STATUS_OK);
                    } catch (RejectedExecutionException rejected) {
                        IN_FLIGHT.set(false);
                        writeDumpErrorAndClose(targetPfd, STATUS_BUSY, "系统采集队列繁忙");
                        outPfd = null;
                        writeReplyOk(reply, STATUS_BUSY);
                    }
                    return;
                }
                case OP_CAPTURE_VIEWS: {
                    String targetPkg = data.readString();
                    String[] rawTokens = data.createStringArray();
                    outPfd = ParcelFileDescriptor.CREATOR.createFromParcel(data);
                    List<String> validTokens = sanitizeTokens(rawTokens);
                    if (targetPkg == null || targetPkg.trim().isEmpty() || validTokens.isEmpty()) {
                        writeCaptureErrorAndClose(outPfd, STATUS_FAILED, "缺少有效的目标视图描述符");
                        outPfd = null;
                        writeReplyOk(reply, STATUS_FAILED);
                        return;
                    }
                    if (!IN_FLIGHT.compareAndSet(false, true)) {
                        writeCaptureErrorAndClose(outPfd, STATUS_BUSY, "已有正在进行的单层提图");
                        outPfd = null;
                        writeReplyOk(reply, STATUS_BUSY);
                        return;
                    }
                    final ParcelFileDescriptor targetPfd = outPfd;
                    final Object wms = param.thisObject;
                    final String cleanPkg = targetPkg.trim();
                    try {
                        WORKER_POOL.execute(() -> executeCaptureViews(wms, cleanPkg, validTokens, targetPfd));
                        outPfd = null; // Owned and closed by worker.
                        writeReplyOk(reply, STATUS_OK);
                    } catch (RejectedExecutionException rejected) {
                        IN_FLIGHT.set(false);
                        writeCaptureErrorAndClose(targetPfd, STATUS_BUSY, "系统提图队列繁忙");
                        outPfd = null;
                        writeReplyOk(reply, STATUS_BUSY);
                    }
                    return;
                }
                default: {
                    writeReplyException(reply, new IllegalArgumentException("Unknown GMO op=" + op));
                }
            }
        } catch (Throwable err) {
            closeQuietly(outPfd);
            XposedBridge.log(err);
            writeReplyException(reply, new IllegalStateException(
                    err.getMessage() != null ? err.getMessage() : err.getClass().getSimpleName()
            ));
        }
    }

    private int resolveSelfAppUid(Object wms) {
        long now = SystemClock.elapsedRealtime();
        int prev = cachedAppUid;
        if (prev >= 10000 && now - cachedAppUidAtMs < 5_000L) {
            return prev;
        }
        long id = Binder.clearCallingIdentity();
        try {
            Object ctxObj = getFieldAny(wms, "mContext");
            if (!(ctxObj instanceof Context)) {
                return -1;
            }
            PackageManager pm = ((Context) ctxObj).getPackageManager();
            int uid = pm != null ? pm.getPackageUid(SELF_PKG, 0) : -1;
            cachedAppUid = uid;
            cachedAppUidAtMs = now;
            return uid;
        } catch (Throwable ignored) {
            return -1;
        } finally {
            Binder.restoreCallingIdentity(id);
        }
    }

    private static List<String> sanitizeTokens(String[] rawTokens) {
        List<String> out = new ArrayList<>();
        if (rawTokens == null) return out;
        for (String tok : rawTokens) {
            if (tok == null) continue;
            String t = tok.trim();
            if (SAFE_VIEW_DESCRIPTOR.matcher(t).matches() && !out.contains(t)) {
                out.add(t);
                if (out.size() >= MAX_CAPTURE_VIEWS) break;
            }
        }
        return out;
    }

    private static final class WindowTarget {
        final String packageName;
        final Rect frame;
        final Object client;
        final Method executeCommandMethod;

        WindowTarget(String packageName, Rect frame, Object client, Method executeCommandMethod) {
            this.packageName = packageName;
            this.frame = frame;
            this.client = client;
            this.executeCommandMethod = executeCommandMethod;
        }
    }

    private static void executeDumpTopWindow(Object wms, ParcelFileDescriptor outPfd) {
        long identity = Binder.clearCallingIdentity();
        try {
            List<WindowTarget> targets = snapshotVisibleAppWindows(wms, null, MAX_VISIBLE_WINDOWS_TO_TRY);
            if (targets.isEmpty()) {
                writeDumpResponse(outPfd, STATUS_NO_WINDOW, "未检测到前台可见应用窗口", "", new Rect(), new byte[0]);
                return;
            }
            String lastErr = "窗口未返回视图树";
            for (WindowTarget target : targets) {
                RemoteExecResult res = invokeRemoteExecuteCommand(
                        target,
                        "DUMP",
                        "",
                        DUMP_TIMEOUT_MS,
                        MAX_DUMP_BYTES,
                        true
                );
                if (res.bytes.length > 0 && res.bytes.length <= MAX_DUMP_BYTES) {
                    writeDumpResponse(outPfd, STATUS_OK, "ok", target.packageName, target.frame, res.bytes);
                    return;
                }
                if (res.error != null && !res.error.isEmpty()) {
                    lastErr = target.packageName + ": " + res.error;
                }
            }
            writeDumpResponse(outPfd, STATUS_FAILED, lastErr, targets.get(0).packageName, targets.get(0).frame, new byte[0]);
        } catch (Throwable err) {
            writeDumpErrorAndClose(outPfd, STATUS_INTERNAL_ERROR,
                    "DUMP 异常: " + (err.getMessage() != null ? err.getMessage() : err.getClass().getSimpleName()));
            outPfd = null;
        } finally {
            Binder.restoreCallingIdentity(identity);
            closeQuietly(outPfd);
            IN_FLIGHT.set(false);
        }
    }

    private static void executeCaptureViews(
            Object wms,
            String targetPkg,
            List<String> descriptors,
            ParcelFileDescriptor outPfd
    ) {
        long identity = Binder.clearCallingIdentity();
        try {
            boolean hasTargetPkg = targetPkg != null && !targetPkg.isEmpty();
            List<WindowTarget> targets = snapshotVisibleAppWindows(
                    wms, hasTargetPkg ? targetPkg : null, hasTargetPkg ? 2 : 1);
            if (targets.isEmpty()) {
                writeCaptureErrorAndClose(outPfd, STATUS_NO_WINDOW, "目标窗口已不可见");
                outPfd = null;
                return;
            }
            List<CapturedEntry> entries = new ArrayList<>(descriptors.size());
            long startNs = System.nanoTime();
            int totalPngBytes = 0;
            boolean[] timedOut = new boolean[targets.size()];

            for (String desc : descriptors) {
                boolean anyAlive = false;
                for (boolean t : timedOut) { if (!t) { anyAlive = true; break; } }
                if (!anyAlive) {
                    entries.add(new CapturedEntry(desc, 2, "目标窗口响应超时，已跳过", new byte[0]));
                    continue;
                }
                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
                long remainingMs = CAPTURE_TOTAL_BUDGET_MS - elapsedMs;
                if (remainingMs < 150L) {
                    entries.add(new CapturedEntry(desc, 2, "总预算超时", new byte[0]));
                    continue;
                }
                if (totalPngBytes >= MAX_BATCH_BYTES) {
                    entries.add(new CapturedEntry(desc, 2, "超出单次字节上限", new byte[0]));
                    continue;
                }
                long callTimeout = Math.min(CAPTURE_PER_VIEW_TIMEOUT_MS, remainingMs);
                int maxPerItem = Math.min(MAX_PNG_BYTES, MAX_BATCH_BYTES - totalPngBytes);

                RemoteExecResult res = new RemoteExecResult(new byte[0], "目标视图未返回图像", false);
                for (int i = 0; i < targets.size(); i++) {
                    if (timedOut[i]) continue;
                    long tOut = i == 0 ? callTimeout : Math.min(callTimeout, 500L);
                    res = invokeRemoteExecuteCommand(targets.get(i), "CAPTURE", desc, tOut, maxPerItem, false);
                    if (res.timedOut) timedOut[i] = true;
                    if (res.bytes.length > 0) break;
                }
                if (res.bytes.length > 0 && looksLikePng(res.bytes)) {
                    totalPngBytes += res.bytes.length;
                    entries.add(new CapturedEntry(desc, 0, "ok", res.bytes));
                } else {
                    String errMsg = (res.error != null && !res.error.isEmpty())
                            ? res.error
                            : (res.bytes.length > 0 ? "返回非 PNG 数据" : "目标视图未返回图像");
                    entries.add(new CapturedEntry(desc, 1, errMsg, new byte[0]));
                }
            }
            writeCaptureResponse(outPfd, STATUS_OK, "ok", entries);
        } catch (Throwable err) {
            writeCaptureErrorAndClose(outPfd, STATUS_INTERNAL_ERROR,
                    "CAPTURE 异常: " + (err.getMessage() != null ? err.getMessage() : err.getClass().getSimpleName()));
            outPfd = null;
        } finally {
            Binder.restoreCallingIdentity(identity);
            closeQuietly(outPfd);
            IN_FLIGHT.set(false);
        }
    }

    /**
     * Reads ONLY lightweight WindowTarget references inside mGlobalLock and returns immediately.
     * ZERO Binder IPC or stream I/O ever occurs while mGlobalLock is held.
     */
    private static List<WindowTarget> snapshotVisibleAppWindows(
            Object wms,
            String requiredPkg,
            int maxCount
    ) {
        final List<WindowTarget> result = new ArrayList<>(maxCount);
        if (wms == null) return result;

        Object lock = getFieldAny(wms, "mGlobalLock");
        if (lock == null) {
            lock = getFieldAny(wms, "mWindowMap");
        }
        if (lock == null) {
            lock = wms;
        }

        synchronized (lock) {
            Object root = getFieldAny(wms, "mRoot");
            boolean traversed = false;
            if (root != null) {
                Method forAllConsumer = findMethodByArityAndParam(root.getClass(), "forAllWindows", Consumer.class, boolean.class);
                if (forAllConsumer != null) {
                    traversed = true;
                    try {
                        forAllConsumer.invoke(root, (Consumer<Object>) win -> {
                            if (result.size() < maxCount) {
                                collectWindowIfEligible(win, requiredPkg, result);
                            }
                        }, Boolean.TRUE);
                    } catch (Throwable ignored) {
                        traversed = false;
                    }
                }
            }
            if (!traversed) {
                Object winMapObj = getFieldAny(wms, "mWindowMap");
                if (winMapObj instanceof Map) {
                    Collection<?> vals = ((Map<?, ?>) winMapObj).values();
                    for (Object win : vals) {
                        if (result.size() >= maxCount) break;
                        collectWindowIfEligible(win, requiredPkg, result);
                    }
                }
            }
        }
        return result;
    }

    private static void collectWindowIfEligible(
            Object win,
            String requiredPkg,
            List<WindowTarget> out
    ) {
        if (win == null) return;
        try {
            Object attrsObj = getFieldAny(win, "mAttrs");
            if (!(attrsObj instanceof WindowManager.LayoutParams)) return;
            WindowManager.LayoutParams attrs = (WindowManager.LayoutParams) attrsObj;
            // Application windows (1..99) and attached sub-windows/dialogs (1000..1999).
            if (attrs.type < 1 || attrs.type >= 2000) return;

            Integer displayId = callIntNoArg(win, "getDisplayId");
            if (displayId != null && displayId != 0) return;

            Integer viewVis = getIntField(win, "mViewVisibility");
            if (viewVis != null && viewVis != 0) return;

            Boolean visible = callBooleanNoArg(win, "isVisible");
            if (visible == null) visible = callBooleanNoArg(win, "isVisibleNow");
            if (visible != null && !visible) return;

            String pkg = callStringNoArg(win, "getOwningPackage");
            if ((pkg == null || pkg.isEmpty()) && attrs.packageName != null) {
                pkg = attrs.packageName;
            }
            if (pkg == null || pkg.isEmpty() || SELF_PKG.equals(pkg) || SYSTEMUI_PKG.equals(pkg)) return;
            if (requiredPkg != null && !requiredPkg.equals(pkg)) return;

            Rect frame = extractWindowFrame(win);
            if (frame == null || frame.isEmpty() || frame.width() < 64 || frame.height() < 64) return;

            Object client = getFieldAny(win, "mClient");
            if (client == null) return;
            Method execMethod = client.getClass().getMethod(
                    "executeCommand", String.class, String.class, ParcelFileDescriptor.class
            );
            execMethod.setAccessible(true);
            out.add(new WindowTarget(pkg, new Rect(frame), client, execMethod));
        } catch (Throwable ignored) {
        }
    }

    private static Rect extractWindowFrame(Object win) {
        Object direct = callNoArg(win, "getFrame");
        if (direct instanceof Rect) return (Rect) direct;
        Object wf = getFieldAny(win, "mWindowFrames");
        if (wf != null) {
            Object mf = getFieldAny(wf, "mFrame");
            if (mf instanceof Rect) return (Rect) mf;
        }
        Object legacy = getFieldAny(win, "mFrame");
        return legacy instanceof Rect ? (Rect) legacy : null;
    }

    private static final class RemoteExecResult {
        final byte[] bytes;
        final String error;
        final boolean timedOut;

        RemoteExecResult(byte[] bytes, String error, boolean timedOut) {
            this.bytes = bytes;
            this.error = error;
            this.timedOut = timedOut;
        }
    }

    private static RemoteExecResult invokeRemoteExecuteCommand(
            WindowTarget target,
            String cmd,
            String arg,
            long timeoutMs,
            int maxBytes,
            boolean stopOnDumpDone
    ) {
        ParcelFileDescriptor[] pipe;
        try {
            pipe = ParcelFileDescriptor.createPipe();
        } catch (IOException io) {
            return new RemoteExecResult(new byte[0], "创建内部管道失败", false);
        }
        final ParcelFileDescriptor readSide = pipe[0];
        final ParcelFileDescriptor writeSide = pipe[1];
        Future<Void> execFut = null;
        Future<byte[]> readFut = null;
        try {
            execFut = COMMAND_INVOKE_POOL.submit(() -> {
                try { target.executeCommandMethod.invoke(target.client, cmd, arg, writeSide); }
                finally { closeQuietly(writeSide); }
                return null;
            });
            readFut = PIPE_READER_POOL.submit(() -> readBoundedPipe(readSide, maxBytes, stopOnDumpDone));
            byte[] payload = readFut.get(timeoutMs, TimeUnit.MILLISECONDS);
            if ((payload == null || payload.length == 0) && execFut.isDone()) execFut.get(50L, TimeUnit.MILLISECONDS);
            return new RemoteExecResult(payload != null ? payload : new byte[0], "", false);
        } catch (TimeoutException te) {
            if (execFut != null) execFut.cancel(true);
            if (readFut != null) readFut.cancel(true);
            closeQuietly(writeSide);
            closeQuietly(readSide);
            return new RemoteExecResult(new byte[0], cmd + " 超时 (" + timeoutMs + "ms)", true);
        } catch (Throwable err) {
            if (execFut != null) execFut.cancel(true);
            if (readFut != null) readFut.cancel(true);
            Throwable root = err.getCause() != null ? err.getCause() : err;
            String msg = root.getMessage();
            return new RemoteExecResult(new byte[0], msg != null && !msg.isEmpty() ? msg : root.getClass().getSimpleName(), false);
        } finally { closeQuietly(writeSide); closeQuietly(readSide); }
    }

    private static byte[] readBoundedPipe(
            ParcelFileDescriptor readSide,
            int maxBytes,
            boolean stopOnDumpDone
    ) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
        byte[] buf = new byte[16384];
        byte[] tail = new byte[8];
        int tailLen = 0;
        try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(readSide)) {
            int n;
            while ((n = in.read(buf)) >= 0) {
                if (n == 0) continue;
                int remain = maxBytes - out.size();
                if (remain <= 0) break;
                int toWrite = Math.min(n, remain);
                out.write(buf, 0, toWrite);
                if (stopOnDumpDone) {
                    if (toWrite >= tail.length) {
                        System.arraycopy(buf, toWrite - tail.length, tail, 0, tail.length);
                        tailLen = tail.length;
                    } else {
                        int keep = Math.min(tailLen, tail.length - toWrite);
                        if (keep > 0) {
                            System.arraycopy(tail, tailLen - keep, tail, 0, keep);
                        }
                        System.arraycopy(buf, 0, tail, keep, toWrite);
                        tailLen = keep + toWrite;
                    }
                    if (endsWithDumpDone(tail, tailLen)) {
                        break;
                    }
                }
                if (toWrite < n) break;
            }
        }
        return out.toByteArray();
    }

    private static boolean endsWithDumpDone(byte[] tail, int len) {
        if (len >= 6
                && tail[len - 6] == 'D' && tail[len - 5] == 'O' && tail[len - 4] == 'N'
                && tail[len - 3] == 'E' && tail[len - 2] == '.' && tail[len - 1] == '\n') {
            return true;
        }
        return len >= 5
                && tail[len - 5] == 'D' && tail[len - 4] == 'O' && tail[len - 3] == 'N'
                && tail[len - 2] == 'E' && tail[len - 1] == '.';
    }

    private static boolean looksLikePng(byte[] b) {
        return b != null && b.length >= 24
                && (b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A;
    }

    private static final class CapturedEntry {
        final String descriptor;
        final int errCode;
        final String errMsg;
        final byte[] pngBytes;

        CapturedEntry(String descriptor, int errCode, String errMsg, byte[] pngBytes) {
            this.descriptor = descriptor;
            this.errCode = errCode;
            this.errMsg = errMsg;
            this.pngBytes = pngBytes;
        }
    }

    private static void writeDumpErrorAndClose(ParcelFileDescriptor pfd, int status, String msg) {
        try {
            writeDumpResponse(pfd, status, msg, "", new Rect(), new byte[0]);
        } catch (Throwable ignored) {
        } finally {
            closeQuietly(pfd);
        }
    }

    private static void writeDumpResponse(
            ParcelFileDescriptor outPfd,
            int status,
            String diagnostic,
            String pkg,
            Rect frame,
            byte[] dumpBytes
    ) throws IOException {
        try (DataOutputStream dos = new DataOutputStream(new ParcelFileDescriptor.AutoCloseOutputStream(outPfd))) {
            dos.write(STREAM_MAGIC);
            dos.writeInt(status);
            writeUtf8(dos, diagnostic);
            writeUtf8(dos, pkg);
            dos.writeInt(frame.left);
            dos.writeInt(frame.top);
            dos.writeInt(frame.right);
            dos.writeInt(frame.bottom);
            dos.writeInt(dumpBytes.length);
            if (dumpBytes.length > 0) {
                dos.write(dumpBytes);
            }
            dos.flush();
        }
    }

    private static void writeCaptureErrorAndClose(ParcelFileDescriptor pfd, int status, String msg) {
        try {
            writeCaptureResponse(pfd, status, msg, new ArrayList<>());
        } catch (Throwable ignored) {
        } finally {
            closeQuietly(pfd);
        }
    }

    private static void writeCaptureResponse(
            ParcelFileDescriptor outPfd,
            int status,
            String diagnostic,
            List<CapturedEntry> entries
    ) throws IOException {
        try (DataOutputStream dos = new DataOutputStream(new ParcelFileDescriptor.AutoCloseOutputStream(outPfd))) {
            dos.write(STREAM_MAGIC);
            dos.writeInt(status);
            writeUtf8(dos, diagnostic);
            dos.writeInt(entries.size());
            for (CapturedEntry entry : entries) {
                writeUtf8(dos, entry.descriptor);
                dos.writeInt(entry.errCode);
                writeUtf8(dos, entry.errMsg);
                dos.writeInt(entry.pngBytes.length);
                if (entry.pngBytes.length > 0) {
                    dos.write(entry.pngBytes);
                }
            }
            dos.flush();
        }
    }

    private static void writeUtf8(DataOutputStream dos, String s) throws IOException {
        if (s == null || s.isEmpty()) {
            dos.writeInt(0);
            return;
        }
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        dos.writeInt(bytes.length);
        dos.write(bytes);
    }

    private static void writeReplyOk(Parcel reply, int code) {
        if (reply != null) {
            reply.writeNoException();
            reply.writeInt(code);
        }
    }

    private static void writeReplyException(Parcel reply, RuntimeException ex) {
        if (reply != null) {
            reply.writeException(ex);
        }
    }

    private static void closeQuietly(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static Object getFieldAny(Object target, String name) {
        if (target == null) return null;
        Class<?> cls = target.getClass();
        while (cls != null && cls != Object.class) {
            try {
                Field f = cls.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException ignored) {
                cls = cls.getSuperclass();
            } catch (Throwable err) {
                return null;
            }
        }
        return null;
    }

    private static Integer getIntField(Object target, String name) {
        Object v = getFieldAny(target, name);
        return v instanceof Number ? ((Number) v).intValue() : null;
    }

    private static Object callNoArg(Object target, String name) {
        if (target == null) return null;
        try {
            Method m = target.getClass().getMethod(name);
            m.setAccessible(true);
            return m.invoke(target);
        } catch (Throwable ignored) {
            Class<?> cls = target.getClass();
            while (cls != null && cls != Object.class) {
                try {
                    Method m = cls.getDeclaredMethod(name);
                    m.setAccessible(true);
                    return m.invoke(target);
                } catch (NoSuchMethodException next) {
                    cls = cls.getSuperclass();
                } catch (Throwable err) {
                    return null;
                }
            }
            return null;
        }
    }

    private static Boolean callBooleanNoArg(Object target, String name) {
        Object v = callNoArg(target, name);
        return v instanceof Boolean ? (Boolean) v : null;
    }

    private static Integer callIntNoArg(Object target, String name) {
        Object v = callNoArg(target, name);
        return v instanceof Number ? ((Number) v).intValue() : null;
    }

    private static String callStringNoArg(Object target, String name) {
        Object v = callNoArg(target, name);
        return v != null ? v.toString() : null;
    }

    private static Method findMethodByArityAndParam(
            Class<?> rootCls,
            String name,
            Class<?> p0,
            Class<?> p1
    ) {
        Class<?> cls = rootCls;
        while (cls != null && cls != Object.class) {
            for (Method m : cls.getDeclaredMethods()) {
                if (!m.getName().equals(name)) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length == 2 && pts[0].isAssignableFrom(p0) && pts[1] == p1) {
                    m.setAccessible(true);
                    return m;
                }
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    private static final class NamedDaemonThreadFactory implements ThreadFactory {
        private final String prefix;
        private int count = 0;

        NamedDaemonThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + "-" + (++count));
            t.setDaemon(true);
            return t;
        }
    }
}
