package dev.supplychain.agent.runtime.langgraph;

import dev.supplychain.agent.core.interpretation.InterpretationOutcome;
import dev.supplychain.agent.core.interpretation.InterpretationRequest;
import dev.supplychain.agent.core.interpretation.RequestInterpreter;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;

import java.util.Map;
import java.util.Objects;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * Phase 0 compatibility graph.
 *
 * LangGraph4j owns orchestration and checkpointed interruption. The caller supplies
 * the actual human clarification through updateState(), then resumes the graph.
 * The graph does not fabricate clarification text or treat a retry as user input.
 */
public final class SupplyChainIntakeGraph {

    private static final int MAX_CLARIFICATION_ROUNDS = 1;

    private SupplyChainIntakeGraph() {
    }

    public static CompiledGraph<SupplyChainGraphState> compile(
            RequestInterpreter interpreter,
            BaseCheckpointSaver checkpointSaver) throws GraphStateException {
        Objects.requireNonNull(interpreter, "interpreter");
        Objects.requireNonNull(checkpointSaver, "checkpointSaver");

        NodeAction<SupplyChainGraphState> interpret = state -> {
            var request = new InterpretationRequest(
                    state.request(), state.clarificationContext(), state.clarificationRound());
            var result = interpreter.interpret(request);
            return Map.of(
                    SupplyChainGraphState.OUTCOME, result.outcome().name(),
                    SupplyChainGraphState.NORMALIZED_INTENT, result.normalizedIntent(),
                    SupplyChainGraphState.SUMMARY, result.summary(),
                    SupplyChainGraphState.EVENTS, "request-interpreted:" + result.outcome().name()
            );
        };

        NodeAction<SupplyChainGraphState> recordClarification = state -> {
            if (state.clarificationContext().isBlank()) {
                throw new IllegalStateException(
                        "The graph resumed without a user clarification; refusing to fabricate input");
            }
            if (state.clarificationRound() < 1
                    || state.clarificationRound() > MAX_CLARIFICATION_ROUNDS) {
                throw new IllegalStateException("Clarification round is outside the configured bound");
            }
            return Map.of(
                    SupplyChainGraphState.EVENTS,
                    "user-clarification-recorded:" + state.clarificationRound()
            );
        };

        NodeAction<SupplyChainGraphState> finish = state -> Map.of(
                SupplyChainGraphState.EVENTS, "intake-finished"
        );

        EdgeAction<SupplyChainGraphState> route = state ->
                InterpretationOutcome.NEEDS_CLARIFICATION.name().equals(state.outcome())
                        && state.clarificationRound() < MAX_CLARIFICATION_ROUNDS
                        ? "await-clarification"
                        : "finish";

        var builder = new StateGraph<>(SupplyChainGraphState.SCHEMA, SupplyChainGraphState::new)
                .addNode("interpret", node_async(interpret))
                .addNode("await-clarification", node_async(recordClarification))
                .addNode("finish", node_async(finish))
                .addEdge(START, "interpret")
                .addConditionalEdges("interpret", edge_async(route), Map.of(
                        "await-clarification", "await-clarification",
                        "finish", "finish"
                ))
                .addEdge("await-clarification", "interpret")
                .addEdge("finish", END);

        return builder.compile(CompileConfig.builder()
                .checkpointSaver(checkpointSaver)
                .interruptBefore("await-clarification")
                .recursionLimit(16)
                .build());
    }
}
