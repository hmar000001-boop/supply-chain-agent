package dev.supplychain.agent.evaluation.experiment.mechanism;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.supplychain.agent.evaluation.experiment.scenario.M8ExperimentScenarioFactory;
import dev.supplychain.agent.mechanisms.b1.B1MemoryCandidateKind;
import dev.supplychain.agent.mechanisms.b1.B1MemoryCandidateReference;
import dev.supplychain.agent.mechanisms.b1.B1MemoryFeatureState;
import dev.supplychain.agent.mechanisms.b3.B3CoalitionFeedbackPolicyService;
import dev.supplychain.agent.mechanisms.b6.UncertaintyCalibrationTrainer;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MechanismTrainingArtifactBuilderTest {
    @Test
    void b1ArtifactFingerprintIsDeterministicAndBindsFeatureStateContents() {
        var scope = new MemoryScopeId(M8ExperimentScenarioFactory.EXPERIMENT_MEMORY_SCOPE_ID);
        var ref = new B1MemoryCandidateReference(B1MemoryCandidateKind.EPISODIC, scope, "memory-17", 2);
        var initial = B1MemoryFeatureState.empty(ref, Instant.EPOCH);
        var trained = initial.applyVerifiedOutcome("outcome-1",
                dev.supplychain.agent.mechanisms.b3.DownstreamOutcomeSignal.USEFUL, Set.of("supplier-delay"),
                Instant.parse("2026-11-01T00:00:00Z"));
        var units = Set.of("a".repeat(64), "b".repeat(64));
        String first = MechanismTrainingArtifactFingerprints.b1(Map.of(ref, trained), Map.of(ref, initial),
                List.of(), Map.of(), units, "independent-b1-verifier-v1");
        String reordered = MechanismTrainingArtifactFingerprints.b1(Map.of(ref, trained), Map.of(ref, initial),
                List.of(), Map.of(), Set.of("b".repeat(64), "a".repeat(64)), "independent-b1-verifier-v1");
        String changed = MechanismTrainingArtifactFingerprints.b1(Map.of(ref, initial), Map.of(ref, initial),
                List.of(), Map.of(), units, "independent-b1-verifier-v1");
        assertEquals(first, reordered);
        assertNotEquals(first, changed);
    }

    @Test
    void builderRejectsMissingB1SourceStateBeforeProducingAnyBundle() {
        var verifiers = new MechanismTrainingArtifactVerificationServices(
                outcome -> false, (procedure, at) -> false, outcome -> false, label -> false);
        var builder = new MechanismTrainingArtifactBuilder(verifiers,
                new B3CoalitionFeedbackPolicyService(5, 1.0), new UncertaintyCalibrationTrainer());
        var inputs = new MechanismTrainingArtifactBuilder.Inputs(
                new MechanismTrainingArtifactBuilder.B1Input(Map.of(), Map.of(), List.of(), Map.of(), "b1-verifier"),
                new MechanismTrainingArtifactBuilder.B2Input(Map.of(), Map.of()),
                new MechanismTrainingArtifactBuilder.B3Input(List.of(), Map.of(), "m8-shadow-policy", 1, 1, 0.25,
                        Instant.parse("2026-11-21T00:00:00Z")),
                new MechanismTrainingArtifactBuilder.B6Input(List.of(), "calibration-trainer", "simulator-label-verifier"));
        assertThrows(SecurityException.class, () -> builder.build("c".repeat(64), inputs,
                Instant.parse("2026-11-22T00:00:00Z")));
    }
}
