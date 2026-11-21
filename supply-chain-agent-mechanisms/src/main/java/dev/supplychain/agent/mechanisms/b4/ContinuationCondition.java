package dev.supplychain.agent.mechanisms.b4;

import java.time.Instant;
import java.util.Objects;

/** Explicit condition governing whether an intention remains live across cognitive cycles. */
public record ContinuationCondition(Kind kind, String reference, Instant deadline) {
    public ContinuationCondition {
        kind = Objects.requireNonNull(kind, "kind");
        reference = reference == null ? "" : reference.trim();
        if (kind == Kind.UNTIL_DEADLINE && deadline == null) {
            throw new IllegalArgumentException("UNTIL_DEADLINE requires a deadline");
        }
        if (kind != Kind.UNTIL_DEADLINE && deadline != null) {
            throw new IllegalArgumentException("deadline is valid only for UNTIL_DEADLINE");
        }
        if (kind == Kind.UNTIL_AUTHORITY_RELEASE && reference.isBlank()) {
            throw new IllegalArgumentException("UNTIL_AUTHORITY_RELEASE requires an authority reference");
        }
    }

    public static ContinuationCondition untilObjectiveVerified() {
        return new ContinuationCondition(Kind.UNTIL_OBJECTIVE_VERIFIED, "", null);
    }

    public static ContinuationCondition untilDeadline(Instant deadline) {
        return new ContinuationCondition(Kind.UNTIL_DEADLINE, "", deadline);
    }

    public static ContinuationCondition untilAuthorityRelease(String authorityReference) {
        return new ContinuationCondition(Kind.UNTIL_AUTHORITY_RELEASE, authorityReference, null);
    }

    /** Returns true only when the condition itself proves that the intention should no longer continue. */
    public boolean continuationExpired(Instant at, boolean objectiveVerified, boolean authorityReleased) {
        Objects.requireNonNull(at, "at");
        return switch (kind) {
            case UNTIL_OBJECTIVE_VERIFIED -> objectiveVerified;
            case UNTIL_DEADLINE -> !at.isBefore(deadline);
            case UNTIL_AUTHORITY_RELEASE -> authorityReleased;
        };
    }

    public enum Kind { UNTIL_OBJECTIVE_VERIFIED, UNTIL_DEADLINE, UNTIL_AUTHORITY_RELEASE }
}
