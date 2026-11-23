package dev.supplychain.agent.evaluation.experiment.mechanism;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.supplychain.agent.evaluation.experiment.execution.ExperimentArm;
import dev.supplychain.agent.evaluation.experiment.execution.ExperimentMechanism;
import dev.supplychain.agent.evaluation.experiment.execution.ExperimentSandboxAudit;
import dev.supplychain.agent.evaluation.experiment.execution.FullGraphExperimentPair;
import dev.supplychain.agent.evaluation.experiment.execution.FullGraphExperimentRunObservation;
import dev.supplychain.agent.mechanisms.b1.B1MemoryCandidateKind;
import dev.supplychain.agent.mechanisms.b1.B1MemoryCandidateReference;
import dev.supplychain.agent.mechanisms.b1.B1MemoryFeatureState;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MechanismExperimentCompileRegressionTest {
    @Test
    void b1EvidenceFingerprintUsesTheTypedCandidateItemVersion() {
        var reference = new B1MemoryCandidateReference(B1MemoryCandidateKind.EPISODIC,
                new MemoryScopeId("experiment-scope"), "episode-17", 7);
        var state = B1MemoryFeatureState.empty(reference, Instant.EPOCH);

        Map<String, String> fingerprints = FullGraphMechanismEvidenceCollector.fingerprints(List.of(state));

        assertEquals(1, fingerprints.size());
        assertTrue(fingerprints.keySet().iterator().next().endsWith("|7"));
    }

    @Test
    void matchedPairCalculatesTreatmentMinusBaselineWithoutLambdaCapture() {
        var mechanism = ExperimentMechanism.B4_INTENTION_METHOD_AUTHORITY;
        var baseline = observation("baseline-session", ExperimentArm.BASELINE, mechanism,
                1, Map.of("objectiveScore", 0.60, "feasible", 0.0));
        var treatment = observation("treatment-session", ExperimentArm.TREATMENT, mechanism,
                2, Map.of("objectiveScore", 0.75, "feasible", 1.0));

        var pair = new FullGraphExperimentPair("M8-IEP-1.0", "unit-001", "family-a", "profile-a",
                "f".repeat(64), "e".repeat(64), mechanism, baseline, treatment, Map.of(), "p".repeat(64));

        assertEquals(0.15, pair.treatmentMinusBaseline().get("objectiveScore"), 1.0e-12);
        assertEquals(1.0, pair.treatmentMinusBaseline().get("feasible"), 1.0e-12);
        assertTrue(baseline.toJsonLine().contains("\"effectiveInputFingerprint\":\"" + "e".repeat(64) + "\""));
        var mismatchedTreatment = observation("treatment-session-mismatched", ExperimentArm.TREATMENT, mechanism,
                2, Map.of("objectiveScore", 0.75, "feasible", 1.0), "x".repeat(64));
        assertThrows(SecurityException.class, () -> new FullGraphExperimentPair("M8-IEP-1.0", "unit-001",
                "family-a", "profile-a", "f".repeat(64), "e".repeat(64), mechanism,
                baseline, mismatchedTreatment, Map.of(), "p".repeat(64)));
    }


    private static FullGraphExperimentRunObservation observation(String sessionId, ExperimentArm arm,
            ExperimentMechanism mechanism, int order, Map<String, Double> metrics) {
        return observation(sessionId, arm, mechanism, order, metrics, "e".repeat(64));
    }

    private static FullGraphExperimentRunObservation observation(String sessionId, ExperimentArm arm,
            ExperimentMechanism mechanism, int order, Map<String, Double> metrics, String effectiveInputFingerprint) {
        return new FullGraphExperimentRunObservation(sessionId, "unit-001", "f".repeat(64),
                effectiveInputFingerprint, "request-fingerprint", mechanism, arm, order,
                FullGraphExperimentRunObservation.Boundary.TERMINAL, "COMPLETED", "test observation",
                Map.of(), List.of("graph-completed"), "g".repeat(64), metrics,
                new ExperimentSandboxAudit.Snapshot(0, 0, 0, 0), "o".repeat(64));
    }
}
