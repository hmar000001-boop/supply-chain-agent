package dev.supplychain.agent.memory.inmemory;

import dev.supplychain.agent.memory.common.MemoryConflictException;
import dev.supplychain.agent.memory.common.MemoryNotFoundException;
import dev.supplychain.agent.memory.common.MemoryVersionConflictException;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.memory.procedural.ProcedureStatus;
import dev.supplychain.agent.memory.procedural.ProcedureVersion;
import dev.supplychain.agent.memory.procedural.ProcedureVersionId;
import dev.supplychain.agent.memory.procedural.ProceduralMemoryRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Atomic reference adapter; activation retires the former active version under one monitor. */
public final class InMemoryProceduralMemoryRepository implements ProceduralMemoryRepository {
    private final ConcurrentHashMap<ProcedureVersionId, ProcedureVersion> versions = new ConcurrentHashMap<>();

    @Override
    public ProcedureVersion stage(ProcedureVersion draft) {
        if (draft.status() != ProcedureStatus.DRAFT) throw new IllegalArgumentException("only draft versions can be staged");
        synchronized (this) {
            var previous = versions.values().stream().filter(v -> v.scopeId().equals(draft.scopeId())
                    && v.procedureKey().equals(draft.procedureKey()))
                    .max(Comparator.comparingInt(ProcedureVersion::versionNumber)).orElse(null);
            int expectedNumber = previous == null ? 1 : previous.versionNumber() + 1;
            if (draft.versionNumber() != expectedNumber
                    || (previous == null && draft.predecessor() != null)
                    || (previous != null && !previous.id().equals(draft.predecessor()))) {
                throw new MemoryConflictException("Procedure version must append after the latest immutable version; expected v"
                        + expectedNumber);
            }
            if (versions.putIfAbsent(draft.id(), draft) != null) {
                throw new MemoryConflictException("Procedure version id already exists: " + draft.id().value());
            }
            return draft;
        }
    }

    @Override
    public synchronized Optional<ProcedureVersion> findById(ProcedureVersionId id) { return Optional.ofNullable(versions.get(id)); }

    @Override
    public synchronized List<ProcedureVersion> findVersions(MemoryScopeId scope, String key) {
        return versions.values().stream().filter(v -> v.scopeId().equals(scope) && v.procedureKey().equals(key))
                .sorted(Comparator.comparingInt(ProcedureVersion::versionNumber).reversed()).toList();
    }

    @Override
    public synchronized ProcedureVersion compareAndSet(ProcedureVersionId id, long expectedRevision, ProcedureVersion replacement) {
        return versions.compute(id, (key, current) -> {
            if (current == null) throw new MemoryNotFoundException("Procedure version not found: " + id.value());
            checkRevision(current, expectedRevision);
            boolean allowedTransition = current.status() == ProcedureStatus.DRAFT
                    && (replacement.status() == ProcedureStatus.VALIDATED || replacement.status() == ProcedureStatus.REJECTED);
            if (!allowedTransition || !replacement.id().equals(id)
                    || replacement.lifecycleRevision() != expectedRevision + 1
                    || !replacement.scopeId().equals(current.scopeId())
                    || !replacement.procedureKey().equals(current.procedureKey())
                    || replacement.versionNumber() != current.versionNumber()
                    || replacement.kind() != current.kind()
                    || !replacement.description().equals(current.description())
                    || !replacement.artifact().equals(current.artifact())
                    || !replacement.provenance().equals(current.provenance())
                    || !replacement.createdAt().equals(current.createdAt())
                    || !replacement.createdBy().equals(current.createdBy())
                    || !Objects.equals(replacement.predecessor(), current.predecessor())) {
                throw new IllegalArgumentException("lifecycle update must be a valid draft transition and preserve immutable procedure identity/artifact");
            }
            return replacement;
        });
    }

    @Override
    public synchronized ProcedureVersion activate(ProcedureVersionId id, long expectedRevision, String approver, Instant at) {
        ProcedureVersion candidate = require(id);
        checkRevision(candidate, expectedRevision);
        if (candidate.status() != ProcedureStatus.VALIDATED) throw new IllegalStateException("only validated version can activate");
        ProcedureVersion old = findActive(candidate.scopeId(), candidate.procedureKey()).orElse(null);
        if (old != null && old.id().equals(id)) return old;
        // Build/validate both transitions before mutating either record; failed activation cannot retire the current procedure.
        ProcedureVersion active = candidate.activate(approver, at);
        ProcedureVersion retired = old == null ? null : old.retire(at);
        if (retired != null) versions.put(old.id(), retired);
        versions.put(id, active);
        return active;
    }

    @Override
    public synchronized Optional<ProcedureVersion> findActive(MemoryScopeId scope, String key) {
        return versions.values().stream().filter(v -> v.scopeId().equals(scope)
                && v.procedureKey().equals(key) && v.status() == ProcedureStatus.ACTIVE).findFirst();
    }

    @Override
    public synchronized List<ProcedureVersion> findActive(MemoryScopeId scope, int limit) {
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("limit must be in [1,500]");
        return versions.values().stream().filter(v -> v.scopeId().equals(scope)
                && v.status() == ProcedureStatus.ACTIVE)
                .sorted(Comparator.comparing(ProcedureVersion::procedureKey)
                        .thenComparingInt(ProcedureVersion::versionNumber))
                .limit(limit).toList();
    }

    @Override
    public synchronized List<ProcedureVersion> findActiveAt(MemoryScopeId scope, Instant knownAt, int limit) {
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("limit must be in [1,500]");
        return versions.values().stream().filter(v -> v.scopeId().equals(scope) && v.knownActiveAt(knownAt))
                .sorted(Comparator.comparing(ProcedureVersion::procedureKey)
                        .thenComparingInt(ProcedureVersion::versionNumber))
                .limit(limit).toList();
    }

    private ProcedureVersion require(ProcedureVersionId id) {
        ProcedureVersion value = versions.get(id);
        if (value == null) throw new MemoryNotFoundException("Procedure version not found: " + id.value());
        return value;
    }

    private static void checkRevision(ProcedureVersion value, long expected) {
        if (value.lifecycleRevision() != expected) {
            throw new MemoryVersionConflictException("Procedure lifecycle revision changed from "
                    + expected + " to " + value.lifecycleRevision());
        }
    }
}
