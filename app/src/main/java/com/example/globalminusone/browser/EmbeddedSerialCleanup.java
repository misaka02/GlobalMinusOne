package com.example.globalminusone.browser;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/** Resource releases follow Binder transfers on one owner queue. Only finish() shuts it down. */
public final class EmbeddedSerialCleanup {
    private EmbeddedSerialCleanup() { }
    public static void release(ExecutorService worker, Runnable release) {
        if (worker == null) { release.run(); return; }
        try { worker.execute(release); }
        catch (RejectedExecutionException ended) {
            // finish's terminal item already passed all transfers. Late TextureView
            // destruction still owns a release, even after the queue has terminated.
            if (!worker.isShutdown()) throw ended;
            release.run();
        }
    }
    public static void finish(ExecutorService worker) {
        if (worker != null) worker.execute(worker::shutdown);
    }
}
