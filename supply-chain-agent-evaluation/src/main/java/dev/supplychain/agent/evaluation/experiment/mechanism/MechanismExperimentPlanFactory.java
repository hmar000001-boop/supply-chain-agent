package dev.supplychain.agent.evaluation.experiment.mechanism;

import dev.supplychain.agent.evaluation.experiment.execution.ExperimentArm;
import dev.supplychain.agent.evaluation.experiment.execution.ExperimentMechanism;
import dev.supplychain.agent.evaluation.experiment.scenario.ExperimentScenarioUnit;
import dev.supplychain.agent.evaluation.experiment.scenario.ExperimentSplit;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Preregistered mechanism/control mapping; no runtime tuning or holdout-derived values are accepted. */
public final class MechanismExperimentPlanFactory {
    private static final Map<ExperimentMechanism, String> CONTROLS = Map.of(
            ExperimentMechanism.B1_MEMORY_LEARNING, "untrained_empty_feature_repository",
            ExperimentMechanism.B2_RECOVERY_PROCEDURE_REUSE, "no_active_recovery_procedure",
            ExperimentMechanism.B3_COALITION_FEEDBACK, "no_learned_coalition_preference",
            ExperimentMechanism.B4_INTENTION_METHOD_AUTHORITY, "fixed_initial_planning_method",
            ExperimentMechanism.B6_UNCERTAINTY_CALIBRATION, "uncalibrated_uncertainty_assessment");
    private static final Map<ExperimentMechanism, String> TREATMENTS = Map.of(
            ExperimentMechanism.B1_MEMORY_LEARNING, "verified_use_and_outcome_feature_learning",
            ExperimentMechanism.B2_RECOVERY_PROCEDURE_REUSE, "approved_validated_active_procedure_reuse",
            ExperimentMechanism.B3_COALITION_FEEDBACK, "offline_shadow_coalition_preference",
            ExperimentMechanism.B4_INTENTION_METHOD_AUTHORITY, "governed_reconsideration_and_authorized_method_change",
            ExperimentMechanism.B6_UNCERTAINTY_CALIBRATION, "trained_target_specific_shadow_calibration");

    public MechanismArmPlan create(ExperimentScenarioUnit unit, ExperimentMechanism mechanism,
            ExperimentArm arm, MechanismTrainingArtifactBundle bundle) {
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(mechanism, "mechanism");
        Objects.requireNonNull(arm, "arm");
        Objects.requireNonNull(bundle, "bundle");
        if (unit.split() != ExperimentSplit.HOLDOUT)
            throw new SecurityException("confirmatory mechanism arm plans accept HOLDOUT units only");
        if (!bundle.protocolId().equals(unit.protocolId())
                || bundle.trainUnitFingerprints().contains(unit.effectiveInputFingerprint()))
            throw new SecurityException("HOLDOUT unit does not belong to the protocol or overlaps the frozen TRAIN artifact bundle");
        Map<String, String> settings = settings(mechanism, arm, bundle);
        return MechanismArmPlan.create(unit.protocolId(), unit.experimentUnitId(), mechanism, arm,
                CONTROLS.get(mechanism), TREATMENTS.get(mechanism), bundle.artifactFingerprint(),
                arm == ExperimentArm.TREATMENT, settings);
    }

    private static Map<String, String> settings(ExperimentMechanism mechanism, ExperimentArm arm,
            MechanismTrainingArtifactBundle bundle) {
        boolean enabled = arm == ExperimentArm.TREATMENT;
        var settings = new TreeMap<String, String>();
        switch (mechanism) {
            case B1_MEMORY_LEARNING -> {
                settings.put("learningEnabled", Boolean.toString(enabled));
                settings.put("seedFeatureCount", Integer.toString(enabled ? bundle.b1().featureSeeds().size() : 0));
                settings.put("sourceEvidenceFingerprint", bundle.b1().evidenceFingerprint());
                settings.put("learningReward", "USEFUL=1.0;NEUTRAL=0.5;HARMFUL=0.0");
                settings.put("utilityUpdate", "first-outcome-reward;then-ewma-alpha-0.2");
                settings.put("cueUpdate", "USEFUL=+0.1;NEUTRAL=0.0;HARMFUL=-0.1;clamp=[0,1]");
            }
            case B2_RECOVERY_PROCEDURE_REUSE -> {
                settings.put("procedureReuseEnabled", Boolean.toString(enabled));
                settings.put("approvedProcedureCount", Integer.toString(enabled ? bundle.b2().proceduresByCondition().size() : 0));
                settings.put("sourceEvidenceFingerprint", bundle.b2().evidenceFingerprint());
                settings.put("actionExecution", "PROPOSAL_HINT_ONLY");
            }
            case B3_COALITION_FEEDBACK -> {
                settings.put("shadowFeedbackEnabled", Boolean.toString(enabled));
                settings.put("candidateFingerprint", B3CoalitionFeedbackPolicyServiceFingerprint.of(bundle.b3().candidate()));
                settings.put("adjustmentCount", Integer.toString(enabled ? bundle.b3().candidate().preferenceAdjustments().size() : 0));
                settings.put("activation", "FORBIDDEN_SHADOW_ONLY");
            }
            case B4_INTENTION_METHOD_AUTHORITY -> {
                settings.put("governedReconsiderationEnabled", Boolean.toString(enabled));
                settings.put("objectiveMutation", "FORBIDDEN");
                settings.put("methodChangeAuthority", "DECISION_GOVERNOR_OR_OBJECTIVE_OWNER");
                settings.put("transitionAudit", "COMMITTED_VERSIONED_TRANSITIONS_ONLY");
            }
            case B6_UNCERTAINTY_CALIBRATION -> {
                settings.put("shadowCalibrationEnabled", Boolean.toString(enabled));
                settings.put("profileId", bundle.b6().profile().id());
                settings.put("profileVersion", Long.toString(bundle.b6().profile().version()));
                settings.put("profileStatus", bundle.b6().profile().status().name());
                settings.put("operationalCalibrationStatus", "NOT_CALIBRATED");
                settings.put("sourceEvidenceFingerprint", bundle.b6().evidenceFingerprint());
            }
        }
        return Map.copyOf(settings);
    }

    private static final class B3CoalitionFeedbackPolicyServiceFingerprint {
        static String of(dev.supplychain.agent.mechanisms.b3.B3CoalitionFeedbackCandidate candidate) {
            return dev.supplychain.agent.mechanisms.b3.B3CoalitionFeedbackPolicyService.candidateFingerprint(candidate);
        }
    }
}
