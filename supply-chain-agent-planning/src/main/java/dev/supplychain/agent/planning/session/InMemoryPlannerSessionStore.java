package dev.supplychain.agent.planning.session;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Deterministic reference store. M7 replaces this with transactional PostgreSQL persistence. */
public final class InMemoryPlannerSessionStore implements PlannerSessionStore {
    private final ConcurrentHashMap<String, PlannerSessionCheckpoint> sessions = new ConcurrentHashMap<>();

    @Override public Optional<PlannerSessionCheckpoint> find(String sessionId) {
        return Optional.ofNullable(sessions.get(require(sessionId)));
    }

    @Override public PlannerSessionCheckpoint save(PlannerSessionCheckpoint checkpoint, long expectedRevision) {
        if (expectedRevision < 0) throw new IllegalArgumentException("expectedRevision cannot be negative");
        var saved = new java.util.concurrent.atomic.AtomicReference<PlannerSessionCheckpoint>();
        sessions.compute(checkpoint.sessionId(), (id, current) -> {
            long actualRevision = current == null ? 0 : current.revision();
            if (actualRevision != expectedRevision || checkpoint.revision() != expectedRevision + 1) {
                throw new PlannerCheckpointConflictException("planner session revision conflict for " + id
                        + ": expected " + expectedRevision + ", actual " + actualRevision);
            }
            saved.set(checkpoint);
            return checkpoint;
        });
        return saved.get();
    }
    private static String require(String value) {
        String result = java.util.Objects.requireNonNull(value, "sessionId").trim();
        if (result.isEmpty()) throw new IllegalArgumentException("sessionId cannot be blank");
        return result;
    }
}
