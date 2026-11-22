package dev.supplychain.agent.evaluation.experiment.mechanism;

import dev.supplychain.agent.evaluation.experiment.execution.ExperimentArm;
import dev.supplychain.agent.evaluation.experiment.execution.ExperimentMechanism;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Explicit, fingerprinted intervention contract for one arm of one generated scenario unit. */
public record MechanismArmPlan(
        String protocolId,
        String experimentUnitId,
        ExperimentMechanism mechanism,
        ExperimentArm arm,
        String controlId,
        String treatmentId,
        String trainingArtifactFingerprint,
        boolean enabled,
        Map<String, String> effectiveSettings,
        String planFingerprint) {
    public MechanismArmPlan {
        protocolId = ExperimentEvidenceFingerprint.required(protocolId, "protocolId");
        experimentUnitId = ExperimentEvidenceFingerprint.required(experimentUnitId, "experimentUnitId");
        mechanism = Objects.requireNonNull(mechanism, "mechanism");
        arm = Objects.requireNonNull(arm, "arm");
        controlId = ExperimentEvidenceFingerprint.required(controlId, "controlId");
        treatmentId = ExperimentEvidenceFingerprint.required(treatmentId, "treatmentId");
        trainingArtifactFingerprint = ExperimentEvidenceFingerprint.requireSha(trainingArtifactFingerprint, "trainingArtifactFingerprint");
        var settings = new TreeMap<String, String>();
        Objects.requireNonNull(effectiveSettings, "effectiveSettings").forEach((key, value) ->
                settings.put(ExperimentEvidenceFingerprint.required(key, "setting key"),
                        ExperimentEvidenceFingerprint.required(value, "setting value")));
        effectiveSettings = Map.copyOf(settings);
        if ((arm == ExperimentArm.BASELINE && enabled) || (arm == ExperimentArm.TREATMENT && !enabled))
            throw new SecurityException("mechanism arm enablement does not match baseline/treatment role");
        String expected = calculate(protocolId, experimentUnitId, mechanism, arm, controlId, treatmentId,
                trainingArtifactFingerprint, enabled, effectiveSettings);
        planFingerprint = ExperimentEvidenceFingerprint.requireSha(planFingerprint, "planFingerprint");
        if (!planFingerprint.equals(expected)) throw new SecurityException("mechanism arm plan fingerprint is invalid");
    }

    public static MechanismArmPlan create(String protocolId, String unitId, ExperimentMechanism mechanism,
            ExperimentArm arm, String controlId, String treatmentId, String bundleFingerprint,
            boolean enabled, Map<String, String> settings) {
        return new MechanismArmPlan(protocolId, unitId, mechanism, arm, controlId, treatmentId,
                bundleFingerprint, enabled, settings, calculate(protocolId, unitId, mechanism, arm,
                controlId, treatmentId, bundleFingerprint, enabled, settings));
    }

    private static String calculate(String protocol, String unit, ExperimentMechanism mechanism,
            ExperimentArm arm, String control, String treatment, String artifact, boolean enabled,
            Map<String, String> settings) {
        return ExperimentEvidenceFingerprint.sha256(protocol + "|" + unit + "|" + mechanism + "|" + arm
                + "|" + control + "|" + treatment + "|" + artifact + "|" + enabled + "|" + new TreeMap<>(settings));
    }
}
