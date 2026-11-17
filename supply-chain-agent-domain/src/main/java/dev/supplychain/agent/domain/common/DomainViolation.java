package dev.supplychain.agent.domain.common;

import java.util.Objects;

/** A deterministic business-rule violation; never represents an infrastructure failure. */
public final class DomainViolation extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public enum Code {
        NOT_FOUND,
        DUPLICATE_ID,
        DUPLICATE_SKU,
        INVALID_STATE,
        INVALID_REFERENCE,
        INVALID_QUANTITY,
        INSUFFICIENT_INVENTORY,
        CAPACITY_EXCEEDED,
        UNQUALIFIED_SUPPLIER,
        UNQUALIFIED_SUBSTITUTE,
        ORDER_QUANTITY_EXCEEDED,
        INVALID_TRANSITION,
        INVALID_DATE,
        CONFLICT
    }

    private final Code code;

    public DomainViolation(Code code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public DomainViolation(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public Code code() {
        return code;
    }
}
