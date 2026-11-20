package dev.supplychain.agent.runtime.langchain4j;

import dev.supplychain.agent.actions.api.ActionContext;
import dev.supplychain.agent.actions.api.ActionExecutionException;
import dev.supplychain.agent.actions.api.ActionFailure;
import dev.supplychain.agent.actions.api.ActionFailureCode;
import dev.supplychain.agent.actions.internal.BudgetBoundReasoningProvider;
import dev.supplychain.agent.actions.internal.ReasoningAssessment;
import dev.supplychain.agent.actions.internal.ReasoningProvider;
import dev.supplychain.agent.actions.internal.ReasoningRequest;
import dev.supplychain.agent.mechanisms.b6.ResourceBudgetExceededException;
import dev.supplychain.agent.mechanisms.b6.ResourceUnit;
import dev.supplychain.agent.mechanisms.b6.ResourceUsage;
import dev.supplychain.agent.runtime.cognitive.ResourceBudgetExecutionGuard;

import java.util.Map;
import java.util.Objects;

/**
 * Execution-aware M3 reasoning adapter. The shared B6 guard reserves and commits one observable
 * model invocation and one reasoning evaluation. Token counts are not guessed; providers exposing
 * usage metadata can introduce a richer trusted measurement later.
 */
public final class BudgetedReasoningProvider implements BudgetBoundReasoningProvider {
    private static final ResourceUsage USAGE = new ResourceUsage(Map.of(
            ResourceUnit.MODEL_CALLS, 1L,
            ResourceUnit.REASONING_EVALUATIONS, 1L));

    private final ReasoningProvider delegate;
    private final ResourceBudgetExecutionGuard budgetGuard;

    public BudgetedReasoningProvider(ReasoningProvider delegate, ResourceBudgetExecutionGuard budgetGuard) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.budgetGuard = Objects.requireNonNull(budgetGuard, "budgetGuard");
    }

    @Override
    public boolean usesBudgetIdentity(Object budgetIdentity) {
        return budgetGuard == budgetIdentity;
    }

    @Override
    public ReasoningAssessment assess(ActionContext context, ReasoningRequest request) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(request, "request");
        try {
            return budgetGuard.execute(context.executionId(), "reasoning:" + context.correlationId(), USAGE,
                    () -> delegate.assess(request), ignored -> USAGE);
        } catch (ResourceBudgetExceededException exhausted) {
            throw new ActionExecutionException(new ActionFailure(ActionFailureCode.RESOURCE_BUDGET_EXHAUSTED,
                    exhausted.getMessage(), false, false));
        }
    }
}
