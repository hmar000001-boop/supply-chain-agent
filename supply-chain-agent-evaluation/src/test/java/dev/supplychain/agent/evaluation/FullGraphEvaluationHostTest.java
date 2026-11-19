package dev.supplychain.agent.evaluation;

import dev.supplychain.agent.evaluation.host.FullGraphEvaluationHost;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class FullGraphEvaluationHostTest {
    @Test
    void compilesTheActualFullGraphThroughTheApplicationCompositionEntryPoint() throws Exception {
        var graph = new FullGraphEvaluationHost().compileFailClosedProbe();
        assertNotNull(graph);
    }
}
