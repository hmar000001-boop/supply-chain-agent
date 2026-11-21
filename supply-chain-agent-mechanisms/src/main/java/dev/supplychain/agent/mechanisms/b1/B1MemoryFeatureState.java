package dev.supplychain.agent.mechanisms.b1;

import dev.supplychain.agent.mechanisms.b3.DownstreamOutcomeSignal;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Durable, versioned accessibility and utility features for one exact CoALA memory revision. */
public record B1MemoryFeatureState(
        B1MemoryCandidateReference reference, List<Instant> accessHistory,
        List<Instant> verifiedUseHistory, Map<String, Double> cueAssociationStrengths,
        double expectedDownstreamUtility,
        long usefulOutcomes, long harmfulOutcomes, long neutralOutcomes,
        Set<String> appliedOutcomeKeys, Set<String> appliedAccessEventKeys,
        long revision, Instant updatedAt) {
    private static final int MAX_ACCESS_HISTORY = 64;
    public B1MemoryFeatureState {
        reference = Objects.requireNonNull(reference, "reference");
        accessHistory = List.copyOf(Objects.requireNonNull(accessHistory, "accessHistory"));
        // A missing field is accepted for pre-GAP01 JSON payloads; V014 payload evolution is additive.
        verifiedUseHistory = verifiedUseHistory == null ? List.of() : List.copyOf(verifiedUseHistory);
        if (accessHistory.size() > MAX_ACCESS_HISTORY || accessHistory.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("access history must contain at most 64 valid timestamps");
        if (verifiedUseHistory.size() > MAX_ACCESS_HISTORY || verifiedUseHistory.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("verified-use history must contain at most 64 valid timestamps");
        var cues = new TreeMap<String, Double>();
        Objects.requireNonNull(cueAssociationStrengths, "cueAssociationStrengths").forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null || !Double.isFinite(value) || value < 0 || value > 1)
                throw new IllegalArgumentException("cue associations must be in [0,1] with nonblank keys");
            cues.put(key.trim(), value);
        });
        cueAssociationStrengths = Map.copyOf(cues);
        if (!Double.isFinite(expectedDownstreamUtility) || expectedDownstreamUtility < 0 || expectedDownstreamUtility > 1)
            throw new IllegalArgumentException("expectedDownstreamUtility must be in [0,1]");
        if (usefulOutcomes < 0 || harmfulOutcomes < 0 || neutralOutcomes < 0 || revision < 0)
            throw new IllegalArgumentException("feature counters cannot be negative");
        appliedOutcomeKeys = Set.copyOf(Objects.requireNonNull(appliedOutcomeKeys, "appliedOutcomeKeys"));
        appliedAccessEventKeys = Set.copyOf(Objects.requireNonNull(appliedAccessEventKeys, "appliedAccessEventKeys"));
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }
    public static B1MemoryFeatureState empty(B1MemoryCandidateReference ref, Instant at) {
        return new B1MemoryFeatureState(ref, List.of(), List.of(), Map.of(), 0.5, 0, 0, 0,
                Set.of(), Set.of(), 0, at);
    }
    public B1MemoryFeatureState recordAccess(String eventKey, Set<String> cues, Instant at) {
        String key = required(eventKey, "eventKey"); Objects.requireNonNull(at, "at");
        if (appliedAccessEventKeys.contains(key)) return this;
        if (at.isBefore(updatedAt)) throw new IllegalArgumentException("B1 access event time cannot move backwards");
        var history = new ArrayList<>(accessHistory); history.add(at);
        if (history.size() > MAX_ACCESS_HISTORY) history.removeFirst();
        var updatedKeys = new HashSet<>(appliedAccessEventKeys); updatedKeys.add(key);
        var nextCues = new TreeMap<>(cueAssociationStrengths);
        for (String cue : Objects.requireNonNull(cues, "cues")) nextCues.putIfAbsent(required(cue, "cue"), 0.5);
        return new B1MemoryFeatureState(reference, history, verifiedUseHistory, nextCues, expectedDownstreamUtility,
                usefulOutcomes, harmfulOutcomes, neutralOutcomes, appliedOutcomeKeys, updatedKeys,
                revision + 1, at);
    }
    public B1MemoryFeatureState applyVerifiedOutcome(String outcomeKey, DownstreamOutcomeSignal signal,
            Set<String> cues, Instant at) {
        String key = required(outcomeKey, "outcomeKey"); Objects.requireNonNull(signal, "signal"); Objects.requireNonNull(at, "at");
        if (appliedOutcomeKeys.contains(key)) return this;
        if (at.isBefore(updatedAt)) throw new IllegalArgumentException("B1 outcome time cannot move backwards");
        double reward = switch (signal) { case USEFUL -> 1.0; case HARMFUL -> 0.0; case NEUTRAL -> 0.5; };
        // A bounded 0.2 EWMA moves future utility without allowing one event to dominate permanently.
        double utility = appliedOutcomeKeys.isEmpty() ? reward : 0.8 * expectedDownstreamUtility + 0.2 * reward;
        var nextCues = new TreeMap<>(cueAssociationStrengths);
        double delta = switch (signal) { case USEFUL -> 0.1; case HARMFUL -> -0.1; case NEUTRAL -> 0.0; };
        for (String cue : Objects.requireNonNull(cues, "cues")) {
            String normalized = required(cue, "cue");
            nextCues.put(normalized, Math.max(0.0, Math.min(1.0, nextCues.getOrDefault(normalized, 0.5) + delta)));
        }
        var keys = new HashSet<>(appliedOutcomeKeys); keys.add(key);
        var uses = new ArrayList<>(verifiedUseHistory); uses.add(at);
        if (uses.size() > MAX_ACCESS_HISTORY) uses.removeFirst();
        return new B1MemoryFeatureState(reference, accessHistory, uses, nextCues, utility,
                usefulOutcomes + (signal == DownstreamOutcomeSignal.USEFUL ? 1 : 0),
                harmfulOutcomes + (signal == DownstreamOutcomeSignal.HARMFUL ? 1 : 0),
                neutralOutcomes + (signal == DownstreamOutcomeSignal.NEUTRAL ? 1 : 0), keys,
                appliedAccessEventKeys, revision + 1, at);
    }
    private static String required(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim(); if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank"); return result;
    }
}
