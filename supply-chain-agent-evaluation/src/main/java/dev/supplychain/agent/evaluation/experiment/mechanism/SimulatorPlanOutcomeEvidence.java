package dev.supplychain.agent.evaluation.experiment.mechanism;

import dev.supplychain.agent.evaluation.experiment.execution.ExperimentArm;
import dev.supplychain.agent.evaluation.experiment.execution.ExperimentMechanism;
import java.util.List;
import java.util.Objects;

/**
 * Independently replayed plan result from a second simulator/evaluator instance.
 * This is simulator-grounded evidence only; it is never a consumer or real-business outcome.
 */
public record SimulatorPlanOutcomeEvidence(
        ExperimentArm arm,
        ExperimentMechanism mechanism,
        String experimentUnitFingerprint,
        String candidateSetId,
        String selectedCandidateId,
        String worldModelFingerprint,
        String evaluatorFingerprint,
        Status status,
        int replayedActionCount,
        boolean hardConstraintsSatisfied,
        double score,
        double expectedFulfillmentProgress,
        double aggregateRisk,
        double robustness,
        List<String> violations,
        String evidenceFingerprint) {
    public enum Status { VERIFIED_SIMULATOR_REPLAY, NO_SELECTED_PLAN, REPLAY_REJECTED }
    public SimulatorPlanOutcomeEvidence {
        arm = Objects.requireNonNull(arm, "arm");
        mechanism = Objects.requireNonNull(mechanism, "mechanism");
        experimentUnitFingerprint = ExperimentEvidenceFingerprint.requireSha(experimentUnitFingerprint, "experimentUnitFingerprint");
        candidateSetId = optional(candidateSetId);
        selectedCandidateId = optional(selectedCandidateId);
        worldModelFingerprint = ExperimentEvidenceFingerprint.required(worldModelFingerprint, "worldModelFingerprint");
        evaluatorFingerprint = ExperimentEvidenceFingerprint.required(evaluatorFingerprint, "evaluatorFingerprint");
        status = Objects.requireNonNull(status, "status");
        if (replayedActionCount < 0 || !finite01(score) || !finite01(expectedFulfillmentProgress)
                || !finite01(aggregateRisk) || !finite01(robustness))
            throw new IllegalArgumentException("invalid independent simulator outcome metrics");
        violations = List.copyOf(Objects.requireNonNull(violations, "violations"));
        if (status == Status.VERIFIED_SIMULATOR_REPLAY && selectedCandidateId.isEmpty())
            throw new IllegalArgumentException("verified replay requires an exact selected candidate");
        if (status != Status.VERIFIED_SIMULATOR_REPLAY && replayedActionCount != 0)
            throw new IllegalArgumentException("unverified/no-plan result cannot report replayed actions");
        String expected = ExperimentEvidenceFingerprint.sha256(canonical(arm, mechanism, experimentUnitFingerprint,
                candidateSetId, selectedCandidateId, worldModelFingerprint, evaluatorFingerprint, status,
                replayedActionCount, hardConstraintsSatisfied, score, expectedFulfillmentProgress,
                aggregateRisk, robustness, violations));
        evidenceFingerprint = ExperimentEvidenceFingerprint.requireSha(evidenceFingerprint, "evidenceFingerprint");
        if (!evidenceFingerprint.equals(expected)) throw new SecurityException("simulator outcome evidence fingerprint mismatch");
    }
    public static SimulatorPlanOutcomeEvidence create(ExperimentArm arm, ExperimentMechanism mechanism,
            String unit, String candidateSet, String candidate, String world, String evaluator, Status status,
            int actions, boolean hardSafe, double score, double progress, double risk, double robustness,
            List<String> violations) {
        String canonical = canonical(arm, mechanism, unit, candidateSet, candidate, world, evaluator,
                status, actions, hardSafe, score, progress, risk, robustness, violations);
        return new SimulatorPlanOutcomeEvidence(arm, mechanism, unit, candidateSet, candidate, world,
                evaluator, status, actions, hardSafe, score, progress, risk, robustness, violations,
                ExperimentEvidenceFingerprint.sha256(canonical));
    }
    public String evidenceClass() { return "SIMULATOR_ONLY_NOT_BUSINESS_OUTCOME"; }
    public String toJson() {
        return "{\"evidenceClass\":\"SIMULATOR_ONLY_NOT_BUSINESS_OUTCOME\",\"arm\":" + q(arm.name())
                + ",\"mechanism\":" + q(mechanism.name()) + ",\"experimentUnitFingerprint\":" + q(experimentUnitFingerprint)
                + ",\"candidateSetId\":" + q(candidateSetId) + ",\"selectedCandidateId\":" + q(selectedCandidateId)
                + ",\"worldModelFingerprint\":" + q(worldModelFingerprint) + ",\"evaluatorFingerprint\":" + q(evaluatorFingerprint)
                + ",\"status\":" + q(status.name()) + ",\"replayedActionCount\":" + replayedActionCount
                + ",\"hardConstraintsSatisfied\":" + hardConstraintsSatisfied + ",\"score\":" + score
                + ",\"expectedFulfillmentProgress\":" + expectedFulfillmentProgress + ",\"aggregateRisk\":" + aggregateRisk
                + ",\"robustness\":" + robustness + ",\"violations\":["
                + violations.stream().map(SimulatorPlanOutcomeEvidence::q).collect(java.util.stream.Collectors.joining(","))
                + "],\"evidenceFingerprint\":" + q(evidenceFingerprint) + "}";
    }
    private static String canonical(ExperimentArm arm, ExperimentMechanism mechanism, String unit, String candidateSet,
            String candidate, String world, String evaluator, Status status, int actions, boolean hardSafe,
            double score, double progress, double risk, double robustness, List<String> violations) {
        return arm + "|" + mechanism + "|" + unit + "|" + optional(candidateSet) + "|" + optional(candidate) + "|"
                + world + "|" + evaluator + "|" + status + "|" + actions + "|" + hardSafe + "|"
                + Double.toHexString(score) + "|" + Double.toHexString(progress) + "|" + Double.toHexString(risk)
                + "|" + Double.toHexString(robustness) + "|" + violations;
    }
    private static boolean finite01(double value) { return Double.isFinite(value) && value >= 0 && value <= 1; }
    private static String optional(String value) { return value == null ? "" : value.trim(); }
    private static String q(String value) { return "\"" + optional(value).replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
}
