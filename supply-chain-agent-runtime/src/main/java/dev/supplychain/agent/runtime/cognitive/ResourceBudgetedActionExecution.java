package dev.supplychain.agent.runtime.cognitive;

import dev.supplychain.agent.actions.api.ActionContext;
import dev.supplychain.agent.actions.api.ActionExecutionBudget;
import dev.supplychain.agent.actions.api.ActionExecutionException;
import dev.supplychain.agent.actions.api.ActionFailure;
import dev.supplychain.agent.actions.api.ActionFailureCode;
import dev.supplychain.agent.mechanisms.b6.BudgetReservationInUseException;
import dev.supplychain.agent.mechanisms.b6.ResourceBudgetExceededException;
import dev.supplychain.agent.mechanisms.b6.ResourceUnit;
import dev.supplychain.agent.mechanisms.b6.ResourceUsage;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** B6 accounting adapter for M3 handler invocation, called only after the M3 idempotency ledger grants the attempt. */
public final class ResourceBudgetedActionExecution implements ActionExecutionBudget {
    private static final ResourceUsage ONE_DISPATCH = ResourceUsage.of(ResourceUnit.ACTION_DISPATCHES, 1L);
    private final ResourceBudgetExecutionGuard guard;

    public ResourceBudgetedActionExecution(ResourceBudgetExecutionGuard guard) {
        this.guard = Objects.requireNonNull(guard, "guard");
    }

    @Override
    public <T> T execute(ActionContext context, String operationId, Supplier<T> invocation) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(invocation, "invocation");
        AtomicBoolean invocationReturned = new AtomicBoolean();
        try {
            return guard.execute(context.executionId(), "m3-action:" + operationId, ONE_DISPATCH,
                    () -> {
                        T result = invocation.get();
                        invocationReturned.set(true);
                        return result;
                    }, ignored -> ONE_DISPATCH);
        } catch (ResourceBudgetExceededException exhausted) {
            throw new ActionExecutionException(new ActionFailure(ActionFailureCode.RESOURCE_BUDGET_EXHAUSTED,
                    exhausted.getMessage(), false, false));
        } catch (BudgetReservationInUseException fencedOrDuplicate) {
            throw new ActionExecutionException(new ActionFailure(ActionFailureCode.UNKNOWN_OUTCOME,
                    "M3 dispatch is blocked because its B6 reservation is already active, committed or reconciled; reconcile the M3 operation before retrying",
                    false, true));
        } catch (RuntimeException accountingFailure) {
            if (!invocationReturned.get()) throw accountingFailure;
            // The domain handler returned successfully, but B6 could not commit its usage because
            // the lease was fenced/expired or the durable ledger failed. The external effect may
            // already exist: never misclassify this as an authorization rejection or retryable
            // provider failure. M3 must retain an unknown outcome and reconcile before retrying.
            throw new ActionExecutionException(new ActionFailure(ActionFailureCode.UNKNOWN_OUTCOME,
                    "M3 action returned but its B6 reservation could not be committed; reconcile the external outcome before retrying",
                    false, true));
        }
    }

    @Override public boolean usesBudgetIdentity(Object identity) { return guard == identity; }
}
