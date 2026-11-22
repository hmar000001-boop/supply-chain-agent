package dev.supplychain.agent.evaluation.experiment.mechanism;

import dev.supplychain.agent.mechanisms.b1.B1MemoryCandidateReference;
import dev.supplychain.agent.mechanisms.b1.B1MemoryFeatureState;
import dev.supplychain.agent.mechanisms.b3.DownstreamOutcomeEvidence;
import dev.supplychain.agent.mechanisms.b3.WorkspaceReceiptEvidence;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.List;
import java.util.Objects;

/** TRAIN-only, receipt-attributed feature snapshot derived from verified B3 downstream outcomes. */
public record B1MemoryTrainingArtifact(
        Map<B1MemoryCandidateReference, B1MemoryFeatureState> featureSeeds,
        Map<B1MemoryCandidateReference, B1MemoryFeatureState> initialFeatureStates,
        List<DownstreamOutcomeEvidence> outcomes,
        Map<String, WorkspaceReceiptEvidence> receiptsById,
        Set<String> trainingUnitFingerprints,
        String verifierId,
        String evidenceFingerprint) {
    public B1MemoryTrainingArtifact {
        var seeds = new java.util.TreeMap<String, B1MemoryFeatureState>();
        Objects.requireNonNull(featureSeeds, "featureSeeds").forEach((reference, state) -> {
            if (reference == null || state == null || !reference.equals(state.reference()))
                throw new SecurityException("B1 seed key must equal the exact versioned memory reference in its state");
            seeds.put(reference.candidateId() + "|" + reference.scopeId().value() + "|" + reference.kind(), state);
        });
        if (seeds.isEmpty()) throw new IllegalArgumentException("B1 training artifact requires at least one learned memory feature");
        var orderedSeeds = new java.util.LinkedHashMap<B1MemoryCandidateReference, B1MemoryFeatureState>();
        Objects.requireNonNull(featureSeeds, "featureSeeds").entrySet().stream()
                .sorted(java.util.Comparator.comparing(e -> e.getKey().candidateId() + "|" + e.getKey().scopeId().value() + "|" + e.getKey().kind()))
                .forEach(e -> orderedSeeds.put(e.getKey(), e.getValue()));
        featureSeeds = java.util.Collections.unmodifiableMap(orderedSeeds);
        var initial = new java.util.TreeMap<String, B1MemoryFeatureState>();
        Objects.requireNonNull(initialFeatureStates, "initialFeatureStates").forEach((reference, state) -> {
            if (reference == null || state == null || !reference.equals(state.reference()))
                throw new SecurityException("B1 initial-state key must equal its exact versioned memory reference");
            initial.put(reference.candidateId() + "|" + reference.scopeId().value() + "|" + reference.kind(), state);
        });
        if (!initial.keySet().equals(seeds.keySet()))
            throw new SecurityException("B1 initial and final feature states must cover exactly the same memory references");
        var orderedInitial = new java.util.LinkedHashMap<B1MemoryCandidateReference, B1MemoryFeatureState>();
        Objects.requireNonNull(initialFeatureStates, "initialFeatureStates").entrySet().stream()
                .sorted(java.util.Comparator.comparing(e -> e.getKey().candidateId() + "|" + e.getKey().scopeId().value() + "|" + e.getKey().kind()))
                .forEach(e -> orderedInitial.put(e.getKey(), e.getValue()));
        initialFeatureStates = java.util.Collections.unmodifiableMap(orderedInitial);
        outcomes = List.copyOf(Objects.requireNonNull(outcomes, "outcomes"));
        var receipts = new TreeMap<String, WorkspaceReceiptEvidence>();
        Objects.requireNonNull(receiptsById, "receiptsById").forEach((id, receipt) -> {
            if (id == null || receipt == null || !id.equals(receipt.receiptId()))
                throw new SecurityException("B1 receipt map key does not match receipt identity");
            receipts.put(id, receipt);
        });
        receiptsById = Map.copyOf(receipts);
        trainingUnitFingerprints = hashes(trainingUnitFingerprints, "trainingUnitFingerprints");
        if (trainingUnitFingerprints.size() < 2) throw new IllegalArgumentException("B1 learning requires at least two independent TRAIN units");
        verifierId = ExperimentEvidenceFingerprint.required(verifierId, "verifierId");
        evidenceFingerprint = ExperimentEvidenceFingerprint.requireSha(evidenceFingerprint, "evidenceFingerprint");
        if (outcomes.isEmpty()) throw new IllegalArgumentException("B1 training requires verifier-attributed downstream outcomes");
    }

    /** Compatibility constructor for a clean-room TRAIN repository whose initial feature rows are empty. */
    public B1MemoryTrainingArtifact(Map<B1MemoryCandidateReference, B1MemoryFeatureState> featureSeeds,
            List<DownstreamOutcomeEvidence> outcomes, Map<String, WorkspaceReceiptEvidence> receiptsById,
            Set<String> trainingUnitFingerprints, String verifierId, String evidenceFingerprint) {
        this(featureSeeds, emptyInitialStates(featureSeeds), outcomes, receiptsById,
                trainingUnitFingerprints, verifierId, evidenceFingerprint);
    }

    public static Map<B1MemoryCandidateReference, B1MemoryFeatureState> emptyInitialStates(
            Map<B1MemoryCandidateReference, B1MemoryFeatureState> featureSeeds) {
        Objects.requireNonNull(featureSeeds, "featureSeeds");
        var result = new java.util.LinkedHashMap<B1MemoryCandidateReference, B1MemoryFeatureState>();
        featureSeeds.keySet().stream().sorted(java.util.Comparator.comparing(reference ->
                reference.candidateId() + "|" + reference.scopeId().value() + "|" + reference.kind()))
                .forEach(reference -> result.put(reference,
                        B1MemoryFeatureState.empty(reference, java.time.Instant.EPOCH)));
        return Map.copyOf(result);
    }

    private static Set<String> hashes(Set<String> values, String name) {
        return Objects.requireNonNull(values, name).stream().map(v -> ExperimentEvidenceFingerprint.requireSha(v, name + " entry"))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
