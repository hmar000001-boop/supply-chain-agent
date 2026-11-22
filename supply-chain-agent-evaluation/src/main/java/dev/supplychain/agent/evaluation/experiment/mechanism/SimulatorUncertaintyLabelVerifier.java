package dev.supplychain.agent.evaluation.experiment.mechanism;

/** Independent deterministic simulator replay that confirms an uncertainty error label. */
@FunctionalInterface
public interface SimulatorUncertaintyLabelVerifier {
    boolean verify(B6SimulatorLabelEvidence evidence);
}
