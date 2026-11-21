package dev.supplychain.agent.mechanisms.feedback;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Paired holdout gate over independently verified source-run evidence. */
public final class FeedbackPolicyHoldoutEvaluator {
    private final FeedbackEvaluationEvidenceVerifier evidenceVerifier;

    public FeedbackPolicyHoldoutEvaluator(FeedbackEvaluationEvidenceVerifier evidenceVerifier) {
        this.evidenceVerifier = Objects.requireNonNull(evidenceVerifier, "evidenceVerifier");
    }

    public FeedbackPolicyEvaluationReport evaluate(OutcomeFeedbackPolicyCandidate candidate,
            List<FeedbackEvaluationObservation> holdout, FeedbackEvaluationThreshold threshold, Instant evaluatedAt) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(holdout, "holdout");
        Objects.requireNonNull(threshold, "threshold");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        if (evaluatedAt.isBefore(candidate.trainedAt())) {
            throw new IllegalArgumentException("evaluation cannot predate candidate training");
        }

        var ordered = holdout.stream().map(v -> Objects.requireNonNull(v, "observation"))
                .sorted(Comparator.comparing(FeedbackEvaluationObservation::scenarioId)).toList();
        Set<String> scenarioIds = ordered.stream().map(FeedbackEvaluationObservation::scenarioId)
                .collect(java.util.stream.Collectors.toSet());
        if (scenarioIds.size() != ordered.size()) {
            throw new IllegalArgumentException("holdout scenario IDs must be unique");
        }
        Set<String> experimentUnits = ordered.stream().map(FeedbackEvaluationObservation::experimentUnitFingerprint)
                .collect(java.util.stream.Collectors.toSet());
        if (experimentUnits.size() != ordered.size()) {
            throw new IllegalArgumentException("holdout experimental-unit fingerprints must be unique");
        }
        Set<String> sourceRunFingerprints = ordered.stream()
                .flatMap(o -> java.util.stream.Stream.of(o.baselineRunFingerprint(), o.candidateRunFingerprint()))
                .collect(java.util.stream.Collectors.toSet());
        if (sourceRunFingerprints.size() != ordered.size() * 2) {
            throw new IllegalArgumentException("paired holdout source-run fingerprints must be unique");
        }
        if (!java.util.Collections.disjoint(candidate.trainingExperimentUnitFingerprints(), experimentUnits)) {
            throw new SecurityException("training/holdout experimental-unit fingerprint overlap detected");
        }
        for (FeedbackEvaluationObservation observation : ordered) {
            if (!evidenceVerifier.verify(candidate, observation, evaluatedAt)) {
                throw new SecurityException("paired holdout run evidence is missing, stale, mismatched or not bound to the exact scenario and policy versions: "
                        + observation.scenarioId());
            }
        }

        double mean = 0.0, variance = 0.0, resource = 0.0;
        int safety = 0;
        for (var observation : ordered) {
            mean += observation.utilityDelta();
            resource += observation.resourceDelta();
            safety += observation.safetyRegression();
        }
        int n = ordered.size();
        if (n > 0) {
            mean /= n;
            resource /= n;
            for (var observation : ordered) {
                double delta = observation.utilityDelta() - mean;
                variance += delta * delta;
            }
            variance = n > 1 ? variance / (n - 1) : 0.0;
        }
        // A low-sample report is SHADOW-only; keep every serialized metric finite.
        // Zero is neutral, never an eligibility signal because the preregistered sample gate
        // prevents activation with fewer than two independent scenarios.
        double lower = n > 1 ? mean - 1.96 * Math.sqrt(variance / n) : 0.0;
        PolicyDisposition decision;
        String reason;
        if (n < threshold.minimumHoldoutScenarios()) {
            decision = PolicyDisposition.SHADOW;
            reason = "insufficient independent held-out scenarios";
        } else if (safety > threshold.maximumSafetyRegressions()) {
            decision = PolicyDisposition.REJECTED;
            reason = "candidate exceeded preregistered safety-regression limit";
        } else if (resource > threshold.maximumMeanResourceIncrease()) {
            decision = PolicyDisposition.REJECTED;
            reason = "candidate exceeded preregistered resource-cost limit";
        } else if (lower < threshold.minimumMeanUtilityGain()) {
            decision = PolicyDisposition.REJECTED;
            reason = "candidate did not clear the preregistered lower-confidence-bound utility threshold";
        } else {
            decision = PolicyDisposition.ELIGIBLE_FOR_AUTHORIZED_ACTIVATION;
            reason = "holdout criteria passed; separate trusted activation approval is still required";
        }

        var canonicalAdjustments = new TreeMap<>(candidate.candidateAdjustments());
        String canonical = candidate.scopeId().value() + "|" + candidate.policyId() + "|" + candidate.version() + "|"
                + candidate.baselinePolicyVersion() + "|" + candidate.sourceFingerprint() + "|" + canonicalAdjustments + "|"
                + candidate.trainingCycleIds().stream().sorted().toList() + "|"
                + candidate.trainingExperimentUnitFingerprints().stream().sorted().toList() + "|"
                + threshold + "|" + ordered;
        return new FeedbackPolicyEvaluationReport(candidate.policyId(), candidate.version(), threshold.protocolId(), n,
                mean, lower, safety, resource, decision, reason, sha256(canonical), evaluatedAt);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
