package dev.supplychain.agent.mechanisms.b4;

/** Typed, auditable reasons for leaving the active intention lifecycle. */
public enum IntentionTerminationReason {
    OBJECTIVE_VERIFIED,
    OPERATOR_CANCELLED,
    OBJECTIVE_IMPOSSIBLE,
    AUTHORITY_REVOKED,
    SAFETY_STOP,
    DEADLINE_EXPIRED,
    CONTINUATION_CONDITION_EXPIRED,
    DELEGATION_UNRECOVERABLE
}
