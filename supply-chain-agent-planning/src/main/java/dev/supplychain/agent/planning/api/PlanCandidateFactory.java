package dev.supplychain.agent.planning.api;

import dev.supplychain.agent.memory.common.EvidenceReference;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;

public final class PlanCandidateFactory {
    private PlanCandidateFactory() { }

    public static PlanCandidate create(PlanningRequest request, PlanningState state,
                                       PlanEvaluation evaluation, String rationale,
                                       List<String> additionalAssumptions) {
        var refs = new LinkedHashSet<EvidenceReference>(request.evidence());
        refs.addAll(request.workingMemory().sourceEvidence());
        refs.addAll(state.evidence());
        var assumptions = new LinkedHashSet<String>();
        state.actionPath().forEach(action -> assumptions.addAll(action.assumptions()));
        assumptions.addAll(additionalAssumptions);
        return new PlanCandidate(candidateId(request, state.actionPath()), request.objective().id(),
                state.actionPath(), List.copyOf(refs), List.copyOf(assumptions), rationale, evaluation);
    }

    /** Candidate identity is scoped to the complete immutable planning input, not merely action labels. */
    public static String candidateId(PlanningRequest request, List<PlanningAction> actions) {
        return digest(request.fingerprint() + "|" + actionPath(actions));
    }

    /** Search-node identity is scoped to the same request fingerprint as its owning planner session. */
    public static String stateNodeId(PlanningRequest request, List<PlanningAction> actions) {
        return digest("NODE|" + request.fingerprint() + "|" + actionPath(actions));
    }

    /** Retained for diagnostic uses where no request context is available. */
    public static String candidateId(List<PlanningAction> actions) {
        return digest("UNSCOPED|" + actionPath(actions));
    }

    private static String actionPath(List<PlanningAction> actions) {
        return actions.stream().map(PlanningAction::planningActionId)
                .reduce((a, b) -> a + "|" + b).orElse("empty");
    }

    private static String digest(String canonical) {
        try {
            return "PLAN-" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8))).substring(0, 24);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
