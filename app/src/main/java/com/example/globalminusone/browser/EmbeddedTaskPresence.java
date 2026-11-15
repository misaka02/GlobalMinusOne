package com.example.globalminusone.browser;

/** Only distinct heartbeat samples confirm absence; resize/status side effects cannot. */
public final class EmbeddedTaskPresence {
    private Long missingHeartbeatAt;
    public boolean observe(boolean present, boolean heartbeat, long now) {
        if (present) { reset(); return false; }
        if (!heartbeat) return false;
        if (missingHeartbeatAt == null) { missingHeartbeatAt = now; return false; }
        return now - missingHeartbeatAt >= 4_000;
    }
    public void reset() { missingHeartbeatAt = null; }
}
