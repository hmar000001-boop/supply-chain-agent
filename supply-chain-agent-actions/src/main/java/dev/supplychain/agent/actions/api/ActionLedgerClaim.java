package dev.supplychain.agent.actions.api;

import java.util.Objects;

public record ActionLedgerClaim(Status status, String operationId, ActionReceipt receipt, ActionFailure failure) {
    public enum Status { ACQUIRED, IN_PROGRESS, COMPLETED, FAILED, UNKNOWN_OUTCOME, KEY_REUSED }
    public ActionLedgerClaim {
        status = Objects.requireNonNull(status, "status");
        switch (status) {
            case ACQUIRED -> {
                requireOperationId(operationId);
                if (receipt != null || failure != null) throw new IllegalArgumentException("invalid acquired claim");
            }
            case IN_PROGRESS -> {
                requireOperationId(operationId);
                Objects.requireNonNull(failure, "in-progress failure details");
                if (receipt != null) throw new IllegalArgumentException("in-progress claim cannot contain a receipt");
            }
            case COMPLETED -> {
                Objects.requireNonNull(receipt, "receipt");
                if (failure != null) throw new IllegalArgumentException("completed claim cannot contain failure");
                if (operationId != null && !operationId.equals(receipt.operationId())) {
                    throw new IllegalArgumentException("claim operation id must match receipt");
                }
            }
            case FAILED, UNKNOWN_OUTCOME, KEY_REUSED -> {
                Objects.requireNonNull(failure, "failure");
                if (receipt != null) throw new IllegalArgumentException("failed claim cannot contain a receipt");
            }
        }
    }

    private static void requireOperationId(String operationId) {
        if (operationId == null || operationId.isBlank()) {
            throw new IllegalArgumentException("operationId is required for this claim status");
        }
    }
}
