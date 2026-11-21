package dev.supplychain.agent.mechanisms.b4;

/** Typed events that may cause a committed intention to be reconsidered. */
public enum ReconsiderationTrigger {
    NEW_VERIFIED_EVIDENCE,
    HARD_CONSTRAINT_AT_RISK,
    METHOD_FAILURE,
    RESOURCE_MARGIN_LOW,
    UNCERTAINTY_REQUIRES_REVIEW,
    DELEGATED_INVESTIGATION_RETURNED,
    OBJECTIVE_RELEVANCE_CHANGED,
    DEADLINE_AT_RISK,
    OPERATOR_REQUEST
}
