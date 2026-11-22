package dev.supplychain.agent.evaluation.experiment.mechanism;

import dev.supplychain.agent.evaluation.experiment.scenario.ExperimentSplit;
import dev.supplychain.agent.mechanisms.b6.UncertaintyCalibrationObservation;
import dev.supplychain.agent.mechanisms.b6.UncertaintyCalibrationProfile;
import dev.supplychain.agent.mechanisms.b6.UncertaintyDimension;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** TRAIN-only B6 profile with simulator-label provenance; it must remain TRAINED, never active. */
public record B6CalibrationTrainingArtifact(
        UncertaintyCalibrationProfile profile,
        List<B6SimulatorLabelEvidence> labels,
        Set<String> trainingUnitFingerprints,
        String labelVerifierId,
        String evidenceFingerprint) {
    public B6CalibrationTrainingArtifact {
        profile = Objects.requireNonNull(profile, "profile");
        if (profile.status() != UncertaintyCalibrationProfile.Status.TRAINED)
            throw new SecurityException("B6 training artifact must be TRAINED and not validated/activated");
        labels = List.copyOf(Objects.requireNonNull(labels, "labels"));
        trainingUnitFingerprints = Objects.requireNonNull(trainingUnitFingerprints, "trainingUnitFingerprints").stream()
                .map(v -> ExperimentEvidenceFingerprint.requireSha(v, "training unit fingerprint"))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (trainingUnitFingerprints.size() < 100) throw new IllegalArgumentException("B6 training artifact requires at least 100 distinct TRAIN units");
        labelVerifierId = ExperimentEvidenceFingerprint.required(labelVerifierId, "labelVerifierId");
        evidenceFingerprint = ExperimentEvidenceFingerprint.requireSha(evidenceFingerprint, "evidenceFingerprint");
        if (labels.isEmpty() || labels.stream().anyMatch(label -> label.split() != ExperimentSplit.TRAIN))
            throw new SecurityException("B6 profile may be built only from TRAIN labels");
        for (UncertaintyDimension dimension : UncertaintyDimension.values()) {
            long count = labels.stream().filter(label -> label.observation().dimension() == dimension).count();
            if (count < 100) throw new IllegalArgumentException("B6 profile requires at least 100 simulator labels for " + dimension);
        }
    }

    public List<UncertaintyCalibrationObservation> observations() {
        return labels.stream().map(B6SimulatorLabelEvidence::observation).toList();
    }
}
