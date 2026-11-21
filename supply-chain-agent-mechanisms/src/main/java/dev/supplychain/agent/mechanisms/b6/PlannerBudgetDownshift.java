package dev.supplychain.agent.mechanisms.b6;

/** Explicit bounded instruction for reducing one subsequent planning call's compute envelope. */
public record PlannerBudgetDownshift(int reductionPercent, String policyId) {
    public PlannerBudgetDownshift {
        if (reductionPercent < 1 || reductionPercent > 90) {
            throw new IllegalArgumentException("B6 planner-budget reduction must be between 1 and 90 percent");
        }
        if (policyId == null || policyId.isBlank()) {
            throw new IllegalArgumentException("planner downshift policy ID cannot be blank");
        }
        policyId = policyId.trim();
    }

    public static PlannerBudgetDownshift halfBudget() {
        return new PlannerBudgetDownshift(50, "b6-resource-margin-downshift-v1");
    }
}
