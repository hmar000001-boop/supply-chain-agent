package dev.supplychain.agent.mechanisms.b2;

import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Typed, scope-bound recovery procedure proposal. It stores references and conditions, never
 * generated instructions or executable action input. Approval is a separate state transition.
 */
public record RecoveryProcedureDraft(
        String id,
        MemoryScopeId scopeId,
        String executionId,
        String impasseCaseId,
        String parentRequestId,
        long parentWorkingMemoryVersion,
        String objectiveFingerprint,
        String conditionKey,
        List<RegisteredRecoveryActionReference> actions,
        List<String> preconditionKeys,
        List<String> expectedPostconditionKeys,
        List<String> failureConditionKeys,
        List<EvidenceReference> evidence,
        String sourceFingerprint,
        long version,
        RecoveryProcedureStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt,
        String approvedBy,
        String approvalReference,
        Instant approvedAt) {

    public RecoveryProcedureDraft {
        id = required(id, "id");
        scopeId = Objects.requireNonNull(scopeId, "scopeId");
        executionId = required(executionId, "executionId");
        impasseCaseId = required(impasseCaseId, "impasseCaseId");
        parentRequestId = required(parentRequestId, "parentRequestId");
        if (parentWorkingMemoryVersion < 0 || version < 0) throw new IllegalArgumentException("versions cannot be negative");
        objectiveFingerprint = required(objectiveFingerprint, "objectiveFingerprint");
        conditionKey = key(conditionKey, "conditionKey");
        actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
        if (actions.isEmpty()) throw new IllegalArgumentException("a recovery draft must reference at least one registered action");
        if (actions.stream().map(RegisteredRecoveryActionReference::actionId).distinct().count() != actions.size())
            throw new IllegalArgumentException("duplicate recovery action reference");
        preconditionKeys = keys(preconditionKeys, "preconditionKeys");
        expectedPostconditionKeys = keys(expectedPostconditionKeys, "expectedPostconditionKeys");
        failureConditionKeys = keys(failureConditionKeys, "failureConditionKeys");
        if (preconditionKeys.isEmpty() || expectedPostconditionKeys.isEmpty() || failureConditionKeys.isEmpty())
            throw new IllegalArgumentException("preconditions, expected postconditions and failure conditions are required");
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        if (evidence.isEmpty()) throw new IllegalArgumentException("verified source evidence is required");
        sourceFingerprint = required(sourceFingerprint, "sourceFingerprint");
        if (!sourceFingerprint.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("sourceFingerprint must be SHA-256");
        status = Objects.requireNonNull(status, "status");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt precedes createdAt");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(createdAt) || updatedAt.isAfter(expiresAt)) throw new IllegalArgumentException("expiresAt must follow createdAt");
        if (status == RecoveryProcedureStatus.APPROVED) {
            approvedBy = required(approvedBy, "approvedBy");
            approvalReference = required(approvalReference, "approvalReference");
            approvedAt = Objects.requireNonNull(approvedAt, "approvedAt");
            if (approvedAt.isBefore(createdAt) || !approvedAt.isBefore(expiresAt))
                throw new IllegalArgumentException("approval timestamp outside draft validity interval");
        } else if (approvedBy != null || approvalReference != null || approvedAt != null) {
            throw new IllegalArgumentException("approval metadata is valid only for APPROVED drafts");
        }
    }

    public RecoveryProcedureDraft approve(String approver, String reference, Instant at) {
        requireDraftAndLive(at);
        return new RecoveryProcedureDraft(id, scopeId, executionId, impasseCaseId, parentRequestId,
                parentWorkingMemoryVersion, objectiveFingerprint, conditionKey, actions, preconditionKeys,
                expectedPostconditionKeys, failureConditionKeys, evidence, sourceFingerprint, version + 1,
                RecoveryProcedureStatus.APPROVED, createdAt, at, expiresAt, approver, reference, at);
    }

    public RecoveryProcedureDraft reject(Instant at) {
        requireDraftAndLive(at);
        return new RecoveryProcedureDraft(id, scopeId, executionId, impasseCaseId, parentRequestId,
                parentWorkingMemoryVersion, objectiveFingerprint, conditionKey, actions, preconditionKeys,
                expectedPostconditionKeys, failureConditionKeys, evidence, sourceFingerprint, version + 1,
                RecoveryProcedureStatus.REJECTED, createdAt, at, expiresAt, null, null, null);
    }

    public RecoveryProcedureDraft expire(Instant at) {
        Objects.requireNonNull(at, "at");
        if (at.isBefore(expiresAt)) throw new IllegalStateException("recovery draft has not expired");
        if (status != RecoveryProcedureStatus.DRAFT) return this;
        return new RecoveryProcedureDraft(id, scopeId, executionId, impasseCaseId, parentRequestId,
                parentWorkingMemoryVersion, objectiveFingerprint, conditionKey, actions, preconditionKeys,
                expectedPostconditionKeys, failureConditionKeys, evidence, sourceFingerprint, version + 1,
                RecoveryProcedureStatus.EXPIRED, createdAt, at, expiresAt, null, null, null);
    }

    public boolean sameSource(RecoveryProcedureDraft other) {
        return other != null && id.equals(other.id) && scopeId.equals(other.scopeId)
                && sourceFingerprint.equals(other.sourceFingerprint);
    }

    private void requireDraftAndLive(Instant at) {
        Objects.requireNonNull(at, "at");
        if (status != RecoveryProcedureStatus.DRAFT) throw new IllegalStateException("only a DRAFT procedure can transition");
        if (at.isBefore(createdAt) || !at.isBefore(expiresAt)) throw new IllegalStateException("recovery draft is outside its validity interval");
    }

    private static List<String> keys(List<String> values, String name) {
        return List.copyOf(Objects.requireNonNull(values, name)).stream().map(v -> key(v, name)).distinct().toList();
    }
    private static String key(String value, String name) {
        String v = required(value, name);
        if (!v.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,159}"))
            throw new IllegalArgumentException(name + " must be an opaque registered condition key, not free-form instructions");
        return v;
    }
    private static String required(String value, String name) {
        String v = Objects.requireNonNull(value, name).trim();
        if (v.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return v;
    }
}
