package com.example.globalminusone.browser;

/** Short state lock only: no platform/Binder calls or polling occur while held. */
public final class EmbeddedLaunchGate {
    private long revision;
    private boolean opening;
    private boolean retired;
    public synchronized long begin() {
        if (retired || opening) throw new IllegalStateException("Window session ended or launch is in progress");
        opening = true;
        return ++revision;
    }
    public synchronized boolean isCurrent(long attempt) { return !retired && revision == attempt; }
    public synchronized boolean isOpening() { return opening; }
    public synchronized void finish(long attempt) { if (revision == attempt) opening = false; }
    public synchronized void cancel() { revision++; opening = false; }
    public synchronized void retire() { retired = true; cancel(); }
}
