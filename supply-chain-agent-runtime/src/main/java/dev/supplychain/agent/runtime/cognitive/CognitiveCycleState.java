package dev.supplychain.agent.runtime.cognitive;

import dev.supplychain.agent.runtime.cognitive.CognitiveCycleOperations.CycleContext;
import dev.supplychain.agent.mechanisms.b6.UncertaintyAssessment;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Minimal, checkpoint-friendly LangGraph4j state. Large memory, planner, domain and approval
 * records live in their own repositories; this state stores only identifiers and routing data.
 */
public final class CognitiveCycleState extends AgentState {
    public static final String REQUEST_ID = "requestId";
    public static final String EXECUTION_ID = "executionId";
    public static final String MEMORY_SCOPE_ID = "memoryScopeId";
    public static final String REQUEST = "request";
    public static final String NORMALIZED_INTENT = "normalizedIntent";
    public static final String SUMMARY = "summary";
    public static final String CLARIFICATION_CONTEXT = "clarificationContext";
    public static final String CLARIFICATION_ROUND = "clarificationRound";
    public static final String WORKING_MEMORY_ID = "workingMemoryId";
    public static final String WORKING_MEMORY_VERSION = "workingMemoryVersion";
    public static final String EVIDENCE_SET_ID = "evidenceSetId";
    public static final String EVIDENCE_REQUEST_COUNT = "evidenceRequestCount";
    public static final String EVIDENCE_RESPONSE_ID = "evidenceResponseId";
    public static final String WORKSPACE_SNAPSHOT_ID = "workspaceSnapshotId";
    public static final String INTENTION_ID = "intentionId";
    public static final String INTENTION_VERSION = "intentionVersion";
    public static final String PLANNER_ID = "plannerId";
    public static final String PLANNER_SESSION_ID = "plannerSessionId";
    public static final String CANDIDATE_SET_ID = "candidateSetId";
    public static final String PLANNING_ROUND = "planningRound";
    public static final String RECONSIDERATION_COUNT = "reconsiderationCount";
    public static final String META_CONTROL_ROUND = "metaControlRound";
    public static final String SELECTED_CANDIDATE_ID = "selectedCandidateId";
    public static final String IMPASSE_CASE_ID = "impasseCaseId";
    public static final String IMPASSE_INVESTIGATION_REFERENCE = "impasseInvestigationReference";
    public static final String APPROVAL_ID = "approvalId";
    public static final String APPROVAL_RESPONSE_ID = "approvalResponseId";
    public static final String APPROVER_ID = "approverId";
    public static final String HUMAN_CONTROL_RESPONSE_ID = "humanControlResponseId";
    public static final String HUMAN_CONTROL_ACTOR_ID = "humanControlActorId";
    public static final String APPROVAL_DECISION = "approvalDecision";
    public static final String APPROVAL_EVIDENCE_REFERENCE = "approvalEvidenceReference";
    public static final String HUMAN_CONTROL_AUDIT_REFERENCE = "humanControlAuditReference";
    public static final String HUMAN_CONTROL_ACTOR_AUDIT_ID = "humanControlActorAuditId";
    public static final String STATE_CHANGING_CANDIDATE = "stateChangingCandidate";
    public static final String PLANNER_TERMINATION_REASON = "plannerTerminationReason";
    public static final String PLANNER_CANDIDATE_COUNT = "plannerCandidateCount";
    public static final String EVIDENCE_MAXIMUM_UNCERTAINTY = "evidenceMaximumUncertainty";
    public static final String UNCERTAINTY_ASSESSMENT_ID = "uncertaintyAssessmentId";
    public static final String UNCERTAINTY_ASSESSMENT_VERSION = "uncertaintyAssessmentVersion";
    public static final String META_CONTROL_MAXIMUM_UNCERTAINTY = "metaControlMaximumUncertainty";
    public static final String EXECUTION_OPERATION_ID = "executionOperationId";
    public static final String EXECUTION_RESULT_REFERENCE = "executionResultReference";
    public static final String OBSERVATION_ID = "observationId";
    public static final String OBSERVATION_DISPOSITION = "observationDisposition";
    public static final String OBSERVATION_MEMORY_RETRY_COUNT = "observationMemoryRetryCount";
    public static final String DECISION_STATUS = "decisionStatus";
    public static final String LAST_REASON = "lastReason";
    public static final String EVENTS = "events";

    public static final Map<String, Channel<?>> SCHEMA = Map.ofEntries(
            Map.entry(REQUEST_ID, Channels.base(() -> "")),
            Map.entry(EXECUTION_ID, Channels.base(() -> "")),
            Map.entry(MEMORY_SCOPE_ID, Channels.base(() -> "")),
            Map.entry(REQUEST, Channels.base(() -> "")),
            Map.entry(NORMALIZED_INTENT, Channels.base(() -> "")),
            Map.entry(SUMMARY, Channels.base(() -> "")),
            Map.entry(CLARIFICATION_CONTEXT, Channels.base(() -> "")),
            Map.entry(CLARIFICATION_ROUND, Channels.base(() -> 0)),
            Map.entry(WORKING_MEMORY_ID, Channels.base(() -> "")),
            Map.entry(WORKING_MEMORY_VERSION, Channels.base(() -> 0L)),
            Map.entry(EVIDENCE_SET_ID, Channels.base(() -> "")),
            Map.entry(EVIDENCE_REQUEST_COUNT, Channels.base(() -> 0)),
            Map.entry(EVIDENCE_RESPONSE_ID, Channels.base(() -> "")),
            Map.entry(WORKSPACE_SNAPSHOT_ID, Channels.base(() -> "")),
            Map.entry(INTENTION_ID, Channels.base(() -> "")),
            Map.entry(INTENTION_VERSION, Channels.base(() -> 0L)),
            Map.entry(PLANNER_ID, Channels.base(() -> "")),
            Map.entry(PLANNER_SESSION_ID, Channels.base(() -> "")),
            Map.entry(CANDIDATE_SET_ID, Channels.base(() -> "")),
            Map.entry(PLANNING_ROUND, Channels.base(() -> 0)),
            Map.entry(RECONSIDERATION_COUNT, Channels.base(() -> 0)),
            Map.entry(META_CONTROL_ROUND, Channels.base(() -> 0)),
            Map.entry(SELECTED_CANDIDATE_ID, Channels.base(() -> "")),
            Map.entry(IMPASSE_CASE_ID, Channels.base(() -> "")),
            Map.entry(IMPASSE_INVESTIGATION_REFERENCE, Channels.base(() -> "")),
            Map.entry(APPROVAL_ID, Channels.base(() -> "")),
            Map.entry(APPROVAL_RESPONSE_ID, Channels.base(() -> "")),
            Map.entry(APPROVER_ID, Channels.base(() -> "")),
            Map.entry(HUMAN_CONTROL_RESPONSE_ID, Channels.base(() -> "")),
            Map.entry(HUMAN_CONTROL_ACTOR_ID, Channels.base(() -> "")),
            Map.entry(APPROVAL_DECISION, Channels.base(() -> "")),
            Map.entry(APPROVAL_EVIDENCE_REFERENCE, Channels.base(() -> "")),
            Map.entry(HUMAN_CONTROL_AUDIT_REFERENCE, Channels.base(() -> "")),
            Map.entry(HUMAN_CONTROL_ACTOR_AUDIT_ID, Channels.base(() -> "")),
            Map.entry(STATE_CHANGING_CANDIDATE, Channels.base(() -> false)),
            Map.entry(PLANNER_TERMINATION_REASON, Channels.base(() -> "")),
            Map.entry(PLANNER_CANDIDATE_COUNT, Channels.base(() -> 0)),
            Map.entry(EVIDENCE_MAXIMUM_UNCERTAINTY, Channels.base(() -> 0.0d)),
            Map.entry(UNCERTAINTY_ASSESSMENT_ID, Channels.base(() -> "")),
            Map.entry(UNCERTAINTY_ASSESSMENT_VERSION, Channels.base(() -> 0L)),
            Map.entry(META_CONTROL_MAXIMUM_UNCERTAINTY, Channels.base(() -> 0.0d)),
            Map.entry(EXECUTION_OPERATION_ID, Channels.base(() -> "")),
            Map.entry(EXECUTION_RESULT_REFERENCE, Channels.base(() -> "")),
            Map.entry(OBSERVATION_ID, Channels.base(() -> "")),
            Map.entry(OBSERVATION_DISPOSITION, Channels.base(() -> "")),
            Map.entry(OBSERVATION_MEMORY_RETRY_COUNT, Channels.base(() -> 0)),
            Map.entry(DECISION_STATUS, Channels.base(() -> "RUNNING")),
            Map.entry(LAST_REASON, Channels.base(() -> "")),
            Map.entry(EVENTS, Channels.appender(ArrayList::new))
    );

    public CognitiveCycleState(Map<String, Object> initialData) { super(initialData); }

    public String text(String key) { return this.<String>value(key).orElse(""); }
    public int integer(String key) { return this.<Integer>value(key).orElse(0); }
    public long number(String key) { return this.<Long>value(key).orElse(0L); }
    public List<String> events() { return this.<List<String>>value(EVENTS).orElse(List.of()); }

    public UncertaintyAssessment.Reference uncertaintyAssessmentReference() {
        String id = text(UNCERTAINTY_ASSESSMENT_ID);
        long version = number(UNCERTAINTY_ASSESSMENT_VERSION);
        if (id.isBlank() && version == 0L) return null;
        if (id.isBlank() || version < 1L) {
            throw new IllegalStateException("checkpoint contains an incomplete uncertainty assessment reference");
        }
        return new UncertaintyAssessment.Reference(id, version, text(EXECUTION_ID), text(MEMORY_SCOPE_ID));
    }

    public CycleContext context() {
        return new CycleContext(text(REQUEST_ID), text(EXECUTION_ID), text(MEMORY_SCOPE_ID), text(REQUEST),
                text(NORMALIZED_INTENT), text(SUMMARY), text(CLARIFICATION_CONTEXT), integer(CLARIFICATION_ROUND),
                text(WORKING_MEMORY_ID), number(WORKING_MEMORY_VERSION), text(EVIDENCE_SET_ID),
                integer(EVIDENCE_REQUEST_COUNT), text(EVIDENCE_RESPONSE_ID), text(WORKSPACE_SNAPSHOT_ID),
                text(INTENTION_ID), number(INTENTION_VERSION), text(PLANNER_ID), text(PLANNER_SESSION_ID),
                text(CANDIDATE_SET_ID), integer(PLANNING_ROUND), text(SELECTED_CANDIDATE_ID),
                text(IMPASSE_CASE_ID), text(IMPASSE_INVESTIGATION_REFERENCE), text(APPROVAL_ID),
                text(APPROVAL_RESPONSE_ID), text(APPROVER_ID), text(HUMAN_CONTROL_RESPONSE_ID),
                text(HUMAN_CONTROL_ACTOR_ID), text(EXECUTION_OPERATION_ID), text(OBSERVATION_ID), text(LAST_REASON),
                uncertaintyAssessmentReference());
    }
}
