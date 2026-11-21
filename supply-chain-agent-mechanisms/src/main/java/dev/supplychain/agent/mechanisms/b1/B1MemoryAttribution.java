package dev.supplychain.agent.mechanisms.b1;

import java.util.Objects;
import java.util.Set;

/** Exact, versioned CoALA memory attribution carried with a workspace candidate. */
public record B1MemoryAttribution(B1MemoryCandidateReference reference, Set<String> queryCues,
                                  String sourceCandidateFingerprint) {
    public B1MemoryAttribution {
        reference = Objects.requireNonNull(reference, "reference");
        queryCues = Set.copyOf(Objects.requireNonNull(queryCues, "queryCues"));
        if (queryCues.stream().anyMatch(cue -> cue == null || cue.isBlank()))
            throw new IllegalArgumentException("B1 attribution cues cannot be blank");
        sourceCandidateFingerprint = Objects.requireNonNull(sourceCandidateFingerprint, "sourceCandidateFingerprint").trim();
        if (!sourceCandidateFingerprint.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("sourceCandidateFingerprint must be SHA-256");
    }
}
