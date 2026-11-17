package dev.supplychain.agent.actions.internal;

import dev.supplychain.agent.actions.api.*;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class ReasoningAction implements ActionHandler<ReasoningRequest, ReasoningAssessment> {
    public static final ActionId ID = new ActionId("coala.reasoning.assess");
    private static final ActionDescriptor DESCRIPTOR = new ActionDescriptor(ID, "Assess supplied context",
            "Produce evidence-bounded findings, hypotheses, assumptions and information gaps", ActionCategory.INTERNAL,
            ActionEffect.READ_ONLY, Capability.REASONING_INVOKE, false);
    private final ReasoningProvider provider;
    public ReasoningAction(ReasoningProvider provider) { this.provider = Objects.requireNonNull(provider, "provider"); }
    @Override public ActionDescriptor descriptor() { return DESCRIPTOR; }
    @Override public Class<ReasoningRequest> inputType() { return ReasoningRequest.class; }
    @Override public Class<ReasoningAssessment> outputType() { return ReasoningAssessment.class; }
    @Override public Set<ResourceReference> resources(ReasoningRequest input) {
        return input.evidence().stream().map(ref -> new ResourceReference("evidence", ref.evidenceType() + ":" + ref.evidenceId())).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    @Override public ReasoningAssessment execute(ActionContext context, ReasoningRequest input) {
        ReasoningAssessment assessment = Objects.requireNonNull(provider.assess(input), "reasoning provider result");
        Set<?> supplied = new HashSet<>(input.evidence());
        if (!supplied.containsAll(assessment.supportingEvidence())) {
            throw new ActionExecutionException(new ActionFailure(ActionFailureCode.INVALID_INPUT,
                    "Reasoning provider cited evidence that was not supplied in the request", false, false));
        }
        return assessment;
    }
}
