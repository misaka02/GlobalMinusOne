package com.example.globalminusone.browser;

import java.util.HashSet;
import java.util.Set;

/** Per-window evidence retained after Activity.finish removes a task from root.children. */
public final class EmbeddedOwnedTasks {
    private final Set<Integer> ids = new HashSet<>();
    public void observe(int taskId, boolean preexisting, boolean ownRoot, boolean ownSessionMarker) {
        if (taskId >= 0 && !preexisting && (ownRoot || ownSessionMarker)) ids.add(taskId);
    }
    public Set<Integer> snapshot() { return new HashSet<>(ids); }
    public void clear() { ids.clear(); }
}
