package dev.supplychain.agent.mechanisms.b2;

import java.util.Optional;

public interface RecoveryProcedureDraftRepository {
    void saveNew(RecoveryProcedureDraft draft);
    Optional<RecoveryProcedureDraft> findById(String id);
    boolean compareAndSet(String id, long expectedVersion, RecoveryProcedureDraft replacement);
}
