package dev.supplychain.agent.runtime.cognitive;

import dev.supplychain.agent.mechanisms.b6.BudgetReservationInUseException;
import dev.supplychain.agent.mechanisms.b6.BudgetReservationLease;
import dev.supplychain.agent.mechanisms.b6.LeasedResourceBudgetLedger;
import dev.supplychain.agent.mechanisms.b6.ResourceBudget;
import dev.supplychain.agent.mechanisms.b6.ResourceBudgetLedger;
import dev.supplychain.agent.mechanisms.b6.ResourceBudgetSnapshot;
import dev.supplychain.agent.mechanisms.b6.ResourceUsage;
import dev.supplychain.agent.mechanisms.b6.ReservationStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Shared, fenced B6 execution boundary for M5 planning and M3 reasoning. Each operation has a
 * deterministic reservation identity, a process-local owner identity and a monotonically increasing
 * fencing token. The operation-start transition is committed before the provider is invoked.
 * An expired unstarted reservation can be reclaimed; an expired started reservation is never
 * replayed and is conservatively charged by the durable ledger.
 */
public final class ResourceBudgetExecutionGuard {
    private static final ScheduledExecutorService HEARTBEATS = Executors.newScheduledThreadPool(4, task -> {
        Thread thread = new Thread(task, "b6-budget-lease-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    private final ResourceBudgetLedger ledger;
    private final LeasedResourceBudgetLedger leasedLedger;
    private final Function<String, ResourceBudget> budgetProvider;
    private final Clock clock;
    private final Duration leaseDuration;
    private final String ownerId = UUID.randomUUID().toString();

    public ResourceBudgetExecutionGuard(ResourceBudgetLedger ledger,
                                        Function<String, ResourceBudget> budgetProvider,
                                        Clock clock) {
        this(ledger, budgetProvider, clock, Duration.ofMinutes(2));
    }

    public ResourceBudgetExecutionGuard(ResourceBudgetLedger ledger,
                                        Function<String, ResourceBudget> budgetProvider,
                                        Clock clock,
                                        Duration leaseDuration) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        if (!(ledger instanceof LeasedResourceBudgetLedger leased)) {
            throw new IllegalArgumentException("B6 production execution requires a leased, fencing-capable resource ledger");
        }
        this.leasedLedger = leased;
        this.budgetProvider = Objects.requireNonNull(budgetProvider, "budgetProvider");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.leaseDuration = Objects.requireNonNull(leaseDuration, "leaseDuration");
        if (leaseDuration.toMillis() < 300L) {
            throw new IllegalArgumentException("leaseDuration must be at least 300 milliseconds so heartbeat renewal can run before expiry");
        }
    }

    public ResourceBudgetSnapshot snapshot(String executionId, Instant at) {
        String execution = required(executionId, "executionId");
        ResourceBudget budget = Objects.requireNonNull(budgetProvider.apply(execution),
                "host returned no B6 budget for execution");
        ledger.ensureRegistered(execution, budget);
        return ledger.snapshot(execution, Objects.requireNonNull(at, "at"));
    }

    public <T> T execute(String executionId, String operationId, ResourceUsage reserved,
                         Supplier<T> operation, Function<T, ResourceUsage> actualUsage) {
        String execution = required(executionId, "executionId");
        String operationKey = required(operationId, "operationId");
        Objects.requireNonNull(reserved, "reserved");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(actualUsage, "actualUsage");

        ResourceBudget budget = Objects.requireNonNull(budgetProvider.apply(execution),
                "host returned no B6 budget for execution");
        ledger.ensureRegistered(execution, budget);
        Instant acquiredAt = clock.instant();
        String reservationId = reservationIdFor(execution, operationKey);
        BudgetReservationLease lease = leasedLedger.acquireLease(execution, reservationId, ownerId, reserved,
                acquiredAt.plus(leaseDuration), acquiredAt);
        if (lease.status() == ReservationStatus.COMMITTED) {
            throw new BudgetReservationInUseException("budgeted operation was already completed or conservatively reconciled; refusing to invoke it again");
        }
        if (lease.status() != ReservationStatus.ACTIVE || !ownerId.equals(lease.ownerId()) || lease.operationStarted()) {
            throw new BudgetReservationInUseException("budgeted operation could not obtain an unstarted reservation lease");
        }

        lease = leasedLedger.markOperationStarted(execution, reservationId, ownerId,
                lease.fencingToken(), clock.instant());
        long fencingToken = lease.fencingToken();
        long heartbeatMillis = Math.max(1L, leaseDuration.toMillis() / 3L);
        AtomicReference<RuntimeException> heartbeatFailure = new AtomicReference<>();
        ScheduledFuture<?> heartbeat = HEARTBEATS.scheduleWithFixedDelay(() -> {
            try {
                Instant now = clock.instant();
                leasedLedger.renewLease(execution, reservationId, ownerId, fencingToken,
                        now.plus(leaseDuration), now);
            } catch (RuntimeException failure) {
                heartbeatFailure.compareAndSet(null, failure);
            }
        }, heartbeatMillis, heartbeatMillis, TimeUnit.MILLISECONDS);

        final T result;
        try {
            result = Objects.requireNonNull(operation.get(), "budgeted operation returned null");
        } catch (RuntimeException | Error operationFailure) {
            try {
                leasedLedger.commitOwned(execution, reservationId, ownerId, fencingToken, reserved, clock.instant());
            } catch (RuntimeException | Error accountingFailure) {
                operationFailure.addSuppressed(accountingFailure);
                RuntimeException heartbeatProblem = heartbeatFailure.get();
                if (heartbeatProblem != null) operationFailure.addSuppressed(heartbeatProblem);
            } finally {
                heartbeat.cancel(false);
            }
            throw operationFailure;
        }

        final ResourceUsage actual;
        try {
            actual = Objects.requireNonNull(actualUsage.apply(result), "actual usage measurement");
        } catch (RuntimeException | Error measurementFailure) {
            try {
                leasedLedger.commitOwned(execution, reservationId, ownerId, fencingToken, reserved, clock.instant());
            } catch (RuntimeException | Error accountingFailure) {
                measurementFailure.addSuppressed(accountingFailure);
                RuntimeException heartbeatProblem = heartbeatFailure.get();
                if (heartbeatProblem != null) measurementFailure.addSuppressed(heartbeatProblem);
            } finally {
                heartbeat.cancel(false);
            }
            throw measurementFailure;
        }

        try {
            leasedLedger.commitOwned(execution, reservationId, ownerId, fencingToken, actual, clock.instant());
        } finally {
            heartbeat.cancel(false);
        }
        return result;
    }

    /** Stable identity used by guarded operation adapters and crash-recovery tests. */
    public static String reservationIdFor(String executionId, String operationId) {
        String execution = required(executionId, "executionId");
        String operation = required(operationId, "operationId");
        return "b6-op-v1-" + sha256(execution.length() + ":" + execution + "|" + operation.length() + ":" + operation);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", impossible);
        }
    }

    private static String required(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
