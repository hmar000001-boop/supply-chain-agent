package dev.supplychain.agent.runtime.cognitive.lifecycle;

import dev.supplychain.agent.actions.api.ActionDescriptor;
import dev.supplychain.agent.actions.api.ActionPrincipal;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleOperations;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Trusted identity/approval adapter. No model, planner or graph state can manufacture its answers. */
public interface CognitiveCycleAuthorityPort {
    CognitiveCycleOperations.AuthorizationPrepared requestApproval(
            CognitiveCycleOperations.CycleContext context, ApprovalBinding binding,
            List<ActionDescriptor> actionDescriptors, Instant at);

    CognitiveCycleOperations.ApprovalResolved resolveApproval(
            CognitiveCycleOperations.CycleContext context, ApprovalBinding binding, Instant at);

    /** Rechecks approval status, expiry, revocation and exact binding immediately before dispatch. */
    boolean isApprovalStillValid(CognitiveCycleOperations.CycleContext context,
                                 ApprovalBinding binding, String approvalId, Instant at);

    CognitiveCycleOperations.HumanControlResolution resolveHumanControl(
            CognitiveCycleOperations.CycleContext context, Instant at);

    /** Whether the supplied actor is currently authorized to terminate this protected intention. */
    boolean isTerminationAuthorized(CognitiveCycleOperations.CycleContext context, String actorId, Instant at);

    /** Returns an authenticated principal with pre-issued, resource-scoped grants. */
    ActionPrincipal executionPrincipal(CognitiveCycleOperations.CycleContext context, Instant at);

    record ApprovalBinding(String requestId, String executionId, String memoryScopeId,
                           String candidateSetId, String candidateId,
                           String intentionId, long intentionVersion,
                           String actionPlanFingerprint) {
        public ApprovalBinding {
            requestId = required(requestId, "requestId");
            executionId = required(executionId, "executionId");
            memoryScopeId = required(memoryScopeId, "memoryScopeId");
            candidateSetId = required(candidateSetId, "candidateSetId");
            candidateId = required(candidateId, "candidateId");
            intentionId = required(intentionId, "intentionId");
            actionPlanFingerprint = required(actionPlanFingerprint, "actionPlanFingerprint");
            if (intentionVersion < 0) throw new IllegalArgumentException("intentionVersion cannot be negative");
        }
    }

    private static String required(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
