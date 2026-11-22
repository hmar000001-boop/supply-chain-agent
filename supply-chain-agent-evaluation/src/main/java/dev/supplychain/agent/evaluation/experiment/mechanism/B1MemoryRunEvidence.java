package dev.supplychain.agent.evaluation.experiment.mechanism;

import dev.supplychain.agent.evaluation.experiment.execution.ExperimentArm;
import dev.supplychain.agent.evaluation.experiment.execution.ExperimentMechanism;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** B1 state before and after this run; verified outcomes are not inferred from retrieval alone. */
public record B1MemoryRunEvidence(
        ExperimentArm arm,
        String experimentUnitFingerprint,
        String trainingArtifactFingerprint,
        String sourceEvidenceFingerprint,
        Map<String, String> initialFeatureStateFingerprints,
        Map<String, String> finalFeatureStateFingerprints,
        int distinctAppliedOutcomeKeys,
        int distinctAppliedAccessEventKeys,
        String evidenceFingerprint) implements MechanismSpecificExperimentEvidence {
    public B1MemoryRunEvidence {
        arm = Objects.requireNonNull(arm, "arm");
        experimentUnitFingerprint = ExperimentEvidenceFingerprint.requireSha(experimentUnitFingerprint, "experimentUnitFingerprint");
        trainingArtifactFingerprint = ExperimentEvidenceFingerprint.requireSha(trainingArtifactFingerprint, "trainingArtifactFingerprint");
        sourceEvidenceFingerprint = ExperimentEvidenceFingerprint.requireSha(sourceEvidenceFingerprint, "sourceEvidenceFingerprint");
        initialFeatureStateFingerprints = hashes(initialFeatureStateFingerprints, "initialFeatureStateFingerprints");
        finalFeatureStateFingerprints = hashes(finalFeatureStateFingerprints, "finalFeatureStateFingerprints");
        if (distinctAppliedOutcomeKeys < 0 || distinctAppliedAccessEventKeys < 0)
            throw new IllegalArgumentException("B1 evidence counters cannot be negative");
        if (!finalFeatureStateFingerprints.keySet().containsAll(initialFeatureStateFingerprints.keySet()))
            throw new SecurityException("B1 seeded feature state disappeared during the run");
        String expected = ExperimentEvidenceFingerprint.sha256(canonical(arm, experimentUnitFingerprint,
                trainingArtifactFingerprint, sourceEvidenceFingerprint, initialFeatureStateFingerprints,
                finalFeatureStateFingerprints, distinctAppliedOutcomeKeys, distinctAppliedAccessEventKeys));
        evidenceFingerprint = ExperimentEvidenceFingerprint.requireSha(evidenceFingerprint, "evidenceFingerprint");
        if (!evidenceFingerprint.equals(expected)) throw new SecurityException("B1 run evidence fingerprint mismatch");
    }
    @Override public ExperimentMechanism mechanism() { return ExperimentMechanism.B1_MEMORY_LEARNING; }
    public static B1MemoryRunEvidence create(ExperimentArm arm, String unit, String bundle, String source,
            Map<String,String> before, Map<String,String> after, int outcomes, int accesses) {
        return new B1MemoryRunEvidence(arm, unit, bundle, source, before, after, outcomes, accesses,
                ExperimentEvidenceFingerprint.sha256(canonical(arm, unit, bundle, source, before, after, outcomes, accesses)));
    }
    @Override public String toJson() {
        return "{\"evidenceType\":\"B1_MEMORY_FEATURE_STATE\",\"arm\":" + q(arm.name())
                + ",\"experimentUnitFingerprint\":" + q(experimentUnitFingerprint)
                + ",\"trainingArtifactFingerprint\":" + q(trainingArtifactFingerprint)
                + ",\"sourceEvidenceFingerprint\":" + q(sourceEvidenceFingerprint)
                + ",\"initialFeatureStateFingerprints\":" + mapJson(initialFeatureStateFingerprints)
                + ",\"finalFeatureStateFingerprints\":" + mapJson(finalFeatureStateFingerprints)
                + ",\"distinctAppliedOutcomeKeys\":" + distinctAppliedOutcomeKeys
                + ",\"distinctAppliedAccessEventKeys\":" + distinctAppliedAccessEventKeys
                + ",\"evidenceFingerprint\":" + q(evidenceFingerprint) + "}";
    }
    private static Map<String,String> hashes(Map<String,String> input, String label) {
        var copy = new TreeMap<String,String>();
        Objects.requireNonNull(input, label).forEach((key,value) -> copy.put(
                ExperimentEvidenceFingerprint.required(key, label + " key"),
                ExperimentEvidenceFingerprint.requireSha(value, label + " value")));
        return Map.copyOf(copy);
    }
    private static String canonical(ExperimentArm arm, String unit, String bundle, String source,
            Map<String,String> before, Map<String,String> after, int outcomes, int accesses) {
        return arm + "|" + unit + "|" + bundle + "|" + source + "|" + new TreeMap<>(before)
                + "|" + new TreeMap<>(after) + "|" + outcomes + "|" + accesses;
    }
    private static String mapJson(Map<String,String> map) {
        return "{" + new TreeMap<>(map).entrySet().stream().map(e -> q(e.getKey()) + ":" + q(e.getValue()))
                .collect(java.util.stream.Collectors.joining(",")) + "}";
    }
    private static String q(String value) { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
}
