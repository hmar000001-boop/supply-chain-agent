package dev.supplychain.agent.actions.api;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ActionRegistry {
    private final Map<ActionId, ActionHandler<?, ?>> handlers = new LinkedHashMap<>();

    public synchronized <I, O> ActionRegistry register(ActionHandler<I, O> handler) {
        Objects.requireNonNull(handler, "handler");
        ActionDescriptor descriptor = Objects.requireNonNull(handler.descriptor(), "handler descriptor");
        ActionId id = Objects.requireNonNull(descriptor.id(), "action id");
        Objects.requireNonNull(handler.inputType(), "action input type");
        Objects.requireNonNull(handler.outputType(), "action output type");
        if (handlers.containsKey(id)) throw new IllegalArgumentException("Duplicate action id: " + id);
        handlers.put(id, handler);
        return this;
    }

    synchronized ActionHandler<?, ?> require(ActionId id) {
        ActionHandler<?, ?> handler = handlers.get(Objects.requireNonNull(id, "id"));
        if (handler == null) throw new IllegalArgumentException("Unknown action: " + id);
        return handler;
    }

    public synchronized Collection<ActionDescriptor> descriptors() {
        return handlers.values().stream().map(ActionHandler::descriptor)
                .sorted(Comparator.comparing(descriptor -> descriptor.id().value())).toList();
    }
}
