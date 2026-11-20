package dev.supplychain.agent.mechanisms.b2;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Deterministic test/embedded repository; production composition uses the PostgreSQL adapter. */
public final class InMemoryRecoveryProcedureDraftRepository implements RecoveryProcedureDraftRepository {
    private final Map<String, RecoveryProcedureDraft> drafts = new HashMap<>();
    @Override public synchronized void saveNew(RecoveryProcedureDraft draft) {
        if (drafts.putIfAbsent(draft.id(), draft) != null) throw new IllegalStateException("recovery procedure draft already exists");
    }
    @Override public synchronized Optional<RecoveryProcedureDraft> findById(String id) { return Optional.ofNullable(drafts.get(id)); }
    @Override public synchronized boolean compareAndSet(String id, long expectedVersion, RecoveryProcedureDraft replacement) {
        RecoveryProcedureDraft current = drafts.get(id);
        if (current == null || current.version() != expectedVersion) return false;
        if (!id.equals(replacement.id()) || replacement.version() != expectedVersion + 1
                || !current.sourceFingerprint().equals(replacement.sourceFingerprint()))
            throw new SecurityException("recovery procedure draft identity/source changed");
        drafts.put(id, replacement); return true;
    }
}
