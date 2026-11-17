package dev.supplychain.agent.memory.procedural;

import dev.supplychain.agent.memory.common.MemoryNotFoundException;
import dev.supplychain.agent.memory.common.MemoryVersionConflictException;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.memory.common.Provenance;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class ProceduralMemoryService {
    private final ProceduralMemoryRepository repository;

    public ProceduralMemoryService(ProceduralMemoryRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public ProcedureVersion stage(MemoryScopeId scope, String key, int version, ProcedureKind kind,
                                 String description, ProcedureArtifactReference artifact, Provenance provenance,
                                 Instant at, String author, ProcedureVersionId predecessor) {
        return repository.stage(ProcedureVersion.draft(scope, key, version, kind, description,
                artifact, provenance, at, author, predecessor));
    }

    public ProcedureVersion validate(ProcedureVersionId id, long expectedRevision, String reviewer, Instant at) {
        ProcedureVersion current = load(id);
        requireRevision(current, expectedRevision);
        return repository.compareAndSet(id, expectedRevision, current.validate(reviewer, at));
    }

    public ProcedureVersion activate(ProcedureVersionId id, long expectedRevision, String approver, Instant at) {
        return repository.activate(id, expectedRevision, approver, at);
    }

    public ProcedureVersion reject(ProcedureVersionId id, long expectedRevision, String reviewer, Instant at) {
        ProcedureVersion current = load(id);
        requireRevision(current, expectedRevision);
        return repository.compareAndSet(id, expectedRevision, current.reject(reviewer, at));
    }

    public ProcedureVersion load(ProcedureVersionId id) {
        return repository.findById(id).orElseThrow(() -> new MemoryNotFoundException("Procedure version not found: " + id.value()));
    }

    public List<ProcedureVersion> versions(MemoryScopeId scope, String key) {
        return repository.findVersions(scope, key);
    }

    public java.util.Optional<ProcedureVersion> active(MemoryScopeId scope, String key) {
        return repository.findActive(scope, key);
    }

    private static void requireRevision(ProcedureVersion current, long expected) {
        if (current.lifecycleRevision() != expected) {
            throw new MemoryVersionConflictException("Procedure lifecycle revision is "
                    + current.lifecycleRevision() + ", expected " + expected);
        }
    }
}
