package dev.supplychain.agent.memory.procedural;

import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.memory.common.Provenance;
import java.time.Instant;
import java.util.Objects;

/** Immutable artifact identity plus an audited lifecycle; artifact content is never edited in place. */
public record ProcedureVersion(
        ProcedureVersionId id,
        MemoryScopeId scopeId,
        String procedureKey,
        int versionNumber,
        ProcedureKind kind,
        String description,
        ProcedureArtifactReference artifact,
        Provenance provenance,
        ProcedureStatus status,
        long lifecycleRevision,
        Instant createdAt,
        String createdBy,
        Instant validatedAt,
        String validatedBy,
        Instant activatedAt,
        String activatedBy,
        Instant retiredAt,
        Instant rejectedAt,
        String rejectedBy,
        ProcedureVersionId predecessor) {

    public ProcedureVersion {
        id = Objects.requireNonNull(id, "id");
        scopeId = Objects.requireNonNull(scopeId, "scopeId");
        procedureKey = require(procedureKey, "procedureKey");
        if (versionNumber < 1 || lifecycleRevision < 0) throw new IllegalArgumentException("invalid procedure version/revision");
        kind = Objects.requireNonNull(kind, "kind");
        description = require(description, "description");
        artifact = Objects.requireNonNull(artifact, "artifact");
        provenance = Objects.requireNonNull(provenance, "provenance");
        status = Objects.requireNonNull(status, "status");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        createdBy = require(createdBy, "createdBy");
        if (!provenance.recordedAt().equals(createdAt)) {
            throw new IllegalArgumentException("procedure createdAt must match provenance recordedAt");
        }
        switch (status) {
            case DRAFT -> requireLifecycleAbsence(validatedAt, validatedBy, activatedAt, activatedBy, retiredAt, rejectedAt, rejectedBy);
            case VALIDATED -> {
                requireValidated(validatedAt, validatedBy);
                requireLifecycleAbsence(activatedAt, activatedBy, retiredAt, rejectedAt, rejectedBy);
            }
            case ACTIVE -> {
                requireValidated(validatedAt, validatedBy);
                requireActivated(activatedAt, activatedBy);
                requireLifecycleAbsence(retiredAt, rejectedAt, rejectedBy);
            }
            case RETIRED -> {
                requireValidated(validatedAt, validatedBy);
                requireActivated(activatedAt, activatedBy);
                Objects.requireNonNull(retiredAt, "retiredAt");
                requireLifecycleAbsence(rejectedAt, rejectedBy);
            }
            case REJECTED -> {
                requireLifecycleAbsence(validatedAt, validatedBy, activatedAt, activatedBy, retiredAt);
                Objects.requireNonNull(rejectedAt, "rejectedAt");
                require(rejectedBy, "rejectedBy");
            }
        }
        if (validatedAt != null && validatedAt.isBefore(createdAt)) throw new IllegalArgumentException("validation precedes creation");
        if (activatedAt != null && (validatedAt == null || activatedAt.isBefore(validatedAt))) {
            throw new IllegalArgumentException("activation precedes validation");
        }
        if (retiredAt != null && (activatedAt == null || retiredAt.isBefore(activatedAt))) {
            throw new IllegalArgumentException("retirement precedes activation");
        }
        if (rejectedAt != null && rejectedAt.isBefore(createdAt)) throw new IllegalArgumentException("rejection precedes creation");
    }

    public static ProcedureVersion draft(MemoryScopeId scope, String key, int number, ProcedureKind kind,
                                         String description, ProcedureArtifactReference artifact, Provenance provenance,
                                         Instant at, String author, ProcedureVersionId predecessor) {
        return new ProcedureVersion(ProcedureVersionId.random(), scope, key, number, kind, description,
                artifact, provenance, ProcedureStatus.DRAFT, 0, at, author,
                null, null, null, null, null, null, null, predecessor);
    }

    public ProcedureVersion validate(String reviewer, Instant at) {
        if (status != ProcedureStatus.DRAFT) throw new IllegalStateException("only draft procedures can be validated");
        if (at.isBefore(createdAt)) throw new IllegalArgumentException("validation precedes creation");
        return new ProcedureVersion(id, scopeId, procedureKey, versionNumber, kind, description, artifact, provenance,
                ProcedureStatus.VALIDATED, lifecycleRevision + 1, createdAt, createdBy, at,
                require(reviewer, "reviewer"), null, null, null, null, null, predecessor);
    }

    public ProcedureVersion activate(String approver, Instant at) {
        if (status != ProcedureStatus.VALIDATED) throw new IllegalStateException("only validated procedures can be activated");
        if (at.isBefore(validatedAt)) throw new IllegalArgumentException("activation precedes validation");
        return new ProcedureVersion(id, scopeId, procedureKey, versionNumber, kind, description, artifact, provenance,
                ProcedureStatus.ACTIVE, lifecycleRevision + 1, createdAt, createdBy, validatedAt, validatedBy,
                at, require(approver, "approver"), null, null, null, predecessor);
    }

    public boolean knownActiveAt(Instant knownAt) {
        if (activatedAt == null || activatedAt.isAfter(knownAt)) return false;
        return status == ProcedureStatus.ACTIVE || (status == ProcedureStatus.RETIRED && knownAt.isBefore(retiredAt));
    }

    public ProcedureVersion retire(Instant at) {
        if (status != ProcedureStatus.ACTIVE) throw new IllegalStateException("only active procedures can be retired");
        if (at.isBefore(activatedAt)) throw new IllegalArgumentException("retirement precedes activation");
        return new ProcedureVersion(id, scopeId, procedureKey, versionNumber, kind, description, artifact, provenance,
                ProcedureStatus.RETIRED, lifecycleRevision + 1, createdAt, createdBy, validatedAt, validatedBy,
                activatedAt, activatedBy, at, null, null, predecessor);
    }

    public ProcedureVersion reject(String reviewer, Instant at) {
        if (status != ProcedureStatus.DRAFT) throw new IllegalStateException("only draft procedures can be rejected");
        if (at.isBefore(createdAt)) throw new IllegalArgumentException("rejection precedes creation");
        return new ProcedureVersion(id, scopeId, procedureKey, versionNumber, kind, description, artifact, provenance,
                ProcedureStatus.REJECTED, lifecycleRevision + 1, createdAt, createdBy,
                null, null, null, null, null, at, require(reviewer, "reviewer"), predecessor);
    }

    private static void requireValidated(Instant at, String by) {
        Objects.requireNonNull(at, "validatedAt");
        require(by, "validatedBy");
    }

    private static void requireActivated(Instant at, String by) {
        Objects.requireNonNull(at, "activatedAt");
        require(by, "activatedBy");
    }

    private static void requireLifecycleAbsence(Object... values) {
        for (Object value : values) {
            if (value != null) throw new IllegalArgumentException("lifecycle metadata is inconsistent with procedure status");
        }
    }

    private static String require(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
