package dev.supplychain.agent.runtime.cognitive.decision;

import dev.supplychain.agent.planning.api.PlanningRequest;
import dev.supplychain.agent.planning.api.PlanningResult;

import java.time.Instant;
import java.util.Optional;

/**
 * Persistence port for complete planner inputs/results, kept outside LangGraph4j state.
 * M6 supplies a thread-safe reference adapter; M7 replaces it with durable storage.
 */
public interface PlanningArtifactStore {

    String store(PlanningRequest request, PlanningResult result, Instant storedAt);

    Optional<PlanningArtifacts> find(String candidateSetId);

    record PlanningArtifacts(String candidateSetId, String executionId,
                             PlanningRequest request, PlanningResult result, Instant storedAt) {
        public PlanningArtifacts {
            if (candidateSetId == null || candidateSetId.isBlank()) {
                throw new IllegalArgumentException("candidateSetId cannot be blank");
            }
            if (executionId == null || executionId.isBlank()) {
                throw new IllegalArgumentException("executionId cannot be blank");
            }
            java.util.Objects.requireNonNull(request, "request");
            java.util.Objects.requireNonNull(result, "result");
            java.util.Objects.requireNonNull(storedAt, "storedAt");
            if (!executionId.equals(request.executionId())) {
                throw new IllegalArgumentException("artifact execution identity does not match planning request");
            }
        }
    }
}
