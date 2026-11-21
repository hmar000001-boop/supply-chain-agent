package dev.supplychain.agent.runtime.cognitive.decision;

import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.memory.retrieval.MemoryRetrievalQuery;
import dev.supplychain.agent.memory.retrieval.MemoryRetrievalResult;
import dev.supplychain.agent.mechanisms.b1.B1CandidateFeatureProvider;
import dev.supplychain.agent.mechanisms.b1.B1SelectionRequest;
import dev.supplychain.agent.mechanisms.b1.B1SelectionResult;
import dev.supplychain.agent.mechanisms.b1.B1MemoryCandidate;
import dev.supplychain.agent.mechanisms.b1.B1MemoryCandidateReference;
import dev.supplychain.agent.mechanisms.b1.B1MemoryCandidateReference;
import java.util.List;
import dev.supplychain.agent.mechanisms.b3.WorkspaceAdmissionRequest;
import dev.supplychain.agent.mechanisms.b3.WorkspaceBroadcastReport;
import dev.supplychain.agent.mechanisms.b6.UncertaintyAssessment;
import dev.supplychain.agent.planning.api.PlanningRequest;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleOperations;

import java.time.Instant;
import java.util.Set;

/**
 * Domain translation and durable artifact boundary for the decision-side integration.
 *
 * <p>This port deliberately does not perform retrieval, B1 selection, B3 admission/broadcast,
 * planner routing or deterministic candidate evaluation. Those are invoked by the real services
 * in {@link CognitiveCycleDecisionServices}. Implementations only translate supply-chain domain
 * state into their typed inputs and persist domain evidence/receipt records.</p>
 */
public interface CognitiveCycleDecisionPort {

    MemoryScopeId memoryScope(CognitiveCycleOperations.CycleContext context);

    MemoryRetrievalQuery retrievalQuery(CognitiveCycleOperations.CycleContext context, Instant at);

    B1SelectionRequest b1SelectionRequest(CognitiveCycleOperations.CycleContext context, Instant at);

    B1CandidateFeatureProvider b1FeatureProvider(CognitiveCycleOperations.CycleContext context);

    /**
     * Persist the M2 result and B1 selection as a provenance-bearing evidence-set record.
     * Implementations must make this write idempotent for a replayed execution/retrieval identity:
     * graph checkpoint interruption may cause the same selection to be persisted again before
     * the working-memory projection version is checkpointed.
     */
    CognitiveCycleOperations.EvidenceRetrieved persistEvidenceSelection(
            CognitiveCycleOperations.CycleContext context,
            MemoryRetrievalResult retrieved,
            B1SelectionResult selected,
            Instant at);

    /**
     * Resolve the exact, current uncertainty revision referenced by the graph context. The returned
     * assessment must match context.requireUncertaintyAssessmentReference(); returning a newer
     * revision is not a substitute because it would make the checkpoint's decision inputs ambiguous.
     */
    UncertaintyAssessment currentUncertaintyAssessment(
            CognitiveCycleOperations.CycleContext context, Instant at);

    /**
     * Build B3 candidates from the evidence set referenced by the graph context. The resulting
     * WorkspaceAdmissionRequest must carry context.requireUncertaintyAssessmentReference() exactly.
     */
    WorkspaceAdmissionRequest workspaceAdmissionRequest(
            CognitiveCycleOperations.CycleContext context, Instant at);

    /**
     * Return SHA-256 of the immutable experimental scenario inputs for this run. This fingerprint
     * must be identical when the same scenario is replayed in training and holdout, regardless of
     * planner/policy outputs. Empty is permitted for non-experimental runs but makes their B3
     * outcomes ineligible for adaptive-policy training.
     */
    default String experimentUnitFingerprint(CognitiveCycleOperations.CycleContext context, Instant at) {
        return "";
    }

    /** Return the explicitly configured downstream workspace consumers. */
    Set<String> workspaceConsumers(CognitiveCycleOperations.CycleContext context);

    /** Persist broadcast receipts for audit/diagnostics; this does not alter the workspace snapshot. */
    void recordWorkspaceBroadcast(
            CognitiveCycleOperations.CycleContext context,
            WorkspaceBroadcastReport report,
            Instant at);

    /**
     * Build a complete M5 request using context.requireUncertaintyAssessmentReference() exactly.
     * The reference participates in the planner-session fingerprint so checkpoint resume cannot
     * silently continue under a different uncertainty revision.
     */
    PlanningRequest planningRequest(CognitiveCycleOperations.CycleContext context, Instant at);


    /**
     * Obtain an independently performed review of competing registered hypotheses for this exact
     * request. The host implementation must issue evidence only after a reviewer distinct from the
     * proposing model/planner has examined at least two registered alternatives. Empty fails closed
     * when the calibrated B6 directive requires review. Model-authored text is not reviewer evidence.
     */
    default java.util.Optional<dev.supplychain.agent.planning.api.IndependentAlternativeHypothesisReview>
            independentlyReviewAlternativeHypotheses(
                    CognitiveCycleOperations.CycleContext context, PlanningRequest request, Instant at) {
        return java.util.Optional.empty();
    }

    /**
     * Separate host-owned verifier for B6 high plan uncertainty. Empty fails closed when the
     * exact planning request carries a directive requiring independent verification.
     */
    default java.util.Optional<IndependentPlanVerification> independentlyVerifySelectedPlan(
            CognitiveCycleOperations.CycleContext context,
            CognitiveCycleDecisionServices.SelectedPlanningCandidate selected,
            PlanningRequest freshRequest, Instant at) {
        return java.util.Optional.empty();
    }


    /**
     * Exact per-workspace-candidate attribution to previously selected B1 memory revisions.
     * Adapters return only references present in the persisted B1 evidence set. Empty is allowed
     * for candidates with no memory-derived provenance; such candidates do not update B1 features.
     */
    default java.util.Map<String, List<B1MemoryCandidateReference>> workspaceCandidateMemoryAttributions(
            CognitiveCycleOperations.CycleContext context, Instant at) {
        return java.util.Map.of();
    }

    /** The exact B1 candidates recorded by the active persisted evidence set, for attribution validation. */
    default List<B1MemoryCandidate> selectedB1MemoryCandidates(
            CognitiveCycleOperations.CycleContext context, Instant at) {
        return List.of();
    }

    /**
     * Opaque, registered condition keys derived from trusted current domain/evidence state.
     * Model-authored free-form text must never be used as a procedure lookup key.
     */
    default Set<String> recoveryProcedureConditionKeys(CognitiveCycleOperations.CycleContext context, Instant at) {
        return Set.of();
    }
}
