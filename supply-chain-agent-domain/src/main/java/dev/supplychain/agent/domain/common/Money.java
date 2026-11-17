package dev.supplychain.agent.domain.common;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/** Currency-safe monetary value for modeled procurement and recovery cost comparisons. */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {
    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (amount.signum() < 0) throw new IllegalArgumentException("Money amount cannot be negative");
        amount = amount.stripTrailingZeros();
        if (amount.signum() == 0) amount = BigDecimal.ZERO;
    }
    public static Money of(String amount, String currencyCode) {
        return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
    }
    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }
    public Money multipliedBy(BigDecimal factor) {
        Objects.requireNonNull(factor, "factor");
        if (factor.signum() < 0) throw new IllegalArgumentException("Cost multiplier cannot be negative");
        return new Money(amount.multiply(factor), currency);
    }
    @Override public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }
    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (!currency.equals(other.currency)) throw new IllegalArgumentException("Currency mismatch: " + currency + " versus " + other.currency);
    }
    @Override public String toString() { return currency.getCurrencyCode() + " " + amount.toPlainString(); }
}
