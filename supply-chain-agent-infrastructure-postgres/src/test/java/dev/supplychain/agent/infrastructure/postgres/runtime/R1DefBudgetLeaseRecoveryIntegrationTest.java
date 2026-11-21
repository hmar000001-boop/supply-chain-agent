package dev.supplychain.agent.infrastructure.postgres.runtime;

import dev.supplychain.agent.infrastructure.postgres.config.PostgresDataSourceFactory;
import dev.supplychain.agent.infrastructure.postgres.config.PostgresDatabaseConfig;
import dev.supplychain.agent.infrastructure.postgres.migration.PostgresMigrationRunner;
import dev.supplychain.agent.mechanisms.b6.BudgetReservationRecovery;
import dev.supplychain.agent.mechanisms.b6.BudgetReservationInUseException;
import dev.supplychain.agent.mechanisms.b6.ReservationStatus;
import dev.supplychain.agent.mechanisms.b6.ResourceBudget;
import dev.supplychain.agent.mechanisms.b6.ResourceBudgetExceededException;
import dev.supplychain.agent.mechanisms.b6.ResourceUnit;
import dev.supplychain.agent.mechanisms.b6.ResourceUsage;
import dev.supplychain.agent.runtime.cognitive.ResourceBudgetExecutionGuard;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "phase0.postgres", matches = "true")
class R1DefBudgetLeaseRecoveryIntegrationTest {
    private static DataSource dataSource;

    @BeforeAll static void initializeSchema() {
        dataSource = PostgresDataSourceFactory.create(PostgresDatabaseConfig.fromEnvironment(System.getenv()));
        assertEquals(15, new PostgresMigrationRunner(dataSource).migrate().totalMigrations());
    }

    @Test
    void expiredUnstartedReservationIsReclaimedWithANewFenceAndCanRunOnce() {
        String scope = scope("unstarted");
        String execution = "execution-" + UUID.randomUUID();
        Instant now = Instant.now().minusSeconds(5);
        ResourceBudget budget = new ResourceBudget(Map.of(ResourceUnit.MODEL_CALLS, 6L), now.plusSeconds(600));
        var first = new PostgresResourceBudgetLedger(dataSource, scope);
        first.register(execution, budget);
        ResourceUsage reserved = ResourceUsage.of(ResourceUnit.MODEL_CALLS, 2L);
        String operationId = "planner:checkpoint:17";
        String reservationId = ResourceBudgetExecutionGuard.reservationIdFor(execution, operationId);
        var firstLease = first.acquireLease(execution, reservationId, "process-one", reserved,
                now.plusSeconds(1), now);
        assertEquals(1L, firstLease.fencingToken());
        assertFalse(firstLease.operationStarted());

        Instant restartedAt = now.plusSeconds(2);
        var restarted = new PostgresResourceBudgetLedger(dataSource, scope);
        int recovered = restarted.recoverExpiredReservations(execution, restartedAt, 100);
        assertEquals(1, recovered);
        var released = restarted.findLease(execution, reservationId).orElseThrow();
        assertEquals(ReservationStatus.RELEASED, released.status());
        assertEquals(BudgetReservationRecovery.EXPIRED_BEFORE_START, released.recovery());
        assertNull(released.operationStartedAt());

        AtomicInteger calls = new AtomicInteger();
        var guard = new ResourceBudgetExecutionGuard(restarted, ignored -> budget,
                Clock.fixed(restartedAt, ZoneOffset.UTC), Duration.ofMinutes(1));
        String value = guard.execute(execution, operationId, reserved, () -> {
            calls.incrementAndGet(); return "planned";
        }, ignored -> ResourceUsage.of(ResourceUnit.MODEL_CALLS, 1L));

        assertEquals("planned", value);
        assertEquals(1, calls.get());
        var committed = restarted.findLease(execution, reservationId).orElseThrow();
        assertEquals(ReservationStatus.COMMITTED, committed.status());
        assertTrue(committed.fencingToken() > firstLease.fencingToken());
        assertNotEquals("process-one", committed.ownerId());
        assertEquals(1L, committed.actualUsage().amount(ResourceUnit.MODEL_CALLS));
        assertEquals(5L, restarted.snapshot(execution, restartedAt).remaining(ResourceUnit.MODEL_CALLS));
    }

    @Test
    void expiredStartedReservationIsFencedAndChargedConservativelyWithoutReplay() {
        String scope = scope("started");
        String execution = "execution-" + UUID.randomUUID();
        Instant now = Instant.now().minusSeconds(5);
        ResourceBudget budget = new ResourceBudget(Map.of(ResourceUnit.MODEL_CALLS, 8L,
                ResourceUnit.REASONING_EVALUATIONS, 20L), now.plusSeconds(600));
        var first = new PostgresResourceBudgetLedger(dataSource, scope);
        first.register(execution, budget);
        first.reserve(execution, "prior-usage", ResourceUsage.of(ResourceUnit.MODEL_CALLS, 2L), now);
        first.commit(execution, "prior-usage", ResourceUsage.of(ResourceUnit.MODEL_CALLS, 2L), now.plusMillis(1));
        ResourceUsage reserved = new ResourceUsage(Map.of(ResourceUnit.MODEL_CALLS, 2L,
                ResourceUnit.REASONING_EVALUATIONS, 1L));
        String operationId = "reasoning:correlation-42";
        String reservationId = ResourceBudgetExecutionGuard.reservationIdFor(execution, operationId);
        var acquired = first.acquireLease(execution, reservationId, "process-one", reserved,
                now.plusSeconds(1), now.plusMillis(2));
        var started = first.markOperationStarted(execution, reservationId, "process-one",
                acquired.fencingToken(), now.plusMillis(3));
        ResourceUsage otherReserved = new ResourceUsage(Map.of(ResourceUnit.MODEL_CALLS, 3L,
                ResourceUnit.REASONING_EVALUATIONS, 2L));
        String otherReservationId = ResourceBudgetExecutionGuard.reservationIdFor(execution, "other-live-operation");
        var otherLease = first.acquireLease(execution, otherReservationId, "process-two", otherReserved,
                now.plusSeconds(120), now.plusMillis(4));
        var otherStarted = first.markOperationStarted(execution, otherReservationId, "process-two",
                otherLease.fencingToken(), now.plusMillis(5));
        assertTrue(otherStarted.operationStarted(), "a live operation must be marked started before it can be committed");

        Instant recoveredAt = now.plusSeconds(2);
        var restarted = new PostgresResourceBudgetLedger(dataSource, scope);
        assertEquals(1, restarted.recoverExpiredReservations(execution, recoveredAt, 100));
        var recovered = restarted.findLease(execution, reservationId).orElseThrow();
        assertEquals(ReservationStatus.COMMITTED, recovered.status());
        assertEquals(BudgetReservationRecovery.EXPIRED_AFTER_START_CONSERVATIVELY_CHARGED, recovered.recovery());
        assertTrue(recovered.fencingToken() > started.fencingToken());
        assertEquals(3L, recovered.actualUsage().amount(ResourceUnit.MODEL_CALLS),
                "unknown work consumes all currently unheld finite capacity, preserving other live reservations");
        assertEquals(18L, recovered.actualUsage().amount(ResourceUnit.REASONING_EVALUATIONS));
        assertEquals(5L, restarted.snapshot(execution, recoveredAt).consumed().get(ResourceUnit.MODEL_CALLS));
        assertEquals(18L, restarted.snapshot(execution, recoveredAt).consumed().get(ResourceUnit.REASONING_EVALUATIONS));
        assertEquals(3L, restarted.snapshot(execution, recoveredAt).reserved().get(ResourceUnit.MODEL_CALLS));
        assertEquals(2L, restarted.snapshot(execution, recoveredAt).reserved().get(ResourceUnit.REASONING_EVALUATIONS));
        restarted.commitOwned(execution, otherReservationId, "process-two", otherStarted.fencingToken(),
                otherReserved, recoveredAt.plusMillis(1));
        assertEquals(8L, restarted.snapshot(execution, recoveredAt.plusMillis(1)).consumed().get(ResourceUnit.MODEL_CALLS));
        assertEquals(20L, restarted.snapshot(execution, recoveredAt.plusMillis(1)).consumed().get(ResourceUnit.REASONING_EVALUATIONS));

        AtomicInteger repeatedCalls = new AtomicInteger();
        var guard = new ResourceBudgetExecutionGuard(restarted, ignored -> budget,
                Clock.fixed(recoveredAt, ZoneOffset.UTC), Duration.ofMinutes(1));
        assertThrows(BudgetReservationInUseException.class, () -> guard.execute(execution, operationId,
                reserved, () -> { repeatedCalls.incrementAndGet(); return "duplicate"; }, ignored -> reserved));
        assertEquals(0, repeatedCalls.get(), "a started operation with unknown outcome must never be replayed");
        assertThrows(SecurityException.class, () -> restarted.commitOwned(execution, reservationId,
                "process-one", started.fencingToken(), reserved, recoveredAt));
        assertThrows(ResourceBudgetExceededException.class, () -> restarted.reserve(execution, "must-not-fit",
                ResourceUsage.of(ResourceUnit.MODEL_CALLS, 1L), recoveredAt.plusSeconds(1)));
    }

    @Test
    void legacyUnfencedLedgerMethodsCannotMutateAnOwnedReservation() {
        String scope = scope("legacy-fencing");
        String execution = "execution-" + UUID.randomUUID();
        Instant now = Instant.now().minusSeconds(5);
        ResourceBudget budget = new ResourceBudget(Map.of(ResourceUnit.MODEL_CALLS, 5L), now.plusSeconds(300));
        var ledger = new PostgresResourceBudgetLedger(dataSource, scope);
        ledger.register(execution, budget);
        String reservationId = "owned-reservation";
        ResourceUsage reserved = ResourceUsage.of(ResourceUnit.MODEL_CALLS, 2L);
        var lease = ledger.acquireLease(execution, reservationId, "owner-one", reserved,
                now.plusSeconds(120), now);
        ledger.markOperationStarted(execution, reservationId, "owner-one", lease.fencingToken(), now);

        assertThrows(SecurityException.class, () -> ledger.reserve(execution, reservationId, reserved, now));
        assertThrows(SecurityException.class, () -> ledger.commit(execution, reservationId, reserved, now));
        assertThrows(SecurityException.class, () -> ledger.release(execution, reservationId, now));

        var unchanged = ledger.findLease(execution, reservationId).orElseThrow();
        assertEquals(ReservationStatus.ACTIVE, unchanged.status());
        assertTrue(unchanged.operationStarted());
        assertEquals(lease.fencingToken(), unchanged.fencingToken());
        assertEquals(2L, ledger.snapshot(execution, now).reserved().get(ResourceUnit.MODEL_CALLS));
    }

    @Test
    void expiredUnstartedReservationsAreReclaimedBeforeStartedUnknownOutcomesAreCharged() {
        String scope = scope("recovery-order");
        String execution = "execution-" + UUID.randomUUID();
        Instant now = Instant.now().minusSeconds(5);
        ResourceBudget budget = new ResourceBudget(Map.of(ResourceUnit.MODEL_CALLS, 10L), now.plusSeconds(600));
        var ledger = new PostgresResourceBudgetLedger(dataSource, scope);
        ledger.register(execution, budget);
        Instant expiry = now.plusSeconds(1);
        ResourceUsage startedAmount = ResourceUsage.of(ResourceUnit.MODEL_CALLS, 2L);
        ResourceUsage unstartedAmount = ResourceUsage.of(ResourceUnit.MODEL_CALLS, 3L);

        // The started row sorts before the unstarted row by reservation ID. The recovery query
        // must prioritize expired unstarted reservations independently of that lexical order.
        var started = ledger.acquireLease(execution, "a-started", "owner-started", startedAmount,
                expiry, now);
        ledger.markOperationStarted(execution, "a-started", "owner-started", started.fencingToken(), now);
        var unstarted = ledger.acquireLease(execution, "z-unstarted", "owner-unstarted", unstartedAmount,
                expiry, now);

        Instant recoveredAt = now.plusSeconds(2);
        assertEquals(2, ledger.recoverExpiredReservations(execution, recoveredAt, 100));
        var recoveredStarted = ledger.findLease(execution, "a-started").orElseThrow();
        var recoveredUnstarted = ledger.findLease(execution, "z-unstarted").orElseThrow();
        assertEquals(ReservationStatus.COMMITTED, recoveredStarted.status());
        assertEquals(BudgetReservationRecovery.EXPIRED_AFTER_START_CONSERVATIVELY_CHARGED,
                recoveredStarted.recovery());
        assertEquals(10L, recoveredStarted.actualUsage().amount(ResourceUnit.MODEL_CALLS),
                "the expired unstarted reservation must be released before computing unknown started usage");
        assertEquals(ReservationStatus.RELEASED, recoveredUnstarted.status());
        assertEquals(BudgetReservationRecovery.EXPIRED_BEFORE_START, recoveredUnstarted.recovery());
        assertTrue(recoveredUnstarted.fencingToken() > unstarted.fencingToken());
        assertEquals(10L, ledger.snapshot(execution, recoveredAt).consumed().get(ResourceUnit.MODEL_CALLS));
        assertEquals(0L, ledger.snapshot(execution, recoveredAt).reserved().getOrDefault(ResourceUnit.MODEL_CALLS, 0L));
    }

    @Test
    void concurrentLiveOwnerCannotAcquireTheSameOperationLease() {
        String scope = scope("concurrent");
        String execution = "execution-" + UUID.randomUUID();
        Instant now = Instant.now().minusSeconds(5);
        ResourceBudget budget = new ResourceBudget(Map.of(ResourceUnit.MODEL_CALLS, 4L), now.plusSeconds(300));
        var first = new PostgresResourceBudgetLedger(dataSource, scope);
        first.register(execution, budget);
        String reservation = ResourceBudgetExecutionGuard.reservationIdFor(execution, "operation:unique");
        first.acquireLease(execution, reservation, "host-a", ResourceUsage.of(ResourceUnit.MODEL_CALLS, 1L),
                now.plusSeconds(120), now);
        var second = new ResourceBudgetExecutionGuard(new PostgresResourceBudgetLedger(dataSource, scope),
                ignored -> budget, Clock.fixed(now, ZoneOffset.UTC), Duration.ofMinutes(1));
        assertThrows(BudgetReservationInUseException.class, () -> second.execute(execution, "operation:unique",
                ResourceUsage.of(ResourceUnit.MODEL_CALLS, 1L), () -> "must-not-run", ignored -> ResourceUsage.of(ResourceUnit.MODEL_CALLS, 1L)));
    }

    @Test
    void abruptJvmDeathAtBothSidesOfOperationStartUsesDistinctRecoveryRules() throws Exception {
        verifyForkedCrash("reserved");
        verifyForkedCrash("started");
    }

    private void verifyForkedCrash(String mode) throws Exception {
        String scope = scope("forked-" + mode);
        String execution = "execution-" + UUID.randomUUID();
        Instant base = Instant.now().minusSeconds(10).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        Instant leaseUntil = base.plusSeconds(1);
        ResourceBudget budget = new ResourceBudget(Map.of(ResourceUnit.MODEL_CALLS, 7L,
                ResourceUnit.REASONING_EVALUATIONS, 3L), base.plusSeconds(600));
        new PostgresResourceBudgetLedger(dataSource, scope).register(execution, budget);
        String operation = "forked-operation:" + mode;
        String reservation = ResourceBudgetExecutionGuard.reservationIdFor(execution, operation);
        runChild(mode, scope, execution, reservation, "process-one-" + mode, base, leaseUntil);

        Instant recoveryAt = base.plusSeconds(2);
        var restarted = new PostgresResourceBudgetLedger(dataSource, scope);
        assertEquals(1, restarted.recoverExpiredReservations(execution, recoveryAt, 100));
        var result = restarted.findLease(execution, reservation).orElseThrow();
        if ("reserved".equals(mode)) {
            assertEquals(ReservationStatus.RELEASED, result.status());
            assertEquals(BudgetReservationRecovery.EXPIRED_BEFORE_START, result.recovery());
            var guard = new ResourceBudgetExecutionGuard(restarted, ignored -> budget,
                    Clock.fixed(recoveryAt, ZoneOffset.UTC), Duration.ofMinutes(1));
            AtomicInteger invocation = new AtomicInteger();
            guard.execute(execution, operation, new ResourceUsage(Map.of(ResourceUnit.MODEL_CALLS, 2L,
                    ResourceUnit.REASONING_EVALUATIONS, 1L)), () -> { invocation.incrementAndGet(); return "once"; }, ignored -> new ResourceUsage(Map.of(ResourceUnit.MODEL_CALLS, 1L)));
            assertEquals(1, invocation.get());
        } else {
            assertEquals(ReservationStatus.COMMITTED, result.status());
            assertEquals(BudgetReservationRecovery.EXPIRED_AFTER_START_CONSERVATIVELY_CHARGED, result.recovery());
            assertEquals(7L, restarted.snapshot(execution, recoveryAt).consumed().get(ResourceUnit.MODEL_CALLS));
            var guard = new ResourceBudgetExecutionGuard(restarted, ignored -> budget,
                    Clock.fixed(recoveryAt, ZoneOffset.UTC), Duration.ofMinutes(1));
            AtomicInteger invocation = new AtomicInteger();
            assertThrows(BudgetReservationInUseException.class, () -> guard.execute(execution, operation,
                    new ResourceUsage(Map.of(ResourceUnit.MODEL_CALLS, 2L,
                            ResourceUnit.REASONING_EVALUATIONS, 1L)),
                    () -> { invocation.incrementAndGet(); return "must-not-repeat"; }, ignored -> ResourceUsage.of(ResourceUnit.MODEL_CALLS, 1L)));
            assertEquals(0, invocation.get());
        }
    }

    private static void runChild(String mode, String scope, String execution, String reservation,
                                 String owner, Instant base, Instant leaseUntil) throws Exception {
        String classpath = System.getProperty("surefire.test.class.path");
        if (classpath == null || classpath.isBlank()) classpath = System.getProperty("java.class.path");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Path log = Files.createTempFile("r1def-budget-" + mode + "-", ".log");
        Process process = new ProcessBuilder(java, "-cp", classpath, R1DefBudgetLeaseForkedProbe.class.getName(),
                mode, scope, execution, reservation, owner, base.toString(), leaseUntil.toString())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "forked reservation owner did not terminate");
            String output = Files.readString(log, StandardCharsets.UTF_8);
            assertEquals(0, process.exitValue(), "forked reservation owner failed:\n" + output);
            assertTrue(output.contains("R1DEF_LEASE_HELD mode=" + mode), output);
        } finally {
            if (process.isAlive()) process.destroyForcibly();
            Files.deleteIfExists(log);
        }
    }

    private static String scope(String suffix) { return "r1def-" + suffix + "-" + UUID.randomUUID(); }
}
