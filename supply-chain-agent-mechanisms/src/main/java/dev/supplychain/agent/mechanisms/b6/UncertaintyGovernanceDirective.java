package dev.supplychain.agent.mechanisms.b6;

import java.util.Objects;

/**
 * Immutable, target-bound B6 control implications derived only from a validated calibration.
 * Uncalibrated scores remain explicitly heuristic and do not activate probability thresholds.
 */
public record UncertaintyGovernanceDirective(
        UncertaintyAssessment.Reference assessmentReference,
        UncertaintyAssessment.CalibrationStatus calibrationStatus,
        String calibrationProfileId,
        String calibrationTargetId,
        long calibrationProfileVersion,
        double evidenceUncertainty,
        double hypothesisUncertainty,
        double planUncertainty,
        int minimumDistinctMemoryKinds,
        int minimumDistinctWorkspaceKinds,
        boolean requireAlternativeHypothesisReview,
        boolean requireIndependentPlanVerification) {

    public static final double BROADER_EVIDENCE_THRESHOLD = 0.65;
    public static final double ALTERNATIVE_HYPOTHESIS_THRESHOLD = 0.70;
    public static final double INDEPENDENT_PLAN_VERIFICATION_THRESHOLD = 0.70;

    public UncertaintyGovernanceDirective {
        Objects.requireNonNull(assessmentReference, "assessmentReference");
        Objects.requireNonNull(calibrationStatus, "calibrationStatus");
        calibrationProfileId = calibrationProfileId == null ? "" : calibrationProfileId.trim();
        calibrationTargetId = calibrationTargetId == null ? "" : calibrationTargetId.trim();
        if (calibrationProfileVersion < 0) throw new IllegalArgumentException("calibration profile version cannot be negative");
        for (double value : new double[]{evidenceUncertainty, hypothesisUncertainty, planUncertainty}) {
            if (!Double.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException("uncertainty values must be in [0,1]");
        }
        if (minimumDistinctMemoryKinds < 1 || minimumDistinctMemoryKinds > 4
                || minimumDistinctWorkspaceKinds < 1 || minimumDistinctWorkspaceKinds > 6) {
            throw new IllegalArgumentException("uncertainty diversity requirements are outside supported bounds");
        }
        if (calibrationStatus == UncertaintyAssessment.CalibrationStatus.NOT_CALIBRATED) {
            if (!calibrationProfileId.isEmpty() || !calibrationTargetId.isEmpty() || calibrationProfileVersion != 0
                    || minimumDistinctMemoryKinds != 1 || minimumDistinctWorkspaceKinds != 1
                    || requireAlternativeHypothesisReview || requireIndependentPlanVerification) {
                throw new SecurityException("uncalibrated uncertainty cannot activate calibrated governance thresholds");
            }
        } else if (calibrationProfileId.isBlank() || calibrationTargetId.isBlank() || calibrationProfileVersion < 1) {
            throw new IllegalArgumentException("calibrated governance requires an exact profile identity and version");
        }
    }

    public static UncertaintyGovernanceDirective from(UncertaintyAssessment assessment) {
        Objects.requireNonNull(assessment, "assessment");
        var calibration = assessment.calibration();
        boolean calibrated = calibration.status() == UncertaintyAssessment.CalibrationStatus.CALIBRATED;
        double evidence = assessment.signals().get(UncertaintyDimension.EVIDENCE).uncertainty();
        double hypothesis = assessment.signals().get(UncertaintyDimension.HYPOTHESIS).uncertainty();
        double plan = assessment.signals().get(UncertaintyDimension.PLAN).uncertainty();
        if (!calibrated) {
            return new UncertaintyGovernanceDirective(assessment.reference(), calibration.status(), "", "", 0,
                    evidence, hypothesis, plan, 1, 1, false, false);
        }
        return new UncertaintyGovernanceDirective(assessment.reference(), calibration.status(), calibration.profileId(),
                calibration.profileTargetId(), calibration.profileVersion(), evidence, hypothesis, plan,
                evidence >= BROADER_EVIDENCE_THRESHOLD ? 2 : 1,
                evidence >= BROADER_EVIDENCE_THRESHOLD ? 2 : 1,
                hypothesis >= ALTERNATIVE_HYPOTHESIS_THRESHOLD,
                plan >= INDEPENDENT_PLAN_VERIFICATION_THRESHOLD);
    }

    public boolean calibrated() { return calibrationStatus == UncertaintyAssessment.CalibrationStatus.CALIBRATED; }
}
