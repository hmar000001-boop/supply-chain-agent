package dev.supplychain.agent.mechanisms.b2;

import dev.supplychain.agent.actions.api.ActionId;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Host-authored typed references only; this is not populated directly from an LLM response. */
public record RecoveryProcedureDraftRequest(String draftId, String impasseCaseId, long expectedCaseVersion,
                                            MemoryScopeId scopeId, ParentContextSnapshot currentParent,
                                            String conditionKey, List<ActionId> actionIds,
                                            List<String> preconditionKeys, List<String> expectedPostconditionKeys,
                                            List<String> failureConditionKeys, Instant expiresAt) {
    public RecoveryProcedureDraftRequest {
        draftId = required(draftId, "draftId");
        impasseCaseId = required(impasseCaseId, "impasseCaseId");
        if (expectedCaseVersion < 0) throw new IllegalArgumentException("expectedCaseVersion cannot be negative");
        scopeId = Objects.requireNonNull(scopeId, "scopeId");
        currentParent = Objects.requireNonNull(currentParent, "currentParent");
        if (!scopeId.equals(currentParent.scopeId())) throw new SecurityException("draft request scope differs from parent scope");
        conditionKey = key(conditionKey, "conditionKey");
        actionIds = List.copyOf(Objects.requireNonNull(actionIds, "actionIds"));
        if (actionIds.isEmpty() || actionIds.stream().anyMatch(Objects::isNull)
                || actionIds.stream().distinct().count() != actionIds.size()) throw new IllegalArgumentException("unique registered action IDs are required");
        preconditionKeys = keys(preconditionKeys, "preconditionKeys");
        expectedPostconditionKeys = keys(expectedPostconditionKeys, "expectedPostconditionKeys");
        failureConditionKeys = keys(failureConditionKeys, "failureConditionKeys");
        if (preconditionKeys.isEmpty() || expectedPostconditionKeys.isEmpty() || failureConditionKeys.isEmpty())
            throw new IllegalArgumentException("typed precondition, postcondition and failure-condition keys are required");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }
    private static List<String> keys(List<String> values, String name) { return List.copyOf(values).stream().map(v -> key(v, name)).distinct().toList(); }
    private static String key(String value, String name) {
        String v = required(value, name);
        if (!v.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,159}")) throw new IllegalArgumentException(name + " must be an opaque registered key");
        return v;
    }
    private static String required(String value, String name) { String v=Objects.requireNonNull(value,name).trim();if(v.isEmpty())throw new IllegalArgumentException(name+" cannot be blank");return v; }
}
