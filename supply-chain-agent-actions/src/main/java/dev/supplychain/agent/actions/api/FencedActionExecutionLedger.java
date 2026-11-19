package dev.supplychain.agent.actions.api;

import java.time.Instant;

/**
 * Extension for production ledgers. The fencing token returned by claimFenced must accompany every
 * receipt/failure write so an expired worker cannot overwrite a newer lease holder's result.
 */
public interface FencedActionExecutionLedger extends ActionExecutionLedger {
    record FencedClaim(ActionLedgerClaim claim, long fencingToken) {
        public FencedClaim {
            java.util.Objects.requireNonNull(claim, "claim");
            if (fencingToken < 0) throw new IllegalArgumentException("fencingToken cannot be negative");
        }
    }

    FencedClaim claimFenced(IdempotencyLedgerKey key, String requestFingerprint, Instant startedAt);
    void completeFenced(IdempotencyLedgerKey key, long fencingToken, ActionReceipt receipt);
    void failFenced(IdempotencyLedgerKey key, long fencingToken, ActionFailure failure);

    @Override default ActionLedgerClaim claim(IdempotencyLedgerKey key, String requestFingerprint, Instant startedAt) {
        return claimFenced(key, requestFingerprint, startedAt).claim();
    }
    @Override default void complete(IdempotencyLedgerKey key, ActionReceipt receipt) {
        throw new UnsupportedOperationException("production fenced ledgers require completeFenced");
    }
    @Override default void fail(IdempotencyLedgerKey key, ActionFailure failure) {
        throw new UnsupportedOperationException("production fenced ledgers require failFenced");
    }
}
