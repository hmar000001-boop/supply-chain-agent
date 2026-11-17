package dev.supplychain.agent.app.cognitive;

import dev.supplychain.agent.runtime.cognitive.CognitiveCycleGraph;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleOperations;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleState;
import dev.supplychain.agent.runtime.cognitive.decision.CognitiveCycleDecisionOperations;
import dev.supplychain.agent.runtime.cognitive.decision.CognitiveCycleDecisionServices;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;

import java.util.Objects;

/**
 * Application composition entry point for the M6 decision-side service pipeline.
 *
 * <p>The host supplies the still-host-owned lifecycle/authority/grounding operations. The four
 * decision stages are decorated with concrete M2/B1/B3/M5 services before the graph is compiled.
 * M6-B replaces the remaining host delegate with the full service-backed application adapter.</p>
 */
public final class CognitiveCycleApplicationGraph {
    private CognitiveCycleApplicationGraph() { }

    public static CompiledGraph<CognitiveCycleState> compile(
            CognitiveCycleOperations hostOperations,
            CognitiveCycleDecisionServices decisionServices,
            BaseCheckpointSaver checkpointSaver) throws GraphStateException {
        Objects.requireNonNull(hostOperations, "hostOperations");
        Objects.requireNonNull(decisionServices, "decisionServices");
        Objects.requireNonNull(checkpointSaver, "checkpointSaver");
        var composed = new CognitiveCycleDecisionOperations(hostOperations, decisionServices);
        return CognitiveCycleGraph.compile(composed, checkpointSaver);
    }
}
