package dev.supplychain.agent.actions.api;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Thread-safe reference ledger for deterministic tests; not a production durable store. */
public final class InMemoryActionExecutionLedger implements ActionExecutionLedger {
    private record Entry(String fingerprint, String operationId, State state,
                         ActionReceipt receipt, ActionFailure failure, Instant startedAt) { }
    private enum State { IN_PROGRESS, COMPLETED, FAILED, UNKNOWN_OUTCOME }
    private final Map<IdempotencyLedgerKey, Entry> entries = new HashMap<>();


    @Override public synchronized Optional<ActionLedgerClaim> inspect(IdempotencyLedgerKey key, String fingerprint) {
        Objects.requireNonNull(key, "key");
        fingerprint = require(fingerprint, "fingerprint");
        Entry current = entries.get(key);
        if (current == null) return Optional.empty();
        if (!current.fingerprint().equals(fingerprint)) {
            return Optional.of(new ActionLedgerClaim(ActionLedgerClaim.Status.KEY_REUSED, null, null,
                    new ActionFailure(ActionFailureCode.IDEMPOTENCY_KEY_REUSED,
                            "Idempotency key was previously used for a different action input", false, false)));
        }
        return switch (current.state()) {
            case IN_PROGRESS -> Optional.of(new ActionLedgerClaim(ActionLedgerClaim.Status.IN_PROGRESS,
                    current.operationId(), null, new ActionFailure(ActionFailureCode.OPERATION_IN_PROGRESS,
                    "An operation with this idempotency key is already running", false, false)));
            case COMPLETED -> Optional.of(new ActionLedgerClaim(ActionLedgerClaim.Status.COMPLETED,
                    current.operationId(), current.receipt(), null));
            case FAILED -> {
                ActionFailure previousFailure = Objects.requireNonNull(current.failure());
                yield previousFailure.retryable() && !previousFailure.outcomeUnknown()
                        ? Optional.empty()
                        : Optional.of(new ActionLedgerClaim(ActionLedgerClaim.Status.FAILED,
                                current.operationId(), null, previousFailure));
            }
            case UNKNOWN_OUTCOME -> Optional.of(new ActionLedgerClaim(ActionLedgerClaim.Status.UNKNOWN_OUTCOME,
                    current.operationId(), null, Objects.requireNonNull(current.failure())));
        };
    }

    @Override public synchronized ActionLedgerClaim claim(IdempotencyLedgerKey key, String fingerprint, Instant startedAt) {
        Objects.requireNonNull(key, "key");
        fingerprint = require(fingerprint, "fingerprint");
        Objects.requireNonNull(startedAt, "startedAt");
        Entry current = entries.get(key);
        if (current == null) {
            String operationId = UUID.randomUUID().toString();
            entries.put(key, new Entry(fingerprint, operationId, State.IN_PROGRESS, null, null, startedAt));
            return new ActionLedgerClaim(ActionLedgerClaim.Status.ACQUIRED, operationId, null, null);
        }
        if (!current.fingerprint().equals(fingerprint)) {
            return new ActionLedgerClaim(ActionLedgerClaim.Status.KEY_REUSED, null, null,
                    new ActionFailure(ActionFailureCode.IDEMPOTENCY_KEY_REUSED,
                            "Idempotency key was previously used for a different action input", false, false));
        }
        return switch (current.state()) {
            case IN_PROGRESS -> new ActionLedgerClaim(ActionLedgerClaim.Status.IN_PROGRESS, current.operationId(), null,
                    new ActionFailure(ActionFailureCode.OPERATION_IN_PROGRESS, "An operation with this idempotency key is already running", false, false));
            case COMPLETED -> new ActionLedgerClaim(ActionLedgerClaim.Status.COMPLETED, current.operationId(), current.receipt(), null);
            case FAILED -> {
                ActionFailure previousFailure = Objects.requireNonNull(current.failure());
                if (previousFailure.retryable() && !previousFailure.outcomeUnknown()) {
                    entries.put(key, new Entry(current.fingerprint(), current.operationId(), State.IN_PROGRESS,
                            null, null, startedAt));
                    yield new ActionLedgerClaim(ActionLedgerClaim.Status.ACQUIRED, current.operationId(), null, null);
                }
                yield new ActionLedgerClaim(ActionLedgerClaim.Status.FAILED, current.operationId(), null, previousFailure);
            }
            case UNKNOWN_OUTCOME -> new ActionLedgerClaim(ActionLedgerClaim.Status.UNKNOWN_OUTCOME, current.operationId(), null,
                    Objects.requireNonNull(current.failure()));
        };
    }

    @Override public synchronized void complete(IdempotencyLedgerKey key, ActionReceipt receipt) {
        Entry current = requireInProgress(key);
        entries.put(key, new Entry(current.fingerprint(), current.operationId(), State.COMPLETED,
                Objects.requireNonNull(receipt), null, current.startedAt()));
    }

    @Override public synchronized void fail(IdempotencyLedgerKey key, ActionFailure failure) {
        Entry current = requireInProgress(key);
        Objects.requireNonNull(failure, "failure");
        State state = failure.outcomeUnknown() ? State.UNKNOWN_OUTCOME : State.FAILED;
        entries.put(key, new Entry(current.fingerprint(), current.operationId(), state,
                null, failure, current.startedAt()));
    }

    private Entry requireInProgress(IdempotencyLedgerKey key) {
        Entry current = entries.get(Objects.requireNonNull(key, "key"));
        if (current == null || current.state() != State.IN_PROGRESS) throw new IllegalStateException("operation is not in progress");
        return current;
    }
    private static String require(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
