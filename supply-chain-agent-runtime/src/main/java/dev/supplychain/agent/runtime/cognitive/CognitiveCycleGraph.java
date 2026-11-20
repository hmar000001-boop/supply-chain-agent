package dev.supplychain.agent.runtime.cognitive;

import dev.supplychain.agent.mechanisms.b6.MetaControlAction;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleOperations.*;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * Main CoALA cognitive-cycle controller. LangGraph4j owns application-level sequencing, routing,
 * interruption and checkpointing; the services and planning algorithms remain independent Java
 * components invoked by {@link CognitiveCycleOperations}.
 */
public final class CognitiveCycleGraph {
    public static final String NODE_INITIALIZE = "initialize";
    public static final String NODE_INTERPRET = "interpret";
    public static final String NODE_AWAIT_CLARIFICATION = "await-clarification";
    public static final String NODE_LOAD_CONTEXT = "load-context";
    public static final String NODE_RETRIEVE = "retrieve-evidence";
    public static final String NODE_AWAIT_EVIDENCE = "await-evidence";
    public static final String NODE_WORKSPACE = "workspace-admission-and-broadcast";
    public static final String NODE_PROPOSE = "propose-candidates";
    public static final String NODE_EVALUATE = "evaluate-candidates";
    public static final String NODE_B2 = "b2-impasse-resolution";
    public static final String NODE_INTENTION = "b4-intention-governance";
    public static final String NODE_META_CONTROL = "b6-meta-control";
    public static final String NODE_AWAIT_HUMAN_CONTROL = "await-human-control";
    public static final String NODE_AUTHORIZE = "authorize-candidate";
    public static final String NODE_AWAIT_APPROVAL = "await-approval";
    public static final String NODE_REVALIDATE = "revalidate-plan-and-approval";
    public static final String NODE_EXECUTE = "execute-authorized-actions";
    public static final String NODE_OBSERVE = "observe-and-reconcile";
    public static final String NODE_RELOAD_MEMORY_FOR_OBSERVATION = "reload-memory-for-observation";
    public static final String NODE_RETRY_MEMORY_UPDATE = "retry-observation-memory-update";
    public static final String NODE_LEARN = "record-outcome-episode";
    public static final String NODE_COMPLETE = "complete";
    public static final String NODE_ESCALATE = "escalate-or-stop";

    private static final int MAX_CLARIFICATION_ROUNDS = 2;
    private static final int MAX_EVIDENCE_REQUESTS = 3;
    private static final int MAX_PLANNING_ROUNDS = 4;
    private static final int MAX_RECONSIDERATIONS = 5;
    private static final int MAX_OBSERVATION_MEMORY_RETRIES = 3;

    private CognitiveCycleGraph() { }

    public static CompiledGraph<CognitiveCycleState> compile(
            CognitiveCycleOperations operations,
            BaseCheckpointSaver checkpointSaver) throws GraphStateException {
        Objects.requireNonNull(operations, "operations");
        Objects.requireNonNull(checkpointSaver, "checkpointSaver");

        var b2 = B2ResolutionSubgraph.compile(operations);

        NodeAction<CognitiveCycleState> initialize = state -> {
            require(state.text(CognitiveCycleState.REQUEST_ID), CognitiveCycleState.REQUEST_ID);
            require(state.text(CognitiveCycleState.EXECUTION_ID), CognitiveCycleState.EXECUTION_ID);
            require(state.text(CognitiveCycleState.MEMORY_SCOPE_ID), CognitiveCycleState.MEMORY_SCOPE_ID);
            require(state.text(CognitiveCycleState.REQUEST), CognitiveCycleState.REQUEST);
            return update("request-accepted:" + state.text(CognitiveCycleState.REQUEST_ID),
                    CognitiveCycleState.DECISION_STATUS, "RUNNING");
        };

        NodeAction<CognitiveCycleState> interpret = state -> {
            var result = Objects.requireNonNull(operations.interpret(state.context()), "interpretation result");
            return update("request-interpreted:" + result.outcome().name(),
                    CognitiveCycleState.NORMALIZED_INTENT, result.normalizedIntent(),
                    CognitiveCycleState.SUMMARY, result.summary(),
                    CognitiveCycleState.DECISION_STATUS, result.outcome().name(),
                    CognitiveCycleState.LAST_REASON, result.summary());
        };

        NodeAction<CognitiveCycleState> acceptClarification = state -> {
            if (state.text(CognitiveCycleState.CLARIFICATION_CONTEXT).isBlank()) {
                throw new IllegalStateException("The graph resumed without actual user clarification; refusing to fabricate input");
            }
            int nextRound = state.integer(CognitiveCycleState.CLARIFICATION_ROUND) + 1;
            if (nextRound > MAX_CLARIFICATION_ROUNDS) {
                throw new IllegalStateException("Clarification limit exceeded");
            }
            operations.recordClarification(state.context());
            return update("user-clarification-recorded:" + nextRound,
                    CognitiveCycleState.CLARIFICATION_ROUND, nextRound,
                    CognitiveCycleState.DECISION_STATUS, "CLARIFICATION_RECORDED");
        };

        NodeAction<CognitiveCycleState> loadContext = state -> {
            var loaded = Objects.requireNonNull(operations.loadContext(state.context()), "loaded context");
            return update("working-context-loaded:" + loaded.workingMemoryId(),
                    CognitiveCycleState.WORKING_MEMORY_ID, loaded.workingMemoryId(),
                    CognitiveCycleState.WORKING_MEMORY_VERSION, loaded.workingMemoryVersion(),
                    CognitiveCycleState.INTENTION_ID, loaded.intentionId(),
                    CognitiveCycleState.INTENTION_VERSION, loaded.intentionVersion(),
                    CognitiveCycleState.DECISION_STATUS, "CONTEXT_READY");
        };

        NodeAction<CognitiveCycleState> retrieve = state -> {
            var result = Objects.requireNonNull(operations.retrieveEvidence(state.context()), "evidence result");
            if (result.workingMemoryId().isBlank()) {
                return update("evidence-retrieval:" + result.disposition().name(),
                        CognitiveCycleState.EVIDENCE_SET_ID, result.evidenceSetId(),
                        CognitiveCycleState.EVIDENCE_MAXIMUM_UNCERTAINTY, result.maximumUncertainty(),
                        CognitiveCycleState.DECISION_STATUS, result.disposition().name(),
                        CognitiveCycleState.LAST_REASON, result.reason());
            }
            return update("evidence-retrieval:" + result.disposition().name(),
                    CognitiveCycleState.EVIDENCE_SET_ID, result.evidenceSetId(),
                    CognitiveCycleState.EVIDENCE_MAXIMUM_UNCERTAINTY, result.maximumUncertainty(),
                    CognitiveCycleState.WORKING_MEMORY_ID, result.workingMemoryId(),
                    CognitiveCycleState.WORKING_MEMORY_VERSION, result.workingMemoryVersion(),
                    CognitiveCycleState.DECISION_STATUS, result.disposition().name(),
                    CognitiveCycleState.LAST_REASON, result.reason());
        };

        NodeAction<CognitiveCycleState> acceptEvidence = state -> {
            if (state.text(CognitiveCycleState.EVIDENCE_RESPONSE_ID).isBlank()) {
                throw new IllegalStateException("Evidence interruption resumed without a host-issued evidence response reference");
            }
            var accepted = Objects.requireNonNull(operations.acceptExternalEvidence(state.context()), "evidence acceptance result");
            int requests = state.integer(CognitiveCycleState.EVIDENCE_REQUEST_COUNT) + 1;
            return update("external-evidence:" + accepted.acceptance().name(),
                    CognitiveCycleState.EVIDENCE_SET_ID, accepted.evidenceSetId(),
                    CognitiveCycleState.EVIDENCE_RESPONSE_ID, "",
                    CognitiveCycleState.EVIDENCE_REQUEST_COUNT, requests,
                    CognitiveCycleState.DECISION_STATUS, accepted.acceptance().name(),
                    CognitiveCycleState.LAST_REASON, accepted.reason());
        };

        NodeAction<CognitiveCycleState> workspace = state -> {
            var result = Objects.requireNonNull(operations.prepareWorkspace(state.context()), "workspace result");
            return update("workspace:" + result.disposition().name(),
                    CognitiveCycleState.WORKSPACE_SNAPSHOT_ID, result.workspaceSnapshotId(),
                    CognitiveCycleState.DECISION_STATUS, result.disposition().name(),
                    CognitiveCycleState.LAST_REASON, result.reason());
        };

        NodeAction<CognitiveCycleState> propose = state -> {
            var result = Objects.requireNonNull(operations.proposeCandidates(state.context()), "planning result");
            int planningRound = state.integer(CognitiveCycleState.PLANNING_ROUND) + 1;
            return update("planner:" + result.plannerId().name() + ":" + result.terminationReason().name(),
                    CognitiveCycleState.PLANNER_ID, result.plannerId().name(),
                    CognitiveCycleState.PLANNER_SESSION_ID, result.plannerSessionId(),
                    CognitiveCycleState.CANDIDATE_SET_ID, result.candidateSetId(),
                    CognitiveCycleState.PLANNING_ROUND, planningRound,
                    CognitiveCycleState.PLANNER_TERMINATION_REASON, result.terminationReason().name(),
                    CognitiveCycleState.PLANNER_CANDIDATE_COUNT, result.candidateCount(),
                    CognitiveCycleState.DECISION_STATUS, "PLANS_PROPOSED",
                    CognitiveCycleState.LAST_REASON, "candidate-count=" + result.candidateCount());
        };

        NodeAction<CognitiveCycleState> evaluate = state -> {
            var result = Objects.requireNonNull(operations.evaluateCandidates(state.context()), "candidate evaluation result");
            return update("candidate-evaluation:" + result.disposition().name(),
                    CognitiveCycleState.SELECTED_CANDIDATE_ID, result.selectedCandidateId(),
                    CognitiveCycleState.DECISION_STATUS, result.disposition().name(),
                    CognitiveCycleState.LAST_REASON, result.reason());
        };

        NodeAction<CognitiveCycleState> resolveB2 = state -> {
            try {
                var child = B2ResolutionSubgraph.invoke(b2, state.context());
                return update("b2-resolution:" + child.text(B2ResolutionGraphState.DISPOSITION),
                        CognitiveCycleState.IMPASSE_CASE_ID, child.text(B2ResolutionGraphState.IMPASSE_CASE_ID),
                        CognitiveCycleState.IMPASSE_INVESTIGATION_REFERENCE,
                        child.text(B2ResolutionGraphState.INVESTIGATION_REFERENCE),
                        CognitiveCycleState.DECISION_STATUS, child.text(B2ResolutionGraphState.DISPOSITION),
                        CognitiveCycleState.LAST_REASON, child.text(B2ResolutionGraphState.REASON));
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IllegalStateException("B2 impasse-resolution subgraph failed", ex);
            }
        };

        NodeAction<CognitiveCycleState> governIntention = state -> {
            var result = Objects.requireNonNull(operations.governIntention(state.context()), "intention decision");
            int reconsiderations = state.integer(CognitiveCycleState.RECONSIDERATION_COUNT);
            if (result.action() == IntentionAction.RECONSIDER || result.action() == IntentionAction.CHANGE_METHOD) {
                reconsiderations++;
            }
            return update("b4-intention:" + result.action().name(),
                    CognitiveCycleState.RECONSIDERATION_COUNT, reconsiderations,
                    CognitiveCycleState.INTENTION_ID, result.intentionId(),
                    CognitiveCycleState.INTENTION_VERSION, result.intentionVersion(),
                    CognitiveCycleState.DECISION_STATUS, result.action().name(),
                    CognitiveCycleState.LAST_REASON, result.reason());
        };

        NodeAction<CognitiveCycleState> metaControl = state -> {
            var result = Objects.requireNonNull(operations.governMetaControl(state.context()), "meta-control decision");
            int metaControlRound = state.integer(CognitiveCycleState.META_CONTROL_ROUND) + 1;
            return update("b6-meta-control:" + result.action().name(),
                    CognitiveCycleState.META_CONTROL_ROUND, metaControlRound,
                    CognitiveCycleState.META_CONTROL_MAXIMUM_UNCERTAINTY, result.maximumUncertainty(),
                    CognitiveCycleState.DECISION_STATUS, result.action().name(),
                    CognitiveCycleState.LAST_REASON, result.explanation());
        };

        NodeAction<CognitiveCycleState> resolveHumanControl = state -> {
            if (state.text(CognitiveCycleState.HUMAN_CONTROL_RESPONSE_ID).isBlank()
                    || state.text(CognitiveCycleState.HUMAN_CONTROL_ACTOR_ID).isBlank()) {
                throw new IllegalStateException("Human-control interruption resumed without a host-issued response reference");
            }
            var result = Objects.requireNonNull(operations.resolveHumanControl(state.context()), "human-control result");
            if (!state.text(CognitiveCycleState.HUMAN_CONTROL_ACTOR_ID).equals(result.actorId())) {
                throw new SecurityException("human-control response actor does not match the authenticated host actor");
            }
            if (!state.text(CognitiveCycleState.HUMAN_CONTROL_RESPONSE_ID).equals(result.responseReference())) {
                throw new SecurityException("human-control response reference does not match the host-issued response");
            }
            return update("human-control:" + result.action().name(),
                    CognitiveCycleState.HUMAN_CONTROL_AUDIT_REFERENCE, result.responseReference(),
                    CognitiveCycleState.HUMAN_CONTROL_ACTOR_AUDIT_ID, result.actorId(),
                    CognitiveCycleState.HUMAN_CONTROL_RESPONSE_ID, "",
                    CognitiveCycleState.HUMAN_CONTROL_ACTOR_ID, "",
                    CognitiveCycleState.DECISION_STATUS, result.action().name(),
                    CognitiveCycleState.LAST_REASON, result.reason());
        };

        NodeAction<CognitiveCycleState> authorize = state -> {
            var result = Objects.requireNonNull(operations.prepareAuthorization(state.context()), "authorization result");
            return update("authorization:" + result.disposition().name(),
                    CognitiveCycleState.APPROVAL_ID, result.approvalId(),
                    CognitiveCycleState.STATE_CHANGING_CANDIDATE, result.stateChanging(),
                    CognitiveCycleState.DECISION_STATUS, result.disposition().name(),
                    CognitiveCycleState.LAST_REASON, result.reason());
        };

        NodeAction<CognitiveCycleState> resolveApproval = state -> {
            if (state.text(CognitiveCycleState.APPROVAL_ID).isBlank()
                    || state.text(CognitiveCycleState.APPROVAL_RESPONSE_ID).isBlank()
                    || state.text(CognitiveCycleState.APPROVER_ID).isBlank()) {
                throw new IllegalStateException("Approval interruption resumed without approval, response and approver references");
            }
            var result = Objects.requireNonNull(operations.resolveApproval(state.context()), "approval resolution");
            if (!state.text(CognitiveCycleState.APPROVAL_ID).equals(result.approvalId())) {
                throw new SecurityException("approval response resolved to a different approval record");
            }
            if (!state.text(CognitiveCycleState.SELECTED_CANDIDATE_ID).equals(result.candidateId())
                    || !state.text(CognitiveCycleState.CANDIDATE_SET_ID).equals(result.candidateSetId())
                    || !state.text(CognitiveCycleState.INTENTION_ID).equals(result.intentionId())
                    || state.number(CognitiveCycleState.INTENTION_VERSION) != result.intentionVersion()) {
                throw new SecurityException("approval record is not bound to the exact selected candidate and intention version");
            }
            if (!result.approverId().isBlank()
                    && !state.text(CognitiveCycleState.APPROVER_ID).equals(result.approverId())) {
                throw new SecurityException("approval response approver identity does not match the host-supplied identity");
            }
            return update("approval-resolution:" + result.disposition().name(),
                    CognitiveCycleState.APPROVAL_DECISION, result.disposition().name(),
                    CognitiveCycleState.APPROVER_ID, result.approverId(),
                    CognitiveCycleState.APPROVAL_EVIDENCE_REFERENCE, result.evidenceReference(),
                    CognitiveCycleState.APPROVAL_RESPONSE_ID, "",
                    CognitiveCycleState.DECISION_STATUS, result.disposition().name(),
                    CognitiveCycleState.LAST_REASON, result.reason());
        };

        NodeAction<CognitiveCycleState> revalidate = state -> {
            var result = Objects.requireNonNull(operations.revalidatePlan(state.context()), "plan revalidation");
            return update("plan-revalidation:" + result.disposition().name(),
                    CognitiveCycleState.DECISION_STATUS, result.disposition().name(),
                    CognitiveCycleState.LAST_REASON, result.reason());
        };

        NodeAction<CognitiveCycleState> execute = state -> {
            var result = Objects.requireNonNull(operations.executeAuthorizedCandidate(state.context()), "execution result");
            return update("action-execution:" + result.disposition().name(),
                    CognitiveCycleState.EXECUTION_OPERATION_ID, result.operationId(),
                    CognitiveCycleState.EXECUTION_RESULT_REFERENCE, result.resultReference(),
                    CognitiveCycleState.DECISION_STATUS, result.disposition().name(),
                    CognitiveCycleState.LAST_REASON, result.reason());
        };

        NodeAction<CognitiveCycleState> observe = state -> {
            var result = Objects.requireNonNull(operations.observeAndReconcile(state.context()), "external observation");
            var memoryUpdate = Objects.requireNonNull(
                    operations.updateWorkingMemoryFromObservation(state.context(), result), "working-memory observation update");
            String nextStatus = result.disposition().name();
            String nextReason = result.reason();
            if (memoryUpdate.disposition() == WorkingMemoryUpdateDisposition.CONFLICT) {
                nextStatus = "WORKING_MEMORY_CONFLICT";
                nextReason = "Working-memory version conflict after persisted observation: " + memoryUpdate.reason();
            } else if (memoryUpdate.disposition() == WorkingMemoryUpdateDisposition.FAILED) {
                nextStatus = "WORKING_MEMORY_UPDATE_FAILED";
                nextReason = memoryUpdate.reason();
            }
            var updates = new HashMap<String, Object>();
            updates.put(CognitiveCycleState.EVENTS, "post-action-observation:" + result.disposition().name()
                    + ";working-memory:" + memoryUpdate.disposition().name());
            updates.put(CognitiveCycleState.OBSERVATION_ID, result.observationId());
            updates.put(CognitiveCycleState.OBSERVATION_DISPOSITION, result.disposition().name());
            updates.put(CognitiveCycleState.OBSERVATION_MEMORY_RETRY_COUNT, 0);
            updates.put(CognitiveCycleState.EVIDENCE_SET_ID, result.evidenceSetId().isBlank()
                    ? state.text(CognitiveCycleState.EVIDENCE_SET_ID) : result.evidenceSetId());
            updates.put(CognitiveCycleState.DECISION_STATUS, nextStatus);
            updates.put(CognitiveCycleState.LAST_REASON, nextReason);
            if (memoryUpdate.disposition() == WorkingMemoryUpdateDisposition.UPDATED) {
                updates.put(CognitiveCycleState.WORKING_MEMORY_ID, memoryUpdate.workingMemoryId());
                updates.put(CognitiveCycleState.WORKING_MEMORY_VERSION, memoryUpdate.workingMemoryVersion());
            }
            return Map.copyOf(updates);
        };

        NodeAction<CognitiveCycleState> reloadMemoryForObservation = state -> {
            if (state.text(CognitiveCycleState.OBSERVATION_ID).isBlank()) {
                throw new IllegalStateException("Cannot retry a working-memory write without the persisted observation reference");
            }
            var loaded = Objects.requireNonNull(operations.loadContext(state.context()), "reloaded working context");
            return update("working-memory-reloaded-for-observation:" + loaded.workingMemoryVersion(),
                    CognitiveCycleState.WORKING_MEMORY_ID, loaded.workingMemoryId(),
                    CognitiveCycleState.WORKING_MEMORY_VERSION, loaded.workingMemoryVersion(),
                    CognitiveCycleState.INTENTION_ID, loaded.intentionId(),
                    CognitiveCycleState.INTENTION_VERSION, loaded.intentionVersion(),
                    CognitiveCycleState.DECISION_STATUS, "WORKING_MEMORY_RELOADED");
        };

        NodeAction<CognitiveCycleState> retryMemoryUpdate = state -> {
            var result = Objects.requireNonNull(
                    operations.updateWorkingMemoryFromStoredObservation(state.context()), "working-memory retry result");
            int attempts = state.integer(CognitiveCycleState.OBSERVATION_MEMORY_RETRY_COUNT) + 1;
            String nextStatus;
            if (result.disposition() == WorkingMemoryUpdateDisposition.UPDATED) {
                nextStatus = state.text(CognitiveCycleState.OBSERVATION_DISPOSITION);
            } else if (result.disposition() == WorkingMemoryUpdateDisposition.CONFLICT
                    && attempts < MAX_OBSERVATION_MEMORY_RETRIES) {
                nextStatus = "WORKING_MEMORY_CONFLICT";
            } else {
                nextStatus = "WORKING_MEMORY_UPDATE_FAILED";
            }
            var updates = new HashMap<String, Object>();
            updates.put(CognitiveCycleState.EVENTS, "working-memory-observation-retry:" + result.disposition().name()
                    + ":attempt=" + attempts);
            updates.put(CognitiveCycleState.OBSERVATION_MEMORY_RETRY_COUNT, attempts);
            updates.put(CognitiveCycleState.DECISION_STATUS, nextStatus);
            updates.put(CognitiveCycleState.LAST_REASON, result.reason());
            if (result.disposition() == WorkingMemoryUpdateDisposition.UPDATED) {
                updates.put(CognitiveCycleState.WORKING_MEMORY_ID, result.workingMemoryId());
                updates.put(CognitiveCycleState.WORKING_MEMORY_VERSION, result.workingMemoryVersion());
            }
            return Map.copyOf(updates);
        };

        NodeAction<CognitiveCycleState> learn = state -> {
            var result = Objects.requireNonNull(operations.recordOutcomeEpisode(state.context()), "episode result");
            return update("outcome-episode-recorded-unverified:" + result.episodeId(),
                    CognitiveCycleState.DECISION_STATUS, "OUTCOME_RECORDED_UNVERIFIED",
                    CognitiveCycleState.LAST_REASON, "episode=" + result.episodeId());
        };

        NodeAction<CognitiveCycleState> complete = state -> {
            String terminal = "TERMINATE".equals(state.text(CognitiveCycleState.DECISION_STATUS))
                    ? "TERMINATED_BY_INTENTION" : "COMPLETED";
            return update("cognitive-cycle:" + terminal,
                    CognitiveCycleState.DECISION_STATUS, terminal);
        };

        NodeAction<CognitiveCycleState> escalate = state -> {
            String reason = state.text(CognitiveCycleState.LAST_REASON).isBlank()
                    ? "Cognitive cycle stopped without an executable outcome" : state.text(CognitiveCycleState.LAST_REASON);
            operations.recordEscalation(state.context(), reason);
            return update("cognitive-cycle:ESCALATED", CognitiveCycleState.DECISION_STATUS, "ESCALATED");
        };

        EdgeAction<CognitiveCycleState> interpretationRoute = state -> switch (state.text(CognitiveCycleState.DECISION_STATUS)) {
            case "READY" -> "load-context";
            case "NEEDS_CLARIFICATION" -> state.integer(CognitiveCycleState.CLARIFICATION_ROUND) < MAX_CLARIFICATION_ROUNDS
                    ? "await-clarification" : "escalate";
            default -> "escalate";
        };
        EdgeAction<CognitiveCycleState> evidenceRoute = state -> switch (state.text(CognitiveCycleState.DECISION_STATUS)) {
            case "READY" -> "workspace";
            case "NEEDS_EXTERNAL_EVIDENCE" -> state.integer(CognitiveCycleState.EVIDENCE_REQUEST_COUNT) < MAX_EVIDENCE_REQUESTS
                    ? "await-evidence" : "escalate";
            case "IMPASSE" -> "b2-resolution";
            default -> "escalate";
        };
        EdgeAction<CognitiveCycleState> acceptedEvidenceRoute = state -> switch (state.text(CognitiveCycleState.DECISION_STATUS)) {
            case "ACCEPTED" -> "load-context";
            case "IMPASSE" -> "b2-resolution";
            default -> "escalate";
        };
        EdgeAction<CognitiveCycleState> workspaceRoute = state -> switch (state.text(CognitiveCycleState.DECISION_STATUS)) {
            case "ADMITTED" -> "propose";
            case "INCOMPLETE", "INSUFFICIENT_CAPACITY" -> "b2-resolution";
            default -> "escalate";
        };
        EdgeAction<CognitiveCycleState> evaluationRoute = state -> switch (state.text(CognitiveCycleState.DECISION_STATUS)) {
            case "SELECTED" -> "intention";
            case "NEEDS_MORE_SEARCH" -> state.integer(CognitiveCycleState.PLANNING_ROUND) < MAX_PLANNING_ROUNDS
                    ? "propose" : "escalate";
            case "NEEDS_EVIDENCE" -> state.integer(CognitiveCycleState.EVIDENCE_REQUEST_COUNT) < MAX_EVIDENCE_REQUESTS
                    ? "await-evidence" : "escalate";
            case "IMPASSE" -> "b2-resolution";
            default -> "escalate";
        };
        EdgeAction<CognitiveCycleState> b2Route = state -> "RESOLVED".equals(state.text(CognitiveCycleState.DECISION_STATUS))
                ? "load-context" : "escalate";
        EdgeAction<CognitiveCycleState> intentionRoute = state -> switch (state.text(CognitiveCycleState.DECISION_STATUS)) {
            case "CONTINUE" -> "meta-control";
            case "RECONSIDER", "CHANGE_METHOD" -> state.integer(CognitiveCycleState.RECONSIDERATION_COUNT) <= MAX_RECONSIDERATIONS
                    ? "load-context" : "escalate";
            case "SUSPEND" -> "await-human-control";
            case "TERMINATE" -> "complete";
            default -> "escalate";
        };
        EdgeAction<CognitiveCycleState> metaRoute = state -> switch (state.text(CognitiveCycleState.DECISION_STATUS)) {
            case "CONTINUE" -> "authorize";
            case "REQUEST_EVIDENCE" -> state.integer(CognitiveCycleState.EVIDENCE_REQUEST_COUNT) < MAX_EVIDENCE_REQUESTS
                    ? "await-evidence" : "escalate";
            case "REDUCE_COMPUTATION" -> state.integer(CognitiveCycleState.META_CONTROL_ROUND) < MAX_RECONSIDERATIONS
                    ? "propose" : "escalate";
            case "INTERRUPT_FOR_HUMAN" -> "await-human-control";
            default -> "escalate";
        };
        EdgeAction<CognitiveCycleState> humanControlRoute = state -> switch (state.text(CognitiveCycleState.DECISION_STATUS)) {
            case "CONTINUE" -> "authorize";
            case "PROVIDE_EVIDENCE" -> state.integer(CognitiveCycleState.EVIDENCE_REQUEST_COUNT) < MAX_EVIDENCE_REQUESTS
                    ? "await-evidence" : "escalate";
            case "REDUCE_COMPUTATION" -> state.integer(CognitiveCycleState.META_CONTROL_ROUND) < MAX_RECONSIDERATIONS
                    ? "propose" : "escalate";
            default -> "escalate";
        };
        EdgeAction<CognitiveCycleState> authorizationRoute = state -> switch (state.text(CognitiveCycleState.DECISION_STATUS)) {
            case "NO_APPROVAL_REQUIRED" -> "revalidate";
            case "REQUIRES_APPROVAL" -> "await-approval";
            default -> "escalate";
        };
        EdgeAction<CognitiveCycleState> approvalRoute = state -> "APPROVED".equals(state.text(CognitiveCycleState.DECISION_STATUS))
                ? "revalidate" : "escalate";
        EdgeAction<CognitiveCycleState> revalidationRoute = state -> switch (state.text(CognitiveCycleState.DECISION_STATUS)) {
            case "VALID" -> "execute";
            case "STALE" -> state.integer(CognitiveCycleState.PLANNING_ROUND) < MAX_RECONSIDERATIONS
                    ? "load-context" : "escalate";
            case "NEEDS_EVIDENCE" -> state.integer(CognitiveCycleState.EVIDENCE_REQUEST_COUNT) < MAX_EVIDENCE_REQUESTS
                    ? "await-evidence" : "escalate";
            default -> "escalate";
        };
        EdgeAction<CognitiveCycleState> executionRoute = state -> switch (state.text(CognitiveCycleState.DECISION_STATUS)) {
            case "SUCCEEDED", "UNKNOWN_OUTCOME" -> "observe";
            default -> "escalate";
        };
        EdgeAction<CognitiveCycleState> observationRoute = state -> switch (state.text(CognitiveCycleState.DECISION_STATUS)) {
            case "OBJECTIVE_SATISFIED" -> "learn";
            case "RECONSIDER" -> "load-context";
            case "NEEDS_EVIDENCE" -> state.integer(CognitiveCycleState.EVIDENCE_REQUEST_COUNT) < MAX_EVIDENCE_REQUESTS
                    ? "await-evidence" : "escalate";
            case "WORKING_MEMORY_CONFLICT" -> "reload-memory";
            default -> "escalate";
        };
        EdgeAction<CognitiveCycleState> observationMemoryRetryRoute = state -> switch (state.text(CognitiveCycleState.DECISION_STATUS)) {
            case "OBJECTIVE_SATISFIED" -> "learn";
            case "RECONSIDER" -> "load-context";
            case "NEEDS_EVIDENCE" -> state.integer(CognitiveCycleState.EVIDENCE_REQUEST_COUNT) < MAX_EVIDENCE_REQUESTS
                    ? "await-evidence" : "escalate";
            case "WORKING_MEMORY_CONFLICT" -> "reload-memory";
            default -> "escalate";
        };

        var builder = new StateGraph<>(CognitiveCycleState.SCHEMA, CognitiveCycleState::new)
                .addNode(NODE_INITIALIZE, node_async(initialize))
                .addNode(NODE_INTERPRET, node_async(interpret))
                .addNode(NODE_AWAIT_CLARIFICATION, node_async(acceptClarification))
                .addNode(NODE_LOAD_CONTEXT, node_async(loadContext))
                .addNode(NODE_RETRIEVE, node_async(retrieve))
                .addNode(NODE_AWAIT_EVIDENCE, node_async(acceptEvidence))
                .addNode(NODE_WORKSPACE, node_async(workspace))
                .addNode(NODE_PROPOSE, node_async(propose))
                .addNode(NODE_EVALUATE, node_async(evaluate))
                .addNode(NODE_B2, node_async(resolveB2))
                .addNode(NODE_INTENTION, node_async(governIntention))
                .addNode(NODE_META_CONTROL, node_async(metaControl))
                .addNode(NODE_AWAIT_HUMAN_CONTROL, node_async(resolveHumanControl))
                .addNode(NODE_AUTHORIZE, node_async(authorize))
                .addNode(NODE_AWAIT_APPROVAL, node_async(resolveApproval))
                .addNode(NODE_REVALIDATE, node_async(revalidate))
                .addNode(NODE_EXECUTE, node_async(execute))
                .addNode(NODE_OBSERVE, node_async(observe))
                .addNode(NODE_RELOAD_MEMORY_FOR_OBSERVATION, node_async(reloadMemoryForObservation))
                .addNode(NODE_RETRY_MEMORY_UPDATE, node_async(retryMemoryUpdate))
                .addNode(NODE_LEARN, node_async(learn))
                .addNode(NODE_COMPLETE, node_async(complete))
                .addNode(NODE_ESCALATE, node_async(escalate))
                .addEdge(START, NODE_INITIALIZE)
                .addEdge(NODE_INITIALIZE, NODE_INTERPRET)
                .addConditionalEdges(NODE_INTERPRET, edge_async(interpretationRoute), Map.of(
                        "load-context", NODE_LOAD_CONTEXT,
                        "await-clarification", NODE_AWAIT_CLARIFICATION,
                        "escalate", NODE_ESCALATE))
                .addEdge(NODE_AWAIT_CLARIFICATION, NODE_INTERPRET)
                .addEdge(NODE_LOAD_CONTEXT, NODE_RETRIEVE)
                .addConditionalEdges(NODE_RETRIEVE, edge_async(evidenceRoute), Map.of(
                        "workspace", NODE_WORKSPACE,
                        "await-evidence", NODE_AWAIT_EVIDENCE,
                        "b2-resolution", NODE_B2,
                        "escalate", NODE_ESCALATE))
                .addConditionalEdges(NODE_AWAIT_EVIDENCE, edge_async(acceptedEvidenceRoute), Map.of(
                        "load-context", NODE_LOAD_CONTEXT,
                        "b2-resolution", NODE_B2,
                        "escalate", NODE_ESCALATE))
                .addConditionalEdges(NODE_WORKSPACE, edge_async(workspaceRoute), Map.of(
                        "propose", NODE_PROPOSE,
                        "b2-resolution", NODE_B2,
                        "escalate", NODE_ESCALATE))
                .addEdge(NODE_PROPOSE, NODE_EVALUATE)
                .addConditionalEdges(NODE_EVALUATE, edge_async(evaluationRoute), Map.of(
                        "intention", NODE_INTENTION,
                        "propose", NODE_PROPOSE,
                        "await-evidence", NODE_AWAIT_EVIDENCE,
                        "b2-resolution", NODE_B2,
                        "escalate", NODE_ESCALATE))
                .addConditionalEdges(NODE_B2, edge_async(b2Route), Map.of(
                        "load-context", NODE_LOAD_CONTEXT,
                        "escalate", NODE_ESCALATE))
                .addConditionalEdges(NODE_INTENTION, edge_async(intentionRoute), Map.of(
                        "meta-control", NODE_META_CONTROL,
                        "load-context", NODE_LOAD_CONTEXT,
                        "await-human-control", NODE_AWAIT_HUMAN_CONTROL,
                        "complete", NODE_COMPLETE,
                        "escalate", NODE_ESCALATE))
                .addConditionalEdges(NODE_META_CONTROL, edge_async(metaRoute), Map.of(
                        "authorize", NODE_AUTHORIZE,
                        "await-evidence", NODE_AWAIT_EVIDENCE,
                        "propose", NODE_PROPOSE,
                        "await-human-control", NODE_AWAIT_HUMAN_CONTROL,
                        "escalate", NODE_ESCALATE))
                .addConditionalEdges(NODE_AWAIT_HUMAN_CONTROL, edge_async(humanControlRoute), Map.of(
                        "authorize", NODE_AUTHORIZE,
                        "await-evidence", NODE_AWAIT_EVIDENCE,
                        "propose", NODE_PROPOSE,
                        "escalate", NODE_ESCALATE))
                .addConditionalEdges(NODE_AUTHORIZE, edge_async(authorizationRoute), Map.of(
                        "revalidate", NODE_REVALIDATE,
                        "await-approval", NODE_AWAIT_APPROVAL,
                        "escalate", NODE_ESCALATE))
                .addConditionalEdges(NODE_AWAIT_APPROVAL, edge_async(approvalRoute), Map.of(
                        "revalidate", NODE_REVALIDATE,
                        "escalate", NODE_ESCALATE))
                .addConditionalEdges(NODE_REVALIDATE, edge_async(revalidationRoute), Map.of(
                        "execute", NODE_EXECUTE,
                        "load-context", NODE_LOAD_CONTEXT,
                        "await-evidence", NODE_AWAIT_EVIDENCE,
                        "escalate", NODE_ESCALATE))
                .addConditionalEdges(NODE_EXECUTE, edge_async(executionRoute), Map.of(
                        "observe", NODE_OBSERVE,
                        "escalate", NODE_ESCALATE))
                .addConditionalEdges(NODE_OBSERVE, edge_async(observationRoute), Map.of(
                        "learn", NODE_LEARN,
                        "load-context", NODE_LOAD_CONTEXT,
                        "await-evidence", NODE_AWAIT_EVIDENCE,
                        "reload-memory", NODE_RELOAD_MEMORY_FOR_OBSERVATION,
                        "escalate", NODE_ESCALATE))
                .addEdge(NODE_RELOAD_MEMORY_FOR_OBSERVATION, NODE_RETRY_MEMORY_UPDATE)
                .addConditionalEdges(NODE_RETRY_MEMORY_UPDATE, edge_async(observationMemoryRetryRoute), Map.of(
                        "learn", NODE_LEARN,
                        "load-context", NODE_LOAD_CONTEXT,
                        "await-evidence", NODE_AWAIT_EVIDENCE,
                        "reload-memory", NODE_RELOAD_MEMORY_FOR_OBSERVATION,
                        "escalate", NODE_ESCALATE))
                .addEdge(NODE_LEARN, NODE_COMPLETE)
                .addEdge(NODE_COMPLETE, END)
                .addEdge(NODE_ESCALATE, END);

        return builder.compile(CompileConfig.builder()
                .checkpointSaver(checkpointSaver)
                .interruptBefore(NODE_AWAIT_CLARIFICATION, NODE_AWAIT_EVIDENCE,
                        NODE_AWAIT_HUMAN_CONTROL, NODE_AWAIT_APPROVAL)
                .recursionLimit(256)
                .graphId("supply-chain-cognitive-cycle")
                .build());
    }

    private static Map<String, Object> update(String event, Object... pairs) {
        if (pairs.length % 2 != 0) throw new IllegalArgumentException("updates must be key/value pairs");
        var result = new HashMap<String, Object>();
        result.put(CognitiveCycleState.EVENTS, event);
        for (int index = 0; index < pairs.length; index += 2) {
            result.put((String) pairs[index], pairs[index + 1]);
        }
        return Map.copyOf(result);
    }

    private static String require(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return normalized;
    }
}
