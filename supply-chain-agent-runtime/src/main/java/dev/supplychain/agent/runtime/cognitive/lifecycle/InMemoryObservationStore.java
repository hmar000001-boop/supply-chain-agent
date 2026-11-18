package dev.supplychain.agent.runtime.cognitive.lifecycle;

import dev.supplychain.agent.runtime.cognitive.CognitiveCycleOperations;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe reference adapter; M7 supplies a durable observation/event store. */
public final class InMemoryObservationStore implements ObservationStore {
    private final ConcurrentHashMap<String, CognitiveCycleOperations.ExternalObservation> values = new ConcurrentHashMap<>();
    @Override public void save(CognitiveCycleOperations.ExternalObservation observation) {
        Objects.requireNonNull(observation, "observation");
        var current = values.putIfAbsent(observation.observationId(), observation);
        if (current != null && !current.equals(observation)) throw new SecurityException("observation ID was reused for different content");
    }
    @Override public Optional<CognitiveCycleOperations.ExternalObservation> find(String id) {
        String key = Objects.requireNonNull(id, "observationId").trim();
        if (key.isEmpty()) throw new IllegalArgumentException("observationId cannot be blank");
        return Optional.ofNullable(values.get(key));
    }
}
