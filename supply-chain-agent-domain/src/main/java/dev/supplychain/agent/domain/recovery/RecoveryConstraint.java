package dev.supplychain.agent.domain.recovery;

/** Named hard constraints; numeric policy parameters are owned by the application policy layer. */
public enum RecoveryConstraint {
    NO_UNQUALIFIED_SUBSTITUTION,
    NO_OVER_ALLOCATION,
    PRESERVE_PROTECTED_CUSTOMER_COMMITMENTS,
    APPROVAL_REQUIRED_FOR_EXTERNAL_COMMITMENT
}
