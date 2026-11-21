package dev.supplychain.agent.mechanisms.b1;

import java.util.Optional;

/** CAS store for durable B1 access and outcome features. */
public interface B1MemoryFeatureRepository {
    Optional<B1MemoryFeatureState> find(B1MemoryCandidateReference reference);
    void saveNew(B1MemoryFeatureState state);
    boolean compareAndSet(B1MemoryCandidateReference reference, long expectedRevision, B1MemoryFeatureState replacement);
}
