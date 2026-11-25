package com.example.globalminusone.screenassistant;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.UiAutomation;
import android.content.Context;
import android.graphics.Rect;
import android.os.Binder;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import androidx.annotation.Keep;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Single Shizuku UserService binder handling screen capture, in-process UiAutomation true-bounds tree dump,
 * and native OCR RPC.
 */
@Keep
public final class ShizukuPrivilegedService extends Binder {
    public static final String DESCRIPTOR = "com.example.globalminusone.screenassistant.IShizukuPrivilegedService";
    public static final int OP_CAPTURE_SCREEN = 1;
    public static final int OP_DUMP_UI_TREE = 9;
    public static final int OP_DUMP_XML = 3;
    public static final int OP_OCR_HAS_FILE = 5;
    public static final int OP_OCR_SYNC_FILE = 6;
    public static final int OP_OCR_RPC = 7;
    public static final int OP_OCR_SHUTDOWN = 8;

    public static final int MAX_CAPTURE_BYTES = 24 * 1024 * 1024;

    private static final int MAX_UI_NODES = 1600;
    private static final int MAX_UI_DEPTH = 48;
    private static final String SELF_PKG = "com.example.globalminusone";
    private static final String SYSTEMUI_PKG = "com.android.systemui";
    private static final String WORK_ROOT = "/data/local/tmp/gmo-ocr-v1";
    private static final Pattern SAFE_NAME = Pattern.compile("^[a-zA-Z0-9._-]{1,96}$");
    private static final Pattern SAFE_SHA = Pattern.compile("^[a-f0-9]{64}$");

    private final int ownerUid;
    private final Object ocrLock = new Object();
    private java.lang.Process ocrDaemon;
    private BufferedWriter ocrWriter;
    private BufferedReader ocrReader;

    @Keep
    public ShizukuPrivilegedService() {
        this.ownerUid = Process.myUid();
    }

    @Keep
    public ShizukuPrivilegedService(Context context) {
        this.ownerUid = context.getApplicationInfo().uid;
    }

    @Keep
    public static void main(String[] args) {
        if (args != null && args.length > 0 && "--dump-ui".equals(args[0])) {
            try {
                dumpUiAutomationToStream(System.out);
                System.out.flush();
                System.exit(0);
                return;
            } catch (Throwable err) {
                System.err.println(err.toString());
                System.exit(1);
                return;
            }
        }
        System.exit(2);
    }

    public void destroy() {
        stopOcrDaemon();
        System.exit(0);
    }

    @Override
    protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        if (code == INTERFACE_TRANSACTION) {
            reply.writeString(DESCRIPTOR);
            return true;
        }
        int callingUid = Binder.getCallingUid();
        if (callingUid != ownerUid && callingUid != Process.myUid()) {
            throw new SecurityException("Unauthorized Shizuku caller");
        }
        if (code == 16777115) {
            destroy();
            return true;
        }
        if (code != IBinder.FIRST_CALL_TRANSACTION) {
            return super.onTransact(code, data, reply, flags);
        }
        data.enforceInterface(DESCRIPTOR);
        int op = data.readInt();
        long identity = Binder.clearCallingIdentity();
        try {
            switch (op) {
                case OP_CAPTURE_SCREEN:
                    handlePipeCommand(data, reply, new String[]{"/system/bin/screencap", "-p"}, 6000L, MAX_CAPTURE_BYTES);
                    return true;
                case OP_DUMP_UI_TREE:
                    handleDumpUiTree(data, reply);
                    return true;
                case OP_DUMP_XML:
                    handlePipeCommand(
                            data,
                            reply,
                            new String[]{
                                    "/system/bin/sh",
                                    "-c",
                                    "f=/data/local/tmp/gmo_shz_ui.xml; rm -f \"$f\"; " +
                                            "/system/bin/timeout -k 1s 2s /system/bin/uiautomator dump --compressed \"$f\" >/dev/null 2>&1 " +
                                            "&& /system/bin/cat \"$f\"; rm -f \"$f\""
                            },
                            4000L,
                            4 * 1024 * 1024
                    );
                    return true;
                case OP_OCR_HAS_FILE:
                    handleHasFile(data, reply);
                    return true;
                case OP_OCR_SYNC_FILE:
                    handleSyncFile(data, reply);
                    return true;
                case OP_OCR_RPC:
                    handleOcrRpc(data, reply);
                    return true;
                case OP_OCR_SHUTDOWN:
                    stopOcrDaemon();
                    reply.writeNoException();
                    return true;
                default:
                    throw new SecurityException("Unknown Shizuku operation: " + op);
            }
        } catch (Throwable err) {
            if ((flags & IBinder.FLAG_ONEWAY) == 0) {
                reply.writeException(new IllegalStateException(err.getMessage() != null ? err.getMessage() : "Shizuku IPC failed"));
            }
            return true;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private void handlePipeCommand(
            Parcel data,
            Parcel reply,
            String[] command,
            long timeoutMs,
            int maxBytes
    ) throws Exception {
        ParcelFileDescriptor outPfd = ParcelFileDescriptor.CREATOR.createFromParcel(data);
        java.lang.Process proc = null;
        int exit = -1;
        try (OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(outPfd)) {
            proc = new ProcessBuilder(command).start();
            byte[] buf = new byte[16384];
            int total = 0;
            try (InputStream in = proc.getInputStream()) {
                int n;
                while ((n = in.read(buf)) >= 0) {
                    if (n == 0) continue;
                    total += n;
                    if (total > maxBytes) {
                        proc.destroy();
                        break;
                    }
                    out.write(buf, 0, n);
                }
            }
            out.flush();
            if (proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                exit = proc.exitValue();
            } else {
                proc.destroy();
            }
        } finally {
            if (proc != null) proc.destroy();
        }
        reply.writeNoException();
        reply.writeInt(exit);
    }

    private void handleDumpUiTree(Parcel data, Parcel reply) throws Exception {
        ParcelFileDescriptor outPfd = ParcelFileDescriptor.CREATOR.createFromParcel(data);
        try (OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(outPfd)) {
            int count = dumpUiAutomationToStream(out);
            reply.writeNoException();
            reply.writeInt(count);
        }
    }

    @android.annotation.SuppressLint({"SoonBlockedPrivateApi", "PrivateApi", "DiscouragedPrivateApi"})
    static int dumpUiAutomationToStream(OutputStream out) throws Exception {
        StringBuilder sb = new StringBuilder(65536);
        sb.append("GMOU1\n");
        int[] emitted = new int[]{0};
        HandlerThread ht = new HandlerThread("gmo-uiauto");
        ht.start();
        Object uaObj = null;
        try {
            Class<?> connCls = Class.forName("android.app.UiAutomationConnection");
            Constructor<?> connCtor = connCls.getDeclaredConstructor();
            connCtor.setAccessible(true);
            Object conn = connCtor.newInstance();
            Class<?> iConnCls = Class.forName("android.app.IUiAutomationConnection");
            Constructor<UiAutomation> uaCtor = UiAutomation.class.getDeclaredConstructor(Looper.class, iConnCls);
            uaCtor.setAccessible(true);
            UiAutomation ua = uaCtor.newInstance(ht.getLooper(), conn);
            uaObj = ua;
            int flagDontSuppress = 1;
            try {
                flagDontSuppress = UiAutomation.class.getField("FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES").getInt(null);
            } catch (Throwable ignored) {
            }
            try {
                Method connectInt = UiAutomation.class.getDeclaredMethod("connect", int.class);
                connectInt.setAccessible(true);
                connectInt.invoke(ua, flagDontSuppress);
            } catch (NoSuchMethodException noFlags) {
                Method connectNoArg = UiAutomation.class.getDeclaredMethod("connect");
                connectNoArg.setAccessible(true);
                connectNoArg.invoke(ua);
            }
            AccessibilityServiceInfo info = ua.getServiceInfo();
            if (info != null) {
                info.flags |= AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                        | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                        | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
                ua.setServiceInfo(info);
            }

            List<AccessibilityNodeInfo> roots = new ArrayList<>();
            List<AccessibilityWindowInfo> windows = ua.getWindows();
            int activeLayer = 0;
            int maxWinW = 0;
            int maxWinH = 0;
            if (windows != null) {
                Rect wRect = new Rect();
                for (AccessibilityWindowInfo win : windows) {
                    if (win == null) continue;
                    win.getBoundsInScreen(wRect);
                    if (wRect.width() > maxWinW) maxWinW = wRect.width();
                    if (wRect.height() > maxWinH) maxWinH = wRect.height();
                    if (win.getType() == AccessibilityWindowInfo.TYPE_APPLICATION && win.getLayer() > activeLayer) {
                        activeLayer = win.getLayer();
                    }
                }
                long maxArea = (long) maxWinW * (long) maxWinH;
                for (AccessibilityWindowInfo win : windows) {
                    if (win == null) continue;
                    int type = win.getType();
                    if (type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                        AccessibilityNodeInfo wRoot = win.getRoot();
                        if (wRoot != null) {
                            CharSequence pkgCs = wRoot.getPackageName();
                            String wPkg = pkgCs != null ? pkgCs.toString() : "";
                            if (SELF_PKG.equals(wPkg) || SYSTEMUI_PKG.equals(wPkg)) {
                                recycleNodeSafely(wRoot);
                            } else {
                                roots.add(wRoot);
                            }
                        }
                        if (win.getLayer() <= activeLayer) continue;
                    }
                    boolean isOverlay = type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY
                            || type == AccessibilityWindowInfo.TYPE_SYSTEM
                            || (type == AccessibilityWindowInfo.TYPE_APPLICATION && win.getLayer() > activeLayer);
                    if (isOverlay) {
                        AccessibilityNodeInfo ovRoot = win.getRoot();
                        String ovPkg = "";
                        if (ovRoot != null) {
                            CharSequence ovCs = ovRoot.getPackageName();
                            if (ovCs != null) ovPkg = ovCs.toString();
                            recycleNodeSafely(ovRoot);
                        }
                        if (SELF_PKG.equals(ovPkg) || SYSTEMUI_PKG.equals(ovPkg)) continue;
                        win.getBoundsInScreen(wRect);
                        long area = (long) Math.max(0, wRect.width()) * (long) Math.max(0, wRect.height());
                        boolean sizeOk = (maxWinW <= 0 || wRect.width() <= (maxWinW * 75) / 100)
                                && (maxArea <= 0L || area <= (maxArea * 25L) / 100L);
                        if (wRect.width() >= 24 && wRect.height() >= 24 && sizeOk) {
                            sb.append("O\t").append(wRect.left).append('\t').append(wRect.top)
                                    .append('\t').append(wRect.right).append('\t').append(wRect.bottom).append('\n');
                        }
                    }
                }
            }
            if (roots.isEmpty()) {
                AccessibilityNodeInfo active = ua.getRootInActiveWindow();
                if (active != null) roots.add(active);
            }
            Rect scratchRect = new Rect();
            for (AccessibilityNodeInfo root : roots) {
                try {
                    walkUiNode(root, 0, scratchRect, emitted, sb);
                } finally {
                    recycleNodeSafely(root);
                }
            }
        } catch (Throwable ignored) {
            emitted[0] = 0;
        } finally {
            if (uaObj != null) {
                try {
                    Method disconnect = UiAutomation.class.getDeclaredMethod("disconnect");
                    disconnect.setAccessible(true);
                    disconnect.invoke(uaObj);
                } catch (Throwable ignored) {
                }
            }
            ht.quitSafely();
        }
        if (emitted[0] > 0) {
            byte[] payload = sb.toString().getBytes(StandardCharsets.UTF_8);
            out.write(payload);
            out.flush();
            return emitted[0];
        }
        streamFallbackXml(out);
        return 0;
    }

    private static void walkUiNode(
            AccessibilityNodeInfo node,
            int depth,
            Rect rect,
            int[] emitted,
            StringBuilder sb
    ) {
        if (node == null || depth > MAX_UI_DEPTH || emitted[0] >= MAX_UI_NODES) return;
        CharSequence pkgCs = node.getPackageName();
        String pkg = pkgCs != null ? cleanField(pkgCs.toString(), 128) : "";
        if (SELF_PKG.equals(pkg) || SYSTEMUI_PKG.equals(pkg)) return;
        if (!node.isVisibleToUser()) return;
        node.getBoundsInScreen(rect);
        if (rect.width() > 0 && rect.height() > 0) {
            int childCount = node.getChildCount();
            CharSequence clsCs = node.getClassName();
            String cls = clsCs != null ? cleanField(clsCs.toString(), 160) : "";
            String viewId = cleanField(node.getViewIdResourceName(), 160);
            CharSequence txtCs = node.getText();
            String text = txtCs != null ? cleanField(txtCs.toString(), 240) : "";
            CharSequence descCs = node.getContentDescription();
            String desc = descCs != null ? cleanField(descCs.toString(), 240) : "";
            sb.append("N\t")
                    .append(depth).append('\t')
                    .append(childCount).append('\t')
                    .append(rect.left).append('\t')
                    .append(rect.top).append('\t')
                    .append(rect.right).append('\t')
                    .append(rect.bottom).append('\t')
                    .append(pkg).append('\t')
                    .append(cls).append('\t')
                    .append(viewId).append('\t')
                    .append(text).append('\t')
                    .append(desc).append('\n');
            emitted[0]++;
        }
        int limit = Math.min(node.getChildCount(), 128);
        for (int i = 0; i < limit; i++) {
            if (emitted[0] >= MAX_UI_NODES) break;
            AccessibilityNodeInfo child = null;
            try {
                child = node.getChild(i);
                if (child != null) walkUiNode(child, depth + 1, rect, emitted, sb);
            } catch (Throwable ignored) {
            } finally {
                if (child != null) recycleNodeSafely(child);
            }
        }
    }

    private static String cleanField(String raw, int maxLen) {
        if (raw == null || raw.isEmpty()) return "";
        String cleaned = raw.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ').trim();
        return cleaned.length() > maxLen ? cleaned.substring(0, maxLen) : cleaned;
    }

    @SuppressWarnings("deprecation")
    private static void recycleNodeSafely(AccessibilityNodeInfo node) {
        try {
            node.recycle();
        } catch (Throwable ignored) {
        }
    }

    private static void streamFallbackXml(OutputStream out) {
        java.lang.Process proc = null;
        try {
            proc = new ProcessBuilder(
                    "/system/bin/sh",
                    "-c",
                    "f=/data/local/tmp/gmo_uia_fb_" + Process.myPid() + ".xml; rm -f \"$f\"; " +
                            "/system/bin/timeout -k 1s 2s /system/bin/uiautomator dump --compressed \"$f\" >/dev/null 2>&1 " +
                            "&& /system/bin/cat \"$f\"; rm -f \"$f\""
            ).start();
            byte[] buf = new byte[16384];
            int total = 0;
            try (InputStream in = proc.getInputStream()) {
                int n;
                while ((n = in.read(buf)) >= 0) {
                    if (n == 0) continue;
                    total += n;
                    if (total > 4 * 1024 * 1024) break;
                    out.write(buf, 0, n);
                }
            }
            out.flush();
            proc.waitFor(3500L, TimeUnit.MILLISECONDS);
        } catch (Throwable ignored) {
        } finally {
            if (proc != null) proc.destroy();
        }
    }

    private void handleHasFile(Parcel data, Parcel reply) throws Exception {
        synchronized (ocrLock) {
            File target = resolveSafeOcrTarget(data.readString(), data.readString());
            String sha = data.readString();
            boolean ok = SAFE_SHA.matcher(sha != null ? sha : "").matches() && matchesFileSha(target, sha);
            reply.writeNoException();
            reply.writeInt(ok ? 1 : 0);
        }
    }

    private void handleSyncFile(Parcel data, Parcel reply) throws Exception {
        synchronized (ocrLock) {
            File target = resolveSafeOcrTarget(data.readString(), data.readString());
            String sha = data.readString();
            boolean executable = data.readInt() == 1;
            ParcelFileDescriptor pfd = ParcelFileDescriptor.CREATOR.createFromParcel(data);
            if (!SAFE_SHA.matcher(sha != null ? sha : "").matches()) {
                pfd.close();
                throw new IllegalArgumentException("Invalid SHA-256");
            }
            File parent = target.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                pfd.close();
                throw new IllegalStateException("Failed to create OCR stage directory");
            }
            File tmp = new File(parent, target.getName() + ".tmp");
            try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
                 FileOutputStream out = new FileOutputStream(tmp)) {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] buf = new byte[65536];
                long total = 0L;
                int n;
                while ((n = in.read(buf)) >= 0) {
                    if (n == 0) continue;
                    total += n;
                    if (total > 128L * 1024L * 1024L) {
                        throw new IllegalStateException("File exceeds limit");
                    }
                    out.write(buf, 0, n);
                    md.update(buf, 0, n);
                }
                out.getFD().sync();
                String actual = toHex(md.digest());
                if (!actual.equals(sha)) {
                    throw new SecurityException("Synced file SHA-256 mismatch");
                }
            } catch (Throwable t) {
                tmp.delete();
                throw t;
            }
            if (!tmp.renameTo(target)) {
                tmp.delete();
                throw new IllegalStateException("Failed to commit OCR asset");
            }
            target.setReadable(true, false);
            if (executable) {
                target.setExecutable(true, false);
            }
            reply.writeNoException();
        }
    }

    private void handleOcrRpc(Parcel data, Parcel reply) throws Exception {
        synchronized (ocrLock) {
            boolean hasFrame = data.readInt() == 1;
            ParcelFileDescriptor framePfd = hasFrame ? ParcelFileDescriptor.CREATOR.createFromParcel(data) : null;
            String jsonLine = data.readString();
            if (jsonLine == null || jsonLine.indexOf('\n') >= 0 || jsonLine.indexOf('\r') >= 0) {
                if (framePfd != null) framePfd.close();
                throw new IllegalArgumentException("Invalid JSON-Line request");
            }
            if (framePfd != null) {
                File reqFile = new File(WORK_ROOT, "req.raw");
                try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(framePfd);
                     FileOutputStream out = new FileOutputStream(reqFile)) {
                    byte[] buf = new byte[65536];
                    long total = 0L;
                    int n;
                    while ((n = in.read(buf)) >= 0) {
                        if (n == 0) continue;
                        total += n;
                        if (total > 32L * 1024L * 1024L) {
                            throw new IllegalStateException("Frame exceeds limit");
                        }
                        out.write(buf, 0, n);
                    }
                }
            }
            ensureOcrDaemonStarted();
            ocrWriter.write(jsonLine);
            ocrWriter.write('\n');
            ocrWriter.flush();
            String responseLine = ocrReader.readLine();
            if (responseLine == null) {
                stopOcrDaemon();
                throw new IllegalStateException("gmo-ocr-native daemon exited unexpectedly");
            }
            reply.writeNoException();
            reply.writeString(responseLine);
        }
    }

    private void ensureOcrDaemonStarted() throws Exception {
        if (ocrDaemon != null && isProcessAlive(ocrDaemon) && ocrWriter != null && ocrReader != null) {
            return;
        }
        stopOcrDaemon();
        File exec = new File(WORK_ROOT, "gmo-ocr-native");
        File ort = new File(WORK_ROOT, "libonnxruntime.so");
        if (!exec.isFile() || !ort.isFile()) {
            throw new IllegalStateException("Native OCR binaries missing in " + WORK_ROOT);
        }
        exec.setExecutable(true, false);
        ProcessBuilder pb = new ProcessBuilder(exec.getAbsolutePath());
        pb.directory(new File(WORK_ROOT));
        pb.environment().put("LD_LIBRARY_PATH", WORK_ROOT);
        pb.redirectErrorStream(false);
        ocrDaemon = pb.start();
        ocrWriter = new BufferedWriter(new OutputStreamWriter(ocrDaemon.getOutputStream(), StandardCharsets.UTF_8));
        ocrReader = new BufferedReader(new InputStreamReader(ocrDaemon.getInputStream(), StandardCharsets.UTF_8));
        ocrWriter.write("{\"id\":\"boot\",\"cmd\":\"ping\"}\n");
        ocrWriter.flush();
        String pong = ocrReader.readLine();
        if (pong == null || (!pong.contains("\"pong\"") && !pong.contains("\"ok\":true") && !pong.contains("\"ok\": true"))) {
            String err = drainErrorSnippet(ocrDaemon.getErrorStream());
            stopOcrDaemon();
            throw new IllegalStateException("Native OCR handshake failed" + (err.isEmpty() ? "" : ": " + err));
        }
    }

    private void stopOcrDaemon() {
        synchronized (ocrLock) {
            try {
                if (ocrWriter != null) {
                    ocrWriter.write("{\"id\":\"bye\",\"cmd\":\"quit\"}\n");
                    ocrWriter.flush();
                }
            } catch (Exception ignored) {
            }
            try {
                if (ocrDaemon != null) {
                    ocrDaemon.destroyForcibly();
                }
            } catch (Exception ignored) {
            }
            ocrDaemon = null;
            ocrWriter = null;
            ocrReader = null;
        }
    }

    private static File resolveSafeOcrTarget(String subDir, String name) {
        String cleanSub = subDir == null ? "" : subDir.trim();
        if (!cleanSub.isEmpty() && !SAFE_NAME.matcher(cleanSub).matches()) {
            throw new SecurityException("Invalid OCR sub-directory");
        }
        if (name == null || !SAFE_NAME.matcher(name).matches()) {
            throw new SecurityException("Invalid OCR file name");
        }
        File dir = cleanSub.isEmpty() ? new File(WORK_ROOT) : new File(WORK_ROOT, cleanSub);
        return new File(dir, name);
    }

    private static boolean matchesFileSha(File file, String expectedSha) {
        if (!file.isFile()) return false;
        try (FileInputStream in = new FileInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) >= 0) {
                if (n > 0) md.update(buf, 0, n);
            }
            return toHex(md.digest()).equals(expectedSha);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String toHex(byte[] digest) {
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(String.format(Locale.US, "%02x", b & 0xFF));
        }
        return sb.toString();
    }

    private static boolean isProcessAlive(java.lang.Process p) {
        try {
            p.exitValue();
            return false;
        } catch (IllegalThreadStateException alive) {
            return true;
        }
    }

    private static String drainErrorSnippet(InputStream err) {
        try {
            byte[] buf = new byte[512];
            int avail = Math.min(err.available(), buf.length);
            if (avail <= 0) return "";
            int n = err.read(buf, 0, avail);
            return n > 0 ? new String(buf, 0, n, StandardCharsets.UTF_8).trim() : "";
        } catch (Exception ignored) {
            return "";
        }
    }
}
