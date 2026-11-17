package dev.supplychain.agent.mechanisms.b3;

import java.util.Objects;
import java.util.Set;

public record WorkspaceEntry(WorkspaceCandidate candidate, double marginalScore, Set<String> newlyCoveredTags) {
    public WorkspaceEntry {
        candidate = Objects.requireNonNull(candidate, "candidate");
        if (!Double.isFinite(marginalScore)) throw new IllegalArgumentException("marginalScore must be finite");
        newlyCoveredTags = Set.copyOf(Objects.requireNonNull(newlyCoveredTags, "newlyCoveredTags"));
        if (!candidate.coverageTags().containsAll(newlyCoveredTags)) {
            throw new IllegalArgumentException("newly covered tags must belong to the candidate");
        }
    }
}
