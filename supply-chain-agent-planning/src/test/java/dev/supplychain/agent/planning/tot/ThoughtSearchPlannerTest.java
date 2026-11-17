package dev.supplychain.agent.planning.tot;

import static org.junit.jupiter.api.Assertions.*;

import dev.supplychain.agent.planning.PlanningTestFixtures;
import dev.supplychain.agent.planning.api.PlannerId;
import dev.supplychain.agent.planning.api.PlannerTerminationReason;
import dev.supplychain.agent.planning.api.PlanningRequest;
import dev.supplychain.agent.planning.session.PlannerCheckpointConflictException;
import dev.supplychain.agent.planning.session.PlannerSessionStatus;
import org.junit.jupiter.api.Test;

final class ThoughtSearchPlannerTest {
    @Test
    void expandsStructuredAlternativesAndReturnsRankedCandidates() {
        PlanningRequest request = PlanningTestFixtures.request("tot-branching",
                PlanningTestFixtures.budget(20, 20, 0, 0, 2));
        var store = PlanningTestFixtures.sessionStore();
        var planner = planner(request, store, new ToTParameters(8, 2, 1));

        var result = planner.plan(request);

        assertEquals(PlannerId.TOT_BOUNDED_SEARCH, result.plannerId());
        assertTrue(result.candidates().size() >= 2);
        assertTrue(result.statistics().expansions() >= 2);
        assertTrue(result.trace().stream().anyMatch(node -> node.depth() == 2));
        assertEquals(PlannerTerminationReason.MAX_DEPTH_REACHED, result.terminationReason());
        assertEquals(PlannerSessionStatus.COMPLETED, store.find(result.sessionId()).orElseThrow().status());
    }

    @Test
    void persistsUnprocessedFrontierWhenBudgetExhaustsMidLayerAndResumes() {
        PlanningRequest initial = PlanningTestFixtures.request("tot-resume",
                PlanningTestFixtures.budget(1, 1, 0, 0, 3));
        var store = PlanningTestFixtures.sessionStore();
        var planner = planner(initial, store, new ToTParameters(8, 2, 1));

        var partial = planner.plan(initial);

        assertEquals(PlannerTerminationReason.SEARCH_BUDGET_EXHAUSTED, partial.terminationReason());
        assertTrue(partial.sessionId().length() > 0);
        var saved = store.find(partial.sessionId()).orElseThrow();
        assertEquals(PlannerSessionStatus.ACTIVE, saved.status());
        assertTrue(saved.frontier().size() >= 1);

        PlanningRequest resumed = PlanningTestFixtures.withBudgetAndResume(initial,
                PlanningTestFixtures.budget(20, 20, 0, 0, 3), partial.sessionId());
        var resumedPlanner = planner(resumed, store, new ToTParameters(8, 2, 1));
        var completed = resumedPlanner.plan(resumed);

        assertTrue(completed.terminationReason() == PlannerTerminationReason.MAX_DEPTH_REACHED
                || completed.terminationReason() == PlannerTerminationReason.COMPLETED);
        assertTrue(completed.statistics().expansions() > partial.statistics().expansions());
        assertTrue(completed.statistics().backtracks() >= partial.statistics().backtracks());
        assertTrue(completed.statistics().elapsed().compareTo(partial.statistics().elapsed()) >= 0);
        assertTrue(completed.candidates().size() >= partial.candidates().size());
        assertEquals(PlannerSessionStatus.COMPLETED, store.find(partial.sessionId()).orElseThrow().status());
    }

    @Test
    void rejectsResumeWhenSearchConfigurationChanges() {
        PlanningRequest initial = PlanningTestFixtures.request("tot-config-change",
                PlanningTestFixtures.budget(1, 1, 0, 0, 3));
        var store = PlanningTestFixtures.sessionStore();
        var partial = planner(initial, store, new ToTParameters(4, 2, 1)).plan(initial);
        PlanningRequest resume = PlanningTestFixtures.withBudgetAndResume(initial,
                PlanningTestFixtures.budget(20, 20, 0, 0, 3), partial.sessionId());

        var incompatible = planner(resume, store, new ToTParameters(5, 2, 1));

        assertThrows(PlannerCheckpointConflictException.class, () -> incompatible.plan(resume));
    }

    @Test
    void checkpointIntervalIsReflectedInIntermediateSessionRevisions() {
        PlanningRequest request = PlanningTestFixtures.request("tot-checkpoint-depth",
                PlanningTestFixtures.budget(20, 20, 0, 0, 3));
        var store = PlanningTestFixtures.sessionStore();
        var result = planner(request, store, new ToTParameters(8, 2, 1)).plan(request);
        var checkpoint = store.find(result.sessionId()).orElseThrow();

        assertEquals(PlannerSessionStatus.COMPLETED, checkpoint.status());
        assertTrue(checkpoint.revision() >= 2);
    }

    private static ThoughtSearchPlanner planner(PlanningRequest request,
                                                dev.supplychain.agent.planning.session.PlannerSessionStore store,
                                                ToTParameters parameters) {
        var worldModel = PlanningTestFixtures.worldModel();
        return new ThoughtSearchPlanner(worldModel, PlanningTestFixtures.evaluator(worldModel),
                PlanningTestFixtures.meter(PlanningTestFixtures.ledger(request.executionId(), request.budget())),
                store, parameters, PlanningTestFixtures.clock());
    }
}
