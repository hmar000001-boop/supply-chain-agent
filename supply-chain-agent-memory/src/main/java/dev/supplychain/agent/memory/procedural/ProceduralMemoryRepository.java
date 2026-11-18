package dev.supplychain.agent.memory.procedural;

import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ProceduralMemoryRepository {
    ProcedureVersion stage(ProcedureVersion draft);
    Optional<ProcedureVersion> findById(ProcedureVersionId id);
    List<ProcedureVersion> findVersions(MemoryScopeId scope, String procedureKey);
    ProcedureVersion compareAndSet(ProcedureVersionId id, long expectedRevision, ProcedureVersion replacement);
    ProcedureVersion activate(ProcedureVersionId id, long expectedRevision, String approver, Instant at);
    Optional<ProcedureVersion> findActive(MemoryScopeId scope, String procedureKey);
    List<ProcedureVersion> findActive(MemoryScopeId scope, int limit);

    /**
     * Returns versions whose activation interval contains {@code knownAt}. The returned
     * immutable object retains its current persisted lifecycle status: a version retired
     * after {@code knownAt} is returned as {@link ProcedureStatus#RETIRED}, and callers
     * can use {@link ProcedureVersion#knownActiveAt(java.time.Instant)} to verify its
     * historical activation. This deliberately avoids manufacturing a non-persisted
     * historical snapshot and is shared by the in-memory and PostgreSQL adapters.
     */
    List<ProcedureVersion> findActiveAt(MemoryScopeId scope, java.time.Instant knownAt, int limit);
}
