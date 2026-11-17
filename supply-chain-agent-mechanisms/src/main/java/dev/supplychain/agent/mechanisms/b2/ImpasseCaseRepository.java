package dev.supplychain.agent.mechanisms.b2;

import java.util.Optional;

public interface ImpasseCaseRepository {
    void saveNew(ImpasseResolutionCase value);
    Optional<ImpasseResolutionCase> findById(String id);
    boolean compareAndSet(String id, long expectedVersion, ImpasseResolutionCase replacement);
}
