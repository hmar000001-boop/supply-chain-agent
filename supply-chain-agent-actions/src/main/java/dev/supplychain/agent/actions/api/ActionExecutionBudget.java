package dev.supplychain.agent.actions.api;

import java.util.function.Supplier;

/** Optional execution-resource boundary applied after M3 idempotency claim and before handler invocation.
 * For idempotent actions, operationId is stable across M3 ledger replacement and is derived from
 * the principal/action/idempotency-key tuple; non-idempotent attempts receive a unique claim ID.
 */
public interface ActionExecutionBudget {
    <T> T execute(ActionContext context, String operationId, Supplier<T> invocation);

    /** Identity check used by the complete application composition to prevent divergent B6 guards. */
    boolean usesBudgetIdentity(Object identity);
}
