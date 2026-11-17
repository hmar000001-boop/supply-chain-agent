package dev.supplychain.agent.mechanisms.b1;

import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A retrieved M2 memory item plus explicit B1 scoring features supplied by trusted adapters. */
public record B1MemoryCandidate(
        String candidateId,
        MemoryScopeId scopeId,
        B1MemoryCandidateKind kind,
        String memoryReference,
        List<Instant> accessHistory,
        Map<String, Double> cueAssociationStrengths,
        double expectedDownstreamUtility,
        int contextUnits,
        Duration measuredInfrastructureLatency,
        MaintenanceProfile maintenanceProfile) {
    public B1MemoryCandidate {
        candidateId = require(candidateId, "candidateId");
        scopeId = Objects.requireNonNull(scopeId, "scopeId");
        kind = Objects.requireNonNull(kind, "kind");
        memoryReference = require(memoryReference, "memoryReference");
        accessHistory = List.copyOf(Objects.requireNonNull(accessHistory, "accessHistory"));
        cueAssociationStrengths = Map.copyOf(Objects.requireNonNull(cueAssociationStrengths, "cueAssociationStrengths"));
        cueAssociationStrengths.forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null || !Double.isFinite(value) || value < 0.0) {
                throw new IllegalArgumentException("cue associations require nonblank keys and finite nonnegative strengths");
            }
        });
        if (!Double.isFinite(expectedDownstreamUtility) || expectedDownstreamUtility < 0.0) {
            throw new IllegalArgumentException("expectedDownstreamUtility must be finite and nonnegative");
        }
        if (contextUnits < 1) throw new IllegalArgumentException("contextUnits must be positive");
        measuredInfrastructureLatency = Objects.requireNonNull(measuredInfrastructureLatency, "measuredInfrastructureLatency");
        if (measuredInfrastructureLatency.isNegative()) throw new IllegalArgumentException("infrastructure latency cannot be negative");
        maintenanceProfile = Objects.requireNonNull(maintenanceProfile, "maintenanceProfile");
    }
    private static String require(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
