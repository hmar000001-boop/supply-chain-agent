package dev.supplychain.agent.runtime.cognitive.decision;

import dev.supplychain.agent.planning.api.PlanningRequest;
import dev.supplychain.agent.planning.api.PlanningResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe reference adapter only. It is not a durable production store; M7 supplies that adapter. */
public final class InMemoryPlanningArtifactStore implements PlanningArtifactStore {
    private final ConcurrentHashMap<String, PlanningArtifacts> artifacts = new ConcurrentHashMap<>();

    @Override
    public String store(PlanningRequest request, PlanningResult result, Instant storedAt) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(storedAt, "storedAt");
        String candidateSetId = candidateSetId(request, result);
        PlanningArtifacts candidate = new PlanningArtifacts(candidateSetId, request.executionId(), request, result, storedAt);
        PlanningArtifacts existing = artifacts.putIfAbsent(candidateSetId, candidate);
        if (existing != null && (!existing.request().equals(request) || !existing.result().equals(result))) {
            throw new IllegalStateException("candidate-set fingerprint collision or incompatible duplicate write: " + candidateSetId);
        }
        return candidateSetId;
    }

    @Override
    public Optional<PlanningArtifacts> find(String candidateSetId) {
        if (candidateSetId == null || candidateSetId.isBlank()) {
            throw new IllegalArgumentException("candidateSetId cannot be blank");
        }
        return Optional.ofNullable(artifacts.get(candidateSetId));
    }

    private static String candidateSetId(PlanningRequest request, PlanningResult result) {
        String canonical = request.executionId() + "|" + request.fingerprint() + "|"
                + result.plannerId() + "|" + result.sessionId() + "|" + result.terminationReason() + "|"
                + result.statistics() + "|" + result.candidates() + "|" + result.trace();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return "candidates:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", impossible);
        }
    }
}
