package dev.supplychain.agent.planning.tot;

import dev.supplychain.agent.planning.api.PlanningAction;
import dev.supplychain.agent.planning.api.PlanningRequest;
import dev.supplychain.agent.planning.api.PlanningState;
import java.math.MathContext;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Transparent default ordering for structured domain-action thoughts; not an LLM thought generator. */
public final class DeterministicThoughtActionRanker implements ThoughtActionRanker {
    @Override public String configurationFingerprint() { return "deterministic-thought-action-ranker-v1"; }

    @Override
    public List<PlanningAction> rank(PlanningRequest request, PlanningState state,
                                     List<PlanningAction> availableActions) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(availableActions, "availableActions");
        return availableActions.stream().sorted(Comparator
                .comparing((PlanningAction action) -> request.isReusableRecoveryAction(action)).reversed()
                .thenComparing(Comparator.comparingDouble((PlanningAction action) -> estimatedUtility(request, action)).reversed())
                .thenComparing(PlanningAction::planningActionId)).toList();
    }

    private static double estimatedUtility(PlanningRequest request, PlanningAction action) {
        var impact = action.impact();
        var profile = request.scoringProfile();
        if (!impact.estimatedCost().currency().equals(profile.costNormalizationMaximum().currency())) {
            throw new IllegalArgumentException("action cost currency differs from planning score currency: " + action.planningActionId());
        }
        double costScore = 1.0 - Math.min(1.0, impact.estimatedCost().amount().divide(
                profile.costNormalizationMaximum().amount(), MathContext.DECIMAL64).doubleValue());
        long availableSeconds = Math.max(1L, Duration.between(request.requestedAt(),
                request.objective().targetFulfillmentAt()).getSeconds());
        double timeScore = 1.0 - Math.min(1.0, (double) impact.expectedDuration().getSeconds() / availableSeconds);
        return profile.serviceWeight() * impact.fulfillmentProgressDelta()
                + profile.costWeight() * costScore
                + profile.timelinessWeight() * timeScore
                + profile.riskWeight() * (1.0 - impact.risk())
                + profile.robustnessWeight() * impact.robustness();
    }
}
