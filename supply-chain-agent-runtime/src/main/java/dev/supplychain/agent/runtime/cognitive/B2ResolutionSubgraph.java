package dev.supplychain.agent.runtime.cognitive;

import dev.supplychain.agent.runtime.cognitive.CognitiveCycleOperations.CycleContext;
import dev.supplychain.agent.mechanisms.b6.UncertaintyAssessment;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.Map;
import java.util.Objects;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * Bounded B2 child-resolution graph. It receives only scalar parent-context references and
 * versioned identities; the authoritative objective, memory contents and capabilities remain in
 * their owning services and the B2 case record.
 */
public final class B2ResolutionSubgraph {
    private B2ResolutionSubgraph() { }

    public static CompiledGraph<B2ResolutionGraphState> compile(CognitiveCycleOperations operations)
            throws GraphStateException {
        Objects.requireNonNull(operations, "operations");

        NodeAction<B2ResolutionGraphState> open = state -> {
            CycleContext context = childContext(state);
            var opened = operations.openImpasse(context);
            return Map.of(
                    B2ResolutionGraphState.IMPASSE_CASE_ID, opened.caseId(),
                    B2ResolutionGraphState.REASON, opened.reason());
        };
        NodeAction<B2ResolutionGraphState> investigate = state -> {
            var context = childContext(state);
            var result = operations.investigateImpasse(context);
            return Map.of(
                    B2ResolutionGraphState.INVESTIGATION_REFERENCE, result.investigationReference(),
                    B2ResolutionGraphState.REASON, result.summary());
        };
        NodeAction<B2ResolutionGraphState> refreshUncertainty = state -> {
            CycleContext context = childContext(state);
            String triggerId = "post-b2-investigation:" + state.text(B2ResolutionGraphState.IMPASSE_CASE_ID)
                    + ":" + state.text(B2ResolutionGraphState.INVESTIGATION_REFERENCE);
            UncertaintyAssessment.Reference previous = context.requireUncertaintyAssessmentReference();
            UncertaintyAssessment.Reference next = Objects.requireNonNull(
                    operations.assessUncertainty(context, triggerId), "B2 uncertainty refresh reference");
            if (!next.assessmentId().equals(previous.assessmentId())
                    || next.version() != previous.version() + 1L
                    || !next.executionId().equals(context.executionId())
                    || !next.memoryScopeId().equals(context.memoryScopeId())) {
                throw new SecurityException("B2 uncertainty refresh must advance exactly one revision in the same execution and memory scope");
            }
            return Map.of(
                    B2ResolutionGraphState.UNCERTAINTY_ASSESSMENT_ID, next.assessmentId(),
                    B2ResolutionGraphState.UNCERTAINTY_ASSESSMENT_VERSION, next.version());
        };
        NodeAction<B2ResolutionGraphState> validateReturn = state -> {
            var result = operations.validateImpasseReturn(childContext(state));
            return Map.of(
                    B2ResolutionGraphState.DISPOSITION, result.disposition().name(),
                    B2ResolutionGraphState.REASON, result.reason(),
                    B2ResolutionGraphState.RECOVERY_PROCEDURE_DRAFT_ID, result.recoveryProcedureDraftId());
        };

        var graph = new StateGraph<>(B2ResolutionGraphState.SCHEMA, B2ResolutionGraphState::new)
                .addNode("open-child", node_async(open))
                .addNode("investigate-child", node_async(investigate))
                .addNode("refresh-uncertainty-after-investigation", node_async(refreshUncertainty))
                .addNode("validate-parent-return", node_async(validateReturn))
                .addEdge(START, "open-child")
                .addEdge("open-child", "investigate-child")
                .addEdge("investigate-child", "refresh-uncertainty-after-investigation")
                .addEdge("refresh-uncertainty-after-investigation", "validate-parent-return")
                .addEdge("validate-parent-return", END);
        return graph.compile(CompileConfig.builder().recursionLimit(12).build());
    }

    public static B2ResolutionGraphState invoke(CompiledGraph<B2ResolutionGraphState> graph,
                                                CycleContext context) throws Exception {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(context, "context");
        if (context.workingMemoryId().isBlank() || context.intentionId().isBlank()) {
            throw new IllegalStateException("B2 resolution requires the active parent working-memory and intention references");
        }
        var uncertainty = context.requireUncertaintyAssessmentReference();
        var input = Map.<String, Object>ofEntries(
                Map.entry(B2ResolutionGraphState.REQUEST_ID, context.requestId()),
                Map.entry(B2ResolutionGraphState.EXECUTION_ID, context.executionId()),
                Map.entry(B2ResolutionGraphState.MEMORY_SCOPE_ID, context.memoryScopeId()),
                Map.entry(B2ResolutionGraphState.WORKING_MEMORY_ID, context.workingMemoryId()),
                Map.entry(B2ResolutionGraphState.PARENT_WORKING_MEMORY_VERSION, context.workingMemoryVersion()),
                Map.entry(B2ResolutionGraphState.EVIDENCE_SET_ID, context.evidenceSetId()),
                Map.entry(B2ResolutionGraphState.EVIDENCE_REQUEST_COUNT, context.evidenceRequestCount()),
                Map.entry(B2ResolutionGraphState.EVIDENCE_RESPONSE_ID, context.evidenceResponseId()),
                Map.entry(B2ResolutionGraphState.WORKSPACE_SNAPSHOT_ID, context.workspaceSnapshotId()),
                Map.entry(B2ResolutionGraphState.INTENTION_ID, context.intentionId()),
                Map.entry(B2ResolutionGraphState.INTENTION_VERSION, context.intentionVersion()),
                Map.entry(B2ResolutionGraphState.PLANNER_ID, context.plannerId()),
                Map.entry(B2ResolutionGraphState.PLANNER_SESSION_ID, context.plannerSessionId()),
                Map.entry(B2ResolutionGraphState.CANDIDATE_SET_ID, context.candidateSetId()),
                Map.entry(B2ResolutionGraphState.PLANNING_ROUND, context.planningRound()),
                Map.entry(B2ResolutionGraphState.SELECTED_CANDIDATE_ID, context.selectedCandidateId()),
                Map.entry(B2ResolutionGraphState.IMPASSE_CASE_ID, context.impasseCaseId()),
                Map.entry(B2ResolutionGraphState.IMPASSE_REASON, context.lastReason()),
                Map.entry(B2ResolutionGraphState.UNCERTAINTY_ASSESSMENT_ID, uncertainty.assessmentId()),
                Map.entry(B2ResolutionGraphState.UNCERTAINTY_ASSESSMENT_VERSION, uncertainty.version()),
                Map.entry(B2ResolutionGraphState.INVESTIGATION_REFERENCE, context.impasseInvestigationReference())
        );
        var config = org.bsc.langgraph4j.RunnableConfig.builder()
                .threadId("b2-child-" + context.executionId() + "-" + java.util.UUID.randomUUID())
                .build();
        return graph.invoke(GraphInput.args(input), config)
                .orElseThrow(() -> new IllegalStateException("B2 resolution subgraph produced no terminal state"));
    }

    private static CycleContext childContext(B2ResolutionGraphState state) {
        // Preserve the authoritative parent references needed for case opening, execution
        // fencing and return validation. The child never receives an objective or capability set.
        String childReason = state.text(B2ResolutionGraphState.REASON).isBlank()
                ? state.text(B2ResolutionGraphState.IMPASSE_REASON)
                : state.text(B2ResolutionGraphState.REASON);
        return new CycleContext(
                state.text(B2ResolutionGraphState.REQUEST_ID),
                state.text(B2ResolutionGraphState.EXECUTION_ID),
                state.text(B2ResolutionGraphState.MEMORY_SCOPE_ID),
                "", "", "", "", 0,
                state.text(B2ResolutionGraphState.WORKING_MEMORY_ID),
                state.parentWorkingMemoryVersion(),
                state.text(B2ResolutionGraphState.EVIDENCE_SET_ID),
                state.integer(B2ResolutionGraphState.EVIDENCE_REQUEST_COUNT),
                state.text(B2ResolutionGraphState.EVIDENCE_RESPONSE_ID),
                state.text(B2ResolutionGraphState.WORKSPACE_SNAPSHOT_ID),
                state.text(B2ResolutionGraphState.INTENTION_ID),
                state.intentionVersion(),
                state.text(B2ResolutionGraphState.PLANNER_ID),
                state.text(B2ResolutionGraphState.PLANNER_SESSION_ID),
                state.text(B2ResolutionGraphState.CANDIDATE_SET_ID),
                state.integer(B2ResolutionGraphState.PLANNING_ROUND),
                state.text(B2ResolutionGraphState.SELECTED_CANDIDATE_ID),
                state.text(B2ResolutionGraphState.IMPASSE_CASE_ID),
                state.text(B2ResolutionGraphState.INVESTIGATION_REFERENCE),
                "", "", "", "", "", "", "",
                childReason,
                new UncertaintyAssessment.Reference(
                        state.text(B2ResolutionGraphState.UNCERTAINTY_ASSESSMENT_ID),
                        state.uncertaintyAssessmentVersion(),
                        state.text(B2ResolutionGraphState.EXECUTION_ID),
                        state.text(B2ResolutionGraphState.MEMORY_SCOPE_ID)));
    }
}
