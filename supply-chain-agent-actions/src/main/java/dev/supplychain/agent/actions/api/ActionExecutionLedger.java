package dev.supplychain.agent.actions.api;

import java.time.Instant;
import java.util.Optional;

/** Port for durable operation/idempotency storage. M3 ships an in-memory reference adapter only. */
public interface ActionExecutionLedger {
    /**
     * Read-only duplicate check. A missing or safely retryable failed operation returns empty;
     * completed, in-progress, unknown-outcome, non-retryable-failed and key-reused states are returned.
     */
    Optional<ActionLedgerClaim> inspect(IdempotencyLedgerKey key, String requestFingerprint);
    ActionLedgerClaim claim(IdempotencyLedgerKey key, String requestFingerprint, Instant startedAt);
    void complete(IdempotencyLedgerKey key, ActionReceipt receipt);
    void fail(IdempotencyLedgerKey key, ActionFailure failure);
}
