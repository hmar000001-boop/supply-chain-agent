package dev.supplychain.agent.domain.common;

import java.math.BigDecimal;
import java.util.Objects;

/** Immutable non-negative quantity. Arithmetic never silently mixes units. */
public record Quantity(BigDecimal value, UnitOfMeasure unit) implements Comparable<Quantity> {

    public Quantity {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(unit, "unit");
        if (value.signum() < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative: " + value);
        }
        value = normalize(value);
    }

    public static Quantity of(String value, UnitOfMeasure unit) {
        return new Quantity(new BigDecimal(value), unit);
    }

    public static Quantity of(BigDecimal value, UnitOfMeasure unit) {
        return new Quantity(value, unit);
    }

    public static Quantity zero(UnitOfMeasure unit) {
        return new Quantity(BigDecimal.ZERO, unit);
    }

    public boolean isZero() {
        return value.signum() == 0;
    }

    public boolean isPositive() {
        return value.signum() > 0;
    }

    public Quantity plus(Quantity other) {
        requireSameUnit(other);
        return new Quantity(value.add(other.value), unit);
    }

    /** Subtracts without permitting an invalid negative stock balance. */
    public Quantity minus(Quantity other) {
        requireSameUnit(other);
        BigDecimal result = value.subtract(other.value);
        if (result.signum() < 0) {
            throw new IllegalArgumentException("Quantity underflow: " + this + " - " + other);
        }
        return new Quantity(result, unit);
    }

    public Quantity multipliedBy(BigDecimal factor) {
        Objects.requireNonNull(factor, "factor");
        if (factor.signum() < 0) {
            throw new IllegalArgumentException("Quantity multiplier cannot be negative");
        }
        return new Quantity(value.multiply(factor), unit);
    }

    /** Divides exactly; callers must supply a representable conversion. */
    public Quantity dividedBy(BigDecimal divisor) {
        Objects.requireNonNull(divisor, "divisor");
        if (divisor.signum() <= 0) {
            throw new IllegalArgumentException("Quantity divisor must be positive");
        }
        try {
            return new Quantity(value.divide(divisor), unit);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Quantity conversion is not exact: " + value + " / " + divisor, ex);
        }
    }

    @Override
    public int compareTo(Quantity other) {
        requireSameUnit(other);
        return value.compareTo(other.value);
    }

    private void requireSameUnit(Quantity other) {
        Objects.requireNonNull(other, "other");
        if (unit != other.unit) {
            throw new IllegalArgumentException("Unit mismatch: " + unit + " versus " + other.unit);
        }
    }

    private static BigDecimal normalize(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.signum() == 0 ? BigDecimal.ZERO : normalized;
    }

    @Override
    public String toString() {
        return value.toPlainString() + " " + unit;
    }
}
