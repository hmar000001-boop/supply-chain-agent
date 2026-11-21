package dev.supplychain.agent.mechanisms.b2;

import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Immutable, typed payload published for an approved B2 recovery draft.
 * It contains registered action descriptors and opaque condition/evidence references only;
 * it never contains free-form generated instructions or executable action arguments.
 */
public record RecoveryProcedureArtifact(
        int formatVersion,
        String sourceDraftId,
        MemoryScopeId scopeId,
        String conditionKey,
        String objectiveFingerprint,
        List<RegisteredRecoveryActionReference> actions,
        List<String> preconditionKeys,
        List<String> expectedPostconditionKeys,
        List<String> failureConditionKeys,
        List<EvidenceReference> evidence,
        String sourceFingerprint,
        String approvedBy,
        String approvalReference,
        Instant approvedAt) {

    public RecoveryProcedureArtifact {
        if (formatVersion != 1) throw new IllegalArgumentException("unsupported recovery procedure artifact format");
        sourceDraftId = required(sourceDraftId, "sourceDraftId");
        scopeId = Objects.requireNonNull(scopeId, "scopeId");
        conditionKey = key(conditionKey, "conditionKey");
        objectiveFingerprint = sha256(objectiveFingerprint, "objectiveFingerprint");
        actions = Objects.requireNonNull(actions, "actions").stream()
                .sorted(Comparator.comparing(RegisteredRecoveryActionReference::actionId)).toList();
        if (actions.isEmpty() || actions.stream().map(RegisteredRecoveryActionReference::actionId).distinct().count() != actions.size())
            throw new IllegalArgumentException("artifact requires unique registered actions");
        preconditionKeys = normalizedKeys(preconditionKeys, "preconditionKeys");
        expectedPostconditionKeys = normalizedKeys(expectedPostconditionKeys, "expectedPostconditionKeys");
        failureConditionKeys = normalizedKeys(failureConditionKeys, "failureConditionKeys");
        if (preconditionKeys.isEmpty() || expectedPostconditionKeys.isEmpty() || failureConditionKeys.isEmpty())
            throw new IllegalArgumentException("artifact requires preconditions, postconditions and failure conditions");
        evidence = Objects.requireNonNull(evidence, "evidence").stream()
                .sorted(Comparator.comparing(EvidenceReference::evidenceType).thenComparing(EvidenceReference::evidenceId))
                .toList();
        if (evidence.isEmpty() || evidence.stream().distinct().count() != evidence.size())
            throw new IllegalArgumentException("artifact requires unique evidence references");
        sourceFingerprint = sha256(sourceFingerprint, "sourceFingerprint");
        approvedBy = required(approvedBy, "approvedBy");
        approvalReference = required(approvalReference, "approvalReference");
        approvedAt = Objects.requireNonNull(approvedAt, "approvedAt");
    }

    public static RecoveryProcedureArtifact from(RecoveryProcedureDraft draft) {
        Objects.requireNonNull(draft, "draft");
        if (draft.status() != RecoveryProcedureStatus.APPROVED) {
            throw new IllegalStateException("only approved B2 drafts can become procedural artifacts");
        }
        return new RecoveryProcedureArtifact(1, draft.id(), draft.scopeId(), draft.conditionKey(),
                draft.objectiveFingerprint(), draft.actions(), draft.preconditionKeys(),
                draft.expectedPostconditionKeys(), draft.failureConditionKeys(), draft.evidence(),
                draft.sourceFingerprint(), draft.approvedBy(), draft.approvalReference(), draft.approvedAt());
    }

    private static List<String> normalizedKeys(List<String> values, String name) {
        List<String> result = Objects.requireNonNull(values, name).stream().map(v -> key(v, name)).distinct().sorted().toList();
        if (result.size() != values.size()) throw new IllegalArgumentException(name + " contains duplicate keys");
        return result;
    }

    private static String key(String value, String name) {
        String result = required(value, name);
        if (!result.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,159}"))
            throw new IllegalArgumentException(name + " must be an opaque registered key");
        return result;
    }

    private static String sha256(String value, String name) {
        String result = required(value, name).toLowerCase(java.util.Locale.ROOT);
        if (!result.matches("[0-9a-f]{64}")) throw new IllegalArgumentException(name + " must be SHA-256");
        return result;
    }

    private static String required(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
