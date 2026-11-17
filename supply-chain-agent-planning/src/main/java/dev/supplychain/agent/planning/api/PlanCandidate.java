package dev.supplychain.agent.planning.api;

import dev.supplychain.agent.domain.recovery.RecoveryObjectiveId;
import dev.supplychain.agent.memory.common.EvidenceReference;
import java.util.List;
import java.util.Objects;

/** Auditable proposal. It is not approved and cannot be executed directly. */
public record PlanCandidate(String candidateId, RecoveryObjectiveId objectiveId,
                            List<PlanningAction> actions, List<EvidenceReference> supportingEvidence,
                            List<String> assumptions, String conciseRationale, PlanEvaluation evaluation) {
    public PlanCandidate {
        candidateId = require(candidateId, "candidateId");
        objectiveId = Objects.requireNonNull(objectiveId, "objectiveId");
        actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
        if (actions.isEmpty()) throw new IllegalArgumentException("candidate must contain at least one action");
        supportingEvidence = List.copyOf(Objects.requireNonNull(supportingEvidence, "supportingEvidence"));
        assumptions = Objects.requireNonNull(assumptions, "assumptions").stream().map(v -> require(v, "assumption")).distinct().toList();
        conciseRationale = require(conciseRationale, "conciseRationale");
        evaluation = Objects.requireNonNull(evaluation, "evaluation");
    }
    private static String require(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
