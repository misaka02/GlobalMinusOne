package com.example.globalminusone.browser;

import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Optional, bounded attribution only. Never changes an app-op, process, window,
 * display or task. No window dump is logged or persisted. */
final class EmbeddedExternalOverlayMonitor implements AutoCloseable {
    private static final AtomicBoolean SAMPLING = new AtomicBoolean();
    private long epoch;
    private long lastSample = -30_000;
    private Thread worker;
    private java.lang.Process process;
    private volatile boolean primaryOverlay;

    boolean hasPrimaryOverlay() { return primaryOverlay; }

    synchronized void sample(String pkg, int uid, int displayId) {
        long now = SystemClock.elapsedRealtime();
        if (worker != null || uid < 0 || displayId <= 0 || now - lastSample < 30_000 || !SAMPLING.compareAndSet(false, true)) return;
        lastSample = now;
        long expected = epoch;
        worker = new Thread(() -> {
            boolean found = false;
            try { found = inspect(pkg, uid, displayId, expected); }
            catch (Exception ignored) { /* Missing/denied evidence never means successful containment. */ }
            finally {
                synchronized (this) {
                    if (epoch == expected) { primaryOverlay = found; worker = null; }
                }
                SAMPLING.set(false);
            }
        }, "GmoOverlayAttribution");
        worker.setDaemon(true);
        worker.start();
    }

    private boolean inspect(String pkg, int uid, int displayId, long expected) throws Exception {
        java.lang.Process child = new ProcessBuilder("/system/bin/timeout", "-k", "1s", "2s",
                "/system/bin/dumpsys", "-t", "1", "window", "windows").redirectErrorStream(true).start();
        try {
            synchronized (this) {
                if (epoch != expected) return false;
                process = child;
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (java.io.InputStream input = child.getInputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    if (bytes.size() + count > 1_048_576) return false;
                    bytes.write(buffer, 0, count);
                }
            }
            if (!child.waitFor(300, TimeUnit.MILLISECONDS) || child.exitValue() != 0) return false;
            String dump = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
            if (dump.contains("Permission Denial") || dump.contains("DUMP TIMEOUT")) return false;
            return EmbeddedExternalWindowPolicy.hasPrimaryOverlay(dump, pkg, uid, displayId);
        } finally {
            child.destroy();
            synchronized (this) { if (process == child) process = null; }
        }
    }

    @Override public synchronized void close() {
        epoch++;
        primaryOverlay = false;
        lastSample = -30_000;
        if (process != null) { process.destroy(); process = null; }
        if (worker != null) { worker.interrupt(); worker = null; }
    }
}
