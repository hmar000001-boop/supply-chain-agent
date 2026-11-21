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
 * Bounded B2 child-resolution graph. The child can investigate and return evidence-backed findings,
 * but the parent objective and capability set remain protected in the B2 case record.
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
        var input = Map.<String, Object>of(
                B2ResolutionGraphState.REQUEST_ID, context.requestId(),
                B2ResolutionGraphState.EXECUTION_ID, context.executionId(),
                B2ResolutionGraphState.MEMORY_SCOPE_ID, context.memoryScopeId(),
                B2ResolutionGraphState.PARENT_WORKING_MEMORY_VERSION, context.workingMemoryVersion(),
                B2ResolutionGraphState.UNCERTAINTY_ASSESSMENT_ID,
                        context.requireUncertaintyAssessmentReference().assessmentId(),
                B2ResolutionGraphState.UNCERTAINTY_ASSESSMENT_VERSION,
                        context.requireUncertaintyAssessmentReference().version(),
                B2ResolutionGraphState.IMPASSE_CASE_ID, context.impasseCaseId(),
                B2ResolutionGraphState.IMPASSE_REASON, context.lastReason());
        var config = org.bsc.langgraph4j.RunnableConfig.builder()
                .threadId("b2-child-" + context.executionId() + "-" + java.util.UUID.randomUUID())
                .build();
        var result = graph.invoke(GraphInput.args(input), config)
                .orElseThrow(() -> new IllegalStateException("B2 resolution subgraph produced no terminal state"));
        if (!"RESOLVED".equals(result.text(B2ResolutionGraphState.DISPOSITION))) {
            return result;
        }
        return result;
    }

    private static CycleContext childContext(B2ResolutionGraphState state) {
        // Keep the impasse cause in CycleContext.lastReason. The initial child state has no
        // child-specific REASON yet, so fall back to the parent-provided IMPASSE_REASON. Once
        // open/investigate nodes run, their REASON becomes the latest child lifecycle reason.
        String childReason = state.text(B2ResolutionGraphState.REASON).isBlank()
                ? state.text(B2ResolutionGraphState.IMPASSE_REASON)
                : state.text(B2ResolutionGraphState.REASON);
        return new CycleContext(
                state.text(B2ResolutionGraphState.REQUEST_ID),
                state.text(B2ResolutionGraphState.EXECUTION_ID),
                state.text(B2ResolutionGraphState.MEMORY_SCOPE_ID),
                "", "", "", "", 0,
                "", state.parentWorkingMemoryVersion(), "", 0, "", "", "", 0,
                "", "", "", 0, "",
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
