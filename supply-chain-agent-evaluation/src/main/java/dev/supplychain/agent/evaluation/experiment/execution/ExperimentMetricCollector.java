package dev.supplychain.agent.evaluation.experiment.execution;

import dev.supplychain.agent.evaluation.experiment.scenario.ExperimentScenarioUnit;
import dev.supplychain.agent.evaluation.host.FullGraphRuntime;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleState;

import java.util.Map;

/** Captures declared, deterministic metrics from the actual graph state and its injected mechanisms. */
@FunctionalInterface
public interface ExperimentMetricCollector {
    Map<String, Double> collect(ExperimentScenarioUnit unit, ExperimentMechanism mechanism,
                                ExperimentArm arm, FullGraphRuntime runtime,
                                CognitiveCycleState terminalState);
}
