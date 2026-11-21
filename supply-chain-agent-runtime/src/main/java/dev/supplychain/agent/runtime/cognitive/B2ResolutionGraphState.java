package dev.supplychain.agent.runtime.cognitive;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.Map;

/** Small bounded child state; it cannot carry or replace the parent's objective or capabilities. */
public final class B2ResolutionGraphState extends AgentState {
    public static final String REQUEST_ID = "requestId";
    public static final String EXECUTION_ID = "executionId";
    public static final String MEMORY_SCOPE_ID = "memoryScopeId";
    public static final String PARENT_WORKING_MEMORY_VERSION = "parentWorkingMemoryVersion";
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
            Map.entry(PARENT_WORKING_MEMORY_VERSION, Channels.base(() -> 0L)),
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
    public long parentWorkingMemoryVersion() { return this.<Long>value(PARENT_WORKING_MEMORY_VERSION).orElse(0L); }
    public long uncertaintyAssessmentVersion() {
        return this.<Long>value(UNCERTAINTY_ASSESSMENT_VERSION).orElse(0L);
    }
}
