package dev.supplychain.agent.evaluation.experiment.execution;

import dev.supplychain.agent.runtime.cognitive.CognitiveCycleState;

/**
 * Read-only hook invoked after one graph reaches its first terminal/interruption boundary and
 * after sandbox counters have been checked, but before its disposable session is closed.
 */
@FunctionalInterface
public interface ExperimentRunObserver {
    void completed(FullGraphExperimentSession session, CognitiveCycleState terminalState,
                   FullGraphExperimentRunObservation observation) throws Exception;
}
