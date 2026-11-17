package dev.supplychain.agent.memory.inmemory;

import dev.supplychain.agent.memory.common.MemoryConflictException;
import dev.supplychain.agent.memory.common.MemoryNotFoundException;
import dev.supplychain.agent.memory.common.MemoryVersionConflictException;
import dev.supplychain.agent.memory.working.WorkingMemoryId;
import dev.supplychain.agent.memory.working.WorkingMemoryRepository;
import dev.supplychain.agent.memory.working.WorkingMemorySnapshot;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe reference adapter for tests and local execution; not a durable production store. */
public final class InMemoryWorkingMemoryRepository implements WorkingMemoryRepository {
    private final ConcurrentHashMap<WorkingMemoryId, WorkingMemorySnapshot> snapshots = new ConcurrentHashMap<>();

    @Override
    public WorkingMemorySnapshot create(WorkingMemorySnapshot snapshot) {
        if (snapshots.putIfAbsent(snapshot.id(), snapshot) != null) {
            throw new MemoryConflictException("Working memory already exists: " + snapshot.id().value());
        }
        return snapshot;
    }

    @Override
    public Optional<WorkingMemorySnapshot> findById(WorkingMemoryId id) {
        return Optional.ofNullable(snapshots.get(id));
    }

    @Override
    public WorkingMemorySnapshot compareAndSet(WorkingMemoryId id, long expectedVersion,
                                                WorkingMemorySnapshot replacement) {
        return snapshots.compute(id, (key, current) -> {
            if (current == null) throw new MemoryNotFoundException("Working memory not found: " + id.value());
            if (current.version() != expectedVersion) {
                throw new MemoryVersionConflictException("Working memory version changed from " + expectedVersion
                        + " to " + current.version());
            }
            if (!replacement.id().equals(id) || replacement.version() != expectedVersion + 1
                    || !replacement.scopeId().equals(current.scopeId())) {
                throw new IllegalArgumentException("replacement must preserve identity/scope and increment version once");
            }
            return replacement;
        });
    }

    @Override
    public int deleteExpired(Instant now) {
        int before = snapshots.size();
        snapshots.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        return before - snapshots.size();
    }
}
