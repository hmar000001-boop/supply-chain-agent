package dev.supplychain.agent.mechanisms.b1;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record ActivationInput(List<Instant> accessHistory, Map<String, Double> cueAssociations,
                              Set<String> queryCues, Instant evaluatedAt) {
    public ActivationInput {
        accessHistory = List.copyOf(Objects.requireNonNull(accessHistory, "accessHistory"));
        cueAssociations = Map.copyOf(Objects.requireNonNull(cueAssociations, "cueAssociations"));
        evaluatedAt = Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        queryCues = Objects.requireNonNull(queryCues, "queryCues").stream()
                .map(value -> Objects.requireNonNull(value, "query cue").trim())
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        cueAssociations.forEach((cue, strength) -> {
            if (cue == null || cue.isBlank() || strength == null || !Double.isFinite(strength) || strength < 0.0) {
                throw new IllegalArgumentException("cue associations require nonblank keys and finite nonnegative strengths");
            }
        });
        for (Instant accessAt : accessHistory) {
            if (accessAt == null || accessAt.isAfter(evaluatedAt)) {
                throw new IllegalArgumentException("access history cannot contain future timestamps");
            }
        }
    }
}
