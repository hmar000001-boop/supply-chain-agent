package dev.supplychain.agent.actions.api;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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

    /** Returns a handler only to trusted application composition/validation code. */
    public synchronized Optional<ActionHandler<?, ?>> findHandler(ActionId id) {
        return Optional.ofNullable(handlers.get(Objects.requireNonNull(id, "id")));
    }

    /** Returns the registered descriptor for trusted application-side action-plan validation. */
    public synchronized ActionDescriptor descriptor(ActionId id) {
        return require(id).descriptor();
    }

    /**
     * Returns the handler's canonical request fingerprint after validating the runtime input type.
     * This is intended for trusted application composition, not for model-supplied descriptors.
     */
    public synchronized String requestFingerprint(ActionId id, Object input) {
        ActionHandler<?, ?> handler = require(id);
        if (!handler.inputType().isInstance(Objects.requireNonNull(input, "input"))) {
            throw new IllegalArgumentException("Input type does not match registered action " + id);
        }
        return fingerprintUnchecked(handler, input);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String fingerprintUnchecked(ActionHandler handler, Object input) {
        return Objects.requireNonNull(handler.requestFingerprint(input), "request fingerprint");
    }

    public synchronized Collection<ActionDescriptor> descriptors() {
        return handlers.values().stream().map(ActionHandler::descriptor)
                .sorted(Comparator.comparing(descriptor -> descriptor.id().value())).toList();
    }
}
