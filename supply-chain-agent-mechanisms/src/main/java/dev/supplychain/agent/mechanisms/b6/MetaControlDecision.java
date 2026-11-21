package dev.supplychain.agent.mechanisms.b6;

import java.util.Objects;

public record MetaControlDecision(MetaControlAction action, MetaControlReason reason, ResumeCondition resumeCondition,
                                  String explanation, double maximumUncertainty,
                                  PlannerBudgetDownshift plannerBudgetDownshift) {
    public MetaControlDecision {
        action = Objects.requireNonNull(action, "action");
        reason = Objects.requireNonNull(reason, "reason");
        explanation = Objects.requireNonNull(explanation, "explanation");
        if (explanation.isBlank() || !Double.isFinite(maximumUncertainty)
                || maximumUncertainty < 0 || maximumUncertainty > 1) {
            throw new IllegalArgumentException("invalid decision details");
        }
        switch (action) {
            case REQUEST_EVIDENCE -> {
                if (resumeCondition != ResumeCondition.NEW_EVIDENCE) {
                    throw new IllegalArgumentException("evidence request must resume on new evidence");
                }
            }
            case INTERRUPT_FOR_HUMAN -> {
                if (resumeCondition != ResumeCondition.HUMAN_DECISION
                        && resumeCondition != ResumeCondition.BUDGET_REPLENISHED) {
                    throw new IllegalArgumentException("human interruption requires a human decision or authorized budget replenishment");
                }
            }
            case STOP -> {
                if (resumeCondition != ResumeCondition.NEVER) {
                    throw new IllegalArgumentException("terminal stop must use NEVER resume condition");
                }
            }
            case CONTINUE -> {
                if (resumeCondition != null) throw new IllegalArgumentException("continue cannot have a resume condition");
            }
            case REDUCE_COMPUTATION -> {
                if (resumeCondition != null) throw new IllegalArgumentException("computation reduction cannot have a resume condition");
            }
        }
        if (action == MetaControlAction.REDUCE_COMPUTATION && plannerBudgetDownshift == null) {
            throw new IllegalArgumentException("REDUCE_COMPUTATION requires an explicit planner-budget downshift directive");
        }
        if (action != MetaControlAction.REDUCE_COMPUTATION && plannerBudgetDownshift != null) {
            throw new IllegalArgumentException("planner-budget downshift is valid only for REDUCE_COMPUTATION");
        }
    }

    /** Compatibility constructor for callers using the original decision shape. */
    public MetaControlDecision(MetaControlAction action, MetaControlReason reason, ResumeCondition resumeCondition,
                               String explanation, double maximumUncertainty) {
        this(action, reason, resumeCondition, explanation, maximumUncertainty,
                action == MetaControlAction.REDUCE_COMPUTATION ? PlannerBudgetDownshift.halfBudget() : null);
    }
}
