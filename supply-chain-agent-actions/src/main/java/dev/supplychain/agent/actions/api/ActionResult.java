package dev.supplychain.agent.actions.api;

import java.util.Objects;
import java.util.Optional;

public record ActionResult<T>(ActionStatus status, T value, ActionFailure failure, ActionReceipt receipt) {
    public ActionResult {
        status = Objects.requireNonNull(status, "status");
        switch (status) {
            case SUCCEEDED -> {
                Objects.requireNonNull(value, "successful action value");
                Objects.requireNonNull(receipt, "successful action receipt");
                if (failure != null) throw new IllegalArgumentException("successful result cannot contain failure");
            }
            case ALREADY_COMPLETED -> {
                Objects.requireNonNull(receipt, "duplicate result receipt");
                if (value != null || failure != null) throw new IllegalArgumentException("duplicate result carries receipt only");
            }
            case REJECTED, FAILED, IN_PROGRESS -> {
                Objects.requireNonNull(failure, "failure");
                if (value != null) throw new IllegalArgumentException("failed result cannot contain a value");
            }
        }
    }
    public static <T> ActionResult<T> success(T value, ActionReceipt receipt) {
        return new ActionResult<>(ActionStatus.SUCCEEDED, value, null, receipt);
    }
    public static <T> ActionResult<T> alreadyCompleted(ActionReceipt receipt) {
        return new ActionResult<>(ActionStatus.ALREADY_COMPLETED, null, null, receipt);
    }
    public static <T> ActionResult<T> rejected(ActionFailure failure) {
        return new ActionResult<>(ActionStatus.REJECTED, null, failure, null);
    }
    public static <T> ActionResult<T> failed(ActionFailure failure) {
        return new ActionResult<>(ActionStatus.FAILED, null, failure, null);
    }
    public static <T> ActionResult<T> inProgress(ActionFailure failure) {
        return new ActionResult<>(ActionStatus.IN_PROGRESS, null, failure, null);
    }
    public Optional<T> valueOptional() { return Optional.ofNullable(value); }
    public Optional<ActionFailure> failureOptional() { return Optional.ofNullable(failure); }
    public Optional<ActionReceipt> receiptOptional() { return Optional.ofNullable(receipt); }
}
