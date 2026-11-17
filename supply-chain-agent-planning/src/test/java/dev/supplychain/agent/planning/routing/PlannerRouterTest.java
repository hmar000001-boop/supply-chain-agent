package dev.supplychain.agent.planning.routing;

import static org.junit.jupiter.api.Assertions.*;

import dev.supplychain.agent.planning.PlanningTestFixtures;
import dev.supplychain.agent.planning.api.PlannerId;
import dev.supplychain.agent.planning.api.PlanningRequest;
import dev.supplychain.agent.planning.api.PlanningResult;
import dev.supplychain.agent.planning.api.PlanningStatistics;
import dev.supplychain.agent.planning.api.PlanningStrategy;
import dev.supplychain.agent.planning.api.PlannerTerminationReason;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class PlannerRouterTest {
    @Test
    void defaultPolicySelectsSequentialForLowComplexity() {
        var budget = PlanningTestFixtures.budget(20, 20, 0, 10, 4);
        var policy = new DefaultPlannerSelectionPolicy();
        var selection = policy.select(new dev.supplychain.agent.planning.api.PlanningComplexity(2, 2, 0.10, 0.30), budget);
        assertEquals(PlannerId.COT_SEQUENTIAL, selection.plannerId());
    }

    @Test
    void defaultPolicySelectsTotForModerateComplexity() {
        var budget = PlanningTestFixtures.budget(20, 20, 0, 10, 4);
        var policy = new DefaultPlannerSelectionPolicy();
        var selection = policy.select(new dev.supplychain.agent.planning.api.PlanningComplexity(4, 4, 0.60, 0.60), budget);
        assertEquals(PlannerId.TOT_BOUNDED_SEARCH, selection.plannerId());
    }

    @Test
    void defaultPolicySelectsMctsForHigherComplexityWhenRolloutBudgetExists() {
        var budget = PlanningTestFixtures.budget(20, 20, 0, 10, 4);
        var policy = new DefaultPlannerSelectionPolicy();
        var selection = policy.select(new dev.supplychain.agent.planning.api.PlanningComplexity(10, 8, 0.80, 0.90), budget);
        assertEquals(PlannerId.RAP_INSPIRED_MCTS, selection.plannerId());
    }

    @Test
    void defaultPolicyFallsBackToTotWhenNoMctsRolloutBudgetExists() {
        var budget = PlanningTestFixtures.budget(20, 20, 0, 0, 4);
        var policy = new DefaultPlannerSelectionPolicy();
        var selection = policy.select(new dev.supplychain.agent.planning.api.PlanningComplexity(10, 8, 0.80, 0.90), budget);
        assertEquals(PlannerId.TOT_BOUNDED_SEARCH, selection.plannerId());
    }

    @Test
    void routesToSelectedStrategyAndPreservesExplanation() {
        PlanningRequest request = PlanningTestFixtures.request("router-route",
                PlanningTestFixtures.budget(20, 20, 0, 10, 4));
        var invoked = new ArrayList<PlannerId>();
        var router = new PlannerRouter(fakeStrategies(invoked), new DefaultPlannerSelectionPolicy());

        var result = router.plan(request);

        assertEquals(PlannerId.COT_SEQUENTIAL, result.plannerId());
        assertEquals(List.of(PlannerId.COT_SEQUENTIAL), invoked);
        assertTrue(result.selectionExplanation().contains("Low branching"));
    }

    @Test
    void explicitPlannerOverrideIsVisibleInResult() {
        PlanningRequest request = PlanningTestFixtures.request("router-override",
                PlanningTestFixtures.budget(20, 20, 0, 10, 4));
        var invoked = new ArrayList<PlannerId>();
        var router = new PlannerRouter(fakeStrategies(invoked), new DefaultPlannerSelectionPolicy());

        var result = router.planUsing(new PlannerRouter.PlanningIdOverride(
                PlannerId.RAP_INSPIRED_MCTS, "controlled algorithm comparison"), request);

        assertEquals(PlannerId.RAP_INSPIRED_MCTS, result.plannerId());
        assertEquals(List.of(PlannerId.RAP_INSPIRED_MCTS), invoked);
        assertTrue(result.selectionExplanation().contains("controlled algorithm comparison"));
    }

    @Test
    void rejectsMissingOrDuplicateStrategies() {
        var strategies = fakeStrategies(new ArrayList<>());
        assertThrows(IllegalArgumentException.class,
                () -> new PlannerRouter(strategies.subList(0, 2), new DefaultPlannerSelectionPolicy()));
        var duplicate = new ArrayList<>(strategies);
        duplicate.add(strategies.get(0));
        assertThrows(IllegalArgumentException.class,
                () -> new PlannerRouter(duplicate, new DefaultPlannerSelectionPolicy()));
    }

    private static List<PlanningStrategy> fakeStrategies(List<PlannerId> invoked) {
        return List.of(strategy(PlannerId.COT_SEQUENTIAL, invoked),
                strategy(PlannerId.TOT_BOUNDED_SEARCH, invoked),
                strategy(PlannerId.RAP_INSPIRED_MCTS, invoked));
    }

    private static PlanningStrategy strategy(PlannerId id, List<PlannerId> invoked) {
        return new PlanningStrategy() {
            @Override public PlannerId id() { return id; }
            @Override public PlanningResult plan(PlanningRequest request) {
                invoked.add(id);
                return new PlanningResult(id, List.of(), PlannerTerminationReason.NO_AVAILABLE_ACTIONS,
                        new PlanningStatistics(0, 0, 0, 0, 0, 0, Duration.ZERO),
                        "test-session-" + id, "test strategy", List.of());
            }
        };
    }
}
