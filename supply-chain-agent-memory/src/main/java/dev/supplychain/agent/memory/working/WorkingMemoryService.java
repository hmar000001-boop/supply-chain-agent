package dev.supplychain.agent.memory.working;

import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryNotFoundException;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Application service for versioned active context; storage is supplied through a repository port. */
public final class WorkingMemoryService {
    private final WorkingMemoryRepository repository;
    private final Duration ttl;

    public WorkingMemoryService(WorkingMemoryRepository repository, Duration ttl) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        if (ttl.isNegative() || ttl.isZero()) throw new IllegalArgumentException("ttl must be positive");
    }

    public WorkingMemoryService(WorkingMemoryRepository repository,
                                dev.supplychain.agent.memory.maintenance.MemoryRetentionPolicy policy) {
        this(repository, Objects.requireNonNull(policy, "policy").workingMemoryTtl());
    }

    public WorkingMemorySnapshot open(MemoryScopeId scope, Instant now) {
        return repository.create(WorkingMemorySnapshot.empty(scope, now, now.plus(ttl)));
    }

    /** Idempotent open used when the host supplies a stable execution-scoped memory identity. */
    public WorkingMemorySnapshot open(WorkingMemoryId id, MemoryScopeId scope, Instant now) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(now, "now");
        var existing = repository.findById(id);
        if (existing.isPresent()) {
            if (!existing.get().scopeId().equals(scope)) throw new SecurityException("working memory identity is already bound to another scope");
            return existing.get();
        }
        var proposed = WorkingMemorySnapshot.empty(id, scope, now, now.plus(ttl));
        try {
            return repository.create(proposed);
        } catch (dev.supplychain.agent.memory.common.MemoryConflictException concurrentCreate) {
            var winner = repository.findById(id).orElseThrow(() -> concurrentCreate);
            if (!winner.scopeId().equals(scope)) throw new SecurityException("working memory identity is already bound to another scope");
            return winner;
        }
    }

    public java.util.Optional<WorkingMemorySnapshot> find(WorkingMemoryId id) {
        return repository.findById(Objects.requireNonNull(id, "id"));
    }

    public WorkingMemorySnapshot put(WorkingMemoryId id, long expectedVersion, String key,
                                     MemoryValue value, List<EvidenceReference> evidence, Instant now) {
        var current = load(id);
        requireVersion(current, expectedVersion);
        requireNotExpired(current, now);
        var replacement = current.put(key, value, evidence, now, now.plus(ttl));
        return repository.compareAndSet(id, expectedVersion, replacement);
    }

    public WorkingMemorySnapshot remove(WorkingMemoryId id, long expectedVersion, String key, Instant now) {
        var current = load(id);
        requireVersion(current, expectedVersion);
        requireNotExpired(current, now);
        var replacement = current.remove(key, now, now.plus(ttl));
        return repository.compareAndSet(id, expectedVersion, replacement);
    }

    public WorkingMemorySnapshot putHypothesis(WorkingMemoryId id, long expectedVersion,
                                               Hypothesis hypothesis, Instant now) {
        var current = load(id);
        requireVersion(current, expectedVersion);
        requireNotExpired(current, now);
        Objects.requireNonNull(hypothesis, "hypothesis");
        if (hypothesis.updatedAt().isAfter(now) || hypothesis.createdAt().isAfter(now)) {
            throw new IllegalArgumentException("hypothesis timestamps cannot be in the future of the working-memory update");
        }
        var replacement = current.putHypothesis(hypothesis, now, now.plus(ttl));
        return repository.compareAndSet(id, expectedVersion, replacement);
    }

    public WorkingMemorySnapshot load(WorkingMemoryId id) {
        return repository.findById(id).orElseThrow(() ->
                new MemoryNotFoundException("Working memory not found: " + id.value()));
    }

    private static void requireNotExpired(WorkingMemorySnapshot current, Instant now) {
        if (!current.expiresAt().isAfter(Objects.requireNonNull(now, "now"))) {
            throw new IllegalStateException("working memory has expired and cannot be revived");
        }
    }

    private static void requireVersion(WorkingMemorySnapshot current, long expected) {
        if (current.version() != expected) {
            throw new dev.supplychain.agent.memory.common.MemoryVersionConflictException(
                    "Working memory version is " + current.version() + ", expected " + expected);
        }
    }
}
