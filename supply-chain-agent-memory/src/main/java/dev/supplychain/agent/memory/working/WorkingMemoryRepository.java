package dev.supplychain.agent.memory.working;

import java.time.Instant;
import java.util.Optional;

public interface WorkingMemoryRepository {
    WorkingMemorySnapshot create(WorkingMemorySnapshot snapshot);
    Optional<WorkingMemorySnapshot> findById(WorkingMemoryId id);
    WorkingMemorySnapshot compareAndSet(WorkingMemoryId id, long expectedVersion, WorkingMemorySnapshot replacement);
    int deleteExpired(Instant now);
}
