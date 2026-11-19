package dev.supplychain.agent.evaluation.ablation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechanismAblationRunnerTest {
    private final MechanismAblationRunner runner = new MechanismAblationRunner();

    @Test
    void emitsTheDeclaredScenarioAndObservationMatrix() {
        var batch = runner.run();
        assertEquals(19, batch.scenarioCount());
        assertEquals(45, batch.primaryObservationCount());
        assertEquals(18, batch.deterministicReplayCount());
        assertEquals(63, batch.observations().size());
        assertEquals(26, batch.comparisons().size());
        assertEquals(18, batch.determinismReplayMatchCount());
        assertEquals(45, batch.observations().stream()
                .filter(row -> row.observationKind().equals("PRIMARY")).count());
    }

    @Test
    void boundedWorkspaceImprovesRequiredCoverageWithoutIncreasingCapacity() {
        var batch = runner.run();
        for (int i = 1; i <= 3; i++) {
            String scenario = "B3-0" + i;
            var control = find(batch.observations(), scenario, "TOP_K_SHARED_CONTEXT", "PRIMARY");
            var treatment = find(batch.observations(), scenario, "B3_BOUNDED_BROADCAST", "PRIMARY");
            assertEquals(7.0, control.metrics().get("capacityUnitsUsed").doubleValue());
            assertEquals(7.0, treatment.metrics().get("capacityUnitsUsed").doubleValue());
            assertEquals(2.0, control.metrics().get("coverageTags").doubleValue());
            assertEquals(3.0, treatment.metrics().get("coverageTags").doubleValue());
            assertEquals(1.0, treatment.metrics().get("broadcastAccepted").doubleValue());
        }
    }

    @Test
    void b1ToB3CompositionImprovesCoverageAndB6CanRequestEvidence() {
        var batch = runner.run();
        var topK = find(batch.observations(), "B1B3B6-01", "TOP_K_FIXED_BUDGET", "PRIMARY");
        var b1b3 = find(batch.observations(), "B1B3B6-01", "B1_B3_FIXED_BUDGET", "PRIMARY");
        var composed = find(batch.observations(), "B1B3B6-01", "B1_B3_B6_COMPOSED", "PRIMARY");
        assertEquals(2.0, topK.metrics().get("workspaceCoverage").doubleValue());
        assertEquals(1.0, topK.metrics().get("workspaceMissingCoverage").doubleValue());
        assertEquals(3.0, b1b3.metrics().get("workspaceCoverage").doubleValue());
        assertEquals(0.0, b1b3.metrics().get("workspaceMissingCoverage").doubleValue());
        assertEquals("REQUEST_EVIDENCE", composed.labels().get("governorDecision"));
    }

    @Test
    void intentionLifecyclePreservesTheObjectiveAndB6CanSuspendIt() {
        var batch = runner.run();
        for (int i = 1; i <= 2; i++) {
            var intention = find(batch.observations(), "B4-0" + i, "B4_INTENTION_LIFECYCLE", "PRIMARY");
            assertEquals(1.0, intention.metrics().get("objectivePreserved").doubleValue());
            assertEquals(1.0, intention.metrics().get("methodReconsidered").doubleValue());
        }
        var suspended = find(batch.observations(), "B4B6-01", "B4_B6_SUSPEND_ON_GOVERNOR", "PRIMARY");
        assertEquals(1.0, suspended.metrics().get("governorInterrupt").doubleValue());
        assertEquals(1.0, suspended.metrics().get("objectivePreserved").doubleValue());
        assertEquals("SUSPENDED", suspended.labels().get("finalIntentionStatus"));
    }

    @Test
    void typedImpasseConsolidatesDuplicateSignalsAndB6InterruptsOnlyHighUncertaintyFixture() {
        var batch = runner.run();
        var generic = find(batch.observations(), "B2-01", "GENERIC_RETRY", "PRIMARY");
        var typed = find(batch.observations(), "B2-01", "B2_TYPED_IMPASSE", "PRIMARY");
        assertEquals(3.0, generic.metrics().get("resolutionUnits").doubleValue());
        assertEquals(2.0, typed.metrics().get("resolutionUnits").doubleValue());
        assertEquals(2.0, typed.metrics().get("typedSubstates").doubleValue());

        var high = find(batch.observations(), "B6-01", "B6_UNCERTAINTY_RESOURCE_GOVERNOR", "PRIMARY");
        var low = find(batch.observations(), "B6-02", "B6_UNCERTAINTY_RESOURCE_GOVERNOR", "PRIMARY");
        assertEquals("INTERRUPT_FOR_HUMAN", high.labels().get("decision"));
        assertEquals("CONTINUE", low.labels().get("decision"));
    }

    @Test
    void b1KeepsLearningUpdateExplicitlyUnevaluated() {
        var batch = runner.run();
        var treatment = find(batch.observations(), "B1-01", "B1_FULL", "PRIMARY");
        assertTrue(treatment.labels().get("learningUpdate").contains("not evaluated"));
    }

    private static MechanismAblationRunner.Observation find(
            List<MechanismAblationRunner.Observation> rows, String scenarioId,
            String conditionId, String kind) {
        return rows.stream().filter(row -> row.scenarioId().equals(scenarioId)
                        && row.conditionId().equals(conditionId)
                        && row.observationKind().equals(kind))
                .findFirst().orElseThrow(() -> new AssertionError(
                        "Missing observation " + scenarioId + "/" + conditionId + "/" + kind));
    }
}
