package dev.supplychain.agent.actions.internal;

import dev.supplychain.agent.actions.api.ActionContext;

/**
 * Reasoning provider that receives trusted execution metadata from the dispatcher.
 * The request itself remains untrusted content and must never supply identity or authority.
 */
public interface ExecutionAwareReasoningProvider extends ReasoningProvider {
    ReasoningAssessment assess(ActionContext context, ReasoningRequest request);

    @Override
    default ReasoningAssessment assess(ReasoningRequest request) {
        throw new IllegalStateException("execution-aware reasoning requires trusted ActionContext metadata");
    }
}
