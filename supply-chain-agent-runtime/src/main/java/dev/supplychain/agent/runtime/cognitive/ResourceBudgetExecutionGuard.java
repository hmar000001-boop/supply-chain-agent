package dev.supplychain.agent.runtime.cognitive;

import dev.supplychain.agent.mechanisms.b6.ResourceBudget;
import dev.supplychain.agent.mechanisms.b6.ResourceBudgetLedger;
import dev.supplychain.agent.mechanisms.b6.ResourceBudgetSnapshot;
import dev.supplychain.agent.mechanisms.b6.ResourceUsage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Shared B6 execution boundary for M5 planning and M3 reasoning. The account is durably bound to
 * one immutable budget per execution. Work is reserved before invocation and actual usage is
 * committed from a trusted adapter measurement afterwards. If work starts but fails before a
 * measurement is returned, the full reservation is conservatively charged rather than released.
 */
public final class ResourceBudgetExecutionGuard {
    private final ResourceBudgetLedger ledger;
    private final Function<String, ResourceBudget> budgetProvider;
    private final Clock clock;

    public ResourceBudgetExecutionGuard(ResourceBudgetLedger ledger,
                                        Function<String, ResourceBudget> budgetProvider,
                                        Clock clock) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.budgetProvider = Objects.requireNonNull(budgetProvider, "budgetProvider");
        this.clock = Objects.requireNonNull(clock, "clock");
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
        String reservationId = "b6-" + sha256(execution + "\n" + operationKey + "\n" + UUID.randomUUID());
        ledger.reserve(execution, reservationId, reserved, clock.instant());

        final T result;
        try {
            result = Objects.requireNonNull(operation.get(), "budgeted operation returned null");
        } catch (RuntimeException operationFailure) {
            // After crossing the execution boundary, usage is unknown. Charging the reservation
            // is conservative; leaving it active would permanently hold capacity without a reason.
            try {
                ledger.commit(execution, reservationId, reserved, clock.instant());
            } catch (RuntimeException accountingFailure) {
                operationFailure.addSuppressed(accountingFailure);
            }
            throw operationFailure;
        }

        final ResourceUsage actual;
        try {
            actual = Objects.requireNonNull(actualUsage.apply(result), "actual usage measurement");
        } catch (RuntimeException measurementFailure) {
            // A missing or invalid trusted measurement is not evidence that the work was free.
            // Charge the estimate conservatively; if this write also fails, keep the reservation active.
            try {
                ledger.commit(execution, reservationId, reserved, clock.instant());
            } catch (RuntimeException accountingFailure) {
                measurementFailure.addSuppressed(accountingFailure);
            }
            throw measurementFailure;
        }
        // A commit failure is propagated. The active reservation continues to hold capacity so
        // subsequent work cannot spend resources whose accounting outcome is unknown.
        ledger.commit(execution, reservationId, actual, clock.instant());
        return result;
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
