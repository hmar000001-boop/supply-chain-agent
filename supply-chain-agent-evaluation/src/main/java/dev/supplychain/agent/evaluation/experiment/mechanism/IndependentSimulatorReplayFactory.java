package dev.supplychain.agent.evaluation.experiment.mechanism;

import dev.supplychain.agent.evaluation.experiment.execution.FullGraphExperimentSession;
import dev.supplychain.agent.planning.api.CandidateEvaluator;
import dev.supplychain.agent.planning.api.PlanningWorldModel;

/** Supplies fresh, isolated simulator and evaluator instances for independent proposal replay. */
@FunctionalInterface
public interface IndependentSimulatorReplayFactory {
    ReplayPorts create(FullGraphExperimentSession graphSession) throws Exception;

    record ReplayPorts(PlanningWorldModel worldModel, CandidateEvaluator candidateEvaluator) {
        public ReplayPorts {
            if (worldModel == null || candidateEvaluator == null)
                throw new IllegalArgumentException("independent replay requires world model and evaluator");
        }
    }
}
