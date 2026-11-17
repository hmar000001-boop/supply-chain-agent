package dev.supplychain.agent.domain.recovery;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Candidate recovery proposal. Evaluation scores and planner-specific search state live outside the domain. */
public record RecoveryCandidate(
        RecoveryCandidateId id,
        RecoveryObjectiveId objectiveId,
        String description,
        List<RecoveryAction> actions,
        RecoveryCandidateStatus status,
        Instant createdAt,
        long version) {
    public RecoveryCandidate {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(objectiveId, "objectiveId");
        Objects.requireNonNull(description, "description");
        description = description.trim();
        if (description.isEmpty()) throw new IllegalArgumentException("description cannot be blank");
        actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
        if (actions.isEmpty()) throw new IllegalArgumentException("Recovery candidate must contain at least one action");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        if (version < 0) throw new IllegalArgumentException("version cannot be negative");
    }
}
