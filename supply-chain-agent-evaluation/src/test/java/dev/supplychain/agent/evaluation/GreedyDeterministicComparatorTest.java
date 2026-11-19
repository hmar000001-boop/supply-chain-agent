package dev.supplychain.agent.evaluation;

import dev.supplychain.agent.evaluation.catalog.ScenarioCatalog;
import dev.supplychain.agent.evaluation.fixture.ScenarioFixtureFactory;
import dev.supplychain.agent.evaluation.planner.GreedyDeterministicComparator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GreedyDeterministicComparatorTest {
    @Test
    void usesCommonSimulatorEvaluatorAndNeverExposesExecution() {
        var fixtures = new ScenarioFixtureFactory(ScenarioCatalog.loadDefault());
        var fixture = fixtures.create("REQ-01");
        var world = fixtures.worldModel();
        var planner = new GreedyDeterministicComparator(world, fixtures.evaluator(world),
                java.time.Clock.fixed(ScenarioFixtureFactory.FIXTURE_AS_OF, java.time.ZoneOffset.UTC));

        var result = planner.plan(fixture.planningRequest());

        assertEquals(GreedyDeterministicComparator.ID, result.plannerId());
        assertFalse(result.candidates().isEmpty());
        assertEquals(2, result.candidates().getFirst().actions().size(),
                "REQ-01 requires two sequential reservations; the committed evaluation must follow the final state");
        assertTrue(result.candidates().stream().allMatch(c -> c.evaluation().feasible()));
        assertTrue(result.explanation().contains("No execution or dispatch"));
    }

    @Test
    void expiredDeadlineStopsBeforeAnyExpansion() {
        var fixtures = new ScenarioFixtureFactory(ScenarioCatalog.loadDefault());
        var fixture = fixtures.create("REQ-01");
        var world = fixtures.worldModel();
        var planner = new GreedyDeterministicComparator(world, fixtures.evaluator(world),
                java.time.Clock.fixed(fixture.planningRequest().budget().deadline(), java.time.ZoneOffset.UTC));

        var result = planner.plan(fixture.planningRequest());

        assertEquals("DEADLINE_EXCEEDED", result.terminationReason().name());
        assertTrue(result.candidates().isEmpty());
        assertEquals(0, result.statistics().expansions());
        assertNotNull(result.explanation());
    }

    @Test
    void readOnlyScenarioProducesNoCandidates() {
        var fixtures = new ScenarioFixtureFactory(ScenarioCatalog.loadDefault());
        var fixture = fixtures.create("REQ-10");
        var world = fixtures.worldModel();
        var planner = new GreedyDeterministicComparator(world, fixtures.evaluator(world),
                java.time.Clock.fixed(ScenarioFixtureFactory.FIXTURE_AS_OF, java.time.ZoneOffset.UTC));

        var result = planner.plan(fixture.planningRequest());

        assertTrue(result.candidates().isEmpty());
        assertTrue(result.terminationReason().name().equals("NO_AVAILABLE_ACTIONS"));
    }
}
