package dev.supplychain.agent.runtime.cognitive;

import dev.supplychain.agent.core.interpretation.InterpretationOutcome;
import dev.supplychain.agent.mechanisms.b6.MetaControlDecision;
import dev.supplychain.agent.planning.api.PlannerId;
import dev.supplychain.agent.planning.api.PlannerTerminationReason;

import java.util.Objects;

/**
 * Application-owned integration boundary used by the LangGraph4j cognitive-cycle controller.
 *
 * <p>Implementations compose the already-defined CoALA memory/action services, B1/B2/B3/B4/B6
 * mechanisms and M5 planners. The graph owns lifecycle, routing, checkpoints and interruptions;
 * this interface deliberately does not turn those services or algorithms into graph nodes.</p>
 *
 * <p>All returned identifiers refer to authoritative records managed outside graph state. Graph
 * state contains only those references and small scalar routing values.</p>
 */
public interface CognitiveCycleOperations {

    InterpretationResult interpret(CycleContext context);

    /** Persists the actual clarification supplied by the host/user before the next interpretation pass. */
    void recordClarification(CycleContext context);

    /** Opens/loads versioned CoALA working memory and the current B4 recovery intention. */
    ContextLoaded loadContext(CycleContext context);

    /** Performs M2 cross-memory retrieval followed by B1 selection/activation. */
    EvidenceRetrieved retrieveEvidence(CycleContext context);

    /** Accepts evidence supplied through the host after the graph's evidence interruption. */
    EvidenceAccepted acceptExternalEvidence(CycleContext context);

    /** Applies B3 admission and broadcast to the B1-selected memory/evidence candidates. */
    WorkspacePrepared prepareWorkspace(CycleContext context);

    /** Invokes the M5 PlannerRouter; planner search remains internal to the selected strategy. */
    PlanningPrepared proposeCandidates(CycleContext context);

    /** Applies the common deterministic candidate evaluator and hard-constraint checks. */
    CandidateEvaluation evaluateCandidates(CycleContext context);

    /** B2 child-resolution lifecycle, called by the bounded resolution subgraph. */
    ImpasseOpened openImpasse(CycleContext context);
    ImpasseInvestigated investigateImpasse(CycleContext context);
    ImpasseResolved validateImpasseReturn(CycleContext context);

    /** Applies B4 objective/method authority; this must not silently redefine the objective. */
    IntentionGovernance governIntention(CycleContext context);

    /** Applies B6 uncertainty/resource policy and persists any budget reduction it selects. */
    MetaControlDecision governMetaControl(CycleContext context);

    /** Resolves a B6 human-control interruption using a durable, host-issued response reference. */
    HumanControlResolution resolveHumanControl(CycleContext context);

    /**
     * Checks trusted action descriptors, capability scopes and approval policy. A state-changing
     * candidate must return REQUIRES_APPROVAL or DENIED; it can never return NO_APPROVAL_REQUIRED.
     */
    AuthorizationPrepared prepareAuthorization(CycleContext context);

    /** Resolves the host-supplied response against the durable approval record and exact plan version. */
    ApprovalResolved resolveApproval(CycleContext context);

    /** Rechecks approval binding, candidate freshness, feasibility and authority immediately before execution. */
    PlanRevalidation revalidatePlan(CycleContext context);

    /** Executes through the M3 ActionDispatcher and its capability/idempotency boundary. */
    AuthorizedExecution executeAuthorizedCandidate(CycleContext context);

    /** Reads/reconciles external state; UNKNOWN_OUTCOME must never trigger a blind action retry. */
    ExternalObservation observeAndReconcile(CycleContext context);

    /**
     * Applies the observation to the active CoALA working memory using optimistic version checks.
     * A version conflict returns CONFLICT so the parent can reload/reconsider instead of overwriting.
     */
    WorkingMemoryUpdated updateWorkingMemoryFromObservation(CycleContext context, ExternalObservation observation);

    /**
     * Retries the write using the already-persisted observation identified by context.observationId().
     * This path must never re-execute the external action.
     */
    WorkingMemoryUpdated updateWorkingMemoryFromStoredObservation(CycleContext context);

    /** Records an outcome episode as unverified; this operation must not self-promote semantic or procedural knowledge. */
    EpisodeRecorded recordOutcomeEpisode(CycleContext context);

    /** Records an operator-visible stop/escalation decision without executing additional actions. */
    void recordEscalation(CycleContext context, String reason);

    record CycleContext(
            String requestId,
            String executionId,
            String memoryScopeId,
            String request,
            String normalizedIntent,
            String summary,
            String clarificationContext,
            int clarificationRound,
            String workingMemoryId,
            long workingMemoryVersion,
            String evidenceSetId,
            int evidenceRequestCount,
            String evidenceResponseId,
            String workspaceSnapshotId,
            String intentionId,
            long intentionVersion,
            String plannerId,
            String plannerSessionId,
            String candidateSetId,
            int planningRound,
            String selectedCandidateId,
            String impasseCaseId,
            String impasseInvestigationReference,
            String approvalId,
            String approvalResponseId,
            String approverId,
            String humanControlResponseId,
            String humanControlActorId,
            String executionOperationId,
            String observationId,
            String lastReason) {
        public CycleContext {
            requestId = optional(requestId);
            executionId = optional(executionId);
            memoryScopeId = optional(memoryScopeId);
            request = optional(request);
            normalizedIntent = optional(normalizedIntent);
            summary = optional(summary);
            clarificationContext = optional(clarificationContext);
            workingMemoryId = optional(workingMemoryId);
            evidenceSetId = optional(evidenceSetId);
            evidenceResponseId = optional(evidenceResponseId);
            workspaceSnapshotId = optional(workspaceSnapshotId);
            intentionId = optional(intentionId);
            plannerId = optional(plannerId);
            plannerSessionId = optional(plannerSessionId);
            candidateSetId = optional(candidateSetId);
            selectedCandidateId = optional(selectedCandidateId);
            impasseCaseId = optional(impasseCaseId);
            impasseInvestigationReference = optional(impasseInvestigationReference);
            approvalId = optional(approvalId);
            approvalResponseId = optional(approvalResponseId);
            approverId = optional(approverId);
            humanControlResponseId = optional(humanControlResponseId);
            humanControlActorId = optional(humanControlActorId);
            executionOperationId = optional(executionOperationId);
            observationId = optional(observationId);
            lastReason = optional(lastReason);
            if (clarificationRound < 0 || workingMemoryVersion < 0 || evidenceRequestCount < 0
                    || intentionVersion < 0 || planningRound < 0) {
                throw new IllegalArgumentException("cycle counters and versions must be nonnegative");
            }
        }

        private static String optional(String value) {
            return value == null ? "" : value.trim();
        }
    }

    record InterpretationResult(InterpretationOutcome outcome, String normalizedIntent, String summary) {
        public InterpretationResult {
            outcome = Objects.requireNonNull(outcome, "outcome");
            normalizedIntent = required(normalizedIntent, "normalizedIntent");
            summary = required(summary, "summary");
        }
    }

    record ContextLoaded(String workingMemoryId, long workingMemoryVersion,
                         String intentionId, long intentionVersion) {
        public ContextLoaded {
            workingMemoryId = required(workingMemoryId, "workingMemoryId");
            intentionId = optional(intentionId);
            if (workingMemoryVersion < 0 || intentionVersion < 0) {
                throw new IllegalArgumentException("memory versions must be nonnegative");
            }
            if (intentionId.isEmpty() && intentionVersion != 0) {
                throw new IllegalArgumentException("an absent intention must have version zero");
            }
        }
    }

    enum EvidenceDisposition { READY, NEEDS_EXTERNAL_EVIDENCE, IMPASSE, FAILED }
    record EvidenceRetrieved(EvidenceDisposition disposition, String evidenceSetId,
                             double maximumUncertainty, String reason) {
        public EvidenceRetrieved {
            disposition = Objects.requireNonNull(disposition, "disposition");
            evidenceSetId = optional(evidenceSetId);
            reason = required(reason, "reason");
            unit(maximumUncertainty, "maximumUncertainty");
            if (disposition == EvidenceDisposition.READY && evidenceSetId.isEmpty()) {
                throw new IllegalArgumentException("ready retrieval requires an evidence-set reference");
            }
        }
    }

    enum EvidenceAcceptance { ACCEPTED, IMPASSE, REJECTED }
    record EvidenceAccepted(EvidenceAcceptance acceptance, String evidenceSetId, String reason) {
        public EvidenceAccepted {
            acceptance = Objects.requireNonNull(acceptance, "acceptance");
            evidenceSetId = optional(evidenceSetId);
            reason = required(reason, "reason");
            if (acceptance == EvidenceAcceptance.ACCEPTED && evidenceSetId.isEmpty()) {
                throw new IllegalArgumentException("accepted evidence requires an evidence-set reference");
            }
        }
    }

    enum WorkspaceDisposition { ADMITTED, INCOMPLETE, INSUFFICIENT_CAPACITY, FAILED }
    record WorkspacePrepared(WorkspaceDisposition disposition, String workspaceSnapshotId, String reason) {
        public WorkspacePrepared {
            disposition = Objects.requireNonNull(disposition, "disposition");
            workspaceSnapshotId = optional(workspaceSnapshotId);
            reason = required(reason, "reason");
            if ((disposition == WorkspaceDisposition.ADMITTED || disposition == WorkspaceDisposition.INCOMPLETE)
                    && workspaceSnapshotId.isEmpty()) {
                throw new IllegalArgumentException("admitted or partial workspace requires a snapshot reference");
            }
        }
    }

    record PlanningPrepared(PlannerId plannerId, String plannerSessionId, String candidateSetId,
                            int candidateCount, PlannerTerminationReason terminationReason) {
        public PlanningPrepared {
            plannerId = Objects.requireNonNull(plannerId, "plannerId");
            plannerSessionId = required(plannerSessionId, "plannerSessionId");
            candidateSetId = optional(candidateSetId);
            terminationReason = Objects.requireNonNull(terminationReason, "terminationReason");
            if (candidateCount < 0) throw new IllegalArgumentException("candidateCount cannot be negative");
            if (candidateCount > 0 && candidateSetId.isEmpty()) {
                throw new IllegalArgumentException("non-empty candidate set requires a reference");
            }
        }
    }

    enum EvaluationDisposition { SELECTED, NEEDS_MORE_SEARCH, NEEDS_EVIDENCE, IMPASSE, NO_FEASIBLE_CANDIDATE }
    record CandidateEvaluation(EvaluationDisposition disposition, String selectedCandidateId, String reason) {
        public CandidateEvaluation {
            disposition = Objects.requireNonNull(disposition, "disposition");
            selectedCandidateId = optional(selectedCandidateId);
            reason = required(reason, "reason");
            if (disposition == EvaluationDisposition.SELECTED && selectedCandidateId.isEmpty()) {
                throw new IllegalArgumentException("selected evaluation requires a candidate reference");
            }
            if (disposition != EvaluationDisposition.SELECTED && !selectedCandidateId.isEmpty()) {
                throw new IllegalArgumentException("non-selected evaluation cannot carry a selected candidate");
            }
        }
    }

    record ImpasseOpened(String caseId, String reason) {
        public ImpasseOpened {
            caseId = required(caseId, "caseId");
            reason = required(reason, "reason");
        }
    }
    record ImpasseInvestigated(String investigationReference, String summary) {
        public ImpasseInvestigated {
            investigationReference = required(investigationReference, "investigationReference");
            summary = required(summary, "summary");
        }
    }
    enum ImpasseDisposition { RESOLVED, UNRESOLVED, ESCALATE }
    record ImpasseResolved(ImpasseDisposition disposition, String findingsReference, String reason) {
        public ImpasseResolved {
            disposition = Objects.requireNonNull(disposition, "disposition");
            findingsReference = optional(findingsReference);
            reason = required(reason, "reason");
            if (disposition == ImpasseDisposition.RESOLVED && findingsReference.isEmpty()) {
                throw new IllegalArgumentException("resolved impasse requires a validated findings reference");
            }
        }
    }

    enum IntentionAction { CONTINUE, RECONSIDER, CHANGE_METHOD, SUSPEND, ESCALATE, TERMINATE }
    record IntentionGovernance(IntentionAction action, String intentionId, long intentionVersion, String reason) {
        public IntentionGovernance {
            action = Objects.requireNonNull(action, "action");
            intentionId = required(intentionId, "intentionId");
            reason = required(reason, "reason");
            if (intentionVersion < 0) throw new IllegalArgumentException("intentionVersion cannot be negative");
        }
    }

    enum HumanControlAction { CONTINUE, PROVIDE_EVIDENCE, REDUCE_COMPUTATION, ABORT }
    record HumanControlResolution(HumanControlAction action, String actorId,
                                  String responseReference, String reason) {
        public HumanControlResolution {
            action = Objects.requireNonNull(action, "action");
            actorId = required(actorId, "actorId");
            responseReference = required(responseReference, "responseReference");
            reason = required(reason, "reason");
        }
    }

    enum AuthorizationDisposition { NO_APPROVAL_REQUIRED, REQUIRES_APPROVAL, DENIED }
    record AuthorizationPrepared(AuthorizationDisposition disposition, boolean stateChanging,
                                 String approvalId, String reason) {
        public AuthorizationPrepared {
            disposition = Objects.requireNonNull(disposition, "disposition");
            approvalId = optional(approvalId);
            reason = required(reason, "reason");
            if (stateChanging && disposition == AuthorizationDisposition.NO_APPROVAL_REQUIRED) {
                throw new SecurityException("state-changing candidates cannot bypass explicit approval");
            }
            if (disposition == AuthorizationDisposition.REQUIRES_APPROVAL
                    && (!stateChanging || approvalId.isEmpty())) {
                throw new IllegalArgumentException("approval-required decision must bind a state-changing candidate to an approval record");
            }
            if (disposition == AuthorizationDisposition.NO_APPROVAL_REQUIRED && !approvalId.isEmpty()) {
                throw new IllegalArgumentException("no-approval decision cannot carry an approval record");
            }
        }
    }

    enum ApprovalDisposition { APPROVED, DENIED, EXPIRED, INVALID, NOT_FOUND }
    record ApprovalResolved(ApprovalDisposition disposition, String approvalId,
                            String candidateId, String candidateSetId, String intentionId,
                            long intentionVersion, String approverId, String evidenceReference,
                            String reason) {
        public ApprovalResolved {
            disposition = Objects.requireNonNull(disposition, "disposition");
            approvalId = required(approvalId, "approvalId");
            candidateId = required(candidateId, "candidateId");
            candidateSetId = required(candidateSetId, "candidateSetId");
            intentionId = required(intentionId, "intentionId");
            if (intentionVersion < 0) throw new IllegalArgumentException("intentionVersion cannot be negative");
            approverId = optional(approverId);
            evidenceReference = optional(evidenceReference);
            reason = required(reason, "reason");
            if (disposition == ApprovalDisposition.APPROVED && (approverId.isEmpty() || evidenceReference.isEmpty())) {
                throw new IllegalArgumentException("approved response requires an identified approver and evidence reference");
            }
        }
    }

    enum RevalidationDisposition { VALID, STALE, DENIED, NEEDS_EVIDENCE }
    record PlanRevalidation(RevalidationDisposition disposition, String reason) {
        public PlanRevalidation {
            disposition = Objects.requireNonNull(disposition, "disposition");
            reason = required(reason, "reason");
        }
    }

    enum ExecutionDisposition { SUCCEEDED, UNKNOWN_OUTCOME, REJECTED, FAILED }
    record AuthorizedExecution(ExecutionDisposition disposition, String operationId,
                               String resultReference, String reason) {
        public AuthorizedExecution {
            disposition = Objects.requireNonNull(disposition, "disposition");
            operationId = required(operationId, "operationId");
            resultReference = optional(resultReference);
            reason = required(reason, "reason");
        }
    }

    enum ObservationDisposition { OBJECTIVE_SATISFIED, RECONSIDER, NEEDS_EVIDENCE, NOT_VERIFIED }
    record ExternalObservation(ObservationDisposition disposition, String observationId,
                               String evidenceSetId, String reason) {
        public ExternalObservation {
            disposition = Objects.requireNonNull(disposition, "disposition");
            observationId = required(observationId, "observationId");
            evidenceSetId = optional(evidenceSetId);
            reason = required(reason, "reason");
        }
    }

    enum WorkingMemoryUpdateDisposition { UPDATED, CONFLICT, FAILED }
    record WorkingMemoryUpdated(WorkingMemoryUpdateDisposition disposition, String workingMemoryId,
                                long workingMemoryVersion, String reason) {
        public WorkingMemoryUpdated {
            disposition = Objects.requireNonNull(disposition, "disposition");
            workingMemoryId = optional(workingMemoryId);
            if (workingMemoryVersion < 0) throw new IllegalArgumentException("workingMemoryVersion cannot be negative");
            reason = required(reason, "reason");
            if (disposition == WorkingMemoryUpdateDisposition.UPDATED
                    && (workingMemoryId.isEmpty() || workingMemoryVersion == 0)) {
                throw new IllegalArgumentException("updated working memory requires an identity and positive version");
            }
        }
    }

    enum EpisodeVerificationStatus { RECORDED_UNVERIFIED }
    record EpisodeRecorded(String episodeId, EpisodeVerificationStatus verificationStatus) {
        public EpisodeRecorded {
            episodeId = required(episodeId, "episodeId");
            verificationStatus = Objects.requireNonNull(verificationStatus, "verificationStatus");
        }
    }

    private static String required(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
    private static String optional(String value) { return value == null ? "" : value.trim(); }
    private static void unit(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be in [0,1]");
        }
    }
}
