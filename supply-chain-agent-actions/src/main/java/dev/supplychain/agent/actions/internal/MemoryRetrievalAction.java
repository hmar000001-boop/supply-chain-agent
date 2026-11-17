package dev.supplychain.agent.actions.internal;

import dev.supplychain.agent.actions.api.*;
import dev.supplychain.agent.memory.retrieval.MemoryRetrievalQuery;
import dev.supplychain.agent.memory.retrieval.MemoryRetrievalResult;
import dev.supplychain.agent.memory.retrieval.MemoryRetrievalService;
import java.util.Objects;
import java.util.Set;

public final class MemoryRetrievalAction implements ActionHandler<MemoryRetrievalQuery, MemoryRetrievalResult> {
    public static final ActionId ID = new ActionId("coala.memory.retrieve");
    private static final ActionDescriptor DESCRIPTOR = new ActionDescriptor(ID, "Retrieve CoALA memory",
            "Perform deterministic scoped lookup across working, episodic, semantic and procedural memory", ActionCategory.INTERNAL,
            ActionEffect.READ_ONLY, Capability.MEMORY_RETRIEVE, false);
    private final MemoryRetrievalService service;
    public MemoryRetrievalAction(MemoryRetrievalService service) { this.service = Objects.requireNonNull(service, "service"); }
    @Override public ActionDescriptor descriptor() { return DESCRIPTOR; }
    @Override public Class<MemoryRetrievalQuery> inputType() { return MemoryRetrievalQuery.class; }
    @Override public Class<MemoryRetrievalResult> outputType() { return MemoryRetrievalResult.class; }
    @Override public Set<ResourceReference> resources(MemoryRetrievalQuery input) {
        return Set.of(new ResourceReference("memory-scope", input.scopeId().value()));
    }
    @Override public MemoryRetrievalResult execute(ActionContext context, MemoryRetrievalQuery input) { return service.retrieve(input); }
}
