package dev.supplychain.agent.mechanisms.b6;

import java.time.Instant;
import java.util.Objects;

/** Enforces computation budgets and uncertainty policy; business constraints/resources remain domain-owned. */
public final class MetaControlGovernor{
    private final MetaControlPolicy policy;
    public MetaControlGovernor(MetaControlPolicy policy){this.policy=Objects.requireNonNull(policy,"policy");}
    public MetaControlDecision evaluate(ResourceBudgetSnapshot budget,UncertaintyAssessment uncertainty,
                                        ResourceUsage requiredForNextStep,Instant now){
        Objects.requireNonNull(budget,"budget");Objects.requireNonNull(uncertainty,"uncertainty");
        Objects.requireNonNull(requiredForNextStep,"requiredForNextStep");Objects.requireNonNull(now,"now");
        double max=uncertainty.maximumUncertainty();
        if(budget.overBudget())return decision(MetaControlAction.STOP,MetaControlReason.REQUIRED_RESOURCE_EXHAUSTED,ResumeCondition.NEVER,"actual resource consumption exceeded its configured limit",max);
        if(!now.isBefore(budget.budget().deadline()))return decision(MetaControlAction.STOP,MetaControlReason.DEADLINE_EXCEEDED,ResumeCondition.NEVER,"execution deadline has passed",max);
        boolean nextStepBlocked=java.util.Arrays.stream(ResourceUnit.values()).anyMatch(u->requiredForNextStep.amount(u)>budget.remaining(u));
        if(nextStepBlocked)return decision(MetaControlAction.INTERRUPT_FOR_HUMAN,MetaControlReason.REQUIRED_RESOURCE_EXHAUSTED,ResumeCondition.BUDGET_REPLENISHED,"the next step cannot fit the remaining resource budget; wait for explicitly authorized replenishment",max);
        if(max>=policy.humanReviewUncertainty())return decision(MetaControlAction.INTERRUPT_FOR_HUMAN,MetaControlReason.UNCERTAINTY_REQUIRES_HUMAN_REVIEW,ResumeCondition.HUMAN_DECISION,"uncertainty exceeds the human-review threshold",max);
        if(max>=policy.evidenceRequestUncertainty())return decision(MetaControlAction.REQUEST_EVIDENCE,MetaControlReason.UNCERTAINTY_REQUIRES_EVIDENCE,ResumeCondition.NEW_EVIDENCE,"additional evidence is required before continuing",max);
        boolean low=java.util.Arrays.stream(ResourceUnit.values()).filter(u->requiredForNextStep.amount(u)>0&&budget.budget().limit(u)!=Long.MAX_VALUE&&budget.budget().limit(u)>0)
                .anyMatch(u->(double)budget.remaining(u)/budget.budget().limit(u)<=policy.lowResourceRemainingFraction());
        if(low) return new MetaControlDecision(MetaControlAction.REDUCE_COMPUTATION,
                MetaControlReason.RESOURCE_MARGIN_LOW, null,
                "resource margin for the next step is low; reduce the next planner call's consumable compute limits",
                max, new PlannerBudgetDownshift(policy.plannerBudgetReductionPercent(), "b6-resource-margin-downshift-v1"));
        return decision(MetaControlAction.CONTINUE,MetaControlReason.WITHIN_BUDGET_AND_UNCERTAINTY,null,"uncertainty and declared next-step resource requirements are within policy",max);
    }
    private static MetaControlDecision decision(MetaControlAction a,MetaControlReason r,ResumeCondition c,String m,double u){return new MetaControlDecision(a,r,c,m,u);}
}
