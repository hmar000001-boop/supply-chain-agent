package dev.supplychain.agent.evaluation.experiment.host;

import dev.supplychain.agent.evaluation.experiment.execution.ExperimentArm;
import dev.supplychain.agent.evaluation.experiment.execution.ExperimentMechanism;
import dev.supplychain.agent.evaluation.experiment.scenario.ExperimentScenarioUnit;

/**
 * Scenario-specific deterministic adapter composition. A new configuration must be returned for
 * every unit/mechanism/arm call; mutable ports, repositories, consumers and audit counters may not
 * be cached or shared between matched arms.
 */
public interface DeterministicTrainPortsFactory {
    DeterministicTrainExecutionProfile executionProfile();

    DeterministicTrainSessionConfiguration create(
            ExperimentScenarioUnit unit, ExperimentMechanism mechanism, ExperimentArm arm) throws Exception;
}
