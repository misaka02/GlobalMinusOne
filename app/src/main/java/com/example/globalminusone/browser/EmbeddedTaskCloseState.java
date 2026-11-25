package com.example.globalminusone.browser;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Per-launch cleanup ledger. An old ID is a candidate, never authority to remove a task. */
public final class EmbeddedTaskCloseState {
    public enum Outcome { REMOVED, ABSENT, IDENTITY_UNCONFIRMED, REMOVE_REJECTED, REMOVE_FAILED }
    public static final class Evidence {
        public final boolean currentRoot;
        public final String session, packageName;
        public Evidence(boolean currentRoot, String session, String packageName) {
            this.currentRoot = currentRoot; this.session = session; this.packageName = packageName;
        }
    }
    public static final class Snapshot {
        public final Map<Integer, Evidence> tasks;
        public final boolean complete;
        public Snapshot(Map<Integer, Evidence> tasks, boolean complete) {
            this.tasks = Collections.unmodifiableMap(new HashMap<>(tasks)); this.complete = complete;
        }
    }
    public interface Port {
        Snapshot snapshot() throws Exception;
        /** Fresh per-item authority immediately before remove; null means unconfirmed. */
        Evidence revalidate(int taskId) throws Exception;
        boolean remove(int taskId) throws Exception;
    }
    public static final class Result {
        public final String session, packageName;
        public final Set<Integer> preexisting;
        public final Map<Integer, Outcome> outcomes;
        /** Last positive ownership observation; historical evidence never authorizes a retry. */
        public final Map<Integer, Evidence> ownershipEvidence;
        public final int passes;
        public final boolean snapshotUnconfirmed;
        private Result(EmbeddedTaskCloseState state) {
            session = state.session; packageName = state.packageName;
            preexisting = Collections.unmodifiableSet(new HashSet<>(state.preexisting));
            outcomes = Collections.unmodifiableMap(new HashMap<>(state.outcomes));
            ownershipEvidence = Collections.unmodifiableMap(new HashMap<>(state.ownershipEvidence));
            passes = state.passes; snapshotUnconfirmed = state.snapshotUnconfirmed;
        }
        public boolean confirmed() {
            if (passes == 0 || snapshotUnconfirmed) return false;
            for (Outcome outcome : outcomes.values())
                if (outcome != Outcome.REMOVED && outcome != Outcome.ABSENT) return false;
            return true;
        }
    }
    private final String session, packageName;
    private final Set<Integer> preexisting;
    private final Map<Integer, Outcome> outcomes = new HashMap<>();
    private final Map<Integer, Evidence> ownershipEvidence = new HashMap<>();
    private int passes;
    private boolean snapshotUnconfirmed;
    public EmbeddedTaskCloseState(String session, String packageName, Set<Integer> preexisting,
                                  Set<Integer> candidates) {
        this.session = session; this.packageName = packageName;
        this.preexisting = new HashSet<>(preexisting);
        for (int id : candidates) if (id >= 0 && !preexisting.contains(id))
            outcomes.put(id, Outcome.IDENTITY_UNCONFIRMED);
    }
    private boolean owned(int id, Evidence evidence) {
        return id >= 0 && !preexisting.contains(id) && (evidence.currentRoot ||
                ChromeTaskOwnership.belongsToSession(session, evidence.session, false, packageName, evidence.packageName));
    }
    /** Two explicit, synchronous passes at most. No timeout claims to cancel a remote call. */
    public Result verify(Port port) {
        if (passes >= 2) return result();
        passes++;
        final Snapshot snapshot;
        try { snapshot = port.snapshot(); }
        catch (Exception failure) { snapshotUnconfirmed = true; return result(); }
        // Once the root is destroyed, a later complete global snapshot cannot
        // recover unknown markerless children missed by the first discovery.
        snapshotUnconfirmed |= !snapshot.complete;
        for (Map.Entry<Integer, Evidence> entry : snapshot.tasks.entrySet()) {
            if (owned(entry.getKey(), entry.getValue())) {
                ownershipEvidence.put(entry.getKey(), entry.getValue());
                if (!outcomes.containsKey(entry.getKey()))
                    outcomes.put(entry.getKey(), Outcome.IDENTITY_UNCONFIRMED);
            }
        }
        for (int id : new HashSet<>(outcomes.keySet())) {
            Outcome old = outcomes.get(id);
            if (old == Outcome.REMOVED || old == Outcome.ABSENT) continue;
            Evidence current = snapshot.tasks.get(id);
            if (current == null) {
                if (snapshot.complete) outcomes.put(id, Outcome.ABSENT);
            } else if (!owned(id, current)) {
                outcomes.put(id, Outcome.IDENTITY_UNCONFIRMED);
            } else {
                final Evidence authority;
                try { authority = port.revalidate(id); }
                catch (Exception failure) {
                    outcomes.put(id, Outcome.IDENTITY_UNCONFIRMED);
                    continue;
                }
                if (authority == null || !owned(id, authority)) {
                    outcomes.put(id, Outcome.IDENTITY_UNCONFIRMED);
                    continue;
                }
                ownershipEvidence.put(id, authority);
                try {
                    // Validation and removal remain separate remote operations:
                    // this narrows, but cannot eliminate, the platform TOCTOU gap.
                    outcomes.put(id, port.remove(id) ? Outcome.REMOVED : Outcome.REMOVE_REJECTED);
                }
                catch (Exception failure) { outcomes.put(id, Outcome.REMOVE_FAILED); }
            }
        }
        return result();
    }
    public Result result() { return new Result(this); }
}
