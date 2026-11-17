package dev.supplychain.agent.actions;

import dev.supplychain.agent.actions.api.*;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActionDispatcherTest {
    private static final ActionId ID = new ActionId("test.inventory.reserve");
    private static final ResourceReference LOT = new ResourceReference("inventory-lot", "LOT-1");
    private static final Instant NOW = Instant.parse("2026-11-17T13:00:00Z");

    record Input(String resourceId, int units, boolean preconditionAllowed) { }
    record Output(String result) { }

    static class CountingHandler implements ActionHandler<Input, Output> {
        private final AtomicInteger calls = new AtomicInteger();
        @Override public ActionDescriptor descriptor() {
            return new ActionDescriptor(ID, "Test reserve", "Test-only mutation", ActionCategory.GROUNDING,
                    ActionEffect.DOMAIN_WRITE, Capability.INVENTORY_RESERVE, true);
        }
        @Override public Class<Input> inputType() { return Input.class; }
        @Override public Class<Output> outputType() { return Output.class; }
        @Override public Set<ResourceReference> resources(Input input) {
            return Set.of(new ResourceReference("inventory-lot", input.resourceId()));
        }
        @Override public String requestFingerprint(Input input) { return input.resourceId() + "|" + input.units(); }
        @Override public PreconditionCheck precondition(ActionContext context, Input input) {
            return input.preconditionAllowed() ? PreconditionCheck.pass()
                    : PreconditionCheck.reject("NOT_READY", "Handler precondition was not met");
        }
        @Override public Output execute(ActionContext context, Input input) {
            calls.incrementAndGet();
            return new Output("reserved:" + input.units());
        }
        @Override public String resultReference(Output output) { return output.result(); }
    }

    @Test
    void duplicateSuccessfulMutationIsNotExecutedTwice() {
        var handler = new CountingHandler();
        var registry = new ActionRegistry().register(handler);
        var dispatcher = new ActionDispatcher(registry, new CapabilityAuthorizer(), new InMemoryActionExecutionLedger());
        var context = context(Set.of(CapabilityGrant.scoped(Capability.INVENTORY_RESERVE, Set.of(LOT))), "key-1");
        var input = new Input("LOT-1", 5, true);

        var first = dispatcher.dispatch(ID, Input.class, Output.class, context, input);
        var duplicate = dispatcher.dispatch(ID, Input.class, Output.class, context, input);

        assertEquals(ActionStatus.SUCCEEDED, first.status());
        assertEquals("reserved:5", first.value().result());
        assertEquals(ActionStatus.ALREADY_COMPLETED, duplicate.status());
        assertEquals(first.receipt(), duplicate.receipt());
        assertEquals(1, handler.calls.get());
    }

    @Test
    void idempotencyKeyCannotBeReusedForDifferentInput() {
        var handler = new CountingHandler();
        var dispatcher = dispatcher(handler);
        var context = context(Set.of(CapabilityGrant.scoped(Capability.INVENTORY_RESERVE, Set.of(LOT))), "key-2");

        var first = dispatcher.dispatch(ID, Input.class, Output.class, context, new Input("LOT-1", 5, true));
        var reused = dispatcher.dispatch(ID, Input.class, Output.class, context, new Input("LOT-1", 8, true));

        assertEquals(ActionStatus.SUCCEEDED, first.status());
        assertEquals(ActionStatus.REJECTED, reused.status());
        assertEquals(ActionFailureCode.IDEMPOTENCY_KEY_REUSED, reused.failure().code());
        assertEquals(1, handler.calls.get());
    }

    @Test
    void authorizationIsCheckedBeforePreconditionAndIdempotencyClaim() {
        var handler = new CountingHandler();
        var dispatcher = dispatcher(handler);
        var unauthorized = context(Set.of(CapabilityGrant.scoped(Capability.INVENTORY_RESERVE,
                Set.of(new ResourceReference("inventory-lot", "OTHER-LOT")))), "key-3");

        var denied = dispatcher.dispatch(ID, Input.class, Output.class, unauthorized, new Input("LOT-1", 5, true));
        var laterAuthorized = dispatcher.dispatch(ID, Input.class, Output.class,
                context(Set.of(CapabilityGrant.scoped(Capability.INVENTORY_RESERVE, Set.of(LOT))), "key-3"),
                new Input("LOT-1", 5, true));

        assertEquals(ActionStatus.REJECTED, denied.status());
        assertEquals(ActionFailureCode.AUTHORIZATION_DENIED, denied.failure().code());
        assertEquals(ActionStatus.SUCCEEDED, laterAuthorized.status());
        assertEquals(1, handler.calls.get());
    }

    @Test
    void preconditionRejectionDoesNotConsumeIdempotencyKey() {
        var handler = new CountingHandler();
        var dispatcher = dispatcher(handler);
        var context = context(Set.of(CapabilityGrant.scoped(Capability.INVENTORY_RESERVE, Set.of(LOT))), "key-4");

        var rejected = dispatcher.dispatch(ID, Input.class, Output.class, context, new Input("LOT-1", 5, false));
        var allowed = dispatcher.dispatch(ID, Input.class, Output.class, context, new Input("LOT-1", 5, true));

        assertEquals(ActionStatus.REJECTED, rejected.status());
        assertEquals(ActionFailureCode.PRECONDITION_FAILED, rejected.failure().code());
        assertEquals(ActionStatus.SUCCEEDED, allowed.status());
        assertEquals(1, handler.calls.get());
    }

    @Test
    void retryableKnownFailureCanReacquireSameIdempotencyKey() {
        var ledger = new InMemoryActionExecutionLedger();
        var key = new IdempotencyLedgerKey("planner", ID, "key-retry");
        var first = ledger.claim(key, "fp", NOW);
        assertEquals(ActionLedgerClaim.Status.ACQUIRED, first.status());
        ledger.fail(key, new ActionFailure(ActionFailureCode.TRANSIENT_FAILURE,
                "Known failure before effect", true, false));
        var retry = ledger.claim(key, "fp", NOW.plusSeconds(1));
        assertEquals(ActionLedgerClaim.Status.ACQUIRED, retry.status());
        assertEquals(first.operationId(), retry.operationId());
    }

    @Test
    void registryDoesNotRetainInvalidHandlerWhenRegistrationFails() {
        var invalid = new CountingHandler() {
            @Override public Class<Input> inputType() { return null; }
        };
        var registry = new ActionRegistry();
        assertThrows(NullPointerException.class, () -> registry.register(invalid));
        var valid = new CountingHandler();
        assertSame(registry, registry.register(valid));
        assertEquals(1, registry.descriptors().size());
    }

    private static ActionDispatcher dispatcher(CountingHandler handler) {
        return new ActionDispatcher(new ActionRegistry().register(handler), new CapabilityAuthorizer(), new InMemoryActionExecutionLedger());
    }
    private static ActionContext context(Set<CapabilityGrant> grants, String key) {
        return ActionContext.create("exec-1", "corr-1", new ActionPrincipal("planner", grants), NOW, key);
    }
}
