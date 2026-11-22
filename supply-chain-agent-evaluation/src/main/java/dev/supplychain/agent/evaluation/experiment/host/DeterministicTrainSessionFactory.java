package dev.supplychain.agent.evaluation.experiment.host;

import dev.supplychain.agent.evaluation.experiment.execution.FullGraphExperimentSessionFactory;

/**
 * Trusted composition boundary for one fresh, deterministic, side-effect-isolated LangGraph4j
 * session per TRAIN unit/mechanism/arm. Implementations must construct new mutable ports, runtime,
 * graph, checkpoint thread and consumers for every call.
 */
public interface DeterministicTrainSessionFactory extends FullGraphExperimentSessionFactory {
    DeterministicTrainExecutionProfile executionProfile();
}
