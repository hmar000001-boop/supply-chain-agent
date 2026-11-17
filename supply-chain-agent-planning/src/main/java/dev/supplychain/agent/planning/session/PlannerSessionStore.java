package dev.supplychain.agent.planning.session;

import java.util.Optional;

public interface PlannerSessionStore {
    Optional<PlannerSessionCheckpoint> find(String sessionId);
    PlannerSessionCheckpoint save(PlannerSessionCheckpoint checkpoint, long expectedRevision);
}
