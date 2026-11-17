package dev.supplychain.agent.actions.internal;

import dev.supplychain.agent.actions.api.*;
import dev.supplychain.agent.memory.working.WorkingMemoryService;
import dev.supplychain.agent.memory.working.WorkingMemorySnapshot;
import java.util.Objects;
import java.util.Set;

public final class WorkingMemoryUpdateAction implements ActionHandler<WorkingMemoryUpdateRequest, WorkingMemorySnapshot> {
    public static final ActionId ID = new ActionId("coala.memory.working.update");
    private static final ActionDescriptor DESCRIPTOR = new ActionDescriptor(ID, "Update working memory",
            "Write one typed, evidence-linked working-memory slot using optimistic version control", ActionCategory.INTERNAL,
            ActionEffect.MEMORY_WRITE, Capability.MEMORY_UPDATE_WORKING, true);
    private final WorkingMemoryService service;
    public WorkingMemoryUpdateAction(WorkingMemoryService service) { this.service = Objects.requireNonNull(service, "service"); }
    @Override public ActionDescriptor descriptor() { return DESCRIPTOR; }
    @Override public Class<WorkingMemoryUpdateRequest> inputType() { return WorkingMemoryUpdateRequest.class; }
    @Override public Class<WorkingMemorySnapshot> outputType() { return WorkingMemorySnapshot.class; }
    @Override public Set<ResourceReference> resources(WorkingMemoryUpdateRequest input) {
        return Set.of(new ResourceReference("memory-scope", input.scopeId().value()));
    }
    @Override public String requestFingerprint(WorkingMemoryUpdateRequest input) { return input.canonical(); }
    @Override public PreconditionCheck precondition(ActionContext context, WorkingMemoryUpdateRequest input) {
        var current = service.load(input.memoryId());
        if (!current.scopeId().equals(input.scopeId())) {
            return PreconditionCheck.reject("MEMORY_SCOPE_MISMATCH", "Working-memory scope does not match the requested scope");
        }
        if (current.version() != input.expectedVersion()) {
            return PreconditionCheck.reject("STALE_MEMORY_VERSION", "Working-memory version changed; reload before updating");
        }
        if (!current.expiresAt().isAfter(context.now())) {
            return PreconditionCheck.reject("WORKING_MEMORY_EXPIRED", "Expired working memory cannot be updated");
        }
        return PreconditionCheck.pass();
    }
    @Override public WorkingMemorySnapshot execute(ActionContext context, WorkingMemoryUpdateRequest input) {
        return service.put(input.memoryId(), input.expectedVersion(), input.key(), input.value(), input.evidence(), context.now());
    }
    @Override public String resultReference(WorkingMemorySnapshot output) { return "working-memory:" + output.id().value() + ":v" + output.version(); }
}
