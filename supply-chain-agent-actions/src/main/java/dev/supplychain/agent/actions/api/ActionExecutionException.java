package dev.supplychain.agent.actions.api;

import java.util.Objects;

@SuppressWarnings("serial")
public final class ActionExecutionException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final ActionFailure failure;
    public ActionExecutionException(ActionFailure failure) {
        super(Objects.requireNonNull(failure, "failure").message());
        this.failure = failure;
    }
    public ActionFailure failure() { return failure; }
}
