package dev.supplychain.agent.planning.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PlannerBudgetTest {
    private static final Instant DEADLINE = Instant.parse("2026-11-21T18:00:00Z");

    @Test
    void b6DownshiftReducesOnlyConsumablePerCallLimitsAndPreservesTheHardDeadline() {
        var original = new PlannerBudget(5, 100, 80, 40, 20, 12,
                Duration.ofSeconds(10), DEADLINE);

        var reduced = original.scaleComputeResources(50);

        assertNotSame(original, reduced);
        assertEquals(5, reduced.maxDepth());
        assertEquals(50, reduced.maxExpansions());
        assertEquals(40, reduced.maxEvaluations());
        assertEquals(20, reduced.maxModelCalls());
        assertEquals(10, reduced.maxRollouts());
        assertEquals(12, reduced.maxCandidates());
        assertEquals(Duration.ofSeconds(5), reduced.maxRuntime());
        assertEquals(DEADLINE, reduced.deadline());
        assertEquals(100, original.maxExpansions(), "the immutable caller budget must not be changed in place");
        assertEquals(80, original.maxEvaluations());
        assertEquals(Duration.ofSeconds(10), original.maxRuntime());
    }

    @Test
    void runtimeScalingDoesNotOverflowForVeryLargeValidDurations() {
        var original = new PlannerBudget(2, 100, 100, 0, 0, 4,
                Duration.ofSeconds(Long.MAX_VALUE), DEADLINE);
        var reduced = original.scaleComputeResources(50);
        assertEquals(original.maxRuntime().dividedBy(2), reduced.maxRuntime());
    }

    @Test
    void scaleOneHundredPercentReturnsOriginalBudgetAndMinimumsNeverBecomeInvalid() {
        var original = new PlannerBudget(1, 1, 1, 0, 0, 1, Duration.ofMillis(2), DEADLINE);
        assertEquals(original, original.scaleComputeResources(100));
        var reduced = original.scaleComputeResources(50);
        assertEquals(1, reduced.maxExpansions());
        assertEquals(1, reduced.maxEvaluations());
        assertEquals(0, reduced.maxModelCalls());
        assertEquals(0, reduced.maxRollouts());
        assertEquals(Duration.ofMillis(1), reduced.maxRuntime());
        assertEquals(DEADLINE, reduced.deadline());
    }
}
