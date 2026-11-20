package dev.supplychain.agent.runtime.cognitive;

import dev.supplychain.agent.mechanisms.b6.InMemoryResourceBudgetLedger;
import dev.supplychain.agent.mechanisms.b6.ReservationStatus;
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
    @Test
    void committedOperationIdentityCannotBeInvokedAgain() {
        var ledger = new InMemoryResourceBudgetLedger();
        var budget = new ResourceBudget(Map.of(ResourceUnit.MODEL_CALLS, 5L), NOW.plusSeconds(60));
        var guard = new ResourceBudgetExecutionGuard(ledger, ignored -> budget, Clock.fixed(NOW, ZoneOffset.UTC));
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();

        guard.execute("execution-idempotent", "reasoning:correlation-1",
                ResourceUsage.of(ResourceUnit.MODEL_CALLS, 2L), () -> {
                    calls.incrementAndGet(); return "first";
                }, ignored -> ResourceUsage.of(ResourceUnit.MODEL_CALLS, 1L));

        assertThrows(dev.supplychain.agent.mechanisms.b6.BudgetReservationInUseException.class,
                () -> guard.execute("execution-idempotent", "reasoning:correlation-1",
                        ResourceUsage.of(ResourceUnit.MODEL_CALLS, 2L), () -> {
                            calls.incrementAndGet(); return "duplicate";
                        }, ignored -> ResourceUsage.of(ResourceUnit.MODEL_CALLS, 1L)));
        assertEquals(1, calls.get(), "stable operation identity must not repeat a committed provider invocation");
    }

    @Test
    void expiredStartedReservationChargesOnlyCapacityNotHeldByOtherLiveReservations() {
        var ledger = new InMemoryResourceBudgetLedger();
        var budget = new ResourceBudget(Map.of(ResourceUnit.MODEL_CALLS, 10L), NOW.plusSeconds(600));
        ledger.register("execution-shared", budget);
        var first = ledger.acquireLease("execution-shared", "operation-first", "process-one",
                ResourceUsage.of(ResourceUnit.MODEL_CALLS, 2L), NOW.plusSeconds(1), NOW);
        ledger.markOperationStarted("execution-shared", "operation-first", "process-one", first.fencingToken(), NOW);
        var second = ledger.acquireLease("execution-shared", "operation-second", "process-two",
                ResourceUsage.of(ResourceUnit.MODEL_CALLS, 3L), NOW.plusSeconds(120), NOW);

        assertEquals(1, ledger.recoverExpiredReservations("execution-shared", NOW.plusSeconds(2), 100));
        var recovered = ledger.findLease("execution-shared", "operation-first").orElseThrow();
        assertEquals(7L, recovered.actualUsage().amount(ResourceUnit.MODEL_CALLS),
                "unknown started work is charged the remaining capacity after preserving other live holds");
        var interim = ledger.snapshot("execution-shared", NOW.plusSeconds(2));
        assertEquals(7L, interim.consumed().get(ResourceUnit.MODEL_CALLS));
        assertEquals(3L, interim.reserved().get(ResourceUnit.MODEL_CALLS));
        ledger.markOperationStarted("execution-shared", "operation-second", "process-two",
                second.fencingToken(), NOW.plusSeconds(2));
        ledger.commitOwned("execution-shared", "operation-second", "process-two", second.fencingToken(),
                ResourceUsage.of(ResourceUnit.MODEL_CALLS, 3L), NOW.plusSeconds(3));
        assertEquals(10L, ledger.snapshot("execution-shared", NOW.plusSeconds(3)).consumed().get(ResourceUnit.MODEL_CALLS));
    }

    @Test
    void expiredUnstartedReservationsAreReleasedBeforeUnknownStartedWorkIsCharged() {
        var ledger = new InMemoryResourceBudgetLedger();
        var budget = new ResourceBudget(Map.of(ResourceUnit.MODEL_CALLS, 10L), NOW.plusSeconds(600));
        ledger.register("execution-recovery-order", budget);
        Instant expiry = NOW.plusSeconds(1);

        // The started reservation sorts first by ID. Recovery must still release the expired
        // unstarted lease before calculating the conservative charge for unknown started work.
        var started = ledger.acquireLease("execution-recovery-order", "a-started", "owner-started",
                ResourceUsage.of(ResourceUnit.MODEL_CALLS, 2L), expiry, NOW);
        ledger.markOperationStarted("execution-recovery-order", "a-started", "owner-started",
                started.fencingToken(), NOW);
        var unstarted = ledger.acquireLease("execution-recovery-order", "z-unstarted", "owner-unstarted",
                ResourceUsage.of(ResourceUnit.MODEL_CALLS, 3L), expiry, NOW);

        assertEquals(2, ledger.recoverExpiredReservations("execution-recovery-order", NOW.plusSeconds(2), 10));
        var recoveredStarted = ledger.findLease("execution-recovery-order", "a-started").orElseThrow();
        var recoveredUnstarted = ledger.findLease("execution-recovery-order", "z-unstarted").orElseThrow();
        assertEquals(ReservationStatus.COMMITTED, recoveredStarted.status());
        assertEquals(10L, recoveredStarted.actualUsage().amount(ResourceUnit.MODEL_CALLS),
                "unknown started work must be charged against capacity not held by live owners; expired unstarted leases are not live holds");
        assertEquals(ReservationStatus.RELEASED, recoveredUnstarted.status());
        assertEquals(dev.supplychain.agent.mechanisms.b6.BudgetReservationRecovery.EXPIRED_BEFORE_START,
                recoveredUnstarted.recovery());
        assertTrue(recoveredUnstarted.fencingToken() > unstarted.fencingToken());
        var snapshot = ledger.snapshot("execution-recovery-order", NOW.plusSeconds(2));
        assertEquals(10L, snapshot.consumed().get(ResourceUnit.MODEL_CALLS));
        assertEquals(0L, snapshot.reserved().getOrDefault(ResourceUnit.MODEL_CALLS, 0L));
    }

    @Test
    void fatalProviderErrorsStillCommitTheReservationAndPreventASecondInvocation() {
        var ledger = new InMemoryResourceBudgetLedger();
        var budget = new ResourceBudget(Map.of(ResourceUnit.MODEL_CALLS, 4L), NOW.plusSeconds(60));
        var guard = new ResourceBudgetExecutionGuard(ledger, ignored -> budget, Clock.fixed(NOW, ZoneOffset.UTC));
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        String operationId = "provider:fatal-error";

        assertThrows(AssertionError.class, () -> guard.execute("execution-fatal", operationId,
                ResourceUsage.of(ResourceUnit.MODEL_CALLS, 2L), () -> {
                    calls.incrementAndGet();
                    throw new AssertionError("provider implementation failed after start");
                }, ignored -> ResourceUsage.of(ResourceUnit.MODEL_CALLS, 1L)));

        var lease = ledger.findLease("execution-fatal",
                ResourceBudgetExecutionGuard.reservationIdFor("execution-fatal", operationId)).orElseThrow();
        assertEquals(dev.supplychain.agent.mechanisms.b6.ReservationStatus.COMMITTED, lease.status());
        assertEquals(2L, lease.actualUsage().amount(ResourceUnit.MODEL_CALLS));
        assertThrows(dev.supplychain.agent.mechanisms.b6.BudgetReservationInUseException.class,
                () -> guard.execute("execution-fatal", operationId,
                        ResourceUsage.of(ResourceUnit.MODEL_CALLS, 2L), () -> {
                            calls.incrementAndGet(); return "duplicate";
                        }, ignored -> ResourceUsage.of(ResourceUnit.MODEL_CALLS, 1L)));
        assertEquals(1, calls.get(), "a fatal provider error must not leave an unaccounted, replayable reservation");
    }

    @Test
    void explicitlyReleasedUnstartedLeaseIsFencedAndCannotBeReacquired() {
        var ledger = new InMemoryResourceBudgetLedger();
        var budget = new ResourceBudget(Map.of(ResourceUnit.MODEL_CALLS, 4L), NOW.plusSeconds(60));
        ledger.register("execution-released", budget);
        String reservationId = "reservation-explicit-release";
        var lease = ledger.acquireLease("execution-released", reservationId, "owner-original",
                ResourceUsage.of(ResourceUnit.MODEL_CALLS, 1L), NOW.plusSeconds(30), NOW);

        ledger.releaseOwnedBeforeStart("execution-released", reservationId, "owner-original",
                lease.fencingToken(), NOW.plusSeconds(1));

        var released = ledger.findLease("execution-released", reservationId).orElseThrow();
        assertEquals(dev.supplychain.agent.mechanisms.b6.ReservationStatus.RELEASED, released.status());
        assertEquals(dev.supplychain.agent.mechanisms.b6.BudgetReservationRecovery.EXPLICITLY_RELEASED, released.recovery());
        assertTrue(released.fencingToken() > lease.fencingToken());
        assertThrows(SecurityException.class, () -> ledger.markOperationStarted("execution-released", reservationId,
                "owner-original", lease.fencingToken(), NOW.plusSeconds(2)));
        assertThrows(IllegalStateException.class, () -> ledger.acquireLease("execution-released", reservationId,
                "owner-replacement", ResourceUsage.of(ResourceUnit.MODEL_CALLS, 1L), NOW.plusSeconds(30), NOW.plusSeconds(2)));
    }

}
