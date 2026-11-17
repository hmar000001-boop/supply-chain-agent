package dev.supplychain.agent.planning.api;

import dev.supplychain.agent.domain.common.Money;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record PlanEvaluation(boolean feasible, double score, double expectedFulfillmentProgress,
                             Money estimatedCost, Duration expectedDuration, double aggregateRisk,
                             double robustness, Instant predictedCompletionAt, boolean targetDeadlineBreach,
                             boolean approvalRequired, List<String> violations) {
    public PlanEvaluation {
        if (!Double.isFinite(score) || score < 0 || score > 1) throw new IllegalArgumentException("score must be in [0,1]");
        if (!Double.isFinite(expectedFulfillmentProgress) || expectedFulfillmentProgress < 0 || expectedFulfillmentProgress > 1) throw new IllegalArgumentException("progress must be in [0,1]");
        estimatedCost = Objects.requireNonNull(estimatedCost, "estimatedCost");
        expectedDuration = Objects.requireNonNull(expectedDuration, "expectedDuration");
        if (expectedDuration.isNegative()) throw new IllegalArgumentException("expectedDuration cannot be negative");
        if (!Double.isFinite(aggregateRisk) || aggregateRisk < 0 || aggregateRisk > 1
                || !Double.isFinite(robustness) || robustness < 0 || robustness > 1) throw new IllegalArgumentException("risk and robustness must be in [0,1]");
        predictedCompletionAt = Objects.requireNonNull(predictedCompletionAt, "predictedCompletionAt");
        violations = List.copyOf(Objects.requireNonNull(violations, "violations"));
        if (feasible && !violations.isEmpty()) throw new IllegalArgumentException("feasible evaluation cannot contain hard violations");
        if (!feasible && violations.isEmpty()) throw new IllegalArgumentException("infeasible evaluation must explain at least one hard violation");
        if (!feasible && score != 0.0) throw new IllegalArgumentException("infeasible evaluation must have score zero");
    }
}
