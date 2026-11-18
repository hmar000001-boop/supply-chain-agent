package dev.supplychain.agent.runtime.cognitive.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.supplychain.agent.actions.api.ActionCategory;
import dev.supplychain.agent.actions.api.ActionContext;
import dev.supplychain.agent.actions.api.ActionDescriptor;
import dev.supplychain.agent.actions.api.ActionDispatcher;
import dev.supplychain.agent.actions.api.ActionEffect;
import dev.supplychain.agent.actions.api.ActionHandler;
import dev.supplychain.agent.actions.api.ActionId;
import dev.supplychain.agent.actions.api.ActionPrincipal;
import dev.supplychain.agent.actions.api.ActionRegistry;
import dev.supplychain.agent.actions.api.Capability;
import dev.supplychain.agent.actions.api.CapabilityAuthorizer;
import dev.supplychain.agent.actions.api.CapabilityGrant;
import dev.supplychain.agent.actions.api.InMemoryActionExecutionLedger;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

final class ActionDispatchCommandTest {
    private static final Instant NOW = Instant.parse("2026-11-17T22:00:00Z");
    private static final ActionId ACTION_ID = new ActionId("test.grounding.inventory.reserve");

    @Test
    void typedPlanCommandUsesM3DispatcherAndIdempotencyLedger() {
        AtomicInteger executions = new AtomicInteger();
        ActionRegistry registry = new ActionRegistry().register(handler(executions));
        ActionDispatcher dispatcher = new ActionDispatcher(registry, new CapabilityAuthorizer(), new InMemoryActionExecutionLedger());
        ActionDispatchCommand<String, String> command = new ActionDispatchCommand<>(
                "reserve:lot-1", ACTION_ID, String.class, String.class, "lot-1:10");
        ActionPrincipal principal = new ActionPrincipal("operator-1",
                Set.of(CapabilityGrant.all(Capability.INVENTORY_RESERVE)));
        ActionContext context = ActionContext.create("execution:m6b", "request:m6b", principal, NOW, "idempotency:m6b:1");

        var first = command.dispatch(dispatcher, context);
        var retry = command.dispatch(dispatcher, context);

        assertEquals(dev.supplychain.agent.actions.api.ActionStatus.SUCCEEDED, first.status());
        assertEquals(dev.supplychain.agent.actions.api.ActionStatus.ALREADY_COMPLETED, retry.status());
        assertEquals(1, executions.get());
        assertTrue(retry.receiptOptional().isPresent());
    }

    @Test
    void typedPlanCommandCannotExceedPrincipalCapabilities() {
        AtomicInteger executions = new AtomicInteger();
        ActionRegistry registry = new ActionRegistry().register(handler(executions));
        ActionDispatcher dispatcher = new ActionDispatcher(registry, new CapabilityAuthorizer(), new InMemoryActionExecutionLedger());
        ActionDispatchCommand<String, String> command = new ActionDispatchCommand<>(
                "reserve:lot-1", ACTION_ID, String.class, String.class, "lot-1:10");
        ActionPrincipal principal = new ActionPrincipal("read-only-operator", Set.of());
        ActionContext context = ActionContext.create("execution:m6b", "request:m6b", principal, NOW, "idempotency:m6b:2");

        var result = command.dispatch(dispatcher, context);

        assertEquals(dev.supplychain.agent.actions.api.ActionStatus.REJECTED, result.status());
        assertEquals(0, executions.get());
    }

    private static ActionHandler<String, String> handler(AtomicInteger executions) {
        ActionDescriptor descriptor = new ActionDescriptor(ACTION_ID, "Test inventory reservation",
                "State-changing test action", ActionCategory.GROUNDING, ActionEffect.DOMAIN_WRITE,
                Capability.INVENTORY_RESERVE, true);
        return new ActionHandler<>() {
            @Override public ActionDescriptor descriptor() { return descriptor; }
            @Override public Class<String> inputType() { return String.class; }
            @Override public Class<String> outputType() { return String.class; }
            @Override public String execute(ActionContext context, String input) {
                executions.incrementAndGet();
                return "reserved:" + input;
            }
            @Override public String requestFingerprint(String input) { return "reserve:" + input; }
            @Override public String resultReference(String output) { return output; }
        };
    }
}
