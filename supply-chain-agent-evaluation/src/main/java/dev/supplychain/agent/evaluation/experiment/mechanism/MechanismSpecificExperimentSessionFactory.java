package dev.supplychain.agent.evaluation.experiment.mechanism;

import dev.supplychain.agent.evaluation.experiment.execution.FullGraphExperimentSession;
import dev.supplychain.agent.evaluation.experiment.scenario.ExperimentScenarioUnit;

/**
 * Explicit composition boundary for mechanism-specific isolated graph sessions. Implementations
 * must apply the supplied plan to the actual graph ports/repositories, not merely echo its hash.
 */
@FunctionalInterface
public interface MechanismSpecificExperimentSessionFactory {
    FullGraphExperimentSession create(ExperimentScenarioUnit unit, MechanismArmPlan plan,
                                      MechanismTrainingArtifactBundle trainingArtifacts) throws Exception;
}
