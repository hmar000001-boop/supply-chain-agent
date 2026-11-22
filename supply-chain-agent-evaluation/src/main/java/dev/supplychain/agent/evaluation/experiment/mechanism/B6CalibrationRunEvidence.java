package dev.supplychain.agent.evaluation.experiment.mechanism;

import dev.supplychain.agent.evaluation.experiment.execution.ExperimentArm;
import dev.supplychain.agent.evaluation.experiment.execution.ExperimentMechanism;
import dev.supplychain.agent.mechanisms.b6.UncertaintyDimension;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Raw and shadow-transformed uncertainty values from the exact graph assessment reference. */
public record B6CalibrationRunEvidence(
        ExperimentArm arm,
        String experimentUnitFingerprint,
        String trainingArtifactFingerprint,
        String profileId,
        long profileVersion,
        String targetId,
        String assessmentId,
        long assessmentVersion,
        String calibrationStatus,
        Map<UncertaintyDimension, Double> rawValues,
        Map<UncertaintyDimension, Double> observedValues,
        boolean exactShadowTracePresent,
        String evidenceFingerprint) implements MechanismSpecificExperimentEvidence {
    public B6CalibrationRunEvidence {
        arm = Objects.requireNonNull(arm, "arm");
        experimentUnitFingerprint = ExperimentEvidenceFingerprint.requireSha(experimentUnitFingerprint, "experimentUnitFingerprint");
        trainingArtifactFingerprint = ExperimentEvidenceFingerprint.requireSha(trainingArtifactFingerprint, "trainingArtifactFingerprint");
        profileId = profileId == null ? "" : profileId.trim();
        targetId = targetId == null ? "" : targetId.trim();
        assessmentId = assessmentId == null ? "" : assessmentId.trim();
        if (profileVersion < 0 || assessmentVersion < 0) throw new IllegalArgumentException("B6 versions cannot be negative");
        calibrationStatus = ExperimentEvidenceFingerprint.required(calibrationStatus, "calibrationStatus");
        rawValues = values(rawValues, "rawValues");
        observedValues = values(observedValues, "observedValues");
        if (!rawValues.isEmpty() && !rawValues.keySet().equals(java.util.Set.of(UncertaintyDimension.values())))
            throw new IllegalArgumentException("B6 raw uncertainty must cover every dimension");
        if (!observedValues.isEmpty() && !observedValues.keySet().equals(java.util.Set.of(UncertaintyDimension.values())))
            throw new IllegalArgumentException("B6 observed uncertainty must cover every dimension");
        if (!rawValues.isEmpty() && !observedValues.isEmpty() && !rawValues.keySet().equals(observedValues.keySet()))
            throw new SecurityException("B6 raw/observed dimensions differ");
        if (!"NOT_CALIBRATED".equals(calibrationStatus))
            throw new SecurityException("B6 research observations must not claim operational calibration");
        if (arm == ExperimentArm.BASELINE && (!profileId.isEmpty() || exactShadowTracePresent || !rawValues.equals(observedValues)))
            throw new SecurityException("B6 baseline must remain raw and uncalibrated");
        String expected = ExperimentEvidenceFingerprint.sha256(canonical(arm, experimentUnitFingerprint,
                trainingArtifactFingerprint, profileId, profileVersion, targetId, assessmentId,
                assessmentVersion, calibrationStatus, rawValues, observedValues, exactShadowTracePresent));
        evidenceFingerprint = ExperimentEvidenceFingerprint.requireSha(evidenceFingerprint, "evidenceFingerprint");
        if (!evidenceFingerprint.equals(expected)) throw new SecurityException("B6 run evidence fingerprint mismatch");
    }
    @Override public ExperimentMechanism mechanism() { return ExperimentMechanism.B6_UNCERTAINTY_CALIBRATION; }
    public static B6CalibrationRunEvidence create(ExperimentArm arm, String unit, String bundle, String profile,
            long profileVersion, String target, String assessment, long assessmentVersion, String status,
            Map<UncertaintyDimension,Double> raw, Map<UncertaintyDimension,Double> observed, boolean trace) {
        return new B6CalibrationRunEvidence(arm, unit, bundle, profile, profileVersion, target, assessment,
                assessmentVersion, status, raw, observed, trace, ExperimentEvidenceFingerprint.sha256(canonical(
                arm, unit, bundle, profile, profileVersion, target, assessment, assessmentVersion, status,
                raw, observed, trace)));
    }
    @Override public String toJson() {
        return "{\"evidenceType\":\"B6_SHADOW_CALIBRATION\",\"arm\":" + q(arm.name())
                + ",\"experimentUnitFingerprint\":" + q(experimentUnitFingerprint)
                + ",\"trainingArtifactFingerprint\":" + q(trainingArtifactFingerprint)
                + ",\"profileId\":" + q(profileId) + ",\"profileVersion\":" + profileVersion
                + ",\"targetId\":" + q(targetId) + ",\"assessmentId\":" + q(assessmentId)
                + ",\"assessmentVersion\":" + assessmentVersion + ",\"calibrationStatus\":" + q(calibrationStatus)
                + ",\"rawValues\":" + mapJson(rawValues) + ",\"observedValues\":" + mapJson(observedValues)
                + ",\"exactShadowTracePresent\":" + exactShadowTracePresent + ",\"evidenceFingerprint\":" + q(evidenceFingerprint) + "}";
    }
    private static Map<UncertaintyDimension,Double> values(Map<UncertaintyDimension,Double> input, String name) {
        var copy = new TreeMap<UncertaintyDimension,Double>();
        Objects.requireNonNull(input,name).forEach((dimension,value) -> {
            if (dimension == null || value == null || !Double.isFinite(value) || value < 0 || value > 1)
                throw new IllegalArgumentException(name + " has an invalid uncertainty value");
            copy.put(dimension,value);
        });
        return Map.copyOf(copy);
    }
    private static String canonical(ExperimentArm arm, String unit, String bundle, String profile, long profileVersion,
            String target, String assessment, long assessmentVersion, String status,
            Map<UncertaintyDimension,Double> raw, Map<UncertaintyDimension,Double> observed, boolean trace) {
        return arm + "|" + unit + "|" + bundle + "|" + profile + "|" + profileVersion + "|" + target + "|"
                + assessment + "|" + assessmentVersion + "|" + status + "|" + canonicalValues(raw) + "|"
                + canonicalValues(observed) + "|" + trace;
    }
    private static String canonicalValues(Map<UncertaintyDimension,Double> values) {
        return new TreeMap<>(values).entrySet().stream().map(e -> e.getKey() + "=" + Double.toHexString(e.getValue())).toList().toString();
    }
    private static String mapJson(Map<UncertaintyDimension,Double> values) {
        return "{" + new TreeMap<>(values).entrySet().stream().map(e -> q(e.getKey().name()) + ":" + e.getValue())
                .collect(java.util.stream.Collectors.joining(",")) + "}";
    }
    private static String q(String value) { return "\"" + Objects.requireNonNullElse(value, "").replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
}
