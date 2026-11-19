package dev.supplychain.agent.runtime.cognitive.lifecycle;

import dev.supplychain.agent.actions.api.ActionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Reconciliation envelope for one selected plan; individual M3 idempotency keys remain authoritative. */
public record ActionExecutionBatch(String batchId, String executionId, String principalId,
                                   String candidateSetId, String candidateId, String planFingerprint,
                                   BatchStatus status, List<ActionExecutionItem> items,
                                   Instant createdAt, Instant updatedAt, long revision) {
    public ActionExecutionBatch {
        batchId = required(batchId, "batchId");
        executionId = required(executionId, "executionId");
        principalId = required(principalId, "principalId");
        candidateSetId = required(candidateSetId, "candidateSetId");
        candidateId = required(candidateId, "candidateId");
        planFingerprint = required(planFingerprint, "planFingerprint");
        status = Objects.requireNonNull(status, "status");
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (revision < 1) throw new IllegalArgumentException("revision must be positive");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("batch time moved backwards");
        if (items.stream().map(ActionExecutionItem::stepIndex).distinct().count() != items.size()) {
            throw new IllegalArgumentException("action batch contains duplicate step indexes");
        }
    }

    public ActionExecutionBatch(String batchId, String executionId, String principalId,
                                String candidateSetId, String candidateId, String planFingerprint,
                                BatchStatus status, List<ActionExecutionItem> items,
                                Instant createdAt, Instant updatedAt) {
        this(batchId, executionId, principalId, candidateSetId, candidateId, planFingerprint,
                status, items, createdAt, updatedAt, 1);
    }

    public boolean hasStep(int index) { return items.stream().anyMatch(item -> item.stepIndex() == index); }
    public boolean hasSuccessfulEffect() {
        return items.stream().anyMatch(item -> item.status() == ActionStatus.SUCCEEDED
                || item.status() == ActionStatus.ALREADY_COMPLETED);
    }

    public record ActionExecutionItem(int stepIndex, String actionId, ActionStatus status,
                                      String operationId, String resultReference,
                                      String failureCode, String failureMessage, boolean outcomeUnknown) {
        public ActionExecutionItem {
            if (stepIndex < 0) throw new IllegalArgumentException("stepIndex cannot be negative");
            actionId = required(actionId, "actionId");
            status = Objects.requireNonNull(status, "status");
            operationId = optional(operationId);
            resultReference = optional(resultReference);
            failureCode = optional(failureCode);
            failureMessage = optional(failureMessage);
            if ((status == ActionStatus.SUCCEEDED || status == ActionStatus.ALREADY_COMPLETED) && operationId.isEmpty()) {
                throw new IllegalArgumentException("successful action must contain its idempotent operation reference");
            }
            if (outcomeUnknown && status != ActionStatus.FAILED && status != ActionStatus.IN_PROGRESS) {
                throw new IllegalArgumentException("unknown outcome requires a failed or in-progress action status");
            }
        }
    }

    public enum BatchStatus { STARTED, SUCCEEDED, PARTIAL, UNKNOWN_OUTCOME, REJECTED, FAILED }

    private static String required(String v, String n) {
        String s = Objects.requireNonNull(v, n).trim();
        if (s.isEmpty()) throw new IllegalArgumentException(n + " cannot be blank");
        return s;
    }
    private static String optional(String v) { return v == null ? "" : v.trim(); }
}
