package dev.supplychain.agent.evaluation.experiment.mechanism;

import dev.supplychain.agent.evaluation.experiment.scenario.ExperimentSplit;
import dev.supplychain.agent.evaluation.experiment.scenario.M8ExperimentScenarioFactory;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Immutable, content-addressed set of mechanism artifacts produced exclusively from the locked
 * TRAIN split. No HOLDOUT or TRANSFER data can be represented by this type.
 */
public record MechanismTrainingArtifactBundle(
        String protocolId,
        String candidateCodeFingerprint,
        Set<String> trainUnitFingerprints,
        B1MemoryTrainingArtifact b1,
        B2ProcedureTrainingArtifact b2,
        B3CoalitionTrainingArtifact b3,
        B6CalibrationTrainingArtifact b6,
        Instant frozenAt,
        String artifactFingerprint) {

    public MechanismTrainingArtifactBundle {
        protocolId = ExperimentEvidenceFingerprint.required(protocolId, "protocolId");
        if (!M8ExperimentScenarioFactory.PROTOCOL_ID.equals(protocolId))
            throw new SecurityException("training bundle belongs to another experiment protocol");
        candidateCodeFingerprint = ExperimentEvidenceFingerprint.requireSha(candidateCodeFingerprint, "candidateCodeFingerprint");
        trainUnitFingerprints = Set.copyOf(Objects.requireNonNull(trainUnitFingerprints, "trainUnitFingerprints").stream()
                .map(v -> ExperimentEvidenceFingerprint.requireSha(v, "TRAIN unit fingerprint")).toList());
        if (!trainUnitFingerprints.equals(exactTrainUnitFingerprints()))
            throw new SecurityException("training artifact bundle must bind every exact preregistered TRAIN unit and no other unit");
        b1 = Objects.requireNonNull(b1, "b1");
        b2 = Objects.requireNonNull(b2, "b2");
        b3 = Objects.requireNonNull(b3, "b3");
        b6 = Objects.requireNonNull(b6, "b6");
        frozenAt = Objects.requireNonNull(frozenAt, "frozenAt");
        var researchScope = new dev.supplychain.agent.memory.common.MemoryScopeId(
                M8ExperimentScenarioFactory.EXPERIMENT_MEMORY_SCOPE_ID);
        if (b1.featureSeeds().keySet().stream().anyMatch(reference -> !reference.scopeId().equals(researchScope)))
            throw new SecurityException("B1 seeds must use the synthetic shared research scope");
        if (b2.proceduresByCondition().values().stream().anyMatch(value ->
                !value.procedure().scopeId().equals(researchScope) || !value.artifact().scopeId().equals(researchScope)))
            throw new SecurityException("B2 procedures must use the synthetic shared research scope");
        if (!b3.candidate().scopeId().equals(researchScope) || !b6.profile().scopeId().equals(researchScope))
            throw new SecurityException("B3/B6 artifacts must use the synthetic shared research scope");
        final Set<String> allowedTrainUnits = trainUnitFingerprints;
        requireSubset(b1.outcomes().stream().map(value -> value.experimentUnitFingerprint()).collect(java.util.stream.Collectors.toSet()), allowedTrainUnits, "B1 outcome");
        requireSubset(b1.trainingUnitFingerprints(), allowedTrainUnits, "B1 source");
        b2.sourceUnitsByCondition().values().forEach(units -> requireSubset(units, allowedTrainUnits, "B2 source"));
        requireSubset(b3.candidate().trainingUnitFingerprints(), allowedTrainUnits, "B3 source");
        requireSubset(b6.trainingUnitFingerprints(), allowedTrainUnits, "B6 source");
        if (b6.labels().stream().anyMatch(label -> label.split() != ExperimentSplit.TRAIN))
            throw new SecurityException("B6 training bundle contains a non-TRAIN label");
        String expected = calculateFingerprint(protocolId, candidateCodeFingerprint, trainUnitFingerprints,
                b1, b2, b3, b6, frozenAt);
        artifactFingerprint = ExperimentEvidenceFingerprint.requireSha(artifactFingerprint, "artifactFingerprint");
        if (!artifactFingerprint.equals(expected))
            throw new SecurityException("training artifact bundle fingerprint does not reproduce its contents");
    }

    public static MechanismTrainingArtifactBundle freeze(String candidateCodeFingerprint,
            Set<String> exactTrainUnitFingerprints, B1MemoryTrainingArtifact b1,
            B2ProcedureTrainingArtifact b2, B3CoalitionTrainingArtifact b3,
            B6CalibrationTrainingArtifact b6, Instant frozenAt) {
        Set<String> units = Set.copyOf(Objects.requireNonNull(exactTrainUnitFingerprints, "exactTrainUnitFingerprints"));
        String fingerprint = calculateFingerprint(M8ExperimentScenarioFactory.PROTOCOL_ID,
                candidateCodeFingerprint, units, b1, b2, b3, b6, frozenAt);
        return new MechanismTrainingArtifactBundle(M8ExperimentScenarioFactory.PROTOCOL_ID,
                candidateCodeFingerprint, units, b1, b2, b3, b6, frozenAt, fingerprint);
    }

    public static Set<String> exactTrainUnitFingerprints() {
        return M8ExperimentScenarioFactory.loadDefault().generate(ExperimentSplit.TRAIN).stream()
                .map(unit -> unit.effectiveInputFingerprint()).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static String calculateFingerprint(String protocol, String candidate, Set<String> units,
            B1MemoryTrainingArtifact b1, B2ProcedureTrainingArtifact b2,
            B3CoalitionTrainingArtifact b3, B6CalibrationTrainingArtifact b6, Instant at) {
        String canonical = protocol + "|" + candidate + "|" + new TreeSet<>(units) + "|"
                + Objects.requireNonNull(b1, "b1").evidenceFingerprint() + "|"
                + Objects.requireNonNull(b2, "b2").evidenceFingerprint() + "|"
                + Objects.requireNonNull(b3, "b3").evidenceFingerprint() + "|"
                + Objects.requireNonNull(b6, "b6").evidenceFingerprint() + "|" + Objects.requireNonNull(at, "frozenAt");
        return ExperimentEvidenceFingerprint.sha256(canonical);
    }

    private static void requireSubset(Set<String> subset, Set<String> allowed, String name) {
        if (!allowed.containsAll(subset)) throw new SecurityException(name + " evidence contains a unit outside the frozen TRAIN split");
    }
}
