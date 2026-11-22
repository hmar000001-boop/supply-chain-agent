package dev.supplychain.agent.evaluation.experiment.mechanism;

import dev.supplychain.agent.evaluation.experiment.execution.ExperimentArm;
import dev.supplychain.agent.evaluation.experiment.execution.ExperimentMechanism;

/** Typed, raw mechanism exposure evidence captured from one isolated full-graph run. */
public sealed interface MechanismSpecificExperimentEvidence permits
        B1MemoryRunEvidence, B2ProcedureRunEvidence, B3CoalitionRunEvidence,
        B4IntentionRunEvidence, B6CalibrationRunEvidence {
    ExperimentMechanism mechanism();
    ExperimentArm arm();
    String experimentUnitFingerprint();
    String trainingArtifactFingerprint();
    String evidenceFingerprint();
    String toJson();
}
