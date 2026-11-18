package dev.supplychain.agent.app.cognitive;

import dev.supplychain.agent.runtime.cognitive.CognitiveCycleGraph;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleOperations;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleState;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleServiceOperations;
import dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleLifecycleServices;
import dev.supplychain.agent.runtime.cognitive.decision.CognitiveCycleDecisionOperations;
import dev.supplychain.agent.runtime.cognitive.decision.CognitiveCycleDecisionServices;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;

import java.util.Objects;

/**
 * Application composition entry point for the complete M6 cognitive-cycle service pipeline.
 *
 * <p>{@link #compileFull} composes concrete M2/B1/B3/M5 decision services with the M2/M3/B2/B4/B6
 * lifecycle services. The older {@link #compile} overload remains a migration seam for hosts that
 * have not yet supplied the explicit lifecycle and authority adapters; it is not the full M6 path.</p>
 */
public final class CognitiveCycleApplicationGraph {
    private CognitiveCycleApplicationGraph() { }

    @Deprecated(since = "0.1.0", forRemoval = false)
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

    /**
     * Full M6 composition: no catch-all CognitiveCycleOperations host delegate remains. Required
     * domain, authority and infrastructure adapters are supplied through the service constructors.
     */
    public static CompiledGraph<CognitiveCycleState> compileFull(
            CognitiveCycleDecisionServices decisionServices,
            CognitiveCycleLifecycleServices lifecycleServices,
            BaseCheckpointSaver checkpointSaver) throws GraphStateException {
        Objects.requireNonNull(decisionServices, "decisionServices");
        Objects.requireNonNull(lifecycleServices, "lifecycleServices");
        Objects.requireNonNull(checkpointSaver, "checkpointSaver");
        var composed = new CognitiveCycleServiceOperations(decisionServices, lifecycleServices);
        return CognitiveCycleGraph.compile(composed, checkpointSaver);
    }
}
