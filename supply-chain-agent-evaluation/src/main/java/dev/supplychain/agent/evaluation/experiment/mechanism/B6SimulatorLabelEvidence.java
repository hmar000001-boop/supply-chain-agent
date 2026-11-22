package dev.supplychain.agent.evaluation.experiment.mechanism;

import dev.supplychain.agent.evaluation.experiment.scenario.ExperimentSplit;
import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.mechanisms.b6.UncertaintyCalibrationObservation;
import java.util.Objects;

/** One simulator-derived uncertainty label bound to one immutable experiment unit and run. */
public record B6SimulatorLabelEvidence(
        ExperimentSplit split,
        String experimentUnitFingerprint,
        String simulatorRunFingerprint,
        String simulatorStateFingerprint,
        UncertaintyCalibrationObservation observation) {
    public static final String EVIDENCE_TYPE = "m8-simulator-uncertainty-label-v1";
    public B6SimulatorLabelEvidence {
        split = Objects.requireNonNull(split, "split");
        experimentUnitFingerprint = ExperimentEvidenceFingerprint.requireSha(experimentUnitFingerprint, "experimentUnitFingerprint");
        simulatorRunFingerprint = ExperimentEvidenceFingerprint.requireSha(simulatorRunFingerprint, "simulatorRunFingerprint");
        simulatorStateFingerprint = ExperimentEvidenceFingerprint.requireSha(simulatorStateFingerprint, "simulatorStateFingerprint");
        observation = Objects.requireNonNull(observation, "observation");
        String expectedId = evidenceId(experimentUnitFingerprint, simulatorRunFingerprint,
                simulatorStateFingerprint, observation);
        if (!observation.labelEvidence().equals(new EvidenceReference(EVIDENCE_TYPE, expectedId)))
            throw new SecurityException("B6 label evidence reference is not bound to the exact unit, simulator run, state and label");
    }

    public static String evidenceId(String unitFingerprint, String runFingerprint,
            String stateFingerprint, UncertaintyCalibrationObservation observation) {
        Objects.requireNonNull(observation, "observation");
        String canonical = ExperimentEvidenceFingerprint.requireSha(unitFingerprint, "unitFingerprint") + "|"
                + ExperimentEvidenceFingerprint.requireSha(runFingerprint, "runFingerprint") + "|"
                + ExperimentEvidenceFingerprint.requireSha(stateFingerprint, "stateFingerprint") + "|"
                + observation.sampleId() + "|" + observation.dimension() + "|"
                + Double.toHexString(observation.predictedUncertainty()) + "|" + observation.realizedError()
                + "|" + observation.observedAt();
        return ExperimentEvidenceFingerprint.sha256(canonical);
    }
}
