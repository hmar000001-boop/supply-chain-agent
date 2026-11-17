package dev.supplychain.agent.planning.api;

import dev.supplychain.agent.actions.api.ActionEffect;
import dev.supplychain.agent.domain.common.Money;
import dev.supplychain.agent.domain.recovery.RecoveryConstraint;
import dev.supplychain.agent.domain.world.SupplyChainSnapshot;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;

/** Common deterministic replay, hard-constraint gate and transparent weighted scoring policy. */
public final class DeterministicCandidateEvaluator implements CandidateEvaluator {
    private final PlanningWorldModel worldModel;

    public DeterministicCandidateEvaluator(PlanningWorldModel worldModel) {
        this.worldModel = Objects.requireNonNull(worldModel, "worldModel");
    }

    @Override public String configurationFingerprint() { return "deterministic-candidate-evaluator-v1"; }

    @Override
    public PlanEvaluation evaluate(PlanningRequest request, PlanningState proposedState) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(proposedState, "proposedState");
        var violations = new ArrayList<String>();
        if (!request.objective().id().equals(proposedState.objectiveId())) {
            violations.add("candidate state belongs to a different recovery objective");
        }
        if (request.initialWorld().version() != proposedState.rootWorldVersion()) {
            violations.add("candidate state has a different root-world version");
        }
        if (request.hardConstraints().contains(RecoveryConstraint.PRESERVE_PROTECTED_CUSTOMER_COMMITMENTS)) {
            // The M1 domain model does not yet identify protected commitments. Fail closed rather than
            // silently claiming that this constraint has been verified. M7/domain evolution can add it.
            violations.add("PRESERVE_PROTECTED_CUSTOMER_COMMITMENTS cannot be verified by the current domain model");
        }

        PlanningState replay = worldModel.initialState(request);
        var seen = new HashSet<String>();
        for (PlanningAction proposed : proposedState.actionPath()) {
            if (!seen.add(proposed.planningActionId())) {
                violations.add("duplicate action identifier: " + proposed.planningActionId());
                break;
            }
            var available = worldModel.availableActions(request, replay);
            var canonical = available.stream()
                    .filter(action -> action.planningActionId().equals(proposed.planningActionId()))
                    .findFirst();
            if (canonical.isEmpty() || !canonical.get().equals(proposed)) {
                violations.add("action is no longer available or differs from the trusted action definition: "
                        + proposed.planningActionId());
                break;
            }
            WorldTransition transition = worldModel.apply(request, replay, canonical.get());
            if (transition instanceof WorldTransition.Rejected rejected) {
                violations.add(rejected.code() + ": " + rejected.reason());
                break;
            }
            replay = ((WorldTransition.Applied) transition).state();
        }
        if (!sameWorld(replay.world(), proposedState.world())) {
            violations.add("proposed planning state does not match deterministic simulator replay");
        }

        double progress = 0.0;
        double survivalProbability = 1.0;
        double robustnessTotal = 0.0;
        Duration duration = Duration.ZERO;
        Money cost = Money.of("0", request.scoringProfile().costNormalizationMaximum().currency().getCurrencyCode());
        boolean approvalRequired = false;
        int count = 0;
        for (PlanningAction action : proposedState.actionPath()) {
            var descriptor = request.actionDefinitionsById().get(action.descriptorId());
            if (descriptor == null) {
                violations.add("candidate references an action definition outside the request: " + action.descriptorId());
                continue;
            }
            try {
                var impact = action.impact();
                cost = cost.plus(impact.estimatedCost());
                duration = duration.plus(impact.expectedDuration());
                progress = Math.min(1.0, progress + impact.fulfillmentProgressDelta());
                // Explicit engineering assumption: action risks are treated as independent for this aggregate estimate.
                survivalProbability *= (1.0 - impact.risk());
                robustnessTotal += impact.robustness();
                approvalRequired |= descriptor.effect() == ActionEffect.DOMAIN_WRITE
                        && (request.authorityPolicy().approvalRequiredCapabilities().contains(descriptor.requiredCapability())
                        || request.hardConstraints().contains(RecoveryConstraint.APPROVAL_REQUIRED_FOR_EXTERNAL_COMMITMENT));
                count++;
            } catch (IllegalArgumentException currencyMismatch) {
                violations.add("action estimates use inconsistent currencies: " + currencyMismatch.getMessage());
                break;
            } catch (ArithmeticException durationOverflow) {
                violations.add("aggregate action duration exceeds the supported range");
                duration = Duration.ofSeconds(Long.MAX_VALUE);
                break;
            }
        }

        double aggregateRisk = 1.0 - survivalProbability;
        double robustness = count == 0 ? 0.0 : robustnessTotal / count;
        Instant completion;
        try {
            completion = request.requestedAt().plus(duration);
        } catch (DateTimeException | ArithmeticException overflow) {
            completion = Instant.MAX;
            violations.add("predicted completion time exceeds the supported range");
        }
        boolean deadlineBreach = completion.isAfter(request.objective().targetFulfillmentAt());
        if (request.riskPolicy().targetDateIsHard() && deadlineBreach) {
            violations.add("target fulfillment deadline would be missed");
        }
        if (aggregateRisk > request.riskPolicy().maximumAcceptedRisk()) {
            violations.add("aggregate plan risk exceeds the configured hard threshold");
        }
        // Approval is a downstream governance requirement, not a planner feasibility failure.
        // NO_UNQUALIFIED_SUBSTITUTION and NO_OVER_ALLOCATION are verified by deterministic simulator replay above.

        double costScore = 0.0;
        try {
            if (!cost.currency().equals(request.scoringProfile().costNormalizationMaximum().currency())) {
                violations.add("aggregate cost currency differs from the scoring profile");
            } else {
                costScore = 1.0 - Math.min(1.0, cost.amount().divide(
                        request.scoringProfile().costNormalizationMaximum().amount(), java.math.MathContext.DECIMAL64).doubleValue());
            }
        } catch (ArithmeticException invalidNormalization) {
            violations.add("cost normalization could not be evaluated");
        }
        long availableSeconds = Math.max(1L, Duration.between(request.requestedAt(),
                request.objective().targetFulfillmentAt()).getSeconds());
        double timeScore = 1.0 - Math.min(1.0, (double) duration.getSeconds() / availableSeconds);
        double score = request.scoringProfile().serviceWeight() * progress
                + request.scoringProfile().costWeight() * costScore
                + request.scoringProfile().timelinessWeight() * timeScore
                + request.scoringProfile().riskWeight() * (1.0 - aggregateRisk)
                + request.scoringProfile().robustnessWeight() * robustness;
        boolean feasible = violations.isEmpty();
        return new PlanEvaluation(feasible, feasible ? clamp(score) : 0.0, progress, cost, duration,
                clamp(aggregateRisk), clamp(robustness), completion, deadlineBreach, approvalRequired, violations);
    }

    private static boolean sameWorld(SupplyChainSnapshot left, SupplyChainSnapshot right) {
        return left.version() == right.version()
                && left.capturedAt().equals(right.capturedAt())
                && left.products().equals(right.products())
                && left.substitutions().equals(right.substitutions())
                && left.suppliers().equals(right.suppliers())
                && left.locations().equals(right.locations())
                && left.inventoryLots().equals(right.inventoryLots())
                && left.customerOrders().equals(right.customerOrders())
                && left.purchaseOrders().equals(right.purchaseOrders())
                && left.carriers().equals(right.carriers())
                && left.routes().equals(right.routes())
                && left.shipments().equals(right.shipments())
                && left.reservations().equals(right.reservations())
                && left.disruptions().equals(right.disruptions())
                && left.recoveryObjectives().equals(right.recoveryObjectives())
                && left.recoveryCandidates().equals(right.recoveryCandidates());
    }

    private static double clamp(double value) { return Math.max(0.0, Math.min(1.0, value)); }
}
