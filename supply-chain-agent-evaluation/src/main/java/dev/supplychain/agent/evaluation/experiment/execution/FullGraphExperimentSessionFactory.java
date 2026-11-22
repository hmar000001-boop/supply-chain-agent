package dev.supplychain.agent.evaluation.experiment.execution;

import dev.supplychain.agent.evaluation.experiment.scenario.ExperimentScenarioUnit;

/** Builds a fresh application graph and fresh stateful adapters for one condition of one unit. */
@FunctionalInterface
public interface FullGraphExperimentSessionFactory {
    FullGraphExperimentSession create(ExperimentScenarioUnit unit, ExperimentMechanism mechanism,
                                      ExperimentArm arm) throws Exception;
}
