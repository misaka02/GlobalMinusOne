package com.example.globalminusone.browser;

import java.util.function.Consumer;

/** Ownership of one received output; exceptions do not reverse a completed transfer. */
public final class EmbeddedSurfaceInput<T> implements AutoCloseable {
    private final T value;
    private final Consumer<T> release;
    private boolean transferred;
    private boolean closed;

    public EmbeddedSurfaceInput(T value, Consumer<T> release) {
        this.value = value;
        this.release = release;
    }
    public T transfer() {
        if (closed) throw new IllegalStateException("Output input is already closed");
        transferred = true;
        return value;
    }
    @Override public void close() {
        if (closed) return;
        closed = true;
        if (!transferred && value != null) release.accept(value);
    }
}
