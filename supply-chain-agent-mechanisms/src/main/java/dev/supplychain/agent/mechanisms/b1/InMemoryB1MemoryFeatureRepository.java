package dev.supplychain.agent.mechanisms.b1;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Test/reference repository; production hosts should use the PostgreSQL adapter. */
public final class InMemoryB1MemoryFeatureRepository implements B1MemoryFeatureRepository {
    private final ConcurrentHashMap<B1MemoryCandidateReference, B1MemoryFeatureState> values = new ConcurrentHashMap<>();
    @Override public Optional<B1MemoryFeatureState> find(B1MemoryCandidateReference reference) { return Optional.ofNullable(values.get(reference)); }
    @Override public void saveNew(B1MemoryFeatureState state) {
        if (values.putIfAbsent(state.reference(), state) != null) throw new IllegalStateException("B1 feature state already exists");
    }
    /** Immutable point-in-time view for evaluation evidence capture; never used for selection. */
    public java.util.List<B1MemoryFeatureState> snapshot() {
        return values.values().stream()
                .sorted(java.util.Comparator.comparing(value -> value.reference().toString()))
                .toList();
    }

    @Override public boolean compareAndSet(B1MemoryCandidateReference reference, long expectedRevision, B1MemoryFeatureState replacement) {
        if (!reference.equals(replacement.reference())) throw new SecurityException("B1 feature CAS cannot change reference");
        final boolean[] changed = {false};
        values.compute(reference, (key,current) -> {
            if (current != null && current.revision() == expectedRevision) { changed[0] = true; return replacement; }
            return current;
        });
        return changed[0];
    }
}
