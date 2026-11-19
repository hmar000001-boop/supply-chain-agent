package dev.supplychain.agent.evaluation;

import dev.supplychain.agent.evaluation.catalog.ScenarioCatalog;
import dev.supplychain.agent.evaluation.fixture.ScenarioFixtureFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScenarioFixtureFactoryTest {
    @Test
    void advancingFixtureClockKeepsTheCorpusTimelineStableAndTheDeadlineLive() {
        var factory = new ScenarioFixtureFactory(ScenarioCatalog.loadDefault());
        var clock = factory.advancingFixtureClock();
        var fixture = factory.create("REQ-01");

        assertTrue(clock.instant().isAfter(ScenarioFixtureFactory.FIXTURE_AS_OF));
        assertTrue(fixture.planningRequest().budget().deadline().isAfter(clock.instant()));
        assertTrue(fixture.planningRequest().budget().maxRuntime().toMillis() > 0);
    }

    @Test
    void corpusBudgetLabelsMustMatchTheEffectivePlannerLimits() {
        var factory = new ScenarioFixtureFactory(ScenarioCatalog.loadDefault());
        var tight = factory.create("FAIL-10").planningRequest().budget();
        var standard = factory.create("REQ-01").planningRequest().budget();

        assertEquals(1, tight.maxExpansions());
        assertEquals(1, tight.maxEvaluations());
        assertEquals(24, standard.maxExpansions());
        assertEquals(32, standard.maxEvaluations());

        var original = ScenarioCatalog.loadDefault().require("REQ-01");
        var mislabelled = new dev.supplychain.agent.evaluation.catalog.ScenarioDefinition(
                original.scenarioId(), original.kind(), original.scenario(), original.setup(),
                original.expectedInvariantOrBehavior(), original.comparatorsOrControls(),
                original.primaryMeasurements(), original.status(), original.fixtureVersion(),
                original.fixtureProfile(), original.expectedEffectBoundary(), original.seed(),
                ScenarioFixtureFactory.TIGHT_BUDGET_PROFILE);
        assertThrows(IllegalArgumentException.class, () -> factory.create(mislabelled));
    }

    @Test
    void proposalAuthorityNeverBecomesAnExecutionGrant() {
        var factory = new ScenarioFixtureFactory(ScenarioCatalog.loadDefault());
        var approvalRequired = factory.create("REQ-09").planningRequest();
        var readOnly = factory.create("REQ-10").planningRequest();
        var reserveCapability = dev.supplychain.agent.actions.api.Capability.INVENTORY_RESERVE;

        assertTrue(approvalRequired.authorityPolicy().proposalScope().contains(reserveCapability));
        assertTrue(approvalRequired.authorityPolicy().approvalRequiredCapabilities().contains(reserveCapability));
        assertTrue(readOnly.availableActionDefinitions().isEmpty());
        assertTrue(readOnly.authorityPolicy().proposalScope().isEmpty());
        assertTrue(readOnly.authorityPolicy().approvalRequiredCapabilities().isEmpty());
    }

    @Test
    void everyScenarioProducesStableIndependentPlanningInput() {
        var factory = new ScenarioFixtureFactory(ScenarioCatalog.loadDefault());
        var firstPass = factory.createAll();
        var secondPass = factory.createAll();

        assertEquals(25, firstPass.size());
        for (int index = 0; index < firstPass.size(); index++) {
            var first = firstPass.get(index);
            var second = secondPass.get(index);
            assertEquals(first.definition().scenarioId(), first.planningRequest().executionId().substring("m8-eval:".length()));
            assertEquals(first.fixtureFingerprint(), second.fixtureFingerprint());
            assertEquals(first.planningRequest().fingerprint(), second.planningRequest().fingerprint());
        }
        assertNotEquals(firstPass.getFirst().fixtureFingerprint(), firstPass.getLast().fixtureFingerprint());
        assertTrue(firstPass.stream().allMatch(f -> f.planningRequest().requestedAt().isBefore(f.planningRequest().budget().deadline())));
    }
}
