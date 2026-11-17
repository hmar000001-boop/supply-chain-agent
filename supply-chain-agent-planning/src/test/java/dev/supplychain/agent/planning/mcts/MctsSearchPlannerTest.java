package dev.supplychain.agent.planning.mcts;

import static org.junit.jupiter.api.Assertions.*;

import dev.supplychain.agent.planning.PlanningTestFixtures;
import dev.supplychain.agent.planning.api.PlannerId;
import dev.supplychain.agent.planning.api.PlannerTerminationReason;
import dev.supplychain.agent.planning.api.PlanningRequest;
import dev.supplychain.agent.planning.api.PlanningWorldModel;
import dev.supplychain.agent.planning.api.WorldTransition;
import dev.supplychain.agent.planning.session.PlannerSessionStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

final class MctsSearchPlannerTest {
    @Test
    void runsSeededMctsWithBoundedRolloutsAndTraceStatistics() {
        PlanningRequest request = PlanningTestFixtures.request("mcts-basic",
                PlanningTestFixtures.budget(30, 20, 0, 10, 3));
        var store = PlanningTestFixtures.sessionStore();
        var result = planner(request, store, new MctsParameters(1.41421356237, 2, 2, 41L)).plan(request);

        assertEquals(PlannerId.RAP_INSPIRED_MCTS, result.plannerId());
        assertEquals(10, result.statistics().rollouts());
        assertTrue(result.statistics().expansions() <= 30);
        assertTrue(result.statistics().evaluations() <= 20);
        assertFalse(result.candidates().isEmpty());
        assertTrue(result.trace().stream().anyMatch(node -> node.visits() > 0));
        assertEquals(PlannerSessionStatus.COMPLETED, store.find(result.sessionId()).orElseThrow().status());
        assertTrue(result.selectionExplanation().contains("not a faithful RAP"));
    }

    @Test
    void seededSearchIsDeterministicForIdenticalInputsAndConfiguration() {
        PlanningRequest requestA = PlanningTestFixtures.request("mcts-deterministic",
                PlanningTestFixtures.budget(30, 20, 0, 8, 3));
        PlanningRequest requestB = PlanningTestFixtures.request("mcts-deterministic",
                PlanningTestFixtures.budget(30, 20, 0, 8, 3));
        var parameters = new MctsParameters(1.25, 2, 2, 9981L);
        var first = planner(requestA, PlanningTestFixtures.sessionStore(), parameters).plan(requestA);
        var second = planner(requestB, PlanningTestFixtures.sessionStore(), parameters).plan(requestB);

        assertEquals(first.candidates().stream().map(c -> c.candidateId()).toList(),
                second.candidates().stream().map(c -> c.candidateId()).toList());
        assertEquals(first.trace().stream().map(n -> n.visits()).toList(),
                second.trace().stream().map(n -> n.visits()).toList());
    }

    @Test
    void retainsBestCandidatesAndTreeWhenBudgetStopsAndResumes() {
        PlanningRequest initial = PlanningTestFixtures.request("mcts-resume",
                PlanningTestFixtures.budget(1, 10, 0, 5, 3));
        var store = PlanningTestFixtures.sessionStore();
        var parameters = new MctsParameters(1.41421356237, 2, 2, 41L);
        var partial = planner(initial, store, parameters).plan(initial);

        assertEquals(PlannerTerminationReason.SEARCH_BUDGET_EXHAUSTED, partial.terminationReason());
        var checkpoint = store.find(partial.sessionId()).orElseThrow();
        assertEquals(PlannerSessionStatus.ACTIVE, checkpoint.status());
        assertFalse(checkpoint.frontier().isEmpty());
        assertFalse(checkpoint.treeNodes().isEmpty());

        PlanningRequest resumed = PlanningTestFixtures.withBudgetAndResume(initial,
                PlanningTestFixtures.budget(30, 30, 0, 8, 3), partial.sessionId());
        var completed = planner(resumed, store, parameters).plan(resumed);

        assertEquals(PlannerTerminationReason.COMPLETED, completed.terminationReason());
        assertTrue(completed.statistics().rollouts() > partial.statistics().rollouts());
        assertTrue(completed.statistics().elapsed().compareTo(partial.statistics().elapsed()) >= 0);
        assertFalse(completed.candidates().isEmpty());
        assertEquals(PlannerSessionStatus.COMPLETED, store.find(partial.sessionId()).orElseThrow().status());
    }

    @Test
    void reportsNoAvailableActionsWithoutSpendingRolloutBudget() {
        PlanningRequest request = PlanningTestFixtures.request("mcts-no-actions",
                PlanningTestFixtures.budget(10, 10, 0, 5, 3));
        PlanningWorldModel empty = new PlanningWorldModel() {
            private final PlanningWorldModel delegate = PlanningTestFixtures.worldModel();
            @Override public String configurationFingerprint() { return "empty-world-model-test-v1"; }
            @Override public dev.supplychain.agent.planning.api.PlanningState initialState(PlanningRequest input) {
                return delegate.initialState(input);
            }
            @Override public List<dev.supplychain.agent.planning.api.PlanningAction> availableActions(
                    PlanningRequest input, dev.supplychain.agent.planning.api.PlanningState state) { return List.of(); }
            @Override public WorldTransition apply(PlanningRequest input,
                    dev.supplychain.agent.planning.api.PlanningState state,
                    dev.supplychain.agent.planning.api.PlanningAction action) {
                return new WorldTransition.Rejected("TEST", "no actions");
            }
        };
        var result = new MctsSearchPlanner(empty, PlanningTestFixtures.evaluator(empty),
                PlanningTestFixtures.meter(PlanningTestFixtures.ledger(request.executionId(), request.budget())),
                PlanningTestFixtures.sessionStore(), new MctsParameters(1.0, 1, 1, 1L),
                PlanningTestFixtures.clock()).plan(request);

        assertEquals(PlannerTerminationReason.NO_AVAILABLE_ACTIONS, result.terminationReason());
        assertEquals(0, result.statistics().rollouts());
        assertTrue(result.candidates().isEmpty());
    }

    private static MctsSearchPlanner planner(PlanningRequest request,
                                             dev.supplychain.agent.planning.session.PlannerSessionStore store,
                                             MctsParameters parameters) {
        var worldModel = PlanningTestFixtures.worldModel();
        return new MctsSearchPlanner(worldModel, PlanningTestFixtures.evaluator(worldModel),
                PlanningTestFixtures.meter(PlanningTestFixtures.ledger(request.executionId(), request.budget())),
                store, parameters, PlanningTestFixtures.clock());
    }
}
