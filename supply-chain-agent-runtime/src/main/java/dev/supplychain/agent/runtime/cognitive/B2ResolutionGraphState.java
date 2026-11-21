package dev.supplychain.agent.runtime.cognitive;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.Map;

/**
 * Bounded B2 child state. It carries immutable parent context references needed to validate the
 * return, but never copies the parent's objective, memory contents, candidate objects or capabilities.
 */
public final class B2ResolutionGraphState extends AgentState {
    public static final String REQUEST_ID = "requestId";
    public static final String EXECUTION_ID = "executionId";
    public static final String MEMORY_SCOPE_ID = "memoryScopeId";
    public static final String WORKING_MEMORY_ID = "workingMemoryId";
    public static final String PARENT_WORKING_MEMORY_VERSION = "parentWorkingMemoryVersion";
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
    public static final String SELECTED_CANDIDATE_ID = "selectedCandidateId";
    public static final String IMPASSE_CASE_ID = "impasseCaseId";
    public static final String IMPASSE_REASON = "impasseReason";
    public static final String UNCERTAINTY_ASSESSMENT_ID = "uncertaintyAssessmentId";
    public static final String UNCERTAINTY_ASSESSMENT_VERSION = "uncertaintyAssessmentVersion";
    public static final String INVESTIGATION_REFERENCE = "investigationReference";
    public static final String DISPOSITION = "disposition";
    public static final String REASON = "reason";
    public static final String RECOVERY_PROCEDURE_DRAFT_ID = "recoveryProcedureDraftId";

    public static final Map<String, Channel<?>> SCHEMA = Map.ofEntries(
            Map.entry(REQUEST_ID, Channels.base(() -> "")),
            Map.entry(EXECUTION_ID, Channels.base(() -> "")),
            Map.entry(MEMORY_SCOPE_ID, Channels.base(() -> "")),
            Map.entry(WORKING_MEMORY_ID, Channels.base(() -> "")),
            Map.entry(PARENT_WORKING_MEMORY_VERSION, Channels.base(() -> 0L)),
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
            Map.entry(SELECTED_CANDIDATE_ID, Channels.base(() -> "")),
            Map.entry(IMPASSE_CASE_ID, Channels.base(() -> "")),
            Map.entry(IMPASSE_REASON, Channels.base(() -> "")),
            Map.entry(UNCERTAINTY_ASSESSMENT_ID, Channels.base(() -> "")),
            Map.entry(UNCERTAINTY_ASSESSMENT_VERSION, Channels.base(() -> 0L)),
            Map.entry(INVESTIGATION_REFERENCE, Channels.base(() -> "")),
            Map.entry(DISPOSITION, Channels.base(() -> "UNRESOLVED")),
            Map.entry(REASON, Channels.base(() -> "")),
            Map.entry(RECOVERY_PROCEDURE_DRAFT_ID, Channels.base(() -> ""))
    );

    public B2ResolutionGraphState(Map<String, Object> initialData) { super(initialData); }
    public String text(String key) { return this.<String>value(key).orElse(""); }
    public int integer(String key) { return this.<Integer>value(key).orElse(0); }
    public long parentWorkingMemoryVersion() { return this.<Long>value(PARENT_WORKING_MEMORY_VERSION).orElse(0L); }
    public long intentionVersion() { return this.<Long>value(INTENTION_VERSION).orElse(0L); }
    public long uncertaintyAssessmentVersion() {
        return this.<Long>value(UNCERTAINTY_ASSESSMENT_VERSION).orElse(0L);
    }
}
