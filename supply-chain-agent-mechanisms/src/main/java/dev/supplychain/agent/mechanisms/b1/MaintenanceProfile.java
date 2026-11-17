package dev.supplychain.agent.mechanisms.b1;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Estimated maintenance costs are expressed in the same utility-point unit as downstream utility. */
public record MaintenanceProfile(
        double admissionValidationCost,
        double refreshCost,
        double recurringCostPerDay,
        Duration refreshInterval,
        Instant lastMaintainedAt) {
    public MaintenanceProfile {
        if (!finiteNonNegative(admissionValidationCost) || !finiteNonNegative(refreshCost)
                || !finiteNonNegative(recurringCostPerDay)) throw new IllegalArgumentException("maintenance costs must be finite and nonnegative");
        refreshInterval = Objects.requireNonNull(refreshInterval, "refreshInterval");
        if (refreshInterval.isNegative() || refreshInterval.isZero()) throw new IllegalArgumentException("refreshInterval must be positive");
        lastMaintainedAt = Objects.requireNonNull(lastMaintainedAt, "lastMaintainedAt");
    }
    public double estimateCost(Instant at, Duration horizon) {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(horizon, "horizon");
        if (horizon.isNegative()) throw new IllegalArgumentException("maintenance horizon cannot be negative");
        if (lastMaintainedAt.isAfter(at)) throw new IllegalArgumentException("lastMaintainedAt cannot be in the future");
        boolean refreshDue = !lastMaintainedAt.plus(refreshInterval).isAfter(at);
        double days = horizon.toMillis() / 86_400_000.0;
        return admissionValidationCost + (refreshDue ? refreshCost : 0.0) + recurringCostPerDay * days;
    }
    private static boolean finiteNonNegative(double value) { return Double.isFinite(value) && value >= 0.0; }
}
