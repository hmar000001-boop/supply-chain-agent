package dev.supplychain.agent.mechanisms.b3;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** One feasible alternative bundle evaluated against other bundles and singleton candidates. */
public record WorkspaceCoalitionProposal(String id, int round, List<String> candidateIds,
        Set<String> newlyCoveredTags, int contextUnits, double baseScore,
        double coverageBonus, double totalScore, boolean selected) {
    public WorkspaceCoalitionProposal {
        id = required(id, "id");
        if (round < 0) throw new IllegalArgumentException("round cannot be negative");
        candidateIds = Objects.requireNonNull(candidateIds, "candidateIds").stream().map(v -> required(v, "candidateId")).sorted().toList();
        if (candidateIds.size() < 2 || candidateIds.size() != candidateIds.stream().distinct().count())
            throw new IllegalArgumentException("coalition proposal requires at least two unique candidate IDs");
        newlyCoveredTags = Objects.requireNonNull(newlyCoveredTags, "newlyCoveredTags").stream()
                .map(v -> required(v, "coverageTag")).collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (newlyCoveredTags.size() < 2) throw new IllegalArgumentException("coalition proposal must add at least two distinct coverage tags");
        if (contextUnits < 2) throw new IllegalArgumentException("coalition proposal context cost must be at least two units");
        if (!Double.isFinite(baseScore) || !Double.isFinite(coverageBonus) || coverageBonus < 0
                || !Double.isFinite(totalScore) || Double.compare(totalScore, baseScore + coverageBonus) != 0)
            throw new IllegalArgumentException("coalition proposal score accounting is inconsistent");
    }

    private static String required(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
