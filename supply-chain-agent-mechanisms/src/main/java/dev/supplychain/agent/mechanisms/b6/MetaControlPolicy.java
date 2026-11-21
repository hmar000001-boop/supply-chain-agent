package dev.supplychain.agent.mechanisms.b6;

public record MetaControlPolicy(double evidenceRequestUncertainty, double humanReviewUncertainty,
                                double lowResourceRemainingFraction, int plannerBudgetReductionPercent) {
    public MetaControlPolicy {
        if (!unit(evidenceRequestUncertainty) || !unit(humanReviewUncertainty)
                || humanReviewUncertainty < evidenceRequestUncertainty
                || !unit(lowResourceRemainingFraction)
                || plannerBudgetReductionPercent < 1 || plannerBudgetReductionPercent > 90) {
            throw new IllegalArgumentException("invalid meta-control thresholds or planner downshift policy");
        }
    }

    /** Existing configurations retain the bounded 50% compute downshift. */
    public MetaControlPolicy(double evidenceRequestUncertainty, double humanReviewUncertainty,
                             double lowResourceRemainingFraction) {
        this(evidenceRequestUncertainty, humanReviewUncertainty, lowResourceRemainingFraction, 50);
    }

    private static boolean unit(double value) {
        return Double.isFinite(value) && value >= 0 && value <= 1;
    }
}
