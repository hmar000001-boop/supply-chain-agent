package dev.supplychain.agent.actions.internal;

/**
 * Execution-aware reasoning provider whose computation is charged to the same host-owned budget
 * identity as the planner and B6 meta-control service. The identity is opaque to the actions
 * module so the framework-neutral contract does not depend on runtime or persistence adapters.
 */
public interface BudgetBoundReasoningProvider extends ExecutionAwareReasoningProvider {
    boolean usesBudgetIdentity(Object budgetIdentity);
}
