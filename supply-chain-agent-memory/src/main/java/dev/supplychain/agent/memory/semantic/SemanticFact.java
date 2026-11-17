package dev.supplychain.agent.memory.semantic;

import dev.supplychain.agent.memory.common.EntityReference;
import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.memory.common.Provenance;
import java.time.Instant;
import java.util.Objects;

/** Bitemporal fact: valid-time describes the world; recordedAt describes when memory learned it. */
public record SemanticFact(
        SemanticFactId id,
        MemoryScopeId scopeId,
        long version,
        EntityReference subject,
        String predicate,
        FactValue value,
        Instant validFrom,
        Instant validUntil,
        Instant recordedAt,
        FactStatus status,
        Provenance provenance,
        String activatedBy,
        Instant activatedAt,
        EvidenceReference activationEvidence,
        SemanticFactId supersedes,
        Instant supersededAt,
        String rejectedBy,
        Instant rejectedAt,
        EvidenceReference rejectionEvidence) {

    public SemanticFact {
        id = Objects.requireNonNull(id, "id");
        scopeId = Objects.requireNonNull(scopeId, "scopeId");
        if (version < 0) throw new IllegalArgumentException("version cannot be negative");
        subject = Objects.requireNonNull(subject, "subject");
        predicate = require(predicate, "predicate");
        value = Objects.requireNonNull(value, "value");
        validFrom = Objects.requireNonNull(validFrom, "validFrom");
        if (validUntil != null && !validUntil.isAfter(validFrom)) {
            throw new IllegalArgumentException("validUntil must be after validFrom");
        }
        recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
        status = Objects.requireNonNull(status, "status");
        provenance = Objects.requireNonNull(provenance, "provenance");
        if (!provenance.recordedAt().equals(recordedAt)) {
            throw new IllegalArgumentException("fact recordedAt must match provenance recordedAt");
        }
        boolean hasActivation = activatedBy != null || activatedAt != null || activationEvidence != null;
        boolean hasSupersession = supersededAt != null;
        boolean hasRejection = rejectedBy != null || rejectedAt != null || rejectionEvidence != null;
        if (status == FactStatus.ACTIVE || status == FactStatus.SUPERSEDED) {
            if (activatedBy == null || activatedBy.isBlank() || activatedAt == null || activationEvidence == null) {
                throw new IllegalArgumentException("active/superseded fact requires activation actor, time and evidence");
            }
            if (activatedAt.isBefore(recordedAt)) throw new IllegalArgumentException("activation precedes recording");
        }
        if (status == FactStatus.ACTIVE && (hasSupersession || hasRejection)) {
            throw new IllegalArgumentException("active fact cannot contain supersession or rejection metadata");
        }
        if (status == FactStatus.SUPERSEDED) {
            if (supersededAt == null || supersededAt.isBefore(activatedAt)) {
                throw new IllegalArgumentException("superseded fact requires a supersession time after activation");
            }
            if (hasRejection) throw new IllegalArgumentException("superseded fact cannot contain rejection metadata");
        }
        if (status == FactStatus.PROPOSED && (hasActivation || hasSupersession || hasRejection)) {
            throw new IllegalArgumentException("proposed fact cannot contain lifecycle decision metadata");
        }
        if (status == FactStatus.REJECTED) {
            if (rejectedBy == null || rejectedBy.isBlank() || rejectedAt == null || rejectionEvidence == null) {
                throw new IllegalArgumentException("rejected fact requires reviewer, time and evidence");
            }
            if (rejectedAt.isBefore(recordedAt)) throw new IllegalArgumentException("rejection precedes recording");
            if (hasActivation || hasSupersession) throw new IllegalArgumentException("rejected fact cannot be active or superseded");
        }
        if (supersedes != null && supersedes.equals(id)) {
            throw new IllegalArgumentException("fact cannot supersede itself");
        }
        if ((status == FactStatus.PROPOSED || status == FactStatus.REJECTED) && supersedes != null) {
            throw new IllegalArgumentException("only an activated replacement may reference a superseded fact");
        }
    }

    public static SemanticFact proposed(FactKey key, FactValue value, Instant validFrom,
                                        Instant validUntil, Instant recordedAt, Provenance provenance) {
        return new SemanticFact(SemanticFactId.random(), key.scopeId(), 0, key.subject(), key.predicate(),
                value, validFrom, validUntil, recordedAt, FactStatus.PROPOSED, provenance,
                null, null, null, null, null, null, null, null);
    }

    public FactKey key() { return new FactKey(scopeId, subject, predicate); }

    public boolean validAt(Instant at) {
        return !at.isBefore(validFrom) && (validUntil == null || at.isBefore(validUntil));
    }

    /** Whether the fact was active in memory at a historical knowledge time. */
    public boolean knownAsActiveAt(Instant knownAt) {
        if (recordedAt.isAfter(knownAt) || activatedAt == null || activatedAt.isAfter(knownAt)) return false;
        return status == FactStatus.ACTIVE || (status == FactStatus.SUPERSEDED && knownAt.isBefore(supersededAt));
    }

    public boolean overlapsValidity(SemanticFact other) {
        Instant thisEnd = validUntil == null ? Instant.MAX : validUntil;
        Instant otherEnd = other.validUntil == null ? Instant.MAX : other.validUntil;
        return validFrom.isBefore(otherEnd) && other.validFrom.isBefore(thisEnd);
    }

    public SemanticFact activate(String actor, EvidenceReference approvalEvidence, Instant at, SemanticFactId supersedesId) {
        if (status != FactStatus.PROPOSED) throw new IllegalStateException("only proposed facts can be activated");
        if (at.isBefore(recordedAt)) throw new IllegalArgumentException("activation precedes recording");
        return new SemanticFact(id, scopeId, version + 1, subject, predicate, value, validFrom, validUntil,
                recordedAt, FactStatus.ACTIVE, provenance, require(actor, "actor"), at,
                Objects.requireNonNull(approvalEvidence, "approvalEvidence"), supersedesId, null,
                null, null, null);
    }

    public SemanticFact supersede(Instant at) {
        if (status != FactStatus.ACTIVE) throw new IllegalStateException("only active facts can be superseded");
        if (at.isBefore(activatedAt)) throw new IllegalArgumentException("supersededAt precedes activation");
        return new SemanticFact(id, scopeId, version + 1, subject, predicate, value, validFrom, validUntil,
                recordedAt, FactStatus.SUPERSEDED, provenance, activatedBy, activatedAt, activationEvidence,
                supersedes, at, null, null, null);
    }

    public SemanticFact reject(String reviewer, EvidenceReference reviewEvidence, Instant at) {
        if (status != FactStatus.PROPOSED) throw new IllegalStateException("only proposed facts can be rejected");
        if (at.isBefore(recordedAt)) throw new IllegalArgumentException("rejection precedes recording");
        return new SemanticFact(id, scopeId, version + 1, subject, predicate, value, validFrom, validUntil,
                recordedAt, FactStatus.REJECTED, provenance, null, null, null, null, null,
                require(reviewer, "reviewer"), at, Objects.requireNonNull(reviewEvidence, "reviewEvidence"));
    }

    private static String require(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
