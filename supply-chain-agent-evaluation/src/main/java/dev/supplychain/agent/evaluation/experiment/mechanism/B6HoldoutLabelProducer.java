package dev.supplychain.agent.evaluation.experiment.mechanism;

import dev.supplychain.agent.evaluation.experiment.scenario.ExperimentScenarioUnit;
import java.util.List;

/**
 * Domain-specific adapter that derives holdout error labels from independent simulator facts.
 * Implementations must not read treatment outputs when deciding whether an uncertainty prediction
 * was wrong. The harness independently re-verifies every emitted label afterwards.
 */
@FunctionalInterface
public interface B6HoldoutLabelProducer {
    List<B6SimulatorLabelEvidence> produce(ExperimentScenarioUnit unit,
            B6CalibrationRunEvidence baselinePrediction,
            SimulatorPlanOutcomeEvidence baselineSimulatorReplay) throws Exception;
}
