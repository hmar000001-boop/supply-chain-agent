package dev.supplychain.agent.evaluation.app;

import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.mechanisms.feedback.FeedbackEvaluationThreshold;
import dev.supplychain.agent.mechanisms.feedback.FeedbackPolicyHoldoutEvaluator;
import dev.supplychain.agent.mechanisms.feedback.OutcomeFeedbackPolicyTrainer;
import dev.supplychain.agent.mechanisms.feedback.PolicyDisposition;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/**
 * Emits an honest, deterministic R2 feedback-evidence status artifact. The repository currently
 * contains no verified downstream outcome corpus or independent holdout dataset, so the candidate
 * is deliberately trained with zero records and remains SHADOW. This is a reproducibility and
 * fail-closed gate, not an empirical performance comparison or a claim of adaptive superiority.
 */
public final class M8R2OutcomeFeedbackEvidenceCli {
    private static final Instant REFERENCE_TIME = Instant.parse("2026-11-20T00:00:00Z");
    private static final String BASELINE_VERSION = "b1-topk-v1+b3-required-aware-v1";

    private M8R2OutcomeFeedbackEvidenceCli() { }

    public static void main(String[] args) throws IOException {
        Path outputDirectory = Path.of("target", "m8-r2-results");
        if (args.length > 0) {
            if (args.length != 2 || !"--output-dir".equals(args[0])) {
                throw new IllegalArgumentException("supported argument: --output-dir <path>");
            }
            outputDirectory = Path.of(args[1]);
        }
        EvidenceArtifacts artifacts = generate(outputDirectory);
        System.out.println("M8-R2 evidence artifacts written to " + outputDirectory.toAbsolutePath());
        System.out.println("verifiedOutcomes=" + artifacts.verifiedOutcomeCount()
                + ", holdoutScenarios=" + artifacts.holdoutScenarioCount()
                + ", policyDisposition=" + artifacts.disposition()
                + ", activation=" + artifacts.activationStatus());
    }

    public static EvidenceArtifacts generate(Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        var candidate = new OutcomeFeedbackPolicyTrainer().train(
                "m8-r2-outcome-feedback-evidence-v1", 1,
                new MemoryScopeId("r2-offline-evidence-only"), BASELINE_VERSION,
                List.of(), 5, 1.0, REFERENCE_TIME);
        var threshold = new FeedbackEvaluationThreshold("m8-r2-preregistered-holdout-v1",
                12, 0.10, 0.0, 0);
        var report = new FeedbackPolicyHoldoutEvaluator().evaluate(candidate, List.of(), threshold, REFERENCE_TIME);
        if (report.decision() != PolicyDisposition.SHADOW || !Double.isFinite(report.lowerConfidenceBound95())) {
            throw new IllegalStateException("empty evidence must remain a finite, non-activatable SHADOW evaluation");
        }

        String json = """
                {
                  "schemaVersion": 1,
                  "evidenceStatus": "NO_VERIFIED_OUTCOME_CORPUS",
                  "baselinePolicyVersion": "%s",
                  "candidatePolicyId": "%s",
                  "candidatePolicyVersion": %d,
                  "candidateSourceFingerprint": "%s",
                  "evaluationProtocolId": "%s",
                  "evaluationFingerprint": "%s",
                  "verifiedOutcomeCount": %d,
                  "holdoutScenarioCount": %d,
                  "policyDisposition": "%s",
                  "activationStatus": "DISABLED",
                  "empiricalAdvantageEstablished": false,
                  "baselinePreserved": true,
                  "reason": "No verified downstream outcome corpus or independent held-out scenario dataset is included; adaptation remains shadow-only."
                }
                """.formatted(BASELINE_VERSION, candidate.policyId(), candidate.version(),
                candidate.sourceFingerprint(), report.thresholdProtocolId(), report.evaluationFingerprint(),
                candidate.sourceOutcomeIds().size(), report.holdoutScenarioCount(), report.decision().name());

        String conclusion = """
                M8-R2 OUTCOME FEEDBACK EVIDENCE STATUS

                Result: NO_VERIFIED_OUTCOME_CORPUS
                Verified downstream outcomes included: 0
                Independent holdout scenarios included: 0
                Candidate disposition: SHADOW
                Policy activation: DISABLED
                Baseline policy: PRESERVED

                This artifact validates deterministic, fail-closed evidence generation only. It does
                not constitute an empirical B1/B3 ablation, prove adaptive-policy superiority, or
                authorize adaptation. Synthetic unit-test observations exercise protocol gates only
                and are not production outcome evidence. A verified, scope-attributed downstream
                outcome corpus and an independently separated holdout set are required before any
                policy can become eligible for host-authorized activation.
                """;

        Path summaryPath = outputDirectory.resolve("m8-r2-outcome-feedback-evidence.json");
        Path conclusionPath = outputDirectory.resolve("M8-R2-EVIDENCE-CONCLUSION.txt");
        Files.writeString(summaryPath, json, StandardCharsets.UTF_8);
        Files.writeString(conclusionPath, conclusion, StandardCharsets.UTF_8);
        String sums = sha256(summaryPath) + "  " + summaryPath.getFileName() + System.lineSeparator()
                + sha256(conclusionPath) + "  " + conclusionPath.getFileName() + System.lineSeparator();
        Files.writeString(outputDirectory.resolve("SHA256SUMS.txt"), sums, StandardCharsets.UTF_8);
        return new EvidenceArtifacts(candidate.sourceOutcomeIds().size(), report.holdoutScenarioCount(),
                report.decision().name(), "DISABLED", report.evaluationFingerprint());
    }

    private static String sha256(Path path) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    public record EvidenceArtifacts(int verifiedOutcomeCount, int holdoutScenarioCount,
                             String disposition, String activationStatus, String evaluationFingerprint) { }
}
