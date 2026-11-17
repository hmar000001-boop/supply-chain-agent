package dev.supplychain.agent.planning.cot;

import dev.supplychain.agent.memory.common.EvidenceReference;
import java.util.List;
import java.util.Objects;

/** Structured next-step decision; no unrestricted chain-of-thought transcript is retained. */
public record SequentialDecision(boolean stop, String selectedPlanningActionId,
                                 List<EvidenceReference> supportingEvidence,
                                 List<String> assumptions, String conciseRationale) {
    public SequentialDecision {
        selectedPlanningActionId = selectedPlanningActionId == null ? null : selectedPlanningActionId.trim();
        if (stop && selectedPlanningActionId != null) throw new IllegalArgumentException("stop decision cannot select an action");
        if (!stop && (selectedPlanningActionId == null || selectedPlanningActionId.isEmpty())) throw new IllegalArgumentException("non-stop decision must select an action");
        supportingEvidence = List.copyOf(Objects.requireNonNull(supportingEvidence, "supportingEvidence"));
        assumptions = Objects.requireNonNull(assumptions, "assumptions").stream().map(v -> require(v, "assumption")).distinct().toList();
        conciseRationale = require(conciseRationale, "conciseRationale");
    }
    public static SequentialDecision choose(String actionId, List<EvidenceReference> evidence,
                                             List<String> assumptions, String rationale) {
        return new SequentialDecision(false, actionId, evidence, assumptions, rationale);
    }
    public static SequentialDecision stop(List<EvidenceReference> evidence, String rationale) {
        return new SequentialDecision(true, null, evidence, List.of(), rationale);
    }
    private static String require(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
