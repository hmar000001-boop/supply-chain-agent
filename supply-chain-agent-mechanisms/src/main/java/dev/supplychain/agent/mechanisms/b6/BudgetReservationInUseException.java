package dev.supplychain.agent.mechanisms.b6;

/** A different live owner currently holds the operation's reservation lease. */
public final class BudgetReservationInUseException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    public BudgetReservationInUseException(String message) { super(message); }
}
