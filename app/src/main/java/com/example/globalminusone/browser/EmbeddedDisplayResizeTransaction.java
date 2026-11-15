package com.example.globalminusone.browser;

/** Geometry-only transaction. No task launch, display creation or authorization port. */
final class EmbeddedDisplayResizeTransaction {
    static final class Geometry {
        final int width, height, density;
        Geometry(int width, int height, int density) {
            this.width = width; this.height = height; this.density = density;
        }
    }
    interface Port {
        void resizeDisplay(Geometry geometry) throws Exception;
        void resizeRoot(Geometry geometry) throws Exception;
    }
    static final class Failure extends Exception {
        final boolean rollbackConfirmed;
        Failure(Exception cause, boolean rollbackConfirmed) {
            super(cause);
            this.rollbackConfirmed = rollbackConfirmed;
        }
    }
    static void replace(Geometry before, Geometry after, Port port) throws Failure {
        try { apply(after, port); }
        catch (Exception cause) {
            boolean restored = false;
            try { apply(before, port); restored = true; }
            catch (Exception rollback) { cause.addSuppressed(rollback); }
            throw new Failure(cause, restored);
        }
    }
    private static void apply(Geometry geometry, Port port) throws Exception {
        port.resizeDisplay(geometry);
        port.resizeRoot(geometry);
    }
}
