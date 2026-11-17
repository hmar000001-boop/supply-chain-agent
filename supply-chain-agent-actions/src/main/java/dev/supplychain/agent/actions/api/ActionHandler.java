package dev.supplychain.agent.actions.api;

import java.util.Set;

public interface ActionHandler<I, O> {
    ActionDescriptor descriptor();
    Class<I> inputType();
    Class<O> outputType();
    O execute(ActionContext context, I input);
    default Set<ResourceReference> resources(I input) { return Set.of(); }
    default PreconditionCheck precondition(ActionContext context, I input) { return PreconditionCheck.pass(); }
    /** Canonical request representation used only for a hash; required for state-changing actions. */
    default String requestFingerprint(I input) { return inputType().getName() + ":" + input; }
    /** Stable reference and safe metadata stored with the operation receipt. */
    default String resultReference(O output) { return null; }
}
