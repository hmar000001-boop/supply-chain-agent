package dev.supplychain.agent.runtime.cognitive;

import dev.supplychain.agent.mechanisms.b6.InMemoryResourceBudgetLedger;
import dev.supplychain.agent.mechanisms.b6.ResourceBudget;
import dev.supplychain.agent.mechanisms.b6.ResourceUnit;
import dev.supplychain.agent.mechanisms.b6.ResourceUsage;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResourceBudgetExecutionGuardTest {
    private static final Instant NOW = Instant.parse("2026-11-20T19:00:00Z");

    @Test
    void reservesBeforeWorkAndCommitsTrustedMeasuredUsage() {
        var ledger = new InMemoryResourceBudgetLedger();
        var budget = new ResourceBudget(Map.of(
                ResourceUnit.SEARCH_EXPANSIONS, 100L,
                ResourceUnit.REASONING_EVALUATIONS, 20L), NOW.plusSeconds(60));
        var guard = new ResourceBudgetExecutionGuard(ledger, ignored -> budget, Clock.fixed(NOW, ZoneOffset.UTC));

        String result = guard.execute("execution-measured", "planning:fixture-1",
                new ResourceUsage(Map.of(ResourceUnit.SEARCH_EXPANSIONS, 80L,
                        ResourceUnit.REASONING_EVALUATIONS, 15L)),
                () -> "complete",
                ignored -> new ResourceUsage(Map.of(ResourceUnit.SEARCH_EXPANSIONS, 12L,
                        ResourceUnit.REASONING_EVALUATIONS, 3L)));

        assertEquals("complete", result);
        var snapshot = ledger.snapshot("execution-measured", NOW);
        assertEquals(12L, snapshot.consumed().get(ResourceUnit.SEARCH_EXPANSIONS));
        assertEquals(3L, snapshot.consumed().get(ResourceUnit.REASONING_EVALUATIONS));
        assertEquals(0L, snapshot.reserved().getOrDefault(ResourceUnit.SEARCH_EXPANSIONS, 0L));
        assertEquals(88L, snapshot.remaining(ResourceUnit.SEARCH_EXPANSIONS));
    }

    @Test
    void failedStartedWorkIsConservativelyChargedAtItsReservation() {
        var ledger = new InMemoryResourceBudgetLedger();
        var budget = new ResourceBudget(Map.of(ResourceUnit.MODEL_CALLS, 4L), NOW.plusSeconds(60));
        var guard = new ResourceBudgetExecutionGuard(ledger, ignored -> budget, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThrows(IllegalStateException.class, () -> guard.execute("execution-failed", "model:attempt-1",
                ResourceUsage.of(ResourceUnit.MODEL_CALLS, 2L),
                () -> { throw new IllegalStateException("provider failed after invocation started"); },
                ignored -> ResourceUsage.of(ResourceUnit.MODEL_CALLS, 1L)));

        var snapshot = ledger.snapshot("execution-failed", NOW);
        assertEquals(2L, snapshot.consumed().get(ResourceUnit.MODEL_CALLS));
        assertEquals(0L, snapshot.reserved().getOrDefault(ResourceUnit.MODEL_CALLS, 0L));
    }

    @Test
    void invalidTrustedUsageMeasurementConservativelyChargesReservation() {
        var ledger = new InMemoryResourceBudgetLedger();
        var budget = new ResourceBudget(Map.of(ResourceUnit.MODEL_CALLS, 4L), NOW.plusSeconds(60));
        var guard = new ResourceBudgetExecutionGuard(ledger, ignored -> budget, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThrows(IllegalStateException.class, () -> guard.execute("execution-unmeasured", "model:attempt-1",
                ResourceUsage.of(ResourceUnit.MODEL_CALLS, 2L), () -> "provider returned",
                ignored -> { throw new IllegalStateException("trusted usage metadata is malformed"); }));

        var snapshot = ledger.snapshot("execution-unmeasured", NOW);
        assertEquals(2L, snapshot.consumed().get(ResourceUnit.MODEL_CALLS));
        assertEquals(0L, snapshot.reserved().getOrDefault(ResourceUnit.MODEL_CALLS, 0L));
    }

    @Test
    void sameExecutionCannotBeReboundToDifferentDurableLimits() {
        var ledger = new InMemoryResourceBudgetLedger();
        var first = new ResourceBudget(Map.of(ResourceUnit.MODEL_CALLS, 4L), NOW.plusSeconds(60));
        var guard = new ResourceBudgetExecutionGuard(ledger, ignored -> first, Clock.fixed(NOW, ZoneOffset.UTC));
        assertEquals(4L, guard.snapshot("execution-bound", NOW).remaining(ResourceUnit.MODEL_CALLS));

        var incompatible = new ResourceBudgetExecutionGuard(ledger,
                ignored -> new ResourceBudget(Map.of(ResourceUnit.MODEL_CALLS, 40L), NOW.plusSeconds(60)),
                Clock.fixed(NOW, ZoneOffset.UTC));
        assertThrows(SecurityException.class, () -> incompatible.snapshot("execution-bound", NOW));
    }
}
