package dev.supplychain.agent.evaluation.experiment.research;

import dev.supplychain.agent.evaluation.experiment.scenario.ExperimentSplit;
import java.util.Objects;

/**
 * One independently verified, episode-level endpoint observation for a preregistered M8 hypothesis.
 * It is deliberately distinct from graph telemetry and from an LLM-as-a-judge score.
 */
public record M8VerifiedEndpointEvidence(
        EndpointId endpointId,
        ExperimentSplit split,
        String experimentUnitId,
        String experimentUnitFingerprint,
        String fixtureProfile,
        String scenarioFamily,
        double baselineValue,
        double treatmentValue,
        String verifierId,
        String sourceEvidenceFingerprint,
        String evidenceFingerprint) {

    public enum Direction { HIGHER_IS_BETTER, LOWER_IS_BETTER }

    public enum EndpointId {
        H1_VERIFIED_USEFULNESS_PER_RETRIEVAL_BUDGET("H1", Direction.HIGHER_IS_BETTER, ValueKind.RATE, true),
        H1_RETRIEVAL_MAINTENANCE_COST("H1", Direction.LOWER_IS_BETTER, ValueKind.NON_NEGATIVE, false),
        H2_FEASIBLE_RECOVERY_SUCCESS("H2", Direction.HIGHER_IS_BETTER, ValueKind.BINARY, true),
        H2_SEARCH_COST_TO_FIRST_FEASIBLE("H2", Direction.LOWER_IS_BETTER, ValueKind.NON_NEGATIVE, false),
        H3_VERIFIED_USEFUL_CONSUMPTION_PER_RESOURCE("H3", Direction.HIGHER_IS_BETTER, ValueKind.RATE, true),
        H3_WORKSPACE_RESOURCE_UNITS("H3", Direction.LOWER_IS_BETTER, ValueKind.NON_NEGATIVE, false),
        H4_OBJECTIVE_SATISFACTION("H4", Direction.HIGHER_IS_BETTER, ValueKind.BINARY, true),
        H4_OBJECTIVE_SCORE("H4", Direction.HIGHER_IS_BETTER, ValueKind.RATE, false),
        H4_TOTAL_RESOURCE_UNITS("H4", Direction.LOWER_IS_BETTER, ValueKind.NON_NEGATIVE, false);

        private final String hypothesisId;
        private final Direction direction;
        private final ValueKind valueKind;
        private final boolean primary;

        EndpointId(String hypothesisId, Direction direction, ValueKind valueKind, boolean primary) {
            this.hypothesisId = hypothesisId;
            this.direction = direction;
            this.valueKind = valueKind;
            this.primary = primary;
        }
        public String hypothesisId() { return hypothesisId; }
        public Direction direction() { return direction; }
        public boolean primary() { return primary; }
        public boolean binary() { return valueKind == ValueKind.BINARY; }
        void validateValue(double value) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException(name() + " must be finite");
            switch (valueKind) {
                case RATE -> {
                    if (value < 0.0 || value > 1.0) throw new IllegalArgumentException(name() + " must be within [0,1]");
                }
                case BINARY -> {
                    if (value != 0.0 && value != 1.0) throw new IllegalArgumentException(name() + " must be 0 or 1");
                }
                case NON_NEGATIVE -> {
                    if (value < 0.0) throw new IllegalArgumentException(name() + " cannot be negative");
                }
            }
        }
    }

    private enum ValueKind { RATE, BINARY, NON_NEGATIVE }

    public M8VerifiedEndpointEvidence {
        endpointId = Objects.requireNonNull(endpointId, "endpointId");
        split = Objects.requireNonNull(split, "split");
        if (split != ExperimentSplit.HOLDOUT && split != ExperimentSplit.TRANSFER)
            throw new SecurityException("confirmatory endpoint evidence accepts HOLDOUT or TRANSFER only");
        experimentUnitId = required(experimentUnitId, "experimentUnitId");
        experimentUnitFingerprint = sha(experimentUnitFingerprint, "experimentUnitFingerprint");
        fixtureProfile = required(fixtureProfile, "fixtureProfile");
        scenarioFamily = required(scenarioFamily, "scenarioFamily");
        endpointId.validateValue(baselineValue);
        endpointId.validateValue(treatmentValue);
        verifierId = required(verifierId, "verifierId");
        sourceEvidenceFingerprint = sha(sourceEvidenceFingerprint, "sourceEvidenceFingerprint");
        String expected = calculate(endpointId, split, experimentUnitId, experimentUnitFingerprint,
                fixtureProfile, scenarioFamily, baselineValue, treatmentValue, verifierId,
                sourceEvidenceFingerprint);
        evidenceFingerprint = sha(evidenceFingerprint, "evidenceFingerprint");
        if (!evidenceFingerprint.equals(expected))
            throw new SecurityException("verified endpoint evidence fingerprint does not reproduce");
    }

    public static M8VerifiedEndpointEvidence create(EndpointId endpointId, ExperimentSplit split,
            String unitId, String unitFingerprint, String fixtureProfile, String scenarioFamily,
            double baselineValue, double treatmentValue, String verifierId, String sourceEvidenceFingerprint) {
        return new M8VerifiedEndpointEvidence(endpointId, split, unitId, unitFingerprint, fixtureProfile,
                scenarioFamily, baselineValue, treatmentValue, verifierId, sourceEvidenceFingerprint,
                calculate(endpointId, split, unitId, unitFingerprint, fixtureProfile, scenarioFamily,
                        baselineValue, treatmentValue, verifierId, sourceEvidenceFingerprint));
    }

    public double orientedImprovement() {
        return endpointId.direction() == Direction.HIGHER_IS_BETTER
                ? treatmentValue - baselineValue : baselineValue - treatmentValue;
    }

    private static String calculate(EndpointId endpointId, ExperimentSplit split, String unitId,
            String unitFingerprint, String profile, String family, double baseline, double treatment,
            String verifier, String sourceFingerprint) {
        String canonical = endpointId + "|" + split + "|" + unitId + "|" + unitFingerprint + "|"
                + profile + "|" + family + "|" + Double.toHexString(baseline) + "|"
                + Double.toHexString(treatment) + "|" + verifier + "|" + sourceFingerprint;
        return sha256(canonical);
    }

    static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String sha(String value, String name) {
        String result = required(value, name);
        if (!result.matches("[0-9a-f]{64}")) throw new IllegalArgumentException(name + " must be lowercase SHA-256");
        return result;
    }

    private static String required(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
