package dev.supplychain.agent.mechanisms.b6;

import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Instant;
import java.util.Objects;

/** Independently labelled outcome for one target-specific uncertainty prediction. */
public record UncertaintyCalibrationObservation(
        String sampleId, MemoryScopeId scopeId, String targetId,
        UncertaintyDimension dimension, double predictedUncertainty,
        boolean realizedError, EvidenceReference labelEvidence, Instant observedAt) {
    public UncertaintyCalibrationObservation {
        sampleId = required(sampleId, "sampleId");
        scopeId = Objects.requireNonNull(scopeId, "scopeId");
        targetId = required(targetId, "targetId");
        dimension = Objects.requireNonNull(dimension, "dimension");
        if (!Double.isFinite(predictedUncertainty) || predictedUncertainty < 0 || predictedUncertainty > 1)
            throw new IllegalArgumentException("predictedUncertainty must be in [0,1]");
        labelEvidence = Objects.requireNonNull(labelEvidence, "labelEvidence");
        observedAt = Objects.requireNonNull(observedAt, "observedAt");
    }
    private static String required(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
