package com.example.globalminusone.browser;

import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.view.Surface;

/** Drained parking output matching the virtual display's geometry so buffer size never collapses to 1x1. */
final class ChromeParkingSurface implements AutoCloseable {
    private final ImageReader reader;
    private final HandlerThread consumer;
    private final int width;
    private final int height;

    ChromeParkingSurface(int width, int height) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        reader = ImageReader.newInstance(this.width, this.height, PixelFormat.RGBA_8888, 2);
        HandlerThread thread = null;
        try {
            thread = new HandlerThread("GmoParkedDisplay", Process.THREAD_PRIORITY_BACKGROUND);
            thread.start();
            reader.setOnImageAvailableListener(source -> {
                // Event driven: drain buffers so SurfaceFlinger never stalls on a full queue.
                try (Image ignored = source.acquireLatestImage()) { }
                catch (IllegalStateException ignored) { /* Explicit close may race a callback. */ }
            }, new Handler(thread.getLooper()));
        } catch (RuntimeException | Error failure) {
            try { reader.close(); }
            finally { if (thread != null) thread.quitSafely(); }
            throw failure;
        }
        consumer = thread;
    }
    Surface surface() { return reader.getSurface(); }
    boolean matchesSize(int expectedWidth, int expectedHeight) {
        return width == Math.max(1, expectedWidth) && height == Math.max(1, expectedHeight);
    }

    @Override public void close() {
        try { reader.setOnImageAvailableListener(null, null); }
        finally {
            try { reader.close(); }
            finally { consumer.quitSafely(); }
        }
    }
}
